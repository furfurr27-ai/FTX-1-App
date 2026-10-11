#!/usr/bin/env python3
"""Promote CP-0009E only after exact merged-main APK and inherited CI GREEN."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0009E-ANDROID_ARCHIVE_UI_INTEGRATION"
parent = "CP-0009D-ANDROID_APK_BUILD_FOUNDATION"
next_label = "CP-0009F — Android archive UI navigation test and storage-boundary contract"

def one(source, pattern, replacement, label):
    updated, count = re.subn(pattern, lambda _: replacement, source, count=1, flags=re.S)
    if count != 1:
        raise SystemExit("Unexpected " + label + " match count " + str(count))
    return updated

(root / "VERSION").write_text("v56-android-archive-ui-integration\n")
readme_file = root / "README.md"
readme = readme_file.read_text()
match = re.search(r"Host/CI gates:\n\n(.*?)\nEvidence:", readme, re.S)
if not match:
    raise SystemExit("Inherited CI ancestry missing in README")
inherited = match.group(1).strip()
baseline = """## Verified durable baseline

**Latest verified checkpoint:** """ + checkpoint + """

Parent durable checkpoint: """ + parent + """ / v55.

CP-0009E compiles the existing CP-0009C canonical archive-history Kotlin
service into the actual Android debug APK using Kotlin Gradle Android plugin.
A non-exported ArchiveHistoryActivity is reachable from the FieldOps launcher.
Its Select, Delete, Before/After Compare, Previous/Next Page and synthetic
Capture controls dispatch to the unchanged archive workspace state machine.
All canonical provenance notices are shown as readable Android text.

Only synthetic caller-owned propagation pictures are provided. This does NOT
read live propagation providers, CAT, USB audio, microphone, PTT or RF; it
does NOT authenticate origin or persist reports to an Android database.
Screen re-creation starts with synthetic fixtures rather than restored storage.
The UI is compiled in CI but NOT physically run on the Galaxy S23 Ultra.
Hardware tests and actual TalkBack behavior remain unverified.

CP-0003C remains DEFERRED; CP-0004A/B/C are incomplete.

Host/CI gates:

- CP-0009E APK containing real canonical Kotlin core/ArchiveHistoryActivity: **PASS**.
- CP-0009E Android manifest, no-permission and synthetic-only wiring audit: **PASS**.
- CP-0009D actual APK ZIP, aapt package and launcher, artifact upload: **PASS**.
- CP-0009C canonical archive history actions/receipts regression: **PASS**.
""" + inherited + """

Evidence:

- research/android/CP-0009E_ANDROID_ARCHIVE_UI_INTEGRATION.md
- app/src/main/kotlin/dev/n0png/fieldops/app/ArchiveHistoryActivity.kt
- app/src/main/java/dev/n0png/fieldops/app/MainActivity.java
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationArchiveWorkspaceService.kt
- .github/workflows/build-android-apk.yml
- CP-0009E merged-main finalizer run: """ + run_id + """

### Evidence boundary

Android APK compilation and static contracts, plus inherited host functional
tests. No emulator instrumentation, physical phone installation, screen reader
evaluation, disk persistence, RF operation or live FTX-1 integration.

### Inherited verified ancestry

""" + parent + """ and older verified checkpoints remain verified ancestry.

"""
readme = one(readme,
             r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
             baseline + "## Execution tracks and hardware-gated deferrals", "README baseline")
readme = one(readme, r"\*\*Active software track:\*\* [^\n]+",
             "**Active software track:** " + next_label, "README software track")
readme = re.sub(r"- Current Git source baseline: [^\n]+",
                "- Current Git source baseline: " + checkpoint, readme, count=1)
next_section = """## Current exact next action

**""" + next_label + """.**

1. Add CI-testable synthetic archive Android navigation and retained-state
   tests, especially Activity recreation and lifecycle/back behavior.
2. Define explicit storage boundary before Android persistent archives and
   preserve canonical CP-0009C receipts, warnings and FIFO semantics.
3. Continue full APK and inherited host CI. No real RF, phone, USB, account,
   hardware or authenticated-source claims without separate validation.
4. CP-0003C remains DEFERRED; CP-0004A/B/C remain incomplete.

"""
readme = one(readme, r"## Current exact next action\n.*?\n## README maintenance contract",
             next_section + "## README maintenance contract", "README next")
readme = re.sub(r"\n## CP-0009E implementation branch \(not checkpointed\)\n[\s\S]*$", "\n", readme)
readme_file.write_text(readme.rstrip() + "\n")

(root / "NEXT_ACTION.md").write_text("""# NEXT ACTION — FTX-1 FieldOps

## Owner execution override

CP-0003C is **DEFERRED**; do not resume until the owner explicitly says
'resume CP-0003C'. CP-0004A/B/C remain incomplete.
Skip all phone/radio/USB/RF, real accounts/credentials/certificates and manual hardware testing.

## Next software checkpoint

**""" + next_label + """**

Verified Android synthetic archive screen now calls canonical CP-0009C services
from a CI-built debug APK. Next validate navigation/recreation and design an
Android storage boundary without falsely claiming persistent data or phone testing.
Maintain APK build and inherited full host regression gates.
""")

trackfile = root / "SOFTWARE_TRACK.md"
track = trackfile.read_text()
track = one(track,
            r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
            "## Latest verified durable parent\n\n" + checkpoint +
            "\n\n## Deferred but incomplete hardware/account work", "software track baseline")
track = one(track, r"## Active software checkpoint\n\n.*?\n## Resume rule",
            """## Active software checkpoint

""" + next_label + """

Required scope:

- Android archive screen CI navigation and recreation tests using synthetic canonical data
- Design an explicit Android storage contract; no real persistence proof
- Preserve all inherited host tests and real Android APK build upload
- CP-0003C DEFERRED; CP-0004A/B/C remain incomplete

## Resume rule""", "software next")
track = re.sub(r"\n## CP-0009E implementation branch \(not checkpointed\)\n[\s\S]*$", "\n", track)
trackfile.write_text(track.rstrip() + "\n")

historyfile = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = historyfile.read_text()
if "## CP-0009E — Android offline archive history UI integration" not in history:
    history += """

## CP-0009E — Android offline archive history UI integration

Parent: """ + parent + """. The CI-built Android debug APK now includes
the real canonical Kotlin offline archive service and a private Android screen
for synthetic history paging, capture, select, delete and paired comparison.
All provenance notices remain visible; static manifest/UI checks and inherited
host tests GREEN. No physical phone/emulator screen interaction, persistence,
RF, CAT, USB, live propagation, authenticated provider or real LoTW proof.
CP-0003C DEFERRED; CP-0004A/B/C incomplete.

Finalizer run """ + run_id + """.
Evidence: research/android/CP-0009E_ANDROID_ARCHIVE_UI_INTEGRATION.md.
"""
historyfile.write_text(history.rstrip() + "\n")
