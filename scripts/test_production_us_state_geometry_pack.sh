#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
TEST_JAR="$BUILD/cp0007c-state-pack-tests.jar"
SOURCE_ZIP="$BUILD/cb_2025_us_state_20m.zip"
REBUILT_PACK="$BUILD/us_states_2025_20m.pack"
REBUILT_META_KT="$BUILD/Census2025UsState20mPackMetadata.kt"
REBUILT_JSON="$BUILD/US_STATE_2025_20M_PACK.json"

echo "[1/10] Compile FieldOps core main"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[2/10] Compile CP-0007C tests"
kotlinc   "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/ProductionUsStateGeometryPackTests.kt"   -cp "$MAIN_JAR"   -include-runtime   -d "$TEST_JAR"

echo "[3/10] Run production U.S. state geometry pack tests"
java -cp "$TEST_JAR:$MAIN_JAR" dev.n0png.fieldops.core.ProductionUsStateGeometryPackTests

echo "[4/10] Download exact pinned Census artifact"
curl -fsSL --retry 3 --retry-delay 2   "https://www2.census.gov/geo/tiger/GENZ2025/kml/cb_2025_us_state_20m.zip"   -o "$SOURCE_ZIP"

echo "[5/10] Verify upstream artifact SHA-256 and size"
EXPECTED_SOURCE_SHA="efddd884f1442ef233b1ba9c12dddbd66b6fdf94da6a373e1556aefe3dbc5751"
ACTUAL_SOURCE_SHA="$(sha256sum "$SOURCE_ZIP" | awk '{print $1}')"
[[ "$ACTUAL_SOURCE_SHA" == "$EXPECTED_SOURCE_SHA" ]] || {
  echo "Census artifact SHA mismatch expected=$EXPECTED_SOURCE_SHA actual=$ACTUAL_SOURCE_SHA" >&2
  exit 1
}
[[ "$(wc -c < "$SOURCE_ZIP" | tr -d ' ')" == "158017" ]] || {
  echo "Census artifact byte size changed" >&2
  exit 1
}

echo "[6/10] Rebuild production pack from pinned source"
python3 "$ROOT/scripts/build_us_state_geometry_pack.py"   --source-zip "$SOURCE_ZIP"   --output-kotlin "$REBUILT_KT"   --output-metadata "$REBUILT_JSON"

echo "[7/10] Verify generated production artifacts are byte-for-byte deterministic"
cmp "$REBUILT_KT"   "$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/map/Census2025UsState20mGeometryPack.kt"
cmp "$REBUILT_JSON" "$ROOT/research/maps/US_STATE_2025_20M_PACK.json"

echo "[8/10] Verify source ledger and exact WAS scope"
grep -Fq $'US_CENSUS_2025_STATE_20M_KML	EXTERNAL_ARTIFACT	https://www2.census.gov/geo/tiger/GENZ2025/kml/cb_2025_us_state_20m.zip'   "$ROOT/research/maps/GEOMETRY_SOURCES.tsv"
python3 - "$ROOT/research/maps/US_STATE_2025_20M_PACK.json" <<'PY'
import json
import sys
data = json.load(open(sys.argv[1], encoding="utf-8"))
assert data["featureCount"] == 50
assert len(data["stateIdentities"]) == 50
assert data["upstreamPlacemarkCount"] == 52
assert data["ignoredNonWasPlacemarkCount"] == 2
assert data["upstream"]["sha256"] == "efddd884f1442ef233b1ba9c12dddbd66b6fdf94da6a373e1556aefe3dbc5751"
assert data["canonicalPackSha256"] == "5feb8c18688936a526523cb536766130be06b14ebfa918b3d99e39bfbcb0a130"
assert {"DC", "PR", "AS", "GU", "MP", "VI"}.isdisjoint(data["stateIdentities"])
assert data["alaska"]["polygonCount"] == 47
assert data["alaska"]["containsPositiveLongitudes"] is True
assert data["alaska"]["containsNegativeLongitudes"] is True
assert data["alaska"]["maxRingSegmentLongitudeJump"] <= 180.0
print("production state pack metadata gate: PASS")
PY

echo "[9/10] Enforce production geography remains callsign/network/UI/hardware independent"
TARGETS=(
  "$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/map/Census2025UsState20mGeometryPack.kt"
  "$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/map/Census2025UsState20mPackMetadata.kt"
  "$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/map/GeometryPackIntegrity.kt"
  "$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/map/AwardGeometryProviders.kt"
)
! grep -Eq 'qso\.call|\.call\)|HttpURLConnection|OkHttp|Retrofit|androidx\.|GoogleMap|Mapbox|UsbManager|Ftx1|LotwCredentials|webPassword|PKCS|privateKey' "${TARGETS[@]}" || {
  echo "CP-0007C production geometry must remain offline/platform/account/hardware independent" >&2
  exit 1
}

echo "[10/10] Verify generated pack is production Census data, not a synthetic fixture"
grep -Fq 'sourceUrl = UPSTREAM_URL'   "$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/map/Census2025UsState20mGeometryPack.kt"
! grep -Fq 'CP0007B_SYNTHETIC_STATE_GEOMETRY'   "$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/map/Census2025UsState20mGeometryPack.kt" || {
  echo "Production pack must not use synthetic fixture provenance" >&2
  exit 1
}

echo "CP-0007C production U.S. state geometry pack host gate: PASS"
