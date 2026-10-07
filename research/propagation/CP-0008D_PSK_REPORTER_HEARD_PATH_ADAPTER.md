# CP-0008D — PSK Reporter public heard-path adapter

Parent durable checkpoint: `CP-0008C-NOAA_SWPC_GLOTEC_IONOSPHERIC_ADAPTER`.

Evidence level: **host/CI software + official PSK Reporter developer documentation + bounded deterministic recorded-response fixture**.

## Objective

Exercise the CP-0008A provider-neutral heard-path boundary against PSK Reporter's public reception-report query without turning one-way reception reports into QSOs, LoTW records, or inferred geography.

CP-0008D is deliberately transport-independent. It parses already-fetched XML and caller-supplied retrieval provenance.

## Sources actually checked

See `research/propagation/PROPAGATION_SOURCES.tsv`.

### Direct PSK Reporter developer evidence

The official developer page documents:

- public reception retrieval via `https://retrieve.pskreporter.info/query`;
- senderCallsign / receiverCallsign / callsign query selectors;
- negative `flowStartSeconds` query windows up to 24 hours;
- mode, report limit, frequency range, locator and sequence options;
- XML response format;
- reception fields including sender/receiver callsigns and locators, frequency, mode, SNR and flowStartSeconds;
- frequency measured in hertz;
- flowStartSeconds as Unix epoch seconds;
- mode as an ADIF MODE or SUBMODE value;
- sNR as an integer reception SNR value;
- informationSource values distinguishing automatically extracted data, QSO/call-log data, manual data and test records;
- retrieval guidance of no more often than once every five minutes.

The official page notes that `/query` points to the latest query API. CP-0008D therefore uses a dated FieldOps source-version label rather than pretending the service publishes a stable numeric API version.

### Secondary recorded-response evidence

A public client repository, `jasonhancock/go-pskreporter`, contains a recorded PSK Reporter response with:

- root `receptionReports currentSeconds=...`;
- `lastSequenceNumber`;
- `maxFlowStartSeconds`;
- `receptionReport` rows carrying sender/receiver callsigns and locators, frequency, flowStartSeconds, mode and sNR.

Pinned fixture source:

- repository: `jasonhancock/go-pskreporter`
- commit: `b424d3bc83c52e424be7e6e32572ef652cecca4c`
- path: `testdata/output.xml`

This secondary fixture is used only for deterministic response-shape and exact-row provenance. PSK Reporter's own developer documentation remains authoritative for semantics.

## Bounded deterministic fixture

Manifest:

`research/propagation/PSK_REPORTER_FIXTURE.json`

Fixture:

`research/propagation/fixtures/psk_reporter_ag6k_20200903_bounded.xml`

Properties:

- exact recorded root metadata;
- first four exact reception-report rows from the pinned recorded response;
- 1,196 bytes;
- SHA-256 `fb41c07330c8d446dbd52eb4b35358950145b8a75fab76f225e69859b5752da7`.

The fixture is parser evidence only. It is not current propagation evidence and required CI never polls PSK Reporter.

## Normalization contract

A reception report becomes `HeardPathObservation` only when it carries all minimum facts needed by the existing provider-neutral domain:

- sender callsign;
- receiver callsign;
- explicit valid sender Maidenhead locator;
- explicit valid receiver Maidenhead locator;
- positive integer frequency in hertz;
- frequency inside the built-in amateur-band catalog;
- non-negative Unix `flowStartSeconds`;
- nonblank mode.

Optional provider SNR is retained only when it is a valid signed one-byte integer.

No location is derived from a callsign.

Provider locators remain explicit Maidenhead grids with source references identifying `senderLocator` or `receiverLocator`.

## Report rejection instead of invention

The XML document may legally contain reports that do not have enough information for a geographic heard path.

CP-0008D therefore distinguishes:

- document/schema failure, which fails parsing;
- individual report insufficiency, which produces an explicit rejection reason.

Report rejection reasons include:

