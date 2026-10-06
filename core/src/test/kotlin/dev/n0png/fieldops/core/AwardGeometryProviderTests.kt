package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.awards.AwardEvidenceSnapshot
import dev.n0png.fieldops.core.awards.OfficialAwardTargetKind
import dev.n0png.fieldops.core.map.*

object AwardGeometryProviderTests {
    private var assertions = 0

    private fun checkThat(value: Boolean, message: String) {
        assertions++
        check(value) { message }
    }

    private fun <T> eq(expected: T, actual: T, message: String) =
        checkThat(expected == actual, "$message expected=$expected actual=$actual")

    private fun expectFailure(message: String, block: () -> Unit) {
        checkThat(runCatching(block).isFailure, message)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        iaruSourceIsPinned()
        maidenheadKnownCells()
        maidenheadGlobalEdges()
        maidenheadValidation()
        primitiveGeometryValidation()
        sourceMetadataValidation()
        censusContractMetadata()
        syntheticFixtureDoesNotMasqueradeAsCensus()
        usStatePackResolvesOfflineFeatures()
        usStatePackFailsClosed()
        registryCombinesIndependentProviders()
        registryRejectsProviderConflicts()
        projectionUsesRegistryWithoutEmbeddingPayload()
        deterministicProviderBehavior()
        println("CP-0007B award geometry provider tests: PASS assertions=$assertions")
    }

    private fun iaruSourceIsPinned() {
        val source = MaidenheadGrid4GeometryProvider.SOURCE
        eq(AwardGeometrySourceKind.DERIVED_STANDARD, source.kind, "Maidenhead source kind")
        eq("IARU_R1_MAIDENHEAD_LOCATOR", source.sourceId, "Maidenhead source id")
        checkThat(source.sourceVersion.contains("VHF Handbook 9.01"), "IARU handbook version retained")
        checkThat(source.sourceVersion.contains("6.2-6.3"), "IARU handbook sections retained")
        eq(
            "https://www.iaru-r1.org/wp-content/uploads/2021/03/VHF_Handbook_V9.01.pdf",
            source.sourceUrl,
            "IARU official source URL",
        )
        eq("2026-10-06", source.retrievedOn, "IARU retrieval date")
        eq(null, source.licenseLabel, "derived geometry does not claim an external geometry-data license")
    }

    private fun maidenheadKnownCells() {
        val provider = MaidenheadGrid4GeometryProvider("test-build")

        val jo40 = provider.feature(grid("JO40"))!!
        val joBounds = (jo40.payload as BoundsGeometry).bounds
        eq(8.0, joBounds.west, "JO40 west")
        eq(50.0, joBounds.south, "JO40 south")
        eq(10.0, joBounds.east, "JO40 east")
        eq(51.0, joBounds.north, "JO40 north")
        eq(2.0, joBounds.widthDegrees, "grid width")
        eq(1.0, joBounds.heightDegrees, "grid height")
        eq(GeoCoordinate(9.0, 50.5), joBounds.center, "JO40 center")
        eq("maidenhead/grid4/JO40", jo40.assetId, "grid asset id")
        eq("test-build", jo40.source.buildVersion, "provider build version retained")

        val fn31 = provider.feature(grid("fn31"))!!
        val fnBounds = (fn31.payload as BoundsGeometry).bounds
        eq(-74.0, fnBounds.west, "FN31 west")
        eq(41.0, fnBounds.south, "FN31 south")
        eq(-72.0, fnBounds.east, "FN31 east")
        eq(42.0, fnBounds.north, "FN31 north")
        eq("MAIDENHEAD_GRID4:FN31", fn31.identity.stableKey, "grid identity canonical")
    }

    private fun maidenheadGlobalEdges() {
        val provider = MaidenheadGrid4GeometryProvider()

        val aa00 = (provider.feature(grid("AA00"))!!.payload as BoundsGeometry).bounds
        eq(-180.0, aa00.west, "AA00 west origin")
        eq(-90.0, aa00.south, "AA00 south origin")
        eq(-178.0, aa00.east, "AA00 east")
        eq(-89.0, aa00.north, "AA00 north")

        val rr99 = (provider.feature(grid("RR99"))!!.payload as BoundsGeometry).bounds
        eq(178.0, rr99.west, "RR99 west")
        eq(89.0, rr99.south, "RR99 south")
        eq(180.0, rr99.east, "RR99 east global edge")
        eq(90.0, rr99.north, "RR99 north global edge")

        val jj00 = (provider.feature(grid("JJ00"))!!.payload as BoundsGeometry).bounds
        eq(0.0, jj00.west, "JJ00 west")
        eq(0.0, jj00.south, "JJ00 south")
    }

