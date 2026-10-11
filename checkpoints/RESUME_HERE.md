# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0009E-ANDROID_ARCHIVE_UI_INTEGRATION**
Project version: `v56-android-archive-ui-integration`
Phase: **APK-integrated synthetic archive UI host verified; storage and hardware deferred**
Test status: **GREEN CI: Android archive UI APK=PASS, no-privilege manifest=PASS, canonical Kotlin host regression=PASS, inherited=PASS (44+ CI jobs); finalizer run 38121222592**

## What is complete in this checkpoint
- Real Android Activity navigates immutable canonical CP-0009C archive workspace state machine
- Synthetic offline reports support select, delete, paging, paired comparison and clear comparison
- Original canonical receipt and historical warnings shown; no radio/USB/TX/RF or network interaction
- APK includes Kotlin archive core and passes aapt manifest, ZIP, SHA and Actions artifact checks
- All CP-0009D and CP-0009C inherited host regression gates green

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C DEFERRED; do not resume without explicit instruction
- CP-0004A/B/C hardware checkpoints incomplete
- No emulator/device UI automation, physical install, persistent Android archive, CAT/audio/RF or live-source proof

## Continue with these exact actions
1. CP-0009F: Android synthetic archive navigation test and storage boundary contract, GitHub/CI-only
2. Preserve APK artifact build plus all inherited host regression gates
3. CP-0003C remains DEFERRED; skip manual hardware/account validation

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
