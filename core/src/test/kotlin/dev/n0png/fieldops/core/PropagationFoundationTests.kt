package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.map.GeoBounds
import dev.n0png.fieldops.core.map.GeoCoordinate
import dev.n0png.fieldops.core.propagation.*

object PropagationFoundationTests {
    private var assertions = 0

    private fun checkThat(value: Boolean, message: String) {
        assertions++
        check(value) { message }
    }

    private fun <T> eq(expected: T, actual: T, message: String) =
        checkThat(expected == actual, "$message expected=$expected actual=$actual")

    private fun expectFailure(message: String, block: () -> Unit) {
        assertions++
        check(runCatching(block).isFailure) { "Expected failure: $message" }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        sourceProvenanceValidation()
        confidenceAndPositionValidation()
        freshnessClassification()
        solarGeomagneticNormalization()
        ionosphericProductNormalization()
        heardPathEvidenceIsObservationNotQso()
        modeledPathValidation()
        snapshotValidation()
        offlineStoreSemantics()
        observedPathAssessment()
        modelOnlyAssessment()
        outsideModelLimitsAssessment()
        staleAndFutureEvidenceAssessment()
        directionAndBandMatching()
        ionosphericContextDoesNotMasqueradeAsPathPrediction()
        deterministicProviderNeutralBehavior()
        println("CP-0008A propagation intelligence foundation tests: PASS assertions=$assertions")
    }

    private fun sourceProvenanceValidation() {
        val source = observedSource()
        eq("SYNTH_HEARD", source.sourceId, "source id retained")
        eq("Synthetic CI", source.providerName, "provider name retained")
        eq(PropagationSourceClass.SYNTHETIC_FIXTURE, source.sourceClass, "fixture source class")
        eq(1_000_000L, source.retrievedAtUtcMillis, "retrieval timestamp retained")
        eq(null, source.sourceUrl, "synthetic source carries no fake live URL")

        val live = PropagationSourceRef(
            sourceId = "NOAA_STYLE_FIXTURE_METADATA",
            providerName = "Example Provider",
            sourceClass = PropagationSourceClass.MEASUREMENT,
            sourceVersion = "v1",
            retrievedAtUtcMillis = 10,
            sourceUrl = "https://example.invalid/data",
            termsUrl = "https://example.invalid/terms",
        )
        eq("https://example.invalid/data", live.sourceUrl, "HTTPS source URL accepted")
        eq("https://example.invalid/terms", live.termsUrl, "HTTPS terms URL accepted")

        expectFailure("blank source id") { live.copy(sourceId = " ") }
        expectFailure("blank provider") { live.copy(providerName = "") }
        expectFailure("blank source version") { live.copy(sourceVersion = "") }
        expectFailure("negative retrieval time") { live.copy(retrievedAtUtcMillis = -1) }
        expectFailure("HTTP source URL") { live.copy(sourceUrl = "http://example.invalid") }
        expectFailure("HTTP terms URL") { live.copy(termsUrl = "http://example.invalid") }
        expectFailure("synthetic source cannot claim live URL") {
            source.copy(sourceUrl = "https://example.invalid/fake")
        }
    }

