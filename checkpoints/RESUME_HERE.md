# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0007C-US_STATE_GEOMETRY_PACK**
Project version: `v25-production-us-state-geometry-pack`
Phase: **Production WAS state geometry pack host-verified; propagation intelligence domain foundation next; hardware/account checkpoints remain deferred by owner**
Test status: **GREEN host/CI: production_pack=363 geometry=102 map=96 extended=96 service=64 persistence=92 projection=114 target=77 catalog=115 award=67 queue=47 logger=78 LoTW_transaction=53 core=42062; finalizer run 37502790657**

## What is complete in this checkpoint
- Official Census artifact cb_2025_us_state_20m.zip is pinned at 158017 bytes with SHA-256 efddd884f1442ef233b1ba9c12dddbd66b6fdf94da6a373e1556aefe3dbc5751
- Builder converts the 52 upstream placemarks into exactly the verified 50 ARRL WAS state identities and excludes non-WAS state/equivalent records
- Production offline geometry asset is 324531 bytes with canonical SHA-256 5feb8c18688936a526523cb536766130be06b14ebfa918b3d99e39bfbcb0a130 plus deterministic per-state feature hashes
- Census multipart/island geometry is preserved, including Alaska 47 polygons and geometry on both longitude signs with maximum adjacent ring longitude jump 0.980542000000014 degrees
- Build fails on unsplit antimeridian ring segments greater than 180 degrees and performs no clipping or implicit dateline wrapping
- CI re-downloads the exact Census artifact, verifies upstream hash/size, regenerates pack/metadata, and byte-compares generated artifacts with committed production files
- Runtime loader verifies raw pack SHA, exact 50-state identity set, feature hashes, polygon counts, sorted order, and canonical full-pack SHA before exposing geometry
- All 50 WAS states bind production Census geometry; all 488 FFMA grids remain bound to deterministic IARU geometry; runtime provider remains offline/platform/account/hardware independent
- Focused production pack PASS 363; geometry provider PASS 102; map PASS 96; extended catalog PASS 96; service PASS 64; persistence PASS 92; projection PASS 114; target PASS 77; catalog PASS 115; award PASS 67; queue PASS 47; logger PASS 78; LoTW transaction PASS 53; core PASS 42062

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return to CP-0003C until the owner explicitly says 'resume CP-0003C'
- Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation; continue to the next GitHub/CI-only checkpoint
- CP-0004A/B/C FTX-1 Android hardware checkpoints remain deferred and incomplete
- Android map rendering, offline basemap tiles, DXCC/CQ/ITU/IOTA/POTA/SOTA geometry, propagation data, and live/heard map layers remain later work

## Continue with these exact actions
1. CP-0008A: define provider-neutral propagation observations/snapshots, provenance/freshness/confidence, solar/geomagnetic and ionospheric product models, and normalized heard/spot path evidence
2. Add explainable band/path usability assessment and offline snapshot/cache interfaces using deterministic synthetic CI fixtures only
3. Keep credentialed live-provider integration and Android map rendering separately gated; continue skipping phone/radio/credential/account/RF/manual-hardware checkpoints

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
