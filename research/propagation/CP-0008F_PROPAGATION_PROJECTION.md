# CP-0008F — Propagation operating-picture projection service

Parent durable checkpoint: `CP-0008E-PROPAGATION_AGGREGATION_OFFLINE_CACHE`.

Evidence level: **host/CI software over already-normalized propagation snapshots and stores**.

## Objective

Expose the propagation snapshot/cache layer as deterministic, platform-independent workspace projection state for a future operating map without introducing Android UI or map rendering.

## Projection categories

CP-0008F keeps these categories distinct:

- one-way heard RF paths;
- ionospheric products;
- solar/geomagnetic context;
- modeled paths;
- optional selected-path assessment from the existing explainable assessment engine.

No heard path is promoted into a QSO, confirmation, LoTW record, or opaque heat score.

## Heard-path projection

Each projected heard path retains:

- evidence id;
- source provenance;
- source retrieval time;
- source-specific freshness;
- confidence and quality;
- transmitter and receiver explicit positions;
- callsigns/labels;
- exact frequency;
- band;
- mode;
- optional SNR;
- reportCount.

Band, mode and frequency-range filters apply only to heard-path records because those fields are explicitly present there. The filters do not silently reinterpret solar, ionospheric, or modeled evidence.

## Ionospheric projection

Each ionospheric record preserves:

- source provenance/freshness;
- coverage;
- metric identity;
- exact samples;
- sample position/confidence/provider quality;
- optional reference distance;
- optional provider generation timestamp.

VTEC/TECU remains VTEC/TECU. Projection does not interpolate TEC into MUF or a generic path score.

## Solar/geomagnetic context

Projected context preserves the explicit values already present in the snapshot, including F10.7, Kp, Ap, sunspot number, and X-ray flux when available.

No solar/geomagnetic field is treated as a directly observed RF path.

## Modeled paths

Modeled paths remain separate from heard paths and preserve:

- explicit origin/destination;
- optional MUF/LUF limits;
- model-input summary;
- source provenance/freshness;
- confidence/quality.

## Freshness and provenance

Projection uses the source-specific FieldOps defaults introduced by CP-0008E.

It exposes:

- evidence freshness;
- provider retrieval time through the source reference;
- non-negative retrieval age when retrieval is not future-dated;
- an explicit future-retrieval flag with null age rather than a negative age.

Snapshot status exposes:

- snapshot id/capture time;
- non-negative snapshot age or explicit future-dated snapshot state;
- source count;
- oldest/newest source retrieval timestamps;
- whether any underlying evidence is stale;
- whether any underlying evidence is future-dated;
- whether the backing store declares offline persistence support.

The projection remains inspectable when stale or future-dated; it is not rewritten to appear current.

## Filters

Deterministic filters support:

- band;
- minimum/maximum frequency;
- mode;
- source id;
- freshness state.

Source/freshness filters apply to all evidence categories.

Band/mode/frequency filters apply only where those exact fields exist: heard-path evidence.

## Selected-path assessment

A projection query may include one `PropagationAssessmentQuery`.

The existing `PropagationAssessmentEngine` runs against the complete snapshot, not the display-filtered subset. This preserves its evidence/reason links and prevents a UI filter from silently changing the assessment result.

The selected-path assessment UTC must match the projection UTC.

## Snapshot-store access

`PropagationWorkspaceProjectionService` supports:

- latest snapshot;
- exact snapshot id;
- latest snapshot at or before a supplied UTC;
- direct projection of an already-retrieved snapshot.

`FilePropagationSnapshotStore` now implements the marker `OfflinePropagationSnapshotStore`, allowing workspace state to report that an offline-persistent cache is available without coupling the projection service to the file-backed implementation class.

## Determinism

Each projected category is sorted newest-observation-first with evidence id as the deterministic tie-break.

Reordering input lists in an otherwise identical snapshot does not change projection output.

## Platform boundary

Production CP-0008F projection code contains no:

- Android or Compose types;
- Google Maps or Mapbox types;
- HTTP/network clients;
- provider polling;
- USB/radio/PTT control;
- QSO/logbook/LoTW state;
- map-rendering SDK objects.

## Evidence boundary

CP-0008F proves a provider-neutral, deterministic projection/application layer suitable as input to a later map/workspace renderer.

It does not prove:

- Android UI/rendering;
- map-SDK integration;
- geographic interpolation of Maidenhead grids;
- live provider polling;
- WSPRnet/WSPR.live or GIRO ingestion;
- HFcast/VOACAP prediction;
- real-world propagation accuracy;
- phone/radio/RF behavior.

## Deferred checkpoint rule

CP-0003C remains **DEFERRED**. Do not return to CP-0003C until the owner explicitly says `resume CP-0003C`.

Skip phone/radio, real credential/certificate/account, RF, and manual hardware checkpoints and continue only through GitHub/CI-only work.
