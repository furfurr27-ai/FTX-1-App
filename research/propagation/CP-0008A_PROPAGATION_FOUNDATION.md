# CP-0008A — Propagation intelligence domain and source-normalization foundation

Parent durable checkpoint: `CP-0007C-US_STATE_GEOMETRY_PACK`.

Evidence level: **host/CI software plus authoritative-source research; synthetic data only**.

## Objective

Establish a provider-neutral propagation domain that can later accept observed RF paths, solar/geomagnetic context, ionospheric map products, and path-model output without hard-coding any live provider into UI code.

The product requirement remains the operational question:

> From where I am, what bands and directions look useful right now?

CP-0008A does not answer that question from live Internet data yet. It creates the normalized evidence, freshness, cache, and explainable assessment contracts needed to answer it safely later.

## Sources actually checked

See `research/propagation/PROPAGATION_SOURCES.tsv`.

### NOAA SWPC

Checked official NOAA/SWPC documentation for:

- K/Kp semantics;
- the NOAA geomagnetic storm scale;
- the current public product directory.

NOAA identifies Kp 5 as the G1 geomagnetic-storm threshold. CP-0008A therefore permits an explicit **geomagnetic-storm caution** reason when a normalized planetary Kp is at or above 5.

That reason is contextual only. Kp never becomes a fabricated per-path observation and never automatically overrides fresh direct RF-path evidence.

### GIRO / Lowell GIRO Data Center

Checked official GIRO pages for:

- near-real-time ionospheric products;
- foF2/IRTAM-style map concepts;
- MUF products;
- Rules of the Road / licensing;
- GAMBIT access.

The GIRO MUF interface explicitly includes a reference distance. CP-0008A therefore requires `referenceDistanceKm` for a normalized MUF map product rather than allowing an ambiguous free-floating MUF number.

GIRO/LGDC data also carries explicit CC BY-NC-SA and provider-acknowledgement/access conditions. CP-0008A therefore includes **no GIRO data, account access, scraping, or live adapter**. Any future live GIRO integration is a separately reviewed provider checkpoint.

## Provider-neutral source/provenance model

`PropagationSourceRef` retains:

- source id;
- provider name;
- source class;
- source/product version;
- retrieval UTC;
- optional authoritative HTTPS source URL;
- optional HTTPS terms URL.

Source classes are:

- `MEASUREMENT`
- `DERIVED_PRODUCT`
- `MODEL`
- `FORECAST`
- `SYNTHETIC_FIXTURE`

Synthetic fixtures are not allowed to carry a fake live-provider URL.

## Confidence and quality

Every propagation evidence record carries:

- explicit `PropagationConfidence` in [0,1];
- confidence basis;
- human-readable confidence explanation;
- one or more quality flags.

Quality and confidence remain separate from the eventual path-usability state.

This prevents a future provider's opaque number from silently becoming FieldOps truth.

## Explicit geography

Propagation locations require either:

- coordinates; or
- a valid 4/6/8-character Maidenhead locator.

Location method/provenance is explicit.

A callsign by itself is **not** a geographic location and cannot construct `PropagationPosition`.

Coverage can be:

- global;
- point;
- rectangular bounds;
- explicit path.

No callsign-to-location inference exists in CP-0008A.

## Freshness

`PropagationFreshnessPolicy` separates:

- `FRESH`
- `AGING`
- `STALE`
- `FUTURE_DATED`

The default operational policy is intentionally configurable:

- fresh: <=15 minutes
- usable/aging: <=60 minutes
- older: stale

These are FieldOps cache/evidence-age defaults, not scientific claims about how quickly the ionosphere changes.

Future-dated evidence is never treated as current evidence.

## Solar/geomagnetic context

`SolarGeomagneticObservation` can normalize:

- F10.7 solar radio flux;
- planetary Kp;
- planetary Ap;
- sunspot number;
- X-ray flux.

At least one value is required.

This context is explicitly **not an observed RF path**.

Kp >=5 may produce an explainable caution flag, grounded in the NOAA G1 threshold, but it does not alone decide a path.

## Ionospheric map products

`IonosphericMapProduct` supports provider-neutral samples for:

