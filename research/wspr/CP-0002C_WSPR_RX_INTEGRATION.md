# CP-0002C — WSPR native RX integration evidence

Checkpoint objective: replace the saved WSPR Bridge RX stub with a pinned native WSPR decoder, feed it from FieldOps' existing 12 kHz weak-signal receive branch, and prove an end-to-end receive fixture. WSPR transmit remains outside this checkpoint.

## Pinned upstream source

- Repository: `Guenael/rtlsdr-wsprd`
- Commit: `1ca9b83dd2562ce9ef2453aacdd5bc3aab982c7d`
- Decoder license: GNU GPL v3
- Exact vendored source blobs: `native/wspr/UPSTREAM_BLOBS.tsv`
- Vendoring/provenance note: `native/wspr/UPSTREAM.md`

The files under `native/wspr/upstream/` are copied from that exact commit without FieldOps edits. CI recomputes each Git blob ID and compares it with the pinned upstream blob ID.

## Upstream behavior checked directly at the pin

The pinned decoder accepts complex 375 sample/s I/Q through:

`wspr_decode(float *idat, float *qdat, int samples, ...)`

The checked upstream implementation establishes these WSPR invariants:

- receive interval: 120 seconds
- decoder input rate: 375 complex samples/s
- decoder window: 45,000 complex samples
- channel symbols: 162
- samples per WSPR symbol at 375 sps: 256
- tone spacing: 375 / 256 = 1.46484375 Hz
- nominal encoded signal time: 162 × 256 / 375 = 110.592 seconds

The pinned upstream self-test encodes `K1JT FN20QI 20`, places the signal at a +50 Hz complex-baseband offset beginning 2 seconds into the receive interval, and verifies decoded call `K1JT`, locator `FN20`, and power `20`.

The decoder uses the FFTW3 single-precision C API. CP-0002C therefore validates the exact decoder on Linux CI with `libfftw3f`; Android/NDK packaging of FFTW3 is not claimed complete here.

## FieldOps receive path

`12 kHz real PCM -> 1500 Hz complex mixer -> FIR /4 -> FIR /4 -> FIR /2 -> 375 sps complex I/Q -> pinned wspr_decode()`

`WsprRxFrontEnd` requires one exact 120-second 12 kHz window (1,440,000 real samples) and produces exactly 45,000 I/Q pairs.

The three decimation stages are deliberately separate so their anti-alias limits are explicit:

1. 12,000 -> 3,000 sps, 65-tap low-pass
2. 3,000 -> 750 sps, 65-tap low-pass
3. 750 -> 375 sps, 129-tap low-pass

`WsprEngineAdapter` maps native decoder output to the FieldOps `DecodeResult` model. Its `encode()` method intentionally throws in CP-0002C so WSPR TX cannot be accidentally promoted before CP-0002D.

## Native/JNI boundary

FieldOps adds:

- `native/wspr/fieldops_wspr_bridge.c/.h`
- `native/wspr/fieldops_wspr_jni.c`
- `WsprJniBridge.kt`

Required JNI exports are checked during the focused test gate.

A first host integration run exposed a process crash when the pinned decoder was invoked directly from a JVM native thread. Inspection showed the upstream decoder keeps several large buffers on the C stack. FieldOps does not modify the pinned upstream files; the adaptation layer now invokes `wspr_decode()` on a dedicated pthread with an explicit 8 MiB stack. This preserves the exact upstream decoder while avoiding JVM/Android caller-stack assumptions.

## Deterministic end-to-end fixture

The focused test obtains the 162 symbols from the pinned native `get_wspr_channel_symbols()` implementation for:

`K1JT FN20QI 20`

FieldOps then synthesizes a real 12 kHz 4-FSK receive waveform:

- 1500 Hz WSPR audio center + 50 Hz fixture offset = 1550 Hz
- 8192 samples/symbol at 12 kHz
- 1,327,104 signal samples
- 110.592 seconds signal duration
- 2-second start offset inside the 120-second window
- deterministic low-level noise

The test then exercises the actual FieldOps 12 kHz -> 375 I/Q front end and the pinned native decoder. It requires recovery of K1JT / FN20 / 20 and verifies the FieldOps mapping.

## Verified host evidence

Focused branch run `37304795600`:

- vendored upstream blob verification: PASS
- core regression: PASS 42,062 assertions
- pipeline assertions: 56
- LoTW assertions: 19
- native WSPR shared library/JNI compile: PASS
- required WSPR JNI exports: PASS
- native encoder -> synthesized 12 kHz real waveform -> FieldOps downconverter -> pinned native decoder: PASS
- WSPR RX deterministic assertions: **22 PASS**
- focused gate: **PASS**

Comprehensive branch run `37305021201` independently re-ran the WSPR gate and the inherited JS8 gate:

- WSPR RX: **22/22 PASS**
- JS8 RX: **19/19 PASS**
- JS8 TX: **39/39 PASS**
- core: **42,062 PASS**
- pipeline: **56 PASS**
- LoTW: **19 PASS**

## Evidence boundary

Verified/host-tested:

- exact upstream source pin and vendored blob identity
- native decoder build on Linux x86_64
- JNI boundary on Linux x86_64
- 12 kHz real -> 375 complex-I/Q FieldOps front end
- deterministic native-encoder-to-native-decoder receive loop
- WSPR TX remains unavailable from the production adapter

Not yet verified:

- Android arm64-v8a build of the WSPR/FFTW3 native module
- final Android AAR/APK packaging
- actual Galaxy S23 Ultra execution
- actual FTX-1 USB receive audio routing/levels
- off-air WSPR decode from the real radio
- WSPR RF transmit; reserved for CP-0002D
