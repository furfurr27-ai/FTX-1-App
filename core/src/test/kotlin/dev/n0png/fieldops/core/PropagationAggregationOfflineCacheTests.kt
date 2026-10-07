package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

object PropagationAggregationOfflineCacheTests {
    private var assertions = 0
    private lateinit var root: Path

    private val noaaRetrieved1 = Instant.parse("2026-10-07T12:00:00Z").toEpochMilli()
    private val noaaRetrieved2 = Instant.parse("2026-10-07T12:05:00Z").toEpochMilli()
    private val glotecRetrieved1 = Instant.parse("2026-09-09T15:20:00Z").toEpochMilli()
    private val glotecRetrieved2 = Instant.parse("2026-09-09T15:25:00Z").toEpochMilli()
    private val pskrRetrieved1 = 1_599_164_940_000L
    private val pskrRetrieved2 = 1_599_165_240_000L
    private val captured = Instant.parse("2026-10-07T12:06:00Z").toEpochMilli()

    private fun checkThat(value: Boolean, message: String) {
        assertions++
        check(value) { message }
    }

    private fun <T> eq(expected: T, actual: T, message: String) =
        checkThat(expected == actual, "$message expected=$expected actual=$actual")

    private fun expectFailure(message: String, block: () -> Unit) {
        assertions++
        check(runCatching(block).isFailure) { "Expected failure: $message" }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        root = Path.of(args.single())

        aggregatesNormalizedProvidersDeterministically()
        repeatedPayloadDedupDoesNotInflateHeardCount()
        conflictingEvidenceFailsClosed()
        sourceSpecificFreshnessDefaults()
        aggregationDoesNotCreatePrediction()
        fileStoreRoundTripAndRestart()
        fileStoreBoundsHistoryDeterministically()
        binaryCodecIsDeterministicAndComplete()
        corruptedCacheFailsClosed()
        platformTransportAndQsoSeparation()

        println("CP-0008E propagation aggregation/offline cache tests: PASS assertions=$assertions")
    }

