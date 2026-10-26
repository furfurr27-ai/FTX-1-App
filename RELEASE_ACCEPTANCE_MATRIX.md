# FTX-1 FieldOps — finite P0–P3 release acceptance matrix

**Scope owner:** Chris / N0PNG. **Target:** Yaesu FTX-1/FTX-1F and Samsung Galaxy S23 Ultra only.

**Audit basis:** GitHub CP-0009Q durable main `6c660d087fcdea4ae149b6dfe1b5b956d6edbf97` / `v68-recovery-consent-failure-ux` after PR #68; merged-main [finalizer 39722428140](https://github.com/furfurr27-ai/FTX-1-App/actions/runs/39722428140) 46/46. Independently rehashed 1,045,640 snapshot bytes: `06a3d160b082ad3cea19dce2caf7af84c73630e4b362bec6614474dbd1fd463f`, matching manifest and `LATEST.json`. Source-tree classification was based on the PR #68 merge (`705243e489974f2e6627909832781d3d5eafb010`); finalizer changed checkpoint documents only.

**Purpose:** fixed original P0–P3 requirements and a finite release gate. A checked host test of a library is NOT proof that the installed APK exposes or uses it. A successful Android APK build is NOT proof of radio operation. Unknown evidence is marked unknown, not pass.

**Acceptance codes:** `HOST_PARTIAL` = component exists with some host/CI evidence but is not integrated in production Android; `APP_PARTIAL` = observable Android functionality but incomplete scope/synthetic inputs; `MISSING` = no suitable production implementation located; `HARDWARE_BLOCKED` = software work must still be done as feasible, but final real-device proof needs hardware/account; `ACCEPTED_SOFTWARE` = complete software behavior AND integrated tests with durable checkpoint evidence. No original P0–P3 row is currently established as `ACCEPTED_SOFTWARE` for a finished FieldOps operating environment.

## CP-0009R integrated evidence

CP-0009R / v69 finalizer 39870937975 integrates
`app/src/main/kotlin/dev/n0png/fieldops/app/FieldOpsOperatingActivity.kt`
into the launcher. Robolectric tests
`FieldOpsOperatingActivityTest.kt` verify real user-entered
CW/SSB QSO creation through `FastQsoLogger`, ADIF export preview,
shared Awards Center projection, mode profiles, navigation, fresh
session loss versus rotation, and locked transmitter controls.
This changes the following acceptance dispositions **only**:

- Android home/navigation: PARTIALLY INTEGRATED — OPS/DIGI/APRS/MAP/
  LOG/AWARDS/RADIO navigable; actual live operating engines absent.
- CW/SSB logbook: PARTIALLY INTEGRATED — manual QSO/ADIF works
  in RAM; device/radio and durable DB remain unfinished.
- Awards: PARTIALLY INTEGRATED — the actual projection consumes the
  shared RAM logbook; imported confirmation/persistence gaps remain.
- Digital mode UI: PARTIALLY INTEGRATED — mode timing metadata only,
  with RX/TX disabled and no linked providers.
- Map: PARTIALLY INTEGRATED — awardMapLayers() uses real service;
  no interactive geospatial renderer or live source.

No previously marked unmet P0–P3 software requirement is
considered fully accepted by this checkpoint.

## Primary integration evidence

1. `app/src/main/java/dev/n0png/fieldops/app/MainActivity.java`: the launcher self-identifies as a packaging-only developer shell. It opens `ArchiveHistoryActivity` and explicitly states no operating FT8/FT4/JS8/WSPR/CW/SSB/LoTW workflows.
2. `app/src/main/AndroidManifest.xml`: launcher and archive Activity are the only registered Activities; no radio service or declared USB/audio/network integration.
3. `app/build.gradle.kts`: the application compiles `core/src/main/kotlin` as a source directory, but does not register `android/pipeline/src/main/kotlin` or the existing native bridge source as production app inputs. It produces versionCode 68 debug APK, not an operational multimode app.
4. Existing host software evidence spans `core/src/main/kotlin`, `android/pipeline/src/main/kotlin`, `native/wspr`, `scripts/test_*.sh`, and inherited green CI. See CP-0009P manifest and CP-0009Q PR push+PR matrices. Do not interpret the matrix as claiming every native bridge is linked or working on a device.
5. PR #68 exact head `7f9ece57a15423d7e1f2274e8add67bdd505f628`: [push 46/46](https://github.com/furfurr27-ai/FTX-1-App/actions/runs/39713091694), [PR 46/46](https://github.com/furfurr27-ai/FTX-1-App/actions/runs/39713096193), [APK](https://github.com/furfurr27-ai/FTX-1-App/actions/runs/39713096052). These verify existing archive/core regressions, not end-to-end multimode operation.
6. `research/ARCHITECTURE.md`, `research/MULTIMODE_PARITY.md`, and `research/INTEGRATION_STATUS.md` are architecture/earlier source maps, NOT current acceptance certificates.

