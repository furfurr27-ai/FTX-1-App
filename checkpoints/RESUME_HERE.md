# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0009F-ANDROID_ARCHIVE_NAVIGATION_TESTS**
Project version: `v57-android-archive-navigation-tests`
Phase: **Robolectric navigation/rotation verified; persistent storage and hardware deferred**
Test status: **GREEN CI: Robolectric API35 navigation/rotation=PASS, debug APK=PASS, static storage boundary=PASS, canonical host regression=PASS, inherited=PASS; finalizer run 38129220049**

## What is complete in this checkpoint
- Robolectric API35 validates actual Android Activity navigation, compare/delete/page and launcher interactions
- Transient configuration recreation retains in-memory session; fresh process has synthetic fixtures only
- Real Android Activity navigates immutable canonical CP-0009C archive workspace state machine
- Synthetic offline reports support select, delete, paging, paired comparison and clear comparison
- Original canonical receipt and historical warnings shown; no radio/USB/TX/RF or network interaction
- APK includes Kotlin archive core and passes aapt manifest, ZIP, SHA and Actions artifact checks
- All CP-0009D and CP-0009C inherited host regression gates green

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C DEFERRED; do not resume without explicit instruction
- CP-0004A/B/C hardware checkpoints incomplete
- No physical-device installation or emulator instrumentation; Robolectric is JVM simulation only; no persistent archive, CAT/audio/RF or live source

## Continue with these exact actions
1. CP-0009G: opt-in bounded Android archive persistence contract with synthetic restore/integrity tests, GitHub/CI-only
2. Preserve APK artifact build plus all inherited host regression gates
3. CP-0003C remains DEFERRED; skip manual hardware/account validation

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
