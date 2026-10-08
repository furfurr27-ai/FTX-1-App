# CP-0008H — Public propagation transport adapters

Parent durable checkpoint: `CP-0008G-PROPAGATION_SOURCE_REFRESH_COORDINATOR`.

Evidence level: **host/CI software + official public endpoint/documentation re-check + deterministic fake transport over pinned provider fixtures**.

## Objective

Connect the already-verified NOAA SWPC, GloTEC, and PSK Reporter parsers to the CP-0008G refresh coordinator without putting a concrete HTTP client, Android networking stack, account, credential, radio, or RF behavior into core propagation code.

CP-0008H introduces:

- a platform-neutral GET request/response transport contract;
- strict response validation;
- NOAA Kp/F10.7 source factories;
- a GloTEC index-to-artifact transport adapter;
- a PSK Reporter deterministic query builder and transport adapter.

Required CI never contacts a live provider.

## Sources actually checked

The official public endpoints and documentation were re-checked on 2026-10-08.

### NOAA SWPC

Pinned endpoints remain:

- `https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json`
- `https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json`
- `https://services.swpc.noaa.gov/products/summary/10cm-flux.json`
- `https://services.swpc.noaa.gov/products/glotec/geojson_2d_urt.json`
- `https://services.swpc.noaa.gov/products/glotec/geojson_2d_urt/`

The live official GloTEC product directory continues to expose canonical artifacts named:

`glotec_icao_YYYYMMDDTHHMMSSZ.geojson`

The official parent directory exposes the moving `geojson_2d_urt.json` index and reports a file size on the order of hundreds of KiB, while individual GeoJSON artifacts are on the order of a few MiB.

The research client did not expose a stable documented object-key schema for the moving index. CP-0008H therefore does **not** invent one.

Instead, `NoaaSwpcGlotecIndexSelector`:

1. requires syntactically valid JSON;
2. recursively inspects JSON string values;
3. recognizes only an exact canonical artifact filename or an exact official NOAA full artifact URL;
4. rejects malformed candidate-like strings and non-official artifact URLs;
5. selects the candidate with the greatest canonical UTC timestamp token.

This keeps index selection deterministic without coupling FieldOps to an undocumented moving-index key layout.

### PSK Reporter

The official developer page still documents:

- public retrieval at `https://retrieve.pskreporter.info/query`;
- `senderCallsign`, `receiverCallsign`, or `callsign` selection;
- negative `flowStartSeconds` lookback up to 24 hours;
- `rptlimit`, `rronly`, `noactive`, `mode`, `frange`, and other query parameters;
- XML response format;
- a recommendation to retrieve reception data no more often than once every five minutes;
- optional `appcontact` for operators who want service-contact notifications.

FieldOps deliberately does not persist or generate `appcontact`. It also does not request `callback`/JSONP because the existing CP-0008D parser is an XML parser.

## Public transport contract

`PublicPropagationTransport` exposes only:

`get(PublicPropagationRequest) -> PublicPropagationResponse`

A request carries:

- exact HTTPS URL;
- maximum accepted response byte count;
- accepted media types.

A response carries:

- the requested URL reported by the transport;
- the effective URL;
- HTTP status;
- optional media type;
- decoded response body.

The contract is deliberately not tied to:

- OkHttp;
- Retrofit;
- HttpURLConnection;
- java.net transport;
- Android networking;
- WorkManager.

A later application/platform adapter may implement this interface.

## Response validation

Before a provider parser sees a body, CP-0008H checks:

- transport-reported requested URL exactly equals the FieldOps request;
- effective URL exactly equals the request, so unexpected redirects fail closed;
- status is HTTP 200;
- body is nonblank;
- UTF-8 response size does not exceed the request bound;
- media type, when supplied by the transport, is in the request's accepted set.

Retry classification:

