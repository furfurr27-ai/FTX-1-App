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

/**
 * Single guarded CAT/PTT/audio exit for every phone-generated mode.
 *
 * Complete-waveform and streaming modes share this same ownership boundary.
 * Any failure after ownership is granted collapses to RX, closes audio and
 * releases the arbiter.
 */
class Ftx1RadioSession(
    private val arbiter: RadioModeArbiter,
    private val cat: CatTransport,
    private val txAudio: TxAudioPort,
    private val delay: DelayProvider = DelayProvider { Thread.sleep(it) },
) {
    data class TxPolicy(val pttLeadMillis: Long = 100, val tailMillis: Long = 40)

    inner class StreamingTx internal constructor(
        private val owner: RadioModeArbiter.Owner,
        private val policy: TxPolicy,
    ) : AutoCloseable {
        private var closed = false

        @Synchronized
        fun write(samples: FloatArray) {
            check(!closed) { "streaming TX is closed" }
            if (samples.isEmpty()) return
            try {
                txAudio.write(samples)
            } catch (t: Throwable) {
                closed = true
                emergencyRx()
                throw t
            }
        }

        /**
         * Normal completion: apply the configured audio tail, force TX0, close
         * the shared audio port, return the arbiter to RX and release ownership.
         */
        @Synchronized
        fun finish() {
            if (closed) return
            closed = true
            var failure: Throwable? = null
            try {
                delay.sleepMillis(policy.tailMillis)
            } catch (t: Throwable) {
                failure = t
            } finally {
                cleanupOwnedTx(owner)
            }
            failure?.let { throw it }
        }

        /** Failure/cancel path: no tail, immediately force the global RX-safe state. */
        @Synchronized
        fun abort() {
            if (closed) return
            closed = true
            emergencyRx()
        }

        override fun close() = abort()
    }

    fun beginStreamingTx(
        owner: RadioModeArbiter.Owner,
        sampleRate: Int,
        policy: TxPolicy = TxPolicy(),
    ): StreamingTx {
        require(sampleRate > 0)

        when (val r = arbiter.request(owner)) {
            is RadioModeArbiter.Result.Denied -> error(r.reason)
            is RadioModeArbiter.Result.Granted -> Unit
        }
        when (val r = arbiter.beginTx(owner)) {
            is RadioModeArbiter.Result.Denied -> {
                // No RF command was issued yet. Release only the ownership we acquired.
                runCatching { arbiter.release(owner) }
                error(r.reason)
            }
            is RadioModeArbiter.Result.Granted -> Unit
        }

        try {
            txAudio.open(sampleRate)
            cat.write(Ftx1Cat.pttOn())
            delay.sleepMillis(policy.pttLeadMillis)
            return StreamingTx(owner, policy)
        } catch (t: Throwable) {
            emergencyRx()
            throw t
        }
    }

    fun transmit(owner: RadioModeArbiter.Owner, waveform: TxWaveform, policy: TxPolicy = TxPolicy()) {
        val stream = beginStreamingTx(owner, waveform.sampleRate, policy)
        try {
            stream.write(waveform.samples)
            stream.finish()
        } catch (t: Throwable) {
            stream.abort()
            throw t
        }
    }

    private fun cleanupOwnedTx(owner: RadioModeArbiter.Owner) {
        // TX0 is deliberately attempted even if tail/audio cleanup throws.
        runCatching { cat.write(Ftx1Cat.pttOff()) }
        runCatching { txAudio.close() }

        val ended = arbiter.endTx(owner)
        if (ended is RadioModeArbiter.Result.Granted) {
            val released = arbiter.release(owner)
            if (released is RadioModeArbiter.Result.Denied) {
                arbiter.forceRxAndRelease()
            }
        } else {
            arbiter.forceRxAndRelease()
        }
    }

    /** Used by USB detach, foreground-service death, modem exception, etc. */
    fun emergencyRx() {
        runCatching { cat.write(Ftx1Cat.pttOff()) }
        runCatching { txAudio.close() }
        arbiter.forceRxAndRelease()
    }
}
