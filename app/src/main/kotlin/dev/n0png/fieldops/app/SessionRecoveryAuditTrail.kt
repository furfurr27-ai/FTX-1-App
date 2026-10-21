package dev.n0png.fieldops.app

/**
 * CP-0009O: strictly in-memory, bounded, non-sensitive UI audit of recovery
 * gestures. This is NOT a tamper-proof log, provenance proof, secure clock,
 * trusted recency witness, durable history, or Android Keystore attestation.
 *
 * Only compile-time enum values are accepted. Never store report contents,
 * callsigns, secrets, file paths, error text, hashes, timestamps, or IDs.
 */
enum class RecoveryAuditAction {
    REVIEW,
    SELECT,
    CONFIRM,
    CANCEL_SELECTION,
    RETIREMENT_REVIEW,
    SESSION_DISABLED,
    OPTIONS_INVALIDATED,
    DISPLAY_REPLACED,
    CLEAR,
}

enum class RecoveryAuditOutcome {
    ACCEPTED,
    REJECTED,
    BLOCKED,
    CLEARED,
}

data class RecoveryAuditEntry(
    val ordinal: Int,
    val action: RecoveryAuditAction,
    val candidate: EncryptedRecoveryCandidate?,
    val outcome: RecoveryAuditOutcome,
)

/**
 * Instance-owned only. No context references, serialization interfaces, saved state,
 * file I/O, preferences, database, logging, network or system clock.
 *
 * snapshot() returns a detached immutable List of immutable records;
 * clear() destroys the displayed session entries (not secure memory wipe).
 */
class SessionRecoveryAuditTrail(private val capacity: Int = 12) {
    init { require(capacity in 1..64) { "Bounded audit capacity required" } }

    private val entries = ArrayDeque<RecoveryAuditEntry>()
    private var ordinal = 0

    @Synchronized
    fun record(
        action: RecoveryAuditAction,
        outcome: RecoveryAuditOutcome,
        candidate: EncryptedRecoveryCandidate? = null,
    ) {
        require(action != RecoveryAuditAction.CLEAR) { "Use explicit clear()" }
        ordinal += 1
        if (entries.size == capacity) entries.removeFirst()
        entries.addLast(RecoveryAuditEntry(ordinal, action, candidate, outcome))
    }

    @Synchronized
    fun snapshot(): List<RecoveryAuditEntry> = entries.toList()

    @Synchronized
    fun clear() {
        entries.clear()
        ordinal = 0
    }
}
