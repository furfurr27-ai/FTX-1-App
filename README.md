# FTX-1 FieldOps

Private Android operating environment for **Chris / N0PNG** and the **Yaesu FTX-1 / FTX-1F**, with the **Samsung Galaxy S23 Ultra** as the primary target device.

This repository is the durable source of truth for FieldOps. Chat discussion, plans, and summaries are useful navigation aids, but they do **not** count as completed engineering work unless the relevant files and evidence exist in a verified checkpoint/commit.

## New-chat handoff — read this first

When starting a new ChatGPT/Codex chat for this project:

1. Read this `README.md` first.
2. Read `checkpoints/LATEST.json`, `checkpoints/CURRENT_STATE.json`, and `checkpoints/RESUME_HERE.md`.
3. Inspect the current Git tree and latest checkpoint before changing code.
4. Do not redo already-verified work unless verification fails or a newer requirement explicitly invalidates it.
5. Do not promote a feature from planned/researched/host-tested to verified/device-tested without evidence.
6. Update this README in the **same durable commit** whenever the current goals, explicit rules, processed sources/data, established logic, verified checkpoint, blockers, or exact next action materially change.

If this README and an older checkpoint document disagree, preserve both facts and resolve the discrepancy from the newest verified artifact/commit. Never silently choose the more convenient version.

## Primary goal

FieldOps is a one-stop operating environment for one radio family: the Yaesu FTX-1 / FTX-1F.

It is **not** intended to become a universal radio-control application. Hardware-specific complexity stays concentrated in a single FTX-1 device/radio layer. Extensibility belongs above that layer in mode providers, logging/sync providers, awards, maps, propagation, and operating workspaces.

Target operating modes include:

- FT8, FT4, FT2
- JS8
- WSPR
- APRS
- CW
- SSB
- RTTY
- PSK31 / PSK63
- Olivia / MFSK
- Contestia / DominoEX / THOR / Hellschreiber / MT63
- later SSTV and other useful modes

## Verified durable baseline

**Latest verified checkpoint:** `CP-0003B-LOTW_TRANSACTION_SAFE`

Parent: `CP-0003A-TRUSTEDQSL_SIGNER`.

CP-0003B is a **GREEN host/CI LoTW transaction checkpoint with RED real-account/device status**. The old one-shot signer/upload path is retired. LoTW upload now preserves the CP-0003A TrustedQSL transaction through server acceptance verification.

The verified transaction is:

`sign -> upload TQ8 -> verify every QSO in LoTW accepted report -> commit TrustedQSL duplicate state`

CP-0003B proves:

- One explicit FieldOps station profile / station callsign per batch.
- Explicit TrustedQSL station-location name, expected callsign and expected DXCC are carried into signing.
- Duplicate local QSO ids and ambiguous acceptance match keys fail before signing.
- Signing failure never uploads.
- Transport exceptions and non-2xx upload responses roll back and remain QUEUED/retryable.
- LoTW endpoint rejection rolls back and becomes REJECTED rather than silently retrying.
- HTTP upload acceptance alone is only SUBMITTED.
- Acceptance report failure or partial batch acceptance rolls back the signer transaction and never marks the batch ACCEPTED.
- Only when **all** QSOs appear in the LoTW accepted-QSO report does FieldOps commit the TrustedQSL duplicate database and mark the batch ACCEPTED.
- SSB, CW and digital QSOs use the same mode-neutral `LotwUploadQueue`.
- Rejected entries remain visible but are excluded from automatic pending/retry selection.
- Verified accepted entries leave the queue.
- TrustedQSL Kotlin session state is hardened so successful native commit/rollback becomes terminal before cleanup.
- The signer implementation itself remains isolated from HTTP transport.

Host/CI gates:

- CP-0003B transaction suite: **53 assertions PASS**.
- CP-0003A signer regression: **43 assertions PASS**.
- Inherited core: **42,062 PASS**.
- Pipeline: **56 PASS**.
- Inherited LoTW: **19 PASS**.
- Official TrustedQSL 2.8.6 archive hash/API compile remains verified by the signer regression.

Evidence:

- `research/tqsl/CP-0003B_TRANSACTION_SAFE_UPLOAD.md`
- `research/LOTW_INTEGRATION.md`

- CP-0003B finalization workflow run: `37329432853`

### Evidence boundary

The transaction is host/CI verified with deterministic signer/transport/report fixtures. CP-0003B does **not** claim a real LoTW upload, real server acceptance, Android ARM64 TrustedQSL packaging, real certificate/private-key use, Room-backed persistence, or automatic upload.

The shared queue is now mode-neutral, but automatic SSB/CW logger-save enqueue remains the separate CP-0005B item.

### Inherited verified ancestry

`CP-0003A-TRUSTEDQSL_SIGNER`, `CP-0002E-NATIVE_MODE_REGRESSION`, and all prior native-mode checkpoints remain verified ancestry.

## Engineering rules

### Checkpoint first

A feature is durably complete only when it exists in a verified immutable checkpoint/commit containing enough evidence to resume without relying on chat memory.

