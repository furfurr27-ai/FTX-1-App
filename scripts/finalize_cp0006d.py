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
(root / "VERSION").write_text("v19-awards-center-projection\n")

readme_path = root / "README.md"
readme = readme_path.read_text()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** CP-0006D-AWARDS_CENTER_PROJECTION

Parent durable checkpoint: CP-0006C-AWARD_TARGET_ENRICHMENT.

CP-0003C and CP-0004A/B/C remain explicitly deferred hardware/account checkpoints and are not implied complete by this software checkpoint.

CP-0006D is a GREEN host/CI Awards Center read-model checkpoint. It projects the verified catalog and award-progress engine into UI-independent cards without moving sponsor rules into UI code or inferring sponsor eligibility from local progress.

CP-0006D proves:

- One Awards Center card is produced for each official catalog entry while preserving catalog order.
- Cards expose official award name, issuer, description, information/claim links, claim instructions, source URL/retrieval/version metadata and official conditions.
- Mixed, CW, Phone and Digital views are explicit query inputs.
- Band and inclusive UTC date-range filters are supported without rewriting authoritative QSOs.
- Distinct-target awards expose deterministic counted/required progress.
- Triple Play exposes transparent state-by-mode-cell progress; a filtered mode leg can be display-complete without falsely marking the full award threshold complete.
- IOTA 100 progress combines its 100 confirmed-group criterion and seven-continent coverage into a transparent FieldOps display metric while retaining both criteria separately.
- External-scoring awards such as SOTA expose no fabricated numerator, denominator or percentage.
- Sponsor standing remains UNKNOWN/ELIGIBLE/SUBMITTED/AWARDED/CREDITED independently from local worked/confirmed/threshold state.
- Claimable-now is nullable when sponsor standing is unknown and becomes true only from explicit ELIGIBLE_NOT_CLAIMED sponsor evidence.
- Same-time conflicting latest sponsor-standing records fail closed.
- Production projection code contains no hard-coded award IDs, credentials, network submission, hardware ownership, Compose or Room dependencies.
- Projection does not mutate QSO records or recode catalog conditions.

Host/CI gates:

- CP-0006D Awards Center projection: **114/114 PASS**.
- CP-0006C award target/composite evaluator: **77/77 PASS**.
- CP-0006B official award catalog: **115/115 PASS**.
- CP-0006A award evaluator: **67/67 PASS**.
- CP-0005B manual/digital LoTW queue: **47/47 PASS**.
- CP-0005A universal QSO/logger: **78/78 PASS**.
- CP-0003B LoTW transaction: **53/53 PASS**.
- Inherited core: **42,062 PASS**.

Evidence:

- research/awards/CP-0006D_AWARDS_CENTER_PROJECTION.md
- research/awards/CP-0006C_AWARD_TARGET_ENRICHMENT.md
- SOFTWARE_TRACK.md
- CP-0006D finalization workflow run: {run_id}

### Evidence boundary

CP-0006D is host/CI evidence using synthetic QSO, enrichment and sponsor-standing fixtures. It does not claim real enrichment imports/datasets, persistent award evidence storage, sponsor-account synchronization, claim submission, Compose/Room application integration, map award overlays, Android device behavior, or FTX-1 hardware proof.

### Inherited verified ancestry

CP-0006C-AWARD_TARGET_ENRICHMENT, CP-0006B-OFFICIAL_AWARD_CATALOG, CP-0006A-AWARD_EVALUATION_ENGINE, CP-0005B-MANUAL_QSO_LOTW_QUEUE, CP-0005A-UNIVERSAL_QSO_LOGGER, CP-0003B-LOTW_TRANSACTION_SAFE, CP-0003A-TRUSTEDQSL_SIGNER, CP-0002E-NATIVE_MODE_REGRESSION, and prior native-mode checkpoints remain verified ancestry.

