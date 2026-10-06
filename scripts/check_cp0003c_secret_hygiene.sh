#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "[1/8] Reject tracked certificate/key/backup containers"
if git ls-files | grep -Eiq '\.(tbk|p12|pfx|pem|key|crt|cer)$'; then
  echo "Sensitive certificate/key/backup container is tracked:" >&2
  git ls-files | grep -Ei '\.(tbk|p12|pfx|pem|key|crt|cer)$' >&2
  exit 1
fi

echo "[2/8] Reject embedded private-key material"
if git grep -n -I -E -- '-----BEGIN ([A-Z0-9 ]+ )?PRIVATE KEY-----|-----BEGIN OPENSSH PRIVATE KEY-----' -- . ':(exclude)scripts/check_cp0003c_secret_hygiene.sh'; then
  echo "Private-key material found in tracked source" >&2
  exit 1
fi

echo "[3/8] Validation app must disable Android backup"
grep -Fq 'android:allowBackup="false"'   android/validation-app/app/src/main/AndroidManifest.xml

echo "[4/8] Validation app must block screenshots/recent thumbnails"
grep -Fq 'WindowManager.LayoutParams.FLAG_SECURE'   android/validation-app/app/src/main/kotlin/dev/n0png/fieldops/validation/MainActivity.kt

echo "[5/8] Validation app must not persist credentials"
! grep -RInE 'SharedPreferences|getSharedPreferences|RoomDatabase|SQLiteDatabase|putString\(|password.*File|keyPassword.*File'   android/validation-app/app/src/main/kotlin || {
  echo "Potential credential persistence path found in validation app" >&2
  exit 1
}

echo "[6/8] Validation app must clear password fields and wipe mutable secret buffers"
grep -Fq 'lotwPassword.setText("")'   android/validation-app/app/src/main/kotlin/dev/n0png/fieldops/validation/MainActivity.kt
grep -Fq 'keyPassword.setText("")'   android/validation-app/app/src/main/kotlin/dev/n0png/fieldops/validation/MainActivity.kt
grep -Fq "Arrays.fill(secret, '\\u0000')"   android/validation-app/app/src/main/kotlin/dev/n0png/fieldops/validation/MainActivity.kt
grep -Fq 'Arrays.fill(bytes, 0)'   android/validation-app/app/src/main/kotlin/dev/n0png/fieldops/validation/MainActivity.kt

echo "[7/8] Reject obvious credential literals"
if git grep -n -I -E --   '(lotw[_-]?(password|passwd)[[:space:]]*[:=][[:space:]]*["'\''"][^"'\'']{4,}|BEGIN RSA PRIVATE KEY|BEGIN EC PRIVATE KEY)'   -- ':!research/tqsl/CP-0003C_DEVICE_VALIDATION.md' ':!scripts/check_cp0003c_secret_hygiene.sh'; then
  echo "Potential credential literal found" >&2
  exit 1
fi

echo "[8/8] Validate committed device evidence if present"
if [[ -f research/tqsl/CP-0003C_DEVICE_EVIDENCE.txt ]]; then
  python3 scripts/validate_cp0003c_device_evidence.py
fi

echo "CP-0003C secret-hygiene gate: PASS"
