# SOFTWARE TRACK — FTX-1 FieldOps

This file exists to prevent hardware/account checkpoints from stalling unrelated software work or causing a new chat to redo already-started work.

## Latest verified durable parent

`CP-0008E-PROPAGATION_AGGREGATION_OFFLINE_CACHE`

## Deferred but incomplete hardware/account work

| Checkpoint | Status | Durable handling |
| --- | --- | --- |
| CP-0003C Real LoTW validation | BLOCKED / incomplete | WIP preserved on `cp-0003c-real-lotw-validation`; requires real device/account/certificate validation |
| CP-0004A FTX-1 CAT USB port | RED / not started as verified checkpoint | requires S23 + FTX-1 USB topology proof |
| CP-0004B FTX-1 USB audio | RED / not started as verified checkpoint | requires real Android USB audio endpoint/rate enumeration |
| CP-0004C S23 + FTX-1 hardware proof | RED / not started | requires actual phone/radio/RF test |

These checkpoints are not skipped in the sense of being complete. They are deferred and must return later.

## Owner execution override

CP-0003C is **DEFERRED**. Do not return to CP-0003C until the owner explicitly says `resume CP-0003C`.

Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue with the next checkpoint that can be completed entirely through GitHub/CI. Deferred checkpoints remain incomplete.

## Active software checkpoint

CP-0008F-PROPAGATION_OPERATING_PICTURE_PROJECTION

Required scope:

- platform-independent projection/application service over PropagationSnapshot and PropagationSnapshotStore
- deterministic heard-path map-ready records retaining explicit endpoint geography, callsigns, frequency/band/mode/SNR/reportCount/provenance/freshness
- provider-neutral ionospheric metric/sample projection with no TEC-to-MUF or unexplained heat-score conversion
- concise solar/geomagnetic context projection with freshness/provenance
- modeled paths remain distinct from observed heard paths
- deterministic band/frequency/mode/source/freshness filters
- optional selected-path assessment using existing explainable PropagationAssessmentEngine
- snapshot capture/source-retrieval/offline-cache status surfaced explicitly
- no QSO promotion from heard evidence
- no Android/Compose/Google Maps/Mapbox dependency
- no WSPRnet/WSPR.live/GIRO/HFcast/VOACAP integration
- no live provider polling requirement
- no real accounts/credentials
- no phone/radio/RF/manual hardware work
- skip hardware/account-gated checkpoints under the owner execution override

## Resume rule

On interruption or a new chat:

1. read `README.md`, `NEXT_ACTION.md`, and this file;
2. inspect the latest verified checkpoint and the active branch;
3. do not return to CP-0003C unless the owner explicitly says `resume CP-0003C`;
4. do not call deferred hardware work complete;
5. complete and checkpoint the active software objective before opening another software feature branch.
6. skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation and continue to the next GitHub/CI-only checkpoint;
