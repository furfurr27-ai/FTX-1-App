#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
TEST_JAR="$BUILD/cp0006c-award-target-tests.jar"

echo "[1/5] Compile FieldOps core main once"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[2/5] Compile CP-0006C tests separately"
kotlinc   "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/AwardTargetEnrichmentTests.kt"   -cp "$MAIN_JAR"   -include-runtime   -d "$TEST_JAR"

echo "[3/5] Run award target/composite evaluator gate"
java -cp "$TEST_JAR:$MAIN_JAR" dev.n0png.fieldops.core.AwardTargetEnrichmentTests

echo "[4/5] Enforce no callsign/free-text award inference"
! grep -Eq 'qso\.call|qso\.notes|substring|startsWith\([^)]*call|prefix'   "$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/awards/AwardTargetEvidence.kt" || {
  echo "CP-0006C must not infer award targets from callsign/free text" >&2
  exit 1
}

echo "[5/5] Enforce evaluator remains account/network/hardware free"
! grep -Eq 'LotwCredentials|webPassword|HttpURLConnection|uploadTq8|UsbManager|Ftx1|PKCS|privateKey'   "$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/awards/AwardTargetEvidence.kt" || {
  echo "CP-0006C award evaluation must not own credentials, network submission, or hardware" >&2
  exit 1
}

echo "CP-0006C award target enrichment host gate: PASS"
