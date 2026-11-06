package dev.n0png.fieldops.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.n0png.fieldops.android.dsp.FtFamilyNativeEngine
import dev.n0png.fieldops.core.awards.AwardsCenterApplicationService
import dev.n0png.fieldops.core.awards.InMemoryAwardEvidenceRepository
import dev.n0png.fieldops.core.digital.DigitalMode
import dev.n0png.fieldops.core.digital.DigitalModes
import dev.n0png.fieldops.core.dsp.DecodeResult
import dev.n0png.fieldops.core.logbook.AdifCodec
import dev.n0png.fieldops.core.logbook.FastQsoLogger
import dev.n0png.fieldops.core.logbook.QsoOrigin
import dev.n0png.fieldops.core.logbook.ManualQsoInput
import dev.n0png.fieldops.core.logbook.ManualQsoMode
import dev.n0png.fieldops.core.logbook.OperatingSession
import dev.n0png.fieldops.core.logbook.QsoLocationSnapshot
import dev.n0png.fieldops.core.logbook.QsoRadioContext
import dev.n0png.fieldops.core.logbook.QsoRecord
import dev.n0png.fieldops.core.logbook.SequentialQsoIdSource
import java.math.BigDecimal

/**
 * The first integrated FieldOps workspace, NOT a radio controller.
 *
 * All operating-data views share the canonical logbook/awards domain services.
 * The log starts volatile; the operator can opt into app-private SQLite
 * storage. Fresh sessions restore only when that choice was explicitly made.
 * Local storage is not encrypted or a substitute for external backup.
 * No USB, CAT, audio, network, PTT or transmitter path is instantiated here.
 */
open class FieldOpsOperatingActivity : Activity() {
    private companion object {
        const val OPEN_OFFLINE_WAV = 7129
        const val EXPORT_MANUAL_ADIF = 7130
        const val IMPORT_FIELDOPS_ADIF = 7131
    }
    private enum class Tab { OPS, DIGI, APRS, MAP, LOG, AWARDS, RADIO }

    private class UiSession(context: android.content.Context) {
        val logbook = OptInQsoRepository(context)
        val ids = SequentialQsoIdSource(logbook.nextRecordId)
        val logger = FastQsoLogger(logbook, ids)
        val awards = AwardsCenterApplicationService(logbook, InMemoryAwardEvidenceRepository())
        val operatingSession = OperatingSession(
            id = "local-in-memory-session",
            stationProfileId = "N0PNG-local",
            stationCallsign = "N0PNG",
            startedUtcMillis = System.currentTimeMillis(),
        )
        var tab = Tab.OPS
        var mode = DigitalMode.FT8
        val ftRxObservations = FtRxObservationLedger(operatingSession.stationCallsign).apply {
            switchMode(mode)
        }
        var manualMode = ManualQsoMode.SSB
        var draftCall = ""
        var draftFrequencyMhz = ""
        var draftSent = ""
        var draftReceived = ""
        var draftRemoteGrid = ""
        var logMessage = if (logbook.isPersistent)
            "Loaded ${logbook.all().size} contact(s) from app-private local storage. No LoTW upload."
        else "No contacts recorded in this in-memory session."
        var showAdif = false
        // User-selected Storage Access Framework export. The exact immutable
        // record snapshot remains in RAM until the file picker returns.
        var pendingAdifExport: List<QsoRecord>? = null
        var pendingAdifBatches: List<ManualAdifFileExport.Batch> = emptyList()
        var nextAdifBatchIndex = 0
        var adifExportBusy = false
        var adifExportStatus = "No file exported. All contacts remain volatile until exported."
        var adifImportBusy = false
        var adifImportStatus = "No ADIF restore requested."
        var stagedAdifRecords: List<dev.n0png.fieldops.core.logbook.QsoRecord>? = null
        var offlineDecodeBusy = false
        var offlineDecodeStatus = "No offline recording decoded."
        var offlineDecodes: List<DecodeResult> = emptyList()
        // The model survives rotation. Its transient UI observer is rebound to
        // the CURRENT Activity only; the background worker never owns a View.
        var onUiChanged: (() -> Unit)? = null
        var offlineDecodeGeneration: Long = 0

        fun selectMode(newMode: DigitalMode) {
            if (mode != newMode) {
                mode = newMode
                // The old job cannot prevent a new mode's WAV from opening.
                // Even if the worker eventually completes, its generation is
                // invalid and must never affect any newer decode's busy flag.
                offlineDecodeGeneration++
                offlineDecodeBusy = false
                ftRxObservations.switchMode(newMode)
                offlineDecodes = emptyList()
                offlineDecodeStatus = "No offline recording decoded for ${newMode.name}."
            }
        }

