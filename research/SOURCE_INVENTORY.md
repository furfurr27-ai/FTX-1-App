# FTX-1 FieldOps — source inventory

## Primary files actually inspected

### qFT8-v3.16.apk
- Type: Android APK; one `classes.dex`; no native `.so` libraries visible in the package.
- SHA-256: `8ed331624102eebe382cf67f83f0655a47fde874c0b32e7e6877bee04cd6395d`
- Useful evidence: FTX-1 / FTX-1 Field / FTX-1 Optima model strings; Yaesu CAT handling; FT8/FT4/JS8/WSPR-related UI and state strings; audio-route recovery; waterfall/peaks; CAT console; web UI; world/DXCC assets.
- Best use: behavior/feature reference and fault-tolerance reference.
- Limitation: qFT8's public repository says the complete source is not yet published. The APK's DEX is obfuscated and this environment has no JADX/apktool, so this is static package/string analysis, not a faithful reconstruction of its source.

### FT8AF-android-dev.1312.apk
- Type: Android APK; one `classes.dex`; multi-ABI native libraries including `libft8af.so`.
- SHA-256: `22de191aac10005f4ac3e131ddc540cf650c0177cdbfdee2c10f8e9b72334d6f`
- Useful evidence: modern FT8 interaction strings, automatic call tracking/following, caller queue, CQ controls, waterfall/spectrum controls, USB CAT/audio handling, logging, PSK Reporter features.
- Native evidence checked with `nm`: JNI decode/encode symbols plus Hamlib radio-control symbols and FTX-1-specific Hamlib symbols.
- Best use: FT8 interaction model, DSP/Hamlib architecture reference, modern UI direction.
- Limitation: full Kotlin/Java source is not embedded in this APK. Upstream public repository is needed for line-by-line source review.

### APRSdroid-1.6.3d.apk
- Type: Android APK; one `classes.dex`; multi-ABI `libmultimon.so`.
- SHA-256: `8f7261153f7f334911d91ec87b9411cbfaa7b376d065acf93d33a24fc26adccb`
- Useful evidence: unusually, the APK contains 59 Scala source files. Directly inspected `tncproto/KissProto.scala`, `backend/UsbTnc.scala`, and `location/SmartBeaconing.scala`.
- Best use: APRS/KISS/USB lifecycle/SmartBeaconing behavioral reference.
- Limitation: older Scala-era Android architecture; not a suitable UI/application architecture to copy wholesale into a modern single-device app.

## External sources actually checked

### Yaesu FTX-1 Series CAT Operation Reference Manual
- Current US product page lists English CAT manual revision 2508C.
- Checked CAT command format and Table 3 menu controls.
- Directly relevant items: `FA` frequency, `MD` operating mode, `TX` transmit state, `EX` menu access; APRS modem/callsign/digipeat/beacon settings; PRESET TX BPF, USB output/modulation controls.

### Public repositories
- qFT8 GitHub: confirms complete qFT8 source is planned but not currently published.
- FT8AF GitHub: documents Jetpack Compose UI and native `ft8_lib`/JNI architecture.
- APRSdroid GitHub: GPLv2 project and source reference.

## Source-handling conclusion
A professional replacement should be a clean implementation, not an APK splice. qFT8 is used as a behavioral reference, FT8AF as an open-source FT8/DSP/Hamlib reference, APRSdroid as an APRS protocol reference, and Yaesu documentation as the source of truth for FTX-1 CAT commands.

## Additional DEX structure inspection
A local DEX metadata parser was used to enumerate class/method tables without pretending to recover original source. Results include:
- qFT8: `com.ft8.app.MainActivity` has 440 encoded members; dedicated classes include `BackgroundService`, USB serial drivers, `FrequencyRulerView`, `PeaksView`, `SpectrogramView`, `WaterfallView`, and a substantial `WorldMapView` (138 members). The majority of internal symbols are obfuscated.
- FT8AF: identifiable classes include `FT8SignalListener`, `GenerateFT8`, `CallingListFragment`, `SpectrumView`, `WaterfallView`, `HamlibNative`, `UsbAudioNative`, and `ComposeMainActivity`.
- APRSdroid: source-bearing APK structure confirms separate backend, TNC protocol, location/SmartBeaconing and service layers.

The generated DEX inventories are retained in the local research workspace, not represented as original application source.

## Multi-mode expansion review (2026-10-03)