A checkpoint should record, as applicable:

- parent checkpoint/commit
- exact source revisions and pinned upstream commits
- hashes
- tests run and results
- completed items
- blockers/red items
- exact next action
- source snapshot or reproducible source state

`RESUME_HERE.md` plus machine-readable checkpoint state are the recovery path after interruption.

### Anti-freeze / test discipline

Do not repeatedly compile the entire project when a smaller slice can prove the current change.

Preferred progression:

1. inspect latest checkpoint and working tree
2. compile the smallest affected main source slice once
3. compile tests separately against the proven main output
4. run focused suites independently
5. run broader regression only after the affected slice is green
6. checkpoint immediately when the objective is proven

A timeout is neither a pass nor a fail. Do not rerun already-proven subsystems merely because a later suite stalled.

### Radio safety

- Never PTT on startup, reconnect, or mode changes.
- Every transmission goes through one FieldOps TX ownership/arbitration path.
- RTS/DTR are not PTT.
- CAT TX commands own PTT.
- Any TX failure collapses to RX-safe state: PTT off / `TX0`, audio stopped, TX ownership released.
- No modem, helper, workspace, or UI may bypass the TX owner.
- Ambiguous CAT USB-port identification means TX stays disabled.
- Hardware-dependent claims remain RED until tested on the actual S23 Ultra + FTX-1.

### Evidence labels

Use precise labels:

- **verified/durable** — present in a verified checkpoint/commit
- **host-tested** — tested on the host but not the phone/radio
- **Android-build-ready** — source/build integration prepared, not necessarily installed
- **real-device-tested** — actually tested on the S23 Ultra / FTX-1
- **planned/researched** — design/reference work only

Do not use “working,” “native,” “built,” or “tested” without saying which evidence level applies.

## Established signal-path logic

### Shared RX

FTX-1 USB audio is treated as a common **48 kHz mono PCM** source.

Branch 1:

`48 kHz raw -> Bell 202 AFSK1200 -> HDLC -> AX.25 -> APRS`

Branch 2:

`48 kHz -> 97-tap anti-alias FIR -> decimate by 4 -> 12 kHz weak-signal/modem domain`

FT8/FT4/FT2 and WSPR use time-window logic appropriate to each protocol.

For WSPR RX, the 12 kHz branch continues through:

`12 kHz real -> 1500 Hz complex mixer -> FIR /4 -> FIR /4 -> FIR /2 -> 375 sps complex I/Q -> pinned wspr_decode()`

The resulting WSPR decoder window is exactly 45,000 complex samples over 120 seconds.

JS8 is a continuous 12 kHz stream consumer and must **not** be forced through the FT/WSPR slot-window assembler.

### TX ownership

For WSPR TX:

`message -> pinned get_wspr_channel_symbols() -> 162 symbols -> continuous-phase 12 kHz 4-FSK -> WsprTxController -> Ftx1RadioSession`

The WSPR modem layer never owns physical PTT. The current host-proven waveform is 1,327,104 samples / 110.592 seconds at 12 kHz; final device-rate handling remains hardware-gated.

The radio session owns CAT/PTT and USB TX audio. Complete-waveform modes and streaming modes may use different waveform/feed mechanics, but both must use the same ownership and fail-safe boundary.

The current FTX-1 operating preference is PRESET 1 with TX BPF SEL 300–2700 Hz. Practical FT8 audio should normally remain roughly 400–2700 Hz.

## Mode/provider architecture

The long-term common mode boundary is a `DigitalModeProvider`-style contract carrying, at minimum:

- identity and display name
- exact ADIF mode/submode identity
- streaming vs slotted timing model
- sample-rate/bandwidth needs
- RX and TX lifecycle
- AFC/tuning metadata
- waterfall metadata
- recommended FTX-1 mode/filter settings
- logging metadata
- TX timing/safety requirements
- optional conversation/quick-message capabilities

Providers never own physical PTT.

## Logging and LoTW logic

The local logbook is authoritative.

Manual SSB/CW and digital contacts use the same QSO core and sync queue. Preserve:

- exact radio mode
- ADIF parent mode/submode
- controlled award-mode grouping
- band/frequency
- UTC
- station profile/location
- sync state per external provider

LoTW state is not binary. Track at least:

- not uploaded
- pending
- submitted
- accepted
- confirmed
- retry/failure

HTTP 200 does not prove a QSO was accepted or confirmed.

The intended LoTW signing flow is:

`local QSO -> TrustedQSL sign -> .tq8 -> upload -> receipt/acceptance query -> later confirmation sync -> reconcile`

Station location must be explicit. Never infer signing location solely from callsign.

Website credentials are separate from certificate/PKCS#12 material and passwords. Never write secrets, certificates, passwords, or signing keys to logs, crash reports, support bundles, or this public repository.

## Awards logic

Awards are first-class application state, not decoration on the logbook.

Keep distinct:

- needed
- worked
- confirmed
- threshold met
- potentially claimable
- officially awarded/credited when known