- `FOF2_MHZ`
- `MUF_MHZ`
- `HMF2_KM`

Each product retains:

- source/provenance;
- observation/validity time;
- optional generation time;
- geographic coverage;
- confidence/quality;
- sample positions and values.

MUF requires an explicit positive reference distance.

CP-0008A deliberately does **not** interpolate these map samples into a path prediction. A future provider/model adapter must define and test that behavior separately.

## Observed/heard RF paths

`HeardPathObservation` retains:

- independent evidence id;
- source/provenance;
- observation time;
- transmitter and receiver with explicit locations;
- exact frequency;
- normalized band;
- mode;
- optional SNR;
- report count;
- confidence and quality.

These observations are intentionally separate from `QsoRecord`, LoTW state, and award confirmation.

A WSPR reception, PSK Reporter report, or FieldOps decode can later become a normalized heard-path observation without becoming a two-way QSO.

## Modeled path estimates

`ModeledPathEstimate` is separate from heard-path evidence.

It requires:

- MODEL / FORECAST / SYNTHETIC provenance;
- explicit path endpoints;
- at least one explicit MUF or LUF limit;
- confidence/quality;
- a human-readable model-input summary.

This preserves the README requirement that observed RF paths and modeled propagation remain distinct.

## Explainable usability assessment

`PropagationAssessmentEngine` produces:

- `GOOD`
- `MARGINAL`
- `POOR`
- `UNKNOWN`

alongside:

- selected evidence confidence;
- explicit structured reasons;
- exact evidence ids used.

Current conservative rules:

- fresh matching observed/heard path -> GOOD;
- aging matching observed/heard path -> MARGINAL;
- model-only path with selected frequency within explicit model limits -> MARGINAL;
- selected frequency above model MUF or below model LUF -> POOR;
- no current observed/model path evidence -> UNKNOWN.

Solar/geomagnetic and ionospheric-map context can add explanations but cannot masquerade as observed RF-path proof.

A fresh observed path remains GOOD even if Kp>=5; the geomagnetic condition is shown as a caution reason rather than silently overriding direct evidence.

## Offline snapshot/cache contract

`PropagationSnapshot` groups normalized evidence at a capture time while preserving every evidence record's own observation and retrieval metadata.

`PropagationSnapshotStore` defines an offline-capable storage boundary:

- save;
- latest;
- lookup by id;
- latest at-or-before a time;
- bounded history.

`InMemoryPropagationSnapshotStore` is the deterministic host implementation used for CP-0008A. Android/file/database persistence remains an adapter concern.

Duplicate snapshot ids with different content fail closed.

## Synthetic-only test policy

Every CP-0008A test fixture uses `SYNTHETIC_FIXTURE` provenance.

No test:

- calls NOAA;
- calls GIRO;
- calls PSK Reporter;
- calls WSPRnet;
- uses an API key;
- uses a user account;
- requires a phone;
- requires the FTX-1;
- transmits RF.

Official-source research informs terminology and guardrails only.

## Evidence boundary

CP-0008A proves:

- provider-neutral propagation domain validation;
- explicit provenance/retrieval/observation/freshness/confidence/quality metadata;
- solar/geomagnetic normalization;
- ionospheric map-product normalization;
- observed/heard path evidence distinct from QSOs;
- modeled path estimates distinct from observations;
- explainable path assessment;
- offline snapshot/cache interfaces;
- deterministic synthetic CI behavior.

It does **not** prove:

- a live NOAA adapter;
- a live GIRO adapter;
- PSK Reporter/WSPRnet access;
- HFcast/VOACAP prediction output;
- a scientifically calibrated universal path score;
- Android rendering;
- network caching on-device;
- current real-world propagation conditions.

## Next-step selection

After CP-0008A, continue with the next GitHub/CI-only propagation checkpoint. The intended next step is to add a real **public, no-credential source adapter** with recorded schema/source version and deterministic captured fixtures, while leaving credentialed/restricted providers separately gated.

## Deferred checkpoint rule

CP-0003C remains **DEFERRED**. Do not return to CP-0003C until the owner explicitly says `resume CP-0003C`.

Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue with the next checkpoint that can be completed entirely through GitHub/CI.
