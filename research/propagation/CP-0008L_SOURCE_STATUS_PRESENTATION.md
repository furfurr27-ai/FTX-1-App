# CP-0008L — Propagation source-status presentation model

Parent verified durable checkpoint: `CP-0008K-PROPAGATION_REFRESH_STATE_PERSISTENCE`.

Evidence level: **host/CI, deterministic offline stores and fixtures**, not Android/phone/radio/RF testing.

## Read-only status contract

`PropagationSourceStatusService.project(nowUtcMillis)` returns `PropagationSourcesStatusProjection`. It reads `PropagationRefreshStateStore.all()` and `PropagationSnapshotStore.latest()` without fetching providers, calling the refresh coordinator, mutating cache/state, scheduling jobs, or consulting a clock. Sources are sorted by stable source key and duplicate source rows fail closed.

Every source row preserves:
- stable key and declared source role;
- readiness: READY, CADENCE_WAIT, RETRY_BACKOFF or FAILURE_COOLDOWN;
- explicit last-attempt outcome: NEVER_ATTEMPTED, SUCCEEDED or FAILED;
- last attempt UTC, last successful attempt UTC, success age or future-date marker;
- consecutive failure count, retryability, last bounded/sanitized coordinator error;
- exact next eligible UTC and non-negative remaining wait (zero at the inclusive boundary);
- last-good cached evidence count and oldest/newest observed/retrieved UTC, each independently nullable;
- independent evidence freshness counts using the existing source-aware `PropagationSourceFreshnessDefaults.classify` and `PropagationFreshnessClassifier`.

A previously failing source can become READY without losing the FAILED last-attempt marker. A successful fetch with no cached records still reports zero evidence. READY means the existing scheduler may attempt a refresh, **not** that conditions are favorable, data are current, or a provider is accessible.

An empty store gives an empty result with no invented snapshot. The snapshot ID, capture UTC and future-dated marker are exposed as provenance. Evidence matching uses existing role/source provenance rules: forecast product variants are assigned only to the forecast role, planetary observed Kp and F10.7 remain separate, and PSK Reporter evidence is distinct. Freshness is evaluated per observation; a source with mixed fresh/stale/future-dated evidence is not collapsed to one misleading status.

## Runtime composition

`PropagationRuntime.sourceStatus(nowUtcMillis)` is a read-only explicit-UTC companion to `refreshAndProject`, using the same injected snapshot and refresh-state stores. The CP-0008K file-backed store works across new runtime instances. The existing factory, refresh policies, provider adapters, parser contracts and propagation assessment/projection semantics are unchanged.

## Host proof

The focused tests cover:
- empty/missing states and invalid UTC;
- never-attempted readiness; inclusive due-time, countdown and overdue clamp;
- cadence wait, retryable backoff, nonretryable failure cooldown and ready-but-failed status;
- successful refresh without evidence;
- fresh/stale/future-dated per-record classifications and observation/retrieval metadata;
- NOAA observed/forecast/F10.7 role isolation, deterministic ordering and duplicate rejection;
- file-backed state + file-backed snapshots across runtime recreation;
- repeated projection with bit-identical state/snapshot files and zero provider calls.

All verification is deterministic and avoids real HTTP/provider uptime.

## Out of scope / evidence boundary

No actual UI screen, Android framework, WorkManager, lifecycle or network permissions; no new WSPRnet/WSPR.live, GIRO, HFcast/VOACAP sources; no authentication, LoTW/QSO mutation, radio/PTT/USB/RF operations or manual hardware validation. The report shows cached evidence provenance, not a live-provider health guarantee or radio propagation prediction.

CP-0003C remains **DEFERRED** until the owner explicitly says `resume CP-0003C`. CP-0004A/B/C remain incomplete.
