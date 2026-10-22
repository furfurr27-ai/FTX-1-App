# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0009R-ANDROID_OPERATING_NAVIGATION_INTEGRATION**
Project version: `v69-android-operating-navigation-integration`
Phase: **Integrated offline Android operating workspaces host-tested; device/RF deferred**
Test status: **GREEN CI: Android navigation/QSO/awards Robolectric=PASS, APK=PASS, inherited=PASS; finalizer 39870937975**

## What is complete in this checkpoint
- Seven-section Android operating navigation and real shared canonical core profile state
- Manual in-memory CW/SSB logbook, ADIF and Awards Center integration
- Android Robolectric tests for navigation, invalid frequency, QSO, rotation and locked TX
- Preserved synthetic encrypted archive recovery and inherited Kotlin software regression
- Android debug APK with no USB/audio/CAT/PTT/LoTW permissions

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C DEFERRED; do not resume without explicit instruction
- CP-0004A/B/C hardware checkpoints incomplete
- Digital modems and physical FTX-1 CAT/audio/RF absent from integrated app; logbook is memory-only

## Continue with these exact actions
1. Integrate FT8/FT4/FT2 native DSP and radio-safe Android RX workflow with golden vectors
2. Then close all original P0-P3 release acceptance matrix software gaps; stop after final RC
3. CP-0003C remains DEFERRED and CP-0004A/B/C physically unverified

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
