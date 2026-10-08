# CP-0008M — Propagation operating-picture read-model composition

Parent durable checkpoint: `CP-0008L-PROPAGATION_SOURCE_STATUS_PRESENTATION`.

Scope: **platform-neutral deterministic host/CI**; no actual Android screen or live receiver/prediction.

## Contract

`PropagationOperatingPictureService.read(query)` constructs a lossless `PropagationOperatingPicture` holding the existing `PropagationWorkspaceProjection` (nullable if no snapshot) alongside the existing `PropagationSourcesStatusProjection`, both evaluated at exactly `query.nowUtcMillis`.

The service captures a **single snapshot** using `PropagationSnapshotStore.latest()` once, and calls `PropagationRefreshStateStore.all()` once, passing those immutable-at-read inputs to the existing `PropagationWorkspaceProjectionService.project(snapshot, query)` and new `PropagationSourceStatusService.projectFrom(now, states, snapshot)`. This prevents two independent `latest()` calls from selecting different snapshots if the store changes between reads. The original `PropagationSourceStatusService.project(now)` behavior remains unchanged; its new explicit-data overload preserves the same classification.

The composed model validates that snapshot ID, capture timestamp and future-dated markers are equal across both projections, and that both share the same requested UTC. It does not alter either view's filters, ordering, metadata, source provenance, source-specific freshness policies, selected-path assessments or offline-cache distinctions. Workspace filters never erase per-source failure/eligibility or underlying evidence counts. A missing snapshot produces a null workspace and a source-status view with zero cached evidence, not fabricated data.

`PropagationRuntime.operatingPicture(query)` and its explicit-UTC convenience overload use existing injected runtime stores, preserving independent `sourceStatus` and `refreshAndProject` APIs. No provider request, refresh, cache write or scheduler trigger occurs.

## Verification

Deterministic synthetic tests prove: one `latest()` call despite a store that changes its result on successive calls; one state-list capture; identical timestamp/snapshot fields; exact equality to earlier independent workspace/status projections on a stable store; normal workspace source filters leaving the source health rows untouched; old/new/stale/future evidence metadata; no-cache and no-provider cases; validation of mismatched composite objects; bit-identical file-backed data, runtime recreation and no network interaction.

## Limitations and deferred work

Snapshot selection and state-list reads are sequential, **not a cross-store atomic transaction** or concurrency lock. No claim of truly simultaneous refresh state and snapshot commits is made. Cached evidence status is not a live-provider health check, observed RF performance or HF forecast. No Android lifecycle, UI widget, scheduler, WorkManager, permissions, manual hardware test, real credentials/accounts, new providers, transmission or QSO/LoTW effects.

CP-0003C remains **DEFERRED** until the owner explicitly says `resume CP-0003C`. CP-0004A/B/C remain incomplete.
