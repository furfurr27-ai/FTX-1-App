# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0009C-ARCHIVE_WORKSPACE_BUILD_READINESS**
Project version: `v54-archive-workspace-build-readiness`
Phase: **Host archive navigation verified; Android APK packaging not ready; hardware deferred**
Test status: **GREEN host/CI: archive-workspace=PASS, Android packaging static gap=NOT_READY, inherited=PASS (43 CI test jobs); finalizer run 38083056859**

## What is complete in this checkpoint
- Caller-picture archive capture, selection, deletion, bounded paging and two-original paired historical comparison
- Eviction reconciles selection/comparison state; stale/tampered receipts and invalid history state rejected
- Accessible plain-text history labels and preserved archived/non-live/authentication/persistence warnings
- Repository static APK readiness audit accurately identifies absent Gradle app/wrapper/manifest and APK CI
- CP-0009B and full inherited host regression gates preserved

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not resume without explicit command
- CP-0004A/B/C hardware work remains incomplete
- No Android Activity/Gradle APK, Android DB, phone/radio/RF, USB, accounts or origin authentication proof

## Continue with these exact actions
1. CP-0009D Android Gradle application packaging foundation and APK CI, GitHub/CI only
2. Preserve bounded archive/history contract and require actual CI build artifact before APK claim
3. CP-0003C remains DEFERRED; skip hardware/account validation

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.

## IN PROGRESS, not durable: CP-0009D

Branch: cp-0009d-android-apk-build-foundation. Durable checkpoint above remains CP-0009C/v54. CP-0009D seeks real Android Gradle APK packaging, a no-radio launcher shell, actual CI debug APK ZIP/package inspection and artifact upload, plus full inherited regression verification. Before claiming v55, merge exact-head green PR and verify main finalizer, manifest and snapshot SHA. No physical device install or FTX-1/RF proof. CP-0003C DEFERRED, CP-0004A/B/C incomplete.
