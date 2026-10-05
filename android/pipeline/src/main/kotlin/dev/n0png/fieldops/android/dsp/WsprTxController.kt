package dev.n0png.fieldops.android.dsp

import dev.n0png.fieldops.core.dsp.EncodeRequest
import dev.n0png.fieldops.core.radio.Ftx1RadioSession
import dev.n0png.fieldops.core.radio.RadioModeArbiter

/**
 * Guarded WSPR complete-waveform TX state machine.
 *
 * The waveform is generated before any PTT action. Once keyed, audio is written
 * only through Ftx1RadioSession, in bounded chunks so the caller can cancel
 * between pump calls. No WSPR code has direct CAT/PTT or USB-audio access.
 */
class WsprTxController(
    private val wspr: WsprEngineAdapter,
    private val radio: Ftx1RadioSession,
    private val policy: Ftx1RadioSession.TxPolicy = Ftx1RadioSession.TxPolicy(),
    private val chunkSamples: Int = 16_384,
) : AutoCloseable {

    enum class Phase { IDLE, KEYED, AUDIO, FAILED }

    data class Snapshot(
        val phase: Phase,
        val samplesSent: Int,
        val totalSamples: Int,
        val lastFailure: String? = null,
    )

    private val lock = Any()
    private var phase = Phase.IDLE
    private var samples = FloatArray(0)
    private var offset = 0
    private var stream: Ftx1RadioSession.StreamingTx? = null
    private var lastFailure: String? = null

    init {
        require(chunkSamples > 0)
    }

    fun snapshot(): Snapshot = synchronized(lock) {
        snapshotLocked()
    }

    fun start(request: EncodeRequest): Boolean = synchronized(lock) {
        check(stream == null && samples.isEmpty()) { "WSPR TX is already active" }
        phase = Phase.IDLE
        offset = 0
        lastFailure = null

        val waveform = try {
            wspr.encode(request)
        } catch (t: Throwable) {
            phase = Phase.FAILED
            lastFailure = "wspr-encode-failed: " + (t.message ?: t::class.java.simpleName)
            return@synchronized false
        }

        val acquired = try {
            radio.beginStreamingTx(
                owner = RadioModeArbiter.Owner.WSPR,
                sampleRate = waveform.sampleRate,
                policy = policy,
            )
        } catch (t: Throwable) {
            phase = Phase.FAILED
            lastFailure = "wspr-radio-acquire-failed: " + (t.message ?: t::class.java.simpleName)
            return@synchronized false
        }

        samples = waveform.samples
        stream = acquired
        phase = Phase.KEYED
        true
    }

    fun pump(maxChunks: Int = 1): Snapshot = synchronized(lock) {
        require(maxChunks > 0)
        val active = stream ?: return@synchronized snapshotLocked()

        try {
            var chunks = 0
            while (chunks < maxChunks && offset < samples.size) {
                val end = minOf(offset + chunkSamples, samples.size)
                active.write(samples.copyOfRange(offset, end))
                offset = end
                chunks++
                phase = Phase.AUDIO
            }

            if (offset >= samples.size) {
                active.finish()
                clearActiveLocked()
                phase = Phase.IDLE
            }
        } catch (t: Throwable) {
            failLocked("wspr-tx-audio-write-failed: " + (t.message ?: t::class.java.simpleName))
        }

        snapshotLocked()
    }

    fun transmitBlocking(request: EncodeRequest): Boolean {
        if (!start(request)) return false
        while (true) {
            val state = pump(16)
            if (state.phase == Phase.IDLE) return true
            if (state.phase == Phase.FAILED) return false
        }
    }

    fun cancel() = synchronized(lock) {
        stream?.abort()
        clearActiveLocked()
        lastFailure = null
        phase = Phase.IDLE
    }

    private fun failLocked(message: String) {
        lastFailure = message
        phase = Phase.FAILED
        stream?.abort()
        clearActiveLocked(resetOffset = false)
    }

    private fun clearActiveLocked(resetOffset: Boolean = true) {
        stream = null
        samples = FloatArray(0)
        if (resetOffset) offset = 0
    }

    private fun snapshotLocked() = Snapshot(
        phase = phase,
        samplesSent = offset,
        totalSamples = if (samples.isNotEmpty()) samples.size else {
            if (phase == Phase.IDLE) 0 else WsprTxWaveformSynthesizer.TOTAL_SAMPLES
        },
        lastFailure = lastFailure,
    )

    override fun close() {
        cancel()
    }
}
