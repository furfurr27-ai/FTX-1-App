# CP-0008O — Propagation offline diagnostic report payload

Parent verified checkpoint: `CP-0008N-PROPAGATION_READ_MODEL_CONSISTENCY_DIAGNOSTICS`.

## Purpose

Provide a deterministic, platform-neutral **offline payload** for later UI or export adapters, with no new Android UI, file format, JSON serialization, background transport, refresh scheduler, provider or device dependency.

`PropagationOfflineDiagnosticReportService.build(composed)` accepts only a `PropagationOperatingPictureWithDiagnostics` already obtained through the verified CP-0008M/N read model. The new `PropagationRuntime.offlineDiagnosticReport(query)` and explicit-UTC convenience overload capture the operating picture once, attach CP-0008N diagnostics and project the offline report in memory. There are no extra store reads. `schemaVersion=1` permits future consumers to distinguish compatible DTO layouts.

## Report structure

- Snapshot header carries actual snapshot ID, captured UTC, nullable age, future-dated status, offline-cache support if a workspace exists, provider-source count and cached retrieval range. A missing snapshot gives **null**, not invented timestamps, and `workspace=null`.
- Source report rows contain complete original per-source scheduler status **and** original CP-0008N consistency diagnostics, joined by source key and verified role and evidence count. Keys sort deterministically; all configured sources appear even when no cache is available.
- Source summary separates source count, currently ready and failed states, sources with cached evidence, **unfiltered source-attributed cached evidence** from **filtered visible workspace evidence**, timing order counts and future timestamp source counts. The summary always sets `crossStoreAtomicityVerified=false`.
- Workspace sections retain full original filtered heard-path, ionospheric, solar/geomagnetic and modeled-path projections, as well as optional selected-path assessment. No synthetic evidence values or assessment scores are calculated.
- Evidence index contains only **actually visible** filtered workspace projection metadata: evidence type, evidence ID, original source ID, observed UTC, retrieved UTC, existing source-based freshness classifier and future retrieval marker. It contains no hidden cached observations: counts of hidden-but-cached source evidence remain independently inspectable in source rows.
- Invariants reject mismatched source identities/roles/counts, incomplete snapshot identity, invalid schema version, duplicate sources, inaccurate visible evidence totals and false atomic-consistency claims.

## Tests and evidence boundaries

Deterministic Kotlin offline fixtures cover no cache and no configured sources, successful states without evidence, mixed fresh/stale/future observations, filtered-workspace versus original source-cache accounting, failed attempts, signed and unknown timing from existing diagnostics, stable ordering and matching source identities, corruption rejection, exactly one snapshot/state read, repeat determinism, file-backed runtime recreation, unchanged persisted bytes and no network.

This is a typed in-memory DTO, not a serialized CSV/JSON/PDF, rendered UI, live connectivity test, antenna/radio performance metric or live propagation forecast. No phone, FTX-1, hardware, RF, credentials, real accounts, LoTW or transmission features are added. Atomic read consistency across independent stores is not asserted.

CP-0003C remains **DEFERRED** until the owner explicitly says `resume CP-0003C`. CP-0004A/B/C remain incomplete.
