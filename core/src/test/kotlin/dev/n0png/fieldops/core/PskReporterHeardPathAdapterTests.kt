package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*
import java.nio.file.Files
import java.nio.file.Path

object PskReporterHeardPathAdapterTests {
    private var assertions = 0
    private lateinit var fixture: String

    private const val SOURCE_URL =
        "https://retrieve.pskreporter.info/query?senderCallsign=AG6K&rronly=1&noactive=1&rptlimit=4"
    private const val RETRIEVED = 1_599_164_940_000L

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
        val root = Path.of(args.single())
        fixture = Files.readString(
            root.resolve("research/propagation/fixtures/psk_reporter_ag6k_20200903_bounded.xml")
        )

        parsesPinnedFixture()
        preservesHeardPathSemantics()
        preservesOptionalSnrAndDuplicates()
        explicitlyRejectsMissingGeography()
        explicitlyRejectsInvalidPathFacts()
        informationSourceSafety()
        documentAndSourceSafety()
        qsoPlatformAndTransportSeparation()

        println("CP-0008D PSK Reporter heard-path adapter tests: PASS assertions=$assertions")
    }

    private fun parsesPinnedFixture() {
        val result = parse()
        eq(4, result.responseReportCount, "fixture report count retained")
        eq(4, result.observations.size, "all complete reports accepted")
        eq(0, result.rejectedReports.size, "no complete fixture rows rejected")
        eq(1_599_164_934L, result.currentSeconds, "server currentSeconds retained")
        eq(14_631_964_162L, result.lastSequenceNumber, "last sequence retained")
        eq(1_599_164_931L, result.maxFlowStartSeconds, "max flow start retained")

        val first = result.observations.first()
        eq("AG6K", first.transmitter.normalizedCallsign, "sender callsign normalized")
        eq("W5CJ", first.receiver.normalizedCallsign, "receiver callsign normalized")
        eq("DM14CC24", first.transmitter.location.normalizedGrid, "sender grid normalized")
        eq("EM55DB92", first.receiver.location.normalizedGrid, "receiver grid normalized")
        eq(
            PropagationLocationMethod.EXPLICIT_GRID,
            first.transmitter.location.method,
            "sender location remains explicit provider-supplied grid",
        )
        eq(
            "PSK_REPORTER:senderLocator",
            first.transmitter.location.sourceReference,
            "sender locator provenance retained",
        )
        eq(
            "PSK_REPORTER:receiverLocator",
            first.receiver.location.sourceReference,
            "receiver locator provenance retained",
        )
        eq(14_075_311L, first.frequencyHz, "integer-Hz frequency retained")
        eq("20m", first.normalizedBand, "frequency resolves through existing band catalog")
        eq("FT8", first.normalizedMode, "provider mode retained")
        eq(-19.0, first.snrDb, "provider SNR retained")
        eq(1_599_163_380_000L, first.observedAtUtcMillis, "Unix seconds normalized to UTC millis")
        eq(1, first.reportCount, "single provider report count retained")

        eq("PSK_REPORTER_PUBLIC_QUERY", first.source.sourceId, "source id pinned")
        eq("PSK Reporter", first.source.providerName, "provider name pinned")
        eq(
            PropagationSourceClass.MEASUREMENT,
            first.source.sourceClass,
            "heard path remains measurement-class evidence",
        )
        eq(
            "psk-reporter-query-xml-docs-2026-10-07",
            first.source.sourceVersion,
            "dated query schema pin retained",
        )
        eq(RETRIEVED, first.source.retrievedAtUtcMillis, "retrieval UTC separate from observation")
        eq(SOURCE_URL, first.source.sourceUrl, "exact query provenance retained")
        eq(
            PropagationConfidenceBasis.PROVIDER_REPORTED,
            first.confidence.basis,
            "confidence basis is provider-reported",
        )
        eq(0.80, first.confidence.value, "FieldOps provisional confidence retained")
        checkThat(
            first.confidence.explanation.contains("not a QSO"),
            "confidence explanation keeps QSO separation explicit",
        )
        checkThat(
            PropagationDataQuality.PROVISIONAL in first.quality,
            "crowdsourced reception remains provisional",
        )

        eq(
            listOf("20m", "20m", "20m", "40m"),
            result.observations.map { it.normalizedBand },
            "fixture maps to expected amateur bands",
        )
    }

    private fun preservesHeardPathSemantics() {
        val result = parse()
        val first = result.observations.first()
        checkThat(first.coverage is PathPropagationCoverage, "reception record becomes path coverage")
        val coverage = first.coverage as PathPropagationCoverage
        eq("GRID:DM14CC24", coverage.origin.stableKey, "path origin is explicit sender grid")
        eq("GRID:EM55DB92", coverage.destination.stableKey, "path destination is explicit receiver grid")

        val snapshot = PropagationSnapshot(
            snapshotId = "pskr-only",
            capturedAtUtcMillis = RETRIEVED,
            heardPaths = listOf(first),
        )
        val assessment = PropagationAssessmentEngine().assess(
            snapshot,
            PropagationAssessmentQuery(
                band = "20m",
                frequencyHz = first.frequencyHz,
                origin = first.transmitter.location,
                destination = first.receiver.location,
                nowUtcMillis = first.observedAtUtcMillis + 60_000,
            )
        )
        eq(PropagationUsability.GOOD, assessment.usability, "fresh matching one-way heard path is observed propagation evidence")
        checkThat(
            assessment.reasons.any {
                it.code == PropagationAssessmentReasonCode.RECENT_OBSERVED_PATH
            },
            "assessment cites recent observed path rather than model output",
        )
        checkThat(
            assessment.reasons.none {
                it.code == PropagationAssessmentReasonCode.MODEL_FREQUENCY_WITHIN_LIMITS
            },
            "PSK Reporter path is not mislabeled as modeled propagation",
        )

        val wrongBand = PropagationAssessmentEngine().assess(
            snapshot,
            PropagationAssessmentQuery(
                band = "40m",
                frequencyHz = 7_075_000,
                origin = first.transmitter.location,
                destination = first.receiver.location,
                nowUtcMillis = first.observedAtUtcMillis + 60_000,
            )
        )
        eq(PropagationUsability.UNKNOWN, wrongBand.usability, "heard path remains band-specific")
    }

    private fun preservesOptionalSnrAndDuplicates() {
        val withoutSnr = fixture.replaceFirst(" sNR=\"-19\"", "")
        val noSnrResult = parse(withoutSnr)
        eq(4, noSnrResult.observations.size, "missing optional SNR does not reject path")
        eq(null, noSnrResult.observations.first().snrDb, "missing SNR stays null rather than invented")

        val firstRow = fixture.lineSequence().first { it.contains("<receptionReport ") }
        val duplicated = fixture.replace(
            "</receptionReports>",
            firstRow + "\n</receptionReports>",
        )
        val duplicateResult = parse(duplicated)
        eq(5, duplicateResult.responseReportCount, "duplicate row counted in response")
        eq(4, duplicateResult.observations.size, "exact duplicate collapses to one evidence item")
        eq(0, duplicateResult.rejectedReports.size, "duplicate is not a malformed report")
        val collapsed = duplicateResult.observations.single {
            it.receiver.normalizedCallsign == "W5CJ"
        }
        eq(2, collapsed.reportCount, "duplicate increments reportCount")
        checkThat(
            duplicateResult.observations.map { it.evidenceId }.distinct().size ==
                duplicateResult.observations.size,
            "normalized evidence ids remain unique after duplicate collapse",
        )

        val enriched = fixture.replaceFirst(
            " sNR=\"-19\" />",
            " sNR=\"-19\" futureEnrichment=\"ignored\" />",
        )
        eq(4, parse(enriched).observations.size, "unrelated provider enrichment attributes are tolerated")
    }

    private fun explicitlyRejectsMissingGeography() {
        rejectOne(
            fixture.replaceFirst(" senderLocator=\"DM14cc24\"", ""),
            PskReporterRejectionReason.MISSING_SENDER_LOCATOR,
            "missing sender locator",
        )
        rejectOne(
            fixture.replaceFirst(" receiverLocator=\"EM55db92\"", ""),
            PskReporterRejectionReason.MISSING_RECEIVER_LOCATOR,
            "missing receiver locator",
        )
        rejectOne(
            fixture.replaceFirst("senderLocator=\"DM14cc24\"", "senderLocator=\"ZZ99\""),
            PskReporterRejectionReason.INVALID_SENDER_LOCATOR,
            "invalid sender locator",
        )
        rejectOne(
            fixture.replaceFirst("receiverLocator=\"EM55db92\"", "receiverLocator=\"EM5\""),
            PskReporterRejectionReason.INVALID_RECEIVER_LOCATOR,
            "invalid receiver locator",
        )
        rejectOne(
            fixture.replaceFirst("receiverLocator=\"EM55db92\"", "receiverLocator=\"DM14cc24\""),
            PskReporterRejectionReason.IDENTICAL_ENDPOINTS,
            "identical endpoint grids",
        )

        val missingSender = fixture.replaceFirst("senderCallsign=\"AG6K\" ", "")
        rejectOne(
            missingSender,
            PskReporterRejectionReason.MISSING_SENDER_CALLSIGN,
            "missing sender callsign",
        )
        val missingReceiver = fixture.replaceFirst("receiverCallsign=\"W5CJ\" ", "")
        rejectOne(
            missingReceiver,
            PskReporterRejectionReason.MISSING_RECEIVER_CALLSIGN,
            "missing receiver callsign",
        )
    }

    private fun explicitlyRejectsInvalidPathFacts() {
        rejectOne(
            fixture.replaceFirst(" frequency=\"14075311\"", ""),
            PskReporterRejectionReason.MISSING_FREQUENCY,
            "missing frequency",
        )
        rejectOne(
            fixture.replaceFirst("frequency=\"14075311\"", "frequency=\"14.075311MHz\""),
            PskReporterRejectionReason.INVALID_FREQUENCY,
            "nonnumeric frequency",
        )
        rejectOne(
            fixture.replaceFirst("frequency=\"14075311\"", "frequency=\"0\""),
            PskReporterRejectionReason.INVALID_FREQUENCY,
            "zero frequency",
        )
        rejectOne(
            fixture.replaceFirst("frequency=\"14075311\"", "frequency=\"300000000\""),
            PskReporterRejectionReason.UNMAPPED_AMATEUR_BAND,
            "frequency outside built-in amateur bands",
        )
        rejectOne(
            fixture.replaceFirst(" flowStartSeconds=\"1599163380\"", ""),
            PskReporterRejectionReason.MISSING_FLOW_START_SECONDS,
            "missing observation time",
        )
        rejectOne(
            fixture.replaceFirst("flowStartSeconds=\"1599163380\"", "flowStartSeconds=\"yesterday\""),
            PskReporterRejectionReason.INVALID_FLOW_START_SECONDS,
            "nonnumeric observation time",
        )
        rejectOne(
            fixture.replaceFirst("flowStartSeconds=\"1599163380\"", "flowStartSeconds=\"-1\""),
            PskReporterRejectionReason.INVALID_FLOW_START_SECONDS,
            "negative observation time",
        )
        rejectOne(
            fixture.replaceFirst(" mode=\"FT8\"", ""),
            PskReporterRejectionReason.MISSING_MODE,
            "missing mode",
        )
        rejectOne(
            fixture.replaceFirst("sNR=\"-19\"", "sNR=\"-19.5\""),
            PskReporterRejectionReason.INVALID_SNR,
            "fractional SNR rejected against documented integer field",
        )
        rejectOne(
            fixture.replaceFirst("sNR=\"-19\"", "sNR=\"128\""),
            PskReporterRejectionReason.INVALID_SNR,
            "SNR above signed byte range",
        )
    }

    private fun informationSourceSafety() {
        val automatic = fixture.replaceFirst(
            " sNR=\"-19\" />",
            " sNR=\"-19\" informationSource=\"1\" />",
        )
        eq(4, parse(automatic).observations.size, "explicit automatic source accepted")

        val qsoSource = fixture.replaceFirst(
            " sNR=\"-19\" />",
            " sNR=\"-19\" informationSource=\"2\" />",
        )
        rejectOne(
            qsoSource,
            PskReporterRejectionReason.NON_AUTOMATIC_SOURCE,
            "call-log/QSO source does not masquerade as direct heard evidence",
        )

        val manualSource = fixture.replaceFirst(
            " sNR=\"-19\" />",
            " sNR=\"-19\" informationSource=\"3\" />",
        )
        rejectOne(
            manualSource,
            PskReporterRejectionReason.NON_AUTOMATIC_SOURCE,
            "manual source does not masquerade as direct heard evidence",
        )

        val testSource = fixture.replaceFirst(
            " sNR=\"-19\" />",
            " sNR=\"-19\" informationSource=\"129\" />",
        )
        rejectOne(
            testSource,
            PskReporterRejectionReason.TEST_RECORD,
            "test-bit source rejected",
        )

        val invalidSource = fixture.replaceFirst(
            " sNR=\"-19\" />",
            " sNR=\"-19\" informationSource=\"auto\" />",
        )
        rejectOne(
            invalidSource,
            PskReporterRejectionReason.INVALID_INFORMATION_SOURCE,
            "malformed informationSource rejected",
        )
    }

    private fun documentAndSourceSafety() {
        expectFailure("blank XML") { parse(" ") }
        expectFailure("wrong root") {
            parse(fixture.replace("<receptionReports", "<reports").replace("</receptionReports>", "</reports>"))
        }
        expectFailure("malformed XML") { parse(fixture.dropLast(20)) }
        expectFailure("DOCTYPE rejected") {
            parse(
                fixture.replace(
                    "<?xml version=\"1.0\"?>",
                    "<?xml version=\"1.0\"?><!DOCTYPE receptionReports [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                )
            )
        }
        expectFailure("malformed currentSeconds fails closed") {
            parse(fixture.replace("currentSeconds=\"1599164934\"", "currentSeconds=\"now\""))
        }
        expectFailure("negative last sequence fails closed") {
            parse(fixture.replace("value=\"14631964162\"", "value=\"-1\""))
        }
        expectFailure("malformed max flow fails closed") {
            parse(fixture.replace("value=\"1599164931\"", "value=\"later\""))
        }
        expectFailure("negative retrieval UTC") {
            PskReporterHeardPathAdapter.parseXml(fixture, -1, SOURCE_URL)
        }
        expectFailure("nonofficial source host") {
            PskReporterHeardPathAdapter.parseXml(
                fixture,
                RETRIEVED,
                "https://example.invalid/query?senderCallsign=AG6K",
            )
        }
        expectFailure("lookalike path rejected") {
            PskReporterHeardPathAdapter.parseXml(
                fixture,
                RETRIEVED,
                "https://retrieve.pskreporter.info/query2?senderCallsign=AG6K",
            )
        }
        expectFailure("callback JSONP query rejected") {
            PskReporterHeardPathAdapter.parseXml(
                fixture,
                RETRIEVED,
                PskReporterHeardPathAdapter.QUERY_URL + "?senderCallsign=AG6K&callback=x",
            )
        }
        expectFailure("appcontact not persisted in source provenance") {
            PskReporterHeardPathAdapter.parseXml(
                fixture,
                RETRIEVED,
                PskReporterHeardPathAdapter.QUERY_URL + "?senderCallsign=AG6K&appcontact=x@example.com",
            )
        }

        eq(
            300_000L,
            PskReporterHeardPathAdapter.MIN_RETRIEVAL_INTERVAL_MILLIS,
            "official five-minute retrieval guidance exposed to future transport",
        )
    }

    private fun qsoPlatformAndTransportSeparation() {
        checkThat(
            PskReporterParseResult::class.java.declaredFields.none {
                it.name.contains("qso", ignoreCase = true) ||
                    it.name.contains("lotw", ignoreCase = true) ||
                    it.name.contains("confirmation", ignoreCase = true)
            },
            "parse result has no QSO/LoTW/confirmation state",
        )
        checkThat(
            PskReporterHeardPathAdapter::class.java.declaredFields.none {
                it.type.name.startsWith("android.") ||
                    it.type.name.startsWith("androidx.") ||
                    it.type.name.contains("OkHttp") ||
                    it.type.name.contains("Retrofit") ||
                    it.type.name.contains("HttpClient")
            },
            "adapter carries no Android or HTTP client state",
        )
        val first = parse().observations.first()
        checkThat(
            HeardPathObservation::class.java.declaredFields.none {
                it.name.contains("qso", ignoreCase = true) ||
                    it.name.contains("lotw", ignoreCase = true)
            },
            "heard-path domain itself remains separate from QSO/LoTW state",
        )
        checkThat(
            first.source.sourceUrl!!.startsWith(PskReporterHeardPathAdapter.QUERY_URL),
            "accepted evidence points only to official query provenance",
        )
    }

    private fun rejectOne(
        xml: String,
        reason: PskReporterRejectionReason,
        message: String,
    ) {
        val result = parse(xml)
        eq(4, result.responseReportCount, "$message response count")
        eq(3, result.observations.size, "$message leaves other reports usable")
        eq(1, result.rejectedReports.size, "$message explicit rejection count")
        eq(reason, result.rejectedReports.single().reason, "$message rejection reason")
        checkThat(result.rejectedReports.single().detail.isNotBlank(), "$message rejection detail")
    }

    private fun parse(xml: String = fixture): PskReporterParseResult =
        PskReporterHeardPathAdapter.parseXml(
            xml = xml,
            retrievedAtUtcMillis = RETRIEVED,
            sourceUrl = SOURCE_URL,
        )
}
