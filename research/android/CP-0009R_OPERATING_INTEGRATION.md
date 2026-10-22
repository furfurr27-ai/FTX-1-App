# CP-0009R — integrated offline-safe operating navigation and shared core services

Parent: verified `CP-0009Q-RECOVERY_CONSENT_FAILURE_UX` / v68 /
`6c660d087fcdea4ae149b6dfe1b5b956d6edbf97`.
**WIP / not durable** until both exact-head CI matrices, merge, merged-main finalizer and independent immutable snapshot SHA-256.

## Concrete acceptance gap addressed

Previously `MainActivity.java` was an archive-only developer shell. Its only useful path opened a synthetic archive; host-tested mode profiles, the canonical QSO logger and awards service were not connected to the app's operating navigation. CP-0009R replaces the launcher body with `FieldOpsOperatingActivity.kt` and provides non-transmitting operating surfaces without pretending that mode decoders are linked.

## Implementation under test

- Seven navigable workspaces: OPS, DIGI, APRS, MAP, LOG, AWARDS and RADIO, with native Back returning to OPS before exit.
- DIGI uses the actual canonical `DigitalModes.profile` state for FT8, FT4, FT2, JS8 and WSPR, with distinct timings; unsupported RX and TX controls are disabled.
- LOG saves **actual user-entered** manual CW/SSB QSOs via `FastQsoLogger`, `InMemoryLogbookRepository`, `ManualQsoInput`, and `QsoRadioContext`; invalid call/frequency/band data are rejected without inserting records. The same canonical logbook supplies an ADIF preview via `AdifCodec.export`.
- AWARDS reads from the same authoritative in-memory logbook through `AwardsCenterApplicationService`, with empty external confirmations and sponsor awards, and no false certified-award claim.
- MAP reads `awardMapLayers()` without fabricating coordinates, while explicitly labelling missing authenticated heard/APRS data.
- APRS distinguishes internal FTX-1 radio APRS from phone AFSK/AX.25; no packet transmissions.
- RADIO shows disconnected CAT/audio/LoTW state. Disabled controls prevent RX arming, transmitter access and LoTW uploads.
- `UiSession` survives configuration changes through `onRetainNonConfigurationInstance` but does not pretend to survive a fresh process; no new runtime permissions, USB, network, filesystem, microphone or radio operations were added. CP-0009Q encrypted archive recovery stays isolated.
- Preserves existing private synthetic propagation archive navigation through its original Activity.

## Testing and release boundary

New Robolectric API35 tests verify canonical mode timing, disabled TX, real manual logbook -> ADIF -> awards shared state, bad frequency rejection, rotation/fresh-process behavior, map/packet separation and archive navigation. Existing CP-0009D–Q Android regression tests, inherited host script matrix, debug APK verification, and premerge snapshot dry-run must remain green.

**This closes only an integrated Android navigation and locally entered logbook/awards acceptance gap.** It does **not** provide integrated native digital RX/TX, USB CAT, USB audio, RF operations, live APRS/maps/propagation or persistent QSO database. All those remain outstanding in `RELEASE_ACCEPTANCE_MATRIX.md`, and must have acceptance evidence before software release completion.

CP-0003C is **DEFERRED**; CP-0004A/B/C remain hardware-blocked. No real device, RF or account testing is claimed.
