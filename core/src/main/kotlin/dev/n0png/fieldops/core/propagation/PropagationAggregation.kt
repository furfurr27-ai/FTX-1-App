package dev.n0png.fieldops.core.propagation

import java.security.MessageDigest

data class PropagationAggregationInput(
    val solarGeomagnetic: List<SolarGeomagneticObservation> = emptyList(),
    val ionosphericProducts: List<IonosphericMapProduct> = emptyList(),
    val heardPaths: List<HeardPathObservation> = emptyList(),
    val modeledPaths: List<ModeledPathEstimate> = emptyList(),
) {
    fun allEvidence(): List<PropagationEvidence> =
        solarGeomagnetic + ionosphericProducts + heardPaths + modeledPaths
}

object PropagationSourceFreshnessDefaults {
    val PSK_REPORTER = PropagationFreshnessPolicy(
        freshForMillis = 15L * 60L * 1000L,
        usableForMillis = 60L * 60L * 1000L,
    )

    val GLOTEC = PropagationFreshnessPolicy(
        freshForMillis = 20L * 60L * 1000L,
        usableForMillis = 60L * 60L * 1000L,
    )

    val NOAA_KP = PropagationFreshnessPolicy(
        freshForMillis = 3L * 60L * 60L * 1000L,
        usableForMillis = 6L * 60L * 60L * 1000L,
    )

    val NOAA_F107 = PropagationFreshnessPolicy(
        freshForMillis = 36L * 60L * 60L * 1000L,
        usableForMillis = 72L * 60L * 60L * 1000L,
    )

    fun policyFor(evidence: PropagationEvidence): PropagationFreshnessPolicy =
        policyForSourceId(evidence.source.sourceId)

    fun policyForSourceId(sourceId: String): PropagationFreshnessPolicy {
        require(sourceId.isNotBlank()) { "Propagation source id must not be blank" }
        return when {
            sourceId == "PSK_REPORTER_PUBLIC_QUERY" -> PSK_REPORTER
            sourceId == "NOAA_SWPC_GLOTEC_VTEC" -> GLOTEC
            sourceId == "NOAA_SWPC_PLANETARY_KP" -> NOAA_KP
            sourceId.startsWith("NOAA_SWPC_KP_FORECAST_") -> NOAA_KP
            sourceId == "NOAA_SWPC_F107_SUMMARY" -> NOAA_F107
            else -> PropagationFreshnessPolicy.OPERATIONAL
        }
    }

    fun classify(
        evidence: PropagationEvidence,
        nowUtcMillis: Long,
    ): PropagationFreshness =
        PropagationFreshnessClassifier.classify(
            evidenceUtcMillis = evidence.observedAtUtcMillis,
            nowUtcMillis = nowUtcMillis,
            policy = policyFor(evidence),
        )
}

class PropagationSnapshotAggregator {
    fun aggregate(
        capturedAtUtcMillis: Long,
        inputs: List<PropagationAggregationInput>,
        snapshotId: String? = null,
    ): PropagationSnapshot {
        require(capturedAtUtcMillis >= 0) {
            "Propagation snapshot capture UTC must be non-negative"
        }
        require(inputs.isNotEmpty()) {
            "Propagation aggregation requires at least one input batch"
        }

        val solar = mergeSimple(
            inputs.flatMap { it.solarGeomagnetic },
            "solar/geomagnetic",
        ) { a, b ->
            a.copy(source = normalizedSource(a.source)) ==
                b.copy(source = normalizedSource(b.source))
        }.sortedBy { it.evidenceId }

        val ionospheric = mergeSimple(
            inputs.flatMap { it.ionosphericProducts },
            "ionospheric",
        ) { a, b ->
            a.copy(source = normalizedSource(a.source)) ==
                b.copy(source = normalizedSource(b.source))
        }.sortedBy { it.evidenceId }

        val heard = mergeHeard(inputs.flatMap { it.heardPaths })
            .sortedBy { it.evidenceId }

        val modeled = mergeSimple(
            inputs.flatMap { it.modeledPaths },
            "modeled-path",
        ) { a, b ->
            a.copy(source = normalizedSource(a.source)) ==
                b.copy(source = normalizedSource(b.source))
        }.sortedBy { it.evidenceId }

        val all = solar + ionospheric + heard + modeled
        require(all.isNotEmpty()) {
            "Propagation aggregation requires at least one evidence item"
        }

        val idsByType = all.groupBy { it.evidenceId }
        val duplicateAcrossTypes = idsByType.entries.firstOrNull { (_, items) ->
            items.map { it::class.java.name }.distinct().size > 1
        }
        require(duplicateAcrossTypes == null) {
            "Propagation evidence id ${duplicateAcrossTypes?.key} collides across evidence categories"
        }

        val latestRetrieval = all.maxOf { it.source.retrievedAtUtcMillis }
        require(latestRetrieval <= capturedAtUtcMillis) {
            "Propagation snapshot capture UTC cannot precede latest source retrieval UTC"
        }

        val resolvedId = snapshotId?.also {
            require(it.isNotBlank()) { "Propagation snapshot id must not be blank" }
        } ?: deterministicSnapshotId(
            capturedAtUtcMillis = capturedAtUtcMillis,
            solarGeomagnetic = solar,
            ionosphericProducts = ionospheric,
            heardPaths = heard,
            modeledPaths = modeled,
        )

        return PropagationSnapshot(
            snapshotId = resolvedId,
            capturedAtUtcMillis = capturedAtUtcMillis,
            solarGeomagnetic = solar,
            ionosphericProducts = ionospheric,
            heardPaths = heard,
            modeledPaths = modeled,
        )
    }

