#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
echo "[1/5] Compile all platform-neutral core production Kotlin"
find "$ROOT/core/src/main/kotlin" -name '*.kt' -print0 | sort -z | xargs -0 kotlinc -d "$BUILD/core.jar"
echo "[2/5] Compile CP-0008R synthetic import tests"
kotlinc "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/PropagationOfflineReportImportTests.kt" -cp "$BUILD/core.jar" -include-runtime -d "$BUILD/tests.jar"
echo "[3/5] Run import boundary and tamper/non-authentication tests"
java -cp "$BUILD/tests.jar:$BUILD/core.jar" dev.n0png.fieldops.core.PropagationOfflineReportImportTests
echo "[4/5] Enforce untrusted, offline-only boundary and deferred execution"
SRC="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineReportImportService.kt"
! grep -Eq 'snapshotStore|refreshStateStore|\.latest\(|\.save\(|\.fetch\(|System.currentTimeMillis|Instant.now|android\.|androidx\.|UsbManager|WorkManager|URL\.open|OkHttp|javax\.net|java\.io\.File' "$SRC"
grep -Fq 'PropagationOfflineReportDecoder.decode(encoded)' "$SRC"
grep -Fq 'originAuthenticated = false' "$SRC"
grep -Fq 'crossStoreAtomicityVerified = false' "$SRC"
grep -Fq 'CP-0003C remains **DEFERRED**' "$ROOT/research/propagation/CP-0008R_OFFLINE_REPORT_IMPORT_BOUNDARY.md"
echo "[5/5] Preserve version 1 serializer/decode contracts"
grep -Fq 'const val WIRE_VERSION = 1' "$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineReportSerialization.kt"
grep -Fq 'fun decode(encoded: PropagationOfflineSerializedReport)' "$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineReportDecoder.kt"
echo "CP-0008R offline report import host gate: PASS"
