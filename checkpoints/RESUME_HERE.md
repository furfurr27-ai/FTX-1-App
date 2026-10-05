# RESUME HERE — FTX-1 FieldOps

Latest immutable verified checkpoint: **CP-0003B-LOTW_TRANSACTION_SAFE**

Working checkpoint: **CP-0003C-REAL_LOTW_VALIDATION**  
Working branch: `cp-0003c-real-lotw-validation`  
Project version of latest immutable checkpoint: `v13-lotw-transaction-safe`

Phase: **CP-0003C preparation is GREEN; real Galaxy S23 Ultra / LoTW account validation is the remaining external gate.**

## CP-0003C preparation already complete on the working branch

Do not redo these items unless verification fails:

- Official TrustedQSL 2.8.6 archive verified:
  `182e5f2ac35a3db8b409b45d96505e6bd265ae4668ed064754209c4b8e7bdf37`
- Android `arm64-v8a` / AArch64 TrustedQSL package builds successfully.
- OpenSSL, Expat, SQLite and zlib are statically closed into the native package; dynamic dependencies are Android system libraries only.
- TrustedQSL writable data and resource/config roots are explicit app-private paths.
- TQSL `.tbk` import restores Callsign Certificates/private keys and Station Locations; desktop preferences and duplicate-history database are intentionally not imported.
- Focused regression run `37340705550`:
  - signer **56 PASS**
  - transaction **53 PASS**
  - core **42,062 PASS**
  - pipeline **56 PASS**
  - LoTW **19 PASS**
- Secret-hygiene run `37342247174`: **PASS**
- Device-validation APK run `37341539916`: **PASS**
- APK SHA-256:
  `14417f9065c7f3a43a1245f22e8946a3a4d81831dac7fdec8b39ef71a859aa68`
- Artifact:
  `fieldops-cp0003c-validation-apk`, id `11358268081`
- Exact device procedure:
  `research/tqsl/CP-0003C_DEVICE_VALIDATION.md`

## Remaining RED gates

CP-0003C is **not** a verified checkpoint yet.

The remaining work requires Chris's actual Galaxy S23 Ultra and LoTW/TQSL account material:

1. Install the validation APK on the Galaxy S23 Ultra.
2. Import a fresh private TQSL `.tbk` backup and require backup import PASS.
3. Load exactly one genuine QSO that has not already been uploaded to LoTW.
4. Run **real signing only / NO UPLOAD** and require DEVICE SIGNING PASS.
5. Only after signing-only passes, explicitly authorize exactly one live LoTW transaction.
6. Require the accepted-QSO report to verify the QSO before TrustedQSL duplicate state commits.
7. Require the authenticated confirmation-report query to succeed.
8. Use **Copy sanitized validation result** and return that evidence to the project.
9. Commit sanitized device evidence, re-run hygiene/regressions, and only then create the immutable CP-0003C checkpoint.

Automatic LoTW upload remains disabled.

## Verification before continuing

The latest immutable checkpoint is still CP-0003B. Verify it with:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

For CP-0003C branch work, also verify the latest relevant GitHub Actions runs remain GREEN before changing the device-validation logic.

Do not promote CP-0003C to verified/durable merely because the APK builds. Real-device and real-account evidence is mandatory.
