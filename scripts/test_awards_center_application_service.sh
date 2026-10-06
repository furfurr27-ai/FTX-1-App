#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
TEST_JAR="$BUILD/cp0006f-awards-service-tests.jar"

echo "[1/7] Compile FieldOps core main once"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[2/7] Compile CP-0006F tests separately"
kotlinc   "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/AwardsCenterApplicationServiceTests.kt"   -cp "$MAIN_JAR"   -include-runtime   -d "$TEST_JAR"

echo "[3/7] Run Awards Center application service gate"
java -cp "$TEST_JAR:$MAIN_JAR" dev.n0png.fieldops.core.AwardsCenterApplicationServiceTests

TARGET="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/awards/AwardsCenterApplicationService.kt"

echo "[4/7] Enforce immutable-QSO-id ingestion with no callsign resolver"
grep -Fq 'logbook.get(input.qsoId)' "$TARGET"
! grep -Eq 'find.*call|firstOrNull.*call|filter.*call|startsWith\([^)]*call|substring.*call' "$TARGET" || {
  echo "CP-0006F must not resolve award evidence to QSOs by callsign" >&2
  exit 1
}

echo "[5/7] Enforce batch conversion precedes one evidence apply"
test "$(grep -c 'evidenceRepository.apply(combined)' "$TARGET")" -eq 1
! grep -Eq 'for.*evidenceRepository\.apply|map.*evidenceRepository\.apply' "$TARGET" || {
  echo "CP-0006F batch ingestion must convert first and commit once" >&2
  exit 1
}

echo "[6/7] Enforce cards source logbook and evidence repository directly"
grep -Fq 'qsos = logbook.all()' "$TARGET"
grep -Fq 'val evidence = evidenceRepository.snapshot()' "$TARGET"

echo "[7/7] Enforce application service remains account/network/hardware/UI-framework free"
! grep -Eq 'LotwCredentials|webPassword|HttpURLConnection|uploadTq8|UsbManager|Ftx1|PKCS|privateKey|androidx\.compose|androidx\.room' "$TARGET" || {
  echo "CP-0006F application service must remain credential-free, network-free, hardware-free, and UI-framework-free" >&2
  exit 1
}

echo "CP-0006F Awards Center application service host gate: PASS"
