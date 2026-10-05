# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0003B-LOTW_TRANSACTION_SAFE**  
Project version: `v13-lotw-transaction-safe`  
Active branch: `cp-0003c-real-lotw-validation`  
Phase: **CP-0003C preparation is GREEN through the real Galaxy S23 Ultra / LoTW device-account boundary. CP-0003C itself is still RED.**

## Durable baseline

CP-0003B remains the latest immutable checkpoint. Its transaction is:

`sign -> upload TQ8 -> accepted-QSO report verification -> TrustedQSL commit`

HTTP upload acceptance alone is only SUBMITTED. Signing/transport/rejection/incomplete-acceptance failures roll back. Only complete accepted-report verification commits the TrustedQSL duplicate transaction.

## CP-0003C branch work already prepared

Do **not** redo this unless a gate fails:

- Official TrustedQSL 2.8.6 Android `arm64-v8a` package: PASS.
- TQSL `.tbk` restore path for Callsign Certificates/private keys and Station Locations: host-tested.
- Desktop preferences and desktop duplicate history are intentionally not restored.
- Signer regression: **56 PASS**.
- CP-0003B transaction regression: **53 PASS**.
- Inherited core/pipeline/LoTW: **42,062 / 56 / 19 PASS**.
- Sanitized device-evidence validator self-test + CP-0003C finalizer dry-run: **PASS**, run `37344968960`.
- Secret-hygiene gate: **PASS**, run `37344968953`.
- Device-validation APK: **PASS**, run `37343953055`.
- Validation APK artifact: `fieldops-cp0003c-validation-apk`, artifact id `11359961439`.
- Validation APK source SHA: `607bffccfd1b81d8e4847583f1630d10b613ca37`.
- Validation APK SHA-256: `4f57334dbf9f4388e707e0b80d6332a9a12b65dbae6b43de2057330b49d1c947`.
- Exact device procedure: `research/tqsl/CP-0003C_DEVICE_VALIDATION.md`.

## Remaining RED gates

1. Install the validation APK on Chris's Samsung Galaxy S23 Ultra.
2. Import a fresh private TQSL `.tbk` backup.
3. Select exactly one genuine QSO that has not already been uploaded to LoTW.
4. Run **real signing only / NO UPLOAD** and require `DEVICE SIGNING PASS`.
5. Only then explicitly authorize the one-QSO live transaction.
6. Require accepted-QSO verification before TrustedQSL commit.
7. Require the authenticated confirmation-report query to succeed.
8. Copy the app's sanitized validation evidence back into this project.
9. Commit that evidence, rerun regression/hygiene gates, and only then create immutable CP-0003C.

Automatic LoTW upload remains disabled.

## Evidence discipline

The branch is **Android-build-ready / host-tested**, not real-device-tested. Do not mark CP-0003C GREEN, merge it as a completed checkpoint, or advance the checkpoint list until the real device/account evidence above exists.

The operator must never commit or paste the `.tbk`, PKCS#12 material, private key, LoTW password, or key password.

## Verification before continuing

Read `README.md`, `NEXT_ACTION.md`, and `research/tqsl/CP-0003C_DEVICE_VALIDATION.md`, then inspect the current branch head and current workflow status.

The immutable latest-checkpoint verifier still refers to CP-0003B until CP-0003C is finalized.
