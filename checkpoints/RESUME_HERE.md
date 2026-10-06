# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0006D-AWARDS_CENTER_PROJECTION**
Project version: `v19-awards-center-projection`
Phase: **Awards Center projection host-verified; durable award evidence persistence/import next; hardware/account checkpoints remain deferred by owner**
Test status: **GREEN host/CI: projection=114 target=77 catalog=115 award=67 queue=47 logger=78 LoTW_transaction=53 core=42062; finalizer run 37466410722**

## What is complete in this checkpoint
- One projection card per official catalog entry preserves official names, issuer, descriptions, links, claim instructions, source dates/versions and catalog conditions
- Mixed/CW/Phone/Digital plus band and inclusive UTC date-range query views are supported without rewriting authoritative QSOs
- Distinct-target, mode-cell and count-plus-coverage progress metrics are deterministic FieldOps display metrics and not sponsor percentages
- Triple Play filtered mode-leg display completion stays separate from full official award threshold completion
- IOTA progress retains both confirmed-group and seven-continent criteria
- External SOTA scoring returns no fabricated numeric progress
- Sponsor standing/claimable-now is explicit and never inferred from local threshold; conflicting latest sponsor evidence fails closed
- Projection contains no hard-coded award ids and no credentials/network/hardware/Compose/Room dependencies
- Focused projection PASS 114; target PASS 77; catalog PASS 115; award PASS 67; queue PASS 47; logger PASS 78; LoTW transaction PASS 53; core PASS 42062

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return to CP-0003C until the owner explicitly says 'resume CP-0003C'
- Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation; continue to the next GitHub/CI-only checkpoint
- CP-0004A/B/C FTX-1 Android hardware checkpoints remain deferred and incomplete
- Award target/confirmation/sponsor evidence is not yet durably persisted and current projection tests use explicit synthetic fixtures
- Real enrichment imports/datasets, sponsor account sync, claim submission, Compose/Room integration and map award overlays remain later work
- WAS/Triple Play same-location sponsor checks and external SOTA scoring remain outside locally proven evaluation

## Continue with these exact actions
1. CP-0006E: add durable provider-independent award evidence storage and explicit ADIF enrichment import for present DXCC/STATE/CONT/IOTA/POTA fields
2. Preserve source provenance/version and conservatively import explicit confirmation metadata without callsign/free-text inference
3. Keep sponsor account synchronization/claim submission out and continue skipping phone/radio/credential/account/RF/manual-hardware checkpoints

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
