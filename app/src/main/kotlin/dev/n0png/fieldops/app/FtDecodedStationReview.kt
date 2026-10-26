package dev.n0png.fieldops.app

import dev.n0png.fieldops.core.digital.DigitalMode
import dev.n0png.fieldops.core.dsp.DecodeResult
import dev.n0png.fieldops.core.logbook.ManualQsoMode
import java.util.Locale

/** Operator-selected RX hint only; no RF frequency, QSO, PTT or confirmation. */
internal data class FtDecodedStationReview(
    val callsign: String,
    val mode: DigitalMode,
) {
    val manualMode: ManualQsoMode get() = when (mode) {
        DigitalMode.FT8 -> ManualQsoMode.FT8
        DigitalMode.FT4 -> ManualQsoMode.FT4
        DigitalMode.FT2 -> ManualQsoMode.FT2
        else -> error("Not an FT-family decode")
    }

    companion object {
        private val callPattern = Regex("[A-Z0-9]{1,10}(?:/[A-Z0-9]{1,10})*")
        private val locatorPattern = Regex("[A-R]{2}[0-9]{2}(?:[A-X]{2}(?:[0-9]{2})?)?")

        fun from(result: DecodeResult, ownCallsign: String): FtDecodedStationReview? {
            if (result.mode !in setOf(DigitalMode.FT8, DigitalMode.FT4, DigitalMode.FT2)) return null
            // Never extract a call from unstructured free text or truncated display strings.
            val call = result.decodedSenderCallsign?.trim()?.uppercase(Locale.ROOT) ?: return null
            if (call.length !in 3..16 || !callPattern.matches(call) ||
                !call.any(Char::isDigit) || !call.any(Char::isLetter) ||
                locatorPattern.matches(call)) return null
            if (call == ownCallsign.trim().uppercase(Locale.ROOT)) return null
            return FtDecodedStationReview(call, result.mode)
        }
    }
}
