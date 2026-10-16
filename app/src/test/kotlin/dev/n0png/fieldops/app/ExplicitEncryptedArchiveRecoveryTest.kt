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

/** Synthetic software-key proof; no hardware-backed Android Keystore assertions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExplicitEncryptedArchiveRecoveryTest {
    private class Keys(var secret: SecretKey? = null) : EncryptedArchiveKeyProvider {
        var generated = 0
        override fun existingKey() = secret
        override fun createKeyOnExplicitSave(): SecretKey {
            generated++
            return KeyGenerator.getInstance("AES").apply { init(256) }
                .generateKey().also { secret = it }
        }
    }

    private fun report(at: Long): PropagationOfflineReportArchive {
        var state = PropagationArchiveWorkspaceService.open(
            PropagationOfflineArchivePolicy(maxEntries = 4)
        )
        val picture = PropagationOperatingPictureService(
            InMemoryPropagationSnapshotStore(), InMemoryPropagationRefreshStateStore()
        ).read(PropagationProjectionQuery(at))
        state = PropagationArchiveWorkspaceService.apply(
            state, PropagationArchiveWorkspaceAction.Capture(picture)
        )
        return state.archive
    }

    private data class Fixture(
        val original: OptInEncryptedArchiveStore,
        val rotated: ExplicitEncryptedArchiveRotation,
        val keysOriginal: Keys,
        val keysRotated: Keys,
    ) {
        val recovery get() = ExplicitEncryptedArchiveRecovery(original, rotated)
        fun clean() {
            original.enableForThisSession()
            rotated.enableForThisSession()
            rotated.deleteRotatedCopyExplicitly()
            original.deleteEncryptedExplicitly()
        }
    }

    private fun fixture(): Fixture {
        val ctx = RuntimeEnvironment.getApplication()
        val keyA = Keys()
        val keyB = Keys()
        val original = OptInEncryptedArchiveStore(ctx, keyA)
        val rotated = ExplicitEncryptedArchiveRotation(ctx, original, keyB)
        val f = Fixture(original, rotated, keyA, keyB)
        f.clean()
        original.saveEncryptedExplicitly(report(1_790_000_100_000L))
        rotated.rotateExplicitly()
        return f
    }

    @Test fun bothAuthenticatedRequireNamedChoiceAndPreserveCiphertexts() {
        val f = fixture()
        try {
            val originalBytes = f.original.localEncryptedFileForTest().readBytes()
            val rotatedBytes = f.rotated.rotatedFileForTest().readBytes()
            val choice = f.recovery.reviewExplicitly()
            assertEquals(setOf(EncryptedRecoveryCandidate.ORIGINAL_V2,
                EncryptedRecoveryCandidate.ROTATED_V2), choice.choices)
            assertEquals(ArchiveCandidateRelationship.MATCHING_AUTHENTICATED,
                choice.inspection.relationship)
            val original = f.recovery.recoverToMemoryExplicitly(choice,
                EncryptedRecoveryCandidate.ORIGINAL_V2)
            val rotated = f.recovery.recoverToMemoryExplicitly(choice,
                EncryptedRecoveryCandidate.ROTATED_V2)
            assertEquals(original, rotated)
            assertEquals(report(1_790_000_100_000L), original)
            assertArrayEquals(originalBytes, f.original.localEncryptedFileForTest().readBytes())
            assertArrayEquals(rotatedBytes, f.rotated.rotatedFileForTest().readBytes())
            assertEquals(1, f.keysOriginal.generated)
            assertEquals(1, f.keysRotated.generated)
        } finally { f.clean() }
    }

    @Test fun reinspectionRejectsChangedOriginalEvenWhenRotatedStillValid() {
        val f = fixture()
        try {
            val review = f.recovery.reviewExplicitly()
            val rotBefore = f.rotated.rotatedFileForTest().readBytes()
            f.original.saveEncryptedExplicitly(report(1_790_002_100_000L))
            val originalNow = f.original.localEncryptedFileForTest().readBytes()
            assertThrows(IllegalStateException::class.java) {
                f.recovery.recoverToMemoryExplicitly(review,
                    EncryptedRecoveryCandidate.ROTATED_V2)
            }
            assertArrayEquals(originalNow, f.original.localEncryptedFileForTest().readBytes())
            assertArrayEquals(rotBefore, f.rotated.rotatedFileForTest().readBytes())
            val freshReview = f.recovery.reviewExplicitly()
            assertEquals(ArchiveCandidateRelationship.CONFLICTING_AUTHENTICATED,
                freshReview.inspection.relationship)
            assertEquals(report(1_790_002_100_000L),
                f.recovery.recoverToMemoryExplicitly(
                    freshReview, EncryptedRecoveryCandidate.ORIGINAL_V2))
            assertEquals(report(1_790_000_100_000L),
                f.recovery.recoverToMemoryExplicitly(
                    freshReview, EncryptedRecoveryCandidate.ROTATED_V2))
        } finally { f.clean() }
    }

    @Test fun deletedOrTamperedChosenCiphertextRejectsPreviousReview() {
        val f = fixture()
        try {
            val review = f.recovery.reviewExplicitly()
            f.rotated.deleteRotatedCopyExplicitly()
            assertThrows(IllegalStateException::class.java) {
                f.recovery.recoverToMemoryExplicitly(
                    review, EncryptedRecoveryCandidate.ROTATED_V2)
            }
            val newReview = f.recovery.reviewExplicitly()
            assertEquals(setOf(EncryptedRecoveryCandidate.ORIGINAL_V2),
                newReview.choices)
            assertThrows(IllegalStateException::class.java) {
                f.recovery.recoverToMemoryExplicitly(
                    newReview, EncryptedRecoveryCandidate.ROTATED_V2)
            }
            val src = f.original.localEncryptedFileForTest()
            val corrupted = src.readBytes().apply {
                this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte()
            }
            src.writeBytes(corrupted)
            val badReview = f.recovery.reviewExplicitly()
            assertTrue(badReview.choices.isEmpty())
            assertEquals(ArchiveCandidateEvidence.UNREADABLE,
                badReview.inspection.original.evidence)
            assertArrayEquals(corrupted, src.readBytes())
        } finally { f.clean() }
    }

    @Test fun missingKeyNeverCreatesReplacementDuringReviewOrRecovery() {
        val f = fixture()
        try {
            val oldBytes = f.original.localEncryptedFileForTest().readBytes()
            val newerBytes = f.rotated.rotatedFileForTest().readBytes()
            val reviewed = f.recovery.reviewExplicitly()
            f.keysRotated.secret = null
            assertThrows(IllegalStateException::class.java) {
                f.recovery.recoverToMemoryExplicitly(reviewed,
                    EncryptedRecoveryCandidate.ROTATED_V2)
            }
            val newReview = f.recovery.reviewExplicitly()
            assertEquals(ArchiveCandidateEvidence.KEY_UNAVAILABLE,
                newReview.inspection.rotated.evidence)
            assertEquals(setOf(EncryptedRecoveryCandidate.ORIGINAL_V2),
                newReview.choices)
            assertEquals(1, f.keysRotated.generated)
            assertArrayEquals(oldBytes, f.original.localEncryptedFileForTest().readBytes())
            assertArrayEquals(newerBytes, f.rotated.rotatedFileForTest().readBytes())
        } finally { f.clean() }
    }

    @Test fun bothOptInsRequiredForChoiceAndForConfirmedRecovery() {
        val f = fixture()
        try {
            val reviewed = f.recovery.reviewExplicitly()
            f.original.disableForThisSession()
            assertThrows(IllegalStateException::class.java) {
                f.recovery.recoverToMemoryExplicitly(
                    reviewed, EncryptedRecoveryCandidate.ROTATED_V2)
            }
            assertThrows(IllegalStateException::class.java) {
                f.recovery.reviewExplicitly()
            }
            f.original.enableForThisSession()
            f.rotated.disableForThisSession()
            assertThrows(IllegalStateException::class.java) {
                f.recovery.recoverToMemoryExplicitly(
                    reviewed, EncryptedRecoveryCandidate.ORIGINAL_V2)
            }
        } finally { f.clean() }
    }

    @Test fun retirementReadinessIsAlwaysNonExecutingWithConcreteBlockers() {
        val f = fixture()
        try {
            val a = f.recovery.reviewRetirementReadiness()
            assertFalse(a.permitted)
            assertEquals("BLOCKED_NON_EXECUTING", a.status)
            assertTrue(a.unmetConditions.any { it.contains("monotonic") })
            assertTrue(a.unmetConditions.any { it.contains("backup") })
            assertTrue(a.unmetConditions.any { it.contains("Secure flash") })
            assertTrue(f.original.localEncryptedFileForTest().exists())
            assertTrue(f.rotated.rotatedFileForTest().exists())
        } finally { f.clean() }
    }
}
