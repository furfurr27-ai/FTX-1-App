# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0009L-EXPLICIT_ENCRYPTED_KEY_ROTATION**
Project version: `v63-explicit-encrypted-key-rotation`
Phase: **One-shot authenticated distinct-key archive rotation-copy simulated; real device/RF deferred**
Test status: **GREEN CI: explicit rotation Robolectric=PASS, new-key/staging/recovery=PASS, APK=PASS, inherited=PASS; finalizer 38435267578**

## What is complete in this checkpoint
- Distinct AES-256-GCM Keystore alias and separate V2 encrypted copy with authenticated source and staged readback
- Atomic exclusive hard-link publish never overwrites existing candidate; original ciphertext and key remain
- Robolectric tests explicit rotation opt-in, corrupt source/destination, wrong/lost key, conflict, oversized and staged faults
- Android UI requires separate rotation create/restore/delete; FRESHNESS NOT VERIFIED and old V1/V2 retention warnings
- Real Android debug APK and inherited synthetic Kotlin/Robolectric host regressions succeeded

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C DEFERRED; do not resume without explicit instruction
- CP-0004A/B/C hardware checkpoints incomplete
- No automatic active-key cutover, key retirement, replay witness or physical Galaxy S23 Ultra/Keystore/hard-link/RF/CAT/USB/account proof

## Continue with these exact actions
1. CP-0009M: rotated encrypted archive conflict inspection and old-key retirement safety, GitHub/CI-only
2. Preserve APK artifact build plus all inherited host regression gates
3. CP-0003C remains DEFERRED; skip manual hardware/account validation

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.

## CP-0009M WIP — not yet checkpointed

Branch: `cp-0009m-archive-conflict-inspection`, parent CP-0009L/v63.
Independent encrypted candidate inspection, missing-key distinction,
original/rotated comparison, Android UI and Robolectric regression tests
are committed. Do not promote without exact-head PR and push verification,
merge, main CP-0009M finalizer and immutable SHA-256 comparison.
Next CP-0009N after durable CP-0009M. CP-0003C DEFERRED.
