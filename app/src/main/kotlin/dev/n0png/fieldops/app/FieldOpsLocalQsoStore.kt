package dev.n0png.fieldops.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.n0png.fieldops.core.logbook.InMemoryLogbookRepository
import dev.n0png.fieldops.core.logbook.LogbookRepository
import dev.n0png.fieldops.core.logbook.LotwConfirmationState
import dev.n0png.fieldops.core.logbook.LotwUploadState
import dev.n0png.fieldops.core.logbook.QsoLocationSnapshot
import dev.n0png.fieldops.core.logbook.QsoOrigin
import dev.n0png.fieldops.core.logbook.QsoRecord
import org.json.JSONObject

/**
 * Explicitly opt-in local QSO storage. This is not an encrypted archive,
 * a LoTW synchronization service, or a backup surviving app uninstall.
 * No database is created until the operator enables local storage.
 */
internal class FieldOpsLocalQsoStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE, null, 1) {
    private val app = context.applicationContext

    data class Snapshot(val enabled: Boolean, val contacts: List<QsoRecord>)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
        db.execSQL("CREATE TABLE contacts (id INTEGER PRIMARY KEY NOT NULL, payload TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Never destroy the authoritative contact log to make an upgrade pass.
        throw IllegalStateException("QSO database migration requires explicit support")
    }

