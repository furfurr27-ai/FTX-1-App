# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0008F-PROPAGATION_OPERATING_PICTURE_PROJECTION**
Project version: `v31-propagation-projection-service`
Phase: **Propagation workspace projection host-verified; source refresh coordinator next; hardware/account checkpoints remain deferred by owner**
Test status: **GREEN host/CI: projection=79 aggregation=101 pskr=168 glotec=87 noaa=53 propagation=154 production_pack=363 geometry=102 map=96 extended=96 service=64 persistence=92 awards_projection=114 target=77 catalog=115 award=67 queue=47 logger=78 LoTW_transaction=53 core=42062; finalizer run 37658173873**

## What is complete in this checkpoint
- Heard, ionospheric, solar/geomagnetic and modeled evidence project into distinct deterministic workspace records
- Heard projection retains explicit endpoint geography, callsigns, frequency, band, mode, SNR, reportCount, provenance, confidence, quality and source-specific freshness
- Ionospheric projection preserves metric/sample identity and does not convert VTEC/TECU into MUF or an opaque heat score
- Band/mode/frequency filters affect heard records only; source/freshness filters apply across evidence categories
- Selected-path assessment reuses PropagationAssessmentEngine against the complete snapshot and retains reason/evidence links independent of display filters
- Projection status exposes snapshot age, source retrieval bounds, stale/future evidence state and offline-cache availability
- Future snapshot/retrieval timestamps remain explicit with null age rather than fabricated negative age
- FilePropagationSnapshotStore implements OfflinePropagationSnapshotStore without coupling projection service to its concrete class
- Projection code contains no Android/map-SDK, live HTTP/provider polling, FTX-1/PTT, QSO/logbook-write, LoTW or heat-score state
- Superseded CP-0008E finalizer is manual-only
- Focused projection PASS 79; aggregation PASS 101; PSK Reporter PASS 168; GloTEC PASS 87; NOAA PASS 53; propagation PASS 154; production state pack PASS 363; geometry PASS 102; map PASS 96; extended PASS 96; service PASS 64; persistence PASS 92; awards projection PASS 114; target PASS 77; catalog PASS 115; award PASS 67; queue PASS 47; logger PASS 78; LoTW transaction PASS 53; core PASS 42062

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return until owner explicitly says 'resume CP-0003C'
- CP-0004A/B/C hardware checkpoints remain incomplete
- Android UI/map rendering, concrete network scheduling, WSPRnet/WSPR.live, GIRO and HFcast/VOACAP remain unproven

## Continue with these exact actions
1. CP-0008G: build platform-neutral propagation source refresh coordinator over normalized adapters, aggregation/cache and projection
2. Track source cadence, success/failure, bounded retry/backoff, canonical NOAA Kp source selection, partial failure and last-good cached snapshots
3. Use deterministic fake-source CI; keep Android scheduling/network transport and new providers outside CP-0008G

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
