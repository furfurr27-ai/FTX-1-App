# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0009I-ENCRYPTED_ARCHIVE_MIGRATION_CONTRACT**
Project version: `v60-encrypted-archive-migration-contract`
Phase: **Encrypted format reserved, non-executing migration contract CI verified; crypto/RF/device deferred**
Test status: **GREEN CI: CP-0009I migration planner=PASS, canonical legacy=PASS, Android APK/Robolectric=PASS, inherited=PASS; finalizer run 38197040099**

## What is complete in this checkpoint
- Host Kotlin validates bounded canonical legacy format and refuses corrupt, unsupported, oversized or reserved-v2 inputs
- Simulated migration planner always refuses encryption execution; consent/key/atomic fail-closed states tested
- Threat model documents future AEAD AAD, key loss, atomic migration, plaintext retention, downgrade and replay boundaries
- Robolectric API35 validates actual Android Activity navigation, compare/delete/page and launcher interactions
- Transient configuration recreation retains in-memory session; fresh process has synthetic fixtures only
- Fault-injected staged sync and pre-atomic-replace rejection retain prior committed bytes
- Corrupt existing archive cannot be silently overwritten without explicit user delete
- Robolectric archive recovery message and explicit delete-and-retry controls verified
- Explicit Android user opt-in for atomic-replace app-private save, restore and delete
- Real Android Activity navigates immutable canonical CP-0009C archive workspace state machine
- Synthetic offline reports support select, delete, paging, paired comparison and clear comparison
- Original canonical receipt and historical warnings shown; no radio/USB/TX/RF or network interaction
- APK includes Kotlin archive core and passes aapt manifest, ZIP, SHA and Actions artifact checks
- All CP-0009D and CP-0009C inherited host regression gates green

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C DEFERRED; do not resume without explicit instruction
- CP-0004A/B/C hardware checkpoints incomplete
- No encryption/decryption/migration writer is implemented; no physical device/RF/CAT/USB/account/power-loss proof

## Continue with these exact actions
1. CP-0009J: authenticated Keystore-encrypted archive implementation, GitHub/CI-only
2. Preserve APK artifact build plus all inherited host regression gates
3. CP-0003C remains DEFERRED; skip manual hardware/account validation

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
