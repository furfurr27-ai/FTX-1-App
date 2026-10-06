# SOFTWARE TRACK — FTX-1 FieldOps

This file exists to prevent hardware/account checkpoints from stalling unrelated software work or causing a new chat to redo already-started work.

## Latest verified durable parent

`CP-0003B-LOTW_TRANSACTION_SAFE`

## Deferred but incomplete hardware/account work

| Checkpoint | Status | Durable handling |
| --- | --- | --- |
| CP-0003C Real LoTW validation | BLOCKED / incomplete | WIP preserved on `cp-0003c-real-lotw-validation`; requires real device/account/certificate validation |
| CP-0004A FTX-1 CAT USB port | RED / not started as verified checkpoint | requires S23 + FTX-1 USB topology proof |
| CP-0004B FTX-1 USB audio | RED / not started as verified checkpoint | requires real Android USB audio endpoint/rate enumeration |
| CP-0004C S23 + FTX-1 hardware proof | RED / not started | requires actual phone/radio/RF test |

These checkpoints are not skipped in the sense of being complete. They are deferred and must return later.

## Active software checkpoint

`CP-0005A-UNIVERSAL_QSO_LOGGER`

Required scope:

- exact mode/submode and physical radio mode
- exact frequency + band
- UTC start/end data
- callsign
- RST/report sent and received
- remote/station grid and location snapshot
- station profile and operating session identity
- manual SSB/CW fast-log path
- digital completed-contact auto-log adapter path
- one authoritative local QSO model shared with LoTW
- ADIF preservation of mode/submode/frequency
- no CP-0005B automatic LoTW enqueue in this checkpoint

## Resume rule

On interruption or a new chat:

1. read `README.md`, `NEXT_ACTION.md`, and this file;
2. inspect the latest verified checkpoint and the active branch;
3. do not return to deferred hardware work unless Chris explicitly asks or the required device/account evidence is available;
4. do not call deferred hardware work complete;
5. complete and checkpoint the active software objective before opening another software feature branch.
