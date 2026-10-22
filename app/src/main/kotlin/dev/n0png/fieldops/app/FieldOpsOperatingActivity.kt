package dev.n0png.fieldops.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
import dev.n0png.fieldops.core.awards.AwardsCenterApplicationService
import dev.n0png.fieldops.core.awards.InMemoryAwardEvidenceRepository
import dev.n0png.fieldops.core.digital.DigitalMode
import dev.n0png.fieldops.core.digital.DigitalModes
import dev.n0png.fieldops.core.logbook.AdifCodec
import dev.n0png.fieldops.core.logbook.FastQsoLogger
import dev.n0png.fieldops.core.logbook.InMemoryLogbookRepository
import dev.n0png.fieldops.core.logbook.ManualQsoInput
import dev.n0png.fieldops.core.logbook.ManualQsoMode
import dev.n0png.fieldops.core.logbook.OperatingSession
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
        var logMessage = "No contacts recorded in this in-memory session."
        var showAdif = false

        fun saveManual(): Boolean {
            val call = draftCall.trim().uppercase()
            if (!call.matches(Regex("[A-Z0-9/]{3,16}")) || !call.any { it.isDigit() }) {
                logMessage = "NOT SAVED: enter a nonblank callsign using letters, digits and /."
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
                    ManualQsoInput(call = call, mode = manualMode),
                    operatingSession,
                    QsoRadioContext(frequencyHz = hz, radioMode = manualMode.adifMode),
                )
                logMessage = "Saved in memory: QSO #${record.id} ${record.call} ${record.mode}. Not sent to LoTW."
                draftCall = ""
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ArchiveHistoryActivity independently owns its own explicit recovery consent.
        // Retaining this model does not persist any log or credential to disk.
        model = (lastNonConfigurationInstance as? UiSession) ?: UiSession()
        if (savedInstanceState != null) {
            savedInstanceState.getString("fieldops.tab")?.let {
                model.tab = runCatching { Tab.valueOf(it) }.getOrDefault(Tab.OPS)
            }
        }
        render()
    }

    override fun onRetainNonConfigurationInstance(): Any = model

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

    private fun render() {
        val scroll = ScrollView(this).apply {
            setFillViewport(true)
            setBackgroundColor(background)
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
                model.mode = mode
                render()
            }
        }
        val profile = DigitalModes.profile(model.mode)
        panel("SELECTED ${model.mode.name}",
            "Cycle: ${profile.cycleMillis} ms • workflow: ${profile.workflow}. " +
                "RX decoding, audio capture and TX remain unavailable in this APK.")
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
        root.addView(call)
        root.addView(freq)
        action("MANUAL MODE: ${model.manualMode.adifMode} (SWITCH)") {
            model.draftCall = call.text.toString()
            model.draftFrequencyMhz = freq.text.toString()
            model.manualMode = if (model.manualMode == ManualQsoMode.SSB)
                ManualQsoMode.CW else ManualQsoMode.SSB
            render()
        }
        action("SAVE MANUAL QSO") {
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
