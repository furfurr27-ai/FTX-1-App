# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0006C-AWARD_TARGET_ENRICHMENT**
Project version: `v18-award-target-enrichment`
Phase: **Award target enrichment/composite rules host-verified; Awards Center projection next; hardware/account checkpoints remain deferred by owner**
Test status: **GREEN host/CI: target=77 catalog=115 award=67 queue=47 logger=78 LoTW_transaction=53 core=42062; finalizer run 37463697312**

## What is complete in this checkpoint
- Award target evidence is external to QsoRecord, keyed by immutable QSO id, and requires source id/version provenance
- Normalized direct targets cover DXCC entity, U.S. state, continent, IOTA group and POTA reference without callsign/free-text guessing
- Conflicting single-valued target evidence fails closed while multiple POTA references per QSO are preserved
- Official not-before date, excluded-band, band/mode view and confirmation-source rules apply before contribution
- DXCC/WAS/WAC/POTA consume normalized target evidence without rewriting authoritative QSOs
- Triple Play evaluates the full 150-cell state-by-mode matrix with LoTW-only confirmed cells
- IOTA 100 requires 100 confirmed groups plus all seven required continents
- SOTA point scoring remains external and local evaluation never emits sponsor claimability/awarded/credited state
- Focused target/composite PASS 77; catalog PASS 115; award PASS 67; queue PASS 47; logger PASS 78; LoTW transaction PASS 53; core PASS 42062

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return to CP-0003C until the owner explicitly says 'resume CP-0003C'
- Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation; continue to the next GitHub/CI-only checkpoint
- CP-0004A/B/C FTX-1 Android hardware checkpoints remain deferred and incomplete
- Real enrichment dataset/import adapters are not yet implemented; CP-0006C proves the evidence/evaluation boundary with explicit fixtures
- WAS/Triple Play same-location sponsor rules are retained in catalog but not proven by CP-0006C local evaluation
- VUCC, CQ WAZ/WPX, additional awards, SOTA scoring integration and sponsor account sync remain later work

## Continue with these exact actions
1. CP-0006D: build a UI-independent Awards Center projection with worked/confirmed/remaining/progress and official-source/claim metadata
2. Add explicit Mixed/CW/Phone/Digital plus band/date query views while keeping award rules in catalog/evaluator layers
3. Keep sponsor standing/external scoring separate and continue skipping phone/radio/credential/account/RF/manual-hardware checkpoints

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