    private fun aggregatesNormalizedProvidersDeterministically() {
        val inputs = providerInputs()
        val aggregator = PropagationSnapshotAggregator()

        val first = aggregator.aggregate(
            capturedAtUtcMillis = captured,
            inputs = inputs,
        )
        val reversed = aggregator.aggregate(
            capturedAtUtcMillis = captured,
            inputs = inputs.reversed(),
        )

        eq(first, reversed, "aggregation independent of input-batch ordering")
        checkThat(
            first.snapshotId.startsWith("propagation-$captured-"),
            "generated snapshot id includes capture time",
        )
        eq(64, first.snapshotId.length, "generated snapshot id has bounded deterministic shape")
        eq(7, first.solarGeomagnetic.size, "NOAA observed/forecast/F10.7 records deduplicated")
        eq(2, first.ionosphericProducts.size, "GloTEC plus synthetic point product retained")
        eq(4, first.heardPaths.size, "PSK Reporter paths deduplicated")
        eq(1, first.modeledPaths.size, "modeled evidence remains separate")
        eq(13, first.allEvidence().size, "all evidence categories retained")
        eq(12, first.observationCount, "observations remain distinct from model count")
        eq(1, first.modeledCount, "model count remains explicit")
        checkThat("NOAA_SWPC_PLANETARY_KP" in first.sourceIds, "NOAA Kp source indexed")
        checkThat("NOAA_SWPC_GLOTEC_VTEC" in first.sourceIds, "GloTEC source indexed")
        checkThat("PSK_REPORTER_PUBLIC_QUERY" in first.sourceIds, "PSK Reporter source indexed")
        checkThat("SYNTH_MODEL_CP0008E" in first.sourceIds, "model source indexed")

        val kp = first.solarGeomagnetic.single {
            it.evidenceId == "NOAA_SWPC_KP_OBSERVED:2026-10-07T03:00:00"
        }
        eq(
            noaaRetrieved2,
            kp.source.retrievedAtUtcMillis,
            "repeated NOAA payload keeps latest retrieval provenance",
        )
        eq(
            Instant.parse("2026-10-07T03:00:00Z").toEpochMilli(),
            kp.observedAtUtcMillis,
            "NOAA observation timestamp is not rewritten by aggregation",
        )

        val glotec = first.ionosphericProducts.single {
            it.source.sourceId == "NOAA_SWPC_GLOTEC_VTEC"
        }
        eq(
            glotecRetrieved2,
            glotec.source.retrievedAtUtcMillis,
            "repeated GloTEC payload keeps latest retrieval provenance",
        )
        eq(
            Instant.parse("2026-09-09T15:15:00Z").toEpochMilli(),
            glotec.observedAtUtcMillis,
            "GloTEC provider time remains observation time",
        )
        eq(null, glotec.generatedAtUtcMillis, "aggregation does not fabricate GloTEC generation time")

        val firstHeard = first.heardPaths.single {
            it.receiver.normalizedCallsign == "W5CJ"
        }
        eq(pskrRetrieved2, firstHeard.source.retrievedAtUtcMillis, "latest PSK retrieval retained")
        eq(
            1_599_163_380_000L,
            firstHeard.observedAtUtcMillis,
            "PSK provider observation timestamp preserved",
        )
        eq(3, firstHeard.reportCount, "max within-payload duplicate count retained")
        eq("DM14CC24", firstHeard.transmitter.location.normalizedGrid, "sender grid retained")
        eq("EM55DB92", firstHeard.receiver.location.normalizedGrid, "receiver grid retained")

        val explicitId = aggregator.aggregate(
            capturedAtUtcMillis = captured,
            inputs = inputs,
            snapshotId = "manual-snapshot-id",
        )
        eq("manual-snapshot-id", explicitId.snapshotId, "explicit snapshot id accepted")
        eq(first.allEvidence(), explicitId.allEvidence(), "explicit id does not alter evidence")

        expectFailure("empty batch list") {
            aggregator.aggregate(capturedAtUtcMillis = captured, inputs = emptyList())
        }
        expectFailure("empty evidence across batches") {
            aggregator.aggregate(
                capturedAtUtcMillis = captured,
                inputs = listOf(PropagationAggregationInput()),
            )
        }
        expectFailure("blank explicit snapshot id") {
            aggregator.aggregate(captured, inputs, snapshotId = " ")
        }
        expectFailure("capture before latest retrieval") {
            aggregator.aggregate(
                capturedAtUtcMillis = noaaRetrieved2 - 1,
                inputs = inputs,
            )
        }
        expectFailure("negative capture time") {
            aggregator.aggregate(-1, inputs)
        }
    }

    private fun repeatedPayloadDedupDoesNotInflateHeardCount() {
        val first = pskPaths(pskrRetrieved1)
        val second = pskPaths(pskrRetrieved2).mapIndexed { index, path ->
            if (index == 0) path.copy(reportCount = 5) else path
        }
        val third = pskPaths(pskrRetrieved2).mapIndexed { index, path ->
            if (index == 0) path.copy(reportCount = 2) else path
        }

        val snapshot = PropagationSnapshotAggregator().aggregate(
            capturedAtUtcMillis = captured,
            inputs = listOf(
                PropagationAggregationInput(heardPaths = first),
                PropagationAggregationInput(heardPaths = second),
                PropagationAggregationInput(heardPaths = third),
            ),
        )

        eq(4, snapshot.heardPaths.size, "repeated polls do not create duplicate paths")
        val w5cj = snapshot.heardPaths.single { it.receiver.normalizedCallsign == "W5CJ" }
        eq(5, w5cj.reportCount, "cross-poll dedup uses max reportCount rather than summing")
        eq(pskrRetrieved2, w5cj.source.retrievedAtUtcMillis, "latest repeated retrieval retained")

        val snapshotAgain = PropagationSnapshotAggregator().aggregate(
            capturedAtUtcMillis = captured,
            inputs = listOf(
                PropagationAggregationInput(heardPaths = third),
                PropagationAggregationInput(heardPaths = first),
                PropagationAggregationInput(heardPaths = second),
            ),
        )
        eq(snapshot, snapshotAgain, "dedup merge is batch-order independent")
    }

