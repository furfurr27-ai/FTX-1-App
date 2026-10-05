# FTX-1 FieldOps — One-Stop-Shop Product Requirements

Owner: N0PNG / private Galaxy S23 Ultra build
Status: LOCKED BACKLOG — do not drop during native-mode work
Captured: 2026-10-04

## Product intent
FieldOps should become a one-stop operating companion for the Yaesu FTX-1: radio control, receive/transmit DSP, digital conversational modes, CW/SSB operating aids, logging, LoTW synchronization, awards tracking, propagation intelligence, and a tactical-style map.

The common FTX-1 CAT/audio/PTT layer remains the architectural center. New modes/features must use that shared control path; no feature gets an independent PTT/audio bypass.

## 1. Universal logbook including SSB/CW

### Manual / assisted logger for non-data modes
Add a fast logger for SSB/PHONE, CW, AM, FM and other manually operated modes.

When CAT is connected, prefill automatically:
- UTC start/end
- frequency and band
- radio mode/submode
- station profile / operating location
- callsign currently entered/selected

Fast fields:
- callsign
- sent/received RST
- grid / location if known
- name
- QTH
- notes
- power
- operator/station profile
- award/program tags (POTA/SOTA/etc. where relevant)

Quick actions:
- Start QSO
- End + Log
- Log & New
- duplicate warning
- recent-QSO lookup
- edit before upload

### LoTW behavior
All eligible QSOs, including SSB/CW and other modes, feed the same LoTW queue.
- User-selectable auto-upload after logging.
- Never mark as accepted merely because HTTP upload succeeded.
- Preserve states: local -> signed -> submitted -> accepted -> confirmed.
- Retry and sync use the existing LoTW manager.
- Explicit station profile is mandatory for signing.

## 2. Awards Center

Create an Awards dashboard driven from the unified logbook.

### Required progress states
For each award/program show:
- Worked
- Confirmed (LoTW or other accepted confirmation source)
- Claimable now
- Remaining entities/areas/bands/modes
- Percentage / progress bar
- map overlay where geography is relevant

### Mode views
Every award that permits it should be filterable by:
- Mixed
- CW
- Phone / SSB
- Digital
- individual digital modes where meaningful
- band
- date range

This is important: awards are not a "digital-only" feature.

### Catalog goal
Include major and minor awards, with each award record containing:
- official name
- issuing organization
- plain-English explanation
- qualifying contacts/confirmations
- geographic/band/mode restrictions
- endorsement levels
- what FieldOps currently counts toward it
- what still needs external verification
- official information URL
- official claim/submission URL
- concise "How to claim" instructions

Initial research/catalog targets (subject to official-rule verification before implementation):
- ARRL DXCC family
- ARRL Worked All States (WAS)
- ARRL VUCC
- ARRL Triple Play
- ARRL / IARU Worked All Continents (WAC)
- ARRL Fred Fish Memorial Award / grid-oriented programs where applicable
- CQ Worked All Zones (WAZ)
- CQ WPX
- RSGB IOTA
- POTA awards
- SOTA awards
- commonly used regional/national awards useful to an operator in Europe and the U.S.
- additional mode-, band-, grid-, county-, prefix-, island-, park-, summit-, and country-based awards

Do not hard-code award logic into UI. Use a rule/catalog engine so new awards can be added by data/config plus specialized evaluators when necessary.

## 3. Map intelligence layers

The vintage/WWII field-map visual language remains, but operational readability outranks decoration.

Layer switcher must support independent overlays and opacity controls.

### A. Heard signals / live digital activity
Like qFT8-style map activity, but across supported modes where position can be derived.
- recently decoded stations
- mode
- band
- SNR / signal quality
- time heard
- callsign label toggle
- decay/fade by age
- selectable time horizon

### B. Propagation conditions — TOP PRIORITY MAP LAYER
This is the most important map intelligence feature.

Combine useful propagation context where data is available:
- live spots / heard paths from FieldOps
- PSK Reporter-style reports when available
- WSPR reception/spot data where appropriate
- MUF/foF2 / ionospheric map products if a reliable source/API is available
- solar indices and geomagnetic conditions
- greyline/day-night terminator
- band-specific usable-path visualization
- path history / trend

UI should answer: "From where I am, what bands and directions look useful right now?"

Allow band selection and a compact confidence/explanation panel rather than a single unexplained color heatmap.

### C. Award-area progress
Geographic award overlays, for example:
- DXCC entities
- U.S. states
- Maidenhead grids
- CQ/ITU zones
- islands/parks/summits where data/license permits

Map styling:
- worked but unconfirmed
- confirmed
- still needed
- claimable threshold reached

### D. Logged QSO locations
Show QSOs from the unified log.

Time-scale selector must include at least:
- last hour
- 6 hours
- 24 hours
- 7 days
- 30 days
- current session
- custom dates
- all time / forever

Filters:
- mode
- band
- station profile
- confirmed vs unconfirmed
- callsign / country / grid

For dense histories use clustering/aggregation rather than thousands of unreadable pins.

## 4. SSB operating workspace + cheat sheets

Create an SSB/PHONE mode screen instead of treating SSB as merely a CAT mode.

Core controls/info:
- frequency / band / mode
- PTT state
- filter width / relevant CAT controls where safe
- quick log panel
- recent stations
- propagation panel
- band plan reference / operating reminders where legally appropriate

Cheat sheets should be offline-capable and concise:
- standard phonetic alphabet
- common Q-signals used on voice
- common signal-report conventions
- calling CQ sequence
- answering CQ sequence
- contest/pileup basics
- net check-in pattern
- emergency/priority traffic basics (clearly distinguished from normal operation)
- common HF calling frequencies / band-plan links should be region/profile-aware rather than globally hard-coded

