# FTX-1 FieldOps — v3 shared DSP/audio build

This build layer converts the earlier architecture into a concrete common signal path for the Galaxy S23 Ultra + Yaesu FTX-1.

## What is real now
- Shared 48 kHz RX audio fanout.
- Filtered 12 kHz weak-signal branch with timestamp/group-delay handling.
- UTC-aligned FT8/FT4/FT2/JS8/WSPR window assembly.
- FT8/FT4/FT2 Android JNI compatibility layer over the native library extracted from the user's FT8AF APK.
- Real phone-side APRS Bell-202 AFSK1200 modulation/demodulation with HDLC framing, NRZI, stuffing and AX.25 FCS.
- One guarded FTX-1 PTT/TX-audio session shared by every mode.
- Android AudioRecord/AudioTrack adapters designed around the single S23 Ultra target.

## What still needs an Android/NDK workstation or the phone
- Build/vendor JS8Call-improved `js8core-lib` and connect it to `Js8EngineAdapter`.
- Build/vendor WSPR `wsprd` and encoder bridge, then connect `WsprEngineAdapter`.
- Compile the Android app shell, install on the S23 Ultra and validate USB device routing.
- Bench RX against known FT8/FT4/FT2/JS8/WSPR WAVs.
- Key the FTX-1 first into a dummy load with low power and verify PTT lead/tail, audio level, ALC and watchdog behavior.

Run `scripts/test_pipeline.sh` for host-side validation.


## Host validation result
Current `scripts/test_pipeline.sh` result:
- core regression suite: **42,062 assertions pass**
- new pipeline suite: **56 assertions pass**
- combined host assertions: **42,118**
- ARM64 FT-family required JNI exports: **present**
- Java ABI shims + FT/JS8/WSPR adapter sources: **compile PASS**

See `research/INTEGRATION_STATUS.md` for the exact green/yellow/red boundary.

## LoTW sync layer (v4 work)
FieldOps now includes a LoTW log state model, ADIF import/export parser, ARRL upload/report HTTP transport, acceptance-vs-confirmation reconciliation, explicit station profiles, post-upload verification timing, retry/backoff policy, and cursor-based periodic confirmation synchronization. The remaining production dependency for automatic upload is the Android/ARM64 TrustedQSL signer binding; unsigned ADIF is never sent as if it were a valid LoTW upload.