    private fun conflictingEvidenceFailsClosed() {
        val paths = pskPaths(pskrRetrieved1)
        val conflict = paths.first().copy(snrDb = -1.0)
        expectFailure("same heard id with different SNR") {
            PropagationSnapshotAggregator().aggregate(
                capturedAtUtcMillis = captured,
                inputs = listOf(
                    PropagationAggregationInput(heardPaths = paths),
                    PropagationAggregationInput(heardPaths = listOf(conflict)),
                ),
            )
        }

        val kp = noaaKp(noaaRetrieved1).first()
        val kpConflict = kp.copy(planetaryKp = 8.0)
        expectFailure("same solar id with different content") {
            PropagationSnapshotAggregator().aggregate(
                capturedAtUtcMillis = captured,
                inputs = listOf(
                    PropagationAggregationInput(solarGeomagnetic = listOf(kp)),
                    PropagationAggregationInput(solarGeomagnetic = listOf(kpConflict)),
                ),
            )
        }

        val model = modeledPath().copy(evidenceId = kp.evidenceId)
        expectFailure("evidence id collision across categories") {
            PropagationSnapshotAggregator().aggregate(
                capturedAtUtcMillis = captured,
                inputs = listOf(
                    PropagationAggregationInput(
                        solarGeomagnetic = listOf(kp),
                        modeledPaths = listOf(model),
                    )
                ),
            )
        }

        val changedSourceUrl = paths.first().copy(
            source = paths.first().source.copy(
                sourceUrl = "https://retrieve.pskreporter.info/query?receiverCallsign=W5CJ"
            )
        )
        expectFailure("same evidence id from materially different provenance") {
            PropagationSnapshotAggregator().aggregate(
                capturedAtUtcMillis = captured,
                inputs = listOf(
                    PropagationAggregationInput(heardPaths = listOf(paths.first())),
                    PropagationAggregationInput(heardPaths = listOf(changedSourceUrl)),
                ),
            )
        }
    }

