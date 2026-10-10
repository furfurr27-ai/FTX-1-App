# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0008W-PROPAGATION_OFFLINE_COMPARISON_PRESENTATION**
Project version: `v48-propagation-offline-comparison-presentation`
Phase: **Offline comparison presentation CI-verified; next filtering/pagination software checkpoint; hardware deferred**
Test status: **GREEN host/CI: comparison-presentation=PASS comparison-import=PASS inherited=PASS (37 CI test jobs); finalizer run 38010726704**

## What is complete in this checkpoint
- Validated historical comparison V1 presentation projection with original receipts and query order
- Sorted source/selected-evidence rows, identity/kind/change filters, separate evidence metadata and projection flags
- Explicit four-category source/evidence change counts and standalone versus optional original-report reconciliation
- Rejects corrupt, incompatible or incomplete artifacts without inventing live provider or RF state
- Preserves CP-0008P/Q/R/S/T/U/V and inherited GitHub/CI regression matrix

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return until owner explicitly says 'resume CP-0003C'
- CP-0004A/B/C hardware checkpoints remain incomplete
- No authenticated provenance, cross-store atomicity, Android device or phone/radio/RF proof

## Continue with these exact actions
1. CP-0008X: GitHub/CI-only bounded historical comparison presentation filtering/pagination
2. Preserve CP-0008P/Q/R/S/T/U/V/W canonical and historical presentation semantics
3. CP-0003C remains DEFERRED; skip manual device, account and RF work

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
