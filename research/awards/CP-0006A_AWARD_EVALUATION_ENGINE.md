# CP-0006A — Award evaluation engine

Parent durable checkpoint: `CP-0005B-MANUAL_QSO_LOTW_QUEUE`.

Evidence level: **host/CI software only**.

## Objective

Add the provider-independent award-domain/evaluation layer without encoding guessed sponsor rules or requiring any phone, radio, RF, LoTW account, certificate, or other real credential.

## Implemented

- `AwardProgressState` separates:
  - `WORKED`
  - `CONFIRMED`
  - `THRESHOLD_MET`
  - `OFFICIALLY_CLAIMABLE`
- `AwardConfirmationEvidence` requires explicit QSO id and confirmation source; optional evidence reference/time are preserved.
- A worked QSO is never silently promoted to confirmed.
- `ControlledAwardModeGrouper` maps exact MODE/SUBMODE identities into a separate CW / PHONE / DIGITAL award grouping layer.
- Every `AwardContribution` retains the original exact QSO MODE and SUBMODE.
- Unmapped modes remain `UNCLASSIFIED` and do not get guessed into award progress.
- `AwardEvaluationFilter` supports all-band or one-band evaluation and optional award-mode filtering without mutating the authoritative QSO.
- Distinct target counting supports both WORKED-based and CONFIRMED-based thresholds.
- Optional target universes expose remaining targets against the selected threshold basis.
- Local `THRESHOLD_MET` never implies `OFFICIALLY_CLAIMABLE`.
- Official claimability can only be emitted through an explicit `AwardOfficialClaimabilityEvaluator`, and that evaluator is not consulted until the local threshold is met.

## Deliberate boundary

CP-0006A uses synthetic/generic award definitions only.

It contains no official award requirements, sponsor URLs, claim URLs, account operations, submission logic, or guessed claimability rules. Those belong to CP-0006B after official-source verification.

## Host gate

`scripts/test_award_evaluation.sh`:

1. compiles the FieldOps core main source;
2. compiles the award tests separately;
3. runs 67 synthetic assertions;
4. rejects accidental official award-program names/URLs in the CP-0006A production award-domain file.

Expected focused result:

`CP-0006A award evaluation tests: PASS assertions=67`

## Deferred checkpoint rule

CP-0003C remains **DEFERRED**. Do not return to CP-0003C until the owner explicitly says `resume CP-0003C`.

While that rule is active, skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation, and continue to the next checkpoint that can be completed entirely through GitHub/CI.
