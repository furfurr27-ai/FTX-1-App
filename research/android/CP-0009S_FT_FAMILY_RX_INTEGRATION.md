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

## Android shorter-complete-slot WAV parity with independent FT4 archive — pending exact-head CI

- The official WSJT-X FT4 `000000_000002.wav` (145,196 bytes total) has 72,576 mono PCM samples / 6.048 seconds, rather than a padded 7.5-second full slot. A strict full-slot-only importer would reject a technically complete signal before DSP even runs.
- `FtFamilyOfflineWav` now accepts mode-specific recordings between the actual channel symbol burst plus 500 ms timing margin and the one-slot maximum, **zero-padding only the end** to the correct 180,000 / 90,000 / 45,000 sample decoder input. Invalid, odd-sized, undersized and overlong WAVs still fail closed. Relative UTC remains **UNKNOWN**. This does not invent missing RF, authentic timestamp, or automatically save a QSO.
- Android unit tests explicitly check the 6.048-second independent-source-shaped FT4 WAV reaches a complete 7.5-second engine window, the tail is all zero, and malformed duration/padding cannot reach DSP.
- Validation is CI-pending at this new head; physical S23/JNI decode of the independently sourced WAV is not yet established.

## Decoder lifecycle/mode isolation correction — exact-head CI required

- Offline WAV decode completion now posts only to the retained `UiSession` on the Android main looper, with a transient observer attached to the current Activity and detached in `onDestroy`. The background worker holds the application `ContentResolver`, selected mode, request generation and retained session, never a destroyed screen.
- A mode-selection generation invalidates any in-flight older-mode result and clears decoded station review cards, so a late FT4/FT8/FT2 file decode cannot masquerade as a decode from a newly selected mode or create a stale manual QSO prefill.
- Active Android Robolectric regression exercises observer reattachment across rotation, stale-mode decode discard, and continuing locked transmit UI. This is lifecycle software proof only, not Android physical hardware/JNI or radio work.

- Added direct Robolectric integration checks for retained UI observer replacement on screen rotation, completion routing to the currently live Activity, callback detachment after final destroy, and rejection of late previous-mode station review candidates. These use a clearly synthetic decoded result; they are lifecycle tests, not native audio/RF measurements.

## CI failure diagnosis — Robolectric recreation regression repair (pending new head)

- Exact commit `6e66a3fb11ee978a673d2dde99acb21bb7ae24a2` finished **12/28 CI successful, 16/28 failed**. Failure propagation came from one Android unit regression: `FieldOpsOperatingActivityTest.inFlightDecoderCompletionAfterRotationRefreshesCurrentActivityOnly` at line 319 (`assertNotSame` on the old/new callback). Android app Kotlin compilation and all four source-built FT8/FT4/FT2 native test jobs passed; Android `testDebugUnitTest` reported 78 tests run / 1 failed. The old test called Robolectric `configurationChange()` with no effective resource change, which did not ensure a different Activity object.
- Corrected test to use Robolectric `ActivityController.recreate()`, whose documented contract creates a new Activity and preserves nonconfiguration state. The test now explicitly checks the old/new Activity objects differ before asserting current observer replacement. This is a test harness correction, not a claim that a production callback bug was proven fixed. New exact-head CI must pass before green classification. See the failing Android job logs for `40455145546`, `40455145533` and `40455145476`.

## Explicit Android ADIF file export for manually confirmed FT contacts — pending exact-head CI

- LOG now exposes `EXPORT ADIF FILE (CHOOSE DESTINATION)` only when canonical QSO entries exist. It launches Android `ACTION_CREATE_DOCUMENT` with a user-approved .adi name and `CATEGORY_OPENABLE`. There is no account upload, hidden storage or permissions increase.
- The app snapshots the authoritative in-memory QSO records before the picker, retains that snapshot through Activity recreation, writes UTF-8 ADIF to the user-approved `ContentResolver` output stream off the UI thread and reports success/failure/cancel explicitly. No QSO is created by decoding or exporting, and the in-memory log and LoTW queue are unchanged. It remains **RAM-only** until the user performs this export; there is no import or automatic persistence yet.
- `ManualAdifFileExportTest` verifies true FT4 mode, call, frequency, sent report and station identity bytes produced by the canonical ADIF codec, plus failure/empty log safety. Robolectric tests verify FT4 manual log → explicit Storage Access Framework destination and disabled PTT. These tests do not establish real S23 storage provider interoperability.
- New exact-head CI is necessary before accepting this change; the preceding `29f138bfbddad96a099314febb47bb7f6cc52039` rotation regression is separately under verification.

## Conservative explicit ADIF restore — new exact-head CI required

- Restoring a FieldOps-generated .adi now uses Android ACTION_OPEN_DOCUMENT, at most 1 MB/500 ASCII records and a **two-step** workflow: asynchronous read/validation *without mutation*, followed by an operator's separate CONFIRM RESTORE action. Existing canonical QSO model and ID source are reused; repeated identical records skip rather than duplicate.
- `FieldOpsAdifRestore` allows only `PROGRAMID=FTX1_FieldOps`, the currently selected station callsign, six-digit UTC, supported manual FT8/FT4/FT2/CW/SSB mode identities and matching amateur band/frequency; rejects unknown data fields, non-ASCII encodings and incomplete/out-of-range records. Parsed records are tagged `IMPORTED`; LoTW always resets to NOT_UPLOADED/UNKNOWN. No hidden network, LoTW upload, RF or automatic decode-to-QSO path.
- Android import remains **volatile RAM only**; users need deliberate export to preserve data across a process restart. Read-only validation and confirmation have JVM tests and Robolectric explicit document picker/locked transmitter tests. Exact-head CI is pending; data compatibility with third-party ADIF or on-device SAF is NOT claimed.

- Additional fail-closed validation: malformed optional numeric fields are now rejected rather than silently discarded; injected LoTW confirmation fields fail validation. Robolectric confirms selected ADIF stays staged with **zero log effects** before confirmation, duplicate restoration inserts zero additional contacts, cancellation changes nothing, ADIF export reflects the accepted FT4 identity, and PTT remains locked. These additions require their own exact-head CI.
