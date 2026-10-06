# CP-0005B — Manual/digital QSO LoTW queue evidence

Parent durable checkpoint: `CP-0005A-UNIVERSAL_QSO_LOGGER`.

Deferred hardware/account checkpoints remain incomplete:

- `CP-0003C` real LoTW device/account validation
- `CP-0004A/B/C` FTX-1 Android hardware proof

CP-0005B is software-only and does not imply those checkpoints passed.

## Objective

The verified work queue requires CP-0005B to make saved SSB/CW QSOs enter the same LoTW state machine as digital QSOs when enabled.

The README further requires:

- explicit logger policy
- local save remains authoritative and independent of LoTW/network success
- queue insertion is idempotent by immutable local QSO id
- disabled policy leaves the QSO local-only / NOT_UPLOADED
- station-profile/session binding is preserved
- no real automatic network upload until CP-0003C passes

## Explicit logger policy

`LotwLoggerPolicy` now controls logger-save -> local LoTW queue behavior.

Built-in modes:

- `DISABLED`
- `AUTO_MANUAL_AND_DIGITAL`

The enabled policy accepts:

- manually logged SSB
- manually logged CW
- completed digital-auto QSOs that retain provider, station-profile and session evidence

Legacy/imported records are not silently auto-enqueued by this logger policy.

## Local-first save ordering

`FastQsoLogger` persists the authoritative local `QsoRecord` before any LoTW queue action.

Order:

`build QSO -> repository.save() -> local queue enqueue -> repository sync-state update`

Therefore a queue failure cannot erase or prevent the local contact save.

If local queue insertion/update succeeds:

- the queue entry is `QUEUED`
- the authoritative repository QSO is updated to `QUEUED`

If queue insertion fails:

- the already-saved local QSO remains present
- its state remains `NOT_UPLOADED`
- the failure is delivered separately through `LotwQueueFailureHandler`
- the logger call still returns the saved local contact

This checkpoint adds no HTTP/signing call to the logger path.

## Shared manual/digital queue

The focused gate saves and queues:

- SSB
- CW
- FT8 represented as exact ADIF `MFSK/FT8`

All three use the same `LotwUploadQueue`.

The queued copy retains:

- immutable QSO id
- station profile id
- operating session id
- station callsign
- exact mode/submode
- source provider for digital QSOs

This preserves the binding required for the later TrustedQSL station-location selection.

## Idempotence

`LotwUploadQueue.enqueue()` is now idempotent by immutable local QSO id.

Re-enqueuing the same QSO:

- returns the existing queue entry
- does not create a second entry
- does not reset retry attempt count
- does not reset SUBMITTED/REJECTED queue state

Reusing an existing local QSO id for a materially different contact or station profile fails closed.

## Network boundary

`LotwQueueSink` exposes only local enqueue behavior.

The CP-0005B focused gate explicitly rejects logger-path references to:

- `LotwTransport`
- `uploadTransactional`
- `uploadTq8`
- `HttpURLConnection`

Real automatic LoTW network upload remains disabled.

## Host evidence

Initial green branch workflow: `37450510384`.

Focused CP-0005B gate:

- logger-save/local-queue/idempotence: **47/47 assertions PASS**
- logger path network-separation check: PASS

Inherited regression:

- CP-0005A universal QSO/logger: **78/78 PASS**
- CP-0003B LoTW transaction: **53/53 PASS**
- core: **42,062 PASS**
- pipeline: **56 PASS**
- LoTW: **19 PASS**

## Evidence boundary

Verified/host-tested:

- explicit logger auto-queue policy
- manual SSB/CW and eligible completed digital QSO queue insertion
- authoritative save-before-queue ordering
- queue failure cannot undo the local QSO
- local/repository QUEUED state after successful local enqueue
- disabled policy leaves NOT_UPLOADED
- idempotent queue behavior by immutable local QSO id
- station-profile/session preservation
- no LoTW network ownership in the logger path

Not yet verified / deliberately excluded:

- persistent Room-backed queue/repository
- Compose queue UI
- real automatic LoTW upload
- real certificate/account/device LoTW validation
- deferred FTX-1 Android hardware checkpoints

Those remain later checkpoints.
