# CP-0006E — Award evidence persistence and explicit ADIF enrichment import

Parent durable checkpoint: `CP-0006D-AWARDS_CENTER_PROJECTION`.

Evidence level: **host/CI software only**.

## Objective

Replace fixture-only award evidence with a provider-independent persistent evidence boundary and a conservative ADIF enrichment adapter.

The implementation keeps award metadata outside `QsoRecord`, preserves source provenance, and imports only award/confirmation fields that are explicitly present in the source record.

## Persistent evidence boundary

`AwardEvidenceRepository` owns three evidence streams:

- normalized `AwardTargetEvidence`
- explicit `AwardConfirmationEvidence`
- `OfficialAwardStandingRecord`

Writes occur as `AwardEvidenceBatch` transactions.

### Idempotence and conflicts

Target identity is based on:

- immutable QSO id
- target kind
- normalized target value
- provenance source id
- provenance source version
- provenance URL
- provenance record reference

Repeating the same evidence is idempotent.

The same logical provenance identity with different payload data fails closed.

The CP-0006C single-valued target rule is reused across the full candidate state, so one immutable QSO cannot silently acquire conflicting DXCC entities, U.S. states, continents, or IOTA groups.

POTA remains intentionally multi-valued because one QSO can explicitly carry more than one POTA reference.

Confirmation evidence is idempotent by QSO id + source + reference. A conflicting payload for the same confirmation identity fails closed.

Sponsor history is keyed by award id + recorded timestamp. A different sponsor state at the same timestamp is rejected rather than leaving ambiguous latest-standing semantics.

A failed batch commits nothing.

## Persistence snapshot

`AwardEvidenceSnapshotCodec` provides deterministic schema-versioned serialization.

The format persists:

- normalized target values
- complete target provenance
- confirmations
- sponsor-standing history

String fields are URL-safe Base64 encoded so tabs, Unicode and other record text cannot corrupt row boundaries.

`PersistedAwardEvidenceRepository` loads a snapshot from `AwardEvidenceSnapshotStore.Readable`.

For a write:

1. create and validate the complete candidate state;
2. serialize it;
3. write it to the backing store;
4. only then advance the repository's in-memory state.

If the backing store throws, in-memory state remains unchanged and the complete batch can be retried.

The storage interface is provider/platform independent so a later Android adapter can use Room or another durable local store without changing award-domain rules.

## ADIF enrichment

`AwardAdifEnrichmentAdapter` accepts:

- immutable local QSO id
- one parsed ADIF record
- explicit source metadata/version/provenance

The adapter consumes only explicit remote award fields.

### Direct normalized targets

- `DXCC` -> DXCC entity
- `STATE` -> U.S. state
- `CONT` -> continent
- `IOTA` -> IOTA group
- `POTA_REF` -> POTA reference(s)
- `APP_POTA_REF` -> explicit application POTA reference(s)
- `SIG=POTA` + `SIG_INFO` -> explicit POTA reference(s)

POTA lists may be comma- or semicolon-separated.

Every generated target retains:

- source id
- source version
- HTTPS source URL when supplied
- source record reference
- retrieval UTC when supplied

## No inferred geography

CP-0006E deliberately does not derive award targets from:

- callsign prefixes
- country names
- Maidenhead grids
- notes/free text
- `MY_DXCC`
- `MY_STATE`
- other `MY_*` station fields

Malformed explicit award fields fail closed instead of falling back to inference.

## Conservative confirmation import

Only explicit received-confirmation flags are converted:

- `LOTW_QSL_RCVD=Y` -> source `LOTW`
- `QSL_RCVD=Y` -> source `ADIF_QSL_RCVD`

Associated received-date text and source-record identity are retained in the confirmation reference when available.

Other values, including N/R/I/V/blank, do not become confirmation evidence.

Upload/sent metadata such as `LOTW_QSL_SENT=Y` is not confirmation.

An ordinary imported QSO is never silently treated as confirmed merely because it exists in ADIF.

## Existing parser integration

The adapter accepts the map returned by the existing `AdifCodec.parse()`; CP-0006E does not introduce a competing ADIF parser.

Synthetic parsed-ADIF evidence proves that explicit DXCC/state/continent/IOTA/POTA fields and explicit LoTW confirmation can flow through the existing parser into the persistent award evidence repository.

## Awards Center integration evidence

A persisted repository is reconstructed from its serialized snapshot and then fed into `AwardsCenterProjectionService`.

The host gate proves that six explicit confirmed continent records survive repository reconstruction and produce complete local WAC progress while the original authoritative QSO list remains unchanged.

## Host/CI evidence

Focused CP-0006E coverage proves:

- target write idempotence
- confirmation write idempotence
- sponsor-standing history/idempotence
- atomic conflict rejection
- single-valued target conflict rejection
- multi-reference POTA preservation
- deterministic snapshot serialization
- tab/Unicode-safe snapshot round trip
- repository restoration across instances
- backing-store failure does not advance state
- full-batch retry after storage failure
- explicit DXCC/STATE/CONT/IOTA/POTA import
- source/version/reference/retrieval provenance
- no callsign/country/grid/notes/MY_* inference
- direct and SIG/SIG_INFO POTA forms
- explicit received-confirmation import only
- no upload/sent-as-confirmed conversion
- existing ADIF parser compatibility
- persisted evidence -> Awards Center projection integration
- malformed explicit award data fails closed

Inherited gates remain required:

- CP-0006D Awards Center projection: 114
- CP-0006C target/composite evaluator: 77
- CP-0006B official catalog: 115
- CP-0006A award evaluator: 67
- CP-0005B LoTW queue: 47
- CP-0005A logger: 78
- CP-0003B LoTW transaction: 53
- inherited core: 42,062

## Evidence boundary

CP-0006E proves the storage contract, deterministic snapshot persistence semantics, explicit ADIF conversion and integration using synthetic/local fixtures.

It does **not** prove:

- a Room/SQLite production adapter
- arbitrary external file-system permissions
- fuzzy QSO matching
- live sponsor account synchronization
- live LoTW login/download
- award claim submission
- Android device persistence behavior
- FTX-1 hardware behavior

## Next software step

**CP-0006F — Awards Center application service and evidence-ingestion orchestration.**

Compose the verified logbook, persisted award evidence repository, ADIF evidence adapter and Awards Center projection behind one application-facing service.

Ingestion must require a resolved immutable local QSO id; the award layer must not invent fuzzy callsign-only matching.

Host fixtures may exercise LoTW/import-style parsed ADIF records, but real sponsor accounts remain deferred.

## Deferred checkpoint rule

CP-0003C remains **DEFERRED**. Do not return to CP-0003C until the owner explicitly says `resume CP-0003C`.

Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue with the next checkpoint that can be completed entirely through GitHub/CI.
