package dev.n0png.fieldops.core.logbook

/**
 * Transactional TrustedQSL signing boundary.
 *
 * Signing and network upload are intentionally separate. A signing session
 * remains uncommitted until the caller explicitly commits it after the later
 * upload/acceptance transaction succeeds. Closing an open session rolls back.
 */
data class LotwSigningRequest(
    val adif: String,
    val stationProfileId: String,
    val stationLocationName: String,
    val expectedStationCallsign: String,
    val expectedDxcc: Int,
) {
    init {
        require(adif.isNotBlank()) { "ADIF must not be empty" }
        require(stationProfileId.isNotBlank()) { "Station profile id must not be empty" }
        require(stationLocationName.isNotBlank()) { "TrustedQSL station location must be explicit" }
        require(expectedStationCallsign.isNotBlank()) { "Expected station callsign must not be empty" }
        require(expectedDxcc > 0) { "Expected DXCC must be positive" }
    }
}

enum class LotwSigningSessionState {
    OPEN,
    COMMITTED,
    ROLLED_BACK,
}

interface LotwSigningSession : AutoCloseable {
    /** Signed, zlib-compressed GABBI (.tq8) payload. */
    val tq8Payload: ByteArray
    val state: LotwSigningSessionState

    /** Commit TrustedQSL duplicate-tracking state. CP-0003B decides when to call this. */
    fun commit()

    /** Roll back TrustedQSL duplicate-tracking state. */
    fun rollback()

    /** Closing an OPEN session is fail-closed and therefore rolls it back. */
    override fun close()
}

interface TransactionalLotwSigner {
    /**
     * Import a PKCS#12 callsign-certificate container into the signer store.
     *
     * Password arrays remain caller-owned. Implementations must avoid logging
     * them and must wipe any encoded/transient copies they create.
     */
    fun importPkcs12(
        pkcs12: ByteArray,
        pkcs12Password: CharArray,
        privateKeyPassword: CharArray,
    )

    /**
     * Start an uncommitted signing transaction.
     *
     * The requested TrustedQSL station location must be checked against the
     * expected FieldOps station callsign and DXCC before any QSO is signed.
     */
    fun beginSigning(
        request: LotwSigningRequest,
        privateKeyPassword: CharArray,
    ): LotwSigningSession
}
