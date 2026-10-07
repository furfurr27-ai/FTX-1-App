#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
TEST_JAR="$BUILD/cp0008c-glotec-tests.jar"
ADAPTER="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/NoaaSwpcGlotecAdapter.kt"
DOMAIN="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationDomain.kt"
TEST="$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/NoaaSwpcGlotecAdapterTests.kt"
FIXTURE="$ROOT/research/propagation/fixtures/noaa_swpc_glotec_20260909T151500Z_bounded.geojson"
MANIFEST="$ROOT/research/propagation/NOAA_SWPC_GLOTEC_FIXTURE.json"
EVIDENCE="$ROOT/research/propagation/CP-0008C_GLOTEC_IONOSPHERIC_ADAPTER.md"
LEDGER="$ROOT/research/propagation/PROPAGATION_SOURCES.tsv"

echo "[1/9] Verify bounded GloTEC fixture integrity"
printf '%s  %s\n' \
  'a98741d9a9586082db0eb357f3baf35be09a2646c8ab5b1b4203d4852b09bac2' \
  "$FIXTURE" | sha256sum -c -
test "$(wc -c < "$FIXTURE")" -eq 1573

echo "[2/9] Compile FieldOps core main"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[3/9] Compile CP-0008C tests separately"
kotlinc "$TEST" -cp "$MAIN_JAR" -include-runtime -d "$TEST_JAR"

echo "[4/9] Run deterministic GloTEC adapter tests"
java -cp "$TEST_JAR:$MAIN_JAR" dev.n0png.fieldops.core.NoaaSwpcGlotecAdapterTests "$ROOT"

echo "[5/9] Verify official endpoint/schema pins and provenance ledger"
grep -Fq 'https://services.swpc.noaa.gov/products/glotec/geojson_2d_urt/' "$ADAPTER"
grep -Fq 'https://www.spaceweather.gov/index.php/products/glotec' "$ADAPTER"
grep -Fq 'glotec-operational-geojson-v1' "$ADAPTER"
grep -Fq 'NOAA_SWPC_GLOTEC_SCN_25_04' "$LEDGER"
grep -Fq 'NOAA_SWPC_GLOTEC_PRODUCT' "$LEDGER"
grep -Fq 'NOAA_SWPC_GLOTEC_GEOJSON' "$LEDGER"
grep -Fq 'GLOTEC_RECORDED_FIXTURE_MIRROR' "$LEDGER"
grep -Fq '7382dc23cd5f74c00fae99387d2052940c5c63b7' "$MANIFEST"
grep -Fq 'a98741d9a9586082db0eb357f3baf35be09a2646c8ab5b1b4203d4852b09bac2' "$MANIFEST"

echo "[6/9] Enforce provider-neutral VTEC and quality metadata"
grep -Fq 'VTEC_TECU' "$DOMAIN"
grep -Fq 'providerQualityCode' "$DOMAIN"
grep -Fq 'providerQualityExplanation' "$DOMAIN"
grep -Fq 'PropagationLocationMethod.PROVIDER_COORDINATE' "$ADAPTER"
grep -Fq 'quality_flag must be between 0 and 5' "$ADAPTER"
grep -Fq 'metric = IonosphericMetric.VTEC_TECU' "$ADAPTER"
grep -Fq 'generatedAtUtcMillis = null' "$ADAPTER"

echo "[7/9] Enforce transport/platform/QSO/hardware independence"
! grep -Eq 'HttpURLConnection|OkHttp|Retrofit|java\.net\.|ktor|URL\(|openConnection|android\.|androidx\.|GoogleMap|Mapbox|UsbManager|Ftx1|RadioSession|PTT|TxController|QsoRecord|Lotw|TrustedQSL' "$ADAPTER" || {
  echo "CP-0008C core adapter must remain transport/platform/QSO/hardware independent" >&2
  exit 1
}

echo "[8/9] Enforce no direct TEC-to-path/MUF conversion"
! grep -Eq 'PropagationAssessmentEngine|PropagationPathAssessment|PropagationUsability|maximumUsableFrequency|lowestUsableFrequency|MUF_MHZ' "$ADAPTER" || {
  echo "CP-0008C GloTEC context adapter must not derive path usability or MUF directly" >&2
  exit 1
}

echo "[9/9] Verify evidence boundary and owner override"
grep -Fq 'does **not** create' "$EVIDENCE"
grep -Fq 'GloTEC alone leaves path usability `UNKNOWN`' "$EVIDENCE"
grep -Fq 'generatedAtUtcMillis = null' "$EVIDENCE"
grep -Fq 'CP-0003C remains **DEFERRED**' "$EVIDENCE"

echo "CP-0008C NOAA SWPC GloTEC ionospheric adapter host gate: PASS"
