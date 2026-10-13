package dev.n0png.fieldops.app

import dev.n0png.fieldops.core.propagation.PropagationArchiveStorageCodec
import dev.n0png.fieldops.core.propagation.PropagationOfflineReportArchive
import java.io.IOException
import java.security.MessageDigest

/** Missing Android Keystore alias, not proof of malformed ciphertext. */
class EncryptedArchiveKeyUnavailableException(message: String) : IOException(message)

enum class ArchiveCandidateEvidence {
    MISSING,
    AUTHENTICATED,
    KEY_UNAVAILABLE,
    UNREADABLE,
}

enum class ArchiveCandidateRelationship {
    BOTH_MISSING,
    MATCHING_AUTHENTICATED,
    CONFLICTING_AUTHENTICATED,
    INDETERMINATE,
}

data class ArchiveCandidateInspection(
    val evidence: ArchiveCandidateEvidence,
    val entryCount: Int? = null,
    val canonicalSha256: String? = null,
)

data class EncryptedArchiveConflictInspection(
    val original: ArchiveCandidateInspection,
    val rotated: ArchiveCandidateInspection,
    val relationship: ArchiveCandidateRelationship,
    /** This inspection never authorizes or performs retirement. */
    val oldKeyRetirement: String = "BLOCKED_NON_EXECUTING",
    val freshness: String = "FRESHNESS NOT VERIFIED",
)

/**
 * Read-only, explicitly opted-in inspection of original and rotated V2 copies.
 *
 * Each archive is authenticated with ITS OWN key and checked by the existing
 * canonical receipt decoder. No key is provisioned, archive chosen, restored
 * to UI, copied, changed, or deleted. Old and rotated Keystore aliases survive.
 * Matching contents are not proof of recency. A conflicting authenticated
 * copy cannot be automatically ranked without independent trusted evidence.
 *
 * No file deletion or key retirement is implemented here. On-device Android
 * Keystore, filesystem behavior and power-loss guarantees are unverified.
 */
class EncryptedArchiveConflictInspector(
    private val original: OptInEncryptedArchiveStore,
    private val rotated: ExplicitEncryptedArchiveRotation,
) {
    private data class Read(
        val evidence: ArchiveCandidateInspection,
        val canonical: ByteArray? = null,
    )

    private fun inspect(
        exists: () -> Boolean,
        load: () -> PropagationOfflineReportArchive?,
    ): Read {
        if (!exists()) return Read(ArchiveCandidateInspection(ArchiveCandidateEvidence.MISSING))
        return try {
            // Never use save/create-key methods or silently fall back to V1.
            val archive = load() ?: return Read(
                ArchiveCandidateInspection(ArchiveCandidateEvidence.UNREADABLE)
            )
            val canonical = PropagationArchiveStorageCodec.encode(archive)
            val digest = MessageDigest.getInstance("SHA-256").digest(canonical)
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            Read(
                ArchiveCandidateInspection(
                    ArchiveCandidateEvidence.AUTHENTICATED,
                    archive.entries.size,
                    digest,
                ),
                canonical,
            )
        } catch (_: EncryptedArchiveKeyUnavailableException) {
            Read(ArchiveCandidateInspection(ArchiveCandidateEvidence.KEY_UNAVAILABLE))
        } catch (_: Exception) {
            // Authentication/format/size/I/O failure; do not claim which.
            Read(ArchiveCandidateInspection(ArchiveCandidateEvidence.UNREADABLE))
        }
    }

    @Synchronized
    fun inspectExplicitly(): EncryptedArchiveConflictInspection {
        check(original.isEnabled() && rotated.isEnabled()) {
            "Enable original V2 and rotated sessions before explicit inspection"
        }
        val a = inspect(
            { original.localEncryptedFileForTest().exists() },
            { original.loadEncryptedExplicitly() },
        )
        val b = inspect(
            { rotated.rotatedFileForTest().exists() },
            { rotated.restoreRotatedExplicitly() },
        )
        val relation = when {
            a.evidence.evidence == ArchiveCandidateEvidence.MISSING &&
                b.evidence.evidence == ArchiveCandidateEvidence.MISSING ->
                ArchiveCandidateRelationship.BOTH_MISSING
            a.canonical != null && b.canonical != null ->
                if (a.canonical.contentEquals(b.canonical))
                    ArchiveCandidateRelationship.MATCHING_AUTHENTICATED
                else ArchiveCandidateRelationship.CONFLICTING_AUTHENTICATED
            else -> ArchiveCandidateRelationship.INDETERMINATE
        }
        return EncryptedArchiveConflictInspection(a.evidence, b.evidence, relation)
    }
}
