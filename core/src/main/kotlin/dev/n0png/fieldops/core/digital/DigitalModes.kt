package dev.n0png.fieldops.core.digital

/**
 * Mode metadata used by scheduling, radio arbitration and UI.
 * This class deliberately contains no DSP implementation.
 */
enum class DigitalMode {
    FT8,
    FT4,
    FT2,
    JS8,
    WSPR,
    LQ8,
    LQ4,
    APRS
}

enum class Js8Speed(val cycleMillis: Long) {
    SLOW(30_000),
    NORMAL(15_000),
    FAST(10_000),
    TURBO(6_000)
}

enum class Workflow {
    STRUCTURED_QSO,
    CONVERSATIONAL,
    PROPAGATION_BEACON
}

data class DigitalModeProfile(
    val mode: DigitalMode,
    val cycleMillis: Long,
    val workflow: Workflow,
    val nominalTxMillis: Long?,
    val slotPhaseMillis: Long = 0,
    val productionTarget: Boolean = true,
    val note: String = ""
)

object DigitalModes {
    fun profile(mode: DigitalMode, js8Speed: Js8Speed = Js8Speed.NORMAL): DigitalModeProfile = when (mode) {
        DigitalMode.FT8 -> DigitalModeProfile(
            mode, 15_000, Workflow.STRUCTURED_QSO, 12_640,
            note = "15 s structured weak-signal QSO"
        )
        DigitalMode.FT4 -> DigitalModeProfile(
            mode, 7_500, Workflow.STRUCTURED_QSO, 4_480,
            note = "7.5 s structured high-throughput QSO"
        )
        DigitalMode.FT2 -> DigitalModeProfile(
            mode, 3_750, Workflow.STRUCTURED_QSO, 2_240,
            note = "qFT8 parity target; very short cycle"
        )
        DigitalMode.JS8 -> DigitalModeProfile(
            mode, js8Speed.cycleMillis, Workflow.CONVERSATIONAL, null,
            note = "directed/free-text workflow; timing depends on JS8 speed"
        )
        DigitalMode.WSPR -> DigitalModeProfile(
            mode, 120_000, Workflow.PROPAGATION_BEACON, 110_600,
            slotPhaseMillis = 1_000,
            note = "even-minute propagation beacon; starts ~1 s into slot"
        )
        DigitalMode.LQ8 -> DigitalModeProfile(
            mode, 15_000, Workflow.STRUCTURED_QSO, null,
            productionTarget = false,
            note = "qFT8-specific interoperability target; codec must be independently validated"
        )
        DigitalMode.LQ4 -> DigitalModeProfile(
            mode, 7_500, Workflow.STRUCTURED_QSO, null,
            productionTarget = false,
            note = "qFT8-specific interoperability target; codec must be independently validated"
        )
        DigitalMode.APRS -> DigitalModeProfile(
            mode, 1_000, Workflow.CONVERSATIONAL, null,
            note = "asynchronous packet mode; cycle value is not used for scheduling"
        )
    }
}

/** UTC-aligned slot math shared by FT8/FT4/FT2/JS8/WSPR. */
object DigitalSlotClock {
    fun slotStartMillis(epochMillis: Long, profile: DigitalModeProfile): Long {
        val shifted = epochMillis - profile.slotPhaseMillis
        return Math.floorDiv(shifted, profile.cycleMillis) * profile.cycleMillis + profile.slotPhaseMillis
    }

    fun nextSlotMillis(epochMillis: Long, profile: DigitalModeProfile): Long =
        slotStartMillis(epochMillis, profile) + profile.cycleMillis

    /** Fraction 0.0..<1.0 through the current slot. */
    fun progress(epochMillis: Long, profile: DigitalModeProfile): Double {
        val start = slotStartMillis(epochMillis, profile)
        return (epochMillis - start).toDouble() / profile.cycleMillis.toDouble()
    }
}
