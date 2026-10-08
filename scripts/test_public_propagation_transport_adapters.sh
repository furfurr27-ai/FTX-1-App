#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
TEST_JAR="$BUILD/cp0008h-public-transport-tests.jar"

TRANSPORT="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PublicPropagationTransport.kt"
SOURCES="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PublicPropagationSourceAdapters.kt"
INDEX="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/NoaaSwpcGlotecIndexSelector.kt"
TEST="$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/PublicPropagationTransportAdapterTests.kt"
EVIDENCE="$ROOT/research/propagation/CP-0008H_PUBLIC_PROPAGATION_TRANSPORT_ADAPTERS.md"

echo "[1/8] Compile FieldOps core main"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[2/8] Compile CP-0008H tests separately"
kotlinc "$TEST" -cp "$MAIN_JAR" -include-runtime -d "$TEST_JAR"

echo "[3/8] Run deterministic public transport adapter tests"
java -cp "$TEST_JAR:$MAIN_JAR" dev.n0png.fieldops.core.PublicPropagationTransportAdapterTests

echo "[4/8] Enforce exact established public endpoints"
grep -Fq 'NoaaSwpcPropagationAdapter.PLANETARY_KP_URL' "$SOURCES"
grep -Fq 'NoaaSwpcPropagationAdapter.PLANETARY_KP_FORECAST_URL' "$SOURCES"
grep -Fq 'NoaaSwpcPropagationAdapter.F107_SUMMARY_URL' "$SOURCES"
grep -Fq 'NoaaSwpcGlotecAdapter.INDEX_URL' "$SOURCES"
grep -Fq 'PskReporterHeardPathAdapter.QUERY_URL' "$SOURCES"

echo "[5/8] Enforce GloTEC latest-artifact and host restriction"
grep -Fq 'selectLatest' "$INDEX"
grep -Fq 'NoaaSwpcGlotecAdapter.DIRECTORY_URL' "$INDEX"
grep -Fq 'non-official or malformed artifact reference' "$INDEX"
grep -Fq 'glotec_icao_' "$INDEX"

echo "[6/8] Enforce bounded response/error handling and PSK privacy"
grep -Fq 'maxResponseBytes' "$TRANSPORT"
grep -Fq 'statusCode != 200' "$TRANSPORT"
grep -Fq 'effectiveUrl != request.url' "$TRANSPORT"
grep -Fq 'MIN_RETRIEVAL_INTERVAL_MILLIS' "$SOURCES"
grep -Fq 'appcontact=' "$SOURCES"
grep -Fq 'callback=' "$SOURCES"

echo "[7/8] Enforce core transport abstraction and platform separation"
grep -Fq 'fun interface PublicPropagationTransport' "$TRANSPORT"
! grep -Eq 'android\.|androidx\.|WorkManager|OkHttp|Retrofit|HttpURLConnection|java\.net|openConnection|UsbManager|Ftx1|RadioSession|PTT|password|credential|apiKey'   "$TRANSPORT" "$SOURCES" "$INDEX" || {
    echo "CP-0008H must remain platform/network-client/account/radio independent" >&2
    exit 1
  }

echo "[8/8] Verify evidence boundary and owner override"
grep -Fq 'deterministic fake transport' "$EVIDENCE"
grep -Fq 'GloTEC' "$EVIDENCE"
grep -Fq 'PSK Reporter' "$EVIDENCE"
grep -Fq 'CP-0003C remains **DEFERRED**' "$EVIDENCE"

echo "CP-0008H public propagation transport adapter host gate: PASS"
