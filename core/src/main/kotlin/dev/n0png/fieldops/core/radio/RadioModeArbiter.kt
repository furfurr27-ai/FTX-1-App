package dev.n0png.fieldops.core.radio

/**
 * Prevents any digital mode or phone-side APRS from fighting for the same
 * FTX-1 USB audio/PTT path. Hardware commands are deliberately outside this class.
 */
class RadioModeArbiter {
    enum class Owner { NONE, FT8, FT4, FT2, JS8, WSPR, LQ8, LQ4, APRS }
    enum class Tx { RX, TRANSMITTING }
    data class State(val owner: Owner = Owner.NONE, val tx: Tx = Tx.RX)

    sealed interface Result {
        data class Granted(val state: State) : Result
        data class Denied(val state: State, val reason: String) : Result
    }

    private var state = State()
    @Synchronized fun snapshot(): State = state

    @Synchronized fun request(owner: Owner): Result {
        require(owner != Owner.NONE)
        if (state.tx == Tx.TRANSMITTING) {
            return Result.Denied(state, "radio-already-transmitting")
        }
        state = state.copy(owner = owner)
        return Result.Granted(state)
    }

    @Synchronized fun beginTx(owner: Owner): Result {
        if (state.owner != owner) return Result.Denied(state, "radio-not-owned-by-requester")
        if (state.tx == Tx.TRANSMITTING) return Result.Denied(state, "already-transmitting")
        state = state.copy(tx = Tx.TRANSMITTING)
        return Result.Granted(state)
    }

    @Synchronized fun endTx(owner: Owner): Result {
        if (state.owner != owner) return Result.Denied(state, "radio-not-owned-by-requester")
        state = state.copy(tx = Tx.RX)
        return Result.Granted(state)
    }

    @Synchronized fun release(owner: Owner): Result {
        if (state.owner != owner) return Result.Denied(state, "radio-not-owned-by-requester")
        if (state.tx == Tx.TRANSMITTING) {
            return Result.Denied(state, "cannot-release-radio-while-transmitting")
        }
        state = State()
        return Result.Granted(state)
    }

    /** Emergency software-side release. Caller must separately send CAT TX0. */
    @Synchronized fun forceRxAndRelease(): State {
        state = State()
        return state
    }
}
