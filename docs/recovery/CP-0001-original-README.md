# FTX-1 FieldOps engineering pack

This is a clean-room engineering slice for a **Galaxy S23 Ultra + Yaesu FTX-1 multi-mode digital field terminal** inspired by the strongest behaviors in qFT8, FT8AF and APRSdroid.

It is **not yet an installable APK**. The current execution environment has Kotlin/JVM tooling but not a complete Android SDK/ADB/emulator setup, so claiming a device-tested Android build here would be false.

## Scope
First-class targets:
- FT8
- FT4
- FT2
- JS8 (Slow / Normal / Fast / Turbo)
- WSPR
- APRS (FTX-1 internal APRS control + phone-side AX.25/AFSK)

Compatibility/research targets:
- LQ8 / LQ4, kept disabled until an independently validated implementation is available.

## Implemented and executable in this pack
- FTX-1 CAT command builder/parser
- FTX-1 APRS/PRESET CAT helpers
- generic digital-mode metadata and UTC slot scheduler for FT8/FT4/FT2/JS8/WSPR
- mode-aware radio ownership/interlock state machine
- KISS framing/escaping
- AX.25 APRS UI-frame builder
- APRS uncompressed position encoder
- SmartBeaconing decision engine
- FT8-family automatic TX-audio follow policy with a 300-2700 Hz safe window
- deterministic unit/fuzz harness

Run tests:

```bash
./scripts/test_core.sh
```

See `research/MULTIMODE_PARITY.md` for the qFT8 feature-parity plan and licensing/provenance notes.

The Android integration layer should use Jetpack Compose, Android USB Host APIs, USB audio, NDK/JNI DSP engines, Room, a foreground radio service, and instrumented tests on the actual S23 Ultra + FTX-1.

## Locked one-stop-shop roadmap (2026-10-04)
The owner added substantial product scope that must survive native-mode integration. See:
- `product/ONE_STOP_SHOP_REQUIREMENTS.md`
- `product/FEATURE_BACKLOG.tsv`

These requirements cover universal SSB/CW logging + LoTW, awards progress and claim guidance, propagation-first map layers, JS8 conversational UX, SSB/CW cheat sheets, and an extensible digital modem family including RTTY/PSK/Olivia/MFSK and related modes.

## GitHub reference survey + durable checkpoints (v6)

The design is now informed by a pinned GitHub reference set covering Android FT8/JS8, multi-mode DSP, APRS, rig control, logging, LoTW, awards, maps, propagation, CW and messaging. See:

- `research/github/GITHUB_REFERENCE_SURVEY.md`
- `research/github/SOURCE_PINS.tsv`
- `research/github/ARCHITECTURE_DECISIONS_FROM_SURVEY.md`

FieldOps remains intentionally **FTX-1-only** at the hardware layer. Extensibility is added above the radio driver rather than through a generic multi-radio backend.

Long-running work now uses immutable engineering checkpoints. Before continuing after any interruption, read `checkpoints/RESUME_HERE.md` and verify the snapshot with:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

See `checkpoints/CHECKPOINT_POLICY.md` for cadence and evidence rules.
