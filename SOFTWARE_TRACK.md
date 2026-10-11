# SOFTWARE TRACK — FTX-1 FieldOps

This file exists to prevent hardware/account checkpoints from stalling unrelated software work or causing a new chat to redo already-started work.

## Latest verified durable parent

CP-0009F-ANDROID_ARCHIVE_NAVIGATION_TESTS

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

CP-0009G — bounded opt-in Android archive persistence contract

Scope: gated, opt-in, bounded on-device local archive persistence with
canonical receipt validation, corrupt-store handling and process-death
restore tests. Preserve Robolectric UI checks, real APK artifact build and
all host regressions. CP-0003C DEFERRED; CP-0004A/B/C incomplete.

## Resume rule

On interruption or a new chat:

1. read `README.md`, `NEXT_ACTION.md`, and this file;
2. inspect the latest verified checkpoint and the active branch;
3. do not return to CP-0003C unless the owner explicitly says `resume CP-0003C`;
4. do not call deferred hardware work complete;
5. complete and checkpoint the active software objective before opening another software feature branch.
6. skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation and continue to the next GitHub/CI-only checkpoint;

## CP-0009G implementation branch (not checkpointed)

cp-0009g-opt-in-archive-storage-contract from CP-0009F/v57. Deliver bounded canonical storage codec and explicit opt-in Android private-file facade with atomic staged commits, full JSON receipt reimport and corruption rejection. Host and Robolectric tests required alongside actual APK and inherited 42-gate regression; no physical phone, power-loss, encryption, RF, accounts or CAT/USB proof. CP-0003C DEFERRED; CP-0004A/B/C incomplete.
