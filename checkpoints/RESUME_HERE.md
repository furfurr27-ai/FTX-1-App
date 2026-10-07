# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0008E-PROPAGATION_AGGREGATION_OFFLINE_CACHE**
Project version: `v30-propagation-aggregation-offline-cache`
Phase: **Propagation aggregation/offline cache host-verified; operating-picture projection next; hardware/account checkpoints remain deferred by owner**
Test status: **GREEN host/CI: aggregation=101 pskr=168 glotec=87 noaa=53 propagation=154 production_pack=363 geometry=102 map=96 extended=96 service=64 persistence=92 projection=114 target=77 catalog=115 award=67 queue=47 logger=78 LoTW_transaction=53 core=42062; finalizer run 37653853853**

## What is complete in this checkpoint
- Already-normalized NOAA, GloTEC, PSK Reporter and modeled evidence aggregate into deterministic PropagationSnapshot instances
- Provider observation/retrieval timestamps are preserved and capture UTC cannot precede retained source retrieval UTC
- Repeated same-source payloads deduplicate by evidence id with latest retrieval provenance; heard reportCount merges by max rather than addition
- Materially conflicting same-id content/provenance and cross-category id collisions fail closed
- Overlapping NOAA observed-Kp identity across dedicated/forecast products is treated as provenance conflict; orchestration uses dedicated observed plus forecast estimated/predicted records
- Source-specific FieldOps freshness defaults cover PSK Reporter, GloTEC, NOAA Kp family and F10.7 while unknown sources retain the operational default
- Snapshot ids deterministically fingerprint complete versioned normalized content independent of input-batch ordering
- FilePropagationSnapshotStore implements bounded versioned offline latest/exact/as-of/history persistence with restart/reload and deterministic trimming
- Binary codec preserves all current propagation domain fields/coverage shapes and rejects corrupt versions, oversized collections/strings and trailing bytes
- Context-only aggregation creates no heard/model path or opaque propagation score; QSO/LoTW and Android/map/network/radio state remain outside the layer
- Superseded CP-0008D finalizer is manual-only to prevent checkpoint-state regression
- Focused aggregation PASS 101; PSK Reporter PASS 168; GloTEC PASS 87; NOAA adapter PASS 53; propagation PASS 154; production state pack PASS 363; geometry PASS 102; map PASS 96; extended PASS 96; service PASS 64; persistence PASS 92; projection PASS 114; target PASS 77; catalog PASS 115; award PASS 67; queue PASS 47; logger PASS 78; LoTW transaction PASS 53; core PASS 42062

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return to CP-0003C until the owner explicitly says 'resume CP-0003C'
- Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation; continue to the next GitHub/CI-only checkpoint
- CP-0004A/B/C FTX-1 Android hardware checkpoints remain deferred and incomplete
- CP-0008E does not implement live provider polling, Android filesystem integration or multi-process cache locking
- WSPRnet/WSPR.live, GIRO, HFcast/VOACAP and Android map rendering remain unproven

## Continue with these exact actions
1. CP-0008F: build a platform-independent propagation operating-picture projection/application service over PropagationSnapshot and PropagationSnapshotStore
2. Project heard, ionospheric, solar/geomagnetic and modeled evidence with provenance/freshness plus deterministic band/mode/source/freshness filters
3. Expose optional explainable selected-path assessment and offline snapshot status without Android/map-SDK rendering or new live providers

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
