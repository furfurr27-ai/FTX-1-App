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
(root / "VERSION").write_text("v20-award-evidence-persistence\n")

readme_path = root / "README.md"
readme = readme_path.read_text()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** CP-0006E-AWARD_EVIDENCE_PERSISTENCE

Parent durable checkpoint: CP-0006D-AWARDS_CENTER_PROJECTION.

CP-0003C and CP-0004A/B/C remain explicitly deferred hardware/account checkpoints and are not implied complete by this software checkpoint.

CP-0006E is a GREEN host/CI persistence/import checkpoint. It replaces fixture-only award evidence with a provider-independent persistent repository/snapshot boundary and imports only explicit award/confirmation metadata from ADIF.

CP-0006E proves:

- Award target, confirmation and sponsor-standing evidence share one provider-independent repository boundary without modifying authoritative QSO records.
- Batch writes are atomic, idempotent and conflict-safe.
- Single-valued DXCC/state/continent/IOTA conflicts fail closed across the complete persisted candidate state.
- POTA can retain multiple explicit references for one immutable QSO.
- Persistence serialization is schema-versioned, deterministic and safe for tabs/Unicode.
- A new repository instance reconstructs exactly the persisted canonical evidence state.
- Backing-store failure does not advance in-memory state and the full batch can be retried.
- Explicit ADIF DXCC, STATE, CONT, IOTA, POTA_REF, APP_POTA_REF and SIG=POTA/SIG_INFO metadata can create normalized award-target evidence.
- Every imported target retains source id/version and optional HTTPS URL, record reference and retrieval time.
- Callsigns, country names, grids, notes and MY_* station metadata are not used to infer remote award targets.
- Only explicit LOTW_QSL_RCVD=Y and QSL_RCVD=Y flags create confirmation evidence.
- LoTW/QSL sent or upload metadata is never treated as confirmation.
- The existing AdifCodec parser feeds the enrichment adapter; CP-0006E does not create a competing parser.
- Persisted imported evidence can be reconstructed and fed to the CP-0006D Awards Center projection without mutating QSOs.

Host/CI gates:

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

- research/awards/CP-0006E_AWARD_EVIDENCE_PERSISTENCE.md
- research/awards/CP-0006D_AWARDS_CENTER_PROJECTION.md
- SOFTWARE_TRACK.md
- CP-0006E finalization workflow run: {run_id}

### Evidence boundary

CP-0006E is host/CI evidence using local/synthetic ADIF and storage fixtures. It does not claim a Room/SQLite production adapter, live sponsor-account synchronization, live LoTW login/download, claim submission, Android device persistence behavior, or FTX-1 hardware proof.

### Inherited verified ancestry

CP-0006D-AWARDS_CENTER_PROJECTION, CP-0006C-AWARD_TARGET_ENRICHMENT, CP-0006B-OFFICIAL_AWARD_CATALOG, CP-0006A-AWARD_EVALUATION_ENGINE, CP-0005B-MANUAL_QSO_LOTW_QUEUE, CP-0005A-UNIVERSAL_QSO_LOGGER, CP-0003B-LOTW_TRANSACTION_SAFE, CP-0003A-TRUSTEDQSL_SIGNER, CP-0002E-NATIVE_MODE_REGRESSION, and prior native-mode checkpoints remain verified ancestry.

"""

readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals",
    "verified durable baseline",
)

readme = re.sub(
    r"\*\*Active software track:\*\* .*",
    "**Active software track:** CP-0006F — Awards Center application service and evidence-ingestion orchestration",
    readme,
    count=1,
)

readme = readme.replace(
    "- Current Git source baseline: CP-0006D-AWARDS_CENTER_PROJECTION",
    "- Current Git source baseline: CP-0006E-AWARD_EVIDENCE_PERSISTENCE",
    1,
)
readme = readme.replace(
    "- Current Git source baseline: " + chr(96) + "CP-0006D-AWARDS_CENTER_PROJECTION" + chr(96),
    "- Current Git source baseline: " + chr(96) + "CP-0006E-AWARD_EVIDENCE_PERSISTENCE" + chr(96),
    1,
)

next_section = """## Current exact next action