    private fun confidenceAndPositionValidation() {
        val confidence = syntheticConfidence(0.8)
        eq(0.8, confidence.value, "confidence retained")
        eq(PropagationConfidenceBasis.SYNTHETIC, confidence.basis, "confidence basis retained")
        checkThat(confidence.explanation.isNotBlank(), "confidence explanation retained")

        expectFailure("confidence below zero") { syntheticConfidence(-0.01) }
        expectFailure("confidence above one") { syntheticConfidence(1.01) }
        expectFailure("blank confidence explanation") {
            PropagationConfidence(0.5, PropagationConfidenceBasis.DERIVED, " ")
        }

        val grid = gridPosition("jo40ab")
        eq("JO40AB", grid.normalizedGrid, "grid normalized")
        eq("GRID:JO40AB", grid.stableKey, "grid stable key")

        val coord = coordinatePosition(50.0, 8.0)
        eq("COORD:50,8", coord.stableKey, "coordinate stable key canonical")
        eq(PropagationLocationMethod.SYNTHETIC_FIXTURE, coord.method, "position method retained")

        val both = PropagationPosition(
            coordinate = GeoCoordinate(8.0, 50.0),
            maidenheadGrid = "JO40",
            method = PropagationLocationMethod.PROVIDER_COORDINATE,
            sourceReference = "fixture:station",
        )
        eq("JO40", both.normalizedGrid, "combined position preserves grid")
        checkThat(both.stableKey.startsWith("COORD:"), "explicit coordinate is stable-key authority when present")

        expectFailure("location cannot be absent") {
            PropagationPosition(method = PropagationLocationMethod.STATION_PROFILE)
        }
        expectFailure("invalid grid") { gridPosition("ZZ99") }
        expectFailure("explicit coordinate method requires coordinate") {
            PropagationPosition(
                maidenheadGrid = "JO40",
                method = PropagationLocationMethod.EXPLICIT_COORDINATE,
            )
        }
        expectFailure("explicit grid method requires grid") {
            PropagationPosition(
                coordinate = GeoCoordinate(8.0, 50.0),
                method = PropagationLocationMethod.EXPLICIT_GRID,
            )
        }
        expectFailure("blank position reference") {
            PropagationPosition(
                maidenheadGrid = "JO40",
                method = PropagationLocationMethod.EXPLICIT_GRID,
                sourceReference = " ",
            )
        }

        val endpoint = PropagationEndpoint(grid, callsign = "n0png", label = "home")
        eq("N0PNG", endpoint.normalizedCallsign, "endpoint callsign normalized")
        expectFailure("blank endpoint callsign") { endpoint.copy(callsign = " ") }
        expectFailure("blank endpoint label") { endpoint.copy(label = "") }

        val bounds = BoundsPropagationCoverage(GeoBounds(-10.0, 40.0, 10.0, 55.0))
        eq(-10.0, bounds.bounds.west, "bounds coverage is platform-independent")
        expectFailure("path endpoints must differ") {
            PathPropagationCoverage(grid, grid)
        }
    }

    private fun freshnessClassification() {
        val policy = PropagationFreshnessPolicy(
            freshForMillis = 10_000,
            usableForMillis = 60_000,
        )
        val now = 100_000L
        eq(PropagationFreshness.FRESH, classify(100_000, now, policy), "zero age fresh")
        eq(PropagationFreshness.FRESH, classify(90_000, now, policy), "fresh boundary inclusive")
        eq(PropagationFreshness.AGING, classify(89_999, now, policy), "aging after fresh boundary")
        eq(PropagationFreshness.AGING, classify(40_000, now, policy), "usable boundary inclusive")
        eq(PropagationFreshness.STALE, classify(39_999, now, policy), "stale beyond usable boundary")
        eq(PropagationFreshness.FUTURE_DATED, classify(100_001, now, policy), "future evidence explicit")

        expectFailure("negative fresh window") {
            PropagationFreshnessPolicy(-1, 10)
        }
        expectFailure("usable shorter than fresh") {
            PropagationFreshnessPolicy(10, 9)
        }
        expectFailure("negative evidence timestamp") {
            PropagationFreshnessClassifier.classify(-1, now, policy)
        }
        expectFailure("negative now timestamp") {
            PropagationFreshnessClassifier.classify(0, -1, policy)
        }
    }

    private fun solarGeomagneticNormalization() {
        val solar = solarObservation(
            id = "solar-1",
            observedAt = 900_000,
            kp = 6.0,
        )
        eq(GlobalPropagationCoverage, solar.coverage, "solar context is global coverage")
        eq(6.0, solar.planetaryKp, "Kp retained")
        eq(155.0, solar.f107SolarFluxSfu, "F10.7 retained")
        eq(12.0, solar.planetaryAp, "Ap retained")
        eq(120.0, solar.sunspotNumber, "sunspot number retained")
        checkThat(PropagationDataQuality.SYNTHETIC in solar.quality, "quality metadata retained")

        expectFailure("empty solar observation") {
            solar.copy(
                f107SolarFluxSfu = null,
                planetaryKp = null,
                planetaryAp = null,
                sunspotNumber = null,
                xrayFluxWattsPerSquareMeter = null,
            )
        }
        expectFailure("Kp below range") { solar.copy(planetaryKp = -0.1) }
        expectFailure("Kp above range") { solar.copy(planetaryKp = 9.1) }
        expectFailure("negative Ap") { solar.copy(planetaryAp = -1.0) }
        expectFailure("negative sunspot number") { solar.copy(sunspotNumber = -1.0) }
        expectFailure("nonpositive F10.7") { solar.copy(f107SolarFluxSfu = 0.0) }
        expectFailure("nonpositive xray flux") { solar.copy(xrayFluxWattsPerSquareMeter = 0.0) }
        expectFailure("quality required") { solar.copy(quality = emptySet()) }
    }

