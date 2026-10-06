# CP-0006G — Extended official award catalog and evaluator coverage

Parent durable checkpoint: `CP-0006F-AWARDS_APPLICATION_SERVICE`.

Evidence level: **host/CI software plus issuer-authoritative web research**.

## Objective

Expand the verified award catalog only where current issuing-organization material can be pinned and translated without guessing award geography, band rules, sponsor credit, or claimability.

The owner rule remains unchanged: CP-0003C and any phone/radio/account/credential/RF/manual-hardware checkpoint stay deferred.

## Official sources actually checked

### ARRL VUCC

Issuer sources:

- `https://www.arrl.org/vucc`
- `https://www.arrl.org/files/file/Awards/VUCC-Rules-July-2019.pdf`

The current ARRL VUCC page links the official rules PDF.

The July 2019 rules state:

- VUCC is based on Maidenhead 2° × 1° grid locators represented by two letters plus two digits.
- 50 MHz, 144 MHz, and Satellite initially require 100 credits.
- 222 MHz and 432 MHz initially require 50 credits.
- 902 MHz and 1296 MHz initially require 25 credits.
- higher microwave bands use smaller thresholds listed in the official rules.
- contacts must be dated 1 January 1983 or later.
- separate bands are separate awards.
- cross-band contacts do not count except Satellite.
- active-repeater contacts do not count except Satellite.
- aeronautical-mobile contacts do not count.
- for 50 through 1296 MHz and Satellite, claimed contacts must be made from applicant locations no more than 200 km apart.
- there are no specialty endorsements such as CW-only.

CP-0006G encodes only the FTX-1/FieldOps bands already represented by the built-in band model:

- 50 MHz / 6m — 100 confirmed grids
- 144 MHz / 2m — 100 confirmed grids
- 432 MHz / 70cm — 50 confirmed grids

The remaining VUCC band family is not silently fabricated into the current band catalog.

### ARRL Fred Fish Memorial Award

Issuer source:

- `https://www.arrl.org/FFMA`

The current ARRL FFMA page states:

- all 488 specified Maidenhead four-character grid squares must be confirmed;
- all contacts are on 6 meters / 50 MHz;
- only contacts dated 1 January 1983 or later are creditable;
- there are no mode endorsements or recognized progress tiers;
- repeaters and satellites do not count;
- aeronautical-mobile contacts do not count;
- applicant operating locations must be no more than 200 km apart;
- each claimed contact requires contemporaneous direct initiation by the operators on both sides;
- applications are certified through the official VHF Awards Manager process.

The complete 488-grid list on the issuer page was transcribed into a deterministic catalog target universe and host-tested for the documented field totals:

- CM 12
- CN 27
- DL 5
- DM 90
- DN 90
- EL 27
- EM 100
- EN 80
- FM 20
- FN 37

Total: **488**.

## CQ WAZ / WPX research boundary

CP-0006G also investigated CQ WAZ and CQ WPX because they are named in the locked product requirements.

Current ARRL LoTW material confirms that LoTW supports CQ WAZ/WPX application-credit workflows, but ARRL explicitly states that it is not the administrator of CQ awards.

Search also surfaced copies/current program-manager material describing the programs, but the owner instruction for this checkpoint is stricter: newly encoded award rules must come from an issuer-authoritative source that can be durably pinned and checked.

The historical/current `cq-amateur-radio.com` award-rule URLs could not be reliably retrieved through the research path in this run. Therefore:

- CQ WAZ is **not encoded** in CP-0006G.
- CQ WPX is **not encoded** in CP-0006G.
- no CQ-zone target is guessed from a callsign.
- no WPX prefix is derived from a callsign.
- ARRL LoTW integration pages are not substituted for CQ's award rules.

This is an intentional fail-closed result, not an omission disguised as completion.

## Regional/national research boundary

The official DARC award-rules PDF surfaced during research, including DLD/DOK program material, but the document could not be durably fetched through the available retrieval path in this run. CP-0006G therefore does not encode a DOK normalizer or DLD threshold from a partial source excerpt.

## New normalized target: Maidenhead four-character grid

`OfficialAwardTargetKind.MAIDENHEAD_GRID4` is now directly enrichable.

