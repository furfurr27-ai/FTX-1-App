# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0008Q-PROPAGATION_OFFLINE_REPORT_DECODE_VALIDATION**
Project version: `v42-propagation-offline-report-decode-validation`
Phase: **Propagation offline report decode/validation CI-verified; import boundary next; hardware deferred**
Test status: **GREEN host/CI: decoder=PASS serialization=PASS inherited=PASS (31 CI test jobs); finalizer run 37911878871**

## What is complete in this checkpoint
- Strict 16MiB/48-level/100000-items bounded offline JSON parser rejects malformed, duplicate, truncated and incompatible V1 data
- Full typed source/evidence projection reconstruction preserves nullable timestamps, enum-keyed maps, nested geographic coverage and canonical JSON
- Source-status readiness, freshness counts, timestamp relations, snapshot and evidence-index consistency are validated
- SHA-256 and exact UTF-8 byte count checked without false authenticity or cross-store atomicity claims
- Synthetic decoder, prior serializer and full inherited core regressions all pass in GitHub CI

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return until owner explicitly says 'resume CP-0003C'
- CP-0004A/B/C hardware checkpoints remain incomplete
- No authenticated report signature, cross-store atomicity, Android device or phone/radio/RF proof

## Continue with these exact actions
1. CP-0008R: GitHub/CI-only propagation offline report import boundary
2. Preserve CP-0008P/Q canonical tests, nested data and source provenance
3. CP-0003C remains DEFERRED; skip any manual account/hardware work

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