    private fun maidenheadValidation() {
        val provider = MaidenheadGrid4GeometryProvider()

        eq(
            null,
            provider.feature(AwardAreaGeometryIdentity(OfficialAwardTargetKind.US_STATE, "OR")),
            "Maidenhead provider ignores non-grid target kind",
        )
        expectFailure("six-character identity must be normalized before geometry lookup") {
            provider.feature(grid("FN31PR"))
        }
        expectFailure("invalid field rejected") {
            provider.feature(grid("ZZ99"))
        }
        expectFailure("invalid digits rejected") {
            provider.feature(grid("FNAA"))
        }
        expectFailure("blank grid rejected at identity boundary") {
            AwardAreaGeometryIdentity(OfficialAwardTargetKind.MAIDENHEAD_GRID4, " ")
        }
    }

    private fun primitiveGeometryValidation() {
        val bounds = GeoBounds(-10.0, 40.0, -8.0, 41.0)
        eq(GeoCoordinate(-9.0, 40.5), bounds.center, "bounds center")

        expectFailure("longitude out of range") { GeoCoordinate(181.0, 0.0) }
        expectFailure("latitude out of range") { GeoCoordinate(0.0, 91.0) }
        expectFailure("zero width bounds rejected") { GeoBounds(1.0, 0.0, 1.0, 1.0) }
        expectFailure("reversed latitude bounds rejected") { GeoBounds(0.0, 2.0, 1.0, 1.0) }

        val ring = rectangle(-124.0, 42.0, -116.0, 46.0)
        eq(5, ring.points.size, "rectangle ring has closing point")
        expectFailure("open ring rejected") {
            GeoLinearRing(
                listOf(
                    GeoCoordinate(0.0, 0.0),
                    GeoCoordinate(1.0, 0.0),
                    GeoCoordinate(1.0, 1.0),
                    GeoCoordinate(0.0, 1.0),
                )
            )
        }
        expectFailure("zero-area ring rejected") {
            GeoLinearRing(
                listOf(
                    GeoCoordinate(0.0, 0.0),
                    GeoCoordinate(1.0, 0.0),
                    GeoCoordinate(2.0, 0.0),
                    GeoCoordinate(0.0, 0.0),
                )
            )
        }
        expectFailure("empty multi-polygon rejected") { MultiPolygonGeometry(emptyList()) }
    }

    private fun sourceMetadataValidation() {
        expectFailure("external source requires HTTPS URL") {
            AwardGeometrySourceMetadata(
                kind = AwardGeometrySourceKind.EXTERNAL_DATASET,
                sourceId = "SRC",
                sourceVersion = "1",
                sourceUrl = null,
                licenseLabel = "public domain",
                retrievedOn = "2026-10-06",
                buildVersion = "1",
            )
        }
        expectFailure("external source requires license/public-domain metadata") {
            AwardGeometrySourceMetadata(
                kind = AwardGeometrySourceKind.EXTERNAL_DATASET,
                sourceId = "SRC",
                sourceVersion = "1",
                sourceUrl = "https://example.invalid",
                licenseLabel = null,
                retrievedOn = "2026-10-06",
                buildVersion = "1",
            )
        }
        expectFailure("synthetic source also requires explicit provenance label") {
            AwardGeometrySourceMetadata(
                kind = AwardGeometrySourceKind.SYNTHETIC_FIXTURE,
                sourceId = "FIXTURE",
                sourceVersion = "1",
                licenseLabel = null,
                retrievedOn = "2026-10-06",
                buildVersion = "1",
            )
        }
        expectFailure("non-HTTPS URL rejected") {
            AwardGeometrySourceMetadata(
                kind = AwardGeometrySourceKind.DERIVED_STANDARD,
                sourceId = "SRC",
                sourceVersion = "1",
                sourceUrl = "http://example.invalid",
                retrievedOn = "2026-10-06",
                buildVersion = "1",
            )
        }
        expectFailure("retrieval date must be ISO") {
            AwardGeometrySourceMetadata(
                kind = AwardGeometrySourceKind.DERIVED_STANDARD,
                sourceId = "SRC",
                sourceVersion = "1",
                retrievedOn = "06-10-2026",
                buildVersion = "1",
            )
        }
    }

