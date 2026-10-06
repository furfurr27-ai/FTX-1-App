# CP-0007C — Production U.S. state offline geometry pack

Parent durable checkpoint: `CP-0007B-AWARD_GEOMETRY_PROVIDERS`.

Evidence level: **host/CI software plus official U.S. Census source artifact**.

## Objective

Build and pin the production offline geometry pack for the 50 U.S. states used by ARRL Worked All States, using the CP-0007B provider contract and the official Census 2025 national States 1:20,000,000 boundary artifact.

CP-0007C contains real Census-derived state geometry. Synthetic CP-0007B fixtures remain separate and are never substituted for production data.

## Official source actually checked

Census 2025 Cartographic Boundary Files page:

- `https://www.census.gov/geographies/mapping-files/2025/geo/carto-boundary-file.html`

Census filename convention:

- `https://www2.census.gov/geo/tiger/GENZ2025/2025_file_name_def.pdf`

The official page publishes national States data at 1:500,000, 1:5,000,000, and 1:20,000,000. CP-0007C intentionally uses the compact 1:20,000,000 KML artifact.

Pinned upstream artifact:

- filename: `cb_2025_us_state_20m.zip`
- format: Census KML ZIP
- URL: `https://www2.census.gov/geo/tiger/GENZ2025/kml/cb_2025_us_state_20m.zip`
- vintage: 2025
- scale: 1:20,000,000
- retrieved: 2026-10-06
- byte size: **158,017**
- SHA-256: `efddd884f1442ef233b1ba9c12dddbd66b6fdf94da6a373e1556aefe3dbc5751`

Rights/attribution policy remains inherited from CP-0007B:

- U.S. Government work
- U.S. copyright unavailable under 17 U.S.C. §105
- Census Bureau source attribution requested
- Census cartographic boundaries are statistical mapping products, not legal land descriptions

## Source feature scope

The upstream KML contains **52 placemarks**.

The deterministic builder emits exactly the **50 states in the verified ARRL WAS target universe**.

Two upstream state/equivalent records are excluded from the WAS pack.

FieldOps also explicitly excludes these non-50-state identities from the WAS geometry universe:

- DC
- PR
- AS
- GU
- MP
- VI

The generated state identity set is compared directly to the official award catalog's existing 50-state WAS universe in host tests.

## Production pack representation

The first implementation generated approximately 1 MB of Kotlin coordinate-constructor source.

That representation was rejected after Kotlin 1.9.22 host compilation exhausted the compiler heap while analyzing the giant generated initializer.

No geometry evidence failed.

CP-0007C therefore stores production coordinates as an offline resource:

- `core/src/main/resources/dev/n0png/fieldops/maps/us_states_2025_20m.pack`

The pack is a deterministic canonical line format:

- `STATE<TAB>XX`
- `POLYGON<TAB>index<TAB>O:lon,lat;...|H:lon,lat;...`

This is parsed by the small platform-independent:

- `Census2025UsState20mGeometryPack`

Generated metadata is kept separately in:

- `Census2025UsState20mPackMetadata.kt`
- `research/maps/US_STATE_2025_20M_PACK.json`

This keeps the app's compiled code small while leaving the actual geometry offline and replaceable.

## Deterministic builder

`scripts/build_us_state_geometry_pack.py`:

1. downloads or accepts the pinned Census KML ZIP;
2. verifies ZIP/KML structure;
3. reads Census state/equivalent placemarks;
4. maps only the 50 WAS state FIPS identities;
5. preserves Census polygon/multipart structure;
6. canonicalizes numeric coordinate text without topology simplification;
7. rejects malformed rings;
8. rejects unsplit ring segments with longitude jumps over 180°;
9. writes the canonical offline pack;
10. emits per-state SHA-256 hashes;
11. emits the whole-pack SHA-256;
12. emits source/provenance metadata and compact Kotlin constants.

CI downloads the official artifact again, verifies the upstream SHA/size, regenerates all outputs into a temporary directory, and byte-compares them against the committed production artifacts.

## Pack integrity

Canonical production pack:

- byte size: **324,531**
- features: **50**
- SHA-256: `5feb8c18688936a526523cb536766130be06b14ebfa918b3d99e39bfbcb0a130`

Every state has its own deterministic feature SHA-256 stored in the generated metadata.

`GeometryPackIntegrity` independently recomputes in Kotlin:

- individual feature canonical serialization/hashes
- full pack canonical serialization/hash
- maximum ring longitude jump
- positive/negative-longitude presence

The production loader rejects:

- any modified pack bytes;
- non-canonical CRLF line endings;
- malformed state records;
- malformed polygon indices;
- malformed coordinates;
- missing/extra state identities;
- feature hash mismatches;
- polygon-count mismatches;
- whole-pack canonical hash mismatches.

