package dev.n0png.fieldops.core.logbook

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong

enum class QsoOrigin {
    LEGACY,
    MANUAL,
    DIGITAL_AUTO,
    IMPORTED,
}

data class QsoLocationSnapshot(
    val label: String? = null,
    val gridSquare: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    init {
        require(label == null || label.isNotBlank()) { "Location label must not be blank" }
        require(gridSquare == null || gridSquare.isNotBlank()) { "Grid square must not be blank" }
        require((latitude == null) == (longitude == null)) {
            "Latitude and longitude must be supplied together"
        }
        latitude?.let { require(it in -90.0..90.0) { "Latitude out of range" } }
        longitude?.let { require(it in -180.0..180.0) { "Longitude out of range" } }
    }

    val normalizedGrid: String?
        get() = gridSquare?.trim()?.uppercase()
}

data class OperatingSession(
    val id: String,
    val stationProfileId: String,
    val stationCallsign: String,
    val startedUtcMillis: Long,
    val endedUtcMillis: Long? = null,
    val stationLocation: QsoLocationSnapshot = QsoLocationSnapshot(),
    val radio: String = "Yaesu FTX-1",
    val antennaNotes: String? = null,
    val defaultPowerWatts: Double? = null,
    val activityTags: Set<String> = emptySet(),
    val notes: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Operating session id must not be blank" }
        require(stationProfileId.isNotBlank()) { "Station profile id must not be blank" }
        require(stationCallsign.isNotBlank()) { "Station callsign must not be blank" }
        require(startedUtcMillis >= 0) { "Session UTC start must be non-negative" }
        endedUtcMillis?.let {
            require(it >= startedUtcMillis) { "Session end must not precede start" }
        }
        defaultPowerWatts?.let { require(it > 0.0) { "Default power must be positive" } }
    }

    val normalizedStationCallsign: String
        get() = stationCallsign.trim().uppercase()
}

data class QsoRadioContext(
    val frequencyHz: Long,
    val radioMode: String,
    val band: String? = null,
    val txPowerWatts: Double? = null,
) {
    init {
        require(frequencyHz > 0) { "Frequency must be positive" }
        require(radioMode.isNotBlank()) { "Exact radio mode must not be blank" }
        require(band == null || band.isNotBlank()) { "Band must not be blank" }
        txPowerWatts?.let { require(it > 0.0) { "TX power must be positive" } }
    }

    val resolvedBand: String
        get() = band?.trim()?.lowercase()
            ?: AmateurBandCatalog.bandFor(frequencyHz)
            ?: throw IllegalArgumentException(
                "Frequency ${frequencyHz} Hz is outside the built-in band catalog; supply band explicitly"
            )
}

enum class ManualQsoMode(val adifMode: String) {
    SSB("SSB"),
    CW("CW"),
}

data class ManualQsoInput(
    val call: String,
    val mode: ManualQsoMode,
    val rstSent: String? = null,
    val rstRcvd: String? = null,
    val remoteLocation: QsoLocationSnapshot = QsoLocationSnapshot(),
    val startedUtcMillis: Long? = null,
    val endedUtcMillis: Long? = null,
    val notes: String? = null,
) {
    init {
        require(call.isNotBlank()) { "Callsign must not be blank" }
        rstSent?.let { require(it.isNotBlank()) { "RST sent must not be blank" } }
        rstRcvd?.let { require(it.isNotBlank()) { "RST received must not be blank" } }
        if (startedUtcMillis != null && endedUtcMillis != null) {
            require(endedUtcMillis >= startedUtcMillis) { "QSO end must not precede start" }
        }
    }
}

/**
 * A provider may emit this only after it considers a two-way contact complete.
 *
 * The provider supplies exact ADIF MODE/SUBMODE identity. FieldOps does not
 * collapse a digital submode merely because it belongs to a parent family.
 */
data class DigitalCompletedContact(
    val providerId: String,
    val call: String,
    val adifMode: String,
    val adifSubmode: String? = null,
    val startedUtcMillis: Long,
    val endedUtcMillis: Long? = null,
    val frequencyHz: Long,
    val band: String? = null,
    val radioMode: String,
    val rstSent: String? = null,
    val rstRcvd: String? = null,
    val remoteLocation: QsoLocationSnapshot = QsoLocationSnapshot(),
    val notes: String? = null,
    val completed: Boolean = true,
) {
    init {
        require(providerId.isNotBlank()) { "Digital provider id must not be blank" }
        require(call.isNotBlank()) { "Callsign must not be blank" }
        require(adifMode.isNotBlank()) { "ADIF mode must not be blank" }
        require(adifSubmode == null || adifSubmode.isNotBlank()) { "ADIF submode must not be blank" }
        require(startedUtcMillis >= 0) { "QSO UTC start must be non-negative" }
        endedUtcMillis?.let { require(it >= startedUtcMillis) { "QSO end must not precede start" } }
        require(frequencyHz > 0) { "Frequency must be positive" }
        require(radioMode.isNotBlank()) { "Exact radio mode must not be blank" }
    }
}

