package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.logbook.*
import java.time.Instant

object UniversalQsoLoggerTests {
    private var assertions = 0

    private fun checkThat(value: Boolean, message: String) {
        assertions++
        check(value) { message }
    }

    private fun <T> eq(expected: T, actual: T, message: String) =
        checkThat(expected == actual, "$message expected=$expected actual=$actual")

    private fun expectFailure(message: String, block: () -> Unit) {
        val failed = runCatching(block).isFailure
        checkThat(failed, message)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        manualSsbAndCwUseOneUniversalModel()
        digitalAutoLogPreservesExactModeIdentity()
        adifPreservesExactIdentityAndFrequency()
        sessionAndLocationInvariantsFailClosed()
        repositoryAndLegacyCompatibility()
        bandAndFrequencyHelpers()
        println("Universal QSO/logger tests: PASS assertions=$assertions")
    }

    private fun manualSsbAndCwUseOneUniversalModel() {
        val repo = InMemoryLogbookRepository()
        val now = epoch("2026-10-06T10:15:30Z")
        val session = session()
        val logger = FastQsoLogger(repo, SequentialQsoIdSource(100), UtcMillisClock { now })

        val ssb = logger.logManual(
            ManualQsoInput(
                call = "w1aw",
                mode = ManualQsoMode.SSB,
                rstSent = "59",
                rstRcvd = "57",
                remoteLocation = QsoLocationSnapshot(
                    label = "Newington",
                    gridSquare = "FN20",
                    latitude = 41.714,
                    longitude = -72.727,
                ),
                endedUtcMillis = now + 45_000,
                notes = "manual SSB",
            ),
            session,
            QsoRadioContext(
                frequencyHz = 14_200_000L,
                radioMode = "USB",
                txPowerWatts = 10.0,
            ),
        )

        eq(100L, ssb.id, "manual SSB id")
        eq("W1AW", ssb.call, "manual SSB callsign normalization")
        eq("N0PNG", ssb.stationCallsign, "station callsign")
        eq("20261006", ssb.qsoDate, "manual SSB UTC date")
        eq("101530", ssb.timeOn, "manual SSB UTC time")
        eq("20261006", ssb.qsoDateOff, "manual SSB UTC end date")
        eq("101615", ssb.timeOff, "manual SSB UTC end time")
        eq("20m", ssb.band, "manual SSB derived band")
        eq(14_200_000L, ssb.frequencyHz, "manual SSB exact frequency")
        eq("14.2", ssb.freqMhz, "manual SSB MHz compatibility field")
        eq("SSB", ssb.mode, "manual SSB ADIF mode")
        eq(null, ssb.submode, "manual SSB submode")
        eq("USB", ssb.radioMode, "manual SSB exact radio mode")
        eq("59", ssb.rstSent, "manual SSB RST sent")
        eq("57", ssb.rstRcvd, "manual SSB RST received")
        eq("FN20", ssb.exactRemoteGrid, "manual SSB remote grid")
        eq("JN49", ssb.exactStationGrid, "manual SSB station grid")
        eq("home", ssb.stationProfileId, "manual SSB station profile")
        eq("session-20261006", ssb.sessionId, "manual SSB operating session")
        eq(QsoOrigin.MANUAL, ssb.origin, "manual SSB origin")
        eq(null, ssb.sourceProvider, "manual SSB provider")
        eq(10.0, ssb.txPowerWatts, "manual SSB power")
        eq(LotwUploadState.NOT_UPLOADED, ssb.lotwUpload, "CP-0005A must not auto-enqueue LoTW")

        val cwStart = epoch("2026-10-06T10:20:00Z")
        val cw = logger.logManual(
            ManualQsoInput(
                call = "K1ABC/P",
                mode = ManualQsoMode.CW,
                rstSent = "579",
                rstRcvd = "559",
                startedUtcMillis = cwStart,
                remoteLocation = QsoLocationSnapshot(gridSquare = "FN31"),
            ),
            session,
            QsoRadioContext(
                frequencyHz = 7_030_000L,
                radioMode = "CW",
            ),
        )

        eq(101L, cw.id, "manual CW id")
        eq("K1ABC/P", cw.call, "portable callsign preservation")
        eq("40m", cw.band, "manual CW derived band")
        eq("CW", cw.mode, "manual CW ADIF mode")
        eq("CW", cw.radioMode, "manual CW radio mode")
        eq("579", cw.rstSent, "manual CW report sent")
        eq("559", cw.rstRcvd, "manual CW report received")
        eq(QsoOrigin.MANUAL, cw.origin, "manual CW origin")
        eq(2, repo.all().size, "manual SSB/CW share one repository")
    }

