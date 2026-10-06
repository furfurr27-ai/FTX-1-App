package dev.n0png.fieldops.core.awards

import java.util.Base64

enum class AwardEvidenceWriteResult {
    INSERTED,
    UNCHANGED,
}

data class AwardEvidenceApplyResult(
    val insertedTargets: Int = 0,
    val unchangedTargets: Int = 0,
    val insertedConfirmations: Int = 0,
    val unchangedConfirmations: Int = 0,
    val insertedSponsorStandings: Int = 0,
    val unchangedSponsorStandings: Int = 0,
)

data class AwardEvidenceBatch(
    val targets: List<AwardTargetEvidence> = emptyList(),
    val confirmations: List<AwardConfirmationEvidence> = emptyList(),
    val sponsorStandings: List<OfficialAwardStandingRecord> = emptyList(),
)

data class AwardEvidenceSnapshot(
    val schemaVersion: Int = 1,
    val targets: List<AwardTargetEvidence> = emptyList(),
    val confirmations: List<AwardConfirmationEvidence> = emptyList(),
    val sponsorStandings: List<OfficialAwardStandingRecord> = emptyList(),
) {
    init {
        require(schemaVersion == 1) { "Unsupported award evidence snapshot schema: $schemaVersion" }
    }
}

interface AwardEvidenceRepository {
    fun apply(batch: AwardEvidenceBatch): AwardEvidenceApplyResult
    fun snapshot(): AwardEvidenceSnapshot

    fun targetEvidence(): List<AwardTargetEvidence> = snapshot().targets
    fun confirmations(): List<AwardConfirmationEvidence> = snapshot().confirmations
    fun sponsorStandings(): List<OfficialAwardStandingRecord> = snapshot().sponsorStandings
}

