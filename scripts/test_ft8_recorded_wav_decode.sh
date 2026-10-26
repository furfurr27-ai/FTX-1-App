#!/usr/bin/env bash
# Reproducible source-backed FT8 decoding of an independently recorded 20m WAV.
# No user radio, mock Java decoder, transmission or account credentials.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source_tree="$root/app/src/main/cpp/third_party/ft8af"
ft8="$source_tree/ft8_lib"
glue="$source_tree/ft8af_glue"
fixture="$root/app/src/test/native/ft8af"
upstream_sha="c2f63e8b37fcd484fd2eb2049494425dd2414971"
blob_sha="820972665a551c93ef5334d1e19a917debdaba45"
wav_path="ft8af/app/src/main/cpp/ft8af_glue/testdata/ft8/20m_busy_test_01.wav"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# Download exact pinned upstream WAV. Git's own blob SHA1 is checked to
# prevent moving links, replacement recordings or unreviewed test input.
curl --retry 3 --retry-delay 2 --fail --location --silent --show-error \
  "https://raw.githubusercontent.com/patrickrb/FT8AF/$upstream_sha/$wav_path" \
  --output "$tmp/20m_busy_test_01.wav"
test "$(git hash-object "$tmp/20m_busy_test_01.wav")" = "$blob_sha" || {
  echo "Pinned FT8 recorded WAV Git blob checksum mismatch" >&2
  exit 1
}
cp "$fixture/testdata/20m_busy_test_01.txt" "$tmp/20m_busy_test_01.txt"
test "$(git hash-object "$tmp/20m_busy_test_01.txt")" = "3f388c70c844353974a9bb2d6031af444f3c6063"

cc -std=c11 -O2 -D_GNU_SOURCE -Wall -Wno-unused-function \
  -I "$ft8" -I "$glue" \
  "$fixture/decode_bench.c" \
  "$glue/gfsk.c" "$glue/ft8_subtract.c" "$glue/ft8_fine.c" "$glue/ft8_xslot.c" \
  "$ft8/ft8/pack.c" "$ft8/ft8/encode.c" "$ft8/ft8/crc.c" \
  "$ft8/ft8/constants.c" "$ft8/ft8/text.c" "$ft8/ft8/message.c" \
  "$ft8/ft8/decode.c" "$ft8/ft8/ldpc.c" "$ft8/ft8/osd.c" \
  "$ft8/ft8/unpack.c" "$ft8/common/monitor.c" "$ft8/common/wave.c" \
  "$ft8/fft/kiss_fft.c" "$ft8/fft/kiss_fftr.c" \
  -lm -o "$tmp/ft8_decode_bench"

"$tmp/ft8_decode_bench" --self-test
# Baseline recorded 20m slot includes 24 independent jt9 decoded truth lines;
# upstream source's floor must be matched, not quietly reduced by test edits.
"$tmp/ft8_decode_bench" --assert-floor "$fixture/testdata/floors.txt" \
  "$tmp/20m_busy_test_01.wav" | tee "$tmp/result.txt"
grep -Eq '^RESULT 20m_busy_test_01.wav decoded=[0-9]+ matched=24 truth=24' "$tmp/result.txt"
echo "Recorded FT8/20m decode vs 24 independently logged messages: PASS"