    private fun sourceSpecificFreshnessDefaults() {
        eq(
            PropagationFreshnessPolicy(15 * 60_000L, 60 * 60_000L),
            PropagationSourceFreshnessDefaults.PSK_REPORTER,
            "PSK Reporter freshness policy explicit",
        )
        eq(
            PropagationFreshnessPolicy(20 * 60_000L, 60 * 60_000L),
            PropagationSourceFreshnessDefaults.GLOTEC,
            "GloTEC freshness policy explicit",
        )
        eq(
            PropagationFreshnessPolicy(3 * 60 * 60_000L, 6 * 60 * 60_000L),
            PropagationSourceFreshnessDefaults.NOAA_KP,
            "NOAA Kp freshness policy explicit",
        )
        eq(
            PropagationFreshnessPolicy(36 * 60 * 60_000L, 72 * 60 * 60_000L),
            PropagationSourceFreshnessDefaults.NOAA_F107,
            "NOAA F10.7 freshness policy explicit",
        )
        eq(
            PropagationFreshnessPolicy.OPERATIONAL,
            PropagationSourceFreshnessDefaults.policyForSourceId("FUTURE_PROVIDER"),
            "unknown provider uses inherited operational default",
        )

        val pskr = pskPaths(pskrRetrieved1).first()
        eq(
            PropagationFreshness.FRESH,
            PropagationSourceFreshnessDefaults.classify(
                pskr,
                pskr.observedAtUtcMillis + 10 * 60_000L,
            ),
            "PSK report fresh inside 15 minutes",
        )
        eq(
            PropagationFreshness.AGING,
            PropagationSourceFreshnessDefaults.classify(
                pskr,
                pskr.observedAtUtcMillis + 30 * 60_000L,
            ),
            "PSK report aging inside one hour",
        )
        eq(
            PropagationFreshness.STALE,
            PropagationSourceFreshnessDefaults.classify(
                pskr,
                pskr.observedAtUtcMillis + 61 * 60_000L,
            ),
            "PSK report stale after one hour",
        )

        val glotec = glotec(glotecRetrieved1)
        eq(
            PropagationFreshness.FRESH,
            PropagationSourceFreshnessDefaults.classify(
                glotec,
                glotec.observedAtUtcMillis + 20 * 60_000L,
            ),
            "GloTEC fresh through two 10-minute product cycles",
        )
        eq(
            PropagationFreshness.AGING,
            PropagationSourceFreshnessDefaults.classify(
                glotec,
                glotec.observedAtUtcMillis + 45 * 60_000L,
            ),
            "GloTEC aging before one-hour limit",
        )
        eq(
            PropagationFreshness.STALE,
            PropagationSourceFreshnessDefaults.classify(
                glotec,
                glotec.observedAtUtcMillis + 61 * 60_000L,
            ),
            "GloTEC stale after one hour",
        )

        val kp = noaaKp(noaaRetrieved1).last()
        eq(
            PropagationFreshness.FRESH,
            PropagationSourceFreshnessDefaults.classify(
                kp,
                kp.observedAtUtcMillis + 2 * 60 * 60_000L,
            ),
            "NOAA Kp fresh inside three hours",
        )
        eq(
            PropagationFreshness.AGING,
            PropagationSourceFreshnessDefaults.classify(
                kp,
                kp.observedAtUtcMillis + 4 * 60 * 60_000L,
            ),
            "NOAA Kp aging inside six hours",
        )
        eq(
            PropagationFreshness.STALE,
            PropagationSourceFreshnessDefaults.classify(
                kp,
                kp.observedAtUtcMillis + 7 * 60 * 60_000L,
            ),
            "NOAA Kp stale after six hours",
        )

        val f107 = noaaF107(noaaRetrieved1).single()
        eq(
            PropagationFreshness.FRESH,
            PropagationSourceFreshnessDefaults.classify(
                f107,
                f107.observedAtUtcMillis + 24 * 60 * 60_000L,
            ),
            "F10.7 fresh inside 36 hours",
        )
        eq(
            PropagationFreshness.AGING,
            PropagationSourceFreshnessDefaults.classify(
                f107,
                f107.observedAtUtcMillis + 48 * 60 * 60_000L,
            ),
            "F10.7 aging inside 72 hours",
        )
        eq(
            PropagationFreshness.STALE,
            PropagationSourceFreshnessDefaults.classify(
                f107,
                f107.observedAtUtcMillis + 73 * 60 * 60_000L,
            ),
            "F10.7 stale after 72 hours",
        )
        eq(
            PropagationFreshness.FUTURE_DATED,
            PropagationSourceFreshnessDefaults.classify(
                kp,
                kp.observedAtUtcMillis - 1,
            ),
            "source-specific freshness never rewrites future provider timestamps",
        )
    }

    private fun aggregationDoesNotCreatePrediction() {
        val contextOnly = PropagationSnapshotAggregator().aggregate(
            capturedAtUtcMillis = captured,
            inputs = listOf(
                PropagationAggregationInput(
                    solarGeomagnetic = noaaKp(noaaRetrieved2),
                    ionosphericProducts = listOf(glotec(glotecRetrieved2)),
                )
            ),
        )
        eq(0, contextOnly.heardPaths.size, "context-only aggregation creates no heard paths")
        eq(0, contextOnly.modeledPaths.size, "context-only aggregation creates no modeled paths")

        val assessment = PropagationAssessmentEngine().assess(
            contextOnly,
            PropagationAssessmentQuery(
                band = "20m",
                frequencyHz = 14_074_000,
                origin = gridPosition("JO40"),
                destination = gridPosition("FN31"),
                nowUtcMillis = captured,
            )
        )
        eq(PropagationUsability.UNKNOWN, assessment.usability, "aggregation itself creates no path score")
        checkThat(
            assessment.reasons.any {
                it.code == PropagationAssessmentReasonCode.INSUFFICIENT_PATH_EVIDENCE
            },
            "lack of path evidence remains explicit",
        )
    }