class InMemoryAwardEvidenceRepository(
    initial: AwardEvidenceSnapshot = AwardEvidenceSnapshot(),
) : AwardEvidenceRepository {
    private var state: AwardEvidenceSnapshot = canonicalSnapshot(initial)

    @Synchronized
    override fun apply(batch: AwardEvidenceBatch): AwardEvidenceApplyResult {
        val merged = merge(state, batch)
        state = merged.snapshot
        return merged.result
    }

    @Synchronized
    override fun snapshot(): AwardEvidenceSnapshot = state

    companion object {
        internal fun merge(
            current: AwardEvidenceSnapshot,
            batch: AwardEvidenceBatch,
        ): MergeOutcome {
            val targetMap = linkedMapOf<TargetIdentity, AwardTargetEvidence>()
            current.targets.forEach { targetMap[targetIdentity(it)] = it }

            var insertedTargets = 0
            var unchangedTargets = 0
            for (candidate in batch.targets) {
                val canonicalCandidate = canonicalTarget(candidate)
                val key = targetIdentity(canonicalCandidate)
                val existing = targetMap[key]
                if (existing == null) {
                    targetMap[key] = canonicalCandidate
                    insertedTargets++
                } else if (existing == canonicalCandidate) {
                    unchangedTargets++
                } else {
                    throw IllegalArgumentException(
                        "Conflicting award target evidence for QSO ${candidate.qsoId}, " +
                            "kind ${candidate.kind}, value ${candidate.normalizedValue}, " +
                            "provenance ${candidate.provenance.sourceId}/${candidate.provenance.sourceVersion}"
                    )
                }
            }

            // Reuse the verified CP-0006C conflict rules across the complete candidate state.
            AwardTargetEvidenceIndex(targetMap.values)

            val confirmationMap = linkedMapOf<ConfirmationIdentity, AwardConfirmationEvidence>()
            current.confirmations.forEach { confirmationMap[confirmationIdentity(it)] = it }

            var insertedConfirmations = 0
            var unchangedConfirmations = 0
            for (candidate in batch.confirmations) {
                val key = confirmationIdentity(candidate)
                val existing = confirmationMap[key]
                if (existing == null) {
                    confirmationMap[key] = candidate
                    insertedConfirmations++
                } else if (existing == candidate) {
                    unchangedConfirmations++
                } else {
                    throw IllegalArgumentException(
                        "Conflicting confirmation evidence for QSO ${candidate.qsoId}, " +
                            "source ${candidate.source}, reference ${candidate.reference}"
                    )
                }
            }

            val standingMap = linkedMapOf<SponsorStandingIdentity, OfficialAwardStandingRecord>()
            current.sponsorStandings.forEach { standingMap[sponsorStandingIdentity(it)] = it }

            var insertedSponsor = 0
            var unchangedSponsor = 0
            for (candidate in batch.sponsorStandings) {
                val key = sponsorStandingIdentity(candidate)
                val existing = standingMap[key]
                if (existing == null) {
                    standingMap[key] = candidate
                    insertedSponsor++
                } else if (existing == candidate) {
                    unchangedSponsor++
                } else {
                    throw IllegalArgumentException(
                        "Conflicting sponsor standing evidence for award ${candidate.awardId} " +
                            "at ${candidate.recordedAtUtcMillis}"
                    )
                }
            }

            val next = AwardEvidenceSnapshot(
                targets = targetMap.values.sortedWith(targetComparator),
                confirmations = confirmationMap.values.sortedWith(confirmationComparator),
                sponsorStandings = standingMap.values.sortedWith(sponsorComparator),
            )
            return MergeOutcome(
                snapshot = next,
                result = AwardEvidenceApplyResult(
                    insertedTargets = insertedTargets,
                    unchangedTargets = unchangedTargets,
                    insertedConfirmations = insertedConfirmations,
                    unchangedConfirmations = unchangedConfirmations,
                    insertedSponsorStandings = insertedSponsor,
                    unchangedSponsorStandings = unchangedSponsor,
                ),
            )
        }

        internal fun canonicalSnapshot(snapshot: AwardEvidenceSnapshot): AwardEvidenceSnapshot =
            merge(AwardEvidenceSnapshot(), AwardEvidenceBatch(
                targets = snapshot.targets,
                confirmations = snapshot.confirmations,
                sponsorStandings = snapshot.sponsorStandings,
            )).snapshot

        internal data class MergeOutcome(
            val snapshot: AwardEvidenceSnapshot,
            val result: AwardEvidenceApplyResult,
        )

        private data class TargetIdentity(
            val qsoId: Long,
            val kind: OfficialAwardTargetKind,
            val normalizedValue: String,
            val sourceId: String,
            val sourceVersion: String,
            val sourceUrl: String?,
            val reference: String?,
        )

        private data class ConfirmationIdentity(
            val qsoId: Long,
            val source: String,
            val reference: String?,
        )

        private data class SponsorStandingIdentity(
            val awardId: String,
            val recordedAtUtcMillis: Long?,
        )

        private fun canonicalTarget(value: AwardTargetEvidence): AwardTargetEvidence =
            if (value.value == value.normalizedValue) value else value.copy(value = value.normalizedValue)

        private fun targetIdentity(value: AwardTargetEvidence) = TargetIdentity(
            qsoId = value.qsoId,
            kind = value.kind,
            normalizedValue = value.normalizedValue,
            sourceId = value.provenance.sourceId.trim(),
            sourceVersion = value.provenance.sourceVersion.trim(),
            sourceUrl = value.provenance.sourceUrl,
            reference = value.provenance.reference,
        )

        private fun confirmationIdentity(value: AwardConfirmationEvidence) = ConfirmationIdentity(
            qsoId = value.qsoId,
            source = value.source.trim().uppercase(),
            reference = value.reference,
        )

        private fun sponsorStandingIdentity(value: OfficialAwardStandingRecord) =
            SponsorStandingIdentity(
                awardId = value.awardId.trim().uppercase(),
                recordedAtUtcMillis = value.recordedAtUtcMillis,
            )

        private val targetComparator = compareBy<AwardTargetEvidence>(
            { it.qsoId },
            { it.kind.name },
            { it.normalizedValue },
            { it.provenance.sourceId },
            { it.provenance.sourceVersion },
            { it.provenance.reference ?: "" },
        )

        private val confirmationComparator = compareBy<AwardConfirmationEvidence>(
            { it.qsoId },
            { it.source.uppercase() },
            { it.reference ?: "" },
        )

        private val sponsorComparator = compareBy<OfficialAwardStandingRecord>(
            { it.awardId.uppercase() },
            { it.recordedAtUtcMillis ?: Long.MIN_VALUE },
            { it.standing.name },
            { it.sponsorReference ?: "" },
        )
    }
}

fun interface AwardEvidenceSnapshotStore {
    fun write(serializedSnapshot: String)

    interface Readable : AwardEvidenceSnapshotStore {
        fun read(): String?
    }
}

class InMemoryAwardEvidenceSnapshotStore(
    initialSerializedSnapshot: String? = null,
) : AwardEvidenceSnapshotStore.Readable {
    private var content: String? = initialSerializedSnapshot

    override fun read(): String? = content

    override fun write(serializedSnapshot: String) {
        content = serializedSnapshot
    }

    fun serialized(): String? = content
}

