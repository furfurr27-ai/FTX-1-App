package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.awards.*
import dev.n0png.fieldops.core.logbook.QsoRecord

object AwardsCenterProjectionTests {
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
        emptyProjectionPreservesCatalogAndOfficialMetadata()
        distinctTargetProgressIsDeterministic()
        modeAndBandViewsFilterWithoutChangingRules()
        dateRangeFiltersProjectionInput()
        triplePlayModeViewSeparatesViewCompletionFromAwardThreshold()
        iotaCompositeProgressIncludesCoverage()
        sponsorStandingNeverComesFromLocalThreshold()
        externalScoringNeverFabricatesProgress()
        latestSponsorStandingIsDeterministicAndFailsOnConflict()
        projectionDoesNotMutateQsoOrMoveRulesIntoCards()
        println("CP-0006D Awards Center projection tests: PASS assertions=$assertions")
    }

    private fun emptyProjectionPreservesCatalogAndOfficialMetadata() {
        val cards = service().project(
            qsos = emptyList(),
            targets = AwardTargetEvidenceIndex(emptyList()),
        )

        eq(OfficialAwardCatalog.entries.size, cards.size, "one card per catalog entry")
        eq(
            OfficialAwardCatalog.entries.map { it.id },
            cards.map { it.awardId },
            "catalog ordering preserved",
        )

        val dxcc = cards.card("ARRL_DXCC_MIXED")
        eq("ARRL", dxcc.issuer, "DXCC issuer")
        eq("https://www.arrl.org/dxcc-rules", dxcc.informationUrl, "DXCC info URL")
        checkThat(dxcc.claimUrl != null, "DXCC claim URL present")
        checkThat(dxcc.claimInstructions.isNotBlank(), "DXCC claim instructions present")
        checkThat(dxcc.officialSources.isNotEmpty(), "DXCC official sources present")
        eq(OfficialAwardStanding.UNKNOWN, dxcc.sponsorStanding, "default sponsor standing")
        eq(null, dxcc.claimableNow, "unknown sponsor standing gives unknown claimability")
        eq(0, dxcc.workedCount, "empty DXCC worked count")
        eq(0, dxcc.confirmedCount, "empty DXCC confirmed count")
        eq(false, dxcc.localThresholdMet, "empty DXCC threshold")
        eq(AwardsCenterProgressKind.DISTINCT_TARGETS, dxcc.progress.kind, "DXCC progress shape")
        eq(0, dxcc.progress.completedUnits, "DXCC progress numerator")
        eq(100, dxcc.progress.requiredUnits, "DXCC progress denominator")
        eq(0, dxcc.progress.basisPoints, "DXCC progress basis points")
        eq(0.0, dxcc.progress.percent, "DXCC progress percent")
        checkThat(
            dxcc.warnings.any { it.contains("does not establish sponsor claimability") },
            "local-vs-sponsor warning",
        )

        val iota = cards.card("IOTA_100")
        checkThat(
            iota.officialSources.any { it.versionLabel == "Programme Rules published 2026-05-05" },
            "IOTA source version retained",
        )
        eq(107, iota.progress.requiredUnits, "IOTA deterministic combined denominator")
        eq(0, iota.progress.completedUnits, "empty IOTA progress")
        eq(7, iota.missingCoverage.size, "empty IOTA missing all required continents")

        val pota = cards.card("POTA_BRONZE_HUNTER")
        eq(OfficialAwardClaimMechanism.AUTOMATIC_PROGRAM_ISSUE, pota.claimMechanism, "POTA claim mechanism")
        checkThat(pota.claimUrl == null, "automatic POTA award has no invented claim URL")
        checkThat(
            pota.warnings.any { it.contains("Sponsor program validation is external") },
            "POTA external validation warning",
        )
    }

    private fun distinctTargetProgressIsDeterministic() {
        val was = OfficialAwardCatalog.require("ARRL_WAS_BASIC")
        val states = was.requirement.targetUniverse!!.sorted().take(25)
        val qsos = states.mapIndexed { index, _ -> qso(1000L + index, band = "20m") }
        val targets = states.mapIndexed { index, state ->
            evidence(1000L + index, OfficialAwardTargetKind.US_STATE, state)
        }
        val confirmations = qsos.map { AwardConfirmationEvidence(it.id, "LOTW") }

        val card = service().project(
            qsos,
            AwardTargetEvidenceIndex(targets),
            confirmations,
        ).card("ARRL_WAS_BASIC")

        eq(25, card.workedCount, "WAS worked count")
        eq(25, card.confirmedCount, "WAS confirmed count")
        eq(false, card.localThresholdMet, "WAS half threshold")
        eq(25, card.remainingTargets.size, "WAS remaining targets")
        eq(25, card.progress.completedUnits, "WAS numerator")
        eq(50, card.progress.requiredUnits, "WAS denominator")
        eq(5000, card.progress.basisPoints, "WAS exact half progress")
        eq(50.0, card.progress.percent, "WAS percent")
        eq(AwardThresholdBasis.CONFIRMED, card.thresholdBasis, "WAS threshold basis")
        checkThat(card.officialConditions.any { it.contains("50 miles") }, "WAS sponsor-only condition visible")
        checkThat(card.warnings.any { it.contains("normalized award target evidence") }, "WAS enrichment warning")
    }

    private fun modeAndBandViewsFilterWithoutChangingRules() {
        val qsos = mutableListOf<QsoRecord>()
        val targets = mutableListOf<AwardTargetEvidence>()
        val confirmations = mutableListOf<AwardConfirmationEvidence>()

        for (n in 1..100) {
            val id = 2000L + n
            val mode = if (n <= 50) "CW" else "MFSK"
            val submode = if (n <= 50) null else "FT8"
            val band = if (n % 2 == 0) "20m" else "40m"
            qsos += qso(id, band = band, mode = mode, submode = submode)
            targets += evidence(id, OfficialAwardTargetKind.DXCC_ENTITY, n.toString())
            confirmations += AwardConfirmationEvidence(id, "LOTW")
        }

        val service = service()
        val mixed = service.project(
            qsos,
            AwardTargetEvidenceIndex(targets),
            confirmations,
        ).card("ARRL_DXCC_MIXED")
        eq(10000, mixed.progress.basisPoints, "mixed DXCC complete")
        eq(true, mixed.localThresholdMet, "mixed DXCC threshold")

        val cw = service.project(
            qsos,
            AwardTargetEvidenceIndex(targets),
            confirmations,
            query = AwardsCenterQuery(modeView = AwardsCenterModeView.CW),
        ).card("ARRL_DXCC_MIXED")
        eq(50, cw.confirmedCount, "CW DXCC count")
        eq(5000, cw.progress.basisPoints, "CW DXCC percent")
        eq(false, cw.localThresholdMet, "CW filtered threshold")

        val digital = service.project(
            qsos,
            AwardTargetEvidenceIndex(targets),
            confirmations,
            query = AwardsCenterQuery(modeView = AwardsCenterModeView.DIGITAL),
        ).card("ARRL_DXCC_MIXED")
        eq(50, digital.confirmedCount, "digital DXCC count")
        eq(5000, digital.progress.basisPoints, "digital DXCC percent")

        val twenty = service.project(
            qsos,
            AwardTargetEvidenceIndex(targets),
            confirmations,
            query = AwardsCenterQuery(band = "20M"),
        ).card("ARRL_DXCC_MIXED")
        eq("20m", twenty.query.normalizedBand, "band normalized")
        eq(50, twenty.confirmedCount, "20m DXCC count")
        eq(5000, twenty.progress.basisPoints, "20m DXCC percent")

        val digitalTwenty = service.project(
            qsos,
            AwardTargetEvidenceIndex(targets),
            confirmations,
            query = AwardsCenterQuery(
                modeView = AwardsCenterModeView.DIGITAL,
                band = "20m",
            ),
        ).card("ARRL_DXCC_MIXED")
        eq(25, digitalTwenty.confirmedCount, "digital 20m intersection")
        eq(2500, digitalTwenty.progress.basisPoints, "digital 20m percent")
    }

    private fun dateRangeFiltersProjectionInput() {
        val continents = listOf(
            "AF" to "20260101",
            "AS" to "20260201",
            "EU" to "20260301",
            "NA" to "20260401",
            "OC" to "20260501",
            "SA" to "20260601",
        )
        val qsos = continents.mapIndexed { index, (_, date) ->
            qso(3000L + index, date = date)
        }
        val targets = continents.mapIndexed { index, (continent, _) ->
            evidence(3000L + index, OfficialAwardTargetKind.CONTINENT, continent)
        }
        val confirmations = qsos.map { AwardConfirmationEvidence(it.id, "LOTW") }

        val card = service().project(
            qsos,
            AwardTargetEvidenceIndex(targets),
            confirmations,
            query = AwardsCenterQuery(
                dateRange = AwardsCenterDateRange(
                    startUtcDate = "2026-02-01",
                    endUtcDate = "2026-05-01",
                )
            ),
        ).card("IARU_WAC_BASIC")

        eq(4, card.confirmedCount, "date range is inclusive")
        eq(6666, card.progress.basisPoints, "four of six WAC continents")
        eq(
            setOf("AFRICA", "SOUTH_AMERICA"),
            card.remainingTargets,
            "date-filtered remaining WAC continents",
        )
        eq(false, card.localThresholdMet, "date-filtered WAC incomplete")

        val singleDay = service().project(
            qsos,
            AwardTargetEvidenceIndex(targets),
            confirmations,
            query = AwardsCenterQuery(
                dateRange = AwardsCenterDateRange("2026-03-01", "2026-03-01")
            ),
        ).card("IARU_WAC_BASIC")
        eq(1, singleDay.confirmedCount, "single-day inclusive range")

        expectFailure("date format validation") {
            AwardsCenterDateRange("20260301", null)
        }
        expectFailure("date ordering validation") {
            AwardsCenterDateRange("2026-06-01", "2026-01-01")
        }
        expectFailure("blank band filter") {
            AwardsCenterQuery(band = " ")
        }
    }

    private fun triplePlayModeViewSeparatesViewCompletionFromAwardThreshold() {
        val triple = OfficialAwardCatalog.require("ARRL_TRIPLE_PLAY_WAS")
        val states = triple.requirement.requiredCoverage.sorted()
        val qsos = states.mapIndexed { index, _ ->
            qso(4000L + index, mode = "CW", date = "20260101")
        }
        val targets = states.mapIndexed { index, state ->
            evidence(4000L + index, OfficialAwardTargetKind.US_STATE, state)
        }
        val confirmations = qsos.map { AwardConfirmationEvidence(it.id, "LOTW") }
        val service = service()

        val mixed = service.project(
            qsos,
            AwardTargetEvidenceIndex(targets),
            confirmations,
        ).card("ARRL_TRIPLE_PLAY_WAS")
        eq(50, mixed.progress.completedUnits, "mixed Triple Play completed cells")
        eq(150, mixed.progress.requiredUnits, "mixed Triple Play required cells")
        eq(3333, mixed.progress.basisPoints, "mixed Triple Play one-third progress")
        eq(false, mixed.localThresholdMet, "CW leg alone is not full Triple Play")
        eq(100, mixed.remainingCells.size, "mixed Triple Play missing phone/digital cells")

        val cw = service.project(
            qsos,
            AwardTargetEvidenceIndex(targets),
            confirmations,
            query = AwardsCenterQuery(modeView = AwardsCenterModeView.CW),
        ).card("ARRL_TRIPLE_PLAY_WAS")
        eq(50, cw.progress.completedUnits, "CW view completed cells")
        eq(50, cw.progress.requiredUnits, "CW view denominator")
        eq(10000, cw.progress.basisPoints, "CW leg view complete")
        eq(100.0, cw.progress.percent, "CW leg percent")
        eq(false, cw.localThresholdMet, "view completion does not become overall award threshold")
        eq(emptySet<String>(), cw.remainingCells, "CW view has no missing cells")
        eq(null, cw.claimableNow, "view completion does not infer claimability")

        val digital = service.project(
            qsos,
            AwardTargetEvidenceIndex(targets),
            confirmations,
            query = AwardsCenterQuery(modeView = AwardsCenterModeView.DIGITAL),
        ).card("ARRL_TRIPLE_PLAY_WAS")
        eq(0, digital.progress.completedUnits, "empty digital leg")
        eq(50, digital.progress.requiredUnits, "digital leg denominator")
        eq(0, digital.progress.basisPoints, "digital leg percent")
        eq(50, digital.remainingCells.size, "digital missing state cells")
    }

    private fun iotaCompositeProgressIncludesCoverage() {
        val continents = listOf("AF", "AN", "AS", "EU", "NA", "OC", "SA")
        val qsos = mutableListOf<QsoRecord>()
        val targets = mutableListOf<AwardTargetEvidence>()
        val confirmations = mutableListOf<AwardConfirmationEvidence>()

        for (n in 1..100) {
            val id = 5000L + n
            val continent = if (n <= 7) continents[n - 1] else "EU"
            val group = continent + "-" + n.toString().padStart(3, '0')
            qsos += qso(id, date = "20260101")
            targets += evidence(id, OfficialAwardTargetKind.IOTA_GROUP, group)
            targets += evidence(id, OfficialAwardTargetKind.CONTINENT, continent)
            confirmations += AwardConfirmationEvidence(id, "LOTW")
        }

        val complete = service().project(
            qsos,
            AwardTargetEvidenceIndex(targets),
            confirmations,
        ).card("IOTA_100")
        eq(100, complete.confirmedCount, "IOTA confirmed groups")
        eq(107, complete.progress.completedUnits, "IOTA target plus coverage units")
        eq(107, complete.progress.requiredUnits, "IOTA deterministic denominator")
        eq(10000, complete.progress.basisPoints, "IOTA complete percent")
        eq(true, complete.localThresholdMet, "IOTA local threshold complete")
        eq(emptySet<String>(), complete.missingCoverage, "IOTA no missing coverage")
        eq(null, complete.claimableNow, "IOTA local completion is not sponsor claimability")

        val withoutAntarcticaTargets = targets.filterNot {
            it.kind == OfficialAwardTargetKind.CONTINENT && it.normalizedValue == "ANTARCTICA"
        }
        val incomplete = service().project(
            qsos,
            AwardTargetEvidenceIndex(withoutAntarcticaTargets),
            confirmations,
        ).card("IOTA_100")
        eq(106, incomplete.progress.completedUnits, "IOTA missing one coverage unit")
        eq(9906, incomplete.progress.basisPoints, "IOTA deterministic incomplete percent")
        eq(setOf("ANTARCTICA"), incomplete.missingCoverage, "IOTA missing coverage surfaced")
        eq(false, incomplete.localThresholdMet, "IOTA coverage still required")
    }

    private fun sponsorStandingNeverComesFromLocalThreshold() {
        val qsos = (1..100).map { n -> qso(6000L + n) }
        val targets = (1..100).map { n ->
            evidence(6000L + n, OfficialAwardTargetKind.DXCC_ENTITY, n.toString())
        }
        val confirmations = qsos.map { AwardConfirmationEvidence(it.id, "LOTW") }

        val unknown = service().project(
            qsos,
            AwardTargetEvidenceIndex(targets),
            confirmations,
        ).card("ARRL_DXCC_MIXED")
        eq(true, unknown.localThresholdMet, "DXCC local threshold complete")
        eq(OfficialAwardStanding.UNKNOWN, unknown.sponsorStanding, "threshold does not write sponsor state")
        eq(null, unknown.claimableNow, "threshold does not infer claimable now")
        checkThat(
            unknown.warnings.any { it.contains("Sponsor standing is unknown") },
            "unknown sponsor warning",
        )

        val eligible = service().project(
            qsos,
            AwardTargetEvidenceIndex(targets),
            confirmations,
            sponsorStandings = listOf(
                OfficialAwardStandingRecord(
                    awardId = "ARRL_DXCC_MIXED",
                    standing = OfficialAwardStanding.ELIGIBLE_NOT_CLAIMED,
                    sponsorReference = "sponsor-eligibility-fixture",
                    recordedAtUtcMillis = 100L,
                )
            ),
        ).card("ARRL_DXCC_MIXED")
        eq(OfficialAwardStanding.ELIGIBLE_NOT_CLAIMED, eligible.sponsorStanding, "explicit eligible standing")
        eq(true, eligible.claimableNow, "explicit sponsor eligibility gives claimable now")
        eq("sponsor-eligibility-fixture", eligible.sponsorReference, "sponsor reference preserved")

        val submitted = service().project(
            qsos,
            AwardTargetEvidenceIndex(targets),
            confirmations,
            sponsorStandings = listOf(
                OfficialAwardStandingRecord(
                    awardId = "ARRL_DXCC_MIXED",
                    standing = OfficialAwardStanding.SUBMITTED,
                    sponsorReference = "submission-fixture",
                    recordedAtUtcMillis = 200L,
                )
            ),
        ).card("ARRL_DXCC_MIXED")
        eq(OfficialAwardStanding.SUBMITTED, submitted.sponsorStanding, "submitted standing")
        eq(false, submitted.claimableNow, "submitted is not claimable-now state")

        val awarded = service().project(
            qsos,
            AwardTargetEvidenceIndex(targets),
            confirmations,
            sponsorStandings = listOf(
                OfficialAwardStandingRecord(
                    awardId = "ARRL_DXCC_MIXED",
                    standing = OfficialAwardStanding.AWARDED,
                    sponsorReference = "award-fixture",
                    recordedAtUtcMillis = 300L,
                )
            ),
        ).card("ARRL_DXCC_MIXED")
        eq(OfficialAwardStanding.AWARDED, awarded.sponsorStanding, "awarded standing")
        eq(false, awarded.claimableNow, "awarded is not claimable now")
        eq(300L, awarded.sponsorStandingRecordedAtUtcMillis, "standing time preserved")
    }

    private fun externalScoringNeverFabricatesProgress() {
        val sota = service().project(
            qsos = listOf(qso(7000)),
            targets = AwardTargetEvidenceIndex(emptyList()),
            sponsorStandings = listOf(
                OfficialAwardStandingRecord(
                    awardId = "SOTA_SHACK_SLOTH_1000",
                    standing = OfficialAwardStanding.UNKNOWN,
                )
            ),
            query = AwardsCenterQuery(
                modeView = AwardsCenterModeView.CW,
                band = "20m",
                dateRange = AwardsCenterDateRange("2026-01-01", "2026-12-31"),
            ),
        ).card("SOTA_SHACK_SLOTH_1000")

        eq(AwardsCenterLocalEvaluationStatus.EXTERNAL_PROGRAM_SCORING, sota.localEvaluationStatus, "SOTA external status")
        eq(AwardsCenterProgressKind.EXTERNAL_UNAVAILABLE, sota.progress.kind, "SOTA progress unavailable")
        eq(null, sota.progress.completedUnits, "SOTA no fabricated numerator")
        eq(null, sota.progress.requiredUnits, "SOTA no fabricated denominator")
        eq(null, sota.progress.basisPoints, "SOTA no fabricated percent")
        eq(null, sota.workedCount, "SOTA no local worked count")
        eq(null, sota.confirmedCount, "SOTA no local confirmed count")
        eq(null, sota.localThresholdMet, "SOTA no local threshold assertion")
        checkThat(
            sota.warnings.any { it.contains("external program scoring") },
            "SOTA external scoring warning",
        )
        checkThat(sota.officialSources.isNotEmpty(), "SOTA still exposes official sources")
        checkThat(sota.informationUrl.startsWith("https://"), "SOTA official info URL retained")
    }

    private fun latestSponsorStandingIsDeterministicAndFailsOnConflict() {
        val service = service()
        val newer = service.project(
            emptyList(),
            AwardTargetEvidenceIndex(emptyList()),
            sponsorStandings = listOf(
                OfficialAwardStandingRecord(
                    "ARRL_WAS_BASIC",
                    OfficialAwardStanding.ELIGIBLE_NOT_CLAIMED,
                    sponsorReference = "old",
                    recordedAtUtcMillis = 10L,
                ),
                OfficialAwardStandingRecord(
                    "ARRL_WAS_BASIC",
                    OfficialAwardStanding.SUBMITTED,
                    sponsorReference = "new",
                    recordedAtUtcMillis = 20L,
                ),
            ),
        ).card("ARRL_WAS_BASIC")
        eq(OfficialAwardStanding.SUBMITTED, newer.sponsorStanding, "latest sponsor standing wins")
        eq("new", newer.sponsorReference, "latest sponsor reference wins")

        expectFailure("same-time conflicting sponsor states fail closed") {
            service.project(
                emptyList(),
                AwardTargetEvidenceIndex(emptyList()),
                sponsorStandings = listOf(
                    OfficialAwardStandingRecord(
                        "ARRL_WAS_BASIC",
                        OfficialAwardStanding.ELIGIBLE_NOT_CLAIMED,
                        sponsorReference = "a",
                        recordedAtUtcMillis = 20L,
                    ),
                    OfficialAwardStandingRecord(
                        "ARRL_WAS_BASIC",
                        OfficialAwardStanding.SUBMITTED,
                        sponsorReference = "b",
                        recordedAtUtcMillis = 20L,
                    ),
                ),
            )
        }
    }

    private fun projectionDoesNotMutateQsoOrMoveRulesIntoCards() {
        val original = qso(
            8000,
            call = "K1ABC",
            band = "40m",
            mode = "MFSK",
            submode = "FT8",
            date = "20261006",
        )
        val before = original.copy()

        val cards = service().project(
            listOf(original),
            AwardTargetEvidenceIndex(
                listOf(
                    evidence(original.id, OfficialAwardTargetKind.CONTINENT, "NA"),
                    evidence(original.id, OfficialAwardTargetKind.DXCC_ENTITY, "291"),
                )
            ),
            listOf(AwardConfirmationEvidence(original.id, "LOTW")),
            query = AwardsCenterQuery(modeView = AwardsCenterModeView.DIGITAL),
        )

        eq(before, original, "projection must not rewrite QSO")
        eq(1, cards.card("IARU_WAC_BASIC").confirmedCount, "digital WAC contribution")
        eq(1, cards.card("ARRL_DXCC_MIXED").confirmedCount, "digital DXCC contribution")
        eq(
            OfficialAwardCatalog.require("ARRL_DXCC_MIXED").requirement.additionalConditions,
            cards.card("ARRL_DXCC_MIXED").officialConditions,
            "projection forwards catalog conditions instead of recoding them",
        )
        eq(
            OfficialAwardCatalog.require("ARRL_DXCC_MIXED").sources.size,
            cards.card("ARRL_DXCC_MIXED").officialSources.size,
            "projection forwards catalog sources",
        )
    }

    private fun service() = AwardsCenterProjectionService()

    private fun List<AwardsCenterAwardCard>.card(id: String): AwardsCenterAwardCard =
        single { it.awardId == id }

    private fun evidence(
        qsoId: Long,
        kind: OfficialAwardTargetKind,
        value: String,
    ) = AwardTargetEvidence(
        qsoId = qsoId,
        kind = kind,
        value = value,
        provenance = AwardTargetProvenance(
            sourceId = "PROJECTION_FIXTURE",
            sourceVersion = "v1",
            sourceUrl = "https://example.invalid/projection-fixture",
            reference = "qso-$qsoId",
        ),
    )

    private fun qso(
        id: Long,
        call: String = "W1AW",
        band: String = "20m",
        mode: String = "CW",
        submode: String? = null,
        date: String = "20261006",
    ) = QsoRecord(
        id = id,
        call = call,
        stationCallsign = "N0PNG",
        qsoDate = date,
        timeOn = "120000",
        band = band,
        mode = mode,
        submode = submode,
        sourceProvider = if (submode != null) "SYNTHETIC" else null,
    )
}
