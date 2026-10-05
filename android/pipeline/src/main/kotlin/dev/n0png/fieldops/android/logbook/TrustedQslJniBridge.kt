package dev.n0png.fieldops.android.logbook

/**
 * Narrow JNI surface around official tqsllib/tqslconvert.
 *
 * TrustedQSL has two process-global paths:
 * - a writable base/data directory for certificates, keys, station data and the
 *   duplicate database; and
 * - a read-only resource directory containing the official config.xml.
 *
 * Android cannot safely rely on TrustedQSL's desktop CONFDIR/HOME discovery, so
 * FieldOps supplies both app-private paths explicitly before tqsl_init().
 *
 * The native library owns TrustedQSL handles and never exposes raw key material
 * to Kotlin. Error reporting is intentionally code-only so native/library error
 * strings cannot accidentally contain sensitive paths or certificate details.
 */
internal class TrustedQslJniBridge(
    dataDirectory: String,
    resourceDirectory: String = dataDirectory,
    libraryLoader: () -> Unit = { System.loadLibrary(LIBRARY_NAME) },
) {
    init {
        require(dataDirectory.isNotBlank()) { "TrustedQSL data directory must be explicit" }
        require(resourceDirectory.isNotBlank()) { "TrustedQSL resource directory must be explicit" }
        try {
            libraryLoader()
        } catch (_: Throwable) {
            throw TrustedQslException("TrustedQSL native signer unavailable")
        }
        status("initialize", nativeInitialize(dataDirectory, resourceDirectory))
    }

    fun ensureStationLocation(
        name: String,
        callsign: String,
        dxcc: Int,
        gridSquare: String,
        cqZone: Int,
        ituZone: Int,
    ) {
        require(name.isNotBlank())
        require(callsign.isNotBlank())
        require(dxcc > 0)
        require(gridSquare.length >= 4)
        require(cqZone in 1..40)
        require(ituZone in 1..90)
        status(
            "station-location provisioning",
            nativeEnsureStationLocation(
                name,
                callsign,
                dxcc,
                gridSquare,
                cqZone,
                ituZone,
            ),
        )
    }

    fun importPkcs12(
        pkcs12: ByteArray,
        p12PasswordUtf8: ByteArray,
        keyPasswordUtf8: ByteArray,
    ) {
        require(pkcs12.isNotEmpty()) { "PKCS#12 payload must not be empty" }
        status("PKCS#12 import", nativeImportPkcs12(pkcs12, p12PasswordUtf8, keyPasswordUtf8))
    }

    fun beginSigning(
        adif: String,
        stationLocationName: String,
        expectedCallsign: String,
        expectedDxcc: Int,
        keyPasswordUtf8: ByteArray,
    ): Long {
        require(adif.isNotBlank())
        require(stationLocationName.isNotBlank())
        require(expectedCallsign.isNotBlank())
        require(expectedDxcc > 0)
        val handle = nativeBeginSigning(
            adif,
            stationLocationName,
            expectedCallsign,
            expectedDxcc,
            keyPasswordUtf8,
        )
        if (handle == 0L) {
            throw TrustedQslException("TrustedQSL signing failed (code=" + nativeLastStatus() + ")")
        }
        return handle
    }

    fun payload(handle: Long): ByteArray {
        require(handle != 0L)
        val payload = nativeGetPayload(handle)
        if (payload.isEmpty()) {
            throw TrustedQslException("TrustedQSL signer returned an empty payload")
        }
        return payload
    }

    fun commit(handle: Long) = status("commit", nativeCommit(handle))
    fun rollback(handle: Long) = status("rollback", nativeRollback(handle))
    fun close(handle: Long) = status("close", nativeClose(handle))

    private fun status(operation: String, code: Int) {
        if (code != STATUS_OK) {
            throw TrustedQslException("TrustedQSL " + operation + " failed (code=" + code + ")")
        }
    }

    private external fun nativeInitialize(
        dataDirectory: String,
        resourceDirectory: String,
    ): Int
    private external fun nativeEnsureStationLocation(
        name: String,
        callsign: String,
        dxcc: Int,
        gridSquare: String,
        cqZone: Int,
        ituZone: Int,
    ): Int
    private external fun nativeImportPkcs12(
        pkcs12: ByteArray,
        p12PasswordUtf8: ByteArray,
        keyPasswordUtf8: ByteArray,
    ): Int
    private external fun nativeBeginSigning(
        adif: String,
        stationLocationName: String,
        expectedCallsign: String,
        expectedDxcc: Int,
        keyPasswordUtf8: ByteArray,
    ): Long
    private external fun nativeGetPayload(handle: Long): ByteArray
    private external fun nativeCommit(handle: Long): Int
    private external fun nativeRollback(handle: Long): Int
    private external fun nativeClose(handle: Long): Int
    private external fun nativeLastStatus(): Int

    companion object {
        const val LIBRARY_NAME = "fieldops_tqsl"
        const val STATUS_OK = 0
    }
}

class TrustedQslException(message: String) : IllegalStateException(message)
