# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0009G-ANDROID_ARCHIVE_PERSISTENCE_CONTRACT**
Project version: `v58-android-archive-persistence-contract`
Phase: **App-private opt-in archive storage simulated and bounded; physical device/radio deferred**
Test status: **GREEN CI: canonical bounded storage codec=PASS, opt-in Robolectric private store=PASS, debug APK=PASS, inherited=PASS; finalizer run 38132196485**

## What is complete in this checkpoint
- Robolectric API35 validates actual Android Activity navigation, compare/delete/page and launcher interactions
- Transient configuration recreation retains in-memory session; fresh process has synthetic fixtures only
- Bounded canonical private archive envelope roundtrip, tamper and corrupt-restore checks
- Explicit Android user opt-in for atomic-replace app-private save, restore and delete
- Real Android Activity navigates immutable canonical CP-0009C archive workspace state machine
- Synthetic offline reports support select, delete, paging, paired comparison and clear comparison
- Original canonical receipt and historical warnings shown; no radio/USB/TX/RF or network interaction
- APK includes Kotlin archive core and passes aapt manifest, ZIP, SHA and Actions artifact checks
- All CP-0009D and CP-0009C inherited host regression gates green

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C DEFERRED; do not resume without explicit instruction
- CP-0004A/B/C hardware checkpoints incomplete
- No physical device/RF/CAT/USB/account evidence; app-private storage not encrypted; real-world power-loss durability unverified

## Continue with these exact actions
1. CP-0009H: private archive restore failure/recovery UI and Android durability hardening, GitHub/CI-only
2. Preserve APK artifact build plus all inherited host regression gates
3. CP-0003C remains DEFERRED; skip manual hardware/account validation

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.

## IN PROGRESS — CP-0009H is not durable

Branch cp-0009h-archive-recovery-failure-injection; parent durable main CP-0009G/v58. Android OptInArchiveStore prevents silent overwrites of a corrupt private saved archive and exposes staged-sync / before-rename simulated failure points. Robolectric tests verify earlier committed bytes, temp cleanup and explicit recovery controls, not physical device power-loss durability. Only promote CP-0009H/v59 after exact-head full CI, safe PR merge, main finalizer, manifest/snapshot SHA verification. CP-0003C DEFERRED; CP-0004A/B/C incomplete.

## Additional CP-0009H in-progress recovery evidence

Pull request: https://github.com/furfurr27-ai/FTX-1-App/pull/58
Current implementation additions: bounded FileInputStream reads during restore
and prior-save validation, oversize-file fail-closed Robolectric test, and
matching Android static contract. Commits be2343e, 2dea86a and 405c14a
are on the active branch; inspect newest head rather than assuming those
are final. Earlier CI checks on 437afe9 are not valid for later changes.
Competing PR #57 is not the selected integration path. Do not merge both.
