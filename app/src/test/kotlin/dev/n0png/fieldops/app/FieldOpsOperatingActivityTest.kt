package dev.n0png.fieldops.app

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
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
            assertTrue(text(a).contains("7500 ms cycle"))
            tap(a, "SELECT JS8")
            assertTrue(text(a).contains("15000 ms cycle"))
            assertFalse(button(a, "ARM TX — HARDWARE VALIDATION REQUIRED").isEnabled)
            assertFalse(button(a, "START RX — NOT CONNECTED").isEnabled)
            tap(a, "RADIO")
            assertFalse(button(a, "PTT / TRANSMIT — LOCKED").isEnabled)
            assertFalse(button(a, "CONNECT CAT — PHYSICAL VALIDATION REQUIRED").isEnabled)
        }
    }

    @Test fun manualQsoFeedsSameLogbookAdifAndAwardsWithoutLotw() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { ctl ->
            val a = ctl.get()
            addQso(a, "K1ABC", "7.075")
            assertTrue(text(a).contains("Saved in memory: QSO #1 K1ABC SSB"))
            assertTrue(text(a).contains("40m"))
            assertTrue(text(a).contains("Not sent to LoTW"))
            tap(a, "SHOW ADIF FROM REAL LOGBOOK")
            assertTrue(text(a).contains("<CALL:5>K1ABC"))
            assertTrue(text(a).contains("<MODE:3>SSB"))
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
