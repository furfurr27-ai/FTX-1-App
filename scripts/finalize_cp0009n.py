#!/usr/bin/env python3
"""CP-0009N main-only checkpoint promotion after the exact merged-main CI passes."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0009N-EXPLICIT_ENCRYPTED_ARCHIVE_RECOVERY_CHOICE"
parent = "CP-0009M-ENCRYPTED_ARCHIVE_CONFLICT_INSPECTION"
next_label = "CP-0009O — read-only recovery audit trail and provenance UX"

def replace_once(value, pattern, replacement, name):
    new, count = re.subn(pattern, lambda _: replacement, value, count=1, flags=re.S)
    if count != 1:
        raise SystemExit("Expected one " + name + ", got " + str(count))
    return new

(root / "VERSION").write_text("v65-explicit-encrypted-archive-recovery-choice\n")

readme_path = root / "README.md"
readme = readme_path.read_text()
match = re.search(r"Host/CI gates:\n\n(.*?)\nEvidence:", readme, re.S)
if match is None:
    raise SystemExit("Missing inherited host/CI regression evidence")
inherited = match.group(1).strip()
baseline = """## Verified durable baseline

**Latest verified checkpoint:** """ + checkpoint + """

Parent durable checkpoint: """ + parent + """ / v64.

CP-0009N adds explicit, two-step in-memory recovery from independently
authenticated original/rotated V2 encrypted archive candidates. Both
separate opt-in sessions must be enabled. Inspection yields only
AUTHENTICATED candidate choices, while preserving CP-0009M missing,
key-unavailable, unreadable, equal and conflicting candidate diagnostics.
The user must separately SELECT an original or rotated candidate and
CONFIRM the same candidate. Before any UI replacement, the service
re-inspects BOTH candidates and reauthenticates/re-decodes the selected
copy using its existing key, then verifies the prior canonical digest
and entry count. Stale/changed/invalid choices leave on-screen history
unchanged and never automatically fall back to other V2 or plaintext V1.

The chosen archive is loaded into synthetic in-memory display state ONLY.
No archive files are written/deleted/replaced, no new keys provisioned,
no keys retired, and no copy automatically preferred. Original V2,
rotated V2 and V1 plaintext files remain in their existing storage.
Separate existing opt-in restore/delete buttons are preserved. This
guard improves explicit choice/failure behavior, not cryptographic recency:
FRESHNESS NOT VERIFIED. Authentic older ciphertext is still replayable.
There is no independent trusted monotonic witness or station provenance.

A separate READ-ONLY retirement-readiness checklist always returns
BLOCKED_NON_EXECUTING. It identifies missing rollback-resistant freshness
proof, separately verified backup/authorization, actual target device and
filesystem/Keystore validation, uncertain flash secure deletion, and
independent V1/V2 retention decisions. It does not retire any keys.

Robolectric software-key tests cover both candidate choices, matching and
conflicting valid archives, changed ciphertext, missing key, file deletion,
session opt-ins, unchanged ciphertext and display failure isolation.
Android Activity tests verify explicit review controls and retirement
warnings. Real Galaxy S23 Ultra, FTX-1 CAT/USB/audio/RF, Keystore
filesystem/hard-link/power-loss and credentials/account tests remain
incomplete. CP-0003C DEFERRED; CP-0004A/B/C incomplete.

Host/CI gates:

- CP-0009N explicit recovery, stale-selection refusal Robolectric: **PASS**.
- CP-0009N Android review/retirement UI and APK packaging: **PASS**.
- CP-0009N full inherited Kotlin/Android host regression matrix: **PASS**.
""" + inherited + """

Evidence:

- app/src/main/kotlin/dev/n0png/fieldops/app/ExplicitEncryptedArchiveRecovery.kt
- app/src/test/kotlin/dev/n0png/fieldops/app/ExplicitEncryptedArchiveRecoveryTest.kt
- app/src/test/kotlin/dev/n0png/fieldops/app/ExplicitArchiveRecoveryActivityTest.kt
- research/android/CP-0009N_EXPLICIT_RECOVERY_CHOICE.md
- .github/workflows/cp0009n-explicit-recovery-choice.yml
- .github/workflows/finalize-cp0009n.yml
- CP-0009N merged-main finalizer run: """ + run_id + """

### Evidence boundary

