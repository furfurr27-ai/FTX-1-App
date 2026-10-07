# SOFTWARE TRACK — FTX-1 FieldOps

This file exists to prevent hardware/account checkpoints from stalling unrelated software work or causing a new chat to redo already-started work.

## Latest verified durable parent

`CP-0008C-NOAA_SWPC_GLOTEC_IONOSPHERIC_ADAPTER`

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

CP-0008D-PSK_REPORTER_HEARD_PATH_ADAPTER

Required scope:

- official no-credential PSK Reporter retrieval endpoint/query/schema pin
- deterministic representative XML fixture; no live-service dependency in required CI
- transport-independent parser/normalizer into CP-0008A HeardPathObservation
- explicit valid transmitter and receiver Maidenhead locators required; no callsign-derived geography
- exact provider frequency and observation timestamp retained
- mode/SNR retained when explicitly supplied by provider
- sender/receiver callsigns retained as labels only
- source/retrieval provenance, quality and confidence explanation retained
- one-way reports remain separate from QSO/logbook/LoTW state
- fail closed or explicitly reject records missing minimum geographic/path fields
- published PSK Reporter retrieval-rate guidance documented and respected by any later transport adapter
- no WSPRnet/GIRO/HFcast/VOACAP integration
- no Android/Compose/map SDK dependency
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
