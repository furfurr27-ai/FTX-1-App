# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0002D-WSPR_NATIVE_TX**
Project version: `v10-wspr-native-tx`
Phase: **Native modes: JS8 RX/TX and WSPR RX/TX host-integrated; native-mode regression next**
Test status: **GREEN host/CI: core=42062 pipeline=56 LoTW=19 WSPR_RX=21 WSPR_TX=56 JS8_RX=19 JS8_TX=39; exact pinned WSPR blobs verified; Android/device/RF proof not run; finalizer run 37314097215**

## What is complete in this checkpoint
- Pinned upstream get_wspr_channel_symbols promoted to the production WSPR message codec boundary
- Continuous-phase 12 kHz WSPR 4-FSK synthesis proves 162 symbols, 8192 samples/symbol, 1.46484375 Hz spacing, 1327104 samples and 110.592 seconds
- Production pinned-symbol waveform round-trips through FieldOps RX and the pinned native decoder as K1JT FN20 20
- WSPR physical TX path is restricted to Ftx1RadioSession under Owner.WSPR
- Competing JS8 TX owner cannot be stolen or disturbed by WSPR
- Normal completion, cancel and injected audio-write failure attempt TX0, close audio and release WSPR ownership
- Malformed nonempty WSPR input fails before CAT PTT, TX audio open or radio ownership
- Focused WSPR TX PASS 56; WSPR RX PASS 21; inherited JS8 RX PASS 19 and JS8 TX PASS 39

## Known blockers / red items
- Android arm64-v8a WSPR plus FFTW3 packaging has not been validated
- Actual Samsung Galaxy S23 Ultra plus Yaesu FTX-1 USB TX output rate/routing/level has not been tested
- WSPR RF power, ALC, spectral purity, frequency accuracy and off-air transmission have not been tested
- WSPR even-minute beacon scheduling is not proven by this checkpoint

## Continue with these exact actions
1. CP-0002E: run FT8/FT4/FT2, JS8, WSPR and APRS regression families separately
2. Verify shared audio/timing/TX-ownership composition and keep mode-family gates independent
3. Create one immutable native-modes checkpoint only after every required regression family is green; then proceed to CP-0003A TrustedQSL signer bridge

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
