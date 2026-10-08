# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0008K-PROPAGATION_REFRESH_STATE_PERSISTENCE**
Project version: `v36-propagation-refresh-state-persistence`
Phase: **Refresh-state persistence host-verified; source-status presentation next; hardware/account checkpoints deferred**
Test status: **GREEN host/CI: state_persistence=PASS runtime=67 concrete_https=70 public_transport=98 refresh=112 projection=79 aggregation=101 pskr=168 glotec=87 noaa=53 propagation=154 production_pack=363 geometry=102 map=96 extended=96 service=64 persistence=92 awards_projection=114 target=77 catalog=115 award=67 queue=47 logger=78 LoTW_transaction=53 core=42062; finalizer run 37811486748**

## What is complete in this checkpoint
- Atomic file-backed refresh-state store with explicit PropagationRuntimeFactory injection and unchanged in-memory default
- Versioned deterministic strict binary codec with role/source and duplicate validation
- All source role, UTC attempt/success, consecutive failure, next eligible, message and retryability state survive recreation
- Corrupt/truncated/unknown-version/duplicate/mismatched persisted input fails closed
- Failed write leaves last valid file bytes and live state unchanged; no non-atomic fallback
- Deterministic offline fake transport proves 4/5/10 minute cadence and failure/backoff survive restart
- CP-0008J finalizer changed to manual-only
- All inherited host/CI regression gates green

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return until owner explicitly says 'resume CP-0003C'
- CP-0004A/B/C hardware checkpoints remain incomplete
- No Android lifecycle/WorkManager, power-loss fsync durability, multiprocess locking or phone/radio/RF validation

## Continue with these exact actions
1. CP-0008L: add read-only platform-neutral propagation source-status presentation model using existing persisted refresh state
2. Preserve cadence, parser, snapshot/evidence and projection semantics; require deterministic CI
3. Keep all hardware/account and Android background work outside; CP-0003C remains DEFERRED

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