class PersistedAwardEvidenceRepository(
    private val store: AwardEvidenceSnapshotStore.Readable,
) : AwardEvidenceRepository {
    private var state: AwardEvidenceSnapshot =
        store.read()?.let(AwardEvidenceSnapshotCodec::decode) ?: AwardEvidenceSnapshot()

    @Synchronized
    override fun apply(batch: AwardEvidenceBatch): AwardEvidenceApplyResult {
        val merged = InMemoryAwardEvidenceRepository.merge(state, batch)

        // Persist first. If the backing store rejects the write, the in-memory
        // state is not advanced and callers can safely retry the whole batch.
        if (merged.snapshot != state) {
            store.write(AwardEvidenceSnapshotCodec.encode(merged.snapshot))
            state = merged.snapshot
        }
        return merged.result
    }

    @Synchronized
    override fun snapshot(): AwardEvidenceSnapshot = state
}

object AwardEvidenceSnapshotCodec {
    private const val MAGIC = "FIELDOPS_AWARD_EVIDENCE"
    private const val VERSION = "1"

    fun encode(snapshot: AwardEvidenceSnapshot): String {
        val canonical = InMemoryAwardEvidenceRepository.canonicalSnapshot(snapshot)
        return buildString {
            append(MAGIC).append('\t').append(VERSION).append('\n')

            for (target in canonical.targets) {
                append("T\t")
                append(target.qsoId).append('\t')
                append(target.kind.name).append('\t')
                append(encoded(target.normalizedValue)).append('\t')
                append(encoded(target.provenance.sourceId)).append('\t')
                append(encoded(target.provenance.sourceVersion)).append('\t')
                append(nullableEncoded(target.provenance.sourceUrl)).append('\t')
                append(nullableEncoded(target.provenance.reference)).append('\t')
                append(target.provenance.retrievedAtUtcMillis?.toString() ?: "-")
                append('\n')
            }

            for (confirmation in canonical.confirmations) {
                append("C\t")
                append(confirmation.qsoId).append('\t')
                append(encoded(confirmation.source)).append('\t')
                append(nullableEncoded(confirmation.reference)).append('\t')
                append(confirmation.confirmedAtUtcMillis?.toString() ?: "-")
                append('\n')
            }

            for (standing in canonical.sponsorStandings) {
                append("S\t")
                append(encoded(standing.awardId)).append('\t')
                append(standing.standing.name).append('\t')
                append(nullableEncoded(standing.sponsorReference)).append('\t')
                append(standing.recordedAtUtcMillis?.toString() ?: "-")
                append('\n')
            }
        }
    }

    fun decode(text: String): AwardEvidenceSnapshot {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.isNotEmpty()) { "Award evidence snapshot is empty" }
        require(lines.first() == "$MAGIC\t$VERSION") {
            "Unsupported or invalid award evidence snapshot header"
        }

        val targets = mutableListOf<AwardTargetEvidence>()
        val confirmations = mutableListOf<AwardConfirmationEvidence>()
        val standings = mutableListOf<OfficialAwardStandingRecord>()

        for ((index, line) in lines.drop(1).withIndex()) {
            val fields = line.split('\t')
            when (fields.firstOrNull()) {
                "T" -> {
                    require(fields.size == 9) { "Invalid target evidence row at line ${index + 2}" }
                    targets += AwardTargetEvidence(
                        qsoId = fields[1].toLong(),
                        kind = OfficialAwardTargetKind.valueOf(fields[2]),
                        value = decoded(fields[3]),
                        provenance = AwardTargetProvenance(
                            sourceId = decoded(fields[4]),
                            sourceVersion = decoded(fields[5]),
                            sourceUrl = nullableDecoded(fields[6]),
                            reference = nullableDecoded(fields[7]),
                            retrievedAtUtcMillis = nullableLong(fields[8]),
                        ),
                    )
                }

                "C" -> {
                    require(fields.size == 5) { "Invalid confirmation row at line ${index + 2}" }
                    confirmations += AwardConfirmationEvidence(
                        qsoId = fields[1].toLong(),
                        source = decoded(fields[2]),
                        reference = nullableDecoded(fields[3]),
                        confirmedAtUtcMillis = nullableLong(fields[4]),
                    )
                }

                "S" -> {
                    require(fields.size == 5) { "Invalid sponsor standing row at line ${index + 2}" }
                    standings += OfficialAwardStandingRecord(
                        awardId = decoded(fields[1]),
                        standing = OfficialAwardStanding.valueOf(fields[2]),
                        sponsorReference = nullableDecoded(fields[3]),
                        recordedAtUtcMillis = nullableLong(fields[4]),
                    )
                }

                else -> throw IllegalArgumentException(
                    "Unknown award evidence snapshot row type at line ${index + 2}"
                )
            }
        }

        return InMemoryAwardEvidenceRepository.canonicalSnapshot(
            AwardEvidenceSnapshot(
                targets = targets,
                confirmations = confirmations,
                sponsorStandings = standings,
            )
        )
    }

    private fun encoded(value: String): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decoded(value: String): String =
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)

    private fun nullableEncoded(value: String?): String =
        value?.let(::encoded) ?: "-"

    private fun nullableDecoded(value: String): String? =
        if (value == "-") null else decoded(value)

    private fun nullableLong(value: String): Long? =
        if (value == "-") null else value.toLong()
}

