# CP-0006D — Awards Center progress projection

Parent durable checkpoint: `CP-0006C-AWARD_TARGET_ENRICHMENT`.

Evidence level: **host/CI software only**.

## Objective

Build the UI-independent Awards Center read model on top of the verified official catalog and award-progress engine.

This checkpoint is deliberately a projection/read-model layer. It does not move award rules into UI code, does not perform sponsor account actions, and does not require Android hardware.

## Projection contract

Each catalog entry projects to one `AwardsCenterAwardCard` containing:

- official award name, issuer and description
- official information/claim links
- concise claim instructions
- official source URLs, retrieval dates and source-version labels
- catalog conditions that still matter to sponsor acceptance
- worked and confirmed counts when locally evaluatable
- local threshold status
- remaining targets, matrix cells or required coverage where available
- deterministic FieldOps progress metric
- explicit sponsor standing and sponsor reference/evidence
- nullable `claimableNow`
- warnings that explain local-vs-sponsor evidence boundaries

The projection is generated from `OfficialAwardCatalog`, `OfficialAwardProgressEngine`, and explicit sponsor-standing records. No award id is hard-coded into the projection implementation.

## Query views

`AwardsCenterQuery` supports:

- Mixed
- CW
- Phone
- Digital
- band filter
- inclusive UTC date range

Date filtering occurs before award evaluation. Band and controlled award-mode filtering are passed into the verified evaluator.

Exact QSO MODE/SUBMODE remains below the projection and is not rewritten.

## Progress metric

The displayed percentage is a deterministic **FieldOps UI progress metric**, not a sponsor-issued percentage.

### Distinct-target awards

`counted targets / required targets`

The counted side follows the official catalog threshold basis (worked or confirmed).

### Mode-target matrix awards

`completed required cells / required cells`

For a Mixed view, Triple Play uses all 150 official state/mode cells.

For a CW, Phone or Digital view, the progress display is scoped to that 50-cell leg. A mode leg may therefore display 100% while `localThresholdMet` remains false because the official award still requires the full matrix.

### Count-plus-coverage awards

For IOTA 100 the display units are:

`min(counted groups, 100) + achieved required continents`

over:

`100 groups + 7 required continents`

This gives a transparent deterministic progress bar while preserving both official criteria. It is not represented as an IOTA sponsor percentage.

### External scoring

Awards such as SOTA Shack Sloth return `EXTERNAL_UNAVAILABLE` with no numeric numerator, denominator or percentage. FieldOps does not fabricate sponsor point scoring.

## Sponsor standing and claimability

Sponsor state remains distinct from local progress.

The card can expose:

- UNKNOWN
- ELIGIBLE_NOT_CLAIMED
- SUBMITTED
- AWARDED
- CREDITED

`claimableNow` is:

- `null` when sponsor standing is UNKNOWN
- `true` only for explicit ELIGIBLE_NOT_CLAIMED sponsor evidence
- `false` for SUBMITTED/AWARDED/CREDITED

A locally complete threshold never writes or infers sponsor standing.

If multiple sponsor-standing records exist, the newest recorded timestamp wins. Conflicting records at the same latest timestamp fail closed.

## External-verification warnings

Cards retain explicit warnings when:

- local progress does not establish sponsor claimability/credit
- provenance-bearing normalized target evidence is required
- composite local evaluation still leaves sponsor-only checks external
- program-validated logs are external to ordinary QSO/LoTW state
- sponsor standing is unknown
- external program scoring prevents a trustworthy local percentage

Official catalog `additionalConditions` are forwarded as data rather than reimplemented in the projection layer.

## Host/CI evidence

Focused CP-0006D gate:

- **114/114 assertions PASS**
- one projection card per official catalog entry
- official source/version/claim metadata preserved
- deterministic distinct-target progress
- Mixed/CW/Phone/Digital views
- band views
- inclusive date-range views
- Triple Play mode-leg display progress separated from award threshold state
- IOTA count-plus-coverage progress
- sponsor standing and nullable claimability separation
- external SOTA scoring with no fabricated percentage
- deterministic latest sponsor-standing selection and conflict rejection
- QSO immutability
- catalog conditions/sources forwarded rather than recoded
- production projection contains no hard-coded award IDs
- production projection contains no credential/network/hardware/Compose/Room dependencies

Inherited regressions also pass:

- CP-0006C award target/composite evaluator: 77
- CP-0006B official award catalog: 115
- CP-0006A award evaluator: 67
- CP-0005B queue: 47
- CP-0005A logger: 78
- CP-0003B LoTW transaction: 53
- inherited core: 42,062

## Evidence boundary

CP-0006D proves the read model and query semantics with synthetic QSO/enrichment/sponsor-standing fixtures.

It does **not** prove:

- real award-target enrichment imports/datasets
- persistent award evidence storage
- sponsor account synchronization
- claim submission
- Compose/Room application integration
- map award overlays
- Android device behavior
- FTX-1 hardware behavior

## Next software step

The next software-only checkpoint should replace fixture-only award evidence with durable local evidence storage and explicit import adapters for award metadata already supplied by trusted records (for example explicit ADIF/LoTW fields), without callsign-prefix guessing.

## Deferred checkpoint rule

CP-0003C remains **DEFERRED**. Do not return to CP-0003C until the owner explicitly says `resume CP-0003C`.

Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue with the next checkpoint that can be completed entirely through GitHub/CI.
