#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
mapfile -t SOURCES < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
echo "[1/5] Compile all platform-neutral core production Kotlin"
kotlinc "${SOURCES[@]}" -d "$BUILD/core.jar"
echo "[2/5] Compile and run offline diagnostic report tests"
kotlinc "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/PropagationOfflineDiagnosticReportTests.kt" -cp "$BUILD/core.jar" -include-runtime -d "$BUILD/report-tests.jar"
java -cp "$BUILD/report-tests.jar:$BUILD/core.jar" dev.n0png.fieldops.core.PropagationOfflineDiagnosticReportTests
echo "[3/5] Verify pure report DTO and explicit runtime entry point"
SRC="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineDiagnosticReportService.kt"
RUNTIME="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationRuntime.kt"
grep -Fq 'fun build(composed: PropagationOperatingPictureWithDiagnostics)' "$SRC"
grep -Fq 'crossStoreAtomicityVerified: Boolean = false' "$SRC"
grep -Fq 'fun offlineDiagnosticReport(' "$RUNTIME"
grep -Fq 'operatingPictureWithDiagnostics(query)' "$RUNTIME"
echo "[4/5] Block independent provider/store/device activities in report DTO"
! grep -Eq 'snapshotStore|refreshStateStore|\.latest\(|\.all\(|\.save\(|\.fetch\(|refreshAndProject\(|System.currentTimeMillis|Instant.now|WorkManager|android\.|androidx\.|UsbManager' "$SRC"
echo "[5/5] Confirm evidence limitations and deferred override"
EVIDENCE="$ROOT/research/propagation/CP-0008O_OFFLINE_DIAGNOSTIC_REPORT_PAYLOAD.md"
grep -Fq 'offline payload' "$EVIDENCE"
grep -Fq 'CP-0003C remains **DEFERRED**' "$EVIDENCE"
echo "CP-0008O offline report host gate: PASS"
