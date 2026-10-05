#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-}"
case "$MODE" in
  FT8|FT4|FT2) ;;
  *) echo "usage: $0 FT8|FT4|FT2" >&2; exit 2 ;;
esac

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
PROD_ABI="$BUILD/prod-abi"
PROD_ENGINE="$BUILD/prod-engine"
FAKE_ABI="$BUILD/fake-abi"
FAKE_ENGINE="$BUILD/fake-engine"
TEST_JAR="$BUILD/ft-family-tests.jar"
mkdir -p "$PROD_ABI" "$PROD_ENGINE" "$FAKE_ABI" "$FAKE_ENGINE"

echo "[1/7] Compile FieldOps core main once"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[2/7] Compile production FT8AF Java ABI shims"
mapfile -t PROD_JAVA < <(find "$ROOT/android/compat/src/main/java" -name '*.java' | sort)
javac -d "$PROD_ABI" "${PROD_JAVA[@]}"

echo "[3/7] Verify production ABI method surface"
javap -classpath "$PROD_ABI" com.k1af.ft8af.ft8listener.FT8SignalListener > "$BUILD/listener.txt"
javap -classpath "$PROD_ABI" com.k1af.ft8af.ft8transmit.GenerateFT8 > "$BUILD/generator.txt"
for needle in   'native long InitDecoder(long, int, int, boolean)'   'native long InitDecoderFt2(long, int, int, int)'   'native int DecoderFt8FindSync(long)'   'native int DecoderFt2FindSync(long)'; do
  grep -Fq "$needle" "$BUILD/listener.txt" || { echo "missing FT listener ABI: $needle" >&2; exit 1; }
done
for needle in   'native int packFreeTextTo77(java.lang.String, byte[])'   'native void ft8_encode(byte[], byte[])'   'native void ft4_encode(byte[], byte[])'   'native void synth_gfsk(byte[], int, float, float, float, int, float[], int)'; do
  grep -Fq "$needle" "$BUILD/generator.txt" || { echo "missing FT generator ABI: $needle" >&2; exit 1; }
done

echo "[4/7] Compile production FtFamilyNativeEngine against real ABI declarations"
kotlinc   "$ROOT/android/pipeline/src/main/kotlin/dev/n0png/fieldops/android/dsp/FtFamilyNativeEngine.kt"   -cp "$MAIN_JAR:$PROD_ABI"   -d "$PROD_ENGINE"

echo "[5/7] Compile host-only deterministic FT8AF ABI fixture"
javac   -d "$FAKE_ABI"   "$ROOT/android/compat/src/main/java/com/k1af/ft8af/Ft8Message.java"   "$ROOT/android/pipeline/src/test/fixtures/ft8af/com/k1af/ft8af/ft8listener/FT8SignalListener.java"   "$ROOT/android/pipeline/src/test/fixtures/ft8af/com/k1af/ft8af/ft8transmit/GenerateFT8.java"

echo "[6/7] Compile production engine against host fixture plus selected-mode tests"
kotlinc   "$ROOT/android/pipeline/src/main/kotlin/dev/n0png/fieldops/android/dsp/FtFamilyNativeEngine.kt"   -cp "$MAIN_JAR:$FAKE_ABI"   -d "$FAKE_ENGINE"

kotlinc   "$ROOT/android/pipeline/src/test/kotlin/dev/n0png/fieldops/android/dsp/FtFamilyRegressionTests.kt"   -cp "$MAIN_JAR:$FAKE_ABI:$FAKE_ENGINE"   -include-runtime   -d "$TEST_JAR"

echo "[7/7] Run isolated $MODE adapter regression"
java -cp "$TEST_JAR:$MAIN_JAR:$FAKE_ABI:$FAKE_ENGINE"   dev.n0png.fieldops.android.dsp.FtFamilyRegressionTests "$MODE"

echo "CP-0002E $MODE regression gate: PASS"