## Multipart geometry

Multipart/island geometry is preserved from the Census KML.

Selected verified polygon counts:

- Alaska: **47**
- Hawaii: **8**
- California: **6**
- Michigan: **6**
- Florida: **4**
- Massachusetts: **3**

All 50 production state records contain at least one polygon and all outer rings remain closed.

## Alaska / antimeridian handling

Alaska is handled explicitly rather than clipped or silently rewrapped.

The Census 20m KML already provides multipart Alaska geometry on both sides of the antimeridian:

- polygon count: **47**
- contains negative longitudes: yes
- contains positive longitudes: yes
- maximum longitude jump between adjacent points in any Alaska ring: **0.980542000000014°**

Builder policy:

> Preserve Census multipart geometry after coordinate canonicalization; fail the build if any individual ring segment jumps more than 180°. Do not clip or implicitly wrap across the dateline.

The same <=180° segment rule is host-tested across every production state.

## Production provider integration

The compact pack decodes into the existing CP-0007B:

- `OfflineGeometryPackRecord`
- `MultiPolygonGeometry`
- `UsStateGeometryPackProvider`

Each state uses a stable asset id:

- `census/2025/state/20m/<STATE>`

The production manifest uses the exact Census artifact URL, not only the Census catalog page.

The production registry combines:

- the Census 50-state provider
- deterministic IARU Maidenhead grid geometry

Host integration proves:

- **50/50 WAS states have production geometry**
- **0 WAS states are unbound**
- **488/488 FFMA grids remain geometry-bound**
- Census and IARU source provenance remain separate

## Network/runtime boundary

The production geometry provider performs **no network access**.

Network access exists only in the build/reproducibility workflow that fetches the pinned Census source artifact.

At application runtime the state geometry is an offline asset.

Production geometry code remains free of:

- callsign-derived geography
- Android/Compose classes
- Google Maps/Mapbox classes
- accounts/credentials
- FTX-1 hardware
- RF behavior

## Host/CI evidence

Focused CP-0007C gate:

- **363/363 assertions PASS**
- official source download/hash gate PASS
- deterministic rebuild/byte comparison PASS
- metadata gate PASS
- production-vs-synthetic provenance gate PASS

Pinned hashes reproduced in CI:

- upstream Census ZIP: `efddd884f1442ef233b1ba9c12dddbd66b6fdf94da6a373e1556aefe3dbc5751`
- canonical FieldOps pack: `5feb8c18688936a526523cb536766130be06b14ebfa918b3d99e39bfbcb0a130`

Inherited regressions remain green:

- CP-0007B geometry providers: 102
- CP-0007A award-area map projection: 96
- CP-0006G extended award catalog/grid evaluator: 96
- CP-0006F Awards Center application service: 64
- CP-0006E award evidence persistence/import: 92
- CP-0006D Awards Center projection: 114
- CP-0006C award target/composite evaluator: 77
- CP-0006B base official award catalog: 115
- CP-0006A award evaluator: 67
- CP-0005B queue: 47
- CP-0005A logger: 78
- CP-0003B LoTW transaction: 53
- inherited core: 42,062

## Evidence boundary

CP-0007C proves the production offline geometry pack for the 50 WAS states.

It does **not** prove:

- an Android map renderer;
- basemap/offline tiles;
- visual styling;
- DXCC boundaries;
- CQ/ITU zones;
- IOTA/POTA/SOTA geography;
- propagation data;
- live/heard station geography;
- real sponsor/account state.

## Next software step

**CP-0008A — Propagation intelligence domain and source-normalization foundation.**

This follows the locked product requirement that propagation conditions are the top-priority map intelligence layer.

Recommended GitHub/CI-only scope:

1. define platform-independent normalized propagation observations/snapshots;
2. model source provenance, observation time, retrieval time, staleness, confidence, and geographic coverage;
3. support solar/geomagnetic context plus ionospheric MUF/foF2-style map products without hard-coding a provider into UI;
4. define normalized heard/spot path evidence that can later ingest PSK Reporter/WSPR/FieldOps observations;
5. define band-specific path/usability assessment with an explanation/reason model rather than an unexplained heat-map number;
6. define offline snapshot/cache interfaces so last-known propagation context can remain inspectable without network;
7. use deterministic synthetic CI fixtures only in this checkpoint;
8. do not require real API keys/accounts, phone/radio hardware, RF transmission, or Android map rendering.

Live provider integrations that require credentials should remain separately gated.

## Deferred checkpoint rule

CP-0003C remains **DEFERRED**. Do not return to CP-0003C until the owner explicitly says `resume CP-0003C`.

Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue with the next checkpoint that can be completed entirely through GitHub/CI.
