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

/** Actual Android synthetic screen integration; no hardware KeyStore claims. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExplicitRotationActivityTest {
    private fun walk(root: View, cb: (View) -> Unit) {
        cb(root)
        if (root is ViewGroup) repeat(root.childCount) { walk(root.getChildAt(it), cb) }
    }
    private fun button(a: ArchiveHistoryActivity, label: String): Button? {
        var found: Button? = null
        walk(a.window.decorView) { if (it is Button && it.text.toString() == label) found = it }
        return found
    }
    private fun text(a: ArchiveHistoryActivity): String {
        val result = StringBuilder()
        walk(a.window.decorView) { if (it is TextView) result.append(it.text).append('\n') }
        return result.toString()
    }
    private fun tap(a: ArchiveHistoryActivity, label: String) {
        val b = button(a, label)
        assertNotNull("Missing button $label", b)
        assertTrue("Click refused $label", b!!.performClick())
    }

    @Test fun rotationCopyRequiresSeparateOptInAndMissingSourceRetainsHistory() {
        Robolectric.buildActivity(ArchiveHistoryActivity::class.java).setup().use {
            val activity = it.get()
            assertTrue(text(activity).contains("FRESHNESS NOT VERIFIED"))
            assertTrue(text(activity).contains("Existing V2, its key"))
            assertNull(button(activity, "CREATE DISTINCT-KEY ENCRYPTED COPY"))
            tap(activity, "ENABLE ROTATION COPY THIS SESSION")
            assertNotNull(button(activity, "CREATE DISTINCT-KEY ENCRYPTED COPY"))
            tap(activity, "DELETE ROTATED COPY ONLY")
            tap(activity, "RESTORE ROTATED ENCRYPTED COPY")
            assertTrue(text(activity).contains("No rotated encrypted copy exists"))
            tap(activity, "CREATE DISTINCT-KEY ENCRYPTED COPY")
            assertTrue(text(activity).contains("ROTATION COPY REJECTED"))
            assertTrue(text(activity).contains("Retained reports: 3"))
            tap(activity, "DISABLE ROTATION COPY SESSION")
            assertNull(button(activity, "RESTORE ROTATED ENCRYPTED COPY"))
        }
    }

    @Test fun unreadableRotatedCopyDoesNotAutoRestoreLegacyOrDeleteEvidence() {
        Robolectric.buildActivity(ArchiveHistoryActivity::class.java).setup().use {
            val activity = it.get()
            tap(activity, "ENABLE ROTATION COPY THIS SESSION")
            val file = java.io.File(
                activity.applicationContext.noBackupFilesDir,
                ExplicitEncryptedArchiveRotation.ROTATED_FILENAME
            )
            file.writeBytes("FTX1ENC2broken-rotated-record".toByteArray())
            try {
                tap(activity, "RESTORE ROTATED ENCRYPTED COPY")
                assertTrue(text(activity).contains("ROTATED RESTORE REJECTED"))
                assertTrue(text(activity).contains("no fallback"))
                assertTrue(text(activity).contains("Retained reports: 3"))
                assertTrue(file.exists())
            } finally {
                file.delete()
            }
        }
    }
}
