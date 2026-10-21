#!/usr/bin/env python3
"""Static CP-0009D safety: a packaged offline shell cannot quietly acquire RF privileges."""
from __future__ import annotations
import pathlib
import re
import xml.etree.ElementTree as ET

root = pathlib.Path(__file__).resolve().parents[1]
manifest = root / "app/src/main/AndroidManifest.xml"
source = root / "app/src/main/java/dev/n0png/fieldops/app/MainActivity.java"
appgradle = root / "app/build.gradle.kts"
app = ET.parse(manifest).getroot()
android = "{http://schemas.android.com/apk/res/android}"
if app.findall("uses-permission"):
    raise SystemExit("CP-0009D shell must declare NO runtime permissions")
if app.findall("uses-feature"):
    raise SystemExit("CP-0009D shell must not depend on radio hardware")
activities = app.findall("application/activity")
activity = next((a for a in activities if a.get(android + "name") ==
                 "dev.n0png.fieldops.app.MainActivity"), None)
archive = next((a for a in activities if a.get(android + "name") ==
                "dev.n0png.fieldops.app.ArchiveHistoryActivity"), None)
if archive is None or archive.get(android + "exported") != "false":
    raise SystemExit("Synthetic archive must be private to the app")
if activity is None or activity.get(android + "exported") != "true":
    raise SystemExit("Missing explicit launcher Activity export")
if not any(a.get(android + "name") == "android.intent.action.MAIN"
           for a in activity.findall("intent-filter/action")):
    raise SystemExit("Missing Android MAIN launcher intent")
text = source.read_text()
for token in ("startService(", "sendBroadcast(", "UsbManager", "AudioRecord(",
              "requestPermissions(", "getSystemService(", "HttpURLConnection",
              "DatagramSocket", "android.permission"):
    if token in text:
        raise SystemExit("Unexpected integration beyond offline packaging: " + token)
if "does not connect to a radio" not in text:
    raise SystemExit("Missing explicit no-radio developer UI notice")
gradle = appgradle.read_text()
for field in ("applicationId = \"dev.n0png.fieldops\"", "compileSdk = 35",
              "targetSdk = 35", "versionCode = 67"):
    if field not in gradle:
        raise SystemExit("Android packaging contract missing: " + field)
print("CP-0009D offline application shell/manifest safety contract: PASS")

archive_src = root / "app/src/main/kotlin/dev/n0png/fieldops/app/ArchiveHistoryActivity.kt"
if not archive_src.is_file():
    raise SystemExit("Missing CP-0009E Android archive Activity")
source_text = archive_src.read_text()
for required in ("PropagationArchiveWorkspaceService", "PropagationArchiveWorkspaceAction.Select(",
                 "PropagationArchiveWorkspaceAction.Compare(", "PropagationArchiveWorkspaceAction.Delete(",
                 "PropagationArchiveWorkspaceAction.NextPage", "PropagationArchiveWorkspaceAction.PreviousPage",
                 "screen.provenanceNotices", "InMemoryPropagationSnapshotStore",
                 "NOT LIVE", "contentDescription"):
    if required not in source_text:
        raise SystemExit("Archive UI must use canonical model with history warning: " + required)
for disallowed in ("UsbManager", "AudioRecord", "startService(", "requestPermissions(",
                   "HttpURLConnection", "DatagramSocket", "FileOutputStream"):
    if disallowed in source_text:
        raise SystemExit("CP-0009E history demonstration requests real hardware I/O: " + disallowed)
print("CP-0009E Android synthetic archive navigation contract: PASS")

# CP-0009F: only nonconfiguration in-memory state may be retained.
archive_kotlin = archive_src.read_text()
for required in ("onRetainNonConfigurationInstance()", "lastNonConfigurationInstance", "RotationSession", "Fresh process/session"):
    if required not in archive_kotlin:
        raise SystemExit("Missing transient-only archive lifecycle contract: " + required)
for forbidden in ("openFileOutput(", "getSharedPreferences(", "Room.databaseBuilder", "FileOutputStream", "SavedStateHandle"):
    if forbidden in archive_kotlin:
        raise SystemExit("Unexpected durable Android archive storage: " + forbidden)
print("CP-0009F transient rotation/session storage boundary: PASS")

