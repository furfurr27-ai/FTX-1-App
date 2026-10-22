# SOFTWARE TRACK — FTX-1 FieldOps

This file exists to prevent hardware/account checkpoints from stalling unrelated software work or causing a new chat to redo already-started work.

## Latest verified durable parent

CP-0009Q-RECOVERY_CONSENT_FAILURE_UX

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

## Finite software release track

**Owner-completion override after CP-0009Q:** no speculative CP-0009R
recovery-accessibility micro-checkpoint. Use the acceptance-gapped
`RELEASE_ACCEPTANCE_MATRIX.md` to close original P0–P3 software
requirements, then stop after a durable software release candidate.

### Immediate integration gap / next checkpoint

**CP-0009R — integrated Android operating navigation and shared services**
is the next acceptance target, **not** a verified checkpoint.

- `MainActivity` remains an archive-only developer shell; the debug
  APK has no complete DIGI, APRS, MAP, LOG, AWARDS, CW/SSB or RADIO
  operating flow. Host-only engines and research must not be counted
  as installed APK operation.
- Integrate existing production-tested core components into Android
  with truthful disconnected state and no live RF, working navigation,
  shared app state and lifecycle/accessible tests.
- Continue with remaining P0–P3 gap closure only; required P2/P3
  families remain missing in current source.
- Each milestone must include meaningful source/tests, exact-head CI,
  premerge proof, merged-main finalizer and independent SHA-256 of
  immutable snapshot. Final RC has reproducible integrated APK,
  acceptance matrix and installation/field-test instructions.
- No CP checkpoint after the software RC except for a verified
  requirement bug, safety/security issue, physical integration failure
  or new owner requirement.

### Completed checkpoint

CP-0009Q-RECOVERY_CONSENT_FAILURE_UX / v68;
durable main `6c660d087fcdea4ae149b6dfe1b5b956d6edbf97`.
Finalizer `39722428140` 46/46 success; independent snapshot
`06a3d160b082ad3cea19dce2caf7af84c73630e4b362bec6614474dbd1fd463f`
matches both manifest and LATEST. CP-0003C remains DEFERRED;
CP-0004A/B/C remain physically unverified.

## Resume rule

On interruption or a new chat:

1. read `README.md`, `NEXT_ACTION.md`, and this file;
2. inspect the latest verified checkpoint and the active branch;
3. do not return to CP-0003C unless the owner explicitly says `resume CP-0003C`;
4. do not call deferred hardware work complete;
5. complete and checkpoint the active software objective before opening another software feature branch.
6. skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation and continue to the next GitHub/CI-only checkpoint;
