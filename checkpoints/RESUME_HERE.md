# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0008I-CONCRETE_PUBLIC_HTTPS_TRANSPORT**
Project version: `v34-concrete-public-https-transport`
Phase: **Concrete public HTTPS transport host-verified; propagation runtime composition next; hardware/account checkpoints remain deferred by owner**
Test status: **GREEN host/CI: concrete_https=70 public_transport=98 refresh=112 projection=79 aggregation=101 pskr=168 glotec=87 noaa=53 propagation=154 production_pack=363 geometry=102 map=96 extended=96 service=64 persistence=92 awards_projection=114 target=77 catalog=115 award=67 queue=47 logger=78 LoTW_transaction=53 core=42062; finalizer run 37793790597**

## What is complete in this checkpoint
- HttpsUrlConnectionPublicPropagationTransport provides a concrete JVM/Android-compatible GET implementation behind PublicPropagationTransport
- HTTPS parsing rejects missing host, URL user-info credentials and fragments before connection creation
- GET, redirect refusal, bounded connect/read timeouts, cache/input/output settings and deterministic Accept/UTF-8/identity/User-Agent headers are configured
- Declared and streamed response byte limits enforce request.maxResponseBytes without unbounded buffering
- Non-identity Content-Encoding and malformed/unmappable UTF-8 fail closed as non-retryable contract failures
- HTTP error streams preserve status/body; timeout/I/O/open failures are retryable while contract failures remain non-retryable
- PublicPropagationSourceAdapters preserves explicit PublicPropagationTransportException retryability
- Streams close and every opened connection disconnects across success and failure paths
- Concrete transport integration feeds the verified NOAA F10.7 parser without parser-policy changes
- Required CI has no external-provider dependency and production CP-0008I code has no Android framework/WorkManager/Compose/OkHttp/Retrofit, credentials/accounts, FTX-1/PTT/RF, QSO/logbook-write or LoTW behavior
- Superseded CP-0008H finalizer is manual-only
- Focused concrete HTTPS PASS 70; public transport PASS 98; refresh PASS 112; projection PASS 79; aggregation PASS 101; PSK Reporter PASS 168; GloTEC PASS 87; NOAA PASS 53; propagation PASS 154; production state pack PASS 363; geometry PASS 102; map PASS 96; extended PASS 96; service PASS 64; persistence PASS 92; awards projection PASS 114; target PASS 77; catalog PASS 115; award PASS 67; queue PASS 47; logger PASS 78; LoTW transaction PASS 53; core PASS 42062

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return until owner explicitly says 'resume CP-0003C'
- CP-0004A/B/C hardware checkpoints remain incomplete
- Android app lifecycle/network permission/background scheduling, live provider uptime, WSPRnet/WSPR.live, GIRO and HFcast/VOACAP remain unproven

## Continue with these exact actions
1. CP-0008J: compose concrete HTTPS transport, all five public sources, refresh/cache state and workspace projection into one platform-neutral runtime
2. Make callsign/query and cadence configuration explicit with safe provider-minimum defaults and deterministic injected transport/storage CI
3. Keep WorkManager/background scheduling, Android lifecycle/network permission, credentials/accounts and new providers outside CP-0008J

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
