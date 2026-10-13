package dev.n0png.fieldops.core.propagation

/**
 * CP-0009K is a NON-MUTATING fail-closed key lifecycle and replay-recovery
 * contract. It NEVER rotates a key, overwrites ciphertext, deletes plaintext
 * or claims rollback prevention without a trusted monotonic witness.
 */
enum class ArchiveRotationDecision {
    PRESERVE_UNREADABLE_CIPHERTEXT,
    REJECT_UNAUTHENTICATED_SOURCE,
    CONFLICT_REQUIRES_USER_REVIEW,
    REQUIRE_EXPLICIT_ROTATION_CONSENT,
    REQUIRE_NEW_DISTINCT_KEY,
    REQUIRE_ATOMIC_STAGING,
    REQUIRE_AUTHENTICATED_READBACK,
    ROTATION_EXECUTOR_NOT_IMPLEMENTED,
}

data class ArchiveRotationInputs(
    val currentKeyAvailable: Boolean,
    val currentCiphertextAuthenticated: Boolean,
    val competingEncryptedCopyPresent: Boolean,
    val explicitUserConsent: Boolean,
    val distinctNewKeyAvailable: Boolean,
    val atomicStageAvailable: Boolean,
    val stagedCiphertextAuthenticatedAndReadBack: Boolean,
)

enum class ArchiveReplayDecision {
    UNAUTHENTICATED_OR_UNSUPPORTED,
    CIPHERTEXT_CONFLICT_REQUIRES_USER_REVIEW,
    NO_TRUSTED_MONOTONIC_WITNESS,
    TRUSTED_WITNESS_INTEGRATION_NOT_IMPLEMENTED,
}

data class ArchiveReplayInputs(
    val authenticatedCiphertext: Boolean,
    val competingValidCopy: Boolean,
    val trustedMonotonicWitnessAvailable: Boolean,
)

/** Does not access filesystem, Android Keystore, radio, network or clock. */
object EncryptedArchiveKeyLifecycleContract {
    fun planRotation(input: ArchiveRotationInputs): ArchiveRotationDecision = when {
        !input.currentKeyAvailable ->
            ArchiveRotationDecision.PRESERVE_UNREADABLE_CIPHERTEXT
        !input.currentCiphertextAuthenticated ->
            ArchiveRotationDecision.REJECT_UNAUTHENTICATED_SOURCE
        input.competingEncryptedCopyPresent ->
            ArchiveRotationDecision.CONFLICT_REQUIRES_USER_REVIEW
        !input.explicitUserConsent ->
            ArchiveRotationDecision.REQUIRE_EXPLICIT_ROTATION_CONSENT
        !input.distinctNewKeyAvailable ->
            ArchiveRotationDecision.REQUIRE_NEW_DISTINCT_KEY
        !input.atomicStageAvailable ->
            ArchiveRotationDecision.REQUIRE_ATOMIC_STAGING
        !input.stagedCiphertextAuthenticatedAndReadBack ->
            ArchiveRotationDecision.REQUIRE_AUTHENTICATED_READBACK
        else -> ArchiveRotationDecision.ROTATION_EXECUTOR_NOT_IMPLEMENTED
    }

    /**
     * AES-GCM authenticity alone does NOT establish recency: replayed older
     * genuine ciphertext still authenticates under the same key.
     */
    fun assessReplay(input: ArchiveReplayInputs): ArchiveReplayDecision = when {
        !input.authenticatedCiphertext ->
            ArchiveReplayDecision.UNAUTHENTICATED_OR_UNSUPPORTED
        input.competingValidCopy ->
            ArchiveReplayDecision.CIPHERTEXT_CONFLICT_REQUIRES_USER_REVIEW
        !input.trustedMonotonicWitnessAvailable ->
            ArchiveReplayDecision.NO_TRUSTED_MONOTONIC_WITNESS
        else -> ArchiveReplayDecision.TRUSTED_WITNESS_INTEGRATION_NOT_IMPLEMENTED
    }
}
