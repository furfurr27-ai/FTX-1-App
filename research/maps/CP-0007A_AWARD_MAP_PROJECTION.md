# CP-0007A — Award-area map projection foundation

Parent durable checkpoint: `CP-0006G-EXTENDED_AWARD_CATALOG`.

Evidence level: **host/CI software only**.

## Objective

Create the first map-domain read model for award progress without moving award rules into UI code and without inventing geography.

CP-0007A starts with the geographic target types already verified by the awards engine:

- U.S. states
- Maidenhead four-character grids

The projection is platform independent and has no Android map SDK, Compose, hardware, account, or network dependency.

## Map states

Every emitted target has exactly one primary state:

- `NEEDED`
- `WORKED_UNCONFIRMED`
- `CONFIRMED`

When the award's local evaluator threshold is met, `LOCAL_THRESHOLD_MET` is added as an overlay state.

`LOCAL_THRESHOLD_MET` is explicitly not sponsor claimability, awarded status, or credit.

## Supported award layers

CP-0007A projects only simple distinct-target awards whose target geography is already normalized:

- ARRL Worked All States
- ARRL VUCC — 50 MHz
- ARRL VUCC — 144 MHz
- ARRL VUCC — 432 MHz
- ARRL Fred Fish Memorial Award

It deliberately does not flatten or guess:

- DXCC geometry
- Triple Play mode/state matrices
- IOTA group geography
- POTA reference coordinates
- SOTA summit locations
- CQ/ITU zones

Those require separately verified geometry/source semantics.

## Finite versus open target universes

### WAS and FFMA

The catalog already contains complete finite target universes.

Therefore the map projection can emit explicit `NEEDED` records for:

- all 50 WAS state identities
- all 488 FFMA grid identities

A confirmed/worked target replaces the corresponding needed state for that identity.

### VUCC

VUCC has a numeric threshold but no finite world-grid universe stored in the catalog.

CP-0007A therefore emits only explicitly known worked/confirmed grid identities.

It does **not** fabricate a list of needed grids.

The layer carries `remainingToThresholdCount` so the UI can say, for example, "99 more confirmed grids needed" without pretending to know which 99 grids the operator must work.

## Deterministic aggregation

Each map target carries deterministic aggregation-ready metadata:

- stable target identity
- immutable contributing QSO ids
- confirmed QSO ids
- contributing bands
- controlled mode groups
- exact MODE/SUBMODE labels
- target-evidence provenance
- accepted confirmation-source names

Lists are normalized/deduplicated/sorted so the same repository state produces the same projection regardless of input iteration order.

This prepares later map clustering/popups without coupling award rules to rendering code.

## Query context

The projection accepts the existing `AwardsCenterQuery`.

It preserves:

- Mixed/CW/Phone/Digital mode view
- band filter
- UTC date range

The same official required-band restrictions from CP-0006G remain enforced inside the underlying award evaluator.

A UI filter cannot bypass official award band rules.

## Geometry identity and source separation

CP-0007A introduces:

- `AwardAreaGeometryIdentity`
- `AwardAreaGeometrySource`
- `AwardAreaGeometryBinding`
- `AwardAreaGeometryCatalog`

A binding contains only:

- target identity
- source id/version
- optional HTTPS source URL
- optional license label
- optional retrieval date
- external geometry asset id

It contains **no polygon, coordinate array, GeoJSON, latitude, longitude, or Android map object**.

That separation allows a later licensed/versioned offline geometry pack to change independently of award rules and progress state.

If no geometry binding is available, the target remains in the projection as unbound metadata; FieldOps does not synthesize a boundary.

A geometry catalog that returns a binding for the wrong target identity fails closed.

## Awards Center application-service integration

`AwardsCenterApplicationService.awardMapLayers()` now builds map layers from the same:

- authoritative `LogbookRepository`
- current persisted `AwardEvidenceRepository`

used by the Awards Center.

Later writes to those repositories appear on the next map projection; there is no competing map cache or shadow logbook.

Host evidence compares Awards Center card progress and map-layer progress for the same repository state.

## Callsign/geography boundary

The production map projection does not inspect `QsoRecord.call` for geography.

A callsign with no explicit state/grid evidence contributes no map geography.

This preserves the project rule that boundaries, coordinates, entities, zones, islands, parks, summits, and grids are not guessed from callsigns.

## Host/CI evidence

Focused CP-0007A gate:

- **96/96 assertions PASS**

Coverage includes:

- exact supported layer set
- WAS 50-target finite universe
- needed/worked-unconfirmed/confirmed state semantics
- no callsign-derived state/grid progress
- VUCC open-universe behavior
- numeric VUCC remaining-to-threshold count without fake needed grids
- FFMA 488-target finite universe
- local-threshold overlay semantics
- Mixed/mode/band/date query behavior
- deterministic QSO/band/mode aggregation
- exact MODE/SUBMODE preservation
- confirmation-source aggregation
- target evidence provenance
- geometry source/version/license metadata
- unbound geometry behavior
- mismatched geometry binding rejection
- live application-service repository integration
- DC-to-Maryland WAS canonicalization
- empty-repository behavior
- malformed geometry-source metadata rejection

Inherited regressions remain required:

- CP-0006G extended official award catalog/grid evaluator: 96
- CP-0006F Awards Center application service: 64
- CP-0006E award evidence persistence/import: 92
- CP-0006D Awards Center projection: 114
- CP-0006C award target/composite evaluator: 77
- CP-0006B base official catalog: 115
- CP-0006A award evaluator: 67
- CP-0005B queue: 47
- CP-0005A logger: 78
- CP-0003B LoTW transaction: 53
- inherited core: 42,062

## Evidence boundary

CP-0007A proves a deterministic map-layer read model and geometry-reference boundary.

It does **not** prove:

- real polygons or coordinates
- U.S. state geometry data
- Maidenhead geometry payload generation
- DXCC/IOTA/POTA/SOTA geometry
- CQ/ITU zone geometry
- map SDK rendering
- offline tile storage
- clustering UI
- sponsor claimability
- real account synchronization
- phone/radio/RF behavior

## Next software step

**CP-0007B — Award geometry providers and offline geometry-pack contract.**

Recommended scope:

1. define a versioned geometry payload/provider contract separate from award rules;
2. implement deterministic four-character Maidenhead cell bounds from a verified Maidenhead specification/source;
3. define a licensed/source-attributed U.S. state geometry-pack adapter and verify it with compact CI fixtures before adding a full data pack;
4. require source/version/license metadata for every non-derived geometry payload;
5. resolve geometry by the stable identities emitted by CP-0007A;
6. keep geometry loading usable offline;
7. do not derive map geometry from callsigns;
8. keep Android map rendering outside the core provider contract.

## Deferred checkpoint rule

CP-0003C remains **DEFERRED**. Do not return to CP-0003C until the owner explicitly says `resume CP-0003C`.

Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue with the next checkpoint that can be completed entirely through GitHub/CI.
