package dev.n0png.fieldops.app

import dev.n0png.fieldops.core.digital.DigitalMode
import dev.n0png.fieldops.core.dsp.DecodeResult
import java.util.Locale

/**
 * Reviewable FT8/FT4/FT2 receiver evidence from operator-selected WAV files.
 *
 * Import order is not RF chronology. The file format carries no trusted
 * absolute UTC. These observations NEVER imply two-way QSO completion,
 * station authenticity, transmitter state or a real RF dial frequency.
 */
internal enum class FtRxAddressKind { CQ, ADDRESSED_TO_OWN_CALL, ADDRESSED_TO_OTHER, UNKNOWN }

internal data class FtRxObservation(
    val importOrdinal: Int,
    val mode: DigitalMode,
    val callsign: String,
    val recipient: String?,
    val addressKind: FtRxAddressKind,
    val text: String,
    val audioHz: Float?,
    val snrDb: Int?,
)

internal data class FtRxStationSummary(
    val callsign: String,
    val mode: DigitalMode,
    val messages: Int,
    val cqMessages: Int,
    val addressedToOwnCall: Int,
    val lastImportOrdinal: Int,
)

internal class FtRxObservationLedger(private val ownCallsign: String) {
    private val allowedModes = setOf(DigitalMode.FT8, DigitalMode.FT4, DigitalMode.FT2)
    private val stationCall = ownCallsign.trim().uppercase(Locale.ROOT)
    private val stationPattern = Regex("[A-Z0-9]{1,10}(?:/[A-Z0-9]{1,10})*")

    private var activeMode: DigitalMode? = null
    private val observations = ArrayList<FtRxObservation>()
    private var importCount = 0
    var focusedCallsign: String? = null
        private set

    fun switchMode(mode: DigitalMode) {
        if (activeMode != mode) {
            activeMode = mode
            observations.clear()
            importCount = 0
            focusedCallsign = null
        }
    }

    fun acceptFile(mode: DigitalMode, results: List<DecodeResult>) {
        require(mode == activeMode && mode in allowedModes) {
            "Offline FT observation mode must match the selected mode"
        }
        // A second file can be opened with the same decoder, but its order in
        // the file picker is NOT a slot time and cannot prove a conversation.
        importCount++
        val distinctInFile = HashSet<String>()
        for (result in results.take(40)) {
            if (result.mode != mode) continue
            val review = FtDecodedStationReview.from(result, stationCall) ?: continue
            val recipient = result.decodedRecipientCallsign?.trim()
                ?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
            val address = when {
                recipient == "CQ" -> FtRxAddressKind.CQ
                recipient == stationCall -> FtRxAddressKind.ADDRESSED_TO_OWN_CALL
                recipient != null && stationPattern.matches(recipient) &&
                    recipient.any(Char::isDigit) &&
                    recipient.any(Char::isLetter) -> FtRxAddressKind.ADDRESSED_TO_OTHER
                else -> FtRxAddressKind.UNKNOWN
            }
            val name = if (address == FtRxAddressKind.UNKNOWN) null else recipient
            val message = result.text.take(180)
            val key = listOf(review.callsign, name.orEmpty(), message,
                result.audioHz?.toString().orEmpty()).joinToString("|")
            if (!distinctInFile.add(key)) continue
            observations.add(FtRxObservation(importCount, mode, review.callsign,
                name, address, message, result.audioHz, result.snrDb))
        }
        // Bounded, volatile review; no permanent heard station database.
        if (observations.size > 80) {
            observations.subList(0, observations.size - 80).clear()
        }
        if (focusedCallsign != null &&
            observations.none { it.callsign == focusedCallsign }) focusedCallsign = null
    }

    fun stations(): List<FtRxStationSummary> =
        observations.groupBy { it.callsign }.map { (call, entries) ->
            FtRxStationSummary(
                call, entries.last().mode, entries.size,
                entries.count { it.addressKind == FtRxAddressKind.CQ },
                entries.count { it.addressKind == FtRxAddressKind.ADDRESSED_TO_OWN_CALL },
                entries.last().importOrdinal,
            )
        }.sortedWith(compareByDescending<FtRxStationSummary> { it.lastImportOrdinal }
            .thenBy { it.callsign })

    fun focus(callsign: String) {
        require(observations.any { it.callsign == callsign }) {
            "Cannot inspect an unobserved station"
        }
        focusedCallsign = callsign
    }

    fun focusedObservations(): List<FtRxObservation> =
        observations.filter { it.callsign == focusedCallsign }.takeLast(8)
}