    private fun ionosphericProductNormalization() {
        val coverage = BoundsPropagationCoverage(GeoBounds(-20.0, 35.0, 20.0, 60.0))
        val sample = IonosphericSample(
            position = coordinatePosition(50.0, 8.0),
            value = 8.2,
            confidence = syntheticConfidence(0.7),
        )
        val fof2 = IonosphericMapProduct(
            evidenceId = "iono-fof2",
            source = derivedSource(retrievedAt = 1_000_000),
            observedAtUtcMillis = 990_000,
            confidence = syntheticConfidence(0.7),
            quality = setOf(PropagationDataQuality.SYNTHETIC),
            coverage = coverage,
            metric = IonosphericMetric.FOF2_MHZ,
            samples = listOf(sample),
            generatedAtUtcMillis = 985_000,
        )
        eq(IonosphericMetric.FOF2_MHZ, fof2.metric, "foF2 metric retained")
        eq(null, fof2.referenceDistanceKm, "foF2 has no MUF reference distance")
        eq(1, fof2.samples.size, "ionospheric samples retained")
        eq(8.2, fof2.samples.single().value, "ionospheric sample value retained")

        val muf = fof2.copy(
            evidenceId = "iono-muf",
            metric = IonosphericMetric.MUF_MHZ,
            referenceDistanceKm = 3000.0,
        )
        eq(3000.0, muf.referenceDistanceKm, "MUF reference distance explicit")

        expectFailure("MUF requires distance") {
            fof2.copy(evidenceId = "bad-muf", metric = IonosphericMetric.MUF_MHZ)
        }
        expectFailure("foF2 rejects distance") {
            fof2.copy(referenceDistanceKm = 3000.0)
        }
        expectFailure("empty ionospheric samples") { fof2.copy(samples = emptyList()) }
        expectFailure("nonpositive ionospheric sample") { sample.copy(value = 0.0) }
        expectFailure("generation after retrieval") {
            fof2.copy(generatedAtUtcMillis = 1_000_001)
        }
        expectFailure("ionospheric quality required") { fof2.copy(quality = emptySet()) }
    }

    private fun heardPathEvidenceIsObservationNotQso() {
        val heard = heardPath(
            id = "heard-1",
            observedAt = 995_000,
            band = "20m",
            tx = endpoint("JO40", "N0PNG"),
            rx = endpoint("FN31", "K1ABC"),
        )
        eq("20m", heard.normalizedBand, "heard path band normalized")
        eq("FT8", heard.normalizedMode, "heard path mode normalized")
        eq(14_074_000L, heard.frequencyHz, "heard path exact frequency retained")
        eq(-8.5, heard.snrDb, "heard path SNR retained")
        eq(1, heard.reportCount, "heard path report count retained")
        checkThat(heard.coverage is PathPropagationCoverage, "heard path has explicit path coverage")
        checkThat(
            HeardPathObservation::class.java.declaredFields.none {
                it.name.contains("lotw", ignoreCase = true) ||
                    it.name == "qsoId"
            },
            "heard path is not silently promoted into QSO/LoTW state",
        )

        expectFailure("heard frequency positive") { heard.copy(frequencyHz = 0) }
        expectFailure("heard band required") { heard.copy(band = " ") }
        expectFailure("heard mode required") { heard.copy(mode = "") }
        expectFailure("heard report count positive") { heard.copy(reportCount = 0) }
        expectFailure("heard quality required") { heard.copy(quality = emptySet()) }
        expectFailure("heard SNR finite") { heard.copy(snrDb = Double.NaN) }
    }

