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
(root / "VERSION").write_text("v21-awards-application-service\n")

readme_path = root / "README.md"
readme = readme_path.read_text()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** CP-0006F-AWARDS_APPLICATION_SERVICE

Parent durable checkpoint: CP-0006E-AWARD_EVIDENCE_PERSISTENCE.

CP-0003C and CP-0004A/B/C remain explicitly deferred hardware/account checkpoints and are not implied complete by this software checkpoint.

CP-0006F is a GREEN host/CI application-service checkpoint. It composes the authoritative local logbook, persistent award evidence, explicit ADIF enrichment, and Awards Center projection behind one platform-independent service without introducing fuzzy QSO matching or account/hardware dependencies.

CP-0006F proves:

- Awards Center cards are generated directly from the current authoritative LogbookRepository plus one current AwardEvidenceRepository snapshot.
- Callers no longer manually assemble QSO/evidence/sponsor-standing lists.
- Parsed ADIF evidence ingestion requires a positive immutable local QSO id already resolved by another layer.
- The award service never searches the logbook by callsign, date, grid, country, prefix, or free text.
- An explicit ADIF CALL is only a fail-closed consistency check against the already-resolved QSO.
- Unknown QSO ids, CALL mismatches, malformed explicit award fields, or repository evidence conflicts reject the complete ingestion batch.
- All records are converted before one combined evidence-repository apply, preserving atomic batch semantics.
- Repeated batches are idempotent and reversed input ordering produces the same canonical evidence state.
- Parsed LoTW-style ADIF fixtures can feed explicit confirmation/target evidence into Awards Center progress without network/account access.
- Mixed/CW/Phone/Digital, band, and UTC date-range queries pass through the application service.
- Sponsor standing is recorded only through explicit sponsor evidence and is never inferred from local threshold progress.
- The service does not mutate authoritative QSO records or exact MODE/SUBMODE identity.
- Production service code has no credential, HTTP, FTX-1 hardware, Compose, or Room dependency.

Host/CI gates:

- CP-0006F Awards Center application service: **64/64 PASS**.
- CP-0006E award evidence persistence/import: **92/92 PASS**.
- CP-0006D Awards Center projection: **114/114 PASS**.
- CP-0006C award target/composite evaluator: **77/77 PASS**.
- CP-0006B official award catalog: **115/115 PASS**.
- CP-0006A award evaluator: **67/67 PASS**.
- CP-0005B manual/digital LoTW queue: **47/47 PASS**.
- CP-0005A universal QSO/logger: **78/78 PASS**.
- CP-0003B LoTW transaction: **53/53 PASS**.
- Inherited core: **42,062 PASS**.

Evidence:

- research/awards/CP-0006F_AWARDS_APPLICATION_SERVICE.md
- research/awards/CP-0006E_AWARD_EVIDENCE_PERSISTENCE.md
- SOFTWARE_TRACK.md
- CP-0006F finalization workflow run: {run_id}

### Evidence boundary

CP-0006F is host/CI evidence using platform-independent repositories and synthetic/local parsed ADIF fixtures. It does not claim automatic/fuzzy QSO matching, live LoTW download/login, sponsor account synchronization, claim submission, Android Room/SQLite storage, Compose UI, device persistence behavior, or FTX-1 hardware proof.

### Inherited verified ancestry

CP-0006E-AWARD_EVIDENCE_PERSISTENCE, CP-0006D-AWARDS_CENTER_PROJECTION, CP-0006C-AWARD_TARGET_ENRICHMENT, CP-0006B-OFFICIAL_AWARD_CATALOG, CP-0006A-AWARD_EVALUATION_ENGINE, CP-0005B-MANUAL_QSO_LOTW_QUEUE, CP-0005A-UNIVERSAL_QSO_LOGGER, CP-0003B-LOTW_TRANSACTION_SAFE, CP-0003A-TRUSTEDQSL_SIGNER, CP-0002E-NATIVE_MODE_REGRESSION, and prior native-mode checkpoints remain verified ancestry.

"""

readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals",
    "verified durable baseline",
)

readme = re.sub(
    r"\*\*Active software track:\*\* .*",
    "**Active software track:** CP-0006G — Extended official award catalog and evaluator coverage",
    readme,
    count=1,
)

readme = readme.replace(
    "- Current Git source baseline: CP-0006E-AWARD_EVIDENCE_PERSISTENCE",
    "- Current Git source baseline: CP-0006F-AWARDS_APPLICATION_SERVICE",
    1,
)
readme = readme.replace(
    "- Current Git source baseline: " + chr(96) + "CP-0006E-AWARD_EVIDENCE_PERSISTENCE" + chr(96),
    "- Current Git source baseline: " + chr(96) + "CP-0006F-AWARDS_APPLICATION_SERVICE" + chr(96),
    1,
)

next_section = """## Current exact next action

