#!/usr/bin/env python3
"""Durably promote CP-0009C after merged-main full host CI, not before."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0009C-ARCHIVE_WORKSPACE_BUILD_READINESS"
parent = "CP-0009B-PROPAGATION_OFFLINE_REPORT_ARCHIVE"
next_label = "CP-0009D — Android Gradle application packaging foundation and APK CI"

def replace_one(source, pattern, replacement, label):
    result, count = re.subn(pattern, lambda _: replacement, source, count=1, flags=re.S)
    if count != 1:
        raise SystemExit("Missing/duplicated " + label + ": " + str(count))
    return result

(root / "VERSION").write_text("v54-archive-workspace-build-readiness\n")
readme_file = root / "README.md"
readme = readme_file.read_text()
match = re.search(r"Host/CI gates:\n\n(.*?)\nEvidence:", readme, re.S)
if not match:
    raise SystemExit("Missing inherited verified host test ancestry")
inherited = match.group(1).strip()
baseline = """## Verified durable baseline

**Latest verified checkpoint:** """ + checkpoint + """

Parent: """ + parent + """ (v53).

CP-0009C is GREEN for the platform-neutral archive history host/workspace interface
and a machine-readable Android packaging GAP audit. It is NOT an installable APK
or a running Android Activity: Gradle wrapper/settings, application module,
AndroidManifest and APK-build workflow are still absent. APK readiness remains RED.

History capture/select/delete, canonical paired historical comparison, safe
eviction selection reconciliation, accessible plain-text rows and provenance
warnings are verified with synthetic host tests. CP-0009A/B canonical receipts,
bounded archive rules and inherited regression gates remain preserved.
No live CAT/RF, real Android DB/filesystem, authenticated origin or device proof.

CP-0003C remains DEFERRED; CP-0004A/B/C remain incomplete.

Host/CI gates:

- CP-0009C archive selection workspace host tests: **PASS**.
- CP-0009C static Android APK packaging prerequisite audit: **NOT READY (verified gap)**.
""" + inherited + """

Evidence:

- research/propagation/CP-0009C_ARCHIVE_WORKSPACE_BUILD_READINESS.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationArchiveWorkspaceService.kt
- core/src/test/kotlin/dev/n0png/fieldops/core/PropagationArchiveWorkspaceTests.kt
- scripts/test_propagation_archive_workspace.sh
- scripts/check_android_build_readiness.py
- CP-0009C main-branch finalizer: """ + run_id + """

### Evidence boundary

Host Kotlin model and truthful prerequisite detection only; no gradle build,
Android Activity, working APK, installation, USB/audio, RF or accounts proof.

### Verified ancestry

""" + parent + """ and earlier verified checkpoints remain ancestry.

"""
readme = replace_one(readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals", "README baseline")
readme = replace_one(readme, r"\*\*Active software track:\*\* [^\n]+",
    "**Active software track:** " + next_label, "README active software track")
readme = re.sub(r"- Current Git source baseline: [^\n]+",
    "- Current Git source baseline: " + checkpoint, readme, count=1)
next_section = """## Current exact next action

**""" + next_label + """.**

1. Create a Gradle wrapper, Android project settings, installable app module,
   AndroidManifest and deterministic GitHub Actions debug APK build gate.
2. Integrate the existing host archive workspace into an actual platform host
   incrementally; preserve safe no-transmit defaults and the canonical history.
3. Do not claim phone/radio/USB/RF operation, on-device persistence or verified
   APK install until each is independently proven.
4. CP-0003C DEFERRED; CP-0004A/B/C incomplete.

"""
readme = replace_one(readme,
    r"## Current exact next action\n.*?\n## README maintenance contract",
    next_section + "## README maintenance contract", "README next")
readme = re.sub(r"\n## CP-0009C implementation branch \(not checkpointed\)\n[\s\S]*$", "\n", readme)
readme_file.write_text(readme.rstrip() + "\n")

(root / "NEXT_ACTION.md").write_text("""# NEXT ACTION — FTX-1 FieldOps

## Owner execution override

CP-0003C is **DEFERRED**; do not resume until explicitly instructed 'resume CP-0003C'.
Skip any checkpoint requiring phone/radio, real accounts/credentials/certificates,
RF or manual hardware validation. CP-0004A/B/C remain incomplete.

## Next software checkpoint

**""" + next_label + """**

Build and CI-test an actual Android Gradle application packaging foundation
without requiring a physical phone, FTX-1 or accounts. Treat CP-0009C as host-only
archive controls plus honest red Android APK gap audit; keep all prior regression
contracts. Do not call the APK installable until GitHub CI proves a build artifact.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = replace_one(track,
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n" + checkpoint +
    "\n\n## Deferred but incomplete hardware/account work", "track baseline")
track = replace_one(track, r"## Active software checkpoint\n\n.*?\n## Resume rule",
    """## Active software checkpoint

""" + next_label + """

Required scope:

- Actual Android application module, manifest, Gradle wrapper and APK CI build
- Incremental safe host integration with CP-0009C history selection state
- No on-device database, real RF, CAT/audio or physical device claims
- CP-0003C DEFERRED; CP-0004A/B/C remain incomplete

## Resume rule""", "track next")
track = re.sub(r"\n## CP-0009C implementation branch \(not checkpointed\)\n[\s\S]*$", "\n", track)
track_path.write_text(track.rstrip() + "\n")

history_file = root / "checkpoints/CHECKPOINT_HISTORY.md"
h = history_file.read_text()
if "## CP-0009C — Archive workspace and Android build-readiness gap" not in h:
    h += """

## CP-0009C — Archive workspace and Android build-readiness gap

Parent: """ + parent + """. Host-only archive selection, navigation, deletion,
capturing a caller-supplied operating picture, original-paired historical
comparison, eviction reconciliation and typed accessible rows. Kotlin host
tests and inherited CI GREEN. Repository static Android packaging GAP audit
honestly reports NOT_READY because Gradle application scaffolding and APK
workflow are missing. This is NOT a built Android app, Activity or installable
APK. No Android persistence, RF, hardware or authenticated-origin proof.
CP-0003C DEFERRED; CP-0004A/B/C incomplete.

Finalizer: """ + run_id + """.
Evidence: research/propagation/CP-0009C_ARCHIVE_WORKSPACE_BUILD_READINESS.md
"""
    history_file.write_text(h.rstrip() + "\n")
