package dev.n0png.fieldops.android.dsp

import dev.n0png.fieldops.core.audio.StreamingLinearResampler
import dev.n0png.fieldops.core.radio.Ftx1RadioSession
import dev.n0png.fieldops.core.radio.RadioModeArbiter

/**
 * JS8 TX state machine. The native engine schedules/modulates JS8 and emits PCM;
 * this controller alone bridges those chunks into FieldOps' guarded radio path.
 *
 * Call [poll] from the eventual foreground/service scheduler while a request is
 * active. No native/upstream rig control is used.
 */
class Js8TxController(
    private val js8: Js8EngineAdapter,
    private val radio: Ftx1RadioSession,
    private val outputSampleRate: Int = 48_000,
    private val policy: Ftx1RadioSession.TxPolicy = Ftx1RadioSession.TxPolicy(),
    private val prekeyMarginMillis: Int = 25,
) : AutoCloseable {

    enum class Phase { IDLE, QUEUED, KEYED, AUDIO, FAILED }

    data class Snapshot(
        val phase: Phase,
        val lastFailure: String? = null,
        val inputSampleRate: Int? = null,
    )

    private val lock = Any()
    private var phase = Phase.IDLE
    private var lastFailure: String? = null
    private var request: Js8EngineAdapter.TxRequest? = null
    private var stream: Ftx1RadioSession.StreamingTx? = null
    private var resampler: StreamingLinearResampler? = null
    private var inputSampleRate: Int? = null

    private val txSink = object : Js8EngineAdapter.TxSink {
        override fun onAudio(samples: ShortArray, sampleRateHz: Int) {
            synchronized(lock) {
                if (request == null) return
                val active = stream
                if (active == null) {
                    failLocked("native-js8-audio-before-fieldops-ptt")
                    return
                }

                try {
                    val rate = inputSampleRate
                    if (rate == null) {
                        inputSampleRate = sampleRateHz
                        resampler = StreamingLinearResampler(sampleRateHz, outputSampleRate)
                    } else if (rate != sampleRateHz) {
                        error("JS8 TX sample rate changed from $rate to $sampleRateHz")
                    }

                    val adapted = resampler!!.process(Js8EngineAdapter.fromPcm16(samples))
                    if (adapted.isNotEmpty()) active.write(adapted)
                    phase = Phase.AUDIO
                } catch (t: Throwable) {
                    failLocked("js8-tx-audio-write-failed: ${t.message ?: t::class.java.simpleName}")
                }
            }
        }

        override fun onError(message: String) {
            synchronized(lock) {
                if (request != null) failLocked("js8-native-error: $message")
            }
        }
    }

    init {
        require(outputSampleRate > 0)
        require(prekeyMarginMillis >= 0)
    }

    fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(phase, lastFailure, inputSampleRate)
    }

    fun queue(request: Js8EngineAdapter.TxRequest): Boolean = synchronized(lock) {
        check(this.request == null && stream == null) { "JS8 TX is already active" }
        lastFailure = null
        inputSampleRate = null
        resampler = null
        this.request = request
        phase = Phase.QUEUED

        try {
            val accepted = js8.transmit(request, txSink)
            if (!accepted) {
                this.request = null
                phase = Phase.FAILED
                lastFailure = "js8-native-rejected-transmit"
            }
            accepted
        } catch (t: Throwable) {
            this.request = null
            phase = Phase.FAILED
            lastFailure = "js8-native-transmit-exception: ${t.message ?: t::class.java.simpleName}"
            runCatching { js8.setTransmitReady(false) }
            runCatching { radio.emergencyRx() }
            false
        }
    }

    /**
     * Progress queued TX. PTT is asserted only shortly before native audio is
     * due, then the native transmit gate is opened after FieldOps' PTT lead.
     */
    fun poll(): Snapshot = synchronized(lock) {
        if (request == null) return@synchronized Snapshot(phase, lastFailure, inputSampleRate)

        try {
            val status = js8.txStatus()
            if (!status.sessionActive) {
                completeLocked()
                return@synchronized Snapshot(phase, lastFailure, inputSampleRate)
            }

            if (stream == null) {
                if (status.audioActive) {
                    failLocked("native-js8-audio-active-before-fieldops-ptt")
                    return@synchronized Snapshot(phase, lastFailure, inputSampleRate)
                }

                val threshold = policy.pttLeadMillis + prekeyMarginMillis
                if (status.millisecondsUntilAudio in 0..threshold.toInt()) {
                    stream = radio.beginStreamingTx(
                        owner = RadioModeArbiter.Owner.JS8,
                        sampleRate = outputSampleRate,
                        policy = policy,
                    )
                    js8.setTransmitReady(true)
                    phase = Phase.KEYED
                }
            } else if (status.audioActive) {
                phase = Phase.AUDIO
            }
        } catch (t: Throwable) {
            failLocked("js8-tx-poll-failed: ${t.message ?: t::class.java.simpleName}")
        }

        Snapshot(phase, lastFailure, inputSampleRate)
    }

    fun cancel() = synchronized(lock) {
        runCatching { js8.setTransmitReady(false) }
        runCatching { js8.stopTransmit() }
        stream?.abort() ?: radio.emergencyRx()
        js8.clearTxSink()
        clearActiveLocked()
        phase = Phase.IDLE
    }

    private fun completeLocked() {
        try {
            val tail = resampler?.flushHold()
            if (tail != null && tail.isNotEmpty()) stream?.write(tail)
            stream?.finish()
            runCatching { js8.setTransmitReady(false) }
            js8.clearTxSink()
            clearActiveLocked()
            phase = Phase.IDLE
        } catch (t: Throwable) {
            failLocked("js8-tx-finish-failed: ${t.message ?: t::class.java.simpleName}")
        }
    }

    private fun failLocked(message: String) {
        lastFailure = message
        phase = Phase.FAILED
        runCatching { js8.setTransmitReady(false) }
        runCatching { js8.stopTransmit() }
        stream?.abort() ?: radio.emergencyRx()
        js8.clearTxSink()
        clearActiveLocked()
    }

    private fun clearActiveLocked() {
        request = null
        stream = null
        resampler = null
        inputSampleRate = null
    }

    override fun close() {
        cancel()
    }
}
