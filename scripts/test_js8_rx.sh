#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
ABI_DIR="$BUILD/ft8af-abi"
JS8_API_DIR="$BUILD/js8-api"
DSP_DIR="$BUILD/dsp"
TEST_JAR="$BUILD/js8-rx-tests.jar"

echo "[1/5] Compile FieldOps core main once"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[2/5] Compile existing FT-family Java ABI shims"
mkdir -p "$ABI_DIR"
mapfile -t ABI_JAVA < <(find "$ROOT/android/compat/src/main/java" -name '*.java' | sort)
javac -d "$ABI_DIR" "${ABI_JAVA[@]}"

echo "[3/5] Compile pinned JS8 Kotlin API mirror and production DSP slice"
mkdir -p "$JS8_API_DIR" "$DSP_DIR"
kotlinc   "$ROOT/android/pipeline/src/test/kotlin/com/js8call/core/JS8Engine.kt"   -d "$JS8_API_DIR"

kotlinc   "$ROOT/android/pipeline/src/main/kotlin/dev/n0png/fieldops/android/dsp/FtFamilyNativeEngine.kt"   "$ROOT/android/pipeline/src/main/kotlin/dev/n0png/fieldops/android/dsp/WsprEngineAdapter.kt"   "$ROOT/android/pipeline/src/main/kotlin/dev/n0png/fieldops/android/dsp/Js8EngineAdapter.kt"   "$ROOT/android/pipeline/src/main/kotlin/dev/n0png/fieldops/android/dsp/Js8CallAndroidEngineFactory.kt"   "$ROOT/android/pipeline/src/main/kotlin/dev/n0png/fieldops/android/dsp/FieldOpsDspStack.kt"   -cp "$MAIN_JAR:$ABI_DIR:$JS8_API_DIR"   -d "$DSP_DIR"

echo "[4/5] Compile deterministic JS8 RX tests separately"
kotlinc   "$ROOT/android/pipeline/src/test/kotlin/dev/n0png/fieldops/android/dsp/Js8RxTests.kt"   -cp "$MAIN_JAR:$DSP_DIR:$JS8_API_DIR:$ABI_DIR"   -include-runtime   -d "$TEST_JAR"

echo "[5/5] Run deterministic JS8 RX tests"
java -cp "$TEST_JAR:$MAIN_JAR:$DSP_DIR:$JS8_API_DIR:$ABI_DIR"   dev.n0png.fieldops.android.dsp.Js8RxTests

echo "CP-0002A host compile/test gate: PASS"
