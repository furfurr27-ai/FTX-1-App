# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0008T-PROPAGATION_OFFLINE_COMPARISON_EXPORT_CONTRACT**
Project version: `v45-propagation-offline-comparison-export-contract`
Phase: **Offline comparison export V1 CI-verified; strict decoder next; hardware deferred**
Test status: **GREEN host/CI: export=PASS compare=PASS import=PASS decoder=PASS serialization=PASS inherited=PASS (34 CI test jobs); finalizer run 37961006893**

## What is complete in this checkpoint
- Versioned bounded canonical V1 comparison JSON export with distinct media type and integrity metadata
- Preserves original report receipts, UTC order and source/evidence selected-view diff flags
- Reuses canonical value encoder with unchanged CP-0008P report wire and golden fixtures
- Rejects inconsistent source/evidence counts, flags, ordering and false authenticated/atomic claims
- Pure export plus CP-0008S/R/Q/P and inherited host CI regressions all pass

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return until owner explicitly says 'resume CP-0003C'
- CP-0004A/B/C hardware checkpoints remain incomplete
- No authenticated report signature, cross-store atomicity, Android device or phone/radio/RF proof

## Continue with these exact actions
1. CP-0008U: GitHub/CI-only canonical offline comparison export decoder and validator
2. Preserve CP-0008P/Q/R/S/T canonical serialization, import, comparison and provenance tests
3. CP-0003C remains DEFERRED; skip any manual account/hardware work

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
