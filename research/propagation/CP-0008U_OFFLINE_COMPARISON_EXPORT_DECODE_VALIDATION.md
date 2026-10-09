# CP-0008U — Offline Comparison Export Decode Validation

Parent immutable checkpoint: CP-0008T-PROPAGATION_OFFLINE_COMPARISON_EXPORT_CONTRACT, durable main SHA 91ec7443e32038c220401cf0dbb03b017918efff.

## Boundary and algorithm

`PropagationOfflineReportComparisonDecoder.decode(PropagationOfflineSerializedComparison)` accepts a complete CP-0008T V1 comparison export record. It checks the exact comparison-specific media type, wireVersion=1, bounded and well-formed UTF-16 source text, UTF-8 length at most 16 MiB, expected exact byte count and lower-case 64-hex SHA-256, comparing the SHA-256 digest using constant-time byte comparison. It then reuses the already-proven CP-0008Q bounded scanner and strict typed DTO constructor via a deliberately narrow internal comparison decode entrypoint. The outer JSON must have exactly `format`, `payload`, `wireVersion` with the expected comparison format and numeric version.

Decoded DTOs are revalidated through CP-0008T's unchanged serializer, and exact byte-for-byte V1 reserialization, including attached record metadata, is required. This disallows structurally invalid envelopes, unknown/missing fields, unknown enums, duplicate keys, excessive nesting/collection sizes, noncanonical encoding, altered receipts, unsorted/incorrectly classified source and selected-evidence rows, disallowed authenticated/atomicity assertions and malformed/inconsistent metadata. `decodeCanonical(json)` is the explicitly lower-level canonical-only entrypoint with no external integrity receipt and no authentication claim.

## Meaning and limitations

The returned typed comparison preserves both original CP-0008R report receipts (including original SHA-256, byte count, timestamp and declared selected-view counts), query-time ordering, nested source status/timing DTOs, selected evidence attribution and IDs, and distinct selected metadata vs projection-content change flags. Selected-view absence is not proof that evidence was deleted at the source. Original source status and freshness are historical reported observations, not live provider or RF conditions. The derivative comparison does not contain enough projection content to independently recompute all changes; both original CP-0008P reports are required. SHA-256 without a secret/signature verifies unkeyed consistency, not authenticated origin. It does not establish cross-store atomicity.

## Host-only verification

`scripts/test_propagation_offline_comparison_decoder.sh` runs focused Kotlin/JVM tests for byte-exact round trips, nested DTOs, reversed/same query order, missing evidence, full source/evidence IDs and change semantics, tampered metadata and rebased checksums, malformed/truncated/noncanonical/incompatible JSON, Unicode, size and nesting bounds. Inherited CP-0008T/P/Q/R/S and other host regression jobs remain in the GitHub Actions matrix. A premerge dry-run is not a durable checkpoint; only exact-tip green PR CI followed by full main-branch finalizer success and immutable manifest/snapshot verification promotes CP-0008U.

CP-0003C remains **DEFERRED** until the owner explicitly requests "resume CP-0003C". CP-0004A/B/C remain incomplete. No real phone, FTX-1, RF, certificates, credentials, accounts or manual testing is asserted.