data class QsoDraft(
    val call: String,
    val mode: String,
    val submode: String?,
    val radioMode: String,
    val frequencyHz: Long,
    val band: String,
    val startedUtcMillis: Long,
    val endedUtcMillis: Long?,
    val rstSent: String?,
    val rstRcvd: String?,
    val remoteLocation: QsoLocationSnapshot,
    val origin: QsoOrigin,
    val sourceProvider: String?,
    val notes: String?,
    val txPowerWatts: Double?,
)

fun interface DigitalAutoLogAdapter<T> {
    fun toDraft(contact: T, session: OperatingSession): QsoDraft
}

object CompletedDigitalContactAdapter : DigitalAutoLogAdapter<DigitalCompletedContact> {
    override fun toDraft(contact: DigitalCompletedContact, session: OperatingSession): QsoDraft {
        require(contact.completed) { "Digital contact is not complete and must not be auto-logged" }
        val band = contact.band?.trim()?.lowercase()
            ?: AmateurBandCatalog.bandFor(contact.frequencyHz)
            ?: throw IllegalArgumentException(
                "Frequency ${contact.frequencyHz} Hz is outside the built-in band catalog; supply band explicitly"
            )
        return QsoDraft(
            call = contact.call,
            mode = contact.adifMode,
            submode = contact.adifSubmode,
            radioMode = contact.radioMode,
            frequencyHz = contact.frequencyHz,
            band = band,
            startedUtcMillis = contact.startedUtcMillis,
            endedUtcMillis = contact.endedUtcMillis,
            rstSent = contact.rstSent,
            rstRcvd = contact.rstRcvd,
            remoteLocation = contact.remoteLocation,
            origin = QsoOrigin.DIGITAL_AUTO,
            sourceProvider = contact.providerId,
            notes = contact.notes,
            txPowerWatts = null,
        )
    }
}

interface LogbookRepository {
    fun save(qso: QsoRecord): QsoRecord
    fun get(id: Long): QsoRecord?
    fun all(): List<QsoRecord>
}

class InMemoryLogbookRepository : LogbookRepository {
    private val records = linkedMapOf<Long, QsoRecord>()

    @Synchronized
    override fun save(qso: QsoRecord): QsoRecord {
        require(qso.id !in records) { "QSO id ${qso.id} already exists" }
        records[qso.id] = qso
        return qso
    }

    @Synchronized
    override fun get(id: Long): QsoRecord? = records[id]

    @Synchronized
    override fun all(): List<QsoRecord> = records.values.toList()
}

fun interface QsoIdSource {
    fun nextId(): Long
}

class SequentialQsoIdSource(startAt: Long = 1L) : QsoIdSource {
    private val next = AtomicLong(startAt)

    init {
        require(startAt > 0) { "QSO ids must start above zero" }
    }

    override fun nextId(): Long = next.getAndIncrement()
}

fun interface UtcMillisClock {
    fun nowMillis(): Long

    companion object {
        val SYSTEM = UtcMillisClock { System.currentTimeMillis() }
    }
}

