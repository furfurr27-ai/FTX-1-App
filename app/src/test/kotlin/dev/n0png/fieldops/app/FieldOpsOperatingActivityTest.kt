package dev.n0png.fieldops.app

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import dev.n0png.fieldops.core.digital.DigitalMode
import dev.n0png.fieldops.core.dsp.DecodeResult
import dev.n0png.fieldops.core.logbook.QsoRecord
import dev.n0png.fieldops.core.logbook.QsoOrigin
import dev.n0png.fieldops.core.logbook.LotwUploadState
import dev.n0png.fieldops.core.logbook.LotwConfirmationState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** Android integration test: real shared core services; never real RF/LoTW. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FieldOpsOperatingActivityTest {
    private fun visit(v: View, f: (View) -> Unit) {
        f(v)
        if (v is ViewGroup) repeat(v.childCount) { visit(v.getChildAt(it), f) }
    }

    private fun text(a: MainActivity): String = buildString {
        visit(a.window.decorView) { if (it is TextView) append(it.text).append('\n') }
    }

    private fun button(a: MainActivity, label: String): Button {
        var result: Button? = null
        visit(a.window.decorView) {
            if (it is Button && it.text.toString() == label) result = it
        }
        return requireNotNull(result) { "Missing button: $label" }
    }

    private fun tap(a: MainActivity, label: String) {
        assertTrue("Failed tap $label", button(a, label).performClick())
    }

    private fun field(a: MainActivity, hint: String): EditText {
        var result: EditText? = null
        visit(a.window.decorView) {
            if (it is EditText && it.hint.toString() == hint) result = it
        }
        return requireNotNull(result) { "Missing field: $hint" }
    }

    private fun addQso(a: MainActivity, call: String, frequency: String) {
        tap(a, "LOG")
        field(a, "Remote callsign").setText(call)
        field(a, "Frequency in MHz (e.g. 7.075)").setText(frequency)
        tap(a, "SAVE MANUAL QSO")
    }

    @Test fun launcherIsSafeAndHasRealCanonicalModeMetadata() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { ctl ->
            val a = ctl.get()
            assertTrue(text(a).contains("RADIO DISCONNECTED"))
            assertTrue(text(a).contains("0 manual QSOs"))
            assertTrue(text(a).contains("15000 ms cycle"))
            tap(a, "DIGI")
            tap(a, "SELECT FT4")
            assertTrue(text(a).contains("Cycle: 7500 ms"))
            tap(a, "SELECT JS8")
            assertTrue(text(a).contains("Cycle: 15000 ms"))
            assertFalse(button(a, "ARM TX — HARDWARE VALIDATION REQUIRED").isEnabled)
            assertFalse(button(a, "START RX — NOT CONNECTED").isEnabled)
            tap(a, "RADIO")
            assertFalse(button(a, "PTT / TRANSMIT — LOCKED").isEnabled)
            assertFalse(button(a, "CONNECT CAT — PHYSICAL VALIDATION REQUIRED").isEnabled)
        }
    }

    @Test fun recordedFileReceiveOpensDocumentOnlyAndNeverArmsTransmitter() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { ctl ->
            val a = ctl.get()
            tap(a, "DIGI")
            assertTrue(text(a).contains("RECORDED-WAV RECEIVE / FILE ONLY"))
            assertTrue(text(a).contains("unverified"))
            assertFalse(button(a, "START RX — NOT CONNECTED").isEnabled)
            assertFalse(button(a, "ARM TX — HARDWARE VALIDATION REQUIRED").isEnabled)
            tap(a, "OPEN OFFLINE WAV FILE (NO RF)")
            val request = Shadows.shadowOf(a).nextStartedActivityForResult
            assertNotNull(request)
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, request.intent.action)
            assertTrue(request.intent.hasCategory(Intent.CATEGORY_OPENABLE))
            assertEquals("*/*", request.intent.type)
            tap(a, "OPS")
            assertTrue(text(a).contains("0 manual QSOs"))
            tap(a, "RADIO")
            assertFalse(button(a, "PTT / TRANSMIT — LOCKED").isEnabled)
            assertFalse(button(a, "CONNECT CAT — PHYSICAL VALIDATION REQUIRED").isEnabled)
        }
    }

    @Test fun manualQsoFeedsSameLogbookAdifAndAwardsWithoutLotw() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { ctl ->
            val a = ctl.get()
            tap(a, "LOG")
            field(a, "Remote callsign").setText("K1ABC")
            field(a, "Frequency in MHz (e.g. 7.075)").setText("7.075")
            field(a, "RST sent (optional)").setText("59")
            field(a, "RST received (optional)").setText("57")
            field(a, "Remote grid square (optional)").setText("EM12")
            tap(a, "SAVE MANUAL QSO")
            assertTrue(text(a).contains("Saved in memory: QSO #1 K1ABC SSB"))
            assertTrue(text(a).contains("40m"))
            assertTrue(text(a).contains("Not sent to LoTW"))
            tap(a, "SHOW ADIF FROM REAL LOGBOOK")
            assertTrue(text(a).contains("<CALL:5>K1ABC"))
            assertTrue(text(a).contains("<MODE:3>SSB"))
            assertTrue(text(a).contains("<RST_SENT:2>59"))
            assertTrue(text(a).contains("<RST_RCVD:2>57"))
            assertTrue(text(a).contains("<GRIDSQUARE:4>EM12"))
            tap(a, "AWARDS")
            assertTrue(text(a).contains("AWARDS CENTER / LOCAL EVIDENCE ONLY"))
            assertTrue(text(a).contains("Worked:"))
            tap(a, "OPS")
            assertTrue(text(a).contains("1 manual QSOs"))
            tap(a, "LOG")
            tap(a, "MANUAL MODE: SSB (SWITCH)")
            assertTrue(text(a).contains("MANUAL MODE: CW (SWITCH)"))
            field(a, "Remote callsign").setText("DL1XYZ")
            field(a, "Frequency in MHz (e.g. 7.075)").setText("14.074")
            tap(a, "SAVE MANUAL QSO")
            assertTrue(text(a).contains("Saved in memory: QSO #2 DL1XYZ CW"))
            tap(a, "MAP")
            assertTrue(text(a).contains("AWARD-AREA GEOMETRY"))
            assertTrue(text(a).contains("No authenticated APRS"))
        }
    }

    @Test fun exportingManualFtQsoRequestsExplicitDocumentAndNeverUploads() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { ctl ->
            val a = ctl.get()
            tap(a, "DIGI")
            tap(a, "SELECT FT4")
            tap(a, "PREPARE MANUAL FT4 QSO (NO AUTO LOG)")
            field(a, "Remote callsign").setText("K1JT")
            field(a, "Frequency in MHz (e.g. 7.075)").setText("14.08")
            tap(a, "CONFIRM COMPLETED FT QSO — SAVE MANUALLY")
            assertTrue(text(a).contains("Saved in memory: QSO #1 K1JT FT4"))
            tap(a, "EXPORT ADIF FILE (CHOOSE DESTINATION)")
            val request = Shadows.shadowOf(a).nextStartedActivityForResult
            assertNotNull("Save requires user-selected SAF destination", request)
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, request.intent.action)
            assertTrue(request.intent.hasCategory(Intent.CATEGORY_OPENABLE))
            assertEquals("text/plain", request.intent.type)
            assertTrue(request.intent.getStringExtra(Intent.EXTRA_TITLE)!!.endsWith(".adi"))
            assertTrue(text(a).contains("1 records"))
            tap(a, "RADIO")
            assertFalse(button(a, "PTT / TRANSMIT — LOCKED").isEnabled)
            tap(a, "OPS")
            assertTrue(text(a).contains("1 manual QSOs"))
        }
    }

    @Test fun restoreRequiresDocumentPickerAndSeparateOperatorConfirmation() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { ctl ->
            val a = ctl.get()
            tap(a, "LOG")
            tap(a, "RESTORE FIELDOPS ADIF (PICK FILE)")
            val request = Shadows.shadowOf(a).nextStartedActivityForResult
            assertNotNull(request)
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, request.intent.action)
            assertTrue(request.intent.hasCategory(Intent.CATEGORY_OPENABLE))
            assertFalse(text(a).contains("CONFIRM RESTORE TO LOCAL LOG"))
            assertTrue(text(a).contains("0 records"))
            tap(a, "RADIO")
            assertFalse(button(a, "PTT / TRANSMIT — LOCKED").isEnabled)
        }
    }

    @Test fun stagedAdifHasZeroLogEffectsUntilConfirmationAndDeduplicates() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { ctl ->
            val a = ctl.get()
            val staged = QsoRecord(
                id = 0, call = "K1JT", stationCallsign = "N0PNG",
                qsoDate = "20261027", timeOn = "120000", band = "20m", mode = "FT4",
                freqMhz = "14.08", frequencyHz = 14_080_000L,
                origin = QsoOrigin.IMPORTED, sourceProvider = "FIELDOPS_ADIF",
                lotwUpload = LotwUploadState.NOT_UPLOADED,
                lotwConfirmation = LotwConfirmationState.UNKNOWN,
            )
            val model = operatingModel(a)
            writeSessionField(model, "stagedAdifRecords", listOf(staged))
            tap(a, "LOG")
            assertTrue(text(a).contains("CONFIRM RESTORE TO LOCAL LOG"))
            assertTrue(text(a).contains("0 records"))
            tap(a, "AWARDS")
            assertTrue(text(a).contains("Worked:"))
            tap(a, "LOG")
            assertTrue(text(a).contains("0 records"))
            tap(a, "CONFIRM RESTORE TO LOCAL LOG (NO LOTW)")
            assertTrue(text(a).contains("CONFIRMED IMPORT: 1 new QSO"))
            assertTrue(text(a).contains("1 records"))
            tap(a, "SHOW ADIF FROM REAL LOGBOOK")
            assertTrue(text(a).contains("<MODE:3>FT4"))
            assertTrue(text(a).contains("<CALL:4>K1JT"))
            val entries = readSessionField(model, "logbook")
                as dev.n0png.fieldops.core.logbook.InMemoryLogbookRepository
            assertEquals(1, entries.all().size)
            assertEquals(QsoOrigin.IMPORTED, entries.all().single().origin)
            assertEquals(LotwConfirmationState.UNKNOWN,
                entries.all().single().lotwConfirmation)
            writeSessionField(model, "stagedAdifRecords", listOf(staged))
            tap(a, "OPS")
            tap(a, "LOG")
            tap(a, "CONFIRM RESTORE TO LOCAL LOG (NO LOTW)")
            assertTrue(text(a).contains("CONFIRMED IMPORT: 0 new QSO"))
            assertEquals(1, entries.all().size)
            tap(a, "RADIO")
            assertFalse(button(a, "PTT / TRANSMIT — LOCKED").isEnabled)
        }
    }

    @Test fun cancelStagedAdifImportKeepsExistingLogUnchanged() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { ctl ->
            val a = ctl.get()
            val model = operatingModel(a)
            writeSessionField(model, "stagedAdifRecords", listOf(QsoRecord(
                id = 0, call = "DL1XYZ", stationCallsign = "N0PNG",
                qsoDate = "20261027", timeOn = "120000", band = "20m",
                mode = "FT8", origin = QsoOrigin.IMPORTED
            )))
            tap(a, "LOG")
            assertTrue(text(a).contains("0 records"))
            tap(a, "CANCEL STAGED ADIF RESTORE")
            assertFalse(text(a).contains("CONFIRM RESTORE TO LOCAL LOG"))
            assertTrue(text(a).contains("0 records"))
        }
    }

    @Test fun noFileExportIsOfferedUntilTheOperatorHasLoggedAContact() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { ctl ->
            val a = ctl.get()
            tap(a, "LOG")
            assertFalse(button(a, "EXPORT ADIF FILE — NO QSOS").isEnabled)
            assertTrue(text(a).contains("No file exported"))
        }
    }

    @Test fun invalidFrequencyDoesNotCreateContactOrArmTransmitter() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { ctl ->
            val a = ctl.get()
            addQso(a, "K1ABC", "not-a-frequency")
            assertTrue(text(a).contains("NOT SAVED"))
            tap(a, "OPS")
            assertTrue(text(a).contains("0 manual QSOs"))
            tap(a, "APRS")
            assertFalse(button(a, "TRANSMIT PHONE PACKET — LOCKED").isEnabled)
        }
    }

    @Test fun rotationRetainsLocalSessionButFreshProcessDoesNotPretendPersistence() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        addQso(controller.get(), "K1ABC", "7.075")
        controller.configurationChange()
        val a = controller.get()
        assertTrue(text(a).contains("Saved in memory: QSO #1"))
        tap(a, "OPS")
        assertTrue(text(a).contains("1 manual QSOs"))
        controller.pause().stop().destroy()
        Robolectric.buildActivity(MainActivity::class.java).setup().use { fresh ->
            assertTrue(text(fresh.get()).contains("0 manual QSOs"))
            tap(fresh.get(), "LOG")
            assertFalse(text(fresh.get()).contains("#1  K1ABC"))
        }
    }

    @Test fun malformedGridNeverEntersAuthoritativeLogbookOrAwards() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { ctl ->
            val a = ctl.get()
            tap(a, "LOG")
            field(a, "Remote callsign").setText("K1ABC")
            field(a, "Frequency in MHz (e.g. 7.075)").setText("14.074")
            field(a, "Remote grid square (optional)").setText("NOT_A_GRID")
            tap(a, "SAVE MANUAL QSO")
            assertTrue(text(a).contains("NOT SAVED: enter a valid Maidenhead grid"))
            tap(a, "OPS")
            assertTrue(text(a).contains("0 manual QSOs"))
            tap(a, "LOG")
            field(a, "Remote grid square (optional)").setText("FN31")
            tap(a, "SAVE MANUAL QSO")
            assertTrue(text(a).contains("Saved in memory: QSO #1 K1ABC SSB"))
            tap(a, "SHOW ADIF FROM REAL LOGBOOK")
            assertTrue(text(a).contains("<GRIDSQUARE:4>FN31"))
        }
    }

    @Test fun unsavedDraftSurvivesTabChangeAndRotationWithoutSavingAContact() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = controller.get()
        tap(a, "LOG")
        field(a, "Remote callsign").setText("W1AW")
        field(a, "Frequency in MHz (e.g. 7.075)").setText("14.074")
        tap(a, "AWARDS")
        tap(a, "LOG")
        assertEquals("W1AW", field(a, "Remote callsign").text.toString())
        assertEquals("14.074", field(a, "Frequency in MHz (e.g. 7.075)").text.toString())
        controller.configurationChange()
        assertEquals("W1AW", field(controller.get(), "Remote callsign").text.toString())
        assertEquals("14.074",
            field(controller.get(), "Frequency in MHz (e.g. 7.075)").text.toString())
        tap(controller.get(), "OPS")
        assertTrue(text(controller.get()).contains("0 manual QSOs"))
        controller.pause().stop().destroy()
    }

    @Test fun ftModeDecodeNeverAutoLogsButOperatorCanPrepareAndConfirmManualDigitalQso() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { ctl ->
            val a = ctl.get()
            tap(a, "DIGI")
            assertTrue(text(a).contains("An offline decode, a CQ or a heard signal alone is NOT a QSO"))
            tap(a, "PREPARE MANUAL FT8 QSO (NO AUTO LOG)")
            assertTrue(text(a).contains("MANUAL FT CONTACT / OPERATOR ATTESTATION"))
            assertTrue(text(a).contains("MANUAL MODE: FT8 (SWITCH)"))
            assertTrue(text(a).contains("0 records"))
            field(a, "Remote callsign").setText("DL1XYZ")
            field(a, "Frequency in MHz (e.g. 7.075)").setText("14.074")
            field(a, "RST sent (optional)").setText("-08")
            field(a, "RST received (optional)").setText("-12")
            field(a, "Remote grid square (optional)").setText("JO40")
            tap(a, "CONFIRM COMPLETED FT QSO — SAVE MANUALLY")
            assertTrue(text(a).contains("Saved in memory: QSO #1 DL1XYZ FT8"))
            tap(a, "SHOW ADIF FROM REAL LOGBOOK")
            assertTrue(text(a).contains("<MODE:3>FT8"))
            assertTrue(text(a).contains("<CALL:6>DL1XYZ"))
            assertTrue(text(a).contains("<RST_SENT:3>-08"))
            tap(a, "RADIO")
            assertFalse(button(a, "PTT / TRANSMIT — LOCKED").isEnabled)
        }
    }

    @Test fun ft4AndFt2PrepareDistinctManualModesAndPreserveDraftAcrossRotation() {
        val ctl = Robolectric.buildActivity(MainActivity::class.java).setup()
        tap(ctl.get(), "DIGI")
        tap(ctl.get(), "SELECT FT4")
        tap(ctl.get(), "PREPARE MANUAL FT4 QSO (NO AUTO LOG)")
        field(ctl.get(), "Remote callsign").setText("K1ABC")
        field(ctl.get(), "Frequency in MHz (e.g. 7.075)").setText("7.074")
        ctl.configurationChange()
        assertEquals("K1ABC", field(ctl.get(), "Remote callsign").text.toString())
        assertTrue(text(ctl.get()).contains("MANUAL MODE: FT4 (SWITCH)"))
        tap(ctl.get(), "CONFIRM COMPLETED FT QSO — SAVE MANUALLY")
        tap(ctl.get(), "DIGI")
        tap(ctl.get(), "SELECT FT2")
        tap(ctl.get(), "PREPARE MANUAL FT2 QSO (NO AUTO LOG)")
        field(ctl.get(), "Remote callsign").setText("W1AW")
        field(ctl.get(), "Frequency in MHz (e.g. 7.075)").setText("14.074")
        tap(ctl.get(), "CONFIRM COMPLETED FT QSO — SAVE MANUALLY")
        tap(ctl.get(), "SHOW ADIF FROM REAL LOGBOOK")
        assertTrue(text(ctl.get()).contains("<MODE:3>FT4"))
        assertTrue(text(ctl.get()).contains("<MODE:3>FT2"))
        ctl.pause().stop().destroy()
    }

    @Test fun offlineDecodedSenderOnlyPrefillsUnverifiedManualDraftWithoutQso() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { ctl ->
            val a = ctl.get()
            val modelField = FieldOpsOperatingActivity::class.java.getDeclaredField("model")
            modelField.isAccessible = true
            val model = modelField.get(a)
            val decodedField = model.javaClass.getDeclaredField("offlineDecodes")
            decodedField.isAccessible = true
            decodedField.set(model, listOf(DecodeResult(
                DigitalMode.FT4, 0L, audioHz = 1500f, text = "K1JT N0PNG FN20",
                decodedSenderCallsign = "K1JT")))
            tap(a, "DIGI")
            tap(a, "REVIEW K1JT IN MANUAL LOG (NO AUTO QSO)")
            assertTrue(text(a).contains("UNVERIFIED WAV SENDER"))
            assertTrue(text(a).contains("MANUAL MODE: FT4 (SWITCH)"))
            assertEquals("K1JT", field(a, "Remote callsign").text.toString())
            assertEquals("", field(a, "Frequency in MHz (e.g. 7.075)").text.toString())
            assertEquals("", field(a, "RST sent (optional)").text.toString())
            assertTrue(text(a).contains("0 records"))
            tap(a, "OPS")
            assertTrue(text(a).contains("0 manual QSOs"))
            tap(a, "RADIO")
            assertFalse(button(a, "PTT / TRANSMIT — LOCKED").isEnabled)
        }
    }

    /**
     * Exercise the retained session's completion contract on the Android main
     * test thread without fake RF, network, USB or a real native recording.
     */
    private fun operatingModel(activity: MainActivity): Any =
        FieldOpsOperatingActivity::class.java.getDeclaredField("model").let {
            it.isAccessible = true
            requireNotNull(it.get(activity))
        }

    private fun readSessionField(model: Any, name: String): Any? =
        model.javaClass.getDeclaredField(name).let {
            it.isAccessible = true
            it.get(model)
        }

    private fun writeSessionField(model: Any, name: String, value: Any?) {
        model.javaClass.getDeclaredField(name).apply {
            isAccessible = true
            set(model, value)
        }
    }

    private fun completeSessionDecode(model: Any, generation: Long,
                                      mode: DigitalMode, results: List<DecodeResult>) {
        val method = model.javaClass.getDeclaredMethod("finishOfflineDecode",
            java.lang.Long.TYPE, DigitalMode::class.java, List::class.java, Throwable::class.java)
        method.isAccessible = true
        method.invoke(model, generation, mode, results, null)
    }

    private fun beginSessionDecode(model: Any): Long {
        val method = model.javaClass.getDeclaredMethod("beginOfflineDecode")
        method.isAccessible = true
        return method.invoke(model) as Long
    }

    @Test fun supersededModeDecodesCannotClearOrOverwriteNewerPendingWav() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { ctl ->
            val a = ctl.get()
            tap(a, "DIGI")
            val model = operatingModel(a)
            val oldToken = beginSessionDecode(model) // FT8 requested
            assertEquals(true, readSessionField(model, "offlineDecodeBusy"))
            tap(a, "SELECT FT4")
            assertEquals(false, readSessionField(model, "offlineDecodeBusy"))
            assertTrue(text(a).contains("No offline recording decoded for FT4"))
            val currentToken = beginSessionDecode(model)
            assertNotEquals(oldToken, currentToken)
            assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
                beginSessionDecode(model)
            }
            completeSessionDecode(model, oldToken, DigitalMode.FT8, listOf(DecodeResult(
                DigitalMode.FT8, 0L, text = "CQ K1JT FN20",
                decodedSenderCallsign = "K1JT")))
            assertEquals("Late old WAV must not end current processing", true,
                readSessionField(model, "offlineDecodeBusy"))
            assertFalse(text(a).contains("REVIEW K1JT IN MANUAL LOG"))
            completeSessionDecode(model, currentToken, DigitalMode.FT4, listOf(DecodeResult(
                DigitalMode.FT4, 0L, text = "CQ W1AW FN31",
                decodedSenderCallsign = "W1AW")))
            assertEquals(false, readSessionField(model, "offlineDecodeBusy"))
            assertTrue(text(a).contains("REVIEW W1AW IN MANUAL LOG"))
            assertFalse(text(a).contains("REVIEW K1JT IN MANUAL LOG"))
            // The earlier FT8 worker can complete again without modifying
            // a finished FT4 result or creating a phantom QSO.
            completeSessionDecode(model, oldToken, DigitalMode.FT8, emptyList())
            assertTrue(text(a).contains("REVIEW W1AW IN MANUAL LOG"))
            tap(a, "OPS")
            assertTrue(text(a).contains("0 manual QSOs"))
            tap(a, "RADIO")
            assertFalse(button(a, "PTT / TRANSMIT — LOCKED").isEnabled)
        }
    }

    @Test fun switchingAwayFromAndBackToFt8RejectsOriginalSameModeJob() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { ctl ->
            val a = ctl.get()
            tap(a, "DIGI")
            val model = operatingModel(a)
            val stale = beginSessionDecode(model)
            tap(a, "SELECT JS8")
            assertFalse(readSessionField(model, "offlineDecodeBusy") as Boolean)
            tap(a, "SELECT FT8")
            val current = beginSessionDecode(model)
            assertNotEquals(stale, current)
            completeSessionDecode(model, stale, DigitalMode.FT8, listOf(DecodeResult(
                DigitalMode.FT8, 0L, text = "K1JT FN20",
                decodedSenderCallsign = "K1JT")))
            assertTrue(readSessionField(model, "offlineDecodeBusy") as Boolean)
            completeSessionDecode(model, current, DigitalMode.FT8, emptyList())
            assertFalse(readSessionField(model, "offlineDecodeBusy") as Boolean)
            assertFalse(text(a).contains("REVIEW K1JT IN MANUAL LOG"))
        }
    }

    @Test fun inFlightDecoderCompletionAfterRotationRefreshesCurrentActivityOnly() {
        val ctl = Robolectric.buildActivity(MainActivity::class.java).setup()
        tap(ctl.get(), "DIGI")
        val oldActivity = ctl.get()
        val prior = operatingModel(oldActivity)
        writeSessionField(prior, "offlineDecodeBusy", true)
        val oldObserver = requireNotNull(readSessionField(prior, "onUiChanged"))
        // configurationChange() with unchanged qualifiers can be a no-op in
        // Robolectric. Explicit recreate() guarantees a NEW Activity, while
        // preserving its last non-configuration UiSession.
        ctl.recreate()
        assertNotSame("A real Activity instance must replace the prior screen",
            oldActivity, ctl.get())
        val current = operatingModel(ctl.get())
        assertSame("Retained QSO session and RX job state", prior, current)
        val newObserver = requireNotNull(readSessionField(current, "onUiChanged"))
        assertNotSame("Old Activity UI callback must be replaced", oldObserver, newObserver)
        completeSessionDecode(current, 0L, DigitalMode.FT8, listOf(DecodeResult(
            DigitalMode.FT8, 0L, audioHz = 1200f, text = "CQ K1JT FN20",
            decodedSenderCallsign = "K1JT")))
        assertTrue(text(ctl.get()).contains("1 decoded entries"))
        assertTrue(text(ctl.get()).contains("REVIEW K1JT IN MANUAL LOG"))
        assertEquals(false, readSessionField(current, "offlineDecodeBusy"))
        tap(ctl.get(), "OPS")
        assertTrue(text(ctl.get()).contains("0 manual QSOs"))
        tap(ctl.get(), "RADIO")
        assertFalse(button(ctl.get(), "PTT / TRANSMIT — LOCKED").isEnabled)
        ctl.pause().stop().destroy()
        assertNull("Destroy must remove last Activity callback",
            readSessionField(current, "onUiChanged"))
        // A decode that finishes after final Activity destruction must not
        // attempt to paint the defunct screen.
        completeSessionDecode(current, 0L, DigitalMode.FT8, emptyList())
    }

    @Test fun completedOlderModeWavDoesNotBecomeStationReviewOnNewMode() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { ctl ->
            val a = ctl.get()
            tap(a, "DIGI")
            val model = operatingModel(a)
            writeSessionField(model, "offlineDecodeBusy", true)
            tap(a, "SELECT FT4")
            assertTrue(text(a).contains("No offline recording decoded for FT4"))
            completeSessionDecode(model, 0L, DigitalMode.FT8, listOf(DecodeResult(
                DigitalMode.FT8, 0L, text = "K1JT N0PNG FN20",
                decodedSenderCallsign = "K1JT")))
            assertFalse(text(a).contains("REVIEW K1JT IN MANUAL LOG"))
            assertTrue(text(a).contains("No offline recording decoded for FT4"))
            assertEquals(false, readSessionField(model, "offlineDecodeBusy"))
            tap(a, "OPS")
            assertTrue(text(a).contains("0 manual QSOs"))
            tap(a, "RADIO")
            assertFalse(button(a, "PTT / TRANSMIT — LOCKED").isEnabled)
        }
    }

    @Test fun launcherStillNavigatesToPrivateSyntheticArchive() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { ctl ->
            val a = ctl.get()
            tap(a, "OPEN SYNTHETIC OFFLINE ARCHIVE HISTORY")
            val next: Intent = Shadows.shadowOf(a).nextStartedActivity
            assertNotNull(next)
            assertEquals(ArchiveHistoryActivity::class.java.name, next.component!!.className)
        }
    }
}
