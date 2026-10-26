#!/usr/bin/env bash
# Real source-built FT4/FT2 modem C round-trip, explicitly SYNTHETIC waveform.
# This does NOT replace independent FT4/FT2 recorded-air truth or device RF tests.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
native="$root/app/src/main/cpp/third_party/ft8af"
ft8="$native/ft8_lib"
glue="$native/ft8af_glue"
test_src="$root/app/src/test/native/ft8af/test_fast_waveform_decode.c"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

"${CC:-cc}" -std=c11 -O2 -D_GNU_SOURCE -Wall -Wextra -Wno-unused-function \
  -I "$ft8" -I "$glue" \
  "$test_src" "$glue/gfsk.c" \
  "$ft8/ft8/pack.c" "$ft8/ft8/encode.c" "$ft8/ft8/crc.c" \
  "$ft8/ft8/constants.c" "$ft8/ft8/text.c" "$ft8/ft8/message.c" \
  "$ft8/ft8/decode.c" "$ft8/ft8/ldpc.c" "$ft8/ft8/osd.c" \
  "$ft8/ft8/unpack.c" "$ft8/common/monitor.c" \
  "$ft8/fft/kiss_fft.c" "$ft8/fft/kiss_fftr.c" \
  -lm -o "$tmp/ft_fast_waveform_test"
"$tmp/ft_fast_waveform_test"
echo "FT4/FT2 clean-channel, from-source modulate/demodulate gate: PASS (NOT independent air recording)"
