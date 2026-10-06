package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.awards.*
import dev.n0png.fieldops.core.logbook.QsoRecord

object AwardEvaluationTests {
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
        controlledModeGroupingPreservesExactIdentity()
        workedIsNotSilentlyConfirmed()
        explicitConfirmationEvidenceDrivesConfirmedThreshold()
        officialClaimabilityRequiresExplicitEvaluatorAndThreshold()
        bandScopedAndAllBandEvaluationDoNotRewriteQso()
        modeScopedEvaluationUsesControlledGroup()
        duplicateTargetsCountOnce()
        targetUniverseTracksRemainingAgainstThresholdBasis()
        workedBasisThresholdIsSupported()
        invalidDefinitionsAndEvidenceFailClosed()
        println("CP-0006A award evaluation tests: PASS assertions=$assertions")
    }

    private fun controlledModeGroupingPreservesExactIdentity() {
        val result = AwardEvaluator().evaluate(
            definition = definition(required = 1),
            qsos = listOf(
                qso(1, "DL1AAA", "20m", "MFSK", "FT8"),
                qso(2, "K1ABC", "20m", "SSB"),
                qso(3, "W1AW", "20m", "CW"),
            ),
        )

        eq(3, result.workedCount, "all controlled groups should contribute")
        val ft8 = result.contributions.single { it.qsoId == 1L }
        eq(AwardModeGroup.DIGITAL, ft8.modeGroup, "FT8 parent mode grouping")
        eq("MFSK", ft8.exactMode, "exact ADIF MODE preserved")
        eq("FT8", ft8.exactSubmode, "exact ADIF SUBMODE preserved")
        eq(AwardModeGroup.PHONE, result.contributions.single { it.qsoId == 2L }.modeGroup, "SSB grouping")
        eq(AwardModeGroup.CW, result.contributions.single { it.qsoId == 3L }.modeGroup, "CW grouping")

        val unclassified = AwardEvaluator().evaluate(
            definition = definition(required = 1),
            qsos = listOf(qso(4, "N1UNK", "20m", "SSTV")),
        )
        eq(0, unclassified.contributions.size, "unmapped modes must not be guessed into an award group")
        checkThat(AwardProgressState.WORKED !in unclassified.states, "unmapped mode must not create worked progress")
    }

    private fun workedIsNotSilentlyConfirmed() {
        val result = AwardEvaluator().evaluate(
            definition = definition(required = 2),
            qsos = listOf(
                qso(10, "DL1AAA", "20m", "SSB"),
                qso(11, "F4BBB", "20m", "SSB"),
            ),
        )

        eq(2, result.workedCount, "worked distinct count")
        eq(0, result.confirmedCount, "no evidence means no confirmations")
        checkThat(AwardProgressState.WORKED in result.states, "worked state missing")
        checkThat(AwardProgressState.CONFIRMED !in result.states, "worked must not imply confirmed")
        checkThat(AwardProgressState.THRESHOLD_MET !in result.states, "confirmed-basis threshold must not be met by worked-only QSOs")
        checkThat(AwardProgressState.OFFICIALLY_CLAIMABLE !in result.states, "worked-only progress must not be claimable")
    }

    private fun explicitConfirmationEvidenceDrivesConfirmedThreshold() {
        val result = AwardEvaluator().evaluate(
            definition = definition(required = 2),
            qsos = listOf(
                qso(20, "DL1AAA", "20m", "CW"),
                qso(21, "F4BBB", "20m", "CW"),
                qso(22, "G0CCC", "20m", "CW"),
            ),
            confirmations = listOf(
                AwardConfirmationEvidence(20, "LOTW", reference = "lotw-20", confirmedAtUtcMillis = 1_800_000_000_000L),
                AwardConfirmationEvidence(21, "PAPER_QSL", reference = "card-21"),
            ),
        )

        eq(3, result.workedCount, "worked count with confirmations")
        eq(2, result.confirmedCount, "confirmed distinct count")
        checkThat(AwardProgressState.WORKED in result.states, "worked state")
        checkThat(AwardProgressState.CONFIRMED in result.states, "confirmed state")
        checkThat(AwardProgressState.THRESHOLD_MET in result.states, "confirmed threshold state")
        checkThat(AwardProgressState.OFFICIALLY_CLAIMABLE !in result.states, "threshold alone must not imply sponsor claimability")
        eq("LOTW", result.contributions.single { it.qsoId == 20L }.confirmationEvidence.single().source, "confirmation source provenance")
        eq("lotw-20", result.contributions.single { it.qsoId == 20L }.confirmationEvidence.single().reference, "confirmation reference provenance")
        checkThat(!result.contributions.single { it.qsoId == 22L }.confirmed, "unconfirmed QSO remains unconfirmed")
    }

    private fun officialClaimabilityRequiresExplicitEvaluatorAndThreshold() {
        var evaluatorCalls = 0
        val explicit = definition(
            required = 2,
            official = AwardOfficialClaimabilityEvaluator { context ->
                evaluatorCalls++
                context.confirmedTargets.containsAll(setOf("DL1AAA", "F4BBB"))
            },
        )
        val qsos = listOf(
            qso(30, "DL1AAA", "40m", "CW"),
            qso(31, "F4BBB", "40m", "CW"),
        )

        val below = AwardEvaluator().evaluate(
            definition = explicit,
            qsos = qsos,
            confirmations = listOf(AwardConfirmationEvidence(30, "LOTW")),
        )
        eq(0, evaluatorCalls, "official evaluator must not run before local threshold")
        checkThat(AwardProgressState.THRESHOLD_MET !in below.states, "below threshold")
        checkThat(AwardProgressState.OFFICIALLY_CLAIMABLE !in below.states, "below threshold cannot be claimable")

        val met = AwardEvaluator().evaluate(
            definition = explicit,
            qsos = qsos,
            confirmations = listOf(
                AwardConfirmationEvidence(30, "LOTW"),
                AwardConfirmationEvidence(31, "LOTW"),
            ),
        )
        eq(1, evaluatorCalls, "official evaluator called only after threshold")
        checkThat(AwardProgressState.THRESHOLD_MET in met.states, "threshold met")
        checkThat(AwardProgressState.OFFICIALLY_CLAIMABLE in met.states, "explicit positive sponsor rule may mark claimable")

        val denied = AwardEvaluator().evaluate(
            definition = definition(required = 1, official = AwardOfficialClaimabilityEvaluator { false }),
            qsos = listOf(qso(32, "G0CCC", "20m", "SSB")),
            confirmations = listOf(AwardConfirmationEvidence(32, "LOTW")),
        )
        checkThat(AwardProgressState.THRESHOLD_MET in denied.states, "local threshold still met when sponsor evaluator denies")
        checkThat(AwardProgressState.OFFICIALLY_CLAIMABLE !in denied.states, "explicit sponsor denial remains distinct")
    }

    private fun bandScopedAndAllBandEvaluationDoNotRewriteQso() {
        val q1 = qso(40, "DL1AAA", "20m", "MFSK", "FT8")
        val q2 = qso(41, "F4BBB", "40m", "MFSK", "FT8")
        val q3 = qso(42, "G0CCC", "40m", "CW")
        val qsos = listOf(q1, q2, q3)
        val evaluator = AwardEvaluator()

        val all = evaluator.evaluate(definition(3), qsos)
        val forty = evaluator.evaluate(definition(2), qsos, filter = AwardEvaluationFilter(band = "40M"))

        eq(3, all.workedCount, "all-band worked count")
        eq(2, forty.workedCount, "40m worked count")
        eq(setOf("F4BBB", "G0CCC"), forty.workedTargets, "40m target set")
        eq("MFSK", q1.mode, "evaluation must not rewrite source MODE")
        eq("FT8", q1.submode, "evaluation must not rewrite source SUBMODE")
        eq("20m", q1.band, "evaluation must not rewrite source band")
        eq("40m", forty.filter.normalizedBand, "band filter normalization")
    }

    private fun modeScopedEvaluationUsesControlledGroup() {
        val qsos = listOf(
            qso(50, "DL1AAA", "20m", "SSB"),
            qso(51, "F4BBB", "20m", "MFSK", "FT8"),
            qso(52, "G0CCC", "20m", "CW"),
        )
        val definition = definition(1)
        val evaluator = AwardEvaluator()

        val phone = evaluator.evaluate(definition, qsos, filter = AwardEvaluationFilter(modeGroup = AwardModeGroup.PHONE))
        val digital = evaluator.evaluate(definition, qsos, filter = AwardEvaluationFilter(modeGroup = AwardModeGroup.DIGITAL))
        val cw = evaluator.evaluate(definition, qsos, filter = AwardEvaluationFilter(modeGroup = AwardModeGroup.CW))

        eq(setOf("DL1AAA"), phone.workedTargets, "phone filter")
        eq(setOf("F4BBB"), digital.workedTargets, "digital filter")
        eq(setOf("G0CCC"), cw.workedTargets, "CW filter")
        eq("FT8", digital.contributions.single().exactSubmode, "digital filter preserves exact submode")

        val digitalOnlyDefinition = definition(1, eligible = setOf(AwardModeGroup.DIGITAL))
        val eligible = evaluator.evaluate(digitalOnlyDefinition, qsos)
        eq(setOf("F4BBB"), eligible.workedTargets, "definition-level group eligibility")
    }

    private fun duplicateTargetsCountOnce() {
        val result = AwardEvaluator().evaluate(
            definition = definition(2),
            qsos = listOf(
                qso(60, "DL1AAA", "20m", "SSB"),
                qso(61, "dl1aaa", "40m", "CW"),
                qso(62, "F4BBB", "20m", "CW"),
            ),
            confirmations = listOf(
                AwardConfirmationEvidence(60, "LOTW"),
                AwardConfirmationEvidence(61, "PAPER_QSL"),
                AwardConfirmationEvidence(62, "LOTW"),
            ),
        )

        eq(2, result.workedCount, "duplicate target worked once")
        eq(2, result.confirmedCount, "duplicate target confirmed once")
        eq(3, result.contributions.size, "all qualifying QSOs remain available as evidence")
        checkThat(AwardProgressState.THRESHOLD_MET in result.states, "distinct confirmed targets satisfy threshold")
    }

    private fun targetUniverseTracksRemainingAgainstThresholdBasis() {
        val definition = AwardDefinition(
            id = "synthetic-universe",
            displayName = "Synthetic Universe",
            requiredDistinctTargets = 3,
            targetSelector = AwardTargetSelector { it.call },
            targetUniverse = setOf("DL1AAA", "F4BBB", "G0CCC", "I1DDD"),
        )
        val result = AwardEvaluator().evaluate(
            definition,
            qsos = listOf(
                qso(70, "DL1AAA", "20m", "CW"),
                qso(71, "F4BBB", "20m", "CW"),
                qso(72, "G0CCC", "20m", "CW"),
            ),
            confirmations = listOf(
                AwardConfirmationEvidence(70, "LOTW"),
                AwardConfirmationEvidence(71, "LOTW"),
            ),
        )

        eq(setOf("G0CCC", "I1DDD"), result.remainingTargets, "confirmed-basis remaining targets")
        eq(2, result.thresholdCount, "confirmed-basis threshold count")
        checkThat(AwardProgressState.THRESHOLD_MET !in result.states, "worked third target does not remove confirmation requirement")
    }

    private fun workedBasisThresholdIsSupported() {
        val definition = AwardDefinition(
            id = "synthetic-worked",
            displayName = "Synthetic Worked",
            requiredDistinctTargets = 2,
            targetSelector = AwardTargetSelector { it.call },
            thresholdBasis = AwardThresholdBasis.WORKED,
            targetUniverse = setOf("DL1AAA", "F4BBB", "G0CCC"),
        )
        val result = AwardEvaluator().evaluate(
            definition,
            qsos = listOf(
                qso(80, "DL1AAA", "20m", "SSB"),
                qso(81, "F4BBB", "20m", "SSB"),
            ),
        )

        eq(2, result.thresholdCount, "worked-basis threshold count")
        checkThat(AwardProgressState.THRESHOLD_MET in result.states, "worked-basis threshold")
        checkThat(AwardProgressState.CONFIRMED !in result.states, "worked-basis threshold does not invent confirmation")
        eq(setOf("G0CCC"), result.remainingTargets, "worked-basis remaining target")
        checkThat(AwardProgressState.OFFICIALLY_CLAIMABLE !in result.states, "worked threshold still not automatically official")
    }

    private fun invalidDefinitionsAndEvidenceFailClosed() {
        expectFailure("blank award id") {
            AwardDefinition(" ", "Synthetic", 1, AwardTargetSelector { it.call })
        }
        expectFailure("zero threshold") {
            AwardDefinition("x", "Synthetic", 0, AwardTargetSelector { it.call })
        }
        expectFailure("threshold beyond universe") {
            AwardDefinition("x", "Synthetic", 3, AwardTargetSelector { it.call }, targetUniverse = setOf("A", "B"))
        }
        expectFailure("normalized duplicate universe") {
            AwardDefinition("x", "Synthetic", 1, AwardTargetSelector { it.call }, targetUniverse = setOf("A", " a "))
        }
        expectFailure("blank confirmation source") {
            AwardConfirmationEvidence(1, " ")
        }
        expectFailure("non-positive confirmation QSO id") {
            AwardConfirmationEvidence(0, "LOTW")
        }
        expectFailure("negative confirmation time") {
            AwardConfirmationEvidence(1, "LOTW", confirmedAtUtcMillis = -1)
        }
        expectFailure("duplicate mode selector") {
            ControlledAwardModeGrouper(
                listOf(
                    AwardModeRule("MFSK", "FT8", AwardModeGroup.DIGITAL),
                    AwardModeRule("mfsk", "ft8", AwardModeGroup.CW),
                )
            )
        }
        expectFailure("UNCLASSIFIED cannot be declared eligible") {
            definition(1, eligible = setOf(AwardModeGroup.UNCLASSIFIED))
        }
        expectFailure("UNCLASSIFIED cannot be requested as a filter") {
            AwardEvaluationFilter(modeGroup = AwardModeGroup.UNCLASSIFIED)
        }

        val custom = ControlledAwardModeGrouper(
            listOf(
                AwardModeRule("MFSK", group = AwardModeGroup.DIGITAL),
                AwardModeRule("MFSK", "FT8", AwardModeGroup.CW),
            )
        )
        eq(AwardModeGroup.CW, custom.groupFor(qso(90, "X", "20m", "MFSK", "FT8")), "exact submode rule wins over parent rule")
        eq(AwardModeGroup.DIGITAL, custom.groupFor(qso(91, "Y", "20m", "MFSK", "JS8")), "parent rule handles other submodes")
    }

    private fun definition(
        required: Int,
        eligible: Set<AwardModeGroup> = emptySet(),
        official: AwardOfficialClaimabilityEvaluator? = null,
    ) = AwardDefinition(
        id = "synthetic-calls",
        displayName = "Synthetic Calls",
        requiredDistinctTargets = required,
        targetSelector = AwardTargetSelector { it.call },
        eligibleModeGroups = eligible,
        officialClaimabilityEvaluator = official,
    )

    private fun qso(
        id: Long,
        call: String,
        band: String,
        mode: String,
        submode: String? = null,
    ) = QsoRecord(
        id = id,
        call = call,
        stationCallsign = "N0PNG",
        qsoDate = "20261006",
        timeOn = "120000",
        band = band,
        mode = mode,
        submode = submode,
        sourceProvider = if (submode != null) "SYNTHETIC" else null,
    )
}
