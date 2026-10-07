# CP-0008E — Propagation evidence aggregation and offline cache service

Parent durable checkpoint: `CP-0008D-PSK_REPORTER_HEARD_PATH_ADAPTER`.

Evidence level: **host/CI software using only already-normalized deterministic fixtures and synthetic cache fixtures**.

## Objective

Create the provider-neutral orchestration layer that turns normalized propagation evidence into deterministic `PropagationSnapshot` instances and persists bounded snapshot history offline without introducing live transport, Android UI, map rendering, radio control, or opaque propagation scoring.

CP-0008E operates only on evidence already normalized by the durable propagation domain/adapters.

## Inputs

The aggregation service accepts typed provider-neutral collections:

- `SolarGeomagneticObservation`
- `IonosphericMapProduct`
- `HeardPathObservation`
- `ModeledPathEstimate`

Deterministic CI exercises the real normalized outputs of:

- CP-0008B NOAA SWPC planetary Kp / forecast / F10.7 fixtures;
- CP-0008C NOAA SWPC GloTEC fixture;
- CP-0008D PSK Reporter fixture;
- bounded synthetic model/point-ionosphere records used only to cover category separation and cache codec branches.

No live provider request is required.

## Snapshot capture rules

`PropagationSnapshotAggregator` requires:

- non-negative capture UTC;
- at least one input batch;
- at least one evidence item after flattening;
- capture UTC at or after every retained source retrieval UTC.

Provider `observedAtUtcMillis` values are never replaced by snapshot capture time.

Provider `retrievedAtUtcMillis` is retained as provenance.

Missing generation timestamps remain missing. In particular, GloTEC `generatedAtUtcMillis = null` survives aggregation and cache round-trip unchanged.

## Deterministic ordering and identity

Each evidence category is sorted by `evidenceId` before the snapshot is built.

When no caller-supplied snapshot id is provided, FieldOps creates a deterministic id from:

- capture UTC;
- the complete versioned binary encoding of the normalized snapshot content with a fixed placeholder id.

This means input batch ordering cannot change the snapshot id, while a material normalized evidence change does change the fingerprint.

## Repeated-payload deduplication

Repeated provider retrievals commonly contain the same provider observation again.

CP-0008E deduplicates by `evidenceId`.

For solar/geomagnetic, ionospheric and modeled evidence:

- all normalized semantics except retrieval timestamp must match;
- the copy with the latest retrieval UTC is retained;
- conflicting content under the same evidence id fails closed.

For heard paths:

- all normalized semantics except retrieval timestamp and `reportCount` must match;
- the latest retrieval provenance is retained;
- `reportCount` is merged with `max()`, not addition.

Using maximum report count prevents repeated polling of the same PSK Reporter payload from artificially inflating path evidence.

A single evidence id appearing in different evidence categories is rejected.

### NOAA observed-Kp overlap

The pinned NOAA fixtures expose one important orchestration edge case: the dedicated planetary-Kp product and the Kp forecast product can both carry the same normalized observed Kp identity while retaining different product provenance.

CP-0008E does not silently collapse those different source records.

The deterministic orchestration fixture uses:

- the dedicated planetary-Kp feed for observed Kp;
- the forecast feed for estimated/predicted Kp.

If callers provide the same evidence id from two materially different source-provenance records anyway, aggregation fails closed. A later source coordinator must preserve the same canonical-source rule rather than resolving the collision by input order.

## Source-specific freshness defaults

CP-0008E adds FieldOps policy defaults for inspection/orchestration:

| Source | Fresh | Usable/Aging until |
| --- | ---: | ---: |
| PSK Reporter heard paths | 15 min | 60 min |
| NOAA/SWPC GloTEC | 20 min | 60 min |
| NOAA/SWPC planetary Kp family | 3 h | 6 h |
| NOAA/SWPC F10.7 summary | 36 h | 72 h |
| Unknown/future providers | inherited operational default | inherited operational default |

These are **FieldOps policy defaults**, not provider guarantees or scientific uncertainty intervals.

The existing `PropagationFreshnessClassifier` still owns FRESH / AGING / STALE / FUTURE_DATED classification.

Future-dated provider timestamps are not rewritten to make them look current.

## Offline cache

`FilePropagationSnapshotStore` implements the existing `PropagationSnapshotStore` contract.

Properties:

- one versioned local cache file: `propagation-snapshots-v1.bin`;
- deterministic binary codec;
- bounded snapshot history;
- deterministic ordering by capture UTC then snapshot id;
- same-id/different-content rejection;
- latest, exact-id, as-of and history lookup;
- restart/reload preservation;
- smaller configured history limits trim old snapshots deterministically;
- temp-file write plus atomic replacement where supported;
- cache corruption/version mismatch fails closed.

The cache uses no network access and no provider credentials.

## Binary codec

The versioned codec round-trips every field required by the current propagation domain:

- snapshot id/capture UTC;
- source identity/class/version/retrieval URL/terms URL;
- confidence value/basis/explanation;
- quality flags;
- global, point, bounds and path coverage;
- explicit coordinates and Maidenhead positions;
- solar/geomagnetic measurements;
- ionospheric metric, samples, sample confidence/provider quality and generation time;
- heard-path endpoints/frequency/band/mode/SNR/reportCount;
- modeled path frequency limits and input summary.

Decode applies the normal domain constructors, so invalid coordinates, timestamps, confidence values, empty required fields and other domain-invalid content do not bypass validation.

Collection/string sizes are bounded before allocation.

Trailing bytes and unsupported cache versions are rejected.

## Evidence category separation

Aggregation does not create any new:

- heard path;
- modeled path;
- QSO;
- LoTW record;
- confirmation;
- propagation heat score.

Solar/geomagnetic, ionospheric, heard and modeled collections remain distinct.

A context-only aggregated snapshot remains `UNKNOWN` for path usability when assessed without heard/model path evidence.

## Platform and transport boundary

CP-0008E production aggregation/cache code contains no:

- HTTP client;
- provider polling scheduler;
- Android/Compose types;
- Google Maps or Mapbox types;
- USB/radio/PTT state;
- QSO/logbook/LoTW state.

Network scheduling and map/UI presentation remain later concerns.

## Evidence boundary

CP-0008E proves:

- deterministic provider-neutral snapshot aggregation;
- source-specific FieldOps freshness policy selection;
- repeated-payload deduplication;
- preservation of provider timestamps/provenance;
- bounded offline snapshot persistence/restart;
- deterministic codec behavior;
- fail-closed cache corruption/version handling;
- category separation and no automatic propagation score.

It does not prove:

- live provider polling;
- Android filesystem integration;
- cache behavior under abrupt device power loss beyond temp/replace semantics;
- multi-process file locking;
- WSPRnet/GIRO ingestion;
- HFcast/VOACAP prediction;
- real-world propagation accuracy.

## Deferred checkpoint rule

CP-0003C remains **DEFERRED**. Do not return to CP-0003C until the owner explicitly says `resume CP-0003C`.

Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue with the next checkpoint that can be completed entirely through GitHub/CI.