    private fun fileStoreRoundTripAndRestart() {
        val dir = Files.createTempDirectory("fieldops-cp0008e-roundtrip-")
        val snapshot = PropagationSnapshotAggregator().aggregate(
            capturedAtUtcMillis = captured,
            inputs = providerInputs(),
        )

        val store = FilePropagationSnapshotStore(dir, maxSnapshots = 4)
        eq(null, store.latest(), "new file store starts empty")
        store.save(snapshot)
        eq(snapshot, store.latest(), "saved snapshot immediately readable")
        eq(snapshot, store.snapshot(snapshot.snapshotId), "snapshot id lookup works")
        eq(snapshot, store.latestAtOrBefore(captured), "as-of lookup works")
        eq(null, store.latestAtOrBefore(captured - 1), "as-of before capture returns null")

        val file = dir.resolve(FilePropagationSnapshotStore.FILE_NAME)
        checkThat(Files.exists(file), "offline cache file created")
        val firstBytes = Files.readAllBytes(file)
        checkThat(firstBytes.isNotEmpty(), "offline cache file nonempty")

        val restarted = FilePropagationSnapshotStore(dir, maxSnapshots = 4)
        eq(snapshot, restarted.latest(), "snapshot survives store restart")
        eq(
            snapshot.allEvidence(),
            restarted.latest()!!.allEvidence(),
            "all provider-neutral evidence survives restart exactly",
        )
        val reloadedGlotec = restarted.latest()!!.ionosphericProducts.single {
            it.source.sourceId == "NOAA_SWPC_GLOTEC_VTEC"
        }
        eq(null, reloadedGlotec.generatedAtUtcMillis, "null GloTEC generation time survives reload")
        val reloadedPoint = restarted.latest()!!.ionosphericProducts.single {
            it.evidenceId == "SYNTH_IONO_POINT_CP0008E"
        }
        eq(
            1_791_374_100_000L,
            reloadedPoint.generatedAtUtcMillis,
            "explicit synthetic generation time survives reload",
        )

        restarted.save(snapshot)
        val secondBytes = Files.readAllBytes(file)
        checkThat(firstBytes.contentEquals(secondBytes), "idempotent save produces identical cache bytes")

        expectFailure("same snapshot id cannot change content") {
            restarted.save(snapshot.copy(capturedAtUtcMillis = captured + 1))
        }
        expectFailure("blank lookup id") { restarted.snapshot(" ") }
        expectFailure("negative as-of lookup") { restarted.latestAtOrBefore(-1) }
        expectFailure("nonpositive history limit") { restarted.history(0) }
        expectFailure("nonpositive maxSnapshots") {
            FilePropagationSnapshotStore(dir.resolve("bad"), maxSnapshots = 0)
        }
    }

    private fun fileStoreBoundsHistoryDeterministically() {
        val dir = Files.createTempDirectory("fieldops-cp0008e-bounds-")
        val baseInputs = providerInputs()
        val aggregator = PropagationSnapshotAggregator()
        val first = aggregator.aggregate(captured, baseInputs, "snapshot-a")
        val second = aggregator.aggregate(captured + 1_000, baseInputs, "snapshot-b")
        val third = aggregator.aggregate(captured + 2_000, baseInputs, "snapshot-c")
        val sameTimeLaterId = aggregator.aggregate(captured + 2_000, baseInputs, "snapshot-d")

        val store = FilePropagationSnapshotStore(dir, maxSnapshots = 3)
        store.save(third)
        store.save(first)
        store.save(second)
        eq(
            listOf("snapshot-c", "snapshot-b", "snapshot-a"),
            store.history(3).map { it.snapshotId },
            "history sorts newest first independent of save order",
        )

        store.save(sameTimeLaterId)
        eq(3, store.history(10).size, "cache enforces bounded history")
        eq(null, store.snapshot("snapshot-a"), "oldest snapshot evicted at capacity")
        eq("snapshot-d", store.latest()!!.snapshotId, "same-time id tie-break deterministic")
        eq(
            listOf("snapshot-d", "snapshot-c", "snapshot-b"),
            store.history(10).map { it.snapshotId },
            "bounded history retains deterministic newest set",
        )
        eq(
            "snapshot-b",
            store.latestAtOrBefore(captured + 1_500)!!.snapshotId,
            "bounded cache supports deterministic historical lookup",
        )

        val restarted = FilePropagationSnapshotStore(dir, maxSnapshots = 2)
        eq(2, restarted.history(10).size, "smaller restart bound trims persisted history")
        eq(
            listOf("snapshot-d", "snapshot-c"),
            restarted.history(10).map { it.snapshotId },
            "restart trimming uses same deterministic order",
        )
        val restartedAgain = FilePropagationSnapshotStore(dir, maxSnapshots = 2)
        eq(restarted.history(10), restartedAgain.history(10), "trimmed history persists across restart")
    }

