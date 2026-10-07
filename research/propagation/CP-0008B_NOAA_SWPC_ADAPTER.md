# CP-0008B — NOAA SWPC public propagation source adapter

Parent durable checkpoint: `CP-0008A-PROPAGATION_INTELLIGENCE_FOUNDATION`.

Evidence level: **host/CI software + pinned public-source fixtures + live schema-only validation**.

## Objective

Add the first real public propagation-context provider to FieldOps without weakening the CP-0008A separation between provider transport, normalized evidence, observed RF paths, model output, and the authoritative QSO logbook.

CP-0008B is deliberately narrow:

- NOAA/SWPC planetary Kp;
- NOAA/SWPC Kp observed/estimated/predicted status feed;
- NOAA/SWPC 10.7 cm (F10.7) solar radio flux summary.

No account, API key, phone, radio, RF transmission, Android UI, or map SDK is required.

## Sources actually checked

See `research/propagation/PROPAGATION_SOURCES.tsv`.

The implementation was grounded in official NOAA/NWS sources:

- SWPC public product directory;
- `noaa-planetary-k-index.json`;
- `noaa-planetary-k-index-forecast.json`;
- `products/summary/10cm-flux.json`;
- NWS Service Change Notice 26-21;
- NOAA F10.7 technical/verification documentation.

## 2026 JSON schema boundary

NWS Service Change Notice 26-21 announced a data-format change effective on or about 2026-03-31 for several SWPC JSON products.

For the products used here, the important compatibility boundary is:

- legacy form: first row contained field names and subsequent rows contained values, often quoted;
- post-change form: standard JSON objects with explicit key/value pairs;
- non-`time_tag` data values are numeric rather than quoted.

FieldOps pins this generation as:

`swpc-json-post-scn26-21-v1`

The parser intentionally rejects quoted numeric fields and unexpected fields so silent schema drift becomes a visible failure instead of corrupted propagation state.

## Pinned endpoints

### Planetary Kp history

`https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json`

Expected flat-object fields:

- `time_tag`
- `Kp`
- `a_running`
- `station_count`

FieldOps normalizes `Kp` only into `SolarGeomagneticObservation.planetaryKp`.

`a_running` and `station_count` are retained in the NOAA adapter record as provider metadata. `a_running` is **not** silently promoted to `planetaryAp`, because that would assert a semantic equivalence not established by this checkpoint.

### Planetary Kp observed/estimated/predicted feed

`https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json`

Expected flat-object fields:

- `time_tag`
- `kp`
- `observed`
- `noaa_scale`

The confusing provider field name `observed` contains three state values in the live product:

- `observed`
- `estimated`
- `predicted`

FieldOps preserves those as `NoaaSwpcRecordStatus.OBSERVED`, `ESTIMATED`, and `PREDICTED`.

Provider provenance remains distinct:

- observed -> `DERIVED_PRODUCT`
- estimated -> `DERIVED_PRODUCT` + `ESTIMATED` quality
- predicted -> `FORECAST` + `ESTIMATED` quality

Predicted timestamps can legitimately be later than retrieval time. They are retained as provider validity/observation timestamps; FieldOps does not replace them with device time.

### F10.7 summary

`https://services.swpc.noaa.gov/products/summary/10cm-flux.json`

Expected flat-object fields:

- `flux`
- `time_tag`

The value is normalized into `f107SolarFluxSfu`.

NOAA describes F10.7 as the 10.7 cm / 2800 MHz full-Sun radio flux and expresses it in solar flux units (sfu), where 1 sfu is 10^-22 W m^-2 Hz^-1.

This is solar/ionospheric context, not direct proof that a particular amateur-HF path is open.

## Provenance and confidence policy

All normalized records retain:

- exact NOAA source URL;
- provider/source identity;
- schema generation string;
- caller-supplied retrieval UTC;
- provider `time_tag` converted as UTC;
- source class;
- explicit confidence basis/explanation;
- quality metadata.

