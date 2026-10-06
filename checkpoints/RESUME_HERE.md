# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0007B-AWARD_GEOMETRY_PROVIDERS**
Project version: `v24-award-geometry-providers`
Phase: **Award geometry providers host-verified; production Census U.S. state geometry pack next; hardware/account checkpoints remain deferred by owner**
Test status: **GREEN host/CI: geometry=102 map=96 extended=96 service=64 persistence=92 projection=114 target=77 catalog=115 award=67 queue=47 logger=78 LoTW_transaction=53 core=42062; finalizer run 37498410494**

## What is complete in this checkpoint
- Platform-independent geometry primitives validate coordinates, bounds, rings, polygons, and multi-polygons without Android/map SDK dependencies
- Geometry provenance distinguishes derived standards, external datasets, and synthetic fixtures; all non-derived payloads require explicit rights/license metadata
- Maidenhead four-character bounds are deterministically derived from IARU Region 1 VHF Handbook 9.01 sections 6.2-6.3 using the documented global origin, dimensions, indexing and WGS-84 basis
- U.S. state offline geometry pack contract validates target kind, declared feature count, state identities, unique assets, and offline resolution
- Census production manifest contract pins the 2025 Cartographic Boundary Files source and U.S. Government work/Census attribution metadata while synthetic CI fixtures explicitly contain no Census geometry
- AwardGeometryRegistry rejects provider conflicts/mismatches and bridges separately resolved payloads to CP-0007A metadata-only bindings
- Host tests resolve all 488 FFMA grid identities through deterministic Maidenhead geometry and keep unsupported/unavailable state geometry unbound
- Focused geometry PASS 102; map PASS 96; extended catalog PASS 96; service PASS 64; persistence PASS 92; projection PASS 114; target PASS 77; catalog PASS 115; award PASS 67; queue PASS 47; logger PASS 78; LoTW transaction PASS 53; core PASS 42062

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return to CP-0003C until the owner explicitly says 'resume CP-0003C'
- Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation; continue to the next GitHub/CI-only checkpoint
- CP-0004A/B/C FTX-1 Android hardware checkpoints remain deferred and incomplete
- A production Census state boundary pack is not yet generated; CP-0007B proves only the provider/manifest contract and synthetic fixtures
- Android map rendering, offline tiles, and DXCC/IOTA/POTA/SOTA/CQ/ITU geometry remain later work

## Continue with these exact actions
1. CP-0007C: pin the official Census 2025 national States 1:20,000,000 source artifact and convert exactly the 50 WAS states into a deterministic offline geometry pack
2. Preserve multipart/island topology and explicit Alaska/antimeridian behavior; retain Census attribution/disclaimer metadata and deterministic feature/pack hashes
3. Keep Android rendering out of the pack checkpoint and continue skipping phone/radio/credential/account/RF/manual-hardware checkpoints

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