    private fun modeledPathValidation() {
        val model = modeledPath(
            id = "model-1",
            observedAt = 990_000,
            origin = gridPosition("JO40"),
            destination = gridPosition("FN31"),
            mufHz = 18_000_000L,
            lufHz = 5_000_000L,
        )
        eq(18_000_000L, model.maximumUsableFrequencyHz, "modeled MUF retained")
        eq(5_000_000L, model.lowestUsableFrequencyHz, "modeled LUF retained")
        checkThat(model.modelInputsSummary.contains("synthetic"), "model input explanation retained")

        expectFailure("model needs at least one frequency limit") {
            model.copy(maximumUsableFrequencyHz = null, lowestUsableFrequencyHz = null)
        }
        expectFailure("LUF cannot exceed MUF") {
            model.copy(maximumUsableFrequencyHz = 5_000_000, lowestUsableFrequencyHz = 6_000_000)
        }
        expectFailure("MUF positive") { model.copy(maximumUsableFrequencyHz = 0) }
        expectFailure("LUF positive") { model.copy(lowestUsableFrequencyHz = 0) }
        expectFailure("model input summary required") { model.copy(modelInputsSummary = " ") }
        expectFailure("measurement source cannot masquerade as path model") {
            model.copy(source = measurementSource(retrievedAt = 1_000_000))
        }
    }

    private fun snapshotValidation() {
        val snapshot = completeSnapshot()
        eq("snapshot-main", snapshot.snapshotId, "snapshot id retained")
        eq(3, snapshot.observationCount, "snapshot observation count")
        eq(1, snapshot.modeledCount, "snapshot model count")
        eq(4, snapshot.allEvidence().size, "snapshot full evidence count")
        checkThat("SYNTH_SOLAR" in snapshot.sourceIds, "solar source indexed")
        checkThat("SYNTH_IONO" in snapshot.sourceIds, "ionosphere source indexed")
        checkThat("SYNTH_HEARD" in snapshot.sourceIds, "heard source indexed")
        checkThat("SYNTH_MODEL" in snapshot.sourceIds, "model source indexed")

        expectFailure("duplicate evidence id rejected") {
            snapshot.copy(
                modeledPaths = listOf(
                    snapshot.modeledPaths.single().copy(evidenceId = snapshot.heardPaths.single().evidenceId)
                )
            )
        }
        expectFailure("snapshot cannot predate retrieval") {
            snapshot.copy(capturedAtUtcMillis = 998_999)
        }
        expectFailure("snapshot id required") { snapshot.copy(snapshotId = " ") }
        expectFailure("snapshot UTC nonnegative") { snapshot.copy(capturedAtUtcMillis = -1) }
    }

    private fun offlineStoreSemantics() {
        val store: PropagationSnapshotStore = InMemoryPropagationSnapshotStore()
        eq(null, store.latest(), "empty offline store has no latest snapshot")

        val older = completeSnapshot().copy(
            snapshotId = "snapshot-a",
            capturedAtUtcMillis = 1_000_000,
        )
        val newer = completeSnapshot().copy(
            snapshotId = "snapshot-b",
            capturedAtUtcMillis = 1_100_000,
            solarGeomagnetic = emptyList(),
            ionosphericProducts = emptyList(),
            heardPaths = emptyList(),
            modeledPaths = emptyList(),
        )
        val sameTimeLaterId = newer.copy(snapshotId = "snapshot-c")

        store.save(newer)
        store.save(older)
        store.save(sameTimeLaterId)
        store.save(older)

        eq("snapshot-c", store.latest()!!.snapshotId, "latest store ordering deterministic")
        eq(older, store.snapshot("snapshot-a"), "snapshot lookup offline")
        eq(null, store.snapshot("missing"), "missing snapshot returns null")
        eq("snapshot-a", store.latestAtOrBefore(1_050_000)!!.snapshotId, "as-of lookup works")
        eq(null, store.latestAtOrBefore(999_999), "as-of lookup before history returns null")
        eq(
            listOf("snapshot-c", "snapshot-b"),
            store.history(2).map { it.snapshotId },
            "history newest first with deterministic tie break",
        )
        expectFailure("history limit must be positive") { store.history(0) }
        expectFailure("as-of UTC nonnegative") { store.latestAtOrBefore(-1) }
        expectFailure("same snapshot id cannot change content") {
            store.save(older.copy(capturedAtUtcMillis = 1_000_001))
        }
    }

