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
              "targetSdk = 35", "versionCode = 61"):
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