Accepted explicit locators:

- 4 characters, e.g. `FN31`
- 6 characters, e.g. `FN31PR`
- 8 characters, e.g. `FN31PR12`

All normalize to the official four-character grid needed by VUCC/FFMA.

The normalizer validates the Maidenhead field/subsquare alphabet and does not accept arbitrary text.

Grid evidence is single-valued per immutable QSO. Conflicting remote four-character grids fail closed.

## Explicit ADIF grid ingestion

The CP-0006E enrichment adapter now maps only explicit remote:

- `GRIDSQUARE` -> `MAIDENHEAD_GRID4`

`MY_GRIDSQUARE` remains station-side metadata and is not treated as the remote award target.

No callsign, country, state, notes, or free-text inference is used to create grid evidence.

## Required-band rule

`OfficialAwardRequirement` now has `requiredBands`.

This is an evaluator rule, not a UI convention.

A QSO contributes only if its authoritative local band is one of the award's required bands. A user-supplied Awards Center band filter cannot bypass the official required-band rule.

A band cannot simultaneously be required and excluded.

This allows:

- 6m VUCC
- 2m VUCC
- 70cm VUCC
- 6m-only FFMA

to remain safe even in Mixed/all-band views.

## Sponsor-state boundary

All four new catalog entries use local confirmed-grid thresholds only.

Local evaluation may produce:

- WORKED
- CONFIRMED
- THRESHOLD_MET

It never produces sponsor claimability, AWARDED, or CREDITED.

The VUCC/FFMA location, repeater, boundary, direct-initiation, and application checks remain visible as official conditions that may require sponsor verification.

## Host/CI evidence

Focused CP-0006G gate:

- **96/96 assertions PASS**

It proves:

- catalog version/source metadata
- issuer host restriction for new entries
- VUCC 50/144/432 thresholds
- VUCC 1983 date floor
- VUCC required-band behavior
- VUCC 200 km/repeater conditions retained
- exact 488-grid FFMA universe
- FFMA all-or-nothing behavior
- four/six/eight-character Maidenhead normalization
- invalid grids fail closed
- grid evidence is single-valued per QSO
- explicit ADIF GRIDSQUARE provenance
- MY_GRIDSQUARE is not imported as remote target evidence
- required bands are enforced inside the evaluator
- 99 confirmed 6m grids do not complete 50 MHz VUCC
- 100 confirmed 6m grids do complete the local VUCC threshold
- 50 confirmed 70cm grids complete the local 432 MHz VUCC threshold
- 487 confirmed FFMA grids do not complete FFMA
- 488 confirmed required grids do complete the local FFMA threshold
- non-FFMA grids cannot inflate FFMA progress
- pre-1983 contacts are excluded
- local threshold never becomes sponsor claimability
- WAZ/WPX are not guessed into production code without issuer-authoritative rules

Inherited regressions remain required:

- CP-0006F Awards Center application service: 64
- CP-0006E award evidence persistence/import: 92
- CP-0006D Awards Center projection: 114
- CP-0006C target/composite evaluator: 77
- CP-0006B base official catalog regression: 115
- CP-0006A award evaluator: 67
- CP-0005B queue: 47
- CP-0005A logger: 78
- CP-0003B LoTW transaction: 53
- inherited core: 42,062

## Next software step

The awards foundation is now sufficiently mature to feed geographic presentation without moving award rules into map/UI code.

Recommended next GitHub/CI-only checkpoint:

**CP-0007A — Award-area map projection foundation**

Build a UI-independent map-layer read model from the authoritative logbook, award evidence, and Awards Center state for geographic award targets already supported:

- U.S. states
- Maidenhead grids
- DXCC entities where a licensed/versioned geometry source is available later
- POTA/IOTA references where coordinates/geometry are explicitly available

The projection should classify map targets as needed / worked-unconfirmed / confirmed / local-threshold-reached while keeping geometry-source licensing and sponsor credit separate.

Do not fabricate boundaries or coordinates from callsigns.

## Deferred checkpoint rule

CP-0003C remains **DEFERRED**. Do not return to CP-0003C until the owner explicitly says `resume CP-0003C`.

Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue with the next checkpoint that can be completed entirely through GitHub/CI.
