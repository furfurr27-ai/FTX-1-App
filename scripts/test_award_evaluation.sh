#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
TEST_JAR="$BUILD/cp0006a-award-evaluation-tests.jar"

echo "[1/4] Compile FieldOps core main once"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[2/4] Compile CP-0006A award tests separately"
kotlinc   "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/AwardEvaluationTests.kt"   -cp "$MAIN_JAR"   -include-runtime   -d "$TEST_JAR"

echo "[3/4] Run provider-independent synthetic award gate"
java -cp "$TEST_JAR:$MAIN_JAR" dev.n0png.fieldops.core.AwardEvaluationTests

echo "[4/4] Enforce CP-0006A contains no guessed official award catalog"
! grep -Eiq 'https?://|\bARRL\b|\bDXCC\b|\bWAS\b|\bWAZ\b|\bIOTA\b|\bPOTA\b|\bSOTA\b'   "$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/awards/AwardDomain.kt" || {
  echo "CP-0006A must remain provider-independent and synthetic; official award rules belong to CP-0006B" >&2
  exit 1
}

echo "CP-0006A award evaluation host gate: PASS"
