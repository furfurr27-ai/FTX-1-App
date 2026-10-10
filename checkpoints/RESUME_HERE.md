# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0009B-PROPAGATION_OFFLINE_REPORT_ARCHIVE**
Project version: `v53-propagation-offline-report-archive`
Phase: **Host-only bounded archive verified; next archive workspace/Android CI build integration; hardware deferred**
Test status: **GREEN host/CI: offline-archive=PASS workspace-history=PASS inherited=PASS (42 CI test jobs); finalizer run 38075190464**

## What is complete in this checkpoint
- Canonical report receipts revalidated on every archive read/write; deterministic SHA content identities and duplicate handling
- Bounded FIFO retention by entry count and UTF-8 size; deterministic UTC-sorted paginated selection
- Explicit delete, historical selection and CP-0009A paired offline comparison by retained IDs
- Synthetic host tests for byte/count eviction, duplicates, forgery, out-of-bounds paging and original immutability
- Full inherited CP-0009A and CP-0008P/Q/R/S/T/U/V/W/X/Y/Z regression matrix preserved

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return until owner explicitly says 'resume CP-0003C'
- CP-0004A/B/C hardware checkpoints remain incomplete
- No actual Android archive persistence, signed provider origin, live RF, radio/phone or store atomicity proof

## Continue with these exact actions
1. CP-0009C: archive history selection and Android build-readiness integration, GitHub/CI only
2. Preserve canonical archive/import/history contracts and inherited full CI matrix
3. CP-0003C remains DEFERRED; skip manual device, account and RF work

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
## IN-PROGRESS, not durable: CP-0009C

Branch: cp-0009c-archive-workspace-build-readiness. The verified state above remains CP-0009B/v53. CP-0009C introduces pure Kotlin history selection, paging, deletion, canonical paired historical comparison, eviction handling and plain-text accessible warnings; it also adds a truthful static Android packaging gap audit. Do not claim a working Android Activity or APK. Await exact-head PR/branch CI, merge, then immutable checkpoint main finalizer. CP-0003C DEFERRED; CP-0004A/B/C incomplete.
