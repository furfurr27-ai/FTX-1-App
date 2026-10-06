#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
TEST_JAR="$BUILD/cp0005b-lotw-queue-tests.jar"

echo "[1/4] Compile FieldOps core main once"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[2/4] Compile CP-0005B queue tests separately"
kotlinc   "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/ManualQsoLotwQueueTests.kt"   -cp "$MAIN_JAR"   -include-runtime   -d "$TEST_JAR"

echo "[3/4] Run logger-save -> local LoTW queue gate"
java -cp "$TEST_JAR:$MAIN_JAR" dev.n0png.fieldops.core.ManualQsoLotwQueueTests

echo "[4/4] Enforce logger path remains network-free"
! grep -Eq 'LotwTransport|uploadTransactional|uploadTq8|HttpURLConnection'   "$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/logbook/QsoDomain.kt"   "$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/logbook/LotwLoggerPolicy.kt" || {
  echo "CP-0005B logger-save path must not own LoTW network transport" >&2
  exit 1
}

echo "CP-0005B manual-QSO LoTW queue host gate: PASS"
