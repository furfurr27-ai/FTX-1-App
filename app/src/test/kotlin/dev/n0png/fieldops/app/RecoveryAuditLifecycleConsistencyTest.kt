package dev.n0png.fieldops.app

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CP-0009P: Android/Robolectric synthetic archive and software-key lifecycle.
 * No physical Android Keystore, FTX-1, USB/CAT/RF or durable freshness proof.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecoveryAuditLifecycleConsistencyTest {
    private fun walk(view: View, fn: (View) -> Unit) {
        fn(view)
        if (view is ViewGroup) repeat(view.childCount) { walk(view.getChildAt(it), fn) }
    }
    private fun content(activity: ArchiveHistoryActivity): String {
        val b = StringBuilder()
        walk(activity.window.decorView) { if (it is TextView) b.append(it.text).append('\n') }
        return b.toString()
    }
    private fun hasButton(a: ArchiveHistoryActivity, name: String): Boolean {
        var present = false
        walk(a.window.decorView) {
            if (it is Button && it.text.toString() == name) present = true
        }
        return present
    }
    private fun tap(activity: ArchiveHistoryActivity, name: String) {
        var button: Button? = null
        walk(activity.window.decorView) {
            if (it is Button && it.text.toString() == name) button = it
        }
        assertNotNull("Missing button: $name", button)
        assertTrue(button!!.performClick())
    }
    private fun prepare(activity: ArchiveHistoryActivity) {
        tap(activity, "ENABLE ENCRYPTED ARCHIVE THIS SESSION")
        tap(activity, "ENABLE ROTATION COPY THIS SESSION")
        tap(activity, "SAVE ENCRYPTED COPY OF CURRENT HISTORY")
        assertTrue(content(activity).contains("Saved AES-GCM encrypted V2 copy"))
    }
    private fun reviewSelect(activity: ArchiveHistoryActivity) {
        tap(activity, "REVIEW AUTHENTICATED RECOVERY OPTIONS")
        assertTrue(hasButton(activity, "SELECT ORIGINAL_V2 FOR IN-MEMORY RECOVERY"))
        tap(activity, "SELECT ORIGINAL_V2 FOR IN-MEMORY RECOVERY")
        assertTrue(hasButton(activity, "CONFIRM ORIGINAL_V2 RECOVERY TO MEMORY"))
    }

    @Test fun evidenceMutationInvalidatesPendingSelectAndNeverAutomaticallyFallsBack() {
        Robolectric.buildActivity(SoftwareKeyArchiveHistoryActivity::class.java).setup().use {
            val a = it.get()
            prepare(a)
            reviewSelect(a)
            tap(a, "SAVE ENCRYPTED COPY OF CURRENT HISTORY")
            assertFalse(hasButton(a, "CONFIRM ORIGINAL_V2 RECOVERY TO MEMORY"))
            assertFalse(hasButton(a, "SELECT ORIGINAL_V2 FOR IN-MEMORY RECOVERY"))
            assertTrue(content(a).contains("OPTIONS_INVALIDATED | NO_CANDIDATE | CLEARED"))
            assertTrue(content(a).contains("REVIEW AGAIN"))
            assertTrue(content(a).contains("Retained reports: 3"))
            reviewSelect(a)
            tap(a, "DISABLE ROTATION COPY SESSION")
            assertFalse(hasButton(a, "CONFIRM ORIGINAL_V2 RECOVERY TO MEMORY"))
            assertTrue(content(a).contains("SESSION_DISABLED | NO_CANDIDATE | ACCEPTED"))
            assertTrue(content(a).contains("FRESHNESS NOT VERIFIED"))
            assertTrue(content(a).contains("CURRENT EXPLICIT RECOVERY SOURCE: NONE"))
        }
    }

    @Test fun navigationPreservesAuthenticatedDisplaySourceButMutationAndRecreationClearIt() {
        val runner = Robolectric.buildActivity(SoftwareKeyArchiveHistoryActivity::class.java).setup()
        try {
            var a = runner.get()
            prepare(a)
            reviewSelect(a)
            tap(a, "CONFIRM ORIGINAL_V2 RECOVERY TO MEMORY")
            assertTrue(content(a).contains("CURRENT EXPLICIT RECOVERY SOURCE: ORIGINAL_V2"))
            assertTrue(content(a).contains("CONFIRM | ORIGINAL_V2 | ACCEPTED"))
            // UI paging does not replace archive bytes; do not mislabel as unknown.
            if (hasButton(a, "NEXT PAGE")) tap(a, "NEXT PAGE")
            assertTrue(content(a).contains("CURRENT EXPLICIT RECOVERY SOURCE: ORIGINAL_V2"))
            tap(a, "ADD SYNTHETIC REPORT")
            assertTrue(content(a).contains("CURRENT EXPLICIT RECOVERY SOURCE: NONE"))
            assertTrue(content(a).contains("DISPLAY_REPLACED | NO_CANDIDATE | ACCEPTED"))
            // A second successful recovery is not retained as a provenance
            // claim across a newly created Activity/configuration instance.
            reviewSelect(a)
            tap(a, "CONFIRM ORIGINAL_V2 RECOVERY TO MEMORY")
            assertTrue(content(a).contains("CURRENT EXPLICIT RECOVERY SOURCE: ORIGINAL_V2"))
            runner.recreate()
            a = runner.get()
            assertFalse(content(a).contains("Action 1:"))
            assertTrue(content(a).contains("CURRENT EXPLICIT RECOVERY SOURCE: NONE"))
            assertTrue(content(a).contains("Retained reports: 3"))
        } finally {
            runner.pause().stop().destroy()
        }
    }

    @Test fun deletingSourceFileRetainsDisplayedSourceButInvalidatesChoice() {
        Robolectric.buildActivity(SoftwareKeyArchiveHistoryActivity::class.java).setup().use {
            val a=it.get()
            prepare(a)
            reviewSelect(a)
            tap(a,"CONFIRM ORIGINAL_V2 RECOVERY TO MEMORY")
            assertTrue(content(a).contains("CURRENT EXPLICIT RECOVERY SOURCE: ORIGINAL_V2"))
            reviewSelect(a)
            tap(a,"DELETE ENCRYPTED V2 FILE ONLY")
            assertTrue(content(a).contains("CURRENT EXPLICIT RECOVERY SOURCE: ORIGINAL_V2"))
            assertFalse(hasButton(a, "CONFIRM ORIGINAL_V2 RECOVERY TO MEMORY"))
            assertTrue(content(a).contains("OPTIONS_INVALIDATED | NO_CANDIDATE | CLEARED"))
            tap(a,"RESTORE ENCRYPTED V2 ARCHIVE")
            assertTrue(content(a).contains("No saved encrypted V2 file"))
            assertTrue(content(a).contains("CURRENT EXPLICIT RECOVERY SOURCE: ORIGINAL_V2"))
            assertTrue(content(a).contains("FRESHNESS NOT VERIFIED"))
        }
    }
}