    private fun observedPathAssessment() {
        val snapshot = completeSnapshot(kp = 6.0)
        val query = query(
            frequencyHz = 14_074_000,
            band = "20m",
            now = 1_000_000,
        )
        val assessment = PropagationAssessmentEngine().assess(snapshot, query)
        eq(PropagationUsability.GOOD, assessment.usability, "fresh observed path is GOOD")
        eq(0.95, assessment.confidence!!.value, "observed-path confidence selected")
        checkThat(
            assessment.has(PropagationAssessmentReasonCode.RECENT_OBSERVED_PATH),
            "observed path explanation present",
        )
        checkThat(
            assessment.has(PropagationAssessmentReasonCode.MODEL_FREQUENCY_WITHIN_LIMITS),
            "model frequency context also present",
        )
        checkThat(
            assessment.has(PropagationAssessmentReasonCode.GEOMAGNETIC_STORM_CAUTION),
            "Kp>=5 caution present without overriding observed path",
        )
        checkThat(
            assessment.has(PropagationAssessmentReasonCode.SOLAR_CONTEXT_AVAILABLE),
            "solar context explanation present",
        )
        checkThat(
            assessment.has(PropagationAssessmentReasonCode.IONOSPHERIC_MAP_CONTEXT_AVAILABLE),
            "ionospheric map context explanation present",
        )
        checkThat("heard-main" in assessment.evidenceIds, "observed evidence used")
        checkThat("model-main" in assessment.evidenceIds, "model evidence used")
        checkThat("solar-main" in assessment.evidenceIds, "solar context evidence used")
        checkThat("iono-main" in assessment.evidenceIds, "ionosphere context evidence used")
    }

    private fun modelOnlyAssessment() {
        val snapshot = completeSnapshot().copy(heardPaths = emptyList())
        val assessment = PropagationAssessmentEngine().assess(
            snapshot,
            query(frequencyHz = 14_074_000, band = "20m", now = 1_000_000),
        )
        eq(PropagationUsability.MARGINAL, assessment.usability, "model-only path is marginal")
        eq(0.75, assessment.confidence!!.value, "model confidence selected")
        checkThat(
            assessment.has(PropagationAssessmentReasonCode.MODEL_FREQUENCY_WITHIN_LIMITS),
            "model-within-limits explanation",
        )
        checkThat(
            assessment.has(PropagationAssessmentReasonCode.MODEL_ONLY_NO_OBSERVED_PATH),
            "model-only caveat explicit",
        )
        checkThat(
            !assessment.has(PropagationAssessmentReasonCode.RECENT_OBSERVED_PATH),
            "no fabricated observed path reason",
        )
    }

    private fun outsideModelLimitsAssessment() {
        val modelOnly = completeSnapshot().copy(heardPaths = emptyList())

        val above = PropagationAssessmentEngine().assess(
            modelOnly,
            query(frequencyHz = 21_100_000, band = "15m", now = 1_000_000),
        )
        eq(PropagationUsability.POOR, above.usability, "frequency above modeled MUF is poor")
        checkThat(
            above.has(PropagationAssessmentReasonCode.FREQUENCY_ABOVE_MODELED_MUF),
            "above-MUF reason explicit",
        )

        val below = PropagationAssessmentEngine().assess(
            modelOnly,
            query(frequencyHz = 3_500_000, band = "80m", now = 1_000_000),
        )
        eq(PropagationUsability.POOR, below.usability, "frequency below modeled LUF is poor")
        checkThat(
            below.has(PropagationAssessmentReasonCode.FREQUENCY_BELOW_MODELED_LUF),
            "below-LUF reason explicit",
        )
    }

