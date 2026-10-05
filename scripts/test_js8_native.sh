#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
ABI_DIR="$BUILD/ft8af-abi"
JS8_API_DIR="$BUILD/js8-api"
DSP_DIR="$BUILD/dsp"
RX_TEST_JAR="$BUILD/js8-rx-tests.jar"
TX_TEST_JAR="$BUILD/js8-tx-tests.jar"
CORE_TEST_JAR="$BUILD/core-tests.jar"

echo "[1/7] Compile FieldOps core main once"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[2/7] Compile core tests separately against proven main"
mapfile -t CORE_TESTS < <(find "$ROOT/core/src/test/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_TESTS[@]}" -cp "$MAIN_JAR" -include-runtime -d "$CORE_TEST_JAR"
java -cp "$CORE_TEST_JAR:$MAIN_JAR" dev.n0png.fieldops.core.RunTestsKt

echo "[3/7] Compile existing FT-family Java ABI shims"
mkdir -p "$ABI_DIR"
mapfile -t ABI_JAVA < <(find "$ROOT/android/compat/src/main/java" -name '*.java' | sort)
javac -d "$ABI_DIR" "${ABI_JAVA[@]}"

echo "[4/7] Compile pinned JS8 API mirror and production DSP slice"
mkdir -p "$JS8_API_DIR" "$DSP_DIR"
kotlinc "$ROOT/android/pipeline/src/test/kotlin/com/js8call/core/JS8Engine.kt" -d "$JS8_API_DIR"

kotlinc   "$ROOT/android/pipeline/src/main/kotlin/dev/n0png/fieldops/android/dsp/FtFamilyNativeEngine.kt"   "$ROOT/android/pipeline/src/main/kotlin/dev/n0png/fieldops/android/dsp/WsprRxFrontEnd.kt"   "$ROOT/android/pipeline/src/main/kotlin/dev/n0png/fieldops/android/dsp/WsprTxWaveformSynthesizer.kt" \
  "$ROOT/android/pipeline/src/main/kotlin/dev/n0png/fieldops/android/dsp/WsprEngineAdapter.kt"   "$ROOT/android/pipeline/src/main/kotlin/dev/n0png/fieldops/android/dsp/Js8EngineAdapter.kt"   "$ROOT/android/pipeline/src/main/kotlin/dev/n0png/fieldops/android/dsp/Js8CallAndroidEngineFactory.kt"   "$ROOT/android/pipeline/src/main/kotlin/dev/n0png/fieldops/android/dsp/Js8TxController.kt"   "$ROOT/android/pipeline/src/main/kotlin/dev/n0png/fieldops/android/dsp/FieldOpsDspStack.kt"   -cp "$MAIN_JAR:$ABI_DIR:$JS8_API_DIR"   -d "$DSP_DIR"

echo "[5/7] Compile JS8 RX and TX tests separately"
kotlinc   "$ROOT/android/pipeline/src/test/kotlin/dev/n0png/fieldops/android/dsp/Js8RxTests.kt"   -cp "$MAIN_JAR:$DSP_DIR:$JS8_API_DIR:$ABI_DIR"   -include-runtime   -d "$RX_TEST_JAR"

kotlinc   "$ROOT/android/pipeline/src/test/kotlin/dev/n0png/fieldops/android/dsp/Js8TxTests.kt"   -cp "$MAIN_JAR:$DSP_DIR:$JS8_API_DIR:$ABI_DIR"   -include-runtime   -d "$TX_TEST_JAR"

echo "[6/7] Run JS8 RX suite"
java -cp "$RX_TEST_JAR:$MAIN_JAR:$DSP_DIR:$JS8_API_DIR:$ABI_DIR"   dev.n0png.fieldops.android.dsp.Js8RxTests

echo "[7/7] Run JS8 TX suite"
java -cp "$TX_TEST_JAR:$MAIN_JAR:$DSP_DIR:$JS8_API_DIR:$ABI_DIR"   dev.n0png.fieldops.android.dsp.Js8TxTests

echo "CP-0002B host compile/test gate: PASS"
