# FTX-1 FieldOps — GitHub Amateur-Radio Reference Survey

Owner: N0PNG / Chris
Survey checkpoint: 2026-10-04
Purpose: use proven amateur-radio designs as references while keeping FieldOps a focused, private Android application for one Yaesu FTX-1.

## Scope and evidence standard

This is a broad GitHub survey, not a claim that every amateur-radio repository on GitHub was exhaustively reviewed. Searches included the GitHub topics `ham-radio`, `amateur-radio`, and `hamradio`, plus targeted searches for FT8/FT4/JS8/WSPR, APRS/AX.25, digital modems, CAT/rig control, logging, LoTW, awards, CW, SSTV, mapping, propagation, satellites, Winlink and Android.

More than 100 public repositories were surfaced in the topic scan. A smaller reference set was pinned to exact commits and inspected at README and/or source-code level. Repositories are used as:

- **Pattern reference** — architecture or UX ideas only.
- **Protocol reference** — protocol behavior/data model; implementation remains clean-room where appropriate.
- **Potential reusable implementation** — only after license compatibility is explicitly reviewed.

Do not copy code merely because it is on GitHub. Preserve provenance and license obligations per component.

## Core design conclusion: optimize for the FTX-1, not every radio

Hamlib is a strong example of a stable front-end API over many radio backends. That abstraction is useful as a design lesson, but FieldOps does not need the backend explosion because the target is one radio.

FieldOps should therefore use:

`Ftx1RadioDriver -> Ftx1CatController + Ftx1UsbAudioEngine + Ftx1TxCoordinator`

rather than:

`GenericRig -> manufacturer backend -> model backend -> FTX-1`

The application may borrow ideas from Hamlib, flrig and wfview—state caches, staged startup queries, serialized commands, explicit PTT state, capability metadata, timeout/recovery rules—but the production runtime should be FTX-1-specific.

Extensibility belongs above the radio layer:

- `DigitalModeProvider`
- `AwardEvaluator`
- `MapLayerProvider`
- `LogSyncProvider`
- `SpotSource`
- `PropagationProvider`
- `OperatingWorkspace`

This keeps one reliable device-control path while allowing the application to grow.

---

# Tier 1 — direct architectural references

## patrickrb/FT8AF
Pinned head: `c2f63e8b37fcd484fd2eb2049494425dd2414971` (2026-07-08)

Best use:
- modern Android weak-signal UX
- native DSP/JNI integration
- USB audio and CAT patterns
- signal-follow workflow
- mobile waterfall interaction

FieldOps decision:
- Continue using FT8AF as a primary Android behavior/implementation reference.
- Keep FieldOps shared audio/CAT ownership rather than creating a separate stack per weak-signal mode.

Limitations:
- FieldOps requirements are broader than FT8AF: SSB/CW, APRS, awards, propagation, map layers and many keyboard modes.

## JS8Call-improved/Android-port
Pinned head: `9996202f355569c5ee7b97fae539f3b763081dc2` (2026-09-30)

Best use:
- continuous timestamped JS8 PCM ingestion
- asynchronous decode callbacks
- Android JNI architecture
- TX audio tap that can feed FieldOps-owned PTT/audio
- message-oriented rather than QSO-sequence-oriented UX

FieldOps decision:
- JS8 is a continuous stream consumer, not an FT8-style isolated slot decoder.
- FieldOps owns physical PTT and USB playback even when JS8 generates TX audio.

## kgoba/ft8_lib
Pinned head: `9fec6ca39886edbf96f4f5e71edc76da5074e871` (2025-08-24)

Best use:
- compact portable FT8/FT4 encode/decode reference
- protocol test vectors and clean codec boundary

FieldOps decision:
- keep FT-family DSP behind a narrow native engine API.

## Guenael/rtlsdr-wsprd
Pinned head: `1ca9b83dd2562ce9ef2453aacdd5bc3aab982c7d` (2026-03-06)

Best use:
- pure-C WSPR decoder and 162-symbol generator
- decoder internals and test vectors

FieldOps decision:
- retain FieldOps-owned 12 kHz real-audio to 375 Hz complex-baseband frontend and FieldOps-owned TX waveform generation around the upstream WSPR codec.

