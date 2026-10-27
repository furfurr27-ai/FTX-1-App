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
import dev.n0png.fieldops.core.logbook.InMemoryLogbookRepository
import dev.n0png.fieldops.core.logbook.ManualQsoInput
import dev.n0png.fieldops.core.logbook.ManualQsoMode
import dev.n0png.fieldops.core.logbook.OperatingSession
import dev.n0png.fieldops.core.logbook.QsoLocationSnapshot
import dev.n0png.fieldops.core.logbook.QsoRadioContext
import dev.n0png.fieldops.core.logbook.SequentialQsoIdSource
import java.math.BigDecimal

/**
 * The first integrated FieldOps workspace, NOT a radio controller.
 *
 * All operating-data views share the canonical logbook/awards domain services.
 * A fresh process starts empty. We retain only in-process memory across
 * configuration changes, never claim disk persistence or LoTW delivery.
 * No USB, CAT, audio, network, PTT or transmitter path is instantiated here.
 */
open class FieldOpsOperatingActivity : Activity() {
    private companion object { const val OPEN_OFFLINE_WAV = 7129 }
    private enum class Tab { OPS, DIGI, APRS, MAP, LOG, AWARDS, RADIO }

    private class UiSession {
        val logbook = InMemoryLogbookRepository()
        val logger = FastQsoLogger(logbook, SequentialQsoIdSource())
        val awards = AwardsCenterApplicationService(logbook, InMemoryAwardEvidenceRepository())
        val operatingSession = OperatingSession(
            id = "local-in-memory-session",
            stationProfileId = "N0PNG-local",
            stationCallsign = "N0PNG",
            startedUtcMillis = System.currentTimeMillis(),
        )
        var tab = Tab.OPS
        var mode = DigitalMode.FT8
        var manualMode = ManualQsoMode.SSB
        var draftCall = ""
        var draftFrequencyMhz = ""
        var draftSent = ""
        var draftReceived = ""
        var draftRemoteGrid = ""
        var logMessage = "No contacts recorded in this in-memory session."
        var showAdif = false
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
                // Never present an older mode's recording in a new-mode view.
                offlineDecodeGeneration++
                offlineDecodes = emptyList()
                offlineDecodeStatus = "No offline recording decoded for ${newMode.name}."
            }
        }

        fun finishOfflineDecode(
            requestGeneration: Long,
            modeAtStart: DigitalMode,
            result: Result<List<DecodeResult>>,
        ) {
            offlineDecodeBusy = false
            if (offlineDecodeGeneration != requestGeneration || mode != modeAtStart) {
                // An asynchronous old-mode decode is not an observation of the
                // currently selected mode and must never become a QSO hint.
                onUiChanged?.invoke()
                return
            }
            result.fold(
                onSuccess = { decodes ->
                    offlineDecodeStatus =
                        "${modeAtStart.name}: ${decodes.size} decoded entries from user-selected WAV. " +
                            "Recording UTC is UNKNOWN; timestamps are relative, not verified. " +
                            "NO QSO automatically logged or transmitted."
                    offlineDecodes = decodes.take(40)
                },
                onFailure = { error ->
                    offlineDecodeStatus =
                        if (error is IllegalArgumentException)
                            "OFFLINE RX REJECTED: ${error.message?.take(140)}"
                        else
                            "OFFLINE RX FAILED: native decoder or recording unavailable."
                    offlineDecodes = emptyList()
                },
            )
            onUiChanged?.invoke()
        }

        fun saveManual(): Boolean {
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
                logMessage = "Saved in memory: QSO #${record.id} ${record.call} ${record.mode}. Not sent to LoTW."
                draftCall = ""
                draftSent = ""
                draftReceived = ""
                draftRemoteGrid = ""
                showAdif = false
                true
            } catch (_: IllegalArgumentException) {
                logMessage = "NOT SAVED: check frequency, band and contact fields."
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
        model = (lastNonConfigurationInstance as? UiSession) ?: UiSession()
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
        if (requestCode != OPEN_OFFLINE_WAV || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val session = model
        if (session.offlineDecodeBusy ||
            session.mode !in setOf(DigitalMode.FT8, DigitalMode.FT4, DigitalMode.FT2)) return
        val selected = session.mode
        val generation = session.offlineDecodeGeneration
        session.offlineDecodeBusy = true
        session.offlineDecodeStatus = "Decoding local WAV — offline; no radio, PTT or QSO log."
        session.offlineDecodes = emptyList()
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
            main.post { session.finishOfflineDecode(generation, selected, result) }
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
        title("IN-MEMORY ONLY • FRESH PROCESS LOSES MANUAL QSOs • NO RF TRANSMISSION", 12, muted)
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
                "RX decoding, audio capture and TX remain unavailable in this APK.")
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
                "Requires a single 12 kHz, mono, 16-bit PCM WAV slot. " +
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
                        // A WAV's audio offset is NOT a CAT frequency; clear old
                        // contact fields so no stale QSO can be accidentally saved.
                        model.manualMode = review.manualMode
                        model.draftCall = review.callsign
                        model.draftFrequencyMhz = ""
                        model.draftSent = ""
                        model.draftReceived = ""
                        model.draftRemoteGrid = ""
                        model.logMessage =
                            "UNVERIFIED WAV SENDER ${review.callsign} selected. " +
                            "NOT LOGGED. Enter real RF frequency and confirm a completed two-way contact before saving."
                        model.tab = Tab.LOG
                        render()
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

    private fun log() {
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
        panel("LIVE IN-MEMORY LOGBOOK", "${all.size} records • no persistence or uploads")
        for (qso in all.takeLast(20).asReversed()) {
            title("#${qso.id}  ${qso.call}  ${qso.mode}  ${qso.band}  ${qso.freqMhz} MHz  " +
                "${qso.qsoDate} ${qso.timeOn} UTC", 13, primary)
        }
        if (all.size > 20) title("Only 20 newest records displayed.", 12, muted)
        action(if (model.showAdif) "HIDE ADIF" else "SHOW ADIF FROM REAL LOGBOOK") {
            model.showAdif = !model.showAdif
            render()
        }
        if (model.showAdif) {
            val adif = AdifCodec.export(all)
            title("ADIF PREVIEW / NOT SENT TO LOTW", 12, amber, true)
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
