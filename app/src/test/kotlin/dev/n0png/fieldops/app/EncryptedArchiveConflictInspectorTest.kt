package dev.n0png.fieldops.app

import dev.n0png.fieldops.core.propagation.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Synthetic JVM/Robolectric evidence only; NOT target device Keystore proof. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EncryptedArchiveConflictInspectorTest {
    private class Keys(var key: SecretKey? = null) : EncryptedArchiveKeyProvider {
        var created = 0
        override fun existingKey(): SecretKey? = key
        override fun createKeyOnExplicitSave(): SecretKey {
            created++
            return KeyGenerator.getInstance("AES").apply { init(256) }
                .generateKey().also { key = it }
        }
    }

    private fun archive(utc: Long): PropagationOfflineReportArchive {
        var state = PropagationArchiveWorkspaceService.open(
            PropagationOfflineArchivePolicy(maxEntries = 4)
        )
        val picture = PropagationOperatingPictureService(
            InMemoryPropagationSnapshotStore(), InMemoryPropagationRefreshStateStore()
        ).read(PropagationProjectionQuery(utc))
        state = PropagationArchiveWorkspaceService.apply(
            state, PropagationArchiveWorkspaceAction.Capture(picture)
        )
        return state.archive
    }

    private data class Fixture(
        val original: OptInEncryptedArchiveStore,
        val rotated: ExplicitEncryptedArchiveRotation,
        val oldKeys: Keys,
        val newKeys: Keys,
    ) {
        fun inspect() = EncryptedArchiveConflictInspector(original, rotated).inspectExplicitly()
        fun clean() {
            original.enableForThisSession()
            rotated.enableForThisSession()
            rotated.deleteRotatedCopyExplicitly()
            original.deleteEncryptedExplicitly()
        }
    }

    private fun fixture(): Fixture {
        val context = RuntimeEnvironment.getApplication()
        val oldKeys = Keys()
        val newKeys = Keys()
        val original = OptInEncryptedArchiveStore(context, oldKeys)
        val rotated = ExplicitEncryptedArchiveRotation(context, original, newKeys)
        val result = Fixture(original, rotated, oldKeys, newKeys)
        result.clean()
        original.saveEncryptedExplicitly(archive(1_790_000_100_000L))
        rotated.rotateExplicitly()
        return result
    }

    @Test fun equalAuthenticatedCandidatesAreNotFreshnessOrRetirementProof() {
        val f = fixture()
        try {
            val oldBytes = f.original.localEncryptedFileForTest().readBytes()
            val newBytes = f.rotated.rotatedFileForTest().readBytes()
            val oldCreated = f.oldKeys.created
            val newCreated = f.newKeys.created
            val report = f.inspect()
            assertEquals(ArchiveCandidateEvidence.AUTHENTICATED, report.original.evidence)
            assertEquals(ArchiveCandidateEvidence.AUTHENTICATED, report.rotated.evidence)
            assertEquals(ArchiveCandidateRelationship.MATCHING_AUTHENTICATED, report.relationship)
            assertEquals(report.original.canonicalSha256, report.rotated.canonicalSha256)
            assertEquals(1, report.original.entryCount)
            assertEquals("BLOCKED_NON_EXECUTING", report.oldKeyRetirement)
            assertEquals("FRESHNESS NOT VERIFIED", report.freshness)
            assertEquals(oldCreated, f.oldKeys.created)
            assertEquals(newCreated, f.newKeys.created)
            assertArrayEquals(oldBytes, f.original.localEncryptedFileForTest().readBytes())
            assertArrayEquals(newBytes, f.rotated.rotatedFileForTest().readBytes())
        } finally { f.clean() }
    }

    @Test fun conflictingAuthenticatedCanonicalCopiesAreNeitherAutoSelectedNorRewritten() {
        val f = fixture()
        try {
            val rotatedBytes = f.rotated.rotatedFileForTest().readBytes()
            f.original.saveEncryptedExplicitly(archive(1_790_001_200_000L))
            val originalBytes = f.original.localEncryptedFileForTest().readBytes()
            val report = f.inspect()
            assertEquals(ArchiveCandidateRelationship.CONFLICTING_AUTHENTICATED, report.relationship)
            assertEquals(ArchiveCandidateEvidence.AUTHENTICATED, report.original.evidence)
            assertEquals(ArchiveCandidateEvidence.AUTHENTICATED, report.rotated.evidence)
            assertNotEquals(report.original.canonicalSha256, report.rotated.canonicalSha256)
            assertEquals("BLOCKED_NON_EXECUTING", report.oldKeyRetirement)
            assertArrayEquals(originalBytes, f.original.localEncryptedFileForTest().readBytes())
            assertArrayEquals(rotatedBytes, f.rotated.rotatedFileForTest().readBytes())
        } finally { f.clean() }
    }

    @Test fun missingCandidateAndAbsentBothRemainNonDestructive() {
        val f = fixture()
        try {
            f.rotated.deleteRotatedCopyExplicitly()
            var r = f.inspect()
            assertEquals(ArchiveCandidateEvidence.MISSING, r.rotated.evidence)
            assertEquals(ArchiveCandidateEvidence.AUTHENTICATED, r.original.evidence)
            assertEquals(ArchiveCandidateRelationship.INDETERMINATE, r.relationship)
            f.original.deleteEncryptedExplicitly()
            r = f.inspect()
            assertEquals(ArchiveCandidateEvidence.MISSING, r.original.evidence)
            assertEquals(ArchiveCandidateEvidence.MISSING, r.rotated.evidence)
            assertEquals(ArchiveCandidateRelationship.BOTH_MISSING, r.relationship)
            assertEquals(1, f.oldKeys.created)
            assertEquals(1, f.newKeys.created)
        } finally { f.clean() }
    }

    @Test fun missingKeysAreDistinguishedWithoutKeyProvisionOrFallback() {
        val f = fixture()
        try {
            val oldBytes = f.original.localEncryptedFileForTest().readBytes()
            val rotatedBytes = f.rotated.rotatedFileForTest().readBytes()
            f.oldKeys.key = null
            var r = f.inspect()
            assertEquals(ArchiveCandidateEvidence.KEY_UNAVAILABLE, r.original.evidence)
            assertEquals(ArchiveCandidateEvidence.AUTHENTICATED, r.rotated.evidence)
            assertEquals(ArchiveCandidateRelationship.INDETERMINATE, r.relationship)
            f.newKeys.key = null
            r = f.inspect()
            assertEquals(ArchiveCandidateEvidence.KEY_UNAVAILABLE, r.original.evidence)
            assertEquals(ArchiveCandidateEvidence.KEY_UNAVAILABLE, r.rotated.evidence)
            assertEquals(1, f.oldKeys.created)
            assertEquals(1, f.newKeys.created)
            assertArrayEquals(oldBytes, f.original.localEncryptedFileForTest().readBytes())
            assertArrayEquals(rotatedBytes, f.rotated.rotatedFileForTest().readBytes())
        } finally { f.clean() }
    }

    @Test fun tamperedRotatedAndOriginalAreUnreadableNotMissingOrAutoDeleted() {
        val f = fixture()
        try {
            val rot = f.rotated.rotatedFileForTest()
            val badRotated = rot.readBytes().apply { this[lastIndex] = (last().toInt() xor 1).toByte() }
            rot.writeBytes(badRotated)
            var r = f.inspect()
            assertEquals(ArchiveCandidateEvidence.UNREADABLE, r.rotated.evidence)
            assertEquals(ArchiveCandidateEvidence.AUTHENTICATED, r.original.evidence)
            val src = f.original.localEncryptedFileForTest()
            val badOriginal = src.readBytes().apply { this[lastIndex] = (last().toInt() xor 1).toByte() }
            src.writeBytes(badOriginal)
            r = f.inspect()
            assertEquals(ArchiveCandidateEvidence.UNREADABLE, r.rotated.evidence)
            assertEquals(ArchiveCandidateEvidence.UNREADABLE, r.original.evidence)
            assertEquals(ArchiveCandidateRelationship.INDETERMINATE, r.relationship)
            assertArrayEquals(badRotated, rot.readBytes())
            assertArrayEquals(badOriginal, src.readBytes())
        } finally { f.clean() }
    }

    @Test fun independentSessionOptInRequiredBeforeInspecting() {
        val f = fixture()
        try {
            f.original.disableForThisSession()
            assertThrows(IllegalStateException::class.java) { f.inspect() }
            f.original.enableForThisSession()
            f.rotated.disableForThisSession()
            assertThrows(IllegalStateException::class.java) { f.inspect() }
        } finally { f.clean() }
    }
}
