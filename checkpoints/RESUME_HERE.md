# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0008S-PROPAGATION_OFFLINE_REPORT_INSPECTION_COMPARISON**
Project version: `v44-propagation-offline-report-inspection-comparison`
Phase: **Offline comparison CI-verified; canonical comparison export next; hardware deferred**
Test status: **GREEN host/CI: compare=PASS import=PASS decoder=PASS serialization=PASS inherited=PASS (33 CI test jobs); finalizer run 37944630941**

## What is complete in this checkpoint
- Compare two fully validated imported canonical V1 reports without live reads, clocks or mutation
- Track sorted selected-view source/evidence changes, including complete projection value drift when index keys match
- Preserve before/after receipt hashes, source provenance and original query timestamp ordering
- Distinguish missing selected-view evidence from claims of source deletion, authenticated origin or live RF worsening
- Focused comparison plus CP-0008R/Q/P and all inherited CI regressions pass

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return until owner explicitly says 'resume CP-0003C'
- CP-0004A/B/C hardware checkpoints remain incomplete
- No authenticated report signature, cross-store atomicity, Android device or phone/radio/RF proof

## Continue with these exact actions
1. CP-0008T: GitHub/CI-only canonical offline comparison export contract
2. Preserve CP-0008P/Q/R/S canonical import, comparison and provenance tests
3. CP-0003C remains DEFERRED; skip any manual account/hardware work

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
