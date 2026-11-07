package dev.n0png.fieldops.app

import dev.n0png.fieldops.core.digital.DigitalMode
import dev.n0png.fieldops.core.dsp.DecodeResult
import org.junit.Assert.*
import org.junit.Test

/** Only source-derived receive evidence; never a two-way QSO or TX proof. */
class FtRxExchangeEvidenceTest {
    private fun dec(mode: DigitalMode, sender: String? = "K1JT",
                    recipient: String? = null) =
        DecodeResult(mode, 0L, text = "not independently verified",
            decodedSenderCallsign = sender, decodedRecipientCallsign = recipient)

    @Test fun allModesUseTheirOwnSlotAndNeverAcceptOfflineRxAsCompletion() {
        for ((mode, cycle) in listOf(DigitalMode.FT8 to 15_000L,
            DigitalMode.FT4 to 7_500L, DigitalMode.FT2 to 3_750L)) {
            val ledger = FtRxObservationLedger("N0PNG")
            ledger.switchMode(mode)
            ledger.acceptFile(mode, listOf(
                dec(mode, recipient = "CQ"),
                dec(mode, recipient = "N0PNG")
            ))
            val station = ledger.stations().single()
            val state = FtRxExchangeEvidenceGate.assess(station, ledger.observationsFor("K1JT"))
            assertEquals(mode, state.mode)
            assertEquals(cycle, state.nominalSlotMillis)
            assertEquals(FtRxReviewStage.DIRECTED_TO_OWN_CALL_HEARD, state.stage)
            assertEquals(2, state.messages)
            assertEquals(1, state.selectedWavFiles)
            assertEquals(1, state.directedMessages)
            assertFalse(state.trustedUtc)
            assertFalse(state.verifiedLocalTx)
            assertFalse(state.completedTwoWayQso)
            assertFalse(state.mayAutoLog)
            assertFalse(state.mayArmTx)
        }
    }

    @Test fun cqWithoutDirectedReplyIsAnObservationNotAnExchange() {
        val ledger = FtRxObservationLedger("N0PNG")
        ledger.switchMode(DigitalMode.FT8)
        ledger.acceptFile(DigitalMode.FT8, listOf(dec(DigitalMode.FT8, recipient = "CQ")))
        val s = FtRxExchangeEvidenceGate.assess(
            ledger.stations().single(), ledger.observationsFor("K1JT"))
        assertEquals(FtRxReviewStage.CQ_HEARD_ONLY, s.stage)
        assertTrue(s.explanation.contains("CQ"))
        assertFalse(s.completedTwoWayQso)
    }

    @Test fun replayedFilesCannotImplySlotOrderOrAuthorizedTransmission() {
        val ledger = FtRxObservationLedger("N0PNG")
        ledger.switchMode(DigitalMode.FT4)
        repeat(3) {
            ledger.acceptFile(DigitalMode.FT4, listOf(
                dec(DigitalMode.FT4, recipient = "N0PNG")))
        }
        val s = FtRxExchangeEvidenceGate.assess(
            ledger.stations().single(), ledger.observationsFor("K1JT"))
        assertEquals(3, s.selectedWavFiles)
        assertEquals(3, s.directedMessages)
        assertFalse(s.trustedUtc)
        assertFalse(s.verifiedLocalTx)
        assertFalse(s.mayAutoLog)
        assertFalse(s.mayArmTx)
    }

    @Test fun missingStructuredSenderCannotBeAssessedAndForeignModeRejected() {
        val ledger = FtRxObservationLedger("N0PNG")
        ledger.switchMode(DigitalMode.FT2)
        ledger.acceptFile(DigitalMode.FT2, listOf(dec(DigitalMode.FT2,
            sender = null, recipient = "N0PNG")))
        assertTrue(ledger.stations().isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            FtRxExchangeEvidenceGate.assess(FtRxStationSummary(
                callsign = "K1JT", mode = DigitalMode.JS8,
                messages = 1, cqMessages = 1, addressedToOwnCall = 0,
                lastImportOrdinal = 1), emptyList())
        }
    }

    @Test fun ownOrOtherDirectedAloneDoesNotAllowAssumedCompletion() {
        val ledger = FtRxObservationLedger("N0PNG")
        ledger.switchMode(DigitalMode.FT8)
        ledger.acceptFile(DigitalMode.FT8, listOf(
            dec(DigitalMode.FT8, recipient = "DL1ABC")))
        val stage = FtRxExchangeEvidenceGate.assess(
            ledger.stations().single(), ledger.observationsFor("K1JT"))
        assertEquals(FtRxReviewStage.HEARD_UNCLASSIFIED, stage.stage)
        assertTrue(stage.explanation.contains("other-directed"))
    }
}
