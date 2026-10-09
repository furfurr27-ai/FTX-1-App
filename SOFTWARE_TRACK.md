# SOFTWARE TRACK — FTX-1 FieldOps

This file exists to prevent hardware/account checkpoints from stalling unrelated software work or causing a new chat to redo already-started work.

## Latest verified durable parent

CP-0008P-PROPAGATION_OFFLINE_REPORT_SERIALIZATION_CONTRACT

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

CP-0008Q-PROPAGATION_OFFLINE_REPORT_DECODE_VALIDATION

Required scope:

- strict offline deterministic JSON decoding and validation of CP-0008P canonical wire version 1
- bounded parser and strong source/evidence provenance and version validation
- round-trip equality and tampering/corruption rejection using deterministic CI-only fixtures
- preserve CP-0008M/N/O/P semantics and existing source/cache/assessment/refresh contracts
- immutable main checkpoint after full inherited CI regression
- no Android UI, lifecycle, WorkManager, credentials, real accounts, new providers or phone/radio/RF tests
- CP-0003C remains DEFERRED; CP-0004A/B/C remain incomplete


Implementation status: **staged / not checkpointed** in PR #40, branch `cp-0008q-propagation-offline-report-decode-validation`. The strict decoder and adversarial Kotlin tests have been committed. Awaiting exact-tip CI completion; no successful run or verified finalizer has been established. Do not merge on pending or red CI. Keep the parent CP-0008P durable checkpoint intact.

## Resume rule

On interruption or a new chat:

1. read `README.md`, `NEXT_ACTION.md`, and this file;
2. inspect the latest verified checkpoint and the active branch;
3. do not return to CP-0003C unless the owner explicitly says `resume CP-0003C`;
4. do not call deferred hardware work complete;
5. complete and checkpoint the active software objective before opening another software feature branch.
6. skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation and continue to the next GitHub/CI-only checkpoint;
