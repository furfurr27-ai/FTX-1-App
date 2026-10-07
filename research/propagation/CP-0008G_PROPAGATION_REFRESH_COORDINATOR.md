# CP-0008G — Propagation source refresh coordinator

Parent durable checkpoint: `CP-0008F-PROPAGATION_OPERATING_PICTURE_PROJECTION`.

Evidence level: **host/CI software using deterministic fake source fetchers over already-normalized propagation evidence**.

## Objective

Add a platform-neutral orchestration layer above the existing normalized propagation adapters, aggregation/offline-cache layer, and operating-picture projection service.

CP-0008G does not perform HTTP itself. A caller supplies source definitions whose fetchers return already-normalized `PropagationAggregationInput` values or explicit failures.

## Refresh-source contract

Each source definition declares:

- stable source key;
- source role;
- normal refresh cadence;
- initial retry delay;
- maximum retry delay;
- fetch function.

Built-in roles cover:

- NOAA dedicated observed planetary Kp;
- NOAA Kp forecast/estimated product;
- NOAA F10.7;
- NOAA GloTEC VTEC;
- PSK Reporter;
- generic future normalized sources.

The generic role is scoped to a source id equal to its source key.

## canonical NOAA Kp selection

The dedicated NOAA planetary-Kp source owns observed Kp.

The NOAA Kp forecast source may contain provider rows marked observed, estimated, and predicted. CP-0008G removes `NOAA_SWPC_KP_FORECAST_OBSERVED` from the refresh input before aggregation, retaining only estimated/predicted forecast-product evidence. This avoids the already-documented observed-Kp provenance collision and preserves the dedicated Kp feed as the canonical observed source.

No numerical Kp values are rewritten.

## Scheduling and source state

The coordinator tracks per source:

- last attempt UTC;
- last success UTC;
- consecutive failure count;
- next eligible refresh UTC;
- last failure message;
- retryability of the last failure.

Normal success schedules the next attempt at the configured cadence.

Retryable failure uses exponential backoff:

- starts at the configured initial delay;
- doubles after consecutive failures;
- never exceeds the configured maximum delay;
- uses saturating arithmetic;
- caps tracked failure/backoff growth.

Non-retryable normalized-contract failures wait until the normal cadence rather than entering rapid retry.

Successful recovery clears failure metadata and returns to normal cadence.

## Failure isolation

A source exception is converted into an explicit retryable source failure. One failed source does not abort attempts for other eligible sources.

Normalized source output fails closed when:

- evidence provenance falls outside the source's declared role;
- a built-in source returns the wrong evidence category;
- source retrieval provenance is later than the refresh UTC.

Contract rejection is recorded as a non-rapid-retry failure.

## Partial failure and cached evidence

Before writing a refreshed snapshot, the coordinator reads the latest cached snapshot.

For every source that successfully refreshed, previous evidence managed by that source is removed and replaced with the successful normalized result.

Evidence for:

- failed sources;
- cadence-skipped sources;
- sources not managed by this coordinator

is carried forward unchanged, including original provider retrieval timestamps.

This makes stale state age naturally instead of making old evidence appear newly fetched.

A partial refresh therefore remains inspectable while its per-source attempt/state records explicitly show which source failed.

## Last-good snapshot behavior

The last good snapshot is never deleted by a failed refresh cycle.

If all attempted sources fail, no snapshot is written and the previous latest snapshot remains authoritative.

If successful normalized results cannot be aggregated because of an evidence collision or other snapshot invariant, the aggregation failure is returned explicitly and no new snapshot is saved.

If a successful source returns an empty normalized result and removing its old managed evidence would leave no valid nonempty snapshot, no empty snapshot is written; the last good snapshot remains available for inspection while source state records the successful refresh.

## Snapshot persistence

When at least one source succeeds and the resulting combined evidence is nonempty and valid:

1. successful refreshed evidence replaces cached evidence owned by those successful sources;
2. failed/skipped/unmanaged cached evidence is carried forward unchanged;
3. `PropagationSnapshotAggregator` creates the deterministic snapshot;
4. `PropagationSnapshotStore.save` persists it.

This reuses CP-0008E's aggregation and cache invariants instead of creating a second persistence model.

## Determinism

- source definitions are processed by source key;
- source-state results are sorted by source key;
- skipped-source keys are sorted;
- existing deterministic snapshot aggregation handles normalized evidence ordering and identity;
- fake-source tests use explicit times and scripted results.

No wall-clock read occurs inside the coordinator. The caller supplies `nowUtcMillis`.

## Refresh-to-projection bridge

`PropagationRefreshWorkspaceService` composes the refresh coordinator with the existing `PropagationWorkspaceProjectionService`.

A single caller-supplied UTC is used for both refresh and projection. After a successful refresh, the newly saved snapshot is projected. After an all-source or aggregate failure, the projection service reads the still-authoritative last good snapshot instead.

The bridge therefore exposes source-health/refresh state and operating-picture state together without teaching the projection layer how to fetch or schedule providers.

## Platform boundary

Production CP-0008G refresh code contains no:

- Android or Compose types;
- WorkManager;
- concrete HTTP client;
- `java.net` transport;
- Google Maps/Mapbox;
- FTX-1, USB, CAT, audio, RF, or PTT behavior;
- account credentials, certificates, passwords, or API keys;
- QSO/logbook/LoTW mutation.

A later Android/application layer may adapt real transport and scheduling into these interfaces.

## Evidence boundary

CP-0008G proves deterministic source orchestration semantics around already-normalized evidence. It does not prove live network access, provider uptime, Android background execution, battery behavior, new provider schemas, or real-world propagation accuracy.

The existing adapter checkpoints remain the evidence for NOAA SWPC, GloTEC, and PSK Reporter parsing/schema normalization.

## Deferred checkpoint rule

CP-0003C remains **DEFERRED**. Do not return to CP-0003C until the owner explicitly says `resume CP-0003C`.

CP-0004A/B/C remain incomplete hardware checkpoints. Skip phone/radio, real account/credential/certificate, RF, and manual hardware work while the owner override remains active.
