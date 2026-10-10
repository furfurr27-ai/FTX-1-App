#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
echo "[1/5] Compile platform-neutral core"
find "$ROOT/core/src/main/kotlin" -name '*.kt' -print0 | sort -z | xargs -0 kotlinc -d "$BUILD/core.jar"
echo "[2/5] Compile host-facing archive workspace tests"
kotlinc "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/PropagationArchiveWorkspaceTests.kt" -cp "$BUILD/core.jar" -include-runtime -d "$BUILD/tests.jar"
echo "[3/5] Run synthetic accessible history/selection/eviction tests"
java -cp "$BUILD/tests.jar:$BUILD/core.jar" dev.n0png.fieldops.core.PropagationArchiveWorkspaceTests
echo "[4/5] Enforce no platform I/O or live RF claims"
SRC="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationArchiveWorkspaceService.kt"
! grep -Eq 'android\.|androidx\.|UsbManager|System.currentTimeMillis|Instant.now|URL\.open|OkHttp|java\.io\.File|FileOutputStream|java\.net\.' "$SRC"
grep -Fq 'PropagationOfflineReportArchiveService.validate(' "$SRC"
grep -Fq 'PropagationWorkspaceHistoryService.capture(' "$SRC"
grep -Fq 'ARCHIVED DATA ONLY:' "$SRC"
echo "[5/5] Explicit Android packaging gap audit, not an APK claim"
python3 "$ROOT/scripts/check_android_build_readiness.py" --expect-not-ready
echo "CP-0009C host archive workspace and build-gap gate: PASS"
