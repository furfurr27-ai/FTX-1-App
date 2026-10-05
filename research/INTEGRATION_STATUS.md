# Integration status — v3 shared DSP/audio pipeline

## Green: implemented and host-validated

### Shared audio core
- One 48 kHz capture domain.
- Thread-safe fanout to 48 kHz consumers and a single stateful 48 -> 12 kHz FIR decimator.
- 97-tap Blackman-windowed sinc anti-alias filter.
- FIR group delay is reflected in the 12 kHz UTC sample timestamp.
- UTC-aligned slot assembly for FT8, FT4, FT2, JS8 and WSPR.
- Android capture adapter uses `AudioRecord.getTimestamp(..., TIMEBASE_MONOTONIC)` when available, with an elapsed-realtime fallback.

### FT8 / FT4 / FT2
- JNI ABI shim reconstructed directly from the uploaded FT8AF APK.
- FT8/FT4/FT2 native adapter source compiles against that ABI.
- Required ARM64 JNI exports are present in the extracted `libft8af.so`.
- Direct APK enum inspection confirmed:
  - FT8: 79 tones, 0.160 s symbol period, BT=2.0, 15 s slot.
  - FT4: 105 tones, 0.048 s symbol period, BT=1.0, 7.5 s slot.
  - FT2: 105 tones, 0.024 s symbol period, BT=1.0, 3.75 s slot.
- Free-text 77-bit packing + tone generation + GFSK synthesis is wired.

### APRS
- Separate always-listening 48 kHz branch.
- Bell-202 AFSK1200 modulation and block demodulation.
- NRZI, HDLC flags, bit stuffing and CRC-16/X25 AX.25 FCS.
- Streaming facade keeps enough rolling audio to cover callback boundaries.
- APRS shares the radio/PTT arbiter with weak-signal modes.

### Common CAT/PTT
- One platform-neutral serialized Yaesu CAT transport above a byte-serial port.
- Semicolon command normalization/reply framing.
- Extra serial reply bytes are retained rather than dropped.
- One `Ftx1RadioSession` controls phone-generated TX audio and CAT PTT for all modes.
- `TX0` is attempted in `finally`, and `emergencyRx()` exists for USB detach/service failure.
- `RadioModeArbiter` is synchronized so two app threads cannot claim TX simultaneously.

## Yellow: interface complete, upstream native module not vendored here

### JS8
`Js8EngineAdapter` and the common slot/audio wiring are complete. The current JS8Call-improved Android port exposes a platform-independent core and Android JNI/AAR layer, but that GPLv3 source/binary is not vendored in this pack because this runtime could not fetch/build the repository. The adapter therefore needs the upstream bridge implementation before it can decode or transmit JS8.

### WSPR
`WsprEngineAdapter`, 120-second UTC timing and common audio wiring are complete. A GPLv3 `wsprd` decoder/encoder module still needs to be built/vendored in an Android NDK environment.

## Red: must be proven on the actual S23 Ultra + FTX-1 before RF use
- Android USB permission and physical interface enumeration.
- FTX-1 USB audio device routing and actual offered sample rates/channels.
- Android USB serial byte-port implementation / correct FTX-1 CAT interface selection.
- Native ARM64 `libft8af.so` runtime field/signature compatibility.
- FT8/FT4/FT2 decode against known-good WAV fixtures.
- JS8/WSPR native bridge integration and golden-vector tests.
- APRS receive against real off-air/recorded AX.25, not only self-generated AFSK.
- TX level, ALC, PTT lead/tail and watchdog behavior into a dummy load.

No item in the red section should be inferred as tested merely because the host-side code compiles.

## LoTW (added during v4 native-mode work)
GREEN: local QSO/LoTW state model; ADIF 3.1.6 export; LoTW ADIF report parsing; direct upload/report endpoint transport; APP_LoTW_LASTQSORX / APP_LoTW_LASTQSL cursor model; acceptance/confirmation reconciliation; retry/verify timing; explicit station-location profile model; host tests.
YELLOW: Android WorkManager scheduling and encrypted credential/certificate persistence are designed but not yet device-tested.
RED: production TrustedQSL/tqsllib Android ARM64 signer binding and real LoTW test-account/device upload. Automatic upload must remain disabled until this is green.

## v6 research/checkpoint layer (2026-10-04)

GREEN:
- broad connected-GitHub amateur-radio topic scan completed.
- 27 important reference repositories pinned to exact commits.
- architecture decisions recorded for FTX-1-only rig control, modem providers, operating sessions, log sync, awards, map layers, propagation and offline behavior.
- immutable checkpoint tooling implemented with SHA-256 file manifests, tar snapshots, `LATEST.json`, `CURRENT_STATE.json` and `RESUME_HERE.md`.

IMPORTANT CURRENT-CODE FACT:
- The working pack still contains the pre-native `Js8EngineAdapter(private val bridge: Bridge) : WindowedDspEngine` and equivalent WSPR bridge stub. Previous narrative progress about native JS8/WSPR must not be treated as durable completion until code is present in a verified checkpoint.
