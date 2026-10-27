package dev.n0png.fieldops.app

import dev.n0png.fieldops.core.logbook.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Safe local restore ONLY for FieldOps-exported ADIF, not arbitrary LoTW reports.
 * Always stage records first. A separate human confirmation merges into RAM.
 * Deliberately resets LoTW and evidence provenance to UNKNOWN/NOT_UPLOADED.
 */
internal object FieldOpsAdifRestore {
    private const val MAX_BYTES = 1_000_000
    private const val MAX_RECORDS = 500
    private const val PROGRAM_ID = "FTX1_FieldOps"
    private val allowedFields = setOf(
        "CALL", "STATION_CALLSIGN", "QSO_DATE", "TIME_ON", "BAND", "MODE",
        "SUBMODE", "FREQ", "GRIDSQUARE", "RST_SENT", "RST_RCVD",
        "QSO_DATE_OFF", "TIME_OFF", "MY_GRIDSQUARE", "MY_DXCC",
        "MY_CQ_ZONE", "MY_ITU_ZONE", "MY_COUNTRY", "MY_STATE"
    )
    private val callsign = Regex("[A-Z0-9/]{3,16}")
    private val grid = Regex("[A-R]{2}[0-9]{2}(?:[A-X]{2}(?:[0-9]{2})?)?")
    private val number = Regex("[0-9]{8}")
    private val time = Regex("[0-9]{6}")
    private val basicDate = DateTimeFormatter.BASIC_ISO_DATE

