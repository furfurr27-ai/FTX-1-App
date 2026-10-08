# CP-0008N — Propagation read-model consistency diagnostics

Parent durable checkpoint: `CP-0008M-PROPAGATION_OPERATING_PICTURE_READ_MODEL`.

## Pure-read contract

`PropagationReadModelConsistencyService.diagnose(picture)` accepts an already constructed CP-0008M `PropagationOperatingPicture`, not provider or store handles. It makes no additional `latest()`, `all()`, network, scheduler or file calls. `PropagationRuntime.operatingPictureWithDiagnostics(query)` captures the picture once with the original runtime API and attaches diagnostics via `withDiagnostics`. Both views carry the same explicit UTC, snapshot identity and captured timestamp.

The model reports the snapshot capture timestamp, age (null for future-dated cache), newest known source-success timestamp and per-source:
- signed source success minus snapshot capture milliseconds;
- signed newest cached retrieval minus last successful source refresh milliseconds;
- BEFORE/EQUAL/AFTER/UNKNOWN relations, with UNKNOWN whenever one timestamp is missing;
- last attempt in the future and cached retrieval in the future, relative to the explicit query UTC;
- source evidence count and separate FRESH/AGING/STALE/FUTURE_DATED counts, inherited from the CP-0008L source-aware freshness classifier;
- aggregate counts of successes newer than the cache, retrievals newer than success, future-dated source attempts and future-dated retrievals.

These comparisons preserve raw source UTC provenance and **do not claim errors** merely from temporal ordering. A successful zero-evidence refresh may follow the last retained cache capture; stale carried-forward observations can predate the latest successful attempt. The model is descriptive, not a live connectivity, provider health, path forecast, RF reading or service-level measurement.

There is **no cross-store atomicity** guarantee: snapshot and source-state stores are read sequentially. `crossStoreAtomicityVerified` is explicitly false. The diagnostics cannot detect a concurrent refresh transaction or reconstruct write generation identifiers absent from the existing store contracts. They label unknown timestamps as UNKNOWN rather than inventing a synchronisation event. Never treat a timestamp comparison as proof of atomic consistency.

## Deterministic host evidence

CI synthetic fixtures exercise missing cache, never-attempted states, BEFORE/EQUAL/AFTER/UNKNOWN timestamp relations, positive/negative/zero delta, mixed fresh/stale/future observations, future-dated attempts/retrievals, filtering independence, stable sorted keys, mismatch validation, one snapshot/state-store read, no re-fetch by diagnostics, file-backed snapshot and state restart, and bit-identical persisted files. Existing operating-picture API, provider/cadence/parser, assessment and storage behaviors are untouched.

## Limits

Pure JVM/host CI only. No rendered Android UI, lifecycle, WorkManager, phone, FTX-1 device, CAT/audio, RF validation, real accounts or credentials; no LoTW/QSO mutation or new upstream propagation source.

CP-0003C remains **DEFERRED** until the explicit instruction `resume CP-0003C`. CP-0004A/B/C remain incomplete.
