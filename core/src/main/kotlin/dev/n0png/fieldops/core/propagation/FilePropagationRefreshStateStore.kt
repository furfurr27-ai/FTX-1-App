package dev.n0png.fieldops.core.propagation

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * Single-writer, platform-neutral refresh scheduler state.
 *
 * The caller owns the directory and passes this store explicitly to
 * PropagationRuntimeFactory.create(refreshStateStore = ...). The default runtime
 * intentionally retains its in-memory store. All writes use a sibling temporary
 * file and an ATOMIC_MOVE; unsupported atomic replacement fails closed.
 *
 * No cached state is published in memory until its complete new file is durable.
 */
class FilePropagationRefreshStateStore(
    directory: Path,
    private val atomicReplace: (Path, Path) -> Unit = { source, target ->
        Files.move(
            source, target,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
        Unit
    },
) : PropagationRefreshStateStore {
    private val stateFile: Path
    private val states = linkedMapOf<String, PropagationRefreshSourceState>()

    init {
        Files.createDirectories(directory)
        require(Files.isDirectory(directory)) {
            "Propagation refresh state path must be a directory"
        }
        stateFile = directory.resolve(FILE_NAME)
        if (Files.exists(stateFile)) {
            require(Files.isRegularFile(stateFile)) {
                "Propagation refresh state path must be a regular file"
            }
            val size = Files.size(stateFile)
            require(size in 1..PropagationRefreshStateBinaryCodec.MAX_FILE_BYTES.toLong()) {
                "Propagation refresh state file size is invalid"
            }
            PropagationRefreshStateBinaryCodec.decode(Files.readAllBytes(stateFile)).forEach {
                states[it.sourceKey] = it
            }
        }
    }

    @Synchronized
    override fun state(sourceKey: String): PropagationRefreshSourceState? {
        require(sourceKey.isNotBlank()) { "Propagation refresh source key must not be blank" }
        return states[sourceKey]
    }

    @Synchronized
    override fun all(): List<PropagationRefreshSourceState> =
        states.values.sortedBy { it.sourceKey }

    @Synchronized
    override fun save(state: PropagationRefreshSourceState) {
        val existing = states[state.sourceKey]
        require(existing == null || existing.role == state.role) {
            "Propagation refresh source role cannot change for ${state.sourceKey}"
        }
        val replacement = LinkedHashMap(states)
        replacement[state.sourceKey] = state
        // Encode before touching the filesystem. Never publish replacement
        // in the live state map if the atomic write fails.
        val bytes = PropagationRefreshStateBinaryCodec.encode(replacement.values.toList())
        val parent = stateFile.parent
        val temporary = Files.createTempFile(parent, ".propagation-refresh-", ".tmp")
        try {
            Files.write(temporary, bytes, StandardOpenOption.TRUNCATE_EXISTING)
            atomicReplace(temporary, stateFile)
        } finally {
            Files.deleteIfExists(temporary)
        }
        states.clear()
        states.putAll(replacement)
    }

    companion object {
        const val FILE_NAME = "propagation-refresh-state-v1.bin"
    }
}

/**
 * V1 binary format: fixed magic, integer version, count, then UTF-8 length
 * prefixed records in ascending sourceKey order. Nullable primitives have an
 * explicit tag. Strings decode with strict UTF-8; arbitrary bytes never become
 * replacement characters. A malformed or unknown version is never reset.
 */
object PropagationRefreshStateBinaryCodec {
    private const val MAGIC = "FIELDOPS_PROPAGATION_REFRESH_STATE"
    private const val VERSION = 1
    private const val MAX_RECORDS = 512
    private const val MAX_STRING_BYTES = 16_384
    const val MAX_FILE_BYTES = 1_048_576

    private val builtInRoles = mapOf(
        "NOAA_SWPC_PLANETARY_KP" to PropagationRefreshSourceRole.NOAA_KP_OBSERVED,
        "NOAA_SWPC_KP_FORECAST" to PropagationRefreshSourceRole.NOAA_KP_FORECAST,
        "NOAA_SWPC_F107_SUMMARY" to PropagationRefreshSourceRole.NOAA_F107,
        "NOAA_SWPC_GLOTEC_VTEC" to PropagationRefreshSourceRole.NOAA_GLOTEC,
        "PSK_REPORTER_PUBLIC_QUERY" to PropagationRefreshSourceRole.PSK_REPORTER,
    )

    fun encode(states: List<PropagationRefreshSourceState>): ByteArray {
        validate(states)
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { data ->
            writeString(data, MAGIC)
            data.writeInt(VERSION)
            data.writeInt(states.size)
            for (state in states.sortedBy { it.sourceKey }) {
                writeString(data, state.sourceKey)
                writeString(data, state.role.name)
                writeNullableLong(data, state.lastAttemptUtcMillis)
                writeNullableLong(data, state.lastSuccessUtcMillis)
                data.writeInt(state.consecutiveFailures)
                data.writeLong(state.nextEligibleRefreshUtcMillis)
                writeNullableString(data, state.lastFailureMessage)
                when (state.lastFailureRetryable) {
                    null -> data.writeByte(0)
                    false -> data.writeByte(1)
                    true -> data.writeByte(2)
                }
            }
        }
        val encoded = out.toByteArray()
        require(encoded.size <= MAX_FILE_BYTES) {
            "Propagation refresh state encoded file is too large"
        }
        return encoded
    }

    fun decode(bytes: ByteArray): List<PropagationRefreshSourceState> {
        require(bytes.isNotEmpty() && bytes.size <= MAX_FILE_BYTES) {
            "Propagation refresh state file size is invalid"
        }
        try {
            val raw = ByteArrayInputStream(bytes)
            val input = DataInputStream(raw)
            require(readString(input) == MAGIC) { "Propagation refresh state magic mismatch" }
            require(input.readInt() == VERSION) { "Unknown propagation refresh state version" }
            val count = input.readInt()
            require(count in 0..MAX_RECORDS) { "Invalid propagation refresh state record count" }
            val result = ArrayList<PropagationRefreshSourceState>(count)
            repeat(count) {
                val key = readString(input)
                val role = PropagationRefreshSourceRole.valueOf(readString(input))
                val attempt = readNullableLong(input)
                val success = readNullableLong(input)
                val failures = input.readInt()
                val eligible = input.readLong()
                val failureMessage = readNullableString(input)
                val retryable = when (input.readUnsignedByte()) {
                    0 -> null
                    1 -> false
                    2 -> true
                    else -> throw IllegalArgumentException("Invalid refresh retryability tag")
                }
                result.add(
                    PropagationRefreshSourceState(
                        sourceKey = key,
                        role = role,
                        lastAttemptUtcMillis = attempt,
                        lastSuccessUtcMillis = success,
                        consecutiveFailures = failures,
                        nextEligibleRefreshUtcMillis = eligible,
                        lastFailureMessage = failureMessage,
                        lastFailureRetryable = retryable,
                    )
                )
            }
            require(raw.available() == 0) { "Trailing data after propagation refresh state" }
            validate(result)
            return result.sortedBy { it.sourceKey }
        } catch (e: IOException) {
            throw IllegalArgumentException("Corrupt propagation refresh state file", e)
        } catch (e: CharacterCodingException) {
            throw IllegalArgumentException("Invalid UTF-8 in propagation refresh state", e)
        }
    }

    private fun validate(records: List<PropagationRefreshSourceState>) {
        require(records.size <= MAX_RECORDS) { "Too many propagation refresh state records" }
        require(records.map { it.sourceKey }.distinct().size == records.size) {
            "Duplicate propagation refresh state source record"
        }
        for (state in records) {
            val expected = builtInRoles[state.sourceKey]
            if (expected != null) {
                require(state.role == expected) {
                    "Propagation refresh state role/source mismatch for ${state.sourceKey}"
                }
            } else {
                require(state.role == PropagationRefreshSourceRole.GENERIC) {
                    "Unknown built-in propagation refresh state source ${state.sourceKey}"
                }
            }
            require(state.nextEligibleRefreshUtcMillis >= (state.lastAttemptUtcMillis ?: 0L)) {
                "Propagation refresh state next eligible precedes last attempt"
            }
        }
    }

    private fun writeString(out: DataOutputStream, value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_STRING_BYTES) { "Refresh state string too large" }
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private fun readString(input: DataInputStream): String {
        val size = input.readInt()
        require(size in 0..MAX_STRING_BYTES) { "Invalid propagation refresh state string size" }
        val bytes = ByteArray(size)
        input.readFully(bytes)
        return StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    }

    private fun writeNullableLong(out: DataOutputStream, value: Long?) {
        out.writeByte(if (value == null) 0 else 1)
        if (value != null) out.writeLong(value)
    }

    private fun readNullableLong(input: DataInputStream): Long? =
        when (input.readUnsignedByte()) {
            0 -> null
            1 -> input.readLong()
            else -> throw IllegalArgumentException("Invalid nullable UTC tag")
        }

    private fun writeNullableString(out: DataOutputStream, value: String?) {
        out.writeByte(if (value == null) 0 else 1)
        if (value != null) writeString(out, value)
    }

    private fun readNullableString(input: DataInputStream): String? =
        when (input.readUnsignedByte()) {
            0 -> null
            1 -> readString(input)
            else -> throw IllegalArgumentException("Invalid nullable failure message tag")
        }
}
