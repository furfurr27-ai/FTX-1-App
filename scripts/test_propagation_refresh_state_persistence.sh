#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
TEST_JAR="$BUILD/cp0008k-state-tests.jar"
STATE="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/FilePropagationRefreshStateStore.kt"
RUNTIME="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationRuntime.kt"
TEST="$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/PropagationRefreshStatePersistenceTests.kt"
EVIDENCE="$ROOT/research/propagation/CP-0008K_REFRESH_STATE_PERSISTENCE.md"

echo "[1/6] Compile complete core main to avoid truncated-slice false positives"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"
echo "[2/6] Compile deterministic filesystem tests separately"
kotlinc "$TEST" -cp "$MAIN_JAR" -include-runtime -d "$TEST_JAR"
echo "[3/6] Exercise persisted state, failure/backoff, corruption, and replacement"
java -cp "$TEST_JAR:$MAIN_JAR" dev.n0png.fieldops.core.PropagationRefreshStatePersistenceTests
echo "[4/6] Enforce atomic replace, no non-atomic fallback, explicit factory injection"
grep -Fq 'StandardCopyOption.ATOMIC_MOVE' "$STATE"
grep -Fq 'StandardCopyOption.REPLACE_EXISTING' "$STATE"
grep -Fq 'Files.createTempFile' "$STATE"
grep -Fq 'override fun save' "$STATE"
grep -Fq 'refreshStateStore: PropagationRefreshStateStore' "$RUNTIME"
! grep -Eq 'AtomicMoveNotSupportedException|Files.move\(temporary, stateFile\)' "$STATE"
echo "[5/6] Enforce platform/privacy boundary and evidence"
! grep -Eq 'WorkManager|android\.|androidx\.|RadioSession|PTT|PKCS|privateKey|password|credential' "$STATE"
grep -Fq 'CP-0003C remains **DEFERRED**' "$EVIDENCE"
echo "[6/6] Enforce unchanged refresh/runtime foundation"
grep -Fq 'InMemoryPropagationRefreshStateStore()' "$RUNTIME"
grep -Fq 'PropagationSourceRefreshCoordinator' "$RUNTIME"
echo "CP-0008K propagation refresh-state persistence host gate: PASS"
