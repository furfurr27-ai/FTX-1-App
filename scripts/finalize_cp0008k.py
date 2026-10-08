#!/usr/bin/env python3
"""Promote CP-0008K handoff only after required green GitHub Actions jobs."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0008K-PROPAGATION_REFRESH_STATE_PERSISTENCE"
next_checkpoint = "CP-0008L — propagation source-status presentation model"

def replace_one(value, pattern, replacement, label):
    updated, count = re.subn(pattern, lambda _: replacement, value, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected exactly one {label} section; found {count}")
    return updated

(root / "VERSION").write_text("v36-propagation-refresh-state-persistence\n")
readme_path = root / "README.md"
readme = readme_path.read_text()
match = re.search(r"Host/CI gates:\n\n(.*?)\nEvidence:", readme, re.S)
if not match:
    raise SystemExit("Missing inherited host/CI gate inventory")
inherited = match.group(1).replace("- CP-0008J propagation runtime composition: **67/67 PASS**.", "")
baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** {checkpoint}

Parent durable checkpoint: CP-0008J-PROPAGATION_RUNTIME_COMPOSITION.

CP-0003C and CP-0004A/B/C remain explicitly deferred hardware/account checkpoints and are not implied complete by this software checkpoint.

CP-0008K is a GREEN host/CI checkpoint. It provides explicit file-backed propagation refresh-state persistence across runtime recreation without modifying upstream propagation providers or cadence policies.

CP-0008K proves:

- FilePropagationRefreshStateStore implements PropagationRefreshStateStore and is explicitly injectable through the existing PropagationRuntimeFactory refreshStateStore argument.
- The default runtime remains in-memory; no Android application lifecycle, scheduler or storage directory is invented.
- Versioned deterministic binary serialization persists each source key and role, last attempt UTC, last success UTC, consecutive failures, next eligible refresh UTC, last failure message and retryability.
- Strict version, corruption, duplicate-source, role/source, length, trailing-data and state-validation boundaries fail closed.
- Atomic file replacement uses a same-directory temporary file and has no non-atomic move fallback.
- Failed atomic replacement leaves the previous valid state file bit-for-bit unchanged and does not mutate the in-memory state.
- Runtime recreation retains five source states and expected 4/5/10-minute source cadence.
- Failure retryability, consecutive failures, message and backoff eligibility survive process/runtime recreation.
- Host/CI uses deterministic filesystem tests and an offline synthetic provider transport; no live provider, phone or radio validation is claimed.
- CP-0008J finalizer was changed to manual-only before merge.

Host/CI gates:

- CP-0008K refresh-state persistence: **PASS**.
- CP-0008J propagation runtime composition: **67/67 PASS**.
{inherited.strip()}

Evidence:

- research/propagation/CP-0008K_REFRESH_STATE_PERSISTENCE.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/FilePropagationRefreshStateStore.kt
- core/src/test/kotlin/dev/n0png/fieldops/core/PropagationRefreshStatePersistenceTests.kt
- SOFTWARE_TRACK.md
- CP-0008K finalization workflow run: {run_id}

### Evidence boundary

CP-0008K proves JVM filesystem persistence, atomic *replacement*, fail-closed validation and deterministic runtime recreation with injected store. It does not prove Android application integration, fsync/power-loss durability, multiprocess concurrent writers, persistent snapshots when the separately injected snapshot store remains in-memory, live provider availability, device/radio/RF behavior or new propagation providers.

### Inherited verified ancestry

CP-0008J-PROPAGATION_RUNTIME_COMPOSITION and all earlier verified checkpoints remain verified ancestry.

"""
readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals",
    "verified durable baseline",
)
readme = replace_one(
    readme, r"\*\*Active software track:\*\* [^\n]+",
    "**Active software track:** " + next_checkpoint,
    "active software track",
)
readme = re.sub(
    r"- Current Git source baseline: [^\n]+",
    "- Current Git source baseline: " + checkpoint,
    readme, count=1,
)
next_text = f"""## Current exact next action

**{next_checkpoint}.**

1. Define a platform-neutral source-status projection model using existing persisted source states, explicit failure/backoff status and last-good evidence timestamps.
2. Keep provider cadence and evidence semantics unchanged, prove deterministic host/CI gates and advance only through an immutable checkpoint.
3. Do not add Android lifecycle, WorkManager, permissions, provider network dependency, credentials, or hardware testing without separate authorization.
4. CP-0003C remains DEFERRED until the owner explicitly says resume CP-0003C.

"""
readme = replace_one(readme,
    r"## Current exact next action\n.*?\n## README maintenance contract",
    next_text + "## README maintenance contract",
    "current exact next action",
)
readme_path.write_text(readme)

(root / "NEXT_ACTION.md").write_text(f"""# NEXT ACTION — FTX-1 FieldOps

## Execution override

CP-0003C is **DEFERRED**. Do not return to CP-0003C until the owner explicitly says resume CP-0003C.

Skip any checkpoint that requires the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. A skipped checkpoint remains incomplete.

## Next software checkpoint

**{next_checkpoint}**

Add a platform-neutral read-only projection of per-source refresh readiness, freshness and last error using the existing CP-0008K persisted refresh-state store and the earlier snapshot/evidence model. Do not change any provider cadence/parser/cache/evidence semantics. Prove entirely with deterministic GitHub/CI, checkpoint-first.
""")
track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = replace_one(track,
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n" + checkpoint +
    "\n\n## Deferred but incomplete hardware/account work",
    "software track parent",
)
track = replace_one(track,
    r"## Active software checkpoint\n\n.*?\n## Resume rule",
    f"""## Active software checkpoint

CP-0008L-PROPAGATION_SOURCE_STATUS_PRESENTATION

Required scope:

- read-only, platform-neutral source refresh/status presentation model
- explicit remaining wait time, failure/backoff and freshness; no invented propagation evidence
- preserve CP-0008K persisted refresh-state and existing runtime factory composition
- no Android lifecycle, WorkManager, background scheduling or permission work
- no new propagation providers or external provider dependency in CI
- no credentials/accounts/phone/radio/RF/manual testing
- CP-0003C remains DEFERRED; CP-0004A/B/C remain incomplete

## Resume rule""",
    "software track active checkpoint",
)
track_path.write_text(track)
history_path = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0008K — Propagation refresh-state persistence" not in history:
    history += f"""

## CP-0008K — Propagation refresh-state persistence

Parent: CP-0008J-PROPAGATION_RUNTIME_COMPOSITION.

Versioned and strictly validated per-source refresh-state serialization now survives injected runtime/store recreation. A same-directory atomic replace protects the last valid file on failed writes; corrupt/duplicate/version/role mismatches fail closed. The unchanged factory injection keeps network providers, cadence, snapshots and projection separate. CI tests synthetic provider failures without real network requests.

Verification: CP-0008K focused persistence PASS; CP-0008J runtime 67 PASS; all inherited regression jobs PASS. Finalizer run: {run_id}.

Deferred hardware checkpoints CP-0003C and CP-0004A/B/C remain incomplete.

Evidence: research/propagation/CP-0008K_REFRESH_STATE_PERSISTENCE.md, FilePropagationRefreshStateStore.kt, PropagationRefreshStatePersistenceTests.kt.
"""
    history_path.write_text(history)
