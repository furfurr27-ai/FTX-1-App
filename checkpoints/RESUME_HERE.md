# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0001-GITHUB_SURVEY**
Project version: `v6-reference-checkpoint`
Phase: **Architecture/reference consolidation before native JS8/WSPR replacement**
Test status: **PASS core=42062 pipeline=56 LoTW=19; FT-family JNI symbols present; Kotlin adapter compile PASS**

## What is complete in this checkpoint
- Scanned GitHub ham-radio/amateur-radio topic sets and targeted categories; over 100 repository hits surfaced.
- Pinned 27 high-value reference repositories to exact commits and documented applicable design lessons.
- Accepted FTX-1-only hardware architecture; extensibility moves to modem/award/map/sync/propagation layers.
- Implemented immutable checkpoint manifests, SHA-256 snapshots, LATEST/CURRENT_STATE, RESUME_HERE, create/verify tools.
- Re-ran current host tests successfully and recorded the saved-code discrepancy for JS8/WSPR.

## Known blockers / red items
- Saved working tree still uses JS8 and WSPR Bridge stubs; native implementations are not durably integrated yet.
- TrustedQSL/tqsllib ARM64 Android signer remains missing; live LoTW upload stays gated.
- Android SDK/NDK/device/FTX-1 USB and RF validation remains outstanding.

## Continue with these exact actions
1. Replace JS8 Bridge stub with pinned JS8Call Android continuous JNI engine while preserving FieldOps-owned PTT/audio.
2. Vendor/build pinned WSPR C core and replace WSPR Bridge stub; add native self-decode/golden tests.
3. Create a post-native checkpoint before starting TrustedQSL signing integration.
4. Deep-dive fldigi/Wavelog/wfview reference files before freezing DigitalModeProvider, AwardEvaluator and reconnect APIs.

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
