#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
TEST_JAR="$BUILD/native-mode-composition.jar"

echo "[1/3] Compile FieldOps core main once"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[2/3] Compile shared native-mode composition tests separately"
kotlinc   "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/NativeModeCompositionTests.kt"   -cp "$MAIN_JAR"   -include-runtime   -d "$TEST_JAR"

echo "[3/3] Run shared 48/12 kHz, timing and TX-ownership composition regression"
java -cp "$TEST_JAR:$MAIN_JAR" dev.n0png.fieldops.core.NativeModeCompositionTests

echo "CP-0002E native-mode composition gate: PASS"
