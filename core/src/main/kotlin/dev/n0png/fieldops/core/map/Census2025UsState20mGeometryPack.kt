package dev.n0png.fieldops.core.map

import dev.n0png.fieldops.core.awards.OfficialAwardTargetKind

/**
 * Loader for the generated CP-0007C Census 2025 1:20,000,000 state pack.
 *
 * Geometry coordinates live in a compact offline resource rather than Kotlin
 * source so the compiler never has to type-check thousands of generated
 * coordinate constructor calls.
 */
object Census2025UsState20mGeometryPack {
    const val RESOURCE_PATH =
        "dev/n0png/fieldops/maps/us_states_2025_20m.pack"

    val manifest: OfflineGeometryPackManifest =
        CensusStateGeometryPackContract.productionManifest(
            packVersion = Census2025UsState20mPackMetadata.PACK_VERSION,
            buildVersion = Census2025UsState20mPackMetadata.BUILD_VERSION,
            declaredFeatureCount = Census2025UsState20mPackMetadata.FEATURE_COUNT,
            scaleLabel = "1:20,000,000 national States (KML)",
            sourceUrl = Census2025UsState20mPackMetadata.UPSTREAM_URL,
        )

    fun decode(packText: String): List<OfflineGeometryPackRecord> {
        require(!packText.contains('\r')) {
            "Production geometry pack must use canonical LF line endings"
        }
        require(
            GeometryPackIntegrity.textSha256(packText) ==
                Census2025UsState20mPackMetadata.PACK_SHA256
        ) {
            "Production geometry pack SHA-256 mismatch"
        }

        val records = mutableListOf<OfflineGeometryPackRecord>()
        var currentState: String? = null
        var polygons = mutableListOf<GeoPolygon>()

        fun flushCurrent() {
            val state = currentState ?: return
            require(polygons.isNotEmpty()) {
                "Production geometry state $state has no polygons"
            }
            records += OfflineGeometryPackRecord(
                targetValue = state,
                assetId = "census/2025/state/20m/$state",
                geometry = MultiPolygonGeometry(polygons.toList()),
            )
            currentState = null
            polygons = mutableListOf()
        }

        packText.split('\n').forEach { rawLine ->
            if (rawLine.isEmpty()) return@forEach
            when {
                rawLine.startsWith("STATE\t") -> {
                    flushCurrent()
                    val state = rawLine.removePrefix("STATE\t")
                    require(state.matches(Regex("""[A-Z]{2}"""))) {
                        "Malformed production state identity: $state"
                    }
                    currentState = state
                }

                rawLine.startsWith("POLYGON\t") -> {
                    val state = currentState
                        ?: error("Polygon encountered before state header")
                    val fields = rawLine.split('\t', limit = 3)
                    require(fields.size == 3) {
                        "Malformed polygon record for $state"
                    }
                    val index = fields[1].toIntOrNull()
                        ?: error("Malformed polygon index for $state")
                    require(index == polygons.size) {
                        "Polygon index out of sequence for $state"
                    }
                    polygons += parsePolygon(fields[2], state, index)
                }

                else -> error("Unsupported production geometry record: $rawLine")
            }
        }
        flushCurrent()

        val expectedStates = Census2025UsState20mPackMetadata.featureSha256.keys
        val actualStates = records.map { it.targetValue }
        require(actualStates.size == Census2025UsState20mPackMetadata.FEATURE_COUNT) {
            "Production geometry feature count mismatch"
        }
        require(actualStates.toSet() == expectedStates) {
            "Production geometry state identity set mismatch"
        }
        require(actualStates == actualStates.sorted()) {
            "Production geometry states must be canonical sorted order"
        }

        records.forEach { record ->
            val expected = Census2025UsState20mPackMetadata.featureSha256
                .getValue(record.targetValue)
            require(GeometryPackIntegrity.featureSha256(record) == expected) {
                "Production geometry feature hash mismatch for ${record.targetValue}"
            }
            val expectedPolygons = Census2025UsState20mPackMetadata.polygonCounts
                .getValue(record.targetValue)
            val actualPolygons = (record.geometry as MultiPolygonGeometry).polygons.size
            require(actualPolygons == expectedPolygons) {
                "Production geometry polygon count mismatch for ${record.targetValue}"
            }
        }

        require(
            GeometryPackIntegrity.packSha256(records) ==
                Census2025UsState20mPackMetadata.PACK_SHA256
        ) {
            "Decoded production geometry canonical pack hash mismatch"
        }

        return records
    }

    fun provider(packText: String): UsStateGeometryPackProvider =
        UsStateGeometryPackProvider(manifest, decode(packText))

    private fun parsePolygon(
        canonical: String,
        state: String,
        index: Int,
    ): GeoPolygon {
        val parts = canonical.split('|')
        require(parts.isNotEmpty() && parts.first().startsWith("O:")) {
            "Polygon $index for $state lacks canonical outer ring"
        }
        val outer = parseRing(parts.first().removePrefix("O:"), state, index)
        val holes = parts.drop(1).map { part ->
            require(part.startsWith("H:")) {
                "Polygon $index for $state has malformed hole record"
            }
            parseRing(part.removePrefix("H:"), state, index)
        }
        return GeoPolygon(outer = outer, holes = holes)
    }

    private fun parseRing(
        canonical: String,
        state: String,
        polygonIndex: Int,
    ): GeoLinearRing {
        val points = canonical.split(';').map { token ->
            val pieces = token.split(',', limit = 2)
            require(pieces.size == 2) {
                "Malformed coordinate in $state polygon $polygonIndex"
            }
            val longitude = pieces[0].toDoubleOrNull()
                ?: error("Malformed longitude in $state polygon $polygonIndex")
            val latitude = pieces[1].toDoubleOrNull()
                ?: error("Malformed latitude in $state polygon $polygonIndex")
            GeoCoordinate(longitude = longitude, latitude = latitude)
        }
        return GeoLinearRing(points)
    }

    fun productionRegistry(packText: String): AwardGeometryRegistry =
        AwardGeometryRegistry(
            listOf(
                provider(packText),
                MaidenheadGrid4GeometryProvider(
                    buildVersion = Census2025UsState20mPackMetadata.BUILD_VERSION,
                ),
            )
        )

    fun isWasState(identity: AwardAreaGeometryIdentity): Boolean =
        identity.targetKind == OfficialAwardTargetKind.US_STATE &&
            identity.targetValue.trim().uppercase() in
            Census2025UsState20mPackMetadata.featureSha256.keys
}
