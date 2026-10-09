# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0008V-PROPAGATION_OFFLINE_COMPARISON_IMPORT_INSPECTION**
Project version: `v47-propagation-offline-comparison-import-inspection`
Phase: **Offline comparison import and historical inspection CI-verified; next software-only checkpoint TBD; hardware deferred**
Test status: **GREEN host/CI: comparison-import=PASS comparison-decode=PASS comparison-export=PASS inherited=PASS (36 CI test jobs); finalizer run 37969324294**

## What is complete in this checkpoint
- Detached CP-0008T/U canonical offline comparison import with explicit unkeyed integrity receipt
- Read-only historical source/evidence selection inspection, by identity, kind and change classification
- Optional comparison recomputation from both original CP-0008P V1 reports detects independently falsified derivative fields
- Rejects invalid media types, versions, byte counts, checksums, noncanonical JSON and corrupt metadata
- CP-0008P/Q/R/S/T/U inherited regression suite green; no live or authenticated RF claim

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return until owner explicitly says 'resume CP-0003C'
- CP-0004A/B/C hardware checkpoints remain incomplete
- No authenticated provenance, cross-store atomicity, Android device or phone/radio/RF proof

## Continue with these exact actions
1. CP-0008W: GitHub/CI-only next software checkpoint; define from current propagation roadmap
2. Preserve CP-0008P/Q/R/S/T/U/V canonical report, comparison and inspection contracts
3. CP-0003C remains DEFERRED; skip manual device, account and RF work

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