    private fun censusContractMetadata() {
        val manifest = CensusStateGeometryPackContract.productionManifest(
            packVersion = "2025-20m-v1",
            buildVersion = "builder-1",
            declaredFeatureCount = 50,
        )

        eq(false, manifest.fixtureOnly, "Census production manifest is not fixture")
        eq(OfficialAwardTargetKind.US_STATE, manifest.targetKind, "Census pack target kind")
        eq(AwardGeometrySourceKind.EXTERNAL_DATASET, manifest.source.kind, "Census source kind")
        eq("US_CENSUS_CARTOGRAPHIC_BOUNDARY_FILES", manifest.source.sourceId, "Census source id")
        checkThat(manifest.source.sourceVersion.contains("2025"), "Census vintage retained")
        checkThat(manifest.source.sourceVersion.contains("1:20,000,000"), "Census scale retained")
        eq(CensusStateGeometryPackContract.SOURCE_URL, manifest.source.sourceUrl, "Census official URL")
        checkThat(
            manifest.source.licenseLabel!!.contains("17 U.S.C. §105"),
            "Census government-work copyright status retained",
        )
        checkThat(
            manifest.source.licenseLabel!!.contains("attribution requested"),
            "Census attribution request retained",
        )
        eq("2026-10-06", manifest.source.retrievedOn, "Census retrieval date")
        eq("builder-1", manifest.source.buildVersion, "Census build version")
        eq(50, manifest.declaredFeatureCount, "production declared feature count")
    }

    private fun syntheticFixtureDoesNotMasqueradeAsCensus() {
        val manifest = CensusStateGeometryPackContract.syntheticFixtureManifest(
            packVersion = "ci-1",
            declaredFeatureCount = 2,
        )

        eq(true, manifest.fixtureOnly, "CI state pack explicitly fixture only")
        eq(AwardGeometrySourceKind.SYNTHETIC_FIXTURE, manifest.source.kind, "fixture source kind")
        checkThat(manifest.source.sourceId.contains("SYNTHETIC"), "fixture source id says synthetic")
        checkThat(
            manifest.source.licenseLabel!!.contains("no Census boundary geometry"),
            "fixture explicitly disclaims Census geometry content",
        )
        eq(null, manifest.source.sourceUrl, "synthetic fixture has no fake upstream geometry URL")
    }

    private fun usStatePackResolvesOfflineFeatures() {
        val provider = fixtureStateProvider()

        val or = provider.feature(state("OR"))!!
        eq("US_STATE:OR", or.identity.stableKey, "Oregon stable key")
        eq("fixture/states/OR", or.assetId, "Oregon fixture asset")
        checkThat(or.payload is MultiPolygonGeometry, "state geometry uses multi-polygon payload")
        eq(AwardGeometrySourceKind.SYNTHETIC_FIXTURE, or.source.kind, "fixture provenance retained")

        val lower = provider.feature(state("or"))!!
        eq(or, lower, "state lookup is stable-key case insensitive")
        eq(null, provider.feature(grid("CN85")), "state provider ignores grids")
        eq(null, provider.feature(state("CA")), "missing state remains unresolved")
        checkThat(provider.providerId.contains("cp0007b-us-state-ci-fixture"), "pack identity included in provider id")
    }

