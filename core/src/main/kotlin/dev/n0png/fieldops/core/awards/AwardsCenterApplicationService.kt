package dev.n0png.fieldops.core.awards

import dev.n0png.fieldops.core.logbook.LogbookRepository
import dev.n0png.fieldops.core.logbook.QsoRecord
import dev.n0png.fieldops.core.map.AwardAreaGeometryCatalog
import dev.n0png.fieldops.core.map.AwardAreaMapLayer
import dev.n0png.fieldops.core.map.AwardAreaMapProjectionService

data class ResolvedAwardAdifRecord(
    val qsoId: Long,
    val fields: Map<String, String>,
    val source: AwardAdifImportSource,
) {
    init {
        require(qsoId > 0) { "Resolved award ADIF record requires a positive immutable QSO id" }
    }
}

data class AwardsEvidenceIngestionResult(
    val qsoIds: Set<Long>,
    val recordsProcessed: Int,
    val writeResult: AwardEvidenceApplyResult,
)

class AwardsCenterApplicationService(
    private val logbook: LogbookRepository,
    private val evidenceRepository: AwardEvidenceRepository,
    private val enrichmentAdapter: AwardAdifEnrichmentAdapter = AwardAdifEnrichmentAdapter(),
    private val projectionService: AwardsCenterProjectionService = AwardsCenterProjectionService(),
    private val mapProjectionService: AwardAreaMapProjectionService = AwardAreaMapProjectionService(),
) {
    /**
     * Builds the current Awards Center entirely from the authoritative local
     * logbook plus the current persisted award-evidence snapshot.
     *
     * Callers do not assemble evidence lists themselves.
     */
    fun cards(query: AwardsCenterQuery = AwardsCenterQuery()): List<AwardsCenterAwardCard> {
        val evidence = evidenceRepository.snapshot()
        return projectionService.project(
            qsos = logbook.all(),
            targets = AwardTargetEvidenceIndex(evidence.targets),
            confirmations = evidence.confirmations,
            sponsorStandings = evidence.sponsorStandings,
            query = query,
        )
    }

    /**
     * Builds award-area map records from the same authoritative logbook and
     * persisted award evidence used by Awards Center.
     *
     * Geometry remains an optional metadata binding; no coordinates or
     * boundaries are inferred by this service.
     */
    fun awardMapLayers(
        query: AwardsCenterQuery = AwardsCenterQuery(),
        geometryCatalog: AwardAreaGeometryCatalog = AwardAreaGeometryCatalog.EMPTY,
    ): List<AwardAreaMapLayer> =
        mapProjectionService.project(
            qsos = logbook.all(),
            evidence = evidenceRepository.snapshot(),
            query = query,
            geometryCatalog = geometryCatalog,
        )

    /**
     * Import award evidence only after another layer has already resolved an
     * immutable local QSO id.
     *
     * This service deliberately does not search the logbook by callsign, date,
     * grid, country, or free text. If CALL is present in the source record it
     * is used only as a fail-closed consistency check against the resolved QSO.
     */
    fun ingestResolvedAdifRecord(record: ResolvedAwardAdifRecord): AwardsEvidenceIngestionResult =
        ingestResolvedAdifRecords(listOf(record))

    /**
     * Convert every resolved record first, then apply one repository batch.
     *
     * Therefore an unknown QSO, record/QSO CALL mismatch, malformed explicit
     * award field, or evidence conflict prevents the whole ingestion batch
     * from being written.
     */
    fun ingestResolvedAdifRecords(
        records: Iterable<ResolvedAwardAdifRecord>,
    ): AwardsEvidenceIngestionResult {
        val ordered = records.toList().sortedWith(
            compareBy<ResolvedAwardAdifRecord>(
                { it.qsoId },
                { it.source.sourceId },
                { it.source.sourceVersion },
                { it.source.recordReference ?: "" },
            )
        )

        if (ordered.isEmpty()) {
            return AwardsEvidenceIngestionResult(
                qsoIds = emptySet(),
                recordsProcessed = 0,
                writeResult = AwardEvidenceApplyResult(),
            )
        }

        val resolved = ordered.map { input ->
            val qso = requireNotNull(logbook.get(input.qsoId)) {
                "Award evidence references unknown immutable QSO id ${input.qsoId}"
            }
            validateBinding(qso, input.fields)
            enrichmentAdapter.fromRecord(
                qsoId = input.qsoId,
                record = input.fields,
                source = input.source,
            )
        }

        val combined = AwardEvidenceBatch(
            targets = resolved.flatMap { it.targets }.distinct(),
            confirmations = resolved.flatMap { it.confirmations }.distinct(),
            sponsorStandings = resolved.flatMap { it.sponsorStandings }.distinct(),
        )

        val result = evidenceRepository.apply(combined)
        return AwardsEvidenceIngestionResult(
            qsoIds = ordered.mapTo(linkedSetOf()) { it.qsoId },
            recordsProcessed = ordered.size,
            writeResult = result,
        )
    }

    /**
     * Sponsor standing is always explicit external evidence.
     *
     * Recording it through this service never consults local progress and
     * cannot be triggered by reaching a local award threshold.
     */
    fun recordSponsorStanding(
        standing: OfficialAwardStandingRecord,
    ): AwardEvidenceApplyResult {
        OfficialAwardCatalog.require(standing.awardId)
        return evidenceRepository.apply(
            AwardEvidenceBatch(sponsorStandings = listOf(standing))
        )
    }

    fun evidenceSnapshot(): AwardEvidenceSnapshot = evidenceRepository.snapshot()

    private fun validateBinding(
        qso: QsoRecord,
        fields: Map<String, String>,
    ) {
        val normalized = fields.entries.associate { (key, value) ->
            key.trim().uppercase() to value.trim()
        }
        val explicitCall = normalized["CALL"]
            ?.takeIf { it.isNotBlank() }
            ?.uppercase()

        if (explicitCall != null) {
            require(explicitCall == qso.call.trim().uppercase()) {
                "Resolved ADIF CALL $explicitCall does not match immutable QSO ${qso.id} CALL ${qso.call}"
            }
        }
    }
}
