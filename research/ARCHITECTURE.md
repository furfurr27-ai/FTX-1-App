# Proposed architecture

## Product target
One device and one radio: Samsung Galaxy S23 Ultra + Yaesu FTX-1. The reduced hardware matrix is an advantage: optimize layout, USB lifecycle, audio routing, decoder threading, background behavior, and radio defaults for this pair rather than maintaining a generic radio application.

The app is a **multi-mode digital field terminal**, not an FT8-only app. First-class operational modes are FT8, FT4, FT2, JS8, WSPR and APRS. LQ8/LQ4 remain compatibility/research targets until their codecs can be independently validated.

## Modules
1. **Radio transport** — Android USB host, permission lifecycle, serial/CAT queue, reconnect handling, strict command/response parser.
2. **Audio transport** — USB audio input/output with explicit route validation, ring buffers, sample-clock monitoring and underrun/overrun counters.
3. **Digital scheduler** — mode profiles, UTC-aligned slot clock, TX watchdogs, per-mode timing and mode switching.
4. **FT family engine** — FT8/FT4/FT2 DSP, structured-message encode/decode, deep-decode policy, caller/QSO state machines and logging.
5. **JS8 engine** — JS8 modem plus multipart transport, channel state, directed messages, heartbeat/request handling and chat persistence.
6. **WSPR engine** — WSPR decode/encode, 2-minute scheduler, beacon percentage/power/jitter policy, spot history and reporting.
7. **Digital automation** — auto signal follow, caller queue, CQ/answer policies, retry logic and slot-safe frequency changes. No CAT or UI code in policy classes.
8. **APRS engine** — AX.25/KISS, APRS packet parser/formatter, GPS, SmartBeaconing, APRS-IS optional networking.
9. **FTX-1 APRS control** — CAT `EX` controls for the radio's own APRS modem, callsign, digipeat path, beaconing and SmartBeacon settings.
10. **Radio arbiter** — single owner for PTT/audio. No mode may steal the device while another mode is transmitting.
11. **Persistence** — Room database for QSOs, JS8 conversations, WSPR spots, APRS stations/messages, settings snapshots and diagnostic events.
12. **Network/reporting** — QRZ/LoTW/ADIF, PSKReporter, WSPRnet, optional APRS-IS, retry/backoff and offline queueing.
13. **UI** — Jetpack Compose; fixed S23 Ultra-first ergonomics with portrait primary layout.
14. **Diagnostics** — circular structured log, CAT trace, USB/audio health, timing error, decoder duration, dropped-buffer counters and TX watchdog events.

## Suggested navigation
- **OPS**: radio/USB/audio/GPS/time status, active mode, next slot/beacon, TX interlock, quick band.
- **DIGI**: mode strip + mode-specific workspace for FT8, FT4, FT2, JS8, WSPR (and later validated LQ modes).
- **APRS**: station list/map, message view, beacon controls, radio-internal vs phone-AFSK mode selector.
- **MAP**: layer toggles for QSO/PSKReporter/WSPR/APRS data; never merge unlike datasets without labeling.
- **LOG**: QSO logs, JS8 history/export, WSPR session spots, ADIF import/export, APRS event history.
- **RADIO**: FTX-1 profile, CAT console, preset validation, USB/audio diagnostics.

## DIGI workspaces
### FT8 / FT4 / FT2
Shared waterfall and decode list, but separate timing profiles. Do not hard-code 15-second assumptions into the QSO engine. Each mode gets slot-safe PTT, decode deadlines, retry timing and selectable auto/manual sequencing.

### JS8
Conversation-first interface: channel list, directed/free text, multipart reassembly, heartbeat/presence, requests and explicit outgoing queue. Treat JS8 as a networking/chat protocol layered over a weak-signal modem, not as a longer FT8 message box.

### WSPR
Propagation-first interface: full-width spot table, beacon armed state, next TX countdown, dBm power, TX percentage, optional jitter, WSPRnet/PSKReporter status and map. WSPR does not use the structured QSO sequencer.

## Visual language
Use a restrained WWII field-radio / Army-instrument theme, not camouflage wallpaper. Suggested surfaces: near-black instrument panels, desaturated olive/khaki accents, off-white labels, amber/red status lamps, engraved/stencil-like display headings. Body text and dense radio data should stay in a modern high-legibility typeface. The TX/ABORT path should be visually unmistakable and remain readable outdoors.

## Important APRS design choice
The FTX-1 CAT manual exposes many internal APRS settings through `EX`, which is valuable. It does not document a CAT command that streams the radio's decoded AX.25 frames to the phone as KISS/raw packets. Therefore the app should support two explicit APRS paths:

- **Radio APRS mode:** configure and use the FTX-1's internal APRS features via CAT; treat the radio display as the packet endpoint unless a documented packet stream is found.
- **Phone APRS mode:** decode/encode AFSK1200 through USB audio and use CAT PTT, giving the phone full station/message/map data.

Do not silently assume those two paths are equivalent.

## Safety invariants
- App launch, mode switch and USB reconnect must never key PTT.
- Any exception while transmitting schedules immediate `TX0;` and closes the audio writer.
- A watchdog ends TX if slot/audio state exceeds the expected duration for the selected mode.
- CAT writes are serialized; state-changing commands get read-back verification where the protocol supports it.
- No mode can preempt another mode's active transmission.
- Automatic signal-follow never moves the TX cursor while PTT is active.
- WSPR beacon scheduling must be cancelled on radio ownership loss, USB loss, time-sync failure or unsafe SWR state if SWR monitoring is available.
- JS8 queued traffic must never transmit after an unexpected mode/radio change without revalidation.
