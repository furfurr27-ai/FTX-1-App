package dev.n0png.fieldops.app

import dev.n0png.fieldops.core.propagation.PropagationArchiveStorageCodec
import dev.n0png.fieldops.core.propagation.PropagationOfflineReportArchive
import java.security.MessageDigest

enum class EncryptedRecoveryCandidate {
    ORIGINAL_V2,
    ROTATED_V2,
}

/**
 * An in-memory, nonpersistent inspection token. This is not a trusted
 * freshness witness, capability for key retirement, or device provenance.
 */
data class EncryptedRecoveryReview(
    val inspection: EncryptedArchiveConflictInspection,
    val choices: Set<EncryptedRecoveryCandidate>,
    val warning: String = "FRESHNESS NOT VERIFIED",
)

/** Documentation-only checklist. It has no key or filesystem operations. */
data class ArchiveRetirementReadiness(
    val permitted: Boolean = false,
    val status: String = "BLOCKED_NON_EXECUTING",
    val unmetConditions: List<String>,
)

/**
 * CP-0009N: inspect, choose and revalidate explicit in-memory recovery.
 * 
 * Never auto-picks the newest candidate. Key/ciphertext creation, deletion,
 * replacement, alias retirement, and plaintext fallback are intentionally
 * absent. Even matching authenticated copies do not establish freshness.
 */
class ExplicitEncryptedArchiveRecovery(
    private val original: OptInEncryptedArchiveStore,
    private val rotated: ExplicitEncryptedArchiveRotation,
) {
    private val inspector = EncryptedArchiveConflictInspector(original, rotated)

    @Synchronized
    fun reviewExplicitly(): EncryptedRecoveryReview {
        val report = inspector.inspectExplicitly()
        val choices = buildSet {
            if (report.original.evidence == ArchiveCandidateEvidence.AUTHENTICATED) {
                add(EncryptedRecoveryCandidate.ORIGINAL_V2)
            }
            if (report.rotated.evidence == ArchiveCandidateEvidence.AUTHENTICATED) {
                add(EncryptedRecoveryCandidate.ROTATED_V2)
            }
        }
        return EncryptedRecoveryReview(report, choices)
    }

    /** Reinspect BOTH candidates, then reauthenticate selected bytes on use. */
    @Synchronized
    fun recoverToMemoryExplicitly(
        priorReview: EncryptedRecoveryReview,
        candidate: EncryptedRecoveryCandidate,
    ): PropagationOfflineReportArchive {
        check(original.isEnabled() && rotated.isEnabled()) {
            "Both encrypted archive sessions must be explicitly enabled"
        }
        check(candidate in priorReview.choices) {
            "Selected archive was not authenticated in previous review"
        }
        val now = inspector.inspectExplicitly()
        check(now == priorReview.inspection) {
            "Archive evidence changed since inspection; review again before recovery"
        }
        val expected = when (candidate) {
            EncryptedRecoveryCandidate.ORIGINAL_V2 -> priorReview.inspection.original
            EncryptedRecoveryCandidate.ROTATED_V2 -> priorReview.inspection.rotated
        }
        check(expected.evidence == ArchiveCandidateEvidence.AUTHENTICATED &&
            expected.canonicalSha256 != null) {
            "Selected archive lacks authenticated canonical evidence"
        }
        val loaded = when (candidate) {
            EncryptedRecoveryCandidate.ORIGINAL_V2 -> original.loadEncryptedExplicitly()
            EncryptedRecoveryCandidate.ROTATED_V2 -> rotated.restoreRotatedExplicitly()
        } ?: error("Selected archive disappeared after inspection")
        // Reverify the candidate *after* the explicit tap. This guards against
        // stale selections; it does NOT prove nonrollbackable freshness.
        val bytes = PropagationArchiveStorageCodec.encode(loaded)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        check(expected.canonicalSha256 == digest &&
            expected.entryCount == loaded.entries.size) {
            "Selected archive changed since review; on-screen history retained"
        }
        return loaded
    }

    /** No mechanism to delete an old Keystore alias is exposed. */
    fun reviewRetirementReadiness(): ArchiveRetirementReadiness = ArchiveRetirementReadiness(
        unmetConditions = listOf(
            "Independent trusted monotonic rollback/freshness evidence absent",
            "No independently verified recoverable backup and owner approval",
            "Physical target-device Android Keystore and filesystem testing incomplete",
            "Secure flash erasure cannot be guaranteed",
            "Old V1 plaintext and V2 archives require separate retention decisions",
        )
    )
}
