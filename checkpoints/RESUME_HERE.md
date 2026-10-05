# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0002A-JS8_NATIVE_RX**
Project version: `v7-js8-native-rx`
Phase: **Native modes: JS8 RX complete; JS8 TX next**
Test status: **GREEN host/CI: JS8 RX gate PASS 19 assertions; exact pinned ARM64 JNI exports checked; device/RF proof not run; finalizer run 37296091037**

## What is complete in this checkpoint
- JS8 RX routed as continuous 12 kHz stream; no slot assembler
- Production FieldOps binding imports pinned com.js8call.core.JS8Engine API
- Native decode callback maps to FieldOps DecodeResult
- Upstream TX audio tap disabled and no JS8 TX API exposed in CP-0002A
- Focused deterministic JS8 RX suite PASS 19 assertions
- Exact successful upstream ARM64 JNI artifact checked for nativeCreate/start/stop/destroy/submitAudio exports

## Known blockers / red items
- Actual Samsung Galaxy S23 Ultra + Yaesu FTX-1 JS8 RX has not been device-tested
- Final Android AAR/APK packaging of the pinned JS8 native runtime remains unfinished
- Project-library FTX1_FieldOps_CP-0002_JS8_NATIVE.zip remains inaccessible for raw-byte comparison

## Continue with these exact actions
1. CP-0002B: enable upstream TX audio tap while keeping upstream rig/PTT ownership disabled
2. Route JS8 TX chunks only through FieldOps TX arbiter/PTT and USB audio path
3. Prove TX0/audio-stop/ownership-release on error and close, then checkpoint CP-0002B

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
