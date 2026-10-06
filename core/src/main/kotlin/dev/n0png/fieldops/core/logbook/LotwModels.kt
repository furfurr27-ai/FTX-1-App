package dev.n0png.fieldops.core.logbook

enum class LotwUploadState { NOT_UPLOADED, QUEUED, SUBMITTED, ACCEPTED, REJECTED }
enum class LotwConfirmationState { UNKNOWN, NOT_CONFIRMED, CONFIRMED }

data class QsoRecord(
    val id: Long,
    val call: String,
    val stationCallsign: String,
    val qsoDate: String,      // YYYYMMDD UTC
    val timeOn: String,       // HHMMSS UTC
    val band: String,
    val mode: String,
    val freqMhz: String? = null,
    val grid: String? = null,
    val rstSent: String? = null,
    val rstRcvd: String? = null,
    val myGridSquare: String? = null,
    val myDxcc: Int? = null,
    val myCqZone: Int? = null,
    val myItuZone: Int? = null,
    val myCountry: String? = null,
    val myState: String? = null,
    val lotwUpload: LotwUploadState = LotwUploadState.NOT_UPLOADED,
    val lotwConfirmation: LotwConfirmationState = LotwConfirmationState.UNKNOWN,
    val lotwRxQso: String? = null,
    val lotwRxQsl: String? = null,
    val lotwQslDate: String? = null,
    val lotwRecordId: String? = null,

    // CP-0005A universal local-log fields. Legacy LoTW constructors remain valid.
    val submode: String? = null,
    val radioMode: String? = null,
    val frequencyHz: Long? = null,
    val startedUtcMillis: Long? = null,
    val endedUtcMillis: Long? = null,
    val qsoDateOff: String? = null,
    val timeOff: String? = null,
    val stationProfileId: String? = null,
    val sessionId: String? = null,
    val remoteLocation: QsoLocationSnapshot? = null,
    val stationLocation: QsoLocationSnapshot? = null,
    val origin: QsoOrigin = QsoOrigin.LEGACY,
    val sourceProvider: String? = null,
    val notes: String? = null,
    val txPowerWatts: Double? = null,
) {
    init {
        require(call.isNotBlank()) { "QSO callsign must not be blank" }
        require(stationCallsign.isNotBlank()) { "Station callsign must not be blank" }
        require(qsoDate.length == 8 && qsoDate.all(Char::isDigit)) {
            "QSO date must be UTC YYYYMMDD"
        }
        require(timeOn.length >= 4 && timeOn.take(6).all(Char::isDigit)) {
            "QSO time must be UTC HHMM or HHMMSS"
        }
        require(band.isNotBlank()) { "QSO band must not be blank" }
        require(mode.isNotBlank()) { "QSO mode must not be blank" }
        require(submode == null || submode.isNotBlank()) { "QSO submode must not be blank" }
        require(radioMode == null || radioMode.isNotBlank()) { "Radio mode must not be blank" }
        frequencyHz?.let { require(it > 0) { "QSO frequency must be positive" } }
        startedUtcMillis?.let { require(it >= 0) { "QSO UTC start must be non-negative" } }
        endedUtcMillis?.let { end ->
            require(end >= 0) { "QSO UTC end must be non-negative" }
            startedUtcMillis?.let { start ->
                require(end >= start) { "QSO end must not precede start" }
            }
        }
        txPowerWatts?.let { require(it > 0.0) { "QSO TX power must be positive" } }

        val legacyRemoteGrid = grid?.trim()?.uppercase()
        val snapshotRemoteGrid = remoteLocation?.normalizedGrid
        if (legacyRemoteGrid != null && snapshotRemoteGrid != null) {
            require(legacyRemoteGrid == snapshotRemoteGrid) {
                "Legacy remote grid and location snapshot disagree"
            }
        }

        val legacyStationGrid = myGridSquare?.trim()?.uppercase()
        val snapshotStationGrid = stationLocation?.normalizedGrid
        if (legacyStationGrid != null && snapshotStationGrid != null) {
            require(legacyStationGrid == snapshotStationGrid) {
                "Legacy station grid and location snapshot disagree"
            }
        }

        val legacyMhz = freqMhz
        val exactHz = frequencyHz
        if (exactHz != null && legacyMhz != null) {
            val parsedHz = runCatching {
                java.math.BigDecimal(legacyMhz.trim())
                    .movePointRight(6)
                    .setScale(0, java.math.RoundingMode.HALF_UP)
                    .longValueExact()
            }.getOrNull()
            require(parsedHz == null || kotlin.math.abs(parsedHz - exactHz) <= 1L) {
                "Legacy MHz frequency and exact Hz frequency disagree"
            }
        }
    }

    val exactRemoteGrid: String?
        get() = remoteLocation?.normalizedGrid ?: grid?.trim()?.uppercase()

    val exactStationGrid: String?
        get() = stationLocation?.normalizedGrid ?: myGridSquare?.trim()?.uppercase()

    val exactFrequencyMhz: String?
        get() = frequencyHz?.let(FrequencyFormat::mhz) ?: freqMhz

    /** Conservative key for reconciling a LoTW ADIF report with the local log. */
    fun lotwMatchKey(): String = listOf(
        stationCallsign.trim().uppercase(), call.trim().uppercase(), qsoDate.trim(),
        timeOn.trim().take(4), band.trim().lowercase()
    ).joinToString("|")
}

data class LotwCredentials(val username: String, val webPassword: String)

data class LotwSyncCursor(
    val lastQsoRx: String? = null, // APP_LoTW_LASTQSORX
    val lastQsl: String? = null    // APP_LoTW_LASTQSL
)

data class LotwReport(
    val header: Map<String, String>,
    val records: List<Map<String, String>>
) {
    val lastQsoRx: String? get() = header["APP_LOTW_LASTQSORX"]
    val lastQsl: String? get() = header["APP_LOTW_LASTQSL"]
}

data class LotwUploadResponse(val accepted: Boolean, val message: String, val httpCode: Int)

data class LotwSyncSummary(
    val acceptedNow: Int = 0,
    val confirmedNow: Int = 0,
    val importedNow: Int = 0,
    val unmatchedRemoteRecords: Int = 0,
    val cursor: LotwSyncCursor = LotwSyncCursor()
)

interface LotwTransport {
    fun uploadTq8(payload: ByteArray, filename: String = "fieldops-upload.tq8"): LotwUploadResponse
    fun query(credentials: LotwCredentials, params: Map<String, String>): String
}
