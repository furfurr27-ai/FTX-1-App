#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
TEST_JAR="$BUILD/cp0006d-awards-center-tests.jar"

echo "[1/6] Compile FieldOps core main once"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[2/6] Compile CP-0006D tests separately"
kotlinc   "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/AwardsCenterProjectionTests.kt"   -cp "$MAIN_JAR"   -include-runtime   -d "$TEST_JAR"

echo "[3/6] Run Awards Center projection gate"
java -cp "$TEST_JAR:$MAIN_JAR" dev.n0png.fieldops.core.AwardsCenterProjectionTests

echo "[4/6] Enforce projection does not hard-code award IDs/rules"
PROJECTION="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/awards/AwardsCenterProjection.kt"
! grep -Eq 'ARRL_|IARU_|IOTA_100|POTA_BRONZE|SOTA_SHACK|DXCC_MIXED|TRIPLE_PLAY' "$PROJECTION" || {
  echo "CP-0006D projection must consume catalog/evaluator data instead of hard-coding award rules" >&2
  exit 1
}

echo "[5/6] Enforce sponsor state is not inferred from local threshold"
! grep -Eq 'thresholdMet.*ELIGIBLE_NOT_CLAIMED|localThresholdMet.*ELIGIBLE_NOT_CLAIMED|basisPoints.*ELIGIBLE_NOT_CLAIMED' "$PROJECTION" || {
  echo "CP-0006D projection must not derive sponsor eligibility from local progress" >&2
  exit 1
}

echo "[6/6] Enforce projection remains account/network/hardware/UI-framework free"
! grep -Eq 'LotwCredentials|webPassword|HttpURLConnection|uploadTq8|UsbManager|Ftx1|PKCS|privateKey|androidx\.compose|androidx\.room' "$PROJECTION" || {
  echo "CP-0006D projection must remain UI-independent, credential-free, network-free, and hardware-free" >&2
  exit 1
}

echo "CP-0006D Awards Center projection host gate: PASS"