JVM and Robolectric software-key recovery-choice simulations, Android APK,
and inherited host CI only. Revalidation cannot prove trusted freshness or
protect against genuine replay; no physical hardware or secure erase proof.
Key retirement remains a non-executing blocked policy.

### Inherited verified ancestry

""" + parent + """ and all prior immutable checkpoints remain intact.

"""
readme = replace_once(readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals", "README baseline")
readme = replace_once(readme, r"\*\*Active software track:\*\* [^\n]+",
    "**Active software track:** " + next_label, "README active track")
readme = re.sub(r"- Current Git source baseline: [^\n]+",
    "- Current Git source baseline: " + checkpoint, readme, count=1)
readme = replace_once(readme,
    r"## Current exact next action\n.*?\n## README maintenance contract",
"""## Current exact next action

**""" + next_label + """.**

1. Add nonpersistent, read-only audit trail for user review, choice, rejection
   and in-memory recovery outcomes, without logging private archive payloads.
2. Make explicitly chosen candidate provenance clear, retaining all existing
   authentication, stale-inspection failure and FRESHNESS NOT VERIFIED warnings.
3. Preserve full Android APK, Kotlin/Robolectric, inherited CI and immutable
   snapshot proof. Do NOT automatically retire or erase any key/file.
4. CP-0003C DEFERRED; CP-0004A/B/C remain incomplete.

## README maintenance contract""", "README exact next action")
readme = re.sub(r"\n## CP-0009N implementation branch — not checkpointed\n[\s\S]*$",
    "\n", readme)
readme_path.write_text(readme.rstrip() + "\n")

(root / "NEXT_ACTION.md").write_text("""# NEXT ACTION — FTX-1 FieldOps

## Owner execution override

CP-0003C is **DEFERRED** until the owner explicitly says 'resume CP-0003C'.
CP-0004A/B/C remain incomplete. Skip phone/radio, USB CAT/audio, live RF,
real accounts/certificates/credentials and manual hardware testing.

## Next software checkpoint

**""" + next_label + """**

CP-0009N added explicitly inspected, named and confirmed in-memory
recovery choices using the original/rotated existing AES-GCM key.
Re-inspection and canonical reauthentication reject stale or lost-key
choices; neither old file nor key is ever retired or auto-selected.
Next implement a nonpersistent, non-sensitive read-only audit trail of
explicit choices and failure outcomes. No trusted freshness witness.
CP-0003C stays DEFERRED; physical phone/radio checkpoints stay incomplete.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = replace_once(track,
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n" + checkpoint +
    "\n\n## Deferred but incomplete hardware/account work",
    "SOFTWARE_TRACK baseline")
track = replace_once(track, r"## Active software checkpoint\n\n.*?\n## Resume rule",
"""## Active software checkpoint

""" + next_label + """

CP-0009N provides separate review/select/confirm user gestures to
recover either independently authenticated archive to in-memory
synthetic UI state. Reinspection and canonical digest validation
reject changes/lost keys. Old keys/files stay; retirement checklist
always BLOCKED_NON_EXECUTING. Robolectric/Android APK/inherited CI
pass; no physical device, filesystem, CAT/audio/RF or trusted
freshness evidence. CP-0003C DEFERRED; CP-0004A/B/C incomplete.

## Resume rule""", "SOFTWARE_TRACK active checkpoint")
track = re.sub(r"\n## CP-0009N work in progress — not durable\n[\s\S]*$",
    "\n", track)
track_path.write_text(track.rstrip() + "\n")

history_path = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0009N — explicit reviewed in-memory encrypted recovery" not in history:
    history += """

## CP-0009N — explicit reviewed in-memory encrypted recovery

Parent: """ + parent + """. Two deliberate user actions SELECT and
CONFIRM an authenticated original or rotated V2 candidate following
explicit review; both sessions enabled. Full reinspection plus fresh
authenticated load with digest/entry-count comparison refuse changed
evidence without fallback. No write/retirement/erase or automatic winner.
Old V1/V2 and both Keystore aliases stay. Robolectric, APK and inherited
GitHub CI succeeded; no real hardware, Keystore or RF proof.
CP-0003C DEFERRED and CP-0004A/B/C incomplete.

Finalizer: """ + run_id + """.
Evidence: research/android/CP-0009N_EXPLICIT_RECOVERY_CHOICE.md.
"""
history_path.write_text(history.rstrip() + "\n")