## w1hkj/fldigi
Pinned GitHub head: `61b97f4133c488063f3de1795c894d22d5032e8a` (2022-06-23)

Direct source finding:
- `src/include/modem.h` defines a common `modem` base class.
- individual implementations such as CW and MT63 derive from the modem boundary.
- `mode_info_t` associates mode identity and modem instance metadata.
- spot/PSK Reporter paths consume mode/frequency metadata independently of a specific modem.

Best use:
- proven architecture for many independent digital modes
- common modem lifecycle and metadata
- RSID/mode identification concepts
- RTTY, PSK, Olivia, MFSK, Contestia, DominoEX, THOR, Hell, MT63 references

FieldOps decision:
- adopt the *concept* of a stable modem contract.
- do not transplant fldigi's desktop GUI/audio architecture.
- `DigitalModeProvider` should expose RX, TX, sample-rate/timing needs, bandwidth, AFC, waterfall metadata and ADIF identity.

## f4exb/sdrangel
Pinned head: `95c0622a69808aa5625a525cbfbdd7adf9a59b04` (2026-10-03)

Direct source finding:
- explicit `PluginInterface` and `ChannelAPI` boundaries.
- demodulators/modulators are separate channel plugins.
- `BasebandSampleSink` / source abstractions separate DSP flow from UI/plugin registration.

Best use:
- mature plugin/module boundary
- DSP source/sink separation
- independent RX/TX channel modules

FieldOps decision:
- borrow the clean boundary, not SDRangel's generic SDR complexity.
- FieldOps has one audio source and one FTX-1; modem providers should remain lightweight.

## jketterl/openwebrx
Pinned head: `640c5b0b3e19b10d823a7ee703f2a683ec848eac` (2024-12-11)

README directly confirms one application coordinating multiple decoder families: AM/FM/SSB/CW/BPSK, WSJT-X modes, Dire Wolf APRS, JS8Call, FreeDV, M17 and digiham.

Best use:
- composition/orchestration model: one front end, multiple specialized decoder engines
- waterfall + filter + mode interaction
- avoid rewriting every protocol when a clean, licensed engine already exists

FieldOps decision:
- one UX and one radio service can orchestrate different native engines.
- engines may be embedded or bridged depending on license/build suitability, but they expose a common FieldOps provider contract.

## DJ2LS/FreeDATA
Pinned head: `2b3f037db7aefa5c693acbbf51548fd43cd0e1c3` (2026-07-27)

Direct source findings:
- `modem.py` isolates RF/modem behavior.
- explicit service manager, state manager and event/queue architecture.
- TX commands have a common command base.
- modem/audio queues are distinct from GUI state.

Best use:
- conversational/ARQ state management
- queue-based separation of modem, radio, GUI and message sessions
- future free-text/network digital modes

FieldOps decision:
- conversational modes should have session/message state independent of the waterfall decoder.

---

# Tier 2 — packet/APRS and mapping references

## wb2osz/direwolf
Pinned head: `eda1383f5fa9d8ba3cb27f99db1d2c79494404c9` (2026-05-19)

README directly confirms separation of:
- sound-card DSP modem
- AX.25/FX.25/IL2P link functions
- KISS/AGW network interfaces
- APRS tracker/digipeater/IGate functions
- multiple modem families and radio interfaces

Best use:
- keep AFSK demodulation, AX.25 framing, APRS interpretation and application UI as separate layers.
- KISS remains an interface, not the internal data model.

FieldOps decision:
- preserve current `PCM -> Bell202 -> HDLC/AX.25 -> APRS` separation.
- future packet modes can reuse link-layer objects without contaminating map/database models.

## Xastir/Xastir
Pinned head: `68647b36b8027b67254180f59815915bf0db3e22` (2026-09-24)

Direct source search found map objects with explicit layers, zoom thresholds and GIS/map-source separation.

Best use:
- APRS map layering
- long-lived station/trail objects
- source-independent position data

FieldOps decision:
- map entities must be normalized first; rendering layers should not know whether position came from APRS, grid lookup, log history, WSPR or award geometry.

## rt-bishop/Look4Sat
Pinned head: `782b438a072e26ee5be8629de508530092586136` (2026-09-27)

