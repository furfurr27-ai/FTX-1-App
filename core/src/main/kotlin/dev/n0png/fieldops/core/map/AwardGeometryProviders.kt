package dev.n0png.fieldops.core.map

import dev.n0png.fieldops.core.awards.OfficialAwardTargetKind
import kotlin.math.abs

enum class AwardGeometrySourceKind {
    DERIVED_STANDARD,
    EXTERNAL_DATASET,
    SYNTHETIC_FIXTURE,
}

data class AwardGeometrySourceMetadata(
    val kind: AwardGeometrySourceKind,
    val sourceId: String,
    val sourceVersion: String,
    val sourceUrl: String? = null,
    val licenseLabel: String? = null,
    val retrievedOn: String,
    val buildVersion: String,
) {
    init {
        require(sourceId.isNotBlank()) { "Geometry source id must not be blank" }
        require(sourceVersion.isNotBlank()) { "Geometry source version must not be blank" }
        require(buildVersion.isNotBlank()) { "Geometry build version must not be blank" }
        require(retrievedOn.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
            "Geometry source retrieval date must be YYYY-MM-DD"
        }
        require(sourceUrl == null || sourceUrl.startsWith("https://")) {
            "Geometry source URL must use HTTPS"
        }
        if (kind != AwardGeometrySourceKind.DERIVED_STANDARD) {
            require(!licenseLabel.isNullOrBlank()) {
                "Non-derived geometry requires explicit license/public-domain metadata"
            }
        }
        if (kind == AwardGeometrySourceKind.EXTERNAL_DATASET) {
            require(!sourceUrl.isNullOrBlank()) {
                "External geometry datasets require an authoritative HTTPS source URL"
            }
        }
    }

    fun projectionSource(): AwardAreaGeometrySource = AwardAreaGeometrySource(
        sourceId = sourceId,
        sourceVersion = sourceVersion,
        sourceUrl = sourceUrl,
        licenseLabel = licenseLabel,
        retrievedOn = retrievedOn,
    )
}

data class GeoCoordinate(
    val longitude: Double,
    val latitude: Double,
) {
    init {
        require(longitude in -180.0..180.0) {
            "Geometry longitude must be between -180 and 180 degrees"
        }
        require(latitude in -90.0..90.0) {
            "Geometry latitude must be between -90 and 90 degrees"
        }
    }
}

data class GeoBounds(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
) {
    init {
        require(west in -180.0..180.0 && east in -180.0..180.0) {
            "Geometry bounds longitude must be between -180 and 180 degrees"
        }
        require(south in -90.0..90.0 && north in -90.0..90.0) {
            "Geometry bounds latitude must be between -90 and 90 degrees"
        }
        require(west < east) {
            "Geometry bounds must not cross the antimeridian in this core payload"
        }
        require(south < north) {
            "Geometry bounds south must be below north"
        }
    }

    val widthDegrees: Double
        get() = east - west

    val heightDegrees: Double
        get() = north - south

    val center: GeoCoordinate
        get() = GeoCoordinate(
            longitude = (west + east) / 2.0,
            latitude = (south + north) / 2.0,
        )
}

data class GeoLinearRing(
    val points: List<GeoCoordinate>,
) {
    init {
        require(points.size >= 4) { "Geometry ring requires at least four points" }
        require(points.first() == points.last()) { "Geometry ring must be closed" }
        require(points.dropLast(1).distinct().size >= 3) {
            "Geometry ring requires at least three distinct vertices"
        }
        require(abs(signedArea(points)) > 1e-12) {
            "Geometry ring must enclose non-zero area"
        }
    }

    private companion object {
        fun signedArea(points: List<GeoCoordinate>): Double {
            var sum = 0.0
            for (index in 0 until points.lastIndex) {
                val a = points[index]
                val b = points[index + 1]
                sum += a.longitude * b.latitude - b.longitude * a.latitude
            }
            return sum / 2.0
        }
    }
}

data class GeoPolygon(
    val outer: GeoLinearRing,
    val holes: List<GeoLinearRing> = emptyList(),
)

sealed interface AwardGeometryPayload

data class BoundsGeometry(
    val bounds: GeoBounds,
) : AwardGeometryPayload

data class MultiPolygonGeometry(
    val polygons: List<GeoPolygon>,
) : AwardGeometryPayload {
    init {
        require(polygons.isNotEmpty()) { "Multi-polygon geometry must contain at least one polygon" }
    }
}

data class AwardGeometryFeature(
    val identity: AwardAreaGeometryIdentity,
    val assetId: String,
    val payload: AwardGeometryPayload,
    val source: AwardGeometrySourceMetadata,
) {
    init {
        require(assetId.isNotBlank()) { "Geometry feature asset id must not be blank" }
        require(assetId.matches(Regex("""[A-Za-z0-9._:/-]+"""))) {
            "Geometry feature asset id contains unsupported characters"
        }
    }
}

