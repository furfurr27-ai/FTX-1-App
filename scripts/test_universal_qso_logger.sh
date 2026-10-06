#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
TEST_JAR="$BUILD/universal-qso-logger-tests.jar"

echo "[1/3] Compile FieldOps core main once"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[2/3] Compile CP-0005A logger tests separately"
kotlinc   "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/UniversalQsoLoggerTests.kt"   -cp "$MAIN_JAR"   -include-runtime   -d "$TEST_JAR"

echo "[3/3] Run universal QSO/manual/digital logger gate"
java -cp "$TEST_JAR:$MAIN_JAR" dev.n0png.fieldops.core.UniversalQsoLoggerTests

echo "CP-0005A universal QSO + fast logger host gate: PASS"