        fun beginOfflineDecode(): Long {
            check(!offlineDecodeBusy) { "Offline decoder already running" }
            require(mode in setOf(DigitalMode.FT8, DigitalMode.FT4, DigitalMode.FT2)) {
                "Only FT8/FT4/FT2 offline receive is supported"
            }
            offlineDecodeGeneration++
            offlineDecodeBusy = true
            offlineDecodes = emptyList()
            offlineDecodeStatus = "Decoding local WAV — offline; no radio, PTT or QSO log."
            return offlineDecodeGeneration
        }

        fun finishOfflineDecode(
            requestGeneration: Long,
            modeAtStart: DigitalMode,
            decodes: List<DecodeResult>?,
            failure: Throwable?,
        ) {
            if (offlineDecodeGeneration != requestGeneration || mode != modeAtStart) {
                // Stale workers must NOT clear the busy flag of a newer WAV or
                // overwrite its status or reviewed station. No UI callback is
                // needed: the old result has no current-state effect.
                return
            }
            offlineDecodeBusy = false
            if (failure != null || decodes == null) {
                offlineDecodeStatus =
                    if (failure is IllegalArgumentException)
                        "OFFLINE RX REJECTED: ${failure.message?.take(140)}"
                    else
                        "OFFLINE RX FAILED: native decoder or recording unavailable."
                offlineDecodes = emptyList()
            } else {
                offlineDecodeStatus =
                    "${modeAtStart.name}: ${decodes.size} decoded entries from user-selected WAV. " +
                        "Recording UTC is UNKNOWN; timestamps are relative, not verified. " +
                        "NO QSO automatically logged or transmitted."
                offlineDecodes = decodes.filter { it.mode == modeAtStart }.take(40)
                ftRxObservations.acceptFile(modeAtStart, offlineDecodes)
            }
            onUiChanged?.invoke()
        }

        fun finishAdifExport(writtenRecords: Int?, error: Throwable?) {
            adifExportBusy = false
            if (error == null && writtenRecords != null &&
                nextAdifBatchIndex < pendingAdifBatches.size &&
                writtenRecords == pendingAdifBatches[nextAdifBatchIndex].records.size) {
                val totalParts = pendingAdifBatches.size
                val savedPart = ++nextAdifBatchIndex
                val stillNeeded = pendingAdifBatches.drop(nextAdifBatchIndex).sumOf { it.records.size }
                adifExportStatus = if (stillNeeded == 0) {
                    pendingAdifBatches = emptyList()
                    nextAdifBatchIndex = 0
                    "ADIF FILE SAVED: final part $savedPart/$totalParts. All selected contacts " +
                        "exported to user-selected document(s); no LoTW upload."
                } else {
                    "ADIF FILE SAVED: part $savedPart/$totalParts, $writtenRecords records. " +
                        "$stillNeeded contacts NOT YET EXPORTED. Choose NEXT ADIF PART to preserve them. " +
                        "In-memory records remain volatile; no LoTW upload."
                }
            } else {
                adifExportStatus = "ADIF EXPORT FAILED: document could not be saved/verified. " +
                    "This part remains pending; retry choosing a destination. Log unchanged."
            }
            onUiChanged?.invoke()
        }

        fun stageAdifImport(records: List<dev.n0png.fieldops.core.logbook.QsoRecord>?,
                            failure: Throwable?) {
            adifImportBusy = false
            stagedAdifRecords = if (failure == null) records else null
            adifImportStatus = if (failure == null && !records.isNullOrEmpty())
                "REVIEW BEFORE IMPORT: ${records.size} FieldOps ADIF contact(s) for ${operatingSession.stationCallsign}. " +
                    "Only CONFIRM RESTORE adds these to the local log. LoTW remains unverified."
            else
                "ADIF RESTORE REJECTED: ${failure?.message?.take(110) ?: "No valid records"}. " +
                    "Local log unchanged."
            onUiChanged?.invoke()
        }

        fun confirmAdifImport(): Int {
            val staged = requireNotNull(stagedAdifRecords) { "No staged ADIF import" }
            val imported = FieldOpsAdifRestore.commit(staged, logbook, ids)
            stagedAdifRecords = null
            adifImportStatus = "CONFIRMED IMPORT: $imported new QSO(s) added to volatile log; " +
                "${staged.size - imported} duplicate(s) skipped. LoTW not sent."
            return imported
        }

        /**
         * A validated native RX station is only a hint for a future manual QSO.
         * Clear stale RF frequency/report/grid fields every time; NEVER infer
         * actual dial frequency or two-way completion from a WAV recording.
         * The LOG screen still requires explicit user-entered RF metadata and
         * a distinct affirmative completed-QSO button.
         */
        fun prepareUnverifiedFtContact(review: FtDecodedStationReview) {
            require(mode == review.mode) { "Cannot review a station from another FT mode" }
            manualMode = review.manualMode
            draftCall = review.callsign
            draftFrequencyMhz = ""
            draftSent = ""
            draftReceived = ""
            draftRemoteGrid = ""
            logMessage = "UNVERIFIED WAV SENDER ${review.callsign} selected for ${review.mode.name}. " +
                "NOT LOGGED. No trusted RF frequency or complete two-way QSO evidence. " +
                "Enter the actual contact details and separately confirm completion."
            tab = Tab.LOG
        }

