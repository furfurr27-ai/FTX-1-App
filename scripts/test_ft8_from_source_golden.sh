#!/usr/bin/env bash
# Real FT-family C source + historical golden vectors, not fake Java JNI.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
native="$root/app/src/main/cpp/third_party/ft8af"
ft8="$native/ft8_lib"
test_src="$root/app/src/test/native/ft8af/test_golden_encode.c"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
"${CC:-cc}" -std=c11 -O2 -D_GNU_SOURCE -Wall -Wno-unused-function \
  -I "$ft8" \
  "$test_src" \
  "$ft8/ft8/pack.c" "$ft8/ft8/encode.c" "$ft8/ft8/crc.c" \
  "$ft8/ft8/constants.c" "$ft8/ft8/text.c" "$ft8/ft8/message.c" \
  -lm -o "$tmp/ft8_golden"
"$tmp/ft8_golden"
# Interface audit: statically check that the JNI GetFieldID lookups from the
# real source match EXACT public field types in the packaged Java DTO.
python3 "$root/scripts/audit_ft8_native_abi.py"
echo "FT8 native-source golden encoder and JNI DTO contract: PASS"
