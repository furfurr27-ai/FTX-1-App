# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0008U-PROPAGATION_OFFLINE_COMPARISON_EXPORT_DECODE_VALIDATION**
Project version: `v46-propagation-offline-comparison-export-decode-validation`
Phase: **Offline comparison decode V1 CI-verified; import boundary next; hardware deferred**
Test status: **GREEN host/CI: comparison-decode=PASS comparison-export=PASS inherited=PASS (35 CI test jobs); finalizer run 37965956956**

## What is complete in this checkpoint
- Canonical V1 comparison export strict typed decoder with original receipt, source and evidence changes preserved
- Checks format, media type, wire version, bounded UTF-8 byte count and SHA-256 integrity
- Rejects noncanonical wire, missing or corrupt nested DTOs, classifications and inconsistent metadata
- Preserves CP-0008P/Q/R/S/T interfaces, original report V1 bytes and inherited CI regression matrix
- Host-only pure decoder; no false origin authentication, cross-store atomicity or live RF claims

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return until owner explicitly says 'resume CP-0003C'
- CP-0004A/B/C hardware checkpoints remain incomplete
- No authenticated origin, cross-store atomicity, Android device or phone/radio/RF proof

## Continue with these exact actions
1. CP-0008V: GitHub/CI-only offline comparison import and read-only inspection boundary
2. Preserve CP-0008P/Q/R/S/T/U canonical report, comparison and provenance contracts
3. CP-0003C remains DEFERRED; skip any manual account/hardware work

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