    private fun usStatePackFailsClosed() {
        val manifest2 = CensusStateGeometryPackContract.syntheticFixtureManifest("ci-1", 2)
        val or = stateRecord("OR", "fixture/states/OR", -124.0, 42.0, -116.0, 46.0)
        val wa = stateRecord("WA", "fixture/states/WA", -124.8, 45.5, -116.9, 49.0)

        expectFailure("declared feature count mismatch") {
            UsStateGeometryPackProvider(manifest2, listOf(or))
        }
        expectFailure("duplicate state identity rejected") {
            UsStateGeometryPackProvider(manifest2, listOf(or, or.copy(assetId = "fixture/states/OR2")))
        }
        expectFailure("duplicate asset id rejected") {
            UsStateGeometryPackProvider(manifest2, listOf(or, wa.copy(assetId = or.assetId)))
        }
        expectFailure("invalid state code rejected") {
            UsStateGeometryPackProvider(
                CensusStateGeometryPackContract.syntheticFixtureManifest("ci-1", 1),
                listOf(or.copy(targetValue = "ORE")),
            )
        }
        expectFailure("fixture manifest cannot claim external dataset source") {
            OfflineGeometryPackManifest(
                packId = "bad",
                packVersion = "1",
                targetKind = OfficialAwardTargetKind.US_STATE,
                source = CensusStateGeometryPackContract.productionManifest("p", "b", 1).source,
                declaredFeatureCount = 1,
                fixtureOnly = true,
            )
        }
        expectFailure("production manifest cannot use synthetic source") {
            OfflineGeometryPackManifest(
                packId = "bad",
                packVersion = "1",
                targetKind = OfficialAwardTargetKind.US_STATE,
                source = manifest2.source,
                declaredFeatureCount = 1,
                fixtureOnly = false,
            )
        }
        expectFailure("U.S. state provider rejects wrong manifest target kind") {
            UsStateGeometryPackProvider(
                manifest2.copy(targetKind = OfficialAwardTargetKind.MAIDENHEAD_GRID4),
                listOf(or, wa),
            )
        }
    }

    private fun registryCombinesIndependentProviders() {
        val stateProvider = fixtureStateProvider()
        val registry = AwardGeometryRegistry(
            listOf(
                stateProvider,
                MaidenheadGrid4GeometryProvider("registry-test"),
            )
        )

        val orFeature = registry.feature(state("OR"))!!
        eq("fixture/states/OR", orFeature.assetId, "registry resolves state pack")
        val gridFeature = registry.feature(grid("JO40"))!!
        eq("maidenhead/grid4/JO40", gridFeature.assetId, "registry resolves derived grid")
        eq(null, registry.feature(state("CA")), "registry leaves unsupported state unbound")

        val binding = registry.binding(grid("JO40"))!!
        eq("maidenhead/grid4/JO40", binding.geometryAssetId, "registry exposes CP-0007A binding")
        eq("IARU_R1_MAIDENHEAD_LOCATOR", binding.source.sourceId, "binding source projected")
        eq(grid("JO40").stableKey, binding.identity.stableKey, "binding identity retained")
    }

    private fun registryRejectsProviderConflicts() {
        val base = MaidenheadGrid4GeometryProvider()
        expectFailure("duplicate provider ids rejected") {
            AwardGeometryRegistry(listOf(base, MaidenheadGrid4GeometryProvider("other-build")))
        }

        val conflict = object : AwardGeometryProvider {
            override val providerId = "conflicting-grid-provider"
            override fun feature(identity: AwardAreaGeometryIdentity): AwardGeometryFeature? =
                if (identity.targetKind == OfficialAwardTargetKind.MAIDENHEAD_GRID4) {
                    AwardGeometryFeature(
                        identity = AwardAreaGeometryIdentity(
                            OfficialAwardTargetKind.MAIDENHEAD_GRID4,
                            identity.targetValue.trim().uppercase(),
                        ),
                        assetId = "conflict/${identity.targetValue.trim().uppercase()}",
                        payload = BoundsGeometry(GeoBounds(0.0, 0.0, 1.0, 1.0)),
                        source = AwardGeometrySourceMetadata(
                            kind = AwardGeometrySourceKind.DERIVED_STANDARD,
                            sourceId = "CONFLICT_FIXTURE",
                            sourceVersion = "1",
                            retrievedOn = "2026-10-06",
                            buildVersion = "1",
                        ),
                    )
                } else null
        }
        val registry = AwardGeometryRegistry(listOf(base, conflict))
        expectFailure("two providers cannot silently resolve one geometry identity") {
            registry.feature(grid("JO40"))
        }

        val mismatch = object : AwardGeometryProvider {
            override val providerId = "mismatch-provider"
            override fun feature(identity: AwardAreaGeometryIdentity): AwardGeometryFeature? =
                AwardGeometryFeature(
                    identity = state("WA"),
                    assetId = "bad/WA",
                    payload = MultiPolygonGeometry(listOf(GeoPolygon(rectangle(-1.0, -1.0, 1.0, 1.0)))),
                    source = CensusStateGeometryPackContract.syntheticFixtureManifest("x", 1).source,
                )
        }
        expectFailure("provider cannot return mismatched identity") {
            AwardGeometryRegistry(listOf(mismatch)).feature(state("OR"))
        }
    }

