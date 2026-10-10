package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*

/** CP-0009B host-only deterministic archive contract using synthetic reports. */
object PropagationOfflineReportArchiveTests {
    private var checks = 0
    private fun eq(a: Any?, b: Any?, label: String) {
        checks++
        check(a == b) { "$label: expected=$a actual=$b" }
    }
    private fun yes(a: Boolean, label: String) { checks++; check(a) { label } }
    private fun rejects(label: String, action: () -> Unit) {
        checks++
        check(runCatching(action).isFailure) { "Accepted invalid $label" }
    }
    private fun capture(time: Long): PropagationWorkspaceHistoryCapture {
        val snapshot = InMemoryPropagationSnapshotStore()
        val refresh = InMemoryPropagationRefreshStateStore()
        val picture = PropagationOperatingPictureService(snapshot, refresh)
            .read(PropagationProjectionQuery(time))
        return PropagationWorkspaceHistoryService.capture(picture)
    }

    @JvmStatic fun main(args: Array<String>) {
        val api = PropagationOfflineReportArchiveService
        val a = capture(2_400_000_000L)
        val b = capture(2_400_000_100L)
        val c = capture(2_400_000_050L)
        val empty = api.empty(PropagationOfflineArchivePolicy(maxEntries = 2))
        eq(0, empty.entries.size, "empty")
        eq(0L, empty.totalUtf8Bytes, "empty byte count")
        val first = api.append(empty, a)
        yes(first.inserted, "first insertion")
        eq(emptyList<String>(), first.evictedSha256, "no eviction")
        eq(1, first.archive.entries.size, "one entry")
        eq(a, api.select(first.archive, a.artifact.sha256Hex), "canonical selection")
        eq(0, empty.entries.size, "prior value never mutated")
        val duplicate = api.append(first.archive, a)
        yes(!duplicate.inserted, "canonical duplicate idempotent")
        eq(first.archive, duplicate.archive, "duplicate unchanged")
        val second = api.append(duplicate.archive, b)
        eq(2, second.archive.entries.size, "bound reached")
        eq(listOf(b.receipt.sha256Hex, a.receipt.sha256Hex),
            api.page(second.archive).map { it.id }, "descending query UTC")
        val compared = api.compare(second.archive, a.receipt.sha256Hex, b.receipt.sha256Hex)
        eq(a.receipt, compared.before.receipt, "before receipt")
        eq(b.receipt, compared.after.receipt, "after receipt")
        val next = api.append(second.archive, c)
        eq(listOf(a.receipt.sha256Hex), next.evictedSha256, "FIFO retention eviction")
        eq(listOf(b.receipt.sha256Hex, c.receipt.sha256Hex),
            next.archive.entries.map { it.id }, "retention is insertion order")
        eq(listOf(b.receipt.sha256Hex, c.receipt.sha256Hex),
            api.page(next.archive).map { it.id }, "page by UTC not insertion")
        eq(listOf(c.receipt.sha256Hex),
            api.page(next.archive, offset = 1, limit = 1).map { it.id }, "bounded paging")
        eq(emptyList<String>(), api.page(next.archive, offset = 99).map { it.id }, "past end")
        eq(2, second.archive.entries.size, "older archive remains unchanged")
        rejects("evicted id") { api.select(next.archive, a.receipt.sha256Hex) }
        rejects("comparison missing id") {
            api.compare(next.archive, a.receipt.sha256Hex, b.receipt.sha256Hex)
        }
        val removed = api.remove(next.archive, b.receipt.sha256Hex)
        eq(listOf(c.receipt.sha256Hex), removed.entries.map { it.id }, "explicit removal")
        rejects("unknown remove") { api.remove(removed, b.receipt.sha256Hex) }

        val size = a.artifact.utf8ByteCount.toLong()
        val bytesArchive = api.empty(PropagationOfflineArchivePolicy(
            maxEntries = 8, maxTotalUtf8Bytes = size))
        val one = api.append(bytesArchive, a)
        eq(size, one.archive.totalUtf8Bytes, "exact byte budget")
        val rotated = api.append(one.archive, b)
        eq(listOf(a.receipt.sha256Hex), rotated.evictedSha256, "byte budget eviction")
        eq(size, rotated.archive.totalUtf8Bytes, "byte budget accounted")
        rejects("oversize does not evict") {
            api.append(api.empty(PropagationOfflineArchivePolicy(
                maxEntries = 3, maxTotalUtf8Bytes = size - 1)), a)
        }
        rejects("entry bound zero") { PropagationOfflineArchivePolicy(maxEntries = 0) }
        rejects("entry bound too large") { PropagationOfflineArchivePolicy(maxEntries = 129) }
        rejects("negative byte budget") { PropagationOfflineArchivePolicy(maxTotalUtf8Bytes = 0) }
        rejects("limit zero") { api.page(next.archive, limit = 0) }
        rejects("limit too large") { api.page(next.archive, limit = 101) }
        rejects("offset negative") { api.page(next.archive, offset = -1) }
        rejects("tampered artifact") {
            api.append(empty, a.copy(artifact = a.artifact.copy(json = "{}")))
        }
        rejects("forged receipt") {
            api.append(empty, a.copy(receipt = a.receipt.copy(sourceCount = 500)))
        }
        rejects("forged origin claim") {
            api.append(empty, a.copy(receipt = a.receipt.copy(originAuthenticated = true)))
        }
        rejects("tampered retained entry rejected on read") {
            api.page(first.archive.copy(entries = listOf(
                first.archive.entries.single().copy(receipt =
                    a.receipt.copy(reportQueryUtcMillis = -999)))))
        }
        rejects("duplicate manual archive identities") {
            api.validate(first.archive.copy(entries = listOf(
                first.archive.entries.single(), first.archive.entries.single())))
        }
        rejects("archive over entry limit") {
            api.validate(empty.copy(entries = listOf(
                PropagationOfflineArchiveEntry(a.artifact, a.receipt),
                PropagationOfflineArchiveEntry(b.artifact, b.receipt),
                PropagationOfflineArchiveEntry(c.artifact, c.receipt))))
        }
        eq(a, api.select(first.archive, a.receipt.sha256Hex), "valid original survives")
        println("CP-0009B offline archive: PASS assertions=$checks")
    }
}
