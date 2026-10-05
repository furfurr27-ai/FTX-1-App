# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0003A-TRUSTEDQSL_SIGNER**
Project version: `v12-trustedqsl-signer-bridge`
Phase: **TrustedQSL signer bridge host-verified; transaction-safe LoTW upload next**
Test status: **GREEN host/CI: signer=43 core=42062 pipeline=56 LoTW=19; official TrustedQSL 2.8.6 archive hash/API compile verified; Android ARM64 real certificate/server validation not run; finalizer run 37322373366**

## What is complete in this checkpoint
- Official TrustedQSL 2.8.6 archive SHA256 verified before API compile
- Production JNI bridge compiles against exact official 2.8.6 tqsllib and tqslconvert headers
- In-memory PKCS12 import path uses official tqsllib Base64/import API and wipes transient copies
- Explicit station location callsign and DXCC must match the FieldOps station profile before signing
- Official certificate selection, signing-init, ADIF converter and GABBI API path represented by the production bridge
- Transactional LotwSigningSession preserves explicit TrustedQSL duplicate-state commit and rollback
- Closing an open signer session rolls back and signer/native failures fail closed
- Focused signer bridge PASS 43; inherited core PASS 42062, pipeline PASS 56 and LoTW PASS 19

## Known blockers / red items
- Complete official TrustedQSL dependency stack has not been built/linked for Android arm64-v8a
- Real Callsign Certificate PKCS12 import and cryptographic signing have not been tested on the Galaxy S23 Ultra
- LoTW network upload/acceptance is intentionally not part of CP-0003A
- Automatic LoTW upload remains disabled pending CP-0003C real device/test-account validation

## Continue with these exact actions
1. CP-0003B: migrate upload to sign -> upload -> verify acceptance -> commit TrustedQSL duplicate state
2. Roll back the signer transaction on signing, network, rejection or acceptance-verification failure
3. Keep manual SSB/CW and digital QSOs on the same queue and do not equate HTTP success with LoTW acceptance

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
