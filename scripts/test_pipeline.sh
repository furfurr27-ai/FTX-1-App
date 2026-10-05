#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$ROOT/core/build-test-v3"
rm -rf "$BUILD"
mkdir -p "$BUILD"
mapfile -t MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
mapfile -t TEST < <(find "$ROOT/core/src/test/kotlin" -name '*.kt' | sort)
kotlinc "${MAIN[@]}" "${TEST[@]}" -include-runtime -d "$BUILD/fieldops-pipeline-tests.jar"
java -jar "$BUILD/fieldops-pipeline-tests.jar"

SO="$ROOT/android/app/src/main/jniLibs/arm64-v8a/libft8af.so"
SYMS="$BUILD/libft8af-symbols.txt"
nm -D --defined-only "$SO" > "$SYMS"
for sym in \
  Java_com_k1af_ft8af_ft8listener_FT8SignalListener_InitDecoder \
  Java_com_k1af_ft8af_ft8listener_FT8SignalListener_InitDecoderFt2 \
  Java_com_k1af_ft8af_ft8transmit_GenerateFT8_ft8_1encode \
  Java_com_k1af_ft8af_ft8transmit_GenerateFT8_ft4_1encode \
  Java_com_k1af_ft8af_ft8transmit_GenerateFT8_synth_1gfsk; do
  grep -Fq "$sym" "$SYMS" || { echo "missing JNI symbol: $sym" >&2; exit 1; }
done
echo "ARM64 FT-family JNI ABI: required symbols present"

# Host-side compile check for the ABI shims and platform-neutral DSP adapters.
# (The ARM64 .so itself cannot be loaded on this x86_64 host.)
ADAPTER_BUILD="$BUILD/adapter-check"
mkdir -p "$ADAPTER_BUILD/java" "$ADAPTER_BUILD/kotlin"
mapfile -t ABI_JAVA < <(find "$ROOT/android/compat/src/main/java" -name '*.java' | sort)
javac -d "$ADAPTER_BUILD/java" "${ABI_JAVA[@]}"
mapfile -t DSP_KT < <(find "$ROOT/android/pipeline/src/main/kotlin/dev/n0png/fieldops/android/dsp" -name '*.kt' | sort)
kotlinc "${MAIN[@]}" "${DSP_KT[@]}" -cp "$ADAPTER_BUILD/java" -d "$ADAPTER_BUILD/kotlin"
echo "JNI shims + FT/JS8/WSPR DSP adapter sources: compile PASS"
