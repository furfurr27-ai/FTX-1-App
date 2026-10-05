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
 * FieldOps RX-only boundary around the pinned JS8Call Android native engine.
 *
 * This adapter accepts the shared continuous 12 kHz branch directly. It does
 * not expose upstream TX methods, does not own PTT and does not use a slot
 * assembler. The production binding is [Js8CallAndroidEngineFactory].
 */
class Js8EngineAdapter(
    private val factory: EngineFactory,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val errorSink: (String) -> Unit = {},
) : Streaming12kDspEngine, AutoCloseable {

    interface NativeEngine : AutoCloseable {
        fun start(): Boolean
        fun stop()
        fun submitAudio(samples: ShortArray, timestampNs: Long): Boolean
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
    }

    override val mode: DigitalMode = DigitalMode.JS8

    private val lock = Any()
    @Volatile private var sink: ((DecodeResult) -> Unit)? = null
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

        override fun onError(message: String) = errorSink(message)
    }

    override fun start(onDecode: (DecodeResult) -> Unit) {
        synchronized(lock) {
            sink = onDecode
            val native = engine ?: factory.create(callbacks).also { engine = it }
            if (!started) {
                check(native.start()) { "JS8 native engine failed to start" }
                started = true
            }
        }
    }

    override fun stop() {
        synchronized(lock) {
            if (started) engine?.stop()
            started = false
            sink = null
        }
    }

    override fun accept12k(block: TimedPcmBlock) {
        require(block.sampleRate == 12_000) { "JS8 native RX requires continuous 12 kHz PCM" }
        val native = synchronized(lock) {
            check(started) { "JS8 native RX engine is not started" }
            checkNotNull(engine)
        }

        if (!native.submitAudio(toPcm16(block.samples), block.utcStartNanos)) {
            errorSink("JS8 native engine rejected RX audio block sequence=${block.sequence}")
        }
    }

    override fun close() {
        synchronized(lock) {
            if (started) engine?.stop()
            started = false
            sink = null
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