    private fun binaryCodecIsDeterministicAndComplete() {
        val snapshot = PropagationSnapshotAggregator().aggregate(
            capturedAtUtcMillis = captured,
            inputs = providerInputs(),
        )
        val bytesA = PropagationSnapshotBinaryCodec.encode(listOf(snapshot))
        val bytesB = PropagationSnapshotBinaryCodec.encode(listOf(snapshot))
        checkThat(bytesA.contentEquals(bytesB), "binary codec deterministic for identical snapshot")

        val decoded = PropagationSnapshotBinaryCodec.decode(bytesA)
        eq(listOf(snapshot), decoded, "binary codec round-trips complete snapshot")

        val reordered = snapshot.copy(
            solarGeomagnetic = snapshot.solarGeomagnetic.reversed(),
        )
        checkThat(
            !PropagationSnapshotBinaryCodec.encode(listOf(reordered)).contentEquals(bytesA),
            "codec preserves supplied evidence list ordering",
        )

        val aggregator = PropagationSnapshotAggregator()
        val idA = aggregator.aggregate(captured, providerInputs()).snapshotId
        val modifiedPath = pskPaths(pskrRetrieved2).first().copy(
            evidenceId = "PSKR-MODIFIED-SEMANTIC-ID",
            snrDb = -2.0,
        )
        val idB = aggregator.aggregate(
            capturedAtUtcMillis = captured,
            inputs = listOf(PropagationAggregationInput(heardPaths = listOf(modifiedPath))),
        ).snapshotId
        checkThat(idA != idB, "snapshot fingerprint changes when normalized evidence content changes")

        expectFailure("empty cache bytes") { PropagationSnapshotBinaryCodec.decode(byteArrayOf()) }
        expectFailure("trailing bytes rejected") {
            PropagationSnapshotBinaryCodec.decode(bytesA + byteArrayOf(0))
        }
    }

    private fun corruptedCacheFailsClosed() {
        val dir = Files.createTempDirectory("fieldops-cp0008e-corrupt-")
        val file = dir.resolve(FilePropagationSnapshotStore.FILE_NAME)
        Files.write(file, byteArrayOf(1, 2, 3, 4, 5))
        expectFailure("corrupt cache does not silently load") {
            FilePropagationSnapshotStore(dir)
        }

        val validDir = Files.createTempDirectory("fieldops-cp0008e-version-")
        val snapshot = PropagationSnapshotAggregator().aggregate(
            capturedAtUtcMillis = captured,
            inputs = providerInputs(),
        )
        FilePropagationSnapshotStore(validDir).save(snapshot)
        val validFile = validDir.resolve(FilePropagationSnapshotStore.FILE_NAME)
        val bytes = Files.readAllBytes(validFile)

        val magicLength = java.nio.ByteBuffer.wrap(bytes, 0, 4).int
        val versionOffset = 4 + magicLength
        val changed = bytes.copyOf()
        java.nio.ByteBuffer.wrap(changed, versionOffset, 4).putInt(999)
        Files.write(validFile, changed)
        expectFailure("unsupported cache version fails closed") {
            FilePropagationSnapshotStore(validDir)
        }
    }

