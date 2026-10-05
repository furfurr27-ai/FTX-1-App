package dev.n0png.fieldops.core.cat

import dev.n0png.fieldops.core.radio.CatTransport
import java.io.ByteArrayOutputStream
import java.util.ArrayDeque

/**
 * Small platform-neutral serial byte pipe. Android USB serial, Bluetooth CAT,
 * and a future TCP/rigctld bridge can all sit behind this interface.
 */
interface SerialBytePort {
    /** Write all bytes or throw. */
    fun write(bytes: ByteArray)

    /**
     * Read up to [buffer.size] bytes. Returns 0 on timeout, otherwise the byte
     * count. Implementations must not return a negative count.
     */
    fun read(buffer: ByteArray, timeoutMillis: Int): Int

    fun close() {}
}

/**
 * Serialized FTX-1 CAT transport.
 *
 * Yaesu CAT replies are semicolon terminated. Query and write share one lock so
 * a decoder, APRS controller, UI status poller and TX controller cannot splice
 * commands/replies together on the same radio port. Bytes following the first
 * reply terminator are retained for the next query.
 */
class Ftx1SerialCatTransport(
    private val port: SerialBytePort,
    private val queryTimeoutMillis: Int = 700,
    private val maxReplyBytes: Int = 4096,
) : CatTransport, AutoCloseable {
    init {
        require(queryTimeoutMillis > 0)
        require(maxReplyBytes in 32..65_536)
    }

    private val lock = Any()
    private val ascii = Charsets.US_ASCII
    private val pendingRx = ArrayDeque<Byte>()

    override fun write(command: String) {
        val normalized = normalize(command)
        synchronized(lock) {
            port.write(normalized.toByteArray(ascii))
        }
    }

    override fun query(command: String): String? {
        val normalized = normalize(command)
        synchronized(lock) {
            port.write(normalized.toByteArray(ascii))
            val out = ByteArrayOutputStream(64)
            val buf = ByteArray(256)
            val deadline = System.nanoTime() + queryTimeoutMillis * 1_000_000L

            while (out.size() < maxReplyBytes) {
                while (pendingRx.isNotEmpty() && out.size() < maxReplyBytes) {
                    val b = pendingRx.removeFirst()
                    out.write(b.toInt())
                    if (b.toInt() == ';'.code) return out.toByteArray().toString(ascii).trim()
                }

                val remainingNs = deadline - System.nanoTime()
                if (remainingNs <= 0L) break
                val remainingMs = ((remainingNs + 999_999L) / 1_000_000L)
                    .coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
                val n = port.read(buf, remainingMs)
                require(n >= 0) { "SerialBytePort returned negative read count" }
                if (n == 0) continue
                require(n <= buf.size) { "SerialBytePort returned impossible read count $n" }

                for (i in 0 until n) {
                    val b = buf[i]
                    out.write(b.toInt())
                    if (b.toInt() == ';'.code) {
                        for (j in i + 1 until n) pendingRx.addLast(buf[j])
                        return out.toByteArray().toString(ascii).trim()
                    }
                    if (out.size() >= maxReplyBytes) {
                        for (j in i + 1 until n) pendingRx.addLast(buf[j])
                        break
                    }
                }
            }
            return null
        }
    }

    override fun close() = synchronized(lock) {
        pendingRx.clear()
        port.close()
    }

    private fun normalize(command: String): String {
        val trimmed = command.trim()
        require(trimmed.isNotEmpty()) { "CAT command cannot be empty" }
        require('\r' !in trimmed && '\n' !in trimmed) { "CAT command contains line break" }
        return if (trimmed.endsWith(';')) trimmed else "$trimmed;"
    }
}