    private fun digitalAutoLogPreservesExactModeIdentity() {
        val repo = InMemoryLogbookRepository()
        val logger = FastQsoLogger(repo, SequentialQsoIdSource(200))
        val session = session()

        val ft8 = logger.logDigital(
            DigitalCompletedContact(
                providerId = "FT8AF",
                call = "DL1XYZ",
                adifMode = "MFSK",
                adifSubmode = "FT8",
                startedUtcMillis = epoch("2026-10-06T10:30:15Z"),
                endedUtcMillis = epoch("2026-10-06T10:30:30Z"),
                frequencyHz = 7_074_000L,
                radioMode = "DATA-U",
                rstSent = "-10",
                rstRcvd = "-08",
                remoteLocation = QsoLocationSnapshot(gridSquare = "JO62"),
            ),
            session,
        )

        eq("MFSK", ft8.mode, "digital parent mode")
        eq("FT8", ft8.submode, "digital exact submode")
        eq("DATA-U", ft8.radioMode, "digital exact radio mode")
        eq("FT8AF", ft8.sourceProvider, "digital source provider")
        eq(QsoOrigin.DIGITAL_AUTO, ft8.origin, "digital origin")
        eq(7_074_000L, ft8.frequencyHz, "digital exact frequency")
        eq("40m", ft8.band, "digital band")
        eq("-10", ft8.rstSent, "digital report sent")
        eq("-08", ft8.rstRcvd, "digital report received")
        eq("JO62", ft8.exactRemoteGrid, "digital remote grid")
        eq("session-20261006", ft8.sessionId, "digital session")

        val js8 = logger.logDigital(
            DigitalCompletedContact(
                providerId = "JS8_NATIVE",
                call = "G0ABC",
                adifMode = "MFSK",
                adifSubmode = "JS8",
                startedUtcMillis = epoch("2026-10-06T10:31:00Z"),
                frequencyHz = 7_078_000L,
                radioMode = "DATA-U",
                band = "40m",
            ),
            session,
        )
        eq("MFSK", js8.mode, "JS8 parent mode")
        eq("JS8", js8.submode, "JS8 exact submode must not collapse")
        checkThat(js8.submode != ft8.submode, "unrelated digital submodes were collapsed")

        expectFailure("incomplete digital contact must not auto-log") {
            logger.logDigital(
                DigitalCompletedContact(
                    providerId = "FT8AF",
                    call = "F4ABC",
                    adifMode = "MFSK",
                    adifSubmode = "FT4",
                    startedUtcMillis = epoch("2026-10-06T10:32:00Z"),
                    frequencyHz = 7_047_500L,
                    radioMode = "DATA-U",
                    completed = false,
                ),
                session,
            )
        }
        eq(2, repo.all().size, "incomplete digital contact changed log")
    }

    private fun adifPreservesExactIdentityAndFrequency() {
        val repo = InMemoryLogbookRepository()
        val logger = FastQsoLogger(repo, SequentialQsoIdSource(300))
        val session = session()
        val qso = logger.logDigital(
            DigitalCompletedContact(
                providerId = "FT8AF",
                call = "JA1ABC",
                adifMode = "MFSK",
                adifSubmode = "FT8",
                startedUtcMillis = epoch("2026-10-06T10:40:00Z"),
                endedUtcMillis = epoch("2026-10-06T10:40:15Z"),
                frequencyHz = 21_074_123L,
                radioMode = "DATA-U",
                rstSent = "-15",
                rstRcvd = "-11",
                remoteLocation = QsoLocationSnapshot(gridSquare = "PM95"),
            ),
            session,
        )

        val adif = AdifCodec.export(listOf(qso))
        checkThat(adif.contains("<MODE:4>MFSK"), "ADIF lost parent mode")
        checkThat(adif.contains("<SUBMODE:3>FT8"), "ADIF lost exact submode")
        checkThat(adif.contains("<FREQ:9>21.074123"), "ADIF lost exact Hz-derived frequency")
        checkThat(adif.contains("<QSO_DATE:8>20261006"), "ADIF lost UTC date")
        checkThat(adif.contains("<TIME_ON:6>104000"), "ADIF lost UTC time")
        checkThat(adif.contains("<QSO_DATE_OFF:8>20261006"), "ADIF lost UTC end date")
        checkThat(adif.contains("<TIME_OFF:6>104015"), "ADIF lost UTC end time")
        checkThat(adif.contains("<GRIDSQUARE:4>PM95"), "ADIF lost remote grid")
        checkThat(adif.contains("<MY_GRIDSQUARE:4>JN49"), "ADIF lost station grid")
        checkThat(adif.contains("<RST_SENT:3>-15"), "ADIF lost sent report")
        checkThat(adif.contains("<RST_RCVD:3>-11"), "ADIF lost received report")
    }

