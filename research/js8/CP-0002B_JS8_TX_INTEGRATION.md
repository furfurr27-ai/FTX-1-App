# CP-0002B JS8 native TX integration evidence

Checkpoint objective: enable the pinned JS8Call Android native TX audio tap, keep all rig/PTT authority inside FieldOps, adapt native TX PCM to the FTX-1 USB playback rate, and prove RX-safe collapse on error/cancel/close.

## Upstream pin

- Repository: `JS8Call-improved/Android-port`
- Commit: `9996202f355569c5ee7b97fae539f3b763081dc2`
- Exact successful upstream Android Build run: `36659533828`
- Workflow artifact: `11073478127`, `js8call-debug-apk`

## Exact artifact re-check for CP-0002B

The exact successful workflow artifact was downloaded again during CP-0002B and inspected directly.

- workflow artifact ZIP SHA-256:
  `ee23c95a6d0b5ea2d85cee02c7a4ae5db8a5e780fc39ff9294ee483a074d4aba`
- contained `app-debug.apk` SHA-256:
  `d24775af81c2a3fc66677bf3c024c964b67ea3615252bf702604489b6f2d9046`
- contained ARM64 `libjs8core-jni.so` SHA-256:
  `6a5a9e707b49c5c8c9f2f262f48e22de69a02dfad2888f149be394bd8c8632e9`

Required TX/lifecycle JNI exports found with `nm -D --defined-only`:

- `Java_com_js8call_core_JS8Engine_00024Companion_nativeCreate`
- `Java_com_js8call_core_JS8Engine_nativeTransmitMessage`
- `Java_com_js8call_core_JS8Engine_nativeTransmitFrame`
- `Java_com_js8call_core_JS8Engine_nativeStopTransmit`
- `Java_com_js8call_core_JS8Engine_nativeIsTransmitting`
- `Java_com_js8call_core_JS8Engine_nativeIsTransmittingAudio`
- `Java_com_js8call_core_JS8Engine_nativeTxMillisecondsUntilAudio`
- `Java_com_js8call_core_JS8Engine_nativeSetTxReady`

## Upstream behavior checked at the pinned commit

The pinned Android `JS8Engine` API exposes:

- `transmitMessage(...)`
- `stopTransmit()`
- `isTransmitting()`
- `isTransmittingAudio()`
- `txMillisecondsUntilAudio()`
- `setTransmitReady(Boolean)`
- callback `onTxAudio(ShortArray, sampleRateHz)`

The pinned JNI implementation selects `PumpAudioOutput` when `enableTxAudioTap` is true and sets `tx_output_rate_hz = 11520` for that tap. With the tap enabled, FieldOps receives modem PCM callbacks instead of giving the upstream engine direct FTX-1 audio/PTT ownership.

The upstream sample service was also checked for the transmit gate pattern: it closes `setTransmitReady(false)` while PTT is unavailable, asserts rig PTT shortly before scheduled audio, and opens the transmit gate only after PTT is ready. FieldOps mirrors the safety concept but uses its own CAT/PTT owner rather than the upstream rig-control service.

## FieldOps CP-0002B boundary

- `Js8CallAndroidEngineFactory` enables the native TX audio tap and keeps `useQmxUsbAudio = false`.
- No upstream service/rig/PTT transport is instantiated by the FieldOps binding.
- `Js8TxController` schedules native JS8 TX while the native transmit gate is closed.
- Only `Ftx1RadioSession` may assert CAT `TX1` and write USB TX audio.
- The native transmit gate opens only after FieldOps acquires the JS8 arbiter owner, opens the shared TX audio port, asserts CAT PTT, and completes the PTT lead.
- Native PCM is statefully adapted from its callback rate (expected 11,520 Hz at this pin) to the configured FTX-1 playback domain (48,000 Hz).
- Normal completion sends `TX0`, closes audio, returns RX and releases radio ownership.
- Audio failure, unexpected native audio before FieldOps PTT, cancel and close all fail closed: transmit gate false, native TX stopped, `TX0` attempted, TX audio closed, arbiter released.

## Evidence status

- Exact pinned upstream commit/API: checked.
- Exact successful ARM64 artifact and TX JNI exports: checked.
- Host compile and deterministic RX/TX safety tests: enforced by `scripts/test_js8_native.sh` and CP-0002B CI.
- Actual Galaxy S23 Ultra + FTX-1 RF transmission: **not tested**.
- Actual FTX-1 USB playback acceptance/level at 48 kHz: **not tested**.
- RF spectral quality / ALC / power calibration: **not tested**.
- Final Android AAR/APK packaging: remains a later Android/NDK pipeline task.