## 5. CW workspace + cheat sheets

CW screen should support manual CW operation and logging even before any decoder/keyer features are added.

Cheat/reference panel:
- Morse alphabet/numbers/punctuation
- common prosigns
- Q-codes
- CW abbreviations
- RST explanation
- CQ / answer exchange flow
- common contest exchange patterns
- operating-speed notes

Future-capable architecture:
- CW sidetone/audio decoder can be plugged in later
- keyer/macro integration can be added later if FTX-1 CAT/keying path is proven safe

## 6. Conversational digital UX (JS8 and similar)

JS8 should have a real chat-oriented workspace, not an FT8-style decode table.

Required:
- conversation/thread view
- directed-message state
- inbox / stored messages where protocol supports them
- selected station context
- signal/frequency context
- message history
- canned/quick-select messages similar to the useful qFT8 workflow

Quick-select categories:
- CQ / call station
- signal report / acknowledgement
- grid/location
- "copy" / "stand by" / "repeat" / "thanks" / "73"
- relay/store-and-forward commands where JS8 supports them
- user-editable custom macros

Do not hide the raw protocol text; provide an advanced/raw view.

## 7. Expandable digital-mode family

FieldOps should not be built as a closed list of FT8/FT4/FT2/JS8/WSPR/APRS only.

Create a `ModemPlugin` / `DigitalModeProvider` contract with:
- mode identity + ADIF name
- sample-rate requirements
- streaming vs slotted timing model
- RX decoder
- TX encoder/modulator where supported
- waterfall/bandwidth metadata
- center-frequency / AFC capabilities
- text/conversation capability
- logging metadata
- CAT mode/filter recommendations
- safety and TX timing constraints

### Priority additional modes to research/implement
Broadly useful keyboard/legacy digital modes:
- RTTY ("ritty")
- PSK31
- PSK63 / common PSK variants
- Olivia
- Contestia
- MFSK16 and related common MFSK variants
- DominoEX
- THOR
- Feld Hell / Hellschreiber
- MT63

Potential later/optional modules:
- SSTV (image, not keyboard text)
- packet/AX.25 beyond APRS
- other sound-card modes supported by mature open-source modem cores
- external/proprietary modem integrations only when licensing/API terms allow it (for example VARA-class integrations should not be silently reimplemented)

### Adaptability requirement
The user should be able to encounter an unfamiliar signal, inspect waterfall characteristics, choose from a mode browser, see bandwidth/baud/tone hints, and activate an installed decoder without redesigning the app.

Add a "Mode Library" containing:
- what the mode is
- approximate occupied bandwidth
- typical tones/baud/symbol rate
- common use
- common frequencies/bands (region-aware references, not absolute legal advice)
- whether FieldOps can RX, TX, log, or only identify/reference it

## 8. Waterfall / signal identification support

Because "whatever data signal I find" is a core goal, add mode-discovery aids:
- waterfall with adjustable span/zoom
- cursor bandwidth measurement
- tone spacing measurement where feasible
- baud/symbol-rate estimate hooks
- mode-reference thumbnails/examples generated from known modem properties
- one-tap "try decoder" workflow
- never automatically transmit merely because a mode was identified

## 9. Navigation / information architecture

Suggested primary app areas:
- OPS (current radio/session overview)
- DIGI (mode browser + active digital mode)
- CW
- SSB
- APRS
- MAP
- LOG
- AWARDS
- RADIO

Avoid a bottom bar with nine cramped icons. On phone, use 5-6 primary destinations plus a `MORE`/drawer or context-sensitive mode tabs. The current old-radio/field-map design language can be retained.

## 10. Data model requirements

The unified QSO model must support at least:
- callsign
- station callsign
- operator
- UTC start/end
- band/frequency
- mode/submode
- RST sent/received
- grid/QTH/country/DXCC
- CQ/ITU zones
- name
- power
- notes
- station profile / location
- source (manual, FT decode workflow, JS8, WSPR observation, APRS, import)
- LoTW upload/accept/confirm state
- other confirmation sources later
- award evaluator metadata

A WSPR reception is not automatically a two-way QSO. Keep observations/spots distinct from QSOs.

## 11. Priority order

P0 — do not destabilize:
- common FTX-1 CAT/audio/PTT safety layer
- FT8/FT4/FT2/APRS
- native JS8/WSPR completion
- unified QSO model
- LoTW signing/sync

P1 — one-stop operating essentials:
- SSB/CW manual assisted logger
- automatic LoTW queue for manually logged QSOs
- propagation-focused map layers
- JS8 conversational UI and quick messages
- Awards Center foundation + major award catalog

P2 — broad digital adaptability:
- RTTY + PSK31 first
- Olivia + MFSK16
- plugin/provider API
- mode browser / waterfall identification aids

P3 — breadth and polish:
- Contestia, DominoEX, THOR, Hell, MT63
- extended award catalog
- advanced map award overlays
- optional SSTV/other modem modules
- richer CW tools/decoder/keyer if proven useful and safe

## 12. Non-negotiable product rules

1. No PTT on app launch, reconnect, mode change, decoder selection, or map interaction.
2. All transmitting modes use one radio arbiter / one FTX-1 TX path.
3. Observed/heard stations are not silently promoted to QSOs.
4. A LoTW HTTP success is not equivalent to LoTW acceptance/confirmation.
5. Award progress must distinguish worked from confirmed.
6. Award rules and claim links must be sourced from official program documentation and version/date tracked.
7. Region-sensitive band-plan/frequency references must identify the regulatory/organization source and not be presented as universal.
8. New modem cores require license/provenance review before bundling.
9. The map must remain useful offline for local log/award layers even when live propagation services are unavailable.
10. This document is a locked product requirement source and should be checked before declaring a future FieldOps build feature-complete.
