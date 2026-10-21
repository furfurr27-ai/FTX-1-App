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

/** CP-0009Q: software-only UI checks; not evidence of a real Android Keystore. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecoveryConsentUxTest {
    private fun walk(root: View, action: (View) -> Unit) {
        action(root)
        if (root is ViewGroup) repeat(root.childCount) { walk(root.getChildAt(it), action) }
    }

    private fun text(a: ArchiveHistoryActivity): String {
        val value = StringBuilder()
        walk(a.window.decorView) { if (it is TextView) value.append(it.text).append('\n') }
        return value.toString()
    }

    private fun button(a: ArchiveHistoryActivity, name: String): Button? {
        var found: Button? = null
        walk(a.window.decorView) {
            if (it is Button && it.text.toString() == name) found = it
        }
        return found
    }

    private fun tap(a: ArchiveHistoryActivity, name: String) {
        val target = button(a, name)
        assertNotNull("Missing button $name", target)
        assertTrue(target!!.performClick())
    }

    private fun enableSavedOriginal(a: ArchiveHistoryActivity) {
        tap(a, "ENABLE ENCRYPTED ARCHIVE THIS SESSION")
        tap(a, "ENABLE ROTATION COPY THIS SESSION")
        tap(a, "SAVE ENCRYPTED COPY OF CURRENT HISTORY")
        assertTrue(text(a).contains("Saved AES-GCM encrypted V2 copy"))
    }

    private fun reviewSelect(a: ArchiveHistoryActivity) {
        tap(a, "REVIEW AUTHENTICATED RECOVERY OPTIONS")
        assertNull(button(a, "CONFIRM ORIGINAL_V2 RECOVERY TO MEMORY"))
        tap(a, "SELECT ORIGINAL_V2 FOR IN-MEMORY RECOVERY")
    }

    @Test fun typedConsentIsSourceSpecificAndDoesNotPromiseFreshness() {
        for (source in EncryptedRecoveryCandidate.values()) {
            val content = RecoveryConsentUx.selectionConsent(source)
            assertTrue(content.contains(source.name))
            assertTrue(content.contains("CURRENT ON-SCREEN HISTORY"))
            assertTrue(content.contains("FRESHNESS NOT VERIFIED"))
            assertTrue(content.contains("older, valid encrypted copy"))
            assertTrue(content.contains("NOT proof of station or RF provenance"))
            assertTrue(content.contains("CANCEL RECOVERY SELECTION"))
            assertFalse(content.contains("automatically chosen"))
        }
        assertTrue(RecoveryConsentUx.REJECTED.contains("REVIEW AGAIN"))
        assertTrue(RecoveryConsentUx.REJECTED.contains("on-screen history retained"))
    }

    @Test fun consentOnlyAfterSelectionAndCancellationDoesNotRestoreOrWrite() {
        Robolectric.buildActivity(SoftwareKeyArchiveHistoryActivity::class.java).setup().use {
            val a = it.get()
            enableSavedOriginal(a)
            tap(a, "REVIEW AUTHENTICATED RECOVERY OPTIONS")
            assertNull(button(a, "CANCEL RECOVERY SELECTION"))
            assertNull(button(a, "CONFIRM ORIGINAL_V2 RECOVERY TO MEMORY"))
            tap(a, "SELECT ORIGINAL_V2 FOR IN-MEMORY RECOVERY")
            assertTrue(text(a).contains("RECOVERY CONSENT — ORIGINAL_V2"))
            assertTrue(text(a).contains("Existing unsaved in-memory changes may be lost"))
            assertNotNull(button(a, "CONFIRM ORIGINAL_V2 RECOVERY TO MEMORY"))
            val encryptedFile = a.noBackupFilesDir.resolve("fieldops-synthetic-archive-v2.enc")
            val before = encryptedFile.readBytes()
            tap(a, "CANCEL RECOVERY SELECTION")
            assertNull(button(a, "CONFIRM ORIGINAL_V2 RECOVERY TO MEMORY"))
            assertTrue(text(a).contains("Recovery selection canceled"))
            assertTrue(text(a).contains("CANCEL_SELECTION | ORIGINAL_V2 | CLEARED"))
            assertTrue(text(a).contains("CURRENT EXPLICIT RECOVERY SOURCE: NONE"))
            assertTrue(text(a).contains("Retained reports: 3"))
            assertArrayEquals(before, encryptedFile.readBytes())
            // Cancellation is not a positive confirmation, but the review
            // remains available for an independent SELECT followed by CONFIRM.
            tap(a, "SELECT ORIGINAL_V2 FOR IN-MEMORY RECOVERY")
            assertNotNull(button(a, "CONFIRM ORIGINAL_V2 RECOVERY TO MEMORY"))
            tap(a, "CONFIRM ORIGINAL_V2 RECOVERY TO MEMORY")
            assertTrue(text(a).contains("CURRENT EXPLICIT RECOVERY SOURCE: ORIGINAL_V2"))
            assertTrue(text(a).contains("CONFIRM | ORIGINAL_V2 | ACCEPTED"))
            assertArrayEquals(before, encryptedFile.readBytes())
        }
    }

    @Test fun corruptEvidenceRejectsWithoutLeakingExceptionOrReplacingVisibleHistory() {
        Robolectric.buildActivity(SoftwareKeyArchiveHistoryActivity::class.java).setup().use {
            val a = it.get()
            enableSavedOriginal(a)
            reviewSelect(a)
            tap(a, "CONFIRM ORIGINAL_V2 RECOVERY TO MEMORY")
            assertTrue(text(a).contains("CURRENT EXPLICIT RECOVERY SOURCE: ORIGINAL_V2"))
            reviewSelect(a)
            val encryptedFile = a.noBackupFilesDir.resolve("fieldops-synthetic-archive-v2.enc")
            val preserved = encryptedFile.readBytes()
            try {
                encryptedFile.writeBytes(byteArrayOf(1, 2, 3, 4))
                tap(a, "CONFIRM ORIGINAL_V2 RECOVERY TO MEMORY")
                val result = text(a)
                assertTrue(result.contains("RECOVERY REJECTED; on-screen history retained"))
                assertTrue(result.contains("CURRENT EXPLICIT RECOVERY SOURCE: ORIGINAL_V2"))
                assertTrue(result.contains("CONFIRM | ORIGINAL_V2 | REJECTED"))
                assertTrue(result.contains("Retained reports: 3"))
                assertTrue(result.contains("FRESHNESS NOT VERIFIED"))
                assertNull(button(a, "CONFIRM ORIGINAL_V2 RECOVERY TO MEMORY"))
                assertFalse(result.contains("Encrypted file is missing, malformed"))
            } finally {
                encryptedFile.writeBytes(preserved)
            }
        }
    }
}
