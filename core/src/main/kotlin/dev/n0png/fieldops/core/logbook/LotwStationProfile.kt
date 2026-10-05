package dev.n0png.fieldops.core.logbook

data class LotwStationProfile(
    val id: String,
    val name: String,
    val stationCallsign: String,
    val gridSquare: String,
    val dxcc: Int,
    val cqZone: Int,
    val ituZone: Int,
    val country: String,
    val state: String? = null,
    val county: String? = null
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
        require(stationCallsign.isNotBlank())
        require(gridSquare.length >= 4)
        require(dxcc > 0)
        require(cqZone in 1..40)
        require(ituZone in 1..90)
    }
}

object LotwSyncPolicy {
    const val VERIFY_AFTER_UPLOAD_MILLIS = 30_000L
    const val RETRY_BASE_MILLIS = 5 * 60_000L
    const val DEFAULT_CONFIRMATION_INTERVAL_MILLIS = 6 * 60 * 60_000L
    const val MAX_RETRY_DELAY_MILLIS = 6 * 60 * 60_000L

    fun retryDelayMillis(attempt: Int): Long {
        val safe = attempt.coerceIn(0, 8)
        return (RETRY_BASE_MILLIS * (1L shl safe)).coerceAtMost(MAX_RETRY_DELAY_MILLIS)
    }
}
