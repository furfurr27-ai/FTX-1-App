package dev.n0png.fieldops.app

import dev.n0png.fieldops.core.propagation.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Android file path exercised in simulated Robolectric API35 with injectable keys. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OptInEncryptedArchiveStoreTest {
    private class TestKeys(var key: SecretKey? = null) : EncryptedArchiveKeyProvider {
        var provisions = 0
        var reads = 0
        override fun existingKey(): SecretKey? { reads++; return key }
        override fun createKeyOnExplicitSave(): SecretKey {
            provisions++
            return KeyGenerator.getInstance("AES").apply { init(256) }
                .generateKey().also { key = it }
        }
    }

    private fun fixture(): PropagationOfflineReportArchive {
        var state = PropagationArchiveWorkspaceService.open(
            PropagationOfflineArchivePolicy(maxEntries = 4)
        )
        for (time in listOf(1_790_000_100_000L, 1_790_000_200_000L)) {
            val picture = PropagationOperatingPictureService(
                InMemoryPropagationSnapshotStore(), InMemoryPropagationRefreshStateStore()
            ).read(PropagationProjectionQuery(time))
            state = PropagationArchiveWorkspaceService.apply(
                state, PropagationArchiveWorkspaceAction.Capture(picture))
        }
        return state.archive
    }

    @Test fun separateUserOptInNewCiphertextDoesNotTouchPlaintextV1() {
        val context = RuntimeEnvironment.getApplication()
        val legacy = OptInArchiveStore(context).apply { enableForThisSession() }
        legacy.deleteExplicitly()
        legacy.saveExplicitly(fixture())
        val originalV1 = legacy.localFileForTest().readBytes()
        val keys = TestKeys()
        val store = OptInEncryptedArchiveStore(context, keys)
        store.enableForThisSession()
        store.deleteEncryptedExplicitly()
        store.disableForThisSession()
        assertThrows(IllegalStateException::class.java) { store.saveEncryptedExplicitly(fixture()) }
        assertThrows(IllegalStateException::class.java) { store.loadEncryptedExplicitly() }
        assertEquals(0, keys.provisions)
        store.enableForThisSession()
        store.saveEncryptedExplicitly(fixture())
        val ciphertext = store.localEncryptedFileForTest().readBytes()
        assertTrue(ciphertext.copyOfRange(0, 8)
            .contentEquals("FTX1ENC2".toByteArray()))
        assertFalse(ciphertext.contentEquals(originalV1))
        assertArrayEquals(originalV1, legacy.localFileForTest().readBytes())
        assertEquals(1, keys.provisions)
        val anotherSession = OptInEncryptedArchiveStore(context, keys)
        assertThrows(IllegalStateException::class.java) { anotherSession.loadEncryptedExplicitly() }
        anotherSession.enableForThisSession()
        assertEquals(fixture(), anotherSession.loadEncryptedExplicitly())
        assertEquals(1, keys.provisions)
        assertTrue(anotherSession.deleteEncryptedExplicitly())
        assertArrayEquals("Removing V2 never silently deletes plaintext V1", originalV1,
            legacy.localFileForTest().readBytes())
        legacy.deleteExplicitly()
    }

    @Test fun badTagAndWrongKeyDoNotOverwriteOrFallbackToV1() {
        val context = RuntimeEnvironment.getApplication()
        val keys = TestKeys()
        val store = OptInEncryptedArchiveStore(context, keys)
        store.enableForThisSession()
        store.deleteEncryptedExplicitly()
        store.saveEncryptedExplicitly(fixture())
        val untouched = store.localEncryptedFileForTest().readBytes()
        val wrongKey = TestKeys()
        wrongKey.createKeyOnExplicitSave()
        val wrong = OptInEncryptedArchiveStore(context, wrongKey).apply { enableForThisSession() }
        assertThrows(Exception::class.java) { wrong.loadEncryptedExplicitly() }
        assertThrows(IOException::class.java) { wrong.saveEncryptedExplicitly(fixture()) }
        assertArrayEquals(untouched, store.localEncryptedFileForTest().readBytes())
        val damaged = untouched.copyOf().apply {
            this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte()
        }
        store.localEncryptedFileForTest().writeBytes(damaged)
        assertThrows(Exception::class.java) { store.loadEncryptedExplicitly() }
        assertThrows(IOException::class.java) { store.saveEncryptedExplicitly(fixture()) }
        assertArrayEquals(damaged, store.localEncryptedFileForTest().readBytes())
        store.deleteEncryptedExplicitly()
    }

    @Test fun missingKeyNeverCreatesReplacementOrDeletesExistingCiphertext() {
        val context = RuntimeEnvironment.getApplication()
        val writerKeys = TestKeys()
        val writer = OptInEncryptedArchiveStore(context, writerKeys)
        writer.enableForThisSession()
        writer.deleteEncryptedExplicitly()
        writer.saveEncryptedExplicitly(fixture())
        val bytes = writer.localEncryptedFileForTest().readBytes()
        val gone = TestKeys()
        val store = OptInEncryptedArchiveStore(context, gone).apply { enableForThisSession() }
        assertThrows(IOException::class.java) { store.loadEncryptedExplicitly() }
        assertThrows(IOException::class.java) { store.saveEncryptedExplicitly(fixture()) }
        assertEquals(0, gone.provisions)
        assertArrayEquals(bytes, writer.localEncryptedFileForTest().readBytes())
        store.deleteEncryptedExplicitly()
    }

    @Test fun simulatedPreCommitFaultPreservesPreviousEncryptedFile() {
        val context = RuntimeEnvironment.getApplication()
        val keys = TestKeys()
        val writer = OptInEncryptedArchiveStore(context, keys)
        writer.enableForThisSession()
        writer.deleteEncryptedExplicitly()
        writer.saveEncryptedExplicitly(fixture())
        val previous = writer.localEncryptedFileForTest().readBytes()
        val empty = PropagationOfflineReportArchiveService.empty(
            PropagationOfflineArchivePolicy(maxEntries = 4))
        for (stage in ArchivePreCommitStage.values()) {
            val failing = OptInEncryptedArchiveStore(context, keys,
                ArchivePreCommitFault { observed ->
                    if (observed == stage) throw IOException("simulated $stage")
                })
            failing.enableForThisSession()
            assertThrows(IOException::class.java) { failing.saveEncryptedExplicitly(empty) }
            assertArrayEquals(previous, writer.localEncryptedFileForTest().readBytes())
            assertEquals(fixture(), writer.loadEncryptedExplicitly())
            assertFalse(context.noBackupFilesDir.listFiles().orEmpty()
                .any { it.name.startsWith("fieldops-v2-stage-") })
        }
        writer.deleteEncryptedExplicitly()
    }

    @Test fun oversizedEncryptedArchiveRefusedWithoutSilentOverwrite() {
        val context = RuntimeEnvironment.getApplication()
        val keys = TestKeys()
        val store = OptInEncryptedArchiveStore(context, keys).apply { enableForThisSession() }
        store.deleteEncryptedExplicitly()
        val oversized = ByteArray(AuthenticatedArchiveEnvelopeV2.MAX_ENCRYPTED_BYTES + 1)
        try {
            store.localEncryptedFileForTest().writeBytes(oversized)
            assertThrows(IllegalArgumentException::class.java) {
                store.loadEncryptedExplicitly()
            }
            assertThrows(IllegalArgumentException::class.java) {
                store.saveEncryptedExplicitly(fixture())
            }
            assertArrayEquals(oversized, store.localEncryptedFileForTest().readBytes())
            assertEquals(0, keys.provisions)
        } finally {
            store.deleteEncryptedExplicitly()
        }
    }
}
