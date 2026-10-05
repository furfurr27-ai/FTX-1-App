# Multi-mode parity target

The product target is no longer "FT8 + APRS". It is a Galaxy S23 Ultra / FTX-1 digital field terminal with qFT8-class multi-mode behavior plus APRS.

## Modes

### Production targets
- **FT8** — structured 15 s QSO workflow, manual/auto sequence, caller queue, auto signal follow, logging/reporting.
- **FT4** — same operational model as FT8 but with 7.5 s cycles and mode-specific decoder/TX timing.
- **FT2** — qFT8 parity target with 3.75 s cycles. Keep the UI/state machine separate from FT8/FT4 timing assumptions.
- **JS8** — dedicated conversational interface, not an FT8 contact screen. Directed messages, channel/conversation list, multipart reassembly, heartbeat/presence, requests/replies, message history and optional mailbox/relay behavior. Speed profiles: Slow/Normal/Fast/Turbo.
- **WSPR** — dedicated propagation screen. 120 s even-minute slots, beacon power in dBm, probabilistic TX scheduling, RX spot table, WSPRnet/PSKReporter reporting, map layer and session export.
- **APRS** — Radio APRS (FTX-1 internal modem configuration) and Phone APRS (AFSK/AX.25 through USB audio) remain separate explicit paths.

### Compatibility/research targets
- **LQ8 / LQ4** — qFT8 v3.16 contains these mode identifiers and uses 15 s / 7.5 s cycle timing. Because these are qFT8-specific and the full qFT8 source is not public, do not copy or guess the codec. Keep scheduler/UI hooks ready, but only enable interoperability after an independently validated implementation exists.

## qFT8 behavior observed directly in the uploaded v3.16 APK
Static DEX inspection found mode routing for FT8, FT4, FT2, JS8, WSPR, LQ8 and LQ4. It also exposed distinct JS8 chat/channel actions, WSPR beacon state, WSPR power/percentage/jitter settings, PSKReporter/WSPRnet reporting strings, per-mode decoder-thread settings, and web-remote mode switching. These findings are useful for feature mapping but are not treated as reusable source code.

## Shared services
Every digital mode should reuse the same:
- FTX-1 CAT transaction manager
- USB audio transport
- UTC/GPS/NTP clock discipline
- waterfall/spectrum renderer
- radio ownership/PTT interlock
- band/frequency profile service
- station identity/grid service
- logging database
- reporting queue and network health status
- diagnostics/trace framework
- background/screen-off service

## Mode-specific engines
The UI and state machines must not pretend the modes are interchangeable:

| Mode | Workflow | Cycle | Primary UI |
|---|---|---:|---|
| FT8 | Structured QSO | 15 s | waterfall + decodes + QSO sequencer |
| FT4 | Structured QSO | 7.5 s | waterfall + fast decodes + QSO sequencer |
| FT2 | Structured QSO | 3.75 s | waterfall + very-fast QSO sequencer |
| JS8 | Conversation/network | 30/15/10/6 s | chat + channels + traffic monitor |
| WSPR | Beacon/propagation | 120 s | spots + beacon scheduler + map |
| APRS | Packet/location/message | asynchronous | stations + messages + map |

## License architecture
- qFT8 is used as a behavioral reference; its full source is not presently public.
- FT8/FT4/WSPR reference implementations in WSJT-X are GPLv3. If linked/derived code is used in a distributed build, GPL obligations apply.
- JS8Call/JS8Call-improved is GPLv3. There is now an Android port with a platform-agnostic core + JNI architecture that is useful as a technical reference.
- Because this project is currently for one private device, GPL distribution obligations are not triggered by private use, but the project should still retain provenance/license notices so it can be handled correctly if it is ever shared.

## UI direction
Replace the top-level **FT8** navigation item with **DIGI**. DIGI contains a fast mode strip (FT8 / FT4 / JS8 / WSPR / MORE). Selecting a mode swaps the operational workspace rather than only changing a label.

JS8 and WSPR get deliberately different layouts:
- JS8: conversation-focused, larger text entry, channel/contact drawer, pending/outgoing state and explicit TX queue.
- WSPR: full-width activity list, next-beacon countdown, TX percentage/power controls and propagation map shortcuts.
