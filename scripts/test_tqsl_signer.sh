#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

TQSL_VERSION="2.8.6"
TQSL_SHA256="182e5f2ac35a3db8b409b45d96505e6bd265ae4668ed064754209c4b8e7bdf37"
TQSL_URL="https://downloads.sourceforge.net/project/trustedqsl/tqsl-${TQSL_VERSION}.tar.gz"

MAIN_JAR="$BUILD/fieldops-core-main.jar"
SIGNER_DIR="$BUILD/signer"
TEST_JAR="$BUILD/tqsl-signer-tests.jar"
NATIVE_DIR="$BUILD/native"
DATA_DIR="$BUILD/tqsl-data"
mkdir -p "$SIGNER_DIR" "$NATIVE_DIR" "$DATA_DIR"

echo "[1/8] Fetch and verify official TrustedQSL release pin"
curl -fL --retry 3 --retry-delay 2 -o "$BUILD/tqsl.tar.gz" "$TQSL_URL"
echo "$TQSL_SHA256  $BUILD/tqsl.tar.gz" | sha256sum -c -
mkdir -p "$BUILD/upstream"
tar -xzf "$BUILD/tqsl.tar.gz" -C "$BUILD/upstream"
TQSL_SRC="$(find "$BUILD/upstream" -type f -name tqsllib.h -path '*/src/*' -printf '%h\n' | head -n1)"
[[ -n "$TQSL_SRC" && -f "$TQSL_SRC/tqslconvert.h" ]] || {
  echo "official TrustedQSL headers not found in pinned release" >&2
  exit 1
}

echo "[2/8] Compile production JNI bridge against exact official 2.8.6 headers"
g++ -std=c++17 -Wall -Wextra -Werror -fPIC -c   -I"$JAVA_HOME/include"   -I"$JAVA_HOME/include/linux"   -I"$TQSL_SRC"   "$ROOT/native/tqsl/fieldops_tqsl_jni.cpp"   -o "$BUILD/official-api-check.o"

echo "[3/8] Build deterministic host TrustedQSL fixture + production JNI bridge"
g++ -std=c++17 -Wall -Wextra -Werror -fPIC -shared   -I"$JAVA_HOME/include"   -I"$JAVA_HOME/include/linux"   -I"$ROOT/native/tqsl/test_fixture/include"   "$ROOT/native/tqsl/fieldops_tqsl_jni.cpp"   "$ROOT/native/tqsl/test_fixture/fake_tqsl.cpp"   -lz   -o "$NATIVE_DIR/libfieldops_tqsl.so"

nm -D --defined-only "$NATIVE_DIR/libfieldops_tqsl.so" > "$BUILD/jni-symbols.txt"
for sym in   Java_dev_n0png_fieldops_android_logbook_TrustedQslJniBridge_nativeInitialize   Java_dev_n0png_fieldops_android_logbook_TrustedQslJniBridge_nativeImportPkcs12   Java_dev_n0png_fieldops_android_logbook_TrustedQslJniBridge_nativeBeginSigning   Java_dev_n0png_fieldops_android_logbook_TrustedQslJniBridge_nativeGetPayload   Java_dev_n0png_fieldops_android_logbook_TrustedQslJniBridge_nativeCommit   Java_dev_n0png_fieldops_android_logbook_TrustedQslJniBridge_nativeRollback   Java_dev_n0png_fieldops_android_logbook_TrustedQslJniBridge_nativeClose; do
  grep -Fq "$sym" "$BUILD/jni-symbols.txt" || { echo "missing TrustedQSL JNI symbol: $sym" >&2; exit 1; }
done

echo "[4/8] Compile FieldOps core main once"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[5/8] Compile production TrustedQSL Kotlin slice"
kotlinc   "$ROOT/android/pipeline/src/main/kotlin/dev/n0png/fieldops/android/logbook/TrustedQslJniBridge.kt"   "$ROOT/android/pipeline/src/main/kotlin/dev/n0png/fieldops/android/logbook/TrustedQslSigner.kt"   -cp "$MAIN_JAR"   -d "$SIGNER_DIR"

echo "[6/8] Compile signer tests separately"
kotlinc   "$ROOT/android/pipeline/src/test/kotlin/dev/n0png/fieldops/android/logbook/TrustedQslSignerTests.kt"   -cp "$MAIN_JAR:$SIGNER_DIR"   -include-runtime   -d "$TEST_JAR"

echo "[7/8] Run focused TrustedQSL signer bridge tests"
LD_LIBRARY_PATH="$NATIVE_DIR${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" java -Djava.library.path="$NATIVE_DIR"   -cp "$TEST_JAR:$MAIN_JAR:$SIGNER_DIR"   dev.n0png.fieldops.android.logbook.TrustedQslSignerTests "$DATA_DIR"

echo "[8/8] Enforce signer implementation separation from HTTP transport"
! grep -Eq 'LotwTransport|HttpURLConnection|uploadTq8' \
  "$ROOT/android/pipeline/src/main/kotlin/dev/n0png/fieldops/android/logbook/TrustedQslSigner.kt" \
  "$ROOT/android/pipeline/src/main/kotlin/dev/n0png/fieldops/android/logbook/TrustedQslJniBridge.kt" \
  "$ROOT/native/tqsl/fieldops_tqsl_jni.cpp" || {
  echo "TrustedQSL signer implementation must not own HTTP upload" >&2
  exit 1
}

echo "CP-0003A TrustedQSL signer bridge host gate: PASS"
