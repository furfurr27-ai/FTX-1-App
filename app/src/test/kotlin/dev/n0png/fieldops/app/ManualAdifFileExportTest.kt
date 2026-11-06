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

    @Test fun moreThan500ContactsAreSplitIntoIndependentlyRestorableParts() {
        val (qso, _) = contact()
        val snapshot = (1..501).map { index ->
            qso.copy(id = index.toLong(),
                // Dates remain valid; different UTC seconds preserve identity.
                timeOn = "%06d".format((index - 1)),
                startedUtcMillis = qso.startedUtcMillis + index * 1000L)
        }
        val parts = ManualAdifFileExport.plan(snapshot, "N0PNG")
        assertEquals(2, parts.size)
        assertEquals(500, parts[0].records.size)
        assertEquals(1, parts[1].records.size)
        val recovered = parts.flatMap { part ->
            val bytes = ByteArrayOutputStream()
            assertEquals(part.records.size, ManualAdifFileExport.write(part, bytes))
            assertTrue(bytes.size() <= FieldOpsArchiveBounds.MAX_BYTES)
            FieldOpsAdifRestore.read(java.io.ByteArrayInputStream(bytes.toByteArray()), "N0PNG")
        }
        assertEquals(501, recovered.size)
        assertEquals(LotwUploadState.NOT_UPLOADED, recovered.last().lotwUpload)
        val notWritable = ByteArrayOutputStream()
        assertThrows(IllegalArgumentException::class.java) {
            ManualAdifFileExport.write(snapshot, notWritable)
        }
        assertEquals(0, notWritable.size())
    }

    @Test fun oversizedAsciiPayloadIsSplitByByteLimitEvenBelowRecordLimit() {
        val (qso, _) = contact()
        val snapshot = (1..48).map { index ->
            qso.copy(id = index.toLong(), myCountry = "X".repeat(30_000),
                timeOn = "%06d".format(index),
                startedUtcMillis = qso.startedUtcMillis + index * 1000L)
        }
        val parts = ManualAdifFileExport.plan(snapshot, "N0PNG")
        assertTrue(parts.size >= 2)
        assertEquals(48, parts.sumOf { it.records.size })
        for (part in parts) {
            assertTrue(part.byteCount <= FieldOpsArchiveBounds.MAX_BYTES)
            val data = ByteArrayOutputStream()
            ManualAdifFileExport.write(part, data)
            assertEquals(part.records.size,
                FieldOpsAdifRestore.read(java.io.ByteArrayInputStream(data.toByteArray()),
                    "N0PNG").size)
        }
    }

    @Test fun unsupportedUnicodeAndWrongStationFailBeforeOpeningDocument() {
        val (qso, _) = contact()
        for (contact in listOf(
            qso.copy(myCountry = "München"),
            qso.copy(stationCallsign = "W1AW"),
            qso.copy(freqMhz = "", frequencyHz = 0L),
        )) {
            val output = ByteArrayOutputStream()
            assertThrows(RuntimeException::class.java) {
                ManualAdifFileExport.plan(listOf(contact), "N0PNG")
            }
            assertEquals(0, output.size())
        }
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