    fun read(input: InputStream, expectedStation: String): List<QsoRecord> {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            require(n > 0) { "Non-progressing ADIF source" }
            require(bytes.size() + n <= MAX_BYTES) { "ADIF exceeds 1 MB local restore limit" }
            bytes.write(buffer, 0, n)
        }
        val raw = bytes.toByteArray()
        require(raw.isNotEmpty()) { "Empty ADIF document" }
        // FieldOps exports ASCII metadata. Fail closed rather than treating UTF-8
        // char counts as ADIF byte lengths or silently dropping unsupported fields.
        require(raw.all { (it.toInt() and 0xff) < 128 }) {
            "Non-ASCII ADIF requires explicit conversion; not imported"
        }
        val text = raw.toString(Charsets.US_ASCII)
        require(Regex("(?i)<EOH>").findAll(text).count() == 1) { "Invalid ADIF header" }
        val report = AdifCodec.parse(text)
        require(report.header["PROGRAMID"] == PROGRAM_ID) {
            "Only FieldOps-exported ADIF can be restored here"
        }
        val eorCount = Regex("(?i)<EOR>").findAll(text).count()
        require(eorCount in 1..MAX_RECORDS && eorCount == report.records.size) {
            "ADIF must contain 1..500 complete records"
        }
        require(text.trimEnd().endsWith("<EOR>", ignoreCase = true)) {
            "ADIF contains incomplete trailing record"
        }
        val localCall = expectedStation.trim().uppercase(Locale.ROOT)
        require(callsign.matches(localCall)) { "Invalid active station callsign" }
        return report.records.mapIndexed { index, fields ->
            try {
                require(fields.keys.all { it in allowedFields }) { "Unsupported field would be lost" }
                fun req(k: String): String = fields[k]?.trim()?.uppercase(Locale.ROOT)
                    ?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalArgumentException("Missing field $k")
                val remote = req("CALL")
                val station = req("STATION_CALLSIGN")
                require(callsign.matches(remote) && callsign.matches(station)) { "Invalid callsign" }
                require(station == localCall) { "Station identity mismatch" }
                val date = req("QSO_DATE")
                val at = req("TIME_ON")
                val band = req("BAND").lowercase(Locale.ROOT)
                val mode = req("MODE")
                require(mode in setOf("CW","SSB","FT8","FT4","FT2")) {
                    "Mode must be a manually supported FieldOps mode"
                }
                require(fields["SUBMODE"].isNullOrBlank()) { "Unsupported submode" }
                require(number.matches(date) && time.matches(at)) { "Bad date or UTC time format" }
                val start = parseUtc(date, at)
                val freq = req("FREQ")
                val hz = BigDecimal(freq).movePointRight(6).longValueExact()
                require(hz > 0 && AmateurBandCatalog.bandFor(hz) == band) {
                    "Frequency/band disagreement"
                }
                val endDate = fields["QSO_DATE_OFF"]?.trim()?.takeIf { it.isNotEmpty() }
                val endTime = fields["TIME_OFF"]?.trim()?.takeIf { it.isNotEmpty() }
                require((endDate == null) == (endTime == null)) { "Incomplete end UTC" }
                val end = if (endDate != null && endTime != null) parseUtc(endDate, endTime) else null
                require(end == null || end >= start) { "End UTC precedes contact" }
                val remoteGrid = fields["GRIDSQUARE"]?.trim()?.uppercase(Locale.ROOT)
                val stationGrid = fields["MY_GRIDSQUARE"]?.trim()?.uppercase(Locale.ROOT)
                for (g in listOfNotNull(remoteGrid, stationGrid))
                    require(grid.matches(g)) { "Invalid Maidenhead locator" }
                fun optInt(name: String): Int? {
                    val value = fields[name]?.trim() ?: return null
                    return value.toIntOrNull()
                        ?: throw IllegalArgumentException("Invalid numeric field $name")
                }
                val record = QsoRecord(
                    id = 0L, call = remote, stationCallsign = station, qsoDate = date,
                    timeOn = at, band = band, mode = mode, freqMhz = FrequencyFormat.mhz(hz),
                    frequencyHz = hz, startedUtcMillis = start, endedUtcMillis = end,
                    qsoDateOff = endDate, timeOff = endTime,
                    grid = remoteGrid, myGridSquare = stationGrid,
                    rstSent = fields["RST_SENT"]?.trim()?.takeIf { it.isNotEmpty() },
                    rstRcvd = fields["RST_RCVD"]?.trim()?.takeIf { it.isNotEmpty() },
                    myDxcc = optInt("MY_DXCC"), myCqZone = optInt("MY_CQ_ZONE"),
                    myItuZone = optInt("MY_ITU_ZONE"),
                    myCountry = fields["MY_COUNTRY"]?.takeIf { it.isNotBlank() },
                    myState = fields["MY_STATE"]?.takeIf { it.isNotBlank() },
                    stationProfileId = "$localCall-local",
                    sessionId = "user-confirmed-adif-restore",
                    origin = QsoOrigin.IMPORTED,
                    sourceProvider = "FIELDOPS_ADIF",
                    lotwUpload = LotwUploadState.NOT_UPLOADED,
                    lotwConfirmation = LotwConfirmationState.UNKNOWN
                )
                record
            } catch (e: RuntimeException) {
                throw IllegalArgumentException("ADIF record ${index+1}: ${e.message}", e)
            }
        }
    }

    private fun parseUtc(date: String, utc: String): Long {
        require(number.matches(date) && time.matches(utc)) { "Bad UTC format" }
        val d = LocalDate.parse(date, basicDate)
        val t = LocalTime.of(utc.substring(0,2).toInt(), utc.substring(2,4).toInt(),
            utc.substring(4,6).toInt())
        return LocalDateTime.of(d,t).toInstant(ZoneOffset.UTC).toEpochMilli()
    }

    private fun key(q: QsoRecord): String =
        listOf(q.stationCallsign.uppercase(Locale.ROOT),
            q.call.uppercase(Locale.ROOT), q.qsoDate, q.timeOn,
            q.band.lowercase(Locale.ROOT), q.mode.uppercase(Locale.ROOT),
            q.exactFrequencyMhz.orEmpty()).joinToString("|")

    /** Called only from the explicit second confirmation on the Activity main thread. */
    fun commit(staged: List<QsoRecord>, repo: InMemoryLogbookRepository,
               ids: SequentialQsoIdSource): Int {
        require(staged.isNotEmpty() && staged.size <= MAX_RECORDS) { "Invalid restore batch" }
        require(staged.all { it.origin == QsoOrigin.IMPORTED &&
            it.lotwUpload == LotwUploadState.NOT_UPLOADED &&
            it.lotwConfirmation == LotwConfirmationState.UNKNOWN }) { "Unsafe import provenance" }
        val existing = repo.all().mapTo(mutableSetOf(), ::key)
        var added = 0
        for (record in staged) {
            if (!existing.add(key(record))) continue
            repo.save(record.copy(id = ids.nextId()))
            added++
        }
        return added
    }
}