"""

readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals",
    "verified durable baseline",
)

readme = re.sub(
    r"\*\*Active software track:\*\* .*",
    "**Active software track:** CP-0006E — Award evidence persistence and explicit ADIF enrichment import",
    readme,
    count=1,
)

readme = readme.replace(
    "- Current Git source baseline: CP-0006C-AWARD_TARGET_ENRICHMENT",
    "- Current Git source baseline: CP-0006D-AWARDS_CENTER_PROJECTION",
    1,
)
readme = readme.replace(
    "- Current Git source baseline: " + chr(96) + "CP-0006C-AWARD_TARGET_ENRICHMENT" + chr(96),
    "- Current Git source baseline: " + chr(96) + "CP-0006D-AWARDS_CENTER_PROJECTION" + chr(96),
    1,
)

next_section = """## Current exact next action

**CP-0006E — Award evidence persistence and explicit ADIF enrichment import.**

1. Add a provider-independent durable repository contract for award-target evidence, confirmation evidence and sponsor-standing records without modifying authoritative QSO records.
2. Make inserts idempotent and conflict-safe by immutable QSO id, normalized target kind/value and provenance identity.
3. Add an explicit ADIF award-enrichment adapter for remote DXCC, STATE, CONT, IOTA and POTA reference fields when those fields are actually present in imported records.
4. Preserve import/source provenance and source version on every generated award-target evidence record.
5. Import explicit confirmation metadata conservatively; do not equate ordinary QSO presence or HTTP upload with confirmation.
6. Do not infer missing geography/program identity from callsign prefixes, country names, grids or free-text notes.
7. Keep sponsor-account synchronization and claim submission out of this checkpoint.
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

**CP-0006E — Award evidence persistence and explicit ADIF enrichment import**

Add durable provider-independent award evidence storage plus explicit ADIF enrichment for remote DXCC/STATE/CONT/IOTA/POTA fields and conservative confirmation metadata. Preserve source provenance/version, make writes idempotent/conflict-safe, and never infer missing award geography from callsigns or free text.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = re.sub(
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n" + chr(96) + "CP-0006D-AWARDS_CENTER_PROJECTION" + chr(96) + "\n\n## Deferred but incomplete hardware/account work",
    track,
    count=1,
    flags=re.S,
)
track = re.sub(
    r"## Active software checkpoint\n\n.*?\n\nRequired scope:\n.*?\n## Resume rule",
    """## Active software checkpoint

""" + chr(96) + """CP-0006E-AWARD_EVIDENCE_PERSISTENCE""" + chr(96) + """

Required scope:

- provider-independent durable repository contracts for award target evidence, confirmation evidence and sponsor standing
- immutable-QSO keyed idempotent/conflict-safe writes
- explicit ADIF enrichment for present remote DXCC, STATE, CONT, IOTA and POTA reference fields
- source/provenance/version retention for every imported award target
- conservative explicit confirmation import only; QSO presence/upload success never means confirmed
- no callsign-prefix/country/grid/free-text inference for missing award targets
- no sponsor-account login/sync or claim submission
- host/CI-only verification before Compose/Room/device integration
- no phone/radio/RF/credential use
- skip later hardware/account-gated checkpoints under the owner execution override

## Resume rule""",
    track,
    count=1,
    flags=re.S,
)
track_path.write_text(track)

history_path = root / "checkpoints" / "CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0006D — Awards Center progress projection" not in history:
    history = history.rstrip() + "\n\n" + f"""## CP-0006D — Awards Center progress projection

Parent durable checkpoint: CP-0006C-AWARD_TARGET_ENRICHMENT.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override remains active: do not return to CP-0003C until the owner explicitly says resume CP-0003C; skip checkpoints requiring phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now has a UI-independent Awards Center projection over the verified official catalog/progress engine. Cards expose worked/confirmed/remaining state, deterministic display progress, Mixed/CW/Phone/Digital plus band/date views, official source/claim metadata, external-verification warnings and explicit sponsor standing. Triple Play mode-leg display completion stays distinct from full award threshold completion, IOTA count-plus-coverage remains transparent, and external SOTA scoring never receives a fabricated percentage. Local progress never infers sponsor claimability.

Host/CI gate: Awards Center projection 114; target/composite evaluator 77; official catalog 115; award evaluator 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; inherited core 42,062 assertions, all PASS.

Finalization workflow run: {run_id}.

Evidence: research/awards/CP-0006D_AWARDS_CENTER_PROJECTION.md.
"""
    history_path.write_text(history)
