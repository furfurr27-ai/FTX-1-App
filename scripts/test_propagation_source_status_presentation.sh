#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
MAIN_JAR="$BUILD/fieldops-core-main.jar"
TEST_JAR="$BUILD/cp0008l-status-tests.jar"
SOURCE="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationSourceStatusService.kt"
RUNTIME="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationRuntime.kt"
TEST="$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/PropagationSourceStatusTests.kt"
EVIDENCE="$ROOT/research/propagation/CP-0008L_SOURCE_STATUS_PRESENTATION.md"

echo "[1/6] Compile core main"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"
echo "[2/6] Compile source-status tests separately"
kotlinc "$TEST" -cp "$MAIN_JAR" -include-runtime -d "$TEST_JAR"
echo "[3/6] Run deterministic source-status projections"
java -cp "$TEST_JAR:$MAIN_JAR" dev.n0png.fieldops.core.PropagationSourceStatusTests
echo "[4/6] Check read-only store integration"
grep -Fq 'fun project(nowUtcMillis: Long)' "$SOURCE"
grep -Fq 'PropagationSourceFreshnessDefaults.classify' "$SOURCE"
grep -Fq 'state.role.managesSourceId' "$SOURCE"
grep -Fq 'fun sourceStatus(nowUtcMillis: Long)' "$RUNTIME"
grep -Fq 'PropagationSourceStatusService(refreshStateStore, snapshotStore)' "$RUNTIME"
! grep -Eq 'refreshAndProject\(|\.save\(|\.fetch\(|System.currentTimeMillis|Instant.now|Clock.systemUTC' "$SOURCE"
echo "[5/6] Enforce platform/hardware/privacy boundary"
! grep -Eq 'WorkManager|android\.|androidx\.|RadioSession|PTT|privateKey|password|credential|UsbManager' "$SOURCE"
echo "[6/6] Verify evidence and explicit owner deferral"
grep -Fq 'CP-0003C remains **DEFERRED**' "$EVIDENCE"
grep -Fq 'PropagationSourceStatusService.project(nowUtcMillis)' "$EVIDENCE"
echo "CP-0008L propagation source-status presentation host gate: PASS"