**CP-0006F — Awards Center application service and evidence-ingestion orchestration.**

1. Compose LogbookRepository, AwardEvidenceRepository, AwardAdifEnrichmentAdapter and AwardsCenterProjectionService behind one UI-independent application service.
2. Generate Awards Center cards directly from the authoritative local logbook plus persisted evidence rather than requiring callers to manually assemble lists.
3. Add parsed-ADIF evidence ingestion that requires an already-resolved immutable local QSO id; the award layer must not invent fuzzy callsign-only matching.
4. Support deterministic batch ingestion with per-record provenance and atomic evidence writes.
5. Exercise LoTW/import-style parsed ADIF fixtures without using real accounts, credentials or network calls.
6. Keep sponsor standing and claimability explicit; no local progress or imported QSO presence may imply sponsor eligibility.
7. Keep Compose/Room/device wiring outside this checkpoint unless it can be compiled and verified without hardware; the core service must remain platform independent.
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

**CP-0006F — Awards Center application service and evidence-ingestion orchestration**

Compose the authoritative logbook, persisted award evidence, explicit ADIF enrichment and Awards Center projection behind one platform-independent service. Ingest parsed records only after an immutable local QSO id is resolved; do not add fuzzy callsign matching, real sponsor-account access or claim submission.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = re.sub(
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n" + chr(96) + "CP-0006E-AWARD_EVIDENCE_PERSISTENCE" + chr(96) + "\n\n## Deferred but incomplete hardware/account work",
    track,
    count=1,
    flags=re.S,
)
track = re.sub(
    r"## Active software checkpoint\n\n.*?\n\nRequired scope:\n.*?\n## Resume rule",
    """## Active software checkpoint

""" + chr(96) + """CP-0006F-AWARDS_APPLICATION_SERVICE""" + chr(96) + """

Required scope:

- platform-independent Awards Center application service
- compose LogbookRepository + AwardEvidenceRepository + AwardAdifEnrichmentAdapter + AwardsCenterProjectionService
- cards generated from authoritative local logbook plus persisted evidence
- parsed ADIF ingestion only after immutable local QSO id resolution
- deterministic batch ingestion with explicit record/source provenance
- no fuzzy callsign-only award matching or geography inference
- host fixtures for LoTW/import-style records without real accounts/network
- sponsor standing/claimability remains explicit and separate from local progress
- no real sponsor-account login/sync or claim submission
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
if "## CP-0006E — Award evidence persistence and explicit ADIF enrichment import" not in history:
    history = history.rstrip() + "\n\n" + f"""## CP-0006E — Award evidence persistence and explicit ADIF enrichment import

Parent durable checkpoint: CP-0006D-AWARDS_CENTER_PROJECTION.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override remains active: do not return to CP-0003C until the owner explicitly says resume CP-0003C; skip checkpoints requiring phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now has a provider-independent persistent award-evidence boundary with atomic/idempotent/conflict-safe batches and deterministic schema-versioned snapshot serialization. Explicit ADIF DXCC/STATE/CONT/IOTA/POTA metadata can create provenance-bearing target evidence; only explicit received-confirmation flags become confirmation evidence. Callsign/country/grid/notes/MY_* fields are not used for remote award inference, upload/sent state is not confirmation, and backing-store failure cannot advance repository state.

Host/CI gate: award evidence persistence/import 92; Awards Center projection 114; target/composite evaluator 77; official catalog 115; award evaluator 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; inherited core 42,062 assertions, all PASS.

Finalization workflow run: {run_id}.

Evidence: research/awards/CP-0006E_AWARD_EVIDENCE_PERSISTENCE.md.
"""
    history_path.write_text(history)
