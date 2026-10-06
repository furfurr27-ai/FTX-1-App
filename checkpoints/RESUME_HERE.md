# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0005A-UNIVERSAL_QSO_LOGGER**
Project version: `v14-universal-qso-logger`
Phase: **Universal QSO/logger host-verified; manual-QSO LoTW queue policy next; hardware/account checkpoints remain deferred**
Test status: **GREEN host/CI: logger=78 LoTW_transaction=53 core=42062 pipeline=56 LoTW=19; CP-0003C and CP-0004A/B/C remain hardware/account gated; finalizer run 37444996013**

## What is complete in this checkpoint
- QsoRecord extended as the authoritative universal local QSO model while preserving CP-0003B compatibility
- Exact ADIF mode/submode and exact physical radio mode are preserved independently
- Exact Hz frequency, band, UTC start/end, callsign, reports, station/remote location, station profile and session are preserved
- OperatingSession model added above individual QSOs
- Fast manual SSB/CW logger added using current session and radio context without fabricated RST defaults
- Completed-contact digital auto-log adapter added with explicit provider-supplied mode/submode identity and incomplete-contact rejection
- ADIF export preserves SUBMODE, exact FREQ, UTC end fields, grids and reports
- Focused logger PASS 78; LoTW transaction PASS 53; inherited core PASS 42062, pipeline PASS 56 and LoTW PASS 19

## Known blockers / red items
- CP-0003C real LoTW validation remains hardware/account gated and incomplete
- CP-0004A/B/C FTX-1 Android hardware checkpoints remain incomplete
- Room persistence and Compose logger UI remain later app-shell/UI work
- Automatic manual-QSO LoTW enqueue is intentionally not included until CP-0005B

## Continue with these exact actions
1. CP-0005B: add optional idempotent logger-save -> LotwUploadQueue policy for SSB/CW and eligible digital QSOs without coupling local save to network success

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
