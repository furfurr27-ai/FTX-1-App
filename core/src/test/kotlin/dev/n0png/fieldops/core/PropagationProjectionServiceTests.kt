package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.map.GeoBounds
import dev.n0png.fieldops.core.map.GeoCoordinate
import dev.n0png.fieldops.core.propagation.*
import java.nio.file.Files

object PropagationProjectionServiceTests {
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
        projectsAllEvidenceWithoutCategoryCollapse()
        filtersHeardPathsDeterministically()
        filtersCommonSourceAndFreshness()
        preservesIonosphericMetricWithoutPrediction()
        selectedAssessmentRetainsReasonLinks()
        snapshotSelectionUsesStoreContract()
        offlineCacheStatusIsExplicit()
        futureAndStaleStatusRemainVisible()
        deterministicOrderingDoesNotDependOnInputOrder()
        validatesQueryAndFilterInputs()
        platformAndQsoSeparation()

        println("CP-0008F propagation projection tests: PASS assertions=$assertions")
    }

    private fun projectsAllEvidenceWithoutCategoryCollapse() {
        val snapshot = snapshot()
        val picture = PropagationWorkspaceProjectionService(
            InMemoryPropagationSnapshotStore().also { it.save(snapshot) }
        ).latest(PropagationProjectionQuery(nowUtcMillis = 1_000_000))!!

        eq("snapshot-main", picture.status.snapshotId, "snapshot identity preserved")
        eq(2, picture.heardPaths.size, "heard paths projected separately")
        eq(1, picture.ionosphericProducts.size, "ionospheric product projected separately")
        eq(2, picture.solarGeomagnetic.size, "solar context projected separately")
        eq(1, picture.modeledPaths.size, "model projected separately")
        eq(6, picture.projectedEvidenceCount, "projection count includes four categories")
        eq(5, picture.status.sourceCount, "source count preserved")
        checkThat(!picture.status.offlineCacheAvailable, "in-memory source not presented as offline cache")

        val heard = picture.heardPaths.first { it.metadata.evidenceId == "heard-20m-ft8" }
        eq("N0PNG", heard.transmitter.normalizedCallsign, "transmitter callsign retained")
        eq("K1ABC", heard.receiver.normalizedCallsign, "receiver callsign retained")
        eq("JO40", heard.transmitter.location.normalizedGrid, "origin grid retained")
        eq("FN31", heard.receiver.location.normalizedGrid, "destination grid retained")
        eq(14_074_000L, heard.frequencyHz, "exact heard frequency retained")
        eq("20m", heard.band, "band retained")
        eq("FT8", heard.mode, "mode retained")
        eq(-8.0, heard.snrDb, "SNR retained")
        eq(3, heard.reportCount, "report count retained")
        eq("PSK_REPORTER_PUBLIC_QUERY", heard.metadata.source.sourceId, "source provenance retained")
        eq(PropagationFreshness.FRESH, heard.metadata.freshness, "source-aware freshness projected")
        eq(1_000L, heard.metadata.retrievalAgeMillis, "retrieval age explicit")

        val model = picture.modeledPaths.single()
        eq(18_000_000L, model.maximumUsableFrequencyHz, "mode MUF preserved")
        eq(5_000_000L, model.lowestUsableFrequencyHz, "model LUF preserved")
        checkThat(
            model.modelInputsSummary.contains("synthetic"),
            "model input summary remains inspectable",
        )
    }

    private fun filtersHeardPathsDeterministically() {
        val service = service(snapshot())

        val twentyFt8 = service.latest(
            PropagationProjectionQuery(
                nowUtcMillis = 1_000_000,
                filter = PropagationProjectionFilter(
                    bands = setOf(" 20M "),
                    modes = setOf("ft8"),
                    minimumFrequencyHz = 14_000_000,
                    maximumFrequencyHz = 14_350_000,
                ),
            )
        )!!
        eq(listOf("heard-20m-ft8"), twentyFt8.heardPaths.map { it.metadata.evidenceId }, "band/mode/frequency filters normalize")
        eq(1, twentyFt8.ionosphericProducts.size, "heard-only filters do not discard ionospheric context")
        eq(2, twentyFt8.solarGeomagnetic.size, "heard-only filters do not discard solar context")
        eq(1, twentyFt8.modeledPaths.size, "heard-only filters do not rewrite model evidence")

        val forty = service.latest(
            PropagationProjectionQuery(
                nowUtcMillis = 1_000_000,
                filter = PropagationProjectionFilter(bands = setOf("40m")),
            )
        )!!
        eq(listOf("heard-40m-js8"), forty.heardPaths.map { it.metadata.evidenceId }, "band filter selects exact normalized band")

        val js8 = service.latest(
            PropagationProjectionQuery(
                nowUtcMillis = 1_000_000,
                filter = PropagationProjectionFilter(modes = setOf("Js8")),
            )
        )!!
        eq(listOf("heard-40m-js8"), js8.heardPaths.map { it.metadata.evidenceId }, "mode filter case-normalized")

        val narrow = service.latest(
            PropagationProjectionQuery(
                nowUtcMillis = 1_000_000,
                filter = PropagationProjectionFilter(
                    minimumFrequencyHz = 14_074_001,
                    maximumFrequencyHz = 14_074_100,
                ),
            )
        )!!
        eq(0, narrow.heardPaths.size, "frequency bounds are inclusive and exact")
    }

    private fun filtersCommonSourceAndFreshness() {
        val snapshot = snapshot()
        val service = service(snapshot)

        val pskOnly = service.latest(
            PropagationProjectionQuery(
                nowUtcMillis = 1_000_000,
                filter = PropagationProjectionFilter(
                    sourceIds = setOf("PSK_REPORTER_PUBLIC_QUERY"),
                ),
            )
        )!!
        eq(2, pskOnly.heardPaths.size, "source filter retains PSK heard paths")
        eq(0, pskOnly.ionosphericProducts.size, "source filter removes other ionospheric provider")
        eq(0, pskOnly.solarGeomagnetic.size, "source filter removes solar providers")
        eq(0, pskOnly.modeledPaths.size, "source filter removes model provider")

        val staleFixture = snapshot.copy(
            heardPaths = snapshot.heardPaths.map {
                if (it.evidenceId == "heard-40m-js8") it.copy(observedAtUtcMillis = 0L) else it
            }
        )
        val staleOnly = service(staleFixture).latest(
            PropagationProjectionQuery(
                nowUtcMillis = 4_000_001,
                filter = PropagationProjectionFilter(
                    freshness = setOf(PropagationFreshness.STALE),
                ),
            )
        )!!
        eq(listOf("heard-40m-js8"), staleOnly.heardPaths.map { it.metadata.evidenceId }, "stale PSK path filter uses PSK freshness policy")
        eq(0, staleOnly.ionosphericProducts.size, "fresh ionospheric product excluded from stale filter")
        eq(0, staleOnly.modeledPaths.size, "fresh model excluded from stale filter")

        val freshOnly = service.latest(
            PropagationProjectionQuery(
                nowUtcMillis = 1_000_000,
                filter = PropagationProjectionFilter(
                    freshness = setOf(PropagationFreshness.FRESH),
                ),
            )
        )!!
        checkThat(
            freshOnly.heardPaths.any { it.metadata.evidenceId == "heard-20m-ft8" },
            "fresh PSK path included",
        )
        checkThat(
            freshOnly.solarGeomagnetic.any { it.metadata.evidenceId == "solar-kp" },
            "fresh NOAA Kp included using source-specific longer policy",
        )
    }

    private fun preservesIonosphericMetricWithoutPrediction() {
        val picture = service(snapshot()).latest(
            PropagationProjectionQuery(nowUtcMillis = 1_000_000)
        )!!
        val iono = picture.ionosphericProducts.single()
        eq(IonosphericMetric.VTEC_TECU, iono.metric, "VTEC identity preserved")
        eq(2, iono.samples.size, "all raw bounded samples retained")
        eq(17.2, iono.samples.first().value, "sample value retained")
        eq(null, iono.referenceDistanceKm, "VTEC does not acquire MUF reference distance")
        eq(null, iono.generatedAtUtcMillis, "missing provider generation time remains missing")
        checkThat(
            picture.modeledPaths.none { it.metadata.evidenceId == iono.metadata.evidenceId },
            "ionospheric product is not converted into model path",
        )
    }

    private fun selectedAssessmentRetainsReasonLinks() {
        val now = 1_000_000L
        val selected = PropagationAssessmentQuery(
            band = "20m",
            frequencyHz = 14_074_000,
            origin = grid("JO40"),
            destination = grid("FN31"),
            nowUtcMillis = now,
        )
        val picture = service(snapshot()).latest(
            PropagationProjectionQuery(
                nowUtcMillis = now,
                filter = PropagationProjectionFilter(bands = setOf("40m")),
                selectedPath = selected,
            )
        )!!
        val assessment = picture.selectedPathAssessment!!
        eq(PropagationUsability.GOOD, assessment.usability, "selected path uses existing assessment engine")
        checkThat(
            assessment.reasons.any { it.code == PropagationAssessmentReasonCode.RECENT_OBSERVED_PATH },
            "assessment reason retained",
        )
        checkThat("heard-20m-ft8" in assessment.evidenceIds, "assessment evidence links retained")
        eq(listOf("heard-40m-js8"), picture.heardPaths.map { it.metadata.evidenceId }, "display filter remains independent from selected assessment")
    }

    private fun snapshotSelectionUsesStoreContract() {
        val store = InMemoryPropagationSnapshotStore()
        val older = snapshot().copy(snapshotId = "older", capturedAtUtcMillis = 900_000)
        val newer = snapshot().copy(snapshotId = "newer", capturedAtUtcMillis = 1_000_000)
        store.save(newer)
        store.save(older)
        val service = PropagationWorkspaceProjectionService(store)
        val query = PropagationProjectionQuery(nowUtcMillis = 1_100_000)

        eq("newer", service.latest(query)!!.status.snapshotId, "latest store snapshot selected")
        eq("older", service.snapshot("older", query)!!.status.snapshotId, "exact snapshot selected")
        eq(null, service.snapshot("missing", query), "missing exact snapshot returns null")
        eq("older", service.latestAtOrBefore(950_000, query)!!.status.snapshotId, "as-of snapshot selected")
        eq(null, service.latestAtOrBefore(899_999, query), "as-of before history returns null")
    }

    private fun offlineCacheStatusIsExplicit() {
        val dir = Files.createTempDirectory("fieldops-cp0008f-cache-")
        val store = FilePropagationSnapshotStore(dir, maxSnapshots = 3)
        store.save(snapshot())
        val picture = PropagationWorkspaceProjectionService(store).latest(
            PropagationProjectionQuery(nowUtcMillis = 1_000_000)
        )!!
        checkThat(picture.status.offlineCacheAvailable, "file-backed store exposes offline cache availability")

        val restarted = FilePropagationSnapshotStore(dir, maxSnapshots = 3)
        val reload = PropagationWorkspaceProjectionService(restarted).latest(
            PropagationProjectionQuery(nowUtcMillis = 1_000_000)
        )!!
        eq(picture, reload, "projection is stable after offline cache restart")
    }

    private fun futureAndStaleStatusRemainVisible() {
        val base = snapshot()
        val future = base.copy(
            snapshotId = "future",
            capturedAtUtcMillis = 1_100_000,
            heardPaths = base.heardPaths.mapIndexed { index, path ->
                if (index == 0) {
                    path.copy(
                        observedAtUtcMillis = 1_050_000,
                        source = path.source.copy(retrievedAtUtcMillis = 1_080_000),
                    )
                } else path
            },
        )
        val picture = PropagationWorkspaceProjectionService(InMemoryPropagationSnapshotStore()).project(
            future,
            PropagationProjectionQuery(nowUtcMillis = 1_000_000),
        )
        checkThat(picture.status.snapshotIsFutureDated, "future snapshot remains explicitly future-dated")
        eq(null, picture.status.snapshotAgeMillis, "future snapshot does not expose negative age")
        checkThat(picture.status.containsFutureDatedEvidence, "future evidence surfaced in status")
        val futurePath = picture.heardPaths.first { it.metadata.evidenceId == "heard-20m-ft8" }
        checkThat(futurePath.metadata.retrievalIsFutureDated, "future retrieval surfaced")
        eq(null, futurePath.metadata.retrievalAgeMillis, "future retrieval does not expose negative age")
        checkThat(picture.status.containsStaleEvidence, "stale evidence remains visible alongside future evidence")
    }

    private fun deterministicOrderingDoesNotDependOnInputOrder() {
        val a = snapshot()
        val b = a.copy(
            heardPaths = a.heardPaths.reversed(),
            solarGeomagnetic = a.solarGeomagnetic.reversed(),
            ionosphericProducts = a.ionosphericProducts.reversed(),
            modeledPaths = a.modeledPaths.reversed(),
        )
        val query = PropagationProjectionQuery(nowUtcMillis = 1_000_000)
        val service = PropagationWorkspaceProjectionService(InMemoryPropagationSnapshotStore())
        eq(service.project(a, query), service.project(b, query), "projection ordering deterministic")
        eq(
            listOf("heard-20m-ft8", "heard-40m-js8"),
            service.project(a, query).heardPaths.map { it.metadata.evidenceId },
            "heard paths newest-first then id",
        )
    }

    private fun validatesQueryAndFilterInputs() {
        expectFailure("negative projection time") { PropagationProjectionQuery(-1) }
        expectFailure("blank band filter") { PropagationProjectionFilter(bands = setOf(" ")) }
        expectFailure("blank mode filter") { PropagationProjectionFilter(modes = setOf("")) }
        expectFailure("blank source filter") { PropagationProjectionFilter(sourceIds = setOf(" ")) }
        expectFailure("zero minimum frequency") { PropagationProjectionFilter(minimumFrequencyHz = 0) }
        expectFailure("zero maximum frequency") { PropagationProjectionFilter(maximumFrequencyHz = 0) }
        expectFailure("reversed frequency range") {
            PropagationProjectionFilter(minimumFrequencyHz = 20, maximumFrequencyHz = 10)
        }
        expectFailure("empty freshness filter") { PropagationProjectionFilter(freshness = emptySet()) }
        expectFailure("selected path time mismatch") {
            PropagationProjectionQuery(
                nowUtcMillis = 1_000,
                selectedPath = PropagationAssessmentQuery(
                    band = "20m",
                    frequencyHz = 14_074_000,
                    origin = grid("JO40"),
                    destination = grid("FN31"),
                    nowUtcMillis = 999,
                ),
            )
        }
        expectFailure("blank exact snapshot id") {
            service(snapshot()).snapshot(" ", PropagationProjectionQuery(1_000_000))
        }
        expectFailure("negative as-of time") {
            service(snapshot()).latestAtOrBefore(-1, PropagationProjectionQuery(1_000_000))
        }
    }

    private fun platformAndQsoSeparation() {
        val classes = listOf(
            PropagationProjectionFilter::class.java,
            PropagationProjectionMetadata::class.java,
            PropagationWorkspaceProjection::class.java,
            PropagationWorkspaceProjectionService::class.java,
        )
        val fields = classes.flatMap { it.declaredFields.toList() }
        checkThat(
            fields.none {
                it.type.name.startsWith("android.") ||
                    it.type.name.startsWith("androidx.") ||
                    it.type.name.contains("GoogleMap") ||
                    it.type.name.contains("Mapbox")
            },
            "projection layer has no Android/map-SDK field types",
        )
        checkThat(
            fields.none {
                it.name.contains("qso", ignoreCase = true) ||
                    it.name.contains("lotw", ignoreCase = true) ||
                    it.name.contains("heat", ignoreCase = true)
            },
            "projection does not collapse heard evidence into QSO/LoTW/heat-score state",
        )
    }

    private fun service(snapshot: PropagationSnapshot): PropagationWorkspaceProjectionService {
        val store = InMemoryPropagationSnapshotStore()
        store.save(snapshot)
        return PropagationWorkspaceProjectionService(store)
    }

    private fun snapshot(): PropagationSnapshot =
        PropagationSnapshot(
            snapshotId = "snapshot-main",
            capturedAtUtcMillis = 1_000_000,
            solarGeomagnetic = listOf(
                SolarGeomagneticObservation(
                    evidenceId = "solar-kp",
                    source = source(
                        "NOAA_SWPC_PLANETARY_KP",
                        PropagationSourceClass.DERIVED_PRODUCT,
                        990_000,
                    ),
                    observedAtUtcMillis = 980_000,
                    confidence = confidence(0.9),
                    quality = setOf(PropagationDataQuality.PROVISIONAL),
                    planetaryKp = 2.0,
                ),
                SolarGeomagneticObservation(
                    evidenceId = "solar-f107",
                    source = source(
                        "NOAA_SWPC_F107_SUMMARY",
                        PropagationSourceClass.MEASUREMENT,
                        985_000,
                    ),
                    observedAtUtcMillis = 500_000,
                    confidence = confidence(0.9),
                    quality = setOf(PropagationDataQuality.PROVISIONAL),
                    f107SolarFluxSfu = 145.0,
                ),
            ),
            ionosphericProducts = listOf(
                IonosphericMapProduct(
                    evidenceId = "iono-vtec",
                    source = source(
                        "NOAA_SWPC_GLOTEC_VTEC",
                        PropagationSourceClass.DERIVED_PRODUCT,
                        995_000,
                    ),
                    observedAtUtcMillis = 990_000,
                    confidence = confidence(0.8),
                    quality = setOf(PropagationDataQuality.PROVISIONAL),
                    coverage = BoundsPropagationCoverage(GeoBounds(-10.0, 45.0, 10.0, 55.0)),
                    metric = IonosphericMetric.VTEC_TECU,
                    samples = listOf(
                        IonosphericSample(
                            position = coordinate(50.0, 8.0),
                            value = 17.2,
                            confidence = confidence(0.8),
                            providerQualityCode = 0,
                            providerQualityExplanation = "Provider quality zero",
                        ),
                        IonosphericSample(
                            position = coordinate(52.0, 2.0),
                            value = 14.1,
                            confidence = confidence(0.7),
                            providerQualityCode = 1,
                            providerQualityExplanation = "Provider quality one",
                        ),
                    ),
                    generatedAtUtcMillis = null,
                ),
            ),
            heardPaths = listOf(
                HeardPathObservation(
                    evidenceId = "heard-40m-js8",
                    source = source(
                        "PSK_REPORTER_PUBLIC_QUERY",
                        PropagationSourceClass.MEASUREMENT,
                        800_000,
                    ),
                    observedAtUtcMillis = 800_000,
                    confidence = confidence(0.9),
                    quality = setOf(PropagationDataQuality.PROVISIONAL),
                    transmitter = PropagationEndpoint(grid("JO40"), "N0PNG"),
                    receiver = PropagationEndpoint(grid("EM10"), "W5XYZ"),
                    frequencyHz = 7_078_000,
                    band = "40m",
                    mode = "JS8",
                    snrDb = -12.0,
                    reportCount = 1,
                ),
                HeardPathObservation(
                    evidenceId = "heard-20m-ft8",
                    source = source(
                        "PSK_REPORTER_PUBLIC_QUERY",
                        PropagationSourceClass.MEASUREMENT,
                        999_000,
                    ),
                    observedAtUtcMillis = 997_000,
                    confidence = confidence(0.95),
                    quality = setOf(PropagationDataQuality.PROVISIONAL),
                    transmitter = PropagationEndpoint(grid("JO40"), "N0PNG"),
                    receiver = PropagationEndpoint(grid("FN31"), "K1ABC"),
                    frequencyHz = 14_074_000,
                    band = "20m",
                    mode = "FT8",
                    snrDb = -8.0,
                    reportCount = 3,
                ),
            ),
            modeledPaths = listOf(
                ModeledPathEstimate(
                    evidenceId = "model-path",
                    source = source(
                        "SYNTH_MODEL_CP0008F",
                        PropagationSourceClass.MODEL,
                        990_000,
                    ),
                    observedAtUtcMillis = 992_000,
                    confidence = PropagationConfidence(
                        0.75,
                        PropagationConfidenceBasis.MODEL_OUTPUT,
                        "Synthetic model confidence",
                    ),
                    quality = setOf(PropagationDataQuality.SYNTHETIC),
                    origin = grid("JO40"),
                    destination = grid("FN31"),
                    maximumUsableFrequencyHz = 18_000_000,
                    lowestUsableFrequencyHz = 5_000_000,
                    modelInputsSummary = "Deterministic synthetic model inputs",
                ),
            ),
        )

    private fun source(
        id: String,
        sourceClass: PropagationSourceClass,
        retrievedAt: Long,
    ) = PropagationSourceRef(
        sourceId = id,
        providerName = id,
        sourceClass = sourceClass,
        sourceVersion = "cp0008f-test-v1",
        retrievedAtUtcMillis = retrievedAt,
        sourceUrl = "https://example.invalid/$id",
    )

    private fun confidence(value: Double) =
        PropagationConfidence(
            value = value,
            basis = PropagationConfidenceBasis.PROVIDER_REPORTED,
            explanation = "Deterministic test confidence",
        )

    private fun grid(value: String) =
        PropagationPosition(
            maidenheadGrid = value,
            method = PropagationLocationMethod.EXPLICIT_GRID,
            sourceReference = "cp0008f-test",
        )

    private fun coordinate(latitude: Double, longitude: Double) =
        PropagationPosition(
            coordinate = GeoCoordinate(longitude = longitude, latitude = latitude),
            method = PropagationLocationMethod.PROVIDER_COORDINATE,
            sourceReference = "cp0008f-test",
        )
}
