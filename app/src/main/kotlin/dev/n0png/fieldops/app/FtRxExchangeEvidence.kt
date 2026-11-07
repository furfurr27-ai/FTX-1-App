package dev.n0png.fieldops.app

import dev.n0png.fieldops.core.digital.DigitalMode
import dev.n0png.fieldops.core.digital.DigitalModes

/**
 * A deliberately non-authoritative FT-family receive exchange assessment.
 *
 * Offline WAV selections have no verified UTC, CAT dial frequency or actual
 * locally transmitted slot. Therefore no number/ordering of decoded files
 * can establish a reciprocal, completed QSO or authorize a TX.
 *
 * This projection does NOT own a logbook, LoTW client, radio or TX port.
 */
internal enum class FtRxReviewStage {
    HEARD_UNCLASSIFIED,
    CQ_HEARD_ONLY,
    DIRECTED_TO_OWN_CALL_HEARD,
}

internal data class FtRxExchangeEvidence(
    val callsign: String,
    val mode: DigitalMode,
    val stage: FtRxReviewStage,
    val cqMessages: Int,
    val directedMessages: Int,
    val messages: Int,
    val selectedWavFiles: Int,
    val nominalSlotMillis: Long,
    val trustedUtc: Boolean = false,
    val verifiedLocalTx: Boolean = false,
    val completedTwoWayQso: Boolean = false,
    val mayAutoLog: Boolean = false,
    val mayArmTx: Boolean = false,
) {
    val explanation: String
        get() = when (stage) {
            FtRxReviewStage.HEARD_UNCLASSIFIED ->
                "Only unclassified or other-directed RX was heard; no own-station exchange evidence."
            FtRxReviewStage.CQ_HEARD_ONLY ->
                "A CQ was heard; no verified local reply or reciprocal exchange."
            FtRxReviewStage.DIRECTED_TO_OWN_CALL_HEARD ->
                "A message addressed to the station was heard, but there is no verified " +
                    "local TX, timed exchange or completed contact."
        }
}

internal object FtRxExchangeEvidenceGate {
    fun assess(station: FtRxStationSummary, all: List<FtRxObservation>): FtRxExchangeEvidence {
        require(station.mode in setOf(DigitalMode.FT8, DigitalMode.FT4, DigitalMode.FT2)) {
            "Exchange assessment requires an FT-family mode"
        }
        val relevant = all.filter { it.callsign == station.callsign && it.mode == station.mode }
        require(relevant.isNotEmpty()) { "Cannot assess a station with no RX observations" }
        val cq = relevant.count { it.addressKind == FtRxAddressKind.CQ }
        val toOwn = relevant.count { it.addressKind == FtRxAddressKind.ADDRESSED_TO_OWN_CALL }
        val stage = when {
            toOwn > 0 -> FtRxReviewStage.DIRECTED_TO_OWN_CALL_HEARD
            cq > 0 -> FtRxReviewStage.CQ_HEARD_ONLY
            else -> FtRxReviewStage.HEARD_UNCLASSIFIED
        }
        return FtRxExchangeEvidence(
            callsign = station.callsign,
            mode = station.mode,
            stage = stage,
            cqMessages = cq,
            directedMessages = toOwn,
            messages = relevant.size,
            selectedWavFiles = relevant.map { it.importOrdinal }.distinct().size,
            nominalSlotMillis = DigitalModes.profile(station.mode).cycleMillis,
        )
    }
}
