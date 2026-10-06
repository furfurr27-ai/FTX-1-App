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
(root / "VERSION").write_text("v17-official-award-catalog\n")

readme_path = root / "README.md"
readme = readme_path.read_text()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** CP-0006B-OFFICIAL_AWARD_CATALOG

Parent durable checkpoint: CP-0006A-AWARD_EVALUATION_ENGINE.

CP-0003C and CP-0004A/B/C remain explicitly deferred hardware/account checkpoints and are not implied complete by this software checkpoint.

CP-0006B is a GREEN host/CI official-award-catalog checkpoint. It layers versioned, officially sourced award metadata and rule shapes on top of the CP-0006A evaluator without adding account login, claim submission, credentials, or hardware dependencies.

CP-0006B proves:

- Official award sources are recorded with HTTPS URLs, retrieval dates, and version labels where available.
- The initial verified catalog contains ARRL DXCC Mixed, ARRL WAS, IARU WAC, ARRL Triple Play WAS, IOTA 100, POTA Bronze Hunter, and SOTA Shack Sloth.
- Simple distinct-target awards can produce CP-0006A local threshold definitions without attaching sponsor claimability.
- Triple Play is preserved as a 50-state x 3-mode matrix rather than flattened into an unsafe generic count.
- IOTA 100 preserves both the 100-group threshold and required seven-continent coverage.
- POTA Bronze Hunter preserves automatic-program issuance instead of inventing a manual claim path.
- SOTA Shack Sloth remains external point scoring rather than being misrepresented as a distinct-QSO count.
- Official AWARDED/CREDITED standing is separate from local progress and requires explicit sponsor evidence.
- The current QSO model's missing normalized remote award targets are explicit blockers; the catalog does not infer DXCC/state/continent/IOTA/POTA identity from callsigns or free text.
- VUCC and CQ WAZ/WPX are not guessed into the verified rule catalog when their full rule/source shape is not yet encoded.

Host/CI gates:

- CP-0006B official award catalog: **115/115 PASS**.
- CP-0006A award evaluator: **67/67 PASS**.
- CP-0005B manual/digital LoTW queue: **47/47 PASS**.
- CP-0005A universal QSO/logger: **78/78 PASS**.
- CP-0003B LoTW transaction: **53/53 PASS**.
- Inherited core: **42,062 PASS**.

Evidence:

- research/awards/CP-0006B_OFFICIAL_AWARD_CATALOG.md
- research/awards/OFFICIAL_AWARD_SOURCES.tsv
- SOFTWARE_TRACK.md
- CP-0006B finalization workflow run: {run_id}

### Evidence boundary

CP-0006B verifies catalog structure and the encoded rule facts against official public sources. It does not claim current-QSO local evaluation for award target data that is not normalized, real sponsor-account status, claim submission, official award issuance, Awards Center UI/persistence, live LoTW/device validation, or FTX-1 Android hardware proof.

### Inherited verified ancestry

CP-0006A-AWARD_EVALUATION_ENGINE, CP-0005B-MANUAL_QSO_LOTW_QUEUE, CP-0005A-UNIVERSAL_QSO_LOGGER, CP-0003B-LOTW_TRANSACTION_SAFE, CP-0003A-TRUSTEDQSL_SIGNER, CP-0002E-NATIVE_MODE_REGRESSION, and prior native-mode checkpoints remain verified ancestry.

"""

readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals",
    "verified durable baseline",
)

readme = re.sub(
    r"\*\*Active software track:\*\* .*",
    "**Active software track:** CP-0006C — Award target enrichment and composite rules",
    readme,
    count=1,
)

readme = readme.replace(
    "- Current Git source baseline: CP-0006A-AWARD_EVALUATION_ENGINE",
    "- Current Git source baseline: CP-0006B-OFFICIAL_AWARD_CATALOG",
    1,
)
readme = readme.replace(
    "- Current Git source baseline: " + chr(96) + "CP-0006A-AWARD_EVALUATION_ENGINE" + chr(96),
    "- Current Git source baseline: " + chr(96) + "CP-0006B-OFFICIAL_AWARD_CATALOG" + chr(96),
    1,
)

next_section = """## Current exact next action

