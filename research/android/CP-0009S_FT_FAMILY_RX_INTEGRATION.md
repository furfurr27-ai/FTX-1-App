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

## Additional recorded-audio and Android offline receive acceptance (unverified until CI)

- The upstream pin also supplies `decode_bench.c`, a real host C decoder
  that shares the selected native monitor/LDPC/synchronization code with the
  app. `scripts/test_ft8_recorded_wav_decode.sh` compiles the vendored
  native source and downloads one actual 20-meter WAV from immutable upstream
  commit `c2f63e8b37fcd484fd2eb2049494425dd2414971`. Before processing,
  it verifies Git blob SHA-1
  `820972665a551c93ef5334d1e19a917debdaba45`.
- The WSJT-X jt9 truth sidecar is pinned at Git blob
  `3f388c70c844353974a9bb2d6031af444f3c6063`.
  The test gates on 24 matched independent FT8 decoded messages, not
  synthesized Java fake-native results, and runs in the new
  `recorded-ft8-receive` GitHub Actions job.
- Pinned FT8AF's source explicitly routes FT4 through the same fast decoder
  ABI as FT2, with native protocol IDs FT4=0, FT8=1, FT2=2.
  `FtFamilyNativeEngine.kt` and the deterministic host fixture are corrected
  to dispatch FT4 to `InitDecoderFt2`, `DecoderFt2FindSync`,
  `DecoderFt2Analysis` and the matching handle teardown; the regression
  asserts the **exact** entry point and protocol number.
- `FtFamilyOfflineWav` validates 12 kHz mono 16-bit PCM RIFF/WAVE, requires
  exactly one slot of the specifically selected FT8/FT4/FT2 mode, caps
  input at 2 MB, and dispatches the actual native engine. The DIGI workspace
  exposes a user-selected **file-only** decode operation, with no microphone,
  USB, CAT, PTT or QSO/LoTW mutation. The WAV has no authenticated UTC
  timestamp, so all decoded results are explicitly labeled **UNVERIFIED UTC**
  and are never converted automatically into confirmed contacts.
- `FtFamilyOfflineWavTest.kt` checks all three mode-specific slot sizes,
  rejects invalid headers/rates/oversized payloads, and forbids unrelated
  modes. Android tests use a clearly labeled fixture decoder; the native
  real-recording test separately establishes actual C modem behavior.

**Remaining:** Current-head CI must pass. The host-side FT8 recording gate
does not establish FT4/FT2 real-waveform interoperability, Android
ARM64 JNI execution on real device, phone USB audio rates/routes, live
decode timing, QSO protocol state or RF-safe transmission. The software
release remains incomplete and physical CP-0004A/B/C proofs are outstanding.

## Operator-reviewed FT8/FT4/FT2 manual QSO handoff — pending exact-head CI

The DIGI FT-family screen now has a mode-aware `PREPARE MANUAL ... QSO` action that opens the same canonical LOG workspace already used for CW/SSB. The operator must enter frequency/callsign and explicitly confirm **completed two-way** contact before `FastQsoLogger.logManual` writes a MANUAL-provenance FT8/FT4/FT2 record. The entry contributes to the existing ADIF/Awards projection; no decode, CQ or file import can automatically create a QSO, and CAT/PTT/LoTW remain disabled. Robolectric tests cover FT8 manual log, FT4/FT2 mode identity, draft retention and locked TX. This is **not** automated FT QSO sequencing, RF proof, or persistent storage. Full exact-head CI remains required.

## Fast-mode native demodulation regression — pending exact-head CI

- `app/src/test/native/ft8af/test_fast_waveform_decode.c` now exercises actual bundled FT4/FT2 signal processing: fixed 77-bit CQ message → FT4's 105-tone modulation → 12 kHz GFSK waveform at each mode's distinct 48 ms/24 ms symbol period → full 7.5 s/3.75 s receive slot → real monitor, sync search, LDPC and CRC → original message payload. Also checks Costas groups and rejects a silent slot; no synthetic Java decoder is used.
- `scripts/test_ft_fast_waveform_decode.sh` and the `fast-waveform-host` job make this host verification required before the ARM64 APK job. **This is generated clean-channel / same-library round-trip evidence, NOT an independent FT4/FT2 on-air recording, S23 native JNI execution, live CAT/USB audio, or RF proof.**
- New exact-head check must pass before this regression is counted as tested. The preceding `b1dfbb1e8ad1f48a7b5fc969bb07b75fbd1dd01b` did pass 28/28 CI including native FT8 recorded truth and APK; modifying the branch supersedes that exact-head acceptance.

## Independent-source FT4 audio benchmark — pending exact-head CI

- Added `scripts/test_ft4_independent_wav_decode.sh` to retrieve `samples/FT4/000000_000002.wav` from the **separate WSJT-X** Git repository `saitohirga/WSJT-X@2b9d65408debba3f3f2a6a34c8aa33b1308c0f21`. The WAV blob is SHA-1 pinned as `e5cdc88040b477cad842d51c4dd8e5fb6fdbe803` (145,196 bytes), preventing silent fixture replacement. This is also cataloged in the WSJT-X SourceForge FT4 samples collection, distinct from the FT8AF-generated test audio.
- `app/src/test/native/ft8af/test_independent_ft4_wav.c` runs real bundled FT4 DSP, synchronization and LDPC/CRC against the external 12 kHz PCM recording, rejects unsupported metadata and requires at least **one** distinct CRC-valid decoded payload (floor 1). The `independent-ft4-wav` job is now mandatory before ARM64 APK CI.
- **Boundary:** the recording was sourced independently from the modem implementation, but a separate verified WSJT-X/jt9 per-message truth sidecar has **not** been established. Decoding 1 CRC-valid message does not demonstrate recall, WSJT-X parity, full FT4 QSO interoperability, full-length Android WAV import, any FT2 independent-source recording, S23 hardware or RF. Keep FT4/FT2 as `APP_PARTIAL`, distribution on HOLD, and PR draft until actual CI and remaining acceptance requirements pass.
