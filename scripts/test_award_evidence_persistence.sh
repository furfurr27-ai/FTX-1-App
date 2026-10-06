#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
TEST_JAR="$BUILD/cp0006e-award-evidence-tests.jar"

echo "[1/7] Compile FieldOps core main once"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[2/7] Compile CP-0006E tests separately"
kotlinc   "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/AwardEvidencePersistenceTests.kt"   -cp "$MAIN_JAR"   -include-runtime   -d "$TEST_JAR"

echo "[3/7] Run award evidence persistence/import gate"
java -cp "$TEST_JAR:$MAIN_JAR" dev.n0png.fieldops.core.AwardEvidencePersistenceTests

TARGET="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/awards/AwardEvidencePersistence.kt"

echo "[4/7] Enforce remote award targets come only from explicit ADIF fields"
! grep -Eq 'qso\.call|CALL.*substring|startsWith\([^)]*CALL|country.*DXCC|GRIDSQUARE.*(DXCC|STATE|CONT)|NOTES.*(DXCC|STATE|CONT|IOTA|POTA)' "$TARGET" || {
  echo "CP-0006E must not infer award targets from callsign/country/grid/notes" >&2
  exit 1
}

echo "[5/7] Enforce MY_* station metadata is not imported as remote award target evidence"
! grep -Eq 'normalized\["MY_(DXCC|STATE|CONT|IOTA|POTA)' "$TARGET" || {
  echo "CP-0006E must not reinterpret station MY_* fields as remote award targets" >&2
  exit 1
}

echo "[6/7] Enforce confirmation import is explicit received=Y only"
grep -Fq 'normalized["LOTW_QSL_RCVD"]?.uppercase() == "Y"' "$TARGET"
grep -Fq 'normalized["QSL_RCVD"]?.uppercase() == "Y"' "$TARGET"
! grep -Eq 'QSL_SENT.*AwardConfirmationEvidence|LOTW_QSL_SENT.*AwardConfirmationEvidence|lotwUpload.*AwardConfirmationEvidence' "$TARGET" || {
  echo "CP-0006E must not equate sent/upload state with confirmation" >&2
  exit 1
}

echo "[7/7] Enforce persistence/import remains account/network/hardware/UI-framework free"
! grep -Eq 'LotwCredentials|webPassword|HttpURLConnection|uploadTq8|UsbManager|Ftx1|PKCS|privateKey|androidx\.compose|androidx\.room' "$TARGET" || {
  echo "CP-0006E core persistence/import must remain credential-free, network-free, hardware-free, and UI-framework-free" >&2
  exit 1
}

echo "CP-0006E award evidence persistence host gate: PASS"
