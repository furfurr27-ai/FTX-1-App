#!/usr/bin/env python3
"""CP-0009D durable handoff after CI produced a real Android debug APK."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0009D-ANDROID_APK_BUILD_FOUNDATION"
parent = "CP-0009C-ARCHIVE_WORKSPACE_BUILD_READINESS"
next_label = "CP-0009E — Android archive-history screen and offline UI integration"

def replace_one(source: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, lambda _: replacement, source, count=1, flags=re.S)
    if count != 1:
        raise SystemExit("Missing/ambiguous " + label + ": " + str(count))
    return updated

(root / "VERSION").write_text("v55-android-apk-build-foundation\n")
readme_file = root / "README.md"
readme = readme_file.read_text()
match = re.search(r"Host/CI gates:\n\n(.*?)\nEvidence:", readme, re.S)
if not match:
    raise SystemExit("Missing verified ancestry host gates")
inherited = match.group(1).strip()
baseline = """## Verified durable baseline

**Latest verified checkpoint:** """ + checkpoint + """

Parent durable checkpoint: """ + parent + """ / v54.

CP-0009D adds a genuine CI-built Android debug APK application shell
built using pinned Gradle 8.13 and Android Gradle Plugin 8.13.2. It contains
an exported launcher Activity, offline provenance banners and no requested
Android runtime permissions. GitHub Actions independently checked the actual
APK archive, Android package and launcher, SHA-256 and uploaded build artifact.
It is a packaging proof, NOT a fully working FTX-1 companion or proof of
installing/launching on the physical Galaxy S23 Ultra.

CP-0009C host-only archive/history contracts and all inherited regressions
remain preserved but are NOT yet attached to the Android Activity. There is
no Android archive database or tested RF/CAT/USB/audio/PTT operation.

CP-0003C remains DEFERRED; CP-0004A/B/C remain incomplete.

Host/CI gates:

- CP-0009D Android assembleDebug real APK build and artifact inspection: **PASS**.
- CP-0009D offline-only AndroidManifest/launcher safety contract: **PASS**.
- CP-0009D Gradle wrapper pinned source object verification: **PASS**.
- CP-0009D static Android packaging prerequisites: **PRESENT** (not itself a build).
""" + inherited + """

Evidence:

- research/android/CP-0009D_ANDROID_APK_BUILD_FOUNDATION.md
- app/src/main/java/dev/n0png/fieldops/app/MainActivity.java
- app/src/main/AndroidManifest.xml
- gradle/wrapper/gradle-wrapper.jar (upstream v8.13.0 Git blob pin)
- .github/workflows/build-android-apk.yml
- scripts/verify_android_apk_artifact.py
- CP-0009D merged-main finalizer: """ + run_id + """

### Evidence boundary

Only CI debug APK packaging and structure proven. No physical device install,
Android UI automation, actual core archive binding, radio, CAT, RF, transmit,
LoTW or real account/credential proof.

### Inherited verified ancestry

""" + parent + """ and older verified checkpoints remain ancestry.

"""
readme = replace_one(readme, r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
                     baseline + "## Execution tracks and hardware-gated deferrals", "README baseline")
readme = replace_one(readme, r"\*\*Active software track:\*\* [^\n]+",
                     "**Active software track:** " + next_label, "README track")
readme = re.sub(r"- Current Git source baseline: [^\n]+",
                "- Current Git source baseline: " + checkpoint, readme, count=1)
next_section = """## Current exact next action

**""" + next_label + """.**

1. Bind the CP-0009C canonical archive history workspace into the packaged
   Android Activity using platform-safe, offline-only UI patterns.
2. Add deterministic Android CI tests for selection, paging and comparison
   plus accessibility and navigation semantics. Preserve safe no-TX defaults.
3. Do not claim live FTX-1/USB/RF operation, Android archive persistence or
   physical device install until independently verified.
4. CP-0003C remains DEFERRED and CP-0004A/B/C remain incomplete.

"""
readme = replace_one(readme, r"## Current exact next action\n.*?\n## README maintenance contract",
                     next_section + "## README maintenance contract", "README next")
readme = re.sub(r"\n## CP-0009D implementation branch \(not checkpointed\)\n[\s\S]*$", "\n", readme)
readme_file.write_text(readme.rstrip() + "\n")

(root / "NEXT_ACTION.md").write_text("""# NEXT ACTION — FTX-1 FieldOps

## Owner execution override

CP-0003C is **DEFERRED**. Do not resume until explicitly instructed
'resume CP-0003C'. CP-0004A/B/C remain incomplete.
Skip phone/radio/USB/RF, real accounts/credentials/certificates and all
manual hardware validation.

## Next software checkpoint

**""" + next_label + """**

CI-built debug APK packaging foundation is complete, but the Activity remains
a read-only developer shell. Connect CP-0009C canonical archive selection,
history and paired comparison models to Android UI with CI-testable synthetic
offline data. Maintain 43+ inherited host regressions and rebuild APK in CI.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = replace_one(track,
                    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
                    "## Latest verified durable parent\n\n" + checkpoint +
                    "\n\n## Deferred but incomplete hardware/account work", "software track parent")
track = replace_one(track, r"## Active software checkpoint\n\n.*?\n## Resume rule",
                    """## Active software checkpoint

""" + next_label + """

Required scope:

- Integrate canonical caller-owned offline archive history with packaged Android UI
- Preserve Android APK GitHub Actions compile, artifact and no-TX safety evidence
- Enforce original-paired comparison and provenance notices in actual UI tests
- CP-0003C DEFERRED; CP-0004A/B/C incomplete; no live RF or physical install claim

## Resume rule""", "software track next")
track = re.sub(r"\n## CP-0009D implementation branch \(not checkpointed\)\n[\s\S]*$", "\n", track)
track_path.write_text(track.rstrip() + "\n")

hfile = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = hfile.read_text()
if "## CP-0009D — Android debug APK packaging foundation" not in history:
    history += """

## CP-0009D — Android debug APK packaging foundation

Parent: """ + parent + """. Verified actual Gradle Android debug APK build,
package/launcher and ZIP structure through GitHub Actions; Gradle wrapper pin,
offline launcher and zero requested permissions. Uploaded APK artifact and
SHA-256 in GitHub CI. Host archive still separate and NO phone install,
radio/USB/RF, source authentication or on-device history storage verified.
CP-0003C DEFERRED; CP-0004A/B/C incomplete.

Finalizer: """ + run_id + """.
Evidence: research/android/CP-0009D_ANDROID_APK_BUILD_FOUNDATION.md.
"""
hfile.write_text(history.rstrip() + "\n")