- missing/invalid sender or receiver locator;
- missing/invalid frequency;
- frequency outside FieldOps' known amateur-band catalog;
- missing/invalid observation time;
- missing mode;
- invalid SNR;
- invalid/nonautomatic/test `informationSource`, when that provider field is present;
- identical endpoint grids, which cannot form a nonzero path under the current propagation domain.

This is intentionally conservative. A rejected report is not repaired from callsign databases, DXCC centers, cached station profiles or device location.

## informationSource handling

PSK Reporter's submission protocol defines:

- bottom bits value 1: automatically extracted;
- value 2: from a call log / QSO;
- value 3: other manual entry;
- bit 0x80: test transmission.

The public query response does not always expose `informationSource`.

Policy:

- absent `informationSource`: accept the report if all geographic/path facts are explicit;
- present automatic value: accept;
- present QSO/manual value: reject as heard-path evidence;
- present test bit: reject.

This avoids knowingly converting call-log/manual/test data into direct RF path evidence while remaining compatible with the documented public retrieval response.

## Confidence and data quality

Accepted PSK Reporter paths normalize as:

- source class: `MEASUREMENT`;
- data quality: `PROVISIONAL`;
- confidence basis: `PROVIDER_REPORTED`;
- FieldOps confidence: 0.80.

The 0.80 value is a FieldOps policy marker. It is not a PSK Reporter probability and not a guarantee that the decode, callsign or locator is correct.

The confidence explanation explicitly says the record is one-way heard-path evidence and not a QSO.

## Duplicate semantics

Exact duplicate normalized reception rows collapse to one `HeardPathObservation` and increment `reportCount`.

The evidence identity is derived from:

- provider observation epoch;
- sender callsign and explicit grid;
- receiver callsign and explicit grid;
- frequency;
- normalized mode;
- SNR or an explicit no-SNR marker.

No list index is used as evidence identity.

## QSO / LoTW separation

PSK Reporter reception reports are not authoritative contacts.

CP-0008D does not:

- create `QsoRecord`;
- create `DigitalCompletedContact`;
- write to the logbook;
- enqueue LoTW;
- claim a confirmation;
- imply two-way communication.

The existing propagation engine may use a fresh matching reception report as observed path evidence, but that remains separate from QSO state.

## Retrieval-rate boundary

The official developer page asks users to retrieve reception data no more often than once every five minutes.

CP-0008D exposes that interval as:

`MIN_RETRIEVAL_INTERVAL_MILLIS = 300000`

The adapter itself performs no network access. Any future transport/cache implementation must enforce or exceed that interval and should avoid repeated equivalent queries.

Required CI does not contact the live service.

## XML safety

The parser disables:

- DOCTYPE declarations;
- external general entities;
- external parameter entities;
- external DTD loading;
- XInclude;
- external DTD/schema access.

This keeps a provider response from becoming an XML external-entity/file/network access path inside the core parser.

## Source URL safety

The parser accepts only the official HTTPS query endpoint or that endpoint plus query parameters.

It rejects:

- non-PSK-Reporter hosts;
- callback/JSONP query URLs for the XML parser;
- `appcontact` query parameters in persisted source provenance so an operator email address is not written into checkpoint/cache evidence.

## Evidence boundary

CP-0008D proves:

- deterministic normalization of a pinned PSK Reporter XML response shape;
- explicit Maidenhead-only path geography;
- provider frequency/time/mode/SNR preservation;
- explicit per-report rejection instead of data invention;
- one-way heard-path/QSO separation;
- rate-limit policy exposure;
- transport/platform/hardware independence;
- XML external-entity hardening.

It does not prove:

- current PSK Reporter uptime;
- current station activity;
- current real-world propagation;
- all possible optional/enrichment attributes returned by future query generations;
- callsign truth;
- locator truth;
- QSO completion;
- Android network/cache/rendering integration;
- WSPRnet, GIRO or HFcast/VOACAP integration.

## Deferred checkpoint rule

CP-0003C remains **DEFERRED**. Do not return to CP-0003C until the owner explicitly says `resume CP-0003C`.

Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue with the next checkpoint that can be completed entirely through GitHub/CI.
