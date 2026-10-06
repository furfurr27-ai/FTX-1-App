# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0007A-AWARD_MAP_PROJECTION**
Project version: `v23-award-map-projection`
Phase: **Award-area map projection host-verified; geometry providers/offline pack contract next; hardware/account checkpoints remain deferred by owner**
Test status: **GREEN host/CI: map=96 extended=96 service=64 persistence=92 projection=114 target=77 catalog=115 award=67 queue=47 logger=78 LoTW_transaction=53 core=42062; finalizer run 37495347219**

## What is complete in this checkpoint
- WAS and FFMA finite target universes emit deterministic needed/worked-unconfirmed/confirmed map targets while VUCC never fabricates unknown needed grid identities
- LOCAL_THRESHOLD_MET is an explicit overlay state separate from sponsor claimability/credit
- Map target records retain deterministic QSO ids, confirmation ids, bands, mode groups, exact MODE/SUBMODE, target provenance, and confirmation sources
- AwardsCenterQuery mode/band/date context flows through the map projection and official award band restrictions remain enforced by the evaluator
- Geometry identity/source/version/license metadata is separated from geometry payload using external asset bindings; missing geometry remains unbound rather than synthesized
- AwardsCenterApplicationService builds map layers from the same authoritative logbook and persistent evidence repositories used by Awards Center
- Production map projection never derives state/grid geography from callsigns and unsupported geography types remain unprojected
- Focused map PASS 96; extended catalog PASS 96; service PASS 64; persistence PASS 92; Awards Center projection PASS 114; target PASS 77; catalog PASS 115; award PASS 67; queue PASS 47; logger PASS 78; LoTW transaction PASS 53; core PASS 42062

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return to CP-0003C until the owner explicitly says 'resume CP-0003C'
- Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation; continue to the next GitHub/CI-only checkpoint
- CP-0004A/B/C FTX-1 Android hardware checkpoints remain deferred and incomplete
- Real U.S. state geometry data, Maidenhead geometry payloads, DXCC/IOTA/POTA/SOTA/CQ/ITU geometry, Android map rendering, and offline tiles are not yet implemented
- Local threshold state remains distinct from sponsor claimability, awarded status, and credit

## Continue with these exact actions
1. CP-0007B: define versioned award geometry providers/offline geometry-pack contract and authoritative deterministic Maidenhead four-character cell geometry
2. Define source/version/license-attributed U.S. state geometry-pack adapter with compact CI fixtures before any full offline dataset
3. Keep geometry independent of award rules, do not derive geography from callsigns, and continue skipping phone/radio/credential/account/RF/manual-hardware checkpoints

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
