# CP-0008C — NOAA SWPC GloTEC public ionospheric map adapter

Parent durable checkpoint: `CP-0008B-NOAA_SWPC_PROPAGATION_ADAPTER`.

Evidence level: **host/CI software + official NOAA/NWS product research + bounded deterministic recorded-source fixture**.

## Objective

Exercise the CP-0008A provider-neutral ionospheric-map boundary against NOAA/SWPC GloTEC without turning TEC into an invented amateur-HF path prediction.

CP-0008C is deliberately limited to vertical total electron content (VTEC) expressed in TECU.

It does not add Android rendering, network transport, MUF inference, or a propagation heat-map score.

## Sources actually checked

See `research/propagation/PROPAGATION_SOURCES.tsv`.

### Direct official NOAA/NWS evidence

The NWS GloTEC transition-to-operations service change notice documents:

- GloTEC as a real-time global ionospheric total-electron-content product;
- assimilation of ground- and space-based slant TEC into the background ionosphere;
- an ASCII GeoJSON TEC product;
- 10-minute cadence;
- a global 2.5-degree latitude by 5-degree longitude grid.

The current NOAA/SWPC GloTEC product page documents the output products and the provider `quality_flag`. NOAA describes that flag as based on the mean number of F-region observations in each vertical profile, rounded up and capped at five.

The public NOAA/SWPC GeoJSON directory exposes versioned artifacts named:

`glotec_icao_YYYYMMDDTHHMMSSZ.geojson`

The fixture source artifact for this checkpoint is:

`https://services.swpc.noaa.gov/products/glotec/geojson_2d_urt/glotec_icao_20260909T151500Z.geojson`

The moving index at `geojson_2d_urt.json` is not part of deterministic CI. During CP-0008C research it returned an empty array through the research client, so the checkpoint does not make correctness depend on that moving service.

### Secondary recorded-fixture evidence

The complete source artifact is large and time-versioned. A public repository, `RealDougEubanks/solarham`, retains a recorded copy of the same 2026-09-09T15:15:00Z GloTEC grid at pinned commit:

`7382dc23cd5f74c00fae99387d2052940c5c63b7`

CP-0008C uses that pinned recorded copy only to extract four exact feature rows into a bounded deterministic fixture.

The mirror is **not** authoritative for GloTEC semantics. NOAA/NWS remains authoritative for product purpose, cadence, grid, units, and quality interpretation.

## Pinned GeoJSON schema

The pinned generation is identified inside FieldOps as:

`glotec-operational-geojson-v1`

The bounded source record contains:

- root:
  - `type`
  - `time_tag`
  - `cadence`
  - `metadata`
  - `features`
- metadata variable information including:
  - `tec.units = TECU`
  - numeric TEC minimum/maximum
- each feature:
  - `type = Feature`
  - `geometry.type = Point`
  - GeoJSON `coordinates = [longitude, latitude]`
  - properties:
    - `tec`
    - `anomaly`
    - `hmF2`
    - `NmF2`
    - `quality_flag`

CP-0008C normalizes **only TEC** into the provider-neutral propagation domain.

The additional feature properties are schema-checked so format drift fails visibly, but CP-0008C does not silently turn them into new domain meanings.

## Provider-neutral metric extension

`IonosphericMetric.VTEC_TECU` is added.

This is intentionally distinct from:

- `FOF2_MHZ`
- `MUF_MHZ`
- `HMF2_KM`

TEC is not renamed or reinterpreted as foF2 or MUF.

The generic `IonosphericSample` gains optional provider-neutral quality metadata:

- `providerQualityCode`
- `providerQualityExplanation`

They must either both be present or both be absent.

This lets the GloTEC quality flag survive normalization without hard-coding a GloTEC-specific field into `IonosphericMapProduct`.

## Time semantics

GloTEC supplies `time_tag`.

FieldOps retains that as the product observation/validity timestamp and also requires the canonical artifact filename timestamp to match it.

Retrieval UTC remains a separate caller-supplied value in `PropagationSourceRef`.

No separately documented generation timestamp was established from the pinned GeoJSON schema. Therefore:

`generatedAtUtcMillis = null`

FieldOps does not invent a generation timestamp from retrieval time or device time.

## Geographic semantics

GeoJSON point coordinates are parsed in the required order:

