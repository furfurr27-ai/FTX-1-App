# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0009J-AUTHENTICATED_ARCHIVE_ENCRYPTION**
Project version: `v61-authenticated-archive-encryption`
Phase: **Authenticated V2 app-private store and Robolectric failure tests verified; real device/RF deferred**
Test status: **GREEN CI: V2 AES-GCM crypto host=PASS, Keystore-API encrypted store Robolectric=PASS, APK=PASS, inherited=PASS; finalizer 38380915587**

## What is complete in this checkpoint
- AES-256-GCM V2 bounded authenticated header/ciphertext and canonical V1 receipt validation tested on JVM
- Android Keystore AES-256-GCM key creation occurs only on explicit V2 save; restore never creates replacement key
- Encrypted V2 archive saved in separate no-backup file with atomic staging and previous V1 plaintext preserved
- Robolectric injects software keys to verify encrypted save/restore, wrong key/tag, missing key, oversized files and precommit failures
- Android UI requires separate opt-in; explicit V1-to-V2 copy/restore/delete do not downgrade or silently erase plaintext
- Actual Android debug APK, aapt/ZIP/artifact plus inherited host regressions succeeded

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C DEFERRED; do not resume without explicit instruction
- CP-0004A/B/C hardware checkpoints incomplete
- No physical Galaxy S23 Ultra/Android Keystore hardware backing, power-loss, RF/CAT/USB/account or source-authenticity proof

## Continue with these exact actions
1. CP-0009K: encrypted archive key lifecycle, rotation and replay-recovery contract, GitHub/CI-only
2. Preserve APK artifact build plus all inherited host regression gates
3. CP-0003C remains DEFERRED; skip manual hardware/account validation

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.

## CP-0009K unverified implementation

Branch `cp-0009k-encrypted-key-lifecycle-replay-contract`, parent CP-0009J/v61. Added fail-closed host policy
for lost keys, unverified ciphertext, competing encrypted copies, consent,
new keys, atomic staging and readback. Replay remains undetectable without
trusted monotonic state; no rotation writer exists. Host tests are not
full CP-0009K verification. Complete inherited APK/Robolectric CI, PR
premerge, merged-main finalizer and immutable SHA before promoting.
CP-0003C DEFERRED; CP-0004A/B/C incomplete.
