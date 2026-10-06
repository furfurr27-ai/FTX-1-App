#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
TEST_JAR="$BUILD/cp0007a-award-map-tests.jar"

echo "[1/8] Compile FieldOps core main once"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[2/8] Compile CP-0007A tests separately"
kotlinc   "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/AwardAreaMapProjectionTests.kt"   -cp "$MAIN_JAR"   -include-runtime   -d "$TEST_JAR"

echo "[3/8] Run award-area map projection gate"
java -cp "$TEST_JAR:$MAIN_JAR" dev.n0png.fieldops.core.AwardAreaMapProjectionTests

TARGET="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/map/AwardAreaMapProjection.kt"
APP="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/awards/AwardsCenterApplicationService.kt"

echo "[4/8] Enforce initial map geography is explicit state/grid evidence only"
grep -Fq 'OfficialAwardTargetKind.US_STATE' "$TARGET"
grep -Fq 'OfficialAwardTargetKind.MAIDENHEAD_GRID4' "$TARGET"
! grep -Eq 'OfficialAwardTargetKind\.(DXCC_ENTITY|IOTA_GROUP|POTA_REFERENCE|SOTA_POINT)' "$TARGET" || {
  echo "CP-0007A must not project unsupported geography types" >&2
  exit 1
}

echo "[5/8] Enforce no callsign-derived geography"
! grep -Eq 'qso\.call|\.call\)|substring.*call|startsWith.*call|prefix.*call' "$TARGET" || {
  echo "CP-0007A must not derive map geography from callsigns" >&2
  exit 1
}

echo "[6/8] Enforce geometry remains metadata-only"
grep -Fq 'val geometryAssetId: String' "$TARGET"
grep -Fq 'val source: AwardAreaGeometrySource' "$TARGET"
! grep -Eq 'val (latitude|longitude|coordinates|polygon|geoJson|geojson)[ :]' "$TARGET" || {
  echo "CP-0007A must not embed geometry payload in award projection records" >&2
  exit 1
}

echo "[7/8] Enforce map service reads authoritative repositories"
grep -Fq 'fun awardMapLayers(' "$APP"
grep -Fq 'qsos = logbook.all()' "$APP"
grep -Fq 'evidence = evidenceRepository.snapshot()' "$APP"

echo "[8/8] Enforce map core remains account/network/hardware/UI-framework free"
! grep -Eq 'LotwCredentials|webPassword|HttpURLConnection|uploadTq8|UsbManager|Ftx1|PKCS|privateKey|androidx\.compose|androidx\.room|GoogleMap|Mapbox' "$TARGET" || {
  echo "CP-0007A map projection must remain credential-free, network-free, hardware-free, and UI-framework-free" >&2
  exit 1
}

echo "CP-0007A award-area map projection host gate: PASS"
