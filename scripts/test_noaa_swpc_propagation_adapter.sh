#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
TEST_JAR="$BUILD/cp0008b-noaa-swpc-tests.jar"
TARGET="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/NoaaSwpcPropagationAdapter.kt"
TEST="$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/NoaaSwpcPropagationAdapterTests.kt"
MANIFEST="$ROOT/research/propagation/NOAA_SWPC_FIXTURES.json"
EVIDENCE="$ROOT/research/propagation/CP-0008B_NOAA_SWPC_ADAPTER.md"
LEDGER="$ROOT/research/propagation/PROPAGATION_SOURCES.tsv"

echo "[1/8] Verify pinned NOAA fixture hashes"
printf '%s  %s\n'   'a5e3aed635b9fc26d98326fbcbbc75fe8eaaac1a1cbf41f20f42b6f2d1243cda'   "$ROOT/research/propagation/fixtures/noaa_swpc_planetary_kp_sample.json"   'fedebb18935589bf394cd6bd55ae54ce871379722aca2ef6b5ce9c84adf736ba'   "$ROOT/research/propagation/fixtures/noaa_swpc_planetary_kp_forecast_sample.json"   '5b16306eb6f466b127b6ac32f293559284268928011df7c4f6bb949792f7c675'   "$ROOT/research/propagation/fixtures/noaa_swpc_f107_summary_sample.json"   | sha256sum -c -

echo "[2/8] Compile FieldOps core main"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[3/8] Compile CP-0008B tests separately"
kotlinc "$TEST" -cp "$MAIN_JAR" -include-runtime -d "$TEST_JAR"

echo "[4/8] Run deterministic NOAA adapter tests"
java -cp "$TEST_JAR:$MAIN_JAR" dev.n0png.fieldops.core.NoaaSwpcPropagationAdapterTests "$ROOT"

echo "[5/8] Verify endpoint/schema pins and source ledger"
grep -Fq 'swpc-json-post-scn26-21-v1' "$TARGET"
grep -Fq 'https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json' "$TARGET"
grep -Fq 'https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json' "$TARGET"
grep -Fq 'https://services.swpc.noaa.gov/products/summary/10cm-flux.json' "$TARGET"
grep -Fq 'NOAA_SWPC_SCN_26_21' "$LEDGER"
grep -Fq 'NOAA_SWPC_PLANETARY_KP' "$LEDGER"
grep -Fq 'NOAA_SWPC_F107_SUMMARY' "$LEDGER"
grep -Fq 'a5e3aed635b9fc26d98326fbcbbc75fe8eaaac1a1cbf41f20f42b6f2d1243cda' "$MANIFEST"
grep -Fq 'fedebb18935589bf394cd6bd55ae54ce871379722aca2ef6b5ce9c84adf736ba' "$MANIFEST"
grep -Fq '5b16306eb6f466b127b6ac32f293559284268928011df7c4f6bb949792f7c675' "$MANIFEST"

echo "[6/8] Enforce status/provenance/timestamp separation"
grep -Fq 'NoaaSwpcRecordStatus.OBSERVED' "$TARGET"
grep -Fq 'NoaaSwpcRecordStatus.ESTIMATED' "$TARGET"
grep -Fq 'NoaaSwpcRecordStatus.PREDICTED' "$TARGET"
grep -Fq 'PropagationSourceClass.FORECAST' "$TARGET"
grep -Fq 'PropagationDataQuality.ESTIMATED' "$TARGET"
grep -Fq 'retrievedAtUtcMillis' "$TARGET"
grep -Fq 'parseProviderUtc' "$TARGET"

echo "[7/8] Enforce transport/platform/QSO/path-score independence"
! grep -Eq 'HttpURLConnection|OkHttp|Retrofit|java\.net\.|ktor|URL\(|openConnection|android\.|androidx\.|GoogleMap|Mapbox|UsbManager|Ftx1|RadioSession|PTT|TxController|QsoRecord|Lotw|TrustedQSL' "$TARGET" || {
  echo "CP-0008B core adapter must remain transport/platform/QSO/hardware independent" >&2
  exit 1
}
! grep -Eq 'PropagationAssessmentEngine|PropagationPathAssessment|PropagationUsability' "$TARGET" || {
  echo "CP-0008B NOAA context adapter must not derive path usability directly" >&2
  exit 1
}

echo "[8/8] Verify evidence boundary"
grep -Fq 'not direct proof that a particular amateur-HF path is open' "$EVIDENCE"
grep -Fq 'does not calculate a universal path score' "$EVIDENCE"
grep -Fq 'does not create or mutate QSOs' "$EVIDENCE"
grep -Fq 'CP-0003C remains **DEFERRED**' "$EVIDENCE"

echo "CP-0008B NOAA SWPC public propagation adapter host gate: PASS"
