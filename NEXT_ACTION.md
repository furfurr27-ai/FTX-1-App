# NEXT ACTION — FTX-1 FieldOps

**CP-0003C — Galaxy S23 Ultra / real LoTW validation**

The branch preparation is GREEN:

- official TrustedQSL 2.8.6 Android arm64-v8a package: PASS
- CP-0003C signer regression: 56 PASS
- CP-0003B transaction regression: 53 PASS
- inherited core/pipeline/LoTW: 42,062 / 56 / 19 PASS
- repository secret-hygiene gate: PASS, run `37342247174`
- device-validation APK: PASS, run `37341539916`
- APK SHA-256: `14417f9065c7f3a43a1245f22e8946a3a4d81831dac7fdec8b39ef71a859aa68`
- artifact: `fieldops-cp0003c-validation-apk` id `11358268081`

The latest immutable checkpoint remains `CP-0003B-LOTW_TRANSACTION_SAFE`. CP-0003C is **not complete** until the real-device/account gates pass.

Exact next action:

1. Install the validation APK on Chris's Galaxy S23 Ultra.
2. Import a fresh private TQSL `.tbk` backup.
3. Select exactly one genuine, not-yet-uploaded QSO.
4. Run **real signing only / NO UPLOAD** and require DEVICE SIGNING PASS.
5. Only after that passes, explicitly authorize the one-QSO live LoTW transaction.
6. Require accepted-QSO verification before TrustedQSL commit and require confirmation-report query success.
7. Copy the app's sanitized validation evidence back into this project.
8. Commit that evidence, re-run hygiene/regressions, and only then finalize the immutable CP-0003C checkpoint.

Operator procedure: `research/tqsl/CP-0003C_DEVICE_VALIDATION.md`.

Automatic LoTW upload remains disabled.
