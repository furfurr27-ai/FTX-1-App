# CP-0003B — Transaction-safe LoTW upload evidence

Checkpoint objective: replace the legacy one-shot signer/upload path with the CP-0003A transactional signer session and prove:

`sign -> upload TQ8 -> verify LoTW acceptance -> commit TrustedQSL duplicate state`

Any signing, transport, server-rejection, or acceptance-verification failure must leave the TrustedQSL transaction uncommitted and must not mark the local QSO batch ACCEPTED.

Parent checkpoint: `CP-0003A-TRUSTEDQSL_SIGNER`.

## Transaction path

`LotwSyncManager.uploadTransactional()` now owns the transaction orchestration:

1. validate one explicit station profile / station callsign for the batch
2. reject duplicate local QSO ids and ambiguous LoTW acceptance match keys before signing
3. export the selected local QSOs to ADIF
4. start an OPEN `LotwSigningSession` using the explicit TrustedQSL station-location name, expected station callsign and expected DXCC
5. upload the signed TQ8 payload
6. require HTTP 2xx and the LoTW upload endpoint's accepted result
7. poll the LoTW accepted-QSO report
8. require **every QSO in the batch** to appear by the conservative FieldOps LoTW match key
9. only then call `LotwSigningSession.commit()`
10. close the signer session

HTTP upload success alone produces SUBMITTED state and is never treated as LoTW QSO acceptance.

## Failure/rollback behavior

The host tests prove:

- signer failure: no upload occurs; QSOs remain QUEUED
- transport exception: signer transaction rolls back; QSOs remain QUEUED
- non-2xx upload response: signer transaction rolls back; QSOs remain QUEUED
- LoTW endpoint rejection: signer transaction rolls back; QSOs become REJECTED
- acceptance-report network/parsing failure through all configured checks: signer transaction rolls back; QSOs are not ACCEPTED
- partial acceptance of a batch: signer transaction rolls back; no QSO in that batch is promoted to ACCEPTED
- full batch acceptance: TrustedQSL duplicate state commits exactly once
- accepted records retain LoTW receive/record identifiers when present

The transaction result deliberately exposes typed outcomes rather than exception messages that could accidentally propagate credential or signer detail.

## Acceptance polling

`LotwAcceptancePolicy` makes acceptance polling explicit and testable:

- default checks: 3
- initial verification delay: existing `LotwSyncPolicy.VERIFY_AFTER_UPLOAD_MILLIS` (30 seconds)
- retry delay: 30 seconds
- `LotwVerificationDelay` is injected in tests so CI does not sleep

The existing LoTW report cursor can be supplied as `acceptedSince`; when present it is forwarded as `qso_qsorxsince`.

Acceptance matching intentionally does not depend on LoTW preserving the local MODE string because LoTW can normalize/remap modes.

## Shared manual/digital queue

`LotwUploadQueue` is mode-neutral.

The focused test places:

- SSB
- CW
- FT8

into the same queue and processes them through one transaction path.

Queue behavior:

- enqueue -> QUEUED
- retryable signing/network failure -> stays in automatic pending queue
- HTTP-accepted but not yet report-verified -> SUBMITTED, not ACCEPTED
- endpoint rejection -> retained for operator visibility but excluded from automatic retry
- verified ACCEPTED -> removed from the queue

This satisfies CP-0003B's requirement that manual SSB/CW and digital QSOs share the same LoTW state machine. It does **not** claim that a future manual-logger save action automatically enqueues a QSO; that UI/repository wiring remains the separate CP-0005B work item.

## Signer terminal-state hardening

CP-0003B also hardens the CP-0003A Kotlin signer session:

- once native TrustedQSL commit succeeds, Kotlin marks the session COMMITTED before cleanup
- cleanup failure cannot make a successfully committed duplicate database appear roll-backable
- the same terminal ordering is used for rollback
- the signer implementation itself remains separate from HTTP transport

## Branch evidence

Final pre-merge branch workflow: `37328897441`.

Expected gates:

- focused CP-0003B transaction suite: **53 assertions**
- CP-0003A signer regression: **43 assertions**
- inherited core: **42,062 assertions**
- pipeline: **56 assertions**
- inherited LoTW: **19 assertions**
- official TrustedQSL 2.8.6 archive hash/API compile remains verified by the signer regression

## Evidence boundary

Host/CI can prove transaction ordering and failure behavior with deterministic signer/transport/report fixtures.

CP-0003B does **not** claim:

- real LoTW network upload with Chris's account
- real LoTW server acceptance
- real TrustedQSL cryptographic signing on Android
- complete TrustedQSL Android arm64-v8a dependency packaging
- persistent Room-backed upload queue
- automatic upload
- automatic manual-logger enqueue wiring

Those are intentionally later work. CP-0003C is the next checkpoint for real device/test-account signed upload and confirmation sync. Automatic upload remains disabled until CP-0003C passes.
