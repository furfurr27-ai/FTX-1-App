package dev.n0png.fieldops.core.propagation

import dev.n0png.fieldops.core.logbook.AmateurBandCatalog
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

enum class PskReporterRejectionReason {
    MISSING_SENDER_CALLSIGN,
    MISSING_RECEIVER_CALLSIGN,
    MISSING_SENDER_LOCATOR,
    MISSING_RECEIVER_LOCATOR,
    INVALID_SENDER_LOCATOR,
    INVALID_RECEIVER_LOCATOR,
    MISSING_FREQUENCY,
    INVALID_FREQUENCY,
    UNMAPPED_AMATEUR_BAND,
    MISSING_FLOW_START_SECONDS,
    INVALID_FLOW_START_SECONDS,
    MISSING_MODE,
    INVALID_SNR,
    INVALID_INFORMATION_SOURCE,
    NON_AUTOMATIC_SOURCE,
    TEST_RECORD,
    IDENTICAL_ENDPOINTS,
}

data class PskReporterRejectedReport(
    val reportIndex: Int,
    val reason: PskReporterRejectionReason,
    val detail: String,
) {
    init {
        require(reportIndex >= 0) { "PSK Reporter rejected report index must be non-negative" }
        require(detail.isNotBlank()) { "PSK Reporter rejection detail must not be blank" }
    }
}

data class PskReporterParseResult(
    val observations: List<HeardPathObservation>,
    val rejectedReports: List<PskReporterRejectedReport>,
    val currentSeconds: Long?,
    val lastSequenceNumber: Long?,
    val maxFlowStartSeconds: Long?,
    val responseReportCount: Int,
) {
    init {
        require(currentSeconds == null || currentSeconds >= 0) {
            "PSK Reporter currentSeconds must be non-negative"
        }
        require(lastSequenceNumber == null || lastSequenceNumber >= 0) {
            "PSK Reporter last sequence number must be non-negative"
        }
        require(maxFlowStartSeconds == null || maxFlowStartSeconds >= 0) {
            "PSK Reporter max flow start seconds must be non-negative"
        }
        require(responseReportCount >= 0) {
            "PSK Reporter response report count must be non-negative"
        }
        require(
            observations.sumOf { it.reportCount } + rejectedReports.size == responseReportCount
        ) {
            "PSK Reporter accepted/rejected accounting must match response report count"
        }
    }
}

object PskReporterHeardPathAdapter {
    const val QUERY_URL = "https://retrieve.pskreporter.info/query"
    const val DEVELOPER_INFO_URL = "https://www.pskreporter.info/pskdev.html"
    const val SOURCE_VERSION = "psk-reporter-query-xml-docs-2026-10-07"
    const val MIN_RETRIEVAL_INTERVAL_MILLIS = 5 * 60 * 1000L

    private val maidenhead =
        Regex("""[A-Ra-r]{2}[0-9]{2}([A-Xa-x]{2}([0-9]{2})?)?""")