interface AwardGeometryProvider {
    val providerId: String

    fun feature(identity: AwardAreaGeometryIdentity): AwardGeometryFeature?
}

class AwardGeometryRegistry(
    providers: Iterable<AwardGeometryProvider>,
) : AwardAreaGeometryCatalog {
    private val orderedProviders = providers.toList().also { list ->
        require(list.all { it.providerId.isNotBlank() }) {
            "Geometry provider ids must not be blank"
        }
        require(list.map { it.providerId }.distinct().size == list.size) {
            "Geometry provider ids must be unique"
        }
    }

    fun feature(identity: AwardAreaGeometryIdentity): AwardGeometryFeature? {
        val matches = orderedProviders.mapNotNull { provider ->
            provider.feature(identity)?.let { provider to it }
        }
        require(matches.size <= 1) {
            "Multiple geometry providers resolved ${identity.stableKey}: " +
                matches.joinToString { it.first.providerId }
        }
        return matches.singleOrNull()?.second?.also { feature ->
            require(feature.identity.stableKey == identity.stableKey) {
                "Geometry provider returned mismatched identity for ${identity.stableKey}"
            }
        }
    }

    override fun binding(identity: AwardAreaGeometryIdentity): AwardAreaGeometryBinding? =
        feature(identity)?.let { feature ->
            AwardAreaGeometryBinding(
                identity = identity,
                source = feature.source.projectionSource(),
                geometryAssetId = feature.assetId,
            )
        }
}

/**
 * IARU Region 1 VHF Handbook 9.01 sections 6.2/6.3:
 * - fields are 20 degrees longitude by 10 degrees latitude
 * - squares are 2 degrees longitude by 1 degree latitude
 * - numbering proceeds west-to-east and south-to-north
 * - origin is 180 degrees west / 90 degrees south
 * - WGS-84 is the reference geodetic system
 */
class MaidenheadGrid4GeometryProvider(
    private val buildVersion: String = "fieldops-cp0007b",
) : AwardGeometryProvider {
    override val providerId: String = "maidenhead-grid4-derived"

    override fun feature(identity: AwardAreaGeometryIdentity): AwardGeometryFeature? {
        if (identity.targetKind != OfficialAwardTargetKind.MAIDENHEAD_GRID4) return null

        val grid = identity.targetValue.trim().uppercase()
        require(grid.matches(Regex("""[A-R]{2}[0-9]{2}"""))) {
            "Four-character Maidenhead geometry identity must match [A-R]{2}[0-9]{2}"
        }

        val fieldLon = grid[0] - 'A'
        val fieldLat = grid[1] - 'A'
        val squareLon = grid[2] - '0'
        val squareLat = grid[3] - '0'

        val west = -180.0 + fieldLon * 20.0 + squareLon * 2.0
        val south = -90.0 + fieldLat * 10.0 + squareLat * 1.0
        val canonicalIdentity = AwardAreaGeometryIdentity(
            targetKind = OfficialAwardTargetKind.MAIDENHEAD_GRID4,
            targetValue = grid,
        )

        return AwardGeometryFeature(
            identity = canonicalIdentity,
            assetId = "maidenhead/grid4/$grid",
            payload = BoundsGeometry(
                GeoBounds(
                    west = west,
                    south = south,
                    east = west + 2.0,
                    north = south + 1.0,
                )
            ),
            source = SOURCE.copy(buildVersion = buildVersion),
        )
    }

    companion object {
        val SOURCE = AwardGeometrySourceMetadata(
            kind = AwardGeometrySourceKind.DERIVED_STANDARD,
            sourceId = "IARU_R1_MAIDENHEAD_LOCATOR",
            sourceVersion = "VHF Handbook 9.01 (March 2021), sections 6.2-6.3",
            sourceUrl = "https://www.iaru-r1.org/wp-content/uploads/2021/03/VHF_Handbook_V9.01.pdf",
            licenseLabel = null,
            retrievedOn = "2026-10-06",
            buildVersion = "template",
        )
    }
}

data class OfflineGeometryPackManifest(
    val packId: String,
    val packVersion: String,
    val targetKind: OfficialAwardTargetKind,
    val source: AwardGeometrySourceMetadata,
    val declaredFeatureCount: Int,
    val fixtureOnly: Boolean = false,
) {
    init {
        require(packId.isNotBlank()) { "Geometry pack id must not be blank" }
        require(packVersion.isNotBlank()) { "Geometry pack version must not be blank" }
        require(declaredFeatureCount > 0) { "Geometry pack feature count must be positive" }
        if (fixtureOnly) {
            require(source.kind == AwardGeometrySourceKind.SYNTHETIC_FIXTURE) {
                "Fixture geometry packs must identify synthetic-fixture provenance"
            }
        } else {
            require(source.kind == AwardGeometrySourceKind.EXTERNAL_DATASET) {
                "Production geometry packs must identify external-dataset provenance"
            }
        }
    }
}

