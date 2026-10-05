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

echo "=== top-level CMakeLists.txt ==="
sed -n '1,320p' "$ROOT/CMakeLists.txt"

echo "=== src/CMakeLists.txt ==="
sed -n '1,360p' "$ROOT/src/CMakeLists.txt"

echo "=== exact dependency calls ==="
grep -InE 'find_package\(|find_library\(|include_directories\(|target_link_libraries\(|add_library\('   "$ROOT/CMakeLists.txt" "$ROOT/src/CMakeLists.txt" || true

echo "=== compile-time platform conditionals in src root ==="
grep -InE '#if|#ifdef|#ifndef|__APPLE__|_WIN32|WIN32|UNIX|ANDROID|__ANDROID__'   "$ROOT/src/"*.{cpp,h} 2>/dev/null | head -350 || true

echo "=== SQLite/OpenSSL/Expat/Zlib references in src root only ==="
grep -InE 'sqlite3|openssl/|expat|zlib|gzopen|deflate|inflate'   "$ROOT/src/"*.{cpp,h} 2>/dev/null | head -350 || true


echo "=== config/data-directory behavior ==="
grep -RIn -C 4 -E 'CONFDIR|config\.xml|tqsl_setDirectory|tQSL_BaseDir' "$ROOT/src"/*.cpp "$ROOT/src"/*.h 2>/dev/null || true
