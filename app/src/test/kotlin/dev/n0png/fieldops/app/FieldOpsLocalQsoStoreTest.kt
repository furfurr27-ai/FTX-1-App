package dev.n0png.fieldops.app

import dev.n0png.fieldops.core.logbook.LotwConfirmationState
import dev.n0png.fieldops.core.logbook.LotwUploadState
import dev.n0png.fieldops.core.logbook.QsoLocationSnapshot
import dev.n0png.fieldops.core.logbook.QsoOrigin
import dev.n0png.fieldops.core.logbook.QsoRecord
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FieldOpsLocalQsoStoreTest {
    private val app: android.app.Application get() = RuntimeEnvironment.getApplication()

    @Before fun clearBefore() { app.deleteDatabase(FieldOpsLocalQsoStore.DATABASE) }
    @After fun clearAfter() { app.deleteDatabase(FieldOpsLocalQsoStore.DATABASE) }

    private fun qso(id: Long = 1): QsoRecord = QsoRecord(
        id = id,
        call = "K1JT",
        stationCallsign = "N0PNG",
        qsoDate = "20261106",
        timeOn = "120001",
        band = "20m",
        mode = "FT4",
        freqMhz = "14.08",
        grid = "FN20",
        rstSent = "-09",
        rstRcvd = "-10",
        myGridSquare = "JN49",
        myDxcc = 291,
        myCqZone = 4,
        myItuZone = 8,
        myCountry = "United States",
        myState = "NJ",
        lotwUpload = LotwUploadState.NOT_UPLOADED,
        lotwConfirmation = LotwConfirmationState.UNKNOWN,
        lotwRxQso = "2026-11-06T12:00:01Z",
        lotwRxQsl = null,
        lotwQslDate = null,
        lotwRecordId = "local-only-1",
        radioMode = "FT4",
        frequencyHz = 14_080_000L,
        startedUtcMillis = 1793966401000L,
        endedUtcMillis = 1793966410000L,
        qsoDateOff = "20261106",
        timeOff = "120010",
        stationProfileId = "N0PNG-local",
        sessionId = "session-20261106",
        remoteLocation = QsoLocationSnapshot(
            label = "Remote", gridSquare = "FN20", latitude = 40.5, longitude = -74.0),
        stationLocation = QsoLocationSnapshot(
            label = "Home", gridSquare = "JN49", latitude = 49.5, longitude = 8.5),
        origin = QsoOrigin.MANUAL,
        sourceProvider = "operator",
        notes = "Unencrypted local metadata: München",
        txPowerWatts = 10.0,
    )

    @Test fun volatileByDefaultDoesNotCreateDiskAndFreshSessionStartsEmpty() {
        val first = OptInQsoRepository(app)
        assertFalse(first.isPersistent)
        assertFalse(app.getDatabasePath(FieldOpsLocalQsoStore.DATABASE).exists())
        first.save(qso())
        assertEquals(1, first.all().size)
        first.close()
        val reopened = OptInQsoRepository(app)
        assertFalse(reopened.isPersistent)
        assertTrue(reopened.all().isEmpty())
        assertFalse(app.getDatabasePath(FieldOpsLocalQsoStore.DATABASE).exists())
        reopened.close()
    }

    @Test fun enablingAfterVolatileContactRoundTripsEveryCurrentQsoFieldAndNextId() {
        val first = OptInQsoRepository(app)
        val original = qso()
        first.save(original)
        first.enableLocalPersistence()
        assertTrue(first.isPersistent)
        assertTrue(app.getDatabasePath(FieldOpsLocalQsoStore.DATABASE).exists())
        first.save(qso(2).copy(call = "DL1ABC"))
        first.close()

        val fresh = OptInQsoRepository(app)
        assertNull(fresh.storageError)
        assertTrue(fresh.isPersistent)
        assertEquals(3L, fresh.nextRecordId)
        assertEquals(original, fresh.all().first())
        assertEquals("DL1ABC", fresh.all().last().call)
        assertEquals("München", fresh.all().first().notes!!.substringAfter(": "))
        fresh.close()
    }

    @Test fun transactionFailureDoesNotCommitAnyOfTheRestoredBatch() {
        val first = OptInQsoRepository(app)
        first.enableLocalPersistence()
        first.save(qso())
        assertThrows(RuntimeException::class.java) {
            first.saveBatch(listOf(qso(2), qso(1)))
        }
        assertEquals(1, first.all().size)
        first.close()

        val fresh = OptInQsoRepository(app)
        assertEquals(listOf(qso()), fresh.all())
        fresh.close()
    }

    @Test fun damagedStoredRecordBlocksNewWritesWithoutDestroyingDatabase() {
        val store = FieldOpsLocalQsoStore(app)
        store.enable(emptyList())
        store.writableDatabase.execSQL(
            "INSERT INTO contacts(id,payload) VALUES (1,'not-json')"
        )
        store.close()
        val file = app.getDatabasePath(FieldOpsLocalQsoStore.DATABASE)
        val loaded = OptInQsoRepository(app)
        assertNotNull(loaded.storageError)
        assertThrows(IllegalStateException::class.java) {
            loaded.save(qso(2))
        }
        loaded.close()
        assertTrue(file.exists())
        FieldOpsLocalQsoStore(app).use { opened ->
            opened.readableDatabase.rawQuery("SELECT payload FROM contacts WHERE id = 1", null)
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("not-json", cursor.getString(0))
                }
        }
    }
}
