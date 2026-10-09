# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0008P-PROPAGATION_OFFLINE_REPORT_SERIALIZATION_CONTRACT**
Project version: `v41-propagation-offline-report-serialization`
Phase: **Propagation offline serialization contract host-verified; strict decode validation next; hardware/account deferred**
Test status: **GREEN host/CI: serialization=PASS offline_report=PASS read_model_diagnostics=PASS operating_picture=PASS source_status=67 state_persistence=PASS runtime=67 concrete_https=70 public_transport=98 refresh=112 projection=79 aggregation=101 pskr=168 glotec=87 noaa=53 propagation=154 production_pack=363 geometry=102 map=96 extended=96 service=64 persistence=92 awards_projection=114 target=77 catalog=115 award=67 queue=47 logger=78 LoTW_transaction=53 core=42062; finalizer run 37882589879**

## What is complete in this checkpoint
- Versioned canonical JSON UTF-8 serializer exports every nested CP-0008O diagnostic report field without new store reads
- Deterministic root/field/map/set ordering, original ordered lists, exact nullable UTC millisecond timestamps and enum provenance
- Unicode/JSON escaping, nonfinite number and unpaired surrogate rejection, bounded output/depth/collections
- SHA-256 checksum and byte-count metadata with deterministic re-serialization integrity verification, no claim of authenticity
- Source summary/time and evidence-index checks reject corrupted output or fabricated atomic snapshot consistency
- Runtime in-memory serialization overloads verified against deterministic file-backed restart with no network or persisted mutation
- Superseded CP-0008O finalizer manual-only; focused plus full inherited CI successful

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C is DEFERRED; do not return until owner explicitly says 'resume CP-0003C'
- CP-0004A/B/C hardware checkpoints remain incomplete
- JSON deserialization not built; cross-store atomicity not verified; no Android UI/lifecycle or phone/radio/RF proof

## Continue with these exact actions
1. CP-0008Q: strict bounded offline report JSON decode and validation
2. Preserve complete source/evidence/clock fidelity and repeat full inherited deterministic CI
3. Keep account and hardware work outside; CP-0003C remains DEFERRED

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.

## In-flight CP-0008Q handoff (not verified)

PR: https://github.com/furfurr27-ai/FTX-1-App/pull/40
Branch: `cp-0008q-propagation-offline-report-decode-validation`
Parent: `CP-0008P-PROPAGATION_OFFLINE_REPORT_SERIALIZATION_CONTRACT`, main `52453fad9e4c18504394f469fcb8302b5b8f8a49`.

Decoder/tests/scripts/evidence note and full inherited + main finalizer workflows are staged. GitHub Actions jobs were queued at last inspection; do not infer passing tests from successful commits. Next: inspect exact PR head, run status, focused test and all inherited results; fix failures on this branch. Merge PR only after exact-tip green CI. Then verify the automatic main finalizer, `checkpoints/LATEST.json`, manifest file hashes, snapshot SHA-256 and resulting main commit. Until verification CP-0008P is latest durable state. CP-0003C is DEFERRED; CP-0004A/B/C incomplete.