    private fun projectionUsesRegistryWithoutEmbeddingPayload() {
        val registry = AwardGeometryRegistry(
            listOf(
                fixtureStateProvider(),
                MaidenheadGrid4GeometryProvider("projection-test"),
            )
        )
        val layers = AwardAreaMapProjectionService().project(
            qsos = emptyList(),
            evidence = AwardEvidenceSnapshot(),
            geometryCatalog = registry,
        )

        val was = layers.single { it.awardId == "ARRL_WAS_BASIC" }
        eq(2, was.geometryBoundCount, "only fixture OR/WA state geometry bound")
        eq(48, was.geometryUnboundCount, "other WAS states remain unbound")
        eq("fixture/states/OR", was.targets.single { it.identity.targetValue == "OR" }.geometry!!.geometryAssetId, "OR binding")

        val ffma = layers.single { it.awardId == "ARRL_FFMA" }
        eq(488, ffma.geometryBoundCount, "all deterministic FFMA grids have derived geometry")
        eq(0, ffma.geometryUnboundCount, "FFMA grids need no external pack")
        checkThat(
            ffma.targets.all { it.geometry!!.source.sourceId == "IARU_R1_MAIDENHEAD_LOCATOR" },
            "FFMA grid bindings retain IARU source",
        )

        val firstFfma = ffma.targets.first()
        val resolved = registry.feature(firstFfma.identity)!!
        checkThat(resolved.payload is BoundsGeometry, "payload resolved separately from map target")
        checkThat(
            firstFfma.geometry!!.javaClass.declaredFields.none { it.name == "payload" },
            "CP-0007A binding does not embed geometry payload",
        )
    }

    private fun deterministicProviderBehavior() {
        val providerA = MaidenheadGrid4GeometryProvider("stable-build")
        val providerB = MaidenheadGrid4GeometryProvider("stable-build")
        eq(providerA.feature(grid("JO40")), providerB.feature(grid("JO40")), "derived geometry deterministic")

        val records = listOf(
            stateRecord("WA", "fixture/states/WA", -124.8, 45.5, -116.9, 49.0),
            stateRecord("OR", "fixture/states/OR", -124.0, 42.0, -116.0, 46.0),
        )
        val manifest = CensusStateGeometryPackContract.syntheticFixtureManifest("stable", 2)
        val a = UsStateGeometryPackProvider(manifest, records)
        val b = UsStateGeometryPackProvider(manifest, records.reversed())
        eq(a.feature(state("OR")), b.feature(state("OR")), "pack lookup independent of input order")
        eq(a.feature(state("WA")), b.feature(state("WA")), "pack lookup deterministic")
    }

    private fun fixtureStateProvider(): UsStateGeometryPackProvider {
        val records = listOf(
            stateRecord("OR", "fixture/states/OR", -124.0, 42.0, -116.0, 46.0),
            stateRecord("WA", "fixture/states/WA", -124.8, 45.5, -116.9, 49.0),
        )
        return UsStateGeometryPackProvider(
            CensusStateGeometryPackContract.syntheticFixtureManifest("ci-1", records.size),
            records,
        )
    }

    private fun stateRecord(
        state: String,
        assetId: String,
        west: Double,
        south: Double,
        east: Double,
        north: Double,
    ) = OfflineGeometryPackRecord(
        targetValue = state,
        assetId = assetId,
        geometry = MultiPolygonGeometry(
            polygons = listOf(
                GeoPolygon(rectangle(west, south, east, north))
            )
        ),
    )

    private fun rectangle(
        west: Double,
        south: Double,
        east: Double,
        north: Double,
    ) = GeoLinearRing(
        listOf(
            GeoCoordinate(west, south),
            GeoCoordinate(east, south),
            GeoCoordinate(east, north),
            GeoCoordinate(west, north),
            GeoCoordinate(west, south),
        )
    )

    private fun state(value: String) =
        AwardAreaGeometryIdentity(OfficialAwardTargetKind.US_STATE, value)

    private fun grid(value: String) =
        AwardAreaGeometryIdentity(OfficialAwardTargetKind.MAIDENHEAD_GRID4, value)
}