README directly confirms:
- Kotlin + Coroutines + Jetpack Compose + Navigation
- offline-first calculations
- map ground tracks/footprints
- periodic external-data refresh rather than always-online operation

Best use:
- current Android application structure
- offline-first reference-data model
- map rendering with calculated overlays

FieldOps decision:
- propagation/awards/reference data should degrade gracefully offline.
- cached data needs age/source metadata visible to the operator.

---

# Tier 3 — radio/CAT state references

## Hamlib/Hamlib
Pinned head: `7a556dbfc80649ddbceedf5b8f8627b990671594` (2026-10-03)

README/source findings:
- stable generic API over radio backends
- explicit `rig_open`, `rig_close`, set/get operations
- state/cache structures include communications status
- current master includes streaming APIs and metadata/cache concepts

Best use:
- capability/state cache patterns
- transport lifecycle
- error/status taxonomy
- careful separation of front-end requests from backend serial protocol

FieldOps decision:
- implement those ideas directly in `Ftx1RadioDriver`; do not support arbitrary Hamlib models.

## w1hkj/flrig
Pinned GitHub head: `915d98c5781fc8a39b37699a23d63bea4a235451` (2023-05-15)

Direct source finding:
- configurable polling interval and explicit radio-control UI behavior.

Best use:
- polling/caching lessons and operator-visible rig controls.

## wf-group/wfview
Pinned head: `098568546731aff25c7e9ea93c0f009eeee19137` (2026-09-27)

Direct source findings:
- separate delays for serial and LAN command queues.
- slower staged startup polling before normal command cadence.
- explicit frequency/mode/PTT query and UI state handling.

Best use:
- staged radio startup handshake
- command queue pacing
- cache UI state from confirmed radio replies rather than optimistic local assumptions

FieldOps decision:
- on USB attach/reconnect: identify radio, force RX-safe state, query required state in stages, then mark session READY.
- never enable TX merely because UI mode changed.

---

# Tier 4 — logging, LoTW, awards and activity references

## foldynl/QLog
Pinned head: `cbb75f2f2555a4a84c9761e38075754f4821c27c` (2026-09-18)

README directly confirms:
- SQLite backend
- Hamlib/flrig/TCI rig integration
- LoTW/eQSL/QRZ/Clublog/etc.
- online and offline maps
- station location profiles
- CW console/keyer integration
- basic awards
- secure OS credential storage
- TQSL as LoTW dependency

Best use:
- clear separation between local log, station profile, online services and credential storage.

FieldOps decision:
- use Android Keystore-backed secrets.
- station profile is a first-class foreign key on each QSO and LoTW job.
- network service state must never overwrite local log truth without reconciliation.

## wavelog/wavelog
Pinned head: `3af1ba557a54da9ba318daf9d4a5ed8e937db1b8` (2026-09-23)

Direct source searches found:
- dedicated LoTW controllers/models and certificate metadata.
- large award catalog.
- explicit worked vs confirmed states.
- mode/band award filtering.
- award-specific information/rule links.
- DXCC/WAS/VUCC/FFMA/WPX/WAC/POTA/SOTA/WWFF and many regional award views.
- Leaflet layer controls, Maidenhead overlays and marker clustering.
- award map status and QSO map layers.

Best use:
- strongest surveyed reference for FieldOps Awards Center and award-map semantics.

FieldOps decision:
- award state is at least `{needed, worked, confirmed, claimable/credited}`.
- confirmation source is explicit.
- award rules belong in evaluators/catalog definitions, not UI components.
- dense map overlays use aggregation/cluster techniques.

Important caution:
- FieldOps must independently verify official award rules before encoding them. Wavelog is a reference, not the legal/rules authority.

## ea4k/klog
Pinned head: `6819f98a5e8542e3dc657f2048cf3d305c1c7d10` (2026-09-10)

Direct source findings:
- LoTW upload queue and TQSL invocation.
- separate LoTW sent/received dates and states in ADIF/database.
- DXCC/WAZ/IOTA award support.
- award logic has explicit mode handling.
- a recent code comment correctly notes that USB/LSB/SSB should be grouped for some award purposes while unrelated digital submodes must not be collapsed simply because they share an ADIF parent family.

