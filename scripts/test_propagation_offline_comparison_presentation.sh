#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
echo "[1/4] Compile all platform-neutral Kotlin production core"
find "$ROOT/core/src/main/kotlin" -name '*.kt' -print0 | sort -z | xargs -0 kotlinc -d "$BUILD/core.jar"
echo "[2/4] Compile CP-0008W presentation tests"
kotlinc "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/PropagationOfflineReportComparisonPresentationTests.kt" \
  -cp "$BUILD/core.jar" -include-runtime -d "$BUILD/tests.jar"
echo "[3/4] Run deterministic historical presentation tests"
java -cp "$BUILD/tests.jar:$BUILD/core.jar" dev.n0png.fieldops.core.PropagationOfflineReportComparisonPresentationTests
echo "[4/4] Guard offline-only scope"
SRC="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineReportComparisonPresentationService.kt"
! grep -Eq 'snapshotStore|refreshStateStore|\.latest\(|\.save\(|\.fetch\(|System.currentTimeMillis|Instant.now|android\.|androidx\.|UsbManager|WorkManager|URL\.open|OkHttp|javax\.net|java\.io\.File' "$SRC"
grep -Fq 'PropagationOfflineReportComparisonImportService.importComparison(artifact)' "$SRC"
grep -Fq 'ORIGINAL_REPORTS_NOT_SUPPLIED' "$SRC"
grep -Fq 'CP-0003C remains **DEFERRED**' "$ROOT/research/propagation/CP-0008W_OFFLINE_COMPARISON_PRESENTATION.md"
echo "CP-0008W historical comparison presentation host gate: PASS"