data class OfflineGeometryPackRecord(
    val targetValue: String,
    val assetId: String,
    val geometry: AwardGeometryPayload,
) {
    init {
        require(targetValue.isNotBlank()) { "Geometry pack target value must not be blank" }
        require(assetId.isNotBlank()) { "Geometry pack asset id must not be blank" }
    }
}

class UsStateGeometryPackProvider(
    val manifest: OfflineGeometryPackManifest,
    records: Iterable<OfflineGeometryPackRecord>,
) : AwardGeometryProvider {
    override val providerId: String = "us-state-pack:${manifest.packId}:${manifest.packVersion}"

    private val byStableKey: Map<String, AwardGeometryFeature>

    init {
        require(manifest.targetKind == OfficialAwardTargetKind.US_STATE) {
            "U.S. state geometry provider requires US_STATE target kind"
        }

        val built = records.map { record ->
            val state = record.targetValue.trim().uppercase()
            require(state.matches(Regex("""[A-Z]{2}"""))) {
                "U.S. state geometry target must be a two-letter code"
            }
            val identity = AwardAreaGeometryIdentity(OfficialAwardTargetKind.US_STATE, state)
            identity.stableKey to AwardGeometryFeature(
                identity = identity,
                assetId = record.assetId,
                payload = record.geometry,
                source = manifest.source,
            )
        }
        require(built.size == manifest.declaredFeatureCount) {
            "Geometry pack declared ${manifest.declaredFeatureCount} features but provided ${built.size}"
        }
        require(built.map { it.first }.distinct().size == built.size) {
            "Geometry pack target identities must be unique"
        }
        require(built.map { it.second.assetId }.distinct().size == built.size) {
            "Geometry pack asset ids must be unique"
        }
        byStableKey = built.toMap()
    }

    override fun feature(identity: AwardAreaGeometryIdentity): AwardGeometryFeature? {
        if (identity.targetKind != OfficialAwardTargetKind.US_STATE) return null
        return byStableKey[identity.stableKey]
    }
}

object CensusStateGeometryPackContract {
    const val SOURCE_URL =
        "https://www.census.gov/geographies/mapping-files/2025/geo/carto-boundary-file.html"

    const val LICENSE_LABEL =
        "U.S. Government work; U.S. copyright unavailable under 17 U.S.C. §105; Census Bureau source attribution requested"

    fun productionManifest(
        packVersion: String,
        buildVersion: String,
        declaredFeatureCount: Int,
        scaleLabel: String = "1:20,000,000 national States",
    ): OfflineGeometryPackManifest = OfflineGeometryPackManifest(
        packId = "us-census-cartographic-states",
        packVersion = packVersion,
        targetKind = OfficialAwardTargetKind.US_STATE,
        source = AwardGeometrySourceMetadata(
            kind = AwardGeometrySourceKind.EXTERNAL_DATASET,
            sourceId = "US_CENSUS_CARTOGRAPHIC_BOUNDARY_FILES",
            sourceVersion = "2025 Cartographic Boundary Files — $scaleLabel",
            sourceUrl = SOURCE_URL,
            licenseLabel = LICENSE_LABEL,
            retrievedOn = "2026-10-06",
            buildVersion = buildVersion,
        ),
        declaredFeatureCount = declaredFeatureCount,
        fixtureOnly = false,
    )

    fun syntheticFixtureManifest(
        packVersion: String,
        declaredFeatureCount: Int,
    ): OfflineGeometryPackManifest = OfflineGeometryPackManifest(
        packId = "cp0007b-us-state-ci-fixture",
        packVersion = packVersion,
        targetKind = OfficialAwardTargetKind.US_STATE,
        source = AwardGeometrySourceMetadata(
            kind = AwardGeometrySourceKind.SYNTHETIC_FIXTURE,
            sourceId = "CP0007B_SYNTHETIC_STATE_GEOMETRY",
            sourceVersion = "fixture-v1",
            sourceUrl = null,
            licenseLabel = "Synthetic CI fixture authored for FieldOps; contains no Census boundary geometry",
            retrievedOn = "2026-10-06",
            buildVersion = packVersion,
        ),
        declaredFeatureCount = declaredFeatureCount,
        fixtureOnly = true,
    )
}