        fun prepareObservedStation(callsign: String) {
            val station = ftRxObservations.stations().singleOrNull {
                it.callsign == callsign && it.mode == mode
            } ?: throw IllegalArgumentException("Station is not in the selected FT receive review")
            prepareUnverifiedFtContact(FtDecodedStationReview(station.callsign, station.mode))
        }

        fun saveManual(): Boolean {
            if (logbook.storageError != null) {
                logMessage = "NOT SAVED: existing local QSO database cannot be read. Storage is locked."
                return false
            }
            val call = draftCall.trim().uppercase()
            if (!call.matches(Regex("[A-Z0-9/]{3,16}")) || !call.any { it.isDigit() }) {
                logMessage = "NOT SAVED: enter a nonblank callsign using letters, digits and /."
                return false
            }
            val locator = draftRemoteGrid.trim().uppercase()
            if (locator.isNotEmpty() &&
                !locator.matches(Regex("[A-R]{2}(?:[0-9]{2}(?:[A-X]{2}(?:[0-9]{2})?)?)?"))
            ) {
                logMessage = "NOT SAVED: enter a valid Maidenhead grid (2, 4, 6 or 8 characters)."
                return false
            }
            val hz = try {
                BigDecimal(draftFrequencyMhz.trim())
                    .multiply(BigDecimal("1000000")).longValueExact()
            } catch (_: Exception) {
                logMessage = "NOT SAVED: enter a valid frequency in MHz."
                return false
            }
            // No radio tune or transmit: frequency is user-entered QSO metadata.
            return try {
                val record = logger.logManual(
                    ManualQsoInput(
                        call = call,
                        mode = manualMode,
                        rstSent = draftSent.trim().ifEmpty { null },
                        rstRcvd = draftReceived.trim().ifEmpty { null },
                        remoteLocation = QsoLocationSnapshot(
                            gridSquare = locator.ifEmpty { null },
                        ),
                    ),
                    operatingSession,
                    QsoRadioContext(frequencyHz = hz, radioMode = manualMode.adifMode),
                )
                val destination = if (logbook.isPersistent)
                    "Saved to app-private local storage" else "Saved in memory"
                logMessage = "$destination: QSO #${record.id} ${record.call} ${record.mode}. Not sent to LoTW."
                draftCall = ""
                draftSent = ""
                draftReceived = ""
                draftRemoteGrid = ""
                showAdif = false
                true
            } catch (_: IllegalArgumentException) {
                logMessage = "NOT SAVED: check frequency, band and contact fields."
                false
            } catch (_: RuntimeException) {
                logMessage = "NOT SAVED: local storage operation failed. Do not assume this QSO was recorded."
                false
            }
        }
    }

    private val background = Color.rgb(15, 19, 15)
    private val panelBackground = Color.rgb(32, 38, 29)
    private val borderColor = Color.rgb(85, 92, 68)
    private val primary = Color.rgb(235, 232, 211)
    private val muted = Color.rgb(180, 183, 159)
    private val amber = Color.rgb(230, 180, 101)

    private lateinit var model: UiSession
    private lateinit var root: LinearLayout
    private val refreshActiveUi: () -> Unit = {
        if (::model.isInitialized && !isFinishing && !isDestroyed) render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ArchiveHistoryActivity independently owns its own explicit recovery consent.
        // Retaining this model does not persist any log or credential to disk.
        model = (lastNonConfigurationInstance as? UiSession) ?: UiSession(applicationContext)
        model.onUiChanged = refreshActiveUi
        if (savedInstanceState != null) {
            savedInstanceState.getString("fieldops.tab")?.let {
                model.tab = runCatching { Tab.valueOf(it) }.getOrDefault(Tab.OPS)
            }
        }
        render()
    }

    override fun onRetainNonConfigurationInstance(): Any = model

    override fun onDestroy() {
        if (::model.isInitialized && model.onUiChanged === refreshActiveUi) {
            model.onUiChanged = null
        }
        if (::model.isInitialized && !isChangingConfigurations) model.logbook.close()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("fieldops.tab", model.tab.name)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (model.tab != Tab.OPS) {
            model.tab = Tab.OPS
            render()
        } else {
            super.onBackPressed()
        }
    }

