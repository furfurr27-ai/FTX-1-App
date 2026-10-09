# CP-0008S — Offline Imported Report Inspection and Comparison

Parent: `CP-0008R-PROPAGATION_OFFLINE_REPORT_IMPORT_BOUNDARY`, verified main commit `39f436b8526c1a49a2c885baaffa650526cdb4d2`.

## Pure comparison contract

`PropagationOfflineReportComparisonService.compare(beforeArtifact, afterArtifact)` imports both *complete* canonical V1 CP-0008P artifacts via CP-0008R and CP-0008Q validation, then creates a deterministic, ordered, read-only comparison. A second overload accepts two CP-0008R imported views and rechecks their canonical payloads against the stored receipts, rejecting unsafe post-import mutation. This is an offline analysis, never a live provider refresh or cache ingest.

Source rows are keyed by sourceKey and sorted lexicographically. Selected evidence is keyed by evidenceId, sorted lexicographically, and compared using **both** index/source-provenance metadata **and** original complete selected projection objects: changes in measurements, model details, map samples or RF SNR cannot be silently missed merely because metadata keys match. Additions/removals are labeled `ADDED_TO_VIEW` / `REMOVED_FROM_VIEW`: because the two reports may have different filters or source availability, an item missing from a selected view is *not* proof it was deleted from provider data or that conditions worsened. Changes and unchanged items remain inspectable.

Comparison retains both immutable import receipt values (SHA-256/content type/UTC/snapshot counts), original query-time ordering, snapshot/summary/assessment change flags, and original before/after source status and evidence index items. Do not recompute freshness, infer signal quality, or interpret the checksum as authenticated identity. If two reports differ only in their query/capture metadata, the canonical artifact can change without any selected evidence being newly observed.

All source, evidence and snapshot fields remain derived from the two complete validated inputs. No secrets, external accounts, disk, network, Android UI, device radio, RF, or new dependencies are used.

## Host/CI verification

Synthetic comparison fixtures cover identical/absent reports, source additions/removals, evidence additions/removals, payload-only changes with unchanged index metadata, retained before/after provenance, deterministic ordering, reversed comparisons, query-time ordering and freshness markers, assessment-only differences, invalid format/digest/truncated artifacts, unsafe imported DTO mutation, and nonmutation of serialized source artifacts. The CP-0008R import, CP-0008Q decode and CP-0008P serialization tests, plus inherited regression suites, are separately retained in premerge and postmerge CI. The premerge dry-run verifies a temporary checkpoint; the postmerge finalizer commits the immutable checkpoint after green main CI.

## Evidence limits and owner override

SHA-256 is an unkeyed integrity checksum, not an authenticated report signature. No cross-store atomicity, independently verified source provenance, real device, provider availability, or RF proof. CP-0003C remains **DEFERRED** until the owner explicitly says resume CP-0003C. CP-0004A/B/C remain incomplete.
