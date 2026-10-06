# CP-0007B — Award geometry providers and offline geometry-pack contract

Parent durable checkpoint: `CP-0007A-AWARD_MAP_PROJECTION`.

Evidence level: **host/CI software plus authoritative-source research**.

## Objective

Provide actual geometry payloads behind the metadata-only CP-0007A bindings without coupling award rules to a map SDK or network service.

CP-0007B establishes two geometry paths:

- deterministic geometry derived from a published technical standard;
- versioned offline geometry packs derived from external datasets.

Synthetic CI fixtures are explicitly separated from both.

## Authoritative sources actually checked

### Maidenhead Locator System

Primary technical source:

- IARU Region 1 VHF Handbook 9.01, March 2021
- `https://www.iaru-r1.org/wp-content/uploads/2021/03/VHF_Handbook_V9.01.pdf`

The handbook states that:

- the globe is divided into 18 × 18 fields;
- each field is 20° longitude by 10° latitude;
- each field is divided into 10 × 10 squares;
- each square is 2° longitude by 1° latitude;
- indexing proceeds west-to-east and south-to-north;
- the origin is 180° west / 90° south;
- WGS-84 is the reference geodetic system for locator determination.

CP-0007B therefore computes four-character Maidenhead geometry directly from the locator value. No IARU geometry file is copied into the app.

### U.S. state geometry

Official Census source:

- 2025 Cartographic Boundary Files
- `https://www.census.gov/geographies/mapping-files/2025/geo/carto-boundary-file.html`

The Census page publishes national States files at:

- 1:500,000
- 1:5,000,000
- 1:20,000,000

The planned FieldOps production pack contract defaults to the compact 1:20,000,000 national States representation, while keeping the scale/version explicit.

Rights/attribution source checked:

- U.S. Census Bureau TIGER/Line Shapefiles Technical Documentation, Chapter 1
- `https://www2.census.gov/geo/pdfs/maps-data/data/tiger/tgrshp2019/TGRSHP2019_TechDoc_Ch1.pdf`

The Census technical documentation states that U.S. Government works are not subject to U.S. copyright protection under 17 U.S.C. §105 and requests Census Bureau source citation. It also warns that Census boundary products are for statistical purposes and are not legal land descriptions.

CP-0007B records those rights/attribution constraints in the production state-pack contract.

## Geometry payload contract

Platform-independent core geometry types now include:

- `GeoCoordinate`
- `GeoBounds`
- `GeoLinearRing`
- `GeoPolygon`
- `BoundsGeometry`
- `MultiPolygonGeometry`

Validation fails closed for:

- longitude/latitude outside world bounds;
- zero/reversed rectangular bounds;
- open rings;
- rings with fewer than three distinct vertices;
- zero-area rings;
- empty multi-polygons.

No Android, Compose, Google Maps, Mapbox, network client, or FTX-1 type is involved.

## Provenance classes

`AwardGeometrySourceKind` distinguishes:

- `DERIVED_STANDARD`
- `EXTERNAL_DATASET`
- `SYNTHETIC_FIXTURE`

Every source records:

- source id
- source version
- optional HTTPS source URL
- retrieval date
- build version

Every non-derived payload additionally requires explicit license/public-domain metadata.

External datasets also require an authoritative HTTPS source URL.

## Maidenhead provider

`MaidenheadGrid4GeometryProvider` resolves only:

- `OfficialAwardTargetKind.MAIDENHEAD_GRID4`
- already-normalized four-character locators

Formula:

- longitude field index: first letter A-R, 20° each
- latitude field index: second letter A-R, 10° each
- longitude square digit: 2° each
- latitude square digit: 1° each
- global origin: (-180°, -90°)

Examples verified by host tests:

- `AA00` -> west -180, south -90, east -178, north -89
- `JJ00` -> west 0, south 0
- `JO40` -> west 8, south 50, east 10, north 51
- `FN31` -> west -74, south 41, east -72, north 42
- `RR99` -> west 178, south 89, east 180, north 90

Every grid square is exactly 2° wide × 1° high.

Six/eight-character locators are intentionally rejected at the geometry-provider boundary because CP-0006G already normalizes award evidence to four characters before geometry lookup.

## Offline pack contract

`OfflineGeometryPackManifest` records:

- pack id
- pack version
- target kind
- source metadata
- declared feature count
- fixture/production distinction

`OfflineGeometryPackRecord` records:

- target identity value
- stable asset id
- platform-independent geometry payload

`UsStateGeometryPackProvider`:

