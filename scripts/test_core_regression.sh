#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
TEST_JAR="$BUILD/fieldops-core-tests.jar"

echo "[1/3] Compile FieldOps core main once"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[2/3] Compile inherited core tests separately"
mapfile -t CORE_TESTS < <(find "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core" -maxdepth 1 -name '*.kt' ! -name 'AprsRegressionTests.kt' ! -name 'NativeModeCompositionTests.kt' | sort)
kotlinc "${CORE_TESTS[@]}" -cp "$MAIN_JAR" -include-runtime -d "$TEST_JAR"

echo "[3/3] Run inherited core/pipeline/LoTW suite"
java -cp "$TEST_JAR:$MAIN_JAR" dev.n0png.fieldops.core.RunTestsKt

echo "CP-0002E inherited core regression gate: PASS"
