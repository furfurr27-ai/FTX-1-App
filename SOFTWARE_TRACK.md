# SOFTWARE TRACK — FTX-1 FieldOps

This file exists to prevent hardware/account checkpoints from stalling unrelated software work or causing a new chat to redo already-started work.

## Latest verified durable parent

CP-0009P-AUDIT_LIFECYCLE_PROVENANCE_CONSISTENCY

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

CP-0009Q — recovery consent and failure UX hardening

CP-0009P verified audit choice invalidation on disk changes, review
clearing on disable, authenticated source kept across navigation but
cleared after archive replacement, and failed-load preservation.
Robolectric and Android APK/Kotlin CI pass. Session audit remains
nonpersistent and typed. No physical S23, Keystore, CAT/RF, freshness
witness or key retirement proof. CP-0003C DEFERRED, CP-0004A/B/C
incomplete.

## Resume rule

On interruption or a new chat:

1. read `README.md`, `NEXT_ACTION.md`, and this file;
2. inspect the latest verified checkpoint and the active branch;
3. do not return to CP-0003C unless the owner explicitly says `resume CP-0003C`;
4. do not call deferred hardware work complete;
5. complete and checkpoint the active software objective before opening another software feature branch.
6. skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation and continue to the next GitHub/CI-only checkpoint;

## CP-0009Q work in progress — not durable

Branch `cp-0009q-recovery-consent-failure-ux`; immutable parent CP-0009P.
Explicit REVIEW → SELECT → consent/cancel/CONFIRM preserves fresh
reauthentication; cancellation never modifies archive bytes and failed
confirmation preserves source/history with safe error text. Test-only
software AES fixture is excluded from packaged APK; production keeps
AndroidKeyStore. Await current-head CI, main finalizer and verified
immutable snapshot before promotion. No physical radio/Keystore proof.
CP-0003C remains DEFERRED, CP-0004A/B/C incomplete.