    private fun platformTransportAndQsoSeparation() {
        val classes = listOf(
            PropagationSnapshotAggregator::class.java,
            PropagationSourceFreshnessDefaults::class.java,
            FilePropagationSnapshotStore::class.java,
            PropagationSnapshotBinaryCodec::class.java,
        )
        val fields = classes.flatMap { it.declaredFields.toList() }
        checkThat(
            fields.none {
                it.type.name.startsWith("android.") ||
                    it.type.name.startsWith("androidx.") ||
                    it.type.name.contains("GoogleMap") ||
                    it.type.name.contains("Mapbox")
            },
            "aggregation/cache carry no Android or map-SDK state",
        )
        checkThat(
            fields.none {
                it.type.name.contains("OkHttp") ||
                    it.type.name.contains("Retrofit") ||
                    it.type.name.contains("HttpClient") ||
                    it.type.name.contains("UsbManager")
            },
            "aggregation/cache carry no network/radio client state",
        )
        checkThat(
            PropagationAggregationInput::class.java.declaredFields.none {
                it.name.contains("qso", ignoreCase = true) ||
                    it.name.contains("lotw", ignoreCase = true)
            },
            "aggregation input has no QSO/LoTW state",
        )
        checkThat(
            PropagationSnapshot::class.java.declaredFields.none {
                it.name.contains("score", ignoreCase = true) ||
                    it.name.contains("heat", ignoreCase = true)
            },
            "snapshot remains evidence container rather than opaque heat score",
        )
    }

    private fun providerInputs(): List<PropagationAggregationInput> {
        val kp1 = noaaKp(noaaRetrieved1)
        val kp2 = noaaKp(noaaRetrieved2)
        val forecast1 = noaaForecast(noaaRetrieved1)
        val forecast2 = noaaForecast(noaaRetrieved2)
        val f107 = noaaF107(noaaRetrieved2)

        val glotec1 = glotec(glotecRetrieved1)
        val glotec2 = glotec(glotecRetrieved2)

        val pskr1 = pskPaths(pskrRetrieved1)
        val pskr2 = pskPaths(pskrRetrieved2).mapIndexed { index, value ->
            if (index == 0) value.copy(reportCount = 3) else value
        }

        return listOf(
            PropagationAggregationInput(
                solarGeomagnetic = kp1 + forecast1,
                ionosphericProducts = listOf(glotec1),
                heardPaths = pskr1,
                modeledPaths = listOf(modeledPath()),
            ),
            PropagationAggregationInput(
                solarGeomagnetic = kp2 + forecast2 + f107,
                ionosphericProducts = listOf(glotec2, pointIonosphericProduct()),
                heardPaths = pskr2,
            ),
        )
    }

    private fun noaaKp(retrievedAt: Long): List<SolarGeomagneticObservation> =
        NoaaSwpcPropagationAdapter.parsePlanetaryKp(
            Files.readString(
                root.resolve("research/propagation/fixtures/noaa_swpc_planetary_kp_sample.json")
            ),
            retrievedAt,
        ).map { it.observation }

    private fun noaaForecast(retrievedAt: Long): List<SolarGeomagneticObservation> =
        NoaaSwpcPropagationAdapter.parsePlanetaryKpForecast(
            Files.readString(
                root.resolve("research/propagation/fixtures/noaa_swpc_planetary_kp_forecast_sample.json")
            ),
            retrievedAt,
        ).map { it.observation }

    private fun noaaF107(retrievedAt: Long): List<SolarGeomagneticObservation> =
        NoaaSwpcPropagationAdapter.parseF107Summary(
            Files.readString(
                root.resolve("research/propagation/fixtures/noaa_swpc_f107_summary_sample.json")
            ),
            retrievedAt,
        ).map { it.observation }

    private fun glotec(retrievedAt: Long): IonosphericMapProduct =
        NoaaSwpcGlotecAdapter.parseGeoJson(
            json = Files.readString(
                root.resolve(
                    "research/propagation/fixtures/noaa_swpc_glotec_20260909T151500Z_bounded.geojson"
                )
            ),
            retrievedAtUtcMillis = retrievedAt,
            sourceUrl =
                NoaaSwpcGlotecAdapter.DIRECTORY_URL +
                    "glotec_icao_20260909T151500Z.geojson",
        ).product