    private fun staleAndFutureEvidenceAssessment() {
        val base = completeSnapshot()
        val stale = base.copy(
            solarGeomagnetic = base.solarGeomagnetic.map { it.copy(observedAtUtcMillis = 100_000) },
            ionosphericProducts = base.ionosphericProducts.map { it.copy(observedAtUtcMillis = 100_000) },
            heardPaths = base.heardPaths.map { it.copy(observedAtUtcMillis = 100_000) },
            modeledPaths = base.modeledPaths.map { it.copy(observedAtUtcMillis = 100_000) },
        )
        val staleAssessment = PropagationAssessmentEngine().assess(
            stale,
            query(frequencyHz = 14_074_000, band = "20m", now = 1_000_000),
        )
        eq(PropagationUsability.UNKNOWN, staleAssessment.usability, "stale path evidence not treated current")
        eq(null, staleAssessment.confidence, "stale-only assessment has no current confidence")
        checkThat(
            staleAssessment.has(PropagationAssessmentReasonCode.STALE_EVIDENCE_IGNORED),
            "stale evidence explicitly reported",
        )
        checkThat(
            staleAssessment.has(PropagationAssessmentReasonCode.INSUFFICIENT_PATH_EVIDENCE),
            "insufficient current path evidence explicit",
        )

        val future = base.copy(
            solarGeomagnetic = emptyList(),
            ionosphericProducts = emptyList(),
            heardPaths = base.heardPaths.map { it.copy(observedAtUtcMillis = 1_000_001) },
            modeledPaths = emptyList(),
        )
        val futureAssessment = PropagationAssessmentEngine().assess(
            future,
            query(frequencyHz = 14_074_000, band = "20m", now = 1_000_000),
        )
        eq(PropagationUsability.UNKNOWN, futureAssessment.usability, "future path ignored")
        checkThat(
            futureAssessment.has(PropagationAssessmentReasonCode.FUTURE_DATED_EVIDENCE_IGNORED),
            "future evidence explicitly reported",
        )

        val aging = base.copy(
            solarGeomagnetic = emptyList(),
            ionosphericProducts = emptyList(),
            heardPaths = base.heardPaths.map { it.copy(observedAtUtcMillis = 960_000) },
            modeledPaths = emptyList(),
        )
        val agingAssessment = PropagationAssessmentEngine().assess(
            aging,
            query(
                frequencyHz = 14_074_000,
                band = "20m",
                now = 1_000_000,
                policy = PropagationFreshnessPolicy(10_000, 60_000),
            ),
        )
        eq(PropagationUsability.MARGINAL, agingAssessment.usability, "aging observed path is marginal")
        checkThat(
            agingAssessment.has(PropagationAssessmentReasonCode.AGING_OBSERVED_PATH),
            "aging observation explained",
        )
    }

    private fun directionAndBandMatching() {
        val snapshot = completeSnapshot().copy(
            solarGeomagnetic = emptyList(),
            ionosphericProducts = emptyList(),
            modeledPaths = emptyList(),
        )
        val engine = PropagationAssessmentEngine()

        val reverse = PropagationAssessmentQuery(
            band = "20M",
            frequencyHz = 14_074_000,
            origin = gridPosition("FN31"),
            destination = gridPosition("JO40"),
            nowUtcMillis = 1_000_000,
        )
        eq(PropagationUsability.GOOD, engine.assess(snapshot, reverse).usability, "reverse path matches")

        val localDirectionOnly = PropagationAssessmentQuery(
            band = "20m",
            frequencyHz = 14_074_000,
            origin = gridPosition("JO40"),
            destination = null,
            nowUtcMillis = 1_000_000,
        )
        eq(
            PropagationUsability.GOOD,
            engine.assess(snapshot, localDirectionOnly).usability,
            "origin-only direction query accepts path touching origin",
        )

        val wrongBand = reverse.copy(band = "40m")
        val wrongBandAssessment = engine.assess(snapshot, wrongBand)
        eq(PropagationUsability.UNKNOWN, wrongBandAssessment.usability, "heard path is band-specific")
        checkThat(
            wrongBandAssessment.has(PropagationAssessmentReasonCode.INSUFFICIENT_PATH_EVIDENCE),
            "wrong-band query does not borrow 20m observation",
        )

        val otherPath = reverse.copy(destination = gridPosition("EM10"))
        eq(
            PropagationUsability.UNKNOWN,
            engine.assess(snapshot, otherPath).usability,
            "different destination does not borrow path observation",
        )
    }

    private fun ionosphericContextDoesNotMasqueradeAsPathPrediction() {
        val snapshot = completeSnapshot().copy(
            heardPaths = emptyList(),
            modeledPaths = emptyList(),
        )
        val assessment = PropagationAssessmentEngine().assess(
            snapshot,
            query(frequencyHz = 14_074_000, band = "20m", now = 1_000_000),
        )
        eq(PropagationUsability.UNKNOWN, assessment.usability, "map products alone do not become path prediction")
        checkThat(
            assessment.has(PropagationAssessmentReasonCode.IONOSPHERIC_MAP_CONTEXT_AVAILABLE),
            "ionospheric map is retained as context",
        )
        checkThat(
            assessment.has(PropagationAssessmentReasonCode.INSUFFICIENT_PATH_EVIDENCE),
            "map context does not fabricate path evidence",
        )
    }

