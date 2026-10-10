#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
echo "[1/4] Compile platform-neutral Kotlin core"
find "$ROOT/core/src/main/kotlin" -name '*.kt' -print0 | sort -z | xargs -0 kotlinc -d "$BUILD/core.jar"
echo "[2/4] Compile CP-0008Y display integration tests"
kotlinc "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/PropagationOfflineReportComparisonDisplayTests.kt" \
  -cp "$BUILD/core.jar" -include-runtime -d "$BUILD/tests.jar"
echo "[3/4] Exercise historical display contract and pagination integration"
java -cp "$BUILD/tests.jar:$BUILD/core.jar" dev.n0png.fieldops.core.PropagationOfflineReportComparisonDisplayTests
echo "[4/4] Enforce offline read-only boundaries and owner deferral"
SRC="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineReportComparisonDisplayService.kt"
! grep -Eq 'snapshotStore|refreshStateStore|\.latest\(|\.save\(|\.fetch\(|System.currentTimeMillis|Instant.now|android\.|androidx\.|UsbManager|WorkManager|URL\.open|OkHttp|javax\.net|java\.io\.File' "$SRC"
grep -Fq 'PropagationOfflineReportComparisonPaginationService.page(' "$SRC"
grep -Fq 'CHECKSUM_NOT_AUTHENTICATION' "$SRC"
grep -Fq 'SELECTED_VIEW_NOT_PROVIDER_DELETION' "$SRC"
grep -Fq 'CP-0003C remains **DEFERRED**' "$ROOT/research/propagation/CP-0008Y_OFFLINE_COMPARISON_DISPLAY_CONTRACT.md"
echo "CP-0008Y historical paged display integration host gate: PASS"
