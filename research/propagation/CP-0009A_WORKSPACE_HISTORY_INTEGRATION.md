# CP-0009A — Propagation Workspace to Historical Comparison Integration

Parent: **CP-0008Z-PROPAGATION_OFFLINE_COMPARISON_ACCESSIBILITY_STATE**, durable main `dd9a300d82701628e62faa69d81d05416c90bacb`. Live README and NEXT_ACTION were reviewed; CP-0009A is deliberately the first composition/integration checkpoint above the CP-0008P–Z stack, not another abstract presentation layer.

## Software-only boundary

`PropagationWorkspaceHistoryService.capture(picture)` accepts an already-read CP-0008M `PropagationOperatingPicture` and derives CP-0008N diagnostics, CP-0008O typed offline report, CP-0008P canonical V1 export, and strict CP-0008R validated import receipt. It does not initiate a second snapshot/status read, refresh, network request, timer or storage mutation, and does not assume that the two independent underlying stores were transactionally consistent. The host is responsible for deciding whether/where to retain the returned immutable artifact record; this checkpoint does not persist it.

`compare(before,after)` accepts two explicitly provided captured canonical report artifacts (never implicitly using “latest” or live providers). Each record is imported/validated against its own receipt; CP-0008T canonical derivative is built from the original report artifacts, and the full CP-0008Z historical accessibility display is opened with both original reports supplied. The integration asserts original-reports-match and exact report receipts. Reports may be earlier, later, or same UTC; the existing exact ordering classification is preserved, never invented or forced monotonic.

`interact(comparison,action)` revalidates both canonical originals, recalculates the derivative from those originals, rejects a derivative that does not match, and forwards an action to CP-0008Z's strict, artifact-bound navigation/filter/focus state transition. Both original captures, canonical derivative, global unfiltered counts and four required historical-only provenance warnings survive transitions; source and selected evidence have independent bounded pages and filters. No statement about on-air performance, live provider status, provider deletion or authenticated origin is possible from these archive views.

## Evidence and limits

Synthetic host fixtures exercise no-snapshot and populated cached pictures, exact query timestamps/snapshot/source/evidence receipt counts, deterministic export/import, full canonical derivative reconciliation, forward and reverse UTC comparisons, independent cursors and filter changes, provenance notices, stale/corrupt/swapped or caller-forged data rejection, and separation from live status. The full 40-job CP-0008Z inherited host matrix must remain green, plus the CP-0009A focused integration job; main finalizer must verify immutable snapshot/file hashes before durable promotion.

This is host-tested Kotlin composition only: no Android app screen, real FTX-1 control/audio, USB, PTT, live RF, provider authentication, station account, certificate, physical device, archive persistence, or store atomicity claim.

CP-0003C remains **DEFERRED** until the owner explicitly says `resume CP-0003C`. CP-0004A/B/C remain incomplete.
