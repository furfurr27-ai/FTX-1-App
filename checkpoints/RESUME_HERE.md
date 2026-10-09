# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0008R-PROPAGATION_OFFLINE_REPORT_IMPORT_BOUNDARY**
Project version: `v43-propagation-offline-report-import-boundary`
Phase: **Offline report import boundary CI-verified; deterministic comparison next; hardware deferred**
Test status: **GREEN host/CI: import=PASS decoder=PASS serialization=PASS inherited=PASS (32 CI test jobs); finalizer run 37915118080**

## What is complete in this checkpoint
- Validation-first offline V1 artifact import with strict typed reconstruction and byte-identical canonical reserialization
- Detached read-only source/selected-evidence lookup without store writes or clock/provider/RF access
- Explicit integrity receipt retains original query/capture times, evidence/source counts and content-type/byte/digest metadata
- Forgery with recomputed SHA-256 remains unauthenticated; corrupt/incompatible reports fail closed
- Synthetic import plus CP-0008Q decoder, CP-0008P serialization and inherited core CI suites all pass

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return until owner explicitly says 'resume CP-0003C'
- CP-0004A/B/C hardware checkpoints remain incomplete
- No authenticated report signature, cross-store atomicity, Android device or phone/radio/RF proof

## Continue with these exact actions
1. CP-0008S: GitHub/CI-only offline imported report inspection and comparison
2. Preserve CP-0008P/Q/R canonical import, nested data and provenance tests
3. CP-0003C remains DEFERRED; skip any manual account/hardware work

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
