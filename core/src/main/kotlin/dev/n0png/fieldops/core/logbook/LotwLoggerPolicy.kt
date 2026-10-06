package dev.n0png.fieldops.core.logbook

enum class LotwAutoQueueMode {
    DISABLED,
    MANUAL_SSB_CW_AND_COMPLETED_DIGITAL,
}

/**
 * Logger-only policy. It decides whether a successfully saved local QSO should
 * be copied into the local LoTW queue. It never performs signing or networking.
 */
data class LotwLoggerPolicy(
    val autoQueueMode: LotwAutoQueueMode = LotwAutoQueueMode.DISABLED,
) {
    fun shouldAutoQueue(qso: QsoRecord): Boolean = when (autoQueueMode) {
        LotwAutoQueueMode.DISABLED -> false
        LotwAutoQueueMode.MANUAL_SSB_CW_AND_COMPLETED_DIGITAL -> when (qso.origin) {
            QsoOrigin.MANUAL -> qso.mode.trim().uppercase() in setOf("SSB", "CW")
            QsoOrigin.DIGITAL_AUTO ->
                !qso.sourceProvider.isNullOrBlank() &&
                    !qso.stationProfileId.isNullOrBlank() &&
                    !qso.sessionId.isNullOrBlank()
            QsoOrigin.LEGACY,
            QsoOrigin.IMPORTED -> false
        }
    }

    companion object {
        val DISABLED = LotwLoggerPolicy()
        val AUTO_MANUAL_AND_DIGITAL = LotwLoggerPolicy(
            LotwAutoQueueMode.MANUAL_SSB_CW_AND_COMPLETED_DIGITAL
        )
    }
}

/**
 * Queue failure reporting is separated from the authoritative local save.
 * The default is intentionally quiet; UI/application layers can inject a
 * visible error sink without making contact logging depend on LoTW.
 */
fun interface LotwQueueFailureHandler {
    fun onFailure(qso: QsoRecord, error: Throwable)

    companion object {
        val IGNORE = LotwQueueFailureHandler { _, _ -> }
    }
}
