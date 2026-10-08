#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
TEST_JAR="$BUILD/cp0008j-runtime-tests.jar"

RUNTIME="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationRuntime.kt"
TEST="$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/PropagationRuntimeTests.kt"
EVIDENCE="$ROOT/research/propagation/CP-0008J_PROPAGATION_RUNTIME_COMPOSITION.md"

echo "[1/8] Compile FieldOps core main"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[2/8] Compile CP-0008J tests separately"
kotlinc "$TEST" -cp "$MAIN_JAR" -include-runtime -d "$TEST_JAR"

echo "[3/8] Run deterministic propagation runtime tests"
java -cp "$TEST_JAR:$MAIN_JAR" dev.n0png.fieldops.core.PropagationRuntimeTests

echo "[4/8] Enforce five-source composition"
grep -Fq 'noaaPlanetaryKp(' "$RUNTIME"
grep -Fq 'noaaPlanetaryKpForecast(' "$RUNTIME"
grep -Fq 'noaaF107(' "$RUNTIME"
grep -Fq 'noaaGlotec(' "$RUNTIME"
grep -Fq 'pskReporter(' "$RUNTIME"

echo "[5/8] Enforce provider-safe default cadence boundaries"
grep -Fq 'EXPECTED_CADENCE_MINUTES' "$RUNTIME"
grep -Fq 'MIN_RETRIEVAL_INTERVAL_MILLIS' "$RUNTIME"
grep -Fq 'Runtime GloTEC cadence must respect' "$RUNTIME"
grep -Fq 'Runtime PSK Reporter cadence must respect' "$RUNTIME"

echo "[6/8] Enforce runtime refresh/cache/projection composition"
grep -Fq 'PropagationSourceRefreshCoordinator' "$RUNTIME"
grep -Fq 'PropagationWorkspaceProjectionService' "$RUNTIME"
grep -Fq 'PropagationRefreshWorkspaceService' "$RUNTIME"
grep -Fq 'refreshAndProject' "$RUNTIME"
grep -Fq 'InMemoryPropagationSnapshotStore' "$RUNTIME"
grep -Fq 'InMemoryPropagationRefreshStateStore' "$RUNTIME"

echo "[7/8] Enforce identity/geography/platform boundary"
! grep -Eq 'latitude|longitude|maidenhead|WorkManager|android\.|androidx\.|UsbManager|Ftx1|RadioSession|PTT|apiKey|password|credential|PKCS|privateKey'   "$RUNTIME" || {
    echo "CP-0008J runtime must not infer geography or include Android/account/radio state" >&2
    exit 1
  }

echo "[8/8] Verify evidence boundary and owner override"
grep -Fq 'platform-neutral runtime composition' "$EVIDENCE"
grep -Fq 'Manual refresh-and-project entry point' "$EVIDENCE"
grep -Fq 'CP-0003C remains **DEFERRED**' "$EVIDENCE"

echo "CP-0008J propagation runtime composition host gate: PASS"
