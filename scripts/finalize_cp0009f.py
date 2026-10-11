#!/usr/bin/env python3
"""CP-0009F: promote only after merged-main Robolectric, APK and host CI gates."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0009F-ANDROID_ARCHIVE_NAVIGATION_TESTS"
parent = "CP-0009E-ANDROID_ARCHIVE_UI_INTEGRATION"
next_label = "CP-0009G — bounded opt-in Android archive persistence contract"

def replace_one(content, pattern, replacement, label):
    updated, count = re.subn(pattern, lambda _: replacement, content, count=1, flags=re.S)
    if count != 1:
        raise SystemExit("Unexpected " + label + " count=" + str(count))
    return updated

(root / "VERSION").write_text("v57-android-archive-navigation-tests\n")
readme_file = root / "README.md"
readme = readme_file.read_text()
match = re.search(r"Host/CI gates:\n\n(.*?)\nEvidence:", readme, re.S)
if not match:
    raise SystemExit("Cannot find previous host/CI evidence ancestry")
inherited = match.group(1).strip()
baseline = """## Verified durable baseline

**Latest verified checkpoint:** """ + checkpoint + """

Parent verified durable checkpoint: """ + parent + """ / v56.

CP-0009F verifies clickable Android archive-history navigation via Robolectric
API 35 JVM Activity tests. Select, Before/After Compare, Delete, Page, Clear
and Synthetic Capture UI actions dispatch to the canonical CP-0009C host
workspace. Android launcher navigation is covered, too.

Transient rotation-only retention uses onRetainNonConfigurationInstance
with a bounded, in-memory workspace state holder. Fresh sessions reconstruct
three synthetic demo captures and do NOT persist reports or retrieve live
radio/network data. No Android database, file store or real-world RF state
is proven or requested.

Robolectric CI is virtualized JVM Android UI verification, not a physical
Galaxy S23 Ultra installation, actual TalkBack interaction, radio CAT,
audio/PTT/RF or live propagation validation.

CP-0003C remains DEFERRED; CP-0004A/B/C incomplete.

Host/CI gates:

- CP-0009F Robolectric API 35 Android Activity navigation tests: **PASS**.
- CP-0009F rotation-only storage-boundary static contract: **PASS**.
- CP-0009F actual debug APK packaging/aapt/ZIP/Actions artifact: **PASS**.
""" + inherited + """

Evidence:

- research/android/CP-0009F_ANDROID_ARCHIVE_NAVIGATION_TESTS.md
- app/src/test/java/dev/n0png/fieldops/app/ArchiveHistoryActivityTest.java
- app/src/main/kotlin/dev/n0png/fieldops/app/ArchiveHistoryActivity.kt
- .github/workflows/build-android-apk.yml
- CP-0009F merged-main finalizer: """ + run_id + """

### Evidence boundary

Robolectric Activity JVM simulation, static storage contract, APK packaging
and inherited host tests. No actual phone install, hardware RF, real data,
accessibility device testing, archive persistence or account verification.

### Inherited verified ancestry

""" + parent + """ and earlier immutable checkpoints remain ancestry.

"""
readme = replace_one(readme, r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals", "README verified baseline")
readme = replace_one(readme, r"\*\*Active software track:\*\* [^\n]+",
    "**Active software track:** " + next_label, "README software track")
readme = re.sub(r"- Current Git source baseline: [^\n]+",
    "- Current Git source baseline: " + checkpoint, readme, count=1)
next_section = """## Current exact next action

**""" + next_label + """.**

1. Design explicit opt-in Android local archive persistence using canonical
   receipts, bounded byte/count retention, and atomic integrity checks.
2. Verify process-death restoration and intentionally corrupted archives
   under synthetic CI fixtures before claiming persistent storage.
3. Preserve Robolectric UI controls, actual APK build and inherited host
   regression gates; no physical hardware or real RF/account validation.
4. CP-0003C DEFERRED; CP-0004A/B/C incomplete.

"""
readme = replace_one(readme, r"## Current exact next action\n.*?\n## README maintenance contract",
    next_section + "## README maintenance contract", "README next")
readme = re.sub(r"\n## CP-0009F implementation branch \(not checkpointed\)\n[\s\S]*$", "\n", readme)
readme_file.write_text(readme.rstrip() + "\n")

(root / "NEXT_ACTION.md").write_text("""# NEXT ACTION — FTX-1 FieldOps

## Owner execution override

CP-0003C is **DEFERRED** until the owner explicitly says 'resume CP-0003C'.
CP-0004A/B/C remain incomplete. Skip physical radio, phone, USB/RF tests,
real accounts/credentials/certificates and other manual hardware validation.

## Next software checkpoint

**""" + next_label + """**

The CP-0009F Android Activity navigation and rotation-only transient history
contract now has Robolectric API 35 CI coverage and APK packaging evidence.
Develop bounded canonical opt-in local history storage with explicit
process-death/integrity failure semantics, synthetic-only CI, and no false
claims of physical-device install or radio operation.
""")

trackfile = root / "SOFTWARE_TRACK.md"
track = trackfile.read_text()
track = replace_one(track,
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n" + checkpoint +
    "\n\n## Deferred but incomplete hardware/account work", "software baseline")
track = replace_one(track, r"## Active software checkpoint\n\n.*?\n## Resume rule",
    """## Active software checkpoint

""" + next_label + """

Scope: gated, opt-in, bounded on-device local archive persistence with
canonical receipt validation, corrupt-store handling and process-death
restore tests. Preserve Robolectric UI checks, real APK artifact build and
all host regressions. CP-0003C DEFERRED; CP-0004A/B/C incomplete.

## Resume rule""", "software next")
track = re.sub(r"\n## CP-0009F implementation branch \(not checkpointed\)\n[\s\S]*$", "\n", track)
trackfile.write_text(track.rstrip() + "\n")

historyfile = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = historyfile.read_text()
if "## CP-0009F — Android archive navigation tests and storage boundary" not in history:
    history += """

## CP-0009F — Android archive navigation tests and storage boundary

Parent: """ + parent + """. Robolectric API 35 runs actual Android View
click/navigation/rotation tests with synthetic canonical reports. Activity
retains caller-owned memory across configuration changes only, never disk
or saved Android Bundle. Fresh sessions reconstruct demo fixtures. APK,
manifest, and host inherited gates GREEN. Does not prove physical phone,
persistent storage, real RF/CAT/PTT/USB, account or live data operation.
CP-0003C DEFERRED; CP-0004A/B/C incomplete.

Finalizer: """ + run_id + """.
Evidence: research/android/CP-0009F_ANDROID_ARCHIVE_NAVIGATION_TESTS.md.
"""
historyfile.write_text(history.rstrip() + "\n")
