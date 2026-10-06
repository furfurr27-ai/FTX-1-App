package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.awards.AwardEvidenceSnapshot
import dev.n0png.fieldops.core.awards.OfficialAwardCatalog
import dev.n0png.fieldops.core.awards.OfficialAwardTargetKind
import dev.n0png.fieldops.core.map.*

object ProductionUsStateGeometryPackTests {
    private var assertions = 0

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
        println("CP-0007C production U.S. state geometry pack tests: PASS assertions=$assertions")
    }

    private fun upstreamArtifactIsPinned() {
        eq(
            "cb_2025_us_state_20m.zip",
            Census2025UsState20mGeometryPack.UPSTREAM_FILENAME,
            "exact Census filename",
        )
        eq(
            "https://www2.census.gov/geo/tiger/GENZ2025/kml/cb_2025_us_state_20m.zip",
            Census2025UsState20mGeometryPack.UPSTREAM_URL,
            "exact Census KML artifact URL",
        )
        eq(
            "efddd884f1442ef233b1ba9c12dddbd66b6fdf94da6a373e1556aefe3dbc5751",
            Census2025UsState20mGeometryPack.UPSTREAM_SHA256,
            "pinned upstream SHA-256",
        )
        eq(158017L, Census2025UsState20mGeometryPack.UPSTREAM_SIZE_BYTES, "pinned upstream size")
        eq("2025", Census2025UsState20mGeometryPack.SOURCE_VINTAGE, "source vintage")
        eq("1:20,000,000", Census2025UsState20mGeometryPack.SOURCE_SCALE, "source scale")
        eq("2026-10-06", Census2025UsState20mGeometryPack.RETRIEVED_ON, "source retrieval date")
        eq(52, Census2025UsState20mGeometryPack.UPSTREAM_PLACEMARK_COUNT, "upstream placemark count")
        eq(2, Census2025UsState20mGeometryPack.IGNORED_NON_WAS_PLACEMARK_COUNT, "non-WAS source features excluded")

        val manifest = Census2025UsState20mGeometryPack.manifest
        eq(50, manifest.declaredFeatureCount, "production manifest feature count")
        eq(false, manifest.fixtureOnly, "production pack not fixture")
        eq(OfficialAwardTargetKind.US_STATE, manifest.targetKind, "production pack target kind")
        eq(
            Census2025UsState20mGeometryPack.UPSTREAM_URL,
            manifest.source.sourceUrl,
            "manifest points to exact source artifact",
        )
        checkThat(
            manifest.source.sourceVersion.contains("2025") &&
                manifest.source.sourceVersion.contains("1:20,000,000") &&
                manifest.source.sourceVersion.contains("KML"),
            "manifest retains source vintage/scale/format",
        )
        checkThat(
            manifest.source.licenseLabel!!.contains("17 U.S.C. §105"),
            "manifest retains public-domain rights basis",
        )
        checkThat(
            Census2025UsState20mGeometryPack.STATISTICAL_BOUNDARY_DISCLAIMER
                .contains("not legal land descriptions"),
            "Census statistical-boundary disclaimer retained",
        )
    }

    private fun exactWasUniverseIsPresent() {
        val expected = OfficialAwardCatalog.require("ARRL_WAS_BASIC")
            .requirement
            .targetUniverse!!
            .toSortedSet()
        val actual = Census2025UsState20mGeometryPack.records
            .map { it.targetValue }
            .toSortedSet()

        eq(50, actual.size, "production state identity count")
        eq(expected, actual, "production geometry identities exactly equal WAS universe")
        listOf("DC", "PR", "AS", "GU", "MP", "VI").forEach { excluded ->
            checkThat(excluded !in actual, "$excluded excluded from 50-state WAS geometry pack")
        }

        eq(
            actual,
            Census2025UsState20mGeometryPack.featureSha256.keys.toSortedSet(),
            "hash table has exactly one entry per production state",
        )
        checkThat(
            Census2025UsState20mGeometryPack.records
                .map { it.assetId }
                .distinct()
                .size == 50,
            "all production geometry asset ids are unique",
        )
    }

    private fun integrityHashesRecompute() {
        val records = Census2025UsState20mGeometryPack.records
        eq(
            Census2025UsState20mGeometryPack.PACK_SHA256,
            GeometryPackIntegrity.packSha256(records),
            "canonical whole-pack SHA-256 recomputes in Kotlin",
        )
        eq(
            "5feb8c18688936a526523cb536766130be06b14ebfa918b3d99e39bfbcb0a130",
            Census2025UsState20mGeometryPack.PACK_SHA256,
            "pinned canonical pack SHA-256",
        )

        records.forEach { record ->
            eq(
                Census2025UsState20mGeometryPack.featureSha256.getValue(record.targetValue),
                GeometryPackIntegrity.featureSha256(record),
                "canonical feature hash ${record.targetValue}",
            )
        }
    }

    private fun multipartGeometryIsPreserved() {
        val byState = Census2025UsState20mGeometryPack.records.associateBy { it.targetValue }

        eq(47, polygonCount(byState.getValue("AK")), "Alaska multipart polygon count")
        eq(8, polygonCount(byState.getValue("HI")), "Hawaii multipart polygon count")
        eq(6, polygonCount(byState.getValue("CA")), "California islands preserved")
        eq(6, polygonCount(byState.getValue("MI")), "Michigan multipart geometry preserved")
        eq(4, polygonCount(byState.getValue("FL")), "Florida multipart geometry preserved")
        eq(3, polygonCount(byState.getValue("MA")), "Massachusetts multipart geometry preserved")

        checkThat(
            byState.values.all { polygonCount(it) >= 1 },
            "every production state has polygon geometry",
        )
        checkThat(
            byState.values
                .flatMap { (it.geometry as MultiPolygonGeometry).polygons }
                .all { it.outer.points.first() == it.outer.points.last() },
            "all production outer rings are closed",
        )
    }

    private fun alaskaDatelineHandlingIsExplicitAndSafe() {
        val alaska = Census2025UsState20mGeometryPack.records.single { it.targetValue == "AK" }
        checkThat(
            GeometryPackIntegrity.hasPositiveLongitude(alaska),
            "Alaska preserves Aleutian geometry east of the antimeridian",
        )
        checkThat(
            GeometryPackIntegrity.hasNegativeLongitude(alaska),
            "Alaska preserves geometry west of the antimeridian",
        )

        val maxJump = GeometryPackIntegrity.maxLongitudeJump(alaska)
        checkThat(maxJump < 1.0, "Census Alaska geometry is already split into short dateline-safe segments")
        checkThat(maxJump <= 180.0, "no Alaska ring silently crosses the map through the long way")

        Census2025UsState20mGeometryPack.records.forEach { record ->
            checkThat(
                GeometryPackIntegrity.maxLongitudeJump(record) <= 180.0,
                "${record.targetValue} contains no unsplit antimeridian jump",
            )
        }
    }

    private fun productionProviderResolvesAllFifty() {
        val provider = Census2025UsState20mGeometryPack.provider
        val expected = OfficialAwardCatalog.require("ARRL_WAS_BASIC")
            .requirement
            .targetUniverse!!

        expected.forEach { state ->
            val feature = provider.feature(
                AwardAreaGeometryIdentity(OfficialAwardTargetKind.US_STATE, state)
            )
            checkThat(feature != null, "$state resolves in production provider")
            eq(
                "census/2025/state/20m/$state",
                feature!!.assetId,
                "$state stable production asset id",
            )
            eq(
                Census2025UsState20mGeometryPack.UPSTREAM_URL,
                feature.source.sourceUrl,
                "$state retains exact Census artifact provenance",
            )
        }

        eq(
            null,
            provider.feature(AwardAreaGeometryIdentity(OfficialAwardTargetKind.US_STATE, "DC")),
            "District of Columbia does not resolve in WAS provider",
        )
        eq(
            null,
            provider.feature(AwardAreaGeometryIdentity(OfficialAwardTargetKind.US_STATE, "PR")),
            "Puerto Rico does not resolve in WAS provider",
        )
        eq(
            null,
            provider.feature(AwardAreaGeometryIdentity(OfficialAwardTargetKind.MAIDENHEAD_GRID4, "JO40")),
            "state provider ignores Maidenhead grids",
        )
    }

    private fun awardsMapBindsProductionStateGeometry() {
        val registry = AwardGeometryRegistry(
            listOf(
                Census2025UsState20mGeometryPack.provider,
                MaidenheadGrid4GeometryProvider("cp0007c-test"),
            )
        )
        val layers = AwardAreaMapProjectionService().project(
            qsos = emptyList(),
            evidence = AwardEvidenceSnapshot(),
            geometryCatalog = registry,
        )

        val was = layers.single { it.awardId == "ARRL_WAS_BASIC" }
        eq(50, was.geometryBoundCount, "all WAS states bind production geometry")
        eq(0, was.geometryUnboundCount, "no WAS state geometry missing")

        val ffma = layers.single { it.awardId == "ARRL_FFMA" }
        eq(488, ffma.geometryBoundCount, "all FFMA grids still bind deterministic geometry")
        eq(0, ffma.geometryUnboundCount, "FFMA remains fully geometry-resolved")

        checkThat(
            was.targets.all {
                it.geometry!!.source.sourceId == "US_CENSUS_CARTOGRAPHIC_BOUNDARY_FILES"
            },
            "WAS map bindings identify Census source",
        )
    }

    private fun censusProvenanceSurvivesBinding() {
        val binding = Census2025UsState20mGeometryPack.provider.feature(
            AwardAreaGeometryIdentity(OfficialAwardTargetKind.US_STATE, "OR")
        )!!
        eq(
            AwardGeometrySourceKind.EXTERNAL_DATASET,
            binding.source.kind,
            "production state source is external dataset",
        )
        eq(
            "US_CENSUS_CARTOGRAPHIC_BOUNDARY_FILES",
            binding.source.sourceId,
            "production state source id",
        )
        eq(
            "fieldops-cp0007c",
            binding.source.buildVersion,
            "production geometry build version",
        )
        checkThat(
            binding.source.licenseLabel!!.contains("Census Bureau source attribution requested"),
            "Census attribution requirement retained",
        )
    }

    private fun packIsDeterministicAndOffline() {
        val a = Census2025UsState20mGeometryPack.provider
        val b = UsStateGeometryPackProvider(
            Census2025UsState20mGeometryPack.manifest,
            Census2025UsState20mGeometryPack.records.reversed(),
        )
        listOf("AK", "CA", "OR", "WA", "ME").forEach { state ->
            val identity = AwardAreaGeometryIdentity(OfficialAwardTargetKind.US_STATE, state)
            eq(a.feature(identity), b.feature(identity), "$state lookup independent of record input order")
        }

        checkThat(
            Census2025UsState20mGeometryPack.records
                .all { it.geometry is MultiPolygonGeometry },
            "production state pack contains only platform-independent multi-polygons",
        )
    }

    private fun polygonCount(record: OfflineGeometryPackRecord): Int =
        (record.geometry as MultiPolygonGeometry).polygons.size
}
