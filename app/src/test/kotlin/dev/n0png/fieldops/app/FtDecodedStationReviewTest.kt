package dev.n0png.fieldops.app

import dev.n0png.fieldops.core.digital.DigitalMode
import dev.n0png.fieldops.core.dsp.DecodeResult
import dev.n0png.fieldops.core.logbook.ManualQsoMode
import org.junit.Assert.*
import org.junit.Test

class FtDecodedStationReviewTest {
    private fun result(mode: DigitalMode = DigitalMode.FT8, from: String? = "K1JT",
                       text: String = "K1JT CQ FN20") =
        DecodeResult(mode, 0L, audioHz = 1500f, text = text, decodedSenderCallsign = from)

    @Test fun structuredSenderCanBeReviewedInItsOwnNativeMode() {
        for ((mode, manual) in listOf(DigitalMode.FT8 to ManualQsoMode.FT8,
            DigitalMode.FT4 to ManualQsoMode.FT4, DigitalMode.FT2 to ManualQsoMode.FT2)) {
            val candidate = requireNotNull(FtDecodedStationReview.from(result(mode, " k1jt "), "N0PNG"))
            assertEquals("K1JT", candidate.callsign)
            assertEquals(mode, candidate.mode)
            assertEquals(manual, candidate.manualMode)
        }
    }

    @Test fun freeTextMalformedCallGridAndOwnCallCannotBecomeCandidate() {
        assertNull(FtDecodedStationReview.from(result(from = null, text = "K1JT CQ FN20"), "N0PNG"))
        for (sender in listOf("CQ", "<...>", "RR73", "FN20", "JN58TD",
            "../K1JT", "K1JT/", "K1 J T", "N0PNG", "ABC")) {
            assertNull("Unexpected review candidate: $sender",
                FtDecodedStationReview.from(result(from = sender), "N0PNG"))
        }
        assertNull(FtDecodedStationReview.from(result(mode = DigitalMode.JS8), "N0PNG"))
    }

    @Test fun reviewContainsNoRfFrequencyOrContactProvenance() {
        val candidate = requireNotNull(FtDecodedStationReview.from(result(), "N0PNG"))
        assertEquals("K1JT", candidate.callsign)
        assertEquals(setOf("callsign", "mode"),
            FtDecodedStationReview::class.java.declaredFields
                .filter { !it.isSynthetic && !java.lang.reflect.Modifier.isStatic(it.modifiers) }
                .map { it.name }.toSet())
    }
}
