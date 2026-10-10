package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*

/** CP-0009C synthetic host-only archive workspace/navigation contract. */
object PropagationArchiveWorkspaceTests {
    private var assertions = 0
    private fun eq(expected: Any?, actual: Any?, label: String) {
        assertions++
        check(expected == actual) { label + ": expected=" + expected + " actual=" + actual }
    }
    private fun yes(value: Boolean, label: String) {
        assertions++
        check(value) { label }
    }
    private fun rejects(label: String, operation: () -> Unit) {
        assertions++
        check(runCatching(operation).isFailure) { "Accepted " + label }
    }
    private fun picture(time: Long): PropagationOperatingPicture =
        PropagationOperatingPictureService(
            InMemoryPropagationSnapshotStore(), InMemoryPropagationRefreshStateStore()
        ).read(PropagationProjectionQuery(time))

    @JvmStatic fun main(args: Array<String>) {
        val service = PropagationArchiveWorkspaceService
        val firstTime = 2_500_000_000L
        val secondTime = 2_500_000_200L
        val thirdTime = 2_500_000_100L
        val initial = service.open(PropagationOfflineArchivePolicy(maxEntries = 2))
        val blank = service.screen(initial)
        eq(0, blank.totalReports, "empty history")
        yes(blank.rows.isEmpty(), "empty rows")
        yes(blank.provenanceNotices.any { it.contains("not authenticate") }, "origin warning always visible")
        yes(blank.provenanceNotices.any { it.contains("No Android") }, "no persistence claim")
        val a = service.apply(initial, PropagationArchiveWorkspaceAction.Capture(picture(firstTime)))
        val idA = a.selectedSha256!!
        eq(1, service.screen(a).totalReports, "one capture from caller picture")
        eq(idA, service.screen(a).selectedReceipt!!.sha256Hex, "selected canonical receipt")
        eq(0, service.screen(initial).totalReports, "old immutable history")
        val b = service.apply(a, PropagationArchiveWorkspaceAction.Capture(picture(secondTime)))
        val idB = b.selectedSha256!!
        val compared = service.apply(b, PropagationArchiveWorkspaceAction.Compare(idA, idB))
        val screen = service.screen(compared)
        eq(idA, screen.comparison!!.before.receipt.sha256Hex, "original first receipt")
        eq(idB, screen.comparison.after.receipt.sha256Hex, "original second receipt")
        yes(screen.provenanceNotices.any { it.contains("ARCHIVED DATA ONLY") }, "historical safety label")
        rejects("self comparison") {
            service.apply(b, PropagationArchiveWorkspaceAction.Compare(idA, idA))
        }
        rejects("unknown comparison") {
            service.apply(b, PropagationArchiveWorkspaceAction.Compare(idA, "missing"))
        }
        val paged = service.apply(compared, PropagationArchiveWorkspaceAction.SetPageSize(1))
        eq(listOf(idB), service.screen(paged).rows.map { it.sha256Hex }, "UTC descending first page")
        yes(service.screen(paged).nextEnabled, "next enabled")
        val pageTwo = service.apply(paged, PropagationArchiveWorkspaceAction.NextPage)
        eq(listOf(idA), service.screen(pageTwo).rows.map { it.sha256Hex }, "next page")
        yes(service.screen(pageTwo).previousEnabled, "previous enabled")
        yes(!service.screen(pageTwo).nextEnabled, "end page bound")
        eq(pageTwo, service.apply(pageTwo, PropagationArchiveWorkspaceAction.NextPage), "last page stable")
        eq(paged, service.apply(pageTwo, PropagationArchiveWorkspaceAction.PreviousPage), "previous page")
        val c = service.apply(compared, PropagationArchiveWorkspaceAction.Capture(picture(thirdTime)))
        val idC = c.selectedSha256!!
        yes(c.archive.entries.none { it.id == idA }, "oldest insertion FIFO eviction")
        eq(2, service.screen(c).totalReports, "retention bound respected")
        eq(null, c.comparisonBeforeSha256, "eviction clears unsafe comparison")
        eq(null, c.comparisonAfterSha256, "eviction clears paired selection")
        yes(service.screen(c).comparison == null, "evicted comparison not rendered")
        val selectedB = service.apply(c, PropagationArchiveWorkspaceAction.Select(idB))
        eq(idB, service.screen(selectedB).selectedReceipt!!.sha256Hex, "explicit historical selection")
        val removed = service.apply(selectedB, PropagationArchiveWorkspaceAction.Delete(idB))
        eq(null, removed.selectedSha256, "delete clears active selection")
        eq(listOf(idC), removed.archive.entries.map { it.id }, "only retained capture remains")
        rejects("select evicted") {
            service.apply(removed, PropagationArchiveWorkspaceAction.Select(idA))
        }
        rejects("delete missing") {
            service.apply(removed, PropagationArchiveWorkspaceAction.Delete(idB))
        }
        rejects("invalid page limit") {
            service.apply(removed, PropagationArchiveWorkspaceAction.SetPageSize(101))
        }
        rejects("negative untrusted offset") {
            service.screen(removed.copy(offset = -1))
        }
        rejects("stale fabricated selected id") {
            service.screen(removed.copy(selectedSha256 = idB))
        }
        rejects("orphan comparison state") {
            service.screen(removed.copy(comparisonBeforeSha256 = idC))
        }
        rejects("forged receipt in read state") {
            val bad = removed.archive.entries.first().copy(
                receipt = removed.archive.entries.first().receipt.copy(sourceCount = -3)
            )
            service.screen(removed.copy(archive = removed.archive.copy(entries = listOf(bad))))
        }
        eq(null, service.screen(service.apply(compared,
            PropagationArchiveWorkspaceAction.ClearComparison)).comparison, "clear comparison")
        eq(2, service.screen(compared).totalReports, "prior comparison remains unchanged")
        println("CP-0009C archive workspace: PASS assertions=" + assertions)
    }
}
