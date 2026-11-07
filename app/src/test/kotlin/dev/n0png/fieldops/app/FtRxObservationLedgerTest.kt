package dev.n0png.fieldops.app

import dev.n0png.fieldops.core.digital.DigitalMode
import dev.n0png.fieldops.core.dsp.DecodeResult
import org.junit.Assert.*
import org.junit.Test

class FtRxObservationLedgerTest {
    private fun rx(mode: DigitalMode = DigitalMode.FT8,
                   sender: String? = "K1JT", recipient: String? = null,
                   text: String = "decoder message") =
        DecodeResult(mode, 0L, audioHz = 1510f, snrDb = -9, text = text,
            decodedSenderCallsign = sender, decodedRecipientCallsign = recipient)

    @Test fun onlyNativeStructuredSenderAndRecipientArePromotedToRxEvidence() {
        val board = FtRxObservationLedger("N0PNG")
        board.switchMode(DigitalMode.FT8)
        board.acceptFile(DigitalMode.FT8, listOf(
            rx(sender = null, text = "K1JT N0PNG -11"), // free text MUST NOT become identity
            rx(recipient = "CQ", text = "K1JT CQ FN20"),
            rx(recipient = "n0png", text = "K1JT N0PNG -11"),
            rx(recipient = "DL1ABC", text = "K1JT DL1ABC R-09"),
            rx(recipient = "<...>", text = "K1JT <...> 73"),
            rx(sender = "N0PNG", recipient = "K1JT"),
            rx(mode = DigitalMode.FT4, recipient = "N0PNG"),
        ))
        val station = board.stations().single()
        assertEquals("K1JT", station.callsign)
        assertEquals(DigitalMode.FT8, station.mode)
        assertEquals(4, station.messages)
        assertEquals(1, station.cqMessages)
        assertEquals(1, station.addressedToOwnCall)
        board.focus("K1JT")
        val history = board.focusedObservations()
        assertEquals(4, history.size)
        assertEquals(FtRxAddressKind.CQ, history[0].addressKind)
        assertEquals(FtRxAddressKind.ADDRESSED_TO_OWN_CALL, history[1].addressKind)
        assertEquals("N0PNG", history[1].recipient)
        assertEquals(FtRxAddressKind.ADDRESSED_TO_OTHER, history[2].addressKind)
        assertEquals(FtRxAddressKind.UNKNOWN, history[3].addressKind)
        assertNull(history[3].recipient)
        assertThrows(IllegalArgumentException::class.java) { board.focus("W1AW") }
    }

    @Test fun duplicateWithinOneFileDoesNotCountAsIndependentExchange() {
        val board = FtRxObservationLedger("N0PNG")
        board.switchMode(DigitalMode.FT4)
        val result = rx(DigitalMode.FT4, recipient = "CQ")
        board.acceptFile(DigitalMode.FT4, listOf(result, result))
        assertEquals(1, board.stations().single().messages)
        board.acceptFile(DigitalMode.FT4, listOf(result))
        assertEquals(2, board.stations().single().messages)
        assertEquals(2, board.stations().single().lastImportOrdinal)
        // Two separately selected files are NOT independently timed RF slots.
    }

    @Test fun modeSwitchClearsOnlyUnverifiedReceiveHistory() {
        val board = FtRxObservationLedger("N0PNG")
        board.switchMode(DigitalMode.FT2)
        board.acceptFile(DigitalMode.FT2, listOf(rx(DigitalMode.FT2, recipient = "CQ")))
        board.focus("K1JT")
        board.switchMode(DigitalMode.FT8)
        assertTrue(board.stations().isEmpty())
        assertNull(board.focusedCallsign)
        assertTrue(board.focusedObservations().isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            board.acceptFile(DigitalMode.FT2, listOf(rx(DigitalMode.FT2)))
        }
        board.acceptFile(DigitalMode.FT8, listOf(rx(DigitalMode.FT8, recipient = "CQ")))
        assertEquals(1, board.stations().single().lastImportOrdinal)
    }

    @Test fun manyWavFilesRemainBoundedAndNeverTurnIntoQsoRecords() {
        val board = FtRxObservationLedger("N0PNG")
        board.switchMode(DigitalMode.FT8)
        repeat(110) {
            board.acceptFile(DigitalMode.FT8, listOf(rx(recipient = "N0PNG",
                text = "K1JT N0PNG -${it % 30}")))
        }
        board.focus("K1JT")
        assertEquals(80, board.stations().single().messages)
        assertEquals(8, board.focusedObservations().size)
        assertEquals(110, board.stations().single().lastImportOrdinal)
        // The ledger does not even have an injected QSO repository or TX handle.
        assertEquals(setOf("ownCallsign", "allowedModes", "stationCall", "stationPattern",
                "activeMode", "observations", "importCount", "focusedCallsign"),
            FtRxObservationLedger::class.java.declaredFields.filter {
                !it.isSynthetic && !java.lang.reflect.Modifier.isStatic(it.modifiers)
            }.map { it.name }.toSet())
    }
}
