package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.map.GeoBounds
import dev.n0png.fieldops.core.map.GeoCoordinate
import dev.n0png.fieldops.core.propagation.*
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

object NoaaSwpcGlotecAdapterTests {
    private var assertions = 0
    private lateinit var fixture: String
    private const val SOURCE_URL =
        "https://services.swpc.noaa.gov/products/glotec/geojson_2d_urt/glotec_icao_20260909T151500Z.geojson"
    private val RETRIEVED = Instant.parse("2026-09-09T15:40:00Z").toEpochMilli()

    private fun checkThat(value: Boolean, message: String) {
        assertions++
        check(value) { message }
    }

    private fun <T> eq(expected: T, actual: T, message: String) =
        checkThat(expected == actual, "$message expected=$expected actual=$actual")

    private fun close(expected: Double, actual: Double, message: String) =
        checkThat(kotlin.math.abs(expected - actual) < 1e-9, "$message expected=$expected actual=$actual")

    private fun expectFailure(message: String, block: () -> Unit) {
        assertions++
        check(runCatching(block).isFailure) { "Expected failure: $message" }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val root = Path.of(args.single())
        fixture = Files.readString(
            root.resolve("research/propagation/fixtures/noaa_swpc_glotec_20260909T151500Z_bounded.geojson")
        )

        parsesPinnedFixture()
        preservesCoordinatesAndProviderQuality()
        remainsContextNotPathPrediction()
        domainQualityPairValidation()
        failsClosedOnSourceAndTimeDrift()
        failsClosedOnRootAndMetadataDrift()
        failsClosedOnFeatureAndGeometryDrift()
        failsClosedOnValueAndQualityDrift()
        failsClosedOnJsonCorruption()
        platformAndStateSeparation()

        println("CP-0008C NOAA SWPC GloTEC adapter tests: PASS assertions=$assertions")
    }

    private fun parsesPinnedFixture() {
        val record = parse()
        eq("2026-09-09T15:15:00Z", record.providerTimeTag, "provider time retained")
        eq(10, record.cadenceMinutes, "ten-minute cadence retained")
        eq("TECU", record.tecUnits, "TEC units retained")
        eq(4, record.featureCount, "bounded fixture feature count retained")

        val product = record.product
        eq(IonosphericMetric.VTEC_TECU, product.metric, "VTEC metric explicit")
        eq(null, product.referenceDistanceKm, "TEC has no MUF reference distance")
        eq(null, product.generatedAtUtcMillis, "generation time not fabricated")
        eq(
            Instant.parse("2026-09-09T15:15:00Z").toEpochMilli(),
            product.observedAtUtcMillis,
            "provider time_tag becomes observation/validity UTC",
        )
        eq(RETRIEVED, product.source.retrievedAtUtcMillis, "retrieval UTC remains separate")
        eq("NOAA_SWPC_GLOTEC_VTEC", product.source.sourceId, "source id pinned")
        eq("NOAA SWPC GloTEC", product.source.providerName, "provider name pinned")
        eq(
            PropagationSourceClass.DERIVED_PRODUCT,
            product.source.sourceClass,
            "GloTEC remains a derived product",
        )
        eq(
            "glotec-operational-geojson-v1",
            product.source.sourceVersion,
            "source schema version pinned",
        )
        eq(SOURCE_URL, product.source.sourceUrl, "exact source artifact retained")
        eq(
            PropagationConfidenceBasis.DERIVED,
            product.confidence.basis,
            "confidence is FieldOps derived policy",
        )
        eq(0.70, product.confidence.value, "confidence value retained")
        checkThat(
            PropagationDataQuality.ESTIMATED in product.quality,
            "assimilative map marked estimated rather than direct RF path",
        )
        eq(4, product.samples.size, "all bounded TEC samples retained")
    }