    fun parseXml(
        xml: String,
        retrievedAtUtcMillis: Long,
        sourceUrl: String,
    ): PskReporterParseResult {
        require(retrievedAtUtcMillis >= 0) {
            "PSK Reporter retrieval UTC must be non-negative"
        }
        validateSourceUrl(sourceUrl)
        require(xml.isNotBlank()) { "PSK Reporter XML must not be blank" }

        val document = secureDocumentBuilderFactory()
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))
        val root = document.documentElement
            ?: throw IllegalArgumentException("PSK Reporter XML has no document element")

        require(root.tagName == "receptionReports") {
            "PSK Reporter root element must be receptionReports"
        }

        val currentSeconds = root.optionalLongAttribute("currentSeconds")
        val lastSequenceNumber =
            root.firstDirectChild("lastSequenceNumber")?.optionalLongAttribute("value")
        val maxFlowStartSeconds =
            root.firstDirectChild("maxFlowStartSeconds")?.optionalLongAttribute("value")

        val reportNodes = root.getElementsByTagName("receptionReport")
        val accepted = linkedMapOf<String, HeardPathObservation>()
        val rejected = mutableListOf<PskReporterRejectedReport>()

        for (index in 0 until reportNodes.length) {
            val element = reportNodes.item(index) as? Element
                ?: throw IllegalArgumentException(
                    "PSK Reporter receptionReport[$index] is not an XML element"
                )

            when (val parsed = parseReport(
                element = element,
                index = index,
                retrievedAtUtcMillis = retrievedAtUtcMillis,
                sourceUrl = sourceUrl,
            )) {
                is ParsedReport.Accepted -> {
                    val key = parsed.observation.evidenceId
                    val previous = accepted[key]
                    accepted[key] =
                        if (previous == null) {
                            parsed.observation
                        } else {
                            previous.copy(reportCount = previous.reportCount + 1)
                        }
                }
                is ParsedReport.Rejected -> rejected += parsed.rejection
            }
        }

        return PskReporterParseResult(
            observations = accepted.values.toList(),
            rejectedReports = rejected.toList(),
            currentSeconds = currentSeconds,
            lastSequenceNumber = lastSequenceNumber,
            maxFlowStartSeconds = maxFlowStartSeconds,
            responseReportCount = reportNodes.length,
        )
    }

    private fun parseReport(
        element: Element,
        index: Int,
        retrievedAtUtcMillis: Long,
        sourceUrl: String,
    ): ParsedReport {
        val senderCallsign = element.attribute("senderCallsign")
            ?: return rejected(index, PskReporterRejectionReason.MISSING_SENDER_CALLSIGN)
        val receiverCallsign = element.attribute("receiverCallsign")
            ?: return rejected(index, PskReporterRejectionReason.MISSING_RECEIVER_CALLSIGN)

        val senderLocator = element.attribute("senderLocator")
            ?: return rejected(index, PskReporterRejectionReason.MISSING_SENDER_LOCATOR)
        val receiverLocator = element.attribute("receiverLocator")
            ?: return rejected(index, PskReporterRejectionReason.MISSING_RECEIVER_LOCATOR)

        if (!maidenhead.matches(senderLocator)) {
            return rejected(
                index,
                PskReporterRejectionReason.INVALID_SENDER_LOCATOR,
                "senderLocator is not a valid 4/6/8-character Maidenhead locator",
            )
        }
        if (!maidenhead.matches(receiverLocator)) {
            return rejected(
                index,
                PskReporterRejectionReason.INVALID_RECEIVER_LOCATOR,
                "receiverLocator is not a valid 4/6/8-character Maidenhead locator",
            )
        }

        val frequencyText = element.attribute("frequency")
            ?: return rejected(index, PskReporterRejectionReason.MISSING_FREQUENCY)
        val frequencyHz = frequencyText.toLongOrNull()
            ?: return rejected(
                index,
                PskReporterRejectionReason.INVALID_FREQUENCY,
                "frequency must be an integer number of hertz",
            )
        if (frequencyHz <= 0) {
            return rejected(
                index,
                PskReporterRejectionReason.INVALID_FREQUENCY,
                "frequency must be positive",
            )
        }
        val band = AmateurBandCatalog.bandFor(frequencyHz)
            ?: return rejected(
                index,
                PskReporterRejectionReason.UNMAPPED_AMATEUR_BAND,
                "frequency is outside the built-in amateur-band catalog",
            )

        val flowText = element.attribute("flowStartSeconds")
            ?: return rejected(index, PskReporterRejectionReason.MISSING_FLOW_START_SECONDS)
        val flowSeconds = flowText.toLongOrNull()
            ?: return rejected(
                index,
                PskReporterRejectionReason.INVALID_FLOW_START_SECONDS,
                "flowStartSeconds must be Unix epoch seconds",
            )
        if (flowSeconds < 0) {
            return rejected(
                index,
                PskReporterRejectionReason.INVALID_FLOW_START_SECONDS,
                "flowStartSeconds must be non-negative",
            )
        }
        val observedAtUtcMillis = try {
            Math.multiplyExact(flowSeconds, 1000L)
        } catch (_: ArithmeticException) {
            return rejected(
                index,
                PskReporterRejectionReason.INVALID_FLOW_START_SECONDS,
                "flowStartSeconds is outside the supported epoch range",
            )
        }

        val mode = element.attribute("mode")
            ?: return rejected(index, PskReporterRejectionReason.MISSING_MODE)

        val snrDb = element.attribute("sNR")?.let { raw ->
            val snr = raw.toIntOrNull()
                ?: return rejected(
                    index,
                    PskReporterRejectionReason.INVALID_SNR,
                    "sNR must be an integer dB value",
                )
            if (snr !in -128..127) {
                return rejected(
                    index,
                    PskReporterRejectionReason.INVALID_SNR,
                    "sNR must fit the provider's documented signed one-byte range",
                )
            }
            snr.toDouble()
        }

        element.attribute("informationSource")?.let { raw ->
            val value = raw.toIntOrNull()
                ?: return rejected(
                    index,
                    PskReporterRejectionReason.INVALID_INFORMATION_SOURCE,
                    "informationSource must be an integer when present",
                )
            if ((value and 0x80) != 0) {
                return rejected(
                    index,
                    PskReporterRejectionReason.TEST_RECORD,
                    "provider informationSource marks this as a test transmission",
                )
            }
            if ((value and 0x03) != 1) {
                return rejected(
                    index,
                    PskReporterRejectionReason.NON_AUTOMATIC_SOURCE,
                    "provider informationSource is not automatically extracted reception data",
                )
            }
        }

        val txGrid = senderLocator.uppercase()
        val rxGrid = receiverLocator.uppercase()
        if (txGrid == rxGrid) {
            return rejected(
                index,
                PskReporterRejectionReason.IDENTICAL_ENDPOINTS,
                "transmitter and receiver locators resolve to the same explicit grid",
            )
        }

        val txCall = senderCallsign.uppercase()
        val rxCall = receiverCallsign.uppercase()
        val normalizedMode = mode.uppercase()
        val snrKey = snrDb?.toInt()?.toString() ?: "NA"

        val source = PropagationSourceRef(
            sourceId = "PSK_REPORTER_PUBLIC_QUERY",
            providerName = "PSK Reporter",
            sourceClass = PropagationSourceClass.MEASUREMENT,
            sourceVersion = SOURCE_VERSION,
            retrievedAtUtcMillis = retrievedAtUtcMillis,
            sourceUrl = sourceUrl,
        )

        val confidence = PropagationConfidence(
            value = 0.80,
            basis = PropagationConfidenceBasis.PROVIDER_REPORTED,
            explanation =
                "PSK Reporter reception record with explicit sender/receiver locators; " +
                    "FieldOps treats it as provisional one-way heard-path evidence, not a QSO.",
        )

        return ParsedReport.Accepted(
            HeardPathObservation(
                evidenceId =
                    "PSKR:$flowSeconds:$txCall:$txGrid:$rxCall:$rxGrid:" +
                        "$frequencyHz:$normalizedMode:$snrKey",
                source = source,
                observedAtUtcMillis = observedAtUtcMillis,
                confidence = confidence,
                quality = setOf(PropagationDataQuality.PROVISIONAL),
                transmitter = PropagationEndpoint(
                    location = PropagationPosition(
                        maidenheadGrid = txGrid,
                        method = PropagationLocationMethod.EXPLICIT_GRID,
                        sourceReference = "PSK_REPORTER:senderLocator",
                    ),
                    callsign = txCall,
                ),
                receiver = PropagationEndpoint(
                    location = PropagationPosition(
                        maidenheadGrid = rxGrid,
                        method = PropagationLocationMethod.EXPLICIT_GRID,
                        sourceReference = "PSK_REPORTER:receiverLocator",
                    ),
                    callsign = rxCall,
                ),
                frequencyHz = frequencyHz,
                band = band,
                mode = mode,
                snrDb = snrDb,
                reportCount = 1,
            )
        )
    }

    private fun validateSourceUrl(sourceUrl: String) {
        require(
            sourceUrl == QUERY_URL || sourceUrl.startsWith("$QUERY_URL?")
        ) {
            "PSK Reporter source URL must use the official HTTPS query endpoint"
        }
        require(!sourceUrl.contains("callback=", ignoreCase = true)) {
            "PSK Reporter XML adapter does not accept callback/JSONP query URLs"
        }
        require(!sourceUrl.contains("appcontact=", ignoreCase = true)) {
            "PSK Reporter source provenance must not persist appcontact email addresses"
        }
    }

    private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isXIncludeAware = false
            setExpandEntityReferences(false)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }

    private fun Element.attribute(name: String): String? =
        getAttribute(name).trim().takeIf { it.isNotEmpty() }

    private fun Element.optionalLongAttribute(name: String): Long? {
        val raw = attribute(name) ?: return null
        return raw.toLongOrNull()?.takeIf { it >= 0 }
            ?: throw IllegalArgumentException(
                "PSK Reporter XML attribute $name must be a non-negative integer"
            )
    }

    private fun Element.firstDirectChild(tagName: String): Element? {
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child is Element && child.tagName == tagName) return child
        }
        return null
    }

    private fun rejected(
        index: Int,
        reason: PskReporterRejectionReason,
        detail: String = reason.name.lowercase().replace('_', ' '),
    ): ParsedReport.Rejected =
        ParsedReport.Rejected(
            PskReporterRejectedReport(
                reportIndex = index,
                reason = reason,
                detail = detail,
            )
        )

    private sealed interface ParsedReport {
        data class Accepted(val observation: HeardPathObservation) : ParsedReport
        data class Rejected(val rejection: PskReporterRejectedReport) : ParsedReport
    }
}
