# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0008J-PROPAGATION_RUNTIME_COMPOSITION**
Project version: `v35-propagation-runtime-composition`
Phase: **Propagation runtime composition host-verified; refresh-state persistence next; hardware/account checkpoints remain deferred by owner**
Test status: **GREEN host/CI: runtime=67 concrete_https=70 public_transport=98 refresh=112 projection=79 aggregation=101 pskr=168 glotec=87 noaa=53 propagation=154 production_pack=363 geometry=102 map=96 extended=96 service=64 persistence=92 awards_projection=114 target=77 catalog=115 award=67 queue=47 logger=78 LoTW_transaction=53 core=42062; finalizer run 37808105308**

## What is complete in this checkpoint
- PropagationRuntimeConfig requires explicit operator callsign and carries explicit PSK Reporter query, refresh policies and HTTPS transport configuration
- Safe default cadences are observed Kp 15m, Kp forecast 15m, F10.7 60m, GloTEC 10m and PSK Reporter 5m with provider-minimum validation
- PropagationRuntimeFactory composes concrete HTTPS transport by default, all five verified public source definitions, snapshot/state stores, refresh coordinator and projection service
- Transport, snapshot store, refresh-state store, aggregator and assessment engine remain injectable
- PropagationRuntime.refreshAndProject is a manual explicit-UTC entry point with no internal wall-clock read
- Deterministic full-source integration saves and projects canonical NOAA Kp/F10.7, GloTEC and PSK Reporter evidence
- Cadence behavior refreshes no source at four minutes, only PSK Reporter at five minutes, and GloTEC plus PSK Reporter at ten minutes
- Eligible-source failure preserves the last good projection and exposes source failure state without writing a new snapshot
- Runtime configuration contains no inferred station geography and production composition contains no Android lifecycle/WorkManager, credentials/accounts, FTX-1/PTT/RF, QSO/logbook-write or LoTW behavior
- Superseded CP-0008I finalizer is manual-only
- Focused runtime PASS 67; concrete HTTPS PASS 70; public transport PASS 98; refresh PASS 112; projection PASS 79; aggregation PASS 101; PSK Reporter PASS 168; GloTEC PASS 87; NOAA PASS 53; propagation PASS 154; production state pack PASS 363; geometry PASS 102; map PASS 96; extended PASS 96; service PASS 64; persistence PASS 92; awards projection PASS 114; target PASS 77; catalog PASS 115; award PASS 67; queue PASS 47; logger PASS 78; LoTW transaction PASS 53; core PASS 42062

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return until owner explicitly says 'resume CP-0003C'
- CP-0004A/B/C hardware checkpoints remain incomplete
- Process-restart persistence for default refresh state, Android background scheduling/lifecycle/network permission, WSPRnet/WSPR.live, GIRO and HFcast/VOACAP remain unproven

## Continue with these exact actions
1. CP-0008K: add platform-neutral file-backed PropagationRefreshStateStore with versioned deterministic serialization and atomic replace semantics
2. Prove restart, corrupt/duplicate/unknown-version input, role mismatch and failed-write preservation with deterministic filesystem CI
3. Keep WorkManager/background scheduling, Android lifecycle/network permission, credentials/accounts and new providers outside CP-0008K

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