`[longitude, latitude]`

Every sample becomes a `PropagationPosition` with:

`PropagationLocationMethod.PROVIDER_COORDINATE`

No geography is derived from a callsign.

The product coverage bounds are calculated from the actual sample coordinates in the parsed payload.

The bounded fixture is intentionally not presented as full-global coverage. A future transport/cache layer parsing the complete 72 x 72 operational grid will naturally produce bounds from that complete grid instead.

## Quality and confidence

Each GloTEC sample retains the exact integer provider `quality_flag` from 0 through 5 plus a human-readable explanation.

The product is normalized as:

- source class: `DERIVED_PRODUCT`
- data quality: `ESTIMATED`
- FieldOps confidence: 0.70
- confidence basis: `DERIVED`

The 0.70 value is a FieldOps adapter policy marker, **not a NOAA forecast probability or scientific accuracy score**.

A quality flag of zero is preserved rather than discarded. It is provider evidence about assimilation support; CP-0008C does not rewrite it as a TEC value, path score, or QSO state.

## Bounded deterministic fixture

Manifest:

`research/propagation/NOAA_SWPC_GLOTEC_FIXTURE.json`

Fixture:

`research/propagation/fixtures/noaa_swpc_glotec_20260909T151500Z_bounded.geojson`

Properties:

- source time: 2026-09-09T15:15:00Z
- four exact source features
- byte size: 1,573
- SHA-256: `a98741d9a9586082db0eb357f3baf35be09a2646c8ab5b1b4203d4852b09bac2`

The bounded fixture is immutable parser evidence. It is not a claim to contain the complete GloTEC globe.

## Fail-closed behavior

CP-0008C rejects:

- non-NOAA or noncanonical artifact URLs;
- filename timestamp / `time_tag` mismatch;
- malformed JSON or duplicate JSON keys;
- unexpected root/feature/geometry/property fields;
- non-FeatureCollection roots;
- cadence other than the pinned 10 minutes;
- missing TEC metadata or units other than TECU;
- malformed/non-Point geometry;
- coordinate arrays that are not exactly longitude/latitude;
- coordinates outside valid geographic domains;
- duplicate grid coordinates;
- quoted or otherwise nonnumeric TEC;
- nonpositive TEC under the inherited normalized-sample contract;
- TEC outside provider metadata bounds;
- noninteger or out-of-range quality flags;
- nonnumeric schema-pinned anomaly/hmF2/NmF2 fields.

Failing closed is preferable to silently changing what the propagation map means.

## Path-assessment separation

GloTEC VTEC is ionospheric context.

It does **not** create:

- a `HeardPathObservation`;
- a `ModeledPathEstimate`;
- a QSO;
- a LoTW record;
- a MUF;
- a GOOD/MARGINAL/POOR path score.

The inherited `PropagationAssessmentEngine` can report that fresh ionospheric context exists, but GloTEC alone leaves path usability `UNKNOWN` with insufficient path evidence.

A later scientifically justified model may consume TEC as one input. That model must be a separate checkpoint with explicit assumptions and tests.

## Transport boundary

`NoaaSwpcGlotecAdapter` accepts:

- already-fetched JSON text;
- caller-supplied retrieval UTC;
- exact source artifact URL.

It performs no network access.

This keeps offline snapshots/cache adapters possible and prevents parser tests from depending on NOAA availability.

## Evidence boundary

CP-0008C proves:

- deterministic normalization of the pinned GloTEC GeoJSON structure into VTEC/TECU map evidence;
- explicit coordinates and source provenance;
- separate provider observation and retrieval timestamps;
- preservation of provider quality metadata;
- deterministic bounded fixture integrity;
- fail-closed schema behavior;
- continued separation of ionospheric context from paths and QSOs.

It does not prove:

- NOAA endpoint uptime;
- the complete current GloTEC global grid at runtime;
- a TEC-to-MUF conversion;
- HF path prediction from TEC;
- Android network/cache integration;
- Android/Compose/map rendering;
- GIRO, PSK Reporter, WSPRnet, HFcast, or VOACAP integration.

## Deferred checkpoint rule

CP-0003C remains **DEFERRED**. Do not return to CP-0003C until the owner explicitly says `resume CP-0003C`.

Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue with the next checkpoint that can be completed entirely through GitHub/CI.