- 408, 425, 429 and 5xx are retryable;
- other non-200 responses are not rapid-retryable;
- blank HTTP-200 bodies are retryable;
- size/content-type/provenance/redirect violations are non-retryable contract failures;
- thrown transport exceptions are retryable;
- parser/schema rejection is non-retryable until normal source cadence.

The transport request includes a maximum body size, and the adapter re-checks the decoded body size. A future concrete transport should enforce the limit while reading, not only after decoding.

## NOAA source factories

The following source factories return CP-0008G `PropagationRefreshSourceDefinition` objects:

- `noaaPlanetaryKp`
- `noaaPlanetaryKpForecast`
- `noaaF107`
- `noaaGlotec`

They call the already-verified CP-0008B/CP-0008C parsers and do not duplicate provider normalization logic.

The CP-0008G coordinator remains responsible for canonical Kp merge policy:

- dedicated planetary-Kp feed owns observed records;
- forecast-product observed rows are removed during refresh canonicalization;
- estimated and predicted forecast records remain.

## GloTEC two-stage retrieval

A GloTEC refresh is:

1. fetch the official moving index;
2. select the latest canonical official artifact URL;
3. fetch that exact artifact;
4. pass the body, refresh UTC, and exact artifact URL to `NoaaSwpcGlotecAdapter`.

The configured GloTEC refresh cadence may not be shorter than the pinned 10-minute product cadence.

Response bounds:

- moving index: 2 MiB;
- GeoJSON artifact: 8 MiB.

The artifact bound is intentionally above the currently observed approximate 2.4 MiB product size while still preventing unbounded provider responses.

## PSK Reporter query contract

`PskReporterPublicQuery` constructs a deterministic XML-query URL.

It supports:

- sender, receiver, or either-callsign selector;
- 60-second to 24-hour lookback;
- bounded report limit;
- optional mode;
- optional complete frequency range.

The generated query always includes:

- negative `flowStartSeconds`;
- `rptlimit`;
- `rronly=1`;
- `noactive=1`.

It never adds:

- `appcontact`;
- `callback`;
- credentials.

Portable-style callsigns are percent-encoded deterministically.

The source factory refuses a refresh cadence shorter than the documented five-minute retrieval interval.

## Deterministic fake transport

`PublicPropagationTransportAdapterTests` uses an in-memory recording fake transport.

It exercises the real pinned fixtures for:

- NOAA observed Kp;
- NOAA observed/estimated/predicted Kp;
- NOAA F10.7;
- bounded GloTEC GeoJSON;
- bounded PSK Reporter XML.

The tests cover:

- exact endpoint requests;
- media-type handling;
- size limits;
- HTTP retry classification;
- redirects and request-provenance mismatch;
- malformed provider payloads;
- transport exceptions;
- GloTEC index selection and host restriction;
- PSK query construction and privacy restrictions;
- end-to-end composition into the CP-0008G refresh coordinator.

The integration test proves five source definitions can produce one deterministic propagation snapshot while CP-0008G still removes forecast-product observed Kp in favor of the dedicated observed feed.

## Evidence boundary

CP-0008H proves:

- transport-independent request/response semantics;
- bounded provider response handling;
- exact pinned public endpoint usage;
- deterministic GloTEC latest-artifact discovery from valid JSON string values;
- existing parser reuse;
- PSK Reporter public query construction without persisted contact identifiers;
- composition with the CP-0008G coordinator.

It does **not** prove:

- live endpoint uptime;
- Android networking;
- WorkManager scheduling;
- TLS implementation details of a future concrete client;
- cellular/Wi-Fi behavior;
- battery behavior;
- WSPRnet/WSPR.live, GIRO, HFcast, or VOACAP;
- real propagation accuracy;
- phone/radio/RF operation.

## Deferred checkpoint rule

CP-0003C remains **DEFERRED**. Do not return to CP-0003C until the owner explicitly says `resume CP-0003C`.

CP-0004A/B/C remain incomplete hardware checkpoints. Skip phone/radio, real credential/account/certificate, RF, and other manual hardware validation while the owner override remains active.
