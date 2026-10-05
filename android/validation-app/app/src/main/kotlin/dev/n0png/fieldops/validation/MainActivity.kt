package dev.n0png.fieldops.validation

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import dev.n0png.fieldops.android.logbook.LotwHttpTransport
import dev.n0png.fieldops.android.logbook.TrustedQslSigner
import dev.n0png.fieldops.core.logbook.AdifCodec
import dev.n0png.fieldops.core.logbook.LotwAcceptancePolicy
import dev.n0png.fieldops.core.logbook.LotwCredentials
import dev.n0png.fieldops.core.logbook.LotwStationProfile
import dev.n0png.fieldops.core.logbook.LotwSyncManager
import dev.n0png.fieldops.core.logbook.LotwTransactionOutcome
import dev.n0png.fieldops.core.logbook.LotwUploadContext
import dev.n0png.fieldops.core.logbook.LotwSigningRequest
import dev.n0png.fieldops.core.logbook.LotwSigningSessionState
import dev.n0png.fieldops.core.logbook.QsoRecord
import java.io.File
import java.util.Arrays

/**
 * CP-0003C device-only validation harness.
 *
 * Deliberately not the FieldOps production UI. Nothing uploads automatically.
 * A live LoTW upload requires the operator to select one real QSO and confirm
 * the explicit network action.
 */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var stationLocation: EditText
    private lateinit var stationCallsign: EditText
    private lateinit var dxcc: EditText
    private lateinit var lotwUsername: EditText
    private lateinit var lotwPassword: EditText
    private lateinit var keyPassword: EditText

    private var signer: TrustedQslSigner? = null
    private var selectedRecord: Map<String, String>? = null

    // In-memory only. These are deliberately not persisted; copied evidence is
    // a sanitized gate summary, not a credential or QSO record.
    private var backupImportPassed = false
    private var signingOnlyPassed = false
    private var liveTransactionPassed = false
    private var confirmationSyncPassed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep credentials/private-key passwords out of screenshots, recent-app
        // thumbnails, and screen recordings during this validation gate.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val dataDir = File(filesDir, "trustedqsl-data").apply { mkdirs() }
        val resourceDir = File(filesDir, "trustedqsl-resource").apply { mkdirs() }

        val initResult = runCatching {
            assets.open("trustedqsl/config.xml").use { input ->
                File(resourceDir, "config.xml").outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            signer = TrustedQslSigner(
                dataDirectory = dataDir.absolutePath,
                resourceDirectory = resourceDir.absolutePath,
            )
        }

        setContentView(buildUi())

        if (initResult.isSuccess) {
            setStatus(
                "Native TrustedQSL initialized. Select a TQSL .tbk backup, then select " +
                    "an ADIF file containing exactly one real QSO."
            )
        } else {
            signer = null
            setStatus("FAIL: TrustedQSL initialization failed. No upload is possible.")
        }
    }

    private fun buildUi(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
        }

        content.addView(TextView(this).apply {
            text = "FieldOps CP-0003C — Real LoTW Validation"
            textSize = 22f
        })
        content.addView(TextView(this).apply {
            text =
                "This harness is intentionally manual. It restores certificates/station locations " +
                    "from your TQSL backup, proves real arm64-v8a signing, and can perform one explicit " +
                    "LoTW transaction. It does not enable automatic upload."
            textSize = 15f
            setPadding(0, dp(8), 0, dp(14))
        })

        content.addView(Button(this).apply {
            text = "1. Select TQSL backup (.tbk)"
            setOnClickListener { pickDocument(REQ_TBK, arrayOf("*/*")) }
        })

        content.addView(Button(this).apply {
            text = "2. Select one-QSO ADIF (.adi/.adif)"
            setOnClickListener { pickDocument(REQ_ADIF, arrayOf("text/*", "application/octet-stream")) }
        })

        stationLocation = field("TQSL station location name (exact)")
        stationCallsign = field("Station callsign").apply { setText("N0PNG") }
        dxcc = field("DXCC entity number", numeric = true).apply { setText("230") }
        lotwUsername = field("LoTW web username")
        lotwPassword = field("LoTW web password", password = true)
        keyPassword = field("TQSL private-key password", password = true)

        listOf(
            stationLocation,
            stationCallsign,
            dxcc,
            lotwUsername,
            lotwPassword,
            keyPassword,
        ).forEach(content::addView)

        content.addView(Button(this).apply {
            text = "3. Prove real signing only (NO UPLOAD)"
            setOnClickListener { runSigningOnly() }
        })

        content.addView(Button(this).apply {
            text = "4. RUN LIVE LoTW TRANSACTION"
            setOnClickListener { confirmLiveUpload() }
        })

        content.addView(Button(this).apply {
            text = "Copy sanitized validation result"
            setOnClickListener { copyEvidence() }
        })

        status = TextView(this).apply {
            textSize = 15f
            setPadding(0, dp(18), 0, 0)
        }
        content.addView(status)

        return ScrollView(this).apply { addView(content) }
    }

    private fun field(hintText: String, password: Boolean = false, numeric: Boolean = false): EditText =
        EditText(this).apply {
            hint = hintText
            inputType = when {
                password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                numeric -> InputType.TYPE_CLASS_NUMBER
                else -> InputType.TYPE_CLASS_TEXT
            }
        }

    private fun pickDocument(requestCode: Int, mimeTypes: Array<String>) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (mimeTypes.size == 1) mimeTypes[0] else "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        }
        startActivityForResult(intent, requestCode)
    }

    @Deprecated("Validation harness intentionally avoids AndroidX activity dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return

        when (requestCode) {
            REQ_TBK -> {
                val activeSigner = signer ?: return setStatus("FAIL: native signer is unavailable.")
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return setStatus("FAIL: could not read TQSL backup.")
                try {
                    val result = runCatching { activeSigner.importBackup(bytes) }
                    if (result.isSuccess) {
                        backupImportPassed = true
                        signingOnlyPassed = false
                        liveTransactionPassed = false
                        confirmationSyncPassed = false
                        setStatus(
                            "TQSL backup import PASS. Callsign certificates and station locations " +
                                "were restored into app-private storage. Duplicate-history data was not imported."
                        )
                    } else {
                        backupImportPassed = false
                        signingOnlyPassed = false
                        liveTransactionPassed = false
                        confirmationSyncPassed = false
                        setStatus("FAIL: TQSL backup import failed. No upload was attempted.")
                    }
                } finally {
                    Arrays.fill(bytes, 0)
                }
            }

            REQ_ADIF -> {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: return setStatus("FAIL: could not read ADIF.")
                val result = runCatching {
                    val report = AdifCodec.parse(text)
                    require(report.records.size == 1) {
                        "Validation ADIF must contain exactly one QSO"
                    }
                    val record = report.records.single()
                    require(!record["CALL"].isNullOrBlank())
                    require(!record["QSO_DATE"].isNullOrBlank())
                    require(!record["TIME_ON"].isNullOrBlank())
                    require(!record["BAND"].isNullOrBlank())
                    require(!record["MODE"].isNullOrBlank())
                    record
                }
                if (result.isSuccess) {
                    selectedRecord = result.getOrThrow()
                    val record = selectedRecord!!
                    setStatus(
                        "One-QSO ADIF loaded: ${record["CALL"]} ${record["QSO_DATE"]} " +
                            "${record["TIME_ON"]} ${record["BAND"]} ${record["MODE"]}. No upload performed."
                    )
                } else {
                    selectedRecord = null
                    setStatus("FAIL: ADIF must contain exactly one complete QSO. No upload performed.")
                }
            }
        }
    }

    private fun runSigningOnly() {
        val activeSigner = signer ?: return setStatus("FAIL: native signer is unavailable.")
        if (!backupImportPassed) return setStatus("Import a TQSL backup successfully before signing.")
        val record = selectedRecord ?: return setStatus("Select a one-QSO ADIF first.")
        val station = stationCallsign.text.toString().trim().uppercase()
        val location = stationLocation.text.toString().trim()
        val entity = dxcc.text.toString().toIntOrNull()
            ?: return setStatus("Enter a valid DXCC entity number.")
        val secret = keyPassword.text.toString().toCharArray()
        keyPassword.setText("")

        if (station.isBlank() || location.isBlank()) {
            Arrays.fill(secret, '\u0000')
            return setStatus("Station callsign and station location are required.")
        }

        setStatus("Signing on device… no network upload will occur.")
        Thread {
            val result = runCatching {
                val qso = qsoFromRecord(record, station)
                val request = LotwSigningRequest(
                    adif = AdifCodec.export(listOf(qso)),
                    stationProfileId = "cp0003c-validation",
                    stationLocationName = location,
                    expectedStationCallsign = station,
                    expectedDxcc = entity,
                )
                val session = activeSigner.beginSigning(request, secret)
                try {
                    val bytes = session.tq8Payload
                    require(bytes.size > 20) { "Signed payload too short" }
                    if (session.state == LotwSigningSessionState.OPEN) session.rollback()
                    bytes.size
                } finally {
                    session.close()
                }
            }
            Arrays.fill(secret, '\u0000')
            runOnUiThread {
                if (result.isSuccess) {
                    signingOnlyPassed = true
                    liveTransactionPassed = false
                    confirmationSyncPassed = false
                    setStatus(
                        "DEVICE SIGNING PASS: real TrustedQSL produced a ${result.getOrThrow()}-byte TQ8 " +
                            "payload on this phone. Duplicate state was rolled back. Nothing was uploaded."
                    )
                } else {
                    signingOnlyPassed = false
                    liveTransactionPassed = false
                    confirmationSyncPassed = false
                    setStatus("DEVICE SIGNING FAIL. Duplicate state was not committed and nothing was uploaded.")
                }
            }
        }.start()
    }

    private fun confirmLiveUpload() {
        if (!backupImportPassed) return setStatus("Import a TQSL backup successfully first.")
        if (!signingOnlyPassed) return setStatus("Pass the real signing-only / NO UPLOAD gate first.")
        if (selectedRecord == null) return setStatus("Select a one-QSO ADIF first.")
        AlertDialog.Builder(this)
            .setTitle("Upload one real QSO to LoTW?")
            .setMessage(
                "This is the CP-0003C live gate. The selected QSO will be digitally signed and sent to " +
                    "ARRL LoTW. TrustedQSL duplicate state commits only after the accepted-QSO report verifies it."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("UPLOAD ONE QSO") { _, _ -> runLiveUpload() }
            .show()
    }

    private fun runLiveUpload() {
        val activeSigner = signer ?: return setStatus("FAIL: native signer is unavailable.")
        val record = selectedRecord ?: return setStatus("Select a one-QSO ADIF first.")

        val station = stationCallsign.text.toString().trim().uppercase()
        val location = stationLocation.text.toString().trim()
        val entity = dxcc.text.toString().toIntOrNull()
            ?: return setStatus("Enter a valid DXCC entity number.")
        val username = lotwUsername.text.toString().trim()
        val webPassword = lotwPassword.text.toString()
        val secret = keyPassword.text.toString().toCharArray()

        lotwPassword.setText("")
        keyPassword.setText("")

        if (station.isBlank() || location.isBlank() || username.isBlank() ||
            webPassword.isBlank()) {
            Arrays.fill(secret, '\u0000')
            return setStatus("Station and LoTW web login fields are required.")
        }

        setStatus(
            "LIVE validation running: sign → upload → accepted-report verification → commit. " +
                "This can take a few minutes."
        )

        Thread {
            val result = runCatching {
                val qso = qsoFromRecord(record, station)
                val profile = LotwStationProfile(
                    id = "cp0003c-validation",
                    name = "CP-0003C validation",
                    stationCallsign = station,
                    gridSquare = record["MY_GRIDSQUARE"]?.takeIf { it.length >= 4 } ?: "AA00",
                    dxcc = entity,
                    cqZone = record["MY_CQ_ZONE"]?.toIntOrNull()?.takeIf { it in 1..40 } ?: 1,
                    ituZone = record["MY_ITU_ZONE"]?.toIntOrNull()?.takeIf { it in 1..90 } ?: 1,
                    country = record["MY_COUNTRY"]?.takeIf { it.isNotBlank() } ?: "Validation",
                    state = record["MY_STATE"],
                )
                val credentials = LotwCredentials(username, webPassword)
                val manager = LotwSyncManager(LotwHttpTransport())
                val tx = manager.uploadTransactional(
                    qsos = listOf(qso),
                    signer = activeSigner,
                    context = LotwUploadContext(
                        stationProfile = profile,
                        tqslStationLocationName = location,
                        credentials = credentials,
                    ),
                    privateKeyPassword = secret,
                    acceptancePolicy = LotwAcceptancePolicy(
                        attempts = 5,
                        initialDelayMillis = 30_000L,
                        retryDelayMillis = 30_000L,
                    ),
                )

                if (tx.outcome != LotwTransactionOutcome.ACCEPTED) {
                    ValidationResult(
                        accepted = false,
                        confirmationSyncPassed = false,
                        message = "LIVE validation stopped with ${tx.outcome}. TrustedQSL duplicate state was not committed.",
                    )
                } else {
                    val confirmations = manager.fetchConfirmations(credentials)
                    val target = qso.lotwMatchKey()
                    val confirmed = confirmations.records.any { recordKey(it) == target }
                    ValidationResult(
                        accepted = true,
                        confirmationSyncPassed = true,
                        message =
                            "CP-0003C LIVE TRANSACTION PASS: LoTW accepted and verified the QSO; " +
                                "TrustedQSL duplicate state committed. Confirmation-sync query also PASS " +
                                "(selected QSO currently confirmed=${if (confirmed) "yes" else "no"}).",
                    )
                }
            }

            Arrays.fill(secret, '\u0000')
            runOnUiThread {
                val final = result.getOrElse {
                    ValidationResult(
                        accepted = false,
                        confirmationSyncPassed = false,
                        message = "LIVE validation FAIL. No credentials were saved by this app.",
                    )
                }
                liveTransactionPassed = final.accepted
                confirmationSyncPassed = final.confirmationSyncPassed
                setStatus(final.message)
            }
        }.start()
    }

    private fun qsoFromRecord(record: Map<String, String>, station: String): QsoRecord {
        val recordStation = record["STATION_CALLSIGN"]?.trim()?.uppercase()
        require(recordStation.isNullOrBlank() || recordStation == station) {
            "ADIF station callsign does not match validation station"
        }
        return QsoRecord(
            id = 1L,
            call = requireField(record, "CALL").uppercase(),
            stationCallsign = station,
            qsoDate = requireField(record, "QSO_DATE"),
            timeOn = requireField(record, "TIME_ON"),
            band = requireField(record, "BAND"),
            mode = requireField(record, "MODE"),
            freqMhz = record["FREQ"],
            grid = record["GRIDSQUARE"],
            rstSent = record["RST_SENT"],
            rstRcvd = record["RST_RCVD"],
            myGridSquare = record["MY_GRIDSQUARE"],
            myDxcc = record["MY_DXCC"]?.toIntOrNull(),
            myCqZone = record["MY_CQ_ZONE"]?.toIntOrNull(),
            myItuZone = record["MY_ITU_ZONE"]?.toIntOrNull(),
            myCountry = record["MY_COUNTRY"],
            myState = record["MY_STATE"],
        )
    }

    private fun recordKey(record: Map<String, String>): String {
        val own = (record["STATION_CALLSIGN"] ?: record["APP_LOTW_OWNCALL"] ?: "").uppercase()
        val call = (record["CALL"] ?: "").uppercase()
        val date = record["QSO_DATE"] ?: ""
        val time = (record["TIME_ON"] ?: "").take(4)
        val band = (record["BAND"] ?: "").lowercase()
        return listOf(own, call, date, time, band).joinToString("|")
    }

    private fun requireField(record: Map<String, String>, name: String): String =
        record[name]?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("ADIF is missing $name")

    private fun copyEvidence() {
        val current = if (::status.isInitialized) status.text.toString() else "No status"
        val evidence = buildString {
            appendLine("FieldOps CP-0003C validation")
            appendLine("source_sha=" + BuildConfig.SOURCE_SHA)
            appendLine("device=" + Build.MANUFACTURER + " " + Build.MODEL)
            appendLine("android_sdk=" + Build.VERSION.SDK_INT)
            appendLine("backup_import=" + if (backupImportPassed) "PASS" else "FAIL")
            appendLine("signing_only=" + if (signingOnlyPassed) "PASS" else "FAIL")
            appendLine("live_transaction=" + if (liveTransactionPassed) "PASS" else "FAIL")
            appendLine("confirmation_sync=" + if (confirmationSyncPassed) "PASS" else "FAIL")
            appendLine("automatic_upload=DISABLED")
            appendLine("result=" + current.replace('\n', ' '))
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("CP-0003C validation", evidence))
        Toast.makeText(this, "Sanitized result copied", Toast.LENGTH_SHORT).show()
    }

    private fun setStatus(message: String) {
        if (::status.isInitialized) status.text = message
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private data class ValidationResult(
        val accepted: Boolean,
        val confirmationSyncPassed: Boolean,
        val message: String,
    )

    companion object {
        private const val REQ_TBK = 3001
        private const val REQ_ADIF = 3002
    }
}
