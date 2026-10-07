#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
TEST_JAR="$BUILD/cp0008e-aggregation-tests.jar"
AGG="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationAggregation.kt"
STORE="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/FilePropagationSnapshotStore.kt"
TEST="$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/PropagationAggregationOfflineCacheTests.kt"
EVIDENCE="$ROOT/research/propagation/CP-0008E_PROPAGATION_AGGREGATION_OFFLINE_CACHE.md"

echo "[1/7] Compile FieldOps core main"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[2/7] Compile CP-0008E tests separately"
kotlinc "$TEST" -cp "$MAIN_JAR" -include-runtime -d "$TEST_JAR"

echo "[3/7] Run deterministic aggregation/offline-cache tests"
java -cp "$TEST_JAR:$MAIN_JAR" dev.n0png.fieldops.core.PropagationAggregationOfflineCacheTests "$ROOT"

echo "[4/7] Enforce source-specific freshness and deterministic dedup"
grep -Fq 'object PropagationSourceFreshnessDefaults' "$AGG"
grep -Fq 'PSK_REPORTER_PUBLIC_QUERY' "$AGG"
grep -Fq 'NOAA_SWPC_GLOTEC_VTEC' "$AGG"
grep -Fq 'NOAA_SWPC_PLANETARY_KP' "$AGG"
grep -Fq 'NOAA_SWPC_F107_SUMMARY' "$AGG"
grep -Fq 'reportCount = maxOf(previous.reportCount, item.reportCount)' "$AGG"
grep -Fq 'Conflicting heard-path evidence' "$AGG"
grep -Fq 'capture UTC cannot precede latest source retrieval UTC' "$AGG"

echo "[5/7] Enforce bounded offline persistence and fail-closed codec"
grep -Fq 'class FilePropagationSnapshotStore' "$STORE"
grep -Fq 'maxSnapshots' "$STORE"
grep -Fq 'propagation-snapshots-v1.bin' "$STORE"
grep -Fq 'ATOMIC_MOVE' "$STORE"
grep -Fq 'Unsupported propagation cache version' "$STORE"
grep -Fq 'Trailing bytes after propagation cache payload' "$STORE"
grep -Fq 'MAX_COLLECTION' "$STORE"
grep -Fq 'MAX_STRING_BYTES' "$STORE"

echo "[6/7] Enforce transport/platform/QSO separation"
! grep -Eq 'HttpURLConnection|OkHttp|Retrofit|ktor|openConnection|android\.|androidx\.|GoogleMap|Mapbox|UsbManager|Ftx1|RadioSession|PTT|TxController|QsoRecord|DigitalCompletedContact|Lotw|enqueue' "$AGG" "$STORE" || {
  echo "CP-0008E aggregation/cache must remain transport/platform/QSO/hardware independent" >&2
  exit 1
}

echo "[7/7] Verify evidence boundary and owner override"
grep -Fq 'Aggregation does not create any new:' "$EVIDENCE"
grep -Fq 'context-only aggregated snapshot remains `UNKNOWN`' "$EVIDENCE"
grep -Fq 'CP-0003C remains **DEFERRED**' "$EVIDENCE"

echo "CP-0008E propagation aggregation/offline cache host gate: PASS"
