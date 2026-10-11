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

    @Test fun injectedSaveFailuresRetainOriginalCanonicalFileAndRemoveTemporaryStages() {
        val app = RuntimeEnvironment.getApplication()
        val store = OptInArchiveStore(app)
        store.enableForThisSession()
        store.deleteExplicitly()
        val original = capturedHistory()
        store.saveExplicitly(original)
        val originalBytes = store.localFileForTest().readBytes().toList()
        val replacement = original.copy(entries = original.entries.dropLast(1))
        for (fault in listOf(
            ArchiveSaveFaultPoint.AFTER_STAGED_FILE_SYNC,
            ArchiveSaveFaultPoint.ATOMIC_RENAME_UNAVAILABLE,
        )) {
            assertThrows(java.io.IOException::class.java) {
                store.saveWithInjectedFault(replacement, fault)
            }
            assertEquals("Prior archive bytes must not be replaced for $fault",
                originalBytes, store.localFileForTest().readBytes().toList())
            assertEquals("Prior canonical archive must restore unchanged for $fault",
                original, store.loadExplicitly())
            assertFalse("Staged temporary file not cleaned for $fault",
                app.noBackupFilesDir.listFiles().orEmpty().any {
                    it.name.startsWith("fieldops-stage-")
                })
        }
        assertTrue(store.deleteExplicitly())
    }

    @Test fun savedArchiveLengthGateFailsClosedBeforeReadingOversizedInput() {
        val app = RuntimeEnvironment.getApplication()
        val store = OptInArchiveStore(app)
        store.enableForThisSession()
        store.deleteExplicitly()
        store.saveExplicitly(capturedHistory())
        java.io.RandomAccessFile(store.localFileForTest(), "rw").use {
            it.setLength(PropagationArchiveStorageCodec.MAX_ENVELOPE_BYTES.toLong() + 1L)
        }
        assertThrows(IllegalArgumentException::class.java) { store.loadExplicitly() }
        assertTrue("Invalid file must not be removed or silently rewritten",
            store.localFileForTest().exists())
        assertTrue(store.deleteExplicitly())
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
}
