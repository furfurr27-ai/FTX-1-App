# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0006E-AWARD_EVIDENCE_PERSISTENCE**
Project version: `v20-award-evidence-persistence`
Phase: **Award evidence persistence/import host-verified; Awards Center application service next; hardware/account checkpoints remain deferred by owner**
Test status: **GREEN host/CI: persistence=92 projection=114 target=77 catalog=115 award=67 queue=47 logger=78 LoTW_transaction=53 core=42062; finalizer run 37469159046**

## What is complete in this checkpoint
- Award target, confirmation and sponsor-standing evidence share one provider-independent repository boundary without modifying authoritative QSOs
- Batch writes are atomic, idempotent and conflict-safe; single-valued target conflicts fail closed while multi-reference POTA is preserved
- Schema-versioned deterministic snapshot serialization survives repository reconstruction and safely preserves tabs/Unicode/provenance
- Backing-store failure does not advance in-memory state and the complete batch can be retried
- Explicit ADIF DXCC/STATE/CONT/IOTA/POTA metadata creates normalized provenance-bearing targets without callsign/country/grid/notes/MY_* inference
- Only explicit LOTW_QSL_RCVD=Y and QSL_RCVD=Y import confirmation evidence; sent/upload state is never confirmation
- Existing AdifCodec records feed the enrichment adapter and persisted evidence feeds the Awards Center projection without QSO mutation
- Focused persistence/import PASS 92; projection PASS 114; target PASS 77; catalog PASS 115; award PASS 67; queue PASS 47; logger PASS 78; LoTW transaction PASS 53; core PASS 42062

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return to CP-0003C until the owner explicitly says 'resume CP-0003C'
- Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation; continue to the next GitHub/CI-only checkpoint
- CP-0004A/B/C FTX-1 Android hardware checkpoints remain deferred and incomplete
- The persistence boundary is platform independent; a production Room/SQLite adapter and Android persistence behavior are not yet proven
- Live sponsor account synchronization, live LoTW download, claim submission and fuzzy QSO matching remain outside CP-0006E

## Continue with these exact actions
1. CP-0006F: compose logbook, persisted award evidence, ADIF enrichment and Awards Center projection behind one platform-independent application service
2. Require resolved immutable local QSO ids for evidence ingestion and add deterministic provenance-bearing batch orchestration without fuzzy callsign matching
3. Keep real sponsor accounts/claim submission out and continue skipping phone/radio/credential/account/RF/manual-hardware checkpoints

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
