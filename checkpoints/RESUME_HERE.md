# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0008L-PROPAGATION_SOURCE_STATUS_PRESENTATION**
Project version: `v37-propagation-source-status-presentation`
Phase: **Propagation source-status presentation host-verified; operating-picture composition next; hardware/account deferred**
Test status: **GREEN host/CI: source_status=PASS state_persistence=PASS runtime=67 concrete_https=70 public_transport=98 refresh=112 projection=79 aggregation=101 pskr=168 glotec=87 noaa=53 propagation=154 production_pack=363 geometry=102 map=96 extended=96 service=64 persistence=92 awards_projection=114 target=77 catalog=115 award=67 queue=47 logger=78 LoTW_transaction=53 core=42062; finalizer run 37815535216**

## What is complete in this checkpoint
- Explicit-UTC read-only source-status service reporting READY, CADENCE_WAIT, RETRY_BACKOFF and FAILURE_COOLDOWN
- Last-attempt outcome, failure details, eligibility timestamps, remaining time and future-dated success exposed independently
- Role-based last-good evidence mapping and per-observation source-aware freshness counts with observed/retrieved UTC provenance
- No evidence invented for successful refreshes or absent snapshots; stable ordering and duplicate source row rejection
- PropagationRuntime.sourceStatus uses existing store injection without altering provider refresh or projection semantics
- Persisted store and snapshot recreation yields deterministic identical status and bit-identical read-only files
- Superseded CP-0008K finalizer set manual-only and full inherited host/CI matrix green

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return until owner explicitly says 'resume CP-0003C'
- CP-0004A/B/C hardware checkpoints remain incomplete
- Android lifecycle/WorkManager, live-provider availability, phone/radio/RF and actual UI not verified

## Continue with these exact actions
1. CP-0008M: read-only propagation operating-picture composition combining existing workspace projection and source-status model
2. Preserve all source/provider, cadence, cache and evidence semantics, prove deterministic CI
3. Keep hardware/account and Android background work outside; CP-0003C remains DEFERRED

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