    private fun preservesCoordinatesAndProviderQuality() {
        val product = parse().product
        val samples = product.samples
        val first = samples[0]
        close(-177.5, first.position.coordinate!!.longitude, "GeoJSON longitude order retained")
        close(-88.75, first.position.coordinate!!.latitude, "GeoJSON latitude order retained")
        close(5.991683217188725, first.value, "TEC value retained exactly")
        eq(
            PropagationLocationMethod.PROVIDER_COORDINATE,
            first.position.method,
            "provider coordinate provenance retained",
        )
        checkThat(
            first.position.sourceReference!!.startsWith("NOAA_SWPC_GLOTEC:"),
            "position source reference retained",
        )
        eq(0, first.providerQualityCode, "quality flag zero preserved")
        checkThat(
            first.providerQualityExplanation!!.contains("F-region observation"),
            "provider quality semantics explained",
        )
        eq(listOf(0, 1, 5, 5), samples.map { it.providerQualityCode }, "quality flags preserved")
        eq(
            listOf(
                5.991683217188725,
                11.294521460140102,
                21.53414670593684,
                17.120824407721763,
            ),
            samples.map { it.value },
            "bounded TEC values retained",
        )

        val bounds = (product.coverage as BoundsPropagationCoverage).bounds
        close(-177.5, bounds.west, "bounded fixture west derived from coordinates")
        close(32.5, bounds.east, "bounded fixture east derived from coordinates")
        close(-88.75, bounds.south, "bounded fixture south derived from coordinates")
        close(53.75, bounds.north, "bounded fixture north derived from coordinates")
        checkThat(
            product.samples.all { it.position.maidenheadGrid == null },
            "GloTEC geography is not inferred through grid/callsign",
        )
    }

    private fun remainsContextNotPathPrediction() {
        val product = parse().product
        val snapshot = PropagationSnapshot(
            snapshotId = "glotec-only",
            capturedAtUtcMillis = Instant.parse("2026-09-09T15:41:00Z").toEpochMilli(),
            ionosphericProducts = listOf(product),
        )
        val assessment = PropagationAssessmentEngine().assess(
            snapshot,
            PropagationAssessmentQuery(
                band = "20m",
                frequencyHz = 14_074_000,
                origin = PropagationPosition(
                    coordinate = GeoCoordinate(8.0, 50.0),
                    method = PropagationLocationMethod.EXPLICIT_COORDINATE,
                ),
                destination = PropagationPosition(
                    coordinate = GeoCoordinate(-74.0, 41.0),
                    method = PropagationLocationMethod.EXPLICIT_COORDINATE,
                ),
                nowUtcMillis = Instant.parse("2026-09-09T15:20:00Z").toEpochMilli(),
            )
        )

        eq(PropagationUsability.UNKNOWN, assessment.usability, "GloTEC alone does not score a path")
        eq(null, assessment.confidence, "GloTEC context does not become path confidence")
        checkThat(
            assessment.reasons.any {
                it.code == PropagationAssessmentReasonCode.IONOSPHERIC_MAP_CONTEXT_AVAILABLE
            },
            "GloTEC appears only as ionospheric context",
        )
        checkThat(
            assessment.reasons.any {
                it.code == PropagationAssessmentReasonCode.INSUFFICIENT_PATH_EVIDENCE
            },
            "path still requires direct/model evidence",
        )
        checkThat(
            assessment.reasons.none {
                it.code == PropagationAssessmentReasonCode.MODEL_FREQUENCY_WITHIN_LIMITS ||
                    it.code == PropagationAssessmentReasonCode.RECENT_OBSERVED_PATH
            },
            "GloTEC does not masquerade as modeled or observed RF path",
        )
    }

    private fun domainQualityPairValidation() {
        val base = parse().product.samples.first()
        eq(0, base.providerQualityCode, "provider quality code available in neutral domain")
        expectFailure("quality code requires explanation") {
            base.copy(providerQualityExplanation = null)
        }
        expectFailure("quality explanation requires code") {
            base.copy(providerQualityCode = null)
        }
        expectFailure("quality code nonnegative") {
            base.copy(providerQualityCode = -1)
        }
        expectFailure("quality explanation nonblank") {
            base.copy(providerQualityExplanation = " ")
        }
        val neutral = base.copy(
            providerQualityCode = null,
            providerQualityExplanation = null,
        )
        eq(null, neutral.providerQualityCode, "provider quality remains optional for other providers")
    }

