# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0008D-PSK_REPORTER_HEARD_PATH_ADAPTER**
Project version: `v29-psk-reporter-heard-path-adapter`
Phase: **PSK Reporter heard-path adapter host-verified; propagation aggregation/offline cache next; hardware/account checkpoints remain deferred by owner**
Test status: **GREEN host/CI: pskr=168 glotec=87 noaa=53 propagation=154 production_pack=363 geometry=102 map=96 extended=96 service=64 persistence=92 projection=114 target=77 catalog=115 award=67 queue=47 logger=78 LoTW_transaction=53 core=42062; finalizer run 37639835383**

## What is complete in this checkpoint
- Official PSK Reporter public query, XML field semantics and five-minute retrieval guidance are source-pinned
- Bounded four-report fixture is 1196 bytes with SHA-256 fb41c07330c8d446dbd52eb4b35358950145b8a75fab76f225e69859b5752da7
- Accepted paths require explicit valid sender and receiver Maidenhead locators; no callsign-derived geography is used
- Frequency Hz, amateur band, Unix observation time, mode and optional integer SNR are retained with provider/retrieval provenance
- Missing or invalid report facts are explicitly rejected per report without discarding other valid rows
- Present QSO/manual/test informationSource values are rejected from direct heard-path normalization
- Exact duplicate normalized rows collapse deterministically and increment reportCount
- XML parser disables DOCTYPE, external entities, external DTD/schema access and XInclude
- Core PSK Reporter adapter performs no network access and contains no Android/map SDK, FTX-1/PTT, QSO-write or LoTW behavior
- Superseded CP-0008C finalizer is manual-only to prevent checkpoint-state regression
- Focused PSK Reporter PASS 168; GloTEC PASS 87; NOAA adapter PASS 53; propagation PASS 154; production state pack PASS 363; geometry PASS 102; map PASS 96; extended PASS 96; service PASS 64; persistence PASS 92; projection PASS 114; target PASS 77; catalog PASS 115; award PASS 67; queue PASS 47; logger PASS 78; LoTW transaction PASS 53; core PASS 42062

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return to CP-0003C until the owner explicitly says 'resume CP-0003C'
- Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation; continue to the next GitHub/CI-only checkpoint
- CP-0004A/B/C FTX-1 Android hardware checkpoints remain deferred and incomplete
- PSK Reporter uptime/current station activity is external; deterministic fixture proves pinned parsing/normalization only
- Reception reports are provisional one-way evidence and do not prove QSO completion, callsign truth, or locator truth
- WSPRnet/GIRO/HFcast/VOACAP and Android live-network/map integrations remain unproven

## Continue with these exact actions
1. CP-0008E: aggregate already-normalized NOAA, GloTEC and PSK Reporter evidence into deterministic PropagationSnapshot instances
2. Define source-specific freshness defaults, repeated-payload deduplication and bounded offline PropagationSnapshotStore persistence/reload
3. Keep observed, ionospheric and modeled evidence categories separate and do not create a path score merely by aggregation

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