    private fun <T : PropagationEvidence> mergeSimple(
        items: List<T>,
        category: String,
        semanticallySame: (T, T) -> Boolean,
    ): List<T> {
        val merged = linkedMapOf<String, T>()
        for (item in items) {
            val previous = merged[item.evidenceId]
            if (previous == null) {
                merged[item.evidenceId] = item
                continue
            }

            require(semanticallySame(previous, item)) {
                "Conflicting $category evidence for id ${item.evidenceId}"
            }
            if (item.source.retrievedAtUtcMillis > previous.source.retrievedAtUtcMillis) {
                merged[item.evidenceId] = item
            }
        }
        return merged.values.toList()
    }

    private fun mergeHeard(items: List<HeardPathObservation>): List<HeardPathObservation> {
        val merged = linkedMapOf<String, HeardPathObservation>()
        for (item in items) {
            val previous = merged[item.evidenceId]
            if (previous == null) {
                merged[item.evidenceId] = item
                continue
            }

            val previousNormalized = previous.copy(
                source = normalizedSource(previous.source),
                reportCount = 1,
            )
            val itemNormalized = item.copy(
                source = normalizedSource(item.source),
                reportCount = 1,
            )
            require(previousNormalized == itemNormalized) {
                "Conflicting heard-path evidence for id ${item.evidenceId}"
            }

            val latest =
                if (item.source.retrievedAtUtcMillis > previous.source.retrievedAtUtcMillis) {
                    item
                } else {
                    previous
                }
            merged[item.evidenceId] = latest.copy(
                reportCount = maxOf(previous.reportCount, item.reportCount),
            )
        }
        return merged.values.toList()
    }

    private fun normalizedSource(source: PropagationSourceRef): PropagationSourceRef =
        source.copy(retrievedAtUtcMillis = 0L)

    private fun deterministicSnapshotId(
        capturedAtUtcMillis: Long,
        solarGeomagnetic: List<SolarGeomagneticObservation>,
        ionosphericProducts: List<IonosphericMapProduct>,
        heardPaths: List<HeardPathObservation>,
        modeledPaths: List<ModeledPathEstimate>,
    ): String {
        val canonical = buildString {
            append("FIELDOPS_PROPAGATION_SNAPSHOT_V1\n")
            append("captured=").append(capturedAtUtcMillis).append('\n')
            solarGeomagnetic.forEach {
                append("SOLAR|")
                append(it.evidenceId).append('|')
                append(it.source.retrievedAtUtcMillis).append('\n')
            }
            ionosphericProducts.forEach {
                append("IONO|")
                append(it.evidenceId).append('|')
                append(it.source.retrievedAtUtcMillis).append('\n')
            }
            heardPaths.forEach {
                append("HEARD|")
                append(it.evidenceId).append('|')
                append(it.source.retrievedAtUtcMillis).append('|')
                append(it.reportCount).append('\n')
            }
            modeledPaths.forEach {
                append("MODEL|")
                append(it.evidenceId).append('|')
                append(it.source.retrievedAtUtcMillis).append('\n')
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "propagation-$capturedAtUtcMillis-${digest.take(24)}"
    }
}