data class AwardAdifImportSource(
    val sourceId: String,
    val sourceVersion: String,
    val sourceUrl: String? = null,
    val retrievedAtUtcMillis: Long? = null,
    val recordReference: String? = null,
) {
    init {
        require(sourceId.isNotBlank()) { "ADIF award import source id must not be blank" }
        require(sourceVersion.isNotBlank()) { "ADIF award import source version must not be blank" }
        require(sourceUrl == null || sourceUrl.startsWith("https://")) {
            "ADIF award import source URL must use HTTPS"
        }
        require(recordReference == null || recordReference.isNotBlank()) {
            "ADIF award import record reference must not be blank"
        }
        retrievedAtUtcMillis?.let {
            require(it >= 0) { "ADIF award import retrieval UTC must be non-negative" }
        }
    }
}

class AwardAdifEnrichmentAdapter {
    fun fromRecord(
        qsoId: Long,
        record: Map<String, String>,
        source: AwardAdifImportSource,
    ): AwardEvidenceBatch {
        require(qsoId > 0) { "ADIF award enrichment requires a positive immutable QSO id" }

        val normalized = record.entries.associate { (key, value) ->
            key.trim().uppercase() to value.trim()
        }

        val targets = mutableListOf<AwardTargetEvidence>()
        fun addTarget(kind: OfficialAwardTargetKind, rawValue: String) {
            if (rawValue.isBlank()) return
            targets += AwardTargetEvidence(
                qsoId = qsoId,
                kind = kind,
                value = rawValue,
                provenance = AwardTargetProvenance(
                    sourceId = source.sourceId,
                    sourceVersion = source.sourceVersion,
                    sourceUrl = source.sourceUrl,
                    reference = source.recordReference ?: "qso:$qsoId",
                    retrievedAtUtcMillis = source.retrievedAtUtcMillis,
                ),
            )
        }

        normalized["DXCC"]?.let { addTarget(OfficialAwardTargetKind.DXCC_ENTITY, it) }
        normalized["STATE"]?.let { addTarget(OfficialAwardTargetKind.US_STATE, it) }
        normalized["CONT"]?.let { addTarget(OfficialAwardTargetKind.CONTINENT, it) }
        normalized["IOTA"]?.let { addTarget(OfficialAwardTargetKind.IOTA_GROUP, it) }

        explicitPotaReferences(normalized).forEach {
            addTarget(OfficialAwardTargetKind.POTA_REFERENCE, it)
        }

        val confirmations = mutableListOf<AwardConfirmationEvidence>()
        if (normalized["LOTW_QSL_RCVD"]?.uppercase() == "Y") {
            confirmations += AwardConfirmationEvidence(
                qsoId = qsoId,
                source = "LOTW",
                reference = confirmationReference(
                    "LOTW_QSL_RCVD=Y",
                    normalized["LOTW_QSLRDATE"],
                    source.recordReference,
                ),
            )
        }
        if (normalized["QSL_RCVD"]?.uppercase() == "Y") {
            confirmations += AwardConfirmationEvidence(
                qsoId = qsoId,
                source = "ADIF_QSL_RCVD",
                reference = confirmationReference(
                    "QSL_RCVD=Y",
                    normalized["QSLRDATE"],
                    source.recordReference,
                ),
            )
        }

        return AwardEvidenceBatch(
            targets = targets.distinct(),
            confirmations = confirmations.distinct(),
        )
    }

    fun importRecord(
        repository: AwardEvidenceRepository,
        qsoId: Long,
        record: Map<String, String>,
        source: AwardAdifImportSource,
    ): AwardEvidenceApplyResult =
        repository.apply(fromRecord(qsoId, record, source))

    private fun explicitPotaReferences(record: Map<String, String>): Set<String> {
        val values = linkedSetOf<String>()

        record["POTA_REF"]?.let { values += splitReferences(it) }
        record["APP_POTA_REF"]?.let { values += splitReferences(it) }

        if (record["SIG"]?.trim()?.uppercase() == "POTA") {
            record["SIG_INFO"]?.let { values += splitReferences(it) }
        }

        return values
    }

    private fun splitReferences(value: String): Set<String> =
        value.split(',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toCollection(linkedSetOf())

    private fun confirmationReference(
        flag: String,
        date: String?,
        sourceReference: String?,
    ): String = listOfNotNull(
        flag,
        date?.takeIf { it.isNotBlank() }?.let { "date=$it" },
        sourceReference?.let { "record=$it" },
    ).joinToString(";")
}
