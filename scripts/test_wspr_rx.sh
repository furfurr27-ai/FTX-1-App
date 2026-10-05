#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
CORE_TEST_JAR="$BUILD/core-tests.jar"
DSP_DIR="$BUILD/wspr-dsp"
WSPR_TEST_JAR="$BUILD/wspr-rx-tests.jar"
NATIVE_DIR="$BUILD/native"
mkdir -p "$DSP_DIR" "$NATIVE_DIR"

echo "[1/6] Compile FieldOps core main once"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[2/6] Compile/run inherited core tests separately"
mapfile -t CORE_TESTS < <(find "$ROOT/core/src/test/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_TESTS[@]}" -cp "$MAIN_JAR" -include-runtime -d "$CORE_TEST_JAR"
java -cp "$CORE_TEST_JAR:$MAIN_JAR" dev.n0png.fieldops.core.RunTestsKt

echo "[3/6] Build pinned native WSPR decoder + FieldOps JNI bridge"
gcc -std=gnu11 -O2 -fPIC -shared \
  -I"$JAVA_HOME/include" \
  -I"$JAVA_HOME/include/linux" \
  -I"$ROOT/native/wspr" \
  -I"$ROOT/native/wspr/upstream" \
  "$ROOT/native/wspr/fieldops_wspr_bridge.c" \
  "$ROOT/native/wspr/fieldops_wspr_jni.c" \
  "$ROOT/native/wspr/upstream/wsprd.c" \
  "$ROOT/native/wspr/upstream/wsprsim_utils.c" \
  "$ROOT/native/wspr/upstream/wsprd_utils.c" \
  "$ROOT/native/wspr/upstream/fano.c" \
  "$ROOT/native/wspr/upstream/nhash.c" \
  "$ROOT/native/wspr/upstream/tab.c" \
  -lfftw3f -lm \
  -o "$NATIVE_DIR/libfieldops_wspr.so"

nm -D --defined-only "$NATIVE_DIR/libfieldops_wspr.so" > "$BUILD/wspr-symbols.txt"
for sym in \
  Java_dev_n0png_fieldops_android_dsp_WsprJniBridge_nativeDecode375 \
  Java_dev_n0png_fieldops_android_dsp_WsprJniBridge_nativeEncodeSymbols; do
  grep -Fq "$sym" "$BUILD/wspr-symbols.txt" || { echo "missing WSPR JNI symbol: $sym" >&2; exit 1; }
done

echo "[4/6] Compile production WSPR Kotlin slice"
kotlinc \
  "$ROOT/android/pipeline/src/main/kotlin/dev/n0png/fieldops/android/dsp/WsprRxFrontEnd.kt" \
  "$ROOT/android/pipeline/src/main/kotlin/dev/n0png/fieldops/android/dsp/WsprEngineAdapter.kt" \
  "$ROOT/android/pipeline/src/main/kotlin/dev/n0png/fieldops/android/dsp/WsprJniBridge.kt" \
  -cp "$MAIN_JAR" \
  -d "$DSP_DIR"

echo "[5/6] Compile WSPR RX tests separately"
kotlinc \
  "$ROOT/android/pipeline/src/test/kotlin/dev/n0png/fieldops/android/dsp/WsprRxTests.kt" \
  -cp "$MAIN_JAR:$DSP_DIR" \
  -include-runtime \
  -d "$WSPR_TEST_JAR"

echo "[6/6] Run native encoder -> 12 kHz real -> FieldOps downconverter -> native decoder gate"
LD_LIBRARY_PATH="$NATIVE_DIR${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" \
java -Djava.library.path="$NATIVE_DIR" \
  -cp "$WSPR_TEST_JAR:$MAIN_JAR:$DSP_DIR" \
  dev.n0png.fieldops.android.dsp.WsprRxTests

echo "CP-0002C focused WSPR RX host gate: PASS"