# CP-0009G bounded private-file persistence is explicitly opted in.
persistence_file = root / "app/src/main/kotlin/dev/n0png/fieldops/app/OptInArchiveStore.kt"
persistence = persistence_file.read_text()
for token in ("noBackupFilesDir", "enableForThisSession", "check(enabled)", "ATOMIC_MOVE",
              "REPLACE_EXISTING", "stream.fd.sync()", "PropagationArchiveStorageCodec.decode"):
    if token not in persistence:
        raise SystemExit("Missing opt-in private storage safety requirement: " + token)
for token in ("getExternalFilesDir", "getExternalStorageDirectory", "URL(", "HttpURLConnection",
              "Room.databaseBuilder", "SharedPreferences", "AES/ECB"):
    if token in persistence:
        raise SystemExit("Unexpected external or unverified persistence behavior: " + token)
for token in ("ENABLE LOCAL ARCHIVE STORAGE", "SAVE SYNTHETIC ARCHIVE LOCALLY",
              "RESTORE SAVED LOCAL ARCHIVE", "DELETE LOCAL ARCHIVE FILE", "not encrypted",
              "RESTORE REJECTED"):
    if token not in archive_kotlin:
        raise SystemExit("Missing explicit Android opt-in/corruption UI: " + token)
print("CP-0009G explicit private-file storage, no-backup and unencrypted provenance: PASS")

# CP-0009H recovery/hardening contract.
for required in ("ArchivePreCommitStage.AFTER_STAGED_SYNC", "ArchivePreCommitStage.BEFORE_ATOMIC_REPLACE",
                 "Existing archive failed integrity checks", "preCommitFault.check",
                 "FileInputStream(file)", "output.size().toLong() + count <= maxBytes.toLong()"):
    if required not in persistence:
        raise SystemExit("Missing CP-0009H fail-closed store recovery requirement: " + required)
for required in ("RESTORE REJECTED; on-screen archive unchanged", "Use DELETE LOCAL ARCHIVE FILE only if you want"):
    if required not in archive_kotlin:
        raise SystemExit("Missing explicit CP-0009H corrupt restore recovery UX: " + required)
print("CP-0009H staged-write failure isolation and explicit recovery warnings: PASS")

# CP-0009J separate app-private AES-GCM V2 feature: never disable prior V1 safety.
encrypted_file = root / "app/src/main/kotlin/dev/n0png/fieldops/app/OptInEncryptedArchiveStore.kt"
encrypted_source = encrypted_file.read_text()
for required in ("AndroidKeyStore", "KeyGenParameterSpec.Builder", "setKeySize(256)",
                 "setRandomizedEncryptionRequired(true)", "noBackupFilesDir",
                 "fieldops-synthetic-archive-v2.enc", "saveEncryptedExplicitly",
                 "loadEncryptedExplicitly", "ArchivePreCommitStage",
                 "StandardCopyOption.ATOMIC_MOVE", "output.fd.sync()"):
    if required not in encrypted_source:
        raise SystemExit("CP-0009J encrypted opt-in file requirement missing: " + required)
for forbidden in ("getExternalFilesDir", "HttpURLConnection", "URL(", "AES/ECB"):
    if forbidden in encrypted_source:
        raise SystemExit("Unexpected CP-0009J external I/O or weak crypto: " + forbidden)
for required in ("ENABLE ENCRYPTED ARCHIVE THIS SESSION",
                 "SAVE ENCRYPTED COPY OF CURRENT HISTORY",
                 "MIGRATE VALIDATED LOCAL V1 TO ENCRYPTED COPY",
                 "RESTORE ENCRYPTED V2 ARCHIVE", "DELETE ENCRYPTED V2 FILE ONLY",
                 "no fallback to V1", "UNENCRYPTED V1"):
    if required not in archive_kotlin:
        raise SystemExit("CP-0009J encrypted opt-in UI missing: " + required)
print("CP-0009J distinct opt-in encrypted V2 storage and UI: PASS")

# AndroidKeyStore with randomizedEncryptionRequired(true) must generate its own IV
# during encryption; caller-specified GCM nonce is rejected by Keystore.
crypto_source = (root / "core/src/main/kotlin/dev/n0png/fieldops/core/propagation/AuthenticatedArchiveEnvelopeV2.kt").read_text()
for required in ("cipher.init(Cipher.ENCRYPT_MODE, key, secureRandom)", "val nonce = cipher.iv",
                 "nonce.size == NONCE_BYTES", "cipher.updateAAD(header)"):
    if required not in crypto_source:
        raise SystemExit("CP-0009J provider-issued GCM IV contract missing: " + required)
