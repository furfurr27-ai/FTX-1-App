# CP-0008R — propagation offline report import boundary

Parent: CP-0008Q-PROPAGATION_OFFLINE_REPORT_DECODE_VALIDATION, verified on main at commit `7278689bb4c8ae4144d9ca2f0f85e42703e51cb8`.

## Scope

The platform-neutral `PropagationOfflineReportImportService.importReport(encoded)` accepts a fully specified CP-0008P canonical V1 artifact containing content type, wire version, JSON, UTF-8 byte count and SHA-256. It calls the CP-0008Q strict decoder before constructing a detached typed offline view. A second canonical reserialization must equal the entire supplied serialized report record. Incompatible, corrupted, duplicate, noncanonical and malformed data fail before publishing an imported view.

The imported view retains the full report, source statuses/diagnostics, selected workspace sections, selected evidence index, original query UTC, snapshot identity/timestamp and source attribution. Its read-only source/evidence lookups operate *only on imported values*, without loading or updating live stores. Absent snapshots, unknown keys, and filtered-out evidence remain absent. No implicit import clock or fresh propagation prediction is introduced.

The receipt states `UNAUTHENTICATED_SELF_CONSISTENT`, `originAuthenticated=false` and `crossStoreAtomicityVerified=false`. SHA-256 and byte count detect corruption, **not malicious forgery or provenance authenticity**. Caller-supplied DTO lists are normal Kotlin read-only interfaces, not deep immutable collections enforced across unsafe casts. A read-only import view is not a transactional data ingest or publication to a live cache.

No filesystem I/O, Android UI/lifecycle, external provider calls, network requests, credentials, real LoTW account, radio or RF. No store write, refresh, or data migration. No new dependencies or changes to the canonical V1 schema.

## Verification

Synthetic host tests cover absent cache, both observed and heard selected evidence, non-ASCII source fields, stable source/ID/kind lookups, preserved source count and original times, detached reconstruction and original store nonmutation, roundtrip canonical byte identity, forged-but-valid artifact remaining unauthenticated, and corruption/version/length/schema/Unicode failure. CP-0008Q decode and CP-0008P serialization golden/negative tests plus all inherited CP-0008A–Q and earlier award/logger/LoTW core gates run as separate CI jobs. The premerge branch builds a temporary checkpoint and verifies its manifest/snapshot hashes. The postmerge main workflow repeats all gates and commits the immutable checkpoint only after success.

## Boundaries

CP-0003C remains **DEFERRED** until explicitly resumed. CP-0004A/B/C remain incomplete. Neither hardware behavior nor account/certificate/RF testing is claimed.