    private fun failsClosedOnSourceAndTimeDrift() {
        expectFailure("non-NOAA source rejected") {
            NoaaSwpcGlotecAdapter.parseGeoJson(
                fixture,
                RETRIEVED,
                "https://example.invalid/glotec_icao_20260909T151500Z.geojson",
            )
        }
        expectFailure("noncanonical filename rejected") {
            NoaaSwpcGlotecAdapter.parseGeoJson(
                fixture,
                RETRIEVED,
                NoaaSwpcGlotecAdapter.DIRECTORY_URL + "latest.geojson",
            )
        }
        expectFailure("query string rejected") {
            NoaaSwpcGlotecAdapter.parseGeoJson(
                fixture,
                RETRIEVED,
                SOURCE_URL + "?x=1",
            )
        }
        expectFailure("filename time must match time_tag") {
            NoaaSwpcGlotecAdapter.parseGeoJson(
                fixture,
                RETRIEVED,
                NoaaSwpcGlotecAdapter.DIRECTORY_URL +
                    "glotec_icao_20260909T152500Z.geojson",
            )
        }
        expectFailure("negative retrieval UTC rejected") {
            NoaaSwpcGlotecAdapter.parseGeoJson(fixture, -1, SOURCE_URL)
        }
        expectFailure("invalid provider timestamp rejected") {
            parse(fixture.replace("2026-09-09T15:15:00Z", "yesterday"))
        }
    }

    private fun failsClosedOnRootAndMetadataDrift() {
        expectFailure("wrong collection type rejected") {
            parse(fixture.replace("\"FeatureCollection\"", "\"Collection\""))
        }
        expectFailure("cadence drift rejected") {
            parse(fixture.replace("\"cadence\": 10", "\"cadence\": 5"))
        }
        expectFailure("quoted cadence rejected") {
            parse(fixture.replace("\"cadence\": 10", "\"cadence\": \"10\""))
        }
        expectFailure("TECU units pinned") {
            parse(fixture.replace("\"units\": \"TECU\"", "\"units\": \"MHz\""))
        }
        expectFailure("TEC metadata required") {
            parse(fixture.replace("\"tec\": {\"units\": \"TECU\"", "\"other\": {\"units\": \"TECU\""))
        }
        expectFailure("TEC metadata extra fields rejected") {
            parse(fixture.replace(
                "\"tec\": {\"units\": \"TECU\", \"min\": 0.0, \"max\": 300.0}",
                "\"tec\": {\"units\": \"TECU\", \"min\": 0.0, \"max\": 300.0, \"extra\": 1}"
            ))
        }
        expectFailure("TEC metadata min nonnegative") {
            parse(fixture.replace("\"min\": 0.0, \"max\": 300.0", "\"min\": -1.0, \"max\": 300.0"))
        }
        expectFailure("TEC metadata max exceeds min") {
            parse(fixture.replace("\"min\": 0.0, \"max\": 300.0", "\"min\": 300.0, \"max\": 300.0"))
        }
        expectFailure("unexpected root field rejected") {
            parse(fixture.replace("\"features\": [", "\"extra\": 1,\n  \"features\": ["))
        }
        expectFailure("empty features rejected") {
            val start = fixture.indexOf("\"features\": [")
            val prefix = fixture.substring(0, start)
            parse(prefix + "\"features\": []\n}\n")
        }
    }

    private fun failsClosedOnFeatureAndGeometryDrift() {
        expectFailure("feature type pinned") {
            parse(fixture.replaceFirst("\"type\": \"Feature\",", "\"type\": \"Cell\","))
        }
        expectFailure("geometry type pinned") {
            parse(fixture.replaceFirst("\"type\": \"Point\"", "\"type\": \"LineString\""))
        }
        expectFailure("coordinate array length pinned") {
            parse(fixture.replaceFirst("[-177.5, -88.75]", "[-177.5]"))
        }
        expectFailure("longitude domain enforced") {
            parse(fixture.replaceFirst("[-177.5, -88.75]", "[-181.0, -88.75]"))
        }
        expectFailure("latitude domain enforced") {
            parse(fixture.replaceFirst("[-177.5, -88.75]", "[-177.5, -91.0]"))
        }
        expectFailure("quoted coordinate rejected") {
            parse(fixture.replaceFirst("[-177.5, -88.75]", "[\"-177.5\", -88.75]"))
        }
        expectFailure("duplicate coordinates rejected") {
            parse(fixture.replaceFirst("[32.5, -73.75]", "[-177.5, -88.75]"))
        }
        expectFailure("unexpected geometry field rejected") {
            parse(fixture.replaceFirst(
                "\"coordinates\": [-177.5, -88.75]",
                "\"coordinates\": [-177.5, -88.75], \"extra\": 1"
            ))
        }
        expectFailure("unexpected feature field rejected") {
            parse(fixture.replaceFirst(
                "\"geometry\": {\"type\": \"Point\", \"coordinates\": [-177.5, -88.75]},",
                "\"geometry\": {\"type\": \"Point\", \"coordinates\": [-177.5, -88.75]}, \"extra\": 1,"
            ))
        }
    }