Local threshold completion does not automatically equal sponsor claimability. Official rules and claim links must be verified from the issuing organization before encoding requirements.

Keep exact mode/submode and a controlled award-mode group. Do not collapse unrelated digital submodes simply because ADIF places them under a common parent family.

## Map and propagation logic

The map is a core operating view.

Layer categories include:

- heard/decoded signals
- logged QSOs
- APRS
- observed propagation
- predicted propagation
- award progress
- Maidenhead/grid geography
- DXCC/CQ/ITU/state geography where a real versioned/licensed dataset is available

Observed RF paths and modeled propagation must remain separate.

Do not fabricate geographic polygons.

Prediction results must retain model/provider provenance, generation time, validity/freshness, inputs, band/frequency, power/path assumptions, and reliability/confidence.

NOAA/SWPC products are model inputs/evidence, not observed RF paths.

The later recovery handoff identifies embedded HFcast as the intended prediction path, but that implementation must be recovered/verified before GitHub treats it as durable.

## FTX-1 USB target rules

The intended CAT path is the FTX-1 **Enhanced COM Port**.

Default CAT settings: **38400, 8N1**, configurable.

Do not assume USB interface 0 is CAT. Port resolution priority is:

1. descriptor/name identifying the Enhanced port
2. previously verified saved mapping
3. exact CP2105 topology fallback

If identification remains ambiguous, TX is disabled.

USB audio endpoint/rate selection is fail-closed until actual Android enumeration is known.

## Processed research / source pins

The CP-0001 survey processed more than 100 amateur-radio GitHub hits and pinned a smaller high-value reference set to exact commits. The authoritative pin table is:

`research/github/SOURCE_PINS.tsv`

Important references include FT8AF, JS8Call Android, rtlsdr-wsprd, fldigi, SDRangel, OpenWebRX, Dire Wolf, Hamlib, wfview, Wavelog, KLog, QLog, Cloudlog, ham2k/Polo, DVOACAP, Look4Sat, Pat, FreeDATA, FreeDV, QSSTV and others.

References are categorized as architecture/behavior/protocol/reusable implementation candidates. Do not copy code merely because it is public. Review licensing before vendoring any implementation.

## Repository/import notes

The verified CP-0001 text/source tree has now been restored to GitHub `main`.

- GitHub restore commit: `722de2e7b744b67a77ffa05a25b1b70f933871ab`
- Restore workflow: **PASS**; the CP-0001 recovery workflow is now manual-only and requires explicit `RESTORE_CP0001` confirmation
- Reassembled source-transport archive SHA-256: `125026544bb75c8089b14f8fbbcdba131d755ad7599427bb7495acd717e3cbf2`
- Current Git source baseline: `CP-0003B-LOTW_TRANSACTION_SAFE`
- Original external checkpoint package verification before import: **PASS, 157 file hashes**

The Git checkout contains the recovered source/text/checkpoint metadata, including `checkpoints/LATEST.json`, `checkpoints/CURRENT_STATE.json`, `checkpoints/RESUME_HERE.md`, and `research/github/SOURCE_PINS.tsv`.

The original recovery package also contained generated build outputs and binary artifacts such as the extracted FT8AF native library, compiled classes/JARs, the checkpoint snapshot tarball, and a UI PNG. Those artifacts were intentionally not restored into this public Git source tree. See `artifacts/CP-0001-BINARY-ARTIFACTS.md` for recovery hashes/status.

Because the original immutable checkpoint snapshot is intentionally absent from the public checkout, the old snapshot-based verifier is not expected to reproduce its original PASS directly against this Git tree. The original CP-0001 ZIP remains the verified evidence package; this Git commit is the durable recovered source baseline.

The repository is currently **public**. Never commit credentials, private keys, PKCS#12 files, LoTW passwords, personal tokens, device secrets, or other sensitive material.

## Current exact next action

**CP-0003C — Real LoTW validation.**

1. Build/package the official TrustedQSL dependency stack for Android arm64-v8a.
2. Load it on the Galaxy S23 Ultra and import a real test Callsign Certificate/PKCS#12 through the production secret-storage boundary.
3. Use a controlled test QSO/account flow to produce a real signed TQ8.
4. Exercise the CP-0003B transaction against LoTW: sign -> upload -> verify accepted-QSO report -> commit.
5. Run confirmation sync and verify the local confirmation metadata/cursors.
6. Confirm no certificate/password/private-key material appears in logs, crash output, support bundles or repository files.
7. Keep automatic upload disabled unless every CP-0003C device/test-account gate passes.

## README maintenance contract

This file is the human-readable project handoff.

Every future durable engineering pass must review and update this README when any of the following changes:

- primary/current goal
- explicit engineering or safety rule
- verified checkpoint/baseline
- processed source/data set or source pin
- established protocol/DSP/state-machine logic
- evidence status
- blocker
- exact next action

A new chat should be able to read this file and know what FieldOps is, what is proven, what is merely planned/recovered from discussion, and exactly where to resume.
