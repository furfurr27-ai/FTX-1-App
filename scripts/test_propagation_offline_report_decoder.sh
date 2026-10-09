#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
echo "[1/5] Compile all platform-neutral core production Kotlin"
find "$ROOT/core/src/main/kotlin" -name '*.kt' -print0 | sort -z | xargs -0 kotlinc -d "$BUILD/core.jar"
echo "[2/5] Compile CP-0008Q tests"
kotlinc "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/PropagationOfflineReportDecoderTests.kt" -cp "$BUILD/core.jar" -include-runtime -d "$BUILD/test.jar"
echo "[3/5] Run strict decoding fixtures"
java -cp "$BUILD/test.jar:$BUILD/core.jar" dev.n0png.fieldops.core.PropagationOfflineReportDecoderTests
echo "[4/5] Preserve CP-0008P canonical golden fixture and regression"
kotlinc "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/PropagationOfflineReportSerializationTests.kt" -cp "$BUILD/core.jar" -include-runtime -d "$BUILD/prior.jar"
java -cp "$BUILD/prior.jar:$BUILD/core.jar" dev.n0png.fieldops.core.PropagationOfflineReportSerializationTests
echo "[5/5] Enforce pure-offline implementation and deferred owner rule"
SRC="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineReportDecoder.kt"
! grep -Eq 'snapshotStore|refreshStateStore|\.latest\(|\.all\(|\.save\(|\.fetch\(|System.currentTimeMillis|Instant.now|android\.|androidx\.|UsbManager|WorkManager|URL\.open' "$SRC"
grep -Fq 'CP-0003C remains **DEFERRED**' "$ROOT/research/propagation/CP-0008Q_OFFLINE_REPORT_DECODE_VALIDATION.md"
grep -Fq 'const val WIRE_VERSION = 1' "$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineReportSerialization.kt"
echo "CP-0008Q strict decode host gate: PASS"
