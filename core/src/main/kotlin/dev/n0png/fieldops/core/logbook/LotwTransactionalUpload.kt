package dev.n0png.fieldops.core.logbook

enum class LotwTransactionOutcome {
    ACCEPTED,
    SIGNING_FAILED,
    UPLOAD_FAILED,
    UPLOAD_REJECTED,
    VERIFICATION_FAILED,
}

data class LotwUploadContext(
    val stationProfile: LotwStationProfile,
    val tqslStationLocationName: String,
    val credentials: LotwCredentials,
    val acceptedSince: String? = null,
) {
    init {
        require(tqslStationLocationName.isNotBlank()) {
            "TrustedQSL station location must be explicit"
        }
    }
}

data class LotwTransactionalUploadResult(
    val outcome: LotwTransactionOutcome,
    val qsos: List<QsoRecord>,
    val uploadResponse: LotwUploadResponse? = null,
    val acceptedReport: LotwReport? = null,
) {
    val committed: Boolean
        get() = outcome == LotwTransactionOutcome.ACCEPTED
}

/**
 * Sleep/delay abstraction so acceptance polling is testable without waiting.
 */
fun interface LotwVerificationDelay {
    fun sleep(millis: Long)

    companion object {
        val SYSTEM = LotwVerificationDelay { millis ->
            if (millis > 0) Thread.sleep(millis)
        }
    }
}

data class LotwAcceptancePolicy(
    val attempts: Int = 3,
    val initialDelayMillis: Long = LotwSyncPolicy.VERIFY_AFTER_UPLOAD_MILLIS,
    val retryDelayMillis: Long = LotwSyncPolicy.VERIFY_AFTER_UPLOAD_MILLIS,
) {
    init {
        require(attempts >= 1)
        require(initialDelayMillis >= 0)
        require(retryDelayMillis >= 0)
    }
}

/**
 * Mode-neutral local LoTW queue.
 *
 * This queue deliberately knows nothing about "manual" versus "digital".
 * SSB/CW and decoded digital QSOs enter the same state machine. Persistence
 * will move behind this contract when the Room-backed app shell is added.
 */
class LotwUploadQueue {
    data class Entry(
        val qso: QsoRecord,
        val stationProfileId: String,
        val attempt: Int = 0,
    )

    private val entries = linkedMapOf<Long, Entry>()

    @Synchronized
    fun enqueue(qso: QsoRecord, stationProfileId: String): Entry {
        require(stationProfileId.isNotBlank())
        require(qso.lotwUpload != LotwUploadState.ACCEPTED) {
            "Accepted QSO must not be re-enqueued"
        }
        val queued = qso.copy(lotwUpload = LotwUploadState.QUEUED)
        val entry = Entry(queued, stationProfileId, entries[qso.id]?.attempt ?: 0)
        entries[qso.id] = entry
        return entry
    }

    @Synchronized
    fun pending(stationProfileId: String? = null): List<Entry> =
        entries.values.filter {
            (stationProfileId == null || it.stationProfileId == stationProfileId) &&
                it.qso.lotwUpload != LotwUploadState.ACCEPTED
        }

    @Synchronized
    fun applyResult(result: LotwTransactionalUploadResult) {
        for (qso in result.qsos) {
            val old = entries[qso.id] ?: continue
            if (qso.lotwUpload == LotwUploadState.ACCEPTED) {
                entries.remove(qso.id)
            } else {
                entries[qso.id] = old.copy(
                    qso = qso,
                    attempt = old.attempt + 1,
                )
            }
        }
    }

    @Synchronized
    fun size(): Int = entries.size
}
