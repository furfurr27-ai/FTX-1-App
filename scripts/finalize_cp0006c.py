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
(root / "VERSION").write_text("v18-award-target-enrichment\n")

readme_path = root / "README.md"
readme = readme_path.read_text()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** CP-0006C-AWARD_TARGET_ENRICHMENT

Parent durable checkpoint: CP-0006B-OFFICIAL_AWARD_CATALOG.

CP-0003C and CP-0004A/B/C remain explicitly deferred hardware/account checkpoints and are not implied complete by this software checkpoint.

CP-0006C is a GREEN host/CI award-target/composite-evaluation checkpoint. It makes officially sourced award rules locally evaluatable from explicit provenance-bearing enrichment without rewriting QSO records or guessing geography from callsigns/free text.

CP-0006C proves:

- Award target evidence is keyed by immutable QSO id and kept outside the authoritative QSO record.
- Explicit source id/version provenance is required for normalized DXCC entity, U.S. state, continent, IOTA group and POTA reference enrichment.
- Conflicting single-valued geography fails closed; POTA can preserve multiple references on one QSO.
- No production award-target path derives geography/program identity from callsign prefixes or QSO notes.
- Official not-before dates, excluded bands, view band/mode filters and confirmation-source policies are applied before contribution.
- DXCC Mixed, WAS, WAC and POTA Bronze Hunter can consume normalized target evidence locally.
- WAS and Triple Play apply explicit DC-to-Maryland handling without changing the stored evidence/QSO.
- Triple Play is evaluated as the full 150-cell state-by-mode matrix and each confirmed cell requires LoTW evidence.
- IOTA 100 requires both 100 confirmed groups and all seven required continents.
- SOTA point scoring remains external and fails closed rather than being approximated.
- Local evaluation never emits sponsor claimability, awarded or credited status.
- Evaluation preserves exact QSO MODE/SUBMODE and never mutates the authoritative QSO.

Host/CI gates:

- CP-0006C award target/composite evaluator: **77/77 PASS**.
- CP-0006B official award catalog: **115/115 PASS**.
- CP-0006A award evaluator: **67/67 PASS**.
- CP-0005B manual/digital LoTW queue: **47/47 PASS**.
- CP-0005A universal QSO/logger: **78/78 PASS**.
- CP-0003B LoTW transaction: **53/53 PASS**.
- Inherited core: **42,062 PASS**.

Evidence:

- research/awards/CP-0006C_AWARD_TARGET_ENRICHMENT.md
- research/awards/CP-0006B_OFFICIAL_AWARD_CATALOG.md
- SOFTWARE_TRACK.md
- CP-0006C finalization workflow run: {run_id}

### Evidence boundary

CP-0006C is host/CI evidence using explicit synthetic target/provenance fixtures. It does not claim a real external enrichment dataset/import adapter, sponsor-account status, claim submission, official award issuance, Awards Center UI/persistence, live LoTW/device validation, or FTX-1 Android hardware proof.

### Inherited verified ancestry

CP-0006B-OFFICIAL_AWARD_CATALOG, CP-0006A-AWARD_EVALUATION_ENGINE, CP-0005B-MANUAL_QSO_LOTW_QUEUE, CP-0005A-UNIVERSAL_QSO_LOGGER, CP-0003B-LOTW_TRANSACTION_SAFE, CP-0003A-TRUSTEDQSL_SIGNER, CP-0002E-NATIVE_MODE_REGRESSION, and prior native-mode checkpoints remain verified ancestry.

"""

readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals",
    "verified durable baseline",
)

readme = re.sub(
    r"\*\*Active software track:\*\* .*",
    "**Active software track:** CP-0006D — Awards Center progress projection",
    readme,
    count=1,
)

readme = readme.replace(
    "- Current Git source baseline: CP-0006B-OFFICIAL_AWARD_CATALOG",
    "- Current Git source baseline: CP-0006C-AWARD_TARGET_ENRICHMENT",
    1,
)
readme = readme.replace(
    "- Current Git source baseline: " + chr(96) + "CP-0006B-OFFICIAL_AWARD_CATALOG" + chr(96),
    "- Current Git source baseline: " + chr(96) + "CP-0006C-AWARD_TARGET_ENRICHMENT" + chr(96),
    1,
)

next_section = """## Current exact next action

