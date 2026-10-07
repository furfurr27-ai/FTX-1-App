#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
TEST_JAR="$BUILD/cp0008f-projection-tests.jar"
MODELS="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationProjectionModels.kt"
SERVICE="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationProjectionService.kt"
STORE="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/FilePropagationSnapshotStore.kt"
TEST="$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/PropagationProjectionServiceTests.kt"
EVIDENCE="$ROOT/research/propagation/CP-0008F_PROPAGATION_PROJECTION.md"

echo "[1/7] Compile FieldOps core main"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[2/7] Compile CP-0008F tests separately"
kotlinc "$TEST" -cp "$MAIN_JAR" -include-runtime -d "$TEST_JAR"

echo "[3/7] Run deterministic projection tests"
java -cp "$TEST_JAR:$MAIN_JAR" dev.n0png.fieldops.core.PropagationProjectionServiceTests

echo "[4/7] Enforce explicit projection categories and filters"
grep -Fq 'data class HeardPathProjection' "$MODELS"
grep -Fq 'data class IonosphericProjection' "$MODELS"
grep -Fq 'data class SolarGeomagneticProjection' "$MODELS"
grep -Fq 'data class ModeledPathProjection' "$MODELS"
grep -Fq 'minimumFrequencyHz' "$MODELS"
grep -Fq 'maximumFrequencyHz' "$MODELS"
grep -Fq 'sourceIds' "$MODELS"
grep -Fq 'freshness' "$MODELS"

echo "[5/7] Enforce source-aware freshness and offline status"
grep -Fq 'PropagationSourceFreshnessDefaults.classify' "$SERVICE"
grep -Fq 'offlineCacheAvailable = store is OfflinePropagationSnapshotStore' "$SERVICE"
grep -Fq ': OfflinePropagationSnapshotStore' "$STORE"
grep -Fq 'snapshotIsFutureDated' "$MODELS"
grep -Fq 'retrievalIsFutureDated' "$MODELS"

echo "[6/7] Enforce platform/QSO/rendering separation"
! grep -Eq 'android\.|androidx\.|GoogleMap|Mapbox|OkHttp|Retrofit|HttpURLConnection|openConnection|UsbManager|Ftx1|RadioSession|PTT|TxController|QsoRecord|Lotw|heatScore|heatmapScore' "$MODELS" "$SERVICE" || {
  echo "CP-0008F projection must remain platform/rendering/QSO/radio independent" >&2
  exit 1
}

echo "[7/7] Verify evidence boundary and owner override"
grep -Fq 'No heard path is promoted into a QSO' "$EVIDENCE"
grep -Fq 'VTEC/TECU remains VTEC/TECU' "$EVIDENCE"
grep -Fq 'CP-0003C remains **DEFERRED**' "$EVIDENCE"

echo "CP-0008F propagation projection host gate: PASS"
