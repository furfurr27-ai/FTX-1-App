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
(root / "VERSION").write_text("v22-extended-award-catalog\n")

readme_path = root / "README.md"
readme = readme_path.read_text()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** CP-0006G-EXTENDED_AWARD_CATALOG

Parent durable checkpoint: CP-0006F-AWARDS_APPLICATION_SERVICE.

CP-0003C and CP-0004A/B/C remain explicitly deferred hardware/account checkpoints and are not implied complete by this software checkpoint.

CP-0006G is a GREEN host/CI + issuer-source research checkpoint. It expands the official catalog with ARRL VUCC 50 MHz, 144 MHz, 432 MHz, and Fred Fish Memorial Award rules; adds explicit Maidenhead four-character grid evidence; and makes official required-band restrictions part of the evaluator rather than a UI convention.

CP-0006G proves:

- New encoded award rules are sourced only from current ARRL issuing-organization pages/rules.
- 50 MHz VUCC requires 100 confirmed grids on 6m.
- 144 MHz VUCC requires 100 confirmed grids on 2m.
- 432 MHz VUCC requires 50 confirmed grids on 70cm.
- VUCC contacts before 1983-01-01 are excluded locally.
- VUCC sponsor conditions such as repeater, aeronautical-mobile, grid-boundary, and 200 km applicant-location rules remain visible rather than silently claimed solved.
- FFMA requires all 488 ARRL-listed four-character grids, on 6m, from 1983-01-01 forward.
- The catalog's FFMA target universe contains exactly 488 grids matching the issuer's published field totals.
- Four-, six-, and eight-character explicit Maidenhead locators normalize to a validated four-character award grid.
- Conflicting remote grid evidence for one immutable QSO fails closed.
- Explicit ADIF GRIDSQUARE may create remote grid evidence with provenance; MY_GRIDSQUARE does not.
- Required award bands are enforced inside the official progress engine and cannot be bypassed by a user view filter.
- Local VUCC/FFMA threshold completion never becomes sponsor claimability, awarded, or credited state.
- CQ WAZ/WPX are intentionally not encoded because a current issuer-authoritative rules source could not be durably pinned through the research path; ARRL LoTW integration material is not substituted for CQ rules.
- DARC DLD/DOK is also deferred rather than encoded from a partially retrievable source.

Host/CI gates:

- CP-0006G extended official award catalog/grid evaluator: **96/96 PASS**.
- CP-0006F Awards Center application service: **64/64 PASS**.
- CP-0006E award evidence persistence/import: **92/92 PASS**.
- CP-0006D Awards Center projection: **114/114 PASS**.
- CP-0006C award target/composite evaluator: **77/77 PASS**.
- CP-0006B base official award catalog regression: **115/115 PASS**.
- CP-0006A award evaluator: **67/67 PASS**.
- CP-0005B manual/digital LoTW queue: **47/47 PASS**.
- CP-0005A universal QSO/logger: **78/78 PASS**.
- CP-0003B LoTW transaction: **53/53 PASS**.
- Inherited core: **42,062 PASS**.

Evidence:

- research/awards/CP-0006G_EXTENDED_AWARD_CATALOG.md
- research/awards/OFFICIAL_AWARD_SOURCES.tsv
- research/awards/CP-0006F_AWARDS_APPLICATION_SERVICE.md
- SOFTWARE_TRACK.md
- CP-0006G finalization workflow run: {run_id}

### Evidence boundary

CP-0006G proves official rule data, target normalization, required-band evaluation, and host/CI progress behavior. It does not prove sponsor acceptance of station-location/grid-boundary evidence, live award-account state, claim submission, CQ WAZ/WPX rules, DARC DLD rules, Android map rendering, map geometry licensing, or FTX-1 hardware behavior.

### Inherited verified ancestry

CP-0006F-AWARDS_APPLICATION_SERVICE, CP-0006E-AWARD_EVIDENCE_PERSISTENCE, CP-0006D-AWARDS_CENTER_PROJECTION, CP-0006C-AWARD_TARGET_ENRICHMENT, CP-0006B-OFFICIAL_AWARD_CATALOG, CP-0006A-AWARD_EVALUATION_ENGINE, CP-0005B-MANUAL_QSO_LOTW_QUEUE, CP-0005A-UNIVERSAL_QSO_LOGGER, CP-0003B-LOTW_TRANSACTION_SAFE, CP-0003A-TRUSTEDQSL_SIGNER, CP-0002E-NATIVE_MODE_REGRESSION, and prior native-mode checkpoints remain verified ancestry.

