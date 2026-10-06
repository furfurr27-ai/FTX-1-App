#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
TEST_JAR="$BUILD/cp0006b-official-award-catalog-tests.jar"

echo "[1/5] Compile FieldOps core main once"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[2/5] Compile CP-0006B catalog tests separately"
kotlinc   "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/OfficialAwardCatalogTests.kt"   -cp "$MAIN_JAR"   -include-runtime   -d "$TEST_JAR"

echo "[3/5] Run official award catalog gate"
java -cp "$TEST_JAR:$MAIN_JAR" dev.n0png.fieldops.core.OfficialAwardCatalogTests

echo "[4/5] Verify durable official-source ledger"
LEDGER="$ROOT/research/awards/OFFICIAL_AWARD_SOURCES.tsv"
test -s "$LEDGER"
grep -Fq $'ARRL_DXCC_MIXED\tARRL\tRULES\thttps://www.arrl.org/dxcc-rules' "$LEDGER"
grep -Fq $'IOTA_100\tIOTA Ltd / IOTA Programme\tRULES\thttps://www.iota-world.org/info/directory/rules-en.pdf' "$LEDGER"
grep -Fq $'POTA_BRONZE_HUNTER\tParks on the Air\tRULES\thttps://docs.pota.app/' "$LEDGER"
grep -Fq $'SOTA_SHACK_SLOTH_1000\tSummits on the Air\tRULES\thttps://www.sota.org.uk/' "$LEDGER"
if tail -n +2 "$LEDGER" | cut -f4 | grep -Ev '^https://'; then
  echo "CP-0006B source ledger contains a non-HTTPS source" >&2
  exit 1
fi

echo "[5/5] Enforce catalog remains credential/network/submission free"
! grep -Eq 'LotwCredentials|webPassword|HttpURLConnection|uploadTq8|uploadTransactional|PKCS|privateKey'   "$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/awards/OfficialAwardCatalog.kt" || {
  echo "CP-0006B catalog must not own credentials, signing, network upload, or claim submission" >&2
  exit 1
}

echo "CP-0006B official award catalog host gate: PASS"
