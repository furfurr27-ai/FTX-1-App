# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0008M-PROPAGATION_OPERATING_PICTURE_READ_MODEL**
Project version: `v38-propagation-operating-picture-read-model`
Phase: **Propagation operating-picture composition host-verified; read-model consistency diagnostics next; hardware/account deferred**
Test status: **GREEN host/CI: operating_picture=PASS source_status=67 state_persistence=PASS runtime=67 concrete_https=70 public_transport=98 refresh=112 projection=79 aggregation=101 pskr=168 glotec=87 noaa=53 propagation=154 production_pack=363 geometry=102 map=96 extended=96 service=64 persistence=92 awards_projection=114 target=77 catalog=115 award=67 queue=47 logger=78 LoTW_transaction=53 core=42062; finalizer run 37819591445**

## What is complete in this checkpoint
- Single snapshot and source-state capture used for both existing read projections at same explicit UTC
- Avoided inconsistent second latest() snapshot reads while retaining original workspace projection and source status semantics
- Composite validates snapshot identity, capture timestamp and future-dated markers across both projections
- Workspace evidence filters do not hide source failure/backoff or last-good provenance
- Runtime operatingPicture API uses already injected stores without refresh, scheduling, fetch or mutation
- Offline synthetic proof of deterministic source/store reads, historical evidence, cache persistence and runtime recreation
- Superseded CP-0008L finalizer changed to manual-only; inherited host/CI matrix green

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return until owner explicitly says 'resume CP-0003C'
- CP-0004A/B/C hardware checkpoints remain incomplete
- Sequential state/snapshot reads are not cross-store atomic; Android UI/lifecycle and phone/radio/RF not verified

## Continue with these exact actions
1. CP-0008N: read-only propagation read-model consistency and timestamp provenance diagnostics
2. Preserve operating-picture, provider/cache/refresh semantics; prove deterministic CI
3. Keep hardware/account and Android work outside; CP-0003C remains DEFERRED

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