    fun load(): Snapshot {
        if (!app.getDatabasePath(DATABASE).exists()) return Snapshot(false, emptyList())
        val db = readableDatabase
        val enabled = db.rawQuery(
            "SELECT value FROM settings WHERE key = 'enabled'", null
        ).use { if (it.moveToFirst()) it.getString(0) == "1" else false }
        val result = ArrayList<QsoRecord>()
        db.rawQuery("SELECT id, payload FROM contacts ORDER BY id", null).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val record = FieldOpsQsoJson.decode(cursor.getString(1))
                require(id > 0 && id == record.id) { "Stored QSO id mismatch" }
                result.add(record)
            }
        }
        require(enabled || result.isEmpty()) {
            "Uncommitted local QSO data present; refuse an empty fallback"
        }
        return Snapshot(enabled, result)
    }

    fun enable(contacts: List<QsoRecord>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val already = db.rawQuery(
                "SELECT value FROM settings WHERE key = 'enabled'", null
            ).use { it.moveToFirst() && it.getString(0) == "1" }
            check(!already) { "Local log already enabled; reload the stored log" }
            check(db.rawQuery("SELECT id FROM contacts LIMIT 1", null).use {
                !it.moveToFirst()
            }) { "Unresolved stored contacts; refusing replacement" }
            insert(db, contacts)
            val values = ContentValues().apply {
                put("key", "enabled")
                put("value", "1")
            }
            db.insertOrThrow("settings", null, values)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun insertBatch(records: List<QsoRecord>) {
        if (records.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            insert(db, records)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun update(record: QsoRecord) {
        val db = writableDatabase
        val value = ContentValues().apply { put("payload", FieldOpsQsoJson.encode(record)) }
        check(db.update("contacts", value, "id = ?", arrayOf(record.id.toString())) == 1) {
            "Stored QSO disappeared during update"
        }
    }

    private fun insert(db: SQLiteDatabase, records: List<QsoRecord>) {
        for (record in records) {
            require(record.id > 0) { "Persistent QSO requires positive id" }
            val values = ContentValues().apply {
                put("id", record.id)
                put("payload", FieldOpsQsoJson.encode(record))
            }
            db.insertOrThrow("contacts", null, values)
        }
    }

    companion object {
        const val DATABASE = "fieldops-qso-v1.db"
    }
}

/**
 * The canonical LogbookRepository used by the Android operating session.
 * A successful local write precedes the in-memory mirror mutation.
 * Import batches are all-or-nothing in SQLite. Storage read errors block
 * new writes instead of silently resetting or overwriting a corrupt log.
 */
internal class OptInQsoRepository(context: Context) : LogbookRepository, AutoCloseable {
    private val store = FieldOpsLocalQsoStore(context)
    private val cache = InMemoryLogbookRepository()
    private val load = runCatching { store.load() }
    val storageError: String? = load.exceptionOrNull()?.let {
        "Cannot read the existing app-private QSO database. Local writes blocked; data not reset."
    }
    var isPersistent: Boolean = load.getOrNull()?.enabled == true
        private set
    val nextRecordId: Long

    init {
        val loaded = load.getOrNull()?.contacts.orEmpty()
        for (record in loaded) cache.save(record)
        val max = loaded.maxOfOrNull { it.id } ?: 0L
        require(max < Long.MAX_VALUE) { "No QSO identifiers remain" }
        nextRecordId = max + 1L
    }

    private fun ensureWritable() {
        check(storageError == null) { "Existing QSO store cannot be read; writes locked" }
    }

    @Synchronized
    fun enableLocalPersistence() {
        ensureWritable()
        check(!isPersistent) { "Local QSO storage is already enabled" }
        store.enable(cache.all())
        isPersistent = true
    }

    @Synchronized
    fun saveBatch(records: List<QsoRecord>) {
        ensureWritable()
        require(records.map { it.id }.distinct().size == records.size) {
            "Duplicate QSO identifiers in import batch"
        }
        require(records.all { it.id > 0 && cache.get(it.id) == null }) {
            "QSO id already exists"
        }
        if (isPersistent) store.insertBatch(records)
        records.forEach(cache::save)
    }

    @Synchronized
    override fun save(qso: QsoRecord): QsoRecord {
        saveBatch(listOf(qso))
        return qso
    }

    @Synchronized
    override fun update(qso: QsoRecord): QsoRecord {
        ensureWritable()
        require(cache.get(qso.id) != null) { "Cannot update unknown QSO" }
        if (isPersistent) store.update(qso)
        return cache.update(qso)
    }

    override fun get(id: Long): QsoRecord? = cache.get(id)

    override fun all(): List<QsoRecord> = cache.all()

    override fun close() = store.close()
}

/**
 * Lossless storage of the CURRENT QsoRecord contract, including station,
 * timing, radio metadata, location and LoTW state. No credentials are stored.
 * The separate operator-controlled ADIF exporter deliberately has a narrower
 * interoperability/restore contract.
 */
internal object FieldOpsQsoJson {
    private fun JSONObject.putOptional(name: String, value: Any?) {
        if (value != null) put(name, value)
    }

    private fun JSONObject.optionalText(name: String): String? =
        if (isNull(name)) null else getString(name)

    private fun JSONObject.optionalInt(name: String): Int? =
        if (isNull(name)) null else getInt(name)

    private fun JSONObject.optionalLong(name: String): Long? =
        if (isNull(name)) null else getLong(name)

    private fun JSONObject.optionalDouble(name: String): Double? =
        if (isNull(name)) null else getDouble(name)

    private fun location(value: QsoLocationSnapshot?): JSONObject? = value?.let {
        JSONObject().apply {
            putOptional("label", it.label)
            putOptional("gridSquare", it.gridSquare)
            putOptional("latitude", it.latitude)
            putOptional("longitude", it.longitude)
        }
    }

    private fun JSONObject.optionalLocation(name: String): QsoLocationSnapshot? {
        if (isNull(name)) return null
        val loc = getJSONObject(name)
        return QsoLocationSnapshot(
            label = loc.optionalText("label"),
            gridSquare = loc.optionalText("gridSquare"),
            latitude = loc.optionalDouble("latitude"),
            longitude = loc.optionalDouble("longitude"),
        )
    }

    fun encode(q: QsoRecord): String = JSONObject().apply {
        put("format", 1)
        put("id", q.id)
        put("call", q.call)
        put("stationCallsign", q.stationCallsign)
        put("qsoDate", q.qsoDate)
        put("timeOn", q.timeOn)
        put("band", q.band)
        put("mode", q.mode)
        putOptional("freqMhz", q.freqMhz)
        putOptional("grid", q.grid)
        putOptional("rstSent", q.rstSent)
        putOptional("rstRcvd", q.rstRcvd)
        putOptional("myGridSquare", q.myGridSquare)
        putOptional("myDxcc", q.myDxcc)
        putOptional("myCqZone", q.myCqZone)
        putOptional("myItuZone", q.myItuZone)
        putOptional("myCountry", q.myCountry)
        putOptional("myState", q.myState)
        put("lotwUpload", q.lotwUpload.name)
        put("lotwConfirmation", q.lotwConfirmation.name)
        putOptional("lotwRxQso", q.lotwRxQso)
        putOptional("lotwRxQsl", q.lotwRxQsl)
        putOptional("lotwQslDate", q.lotwQslDate)
        putOptional("lotwRecordId", q.lotwRecordId)
        putOptional("submode", q.submode)
        putOptional("radioMode", q.radioMode)
        putOptional("frequencyHz", q.frequencyHz)
        putOptional("startedUtcMillis", q.startedUtcMillis)
        putOptional("endedUtcMillis", q.endedUtcMillis)
        putOptional("qsoDateOff", q.qsoDateOff)
        putOptional("timeOff", q.timeOff)
        putOptional("stationProfileId", q.stationProfileId)
        putOptional("sessionId", q.sessionId)
        putOptional("remoteLocation", location(q.remoteLocation))
        putOptional("stationLocation", location(q.stationLocation))
        put("origin", q.origin.name)
        putOptional("sourceProvider", q.sourceProvider)
        putOptional("notes", q.notes)
        putOptional("txPowerWatts", q.txPowerWatts)
    }.toString()

    fun decode(text: String): QsoRecord = JSONObject(text).let { j ->
        require(j.getInt("format") == 1) { "Unknown QSO storage format" }
        QsoRecord(
            id = j.getLong("id"),
            call = j.getString("call"),
            stationCallsign = j.getString("stationCallsign"),
            qsoDate = j.getString("qsoDate"),
            timeOn = j.getString("timeOn"),
            band = j.getString("band"),
            mode = j.getString("mode"),
            freqMhz = j.optionalText("freqMhz"),
            grid = j.optionalText("grid"),
            rstSent = j.optionalText("rstSent"),
            rstRcvd = j.optionalText("rstRcvd"),
            myGridSquare = j.optionalText("myGridSquare"),
            myDxcc = j.optionalInt("myDxcc"),
            myCqZone = j.optionalInt("myCqZone"),
            myItuZone = j.optionalInt("myItuZone"),
            myCountry = j.optionalText("myCountry"),
            myState = j.optionalText("myState"),
            lotwUpload = LotwUploadState.valueOf(j.getString("lotwUpload")),
            lotwConfirmation = LotwConfirmationState.valueOf(j.getString("lotwConfirmation")),
            lotwRxQso = j.optionalText("lotwRxQso"),
            lotwRxQsl = j.optionalText("lotwRxQsl"),
            lotwQslDate = j.optionalText("lotwQslDate"),
            lotwRecordId = j.optionalText("lotwRecordId"),
            submode = j.optionalText("submode"),
            radioMode = j.optionalText("radioMode"),
            frequencyHz = j.optionalLong("frequencyHz"),
            startedUtcMillis = j.optionalLong("startedUtcMillis"),
            endedUtcMillis = j.optionalLong("endedUtcMillis"),
            qsoDateOff = j.optionalText("qsoDateOff"),
            timeOff = j.optionalText("timeOff"),
            stationProfileId = j.optionalText("stationProfileId"),
            sessionId = j.optionalText("sessionId"),
            remoteLocation = j.optionalLocation("remoteLocation"),
            stationLocation = j.optionalLocation("stationLocation"),
            origin = QsoOrigin.valueOf(j.getString("origin")),
            sourceProvider = j.optionalText("sourceProvider"),
            notes = j.optionalText("notes"),
            txPowerWatts = j.optionalDouble("txPowerWatts"),
        )
    }
}