Confidence numbers are FieldOps adapter policy, not NOAA-issued probabilities:

- provider observed: 0.90;
- provider estimated: 0.75;
- provider predicted: 0.65.

They are intentionally exposed with `PROVIDER_REPORTED` basis and human-readable explanation so they cannot be confused with scientific forecast skill scores.

## Deterministic fixtures

Manifest: `research/propagation/NOAA_SWPC_FIXTURES.json`.

Pinned representative captures:

- historical Kp fixture SHA-256 `a5e3aed635b9fc26d98326fbcbbc75fe8eaaac1a1cbf41f20f42b6f2d1243cda`;
- status-aware Kp fixture SHA-256 `fedebb18935589bf394cd6bd55ae54ce871379722aca2ef6b5ce9c84adf736ba`;
- F10.7 summary fixture SHA-256 `5b16306eb6f466b127b6ac32f293559284268928011df7c4f6bb949792f7c675`.

The fixtures are exact representative rows captured on 2026-10-07. They are immutable parser evidence, **not** assertions about current space-weather conditions.

## Live schema-freshness check

`scripts/check_noaa_swpc_schema.py` performs a no-credential HTTPS fetch from the three pinned NOAA endpoints and checks only structural invariants:

- top-level JSON array;
- expected exact field sets;
- numeric-vs-string types;
- accepted observed/estimated/predicted labels;
- parseable timestamps;
- valid basic numeric domains.

It does not assert today's Kp or F10.7 values.

The deterministic parser gate does not require the network. Branch CI runs the live schema guard separately so provider drift is visible before merge.

## Fail-closed normalization

CP-0008B rejects:

- malformed JSON;
- duplicate keys;
- missing required fields;
- unexpected extra fields;
- quoted numeric fields from the retired schema generation;
- unknown Kp status labels;
- invalid provider timestamps;
- nonpositive F10.7;
- Kp outside the normalized 0..9 domain;
- invalid NOAA G-scale labels.

No missing provider timestamp is replaced with current/device time.

## Separation boundaries

The NOAA adapter:

- returns normalized `SolarGeomagneticObservation` records;
- does not create `HeardPathObservation`;
- does not create or mutate QSOs;
- does not touch LoTW;
- does not calculate a universal path score;
- does not own network transport;
- contains no Android/Compose/map SDK types;
- contains no FTX-1/PTT behavior.

The source URLs are metadata. A later network/cache adapter may fetch them and pass bytes plus retrieval time into this parser.

## Evidence boundary

CP-0008B proves:

- the post-SCN NOAA schemas can be normalized into CP-0008A solar/geomagnetic context;
- observed/estimated/predicted Kp distinctions survive normalization;
- provider timestamps and retrieval timestamps remain separate;
- F10.7 units/provenance are explicit;
- deterministic captured fixtures and hashes exist;
- malformed/schema-drifted input fails closed;
- transport remains outside the core adapter.

It does not prove:

- NOAA endpoint uptime;
- scientific calibration of the FieldOps confidence policy;
- current real-world propagation conditions;
- a path prediction from Kp/F10.7 alone;
- GIRO, PSK Reporter, WSPRnet, HFcast/VOACAP, or other providers;
- Android network caching or UI rendering.

## Next-step selection

The next GitHub/CI-only propagation checkpoint is intended to exercise the CP-0008A ionospheric-map boundary against another public no-credential source:

**CP-0008C — NOAA SWPC GloTEC public ionospheric map adapter.**

That checkpoint should pin the official GloTEC GeoJSON schema, extend the provider-neutral ionospheric metric set only as required for TEC, preserve source/generation/validity metadata, and use captured deterministic fixtures. It must not derive MUF or path usability from TEC without an explicit scientifically justified model.

## Deferred checkpoint rule

CP-0003C remains **DEFERRED**. Do not return to CP-0003C until the owner explicitly says `resume CP-0003C`.

Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue with the next checkpoint that can be completed entirely through GitHub/CI.
