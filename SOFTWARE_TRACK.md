# SOFTWARE TRACK — FTX-1 FieldOps

This file exists to prevent hardware/account checkpoints from stalling unrelated software work or causing a new chat to redo already-started work.

## Latest verified durable parent

`CP-0006G-EXTENDED_AWARD_CATALOG`

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

`CP-0007A-AWARD_MAP_PROJECTION`

Required scope:

- platform-independent award-area map-layer projection
- source from authoritative logbook + persisted award evidence + Awards Center progress
- explicit NEEDED / WORKED_UNCONFIRMED / CONFIRMED / LOCAL_THRESHOLD_MET semantics
- first supported geographic targets: U.S. states and Maidenhead four-character grids
- geometry identity/source metadata separated from geometry payload
- no callsign-derived boundaries/coordinates/zones/entities/islands/parks/summits
- band/mode/date query context preserved
- deterministic clustering/aggregation-ready records
- host/CI-only verification before Android map rendering
- no real sponsor accounts, credentials, claim submission, phone/radio/RF/manual hardware work
- skip later hardware/account-gated checkpoints under the owner execution override

## Resume rule

On interruption or a new chat:

1. read `README.md`, `NEXT_ACTION.md`, and this file;
2. inspect the latest verified checkpoint and the active branch;
3. do not return to CP-0003C unless the owner explicitly says `resume CP-0003C`;
4. do not call deferred hardware work complete;
5. complete and checkpoint the active software objective before opening another software feature branch.
6. skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation and continue to the next GitHub/CI-only checkpoint;