### qFT8 v3.16 uploaded APK — additional direct findings
A fresh string/DEX pass specifically for mode parity found identifiers and web/UI actions for:
- FT8
- FT4
- FT2
- JS8, including chat channels, directed requests, heartbeat, reply/cancel flows, per-speed decode settings and JS8 log export
- WSPR, including beacon arm/next-TX state, power dBm, TX percentage, random jitter, WSPR spot persistence/export, WSPRnet upload/fetch and PSKReporter integration
- LQ8 and LQ4 mode identifiers and cycle routing

The qFT8 web UI embedded in the APK maps cycle lengths as 15 s FT8/LQ8, 7.5 s FT4/LQ4, 3.75 s FT2, 120 s WSPR, and JS8 speed-dependent cycles. This is treated only as behavioral/timing evidence from the user's uploaded binary, not copied source.

### qFT8 public site/manual
The current public qFT8 material independently confirms support for FT8, FT4, FT2, JS8, WSPR and LQ8, plus dedicated JS8 Chat and WSPR mode interfaces, reporting, world-map behavior, remote web control, logging and portable-operation features.

### FT8AF public repository
Checked current upstream repository. It is MIT licensed, uses a Jetpack Compose UI, USB CAT/audio, and now builds its native FT8 DSP from source using a vendored/pinned `ft8_lib` plus JNI glue. This remains the best open Android reference for a modern FT8-native architecture.

### JS8Call / JS8Call-improved
Checked the current official/community repositories. JS8Call is GPLv3 and implements directed/free-text weak-signal messaging and networking. A current Android port exposes a platform-agnostic core, JNI adapter and Android foreground-service/audio architecture, making it a strong implementation reference for JS8 mode integration. GPL provenance must be preserved if code is linked or derived.

### WSJT-X / WS-family modes
Checked current public documentation/source distribution. FT8/FT4/WSPR implementations are GPLv3; independent interoperable implementations are possible, but copying or linking WSJT-X code changes licensing obligations. This architecture therefore keeps DSP engines behind explicit native interfaces and records provenance per engine.


## Shared DSP build pass (v3)

### Uploaded files directly re-checked
- `FT8AF-android-dev.1312.apk`: extracted ARM64 native library; parsed DEX field/method descriptors; mapped exact JNI method signatures and FT8/FT4/FT2 timing metadata. Native binary dependency table and license strings were also inspected.
- `APRSdroid-1.6.3d.apk`: directly inspected `backend/AfskDemodulator.scala`, `backend/AfskUploader.scala`, and `backend/AfskInWrapper.scala`. These confirm the historical HQ path normalizes PCM and streams it to a Bell-202/AX.25 demodulator; FieldOps replaces the separate recorder with shared audio fanout.
- `qFT8-v3.16.apk`: existing v2 behavioral findings remain the parity reference; no qFT8 code is copied into this layer.

### Current external references checked
- `kgoba/ft8_lib`: MIT FT8/FT4 C encode/decode reference.
- `JS8Call-improved/Android-port`: GPLv3; current README says RX/TX work and documents a platform-independent `core/`, Android adapter, JNI bridge, Gradle/NDK AAR and AudioRecord foreground-service path.
- `pavel-demin/wsprd` / WSJT sources: compact GPLv3 WSPR decoder reference.
- Yaesu FTX-1 product/manual page: current product page still lists CAT manual revision 2508C.

### Environment limitation
There is no Android SDK/NDK/ADB attached to this runtime. JVM/Kotlin core tests and static ARM64 ABI checks can be run here; final JNI loading, USB routing, CAT transactions and RF TX must be validated on the Galaxy S23 Ultra + FTX-1 hardware.

## Broad GitHub reference survey (2026-10-04)

A connected-GitHub scan used `topic:ham-radio`, `topic:amateur-radio`, `topic:hamradio` and targeted repository/code searches. More than 100 public repository hits were surfaced before de-duplication. Detailed conclusions were limited to a pinned reference set whose README/source was actually inspected.

Key directly checked references now include FT8AF, JS8Call Android, ft8_lib, rtlsdr-wsprd, fldigi, SDRangel, OpenWebRX, FreeDATA, Dire Wolf, Xastir, Hamlib, flrig, wfview, QLog, Wavelog, KLog, Cloudlog, not1mm, Ham2K PoLo docs, DVOACAP-Python, Pat, K3NG CW Keyer, FreeDV, QSSTV, Look4Sat and FT8CN.

Exact commit pins, inspection level, lessons and limitations are stored in `research/github/`.

Important conclusion: GitHub references support a stable radio-control boundary, but FieldOps should not inherit multi-radio complexity. The production radio layer remains a concrete FTX-1 driver. Modem, award, map, propagation and sync interfaces are the extension points.
