# CP-0002A JS8 native RX integration evidence

Checkpoint objective: replace the FieldOps JS8 RX bridge stub with the pinned JS8Call Android JNI engine boundary while keeping JS8 RX on the shared continuous 12 kHz stream. No TX work is included.

## Upstream pin

- Repository: `JS8Call-improved/Android-port`
- Commit: `9996202f355569c5ee7b97fae539f3b763081dc2`
- License: GPLv3 upstream; keep this boundary explicit.
- Exact upstream Android Build run: `36659533828`
- Run conclusion checked on GitHub: **success**
- Workflow artifact: `11073478127`, `js8call-debug-apk`

## Checked artifact hashes

The exact successful upstream workflow artifact was downloaded and inspected during CP-0002A integration.

- workflow artifact ZIP SHA-256:
  `ee23c95a6d0b5ea2d85cee02c7a4ae5db8a5e780fc39ff9294ee483a074d4aba`
- contained `app-debug.apk` SHA-256:
  `d24775af81c2a3fc66677bf3c024c964b67ea3615252bf702604489b6f2d9046`
- contained ARM64 `libjs8core-jni.so` SHA-256:
  `6a5a9e707b49c5c8c9f2f262f48e22de69a02dfad2888f149be394bd8c8632e9`

## Exact JNI export check

`nm -D --defined-only libjs8core-jni.so` was run against the ARM64 library extracted from that exact artifact. Required CP-0002A RX/lifecycle exports were present:

- `Java_com_js8call_core_JS8Engine_00024Companion_nativeCreate`
- `Java_com_js8call_core_JS8Engine_nativeStart`
- `Java_com_js8call_core_JS8Engine_nativeStop`
- `Java_com_js8call_core_JS8Engine_nativeDestroy`
- `Java_com_js8call_core_JS8Engine_nativeSubmitAudio`
- `Java_com_js8call_core_JS8Engine_nativeSubmitAudioRaw`
- `Java_com_js8call_core_JS8Engine_nativeSetSubmodes`

The export owner for creation is therefore the Kotlin companion object form, not `Java_com_js8call_core_JS8Engine_nativeCreate`.

## Upstream API checked

At the pinned commit, `adapters/android/jni/kotlin/JS8Engine.kt` provides:

- `JS8Engine.create(...)`
- 12 kHz-capable sample-rate configuration
- `start()`, `stop()`, `close()`
- `submitAudio(ShortArray, timestampNs)`
- `CallbackHandler.onDecoded(utc, snr, dt, freq, text, type, quality, mode, driftMs)`

The upstream Android service maps the integer `utc` callback as HHMMSS UTC to the nearest UTC day using a +/-12 hour rollover rule. FieldOps mirrors that behavior when producing epoch milliseconds.

## FieldOps CP-0002A boundary

Production FieldOps code now:

- routes JS8 through `Streaming12kDspEngine`, not `SlotWindowAssembler`;
- consumes the common 48 kHz -> 12 kHz decimated stream continuously;
- converts normalized Float PCM to signed PCM16 for upstream `submitAudio`;
- maps upstream decode callbacks into `DecodeResult`;
- binds directly to `com.js8call.core.JS8Engine` in `Js8CallAndroidEngineFactory`;
- disables the upstream TX audio tap;
- exposes no JS8 transmit method through the CP-0002A FieldOps native boundary.

## Evidence status

- Upstream exact commit: checked.
- Upstream exact Android CI run: checked, successful.
- Exact ARM64 JNI binary export set: checked from the successful workflow artifact.
- FieldOps host compile/deterministic callback tests: enforced by `scripts/test_js8_rx.sh` and the CP-0002A GitHub Actions workflow.
- Actual S23 Ultra + FTX-1 RX: **not tested** in CP-0002A.
- JS8 TX: **not part of CP-0002A**.
- Final Android AAR/APK packaging: remains a later Android/NDK pipeline task.
