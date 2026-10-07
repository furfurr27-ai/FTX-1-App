#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
TEST_JAR="$BUILD/cp0008g-refresh-tests.jar"
MODELS="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationRefreshModels.kt"
COORDINATOR="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationRefreshCoordinator.kt"
TEST="$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/PropagationRefreshCoordinatorTests.kt"
EVIDENCE="$ROOT/research/propagation/CP-0008G_PROPAGATION_REFRESH_COORDINATOR.md"

echo "[1/7] Compile FieldOps core main"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[2/7] Compile CP-0008G tests separately"
kotlinc "$TEST" -cp "$MAIN_JAR" -include-runtime -d "$TEST_JAR"

echo "[3/7] Run deterministic refresh coordinator tests"
java -cp "$TEST_JAR:$MAIN_JAR" dev.n0png.fieldops.core.PropagationRefreshCoordinatorTests

echo "[4/7] Enforce source state and bounded retry contract"
grep -Fq 'data class PropagationRefreshSourceState' "$MODELS"
grep -Fq 'nextEligibleRefreshUtcMillis' "$MODELS"
grep -Fq 'consecutiveFailures' "$MODELS"
grep -Fq 'maximumRetryBackoffMillis' "$MODELS"
grep -Fq 'MAX_TRACKED_CONSECUTIVE_FAILURES' "$COORDINATOR"
grep -Fq 'MAX_BACKOFF_DOUBLINGS' "$COORDINATOR"

echo "[5/7] Enforce canonical NOAA and last-good cache behavior"
grep -Fq 'NOAA_SWPC_KP_FORECAST_OBSERVED' "$COORDINATOR"
grep -Fq 'carryForwardUnrefreshed' "$COORDINATOR"
grep -Fq 'latestBefore' "$COORDINATOR"
grep -Fq 'aggregationFailure' "$COORDINATOR"
grep -Fq 'snapshotStore.save(snapshot)' "$COORDINATOR"

echo "[6/7] Enforce platform/network/account separation"
! grep -Eq 'android\.|androidx\.|WorkManager|OkHttp|Retrofit|HttpURLConnection|openConnection|java\.net|UsbManager|Ftx1|RadioSession|PTT|password|credential|apiKey' "$MODELS" "$COORDINATOR" || {
  echo "CP-0008G must remain platform/network/account/radio independent" >&2
  exit 1
}

echo "[7/7] Verify evidence boundary and owner override"
grep -Fq 'canonical NOAA Kp' "$EVIDENCE"
grep -Fq 'last good snapshot' "$EVIDENCE"
grep -Fq 'CP-0003C remains **DEFERRED**' "$EVIDENCE"

echo "CP-0008G propagation refresh coordinator host gate: PASS"
