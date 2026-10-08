# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0008H-PUBLIC_PROPAGATION_TRANSPORT_ADAPTERS**
Project version: `v33-public-propagation-transport-adapters`
Phase: **Public propagation transport adapters host-verified; concrete HTTPS transport next; hardware/account checkpoints remain deferred by owner**
Test status: **GREEN host/CI: public_transport=98 refresh=112 projection=79 aggregation=101 pskr=168 glotec=87 noaa=53 propagation=154 production_pack=363 geometry=102 map=96 extended=96 service=64 persistence=92 awards_projection=114 target=77 catalog=115 award=67 queue=47 logger=78 LoTW_transaction=53 core=42062; finalizer run 37786722486**

## What is complete in this checkpoint
- PublicPropagationTransport defines a platform-neutral bounded HTTPS GET request/response contract without a concrete client
- Response validation rejects request-provenance mismatch, unexpected redirects, non-200 status, oversized bodies and unexpected media types
- Retry classification distinguishes transient HTTP/transport failures from parser/schema/provenance contract failures
- NOAA observed Kp, Kp forecast and F10.7 source factories reuse exact official endpoints and verified CP-0008B parsers
- GloTEC two-stage index/artifact adapter uses valid-JSON canonical official artifact selection and enforces a ten-minute minimum cadence
- PSK Reporter deterministic queries support sender/receiver/either callsign selection, bounded lookback/report count, optional mode/frequency range, and never add appcontact/callback
- PSK Reporter transport cadence enforces the documented five-minute minimum retrieval guidance
- Deterministic fake-transport integration feeds all five public source definitions into CP-0008G while preserving canonical observed-Kp selection
- Required CI has no external-provider dependency and production CP-0008H code has no Android/WorkManager/concrete HTTP client, credentials/accounts, FTX-1/PTT/RF, QSO/logbook-write or LoTW behavior
- Superseded CP-0008G finalizer is manual-only
- Focused public transport PASS 98; refresh PASS 112; projection PASS 79; aggregation PASS 101; PSK Reporter PASS 168; GloTEC PASS 87; NOAA PASS 53; propagation PASS 154; production state pack PASS 363; geometry PASS 102; map PASS 96; extended PASS 96; service PASS 64; persistence PASS 92; awards projection PASS 114; target PASS 77; catalog PASS 115; award PASS 67; queue PASS 47; logger PASS 78; LoTW transaction PASS 53; core PASS 42062

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return until owner explicitly says 'resume CP-0003C'
- CP-0004A/B/C hardware checkpoints remain incomplete
- Concrete HTTPS transport, Android background scheduling, WSPRnet/WSPR.live, GIRO and HFcast/VOACAP remain unproven

## Continue with these exact actions
1. CP-0008I: implement a concrete JVM/Android-compatible HTTPS GET transport behind PublicPropagationTransport
2. Enforce timeouts, bounded streaming reads, redirect refusal, UTF-8 decoding, exact response metadata and safe cleanup with deterministic CI
3. Keep WorkManager/background scheduling, credentials/accounts and new providers outside CP-0008I

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
