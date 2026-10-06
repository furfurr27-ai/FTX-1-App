package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.awards.AwardEvidenceSnapshot
import dev.n0png.fieldops.core.awards.OfficialAwardCatalog
import dev.n0png.fieldops.core.awards.OfficialAwardTargetKind
import dev.n0png.fieldops.core.map.*
import java.nio.file.Files
import java.nio.file.Path

object ProductionUsStateGeometryPackTests {
    private var assertions = 0

    private val packText: String by lazy {
        Files.readString(
            Path.of(
                "core/src/main/resources/dev/n0png/fieldops/maps/" +
                    "us_states_2025_20m.pack"
            )
        )
    }

    private val records: List<OfflineGeometryPackRecord> by lazy {
        Census2025UsState20mGeometryPack.decode(packText)
    }

    private val provider: UsStateGeometryPackProvider by lazy {
        Census2025UsState20mGeometryPack.provider(packText)
    }

    private fun checkThat(value: Boolean, message: String) {
        assertions++
        check(value) { message }
    }

    private fun <T> eq(expected: T, actual: T, message: String) =
        checkThat(expected == actual, "$message expected=$expected actual=$actual")

    @JvmStatic
    fun main(args: Array<String>) {
        upstreamArtifactIsPinned()
        exactWasUniverseIsPresent()
        integrityHashesRecompute()
        multipartGeometryIsPreserved()
        alaskaDatelineHandlingIsExplicitAndSafe()
        productionProviderResolvesAllFifty()
        awardsMapBindsProductionStateGeometry()
        censusProvenanceSurvivesBinding()
        packIsDeterministicAndOffline()
        corruptionFailsClosed()
        println("CP-0007C production U.S. state geometry pack tests: PASS assertions=$assertions")
    }

    private fun upstreamArtifactIsPinned() {
        val m = Census2025UsState20mPackMetadata
        eq("cb_2025_us_state_20m.zip", m.UPSTREAM_FILENAME, "exact Census filename")
        eq(
            "https://www2.census.gov/geo/tiger/GENZ2025/kml/cb_2025_us_state_20m.zip",
            m.UPSTREAM_URL,
            "exact Census KML artifact URL",
        )
        eq(
            "efddd884f1442ef233b1ba9c12dddbd66b6fdf94da6a373e1556aefe3dbc5751",
            m.UPSTREAM_SHA256,
            "pinned upstream SHA-256",
        )
        eq(158017L, m.UPSTREAM_SIZE_BYTES, "pinned upstream size")
        eq("2025", m.SOURCE_VINTAGE, "source vintage")
        eq("1:20,000,000", m.SOURCE_SCALE, "source scale")
        eq("2026-10-06", m.RETRIEVED_ON, "source retrieval date")
        eq(52, m.UPSTREAM_PLACEMARK_COUNT, "upstream placemark count")
        eq(2, m.IGNORED_NON_WAS_PLACEMARK_COUNT, "non-WAS source features excluded")

        val manifest = Census2025UsState20mGeometryPack.manifest
        eq(50, manifest.declaredFeatureCount, "production manifest feature count")
        eq(false, manifest.fixtureOnly, "production pack not fixture")
        eq(OfficialAwardTargetKind.US_STATE, manifest.targetKind, "production pack target kind")
        eq(m.UPSTREAM_URL, manifest.source.sourceUrl, "manifest points to exact source artifact")
        checkThat(
            manifest.source.sourceVersion.contains("2025") &&
                manifest.source.sourceVersion.contains("1:20,000,000") &&
                manifest.source.sourceVersion.contains("KML"),
            "manifest retains source vintage/scale/format",
        )
        checkThat(
            manifest.source.licenseLabel!!.contains("17 U.S.C. §105"),
            "manifest retains government-work rights basis",
        )
        checkThat(
            m.STATISTICAL_BOUNDARY_DISCLAIMER.contains("not legal land descriptions"),
            "Census statistical-boundary disclaimer retained",
        )
    }

