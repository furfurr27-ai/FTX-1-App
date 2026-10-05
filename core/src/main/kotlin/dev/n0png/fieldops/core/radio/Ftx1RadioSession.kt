package dev.n0png.fieldops.core.radio

import dev.n0png.fieldops.core.audio.TxWaveform
import dev.n0png.fieldops.core.cat.Ftx1Cat

interface CatTransport {
    fun write(command: String)
    fun query(command: String): String?
}

interface TxAudioPort {
    fun open(sampleRate: Int)
    fun write(samples: FloatArray)
    fun close()
}

fun interface DelayProvider { fun sleepMillis(ms: Long) }

/** Single guarded PTT/audio exit for every phone-generated mode. */
class Ftx1RadioSession(
    private val arbiter: RadioModeArbiter,
    private val cat: CatTransport,
    private val txAudio: TxAudioPort,
    private val delay: DelayProvider = DelayProvider { Thread.sleep(it) },
) {
    data class TxPolicy(val pttLeadMillis: Long = 100, val tailMillis: Long = 40)

    fun transmit(owner: RadioModeArbiter.Owner, waveform: TxWaveform, policy: TxPolicy = TxPolicy()) {
        when (val r = arbiter.request(owner)) {
            is RadioModeArbiter.Result.Denied -> error(r.reason)
            is RadioModeArbiter.Result.Granted -> Unit
        }
        when (val r = arbiter.beginTx(owner)) {
            is RadioModeArbiter.Result.Denied -> error(r.reason)
            is RadioModeArbiter.Result.Granted -> Unit
        }

        var audioOpen = false
        try {
            txAudio.open(waveform.sampleRate)
            audioOpen = true
            cat.write(Ftx1Cat.pttOn())
            delay.sleepMillis(policy.pttLeadMillis)
            txAudio.write(waveform.samples)
            delay.sleepMillis(policy.tailMillis)
        } finally {
            // TX0 is deliberately attempted even if audio throws.
            runCatching { cat.write(Ftx1Cat.pttOff()) }
            if (audioOpen) runCatching { txAudio.close() }
            arbiter.endTx(owner)
        }
    }

    /** Used by USB detach, foreground-service death, decoder exception, etc. */
    fun emergencyRx() {
        runCatching { cat.write(Ftx1Cat.pttOff()) }
        runCatching { txAudio.close() }
        arbiter.forceRxAndRelease()
    }
}
