package dev.n0png.fieldops.core.propagation

/**
 * CP-0009B software-only bounded, value-based offline report archive.
 * NOT disk/DB persistence, signed provenance, an RF log, or an atomic source store.
 * Callers retain archive values and explicitly save them in a later platform layer.
 */
data class PropagationOfflineArchivePolicy(
    val maxEntries: Int = 32,
    val maxTotalUtf8Bytes: Long = 64L * 1024 * 1024,
) {
    init {
        require(maxEntries in 1..128) { "Archive entry bound must be 1..128" }
        require(maxTotalUtf8Bytes in 1L..(512L * 1024 * 1024)) {
            "Archive byte budget must be positive and bounded"
        }
    }
}

data class PropagationOfflineArchiveEntry(
    val artifact: PropagationOfflineSerializedReport,
    val receipt: PropagationOfflineImportReceipt,
) {
    val id: String get() = receipt.sha256Hex
    val queryUtcMillis: Long get() = receipt.reportQueryUtcMillis
}

data class PropagationOfflineReportArchive(
    val policy: PropagationOfflineArchivePolicy,
    /** Retention order: oldest inserted first, newest inserted last. */
    val entries: List<PropagationOfflineArchiveEntry>,
) {
    val totalUtf8Bytes: Long get() = entries.sumOf { it.artifact.utf8ByteCount.toLong() }
}

data class PropagationOfflineArchiveAppend(
    val archive: PropagationOfflineReportArchive,
    val inserted: Boolean,
    val evictedSha256: List<String>,
)

/**
 * Every public operation reimports every retained canonical artifact and
 * reconciles its receipt, including reads. The digest is an archive-local
 * content key, NOT a verified source signature or authenticated origin.
 */
object PropagationOfflineReportArchiveService {
    fun empty(policy: PropagationOfflineArchivePolicy = PropagationOfflineArchivePolicy()):
        PropagationOfflineReportArchive = PropagationOfflineReportArchive(policy, emptyList())

    fun validate(archive: PropagationOfflineReportArchive): PropagationOfflineReportArchive {
        require(archive.entries.size <= archive.policy.maxEntries) {
            "Archive exceeds retained entry bound"
        }
        require(archive.totalUtf8Bytes <= archive.policy.maxTotalUtf8Bytes) {
            "Archive exceeds retained byte bound"
        }
        require(archive.entries.map { it.id }.toSet().size == archive.entries.size) {
            "Duplicate canonical archive identities"
        }
        archive.entries.forEach { checked(it) }
        return archive
    }

    fun append(
        archive: PropagationOfflineReportArchive,
        capture: PropagationWorkspaceHistoryCapture,
    ): PropagationOfflineArchiveAppend {
        validate(archive)
        val entry = PropagationOfflineArchiveEntry(capture.artifact, capture.receipt)
        checked(entry)
        require(entry.artifact.utf8ByteCount.toLong() <= archive.policy.maxTotalUtf8Bytes) {
            "Report larger than the archive budget"
        }
        if (archive.entries.any { it.id == entry.id }) {
            return PropagationOfflineArchiveAppend(archive, false, emptyList())
        }
        val remaining = archive.entries.toMutableList()
        remaining.add(entry)
        val evicted = mutableListOf<String>()
        var bytes = archive.totalUtf8Bytes + entry.artifact.utf8ByteCount.toLong()
        while (remaining.size > archive.policy.maxEntries ||
            bytes > archive.policy.maxTotalUtf8Bytes) {
            val removed = remaining.removeAt(0)
            evicted.add(removed.id)
            bytes -= removed.artifact.utf8ByteCount.toLong()
        }
        val updated = PropagationOfflineReportArchive(archive.policy, remaining.toList())
        validate(updated)
        return PropagationOfflineArchiveAppend(updated, true, evicted.toList())
    }

    fun remove(archive: PropagationOfflineReportArchive, sha256Hex: String):
        PropagationOfflineReportArchive {
        validate(archive)
        require(archive.entries.any { it.id == sha256Hex }) { "Unknown archive entry" }
        return validate(archive.copy(entries = archive.entries.filterNot { it.id == sha256Hex }))
    }

    fun select(archive: PropagationOfflineReportArchive, sha256Hex: String):
        PropagationWorkspaceHistoryCapture {
        validate(archive)
        val entry = archive.entries.firstOrNull { it.id == sha256Hex }
            ?: throw IllegalArgumentException("Unknown archive entry")
        return PropagationWorkspaceHistoryCapture(entry.artifact, entry.receipt)
    }

    /** Most recent report query UTC first; equal query times tie-break by digest. */
    fun page(archive: PropagationOfflineReportArchive, offset: Int = 0, limit: Int = 25):
        List<PropagationOfflineArchiveEntry> {
        validate(archive)
        require(offset >= 0 && limit in 1..100) { "Invalid archive page boundary" }
        return archive.entries.sortedWith(
            compareByDescending<PropagationOfflineArchiveEntry> { it.queryUtcMillis }
                .thenBy { it.id }
        ).drop(offset).take(limit).toList()
    }

    fun compare(
        archive: PropagationOfflineReportArchive,
        beforeSha256: String,
        afterSha256: String,
    ): PropagationWorkspaceHistoryComparison =
        PropagationWorkspaceHistoryService.compare(
            select(archive, beforeSha256), select(archive, afterSha256))

    private fun checked(entry: PropagationOfflineArchiveEntry) {
        val imported = PropagationOfflineReportImportService.importReport(entry.artifact)
        require(imported.receipt == entry.receipt) {
            "Archive receipt differs from canonical imported report"
        }
        require(entry.id == entry.artifact.sha256Hex) {
            "Archive identity does not match canonical digest"
        }
        require(!entry.receipt.originAuthenticated &&
            !entry.receipt.crossStoreAtomicityVerified) {
            "Offline report cannot claim source authentication or atomicity"
        }
    }
}
