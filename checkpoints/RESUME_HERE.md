# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0002E-NATIVE_MODE_REGRESSION**
Project version: `v11-native-modes-regression`
Phase: **Native-mode foundation regression-locked; TrustedQSL signer bridge next**
Test status: **GREEN host/CI: core=42062 pipeline=56 LoTW=19 FT8=20 FT4=20 FT2=20 JS8_RX=19 JS8_TX=39 WSPR_RX=21 WSPR_TX=56 APRS=2048 composition=130; Android/device/RF proof not run; finalizer run 37316846958**

## What is complete in this checkpoint
- Independent FT8 host adapter regression PASS 20 assertions
- Independent FT4 host adapter regression PASS 20 assertions
- Independent FT2 host adapter regression PASS 20 assertions
- Independent JS8 RX PASS 19 and TX PASS 39 assertions
- Independent WSPR RX PASS 21 and TX PASS 56 assertions with exact pinned WSPR blobs verified
- Independent APRS AX.25/KISS/Bell-202/SmartBeaconing regression PASS 2048 assertions
- Shared 48 kHz/12 kHz, continuous/windowed timing and all-mode TX-ownership composition PASS 130 assertions
- Inherited core PASS 42062, pipeline PASS 56 and LoTW PASS 19

## Known blockers / red items
- Public Git tree omits the extracted ARM64 libft8af.so, so CP-0002E does not execute actual FT8AF native DSP on x86_64 CI
- Actual Samsung Galaxy S23 Ultra plus Yaesu FTX-1 CAT/USB audio runtime has not been tested
- Actual FT-family and JS8 native execution on the phone, Android arm64 WSPR/FFTW3 packaging and real APRS off-air reception remain untested
- Actual RF TX level, ALC, spectral purity and watchdog behavior remain untested

## Continue with these exact actions
1. CP-0003A: pin official TrustedQSL source/version and build a narrow JNI signer bridge
2. Support PKCS#12 certificate import, explicit station-location selection and ADIF to signed GABBI/TQ8 output; fail closed if signer material or location is unavailable
3. Keep signing separate from upload/reconciliation; CP-0003B will own transaction-safe sign-upload-verify-commit behavior

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
