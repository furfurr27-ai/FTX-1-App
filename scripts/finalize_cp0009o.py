#!/usr/bin/env python3
"""CP-0009O main-only checkpoint promotion after the exact merged-main CI passes."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0009O-SESSION_RECOVERY_AUDIT_PROVENANCE"
parent = "CP-0009O-EXPLICIT_ENCRYPTED_ARCHIVE_RECOVERY_CHOICE"
next_label = "CP-0009P — audit lifecycle and provenance consistency hardening"

def replace_once(value, pattern, replacement, name):
    new, count = re.subn(pattern, lambda _: replacement, value, count=1, flags=re.S)
    if count != 1:
        raise SystemExit("Expected one " + name + ", got " + str(count))
    return new

(root / "VERSION").write_text("v66-session-recovery-audit-provenance\n")

readme_path = root / "README.md"
readme = readme_path.read_text()
match = re.search(r"Host/CI gates:\n\n(.*?)\nEvidence:", readme, re.S)
if match is None:
    raise SystemExit("Missing inherited host/CI regression evidence")
inherited = match.group(1).strip()
baseline = """## Verified durable baseline

**Latest verified checkpoint:** """ + checkpoint + """

Parent durable checkpoint: """ + parent + """ / v65.

CP-0009O adds a bounded, session-only RECOVERY AUDIT TRAIL for the
explicit authenticated encrypted V2 archive review/select/confirm flow.
Typed closed enum actions/outcomes/candidate labels and sequence ordinals
are the only stored metadata. A fixed 12-entry FIFO cap (maximum 64),
detached read-only snapshots, clear control and Activity-instance-only
lifetime prevent accumulation or persistence. No credentials, archive
payload, callsign, file path, hash, time, exception text, account detail
or other arbitrary string is stored; no DB, file, preferences, Android
saved state, Logcat or network output. Activity recreation resets audit.

Android displays review ACCEPTED/REJECTED, SELECT, CONFIRM
ACCEPTED/REJECTED, retirement review BLOCKED, and disable session
events as user-visible, ephemeral records. It identifies original or
rotated authenticated local V2 **only after successful explicit
recovery**. A synthetic capture, workspace action or other explicit
restore invalidates the current displayed recovery provenance.
Authentication is not transmitter/station provenance or trusted
monotonic freshness: FRESHNESS NOT VERIFIED remains visible.

CP-0009N's original/rotated independent authentication, source
reinspection, SHA-256 canonical equality verification, stale-source
refusal and two-tap confirmation remain intact. Original V1 plaintext,
both encrypted V2 copies, their Keystore keys, synthetic demo-only
history and all separate opt-ins remain. No archive is automatically
preferred, no secure flash wipe or key retirement is implemented.
Old-key retirement remains BLOCKED_NON_EXECUTING. CP-0003C is DEFERRED,
CP-0004A/B/C hardware checkpoints incomplete.

Host/CI gates:

- CP-0009O typed bounded session audit Robolectric tests: **PASS**.
- CP-0009O Android nonpersistent audit and current provenance UX: **PASS**.
- CP-0009O API35 APK and complete inherited Kotlin host regressions: **PASS**.
""" + inherited + """

Evidence:

- app/src/main/kotlin/dev/n0png/fieldops/app/SessionRecoveryAuditTrail.kt
- app/src/test/kotlin/dev/n0png/fieldops/app/SessionRecoveryAuditTrailTest.kt
- app/src/test/kotlin/dev/n0png/fieldops/app/SessionRecoveryAuditActivityTest.kt
- research/android/CP-0009O_SESSION_RECOVERY_AUDIT.md
- .github/workflows/cp0009o-session-recovery-audit.yml
- .github/workflows/finalize-cp0009o.yml
- CP-0009O merged-main finalizer run: """ + run_id + """

### Evidence boundary

Only JVM/Robolectric software-key/archive and Android build validation.
The audit is not tamper-proof, persistent, cryptographically signed,
a reliable clock or trusted recency witness; no physical device
Keystore, filesystem, radio/RF/account or secure deletion proof exists.

### Inherited verified ancestry

""" + parent + """ and all preceding immutable checkpoints remain intact.

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

1. Harden audit lifecycle on session disable, independent restore and
   synthetic history changes, preserving typed-only privacy boundaries.
2. Test consistent visible archive provenance after failed recovery
   and new Activity/configuration recreation without logging any payload.
3. Preserve full Android APK/Robolectric/inherited CI, immutable
   snapshot verification and FRESHNESS NOT VERIFIED warnings.
4. CP-0003C DEFERRED; CP-0004A/B/C incomplete.

## README maintenance contract""", "README exact next action")
readme = re.sub(r"\n## CP-0009O implementation branch — not checkpointed\n[\s\S]*$",
    "\n", readme)
readme_path.write_text(readme.rstrip() + "\n")

(root / "NEXT_ACTION.md").write_text("""# NEXT ACTION — FTX-1 FieldOps

## Owner execution override

CP-0003C is **DEFERRED** until the owner explicitly says 'resume CP-0003C'.
CP-0004A/B/C remain incomplete. Skip phone/radio, USB CAT/audio, live RF,
real accounts/certificates/credentials and manual hardware testing.

## Next software checkpoint

**""" + next_label + """**

CP-0009O added a bounded, instance-owned, metadata-only recovery audit,
clear-on-request and clear-on-Activity-recreation; it labels only selected
original/rotated V2 after explicitly confirmed in-memory recovery.
No archive text/hashes/keys or time is logged; FRESHNESS NOT VERIFIED.
Next strengthen lifecycle/provenance consistency across screen changes,
without persistent audit records or key retirement. Preserve every
inherited Android/host gate and immutable checkpoint proof.
CP-0003C DEFERRED; CP-0004A/B/C incomplete.
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

CP-0009O session-only typed bounded audit, explicit source label
and automatic provenance invalidation are Robolectric/Android APK
and inherited Kotlin CI verified. No persistent log, trusted
timestamp/anti-replay, Android physical Keystore or radio proof.
Original V1/V2 and keys remain; no key retirement. CP-0003C
DEFERRED; CP-0004A/B/C incomplete.

## Resume rule""", "SOFTWARE_TRACK active checkpoint")
track = re.sub(r"\n## CP-0009O work in progress — not durable\n[\s\S]*$",
    "\n", track)
track_path.write_text(track.rstrip() + "\n")

history_path = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0009O — session-only recovery audit provenance" not in history:
    history += """

## CP-0009O — session-only recovery audit provenance

Parent: """ + parent + """. The explicit encrypted recovery path now
records bounded, typed, nonpersistent action/outcome/candidate metadata,
including rejected review, selected source, confirmed recovery,
failed confirm, and blocked old-key retirement inspection. New Activity
or explicit audit clear resets the log. Source label is invalidated on
synthetic capture, workspace action and other restores; freshness remains
unverified. No key/file/secret/payload logging, secure erase, automatic
archive preference, trusted witness or real hardware proof.
Robolectric, Android APK and complete inherited host CI PASS.
CP-0003C DEFERRED; CP-0004A/B/C incomplete.

Finalizer: """ + run_id + """.
Evidence: research/android/CP-0009O_SESSION_RECOVERY_AUDIT.md.
"""
history_path.write_text(history.rstrip() + "\n")