    private fun sessionAndLocationInvariantsFailClosed() {
        expectFailure("session end before start must fail") {
            OperatingSession(
                id = "bad",
                stationProfileId = "home",
                stationCallsign = "N0PNG",
                startedUtcMillis = 20,
                endedUtcMillis = 10,
            )
        }

        expectFailure("partial coordinates must fail") {
            QsoLocationSnapshot(latitude = 50.0)
        }

        val session = session()
        val logger = FastQsoLogger(
            InMemoryLogbookRepository(),
            SequentialQsoIdSource(400),
            UtcMillisClock { session.startedUtcMillis - 1 },
        )
        expectFailure("manual QSO before session must fail") {
            logger.logManual(
                ManualQsoInput(call = "W1AW", mode = ManualQsoMode.SSB),
                session,
                QsoRadioContext(14_250_000L, "USB"),
            )
        }

        expectFailure("unknown frequency requires explicit band") {
            QsoRadioContext(3_000_000_000L, "DATA-U").resolvedBand
        }

        val explicit = QsoRadioContext(3_000_000_000L, "DATA-U", band = "custom")
        eq("custom", explicit.resolvedBand, "explicit uncommon band")
    }

    private fun repositoryAndLegacyCompatibility() {
        val repo = InMemoryLogbookRepository()
        val legacy = QsoRecord(
            id = 900,
            call = "W1AW",
            stationCallsign = "N0PNG",
            qsoDate = "20261006",
            timeOn = "110000",
            band = "40m",
            mode = "FT8",
            freqMhz = "7.07400",
            grid = "FN20",
            myGridSquare = "JN49",
        )
        repo.save(legacy)
        eq("7.07400", legacy.exactFrequencyMhz, "legacy MHz fallback")
        eq("FN20", legacy.exactRemoteGrid, "legacy remote grid fallback")
        eq("JN49", legacy.exactStationGrid, "legacy station grid fallback")
        eq(QsoOrigin.LEGACY, legacy.origin, "legacy origin default")

        expectFailure("duplicate QSO ids must fail") {
            repo.save(legacy.copy(call = "K1ABC"))
        }
        eq(1, repo.all().size, "duplicate save changed repository")
    }

    private fun bandAndFrequencyHelpers() {
        eq("40m", AmateurBandCatalog.bandFor(7_074_000L), "40m band helper")
        eq("20m", AmateurBandCatalog.bandFor(14_074_000L), "20m band helper")
        eq("2m", AmateurBandCatalog.bandFor(144_390_000L), "2m band helper")
        eq(null, AmateurBandCatalog.bandFor(3_000_000_000L), "unknown band helper")
        eq("7.074", FrequencyFormat.mhz(7_074_000L), "frequency MHz formatter")
        eq("21.074123", FrequencyFormat.mhz(21_074_123L), "Hz precision formatter")

        val utc = QsoUtcFields.fromEpochMillis(epoch("2026-10-06T23:59:59Z"))
        eq("20261006", utc.date, "UTC date formatter")
        eq("235959", utc.time, "UTC time formatter")
    }

    private fun session(): OperatingSession = OperatingSession(
        id = "session-20261006",
        stationProfileId = "home",
        stationCallsign = "n0png",
        startedUtcMillis = epoch("2026-10-06T09:00:00Z"),
        stationLocation = QsoLocationSnapshot(
            label = "Wiesbaden",
            gridSquare = "JN49",
            latitude = 50.0782,
            longitude = 8.2398,
        ),
        antennaNotes = "EFHW",
        defaultPowerWatts = 5.0,
        activityTags = setOf("HOME"),
    )

    private fun epoch(iso: String): Long = Instant.parse(iso).toEpochMilli()
}
