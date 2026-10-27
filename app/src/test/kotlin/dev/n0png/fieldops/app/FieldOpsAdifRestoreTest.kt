package dev.n0png.fieldops.app

import dev.n0png.fieldops.core.logbook.*
import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class FieldOpsAdifRestoreTest {
    private fun export(): String {
        val repo = InMemoryLogbookRepository()
        val logger = FastQsoLogger(repo, SequentialQsoIdSource(),
            UtcMillisClock { 1_798_000_000_000L })
        val session = OperatingSession("fixture", "N0PNG-local", "N0PNG",
            startedUtcMillis = 1_700_000_000_000L)
        logger.logManual(ManualQsoInput("K1JT", ManualQsoMode.FT4, "-07", "-14"),
            session, QsoRadioContext(14_080_000L, "FT4"))
        return AdifCodec.export(repo.all())
    }
    private fun read(s: String) =
        FieldOpsAdifRestore.read(ByteArrayInputStream(s.toByteArray(Charsets.US_ASCII)), "N0PNG")

    @Test fun exactSelfExportRestoresFtModeAndRequiresExplicitSeparateCommit() {
        val adif=export()
        val staged=read(adif)
        val q=staged.single()
        assertEquals(QsoOrigin.IMPORTED,q.origin)
        assertEquals("FT4",q.mode)
        assertEquals(14_080_000L,q.frequencyHz)
        assertEquals("-07",q.rstSent)
        assertEquals("N0PNG",q.stationCallsign)
        assertEquals(LotwUploadState.NOT_UPLOADED,q.lotwUpload)
        assertEquals(LotwConfirmationState.UNKNOWN,q.lotwConfirmation)
        val repo=InMemoryLogbookRepository()
        val ids=SequentialQsoIdSource()
        assertTrue(repo.all().isEmpty()) // opening/parsing cannot create a QSO
        assertEquals(1,FieldOpsAdifRestore.commit(staged,repo,ids))
        assertEquals(0,FieldOpsAdifRestore.commit(staged,repo,ids))
        assertEquals(1,repo.all().size)
        assertEquals("K1JT",repo.all().single().call)
        assertEquals("FT4",repo.all().single().mode)
    }

    @Test fun rejectingMismatchTruncationUnknownModeAndUntrustedConfirmationIsAtomic() {
        val adif=export()
        val invalid = listOf(
            adif.replace("N0PNG", "W0XXX"),
            adif.replace("<EOR>", ""),
            adif.replace("<MODE:3>FT4", "<MODE:3>XYZ"),
            adif.replace("<CALL:4>K1JT", "<CALL:4>AAA!"),
            adif.replace("<PROGRAMID:13>FTX1_FieldOps", "<PROGRAMID:13>Other_FieldOp"),
            adif + "<CALL:4>K1JT",
            adif.replace("14.08", "18.08"),
            "<EOH><EOR>",
        )
        val repo=InMemoryLogbookRepository()
        for (bad in invalid) {
            assertThrows("Should reject malformed/untrusted ADIF", RuntimeException::class.java) {
                FieldOpsAdifRestore.read(ByteArrayInputStream(bad.toByteArray()), "N0PNG")
            }
        }
        assertTrue(repo.all().isEmpty())
    }

    @Test fun oversizeAndInvalidUtf8AreRejectedBeforeAnyQsoExists() {
        assertThrows(IllegalArgumentException::class.java) {
            FieldOpsAdifRestore.read(ByteArrayInputStream(ByteArray(1_000_001) { 65 }), "N0PNG")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FieldOpsAdifRestore.read(ByteArrayInputStream(export().toByteArray()+0xC3.toByte()), "N0PNG")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FieldOpsAdifRestore.read(ByteArrayInputStream(export().toByteArray()), "K1ABC")
        }
    }

    @Test fun recordValidationIsAllOrNothingAndNeverTrustsLotwFields() {
        val adif=export()
        val pair=adif.replace("<EOR>", "<EOR>\n",ignoreCase=true)+
            adif.substringAfter("<EOH>\n").replace("<CALL:4>K1JT", "<CALL:4>W1AW")
        val report = FieldOpsAdifRestore.read(ByteArrayInputStream(pair.toByteArray()), "N0PNG")
        assertEquals(2, report.size)
        assertThrows(IllegalArgumentException::class.java) {
            FieldOpsAdifRestore.read(ByteArrayInputStream((pair+"<LOTW_QSL_RCVD:1>Y")
                .toByteArray()), "N0PNG")
        }
    }
}