**CP-0006G — Extended official award catalog and evaluator coverage.**

1. Expand the official award catalog beyond the initial seven entries using only official issuing-organization sources with URL, source version/date, and retrieval date.
2. Prioritize the product-requirement targets ARRL VUCC/grid-oriented awards, ARRL Fred Fish Memorial Award where locally representable, CQ WAZ, CQ WPX, and useful regional/national awards for Europe and the U.S.
3. For each candidate, explicitly classify whether local evaluation is a distinct-target rule, composite rule, external program scoring, or unsupported until additional normalized target evidence exists.
4. Add any new normalized target kinds/evaluator shapes only when official rules require them; do not approximate grids, CQ zones, prefixes, counties, or sponsor-only credit.
5. Preserve exact QSO mode/submode, worked/confirmed/local-threshold/sponsor-standing separation, official claim links, and claim instructions.
6. Extend official-source ledger and host/CI tests; fail closed on ambiguous or sponsor-account-only requirements.
7. Do not use real sponsor accounts, credentials, claim submission, phone/radio/RF testing, or manual hardware validation.
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

**CP-0006G — Extended official award catalog and evaluator coverage**

Expand the verified official catalog using issuing-organization sources, prioritizing VUCC/grid-oriented awards, Fred Fish where locally representable, CQ WAZ, CQ WPX, and useful Europe/U.S. regional or national awards. Add new target/evaluator shapes only when official rules demand them; never approximate sponsor-only credit or missing geography.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = re.sub(
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n" + chr(96) + "CP-0006F-AWARDS_APPLICATION_SERVICE" + chr(96) + "\n\n## Deferred but incomplete hardware/account work",
    track,
    count=1,
    flags=re.S,
)
track = re.sub(
    r"## Active software checkpoint\n\n.*?\n\nRequired scope:\n.*?\n## Resume rule",
    """## Active software checkpoint

""" + chr(96) + """CP-0006G-EXTENDED_AWARD_CATALOG""" + chr(96) + """

Required scope:

- official issuing-organization research only for newly encoded awards
- source URL + version/date + retrieval date retained
- prioritize ARRL VUCC/grid awards, Fred Fish where locally representable, CQ WAZ, CQ WPX, and useful Europe/U.S. regional/national programs
- explicitly classify local evaluator support vs external/sponsor-only requirements
- add normalized target kinds/evaluator shapes only from verified rules
- no guessed grid/zone/prefix/county/program credit
- preserve exact QSO MODE/SUBMODE and local-vs-sponsor state separation
- official info/claim links and concise claim instructions
- host/CI-only verification
- no real sponsor accounts, credentials, claim submission, phone/radio/RF/manual hardware work
- skip later hardware/account-gated checkpoints under the owner execution override

## Resume rule""",
    track,
    count=1,
    flags=re.S,
)
track_path.write_text(track)

history_path = root / "checkpoints" / "CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0006F — Awards Center application service and evidence-ingestion orchestration" not in history:
    history = history.rstrip() + "\n\n" + f"""## CP-0006F — Awards Center application service and evidence-ingestion orchestration

Parent durable checkpoint: CP-0006E-AWARD_EVIDENCE_PERSISTENCE.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override remains active: do not return to CP-0003C until the owner explicitly says resume CP-0003C; skip checkpoints requiring phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now has one platform-independent Awards Center application service that composes the authoritative local logbook, persisted award evidence, explicit ADIF enrichment, and the verified projection. Evidence ingestion requires already-resolved immutable QSO ids, converts the full batch before one atomic repository write, fails closed on unknown/mismatched QSOs or malformed/conflicting evidence, and never performs callsign-only matching. Parsed LoTW-style host fixtures feed award progress without network access, while sponsor standing remains explicit external evidence.

Host/CI gate: Awards Center application service 64; award evidence persistence/import 92; Awards Center projection 114; target/composite evaluator 77; official catalog 115; award evaluator 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; inherited core 42,062 assertions, all PASS.

Finalization workflow run: {run_id}.

Evidence: research/awards/CP-0006F_AWARDS_APPLICATION_SERVICE.md.
"""
    history_path.write_text(history)
