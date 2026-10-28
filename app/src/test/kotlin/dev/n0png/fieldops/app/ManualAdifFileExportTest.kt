package dev.n0png.fieldops.app

import dev.n0png.fieldops.core.logbook.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.*
import org.junit.Test

class ManualAdifFileExportTest {
    private fun contact(
        repo: InMemoryLogbookRepository = InMemoryLogbookRepository(),
    ): Pair<QsoRecord, InMemoryLogbookRepository> {
        val session = OperatingSession("file-only", "N0PNG-local", "N0PNG", 1000L)
        val logger = FastQsoLogger(repo, SequentialQsoIdSource(), UtcMillisClock { 2000L })
        return logger.logManual(
            ManualQsoInput(call = "K1JT", mode = ManualQsoMode.FT4,
                rstSent = "-07", rstRcvd = "-14"),
            session,
            QsoRadioContext(frequencyHz = 14_080_000L, radioMode = "FT4"),
        ) to repo
    }

    @Test fun exportedSnapshotUsesCanonicalFtModeAndLeavesManualLogUnmodified() {
        val (qso, repo) = contact()
        val output = ByteArrayOutputStream()
        assertEquals(1, ManualAdifFileExport.write(listOf(qso), output))
        val adi = output.toString("UTF-8")
        assertTrue(adi.contains("<CALL:4>K1JT"))
        assertTrue(adi.contains("<MODE:3>FT4"))
        assertTrue(adi.contains("<FREQ:5>14.08"))
        assertTrue(adi.contains("<RST_SENT:3>-07"))
        assertTrue(adi.contains("<STATION_CALLSIGN:5>N0PNG"))
        assertTrue(adi.contains("<EOR>"))
        assertEquals(listOf(qso), repo.all())
        assertEquals(QsoOrigin.MANUAL, repo.all().single().origin)
    }

    @Test fun emptyAndFailingOutputNeverMutateAuthoritativeLog() {
        val (qso, repo) = contact()
        assertThrows(IllegalArgumentException::class.java) {
            ManualAdifFileExport.write(emptyList(), ByteArrayOutputStream())
        }
        val broken = object : OutputStream() {
            override fun write(b: Int): Unit = throw IOException("storage provider failed")
            override fun write(b: ByteArray, off: Int, len: Int): Unit =
                throw IOException("storage provider failed")
        }
        assertThrows(IOException::class.java) {
            ManualAdifFileExport.write(listOf(qso), broken)
        }
        assertEquals(listOf(qso), repo.all())
    }
}
