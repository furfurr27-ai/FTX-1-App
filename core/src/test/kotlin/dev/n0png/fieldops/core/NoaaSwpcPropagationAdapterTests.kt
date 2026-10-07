package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.NoaaSwpcPropagationAdapter
import dev.n0png.fieldops.core.propagation.NoaaSwpcRecordStatus
import dev.n0png.fieldops.core.propagation.PropagationConfidenceBasis
import dev.n0png.fieldops.core.propagation.PropagationDataQuality
import dev.n0png.fieldops.core.propagation.PropagationSourceClass
import java.nio.file.Files
import java.nio.file.Path

object NoaaSwpcPropagationAdapterTests {
    private var assertions = 0

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1) { "Expected repository root path" }
        val root = Path.of(args[0])

        endpointPins()
        observedKpFixture(root)
        statusAwareKpFixture(root)
        f107Fixture(root)
        failClosedParsing()
        providerTimestampSemantics()
        transportIndependence()

        println("CP-0008B NOAA SWPC adapter tests: " + assertions + "/" + assertions + " PASS")
    }

    private fun endpointPins() {
        eq(
            "https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json",
            NoaaSwpcPropagationAdapter.PLANETARY_KP_URL,
            "historical Kp endpoint pin",
        )
        eq(
            "https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json",
            NoaaSwpcPropagationAdapter.PLANETARY_KP_FORECAST_URL,
            "Kp status/forecast endpoint pin",
        )
        eq(
            "https://services.swpc.noaa.gov/products/summary/10cm-flux.json",
            NoaaSwpcPropagationAdapter.F107_SUMMARY_URL,
            "F10.7 endpoint pin",
        )
        eq(
            "swpc-json-post-scn26-21-v1",
            NoaaSwpcPropagationAdapter.SOURCE_VERSION,
            "schema version pin",
        )
    }

    private fun observedKpFixture(root: Path) {
        val records = NoaaSwpcPropagationAdapter.parsePlanetaryKp(
            json = fixture(root, "noaa_swpc_planetary_kp_sample.json"),
            retrievedAtUtcMillis = 1_791_360_000_000,
        )

        eq(3, records.size, "three historical Kp rows parsed")
        eq(
            listOf(1.0, 1.33, 1.0),
            records.map { it.observation.planetaryKp },
            "historical Kp values retained",
        )
        eq(
            listOf(8, 8, 8),
            records.map { it.stationCount },
            "station counts retained",
        )
        eq(
            listOf(4.0, 5.0, 4.0),
            records.map { it.runningA },
            "running-a provider field retained but not remapped",
        )
        checkThat(records.all { it.status == NoaaSwpcRecordStatus.OBSERVED }, "historical rows observed")
        checkThat(
            records.all { it.observation.source.sourceClass == PropagationSourceClass.DERIVED_PRODUCT },
            "planetary Kp modeled as derived provider product",
        )
        checkThat(
            records.all { it.observation.quality == setOf(PropagationDataQuality.PROVISIONAL) },
            "rolling historical Kp kept provisional",
        )
        checkThat(
            records.all { it.observation.confidence.basis == PropagationConfidenceBasis.PROVIDER_REPORTED },
            "Kp confidence basis provider reported",
        )
        checkThat(
            records.all { it.observation.planetaryAp == null },
            "running-a not silently promoted to planetary Ap",
        )
        eq(
            1_791_331_200_000,
            records.first().observation.observedAtUtcMillis,
            "provider Kp timestamp converted as UTC",
        )
        eq(
            1_791_360_000_000,
            records.first().observation.source.retrievedAtUtcMillis,
            "retrieval timestamp retained separately",
        )
        checkThat(
            records.map { it.observation.evidenceId }.distinct().size == records.size,
            "historical Kp evidence ids deterministic and unique",
        )
    }

    private fun statusAwareKpFixture(root: Path) {
        val records = NoaaSwpcPropagationAdapter.parsePlanetaryKpForecast(
            json = fixture(root, "noaa_swpc_planetary_kp_forecast_sample.json"),
            retrievedAtUtcMillis = 1_791_360_000_000,
        )

        eq(3, records.size, "three status-aware Kp rows parsed")
        eq(
            listOf(
                NoaaSwpcRecordStatus.OBSERVED,
                NoaaSwpcRecordStatus.ESTIMATED,
                NoaaSwpcRecordStatus.PREDICTED,
            ),
            records.map { it.status },
            "provider status distinctions preserved",
        )
        eq(
            listOf(
                PropagationSourceClass.DERIVED_PRODUCT,
                PropagationSourceClass.DERIVED_PRODUCT,
                PropagationSourceClass.FORECAST,
            ),
            records.map { it.observation.source.sourceClass },
            "predicted record remains forecast provenance",
        )
        checkThat(
            PropagationDataQuality.ESTIMATED in records[1].observation.quality,
            "estimated record quality explicit",
        )
        checkThat(
            PropagationDataQuality.ESTIMATED in records[2].observation.quality,
            "predicted record quality explicit",
        )
        checkThat(
            records[0].observation.confidence.value >
                records[1].observation.confidence.value,
            "observed confidence exceeds estimated confidence",
        )
        checkThat(
            records[1].observation.confidence.value >
                records[2].observation.confidence.value,
            "estimated confidence exceeds predicted confidence",
        )
        eq(
            1_791_417_600_000,
            records[2].observation.observedAtUtcMillis,
            "future prediction validity time retained from provider",
        )
        eq(
            1_791_360_000_000,
            records[2].observation.source.retrievedAtUtcMillis,
            "future prediction retrieval time remains separate",
        )
        checkThat(
            records[2].observation.observedAtUtcMillis >
                records[2].observation.source.retrievedAtUtcMillis,
            "forecast validity time may follow retrieval without substitution",
        )
        checkThat(
            records.none { it.observation.source.sourceClass == PropagationSourceClass.MEASUREMENT },
            "Kp forecast feed never masquerades as direct measurement",
        )
    }

    private fun f107Fixture(root: Path) {
        val records = NoaaSwpcPropagationAdapter.parseF107Summary(
            json = fixture(root, "noaa_swpc_f107_summary_sample.json"),
            retrievedAtUtcMillis = 1_791_360_000_000,
        )

        eq(1, records.size, "one F10.7 summary row parsed")
        val record = records.single()
        eq(NoaaSwpcRecordStatus.OBSERVED, record.status, "F10.7 summary is observed")
        eq(113.0, record.observation.f107SolarFluxSfu, "F10.7 numeric value retained in sfu")
        eq(
            PropagationSourceClass.MEASUREMENT,
            record.observation.source.sourceClass,
            "F10.7 remains a measurement source",
        )
        eq(
            1_791_316_800_000,
            record.observation.observedAtUtcMillis,
            "F10.7 provider timestamp converted as UTC",
        )
        eq(
            NoaaSwpcPropagationAdapter.F107_SUMMARY_URL,
            record.observation.source.sourceUrl,
            "F10.7 provenance URL retained",
        )
        checkThat(
            record.observation.planetaryKp == null,
            "F10.7 record does not fabricate Kp context",
        )
        checkThat(
            record.observation.quality == setOf(PropagationDataQuality.PROVISIONAL),
            "current F10.7 summary kept provisional",
        )
    }

    private fun failClosedParsing() {
        expectFailure("missing Kp field rejected") {
            NoaaSwpcPropagationAdapter.parsePlanetaryKp(
                """[{"time_tag":"2026-10-07T00:00:00","a_running":4,"station_count":8}]""",
                1_791_360_000_000,
            )
        }
        expectFailure("quoted post-SCN numeric Kp rejected") {
            NoaaSwpcPropagationAdapter.parsePlanetaryKp(
                """[{"time_tag":"2026-10-07T00:00:00","Kp":"1.0","a_running":4,"station_count":8}]""",
                1_791_360_000_000,
            )
        }
        expectFailure("unknown Kp status rejected") {
            NoaaSwpcPropagationAdapter.parsePlanetaryKpForecast(
                """[{"time_tag":"2026-10-07T00:00:00","kp":1.0,"observed":"maybe","noaa_scale":null}]""",
                1_791_360_000_000,
            )
        }
        expectFailure("null Kp rejected") {
            NoaaSwpcPropagationAdapter.parsePlanetaryKpForecast(
                """[{"time_tag":"2026-10-07T00:00:00","kp":null,"observed":"observed","noaa_scale":null}]""",
                1_791_360_000_000,
            )
        }
        expectFailure("out-of-domain Kp rejected") {
            NoaaSwpcPropagationAdapter.parsePlanetaryKpForecast(
                """[{"time_tag":"2026-10-07T00:00:00","kp":9.5,"observed":"observed","noaa_scale":null}]""",
                1_791_360_000_000,
            )
        }
        expectFailure("invalid NOAA G scale rejected") {
            NoaaSwpcPropagationAdapter.parsePlanetaryKpForecast(
                """[{"time_tag":"2026-10-07T00:00:00","kp":5.0,"observed":"observed","noaa_scale":"G7"}]""",
                1_791_360_000_000,
            )
        }
        expectFailure("quoted F10.7 numeric rejected") {
            NoaaSwpcPropagationAdapter.parseF107Summary(
                """[{"flux":"113","time_tag":"2026-10-06T20:00:00"}]""",
                1_791_360_000_000,
            )
        }
        expectFailure("nonpositive F10.7 rejected by normalized domain") {
            NoaaSwpcPropagationAdapter.parseF107Summary(
                """[{"flux":0,"time_tag":"2026-10-06T20:00:00"}]""",
                1_791_360_000_000,
            )
        }
        expectFailure("extra provider field rejected to expose schema drift") {
            NoaaSwpcPropagationAdapter.parseF107Summary(
                """[{"flux":113,"time_tag":"2026-10-06T20:00:00","new_field":1}]""",
                1_791_360_000_000,
            )
        }
        expectFailure("malformed timestamp rejected") {
            NoaaSwpcPropagationAdapter.parseF107Summary(
                """[{"flux":113,"time_tag":"not-a-time"}]""",
                1_791_360_000_000,
            )
        }
        expectFailure("duplicate JSON key rejected") {
            NoaaSwpcPropagationAdapter.parseF107Summary(
                """[{"flux":113,"flux":114,"time_tag":"2026-10-06T20:00:00"}]""",
                1_791_360_000_000,
            )
        }
        expectFailure("trailing JSON content rejected") {
            NoaaSwpcPropagationAdapter.parseF107Summary(
                """[{"flux":113,"time_tag":"2026-10-06T20:00:00"}] garbage""",
                1_791_360_000_000,
            )
        }
        expectFailure("negative retrieval timestamp rejected") {
            NoaaSwpcPropagationAdapter.parseF107Summary(
                """[{"flux":113,"time_tag":"2026-10-06T20:00:00"}]""",
                -1,
            )
        }
    }

    private fun providerTimestampSemantics() {
        val withZulu = NoaaSwpcPropagationAdapter.parseF107Summary(
            """[{"flux":113,"time_tag":"2026-10-06T20:00:00Z"}]""",
            1_791_360_000_000,
        ).single()
        eq(
            1_791_316_800_000,
            withZulu.observation.observedAtUtcMillis,
            "explicit Z timestamp supported without device time",
        )

        val future = NoaaSwpcPropagationAdapter.parsePlanetaryKpForecast(
            """[{"time_tag":"2026-10-10T00:00:00","kp":3.33,"observed":"predicted","noaa_scale":null}]""",
            1_791_360_000_000,
        ).single()
        checkThat(
            future.observation.observedAtUtcMillis > future.observation.source.retrievedAtUtcMillis,
            "future provider validity survives normalization",
        )
        eq(
            PropagationSourceClass.FORECAST,
            future.observation.source.sourceClass,
            "future validity remains forecast provenance",
        )
    }

    private fun transportIndependence() {
        val fields = NoaaSwpcPropagationAdapter::class.java.declaredFields.toList()
        checkThat(
            fields.none {
                it.type.name.startsWith("android.") ||
                    it.type.name.startsWith("androidx.")
            },
            "adapter carries no Android fields",
        )

        val methods = NoaaSwpcPropagationAdapter::class.java.declaredMethods
        checkThat(
            methods.none {
                it.returnType.name.contains("Qso", ignoreCase = true) ||
                    it.returnType.name.contains("PropagationPathAssessment")
            },
            "adapter exposes observations only, not QSOs or path scores",
        )
    }

    private fun fixture(root: Path, name: String): String =
        Files.readString(root.resolve("research/propagation/fixtures").resolve(name))

    private fun eq(expected: Any?, actual: Any?, label: String) {
        assertions++
        check(expected == actual) {
            label + ": expected=" + expected + " actual=" + actual
        }
    }

    private fun checkThat(condition: Boolean, label: String) {
        assertions++
        check(condition) { label }
    }

    private fun expectFailure(label: String, block: () -> Unit) {
        assertions++
        var failed = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        check(failed) { "Expected failure: " + label }
    }
}
