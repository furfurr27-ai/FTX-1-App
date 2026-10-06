package dev.n0png.fieldops.android.logbook

import dev.n0png.fieldops.core.logbook.LotwSigningRequest
import dev.n0png.fieldops.core.logbook.LotwStationProfile
import dev.n0png.fieldops.core.logbook.LotwSigningSession
import dev.n0png.fieldops.core.logbook.LotwSigningSessionState
import dev.n0png.fieldops.core.logbook.TransactionalLotwSigner
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.util.Arrays

/**
 * Production FieldOps TrustedQSL signing facade.
 *
 * Signing remains separate from LoTW HTTP transport. CP-0003B owns the
 * sign/upload/verify/commit transaction; CP-0003C supplies explicit Android
 * data/resource directories and validates the real native runtime.
 */
class TrustedQslSigner internal constructor(
    private val native: TrustedQslJniBridge,
) : TransactionalLotwSigner {

    constructor(
        dataDirectory: String,
        resourceDirectory: String = dataDirectory,
        libraryLoader: () -> Unit = { System.loadLibrary(TrustedQslJniBridge.LIBRARY_NAME) },
    ) : this(TrustedQslJniBridge(dataDirectory, resourceDirectory, libraryLoader))

    fun ensureStationLocation(
        profile: LotwStationProfile,
        stationLocationName: String,
    ) {
        native.ensureStationLocation(
            name = stationLocationName,
            callsign = profile.stationCallsign.trim().uppercase(),
            dxcc = profile.dxcc,
            gridSquare = profile.gridSquare.trim().uppercase(),
            cqZone = profile.cqZone,
            ituZone = profile.ituZone,
        )
    }

    fun importBackup(backup: ByteArray) {
        require(backup.isNotEmpty()) { "TQSL backup payload must not be empty" }
        val copy = backup.copyOf()
        try {
            native.importBackup(copy)
        } finally {
            Arrays.fill(copy, 0)
        }
    }

    override fun importPkcs12(
        pkcs12: ByteArray,
        pkcs12Password: CharArray,
        privateKeyPassword: CharArray,
    ) {
        require(pkcs12.isNotEmpty()) { "PKCS#12 payload must not be empty" }
        val p12Copy = pkcs12.copyOf()
        val p12PasswordBytes = utf8Secret(pkcs12Password)
        val keyPasswordBytes = utf8Secret(privateKeyPassword)
        try {
            native.importPkcs12(p12Copy, p12PasswordBytes, keyPasswordBytes)
        } finally {
            Arrays.fill(p12Copy, 0)
            Arrays.fill(p12PasswordBytes, 0)
            Arrays.fill(keyPasswordBytes, 0)
        }
    }

    override fun beginSigning(
        request: LotwSigningRequest,
        privateKeyPassword: CharArray,
    ): LotwSigningSession {
        val keyPasswordBytes = utf8Secret(privateKeyPassword)
        var handle = 0L
        try {
            handle = native.beginSigning(
                adif = request.adif,
                stationLocationName = request.stationLocationName,
                expectedCallsign = request.expectedStationCallsign.trim().uppercase(),
                expectedDxcc = request.expectedDxcc,
                keyPasswordUtf8 = keyPasswordBytes,
            )
            val payload = native.payload(handle)
            return Session(native, handle, payload)
        } catch (t: Throwable) {
            if (handle != 0L) runCatching { native.close(handle) }
            throw t
        } finally {
            Arrays.fill(keyPasswordBytes, 0)
        }
    }

    private class Session(
        private val native: TrustedQslJniBridge,
        private var handle: Long,
        payload: ByteArray,
    ) : LotwSigningSession {
        private val payloadCopy = payload.copyOf()
        override val tq8Payload: ByteArray
            get() = payloadCopy.copyOf()

        @Volatile
        private var current = LotwSigningSessionState.OPEN

        override val state: LotwSigningSessionState
            get() = current

        @Synchronized
        override fun commit() {
            check(current == LotwSigningSessionState.OPEN) { "TrustedQSL signing session is already terminal" }
            val h = handle
            native.commit(h)
            // The duplicate database is now committed. Cleanup failure must not
            // make the caller think the transaction is still roll-backable.
            current = LotwSigningSessionState.COMMITTED
            handle = 0L
            runCatching { native.close(h) }
        }

        @Synchronized
        override fun rollback() {
            check(current == LotwSigningSessionState.OPEN) { "TrustedQSL signing session is already terminal" }
            val h = handle
            native.rollback(h)
            current = LotwSigningSessionState.ROLLED_BACK
            handle = 0L
            runCatching { native.close(h) }
        }

        @Synchronized
        override fun close() {
            if (current != LotwSigningSessionState.OPEN) return
            val h = handle
            try {
                native.rollback(h)
            } finally {
                native.close(h)
                handle = 0L
                current = LotwSigningSessionState.ROLLED_BACK
            }
        }
    }

    private companion object {
        fun utf8Secret(chars: CharArray): ByteArray {
            val encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars))
            val out = ByteArray(encoded.remaining())
            encoded.get(out)
            if (encoded.hasArray()) {
                Arrays.fill(encoded.array(), 0)
            }
            return out
        }
    }
}