    private fun exactWasUniverseIsPresent() {
        val expected = OfficialAwardCatalog.require("ARRL_WAS_BASIC")
            .requirement.targetUniverse!!.toSortedSet()
        val actual = records.map { it.targetValue }.toSortedSet()

        eq(50, records.size, "production state record count")
        eq(expected, actual, "production geometry identities exactly equal WAS universe")
        listOf("DC", "PR", "AS", "GU", "MP", "VI").forEach { excluded ->
            checkThat(excluded !in actual, "$excluded excluded from 50-state WAS geometry pack")
        }
        eq(
            actual,
            Census2025UsState20mPackMetadata.featureSha256.keys.toSortedSet(),
            "feature hash table exactly matches production state identities",
        )
        checkThat(records.map { it.assetId }.distinct().size == 50, "all production asset ids unique")
    }

    private fun integrityHashesRecompute() {
        eq(
            Census2025UsState20mPackMetadata.PACK_SHA256,
            GeometryPackIntegrity.textSha256(packText),
            "raw offline asset SHA-256 matches generated metadata",
        )
        eq(
            Census2025UsState20mPackMetadata.PACK_SHA256,
            GeometryPackIntegrity.packSha256(records),
            "decoded canonical pack SHA-256 recomputes",
        )
        eq(
            "5feb8c18688936a526523cb536766130be06b14ebfa918b3d99e39bfbcb0a130",
            Census2025UsState20mPackMetadata.PACK_SHA256,
            "pinned canonical pack SHA-256",
        )
        records.forEach { record ->
            eq(
                Census2025UsState20mPackMetadata.featureSha256.getValue(record.targetValue),
                GeometryPackIntegrity.featureSha256(record),
                "canonical feature hash ${record.targetValue}",
            )
        }
    }

    private fun multipartGeometryIsPreserved() {
        val byState = records.associateBy { it.targetValue }

        eq(47, polygonCount(byState.getValue("AK")), "Alaska multipart polygon count")
        eq(8, polygonCount(byState.getValue("HI")), "Hawaii multipart polygon count")
        eq(6, polygonCount(byState.getValue("CA")), "California islands preserved")
        eq(6, polygonCount(byState.getValue("MI")), "Michigan multipart geometry preserved")
        eq(4, polygonCount(byState.getValue("FL")), "Florida multipart geometry preserved")
        eq(3, polygonCount(byState.getValue("MA")), "Massachusetts multipart geometry preserved")

        byState.forEach { (state, record) ->
            eq(
                Census2025UsState20mPackMetadata.polygonCounts.getValue(state),
                polygonCount(record),
                "$state generated polygon count",
            )
        }
        checkThat(
            byState.values
                .flatMap { (it.geometry as MultiPolygonGeometry).polygons }
                .all { it.outer.points.first() == it.outer.points.last() },
            "all production outer rings are closed",
        )
    }

    private fun alaskaDatelineHandlingIsExplicitAndSafe() {
        val m = Census2025UsState20mPackMetadata
        val alaska = records.single { it.targetValue == "AK" }
        eq(true, m.ALASKA_HAS_POSITIVE_LONGITUDES, "metadata records east-of-dateline Alaska parts")
        eq(true, m.ALASKA_HAS_NEGATIVE_LONGITUDES, "metadata records west-of-dateline Alaska parts")
        checkThat(GeometryPackIntegrity.hasPositiveLongitude(alaska), "Alaska retains positive longitudes")
        checkThat(GeometryPackIntegrity.hasNegativeLongitude(alaska), "Alaska retains negative longitudes")

        val maxJump = GeometryPackIntegrity.maxLongitudeJump(alaska)
        eq(m.ALASKA_MAX_LONGITUDE_JUMP, maxJump, "Alaska max longitude jump recomputes")
        checkThat(maxJump < 1.0, "Census Alaska rings are already dateline-safe")
        records.forEach { record ->
            checkThat(
                GeometryPackIntegrity.maxLongitudeJump(record) <= 180.0,
                "${record.targetValue} contains no unsplit antimeridian jump",
            )
        }
    }