    @Deprecated("Android document result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMPORT_FIELDOPS_ADIF) {
            val session = model
            val uri = data?.data
            if (resultCode != RESULT_OK || uri == null) {
                session.adifImportStatus = "ADIF restore canceled; log unchanged."
                render()
                return
            }
            if (session.adifImportBusy) return
            session.adifImportBusy = true
            session.stagedAdifRecords = null
            session.adifImportStatus = "Validating selected FieldOps ADIF; no QSO added."
            render()
            val resolver = applicationContext.contentResolver
            val main = Handler(Looper.getMainLooper())
            val station = session.operatingSession.stationCallsign
            Thread({
                val result = runCatching {
                    val stream = resolver.openInputStream(uri)
                        ?: throw IllegalArgumentException("Could not read selected ADIF")
                    stream.use { FieldOpsAdifRestore.read(it, station) }
                }
                main.post {
                    session.stageAdifImport(result.getOrNull(), result.exceptionOrNull())
                }
            }, "FieldOps-ADIF-Validate-Only").apply { isDaemon = true }.start()
            return
        }
        if (requestCode == EXPORT_MANUAL_ADIF) {
            val session = model
            val batch = session.pendingAdifBatches.getOrNull(session.nextAdifBatchIndex)
            session.pendingAdifExport = null
            val outputUri = data?.data
            if (resultCode != RESULT_OK || outputUri == null || batch == null) {
                session.adifExportStatus = "ADIF destination canceled; the current part is still " +
                    "pending. Choose NEXT ADIF PART to retry; the in-memory log is unchanged."
                render()
                return
            }
            session.adifExportBusy = true
            session.adifExportStatus = "Writing chosen ADIF document; nothing sent to LoTW."
            render()
            val resolver = applicationContext.contentResolver
            val main = Handler(Looper.getMainLooper())
            Thread({
                val result = runCatching {
                    val stream = resolver.openOutputStream(outputUri, "w")
                        ?: throw IllegalStateException("Document provider refused output")
                    val written = stream.use { ManualAdifFileExport.write(batch, it) }
                    // Do not call this part saved based only on close/flush:
                    // the provider must return exactly the prepared bytes.
                    val readback = resolver.openInputStream(outputUri)
                        ?: throw IllegalStateException("Cannot reopen saved ADIF document")
                    readback.use { ManualAdifFileExport.verifySaved(batch, it) }
                    written
                }
                main.post {
                    session.finishAdifExport(result.getOrNull(), result.exceptionOrNull())
                }
            }, "FieldOps-ADIF-Document-Export").apply { isDaemon = true }.start()
            return
        }
        if (requestCode != OPEN_OFFLINE_WAV || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val session = model
        if (session.offlineDecodeBusy ||
            session.mode !in setOf(DigitalMode.FT8, DigitalMode.FT4, DigitalMode.FT2)) return
        val selected = session.mode
        val generation = session.beginOfflineDecode()
        render()
        // Never capture this Activity in the worker. Completion is posted to
        // the retained session and repaints whichever Activity currently owns
        // its transient observer (or no Activity after a final destroy).
        val resolver = applicationContext.contentResolver
        val main = Handler(Looper.getMainLooper())
        Thread({
            val result = runCatching {
                check(FtFamilyNativeAvailability.probe()) { "Native decoder unavailable" }
                val input = resolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Could not open selected WAV")
                input.use { FtFamilyOfflineWav.decode(it, selected, FtFamilyNativeEngine()) }
            }
            main.post {
                session.finishOfflineDecode(
                    generation, selected, result.getOrNull(), result.exceptionOrNull())
            }
        }, "FieldOps-Offline-FT-Family-RX").apply { isDaemon = true }.start()
    }