    private fun failsClosedOnValueAndQualityDrift() {
        expectFailure("quoted TEC rejected") {
            parse(fixture.replaceFirst("\"tec\": 5.991683217188725", "\"tec\": \"5.991683217188725\""))
        }
        expectFailure("zero TEC rejected by normalized sample contract") {
            parse(fixture.replaceFirst("\"tec\": 5.991683217188725", "\"tec\": 0.0"))
        }
        expectFailure("negative TEC rejected") {
            parse(fixture.replaceFirst("\"tec\": 5.991683217188725", "\"tec\": -1.0"))
        }
        expectFailure("TEC above metadata max rejected") {
            parse(fixture.replaceFirst("\"tec\": 5.991683217188725", "\"tec\": 301.0"))
        }
        expectFailure("negative quality flag rejected") {
            parse(fixture.replaceFirst("\"quality_flag\": 0", "\"quality_flag\": -1"))
        }
        expectFailure("quality flag above five rejected") {
            parse(fixture.replaceFirst("\"quality_flag\": 0", "\"quality_flag\": 6"))
        }
        expectFailure("quoted quality flag rejected") {
            parse(fixture.replaceFirst("\"quality_flag\": 0", "\"quality_flag\": \"0\""))
        }
        expectFailure("anomaly must remain numeric in pinned schema") {
            parse(fixture.replaceFirst(
                "\"anomaly\": 0.6210870643697008",
                "\"anomaly\": null"
            ))
        }
        expectFailure("hmF2 must remain numeric in pinned schema") {
            parse(fixture.replaceFirst(
                "\"hmF2\": 335.78443287826775",
                "\"hmF2\": \"335.7\""
            ))
        }
        expectFailure("NmF2 must remain numeric in pinned schema") {
            parse(fixture.replaceFirst(
                "\"NmF2\": 220761200216.95996",
                "\"NmF2\": null"
            ))
        }
        expectFailure("unexpected property rejected") {
            parse(fixture.replaceFirst(
                "\"quality_flag\": 0",
                "\"quality_flag\": 0, \"extra\": 1"
            ))
        }
    }

    private fun failsClosedOnJsonCorruption() {
        expectFailure("malformed JSON rejected") {
            parse(fixture.dropLast(2))
        }
        expectFailure("duplicate root key rejected") {
            parse(fixture.replaceFirst(
                "\"time_tag\": \"2026-09-09T15:15:00Z\",",
                "\"time_tag\": \"2026-09-09T15:15:00Z\",\n  \"time_tag\": \"2026-09-09T15:15:00Z\","
            ))
        }
        expectFailure("duplicate property key rejected") {
            parse(fixture.replaceFirst(
                "\"tec\": 5.991683217188725,",
                "\"tec\": 5.991683217188725, \"tec\": 5.991683217188725,"
            ))
        }
        expectFailure("trailing JSON rejected") {
            parse(fixture + "{}")
        }
    }

    private fun platformAndStateSeparation() {
        val adapterFields = NoaaSwpcGlotecAdapter::class.java.declaredFields
        checkThat(
            adapterFields.none {
                it.type.name.startsWith("android.") ||
                    it.type.name.startsWith("androidx.") ||
                    it.type.name.contains("GoogleMap") ||
                    it.type.name.contains("Mapbox")
            },
            "GloTEC adapter carries no Android/map-SDK state",
        )
        checkThat(
            NoaaSwpcGlotecRecord::class.java.declaredFields.none {
                it.name.contains("qso", ignoreCase = true) ||
                    it.name.contains("lotw", ignoreCase = true) ||
                    it.name.contains("callsign", ignoreCase = true)
            },
            "GloTEC record carries no QSO/LoTW/callsign state",
        )
        checkThat(
            IonosphericMetric.entries.contains(IonosphericMetric.VTEC_TECU),
            "provider-neutral metric explicitly supports VTEC TECU",
        )
        checkThat(
            IonosphericMetric.VTEC_TECU != IonosphericMetric.MUF_MHZ,
            "VTEC remains distinct from MUF",
        )
    }

    private fun parse(json: String = fixture): NoaaSwpcGlotecRecord =
        NoaaSwpcGlotecAdapter.parseGeoJson(
            json = json,
            retrievedAtUtcMillis = RETRIEVED,
            sourceUrl = SOURCE_URL,
        )
}
