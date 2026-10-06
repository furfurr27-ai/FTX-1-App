# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0006B-OFFICIAL_AWARD_CATALOG**
Project version: `v17-official-award-catalog`
Phase: **Official award catalog host-verified; award-target enrichment and composite rules next; hardware/account checkpoints remain deferred by owner**
Test status: **GREEN host/CI: catalog=115 award=67 queue=47 logger=78 LoTW_transaction=53 core=42062; finalizer run 37460474655**

## What is complete in this checkpoint
- Versioned official award source ledger records HTTPS source URLs, retrieval dates and source versions where available
- Initial verified catalog includes DXCC Mixed, WAS, WAC, Triple Play WAS, IOTA 100, POTA Bronze Hunter and SOTA Shack Sloth
- Simple distinct-target catalog entries can create CP-0006A local threshold definitions without sponsor claimability
- Triple Play remains a state-by-mode matrix and IOTA 100 remains count-plus-seven-continent coverage rather than unsafe flattened counts
- POTA automatic issuance and SOTA external point scoring are represented explicitly
- Official AWARDED/CREDITED standing is separate from local progress and requires explicit sponsor evidence
- Focused official-catalog gate PASS 115; award evaluator PASS 67; queue PASS 47; logger PASS 78; LoTW transaction PASS 53; core PASS 42062

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return to CP-0003C until the owner explicitly says 'resume CP-0003C'
- Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation; continue to the next GitHub/CI-only checkpoint
- CP-0004A/B/C FTX-1 Android hardware checkpoints remain deferred and incomplete
- Current QSO records do not contain normalized remote DXCC/state/continent/IOTA/POTA award targets; CP-0006B does not guess them
- Triple Play and IOTA need composite evaluation; SOTA remains external program scoring
- VUCC and CQ WAZ/WPX remain catalog-expansion work until their full verified rule/source shape is encoded

## Continue with these exact actions
1. CP-0006C: add provenance-bearing normalized award-target evidence keyed by immutable QSO id and official date/band/confirmation filters
2. Implement Triple Play state-by-mode and IOTA count-plus-continent composite evaluation without rewriting authoritative QSO records
3. Do not attempt real sponsor-account login or claim submission; continue skipping phone/radio/credential/account/RF/manual-hardware checkpoints

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
