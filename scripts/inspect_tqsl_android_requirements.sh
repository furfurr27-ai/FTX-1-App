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


echo "=== tqsllib runtime directory implementation ==="
sed -n '180,440p' "$ROOT/src/tqsllib.cpp"

echo "=== config loading implementation ==="
sed -n '450,525p' "$ROOT/src/location.cpp"


echo "=== station-location/public import APIs ==="
grep -nE 'StationLocation|LocationCapture|setLocation|import.*(PKCS12|Certificate|Backup|Station)|export.*Station|restore|backup'   "$ROOT/src/tqsllib.h" "$ROOT/src/location.h" "$ROOT/src/location.cpp" "$ROOT/src/openssl_cert.h" "$ROOT/src/openssl_cert.cpp"   | head -500 || true


echo "=== location config field names ==="
grep -niE '<page|<field|fieldname=|CALL|DXCC|GRIDSQUARE|CQZ|ITUZ|STATE|CNTY|COUNTRY' "$ROOT/src/config.xml"   | head -350 || true


echo "=== config.xml first 600 lines ==="
sed -n '1,600p' "$ROOT/src/config.xml"


echo "=== TQSL backup writer format ==="
grep -RIn -C 8 -E 'RootCert|CACert|UserCert|PrivateKey|StationDataFile|Locations|backup.*xml|Backup'   "$ROOT/apps" "$ROOT/src" 2>/dev/null | head -900 || true


echo "=== exact BackupConfig/Restore implementation ==="
grep -nE 'TQSLApp::(BackupConfig|AutoBackup|Restore)|BackupConfig\(|Restore.*Config|TQSL_Configuration|RootCert|CACert|UserCert|PrivateKey|StationDataFile'   "$ROOT/apps/tqsl.cpp" | head -300 || true

backup_line="$(grep -n 'TQSLApp::BackupConfig' "$ROOT/apps/tqsl.cpp" | head -n1 | cut -d: -f1 || true)"
if [[ -n "$backup_line" ]]; then
  start=$(( backup_line > 80 ? backup_line - 80 : 1 ))
  end=$(( backup_line + 520 ))
  sed -n "${start},${end}p" "$ROOT/apps/tqsl.cpp"
fi

restore_line="$(grep -nE 'TQSLApp::.*Restore|Restore.*Config' "$ROOT/apps/tqsl.cpp" | head -n1 | cut -d: -f1 || true)"
if [[ -n "$restore_line" ]]; then
  start=$(( restore_line > 80 ? restore_line - 80 : 1 ))
  end=$(( restore_line + 520 ))
  sed -n "${start},${end}p" "$ROOT/apps/tqsl.cpp"
fi


echo "=== exact restore parser lines 5520-5745 ==="
sed -n '5520,5745p' "$ROOT/apps/tqsl.cpp"

echo "=== exact ParseLocations implementation ==="
parse_line="$(grep -n 'TQSLConfig::ParseLocations' "$ROOT/apps/tqsl.cpp" | head -n1 | cut -d: -f1 || true)"
if [[ -n "$parse_line" ]]; then
  start=$(( parse_line > 40 ? parse_line - 40 : 1 ))
  end=$(( parse_line + 340 ))
  sed -n "${start},${end}p" "$ROOT/apps/tqsl.cpp"
fi
