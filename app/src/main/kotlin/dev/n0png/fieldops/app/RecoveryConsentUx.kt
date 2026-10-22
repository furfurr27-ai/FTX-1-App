package dev.n0png.fieldops.app

/**
 * CP-0009Q: explicit, nonpersistent recovery consent and conservative failure
 * wording. This is presentation only: authentication, reinspection, archive
 * replacement and key handling stay in their existing separate components.
 *
 * Never imply authenticated ciphertext is current, safe from replay, or came
 * from an RF station. This class does not record archive payloads or errors.
 */
object RecoveryConsentUx {
    const val FRESHNESS_WARNING =
        "FRESHNESS NOT VERIFIED: an older, valid encrypted copy can be replayed."

    fun selectionConsent(candidate: EncryptedRecoveryCandidate): String =
        "RECOVERY CONSENT — ${candidate.name}. " +
        "CONFIRM will independently reinspect BOTH encrypted candidates, " +
        "reauthenticate the selected copy, and check its canonical digest. " +
        "Only after ALL checks succeed will this replace CURRENT ON-SCREEN " +
        "HISTORY IN MEMORY; it will not save, delete or switch any archive " +
        "file or key. Existing unsaved in-memory changes may be lost. " +
        "No candidate is chosen automatically. " + FRESHNESS_WARNING +
        " Authenticated archive contents are NOT proof of station or RF provenance. " +
        "Choose CONFIRM to proceed or CANCEL RECOVERY SELECTION to keep the view."

    const val CANCELLED =
        "Recovery selection canceled; on-screen history unchanged. " +
        "No candidate selected. Review may be selected again; " +
        "FRESHNESS NOT VERIFIED."

    const val REJECTED =
        "RECOVERY REJECTED; on-screen history retained and current source unchanged. " +
        "The selected candidate could not be safely revalidated or is no longer " +
        "selected. REVIEW AGAIN before selecting another source. " +
        "No automatic fallback, downgrade, key retirement or file modification. " +
        "FRESHNESS NOT VERIFIED."
}