    private fun deterministicProviderNeutralBehavior() {
        val a = completeSnapshot()
        val b = a.copy(
            solarGeomagnetic = a.solarGeomagnetic.reversed(),
            ionosphericProducts = a.ionosphericProducts.reversed(),
            heardPaths = a.heardPaths.reversed(),
            modeledPaths = a.modeledPaths.reversed(),
        )
        val query = query(frequencyHz = 14_074_000, band = "20m", now = 1_000_000)
        val engine = PropagationAssessmentEngine()
        eq(engine.assess(a, query), engine.assess(b, query), "assessment independent of evidence list order")

        val domainFields = listOf(
            PropagationSnapshot::class.java,
            HeardPathObservation::class.java,
            IonosphericMapProduct::class.java,
            ModeledPathEstimate::class.java,
        ).flatMap { it.declaredFields.toList() }
        checkThat(
            domainFields.none {
                it.type.name.startsWith("android.") ||
                    it.type.name.startsWith("androidx.") ||
                    it.type.name.contains("GoogleMap") ||
                    it.type.name.contains("Mapbox")
            },
            "propagation foundation carries no Android/map-SDK field types",
        )

        checkThat(
            PropagationAssessmentEngine::class.java.declaredFields.isEmpty(),
            "assessment engine carries no provider client/account state",
        )
    }

    private fun completeSnapshot(kp: Double = 2.0): PropagationSnapshot {
        val origin = gridPosition("JO40")
        val destination = gridPosition("FN31")
        return PropagationSnapshot(
            snapshotId = "snapshot-main",
            capturedAtUtcMillis = 1_000_000,
            solarGeomagnetic = listOf(
                solarObservation("solar-main", 995_000, kp)
            ),
            ionosphericProducts = listOf(
                IonosphericMapProduct(
                    evidenceId = "iono-main",
                    source = derivedSource(retrievedAt = 999_000),
                    observedAtUtcMillis = 990_000,
                    confidence = syntheticConfidence(0.7),
                    quality = setOf(PropagationDataQuality.SYNTHETIC),
                    coverage = BoundsPropagationCoverage(
                        GeoBounds(-80.0, 35.0, 20.0, 55.0)
                    ),
                    metric = IonosphericMetric.FOF2_MHZ,
                    samples = listOf(
                        IonosphericSample(
                            position = coordinatePosition(50.0, 8.0),
                            value = 8.2,
                            confidence = syntheticConfidence(0.7),
                        )
                    ),
                    generatedAtUtcMillis = 989_000,
                )
            ),
            heardPaths = listOf(
                heardPath(
                    id = "heard-main",
                    observedAt = 997_000,
                    band = "20m",
                    tx = PropagationEndpoint(origin, callsign = "N0PNG"),
                    rx = PropagationEndpoint(destination, callsign = "K1ABC"),
                )
            ),
            modeledPaths = listOf(
                modeledPath(
                    id = "model-main",
                    observedAt = 992_000,
                    origin = origin,
                    destination = destination,
                    mufHz = 18_000_000,
                    lufHz = 5_000_000,
                )
            ),
        )
    }

    private fun observedSource(
        id: String = "SYNTH_HEARD",
        retrievedAt: Long = 1_000_000,
    ) = PropagationSourceRef(
        sourceId = id,
        providerName = "Synthetic CI",
        sourceClass = PropagationSourceClass.SYNTHETIC_FIXTURE,
        sourceVersion = "cp0008a-v1",
        retrievedAtUtcMillis = retrievedAt,
    )

    private fun derivedSource(retrievedAt: Long) = PropagationSourceRef(
        sourceId = "SYNTH_IONO",
        providerName = "Synthetic CI",
        sourceClass = PropagationSourceClass.SYNTHETIC_FIXTURE,
        sourceVersion = "cp0008a-v1",
        retrievedAtUtcMillis = retrievedAt,
    )

