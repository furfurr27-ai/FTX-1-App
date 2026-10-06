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

data class LotwQueueEntry(
    val qso: QsoRecord,
    val stationProfileId: String,
    val attempt: Int = 0,
)

/**
 * Minimal local queue boundary used by the logger.
 *
 * It deliberately has no network or signing methods. Logger-save may place a
 * QSO into this local queue, but only the later sync worker owns LoTW network
 * behavior.
 */
fun interface LotwQueueSink {
    fun enqueue(qso: QsoRecord, stationProfileId: String): LotwQueueEntry
}

/**
 * Mode-neutral local LoTW queue.
 *
 * This queue deliberately knows nothing about "manual" versus "digital".
 * SSB/CW and decoded digital QSOs enter the same state machine. Persistence
 * will move behind this contract when the Room-backed app shell is added.
 *
 * Enqueue is idempotent by immutable local QSO id. Re-enqueueing the same
 * contact returns the existing entry without resetting attempt/state. Reusing
 * an existing id for a materially different QSO fails closed.
 */
class LotwUploadQueue : LotwQueueSink {
    private val entries = linkedMapOf<Long, LotwQueueEntry>()

    @Synchronized
    override fun enqueue(qso: QsoRecord, stationProfileId: String): LotwQueueEntry {
        val profileId = stationProfileId.trim()
        require(profileId.isNotEmpty())
        require(qso.lotwUpload != LotwUploadState.ACCEPTED) {
            "Accepted QSO must not be re-enqueued"
        }
        qso.stationProfileId?.let {
            require(it == profileId) {
                "QSO station profile and queue station profile disagree"
            }
        }

        entries[qso.id]?.let { existing ->
            require(existing.stationProfileId == profileId) {
                "QSO id " + qso.id + " is already queued for a different station profile"
            }
            require(sameImmutableQso(existing.qso, qso)) {
                "QSO id " + qso.id + " is already queued for a different contact"
            }
            return existing
        }

        val entry = LotwQueueEntry(
            qso = qso.copy(lotwUpload = LotwUploadState.QUEUED),
            stationProfileId = profileId,
            attempt = 0,
        )
        entries[qso.id] = entry
        return entry
    }

    @Synchronized
    fun pending(stationProfileId: String? = null): List<LotwQueueEntry> =
        entries.values.filter {
            (stationProfileId == null || it.stationProfileId == stationProfileId) &&
                it.qso.lotwUpload in setOf(LotwUploadState.QUEUED, LotwUploadState.SUBMITTED)
        }

    @Synchronized
    fun get(qsoId: Long): LotwQueueEntry? = entries[qsoId]

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

    private fun sameImmutableQso(existing: QsoRecord, incoming: QsoRecord): Boolean =
        existing.id == incoming.id &&
            existing.call.trim().uppercase() == incoming.call.trim().uppercase() &&
            existing.stationCallsign.trim().uppercase() == incoming.stationCallsign.trim().uppercase() &&
            existing.qsoDate == incoming.qsoDate &&
            existing.timeOn.take(6) == incoming.timeOn.take(6) &&
            existing.band.trim().lowercase() == incoming.band.trim().lowercase() &&
            existing.mode.trim().uppercase() == incoming.mode.trim().uppercase() &&
            existing.submode?.trim()?.uppercase() == incoming.submode?.trim()?.uppercase() &&
            existing.radioMode?.trim()?.uppercase() == incoming.radioMode?.trim()?.uppercase() &&
            existing.frequencyHz == incoming.frequencyHz &&
            existing.exactFrequencyMhz == incoming.exactFrequencyMhz &&
            existing.stationProfileId == incoming.stationProfileId &&
            existing.sessionId == incoming.sessionId
}
