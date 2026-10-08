# CP-0008J — Propagation runtime composition

Parent durable checkpoint: `CP-0008I-CONCRETE_PUBLIC_HTTPS_TRANSPORT`.

Evidence level: **host/CI software with deterministic injected transport and storage**.

## Objective

Compose the verified propagation stack into one platform-neutral manually-invoked runtime without adding Android lifecycle, WorkManager, background scheduling, credentials, radio hardware, or new propagation providers.

The runtime composes:

- CP-0008I concrete HTTPS transport by default;
- all five CP-0008H public source definitions;
- CP-0008G refresh coordinator and refresh-state store;
- propagation snapshot storage;
- CP-0008F workspace projection service.

## Runtime configuration

`PropagationRuntimeConfig` requires an explicit operator callsign.

It also carries:

- an explicit `PskReporterPublicQuery`;
- explicit refresh policies for each public source;
- concrete HTTPS transport configuration.

The default PSK Reporter query uses the configured operator callsign, but callers may explicitly target a different callsign. The runtime does not infer station geography, Maidenhead grid, latitude, or longitude from a callsign.

## Safe refresh-policy defaults

`PropagationRuntimeRefreshPolicies` defaults to:

- NOAA observed planetary Kp: 15 minutes;
- NOAA Kp forecast: 15 minutes;
- NOAA F10.7: 60 minutes;
- NOAA GloTEC: 10 minutes;
- PSK Reporter: 5 minutes.

Default retry policy for each source:

- initial retry backoff: 1 minute;
- maximum retry backoff: 15 minutes.

The configuration rejects:

- GloTEC cadence below the pinned 10-minute product cadence;
- PSK Reporter cadence below the documented five-minute retrieval minimum.

The existing CP-0008H source adapters continue enforcing these provider minimums as a second boundary.

## Runtime factory

`PropagationRuntimeFactory.create` composes the five verified source definitions in deterministic source order:

- `NOAA_SWPC_PLANETARY_KP`
- `NOAA_SWPC_KP_FORECAST`
- `NOAA_SWPC_F107_SUMMARY`
- `NOAA_SWPC_GLOTEC_VTEC`
- `PSK_REPORTER_PUBLIC_QUERY`

The runtime exposes source keys sorted deterministically.

The factory accepts injected:

- `PublicPropagationTransport`;
- `PropagationSnapshotStore`;
- `PropagationRefreshStateStore`;
- `PropagationSnapshotAggregator`;
- `PropagationAssessmentEngine`.

Production defaults use:

- `HttpsUrlConnectionPublicPropagationTransport`;
- `InMemoryPropagationSnapshotStore`;
- `InMemoryPropagationRefreshStateStore`.

This keeps runtime composition portable while allowing later Android/application storage adapters without changing provider or refresh logic.

## Manual refresh-and-project entry point

`PropagationRuntime.refreshAndProject` is the single runtime entry point.

It accepts either:

- an existing `PropagationProjectionQuery`; or
- an explicit UTC plus optional projection filter and selected-path assessment.

The runtime does not read wall-clock time internally. The caller supplies UTC.

The call performs:

`public sources -> refresh coordinator -> snapshot store -> workspace projection`

Source cadence, retry/backoff, canonical NOAA Kp selection, partial failure behavior, last-good snapshot behavior, source freshness, and projection filtering all remain inherited from prior verified checkpoints.

## Deterministic integration proof

`PropagationRuntimeTests` uses a recording in-memory fake transport plus the pinned provider fixtures.

The full composition test verifies:

- five source definitions are created;
- six transport requests occur on a full refresh because GloTEC uses index plus artifact;
- canonical observed Kp remains the dedicated planetary-Kp feed;
- forecast-product observed Kp remains excluded;
- estimated/predicted Kp remain;
- F10.7, GloTEC, and PSK Reporter evidence reach the snapshot;
- the saved snapshot is immediately projected;
- no modeled paths are invented.

Cadence tests verify:

- at four minutes, all five sources are skipped;
- at five minutes, only PSK Reporter refreshes;
- at ten minutes, GloTEC and PSK Reporter refresh.

Failure tests verify:

- a PSK Reporter HTTP 503 remains an explicit retryable source failure;
- when it is the only eligible source and fails, no new snapshot is written;
- the runtime still projects the last good snapshot;
- source failure state remains inspectable.

Injected storage/filter tests verify that caller-provided stores are used and projection filters remain authoritative.

## Identity and geography boundary

The runtime requires an explicit operator callsign but stores no:

- latitude;
- longitude;
- Maidenhead grid;
- inferred station location.

PSK Reporter endpoint geography continues to come only from explicit provider locators normalized by CP-0008D.

No station geography is derived from callsign prefixes.

## Platform boundary

Production CP-0008J runtime composition contains no:

- Android framework or AndroidX type;
- WorkManager;
- Android application lifecycle;
- Android network-permission handling;
- account credential, API key, password, certificate, or private-key handling;
- FTX-1, USB, CAT, audio, PTT, or RF behavior;
- QSO/logbook mutation;
- LoTW mutation;
- new provider implementation.

## Evidence boundary

CP-0008J proves the platform-neutral runtime composition and manual invocation semantics.

It does not prove:

- Android app integration;
- WorkManager or background scheduling;
- Android network permission handling;
- process restart persistence for the default in-memory stores;
- live provider uptime;
- WSPRnet/WSPR.live;
- GIRO;
- HFcast/VOACAP;
- real phone/radio/RF behavior.

## Deferred checkpoint rule

CP-0003C remains **DEFERRED**. Do not return to CP-0003C until the owner explicitly says `resume CP-0003C`.

CP-0004A/B/C remain incomplete hardware checkpoints. Skip phone/radio, real credential/account/certificate, RF, and manual hardware validation while the owner override remains active.
