package dev.n0png.fieldops.core.propagation

import dev.n0png.fieldops.core.map.GeoBounds
import dev.n0png.fieldops.core.map.GeoCoordinate
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

class FilePropagationSnapshotStore(
    directory: Path,
    private val maxSnapshots: Int = 24,
) : PropagationSnapshotStore {
    private val cacheFile: Path
    private val snapshots = linkedMapOf<String, PropagationSnapshot>()

    init {
        require(maxSnapshots > 0) { "Propagation cache maxSnapshots must be positive" }
        Files.createDirectories(directory)
        require(Files.isDirectory(directory)) {
            "Propagation cache path must be a directory"
        }
        cacheFile = directory.resolve(FILE_NAME)

        if (Files.exists(cacheFile)) {
            val decoded = PropagationSnapshotBinaryCodec.decode(Files.readAllBytes(cacheFile))
            decoded.forEach { snapshot ->
                require(!snapshots.containsKey(snapshot.snapshotId)) {
                    "Propagation cache contains duplicate snapshot id ${snapshot.snapshotId}"
                }
                snapshots[snapshot.snapshotId] = snapshot
            }
            val changed = trimToLimit()
            if (changed) persist()
        }
    }

    @Synchronized
    override fun save(snapshot: PropagationSnapshot) {
        val existing = snapshots[snapshot.snapshotId]
        require(existing == null || existing == snapshot) {
            "Propagation snapshot id ${snapshot.snapshotId} already exists with different content"
        }
        snapshots[snapshot.snapshotId] = snapshot
        trimToLimit()
        persist()
    }

    @Synchronized
    override fun latest(): PropagationSnapshot? =
        snapshots.values.maxWithOrNull(snapshotComparator)

    @Synchronized
    override fun snapshot(snapshotId: String): PropagationSnapshot? {
        require(snapshotId.isNotBlank()) { "Propagation snapshot lookup id must not be blank" }
        return snapshots[snapshotId]
    }

    @Synchronized
    override fun latestAtOrBefore(utcMillis: Long): PropagationSnapshot? {
        require(utcMillis >= 0) { "Propagation lookup UTC must be non-negative" }
        return snapshots.values
            .asSequence()
            .filter { it.capturedAtUtcMillis <= utcMillis }
            .maxWithOrNull(snapshotComparator)
    }

    @Synchronized
    override fun history(limit: Int): List<PropagationSnapshot> {
        require(limit > 0) { "Propagation history limit must be positive" }
        return snapshots.values
            .sortedWith(snapshotComparator.reversed())
            .take(limit)
    }

    private fun trimToLimit(): Boolean {
        if (snapshots.size <= maxSnapshots) return false

        val keep = snapshots.values
            .sortedWith(snapshotComparator.reversed())
            .take(maxSnapshots)
            .associateByTo(linkedMapOf()) { it.snapshotId }

        snapshots.clear()
        snapshots.putAll(keep)
        return true
    }

    private fun persist() {
        val ordered = snapshots.values.sortedWith(snapshotComparator)
        val bytes = PropagationSnapshotBinaryCodec.encode(ordered)
        val temp = cacheFile.resolveSibling(cacheFile.fileName.toString() + ".tmp")

        Files.write(
            temp,
            bytes,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
        try {
            Files.move(
                temp,
                cacheFile,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temp,
                cacheFile,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    companion object {
        const val FILE_NAME = "propagation-snapshots-v1.bin"

        private val snapshotComparator =
            compareBy<PropagationSnapshot>({ it.capturedAtUtcMillis }, { it.snapshotId })
    }
}

internal object PropagationSnapshotBinaryCodec {
    private const val MAGIC = "FIELDOPS_PROPAGATION_SNAPSHOT_CACHE"
    private const val VERSION = 1
    private const val MAX_COLLECTION = 100_000
    private const val MAX_STRING_BYTES = 1_000_000

    fun encode(snapshots: List<PropagationSnapshot>): ByteArray {
        require(snapshots.size <= MAX_COLLECTION) {
            "Propagation cache snapshot collection is too large"
        }
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            val writer = Writer(data)
            writer.string(MAGIC)
            data.writeInt(VERSION)
            data.writeInt(snapshots.size)
            snapshots.forEach { writer.snapshot(it) }
        }
        return output.toByteArray()
    }

    fun decode(bytes: ByteArray): List<PropagationSnapshot> {
        require(bytes.isNotEmpty()) { "Propagation cache file must not be empty" }
        val inputBytes = ByteArrayInputStream(bytes)
        val input = DataInputStream(inputBytes)
        val reader = Reader(input)

        require(reader.string() == MAGIC) { "Propagation cache magic mismatch" }
        require(input.readInt() == VERSION) { "Unsupported propagation cache version" }
        val count = reader.count("snapshot")
        val snapshots = List(count) { reader.snapshot() }
        require(inputBytes.available() == 0) {
            "Trailing bytes after propagation cache payload"
        }
        return snapshots
    }

    private class Writer(
        private val out: DataOutputStream,
    ) {
        fun snapshot(snapshot: PropagationSnapshot) {
            string(snapshot.snapshotId)
            out.writeLong(snapshot.capturedAtUtcMillis)

            out.writeInt(snapshot.solarGeomagnetic.size)
            snapshot.solarGeomagnetic.forEach(::solar)

            out.writeInt(snapshot.ionosphericProducts.size)
            snapshot.ionosphericProducts.forEach(::ionospheric)

            out.writeInt(snapshot.heardPaths.size)
            snapshot.heardPaths.forEach(::heard)

            out.writeInt(snapshot.modeledPaths.size)
            snapshot.modeledPaths.forEach(::modeled)
        }

        private fun solar(value: SolarGeomagneticObservation) {
            evidenceHeader(value)
            nullableDouble(value.f107SolarFluxSfu)
            nullableDouble(value.planetaryKp)
            nullableDouble(value.planetaryAp)
            nullableDouble(value.sunspotNumber)
            nullableDouble(value.xrayFluxWattsPerSquareMeter)
        }

        private fun ionospheric(value: IonosphericMapProduct) {
            evidenceHeader(value)
            coverage(value.coverage)
            string(value.metric.name)
            out.writeInt(value.samples.size)
            value.samples.forEach(::ionosphericSample)
            nullableDouble(value.referenceDistanceKm)
            nullableLong(value.generatedAtUtcMillis)
        }

        private fun heard(value: HeardPathObservation) {
            evidenceHeader(value)
            endpoint(value.transmitter)
            endpoint(value.receiver)
            out.writeLong(value.frequencyHz)
            string(value.band)
            string(value.mode)
            nullableDouble(value.snrDb)
            out.writeInt(value.reportCount)
        }

        private fun modeled(value: ModeledPathEstimate) {
            evidenceHeader(value)
            position(value.origin)
            position(value.destination)
            nullableLong(value.maximumUsableFrequencyHz)
            nullableLong(value.lowestUsableFrequencyHz)
            string(value.modelInputsSummary)
        }

        private fun evidenceHeader(value: PropagationEvidence) {
            string(value.evidenceId)
            source(value.source)
            out.writeLong(value.observedAtUtcMillis)
            confidence(value.confidence)
            val quality = value.quality.map { it.name }.sorted()
            out.writeInt(quality.size)
            quality.forEach(::string)
        }

        private fun source(value: PropagationSourceRef) {
            string(value.sourceId)
            string(value.providerName)
            string(value.sourceClass.name)
            string(value.sourceVersion)
            out.writeLong(value.retrievedAtUtcMillis)
            nullableString(value.sourceUrl)
            nullableString(value.termsUrl)
        }

        private fun confidence(value: PropagationConfidence) {
            out.writeDouble(value.value)
            string(value.basis.name)
            string(value.explanation)
        }

        private fun coverage(value: PropagationCoverage) {
            when (value) {
                GlobalPropagationCoverage -> out.writeByte(0)
                is PointPropagationCoverage -> {
                    out.writeByte(1)
                    position(value.position)
                }
                is BoundsPropagationCoverage -> {
                    out.writeByte(2)
                    out.writeDouble(value.bounds.west)
                    out.writeDouble(value.bounds.south)
                    out.writeDouble(value.bounds.east)
                    out.writeDouble(value.bounds.north)
                }
                is PathPropagationCoverage -> {
                    out.writeByte(3)
                    position(value.origin)
                    position(value.destination)
                }
            }
        }

        private fun ionosphericSample(value: IonosphericSample) {
            position(value.position)
            out.writeDouble(value.value)
            nullableConfidence(value.confidence)
            nullableInt(value.providerQualityCode)
            nullableString(value.providerQualityExplanation)
        }

        private fun endpoint(value: PropagationEndpoint) {
            position(value.location)
            nullableString(value.callsign)
            nullableString(value.label)
        }

        private fun position(value: PropagationPosition) {
            val coordinate = value.coordinate
            out.writeBoolean(coordinate != null)
            if (coordinate != null) {
                out.writeDouble(coordinate.longitude)
                out.writeDouble(coordinate.latitude)
            }
            nullableString(value.maidenheadGrid)
            string(value.method.name)
            nullableString(value.sourceReference)
        }

        fun string(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            require(bytes.size <= MAX_STRING_BYTES) {
                "Propagation cache string is too large"
            }
            out.writeInt(bytes.size)
            out.write(bytes)
        }

        private fun nullableString(value: String?) {
            out.writeBoolean(value != null)
            if (value != null) string(value)
        }

        private fun nullableLong(value: Long?) {
            out.writeBoolean(value != null)
            if (value != null) out.writeLong(value)
        }

        private fun nullableInt(value: Int?) {
            out.writeBoolean(value != null)
            if (value != null) out.writeInt(value)
        }

        private fun nullableDouble(value: Double?) {
            out.writeBoolean(value != null)
            if (value != null) out.writeDouble(value)
        }

        private fun nullableConfidence(value: PropagationConfidence?) {
            out.writeBoolean(value != null)
            if (value != null) confidence(value)
        }
    }

    private class Reader(
        private val input: DataInputStream,
    ) {
        fun snapshot(): PropagationSnapshot {
            val snapshotId = string()
            val capturedAt = input.readLong()

            val solar = List(count("solar observation")) { solar() }
            val ionospheric = List(count("ionospheric product")) { ionospheric() }
            val heard = List(count("heard path")) { heard() }
            val modeled = List(count("modeled path")) { modeled() }

            return PropagationSnapshot(
                snapshotId = snapshotId,
                capturedAtUtcMillis = capturedAt,
                solarGeomagnetic = solar,
                ionosphericProducts = ionospheric,
                heardPaths = heard,
                modeledPaths = modeled,
            )
        }

        private fun solar(): SolarGeomagneticObservation {
            val header = evidenceHeader()
            return SolarGeomagneticObservation(
                evidenceId = header.evidenceId,
                source = header.source,
                observedAtUtcMillis = header.observedAtUtcMillis,
                confidence = header.confidence,
                quality = header.quality,
                f107SolarFluxSfu = nullableDouble(),
                planetaryKp = nullableDouble(),
                planetaryAp = nullableDouble(),
                sunspotNumber = nullableDouble(),
                xrayFluxWattsPerSquareMeter = nullableDouble(),
            )
        }

        private fun ionospheric(): IonosphericMapProduct {
            val header = evidenceHeader()
            val coverage = coverage()
            val metric = enumValueOf<IonosphericMetric>(string())
            val samples = List(count("ionospheric sample")) { ionosphericSample() }
            return IonosphericMapProduct(
                evidenceId = header.evidenceId,
                source = header.source,
                observedAtUtcMillis = header.observedAtUtcMillis,
                confidence = header.confidence,
                quality = header.quality,
                coverage = coverage,
                metric = metric,
                samples = samples,
                referenceDistanceKm = nullableDouble(),
                generatedAtUtcMillis = nullableLong(),
            )
        }

        private fun heard(): HeardPathObservation {
            val header = evidenceHeader()
            return HeardPathObservation(
                evidenceId = header.evidenceId,
                source = header.source,
                observedAtUtcMillis = header.observedAtUtcMillis,
                confidence = header.confidence,
                quality = header.quality,
                transmitter = endpoint(),
                receiver = endpoint(),
                frequencyHz = input.readLong(),
                band = string(),
                mode = string(),
                snrDb = nullableDouble(),
                reportCount = input.readInt().also {
                    require(it > 0) { "Propagation cache heard-path reportCount must be positive" }
                },
            )
        }

        private fun modeled(): ModeledPathEstimate {
            val header = evidenceHeader()
            return ModeledPathEstimate(
                evidenceId = header.evidenceId,
                source = header.source,
                observedAtUtcMillis = header.observedAtUtcMillis,
                confidence = header.confidence,
                quality = header.quality,
                origin = position(),
                destination = position(),
                maximumUsableFrequencyHz = nullableLong(),
                lowestUsableFrequencyHz = nullableLong(),
                modelInputsSummary = string(),
            )
        }

        private fun evidenceHeader(): EvidenceHeader {
            val evidenceId = string()
            val source = source()
            val observedAt = input.readLong()
            val confidence = confidence()
            val qualityCount = count("quality")
            val quality = List(qualityCount) {
                enumValueOf<PropagationDataQuality>(string())
            }.toSet()
            require(quality.size == qualityCount) {
                "Propagation cache contains duplicate quality flags"
            }
            return EvidenceHeader(
                evidenceId = evidenceId,
                source = source,
                observedAtUtcMillis = observedAt,
                confidence = confidence,
                quality = quality,
            )
        }

        private fun source(): PropagationSourceRef =
            PropagationSourceRef(
                sourceId = string(),
                providerName = string(),
                sourceClass = enumValueOf<PropagationSourceClass>(string()),
                sourceVersion = string(),
                retrievedAtUtcMillis = input.readLong(),
                sourceUrl = nullableString(),
                termsUrl = nullableString(),
            )

        private fun confidence(): PropagationConfidence =
            PropagationConfidence(
                value = input.readDouble(),
                basis = enumValueOf<PropagationConfidenceBasis>(string()),
                explanation = string(),
            )

        private fun coverage(): PropagationCoverage =
            when (val kind = input.readUnsignedByte()) {
                0 -> GlobalPropagationCoverage
                1 -> PointPropagationCoverage(position())
                2 -> BoundsPropagationCoverage(
                    GeoBounds(
                        west = input.readDouble(),
                        south = input.readDouble(),
                        east = input.readDouble(),
                        north = input.readDouble(),
                    )
                )
                3 -> PathPropagationCoverage(
                    origin = position(),
                    destination = position(),
                )
                else -> throw IllegalArgumentException(
                    "Unknown propagation coverage kind: $kind"
                )
            }

        private fun ionosphericSample(): IonosphericSample =
            IonosphericSample(
                position = position(),
                value = input.readDouble(),
                confidence = nullableConfidence(),
                providerQualityCode = nullableInt(),
                providerQualityExplanation = nullableString(),
            )

        private fun endpoint(): PropagationEndpoint =
            PropagationEndpoint(
                location = position(),
                callsign = nullableString(),
                label = nullableString(),
            )

        private fun position(): PropagationPosition {
            val coordinate =
                if (input.readBoolean()) {
                    GeoCoordinate(
                        longitude = input.readDouble(),
                        latitude = input.readDouble(),
                    )
                } else {
                    null
                }
            return PropagationPosition(
                coordinate = coordinate,
                maidenheadGrid = nullableString(),
                method = enumValueOf<PropagationLocationMethod>(string()),
                sourceReference = nullableString(),
            )
        }

        fun string(): String {
            val length = input.readInt()
            require(length in 0..MAX_STRING_BYTES) {
                "Propagation cache string length out of bounds: $length"
            }
            val bytes = ByteArray(length)
            input.readFully(bytes)
            return bytes.toString(Charsets.UTF_8)
        }

        fun count(label: String): Int {
            val value = input.readInt()
            require(value in 0..MAX_COLLECTION) {
                "Propagation cache $label count out of bounds: $value"
            }
            return value
        }

        private fun nullableString(): String? =
            if (input.readBoolean()) string() else null

        private fun nullableLong(): Long? =
            if (input.readBoolean()) input.readLong() else null

        private fun nullableInt(): Int? =
            if (input.readBoolean()) input.readInt() else null

        private fun nullableDouble(): Double? =
            if (input.readBoolean()) input.readDouble() else null

        private fun nullableConfidence(): PropagationConfidence? =
            if (input.readBoolean()) confidence() else null
    }

    private data class EvidenceHeader(
        val evidenceId: String,
        val source: PropagationSourceRef,
        val observedAtUtcMillis: Long,
        val confidence: PropagationConfidence,
        val quality: Set<PropagationDataQuality>,
    )
}
