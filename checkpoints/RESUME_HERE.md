# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0009M-ENCRYPTED_ARCHIVE_CONFLICT_INSPECTION**
Project version: `v64-encrypted-archive-conflict-inspection`
Phase: **Explicit read-only encrypted candidate comparison host-tested; device/RF deferred**
Test status: **GREEN CI: archive conflict Robolectric=PASS, missing-key/tamper/match=PASS, APK=PASS, inherited=PASS; finalizer 38817185203**

## What is complete in this checkpoint
- Read-only independent original/rotated authentication, canonical checksum and conflict classification
- No key provision, automatic archive choice, deletion, downgrade or key retirement
- Robolectric tests match/conflict, lost keys, unreadable tags, missing copies and explicit opt-ins
- Android explicit inspection button displays freshness limitation and blocked key retirement
- Real Android debug APK and inherited synthetic Kotlin/Robolectric host regressions succeeded

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C DEFERRED; do not resume without explicit instruction
- CP-0004A/B/C hardware checkpoints incomplete
- No old-key retirement, trusted replay witness or physical Galaxy S23 Ultra/Keystore/hard-link/RF/CAT/USB/account proof

## Continue with these exact actions
1. CP-0009N: explicit candidate recovery choice and non-executing retirement readiness, GitHub/CI-only
2. Preserve APK artifact build plus all inherited host regression gates
3. CP-0003C remains DEFERRED; skip manual hardware/account validation

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.


## CP-0009N WIP handoff — NOT a verified checkpoint

Branch `cp-0009n-explicit-archive-recovery-choice`. CP-0009M/v64
remains durable until CP-0009N is green and promoted. Source:
ExplicitEncryptedArchiveRecovery.kt, two Robolectric test classes, Android
ArchiveHistoryActivity and scripts/finalize_cp0009n.py; workflows:
cp0009n-explicit-recovery-choice.yml and finalize-cp0009n.yml.
Inspect live PR and all checks for the exact head, fix failures before
merge, then verify main finalizer and immutable SHA-256. No real hardware
or Keystore proof. CP-0003C DEFERRED; CP-0004A/B/C incomplete.
