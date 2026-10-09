#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
echo "[1/5] Compile all platform-neutral core production Kotlin"
find "$ROOT/core/src/main/kotlin" -name '*.kt' -print0 | sort -z | xargs -0 kotlinc -d "$BUILD/core.jar"
echo "[2/5] Compile CP-0008S synthetic comparison tests"
kotlinc "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/PropagationOfflineReportComparisonTests.kt" -cp "$BUILD/core.jar" -include-runtime -d "$BUILD/tests.jar"
echo "[3/5] Run deterministic, offline comparison assertions"
java -cp "$BUILD/tests.jar:$BUILD/core.jar" dev.n0png.fieldops.core.PropagationOfflineReportComparisonTests
echo "[4/5] Enforce offline-only, validation-first, non-authenticated boundary"
SRC="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineReportComparisonService.kt"
! grep -Eq 'snapshotStore|refreshStateStore|\.latest\(|\.save\(|\.fetch\(|System.currentTimeMillis|Instant.now|android\.|androidx\.|UsbManager|WorkManager|URL\.open|OkHttp|javax\.net|java\.io\.File' "$SRC"
grep -Fq 'PropagationOfflineReportImportService.importReport(beforeArtifact)' "$SRC"
grep -Fq 'PropagationOfflineReportImportService.importReport(afterArtifact)' "$SRC"
grep -Fq 'originAuthenticated: Boolean = false' "$SRC"
grep -Fq 'crossStoreAtomicityVerified: Boolean = false' "$SRC"
grep -Fq 'CP-0003C remains **DEFERRED**' "$ROOT/research/propagation/CP-0008S_OFFLINE_REPORT_INSPECTION_COMPARISON.md"
echo "[5/5] Preserve canonical V1 import"
grep -Fq 'const val WIRE_VERSION = 1' "$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineReportSerialization.kt"
grep -Fq 'PropagationOfflineReportDecoder.decode(encoded)' "$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineReportImportService.kt"
echo "CP-0008S offline report comparison host gate: PASS"
