#!/usr/bin/env bash
set -euo pipefail

TQSL_VERSION="2.8.6"
TQSL_SHA256="182e5f2ac35a3db8b409b45d96505e6bd265ae4668ed064754209c4b8e7bdf37"
TQSL_URL="https://downloads.sourceforge.net/project/trustedqsl/tqsl-${TQSL_VERSION}.tar.gz"

BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

curl -fL --retry 3 --retry-delay 2 -o "$BUILD/tqsl.tar.gz" "$TQSL_URL"
echo "$TQSL_SHA256  $BUILD/tqsl.tar.gz" | sha256sum -c -
tar -xzf "$BUILD/tqsl.tar.gz" -C "$BUILD"

ROOT="$(find "$BUILD" -mindepth 1 -maxdepth 1 -type d | head -n1)"
echo "ROOT=$ROOT"

echo "=== top-level files ==="
find "$ROOT" -maxdepth 2 -type f | sed "s#^$ROOT/##" | sort | head -250

echo "=== CMake dependency lines ==="
grep -RInE 'find_package\(|find_library\(|OPENSSL|EXPAT|BDB|Berkeley|CURL|ZLIB|WX|SQLITE|sqlite|CMAKE_SYSTEM_NAME|ANDROID'   "$ROOT"/CMakeLists.txt "$ROOT"/src "$ROOT"/cmake 2>/dev/null | head -400 || true

echo "=== tqsllib target context ==="
grep -RInE 'add_library\(|tqsllib|target_link_libraries\(|target_include_directories\('   "$ROOT"/CMakeLists.txt "$ROOT"/src 2>/dev/null | head -500 || true

echo "=== source includes ==="
grep -RhoE '^#include [<"][^>"]+[>"]' "$ROOT"/src/*.{c,cc,cpp,cxx,h,hpp} 2>/dev/null | sort -u | head -300 || true

echo "=== source files ==="
find "$ROOT/src" -maxdepth 1 -type f | sed "s#^$ROOT/src/##" | sort
