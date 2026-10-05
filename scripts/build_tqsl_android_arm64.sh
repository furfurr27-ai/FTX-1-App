#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_ROOT="${BUILD_ROOT:-$ROOT/.build/cp0003c-android-tqsl}"
TQSL_VERSION="2.8.6"
TQSL_SHA256="182e5f2ac35a3db8b409b45d96505e6bd265ae4668ed064754209c4b8e7bdf37"
TQSL_URL="https://downloads.sourceforge.net/project/trustedqsl/tqsl-${TQSL_VERSION}.tar.gz"
VCPKG_COMMIT="19780d9cdf84d0944cf9a318666703b89ab6629c"
NDK_VERSION="${NDK_VERSION:-27.2.12479018}"
ANDROID_API="${ANDROID_API:-26}"

rm -rf "$BUILD_ROOT"
mkdir -p "$BUILD_ROOT"

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$ANDROID_SDK_ROOT" ]]; then
  echo "ANDROID_SDK_ROOT/ANDROID_HOME is required" >&2
  exit 1
fi

SDKMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$SDKMANAGER" ]]; then
  SDKMANAGER="$(command -v sdkmanager || true)"
fi
if [[ -z "$SDKMANAGER" || ! -x "$SDKMANAGER" ]]; then
  echo "sdkmanager not found" >&2
  exit 1
fi

yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
"$SDKMANAGER" "ndk;$NDK_VERSION" >/dev/null
export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/$NDK_VERSION"
export ANDROID_NDK_ROOT="$ANDROID_NDK_HOME"

if [[ ! -f "$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" ]]; then
  echo "Android NDK toolchain not found: $ANDROID_NDK_HOME" >&2
  exit 1
fi

echo "[1/8] Fetch and verify official TrustedQSL $TQSL_VERSION"
curl -fL --retry 3 --retry-delay 2 -o "$BUILD_ROOT/tqsl.tar.gz" "$TQSL_URL"
echo "$TQSL_SHA256  $BUILD_ROOT/tqsl.tar.gz" | sha256sum -c -
mkdir -p "$BUILD_ROOT/upstream"
tar -xzf "$BUILD_ROOT/tqsl.tar.gz" -C "$BUILD_ROOT/upstream"
TQSL_ROOT="$(find "$BUILD_ROOT/upstream" -mindepth 1 -maxdepth 1 -type d | head -n1)"
test -f "$TQSL_ROOT/src/tqsllib.cpp"
test -f "$TQSL_ROOT/src/config.xml"

echo "[2/8] Pin vcpkg dependency toolchain"
git clone --quiet https://github.com/microsoft/vcpkg.git "$BUILD_ROOT/vcpkg"
git -C "$BUILD_ROOT/vcpkg" checkout --quiet "$VCPKG_COMMIT"
"$BUILD_ROOT/vcpkg/bootstrap-vcpkg.sh" -disableMetrics >/dev/null

echo "[3/8] Cross-build static OpenSSL/Expat/SQLite/Zlib for arm64-android"
"$BUILD_ROOT/vcpkg/vcpkg" install openssl expat sqlite3 zlib --triplet arm64-android --clean-after-build

VCPKG_ANDROID_ROOT="$BUILD_ROOT/vcpkg/installed/arm64-android"
test -f "$VCPKG_ANDROID_ROOT/include/openssl/opensslv.h"
test -f "$VCPKG_ANDROID_ROOT/lib/libcrypto.a"
test -f "$VCPKG_ANDROID_ROOT/lib/libexpat.a"
test -f "$VCPKG_ANDROID_ROOT/lib/libsqlite3.a"
test -f "$VCPKG_ANDROID_ROOT/lib/libz.a"

echo "[4/8] Configure production TrustedQSL + FieldOps JNI for arm64-v8a"
cmake_args=(
  -S "$ROOT/native/tqsl/android"
  -B "$BUILD_ROOT/cmake"
  -G Ninja
  -DCMAKE_BUILD_TYPE=Release
  -DCMAKE_TOOLCHAIN_FILE="$BUILD_ROOT/vcpkg/scripts/buildsystems/vcpkg.cmake"
  -DVCPKG_CHAINLOAD_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake"
  -DVCPKG_TARGET_TRIPLET=arm64-android
  -DCMAKE_PREFIX_PATH="$VCPKG_ANDROID_ROOT"
  -DOPENSSL_ROOT_DIR="$VCPKG_ANDROID_ROOT"
  -DOPENSSL_USE_STATIC_LIBS=TRUE
  -DZLIB_ROOT="$VCPKG_ANDROID_ROOT"
  -DANDROID_ABI=arm64-v8a
  -DANDROID_PLATFORM="android-$ANDROID_API"
  -DANDROID_STL=c++_static
  -DTQSL_SOURCE_DIR="$TQSL_ROOT/src"
)
cmake "${cmake_args[@]}"