## CP-0009R / CP-0009S audit update

- CP-0009R `v69` is durable: [merged-main CI finalizer 39870937975](https://github.com/furfurr27-ai/FTX-1-App/actions/runs/39870937975) 46/46, [PR #70](https://github.com/furfurr27-ai/FTX-1-App/pull/70). It replaced the prior archive-only Android launcher. Earlier audit baseline above is retained as historical source context.
- CP-0009S draft also adds a DIGI → LOG operator-confirmed **manual** FT8/FT4/FT2 QSO handoff, using existing canonical FastQsoLogger, ADIF and Awards state. It creates NO contact from WAV decoding, is in-memory only, and is not proof of working FT QSO sequencing.
- CP-0009S [draft PR #71](https://github.com/furfurr27-ai/FTX-1-App/pull/71) introduces **source-built ARM64** FT8/FT4/FT2 JNI, recorded-FT8 truth gate, real Android file-only native decode path and corrected fast-mode JNI protocol routing. Rows below describe directly inspected branch code, with **CURRENT-HEAD CI PENDING** until explicitly verified. These features are neither merged nor checkpointed.
- There is still no validated live FTX-1 USB audio/CAT/PTT workflow, production FT4/FT2 recording/golden reference, automatic FT-family QSO lifecycle, or final software release. Source-built APK and mock unit tests alone do NOT close these gaps. CP-0003C remains DEFERRED; CP-0004A/B/C remain incomplete.

## P0–P3 requirements

| Phase | Required capability / user workflow | Located implementation | Checked evidence | Production app integration | Remaining software gap (CI-closable?) | Physical/account validation | Final acceptance |
|---|---|---|---|---|---|---|---|
| P0 | Safe FTX-1 CAT command transport, PTT arbiter, watchdog, TX abort/recovery | `core/cat/Ftx1CatTransport.kt`; `core/radio/Ftx1RadioSession.kt`, `RadioModeArbiter.kt` | Core host regression in inherited CI; inspect specific TX vector before acceptance | Not wired to launcher/radio device | Integrate serialized Android USB port, lifecycle, UI interlocks and simulated disconnect/duplicate-owner tests: **YES** | CP-0004A/C: physical USB topology, CAT and TX into dummy load | HOST_PARTIAL |
| P0 | Shared capture, playback, DSP, timing and session management | `core/audio`, `core/dsp`, `core/time`; `android/pipeline/.../audio`, `dsp` | Host audio/slot regressions from prior native-mode work; not checked as app-native E2E | Android pipeline absent from `app/build.gradle.kts` | Link/build tested adapters; USB route error handling; simulated audio-to-decode and TX-to-safe-RX: **YES** | CP-0004B/C: real USB audio rates/route/clock, output power | HOST_PARTIAL |
| P0 | FT8 operating screen and tested RX/TX workflow | `app/src/main/cpp/third_party/ft8af`, `app/.../FtFamilyOfflineWav.kt`, `android/pipeline/.../FtFamilyNativeEngine.kt` | Pinned FT8 source/ARM64 JNI APK and native encoder golden tests **green at prior head**; recorded 20m WAV truth test introduced, current exact-head CI pending | Android DIGI now offers a bounded, user-selected offline 12 kHz PCM WAV decode with actual native adapter; no live USB/audio, waterfall or automated QSO state | Validate real WAV/Android JNI on device; FT8 structured QSO RX/TX workflow, waterfall, scheduler, log coupling, safety interlocks: **YES** | CP-0004A/B/C: physical capture and RF into dummy load | APP_PARTIAL |
| P0 | FT4 separate slot timing and operating workflow | `app/.../FtFamilyReceiveSession.kt`, `FtFamilyOfflineWav.kt`, `FtFamilyNativeEngine.kt` | Shared 48k to 12k slot assembly/fixture tests; corrected native ABI FT4 protocol 0 -> `InitDecoderFt2`; current CI pending; NO genuine recorded FT4 decode vector | Android FT4 mode selector and file-only, exactly-7500 ms 12 kHz WAV decoder hook; TX and live RX disabled | Real FT4 WAV/golden interoperability, integrated decoder lifecycle, QSO sequencing, deadline/slot-safe PTT: **YES** | CP-0004A/B/C: physical radio/audio field confirmation | APP_PARTIAL |
| P0 | FT2 very-fast workflow, not FT8 timing reused | `app/.../FtFamilyReceiveSession.kt`, `FtFamilyOfflineWav.kt`, `FtFamilyNativeEngine.kt` | Shared slot assembly/fixture tests, native FT2 protocol 2 uses fast decoder ABI; current CI pending; NO genuine recorded FT2 decode vector | Android FT2 selector and file-only, exactly-3750 ms 12 kHz WAV decode hook; no live USB/RF | Real FT2 WAV/golden interoperability, very-fast scheduling, QSO sequencing, recovery and TX safety: **YES** | CP-0004A/B/C: device audio/clock/RF proof | APP_PARTIAL |
| P0 | JS8 chat: RX/TX, directed/multipart/history, safe queue | `android/pipeline/.../dsp/Js8EngineAdapter.kt`, `Js8CallAndroidEngineFactory.kt`, `Js8TxController.kt`; native checkpoint research | Prior JS8 host/native scripts; actual linked Android runtime not checked | No JS8 workspace; separate bridge not packaged | Complete licensed provider, chat UI/persistence, golden vectors, abortable queued TX: **YES** | Hardware RX/TX and timing | HOST_PARTIAL |
| P0 | WSPR spot RX and opt-in beacon scheduler | `android/pipeline/.../dsp/Wspr*.kt`, `native/wspr` | Native WSPR source and host tests; full app NDK packaging not checked | No WSPR workspace; native bridge not packaged | Link JNI and arm64 assets; golden RX/TX; beacon UI/queue/log/map; safe disarm: **YES** | Physical WSPR reception and power/timing | HOST_PARTIAL |
| P0 | APRS, keeping radio-internal controls distinct from phone-generated AFSK | `core/aprs` AX.25/KISS/AFSK/SmartBeaconing; `core/cat/Ftx1Cat.kt` | Core host packet tests, not real RF or production UI | No station/message/map screen or audio source | Separate explicit radio-vs-phone interfaces, supported CAT capabilities only, AFSK E2E vectors, safe PTT: **YES** | Internal modem CAT semantics and AFSK off-air proof: CP-0004A/B/C | HOST_PARTIAL |
| P0 | Universal logbook, ADIF, offline-safe LoTW queue | `core/logbook`; `android/pipeline/.../logbook` | CP-0005A/B, CP-0003B host tests | No integrated LOG screen/database/jobs | QSO persistence, import/export UI, exactly-once queue semantics, WorkManager, offline tests: **YES** | CP-0003C account/TrustedQSL remains **DEFERRED** by owner | HOST_PARTIAL |
| P1 | CW and SSB workspaces and manual QSO entry | Generic logbook core; no CW/SSB production workspace located | No end-to-end UI tests found | Absent | Build mode tools, cheat sheets, explicit RX-only by default, manual log UX and tests: **YES** | Optional live radio tuning/RF validation | MISSING |
| P1 | Propagation live/offline, comparisons, history, reports | `core/propagation`; `app/.../ArchiveHistoryActivity.kt` | CP-0008A–Z, CP-0009A–Q host/archive/Android Robolectric/CI | Archive Activity exists, but launcher labels data synthetic; no unified live propagation workspace | Connect opt-in source refresh, labeled offline states, filters/comparisons/maps and simulated offline E2E: **YES** | Real-world feed/device network checks | APP_PARTIAL |
| P1 | Awards Center with evidence and supported awards | `core/awards`; `core/map/AwardAreaMapProjection.kt` | CP-0006A–G, CP-0007A–C host CI | No Awards screen | Connect from logbook to score/progress/evidence, meaningful official award coverage, lifecycle tests: **YES** | Account-specific award reconciliation where needed | HOST_PARTIAL |
| P1/P3 | Map layers: QSO, heard, propagation, APRS, award regions | Geometry/projections in `core/map`, reporting adapters in `core/propagation` | Host projection/geodata pack checks | No integrated map UI/layer toggles | Android map renderer, labeling, datasets, saved filters/offline policy and tests: **YES** | GPS/data/field checks on real device | HOST_PARTIAL |
| P2 | RTTY, PSK31/63, Olivia, MFSK | No production engine/provider found in scanned `core/src/main`, `android/pipeline/src/main`, `native` | No mode-specific CI found; requires actual code/vector inspection when added | Absent | Implement licensed/testable RX/TX providers, workflows and golden vectors: **YES** | Final RF/audio checks | MISSING |
| P3 | Contestia, DominoEX, THOR, Hellschreiber, MT63 | No production engine/provider found in same inspected source tree | No mode-specific CI found | Absent | Implement defined mode providers, metadata, UI and tests without inventing interoperability: **YES** | Final RF/audio checks | MISSING |
| P3 | Cohesive home/OPS/DIGI/APRS/MAP/LOG/AWARDS/RADIO navigation and persistent shared session | `app/.../FieldOpsOperatingActivity.kt`, core logbook/awards services; `RELEASE_ACCEPTANCE_MATRIX.md` | CP-0009R merged-main finalizer 39870937975 46/46, durable `v69`, Android Robolectric navigation and real in-memory manual QSOs | OPS/DIGI/APRS/MAP/LOG/AWARDS/RADIO with shared in-memory logbook and honest offline archive; **NOT** a process-persistent operational radio session | Persist QSOs securely, integrated modal RX/monitor/QSO state, connectivity/lifecycle/accessibility and signed final APK: **YES** | Final S23 UI/USB ergonomics | APP_PARTIAL |
| P3 | Release packaging, privacy, licenses, reliable install and source-complete build | `app/build.gradle.kts`, APK CI; `research/LICENSE_PROVENANCE.md`; checkpoint scripts | CP-0009Q debug APK and inherited regression matrix; prior snapshots | Debug shell installable, not completed product | Bundle all required code/native assets, reproducible artifact hash, license notices/source compliance, static privacy audit, regression E2E, install instructions: **YES** | Final device install, USB/Keystore/LoTW validations | APP_PARTIAL |

## Non-negotiable safety and deferrals

- App launch, mode switch, reconnect and background transition must never key TX. Only explicit authorized TX owns the exclusive radio arbiter; watchdog returns to RX on timeout/failure. Preserve TX0/emergencyRx policy; tests must check failures.
- Synthetic audio/Robolectric/software keys cannot be relabeled as verified FTX-1 CAT, RF, USB audio, real Android Keystore, secure storage under power loss, station authenticity, or trusted archive freshness.
- Older authenticated encrypted ciphertext can replay; `FRESHNESS NOT VERIFIED`. No automatic fallback, key retirement or stale-choice confirmation.
- CP-0003C is **DEFERRED** and requires literal owner directive `resume CP-0003C`; CP-0004A/B/C remain incomplete. Do not run real LoTW upload or RF validation as part of CI.
- Review provenance and license obligations before distributing an APK; avoid unaudited qFT8 code and obey upstream GPL/LGPL/MIT source/notice terms.
- LQ8/LQ4, SSTV and unspecified modes remain outside finite P0–P3 release scope unless expressly approved.

## Finite execution order and release stop condition

1. **CP-0009Q complete**: PR #68 exact-head 46/46 push and PR matrices, merged-main finalizer 46/46 and independently verified snapshot SHA-256 all PASS. Preserve this immutable baseline.
2. **First post-CP-0009Q software acceptance task (CP-0009R integration target):** replace the archive-only launcher with a coherent, initially non-transmitting FieldOps navigation/session shell that integrates **actual existing** core services, exposes truthful status and safe disabled hardware-dependent actions, and has instrumented/Robolectric navigation + state tests. Make the next checkpoint close this measurable P3 gap, **not** another unsolicited archive micro-checkpoint.
3. Integrate tested FT family/JS8/WSPR/APRS and radio/audio interfaces with real mode screens and simulated source data clearly identified, maintaining no RF transmit until device safety sign-off. Convert modes, logger, awards and propagation to coherent workflows with explicit acceptance vectors.
4. Implement missing CW/SSB and the specified P2/P3 modes; maintain realistic licensing and interoperability evidence. Complete offline, lifecycle, privacy and navigation tests. Group development into acceptance-oriented checkpoints, not one per helper class.
5. Produce final **software release candidate** only when every CI-closable gap above is satisfied, an installable integrated APK has a verified SHA-256, and source/CI/evidence/release instructions are durable.
6. **STOP** creating software checkpoints at successful software RC. Report only bounded hardware/account proofs still outstanding. Field-operational release requires real Galaxy S23 Ultra + FTX-1 validation and authorized LoTW/account work.

**Change rule:** after software RC, new work only for a verified requirement defect, transmitter/safety/security issue, actual device integration failure, or explicit owner change. The matrix may be updated to reflect new evidence, but the product scope is fixed.

## Acceptance-record maintenance rules

Every subsequent acceptance-focused PR should update affected rows with: precise source path(s), exact test run/link and vector, Android UI integration result, remaining gap, hardware/account proof, and software status. Avoid numerical percent-complete guesses: completed components do not measure weighted operational completeness. Never mark `ACCEPTED_SOFTWARE` based only on a contract, a compiled file or a simulated screenshot.

This is a **source-inspection acceptance audit**. It does not modify CP-0009Q's immutable checkpoint, claim device testing, or constitute a new completed engineering checkpoint. Its classifications must be revised only from direct production-source and test evidence.
