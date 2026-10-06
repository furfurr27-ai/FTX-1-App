package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.awards.*
import dev.n0png.fieldops.core.logbook.QsoRecord
import java.net.URI

object OfficialAwardCatalogTests {
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
        catalogHasVersionedOfficialSources()
        dxccRulesAreEncodedConservatively()
        wasRulesAreEncodedConservatively()
        wacRulesAreEncodedConservatively()
        triplePlayRequiresCompositeLotwRules()
        iota100RequiresCountAndContinentalCoverage()
        potaBronzeHunterIsAutomaticProgramAward()
        sotaShackSlothRemainsExternalPointScoring()
        localDefinitionsNeverInferOfficialClaimability()
        officialStandingRequiresSponsorEvidence()
        invalidCatalogInputsFailClosed()
        println("CP-0006B official award catalog tests: PASS assertions=$assertions")
    }

    private fun catalogHasVersionedOfficialSources() {
        checkThat(OfficialAwardCatalog.CATALOG_VERSION.startsWith("2026-10-06."), "catalog version family")
        eq("2026-10-06", OfficialAwardCatalog.RETRIEVED_ON, "catalog retrieval date")
        checkThat(OfficialAwardCatalog.entries.size >= 7, "initial official catalog entries remain present")
        eq(OfficialAwardCatalog.entries.size, OfficialAwardCatalog.entries.map { it.id }.toSet().size, "catalog ids unique")

        val allowedHosts = setOf(
            "www.arrl.org",
            "lotw.arrl.org",
            "www.iota-world.org",
            "docs.pota.app",
            "www.sota.org.uk",
        )
        OfficialAwardCatalog.entries.forEach { entry ->
            checkThat(entry.sources.isNotEmpty(), "${entry.id} source list")
            checkThat(URI(entry.informationUrl).host in allowedHosts, "${entry.id} official info host")
            entry.claimUrl?.let {
                checkThat(URI(it).host in allowedHosts, "${entry.id} official claim host")
            }
            entry.sources.forEach { source ->
                eq("2026-10-06", source.retrievedOn, "${entry.id} source retrieval date")
                checkThat(URI(source.url).host in allowedHosts, "${entry.id} official source host")
            }
        }

        checkThat(
            OfficialAwardCatalog.require("IOTA_100").sources.any {
                it.versionLabel == "Programme Rules published 2026-05-05"
            },
            "IOTA rules version pin",
        )
        checkThat(
            OfficialAwardCatalog.require("SOTA_SHACK_SLOTH_1000").sources.any {
                it.versionLabel == "General Rules v1.21, updated 2022-06-01"
            },
            "SOTA rules version pin",
        )
    }

    private fun dxccRulesAreEncodedConservatively() {
        val dxcc = OfficialAwardCatalog.require("ARRL_DXCC_MIXED")
        eq("ARRL", dxcc.issuer, "DXCC issuer")
        eq(OfficialAwardTargetKind.DXCC_ENTITY, dxcc.requirement.targetKind, "DXCC target")
        eq(OfficialAwardRuleShape.DISTINCT_TARGET_COUNT, dxcc.requirement.ruleShape, "DXCC rule shape")
        eq(100, dxcc.requirement.requiredDistinctTargets, "DXCC threshold")
        eq(AwardThresholdBasis.CONFIRMED, dxcc.requirement.thresholdBasis, "DXCC threshold basis")
        eq(OfficialAwardConfirmationPolicy.ACCEPTED_CONFIRMATION, dxcc.requirement.confirmationPolicy, "DXCC confirmation")
        eq("1945-11-15", dxcc.requirement.notBeforeUtcDate, "DXCC date floor")
        eq(OfficialAwardEvaluationSupport.REQUIRES_NORMALIZED_QSO_TARGET, dxcc.evaluationSupport, "DXCC local support")
        checkThat(dxcc.requirement.targetUniverse == null, "DXCC list is not copied into catalog")
    }

    private fun wasRulesAreEncodedConservatively() {
        val was = OfficialAwardCatalog.require("ARRL_WAS_BASIC")
        eq(50, was.requirement.requiredDistinctTargets, "WAS threshold")
        eq(50, was.requirement.targetUniverse!!.size, "WAS target universe size")
        checkThat("MD" in was.requirement.targetUniverse!!, "Maryland is a WAS target")
        checkThat("DC" !in was.requirement.targetUniverse!!, "DC is not a separate WAS target")
        eq(setOf("60m"), was.requirement.excludedBands, "WAS excluded band")
        checkThat(
            was.requirement.additionalConditions.any { it.contains("District of Columbia") && it.contains("Maryland") },
            "WAS DC/MD rule",
        )
        checkThat(
            was.requirement.additionalConditions.any { it.contains("50 miles") },
            "WAS location rule retained",
        )
    }

    private fun wacRulesAreEncodedConservatively() {
        val wac = OfficialAwardCatalog.require("IARU_WAC_BASIC")
        eq(6, wac.requirement.requiredDistinctTargets, "WAC threshold")
        eq(
            setOf("AFRICA", "ASIA", "EUROPE", "NORTH_AMERICA", "OCEANIA", "SOUTH_AMERICA"),
            wac.requirement.targetUniverse,
            "WAC six-continent universe",
        )
        checkThat("ANTARCTICA" !in wac.requirement.targetUniverse!!, "WAC excludes Antarctica from six-continent award")
        eq(AwardThresholdBasis.CONFIRMED, wac.requirement.thresholdBasis, "WAC requires confirmed contacts")
    }

    private fun triplePlayRequiresCompositeLotwRules() {
        val triple = OfficialAwardCatalog.require("ARRL_TRIPLE_PLAY_WAS")
        eq(OfficialAwardRuleShape.MODE_TARGET_MATRIX, triple.requirement.ruleShape, "Triple Play rule shape")
        eq(150, triple.requirement.requiredDistinctTargets, "Triple Play state/mode cells")
        eq(50, triple.requirement.requiredCoverage.size, "Triple Play state coverage")
        eq(
            setOf(AwardModeGroup.PHONE, AwardModeGroup.CW, AwardModeGroup.DIGITAL),
            triple.requirement.requiredModeGroups,
            "Triple Play required mode groups",
        )
        eq(OfficialAwardConfirmationPolicy.LOTW_ONLY, triple.requirement.confirmationPolicy, "Triple Play LoTW-only")
        eq("2009-01-01", triple.requirement.notBeforeUtcDate, "Triple Play date floor")
        eq(setOf("60m"), triple.requirement.excludedBands, "Triple Play excluded band")
        eq(
            OfficialAwardEvaluationSupport.REQUIRES_COMPOSITE_RULE_ENGINE,
            triple.evaluationSupport,
            "Triple Play requires composite engine",
        )
        expectFailure("Triple Play must not be reduced to simple distinct-target threshold") {
            triple.toLocalThresholdDefinition(AwardTargetSelector { it.call })
        }
    }

    private fun iota100RequiresCountAndContinentalCoverage() {
        val iota = OfficialAwardCatalog.require("IOTA_100")
        eq(100, iota.requirement.requiredDistinctTargets, "IOTA 100 threshold")
        eq(OfficialAwardTargetKind.IOTA_GROUP, iota.requirement.targetKind, "IOTA target kind")
        eq(OfficialAwardRuleShape.COUNT_PLUS_REQUIRED_COVERAGE, iota.requirement.ruleShape, "IOTA rule shape")
        eq(7, iota.requirement.requiredCoverage.size, "IOTA continental coverage count")
        checkThat("ANTARCTICA" in iota.requirement.requiredCoverage, "IOTA coverage includes Antarctica")
        eq("1945-11-15", iota.requirement.notBeforeUtcDate, "IOTA date floor")
        eq(AwardThresholdBasis.CONFIRMED, iota.requirement.thresholdBasis, "IOTA confirmed threshold")
        expectFailure("IOTA must not discard seven-continent coverage") {
            iota.toLocalThresholdDefinition(AwardTargetSelector { it.call })
        }
    }

    private fun potaBronzeHunterIsAutomaticProgramAward() {
        val pota = OfficialAwardCatalog.require("POTA_BRONZE_HUNTER")
        eq(10, pota.requirement.requiredDistinctTargets, "POTA bronze threshold")
        eq(OfficialAwardTargetKind.POTA_REFERENCE, pota.requirement.targetKind, "POTA target kind")
        eq(AwardThresholdBasis.WORKED, pota.requirement.thresholdBasis, "POTA worked threshold")
        eq(OfficialAwardConfirmationPolicy.PROGRAM_VALIDATED_LOG, pota.requirement.confirmationPolicy, "POTA program log")
        eq(OfficialAwardClaimMechanism.AUTOMATIC_PROGRAM_ISSUE, pota.claimMechanism, "POTA automatic issue")
        checkThat(pota.claimUrl == null, "POTA automatic issue has no fake claim URL")
    }

    private fun sotaShackSlothRemainsExternalPointScoring() {
        val sota = OfficialAwardCatalog.require("SOTA_SHACK_SLOTH_1000")
        eq(OfficialAwardTargetKind.SOTA_POINT, sota.requirement.targetKind, "SOTA target kind")
        eq(OfficialAwardRuleShape.POINTS, sota.requirement.ruleShape, "SOTA points rule")
        checkThat(sota.requirement.requiredDistinctTargets == null, "SOTA points are not modeled as distinct QSOs")
        eq(
            OfficialAwardEvaluationSupport.EXTERNAL_PROGRAM_SCORING,
            sota.evaluationSupport,
            "SOTA scoring remains external",
        )
        eq(
            OfficialAwardClaimMechanism.PROGRAM_DATABASE_AND_SHOP,
            sota.claimMechanism,
            "SOTA claim mechanism",
        )
        expectFailure("SOTA points must not be reduced to CP-0006A distinct-target count") {
            sota.toLocalThresholdDefinition(AwardTargetSelector { it.call })
        }
    }

    private fun localDefinitionsNeverInferOfficialClaimability() {
        val wac = OfficialAwardCatalog.require("IARU_WAC_BASIC")
        val definition = wac.toLocalThresholdDefinition(AwardTargetSelector { it.call })
        checkThat(definition.officialClaimabilityEvaluator == null, "catalog definition must not attach sponsor claimability")

        val qsos = (1L..6L).map { index ->
            qso(index, "TARGET$index")
        }
        val confirmations = qsos.map {
            AwardConfirmationEvidence(it.id, "SYNTHETIC_ACCEPTED_CONFIRMATION")
        }
        val result = AwardEvaluator().evaluate(definition, qsos, confirmations)

        checkThat(AwardProgressState.THRESHOLD_MET in result.states, "local threshold can be calculated")
        checkThat(
            AwardProgressState.OFFICIALLY_CLAIMABLE !in result.states,
            "official claimability is not inferred from catalog threshold",
        )
    }

    private fun officialStandingRequiresSponsorEvidence() {
        val unknown = OfficialAwardStandingRecord(
            awardId = "ARRL_DXCC_MIXED",
            standing = OfficialAwardStanding.UNKNOWN,
        )
        eq(OfficialAwardStanding.UNKNOWN, unknown.standing, "unknown standing")

        val awarded = OfficialAwardStandingRecord(
            awardId = "ARRL_DXCC_MIXED",
            standing = OfficialAwardStanding.AWARDED,
            sponsorReference = "synthetic-sponsor-evidence",
            recordedAtUtcMillis = 1_800_000_000_000L,
        )
        eq(OfficialAwardStanding.AWARDED, awarded.standing, "awarded standing")
        eq("synthetic-sponsor-evidence", awarded.sponsorReference, "awarded sponsor evidence")

        expectFailure("awarded status without sponsor evidence must fail") {
            OfficialAwardStandingRecord("ARRL_DXCC_MIXED", OfficialAwardStanding.AWARDED)
        }
        expectFailure("credited status without sponsor evidence must fail") {
            OfficialAwardStandingRecord("ARRL_DXCC_MIXED", OfficialAwardStanding.CREDITED)
        }
    }

    private fun invalidCatalogInputsFailClosed() {
        expectFailure("source must be HTTPS") {
            OfficialAwardSource(
                role = OfficialAwardSourceRole.RULES,
                url = "http://example.invalid",
                retrievedOn = "2026-10-06",
            )
        }
        expectFailure("source retrieval date format") {
            OfficialAwardSource(
                role = OfficialAwardSourceRole.RULES,
                url = "https://example.invalid",
                retrievedOn = "06-10-2026",
            )
        }
        expectFailure("non-points rules need threshold") {
            OfficialAwardRequirement(
                targetKind = OfficialAwardTargetKind.US_STATE,
                ruleShape = OfficialAwardRuleShape.DISTINCT_TARGET_COUNT,
                thresholdBasis = AwardThresholdBasis.CONFIRMED,
                confirmationPolicy = OfficialAwardConfirmationPolicy.ACCEPTED_CONFIRMATION,
            )
        }
        expectFailure("points target shape mismatch") {
            OfficialAwardRequirement(
                targetKind = OfficialAwardTargetKind.US_STATE,
                ruleShape = OfficialAwardRuleShape.POINTS,
                thresholdBasis = AwardThresholdBasis.WORKED,
                confirmationPolicy = OfficialAwardConfirmationPolicy.PROGRAM_VALIDATED_LOG,
            )
        }
        expectFailure("blank additional condition") {
            OfficialAwardRequirement(
                targetKind = OfficialAwardTargetKind.US_STATE,
                ruleShape = OfficialAwardRuleShape.DISTINCT_TARGET_COUNT,
                requiredDistinctTargets = 1,
                thresholdBasis = AwardThresholdBasis.CONFIRMED,
                confirmationPolicy = OfficialAwardConfirmationPolicy.ACCEPTED_CONFIRMATION,
                additionalConditions = listOf(" "),
            )
        }
        expectFailure("unknown catalog id") {
            OfficialAwardCatalog.require("NOT_A_REAL_AWARD")
        }
    }

    private fun qso(id: Long, call: String) = QsoRecord(
        id = id,
        call = call,
        stationCallsign = "N0PNG",
        qsoDate = "20261006",
        timeOn = "130000",
        band = "20m",
        mode = "CW",
    )
}
