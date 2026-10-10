# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0008X-PROPAGATION_OFFLINE_COMPARISON_PAGINATION**
Project version: `v49-propagation-offline-comparison-pagination`
Phase: **Offline comparison paging/filtering CI-verified; next software-only checkpoint; hardware deferred**
Test status: **GREEN host/CI: comparison-pagination=PASS comparison-presentation=PASS inherited=PASS (38 CI test jobs); finalizer run 38016346728**

## What is complete in this checkpoint
- Strict 1..100 bounded independent historical source and selected-evidence paging with Long offsets
- Deterministic stable sorting, continuation, exact-case prefix, change and kind filters
- Original canonical comparison and report receipts, global tallies and query order preserved separately from filtered counts
- Explicit optional original-report reconciliation; malformed and incompatible payloads rejected
- CP-0008P/Q/R/S/T/U/V/W contracts and complete inherited GitHub CI regression matrix preserved

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return until owner explicitly says 'resume CP-0003C'
- CP-0004A/B/C hardware checkpoints remain incomplete
- No authenticated provider origin, cross-store atomicity, Android device or phone/radio/RF proof

## Continue with these exact actions
1. CP-0008Y: GitHub/CI-only offline comparison paged display integration contract
2. Preserve CP-0008P/Q/R/S/T/U/V/W/X canonical and historical selected-view semantics
3. CP-0003C remains DEFERRED; skip manual device, account and RF work

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
