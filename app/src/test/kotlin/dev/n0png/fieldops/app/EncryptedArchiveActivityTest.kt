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

/** Actual Activity clicks; hardware Keystore execution is covered only on-device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EncryptedArchiveActivityTest {
    private fun views(root: View, visitor: (View) -> Unit) {
        visitor(root)
        if (root is ViewGroup) for (i in 0 until root.childCount) {
            views(root.getChildAt(i), visitor)
        }
    }
    private fun button(activity: ArchiveHistoryActivity, label: String): Button? {
        var hit: Button? = null
        views(activity.window.decorView) {
            if (it is Button && it.text.toString() == label) hit = it
        }
        return hit
    }
    private fun text(activity: ArchiveHistoryActivity): String {
        val builder = StringBuilder()
        views(activity.window.decorView) {
            if (it is TextView) builder.append(it.text).append('\n')
        }
        return builder.toString()
    }
    private fun click(activity: ArchiveHistoryActivity, label: String) {
        val target = button(activity, label)
        assertNotNull("Expected explicit UI action: $label", target)
        assertTrue(target!!.performClick())
    }

    @Test fun encryptedControlsRequireSeparateOptInAndNeverAutoRestore() {
        Robolectric.buildActivity(ArchiveHistoryActivity::class.java).setup().use {
            val activity = it.get()
            assertTrue(text(activity).contains("Retained reports: 3"))
            assertTrue(text(activity).contains("UNENCRYPTED V1"))
            assertTrue(text(activity).contains("FRESHNESS NOT VERIFIED"))
            assertTrue(text(activity).contains("Older authentic V2 copies may be replayed"))
            assertNull(button(activity, "RESTORE ENCRYPTED V2 ARCHIVE"))
            click(activity, "ENABLE ENCRYPTED ARCHIVE THIS SESSION")
            assertNotNull(button(activity, "SAVE ENCRYPTED COPY OF CURRENT HISTORY"))
            assertNotNull(button(activity, "MIGRATE VALIDATED LOCAL V1 TO ENCRYPTED COPY"))
            click(activity, "DELETE ENCRYPTED V2 FILE ONLY")
            click(activity, "RESTORE ENCRYPTED V2 ARCHIVE")
            assertTrue(text(activity).contains("No saved encrypted V2 file"))
            assertTrue(text(activity).contains("Retained reports: 3"))
            click(activity, "MIGRATE VALIDATED LOCAL V1 TO ENCRYPTED COPY")
            assertTrue(text(activity).contains("ENCRYPTED MIGRATION REJECTED"))
            assertTrue(text(activity).contains("Retained reports: 3"))
            click(activity, "DISABLE ENCRYPTED ARCHIVE SESSION")
            assertNull(button(activity, "RESTORE ENCRYPTED V2 ARCHIVE"))
        }
    }

    @Test fun encryptedRestoreFailureDoesNotFallBackToLocalPlaintext() {
        Robolectric.buildActivity(ArchiveHistoryActivity::class.java).setup().use {
            val activity = it.get()
            click(activity, "ENABLE ENCRYPTED ARCHIVE THIS SESSION")
            val file = java.io.File(activity.applicationContext.noBackupFilesDir,
                "fieldops-synthetic-archive-v2.enc")
            file.writeBytes("FTX1ENC2not-an-authenticated-file".toByteArray())
            try {
                click(activity, "RESTORE ENCRYPTED V2 ARCHIVE")
                assertTrue(text(activity).contains("ENCRYPTED RESTORE REJECTED"))
                assertTrue(text(activity).contains("no fallback to V1"))
                assertTrue(text(activity).contains("Retained reports: 3"))
                assertTrue(file.exists())
            } finally {
                file.delete()
            }
        }
    }
}
