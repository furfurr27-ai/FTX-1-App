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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExplicitEncryptedArchiveRotationTest {
    private class Keys(var key: SecretKey? = null) : EncryptedArchiveKeyProvider {
        var provisioned = 0
        override fun existingKey(): SecretKey? = key
        override fun createKeyOnExplicitSave(): SecretKey {
            provisioned++
            return KeyGenerator.getInstance("AES").apply { init(256) }
                .generateKey().also { key = it }
        }
    }
    private fun canonical(): PropagationOfflineReportArchive {
        var state = PropagationArchiveWorkspaceService.open(
            PropagationOfflineArchivePolicy(maxEntries = 4)
        )
        val pic = PropagationOperatingPictureService(
            InMemoryPropagationSnapshotStore(), InMemoryPropagationRefreshStateStore()
        ).read(PropagationProjectionQuery(1_790_000_100_000L))
        state = PropagationArchiveWorkspaceService.apply(
            state, PropagationArchiveWorkspaceAction.Capture(pic)
        )
        return state.archive
    }
    private fun setup(): Triple<OptInEncryptedArchiveStore, ExplicitEncryptedArchiveRotation, Keys> {
        val context = RuntimeEnvironment.getApplication()
        val sourceKeys = Keys()
        val rotatedKeys = Keys()
        val source = OptInEncryptedArchiveStore(context, sourceKeys)
        val rotation = ExplicitEncryptedArchiveRotation(context, source, rotatedKeys)
        source.enableForThisSession()
        rotation.enableForThisSession()
        rotation.deleteRotatedCopyExplicitly()
        source.deleteEncryptedExplicitly()
        source.saveEncryptedExplicitly(canonical())
        return Triple(source, rotation, rotatedKeys)
    }

    @Test fun explicitDifferentKeyAndFilePreserveOriginalAndRejectOverwrite() {
        val (source, rotation, keys) = setup()
        val sourceBefore = source.localEncryptedFileForTest().readBytes()
        assertEquals(0, keys.provisioned)
        val restored = rotation.rotateExplicitly()
        assertEquals(canonical(), restored)
        assertEquals(1, keys.provisioned)
        assertArrayEquals(sourceBefore, source.localEncryptedFileForTest().readBytes())
        val changed = rotation.rotatedFileForTest().readBytes()
        assertFalse(changed.contentEquals(sourceBefore))
        assertEquals(canonical(), rotation.restoreRotatedExplicitly())
        assertThrows(IllegalStateException::class.java) { rotation.rotateExplicitly() }
        assertArrayEquals(changed, rotation.rotatedFileForTest().readBytes())
        assertArrayEquals(sourceBefore, source.localEncryptedFileForTest().readBytes())
        rotation.deleteRotatedCopyExplicitly()
        assertArrayEquals(sourceBefore, source.localEncryptedFileForTest().readBytes())
        source.deleteEncryptedExplicitly()
    }

    @Test fun sourceMustBeOptedInAndAuthenticatedBeforeProvisioningNewKey() {
        val (source, rotation, keys) = setup()
        val original = source.localEncryptedFileForTest().readBytes()
        rotation.disableForThisSession()
        assertThrows(IllegalStateException::class.java) { rotation.rotateExplicitly() }
        rotation.enableForThisSession()
        source.disableForThisSession()
        assertThrows(IllegalStateException::class.java) { rotation.rotateExplicitly() }
        source.enableForThisSession()
        assertEquals(0, keys.provisioned)
        val broken = original.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        source.localEncryptedFileForTest().writeBytes(broken)
        assertThrows(Exception::class.java) { rotation.rotateExplicitly() }
        assertEquals(0, keys.provisioned)
        assertFalse(rotation.rotatedFileForTest().exists())
        assertArrayEquals(broken, source.localEncryptedFileForTest().readBytes())
        source.deleteEncryptedExplicitly()
    }

    @Test fun missingOriginalNeverFallsBackToPlaintextOrCreatesNewKey() {
        val (source, rotation, keys) = setup()
        source.deleteEncryptedExplicitly()
        assertThrows(IOException::class.java) { rotation.rotateExplicitly() }
        assertEquals(0, keys.provisioned)
        assertFalse(rotation.rotatedFileForTest().exists())
    }

    @Test fun precommitFaultsRetainSourceAndCleanStagedFiles() {
        for (stage in EncryptedRotationStage.values()) {
            val (source, rotation, keys) = setup()
            val before = source.localEncryptedFileForTest().readBytes()
            val failing = ExplicitEncryptedArchiveRotation(
                RuntimeEnvironment.getApplication(),
                source, keys,
                EncryptedRotationFault { observed ->
                    if (observed == stage) throw IOException("injected $stage")
                },
            )
            failing.enableForThisSession()
            assertThrows(IOException::class.java) { failing.rotateExplicitly() }
            assertFalse(failing.rotatedFileForTest().exists())
            assertArrayEquals(before, source.localEncryptedFileForTest().readBytes())
            assertEquals(canonical(), source.loadEncryptedExplicitly())
            assertFalse(RuntimeEnvironment.getApplication()
                .noBackupFilesDir.listFiles().orEmpty()
                .any { it.name.startsWith("fieldops-rotation-stage-") })
            rotation.deleteRotatedCopyExplicitly()
            source.deleteEncryptedExplicitly()
        }
    }

    @Test fun keyLossAndCorruptedRotatedCiphertextDoNotFallbackOrDelete() {
        val (source, rotation, keys) = setup()
        rotation.rotateExplicitly()
        val initial = rotation.rotatedFileForTest().readBytes()
        keys.key = null
        assertThrows(IOException::class.java) { rotation.restoreRotatedExplicitly() }
        assertEquals(1, keys.provisioned)
        assertArrayEquals(initial, rotation.rotatedFileForTest().readBytes())
        val wrong = Keys()
        wrong.createKeyOnExplicitSave()
        val reader = ExplicitEncryptedArchiveRotation(
            RuntimeEnvironment.getApplication(), source, wrong
        )
        reader.enableForThisSession()
        assertThrows(Exception::class.java) { reader.restoreRotatedExplicitly() }
        val invalid = initial.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        rotation.rotatedFileForTest().writeBytes(invalid)
        assertThrows(Exception::class.java) { reader.restoreRotatedExplicitly() }
        assertArrayEquals(invalid, rotation.rotatedFileForTest().readBytes())
        rotation.deleteRotatedCopyExplicitly()
        source.deleteEncryptedExplicitly()
    }

    @Test fun conflictAndOversizeNeverOverwriteAndDoNotProvisionKey() {
        val (source, rotation, keys) = setup()
        val preexisting = byteArrayOf(1,2,3,4)
        rotation.rotatedFileForTest().writeBytes(preexisting)
        assertThrows(IllegalStateException::class.java) { rotation.rotateExplicitly() }
        assertEquals(0, keys.provisioned)
        assertArrayEquals(preexisting, rotation.rotatedFileForTest().readBytes())
        rotation.rotatedFileForTest().writeBytes(
            ByteArray(AuthenticatedArchiveEnvelopeV2.MAX_ENCRYPTED_BYTES + 1)
        )
        assertThrows(IllegalArgumentException::class.java) { rotation.restoreRotatedExplicitly() }
        assertEquals(0, keys.provisioned)
        rotation.deleteRotatedCopyExplicitly()
        source.deleteEncryptedExplicitly()
    }
}
