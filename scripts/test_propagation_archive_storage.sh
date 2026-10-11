#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
export JAVA_OPTS="-Xmx1536m -Xms128m"
find "$ROOT/core/src/main/kotlin" -name '*.kt' -print0 | sort -z | xargs -0 kotlinc -d "$BUILD/core.jar"
kotlinc "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/PropagationArchiveStorageCodecTests.kt" \
  -cp "$BUILD/core.jar" -include-runtime -d "$BUILD/tests.jar"
java -cp "$BUILD/tests.jar:$BUILD/core.jar" \
  dev.n0png.fieldops.core.PropagationArchiveStorageCodecTests
SRC="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationArchiveStorageCodec.kt"
! grep -Eq 'android\.|Context|UsbManager|HttpURLConnection|FileOutputStream|java\.nio\.file\.Files|SQLite|SharedPreferences|URL\.open' "$SRC"
grep -Fq 'PropagationOfflineReportImportService.importReport' "$SRC"
grep -Fq 'PropagationOfflineReportArchiveService.validate' "$SRC"
grep -Fq 'MessageDigest.isEqual' "$SRC"
echo "CP-0009G canonical bounded archive wire contract: PASS"
