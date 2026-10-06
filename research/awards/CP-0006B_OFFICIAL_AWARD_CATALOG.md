# CP-0006B — Official award rules/catalog

Parent durable checkpoint: `CP-0006A-AWARD_EVALUATION_ENGINE`.

Evidence level: **host/CI software plus official-source research**.

## Objective

Layer a versioned, officially sourced award catalog on the provider-independent CP-0006A evaluator without guessing sponsor rules, logging into real accounts, submitting claims, or using hardware.

## Verified initial catalog

The catalog version is `2026-10-06.1`. Every encoded rule has an official HTTPS source and retrieval date in `research/awards/OFFICIAL_AWARD_SOURCES.tsv`.

Initial entries:

- ARRL DXCC Mixed
- ARRL Worked All States (WAS)
- IARU Worked All Continents (WAC), using ARRL's official administration page
- ARRL Triple Play WAS
- IOTA 100 Islands of the World
- POTA Bronze Hunter
- SOTA Shack Sloth — 1,000 points

## Rule facts encoded

### ARRL DXCC Mixed

Official ARRL DXCC rules require at least 100 worked-and-confirmed DXCC entities for the basic award. Mixed contacts may use any mode, with qualifying contacts dating from 15 November 1945.

The catalog does **not** copy the current DXCC entity list. ARRL notes that the list is copyrighted; FieldOps stores the threshold/source metadata and leaves entity data to a separately licensed/versioned enrichment source.

### ARRL Worked All States

Basic WAS requires confirmed contacts with all 50 U.S. states. The official rules exclude 60 meters, allow District of Columbia to count for Maryland, and impose a same-location rule (locations no more than 50 miles apart).

The catalog stores the 50-state target universe but does not claim the local evaluator currently proves the station-location restriction.

### IARU Worked All Continents

WAC requires working and confirming all six continents used by the program:

- North America
- South America
- Oceania
- Asia
- Europe
- Africa

Antarctica is not one of the six WAC targets.

### ARRL Triple Play WAS

Triple Play requires each of the 50 states confirmed on all three mode groups: voice, CW, and digital. That is a 50 × 3 mode/state matrix rather than a simple 150-anything count. The official page requires LoTW confirmations, excludes 60 meters, applies the same-location rule, and accepts contacts from 0000Z 1 January 2009 forward.

The entry is therefore marked `REQUIRES_COMPOSITE_RULE_ENGINE`. CP-0006A is not allowed to flatten it to a simple distinct-target threshold.

### IOTA 100

The IOTA Programme Rules published 5 May 2026 define IOTA 100 as the basic award: at least 100 confirmed numbered IOTA groups with different references plus at least one contact from each of seven continents.

This is count-plus-coverage logic and is therefore also marked `REQUIRES_COMPOSITE_RULE_ENGINE`.

### POTA Bronze Hunter

Official POTA documentation defines Bronze Hunter as contacts with 10 different POTA references. The unique-reference awards are issued automatically by POTA after accepted logs are processed.

FieldOps may calculate a local worked threshold once POTA reference identity is available, but it must not invent a manual claim or sponsor-credit state.

### SOTA Shack Sloth

Official SOTA documentation defines Shack Sloth for chasers/SWLs at 1,000 points. SOTA scoring is not equivalent to distinct-QSO counting, and awards are not sent automatically.

The entry is marked `EXTERNAL_PROGRAM_SCORING`; FieldOps must not fake SOTA point calculation from the current QSO model.

## Claimability and official standing boundary

The official catalog can produce a CP-0006A local threshold definition only for simple distinct-target rules.

It deliberately attaches **no** `AwardOfficialClaimabilityEvaluator`. A local threshold can reach `THRESHOLD_MET`, but it cannot become `OFFICIALLY_CLAIMABLE` merely because a catalog entry exists.

`OfficialAwardStandingRecord` separately tracks external sponsor state:

- UNKNOWN
- ELIGIBLE_NOT_CLAIMED
- SUBMITTED
- AWARDED
- CREDITED

AWARDED or CREDITED requires an explicit sponsor reference/evidence value. Local evaluation never writes those states.

## Current QSO-model boundary

The current authoritative QSO model does not yet have normalized remote fields for:

- DXCC entity
- U.S. state
- continent
- IOTA reference
- POTA reference
- SOTA summit/point data

CP-0006B does not infer these from callsigns or free text.

The next software step should add normalized award-target enrichment/provenance and composite rule support before claiming fully local award progress for these programs.

## Catalog scope boundary

The locked product backlog also names VUCC, CQ WAZ, CQ WPX, and additional regional/national awards. They are not silently guessed into this checkpoint.

- VUCC has band-dependent grid requirements and needs a dedicated per-band rule shape.
- CQ WAZ/WPX remain supported by LoTW credit-transfer workflows, but CP-0006B does not encode their award rules without a verified current issuer/program rule source suitable for durable rule data.
- Additional awards remain catalog expansion work after the base data/evaluator contracts are proven.

## Deferred checkpoint rule

CP-0003C remains **DEFERRED**. Do not return to CP-0003C until the owner explicitly says `resume CP-0003C`.

Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue with the next checkpoint that can be completed entirely through GitHub/CI.