echo "[5/8] Build libfieldops_tqsl.so"
cmake --build "$BUILD_ROOT/cmake" --target fieldops_tqsl --parallel 2
SO="$(find "$BUILD_ROOT/cmake" -name 'libfieldops_tqsl.so' -type f | head -n1)"
test -n "$SO"

echo "[6/8] Verify ELF ABI, JNI exports, and dependency closure"
READELF="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
NM="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-nm"

"$READELF" -h "$SO" | tee "$BUILD_ROOT/elf-header.txt"
grep -Eq 'Machine:[[:space:]]+AArch64' "$BUILD_ROOT/elf-header.txt"

"$NM" -D --defined-only "$SO" > "$BUILD_ROOT/jni-symbols.txt"
symbols=(
  Java_dev_n0png_fieldops_android_logbook_TrustedQslJniBridge_nativeInitialize
  Java_dev_n0png_fieldops_android_logbook_TrustedQslJniBridge_nativeImportPkcs12
  Java_dev_n0png_fieldops_android_logbook_TrustedQslJniBridge_nativeBeginSigning
  Java_dev_n0png_fieldops_android_logbook_TrustedQslJniBridge_nativeGetPayload
  Java_dev_n0png_fieldops_android_logbook_TrustedQslJniBridge_nativeCommit
  Java_dev_n0png_fieldops_android_logbook_TrustedQslJniBridge_nativeRollback
  Java_dev_n0png_fieldops_android_logbook_TrustedQslJniBridge_nativeClose
)
for sym in "${symbols[@]}"; do
  grep -Fq "$sym" "$BUILD_ROOT/jni-symbols.txt" || {
    echo "missing JNI export: $sym" >&2
    exit 1
  }
done

"$READELF" -d "$SO" | tee "$BUILD_ROOT/dynamic.txt"
if grep -E 'NEEDED.*(ssl|crypto|expat|sqlite|zlib)' "$BUILD_ROOT/dynamic.txt"; then
  echo "Third-party dependency escaped static linkage" >&2
  exit 1
fi

echo "[7/8] Package reproducible arm64-v8a runtime bundle"
OUT="$BUILD_ROOT/package"
mkdir -p "$OUT/jniLibs/arm64-v8a" "$OUT/trustedqsl"
cp "$SO" "$OUT/jniLibs/arm64-v8a/libfieldops_tqsl.so"
cp "$TQSL_ROOT/src/config.xml" "$OUT/trustedqsl/config.xml"
cp "$TQSL_ROOT/src/LICENSE" "$OUT/trustedqsl/TRUSTEDQSL_LICENSE.txt"

cat > "$OUT/BUILD_METADATA.txt" <<META
checkpoint=CP-0003C-WIP
trustedqsl_version=$TQSL_VERSION
trustedqsl_archive_sha256=$TQSL_SHA256
vcpkg_commit=$VCPKG_COMMIT
openssl=3.6.5
expat=2.8.5
sqlite3=3.53.4#2
zlib=1.3.2#2
android_abi=arm64-v8a
android_api=$ANDROID_API
android_ndk=$NDK_VERSION
automatic_lotw_upload=DISABLED
META

(
  cd "$OUT"
  find . -type f -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS
)
(
  cd "$OUT"
  zip -X -r "$BUILD_ROOT/fieldops-tqsl-android-arm64-v8a.zip" . >/dev/null
)
sha256sum "$BUILD_ROOT/fieldops-tqsl-android-arm64-v8a.zip" | tee "$BUILD_ROOT/package.sha256"

echo "[8/8] CP-0003C Android TrustedQSL package build: PASS"
echo "PACKAGE=$BUILD_ROOT/fieldops-tqsl-android-arm64-v8a.zip"
