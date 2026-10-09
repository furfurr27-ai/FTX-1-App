#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
echo "[1/5] Compile platform-neutral Kotlin production core"
find "$ROOT/core/src/main/kotlin" -name '*.kt' -print0 | sort -z | xargs -0 kotlinc -d "$BUILD/core.jar"
echo "[2/5] Compile CP-0008V import/inspection Kotlin host tests"
kotlinc "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/PropagationOfflineReportComparisonImportTests.kt" \
  -cp "$BUILD/core.jar" -include-runtime -d "$BUILD/tests.jar"
echo "[3/5] Run historical selection, receipt and optional source reconciliation tests"
java -cp "$BUILD/tests.jar:$BUILD/core.jar" dev.n0png.fieldops.core.PropagationOfflineReportComparisonImportTests
echo "[4/5] Verify no live/Android/account/hardware path exists in CP-0008V boundary"
SRC="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineReportComparisonImportService.kt"
! grep -Eq 'snapshotStore|refreshStateStore|\.latest\(|\.save\(|\.fetch\(|System.currentTimeMillis|Instant.now|android\.|androidx\.|UsbManager|WorkManager|URL\.open|OkHttp|javax\.net|java\.io\.File' "$SRC"
grep -Fq 'PropagationOfflineReportComparisonDecoder.decode(encoded)' "$SRC"
grep -Fq 'PropagationOfflineReportComparisonService.compare(before, after)' "$SRC"
echo "[5/5] Confirm deferred hardware checkpoint remains explicit"
grep -Fq 'CP-0003C remains **DEFERRED**' "$ROOT/research/propagation/CP-0008V_OFFLINE_COMPARISON_IMPORT_INSPECTION.md"
grep -Fq 'CP-0004A/B/C remain incomplete' "$ROOT/research/propagation/CP-0008V_OFFLINE_COMPARISON_IMPORT_INSPECTION.md"
echo "CP-0008V offline comparison import and inspection host gate: PASS"