    private fun productionProviderResolvesAllFifty() {
        val expected = OfficialAwardCatalog.require("ARRL_WAS_BASIC").requirement.targetUniverse!!
        expected.forEach { state ->
            val feature = provider.feature(
                AwardAreaGeometryIdentity(OfficialAwardTargetKind.US_STATE, state)
            )
            checkThat(feature != null, "$state resolves in production provider")
            eq("census/2025/state/20m/$state", feature!!.assetId, "$state stable asset id")
            eq(
                Census2025UsState20mPackMetadata.UPSTREAM_URL,
                feature.source.sourceUrl,
                "$state exact Census artifact provenance",
            )
        }
        eq(
            null,
            provider.feature(AwardAreaGeometryIdentity(OfficialAwardTargetKind.US_STATE, "DC")),
            "District of Columbia excluded",
        )
        eq(
            null,
            provider.feature(AwardAreaGeometryIdentity(OfficialAwardTargetKind.US_STATE, "PR")),
            "Puerto Rico excluded",
        )
        eq(
            null,
            provider.feature(AwardAreaGeometryIdentity(OfficialAwardTargetKind.MAIDENHEAD_GRID4, "JO40")),
            "state provider ignores grid identity",
        )
    }

    private fun awardsMapBindsProductionStateGeometry() {
        val registry = Census2025UsState20mGeometryPack.productionRegistry(packText)
        val layers = AwardAreaMapProjectionService().project(
            qsos = emptyList(),
            evidence = AwardEvidenceSnapshot(),
            geometryCatalog = registry,
        )

        val was = layers.single { it.awardId == "ARRL_WAS_BASIC" }
        eq(50, was.geometryBoundCount, "all WAS states bind production geometry")
        eq(0, was.geometryUnboundCount, "no WAS state geometry missing")

        val ffma = layers.single { it.awardId == "ARRL_FFMA" }
        eq(488, ffma.geometryBoundCount, "all FFMA grids still resolve")
        eq(0, ffma.geometryUnboundCount, "FFMA remains fully geometry-resolved")
        checkThat(
            was.targets.all {
                it.geometry!!.source.sourceId == "US_CENSUS_CARTOGRAPHIC_BOUNDARY_FILES"
            },
            "WAS bindings retain Census source",
        )
    }

    private fun censusProvenanceSurvivesBinding() {
        val feature = provider.feature(
            AwardAreaGeometryIdentity(OfficialAwardTargetKind.US_STATE, "OR")
        )!!
        eq(AwardGeometrySourceKind.EXTERNAL_DATASET, feature.source.kind, "external dataset source")
        eq("US_CENSUS_CARTOGRAPHIC_BOUNDARY_FILES", feature.source.sourceId, "Census source id")
        eq("fieldops-cp0007c", feature.source.buildVersion, "pack build version")
        checkThat(
            feature.source.licenseLabel!!.contains("Census Bureau source attribution requested"),
            "Census attribution metadata retained",
        )
    }

    private fun packIsDeterministicAndOffline() {
        val reversed = UsStateGeometryPackProvider(
            Census2025UsState20mGeometryPack.manifest,
            records.reversed(),
        )
        listOf("AK", "CA", "OR", "WA", "ME").forEach { state ->
            val identity = AwardAreaGeometryIdentity(OfficialAwardTargetKind.US_STATE, state)
            eq(provider.feature(identity), reversed.feature(identity), "$state lookup independent of input order")
        }
        checkThat(records.all { it.geometry is MultiPolygonGeometry }, "pack uses platform-independent multipolygons")
        checkThat(
            Census2025UsState20mGeometryPack.RESOURCE_PATH.endsWith(".pack"),
            "production coordinates are packaged as offline data asset, not Kotlin literals",
        )
    }

    private fun corruptionFailsClosed() {
        val corrupted = packText.replaceFirst("STATE\tAK", "STATE\tAZ")
        checkThat(
            runCatching { Census2025UsState20mGeometryPack.decode(corrupted) }.isFailure,
            "modified pack bytes fail SHA verification",
        )
        checkThat(
            runCatching { Census2025UsState20mGeometryPack.decode(packText.replace("\n", "\r\n")) }.isFailure,
            "non-canonical line endings fail closed",
        )
    }

    private fun polygonCount(record: OfflineGeometryPackRecord): Int =
        (record.geometry as MultiPolygonGeometry).polygons.size
}
