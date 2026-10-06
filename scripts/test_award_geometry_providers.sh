#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
TEST_JAR="$BUILD/cp0007b-geometry-tests.jar"

echo "[1/9] Compile FieldOps core main once"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[2/9] Compile CP-0007B tests separately"
kotlinc   "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/AwardGeometryProviderTests.kt"   -cp "$MAIN_JAR"   -include-runtime   -d "$TEST_JAR"

echo "[3/9] Run award geometry provider gate"
java -cp "$TEST_JAR:$MAIN_JAR" dev.n0png.fieldops.core.AwardGeometryProviderTests

TARGET="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/map/AwardGeometryProviders.kt"
LEDGER="$ROOT/research/maps/GEOMETRY_SOURCES.tsv"

echo "[4/9] Pin IARU Maidenhead specification"
grep -Fq 'https://www.iaru-r1.org/wp-content/uploads/2021/03/VHF_Handbook_V9.01.pdf' "$TARGET"
grep -Fq $'IARU_R1_MAIDENHEAD_LOCATOR	DERIVED_STANDARD	https://www.iaru-r1.org/wp-content/uploads/2021/03/VHF_Handbook_V9.01.pdf' "$LEDGER"
grep -Fq 'origin is 180 degrees west / 90 degrees south' "$TARGET"
grep -Fq 'WGS-84 is the reference geodetic system' "$TARGET"

echo "[5/9] Pin Census state geometry source and rights metadata"
grep -Fq 'https://www.census.gov/geographies/mapping-files/2025/geo/carto-boundary-file.html' "$TARGET"
grep -Fq '17 U.S.C. §105' "$TARGET"
grep -Fq $'US_CENSUS_CARTOGRAPHIC_BOUNDARY_FILES	EXTERNAL_DATASET	https://www.census.gov/geographies/mapping-files/2025/geo/carto-boundary-file.html' "$LEDGER"
grep -Fq $'US_CENSUS_TIGER_LEGAL	PROGRAM_INFO	https://www2.census.gov/geo/pdfs/maps-data/data/tiger/tgrshp2019/TGRSHP2019_TechDoc_Ch1.pdf' "$LEDGER"

echo "[6/9] Enforce license metadata for non-derived payloads"
grep -Fq 'if (kind != AwardGeometrySourceKind.DERIVED_STANDARD)' "$TARGET"
grep -Fq 'Non-derived geometry requires explicit license/public-domain metadata' "$TARGET"
grep -Fq 'External geometry datasets require an authoritative HTTPS source URL' "$TARGET"

echo "[7/9] Enforce synthetic CI fixtures cannot masquerade as Census geometry"
grep -Fq 'SYNTHETIC_FIXTURE' "$TARGET"
grep -Fq 'contains no Census boundary geometry' "$TARGET"

echo "[8/9] Enforce provider geometry remains offline and callsign independent"
! grep -Eq 'HttpURLConnection|OkHttp|Retrofit|java\.net\.URL\(|qso\.call|\.call\)|startsWith.*call|substring.*call' "$TARGET" || {
  echo "CP-0007B providers must remain offline and must not derive geography from callsigns" >&2
  exit 1
}

echo "[9/9] Enforce core provider remains Android/map-SDK/hardware/account free"
! grep -Eq 'androidx\.|com\.google\.android\.gms\.maps|Mapbox|UsbManager|Ftx1|LotwCredentials|webPassword|PKCS|privateKey' "$TARGET" || {
  echo "CP-0007B geometry provider must remain platform/account/hardware independent" >&2
  exit 1
}

echo "CP-0007B award geometry provider host gate: PASS"
