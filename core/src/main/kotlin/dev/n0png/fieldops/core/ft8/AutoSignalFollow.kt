package dev.n0png.fieldops.core.ft8

/**
 * Pure FT8 TX-audio follow policy. It never moves TX while transmitting.
 * The UI/decoder decides which decoded station is the currently followed target.
 */
class AutoSignalFollow(
    private val minTxAudioHz: Int = 300,
    private val maxTxAudioHz: Int = 2700
) {
    init { require(minTxAudioHz in 0..5000 && maxTxAudioHz > minTxAudioHz) }

    data class State(
        val currentTxAudioHz: Int,
        val holdTxFrequency: Boolean,
        val transmitting: Boolean
    )

    data class Decision(val txAudioHz: Int, val moved: Boolean, val reason: String)

    fun follow(state: State, decodedAudioHz: Int): Decision {
        if (state.holdTxFrequency) return Decision(state.currentTxAudioHz, false, "hold-enabled")
        if (state.transmitting) return Decision(state.currentTxAudioHz, false, "tx-active")
        if (decodedAudioHz !in minTxAudioHz..maxTxAudioHz) {
            return Decision(state.currentTxAudioHz, false, "target-outside-safe-window")
        }
        if (decodedAudioHz == state.currentTxAudioHz) return Decision(decodedAudioHz, false, "already-aligned")
        return Decision(decodedAudioHz, true, "follow-target")
    }
}
