# CP-0008W — Offline Comparison Presentation Read Model

Durable parent: CP-0008V-PROPAGATION_OFFLINE_COMPARISON_IMPORT_INSPECTION at main `89a226e61bcddb7404a5061eb056888b63723bf8`.

## Scope

The verified propagation roadmap provides canonical offline diagnostic report V1 (P/Q/R), comparison of selected report views (S), canonical comparison export (T), decoder (U), and imported comparison inspection (V). This checkpoint implements the next platform-neutral layer: deterministic historical presentation DTOs for a future Android screen. No Android UI, live provider fetch, data-store mutation, real radio, credentials, or RF testing is included.

`PropagationOfflineReportComparisonPresentationService.present(artifact, before?, after?)` first invokes CP-0008V strict canonical import (which enforces complete metadata, bounded UTF-8, SHA-256 and canonical bytes). It emits both original report receipts, comparison receipt, original query UTC order, four original change flags, sorted source and selected-evidence rows, distinct evidence metadata and full-projection flags, classification counts, deterministic identity lookup and filters by classification or selected evidence kind. The four classification buckets are ADDED_TO_VIEW, REMOVED_FROM_VIEW, CHANGED_IN_VIEW and UNCHANGED; selected-view additions/removals do not imply provider mutation or RF change.

Optional originals must be supplied together. Absent originals are explicitly `ORIGINAL_REPORTS_NOT_SUPPLIED`. Both valid originals are recomputed through the CP-0008V check and marked `ORIGINAL_REPORTS_MATCH` or `ORIGINAL_REPORTS_MISMATCH`; invalid artifacts are rejected. These states only express derivative-to-report consistency, never authenticated provenance. Original reports are themselves unkeyed and unauthenticated.

Outer lists are unmodifiable for ordinary consumers but nested Kotlin DTOs are not a deep immutable trust boundary. A true independent provider-origin verification would need a different security design and evidence sources. No cross-store atomicity, live source health, RF confidence or independently observed path claim is made.

Focused Kotlin host tests cover deterministic repeatability, receipts, timestamps, source/evidence identity, classification tallies, metadata/full content flags, Unicode identity, missing/added/removed/unchanged/reversed selected views, original-report reconciliation including a recomputed yet inconsistent derivative, invalid media/length/version/hash/JSON and read-only outer collections. All inherited CP-0008P/Q/R/S/T/U/V tests and the previous complete regression matrix remain mandatory. Final promotion requires green exact-tip CI plus green main CI and verified immutable snapshot and manifest hashes.

CP-0003C remains **DEFERRED** until the owner explicitly requests `resume CP-0003C`. CP-0004A/B/C remain incomplete.