class FastQsoLogger(
    private val repository: LogbookRepository,
    private val idSource: QsoIdSource,
    private val clock: UtcMillisClock = UtcMillisClock.SYSTEM,
) {
    fun logManual(
        input: ManualQsoInput,
        session: OperatingSession,
        radio: QsoRadioContext,
    ): QsoRecord {
        val started = input.startedUtcMillis ?: clock.nowMillis()
        val ended = input.endedUtcMillis
        require(ended == null || ended >= started) { "QSO end must not precede start" }

        return persist(
            QsoDraft(
                call = input.call,
                mode = input.mode.adifMode,
                submode = null,
                radioMode = radio.radioMode,
                frequencyHz = radio.frequencyHz,
                band = radio.resolvedBand,
                startedUtcMillis = started,
                endedUtcMillis = ended,
                rstSent = input.rstSent,
                rstRcvd = input.rstRcvd,
                remoteLocation = input.remoteLocation,
                origin = QsoOrigin.MANUAL,
                sourceProvider = null,
                notes = input.notes,
                txPowerWatts = radio.txPowerWatts ?: session.defaultPowerWatts,
            ),
            session,
        )
    }

    fun logDigital(
        contact: DigitalCompletedContact,
        session: OperatingSession,
        adapter: DigitalAutoLogAdapter<DigitalCompletedContact> = CompletedDigitalContactAdapter,
    ): QsoRecord = persist(adapter.toDraft(contact, session), session)

    private fun persist(draft: QsoDraft, session: OperatingSession): QsoRecord {
        require(draft.startedUtcMillis >= session.startedUtcMillis) {
            "QSO starts before its operating session"
        }
        session.endedUtcMillis?.let {
            require(draft.startedUtcMillis <= it) { "QSO starts after its operating session ended" }
        }

        val start = QsoUtcFields.fromEpochMillis(draft.startedUtcMillis)
        val end = draft.endedUtcMillis?.let(QsoUtcFields::fromEpochMillis)
        val stationGrid = session.stationLocation.normalizedGrid
        val remoteGrid = draft.remoteLocation.normalizedGrid

        val qso = QsoRecord(
            id = idSource.nextId(),
            call = draft.call.trim().uppercase(),
            stationCallsign = session.normalizedStationCallsign,
            qsoDate = start.date,
            timeOn = start.time,
            band = draft.band.trim().lowercase(),
            mode = draft.mode.trim().uppercase(),
            freqMhz = FrequencyFormat.mhz(draft.frequencyHz),
            grid = remoteGrid,
            rstSent = draft.rstSent?.trim(),
            rstRcvd = draft.rstRcvd?.trim(),
            myGridSquare = stationGrid,
            submode = draft.submode?.trim()?.uppercase(),
            radioMode = draft.radioMode.trim().uppercase(),
            frequencyHz = draft.frequencyHz,
            startedUtcMillis = draft.startedUtcMillis,
            endedUtcMillis = draft.endedUtcMillis,
            qsoDateOff = end?.date,
            timeOff = end?.time,
            stationProfileId = session.stationProfileId,
            sessionId = session.id,
            remoteLocation = draft.remoteLocation,
            stationLocation = session.stationLocation,
            origin = draft.origin,
            sourceProvider = draft.sourceProvider,
            notes = draft.notes,
            txPowerWatts = draft.txPowerWatts,
        )
        return repository.save(qso)
    }
}

data class QsoUtcFields(val date: String, val time: String) {
    companion object {
        private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)
        private val timeFormatter = DateTimeFormatter.ofPattern("HHmmss").withZone(ZoneOffset.UTC)

        fun fromEpochMillis(epochMillis: Long): QsoUtcFields {
            require(epochMillis >= 0) { "UTC epoch must be non-negative" }
            val instant = Instant.ofEpochMilli(epochMillis)
            return QsoUtcFields(
                date = dateFormatter.format(instant),
                time = timeFormatter.format(instant),
            )
        }
    }
}

object FrequencyFormat {
    fun mhz(frequencyHz: Long): String {
        require(frequencyHz > 0)
        val whole = frequencyHz / 1_000_000L
        val fraction = (frequencyHz % 1_000_000L).toString().padStart(6, '0').trimEnd('0')
        return if (fraction.isEmpty()) whole.toString() else "$whole.$fraction"
    }
}

/**
 * Conservative label helper for common amateur bands.
 *
 * Explicit caller-provided band always wins. Frequencies outside these broad
 * label ranges require an explicit band rather than being guessed.
 */
object AmateurBandCatalog {
    private data class Entry(val band: String, val lowHz: Long, val highHz: Long)

    private val entries = listOf(
        Entry("2200m", 135_700L, 137_800L),
        Entry("630m", 472_000L, 479_000L),
        Entry("160m", 1_800_000L, 2_000_000L),
        Entry("80m", 3_500_000L, 4_000_000L),
        Entry("60m", 5_000_000L, 5_500_000L),
        Entry("40m", 7_000_000L, 7_300_000L),
        Entry("30m", 10_100_000L, 10_150_000L),
        Entry("20m", 14_000_000L, 14_350_000L),
        Entry("17m", 18_068_000L, 18_168_000L),
        Entry("15m", 21_000_000L, 21_450_000L),
        Entry("12m", 24_890_000L, 24_990_000L),
        Entry("10m", 28_000_000L, 29_700_000L),
        Entry("6m", 50_000_000L, 54_000_000L),
        Entry("4m", 70_000_000L, 71_000_000L),
        Entry("2m", 144_000_000L, 148_000_000L),
        Entry("1.25m", 222_000_000L, 225_000_000L),
        Entry("70cm", 420_000_000L, 450_000_000L),
        Entry("33cm", 902_000_000L, 928_000_000L),
        Entry("23cm", 1_240_000_000L, 1_300_000_000L),
    )

    fun bandFor(frequencyHz: Long): String? =
        entries.firstOrNull { frequencyHz in it.lowHz..it.highHz }?.band
}
