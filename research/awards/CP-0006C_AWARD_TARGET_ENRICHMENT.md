# CP-0006C — Award target enrichment and composite rules

Parent durable checkpoint: `CP-0006B-OFFICIAL_AWARD_CATALOG`.

Evidence level: **host/CI software only**.

## Objective

Make officially sourced award rules locally evaluatable without rewriting the authoritative QSO record and without guessing geography/program identity from callsigns or free text.

## Implemented

### Provenance-bearing target evidence

`AwardTargetEvidence` is keyed by immutable QSO id and stores a normalized target plus explicit `AwardTargetProvenance`.

Direct enrichment types in this checkpoint:

- DXCC entity
- U.S. state
- continent
- IOTA group
- POTA reference

Required provenance fields include a source id and source version. Optional HTTPS source URL, source reference, and retrieval time are retained.

Award enrichment is intentionally external to `QsoRecord`. Better or newer source data can replace enrichment without mutating the contact itself.

### Fail-closed normalization

The evidence layer normalizes only explicit supplied award data.

Examples:

- numeric DXCC entity ids
- two-letter U.S. state abbreviations
- ADIF continent codes to canonical continent names
- IOTA references such as `EU-005`
- normalized POTA references

No production path reads `qso.call` or QSO notes to derive award targets.

Single-valued geography conflicts fail closed. A QSO cannot simultaneously have two normalized states, DXCC entities, continents, or IOTA groups. POTA references are intentionally multi-valued because one QSO can legitimately carry more than one park reference.

## Official contribution filters

Before a QSO contributes to an official award, the progress engine applies the catalog requirement:

- not-before date
- excluded band
- optional user band/mode view
- required confirmation policy

Confirmation handling is conservative:

- `ACCEPTED_CONFIRMATION`: explicit confirmation evidence is accepted.
- `LOTW_ONLY`: only explicit LoTW/ARRL-LoTW evidence counts as confirmed.
- `PROGRAM_VALIDATED_LOG`: CP-0006C does not forge confirmation from ordinary QSO/LoTW state; program validation remains external sponsor state.

## Locally evaluatable official progress

### DXCC Mixed

DXCC progress consumes explicit normalized DXCC entity evidence. Contacts before the catalog's official date floor are excluded. Mixed accepted-confirmation evidence can count toward confirmed progress.

The DXCC entity list is still not copied into FieldOps; entity identity must come from a versioned enrichment source.

### Worked All States

WAS consumes explicit U.S. state evidence, applies the 60 m exclusion, and maps explicit DC evidence to Maryland under the catalog's official special rule. The 50-state universe drives remaining-state progress.

The same-location sponsor rule is not claimed solved by this checkpoint.

### Worked All Continents

WAC consumes normalized continent evidence, including canonicalization of ADIF continent codes. Band-specific progress views are supported through the existing evaluation filter.

### POTA Bronze Hunter

POTA progress consumes explicit POTA references and permits more than one reference from one QSO. Ten worked unique references can reach the local Bronze threshold.

This does not fabricate POTA acceptance or issuance. The catalog already records that the sponsor issues qualifying awards automatically after its own log processing.

## Composite evaluators

### Triple Play WAS

Triple Play is evaluated as the full official 50-state × 3-mode matrix:

- CW
- Phone
- Digital

That produces exactly 150 required cells.

The evaluator:

- applies the 1 January 2009 date floor;
- excludes 60 m;
- derives mode group separately while retaining the exact QSO mode/submode;
- requires explicit state evidence;
- applies the DC-to-Maryland rule;
- requires LoTW confirmation evidence for each cell.

A non-LoTW confirmation cannot fill a Triple Play cell. Local matrix completion still does not assert sponsor claimability.

### IOTA 100

IOTA 100 requires both:

- 100 distinct confirmed IOTA groups; and
- confirmed coverage of all seven required continents.

Group evidence and continent evidence are independent provenance-bearing records on the same immutable QSO id. Reaching 100 groups without all seven continents does not satisfy the local threshold.

## External scoring boundary

SOTA Shack Sloth remains `EXTERNAL_PROGRAM_SCORING`. CP-0006C refuses to turn SOTA's point system into a distinct-QSO approximation.

## Sponsor-state boundary

The CP-0006B official standing model remains separate.

Local evaluation may produce:

- WORKED
- CONFIRMED
- THRESHOLD_MET

It does not emit `OFFICIALLY_CLAIMABLE`, AWARDED, or CREDITED without separate sponsor evidence/process logic.

## Host/CI evidence

Focused CP-0006C gate:

- **77/77 assertions PASS**
- target normalization/provenance
- conflicting evidence rejection
- no callsign inference
- DXCC date-floor behavior
- WAS state universe/DC alias/60 m exclusion
- WAC continent normalization and band views
- multi-reference POTA progress
- complete 150-cell Triple Play matrix and LoTW-only confirmation
- IOTA 100 plus seven-continent composite rule
- SOTA external-scoring refusal
- authoritative QSO immutability

Inherited regressions also pass:

- CP-0006B official catalog: 115
- CP-0006A award evaluator: 67
- CP-0005B queue: 47
- CP-0005A logger: 78
- CP-0003B LoTW transaction: 53
- inherited core: 42,062

## Remaining award work

CP-0006C proves the engine/data boundary, not a finished Awards screen.

The next software-only checkpoint should project these results into an Awards Center read model that exposes worked, confirmed, remaining, percentage/progress, band/mode views, source/evidence explanations, and external-verification warnings without moving award rules into UI code.

VUCC, CQ WAZ/WPX, additional regional/national awards, real enrichment datasets/import adapters, sponsor account sync, and map overlays remain later work.

## Deferred checkpoint rule

CP-0003C remains **DEFERRED**. Do not return to CP-0003C until the owner explicitly says `resume CP-0003C`.

Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue with the next checkpoint that can be completed entirely through GitHub/CI.
