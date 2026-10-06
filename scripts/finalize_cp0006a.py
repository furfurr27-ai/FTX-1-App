#!/usr/bin/env python3
import os
import pathlib
import re


def replace_one(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected exactly one {label} section, found {count}")
    return updated


root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
(root / "VERSION").write_text("v16-award-evaluation-engine\n")

readme_path = root / "README.md"
readme = readme_path.read_text()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** CP-0006A-AWARD_EVALUATION_ENGINE

Parent durable checkpoint: CP-0005B-MANUAL_QSO_LOTW_QUEUE.

CP-0003C and CP-0004A/B/C remain explicitly deferred hardware/account checkpoints and are not implied complete by this software checkpoint.

CP-0006A is a GREEN host/CI award-domain checkpoint. It adds a provider-independent evaluator on top of the authoritative QSO model without encoding guessed sponsor rules or requiring live services/hardware.

CP-0006A proves:

- WORKED, CONFIRMED, THRESHOLD_MET and OFFICIALLY_CLAIMABLE are distinct award states.
- Confirmation requires explicit source/evidence; worked contacts are not silently treated as confirmed.
- Exact QSO MODE/SUBMODE remains preserved on each award contribution.
- A separate controlled award-mode grouping layer supports CW, PHONE and DIGITAL views.
- Unmapped modes stay unclassified rather than being guessed into award progress.
- Band-scoped and all-band evaluation use filters and do not rewrite authoritative QSO records.
- Distinct target counting supports WORKED-based or CONFIRMED-based thresholds.
- Optional target universes expose remaining targets against the configured threshold basis.
- Local threshold completion never automatically becomes official sponsor claimability.
- OFFICIALLY_CLAIMABLE requires an explicit claimability evaluator and can only be considered after the local threshold is met.
- CP-0006A contains synthetic/generic award definitions only; official rules and claim URLs remain CP-0006B work.

Host/CI gates:

- CP-0006A award evaluator: **67/67 PASS**.
- CP-0005B manual/digital LoTW queue: **47/47 PASS**.
- CP-0005A universal QSO/logger: **78/78 PASS**.
- CP-0003B LoTW transaction: **53/53 PASS**.
- Inherited core: **42,062 PASS**.

Evidence:

- research/awards/CP-0006A_AWARD_EVALUATION_ENGINE.md
- SOFTWARE_TRACK.md
- CP-0006A finalization workflow run: {run_id}

### Evidence boundary

CP-0006A is host/CI software evidence. It does not claim official award-rule correctness, sponsor-account access, claim submission, Awards Center UI/persistence, live LoTW/device validation, or FTX-1 Android hardware proof.

### Inherited verified ancestry

CP-0005B-MANUAL_QSO_LOTW_QUEUE, CP-0005A-UNIVERSAL_QSO_LOGGER, CP-0003B-LOTW_TRANSACTION_SAFE, CP-0003A-TRUSTEDQSL_SIGNER, CP-0002E-NATIVE_MODE_REGRESSION, and prior native-mode checkpoints remain verified ancestry.

"""

readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals",
    "verified durable baseline",
)

owner_rule = """### Owner-directed deferred checkpoint rule

CP-0003C is **DEFERRED**. Do not return to CP-0003C until the owner explicitly says `resume CP-0003C`.

While this rule is active, skip any checkpoint that requires the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue forward to the next checkpoint that can be completed entirely through GitHub/CI. Skipping a gated checkpoint never means it passed.

"""
if "### Owner-directed deferred checkpoint rule" in readme:
    readme = replace_one(
        readme,
        r"### Owner-directed deferred checkpoint rule\n.*?\n\*\*Active software track:\*\*",
        owner_rule + "**Active software track:**",
        "owner deferred checkpoint rule",
    )
else:
    marker = "**Active software track:**"
    if marker not in readme:
        raise SystemExit("Could not find active software track marker")
    readme = readme.replace(marker, owner_rule + marker, 1)

readme = re.sub(
    r"\*\*Active software track:\*\* .*",
    "**Active software track:** CP-0006B — Official award rules/catalog",
    readme,
    count=1,
)

readme = readme.replace(
    "- Current Git source baseline: CP-0005B-MANUAL_QSO_LOTW_QUEUE",
    "- Current Git source baseline: CP-0006A-AWARD_EVALUATION_ENGINE",
    1,
)
readme = readme.replace(
    "- Current Git source baseline: `CP-0005B-MANUAL_QSO_LOTW_QUEUE`",
    "- Current Git source baseline: `CP-0006A-AWARD_EVALUATION_ENGINE`",
    1,
)

next_section = """## Current exact next action

**CP-0006B — Official award rules/catalog.**

1. Verify award definitions only from official issuing-organization sources and record source URLs/version or retrieval date.
2. Encode a versioned award catalog on top of the CP-0006A provider-independent evaluator; do not hard-code sponsor rules into UI.
3. Keep official claimability and credited/awarded status distinct from local threshold calculations.
4. Add official information/claim links and concise claim instructions without attempting real account login or claim submission.
5. Keep exact QSO MODE/SUBMODE plus controlled award-mode grouping and band-specific views.
6. CP-0003C remains DEFERRED; do not return to it until the owner explicitly says `resume CP-0003C`.
7. Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation; continue with the next GitHub/CI-only checkpoint.

"""
readme = replace_one(
    readme,
    r"## Current exact next action\n.*?\n## README maintenance contract",
    next_section + "## README maintenance contract",
    "current exact next action",
)
readme_path.write_text(readme)

(root / "NEXT_ACTION.md").write_text("""# NEXT ACTION — FTX-1 FieldOps

## Execution override

CP-0003C is **DEFERRED**. Do not return to CP-0003C until the owner explicitly says `resume CP-0003C`.

Skip any checkpoint that requires the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue forward to the next checkpoint that can be completed entirely through GitHub/CI. A skipped checkpoint remains incomplete.

## Next software checkpoint

**CP-0006B — Official award rules/catalog**

Verify official issuing-organization rules and links, then encode a versioned catalog on top of the CP-0006A provider-independent evaluator. Keep threshold completion, official claimability, and officially awarded/credited status distinct. Do not attempt real account login or claim submission.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = re.sub(
    r"## Latest verified durable parent\n\n`[^`]+`",
    "## Latest verified durable parent\n\n`CP-0006A-AWARD_EVALUATION_ENGINE`",
    track,
    count=1,
)

track_rule = """## Owner execution override

CP-0003C is **DEFERRED**. Do not return to CP-0003C until the owner explicitly says `resume CP-0003C`.

Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue with the next checkpoint that can be completed entirely through GitHub/CI. Deferred checkpoints remain incomplete.

"""
if "## Owner execution override" in track:
    track = replace_one(
        track,
        r"## Owner execution override\n.*?\n## Active software checkpoint",
        track_rule + "## Active software checkpoint",
        "software-track owner override",
    )
else:
    track = track.replace("## Active software checkpoint", track_rule + "## Active software checkpoint", 1)

track = re.sub(
    r"## Active software checkpoint\n\n`[^`]+`\n\nRequired scope:\n.*?\n## Resume rule",
    """## Active software checkpoint

`CP-0006B-OFFICIAL_AWARD_CATALOG`

Required scope:

- verify rules/claim links from official issuing organizations only
- record source URL plus version/retrieval date for catalog evidence
- layer official definitions on CP-0006A without moving sponsor logic into UI
- keep threshold met, officially claimable, and officially awarded/credited distinct
- no real account login, claim submission, phone/radio/RF, or credential use
- if a later checkpoint becomes hardware/account gated, skip it under the owner execution override

## Resume rule""",
    track,
    count=1,
    flags=re.S,
)
track = track.replace(
    "3. do not return to deferred hardware work unless Chris explicitly asks or the required device/account evidence is available;",
    "3. do not return to CP-0003C unless the owner explicitly says `resume CP-0003C`;",
    1,
)
if "6. skip any checkpoint requiring the phone" not in track:
    track = track.rstrip() + "\n6. skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation and continue to the next GitHub/CI-only checkpoint;\n"
track_path.write_text(track)

history_path = root / "checkpoints" / "CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0006A — Award evaluation engine" not in history:
    history = history.rstrip() + "\n\n" + f"""## CP-0006A — Award evaluation engine

Parent durable checkpoint: CP-0005B-MANUAL_QSO_LOTW_QUEUE.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override: do not return to CP-0003C until the owner explicitly says `resume CP-0003C`; skip other checkpoints that require phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now has a provider-independent award evaluator with separate WORKED, CONFIRMED, THRESHOLD_MET and OFFICIALLY_CLAIMABLE states. Confirmation evidence is explicit, exact QSO MODE/SUBMODE is preserved while a separate controlled award-mode grouping supports CW/PHONE/DIGITAL views, and evaluation can be all-band or band-scoped without mutating the QSO record. Local threshold completion cannot become official claimability without an explicit claimability evaluator.

CP-0006A intentionally uses synthetic/generic definitions only; official sponsor rules and claim URLs remain CP-0006B.

Host/CI gate: award 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; core 42,062 assertions, all PASS.

Finalization workflow run: {run_id}.

Evidence: research/awards/CP-0006A_AWARD_EVALUATION_ENGINE.md.
"""
    history_path.write_text(history)
