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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExplicitArchiveRecoveryActivityTest {
    private fun walk(root: View, fn: (View) -> Unit) {
        fn(root)
        if (root is ViewGroup) repeat(root.childCount) { walk(root.getChildAt(it), fn) }
    }
    private fun buttons(a: ArchiveHistoryActivity): List<Button> {
        val out = mutableListOf<Button>()
        walk(a.window.decorView) { if (it is Button) out.add(it) }
        return out
    }
    private fun contents(a: ArchiveHistoryActivity): String {
        val out = StringBuilder()
        walk(a.window.decorView) { if (it is TextView) out.append(it.text).append('\n') }
        return out.toString()
    }
    private fun tap(a: ArchiveHistoryActivity, name: String) {
        val item = buttons(a).firstOrNull { it.text.toString() == name }
        assertNotNull("Missing button $name", item)
        assertTrue(item!!.performClick())
    }

    @Test fun reviewAndRetirementChecklistNeverAutorestoreOrRetire() {
        Robolectric.buildActivity(ArchiveHistoryActivity::class.java).setup().use {
            val activity = it.get()
            assertFalse(buttons(activity).any {
                it.text.toString() == "REVIEW AUTHENTICATED RECOVERY OPTIONS"
            })
            tap(activity, "ENABLE ROTATION COPY THIS SESSION")
            tap(activity, "REVIEW AUTHENTICATED RECOVERY OPTIONS")
            assertTrue(contents(activity).contains("RECOVERY REVIEW REJECTED"))
            assertTrue(contents(activity).contains("Retained reports: 3"))
            tap(activity, "REVIEW OLD-KEY RETIREMENT BLOCKERS (READ ONLY)")
            assertTrue(contents(activity).contains("BLOCKED_NON_EXECUTING"))
            assertTrue(contents(activity).contains("monotonic"))
            assertTrue(contents(activity).contains("No key or ciphertext deletion"))
            tap(activity, "ENABLE ENCRYPTED ARCHIVE THIS SESSION")
            tap(activity, "REVIEW AUTHENTICATED RECOVERY OPTIONS")
            assertTrue(contents(activity).contains("Authenticated recovery choices:"))
            assertTrue(contents(activity).contains("FRESHNESS NOT VERIFIED"))
            assertFalse(buttons(activity).any {
                it.text.toString().startsWith("CONFIRM ") &&
                    it.text.toString().endsWith("RECOVERY TO MEMORY")
            })
            assertTrue(contents(activity).contains("Retained reports: 3"))
        }
    }
}
