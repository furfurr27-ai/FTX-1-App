# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0008N-PROPAGATION_READ_MODEL_CONSISTENCY_DIAGNOSTICS**
Project version: `v39-propagation-read-model-consistency-diagnostics`
Phase: **Propagation consistency diagnostics host-verified; offline report payload next; hardware/account deferred**
Test status: **GREEN host/CI: read_model_diagnostics=PASS operating_picture=PASS source_status=67 state_persistence=PASS runtime=67 concrete_https=70 public_transport=98 refresh=112 projection=79 aggregation=101 pskr=168 glotec=87 noaa=53 propagation=154 production_pack=363 geometry=102 map=96 extended=96 service=64 persistence=92 awards_projection=114 target=77 catalog=115 award=67 queue=47 logger=78 LoTW_transaction=53 core=42062; finalizer run 37823404574**

## What is complete in this checkpoint
- Pure source/cache timeline diagnostics derived from already captured operating picture with no additional store/provider reads
- BEFORE EQUAL AFTER UNKNOWN comparisons with signed source-success versus cache and retrieval-versus-success timestamp deltas
- Explicit future-dated source attempts, provider retrievals, cache capture timestamps and nullable future snapshot age
- Preserved source-aware fresh aging stale future observation counts and workspace-filter independence
- Aggregate source timing-skew counts derived consistently and no cross-store transaction guarantee claimed
- Runtime diagnostics overloads tested with file-backed state/snapshot runtime recreation and no network
- Superseded CP-0008M finalizer set manual-only; full inherited CI matrix green

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return until owner explicitly says 'resume CP-0003C'
- CP-0004A/B/C hardware checkpoints remain incomplete
- Cross-store read atomicity unverified; no live provider, Android UI/lifecycle or phone/radio/RF proof

## Continue with these exact actions
1. CP-0008O: deterministic propagation offline diagnostic report payload based on existing read-model output
2. Preserve provider/cache/refresh/assessment semantics; deterministic CI and source provenance
3. Keep hardware/account and Android work outside; CP-0003C remains DEFERRED

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
