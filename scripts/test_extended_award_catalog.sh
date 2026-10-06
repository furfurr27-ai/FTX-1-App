#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
TEST_JAR="$BUILD/cp0006g-extended-award-tests.jar"

echo "[1/8] Compile FieldOps core main once"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[2/8] Compile CP-0006G tests separately"
kotlinc   "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/ExtendedAwardCatalogTests.kt"   -cp "$MAIN_JAR"   -include-runtime   -d "$TEST_JAR"

echo "[3/8] Run extended official award catalog gate"
java -cp "$TEST_JAR:$MAIN_JAR" dev.n0png.fieldops.core.ExtendedAwardCatalogTests

CATALOG="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/awards/OfficialAwardCatalog.kt"
TARGET="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/awards/AwardTargetEvidence.kt"
IMPORTER="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/awards/AwardEvidencePersistence.kt"
LEDGER="$ROOT/research/awards/OFFICIAL_AWARD_SOURCES.tsv"

echo "[4/8] Verify issuer-authoritative ARRL source ledger rows"
grep -Fq $'ARRL_VUCC_50MHZ	ARRL	RULES	https://www.arrl.org/files/file/Awards/VUCC-Rules-July-2019.pdf' "$LEDGER"
grep -Fq $'ARRL_VUCC_144MHZ	ARRL	RULES	https://www.arrl.org/files/file/Awards/VUCC-Rules-July-2019.pdf' "$LEDGER"
grep -Fq $'ARRL_VUCC_432MHZ	ARRL	RULES	https://www.arrl.org/files/file/Awards/VUCC-Rules-July-2019.pdf' "$LEDGER"
grep -Fq $'ARRL_FFMA	ARRL	RULES	https://www.arrl.org/FFMA' "$LEDGER"

echo "[5/8] Enforce required-band restrictions are evaluator rules"
grep -Fq 'val requiredBands: Set<String> = emptySet()' "$CATALOG"
grep -Fq 'requirement.requiredBands.isNotEmpty()' "$TARGET"
grep -Fq 'requirement.requiredBands.none' "$TARGET"

echo "[6/8] Enforce explicit grid evidence only"
grep -Fq 'OfficialAwardTargetKind.MAIDENHEAD_GRID4' "$TARGET"
grep -Fq 'normalized["GRIDSQUARE"]?.let { addTarget(OfficialAwardTargetKind.MAIDENHEAD_GRID4, it) }' "$IMPORTER"
! grep -Eq 'MY_GRIDSQUARE.*MAIDENHEAD_GRID4|qso\.call.*MAIDENHEAD_GRID4|COUNTRY.*MAIDENHEAD_GRID4|NOTES.*MAIDENHEAD_GRID4' "$IMPORTER" "$TARGET" || {
  echo "CP-0006G must not infer remote award grids from MY_GRIDSQUARE/callsign/country/notes" >&2
  exit 1
}

echo "[7/8] Enforce CQ WAZ/WPX are not guessed into production catalog"
! grep -Eq 'id = "CQ_(WAZ|WPX)' "$CATALOG" || {
  echo "CP-0006G must not encode CQ WAZ/WPX without a pinned issuer-authoritative rule source" >&2
  exit 1
}

echo "[8/8] Enforce catalog/evaluator remain account/network/hardware/UI-framework free"
! grep -Eq 'LotwCredentials|webPassword|HttpURLConnection|uploadTq8|UsbManager|Ftx1|PKCS|privateKey|androidx\.compose|androidx\.room' "$CATALOG" "$TARGET" "$IMPORTER" || {
  echo "CP-0006G award core must remain credential-free, network-free, hardware-free, and UI-framework-free" >&2
  exit 1
}

echo "CP-0006G extended award catalog host gate: PASS"