    private fun render() {
        val scroll = ScrollView(this).apply {
            setFillViewport(true)
            setBackgroundColor(this@FieldOpsOperatingActivity.background)
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(24))
        }
        scroll.addView(root)
        title("N0PNG / FIELDOPS", 24, amber, true)
        title("YAESU FTX-1 • FIELD TERMINAL / SOFTWARE INTEGRATION", 12, muted, true)
        root.addView(space(8))
        panel("RADIO DISCONNECTED • TX LOCKED",
            "This app does not connect to a radio, use the microphone, key PTT, or transmit. " +
                "Hardware and account operations are disabled. No LoTW uploads.")
        nav()
        when (model.tab) {
            Tab.OPS -> ops()
            Tab.DIGI -> digi()
            Tab.APRS -> aprs()
            Tab.MAP -> map()
            Tab.LOG -> log()
            Tab.AWARDS -> awards()
            Tab.RADIO -> radio()
        }
        title(if (model.logbook.isPersistent)
            "APP-PRIVATE LOCAL QSO STORE • NO CLOUD BACKUP • NO RF TRANSMISSION" else
            "IN-MEMORY ONLY • FRESH PROCESS LOSES MANUAL QSOs • NO RF TRANSMISSION", 12, muted)
        setContentView(scroll)
    }

    private fun nav() {
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (tab in Tab.values()) {
            nav.addView(Button(this).apply {
                text = tab.name
                isAllCaps = false
                contentDescription = "Open ${tab.name} workspace"
                isEnabled = true
                setOnClickListener {
                    model.tab = tab
                    render()
                }
            })
        }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = true
            addView(nav)
        })
    }

    private fun ops() {
        panel("OPERATING STATUS",
            "Station: N0PNG • Yaesu FTX-1/FTX-1F • hardware disconnected. " +
                "No CAT, USB audio or transmit session exists.")
        val profile = DigitalModes.profile(model.mode)
        panel("SELECTED DIGITAL PROFILE",
            "${profile.mode.name}: ${profile.cycleMillis} ms cycle • ${profile.workflow}. " +
                "Metadata from the tested shared core; decoder and transmitter not connected.")
        panel("LOCAL CONTACTS",
            "${model.logbook.all().size} manual QSOs in volatile memory. " +
                "The LOG and AWARDS workspaces read the same authoritative repository.")
        action("OPEN SYNTHETIC OFFLINE ARCHIVE HISTORY") {
            startActivity(Intent(this, ArchiveHistoryActivity::class.java))
        }
    }

    private fun digi() {
        panel("DIGITAL MODE SELECTION / NO RF",
            "Profiles below use the shared DigitalModes engine metadata. " +
                "Selection does not initialize a DSP provider, CAT or PTT.")
        for (mode in listOf(DigitalMode.FT8, DigitalMode.FT4, DigitalMode.FT2,
            DigitalMode.JS8, DigitalMode.WSPR)) {
            action("SELECT ${mode.name}") {
                model.selectMode(mode)
                render()
            }
        }
        val profile = DigitalModes.profile(model.mode)
        panel("SELECTED ${model.mode.name}",
            "Cycle: ${profile.cycleMillis} ms • workflow: ${profile.workflow}. " +
                "FT-family file-only RX is available below; live radio receive, USB audio and TX remain disabled.")
        if (model.mode in setOf(DigitalMode.FT8, DigitalMode.FT4, DigitalMode.FT2)) {
            val nativePresent = FtFamilyNativeAvailability.probe()
            panel("FT-FAMILY 48 kHz / 12 kHz RX ENGINE",
                if (nativePresent)
                    "Native FT8AF library loadable. Shared 48 kHz to UTC slot decoder path is compiled; " +
                        "input capture is NOT connected, and device/RF validation is still absent."
                else
                    "Production Java/JNI adapter compiled, but libft8af.so is not packaged. " +
                        "Native FT8/FT4/FT2 decoding is UNAVAILABLE; no synthetic fallback.")
        }
        if (model.mode in setOf(DigitalMode.FT8, DigitalMode.FT4, DigitalMode.FT2)) {
            panel("RECORDED-WAV RECEIVE / FILE ONLY",
                "Accepts one mono 16-bit PCM WAV at 12 kHz or 48 kHz. " +
                    "48 kHz passes through the same 4:1 FIR as shared capture; no live device connected. " +
                    "Recording time and source are unverified. Decoded text is NOT logged as a QSO.")
            if (!model.offlineDecodeBusy) {
                action("OPEN OFFLINE WAV FILE (NO RF)") {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, OPEN_OFFLINE_WAV)
                }
            }
            panel("OFFLINE DECODE RESULT", model.offlineDecodeStatus)
            model.offlineDecodes.forEach { decoded ->
                panel("DECODED WAV / UNVERIFIED",
                    "${decoded.audioHz ?: "?"} Hz audio offset | " +
                        "${decoded.snrDb ?: "?"} dB | " + decoded.text.take(200))
                val review = FtDecodedStationReview.from(
                    decoded, model.operatingSession.stationCallsign)
                if (review != null) {
                    action("REVIEW ${review.callsign} IN MANUAL LOG (NO AUTO QSO)") {
                        model.prepareUnverifiedFtContact(review)
                        render()
                    }
                }
            }
            val stations = model.ftRxObservations.stations()
            if (stations.isNotEmpty()) {
                panel("STRUCTURED RX STATION REVIEW / NOT A QSO",
                    "Native sender/recipient identities only; no callsigns inferred from free text. " +
                        "WAV file selection order is NOT verified UTC or an RF exchange. " +
                        "No transmission, QSO, confirmation or LoTW action is implied.")
                for (station in stations.take(12)) {
                    panel("HEARD ${station.callsign} / ${station.mode.name}",
                        "${station.messages} messages • ${station.cqMessages} CQ • " +
                            "${station.addressedToOwnCall} addressed to ${model.operatingSession.stationCallsign} " +
                            "• last selected WAV #${station.lastImportOrdinal}.")
                    action("INSPECT ${station.callsign} RX HISTORY (NO QSO)") {
                        model.ftRxObservations.focus(station.callsign)
                        render()
                    }
                    val exchange = FtRxExchangeEvidenceGate.assess(
                        station, model.ftRxObservations.observationsFor(station.callsign))
                    panel("RX EXCHANGE EVIDENCE ${station.callsign} / NO TX",
                        "${exchange.stage}: ${exchange.explanation} " +
                            "${exchange.selectedWavFiles} selected WAV file(s), nominal " +
                            "${exchange.nominalSlotMillis} ms slot. " +
                            "Trusted UTC: NO • Verified local TX: NO • Auto-log: NO.")
                    action("PREPARE ${station.callsign} MANUAL ${station.mode.name} QSO (NO AUTO LOG)") {
                        // Only a station actually present in the selected
                        // receive-only board can be handed to the manual form.
                        model.prepareObservedStation(station.callsign)
                        render()
                    }
                }
                val focused = model.ftRxObservations.focusedCallsign
                if (focused != null) {
                    panel("UNVERIFIED RX MESSAGES FOR $focused",
                        "These are separately selected file observations, NOT a confirmed two-way contact.")
                    model.ftRxObservations.focusedObservations().forEach { heard ->
                        panel("WAV #${heard.importOrdinal} / ${heard.addressKind}",
                            "Recipient: ${heard.recipient ?: "unknown"} • " +
                                "${heard.snrDb ?: "?"} dB estimated • ${heard.text.take(180)}")
                    }
                }
            }
            panel("COMPLETED FT QSO / OPERATOR REVIEW",
                "Only log a TWO-WAY contact that you independently completed. " +
                    "An offline decode, a CQ or a heard signal alone is NOT a QSO. " +
                    "Preparing the manual form does not key the radio, record a QSO or queue LoTW.")
            action("PREPARE MANUAL ${model.mode.name} QSO (NO AUTO LOG)") {
                model.manualMode = when (model.mode) {
                    DigitalMode.FT8 -> ManualQsoMode.FT8
                    DigitalMode.FT4 -> ManualQsoMode.FT4
                    DigitalMode.FT2 -> ManualQsoMode.FT2
                    else -> error("FT-family mode required")
                }
                model.tab = Tab.LOG
                render()
            }
        }
        disabledAction("START RX — NOT CONNECTED")
        disabledAction("ARM TX — HARDWARE VALIDATION REQUIRED")
    }

    private fun aprs() {
        panel("RADIO APRS (FTX-1 INTERNAL)",
            "Separate internal-radio modem configuration. CAT and decoded packet stream not validated.")
        panel("PHONE APRS (AFSK / AX.25)",
            "Separate phone-generated packet path. Requires verified audio routing and safe CAT PTT.")
        disabledAction("ENABLE RADIO APRS — NOT VALIDATED")
        disabledAction("TRANSMIT PHONE PACKET — LOCKED")
    }

    private fun map() {
        // Actual map-layer service: cannot invent station coordinates or award confirmations.
        val layers = model.awards.awardMapLayers()
        panel("AWARD-AREA GEOMETRY / NO INVENTED POSITIONS",
            "Local award-map projection: ${layers.size} layers from current logbook/evidence. " +
                "No authenticated APRS, heard-signal or QSO coordinates available.")
        panel("PROPAGATION & OFFLINE REPORTS",
            "The separate synthetic archive workbench remains available; no live source is implied.")
        action("OPEN SYNTHETIC OFFLINE ARCHIVE HISTORY") {
            startActivity(Intent(this, ArchiveHistoryActivity::class.java))
        }
    }

    /** Each complete ADIF part requires a fresh, operator-approved SAF file. */
    private fun openNextAdifDestination() {
        val batch = model.pendingAdifBatches.getOrNull(model.nextAdifBatchIndex)
            ?: return
        model.pendingAdifExport = batch.records
        val total = model.pendingAdifBatches.size
        val part = model.nextAdifBatchIndex + 1
        val suggestedName = if (total == 1) "N0PNG-FieldOps-log.adi" else
            "N0PNG-FieldOps-log-part-${part.toString().padStart(3, '0')}-of-${total.toString().padStart(3, '0')}.adi"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, suggestedName)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, EXPORT_MANUAL_ADIF)
    }

    private fun log() {
        val storage = model.logbook
        panel("LOCAL QSO STORAGE / EXPLICIT OPT-IN", when {
            storage.storageError != null -> storage.storageError!!
            storage.isPersistent -> "ENABLED: contacts are written to an app-private SQLite database " +
                "before being displayed in the log. Not encrypted, not cloud-synced, " +
                "not backed up by Android. Uninstall/clear-data may remove it; export ADIF separately."
            else -> "OFF: contacts are volatile and disappear after a fresh process. " +
                "Choose ENABLE to keep full local QSO metadata on this device. " +
                "This app-private storage is not encrypted or backed up automatically."
        })
        if (!storage.isPersistent && storage.storageError == null) {
            action("ENABLE LOCAL QSO STORAGE (THIS DEVICE ONLY)") {
                val result = runCatching { storage.enableLocalPersistence() }
                model.logMessage = if (result.isSuccess)
                    "LOCAL STORAGE ENABLED: ${storage.all().size} contact(s) preserved on device. " +
                        "Use ADIF export for independent backups. No LoTW."
                else "LOCAL STORAGE NOT ENABLED: database setup failed. " +
                    "Existing session contacts remain in memory; export ADIF before closing."
                render()
            }
        }
        panel("MANUAL CW / SSB CONTACT",
            "Use this form to record an actual manually entered QSO in volatile memory. " +
                "Do not use it as proof of radio reception, transmission, saved disk records or LoTW sync.")
        if (model.manualMode in setOf(ManualQsoMode.FT8, ManualQsoMode.FT4, ManualQsoMode.FT2)) {
            panel("MANUAL FT CONTACT / OPERATOR ATTESTATION",
                "Press SAVE only after a completed two-way ${model.manualMode.adifMode} contact. " +
                    "No waveform or decoder automatically creates a QSO; no TX or LoTW is performed.")
        }
        val call = EditText(this).apply {
            hint = "Remote callsign"
            contentDescription = "Remote callsign"
            isSingleLine = true
            setText(model.draftCall)
            addTextChangedListener(draftWatcher { model.draftCall = it })
        }
        val freq = EditText(this).apply {
            hint = "Frequency in MHz (e.g. 7.075)"
            contentDescription = "Frequency in MHz"
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(model.draftFrequencyMhz)
            addTextChangedListener(draftWatcher { model.draftFrequencyMhz = it })
        }
        val sent = EditText(this).apply {
            hint = "RST sent (optional)"
            contentDescription = "RST sent"
            isSingleLine = true
            setText(model.draftSent)
            addTextChangedListener(draftWatcher { model.draftSent = it })
        }
        val received = EditText(this).apply {
            hint = "RST received (optional)"
            contentDescription = "RST received"
            isSingleLine = true
            setText(model.draftReceived)
            addTextChangedListener(draftWatcher { model.draftReceived = it })
        }
        val grid = EditText(this).apply {
            hint = "Remote grid square (optional)"
            contentDescription = "Remote grid square"
            isSingleLine = true
            setText(model.draftRemoteGrid)
            addTextChangedListener(draftWatcher { model.draftRemoteGrid = it })
        }
        root.addView(call)
        root.addView(freq)
        root.addView(sent)
        root.addView(received)
        root.addView(grid)
        action("MANUAL MODE: ${model.manualMode.adifMode} (SWITCH)") {
            model.draftCall = call.text.toString()
            model.draftFrequencyMhz = freq.text.toString()
            model.manualMode = if (model.manualMode == ManualQsoMode.SSB)
                ManualQsoMode.CW else ManualQsoMode.SSB
            render()
        }
        action(
            if (model.manualMode in setOf(ManualQsoMode.FT8, ManualQsoMode.FT4, ManualQsoMode.FT2))
                "CONFIRM COMPLETED FT QSO — SAVE MANUALLY" else "SAVE MANUAL QSO"
        ) {
            model.draftCall = call.text.toString()
            model.draftFrequencyMhz = freq.text.toString()
            model.saveManual()
            render()
        }
        panel("SAVE STATUS", model.logMessage)
        val all = model.logbook.all()
        panel(if (storage.isPersistent) "LOCAL DEVICE LOGBOOK" else "LIVE IN-MEMORY LOGBOOK",
            "${all.size} records • " +
            if (storage.isPersistent) "app-private SQLite; no cloud/LoTW" else "volatile; no uploads")
        for (qso in all.takeLast(20).asReversed()) {
            title("#${qso.id}  ${qso.call}  ${qso.mode}  ${qso.band}  ${qso.freqMhz} MHz  " +
                "${qso.qsoDate} ${qso.timeOn} UTC", 13, primary)
        }
        if (all.size > 20) title("Only 20 newest records displayed.", 12, muted)
        action(if (model.showAdif) "HIDE ADIF" else "SHOW ADIF FROM REAL LOGBOOK") {
            model.showAdif = !model.showAdif
            render()
        }
        if (all.isNotEmpty() && !model.adifExportBusy && model.pendingAdifExport == null) {
            if (model.pendingAdifBatches.isNotEmpty()) {
                val remaining = model.pendingAdifBatches.drop(model.nextAdifBatchIndex)
                    .sumOf { it.records.size }
                panel("ADIF PARTS STILL TO EXPORT",
                    "$remaining contact(s) in remaining restore-compatible documents. " +
                        "Continue selecting destinations; the plan is an immutable snapshot.")
                action("EXPORT NEXT ADIF PART (CHOOSE DESTINATION)") {
                    openNextAdifDestination()
                }
                action("DISCARD PENDING ADIF PARTS (LOG UNCHANGED)") {
                    model.pendingAdifBatches = emptyList()
                    model.nextAdifBatchIndex = 0
                    model.adifExportStatus = "Pending export parts discarded. " +
                        "In-memory contacts are NOT preserved on disk."
                    render()
                }
            } else {
                action("EXPORT ADIF FILE (CHOOSE DESTINATION)") {
                    // Validate every planned part against our strict importer
                    // before opening ANY destination document.
                    val prepared = runCatching {
                        ManualAdifFileExport.plan(model.logbook.all(),
                            model.operatingSession.stationCallsign)
                    }
                    val batches = prepared.getOrNull()
                    if (batches == null) {
                        model.adifExportStatus = "ADIF EXPORT BLOCKED: " +
                            "${prepared.exceptionOrNull()?.message?.take(160)}. " +
                            "No potentially non-restorable document written."
                        render()
                    } else {
                        model.pendingAdifBatches = batches
                        model.nextAdifBatchIndex = 0
                        model.adifExportStatus = "Export prepared: ${batches.size} separate " +
                            "restore-compatible file(s); each needs a selected destination."
                        openNextAdifDestination()
                    }
                }
            }
        } else if (all.isEmpty()) {
            disabledAction("EXPORT ADIF FILE — NO QSOS")
        }
        panel("ADIF FILE EXPORT / EXPLICIT USER ACTION", model.adifExportStatus)
        if (!model.adifImportBusy) {
            action("RESTORE FIELDOPS ADIF (PICK FILE)") {
                model.stagedAdifRecords = null
                model.adifImportStatus = "Choose a FieldOps-generated ADIF file for validation."
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                @Suppress("DEPRECATION")
                startActivityForResult(intent, IMPORT_FIELDOPS_ADIF)
            }
        }
        panel("ADIF RESTORE / NOT LOTW", model.adifImportStatus)
        if (!model.stagedAdifRecords.isNullOrEmpty() && !model.adifImportBusy) {
            action("CONFIRM RESTORE TO LOCAL LOG (NO LOTW)") {
                val result = runCatching { model.confirmAdifImport() }
                if (result.isFailure) model.adifImportStatus =
                    "RESTORE NOT COMPLETED: local log storage refused the batch. " +
                    "Review current QSO count before retrying; no LoTW action."
                render()
            }
            action("CANCEL STAGED ADIF RESTORE") {
                model.stagedAdifRecords = null
                model.adifImportStatus = "Staged restore discarded; log unchanged."
                render()
            }
        }
        if (model.showAdif) {
            val adif = AdifCodec.export(all.take(FieldOpsArchiveBounds.MAX_RECORDS))
            title("ADIF PREVIEW / FIRST ${minOf(all.size, FieldOpsArchiveBounds.MAX_RECORDS)} " +
                "CONTACTS ONLY / NOT SENT TO LOTW", 12, amber, true)
            title(adif, 12, primary)
        }
    }

    private fun awards() {
        panel("AWARDS CENTER / LOCAL EVIDENCE ONLY",
            "Cards below are computed from the same in-memory logbook as LOG, " +
                "with no imported confirmations or sponsor award claims.")
        val cards = model.awards.cards()
        for (card in cards.take(8)) {
            val progress = card.progress
            val status = if (progress.completedUnits == null || progress.requiredUnits == null)
                "external scoring unavailable" else
                "${progress.completedUnits}/${progress.requiredUnits} local units"
            panel(card.displayName, "$status • Worked: ${card.workedCount ?: 0} • " +
                "Confirmed: ${card.confirmedCount ?: 0} • Not an official award")
        }
        if (cards.size > 8) title("Showing first 8 of ${cards.size} award cards.", 12, muted)
    }

    private fun radio() {
        panel("FTX-1 TRANSPORT / DISCONNECTED",
            "No Android USB port is opened; CAT and USB audio have not passed CP-0004A/B/C.")
        disabledAction("CONNECT CAT — PHYSICAL VALIDATION REQUIRED")
        disabledAction("PTT / TRANSMIT — LOCKED")
        panel("ACCOUNT & CRYPTO SAFETY",
            "CP-0003C LoTW account validation is DEFERRED. " +
                "Encrypted archive freshness is not verified; keys are not retired automatically.")
    }

    /** Keep unsaved user input through navigation and configuration changes. */
    private fun draftWatcher(change: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            change(s?.toString().orEmpty())
        }
        override fun afterTextChanged(s: Editable?) {}
    }

    private fun disabledAction(label: String) {
        root.addView(Button(this).apply {
            text = label
            isAllCaps = false
            isEnabled = false
            contentDescription = label + "; disabled"
        })
    }

    private fun action(label: String, handler: () -> Unit) {
        root.addView(Button(this).apply {
            text = label
            isAllCaps = false
            contentDescription = label
            setOnClickListener { handler() }
        })
    }

    private fun panel(heading: String, detail: String) {
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = GradientDrawable().apply {
                setColor(panelBackground)
                cornerRadius = dp(7).toFloat()
                setStroke(dp(1), borderColor)
            }
        }
        holder.addView(text(heading, 15, amber, true))
        holder.addView(space(5))
        holder.addView(text(detail, 14, primary))
        root.addView(holder, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(10)
        })
    }

    private fun title(value: String, sp: Int, color: Int, bold: Boolean = false) {
        root.addView(text(value, sp, color, bold))
    }

    private fun text(value: String, sp: Int, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            this.text = value
            textSize = sp.toFloat()
            setTextColor(color)
            gravity = Gravity.START
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

    private fun space(size: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(size))
    }

    private fun dp(value: Int) =
        (resources.displayMetrics.density * value + 0.5f).toInt()
}
