#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
TEST_JAR="$BUILD/cp0008d-pskr-tests.jar"
ADAPTER="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PskReporterHeardPathAdapter.kt"
TEST="$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/PskReporterHeardPathAdapterTests.kt"
FIXTURE="$ROOT/research/propagation/fixtures/psk_reporter_ag6k_20200903_bounded.xml"
MANIFEST="$ROOT/research/propagation/PSK_REPORTER_FIXTURE.json"
EVIDENCE="$ROOT/research/propagation/CP-0008D_PSK_REPORTER_HEARD_PATH_ADAPTER.md"
LEDGER="$ROOT/research/propagation/PROPAGATION_SOURCES.tsv"

echo "[1/8] Verify bounded PSK Reporter fixture integrity"
printf '%s  %s\n' \
  'fb41c07330c8d446dbd52eb4b35358950145b8a75fab76f225e69859b5752da7' \
  "$FIXTURE" | sha256sum -c -
test "$(wc -c < "$FIXTURE")" -eq 1196

echo "[2/8] Compile FieldOps core main"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[3/8] Compile CP-0008D tests separately"
kotlinc "$TEST" -cp "$MAIN_JAR" -include-runtime -d "$TEST_JAR"

echo "[4/8] Run deterministic PSK Reporter adapter tests"
java -cp "$TEST_JAR:$MAIN_JAR" dev.n0png.fieldops.core.PskReporterHeardPathAdapterTests "$ROOT"

echo "[5/8] Verify source/provenance pins"
grep -Fq 'https://retrieve.pskreporter.info/query' "$ADAPTER"
grep -Fq 'https://www.pskreporter.info/pskdev.html' "$ADAPTER"
grep -Fq 'psk-reporter-query-xml-docs-2026-10-07' "$ADAPTER"
grep -Fq 'PSK_REPORTER_DEVELOPER' "$LEDGER"
grep -Fq 'PSK_REPORTER_PUBLIC_QUERY' "$LEDGER"
grep -Fq 'GO_PSKREPORTER_RECORDED_RESPONSE' "$LEDGER"
grep -Fq 'b424d3bc83c52e424be7e6e32572ef652cecca4c' "$MANIFEST"
grep -Fq 'fb41c07330c8d446dbd52eb4b35358950145b8a75fab76f225e69859b5752da7' "$MANIFEST"

echo "[6/8] Enforce no geography invention and QSO promotion"
grep -Fq 'MISSING_SENDER_LOCATOR' "$ADAPTER"
grep -Fq 'MISSING_RECEIVER_LOCATOR' "$ADAPTER"
grep -Fq 'PropagationLocationMethod.EXPLICIT_GRID' "$ADAPTER"
! grep -Eq 'Callsign.*(lookup|geocod)|DXCC.*(center|coordinate)|QsoRecord|DigitalCompletedContact|Lotw|enqueue' "$ADAPTER"

echo "[7/8] Enforce transport/platform independence and five-minute boundary"
grep -Fq 'MIN_RETRIEVAL_INTERVAL_MILLIS = 5 * 60 * 1000L' "$ADAPTER"
! grep -Eq 'HttpURLConnection|OkHttp|Retrofit|java\.net\.|ktor|openConnection|android\.|androidx\.|UsbManager|Ftx1|RadioSession|PTT|TxController' "$ADAPTER"

echo "[8/8] Verify evidence/deferred rule"
grep -Fq 'Required CI does not contact the live service.' "$EVIDENCE"
grep -Fq 'not authoritative contacts' "$EVIDENCE"
grep -Fq 'CP-0003C remains **DEFERRED**' "$EVIDENCE"

echo "CP-0008D PSK Reporter heard-path adapter host gate: PASS"
