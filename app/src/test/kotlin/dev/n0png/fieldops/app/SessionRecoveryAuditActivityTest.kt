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

/** Android UI simulation, no actual Android Keystore or RF tests. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionRecoveryAuditActivityTest {
    private fun walk(view: View, onView: (View) -> Unit) {
        onView(view)
        if (view is ViewGroup) repeat(view.childCount) { walk(view.getChildAt(it), onView) }
    }
    private fun text(activity: ArchiveHistoryActivity): String {
        val out = StringBuilder()
        walk(activity.window.decorView) {
            if (it is TextView) out.append(it.text).append('\n')
        }
        return out.toString()
    }
    private fun tap(activity: ArchiveHistoryActivity, name: String) {
        var found: Button? = null
        walk(activity.window.decorView) {
            if (it is Button && it.text.toString() == name) found = it
        }
        assertNotNull(name, found)
        assertTrue(found!!.performClick())
    }

    @Test fun rejectedReviewBlockedRetirementAndClearHaveNoPayloadAudit() {
        Robolectric.buildActivity(ArchiveHistoryActivity::class.java).setup().use {
            val activity = it.get()
            assertTrue(text(activity).contains("SESSION-ONLY RECOVERY AUDIT"))
            assertTrue(text(activity).contains("FRESHNESS NOT VERIFIED"))
            assertTrue(text(activity).contains("CURRENT EXPLICIT RECOVERY SOURCE: NONE"))
            assertFalse(text(activity).contains("Action 1:"))
            tap(activity, "ENABLE ROTATION COPY THIS SESSION")
            tap(activity, "REVIEW AUTHENTICATED RECOVERY OPTIONS")
            assertTrue(text(activity).contains("Action 1: REVIEW | NO_CANDIDATE | REJECTED"))
            tap(activity, "REVIEW OLD-KEY RETIREMENT BLOCKERS (READ ONLY)")
            assertTrue(text(activity).contains("Action 2: RETIREMENT_REVIEW | NO_CANDIDATE | BLOCKED"))
            assertTrue(text(activity).contains("Old-key retirement: BLOCKED_NON_EXECUTING"))
            tap(activity, "ENABLE ENCRYPTED ARCHIVE THIS SESSION")
            tap(activity, "REVIEW AUTHENTICATED RECOVERY OPTIONS")
            assertTrue(text(activity).contains("Action 3: REVIEW | NO_CANDIDATE | ACCEPTED"))
            assertTrue(text(activity).contains("FRESHNESS NOT VERIFIED"))
            tap(activity, "CLEAR SESSION RECOVERY AUDIT")
            assertFalse(text(activity).contains("Action 1:"))
            assertFalse(text(activity).contains("Action 2:"))
            assertFalse(text(activity).contains("Action 3:"))
            tap(activity, "REVIEW AUTHENTICATED RECOVERY OPTIONS")
            assertTrue(text(activity).contains("Action 1: REVIEW | NO_CANDIDATE | ACCEPTED"))
            assertTrue(text(activity).contains("Retained reports: 3"))
        }
    }

    @Test fun activityRecreationStartsFreshAuditNotSavedState() {
        val first = Robolectric.buildActivity(ArchiveHistoryActivity::class.java).setup()
        try {
            val a = first.get()
            tap(a, "ENABLE ROTATION COPY THIS SESSION")
            tap(a, "REVIEW AUTHENTICATED RECOVERY OPTIONS")
            assertTrue(text(a).contains("Action 1: REVIEW"))
            // The audit belongs to the Activity instance and cannot be
            // included in a Bundle, retained configuration or a disk store.
            first.recreate()
            val newActivity = first.get()
            assertTrue(text(newActivity).contains("SESSION-ONLY RECOVERY AUDIT"))
            assertFalse(text(newActivity).contains("Action 1:"))
            assertTrue(text(newActivity).contains("CURRENT EXPLICIT RECOVERY SOURCE: NONE"))
        } finally {
            first.pause().stop().destroy()
        }
    }
}
