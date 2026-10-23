# CP-0009S — FT8/FT4/FT2 production Android RX source integration (in progress)

Baseline: durable CP-0009R / v69 / main `3b0038ff87cde4fc1ad680b9680149d592954a45`.

This branch addresses a real P0 Android packaging and shared receive-path gap, not archival UX.

## Actual source changes

- `app/build.gradle.kts` includes the existing `android/compat/src/main/java` ABI declarations and stages **the original** `android/pipeline/.../FtFamilyNativeEngine.kt` into the Android app compilation without duplicating that file or implicitly depending on the unresolved JS8 provider.
- `FtFamilyReceiveSession` connects actual production `SharedAudioPipeline` 48 kHz -> 12 kHz decimation with `WeakSignalCoordinator` mode-specific UTC-aligned windows and an injectable `WindowedDspEngine`. It resets FIR/window state on mode change and owns no PTT/CAT/audio-capture device.
- `FtFamilyNativeAvailability` checks `System.loadLibrary("ft8af")` before constructing the genuine native adapter. If unavailable, it **fails closed**, not by returning a fabricated FT8/FT4/FT2 decode.
- The DIGI screen surfaces the native presence check separately from hardware connectivity and continues to disable RX start and all transmission.
- `FtFamilyReceiveSessionTest` tests real software PCM routing, FT8/FT4/FT2 slot window sizes and timebase, safe mode changes, closed-state rejection, and missing-native failures. Its injected decoder is clearly a TEST FIXTURE.

## Evidence boundary

As of source inspection, the GitHub tree has the Java JNI ABI shims and original Kotlin native FT-family engine, plus host adapter tests under `scripts/test_ft_family_mode.sh` with synthetic Java native fixtures. The actual Android ARM64 `libft8af.so` **is not in this repository**, and the historical `FT8AF-android-dev.1312.apk` upload is not retrievable through this turn's currently accessible project or Library files.

Thus **this PR does not deliver real live or recorded FT-family demodulation yet**. Full P0 acceptance still requires an authorized, source/license-reviewed native provider, real decoder golden-vector interoperability, safe Android USB/audio feed and field validation on the S23 Ultra + FTX-1. CP-0003C DEFERRED; CP-0004A/B/C incomplete. No TX output or account operations.

Do not promote this branch to a durable CP-0009S checkpoint unless Android compile/unit-test CI passes and the actual intended FT-family milestone acceptance criteria are met. Do not classify native decoding or FT-family QSO workflow `ACCEPTED_SOFTWARE` on a fixture alone.

## Source-built ARM64 decoder update (pending CI)

The previous “libft8af.so absent” statement above describes the **original
CP-0009R main source tree**, not the current WIP branch. The branch now contains
and compiles the full minimum from-source FT-family decoder + encoder subset:

- Upstream `patrickrb/FT8AF` pin:
  `c2f63e8b37fcd484fd2eb2049494425dd2414971`
- Embedded `kgoba/ft8_lib` pin:
  `6f528128ee3ebf4d08ba2313f6c5d3913eda5608`
- Native sources: `app/src/main/cpp/third_party/ft8af/`, CMake entry:
  `app/src/main/cpp/CMakeLists.txt`, `arm64-v8a` only, NDK 27.2.
- Compiles selected DSP `ft8_lib`, KissFFT and JNI decode, encode, fine-sync,
  subtraction and cross-slot source. **Deliberately excludes libusb,
  USB audio, Hamlib and radio CAT/PTT functions.**
- JVM JNI compatibility DTO now includes hash/contest fields required by
  current source instead of silently raising missing Java field errors.
- `scripts/test_ft8_from_source_golden.sh` compiles the **real** vendored C
  encoder and checks upstream immutable FT8 encoded message vectors, independently
  of the earlier fake-native Kotlin fixtures.
- `scripts/verify_ft8_native_apk.py` requires a real ARM64 ELF64 `libft8af.so`
  and the expected FT8, FT2 and pack JNI symbols inside the actual debug APK.
- `.github/workflows/cp0009s-ft-family-native.yml` checks golden source, JNI
  signatures, NDK build, Robolectric, and APK contents. Any failures require
  correction before full FT8/FT4/FT2 software acceptance.

**Remaining acceptance gap:** a from-source APK is not yet a validated
decoder session. Real recorded-waveform decoding against known-good
FT8/FT4/FT2 WAV fixtures and JNI process behavior on arm64 must be verified;
no USB audio recording or CAT/PTT has been enabled. The user has not granted
real-radio/device RF validation, so CP-0004A/B/C remain hardware-gated.
`CP-0003C` LoTW account checkpoint stays explicitly DEFERRED.

**Distribution HOLD:** upstream GitHub root currently presents MIT while
its public website calls the whole app GPL-3.0. Both texts are recorded in
`research/LICENSE_PROVENANCE.md`, with source and notices checked in; a
redistributable release requires a proper obligation review.