**CP-0006D — Awards Center progress projection.**

1. Build a UI-independent Awards Center read/projection model on top of the CP-0006B catalog and CP-0006C progress engine.
2. Expose worked, confirmed, local threshold, remaining targets/cells/coverage, and deterministic progress percentage without moving award rules into UI code.
3. Preserve official information/claim links, concise claim instructions, source/version evidence, and external-verification warnings in the projection.
4. Support Mixed/CW/Phone/Digital and band/date views through explicit query/filter inputs while preserving exact QSO mode/submode underneath.
5. Surface sponsor standing (unknown/eligible/submitted/awarded/credited) separately from local progress; never infer sponsor state from threshold completion.
6. Represent external-program scoring such as SOTA as externally verified/unavailable local progress instead of fabricating a percentage.
7. Keep the service host/CI-only; Compose/Room/device UI integration may follow after the read model is verified.
8. CP-0003C remains DEFERRED; do not return to it until the owner explicitly says resume CP-0003C.
9. Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation; continue with the next GitHub/CI-only checkpoint.

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

CP-0003C is **DEFERRED**. Do not return to CP-0003C until the owner explicitly says resume CP-0003C.

Skip any checkpoint that requires the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue forward to the next checkpoint that can be completed entirely through GitHub/CI. A skipped checkpoint remains incomplete.

## Next software checkpoint

**CP-0006D — Awards Center progress projection**

Build a UI-independent Awards Center read model from the verified catalog/progress engine. Expose worked/confirmed/remaining/progress, band/mode/date views, official links/source evidence, external-verification warnings, and sponsor standing without moving award rules into UI code or inferring sponsor claim/award state.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = re.sub(
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n" + chr(96) + "CP-0006C-AWARD_TARGET_ENRICHMENT" + chr(96) + "\n\n## Deferred but incomplete hardware/account work",
    track,
    count=1,
    flags=re.S,
)
track = re.sub(
    r"## Active software checkpoint\n\n.*?\n\nRequired scope:\n.*?\n## Resume rule",
    """## Active software checkpoint

""" + chr(96) + """CP-0006D-AWARDS_CENTER_PROJECTION""" + chr(96) + """

Required scope:

- UI-independent Awards Center read/projection model
- worked / confirmed / local-threshold / remaining / deterministic progress fields
- Mixed, CW, Phone, Digital plus band/date query views
- official information/claim links, claim instructions and source/version evidence
- explicit external-verification warnings where local evidence is incomplete
- sponsor standing remains separate from local threshold/progress
- external-program scoring such as SOTA must remain external rather than fabricated
- award rules stay in catalog/evaluator code, not UI projection code
- host/CI-only verification before Compose/Room/device integration
- no real account login, claim submission, phone/radio/RF, or credential use
- skip later hardware/account-gated checkpoints under the owner execution override

## Resume rule""",
    track,
    count=1,
    flags=re.S,
)
track_path.write_text(track)

history_path = root / "checkpoints" / "CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0006C — Award target enrichment and composite rules" not in history:
    history = history.rstrip() + "\n\n" + f"""## CP-0006C — Award target enrichment and composite rules

Parent durable checkpoint: CP-0006B-OFFICIAL_AWARD_CATALOG.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override remains active: do not return to CP-0003C until the owner explicitly says resume CP-0003C; skip checkpoints requiring phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now has provenance-bearing normalized award target evidence outside QsoRecord plus an official award progress engine. DXCC/WAS/WAC/POTA consume explicit enrichment, Triple Play is a 150-cell state-by-mode LoTW-confirmed matrix, and IOTA 100 requires both 100 confirmed groups and seven-continent coverage. Official date/band/confirmation rules apply before contribution. Conflicting single-valued geography fails closed, callsigns/free text are not used to guess targets, SOTA remains external point scoring, and local progress never becomes sponsor claimability/award state automatically.

Host/CI gate: target/composite evaluator 77; official catalog 115; award evaluator 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; inherited core 42,062 assertions, all PASS.

Finalization workflow run: {run_id}.

Evidence: research/awards/CP-0006C_AWARD_TARGET_ENRICHMENT.md.
"""
    history_path.write_text(history)
