package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/** No live provider, account, hardware, or Android dependency. */
object PropagationRefreshStatePersistenceTests {
    private var checks = 0
    private fun yes(value: Boolean, name: String) {
        checks++
        check(value) { name }
    }
    private fun <T> equal(expected: T, actual: T, name: String) {
        checks++
        check(expected == actual) { "$name: expected=$expected actual=$actual" }
    }
    private fun rejected(name: String, block: () -> Unit) {
        checks++
        check(runCatching(block).isFailure) { "Expected rejection: $name" }
    }
    private fun withDirectory(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("fieldops-cp0008k-")
        try {
            block(dir)
        } finally {
            Files.walk(dir).use { walk ->
                walk.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }
    private val start = Instant.parse("2026-10-08T12:00:00Z").toEpochMilli()
    private fun sample(
        key: String = "NOAA_SWPC_PLANETARY_KP",
        role: PropagationRefreshSourceRole = PropagationRefreshSourceRole.NOAA_KP_OBSERVED,
    ) = PropagationRefreshSourceState(
        sourceKey = key,
        role = role,
        lastAttemptUtcMillis = start,
        lastSuccessUtcMillis = start - 120_000L,
        consecutiveFailures = 3,
        nextEligibleRefreshUtcMillis = start + 240_000L,
        lastFailureMessage = "HTTP 503 – provider unavailable",
        lastFailureRetryable = true,
    )

    @JvmStatic
    fun main(args: Array<String>) {
        codecRoundTripAndOrder()
        filesystemRoundTripAndRecreation()
        allFieldsSurviveFailureRoundTrip()
        runtimeRestartPreservesCadence()
        runtimeRestartPreservesBackoff()
        corruptionFailsClosed()
        duplicateFailsClosed()
        versionFailsClosed()
        roleMismatchFailsClosed()
        writeFailurePreservesValidState()
        atomicReplacementLeavesNoTemporaryFiles()
        println("CP-0008K refresh-state persistence tests: PASS assertions=$checks")
    }

    private fun codecRoundTripAndOrder() {
        val a = sample()
        val b = sample("PSK_REPORTER_PUBLIC_QUERY", PropagationRefreshSourceRole.PSK_REPORTER)
        val one = PropagationRefreshStateBinaryCodec.encode(listOf(b, a))
        val two = PropagationRefreshStateBinaryCodec.encode(listOf(a, b))
        yes(one.contentEquals(two), "serialization deterministically orders keys")
        equal(listOf(a, b), PropagationRefreshStateBinaryCodec.decode(one), "all fields serialized")
        equal(emptyList(), PropagationRefreshStateBinaryCodec.decode(
            PropagationRefreshStateBinaryCodec.encode(emptyList())
        ), "empty state")
        rejected("encoder duplicate") {
            PropagationRefreshStateBinaryCodec.encode(listOf(a, a))
        }
        rejected("encoder wrong built-in role") {
            PropagationRefreshStateBinaryCodec.encode(
                listOf(a.copy(role = PropagationRefreshSourceRole.PSK_REPORTER))
            )
        }
    }

    private fun filesystemRoundTripAndRecreation() = withDirectory { dir ->
        val store = FilePropagationRefreshStateStore(dir)
        equal(null, store.state("NOAA_SWPC_PLANETARY_KP"), "initial absent")
        val state = sample()
        store.save(state)
        equal(state, store.state(state.sourceKey), "same-instance read")
        equal(listOf(state), store.all(), "all read")
        val reopened = FilePropagationRefreshStateStore(dir)
        equal(state, reopened.state(state.sourceKey), "file round trip")
        equal(listOf(state), reopened.all(), "recreation loads full state")
        rejected("role change rejected") {
            reopened.save(state.copy(role = PropagationRefreshSourceRole.GENERIC))
        }
        equal(state, FilePropagationRefreshStateStore(dir).state(state.sourceKey), "role rejection preserves file")
    }

    private fun allFieldsSurviveFailureRoundTrip() = withDirectory { dir ->
        val original = sample().copy(
            lastSuccessUtcMillis = null,
            consecutiveFailures = 9,
            lastFailureRetryable = false,
            lastFailureMessage = "permanent failure: 100% Échec",
            nextEligibleRefreshUtcMillis = start + 900_000L,
        )
        FilePropagationRefreshStateStore(dir).save(original)
        val restored = FilePropagationRefreshStateStore(dir).state(original.sourceKey)
        equal(original, restored, "retryability/message/failure/UTC fields survive")
        equal(false, restored?.lastFailureRetryable, "false retryable preserved")
        equal(9, restored?.consecutiveFailures, "consecutive failure count preserved")
        equal(start + 900_000L, restored?.nextEligibleRefreshUtcMillis, "next eligible preserved")
        equal(null, restored?.lastSuccessUtcMillis, "nullable success time preserved")
    }

    private fun noNetworkTransport(): PublicPropagationTransport =
        PublicPropagationTransport { request ->
            PublicPropagationResponse(
                requestedUrl = request.url,
                effectiveUrl = request.url,
                statusCode = 503,
                contentType = "text/plain",
                body = "offline fake only",
            )
        }

    private fun seededRuntimeState(dir: Path): FilePropagationRefreshStateStore {
        val store = FilePropagationRefreshStateStore(dir)
        val due = mapOf(
            "NOAA_SWPC_PLANETARY_KP" to Pair(PropagationRefreshSourceRole.NOAA_KP_OBSERVED, 15L),
            "NOAA_SWPC_KP_FORECAST" to Pair(PropagationRefreshSourceRole.NOAA_KP_FORECAST, 15L),
            "NOAA_SWPC_F107_SUMMARY" to Pair(PropagationRefreshSourceRole.NOAA_F107, 60L),
            "NOAA_SWPC_GLOTEC_VTEC" to Pair(PropagationRefreshSourceRole.NOAA_GLOTEC, 10L),
            "PSK_REPORTER_PUBLIC_QUERY" to Pair(PropagationRefreshSourceRole.PSK_REPORTER, 5L),
        )
        for ((key, pair) in due) {
            store.save(PropagationRefreshSourceState(
                sourceKey = key,
                role = pair.first,
                lastAttemptUtcMillis = start,
                lastSuccessUtcMillis = start,
                nextEligibleRefreshUtcMillis = start + pair.second * 60_000L,
            ))
        }
        return store
    }

    private fun runtime(dir: Path): PropagationRuntime =
        PropagationRuntimeFactory.create(
            config = PropagationRuntimeConfig("N0PNG"),
            transport = noNetworkTransport(),
            refreshStateStore = FilePropagationRefreshStateStore(dir),
        )

    private fun runtimeRestartPreservesCadence() = withDirectory { dir ->
        seededRuntimeState(dir)
        val first = runtime(dir)
        equal(5, first.sourceStates().size, "factory accepts file-backed injected state")
        equal(0, first.refreshAndProject(start + 4 * 60_000L).refresh.attempts.size,
            "four-minute state survives restart")
        val second = runtime(dir)
        val five = second.refreshAndProject(start + 5 * 60_000L).refresh
        equal(listOf("PSK_REPORTER_PUBLIC_QUERY"), five.attempts.map { it.sourceKey },
            "PSK only due at five minutes after restart")
        equal(1, five.failedAttemptCount, "offline fake failure not mistaken for provider data")
        val third = runtime(dir)
        val ten = third.refreshAndProject(start + 10 * 60_000L).refresh
        equal(setOf("NOAA_SWPC_GLOTEC_VTEC", "PSK_REPORTER_PUBLIC_QUERY"),
            ten.attempts.map { it.sourceKey }.toSet(), "GloTEC and PSK due at ten minutes")
        equal(3, ten.skippedSourceKeys.size, "other three retain cadence")
        equal(5, runtime(dir).sourceStates().size, "runtime reinitialization does not duplicate keys")
    }

    private fun runtimeRestartPreservesBackoff() = withDirectory { dir ->
        val seeded = seededRuntimeState(dir)
        seeded.save(sample("PSK_REPORTER_PUBLIC_QUERY", PropagationRefreshSourceRole.PSK_REPORTER)
            .copy(lastSuccessUtcMillis = start - 10_000L,
                nextEligibleRefreshUtcMillis = start + 8 * 60_000L,
                lastFailureRetryable = false, consecutiveFailures = 4))
        val fresh = runtime(dir)
        val restored = fresh.sourceStates().single { it.sourceKey == "PSK_REPORTER_PUBLIC_QUERY" }
        equal(4, restored.consecutiveFailures, "failure count survived restart")
        equal(false, restored.lastFailureRetryable, "failure retryability survived restart")
        equal("HTTP 503 – provider unavailable", restored.lastFailureMessage, "error survives restart")
        yes("PSK_REPORTER_PUBLIC_QUERY" in
            fresh.refreshAndProject(start + 5 * 60_000L).refresh.skippedSourceKeys,
            "backoff not reset by recreation")
        equal(4, runtime(dir).sourceStates().single {
            it.sourceKey == "PSK_REPORTER_PUBLIC_QUERY"
        }.consecutiveFailures, "skipped source backoff remains intact")
    }

    private fun corruptionFailsClosed() = withDirectory { dir ->
        val path = dir.resolve(FilePropagationRefreshStateStore.FILE_NAME)
        Files.write(path, byteArrayOf(0, 1, 2, 3))
        rejected("truncated magic") { FilePropagationRefreshStateStore(dir) }
        Files.write(path, byteArrayOf())
        rejected("empty") { FilePropagationRefreshStateStore(dir) }
        Files.write(path, PropagationRefreshStateBinaryCodec.encode(listOf(sample())) + byteArrayOf(8))
        rejected("trailing garbage") { FilePropagationRefreshStateStore(dir) }
        Files.write(path, ByteArray(PropagationRefreshStateBinaryCodec.MAX_FILE_BYTES + 1))
        rejected("oversize") { FilePropagationRefreshStateStore(dir) }
    }

    // Construct malformed records without routing them through the production encoder.
    private fun raw(vararg records: Pair<String, String>, version: Int = 1): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { data ->
            fun string(s: String) {
                val encoded = s.toByteArray(Charsets.UTF_8)
                data.writeInt(encoded.size)
                data.write(encoded)
            }
            string("FIELDOPS_PROPAGATION_REFRESH_STATE")
            data.writeInt(version)
            data.writeInt(records.size)
            records.forEach { (key, role) ->
                string(key)
                string(role)
                data.writeByte(0) // last attempt
                data.writeByte(0) // last success
                data.writeInt(0) // failures
                data.writeLong(0) // eligible
                data.writeByte(0) // failure message
                data.writeByte(0) // retryability
            }
        }
        return bytes.toByteArray()
    }

    private fun duplicateFailsClosed() = withDirectory { dir ->
        Files.write(dir.resolve(FilePropagationRefreshStateStore.FILE_NAME),
            raw("NOAA_SWPC_PLANETARY_KP" to "NOAA_KP_OBSERVED",
                "NOAA_SWPC_PLANETARY_KP" to "NOAA_KP_OBSERVED"))
        rejected("duplicate file record") { FilePropagationRefreshStateStore(dir) }
    }

    private fun versionFailsClosed() = withDirectory { dir ->
        Files.write(dir.resolve(FilePropagationRefreshStateStore.FILE_NAME), raw(version = 999))
        rejected("unknown on-disk version") { FilePropagationRefreshStateStore(dir) }
    }

    private fun roleMismatchFailsClosed() = withDirectory { dir ->
        val path = dir.resolve(FilePropagationRefreshStateStore.FILE_NAME)
        Files.write(path, raw("NOAA_SWPC_PLANETARY_KP" to "PSK_REPORTER"))
        rejected("wrong role for canonical source") { FilePropagationRefreshStateStore(dir) }
        Files.write(path, raw("UNRECOGNIZED_ROLE_KEY" to "NOAA_KP_OBSERVED"))
        rejected("unknown built-in source key") { FilePropagationRefreshStateStore(dir) }
        Files.write(path, raw("ALPHA" to "NEW_ENUM_ROLE"))
        rejected("unrecognized enum role") { FilePropagationRefreshStateStore(dir) }
    }

    private fun writeFailurePreservesValidState() = withDirectory { dir ->
        val old = sample()
        FilePropagationRefreshStateStore(dir).save(old)
        val path = dir.resolve(FilePropagationRefreshStateStore.FILE_NAME)
        val before = Files.readAllBytes(path)
        val store = FilePropagationRefreshStateStore(dir) { _, _ ->
            throw IOException("simulated atomic move failed")
        }
        rejected("atomic move failure propagated") {
            store.save(old.copy(nextEligibleRefreshUtcMillis = start + 999_000L))
        }
        yes(before.contentEquals(Files.readAllBytes(path)), "valid file bit-identical on failed write")
        equal(old, store.state(old.sourceKey), "failed write does not mutate in-memory state")
        equal(old, FilePropagationRefreshStateStore(dir).state(old.sourceKey), "reopen keeps prior state")
        Files.list(dir).use { files ->
            equal(1L, files.count(), "temp cleaned up after failed replacement")
        }
    }

    private fun atomicReplacementLeavesNoTemporaryFiles() = withDirectory { dir ->
        val store = FilePropagationRefreshStateStore(dir)
        val previous = sample()
        store.save(previous)
        val path = dir.resolve(FilePropagationRefreshStateStore.FILE_NAME)
        val before = Files.readAllBytes(path)
        val updated = previous.copy(
            lastAttemptUtcMillis = start + 60_000L,
            nextEligibleRefreshUtcMillis = start + 300_000L,
        )
        store.save(updated)
        yes(!before.contentEquals(Files.readAllBytes(path)), "new bytes replace old file")
        equal(updated, FilePropagationRefreshStateStore(dir).state(previous.sourceKey),
            "atomic replacement committed new state")
        Files.list(dir).use { files ->
            equal(1L, files.count(), "only canonical state file remains")
        }
    }
}
