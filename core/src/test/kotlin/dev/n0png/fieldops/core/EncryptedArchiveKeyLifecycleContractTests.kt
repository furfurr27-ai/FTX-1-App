package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*

/** Pure host contract, NOT key rotation or hardware replay protection. */
object EncryptedArchiveKeyLifecycleContractTests {
    private var checks = 0
    private fun same(expected: Any, actual: Any, label: String) {
        checks++
        check(expected == actual) { "$label: wanted $expected got $actual" }
    }

    @JvmStatic fun main(args: Array<String>) {
        val policy = EncryptedArchiveKeyLifecycleContract
        val ready = ArchiveRotationInputs(
            currentKeyAvailable = true,
            currentCiphertextAuthenticated = true,
            competingEncryptedCopyPresent = false,
            explicitUserConsent = true,
            distinctNewKeyAvailable = true,
            atomicStageAvailable = true,
            stagedCiphertextAuthenticatedAndReadBack = true,
        )
        same(ArchiveRotationDecision.ROTATION_EXECUTOR_NOT_IMPLEMENTED,
             policy.planRotation(ready), "never authorize an unimplemented writer")
        same(ArchiveRotationDecision.PRESERVE_UNREADABLE_CIPHERTEXT,
             policy.planRotation(ready.copy(currentKeyAvailable = false)),
             "key invalidation must not destroy ciphertext")
        same(ArchiveRotationDecision.REJECT_UNAUTHENTICATED_SOURCE,
             policy.planRotation(ready.copy(currentCiphertextAuthenticated = false)),
             "tag failure cannot trigger rotation")
        same(ArchiveRotationDecision.CONFLICT_REQUIRES_USER_REVIEW,
             policy.planRotation(ready.copy(competingEncryptedCopyPresent = true)),
             "two candidate encrypted copies never auto-win")
        same(ArchiveRotationDecision.REQUIRE_EXPLICIT_ROTATION_CONSENT,
             policy.planRotation(ready.copy(explicitUserConsent = false)),
             "no automatic key replacement")
        same(ArchiveRotationDecision.REQUIRE_NEW_DISTINCT_KEY,
             policy.planRotation(ready.copy(distinctNewKeyAvailable = false)),
             "no accidental self rotation")
        same(ArchiveRotationDecision.REQUIRE_ATOMIC_STAGING,
             policy.planRotation(ready.copy(atomicStageAvailable = false)),
             "no unsafe non-atomic fallback")
        same(ArchiveRotationDecision.REQUIRE_AUTHENTICATED_READBACK,
             policy.planRotation(ready.copy(stagedCiphertextAuthenticatedAndReadBack = false)),
             "old key cannot retire before verification")
        val baseline = ArchiveReplayInputs(
            authenticatedCiphertext = true,
            competingValidCopy = false,
            trustedMonotonicWitnessAvailable = false,
        )
        same(ArchiveReplayDecision.NO_TRUSTED_MONOTONIC_WITNESS,
             policy.assessReplay(baseline),
             "AEAD is not rollback resistance")
        same(ArchiveReplayDecision.CIPHERTEXT_CONFLICT_REQUIRES_USER_REVIEW,
             policy.assessReplay(baseline.copy(competingValidCopy = true)),
             "conflicting valid ciphertext cannot be auto-selected")
        same(ArchiveReplayDecision.UNAUTHENTICATED_OR_UNSUPPORTED,
             policy.assessReplay(baseline.copy(authenticatedCiphertext = false)),
             "bad tag or unsupported ciphertext rejected")
        same(ArchiveReplayDecision.TRUSTED_WITNESS_INTEGRATION_NOT_IMPLEMENTED,
             policy.assessReplay(baseline.copy(trustedMonotonicWitnessAvailable = true)),
             "simulated witness flag is not an implementation")
        println("CP-0009K lifecycle and replay fail-closed policy PASS: $checks assertions")
    }
}
