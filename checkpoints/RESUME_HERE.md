# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0009N-EXPLICIT_ENCRYPTED_ARCHIVE_RECOVERY_CHOICE**
Project version: `v65-explicit-encrypted-archive-recovery-choice`
Phase: **Explicit reviewed encrypted archive recovery host-tested; device/RF deferred**
Test status: **GREEN CI: explicit recovery Robolectric=PASS, stale-selection/lost-key tests=PASS, APK=PASS, inherited=PASS; finalizer 39392390679**

## What is complete in this checkpoint
- Explicit review/select/confirm of authenticated original or rotated V2 recovery into memory
- Fresh reinspection plus canonical digest and entry-count check reject changed evidence
- Robolectric tests both choices, changes, missing keys, deleted copies and session opt-ins
- Android two-tap recovery and blocked nonexecuting old-key retirement checklist
- Real Android debug APK and inherited synthetic Kotlin/Robolectric host regressions succeeded

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C DEFERRED; do not resume without explicit instruction
- CP-0004A/B/C hardware checkpoints incomplete
- No key retirement, trusted freshness witness or physical Galaxy S23 Ultra/Keystore/hard-link/RF/CAT/USB/account proof

## Continue with these exact actions
1. CP-0009O: read-only recovery audit trail and provenance UX, GitHub/CI-only
2. Preserve APK artifact build plus all inherited host regression gates
3. CP-0003C remains DEFERRED; skip manual hardware/account validation

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
