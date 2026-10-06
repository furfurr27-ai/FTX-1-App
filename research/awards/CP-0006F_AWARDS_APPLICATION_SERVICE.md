# CP-0006F — Awards Center application service and evidence-ingestion orchestration

Parent durable checkpoint: `CP-0006E-AWARD_EVIDENCE_PERSISTENCE`.

Evidence level: **host/CI software only**.

## Objective

Compose the verified local logbook, persisted award evidence, explicit ADIF enrichment adapter, and Awards Center projection behind one platform-independent application-facing service.

The service is deliberately below Android UI/storage implementation. It owns orchestration semantics, not hardware, credentials, network login, sponsor claims, Compose, or Room.

## Application service

`AwardsCenterApplicationService` composes:

- `LogbookRepository`
- `AwardEvidenceRepository`
- `AwardAdifEnrichmentAdapter`
- `AwardsCenterProjectionService`

### Current Awards Center cards

`cards(query)` reads the authoritative local logbook through `logbook.all()`, reads one current evidence snapshot from `AwardEvidenceRepository`, builds the target index, and delegates to the verified projection service.

Callers no longer need to manually assemble QSO/evidence/sponsor-standing lists.

The service therefore reflects later authoritative logbook and evidence writes on the next read without keeping a competing cache.

## Resolved ADIF evidence ingestion

`ResolvedAwardAdifRecord` requires:

- an immutable local QSO id
- parsed ADIF fields
- explicit source/provenance metadata

The award service never searches for a QSO by callsign, date, grid, country, prefix, or free text.

The immutable QSO id must already have been resolved by another import/synchronization layer.

If an explicit `CALL` field is present, it is used only as a fail-closed consistency check against the already-resolved QSO. It is not a resolver.

A missing `CALL` remains acceptable after an immutable QSO id is supplied.

## Atomic batch orchestration

`ingestResolvedAdifRecords()`:

1. deterministically orders the supplied resolved records;
2. verifies every immutable QSO id exists in the authoritative logbook;
3. validates any explicit CALL consistency;
4. converts every source record through the verified CP-0006E enrichment adapter;
5. combines the converted target/confirmation/sponsor evidence;
6. performs one `AwardEvidenceRepository.apply()` call.

Consequences:

- an unknown QSO anywhere in the batch writes nothing;
- a CALL/QSO mismatch anywhere in the batch writes nothing;
- malformed explicit award metadata anywhere in the batch writes nothing;
- a repository evidence conflict anywhere in the batch writes nothing;
- repeating the same batch is idempotent;
- reversed input ordering produces the same canonical evidence state.

## LoTW/import-style records

Host fixtures parse ADIF through the existing `AdifCodec`, bind each parsed record to a known immutable QSO id, and ingest explicit `CONT` + `LOTW_QSL_RCVD=Y` metadata.

Six such records produce complete local WAC progress through the application service without any network call or real LoTW account.

This is evidence of orchestration semantics only. It is not a live LoTW synchronization claim.

## Sponsor standing

`recordSponsorStanding()` is explicit external evidence.

The service:

- requires the award to exist in the verified catalog;
- writes the supplied standing through the evidence repository;
- never consults local threshold progress to derive sponsor eligibility.

Local progress therefore cannot silently become sponsor claimability.

## Query pass-through

Mixed/CW/Phone/Digital, band, and UTC date-range views pass through the application service to the verified CP-0006D projection.

The service does not rewrite exact QSO MODE/SUBMODE or encode award rules.

## Host/CI evidence

Focused CP-0006F gate:

- **64/64 assertions PASS**
- cards read authoritative current logbook/evidence state
- single resolved record ingestion
- unknown immutable QSO rejection
- explicit CALL consistency validation
- duplicate callsign does not select/redirect a QSO
- missing CALL allowed after immutable id resolution
- atomic unknown-QSO batch rejection
- atomic CALL-mismatch batch rejection
- atomic malformed-award-field rejection
- atomic repository-conflict rejection
- deterministic batch ordering
- idempotent repeated batch
- parsed LoTW-style ADIF fixture ingestion with no network
- persisted evidence immediately feeds Awards Center projection
- Mixed/mode/band/date query pass-through
- explicit sponsor standing only
- unknown catalog award standing rejection
- empty batch no-op
- authoritative QSO immutability
- exact mode/submode preservation

Static host checks additionally prove:

- production service contains no callsign-based QSO resolver;
- batch ingestion performs one combined evidence apply;
- cards read `logbook.all()` plus one evidence snapshot;
- service has no credentials, HTTP transport, FTX-1 hardware, Compose, or Room dependency.

Inherited regressions remain green:

- CP-0006E award evidence persistence/import: 92
- CP-0006D Awards Center projection: 114
- CP-0006C target/composite evaluator: 77
- CP-0006B official award catalog: 115
- CP-0006A award evaluator: 67
- CP-0005B queue: 47
- CP-0005A logger: 78
- CP-0003B LoTW transaction: 53
- inherited core: 42,062

## Evidence boundary

CP-0006F proves application-service orchestration with in-memory/platform-independent repositories and synthetic/local parsed ADIF fixtures.

It does **not** prove:

- automatic/fuzzy QSO matching
- live LoTW download/login
- sponsor account synchronization
- award claim submission
- Android Room/SQLite storage
- Compose UI
- device persistence behavior
- FTX-1 hardware behavior

## Next software step

**CP-0006G — Extended official award catalog and evaluator coverage.**

The product requirements call for broader major/minor award coverage beyond the initial catalog.

The next GitHub/CI-only checkpoint should research official issuing-organization documentation and add a conservative first expansion covering appropriate candidates such as:

- ARRL VUCC / grid-oriented awards
- ARRL Fred Fish Memorial Award where rules are locally representable
- CQ WAZ
- CQ WPX
- useful regional/national programs for Europe and the U.S.

Do not encode any rule until its official source, version/date, claim path, confirmation rules, and local-vs-external evaluation boundary are verified.

## Deferred checkpoint rule

CP-0003C remains **DEFERRED**. Do not return to CP-0003C until the owner explicitly says `resume CP-0003C`.

Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue with the next checkpoint that can be completed entirely through GitHub/CI.
