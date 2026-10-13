# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0009K-ENCRYPTED_KEY_LIFECYCLE_REPLAY_CONTRACT**
Project version: `v62-encrypted-key-lifecycle-replay-contract`
Phase: **No-write key lifecycle and replay policy with Android UX verified; rotation/device/RF deferred**
Test status: **GREEN CI: CP-0009K lifecycle policy=PASS, inherited AES-GCM=PASS, freshness UI Robolectric=PASS, APK=PASS, other inherited=PASS; finalizer 38412488466**

## What is complete in this checkpoint
- No-I/O key lifecycle planner rejects missing key, untrusted ciphertext, conflicting copies, missing consent, distinct key, staging or readback
- Every simulated key-rotation decision remains non-executable; no key writer or automatic key retirement introduced
- AES-GCM authentic ciphertext replay remains possible; no trusted monotonic freshness witness is claimed
- Android UI warns freshness unverified before and after restored authenticated encrypted V2 history
- Encrypted V2 and plaintext V1 opt-in, Robolectric fault cases, actual APK and all inherited host regressions passed

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C DEFERRED; do not resume without explicit instruction
- CP-0004A/B/C hardware checkpoints incomplete
- No key rotation, key retirement, trusted rollback witness or physical Galaxy S23 Ultra/Keystore/RF/CAT/USB/account proof

## Continue with these exact actions
1. CP-0009L: explicit encrypted archive key rotation with staged recovery, GitHub/CI-only
2. Preserve APK artifact build plus all inherited host regression gates
3. CP-0003C remains DEFERRED; skip manual hardware/account validation

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