**CP-0006C — Award target enrichment and composite rules.**

1. Add a provider-independent award-target evidence layer keyed by immutable QSO id with explicit provenance for normalized remote DXCC entity, U.S. state, continent, IOTA group, and POTA reference data.
2. Do not infer award targets from callsign prefixes or free-text notes without a versioned authoritative enrichment source.
3. Apply official date/band/confirmation-source constraints before a QSO contributes to award progress.
4. Add composite evaluation needed for Triple Play's state-by-mode matrix and IOTA 100's count-plus-continent coverage.
5. Make DXCC/WAS/WAC/POTA local progress consume normalized target evidence without rewriting the authoritative QSO record.
6. Keep SOTA point scoring external until a verified SOTA data/scoring integration exists.
7. Preserve the distinction between local threshold, potentially claimable, submitted, awarded and credited sponsor states.
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

**CP-0006C — Award target enrichment and composite rules**

Add explicit, provenance-bearing normalized award targets keyed by immutable QSO id; then implement official date/band/confirmation constraints and composite evaluators for Triple Play and IOTA. Do not infer missing award geography from callsigns or free text, and do not attempt sponsor-account login or claim submission.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = re.sub(
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n" + chr(96) + "CP-0006B-OFFICIAL_AWARD_CATALOG" + chr(96) + "\n\n## Deferred but incomplete hardware/account work",
    track,
    count=1,
    flags=re.S,
)
track = re.sub(
    r"## Active software checkpoint\n\n.*?\n\nRequired scope:\n.*?\n## Resume rule",
    """## Active software checkpoint

""" + chr(96) + """CP-0006C-AWARD_TARGET_ENRICHMENT""" + chr(96) + """

Required scope:

- provider-independent award-target evidence keyed by immutable QSO id
- explicit provenance for normalized DXCC entity, U.S. state, continent, IOTA group and POTA reference targets
- no callsign/free-text guessing without a versioned authoritative enrichment source
- official date/band/confirmation-source filtering before award contribution
- Triple Play state-by-mode matrix evaluation
- IOTA 100 count-plus-seven-continent coverage evaluation
- DXCC/WAS/WAC/POTA local progress from normalized evidence without rewriting QSO records
- SOTA scoring remains external until a verified integration exists
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
if "## CP-0006B — Official award rules/catalog" not in history:
    history = history.rstrip() + "\n\n" + f"""## CP-0006B — Official award rules/catalog

Parent durable checkpoint: CP-0006A-AWARD_EVALUATION_ENGINE.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override remains active: do not return to CP-0003C until the owner explicitly says resume CP-0003C; skip checkpoints requiring phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now has a versioned official award catalog with source provenance and conservative rule shapes for ARRL DXCC Mixed, ARRL WAS, IARU WAC, ARRL Triple Play WAS, IOTA 100, POTA Bronze Hunter and SOTA Shack Sloth. Simple distinct-target catalog entries can generate local CP-0006A threshold definitions, but never attach sponsor claimability. Composite/program-scored awards fail closed rather than being flattened to unsafe counts.

Official sponsor standing is separately modeled and AWARDED/CREDITED requires explicit sponsor evidence. The current QSO model's missing normalized award-target fields remain explicit future work rather than being guessed from callsigns/free text.

Host/CI gate: official catalog 115; award evaluator 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; inherited core 42,062 assertions, all PASS.

Finalization workflow run: {run_id}.

Evidence: research/awards/CP-0006B_OFFICIAL_AWARD_CATALOG.md and research/awards/OFFICIAL_AWARD_SOURCES.tsv.
"""
    history_path.write_text(history)
