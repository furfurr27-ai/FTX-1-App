package dev.n0png.fieldops.android.dsp

import dev.n0png.fieldops.core.audio.TimedPcmBlock
import dev.n0png.fieldops.core.digital.DigitalMode
import dev.n0png.fieldops.core.dsp.DecodeResult
import dev.n0png.fieldops.core.dsp.Streaming12kDspEngine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

/**
 * FieldOps boundary around the pinned JS8Call Android native engine.
 *
 * RX accepts the shared continuous 12 kHz branch. TX exposes only native modem
 * scheduling/audio callbacks; it never owns rig control, CAT, PTT or USB audio.
 */
class Js8EngineAdapter(
    private val factory: EngineFactory,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val errorSink: (String) -> Unit = {},
) : Streaming12kDspEngine, AutoCloseable {

    data class TxRequest(
        val text: String,
        val myCall: String,
        val myGrid: String,
        val selectedCall: String = "",
        val submode: Int = 0,
        val audioFrequencyHz: Double,
        val txDelaySec: Double = 0.0,
        val forceIdentify: Boolean = false,
        val forceData: Boolean = false,
    ) {
        init {
            require(text.isNotBlank())
            require(myCall.isNotBlank())
            require(audioFrequencyHz >= 0.0)
            require(txDelaySec >= 0.0)
        }
    }

    data class TxStatus(
        val sessionActive: Boolean,
        val audioActive: Boolean,
        val millisecondsUntilAudio: Int,
    )

    interface TxSink {
        fun onAudio(samples: ShortArray, sampleRateHz: Int)
        fun onError(message: String)
    }

    interface NativeEngine : AutoCloseable {
        fun start(): Boolean
        fun stop()
        fun submitAudio(samples: ShortArray, timestampNs: Long): Boolean

        fun transmitMessage(request: TxRequest): Boolean = false
        fun stopTransmit() = Unit
        fun isTransmitting(): Boolean = false
        fun isTransmittingAudio(): Boolean = false
        fun txMillisecondsUntilAudio(): Int = -1
        fun setTransmitReady(ready: Boolean) = Unit
    }

    fun interface EngineFactory {
        fun create(callbacks: Callbacks): NativeEngine
    }

    interface Callbacks {
        fun onDecoded(
            utc: Int,
            snr: Int,
            dt: Float,
            freq: Float,
            text: String,
            type: Int,
            quality: Float,
            submode: Int,
            driftMs: Int,
        )

        fun onError(message: String)
        fun onTxAudio(samples: ShortArray, sampleRateHz: Int) = Unit
    }

    override val mode: DigitalMode = DigitalMode.JS8

    private val lock = Any()
    @Volatile private var sink: ((DecodeResult) -> Unit)? = null
    @Volatile private var txSink: TxSink? = null
    private var engine: NativeEngine? = null
    private var started = false

    private val callbacks = object : Callbacks {
        override fun onDecoded(
            utc: Int,
            snr: Int,
            dt: Float,
            freq: Float,
            text: String,
            type: Int,
            quality: Float,
            submode: Int,
            driftMs: Int,
        ) {
            sink?.invoke(
                DecodeResult(
                    mode = DigitalMode.JS8,
                    utcMillis = nearestUtcMillis(utc, nowMillis()),
                    audioHz = freq,
                    snrDb = snr,
                    dtSeconds = dt,
                    text = text,
                )
            )
        }

        override fun onError(message: String) {
            errorSink(message)
            runCatching { txSink?.onError(message) }
        }

        override fun onTxAudio(samples: ShortArray, sampleRateHz: Int) {
            if (sampleRateHz <= 0 || samples.isEmpty()) {
                val message = "JS8 native TX callback returned invalid PCM"
                errorSink(message)
                runCatching { txSink?.onError(message) }
                return
            }
            try {
                txSink?.onAudio(samples, sampleRateHz)
            } catch (t: Throwable) {
                val message = "JS8 TX sink failure: ${t.message ?: t::class.java.simpleName}"
                errorSink(message)
                runCatching { txSink?.onError(message) }
            }
        }
    }

    override fun start(onDecode: (DecodeResult) -> Unit) {
        synchronized(lock) {
            sink = onDecode
            val native = engine ?: factory.create(callbacks).also { engine = it }
            if (!started) {
                if (!native.start()) {
                    sink = null
                    error("JS8 native engine failed to start")
                }
                started = true
            }
        }
    }

    override fun stop() {
        synchronized(lock) {
            if (started) {
                runCatching { engine?.setTransmitReady(false) }
                runCatching { engine?.stopTransmit() }
                engine?.stop()
            }
            started = false
            sink = null
            txSink = null
        }
    }

    override fun accept12k(block: TimedPcmBlock) {
        require(block.sampleRate == 12_000) { "JS8 native RX requires continuous 12 kHz PCM" }
        val pcm = toPcm16(block.samples)
        val accepted = synchronized(lock) {
            check(started) { "JS8 native RX engine is not started" }
            checkNotNull(engine).submitAudio(pcm, block.utcStartNanos)
        }

        if (!accepted) {
            errorSink("JS8 native engine rejected RX audio block sequence=${block.sequence}")
        }
    }

    fun transmit(request: TxRequest, sink: TxSink): Boolean {
        synchronized(lock) {
            check(started) { "JS8 native engine is not started" }
            check(txSink == null) { "JS8 TX callback sink is already active" }
            val native = checkNotNull(engine)
            native.setTransmitReady(false)
            txSink = sink
            return try {
                val accepted = native.transmitMessage(request)
                if (!accepted) {
                    txSink = null
                    native.setTransmitReady(false)
                }
                accepted
            } catch (t: Throwable) {
                txSink = null
                runCatching { native.setTransmitReady(false) }
                throw t
            }
        }
    }

    fun txStatus(): TxStatus = synchronized(lock) {
        check(started) { "JS8 native engine is not started" }
        val native = checkNotNull(engine)
        TxStatus(
            sessionActive = native.isTransmitting(),
            audioActive = native.isTransmittingAudio(),
            millisecondsUntilAudio = native.txMillisecondsUntilAudio(),
        )
    }

    fun setTransmitReady(ready: Boolean) = synchronized(lock) {
        check(started) { "JS8 native engine is not started" }
        checkNotNull(engine).setTransmitReady(ready)
    }

    fun stopTransmit() = synchronized(lock) {
        val native = engine ?: return@synchronized
        runCatching { native.setTransmitReady(false) }
        runCatching { native.stopTransmit() }
        txSink = null
    }

    fun clearTxSink() {
        txSink = null
    }

    override fun close() {
        synchronized(lock) {
            if (started) {
                runCatching { engine?.setTransmitReady(false) }
                runCatching { engine?.stopTransmit() }
                runCatching { engine?.stop() }
            }
            started = false
            sink = null
            txSink = null
            engine?.close()
            engine = null
        }
    }

    companion object {
        internal fun toPcm16(samples: FloatArray): ShortArray = ShortArray(samples.size) { i ->
            val v = samples[i]
            when {
                v >= 1f -> Short.MAX_VALUE
                v <= -1f -> Short.MIN_VALUE
                else -> (v * Short.MAX_VALUE).roundToInt().toShort()
            }
        }

        internal fun fromPcm16(samples: ShortArray): FloatArray = FloatArray(samples.size) { i ->
            val v = samples[i].toInt()
            if (v < 0) v / 32768f else v / 32767f
        }

        /**
         * Convert upstream JS8 HHMMSS UTC to the closest epoch instant within
         * +/-12 hours of callback time, matching the upstream Android service.
         */
        internal fun nearestUtcMillis(utc: Int, nowMillis: Long): Long {
            val hours = utc / 10_000
            val minutes = (utc / 100) % 100
            val seconds = utc % 100
            if (hours !in 0..23 || minutes !in 0..59 || seconds !in 0..59) return nowMillis

            val now = Instant.ofEpochMilli(nowMillis).atZone(ZoneOffset.UTC)
            val date = LocalDate.of(now.year, now.monthValue, now.dayOfMonth)
            var candidate = date.atTime(hours, minutes, seconds)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()

            val twelveHours = 12L * 60L * 60L * 1000L
            val day = 24L * 60L * 60L * 1000L
            val diff = candidate - nowMillis
            if (diff > twelveHours) candidate -= day
            else if (diff < -twelveHours) candidate += day
            return candidate
        }
    }
}
