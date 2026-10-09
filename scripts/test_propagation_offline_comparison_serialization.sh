#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
echo "[1/5] Compile all platform-neutral core production Kotlin"
find "$ROOT/core/src/main/kotlin" -name '*.kt' -print0 | sort -z | xargs -0 kotlinc -d "$BUILD/core.jar"
echo "[2/5] Compile CP-0008T comparison export tests"
kotlinc "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/PropagationOfflineReportComparisonSerializationTests.kt" \
  -cp "$BUILD/core.jar" -include-runtime -d "$BUILD/tests.jar"
echo "[3/5] Run deterministic V1 export/negative tests"
java -cp "$BUILD/tests.jar:$BUILD/core.jar" dev.n0png.fieldops.core.PropagationOfflineReportComparisonSerializationTests
echo "[4/5] Verify no phone, store, network, crypto-auth or live clocks are introduced"
SRC="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineReportComparisonSerialization.kt"
! grep -Eq 'snapshotStore|refreshStateStore|\.latest\(|\.save\(|\.fetch\(|System.currentTimeMillis|Instant.now|android\.|androidx\.|UsbManager|WorkManager|URL\.open|OkHttp|javax\.net|java\.io\.File' "$SRC"
grep -Fq 'PropagationOfflineReportComparisonService.compare(' "$SRC"
grep -Fq 'originAuthenticated' "$SRC"
grep -Fq 'crossStoreAtomicityVerified' "$SRC"
grep -Fq 'CP-0003C remains **DEFERRED**' "$ROOT/research/propagation/CP-0008T_OFFLINE_COMPARISON_EXPORT_CONTRACT.md"
echo "[5/5] Preserve CP-0008P original V1 serializer"
grep -Fq 'const val WIRE_VERSION = 1' "$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineReportSerialization.kt"
grep -Fq 'fieldops.propagation.offline-diagnostic' "$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineReportSerialization.kt"
echo "CP-0008T offline comparison export host gate: PASS"
