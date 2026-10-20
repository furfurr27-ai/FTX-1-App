# SOFTWARE TRACK — FTX-1 FieldOps

This file exists to prevent hardware/account checkpoints from stalling unrelated software work or causing a new chat to redo already-started work.

## Latest verified durable parent

CP-0009N-EXPLICIT_ENCRYPTED_ARCHIVE_RECOVERY_CHOICE

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

CP-0009O — read-only recovery audit trail and provenance UX

CP-0009N provides separate review/select/confirm user gestures to
recover either independently authenticated archive to in-memory
synthetic UI state. Reinspection and canonical digest validation
reject changes/lost keys. Old keys/files stay; retirement checklist
always BLOCKED_NON_EXECUTING. Robolectric/Android APK/inherited CI
pass; no physical device, filesystem, CAT/audio/RF or trusted
freshness evidence. CP-0003C DEFERRED; CP-0004A/B/C incomplete.

## Resume rule

On interruption or a new chat:

1. read `README.md`, `NEXT_ACTION.md`, and this file;
2. inspect the latest verified checkpoint and the active branch;
3. do not return to CP-0003C unless the owner explicitly says `resume CP-0003C`;
4. do not call deferred hardware work complete;
5. complete and checkpoint the active software objective before opening another software feature branch.
6. skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation and continue to the next GitHub/CI-only checkpoint;


## CP-0009O work in progress — not durable

Branch `cp-0009o-session-recovery-audit` preserves CP-0009N parent;
adds ephemeral metadata-only, bounded recovery event log and visible
candidate source without station or trusted recency claim. No writes,
key retirement, account/hardware proof. Await exact-head GitHub push and
PR checks, merge and immutable main snapshot proof.
CP-0003C remains DEFERRED; CP-0004A/B/C incomplete.
