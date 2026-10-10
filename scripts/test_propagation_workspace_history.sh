#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
echo "[1/4] Compile platform-neutral Kotlin production core"
find "$ROOT/core/src/main/kotlin" -name '*.kt' -print0 | sort -z | xargs -0 kotlinc -d "$BUILD/core.jar"
echo "[2/4] Compile CP-0009A workspace-history integration tests"
kotlinc "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/PropagationWorkspaceHistoryTests.kt" \
  -cp "$BUILD/core.jar" -include-runtime -d "$BUILD/tests.jar"
echo "[3/4] Exercise single-capture source to historical interaction integration"
java -cp "$BUILD/tests.jar:$BUILD/core.jar" dev.n0png.fieldops.core.PropagationWorkspaceHistoryTests
echo "[4/4] Enforce host-only and hardware deferral boundaries"
SRC="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationWorkspaceHistoryService.kt"
! grep -Eq 'snapshotStore|refreshStateStore|\.latest\(|\.save\(|\.fetch\(|System.currentTimeMillis|Instant.now|android\.|androidx\.|UsbManager|WorkManager|URL\.open|OkHttp|javax\.net|java\.io\.File' "$SRC"
grep -Fq 'PropagationOfflineDiagnosticReportService.build(' "$SRC"
grep -Fq 'PropagationOfflineReportComparisonInteractionService.open(' "$SRC"
grep -Fq 'CP-0003C remains **DEFERRED**' "$ROOT/research/propagation/CP-0009A_WORKSPACE_HISTORY_INTEGRATION.md"
echo "CP-0009A workspace/history integration: PASS"