if "cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(" in crypto_source:
    raise SystemExit("CP-0009J AndroidKeyStore encryption must not supply a caller IV")
print("CP-0009J provider-generated AES-GCM nonce Keystore compatibility: PASS")

# CP-0009K: No automatic key rotation or false rollback/replay guarantees.
lifecycle_source = (root / "core/src/main/kotlin/dev/n0png/fieldops/core/propagation/EncryptedArchiveKeyLifecycleContract.kt").read_text()
for marker in ("ROTATION_EXECUTOR_NOT_IMPLEMENTED", "NO_TRUSTED_MONOTONIC_WITNESS",
               "TRUSTED_WITNESS_INTEGRATION_NOT_IMPLEMENTED", "PRESERVE_UNREADABLE_CIPHERTEXT"):
    if marker not in lifecycle_source:
        raise SystemExit("CP-0009K fail-closed key lifecycle marker missing: " + marker)
for forbidden in ("javax.crypto", "FileOutputStream", "KeyGenerator", "Files.move", "HttpURLConnection"):
    if forbidden in lifecycle_source:
        raise SystemExit("CP-0009K lifecycle policy must not rotate or write keys: " + forbidden)
for required in ("FRESHNESS NOT VERIFIED", "Older authentic V2 copies may be replayed",
                 "ArchiveReplayInputs", "ArchiveReplayDecision.NO_TRUSTED_MONOTONIC_WITNESS"):
    if required not in archive_kotlin:
        raise SystemExit("CP-0009K missing explicit no-replay-proof UX: " + required)
print("CP-0009K no-write key lifecycle and replay warning: PASS")

# CP-0009L: distinct-KeyStore-alias rotation only on explicit user action.
rotation_source = (root / "app/src/main/kotlin/dev/n0png/fieldops/app/ExplicitEncryptedArchiveRotation.kt").read_text()
for needle in ("AndroidKeystoreRotatedArchiveKeyProvider", "setKeySize(256)",
               "setRandomizedEncryptionRequired(true)", "ROTATED_FILENAME",
               "legacyEncrypted.loadEncryptedExplicitly()", "rotateExplicitly()",
               "restoreRotatedExplicitly()", "Files.createTempFile",
               "Files.createLink", "AFTER_STAGED_SYNC", "BEFORE_ATOMIC_PUBLISH",
               "output.fd.sync()", "context.noBackupFilesDir"):
    if needle not in rotation_source:
        # Stage IO sync uses 'it.fd.sync()' in a scoped Kotlin lambda.
        if needle == "output.fd.sync()" and "it.fd.sync()" in rotation_source:
            continue
        raise SystemExit("CP-0009L rotation source guard missing: " + needle)
for forbidden in ("KeyStore.deleteEntry", "deleteEntry(", "HttpURLConnection",
                  "getExternalFilesDir", "REPLACE_EXISTING", "System.exit"):
    if forbidden in rotation_source:
        raise SystemExit("CP-0009L automatic key retirement, external I/O or overwrite: " + forbidden)
for needle in ("ENABLE ROTATION COPY THIS SESSION", "CREATE DISTINCT-KEY ENCRYPTED COPY",
               "RESTORE ROTATED ENCRYPTED COPY", "DELETE ROTATED COPY ONLY",
               "original V2/key retained", "FRESHNESS NOT VERIFIED"):
    if needle not in archive_kotlin:
        raise SystemExit("CP-0009L opt-in/retention UI missing: " + needle)
print("CP-0009L distinct alias, atomic exclusive link, opt-in/retention UI: PASS")

# CP-0009M: opt-in, read-only inspection must not rotate or retire keys.
inspector = (root / "app/src/main/kotlin/dev/n0png/fieldops/app/EncryptedArchiveConflictInspector.kt").read_text()
for required in ("ArchiveCandidateEvidence", "KEY_UNAVAILABLE", "UNREADABLE",
                 "CONFLICTING_AUTHENTICATED", "MATCHING_AUTHENTICATED",
                 "BLOCKED_NON_EXECUTING", "FRESHNESS NOT VERIFIED",
                 "inspectExplicitly()", "original.loadEncryptedExplicitly()",
                 "rotated.restoreRotatedExplicitly()"):
    if required not in inspector:
        raise SystemExit("CP-0009M read-only inspection contract missing: " + required)
