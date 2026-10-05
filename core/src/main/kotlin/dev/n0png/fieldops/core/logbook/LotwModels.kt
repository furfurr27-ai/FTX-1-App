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
    val lotwRecordId: String? = null
) {
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
