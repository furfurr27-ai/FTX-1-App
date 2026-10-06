# CP-0003C — Galaxy S23 Ultra / Real LoTW Validation

Status: **PREPARED, DEVICE/ACCOUNT GATES STILL RED**

Parent durable checkpoint: `CP-0003B-LOTW_TRANSACTION_SAFE`.

CP-0003C is not complete until the real Galaxy S23 Ultra and real LoTW account/certificate flow pass. This document is the exact operator procedure for the remaining external gates.

## What CI now proves

The CP-0003C branch has host/CI evidence for:

- Official TrustedQSL **2.8.6** release archive verification:
  `182e5f2ac35a3db8b409b45d96505e6bd265ae4668ed064754209c4b8e7bdf37`
- Android `arm64-v8a` cross-build of the official TrustedQSL library sources plus the FieldOps JNI bridge.
- Static Android dependency closure for OpenSSL, Expat, SQLite and zlib; only Android system libraries remain dynamic.
- AArch64 ELF verification.
- Required JNI export verification.
- Explicit app-private TrustedQSL writable data root and read-only resource root.
- Native restore of the Callsign Certificates and Station Locations from a normal TQSL `.tbk` backup.
- Backup restore deliberately ignores TQSL preferences and the desktop duplicate-history database.
- Host signer regression including backup restore.
- The CP-0003B transaction regression and inherited core/LoTW regressions.
- A dedicated CP-0003C validation APK build path.

This is **Android-build-ready / host-tested**, not yet real-device-tested.

## Why the validation app uses a TQSL backup

A TQSL `.tbk` backup contains the pieces needed to reproduce the real signing environment, including Callsign Certificates and Station Locations. The validation importer restores those two categories into FieldOps app-private storage. It does not import preferences and does not import the desktop TQSL duplicate-QSO database.

The backup file contains sensitive signing material. Treat it like a private key backup. Do not commit it, attach it to GitHub, paste it into chat, or place it in a public cloud folder.

Official TQSL backup/restore reference:

https://lotw.arrl.org/lotw-help/backuprestore/

## Prepare on the computer that already has working TQSL

1. Open TQSL.
2. Use **File -> Backup Station Locations, Certificates, and Preferences...**
3. Save a fresh `.tbk` file somewhere you can transfer privately to the Galaxy S23 Ultra.
4. Note the **exact Station Location name** you use for N0PNG at the QSO location.
5. Prepare an ADIF file containing **exactly one real QSO that has not already been uploaded to LoTW**.
6. Have the following available without putting them in any project file:
   - LoTW web username
   - LoTW web password
   - private-key password only if the Callsign Certificate key is password protected; an unprotected key may leave this field blank

Do not use a fabricated contact. The live validation QSO must be a genuine logged QSO.

## Install the CP-0003C validation APK

Use the GitHub Actions artifact named:

`fieldops-cp0003c-validation-apk`

Install `app-debug.apk` on the Galaxy S23 Ultra.

The validation app is intentionally separate from the future production FieldOps UI. It has no automatic upload path.

The app sets Android `FLAG_SECURE`, so screenshots and screen recordings are blocked while credentials/private-key passwords may be visible.

## Gate 1 — restore real TQSL state

1. Launch **FieldOps CP-0003C**.
2. Tap **Select TQSL backup (.tbk)**.
3. Select the fresh backup.
4. Required result:

`TQSL backup import PASS`

The backup bytes are wiped from the validation app's working buffer after import. TrustedQSL's resulting certificate/key/station files remain only in the app's private files directory.

## Gate 2 — load one genuine QSO

1. Tap **Select one-QSO ADIF**.
2. Choose the ADIF containing exactly one not-yet-uploaded genuine QSO.
3. Confirm the displayed CALL, date, time, band and mode are correct.
4. Enter:
   - exact TQSL Station Location name
   - Station callsign
   - DXCC entity number

For the current N0PNG Germany setup, the defaults are N0PNG and DXCC 230, but the operator must verify them against the actual Station Location before proceeding.

## Gate 3 — real signing with NO upload

