# SOFTWARE TRACK — FTX-1 FieldOps

This file exists to prevent hardware/account checkpoints from stalling unrelated software work or causing a new chat to redo already-started work.

## Latest verified durable parent

`CP-0005A-UNIVERSAL_QSO_LOGGER`

## Deferred but incomplete hardware/account work

| Checkpoint | Status | Durable handling |
| --- | --- | --- |
| CP-0003C Real LoTW validation | BLOCKED / incomplete | WIP preserved on `cp-0003c-real-lotw-validation`; requires real device/account/certificate validation |
| CP-0004A FTX-1 CAT USB port | RED / not started as verified checkpoint | requires S23 + FTX-1 USB topology proof |
| CP-0004B FTX-1 USB audio | RED / not started as verified checkpoint | requires real Android USB audio endpoint/rate enumeration |
| CP-0004C S23 + FTX-1 hardware proof | RED / not started | requires actual phone/radio/RF test |

These checkpoints are not skipped in the sense of being complete. They are deferred and must return later.

## Active software checkpoint

`CP-0005B-MANUAL_QSO_LOTW_QUEUE`

Required scope:

- optional logger-save -> LoTW queue policy
- SSB/CW and eligible digital QSOs use the same queue
- local log save remains independent of LoTW/network success
- idempotent queue insertion by immutable QSO id
- disabled policy leaves local QSO NOT_UPLOADED
- no real automatic network upload until deferred CP-0003C passes

## Resume rule

On interruption or a new chat:

1. read `README.md`, `NEXT_ACTION.md`, and this file;
2. inspect the latest verified checkpoint and the active branch;
3. do not return to deferred hardware work unless Chris explicitly asks or the required device/account evidence is available;
4. do not call deferred hardware work complete;
5. complete and checkpoint the active software objective before opening another software feature branch.
