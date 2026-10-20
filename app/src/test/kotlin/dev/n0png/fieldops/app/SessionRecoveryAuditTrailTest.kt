package dev.n0png.fieldops.app

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** CP-0009O bounded metadata-only audit; software simulation only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionRecoveryAuditTrailTest {
    @Test fun boundedDeterministicActionLabelsOnly() {
        val audit = SessionRecoveryAuditTrail(capacity = 3)
        audit.record(RecoveryAuditAction.REVIEW, RecoveryAuditOutcome.ACCEPTED)
        audit.record(RecoveryAuditAction.SELECT, RecoveryAuditOutcome.ACCEPTED,
            EncryptedRecoveryCandidate.ORIGINAL_V2)
        audit.record(RecoveryAuditAction.CONFIRM, RecoveryAuditOutcome.REJECTED,
            EncryptedRecoveryCandidate.ORIGINAL_V2)
        audit.record(RecoveryAuditAction.CONFIRM, RecoveryAuditOutcome.ACCEPTED,
            EncryptedRecoveryCandidate.ROTATED_V2)
        val records = audit.snapshot()
        assertEquals(3, records.size)
        assertEquals(listOf(2,3,4), records.map { it.ordinal })
        assertEquals(RecoveryAuditAction.SELECT, records.first().action)
        assertEquals(RecoveryAuditOutcome.ACCEPTED, records.last().outcome)
        assertEquals(EncryptedRecoveryCandidate.ROTATED_V2, records.last().candidate)
        assertFalse(records.toString().contains("SHA-256"))
        assertFalse(records.toString().contains("Keystore alias"))
        assertFalse(records.toString().contains("file:"))
    }

    @Test fun snapshotDoesNotExposeMutableBackingCollection() {
        val audit = SessionRecoveryAuditTrail()
        audit.record(RecoveryAuditAction.REVIEW, RecoveryAuditOutcome.REJECTED)
        val before = audit.snapshot()
        audit.record(RecoveryAuditAction.RETIREMENT_REVIEW, RecoveryAuditOutcome.BLOCKED)
        assertEquals(1, before.size)
        assertEquals(2, audit.snapshot().size)
        audit.clear()
        assertTrue(audit.snapshot().isEmpty())
        assertEquals(1, before.first().ordinal)
        audit.record(RecoveryAuditAction.SELECT, RecoveryAuditOutcome.ACCEPTED,
            EncryptedRecoveryCandidate.ORIGINAL_V2)
        assertEquals(1, audit.snapshot().single().ordinal)
    }

    @Test fun instanceAuditHasNoSharedPersistentOrClockState() {
        val a = SessionRecoveryAuditTrail()
        a.record(RecoveryAuditAction.CONFIRM, RecoveryAuditOutcome.REJECTED)
        val fresh = SessionRecoveryAuditTrail()
        assertTrue(fresh.snapshot().isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            SessionRecoveryAuditTrail(capacity = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SessionRecoveryAuditTrail(capacity = 65)
        }
        assertThrows(IllegalArgumentException::class.java) {
            a.record(RecoveryAuditAction.CLEAR, RecoveryAuditOutcome.CLEARED)
        }
    }
}