Best use:
- mode-normalization and award-credit semantics.
- queued network/QSL actions.

FieldOps decision:
- maintain both exact mode/submode and a controlled award-mode grouping table.
- never rely solely on ADIF parent-mode grouping for award evaluation.

## magicbug/Cloudlog
Pinned head: `c1e1834ff151f917078e5d033e2e7358ddfb761b` (2026-09-10)

Best use:
- mature logbook service/database concepts and integrations.
- historical ancestor of Wavelog.

FieldOps decision:
- useful as cross-check, but Wavelog is the more feature-rich active award/map reference.

## mbridak/not1mm
Pinned head: `72fbc5e270af1956f10f8a83c676c46449ad7ced` (2026-10-02)

Best use:
- high-speed operator workflow
- contest-style focus handling, exchanges, macros and keyboard efficiency

FieldOps decision:
- SSB/CW/manual logger should be operable with minimal taps while on the radio.
- contest-specific complexity stays modular.

## ham2k/polo.ham2k.com
Pinned docs head: `7f99987f64c7936853d7e4cc9194dc4717481fa3` (2026-04-27)

Direct documentation findings:
- operations are first-class containers.
- activities such as POTA/SOTA/etc. attach to an operation.
- operation-level map and location context.
- service-specific minimal exports vs full private ADIF export.
- self-spotting and activity integrations are modular.

Best use:
- portable-operation/activity model.

FieldOps decision:
- add `OperatingSession` above individual QSOs. A session can hold station profile, location, radio/antenna notes, activation programs, start/end time and map/log filter scope.
- preserve data-minimization in exports/uploads.

---

# Tier 5 — propagation, messaging, CW and future-mode references

## skyelaird/dvoacap-python
Pinned head: `96f75a62c8db492f2916ad127cd6f3021941e967` (2026-05-01)

README directly describes:
- VOACAP-derived HF propagation engine
- ionospheric calculations
- interactive propagation map
- band-condition meters
- DXCC tracking
- space-weather inputs

Best use:
- propagation-engine ideas and test/reference data.

FieldOps decision:
- propagation map should fuse prediction with observations, not present prediction as ground truth.
- every propagation product needs timestamp, source and confidence/explanation.

Limitation:
- model validity and numerical equivalence must be independently validated before embedded operational use.

## la5nta/pat
Pinned head: `2e6a8d14baf0268f4e2aa4d01784a54ca935cf52` (2026-04-19)

README directly confirms:
- mailbox/message model
- responsive mobile UI
- Hamlib radio control
- scheduled actions
- concurrent listening on several link modes
- Android-known deployment

Best use:
- future messaging/inbox UX and transport-independent message layer.

## DJ2LS/FreeDATA
Also useful here for ARQ/conversational session state and service queues.

## k3ng/k3ng_cw_keyer
Pinned head: `89081784fdb2c3423ecf84fe054d5a86c3c7ee40` (2026-06-27)

README confirms a mature configurable CW keyer project.

Best use:
- CW macros/keyer behavior/reference terms.
- do not assume direct electrical/keying compatibility with the FTX-1 until the exact supported CAT/keying path is proven.

## drowe67/freedv-gui
Pinned head: `03c876fe167b9702d3cc451507bcb55215106cf3` (2026-10-03)

Best use:
- long-running streaming audio mode architecture
- CPU/performance awareness for real-time mobile DSP
- future digital voice possibilities

## ON4QZ/QSSTV
Pinned head: `8c27d6d169d8c6c197eb47c2089870e39bc06a02` (2026-03-14)

Best use:
- future SSTV/image mode concepts.

## N0BOY/FT8CN
Pinned head: `a98fbfaec379a0c7a07d2a50c27be223aef948d8` (2025-01-06)

README directly confirms native Android FT8 and notes deliberate lighter-weight processing for mobile performance/battery constraints; project history also references radio-control testing and automatic Cloudlog/QRZ upload contributions.

Best use:
- additional Android FT8/mobile-performance reference.

---

# FieldOps architecture changes derived from the survey

## 1. One radio service owns all hardware

