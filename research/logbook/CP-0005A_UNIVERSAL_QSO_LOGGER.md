# CP-0005A — Universal QSO model + fast logger evidence

Parent durable checkpoint: `CP-0003B-LOTW_TRANSACTION_SAFE`.

CP-0003C and CP-0004A/B/C remain explicitly deferred hardware/account checkpoints. CP-0005A is a software-only checkpoint and does not imply those checkpoints passed.

## Queue authority actually checked

The verified work queue defines CP-0005A as:

- exact mode/submode
- band/frequency
- UTC
- callsign
- RST
- grid/location/session
- manual SSB/CW logging
- digital auto-log adapters

The prior GitHub reference survey also established:

- `OperatingSession` above individual QSOs
- station profile/location as first-class QSO context
- exact mode/submode preservation
- a local-first authoritative logbook
- minimal-input/high-speed operator workflow for manual logging

CP-0005A implements those software concepts without pulling CP-0005B LoTW auto-enqueue or later Room/UI work into this checkpoint.

## Universal QSO record

The existing `QsoRecord` remains the authoritative local QSO type used by the verified LoTW transaction path. CP-0005A extends it rather than creating a second incompatible log model.

New local-log fields include:

- exact ADIF `mode`
- exact ADIF `submode`
- exact physical/radio mode such as `USB`, `LSB`, `CW`, or `DATA-U`
- exact `frequencyHz`
- compatibility `freqMhz`
- UTC start epoch plus ADIF `QSO_DATE` / `TIME_ON`
- UTC end epoch plus `QSO_DATE_OFF` / `TIME_OFF`
- sent/received reports
- remote and station location snapshots
- station profile id
- operating session id
- origin: legacy/manual/digital-auto/imported
- source provider id for digital auto-log
- notes and transmit power

Legacy CP-0003B QSO constructors remain valid so LoTW behavior is not broken.

When both legacy and new exact fields are present, invariants reject contradictory frequency or grid values.

## Operating sessions and location

`OperatingSession` carries:

- session id
- station profile id
- station callsign
- UTC start/end
- station grid/latitude/longitude/label snapshot
- radio identity
- antenna notes
- default power
- activity tags
- session notes

Every QSO created by `FastQsoLogger` receives the session id and station profile id plus a station-location snapshot.

A QSO cannot be logged before its session starts or after a closed session ends.

## Fast manual SSB/CW logger

`FastQsoLogger.logManual()` takes only the contact-specific operator input plus current session/radio context.

The radio context supplies:

- exact frequency
- exact radio mode
- optional explicit band
- transmit power

The contact input supplies:

- callsign
- SSB or CW
- optional sent/received reports
- optional remote grid/location
- optional explicit UTC start/end
- notes

If UTC start is not supplied, an injected UTC clock is used. The core does not fabricate RST values.

SSB is logged as exact ADIF `MODE=SSB` while the physical radio mode remains separately preserved, for example `USB` or `LSB`.

CW is logged as `MODE=CW` with the exact physical radio mode retained separately.

## Digital auto-log adapter

`DigitalCompletedContact` and `DigitalAutoLogAdapter` provide a mode-provider boundary for completed two-way digital contacts.

The provider must supply:

- provider id
- callsign
- exact ADIF parent mode
- exact ADIF submode when applicable
- exact frequency
- band or a frequency that can be labeled safely
- exact radio mode
- UTC
- optional reports/grid/location/notes

FieldOps does not infer or collapse the digital submode. The focused gate proves two separate contacts can retain `MFSK/FT8` and `MFSK/JS8` independently.

A contact explicitly marked incomplete is rejected rather than auto-logged.

## ADIF preservation

`AdifCodec.export()` now preserves:

- MODE
- SUBMODE
- exact Hz-derived FREQ
- QSO_DATE / TIME_ON
- QSO_DATE_OFF / TIME_OFF
- GRIDSQUARE / MY_GRIDSQUARE
- RST_SENT / RST_RCVD

The exact-Hz local value is the preferred frequency source for new CP-0005A records. Legacy MHz-only records still export through the compatibility path.

## Repository boundary

`LogbookRepository` is the software contract for authoritative local QSO storage.

CP-0005A includes a deterministic `InMemoryLogbookRepository` for host verification. Room persistence remains the later app-shell checkpoint; CP-0005A does not claim persistent Android storage.

Duplicate local QSO ids are rejected.

## CP-0005B boundary

Manual and digital logging in CP-0005A leaves `lotwUpload=NOT_UPLOADED`.

Automatic logger-save -> LoTW queue behavior is deliberately **not** included. That is the next software checkpoint, CP-0005B.

## Host evidence

Green branch workflow: `37444115765`.

Focused CP-0005A gate:

- universal QSO/manual/digital logger: **78/78 assertions PASS**

Inherited regression:

- CP-0003B LoTW transaction: **53/53 PASS**
- core: **42,062 PASS**
- pipeline: **56 PASS**
- LoTW: **19 PASS**

## Evidence boundary

Verified/host-tested:

- universal local QSO extensions
- exact mode/submode/radio-mode preservation
- exact frequency + band handling
- UTC start/end conversion
- session/profile/location snapshots
- fast manual SSB/CW logging
- completed-contact digital auto-log adapter
- ADIF exact-mode/submode/frequency export
- compatibility with CP-0003B LoTW transaction

Not yet verified or not part of CP-0005A:

- Android Room persistence
- Compose logger UI
- live FTX-1 frequency/mode feed
- hardware CAT/audio behavior
- automatic SSB/CW LoTW enqueue
- real LoTW/device validation

Those remain later checkpoints.