for forbidden in ("createKeyOnExplicitSave(", "saveEncryptedExplicitly(",
                  "rotateExplicitly(", "deleteEncryptedExplicitly(",
                  "deleteRotatedCopyExplicitly(", "deleteEntry(", "Files.move("):
    if forbidden in inspector:
        raise SystemExit("CP-0009M inspection may not change keys or ciphertext: " + forbidden)
for required in ("INSPECT ENCRYPTED CANDIDATES (READ ONLY)",
                 "EncryptedArchiveConflictInspector(", "Old-key retirement:",
                 "No archive automatically selected or changed."):
    if required not in archive_kotlin:
        raise SystemExit("CP-0009M Android inspection UX missing: " + required)
print("CP-0009M read-only inspection / no retirement contract: PASS")

# CP-0009N: explicit candidate selection and revalidation; no key retirement.
recovery = (root / "app/src/main/kotlin/dev/n0png/fieldops/app/ExplicitEncryptedArchiveRecovery.kt").read_text()
for required in ("EncryptedRecoveryCandidate", "ORIGINAL_V2", "ROTATED_V2",
                 "reviewExplicitly()", "recoverToMemoryExplicitly(",
                 "reviewRetirementReadiness()", "inspector.inspectExplicitly()",
                 "now == priorReview.inspection", "expected.canonicalSha256 == digest",
                 "BLOCKED_NON_EXECUTING", "FRESHNESS NOT VERIFIED",
                 "original.loadEncryptedExplicitly()", "rotated.restoreRotatedExplicitly()"):
    if required not in recovery:
        raise SystemExit("CP-0009N explicit recovery guard missing: " + required)
for forbidden in ("createKeyOnExplicitSave(", "saveEncryptedExplicitly(",
                  "rotateExplicitly(", "deleteEncryptedExplicitly(",
                  "deleteRotatedCopyExplicitly(", "deleteEntry(", "Files.move(",
                  "Files.delete(", "KeyStore.getInstance("):
    if forbidden in recovery:
        raise SystemExit("CP-0009N recovery code must not write or retire keys: " + forbidden)
for required in ("REVIEW AUTHENTICATED RECOVERY OPTIONS",
                 "REVIEW OLD-KEY RETIREMENT BLOCKERS (READ ONLY)",
                 "SELECT ", "CONFIRM ", "confirmReviewedRecovery(",
                 "recoverToMemoryExplicitly(", "recoveryReview = null",
                 "No files or keys changed.", "FRESHNESS NOT VERIFIED"):
    if required not in archive_kotlin:
        raise SystemExit("CP-0009N two-tap UX / retirement warning missing: " + required)
print("CP-0009N explicit recovery reauthentication, no key retirement: PASS")

# CP-0009O: audit may store enum metadata ONLY, no strings, secrets or clocks.
audit_source = (root / "app/src/main/kotlin/dev/n0png/fieldops/app/SessionRecoveryAuditTrail.kt").read_text()
for required in ("enum class RecoveryAuditAction", "enum class RecoveryAuditOutcome",
                 "data class RecoveryAuditEntry(", "SessionRecoveryAuditTrail(",
                 "capacity: Int = 12", "val ordinal: Int", "ArrayDeque<RecoveryAuditEntry>",
                 "snapshot(): List<RecoveryAuditEntry>", "fun clear()", "entries.removeFirst()"):
    if required not in audit_source:
        raise SystemExit("CP-0009O audit-only contract missing: " + required)
for forbidden in ("android.content.Context", "SharedPreferences", "File(", "FileOutputStream",
                  "SQLiteDatabase", "RoomDatabase", "System.currentTimeMillis", "Instant.now(",
                  "Serializable", "Parcelable", "android.util.Log", "Log.", "HttpURLConnection",
                  "SecretKey", "MessageDigest", "StringBuilder", "entryCount", "canonicalSha256"):
    if forbidden in audit_source:
        raise SystemExit("CP-0009O audit may not store provenance payload/secret/time: " + forbidden)