`Ftx1DeviceService` is the sole owner of:
- USB permission/lifecycle
- CAT serial transport
- USB RX audio
- USB TX audio
- PTT
- radio state cache
- watchdog and emergency RX

No modem, logger, map, award evaluator or UI component opens the radio independently.

## 2. Explicit operating-session model

Add:

```text
OperatingSession
  id
  startedUtc / endedUtc
  stationProfileId
  location/grid/lat/lon
  radio = FTX-1
  antenna/profile notes
  activation/activity tags (POTA/SOTA/etc.)
  default power
  session notes
```

Every QSO can reference the active session. This supports field use, portable awards, map filtering and correct LoTW station-location signing.

## 3. DigitalModeProvider contract modeled after fldigi/SDRangel lessons

Minimum provider surface:

```text
identity
ADIF mode/submode
streaming vs slotted timing model
required input sample rate
RX lifecycle
TX lifecycle
waterfall/bandwidth/AFC metadata
conversation capability
quick-message capability
logging metadata
recommended FTX-1 radio mode/filter preset
TX safety/timing requirements
```

Providers cannot own PTT.

## 4. MapLayerProvider contract

Each layer exposes:
- identity/name/category
- data source and age
- supported band/mode/time filters
- geo objects or raster/vector tiles
- legend
- opacity range
- offline/cached behavior
- attribution/license metadata

Initial layers:
- heard signals
- QSO history
- APRS stations/trails
- propagation observed paths
- propagation prediction
- greyline
- award progress
- Maidenhead grid
- DXCC/CQ/ITU boundaries where data licensing permits

## 5. Award engine separate from UI

Model:

```text
AwardDefinition
AwardTrack
AwardEvaluator
AwardProgressSnapshot
AwardEvidence
ClaimInstructions
```

Keep exact mode plus award-mode group. Keep worked, confirmed, credited/claimable distinct.

## 6. Local-first log + queued sync providers

`LogbookRepository` remains authoritative local storage.

External providers—LoTW, QRZ, Clublog, eQSL, POTA/SOTA upload, etc.—consume immutable QSO IDs and maintain independent sync records:

```text
QsoSyncState(qsoId, provider, state, attempt, remoteId, lastError, timestamps)
```

A provider failure cannot corrupt the QSO.

## 7. UI workspace model

Instead of one giant screen, expose purpose-driven workspaces backed by the same radio/log/map state:
- OPS dashboard
- FT family
- JS8/chat
- legacy keyboard digital
- APRS
- SSB
- CW
- MAP
- LOG
- AWARDS
- RADIO
- SETTINGS/DIAGNOSTICS

## 8. Offline-first and source-age aware

Following Look4Sat's useful mobile pattern:
- core logging, radio control, DSP, cheatsheets and cached award definitions work offline.
- network layers show stale-age indicators.
- propagation never silently presents stale data as live.

---

# Things explicitly not to copy

- Hamlib's multi-radio backend complexity.
- SDRangel's generalized SDR device/channel graph where a simpler FTX-1 audio fanout is sufficient.
- desktop-window assumptions from fldigi/QLog/KLog.
- server-centric PHP architecture from Wavelog/Cloudlog.
- any GPL/AGPL component without deliberate licensing decision.
- any external project's award wording/rules as authoritative; official issuers remain source of truth.

---

# Research queue created by this survey

1. Inspect fldigi modem base class and mode registration more deeply before final `DigitalModeProvider` API freeze.
2. Inspect Wavelog award models for generic/common logic versus one-off award evaluators.
3. Inspect QLog LoTW/TQSL process invocation and secure credential abstraction.
4. Inspect wfview startup state machine/command queue for reconnect behavior applicable to FTX-1.
5. Inspect Dire Wolf Bell-202 timing/error recovery against FieldOps APRS modem performance tests.
6. Inspect OpenWebRX mode orchestration and decoder process isolation for optional GPL engines.
7. Validate DVOACAP propagation output against authoritative/reference cases before deciding embed vs service.
8. Research official award APIs/rules separately; do not freeze award logic from third-party code.
9. Review Android power/foreground-service patterns in Look4Sat/JS8 Android/FT8AF relevant to long field sessions.
10. Keep a license decision record before vendoring any additional source.
