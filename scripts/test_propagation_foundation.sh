#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
TEST_JAR="$BUILD/cp0008a-propagation-tests.jar"
TARGET="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationDomain.kt"
LEDGER="$ROOT/research/propagation/PROPAGATION_SOURCES.tsv"
EVIDENCE="$ROOT/research/propagation/CP-0008A_PROPAGATION_FOUNDATION.md"

echo "[1/9] Compile FieldOps core main"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[2/9] Compile CP-0008A tests separately"
kotlinc   "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/PropagationFoundationTests.kt"   -cp "$MAIN_JAR"   -include-runtime   -d "$TEST_JAR"

echo "[3/9] Run propagation foundation tests"
java -cp "$TEST_JAR:$MAIN_JAR" dev.n0png.fieldops.core.PropagationFoundationTests

echo "[4/9] Verify authoritative research source ledger"
grep -Fq $'NOAA_SWPC_K_INDEX	PROGRAM_INFO	https://www.swpc.noaa.gov/sites/default/files/images/u2/TheK-index.pdf' "$LEDGER"
grep -Fq $'NOAA_SWPC_SCALES	PROGRAM_INFO	https://www.swpc.noaa.gov/node/1085' "$LEDGER"
grep -Fq $'NOAA_SWPC_PRODUCTS	FUTURE_PROVIDER_RESEARCH	https://services.swpc.noaa.gov/products/' "$LEDGER"
grep -Fq $'GIRO_MUF	PROGRAM_INFO	https://giro.uml.edu/rix/MUF/' "$LEDGER"
grep -Fq $'GIRO_RULES	LICENSE_TERMS	https://giro.uml.edu/didbase/RulesOfTheRoad.html' "$LEDGER"

echo "[5/9] Enforce observed/model/provider provenance separation"
grep -Fq 'enum class PropagationSourceClass' "$TARGET"
grep -Fq 'MEASUREMENT' "$TARGET"
grep -Fq 'MODEL' "$TARGET"
grep -Fq 'FORECAST' "$TARGET"
grep -Fq 'SYNTHETIC_FIXTURE' "$TARGET"
grep -Fq 'Synthetic propagation fixtures must not masquerade as live provider URLs' "$TARGET"
grep -Fq 'Modeled-path estimate requires model/forecast/synthetic source provenance' "$TARGET"

echo "[6/9] Enforce explicit location, freshness, and MUF semantics"
grep -Fq 'Propagation position requires explicit coordinates or a Maidenhead grid' "$TARGET"
grep -Fq 'FUTURE_DATED' "$TARGET"
grep -Fq 'MUF product requires an explicit positive reference distance' "$TARGET"
grep -Fq 'Planetary Kp must be between 0 and 9' "$TARGET"
grep -Fq 'it >= 5.0' "$TARGET"

echo "[7/9] Enforce explainable path assessment and offline cache contracts"
grep -Fq 'interface PropagationSnapshotStore' "$TARGET"
grep -Fq 'enum class PropagationAssessmentReasonCode' "$TARGET"
grep -Fq 'RECENT_OBSERVED_PATH' "$TARGET"
grep -Fq 'MODEL_ONLY_NO_OBSERVED_PATH' "$TARGET"
grep -Fq 'STALE_EVIDENCE_IGNORED' "$TARGET"
grep -Fq 'INSUFFICIENT_PATH_EVIDENCE' "$TARGET"

echo "[8/9] Enforce foundation remains provider-neutral and QSO-independent"
! grep -Eq 'NOAA|GIRO|PSK.?Reporter|WSPRnet|QsoRecord|Lotw|TrustedQSL' "$TARGET" || {
  echo "CP-0008A production domain must remain provider-neutral and distinct from QSO/LoTW state" >&2
  exit 1
}
! grep -Eq 'HttpURLConnection|OkHttp|Retrofit|java\.net\.|ktor|apiKey|password|token' "$TARGET" || {
  echo "CP-0008A foundation must not contain live network/account clients" >&2
  exit 1
}

echo "[9/9] Enforce platform/map-SDK/hardware independence and synthetic-only evidence boundary"
! grep -Eq 'android\.|androidx\.|GoogleMap|Mapbox|UsbManager|Ftx1|RadioSession|PTT|TxController' "$TARGET" || {
  echo "CP-0008A propagation foundation must remain platform/map-SDK/radio independent" >&2
  exit 1
}
grep -Fq 'synthetic data only' "$EVIDENCE"
grep -Fq 'No callsign-to-location inference exists in CP-0008A.' "$EVIDENCE"
grep -Fq 'no GIRO data, account access, scraping, or live adapter' "$EVIDENCE"

echo "CP-0008A propagation intelligence foundation host gate: PASS"