for required in ("SESSION-ONLY RECOVERY AUDIT", "CURRENT EXPLICIT RECOVERY SOURCE:",
                 "FRESHNESS NOT VERIFIED", "SessionRecoveryAuditTrail()",
                 "recoveryAudit.snapshot()", "recoveryAudit.clear()",
                 "RecoveryAuditAction.REVIEW", "RecoveryAuditAction.SELECT",
                 "RecoveryAuditAction.CONFIRM", "RecoveryAuditAction.RETIREMENT_REVIEW",
                 "RecoveryAuditOutcome.REJECTED", "RecoveryAuditOutcome.BLOCKED",
                 "invalidateDisplayedRecoveryProvenance()"):
    if required not in archive_kotlin:
        raise SystemExit("CP-0009O Android audit/provenance UX missing: " + required)
print("CP-0009O nonpersistent typed bounded audit, no payload and explicit provenance: PASS")

# CP-0009P: no stale review selection or provenance claim following mutation.
for required in ("fun invalidateRecoveryOptions()", "fun invalidateDisplayedRecoveryProvenance()",
                 "val originalArchive = state.archive", "state.archive != originalArchive",
                 "RecoveryAuditAction.OPTIONS_INVALIDATED", "RecoveryAuditAction.DISPLAY_REPLACED",
                 "RecoveryAuditOutcome.CLEARED", "recoveryReview = null",
                 "pendingRecovery = null", "recoveryAudit.snapshot()",
                 "CURRENT EXPLICIT RECOVERY SOURCE:", "FRESHNESS NOT VERIFIED"):
    if required not in archive_kotlin:
        raise SystemExit("CP-0009P lifecycle/provenance guard missing: " + required)
for label in ("SAVE ENCRYPTED COPY OF CURRENT HISTORY",
              "MIGRATE VALIDATED LOCAL V1 TO ENCRYPTED COPY",
              "DELETE ENCRYPTED V2 FILE ONLY",
              "CREATE DISTINCT-KEY ENCRYPTED COPY",
              "DELETE ROTATED COPY ONLY",
              "DISABLE ENCRYPTED ARCHIVE SESSION",
              "DISABLE ROTATION COPY SESSION"):
    start = archive_kotlin.find('actionButton("' + label + '")')
    if start < 0 or "invalidateRecoveryOptions()" not in archive_kotlin[start:start+290]:
        raise SystemExit("CP-0009P mutation/disable must invalidate review: " + label)
for required in ("OPTIONS_INVALIDATED", "DISPLAY_REPLACED"):
    if required not in audit_source:
        raise SystemExit("CP-0009P typed audit action missing: " + required)
print("CP-0009P stale-review invalidation, archive-only source changes, typed audit: PASS")


# CP-0009Q: consent content must stay outside typed-only audit.
consent_source = (root / "app/src/main/kotlin/dev/n0png/fieldops/app/RecoveryConsentUx.kt").read_text()
for required in ("object RecoveryConsentUx", "selectionConsent(",
                 "CURRENT ON-SCREEN", "FRESHNESS NOT VERIFIED",
                 "NOT proof of station or RF provenance",
                 "CANCEL RECOVERY SELECTION", "RECOVERY REJECTED;",
                 "REVIEW AGAIN"):
    if required not in consent_source:
        raise SystemExit("CP-0009Q consent/failure UX missing: " + required)
for required in ("RecoveryConsentUx.selectionConsent(candidate)",
                 "RecoveryConsentUx.REJECTED",
                 "RecoveryConsentUx.CANCELLED",
                 "RecoveryAuditAction.CANCEL_SELECTION",
                 'actionButton("CANCEL RECOVERY SELECTION")',
                 'actionButton("CONFIRM " + candidate.name + " RECOVERY TO MEMORY")'):
    if required not in archive_kotlin:
        raise SystemExit("CP-0009Q explicit consent/cancel/confirm guard missing: " + required)
if "CANCEL_SELECTION" not in audit_source:
    raise SystemExit("CP-0009Q closed typed audit lacks CANCEL_SELECTION")
for forbidden in ("android.content.Context", "SecretKey", "File(", "System.currentTimeMillis",
                  "Log.", "MessageDigest", "SharedPreferences", "Serializable", "Parcelable"):
    if forbidden in consent_source:
        raise SystemExit("CP-0009Q consent renderer must remain side-effect free: " + forbidden)
if "versionCode = 68" not in appgradle.read_text():
    raise SystemExit("CP-0009Q APK versionCode 68 expected")
print("CP-0009Q explicit consent, safe failure, cancel and typed-only audit: PASS")
