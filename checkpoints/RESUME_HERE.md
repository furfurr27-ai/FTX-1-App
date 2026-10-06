# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0006A-AWARD_EVALUATION_ENGINE**
Project version: `v16-award-evaluation-engine`
Phase: **Award evaluation engine host-verified; official award rules/catalog next; hardware/account checkpoints remain deferred by owner**
Test status: **GREEN host/CI: award=67 queue=47 logger=78 LoTW_transaction=53 core=42062; finalizer run 37458092347**

## What is complete in this checkpoint
- Provider-independent award-domain model exposes WORKED, CONFIRMED, THRESHOLD_MET and OFFICIALLY_CLAIMABLE as distinct states
- Confirmation source/evidence is explicit and worked QSOs are not silently promoted to confirmed
- Controlled award-mode grouping is separate from and preserves exact QSO MODE/SUBMODE
- Band-scoped and all-band evaluation uses filters without rewriting authoritative QSO records
- Distinct-target thresholds support WORKED or CONFIRMED basis and optional remaining-target universes
- Local threshold completion cannot imply official sponsor claimability without an explicit claimability evaluator
- CP-0006A contains synthetic/generic definitions only and 67 focused award assertions pass

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return to CP-0003C until the owner explicitly says 'resume CP-0003C'
- Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation; continue to the next GitHub/CI-only checkpoint
- CP-0004A/B/C FTX-1 Android hardware checkpoints remain deferred and incomplete
- Official sponsor award rules/claim links are not encoded until CP-0006B verifies official sources

## Continue with these exact actions
1. CP-0006B: verify official award rules/claim links from issuing organizations and encode a versioned catalog on top of CP-0006A
2. Do not attempt real award-account login or claim submission; skip any later phone/radio/credential/account/RF/manual-hardware checkpoint under the owner override

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