- accepts only `US_STATE` manifests;
- validates two-letter state target identities;
- validates declared feature count;
- rejects duplicate state identities;
- rejects duplicate asset ids;
- resolves geometry entirely offline.

## Census state-pack contract

`CensusStateGeometryPackContract.productionManifest()` pins:

- source id: `US_CENSUS_CARTOGRAPHIC_BOUNDARY_FILES`
- 2025 Cartographic Boundary Files
- explicit chosen scale
- official Census HTTPS source URL
- 17 U.S.C. §105 / Census-attribution rights metadata
- pack/build version
- retrieval date

CP-0007B does **not** bundle full Census state geometry.

That boundary is intentional.

## Synthetic CI fixtures

Compact state polygons for Oregon and Washington are used only to verify the provider contract.

Their manifest is:

- `SYNTHETIC_FIXTURE`
- explicitly fixture-only
- explicitly states that it contains no Census boundary geometry
- has no fake Census geometry URL

This prevents tests from making a false provenance claim.

## Provider registry

`AwardGeometryRegistry` composes independent providers.

It:

- requires unique provider ids;
- resolves a target through at most one provider;
- rejects conflicting providers;
- rejects mismatched returned target identities;
- exposes both:
  - the full geometry feature/payload;
  - the CP-0007A metadata-only `AwardAreaGeometryBinding`.

The award map projection therefore remains payload-agnostic while a later renderer can resolve the actual geometry separately.

## CP-0007A integration evidence

With a registry containing:

- `MaidenheadGrid4GeometryProvider`
- the two-state synthetic offline pack

host tests prove:

- only Oregon/Washington are bound in the WAS layer;
- the other 48 states remain unbound rather than fabricated;
- all 488 FFMA grids receive deterministic IARU-derived cell geometry;
- map targets still contain only metadata bindings;
- geometry payload is fetched separately through the registry.

## Callsign / network / UI boundary

Production geometry provider code:

- does not inspect callsigns;
- performs no HTTP/network calls;
- does not use Android or map SDK classes;
- does not access accounts, credentials, radio hardware, or RF paths.

## Host/CI evidence

Focused CP-0007B gate:

- **102/102 assertions PASS**

Coverage includes:

- IARU source pin and source sections
- Maidenhead known cells and global edges
- exact 2° × 1° cell dimensions
- malformed locator rejection
- geometry primitive validation
- non-derived license enforcement
- external dataset URL enforcement
- Census 2025 source/vintage/scale metadata
- Census §105/attribution metadata
- synthetic fixture provenance separation
- offline U.S. state pack lookup
- feature-count, identity, and asset-id conflict rejection
- fixture/production source-kind mismatch rejection
- registry multi-provider resolution
- provider-id conflict rejection
- two-provider same-target conflict rejection
- target-identity mismatch rejection
- CP-0007A binding integration
- all 488 FFMA grids resolved by derived geometry
- deterministic geometry regardless provider input order

Inherited gates remain green:

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

CP-0007B proves:

- geometry payload/domain validation;
- IARU-derived four-character Maidenhead bounds;
- offline pack manifest/provider semantics;
- Census production-pack provenance contract;
- compact synthetic state-fixture behavior;
- registry integration with CP-0007A.

It does **not** prove:

- a production Census state geometry pack;
- shapefile/KML/GeoPackage parsing;
- geometry simplification tooling;
- production asset hashing;
- Alaska antimeridian handling in a full state dataset;
- Android map rendering;
- offline map tiles;
- DXCC/IOTA/POTA/SOTA/CQ/ITU geometry.

## Next software step

**CP-0007C — Production U.S. state offline geometry pack.**

Recommended scope:

1. fetch/pin the official Census 2025 national States 1:20,000,000 source artifact;
2. record upstream artifact filename/hash and Census source metadata;
3. convert the relevant 50 WAS state boundaries into the CP-0007B platform-independent multi-polygon payload format;
4. preserve multipart states/islands and handle Alaska/antimeridian geometry explicitly rather than clipping silently;
5. generate a versioned offline pack with deterministic per-feature and pack hashes;
6. verify all 50 WAS identities resolve exactly once;
7. verify no District of Columbia or territory is silently added to the 50-state WAS geometry universe;
8. retain Census attribution/legal-disclaimer metadata in the pack;
9. keep the raw/upstream artifact and generated production pack clearly distinguished from synthetic CI fixtures;
10. keep Android map rendering outside the pack builder/provider checkpoint.

## Deferred checkpoint rule

CP-0003C remains **DEFERRED**. Do not return to CP-0003C until the owner explicitly says `resume CP-0003C`.

Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue with the next checkpoint that can be completed entirely through GitHub/CI.
