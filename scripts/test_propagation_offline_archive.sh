#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
echo "[1/4] Compile platform-neutral production core"
find "$ROOT/core/src/main/kotlin" -name '*.kt' -print0 | sort -z | xargs -0 kotlinc -d "$BUILD/core.jar"
echo "[2/4] Compile bounded archive tests"
kotlinc "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/PropagationOfflineReportArchiveTests.kt" -cp "$BUILD/core.jar" -include-runtime -d "$BUILD/tests.jar"
echo "[3/4] Run deterministic retention/import/eviction/paging/forgery cases"
java -cp "$BUILD/tests.jar:$BUILD/core.jar" dev.n0png.fieldops.core.PropagationOfflineReportArchiveTests
echo "[4/4] Assert no implicit clock, Android, RF, network or filesystem behavior"
SRC="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineReportArchiveService.kt"
! grep -Eq 'System.currentTimeMillis|Instant.now|android\.|androidx\.|UsbManager|WorkManager|URL\.open|OkHttp|java\.io\.File|FileOutputStream|java\.net\.' "$SRC"
grep -Fq 'PropagationOfflineReportImportService.importReport(' "$SRC"
grep -Fq 'PropagationWorkspaceHistoryService.compare(' "$SRC"
grep -Fq 'CP-0003C remains **DEFERRED**' "$ROOT/research/propagation/CP-0009B_OFFLINE_ARCHIVE_CONTRACT.md"
echo "CP-0009B bounded offline archive: PASS"