"""

readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals",
    "verified durable baseline",
)

readme = re.sub(
    r"\*\*Active software track:\*\* .*",
    "**Active software track:** CP-0007A — Award-area map projection foundation",
    readme,
    count=1,
)

readme = readme.replace(
    "- Current Git source baseline: CP-0006F-AWARDS_APPLICATION_SERVICE",
    "- Current Git source baseline: CP-0006G-EXTENDED_AWARD_CATALOG",
    1,
)
readme = readme.replace(
    "- Current Git source baseline: " + chr(96) + "CP-0006F-AWARDS_APPLICATION_SERVICE" + chr(96),
    "- Current Git source baseline: " + chr(96) + "CP-0006G-EXTENDED_AWARD_CATALOG" + chr(96),
    1,
)

next_section = """## Current exact next action

**CP-0007A — Award-area map projection foundation.**

1. Build a UI-independent map-layer read model from the authoritative logbook, persisted award evidence, and verified Awards Center progress.
2. Represent geographic award targets with explicit states such as NEEDED, WORKED_UNCONFIRMED, CONFIRMED, and LOCAL_THRESHOLD_MET without treating local threshold as sponsor credit.
3. Start with target types already explicitly supported: U.S. states and Maidenhead four-character grids.
4. Preserve source/provenance for any geometry or point data; do not derive boundaries, coordinates, CQ zones, DXCC geometry, islands, parks, or summits from callsigns.
5. Keep geometry identity separate from geometry content so a later licensed/versioned offline geometry pack can be swapped without changing award rules.
6. Support band/mode/date query context from the Awards Center projection.
7. Add deterministic clustering/aggregation-ready map records without requiring Android Compose/Maps rendering.
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

**CP-0007A — Award-area map projection foundation**

Build a platform-independent map-layer read model from the verified Awards Center and award evidence, beginning with U.S. states and Maidenhead four-character grids. Keep geometry identity/source separate from geometry content, preserve needed/worked/confirmed/local-threshold states, and never derive geography from callsigns.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = re.sub(
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n" + chr(96) + "CP-0006G-EXTENDED_AWARD_CATALOG" + chr(96) + "\n\n## Deferred but incomplete hardware/account work",
    track,
    count=1,
    flags=re.S,
)
track = re.sub(
    r"## Active software checkpoint\n\n.*?\n\nRequired scope:\n.*?\n## Resume rule",
    """## Active software checkpoint

""" + chr(96) + """CP-0007A-AWARD_MAP_PROJECTION""" + chr(96) + """

Required scope:

- platform-independent award-area map-layer projection
- source from authoritative logbook + persisted award evidence + Awards Center progress
- explicit NEEDED / WORKED_UNCONFIRMED / CONFIRMED / LOCAL_THRESHOLD_MET semantics
- first supported geographic targets: U.S. states and Maidenhead four-character grids
- geometry identity/source metadata separated from geometry payload
- no callsign-derived boundaries/coordinates/zones/entities/islands/parks/summits
- band/mode/date query context preserved
- deterministic clustering/aggregation-ready records
- host/CI-only verification before Android map rendering
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
if "## CP-0006G — Extended official award catalog and evaluator coverage" not in history:
    history = history.rstrip() + "\n\n" + f"""## CP-0006G — Extended official award catalog and evaluator coverage

Parent durable checkpoint: CP-0006F-AWARDS_APPLICATION_SERVICE.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override remains active: do not return to CP-0003C until the owner explicitly says resume CP-0003C; skip checkpoints requiring phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now has issuer-sourced ARRL VUCC 50 MHz/144 MHz/432 MHz and FFMA catalog entries, explicit Maidenhead four-character award evidence, ADIF GRIDSQUARE import, and evaluator-enforced required-band restrictions. The exact 488-grid FFMA universe is represented and locally testable. CQ WAZ/WPX and DARC DLD remain deliberately unencoded where a durable issuer-authoritative source could not be fully pinned through this research run.

Host/CI gate: extended catalog/grid evaluator 96; Awards Center application service 64; award evidence persistence/import 92; Awards Center projection 114; target/composite evaluator 77; base official catalog 115; award evaluator 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; inherited core 42,062 assertions, all PASS.

Finalization workflow run: {run_id}.

Evidence: research/awards/CP-0006G_EXTENDED_AWARD_CATALOG.md and research/awards/OFFICIAL_AWARD_SOURCES.tsv.
"""
    history_path.write_text(history)
