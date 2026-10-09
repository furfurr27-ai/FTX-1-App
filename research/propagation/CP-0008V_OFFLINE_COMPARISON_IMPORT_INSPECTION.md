# CP-0008V — Offline Comparison Import and Read-only Inspection Boundary

Immutable parent checkpoint: **CP-0008U-PROPAGATION_OFFLINE_COMPARISON_EXPORT_DECODE_VALIDATION**, durable main `2535bbeb3ac003febcc9a3f535f6ee5cfb455cf4`. CP-0008T is the comparison serialization contract; CP-0008U is the strict canonical typed decoder.

## Behavior

`PropagationOfflineReportComparisonImportService.importComparison(encoded)` requires a complete CP-0008T V1 `PropagationOfflineSerializedComparison` artifact: it delegates to CP-0008U strict envelope, format, wire version, UTF-8 byte count/size, unkeyed SHA-256, bounded JSON/typed DTO, time/receipt/correlation and canonical byte-for-byte validation. It does **not** offer a bare JSON import overload. Returned `PropagationOfflineImportedComparison` is detached from the caller's DTO containers by canonical re-decoding, exposes the parsed historical comparison and an explicit `PropagationOfflineComparisonImportReceipt` including both original CP-0008R receipt values.

The API provides read-only selected-view source/evidence lists, deterministic lookup by source key and selected evidence ID, classification/kind filtering, change counts and original before/after UTC query order. A selected view's ADDED_TO_VIEW or REMOVED_FROM_VIEW is not a change at the provider, deletion, or RF propagation fact. Returned lists use unmodifiable outer copies. Kotlin nested data classes and collection properties do **not** constitute a deep, transitively immutable object graph against hostile reflection/unsafe casts. This is an inspection boundary, not a security sandbox.

`matchesOriginalReports(imported,before,after)` is optional and strictly reimports both full CP-0008P original report artifacts via CP-0008R, independently recomputes the CP-0008S comparison and checks it against freshly decoded canonical comparison bytes and the current import view/receipt. It detects independently forged-but-structurally-valid derivative flags when original artifacts are available. It is not callable without both original reports, and a mismatch (or invalid input) must never be interpreted as a live-provider result. No additional third-party dependencies.

## Evidence and limits

The comparison itself intentionally omits full original projections. Stand-alone valid import establishes only V1 *self-consistency*; source authentication, observed RF truth and independent verification of projection-content changes are **not** established without the original reports, and original reports themselves are unauthenticated. SHA-256 is neither a signature nor authenticated provenance. Comparison is historical and neither source statuses nor evidence indicate live provider availability. Cross-store atomicity is not established.

Host-only tests cover exact receipt fidelity, nested selected source and evidence identities, non-ASCII IDs, classifications, source/evidence counts, sorted filtered views, original UTC order, both original-report recomputation (including a deliberately altered yet canonical derivative), reverse/same/empty/selected-absence boundaries, mutation resistance of outer read-only collections, malformed/tampered/incompatible metadata and noncanonical JSON. CI inherits all CP-0008P/Q/R/S/T/U and older host regression gates. Full finalization requires exact-tip green PR CI and main-branch 36 test jobs plus checkpoint finalization, with immutable snapshot and manifest verification.

CP-0003C remains **DEFERRED** until the owner explicitly says `resume CP-0003C`. CP-0004A/B/C remain incomplete. Phone, radio, USB, CAT, audio, credentials, accounts, RF and other manual validation remain excluded.
