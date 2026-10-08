# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0008O-PROPAGATION_OFFLINE_DIAGNOSTIC_REPORT_PAYLOAD**
Project version: `v40-propagation-offline-diagnostic-report`
Phase: **Propagation offline report payload host-verified; serialization contract next; hardware/account deferred**
Test status: **GREEN host/CI: offline_report=PASS read_model_diagnostics=PASS operating_picture=PASS source_status=67 state_persistence=PASS runtime=67 concrete_https=70 public_transport=98 refresh=112 projection=79 aggregation=101 pskr=168 glotec=87 noaa=53 propagation=154 production_pack=363 geometry=102 map=96 extended=96 service=64 persistence=92 awards_projection=114 target=77 catalog=115 award=67 queue=47 logger=78 LoTW_transaction=53 core=42062; finalizer run 37827542903**

## What is complete in this checkpoint
- Versioned platform-neutral offline diagnostic report DTO built entirely from CP-0008N already captured read model
- Complete original source status/timing diagnostics retained, stable source ordering and null/unknown snapshot provenance preserved
- Complete original selected workspace projections, selected-path assessment and visible evidence attribution/index preserved
- Source-attributed cached evidence distinct from workspace-filtered visible evidence with explicit source and UTC provenance
- Report input/schema/identity/count invariants reject fabricated evidence and cross-store atomicity assertions
- Runtime offlineDiagnosticReport overloads tested against file-backed runtime recreation, no network and bit-identical state/cache
- Superseded CP-0008N finalizer set manual-only; focused and inherited host/CI green

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return until owner explicitly says 'resume CP-0003C'
- CP-0004A/B/C hardware checkpoints remain incomplete
- Cross-store atomicity unverified; no live provider, offline serialization/export format, Android UI/lifecycle or phone/radio/RF proof

## Continue with these exact actions
1. CP-0008P: deterministic versioned serialization contract for offline propagation report DTO
2. Preserve source/cache/evidence fidelity, unknown and future metadata and full inherited CI
3. Keep hardware/account and Android work outside; CP-0003C remains DEFERRED

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