    private fun modelSource(retrievedAt: Long) = PropagationSourceRef(
        sourceId = "SYNTH_MODEL",
        providerName = "Synthetic CI",
        sourceClass = PropagationSourceClass.SYNTHETIC_FIXTURE,
        sourceVersion = "cp0008a-v1",
        retrievedAtUtcMillis = retrievedAt,
    )

    private fun measurementSource(retrievedAt: Long) = PropagationSourceRef(
        sourceId = "MEASUREMENT",
        providerName = "Example measurement",
        sourceClass = PropagationSourceClass.MEASUREMENT,
        sourceVersion = "v1",
        retrievedAtUtcMillis = retrievedAt,
        sourceUrl = "https://example.invalid/measurement",
    )

    private fun solarObservation(id: String, observedAt: Long, kp: Double) =
        SolarGeomagneticObservation(
            evidenceId = id,
            source = observedSource("SYNTH_SOLAR", 999_000),
            observedAtUtcMillis = observedAt,
            confidence = syntheticConfidence(0.9),
            quality = setOf(PropagationDataQuality.SYNTHETIC),
            f107SolarFluxSfu = 155.0,
            planetaryKp = kp,
            planetaryAp = 12.0,
            sunspotNumber = 120.0,
        )

    private fun heardPath(
        id: String,
        observedAt: Long,
        band: String,
        tx: PropagationEndpoint,
        rx: PropagationEndpoint,
    ) = HeardPathObservation(
        evidenceId = id,
        source = observedSource(retrievedAt = 999_000),
        observedAtUtcMillis = observedAt,
        confidence = syntheticConfidence(0.95),
        quality = setOf(PropagationDataQuality.SYNTHETIC),
        transmitter = tx,
        receiver = rx,
        frequencyHz = 14_074_000,
        band = band,
        mode = "ft8",
        snrDb = -8.5,
    )

    private fun modeledPath(
        id: String,
        observedAt: Long,
        origin: PropagationPosition,
        destination: PropagationPosition,
        mufHz: Long,
        lufHz: Long,
    ) = ModeledPathEstimate(
        evidenceId = id,
        source = modelSource(999_000),
        observedAtUtcMillis = observedAt,
        confidence = PropagationConfidence(
            value = 0.75,
            basis = PropagationConfidenceBasis.MODEL_OUTPUT,
            explanation = "Deterministic synthetic model confidence",
        ),
        quality = setOf(PropagationDataQuality.SYNTHETIC),
        origin = origin,
        destination = destination,
        maximumUsableFrequencyHz = mufHz,
        lowestUsableFrequencyHz = lufHz,
        modelInputsSummary = "Deterministic synthetic CP-0008A inputs only",
    )

    private fun syntheticConfidence(value: Double) = PropagationConfidence(
        value = value,
        basis = PropagationConfidenceBasis.SYNTHETIC,
        explanation = "Deterministic synthetic CI confidence",
    )

    private fun gridPosition(grid: String) = PropagationPosition(
        maidenheadGrid = grid,
        method = PropagationLocationMethod.SYNTHETIC_FIXTURE,
        sourceReference = "cp0008a-fixture",
    )

    private fun coordinatePosition(latitude: Double, longitude: Double) = PropagationPosition(
        coordinate = GeoCoordinate(longitude = longitude, latitude = latitude),
        method = PropagationLocationMethod.SYNTHETIC_FIXTURE,
        sourceReference = "cp0008a-fixture",
    )

    private fun endpoint(grid: String, callsign: String) =
        PropagationEndpoint(gridPosition(grid), callsign = callsign)

    private fun query(
        frequencyHz: Long,
        band: String,
        now: Long,
        policy: PropagationFreshnessPolicy = PropagationFreshnessPolicy.OPERATIONAL,
    ) = PropagationAssessmentQuery(
        band = band,
        frequencyHz = frequencyHz,
        origin = gridPosition("JO40"),
        destination = gridPosition("FN31"),
        nowUtcMillis = now,
        freshnessPolicy = policy,
    )

    private fun classify(
        evidence: Long,
        now: Long,
        policy: PropagationFreshnessPolicy,
    ) = PropagationFreshnessClassifier.classify(evidence, now, policy)

    private fun PropagationPathAssessment.has(code: PropagationAssessmentReasonCode): Boolean =
        reasons.any { it.code == code }
}
