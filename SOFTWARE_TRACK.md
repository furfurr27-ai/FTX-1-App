# SOFTWARE TRACK — FTX-1 FieldOps

This file exists to prevent hardware/account checkpoints from stalling unrelated software work or causing a new chat to redo already-started work.

## Latest verified durable parent

CP-0009O-SESSION_RECOVERY_AUDIT_PROVENANCE

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

CP-0009P — audit lifecycle and provenance consistency hardening

CP-0009O session-only typed bounded audit, explicit source label
and automatic provenance invalidation are Robolectric/Android APK
and inherited Kotlin CI verified. No persistent log, trusted
timestamp/anti-replay, Android physical Keystore or radio proof.
Original V1/V2 and keys remain; no key retirement. CP-0003C
DEFERRED; CP-0004A/B/C incomplete.

## Resume rule

On interruption or a new chat:

1. read `README.md`, `NEXT_ACTION.md`, and this file;
2. inspect the latest verified checkpoint and the active branch;
3. do not return to CP-0003C unless the owner explicitly says `resume CP-0003C`;
4. do not call deferred hardware work complete;
5. complete and checkpoint the active software objective before opening another software feature branch.
6. skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation and continue to the next GitHub/CI-only checkpoint;


## CP-0009P work in progress — not durable

Branch `cp-0009p-audit-lifecycle-consistency`, durable parent
CP-0009O/v66. UI provenance follows *displayed bytes*, not archive
disk existence; stale reviewed choices clear on evidence changes,
session disable and actual display change. No raw data in audit.
Await exact-head push/PR Android/Robolectric/inherited CI and merged-main
finalizer with independently verified immutable SHA-256. No physical
radio/account/device testing. CP-0003C DEFERRED.
