# CP-0008I — Concrete public HTTPS transport implementation

Parent durable checkpoint: `CP-0008H-PUBLIC_PROPAGATION_TRANSPORT_ADAPTERS`.

Evidence level: **host/CI software with deterministic injectable HttpsURLConnection tests**.

## Objective

Provide a concrete JVM/Android-compatible HTTPS GET implementation behind the CP-0008H `PublicPropagationTransport` interface without introducing Android framework dependencies, external provider availability, credentials, radio hardware, or RF testing into required CI.

## Runtime choice

`HttpsUrlConnectionPublicPropagationTransport` uses the standard `javax.net.ssl.HttpsURLConnection` API.

That API is available on the host JVM and on Android, allowing the transport to remain usable by the later Android application layer without making the core transport depend on OkHttp, Retrofit, WorkManager, Compose, or other platform-specific libraries.

Connection creation is injected through `HttpsConnectionFactory`. Production defaults to `URL.openConnection()` plus an explicit `HttpsURLConnection` type check. Deterministic tests provide fake HttpsURLConnection instances and never require external networking.

## Request configuration

For each CP-0008H `PublicPropagationRequest`, the concrete transport:

- parses the exact request URL as a URI;
- requires HTTPS;
- requires a nonblank host;
- rejects user-info credentials;
- rejects URL fragments;
- opens exactly one HTTPS connection;
- uses HTTP GET;
- disables automatic redirects;
- applies explicit connect/read timeouts;
- disables URLConnection caching;
- enables input and disables output;
- sends deterministic `Accept` ordering from the request media types;
- sends `Accept-Charset: UTF-8`;
- sends `Accept-Encoding: identity`;
- sends a bounded, configurable non-secret User-Agent.

Redirects are not followed by the concrete transport. A 3xx response is returned with its actual status for the existing CP-0008H response validator to reject as non-200.

## Timeouts

Default transport timeouts:

- connect: 10 seconds;
- read: 15 seconds.

Both are configurable from 1 through 120 seconds and are validated at construction time.

Socket timeouts become explicit retryable `PublicPropagationTransportException` failures.

## Bounded streaming reads

The transport enforces `request.maxResponseBytes` in two layers:

1. a positive declared Content-Length larger than the request bound is rejected before reading the body;
2. bodies without a trustworthy declared length are streamed in bounded chunks and rejected immediately once cumulative bytes exceed the request bound.

The transport does not allocate an unbounded response buffer.

A response-size violation is a non-retryable contract failure so CP-0008G does not enter rapid retry for a provider payload that violates the configured source bound.

## Content encoding and UTF-8

FieldOps requests `Accept-Encoding: identity`.

If a server nevertheless reports a non-identity Content-Encoding such as gzip, the transport fails closed rather than silently applying decompression with different byte-limit semantics.

Response bytes are decoded using a strict UTF-8 decoder:

- malformed input: rejected;
- unmappable input: rejected;
- replacement-character decoding is not used.

Invalid UTF-8 is a non-retryable contract failure.

## Response metadata

A successful concrete transport call returns the existing CP-0008H `PublicPropagationResponse` carrying:

- exact requested URL;
- connection effective URL;
- HTTP status;
- optional Content-Type;
- decoded body.

The CP-0008H validator remains responsible for exact request/effective-URL equivalence, HTTP-200 requirement, content type acceptance, blank-body handling, and final decoded-body size validation.

This preserves the established separation:

`concrete HTTPS transport -> CP-0008H response validator -> existing provider parser -> CP-0008G refresh coordinator`.

## Error stream semantics

For HTTP status 400 or greater, the concrete transport reads `errorStream` when supplied.

For status below 400, it reads `inputStream`.

The status is never rewritten. A provider 429/5xx remains available to the CP-0008H validator for the already-established retry classification.

## Cleanup

Every opened connection reaches `disconnect()` in a `finally` block.

Any opened input/error stream is managed with `use`, so success, oversize, UTF-8 failure, and read-timeout paths close the stream before connection cleanup.

## Retry classification bridge

CP-0008I adds `PublicPropagationTransportException(retryable=...)`.

`PublicPropagationSourceAdapters` now preserves this explicit transport retryability:

- timeout / I/O / connection-open failures: retryable;
- invalid URL/credentials, unsupported content encoding, oversized response, invalid UTF-8: non-retryable.

Unknown transport exceptions retain the inherited conservative retryable behavior.

Provider parser/schema failures remain non-retryable at the normal source cadence.

## Deterministic testing

`HttpsUrlConnectionPublicPropagationTransportTests` uses fake HttpsURLConnection objects to verify:

- default/configured timeout validation;
- exact URL passed to the factory;
- GET method;
- redirect refusal;
- headers;
- successful response metadata/body;
- non-2xx error-stream handling;
- Content-Length oversize rejection before read;
- streamed oversize rejection;
- strict invalid UTF-8 rejection;
- unexpected compression rejection;
- timeout retryability;
- connection-factory failure retryability;
- credential-bearing URL rejection before connection creation;
- stream close and connection disconnect behavior;
- retryability preservation through CP-0008H source adapters;
- concrete transport integration with the existing NOAA F10.7 parser.

Required CI performs no real HTTP/HTTPS request.

## Platform boundary

Production CP-0008I code contains no:

- Android framework or AndroidX type;
- WorkManager;
- Compose;
- OkHttp or Retrofit dependency;
- account credential, API key, password, certificate, or private-key handling;
- FTX-1, USB, CAT, audio, PTT, or RF behavior;
- QSO/logbook mutation;
- LoTW mutation.

## Evidence boundary

CP-0008I proves the concrete JVM/Android-compatible HTTPS connection behavior and its deterministic integration with the existing transport/source/parser chain.

It does not prove:

- Android application packaging of this class;
- Android network permission or OS scheduling behavior;
- live provider uptime;
- TLS behavior on the actual S23 Ultra;
- Wi-Fi/cellular behavior;
- battery impact;
- WorkManager/background refresh;
- WSPRnet/WSPR.live, GIRO, HFcast, or VOACAP;
- phone/radio/RF behavior.

## Deferred checkpoint rule

CP-0003C remains **DEFERRED**. Do not return to CP-0003C until the owner explicitly says `resume CP-0003C`.

CP-0004A/B/C remain incomplete hardware checkpoints. Skip phone/radio, real credential/account/certificate, RF, and manual hardware validation while the owner override remains active.
