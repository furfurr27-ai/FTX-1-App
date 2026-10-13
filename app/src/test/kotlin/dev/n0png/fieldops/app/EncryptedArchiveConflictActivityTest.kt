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
class EncryptedArchiveConflictActivityTest {
    private fun walk(v: View, visit: (View) -> Unit) {
        visit(v)
        if (v is ViewGroup) repeat(v.childCount) { walk(v.getChildAt(it), visit) }
    }
    private fun button(a: ArchiveHistoryActivity, label: String): Button? {
        var b: Button? = null
        walk(a.window.decorView) {
            if (it is Button && it.text.toString() == label) b = it
        }
        return b
    }
    private fun text(a: ArchiveHistoryActivity): String {
        val b = StringBuilder()
        walk(a.window.decorView) { if (it is TextView) b.append(it.text).append('\n') }
        return b.toString()
    }
    private fun tap(a: ArchiveHistoryActivity, label: String) {
        assertTrue(button(a, label)?.performClick() == true)
    }

    @Test fun inspectionIsReadOnlyRequiresBothExplicitSessions() {
        Robolectric.buildActivity(ArchiveHistoryActivity::class.java).setup().use {
            val a = it.get()
            val label = "INSPECT ENCRYPTED CANDIDATES (READ ONLY)"
            assertNull(button(a, label))
            tap(a, "ENABLE ROTATION COPY THIS SESSION")
            assertNotNull(button(a, label))
            tap(a, label)
            assertTrue(text(a).contains("READ-ONLY INSPECTION REJECTED"))
            assertTrue(text(a).contains("Retained reports: 3"))
            tap(a, "ENABLE ENCRYPTED ARCHIVE THIS SESSION")
            val original = java.io.File(
                a.applicationContext.noBackupFilesDir, "fieldops-synthetic-archive-v2.enc"
            )
            val rotated = java.io.File(
                a.applicationContext.noBackupFilesDir,
                ExplicitEncryptedArchiveRotation.ROTATED_FILENAME
            )
            val originalBefore = original.takeIf { it.exists() }?.readBytes()
            val rotatedBefore = rotated.takeIf { it.exists() }?.readBytes()
            tap(a, label)
            assertTrue(text(a).contains("Relationship: "))
            assertTrue(text(a).contains("FRESHNESS NOT VERIFIED"))
            assertTrue(text(a).contains("Old-key retirement: BLOCKED_NON_EXECUTING"))
            assertTrue(text(a).contains("No archive automatically selected or changed"))
            assertTrue(text(a).contains("Retained reports: 3"))
            assertEquals(originalBefore?.toList(), original.takeIf { it.exists() }?.readBytes()?.toList())
            assertEquals(rotatedBefore?.toList(), rotated.takeIf { it.exists() }?.readBytes()?.toList())
        }
    }
}