    private fun pskPaths(retrievedAt: Long): List<HeardPathObservation> =
        PskReporterHeardPathAdapter.parseXml(
            xml = Files.readString(
                root.resolve("research/propagation/fixtures/psk_reporter_ag6k_20200903_bounded.xml")
            ),
            retrievedAtUtcMillis = retrievedAt,
            sourceUrl =
                PskReporterHeardPathAdapter.QUERY_URL +
                    "?senderCallsign=AG6K&rronly=1&noactive=1&rptlimit=4",
        ).observations

    private fun modeledPath(): ModeledPathEstimate =
        ModeledPathEstimate(
            evidenceId = "SYNTH_MODEL_PATH_CP0008E",
            source = PropagationSourceRef(
                sourceId = "SYNTH_MODEL_CP0008E",
                providerName = "Deterministic CP-0008E fixture",
                sourceClass = PropagationSourceClass.MODEL,
                sourceVersion = "cp0008e-v1",
                retrievedAtUtcMillis = noaaRetrieved2,
            ),
            observedAtUtcMillis = noaaRetrieved1,
            confidence = PropagationConfidence(
                value = 0.72,
                basis = PropagationConfidenceBasis.MODEL_OUTPUT,
                explanation = "Synthetic model record used only to verify evidence-category separation.",
            ),
            quality = setOf(PropagationDataQuality.SYNTHETIC),
            origin = gridPosition("JO40"),
            destination = gridPosition("FN31"),
            maximumUsableFrequencyHz = 18_000_000,
            lowestUsableFrequencyHz = 5_000_000,
            modelInputsSummary = "Deterministic CP-0008E synthetic model inputs.",
        )

    private fun pointIonosphericProduct(): IonosphericMapProduct =
        IonosphericMapProduct(
            evidenceId = "SYNTH_IONO_POINT_CP0008E",
            source = PropagationSourceRef(
                sourceId = "SYNTH_IONO_CP0008E",
                providerName = "Deterministic CP-0008E fixture",
                sourceClass = PropagationSourceClass.SYNTHETIC_FIXTURE,
                sourceVersion = "cp0008e-v1",
                retrievedAtUtcMillis = noaaRetrieved2,
            ),
            observedAtUtcMillis = 1_791_374_040_000L,
            confidence = PropagationConfidence(
                value = 0.60,
                basis = PropagationConfidenceBasis.SYNTHETIC,
                explanation = "Synthetic point ionospheric product for deterministic cache coverage.",
            ),
            quality = setOf(
                PropagationDataQuality.SYNTHETIC,
                PropagationDataQuality.ESTIMATED,
            ),
            coverage = PointPropagationCoverage(
                PropagationPosition(
                    coordinate = dev.n0png.fieldops.core.map.GeoCoordinate(
                        longitude = 8.25,
                        latitude = 50.08,
                    ),
                    method = PropagationLocationMethod.SYNTHETIC_FIXTURE,
                    sourceReference = "cp0008e-point",
                )
            ),
            metric = IonosphericMetric.FOF2_MHZ,
            samples = listOf(
                IonosphericSample(
                    position = PropagationPosition(
                        coordinate = dev.n0png.fieldops.core.map.GeoCoordinate(
                            longitude = 8.25,
                            latitude = 50.08,
                        ),
                        method = PropagationLocationMethod.SYNTHETIC_FIXTURE,
                        sourceReference = "cp0008e-point",
                    ),
                    value = 8.4,
                    confidence = PropagationConfidence(
                        value = 0.60,
                        basis = PropagationConfidenceBasis.SYNTHETIC,
                        explanation = "Synthetic sample confidence.",
                    ),
                    providerQualityCode = 2,
                    providerQualityExplanation = "Synthetic provider quality code.",
                )
            ),
            generatedAtUtcMillis = 1_791_374_100_000L,
        )

    private fun gridPosition(grid: String): PropagationPosition =
        PropagationPosition(
            maidenheadGrid = grid,
            method = PropagationLocationMethod.SYNTHETIC_FIXTURE,
            sourceReference = "cp0008e-fixture",
        )
}
