# Architecture decisions from GitHub reference survey

## ADR-001 — one concrete FTX-1 radio driver
Status: ACCEPTED

FieldOps supports the Yaesu FTX-1 only. Generic multi-radio backend abstraction is intentionally rejected for production runtime.

Implement:
- `Ftx1RadioDriver`
- `Ftx1CatController`
- `Ftx1UsbAudioEngine`
- `Ftx1RadioState`
- `Ftx1TxCoordinator`

Reference lessons: Hamlib, flrig, wfview.

## ADR-002 — only one component may own PTT/audio hardware
Status: ACCEPTED

All modems submit receive requirements and transmit audio/jobs to the common radio service. No modem may independently open USB audio or key CAT PTT.

References: FieldOps existing architecture, JS8 Android TX tap, wfview/Hamlib state patterns.

## ADR-003 — modem extensibility above the radio layer
Status: ACCEPTED

Use a `DigitalModeProvider` contract inspired by fldigi modem inheritance and SDRangel channel/plugin boundaries, simplified for one audio source/radio.

## ADR-004 — local log is source of truth
Status: ACCEPTED

LoTW/QRZ/Clublog/eQSL/activity services maintain provider-specific sync rows keyed to immutable local QSO IDs. Network failure never deletes or rewrites local QSO truth.

References: QLog/KLog/Wavelog patterns.

## ADR-005 — operating sessions are first-class
Status: ACCEPTED

Portable/location/activity context belongs to an `OperatingSession`, not repeated UI fields. Every QSO references station profile and optionally an operating session.

Reference: Ham2K PoLo operations/activity model.

## ADR-006 — awards are rule/evidence engines, not UI queries
Status: ACCEPTED

Awards store exact evidence and confirmation source. `worked`, `confirmed`, `claimable/credited`, and `needed` are separate states.

Reference: Wavelog/KLog award implementations.

## ADR-007 — exact mode and award-mode group both persist
Status: ACCEPTED

A QSO retains exact ADIF mode/submode. Award evaluation may map modes using explicit per-award grouping. Do not use a single generic ADIF parent grouping for every award.

Reference: KLog's handling of USB/LSB/SSB vs distinct digital modes.

## ADR-008 — maps consume normalized geo entities
Status: ACCEPTED

APRS, QSOs, heard stations, award areas and propagation paths normalize to map-domain objects. Renderer/layer code does not query radio/modem databases directly.

References: Xastir and Wavelog map layers.

## ADR-009 — propagation combines prediction and observation
Status: ACCEPTED

Prediction layers (VOACAP/ionospheric models) and observed paths (FieldOps/WSPR/PSK Reporter-style data) are visually and semantically distinct, then optionally fused into a confidence product.

Reference: DVOACAP-style prediction plus FieldOps live spots.

## ADR-010 — offline-first mobile behavior
Status: ACCEPTED

Radio, DSP, logging, cheat sheets, saved award progress and cached maps/reference data operate offline. Network-derived data exposes source and age.

Reference: Look4Sat mobile architecture.

## ADR-011 — conversation state separate from waveform decoder
Status: ACCEPTED

JS8/FreeDATA-style message sessions, inboxes, retries and macros live above the raw modem engine.

References: JS8Call Android, FreeDATA, Pat.

## ADR-012 — checkpoint evidence outranks chat memory
Status: ACCEPTED

Continuation begins from latest verified checkpoint. Progress not present in checkpoint/current files must be re-verified before being claimed.
