package dev.n0png.fieldops.app

import dev.n0png.fieldops.core.propagation.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** CP-0009G real Android private-file facade, exercised by simulated JVM Android. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OptInArchiveStoreTest {
    private val service = PropagationArchiveWorkspaceService

    private fun capturedHistory(): PropagationOfflineReportArchive {
        var state = service.open(PropagationOfflineArchivePolicy(maxEntries = 4))
        for (time in listOf(1_790_000_100_000L, 1_790_000_200_000L)) {
            val picture = PropagationOperatingPictureService(
                InMemoryPropagationSnapshotStore(), InMemoryPropagationRefreshStateStore()
            ).read(PropagationProjectionQuery(time))
            state = service.apply(state, PropagationArchiveWorkspaceAction.Capture(picture))
        }
        return state.archive
    }

    @Test fun optInIsRequiredForEveryNewSessionAndRestoreIsValidated() {
        val app = RuntimeEnvironment.getApplication()
        val store = OptInArchiveStore(app)
        store.enableForThisSession()
        store.deleteExplicitly()
        store.disableForThisSession()
        assertThrows(IllegalStateException::class.java) { store.saveExplicitly(capturedHistory()) }
        assertThrows(IllegalStateException::class.java) { store.loadExplicitly() }

        store.enableForThisSession()
        val source = capturedHistory()
        store.saveExplicitly(source)
        val old = store.localFileForTest().readBytes()
        assertTrue(old.isNotEmpty())
        assertEquals(source, store.loadExplicitly())
        assertEquals(source, store.loadExplicitly())

        val other = OptInArchiveStore(app)
        assertThrows(IllegalStateException::class.java) { other.loadExplicitly() }
        other.enableForThisSession()
        assertEquals(source, other.loadExplicitly())
        assertFalse("No staged temp files may remain",
            app.noBackupFilesDir.listFiles().orEmpty().any { it.name.startsWith("fieldops-stage-") })
        assertTrue(other.deleteExplicitly())
        assertNull(other.loadExplicitly())
        assertFalse(store.localFileForTest().exists())
    }

    @Test fun invalidPrivateStoreFailsClosedWithoutInventingSavedReports() {
        val app = RuntimeEnvironment.getApplication()
        val store = OptInArchiveStore(app)
        store.enableForThisSession()
        store.deleteExplicitly()
        val archive = capturedHistory()
        store.saveExplicitly(archive)
        val bytes = store.localFileForTest().readBytes()
        store.localFileForTest().writeBytes(bytes.copyOfRange(0, bytes.size - 7))
        assertThrows(IllegalArgumentException::class.java) { store.loadExplicitly() }
        assertTrue("Failed restore must not delete/correct untrusted bytes",
            store.localFileForTest().exists())
        assertTrue(store.deleteExplicitly())
        assertNull(store.loadExplicitly())
    }
    @Test fun interruptedStagedWriteNeverReplacesPreviouslyValidArchive() {
        val app = RuntimeEnvironment.getApplication()
        val baseline = capturedHistory()
        val trimmed = baseline.copy(entries = baseline.entries.take(1))
        val original = OptInArchiveStore(app)
        original.enableForThisSession()
        assertTrue(original.deleteExplicitly())
        original.saveExplicitly(baseline)
        val bytes = original.localFileForTest().readBytes()

        for (stage in ArchivePreCommitStage.values()) {
            val crashing = OptInArchiveStore(app, ArchivePreCommitFault { observed ->
                if (observed == stage) throw java.io.IOException("synthetic $stage")
            })
            crashing.enableForThisSession()
            assertThrows(java.io.IOException::class.java) { crashing.saveExplicitly(trimmed) }
            assertArrayEquals("Existing committed bytes lost at $stage",
                bytes, original.localFileForTest().readBytes())
            assertEquals(baseline, original.loadExplicitly())
            assertFalse("A failed staged write left temporary files",
                app.noBackupFilesDir.listFiles().orEmpty().any { it.name.startsWith("fieldops-stage-") })
        }
        assertTrue(original.deleteExplicitly())
    }

    @Test fun corruptCommittedArchiveCannotBeSilentlyOverwritten() {
        val app = RuntimeEnvironment.getApplication()
        val store = OptInArchiveStore(app)
        store.enableForThisSession()
        store.deleteExplicitly()
        store.saveExplicitly(capturedHistory())
        val bytes = store.localFileForTest().readBytes()
        val corrupt = bytes.copyOf().apply { this[size / 2] = (this[size / 2].toInt() xor 1).toByte() }
        store.localFileForTest().writeBytes(corrupt)
        assertThrows(java.io.IOException::class.java) { store.saveExplicitly(capturedHistory()) }
        assertArrayEquals("Corrupt evidence must remain until explicit user delete",
            corrupt, store.localFileForTest().readBytes())
        assertThrows(IllegalArgumentException::class.java) { store.loadExplicitly() }
        assertTrue(store.deleteExplicitly())
        store.saveExplicitly(capturedHistory())
        assertEquals(capturedHistory(), store.loadExplicitly())
        assertTrue(store.deleteExplicitly())
    }

}