1. Enter the private-key password.
2. Tap **Prove real signing only (NO UPLOAD)**.
3. Required result begins:

`DEVICE SIGNING PASS`

This proves that official TrustedQSL running as Android AArch64 can use the restored real Callsign Certificate/private key and real Station Location to generate a signed TQ8 payload.

This gate explicitly rolls the TrustedQSL duplicate transaction back. It sends nothing to LoTW.

Do not continue to the live gate if signing-only fails.

## Gate 4 — one live LoTW transaction

Before this step, verify again that the selected QSO is genuine and has not already been uploaded.

1. Enter:
   - LoTW web username
   - LoTW web password
   - private-key password
2. Tap **RUN LIVE LoTW TRANSACTION**.
3. Read the confirmation dialog.
4. Tap **UPLOAD ONE QSO** only when ready.

The exact transaction remains:

`sign -> upload TQ8 -> poll accepted-QSO report -> verify this QSO -> commit TrustedQSL duplicate state`

A successful HTTP upload by itself is not enough. FieldOps commits TrustedQSL duplicate state only after the accepted-QSO report contains the selected QSO.

The LoTW upload service does not require web-account login because the TQ8 is self-authenticating. The web username/password are used for the acceptance and confirmation report queries.

Official developer references:

- Upload service: https://lotw.arrl.org/lotw-help/developer-submit-qsos/
- Acceptance/confirmation queries: https://lotw.arrl.org/lotw-help/developer-query-qsos-qsls/

Required result begins:

`CP-0003C LIVE TRANSACTION PASS`

## Confirmation-sync interpretation

The validation app also queries the LoTW confirmation report after upload.

The selected QSO may legitimately show:

`confirmed=no`

immediately after upload because the other station may not yet have submitted a matching QSO. That is **not** a confirmation-sync failure.

The CP-0003C confirmation gate is that the authenticated confirmation report query succeeds and is parsed correctly. A later matching QSL can transition the QSO to CONFIRMED.

## Secret-hygiene gate

Required:

- no `.tbk`, PKCS#12, private key, certificate backup, LoTW password, key password or token committed to GitHub
- no password/private-key material in validation status text
- no credentials in GitHub Actions inputs or artifacts
- Android app backup disabled
- credential fields never saved in SharedPreferences/database
- password fields cleared after use
- private-key password CharArray wiped after use
- imported backup working buffer wiped after import
- Android screenshots/recent-app thumbnails blocked while the validation app is open

The TQSL backup selected by the operator remains the operator's external file and is not copied into the repository or CI.

## Capture sanitized evidence

After the live transaction finishes, tap:

**Copy sanitized validation result**

Paste the copied text into the project chat. The latest validation APK emits one cumulative gate block containing only:

- CP-0003C label
- source commit SHA embedded in the APK
- device manufacturer/model
- Android SDK level
- backup-import PASS/FAIL
- signing-only PASS/FAIL
- live-transaction PASS/FAIL
- confirmation-sync PASS/FAIL
- automatic-upload state
- sanitized final result text

It does not include the QSO identity, web credentials, private-key password, certificate bytes, or backup contents.

The repository validator rejects the evidence unless the source SHA matches the approved APK baseline, the device is an SM-S918 Galaxy S23 Ultra, all four gates are PASS, automatic upload remains DISABLED, and the final result proves both live acceptance and confirmation-report query success.

That sanitized evidence must be committed into the final CP-0003C checkpoint before CP-0003C can become GREEN.

## Final CP-0003C completion criteria

Do not create the immutable final CP-0003C checkpoint until all are true:

- Android arm64-v8a package gate PASS
- validation APK gate PASS
- TQSL backup import PASS on the S23 Ultra
- real signing-only gate PASS
- real LoTW sign/upload/acceptance verification/commit PASS
- confirmation report sync PASS
- secret-hygiene review PASS
- sanitized device evidence recorded in the repository
- automatic upload remains disabled unless explicitly enabled by a later checkpoint

Until then, the latest immutable verified checkpoint remains `CP-0003B-LOTW_TRANSACTION_SAFE`.
