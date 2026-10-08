#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
mapfile -t SOURCES < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
echo "[1/5] Compile all core production Kotlin"
kotlinc "${SOURCES[@]}" -d "$BUILD/core.jar"
echo "[2/5] Compile and run CP-0008N pure read-model tests"
kotlinc "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/PropagationReadModelConsistencyTests.kt" -cp "$BUILD/core.jar" -include-runtime -d "$BUILD/diagnostics.jar"
java -cp "$BUILD/diagnostics.jar:$BUILD/core.jar" dev.n0png.fieldops.core.PropagationReadModelConsistencyTests
echo "[3/5] Confirm diagnostics are derived only from captured operating picture"
SRC="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationReadModelConsistencyService.kt"
RUNTIME="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationRuntime.kt"
grep -Fq 'fun diagnose(picture: PropagationOperatingPicture)' "$SRC"
grep -Fq 'crossStoreAtomicityVerified: Boolean get() = false' "$SRC"
grep -Fq 'operatingPictureWithDiagnostics(' "$RUNTIME"
! grep -Eq 'store\.latest\(|stateStore\.all\(|\.save\(|\.fetch\(|refreshAndProject\(|System.currentTimeMillis|Instant.now|WorkManager|android\.|androidx\.' "$SRC"
echo "[4/5] Confirm source contract, existing operating picture and deferred rule"
EVIDENCE="$ROOT/research/propagation/CP-0008N_READ_MODEL_CONSISTENCY_DIAGNOSTICS.md"
grep -Fq 'cross-store atomicity' "$EVIDENCE"
grep -Fq 'CP-0003C remains **DEFERRED**' "$EVIDENCE"
echo "[5/5] Check disallowed runtime/device dependencies"
! grep -Eq 'UsbManager|BluetoothAdapter|RadioSession|PTT|LoTW|privateKey|credential' "$SRC"
echo "CP-0008N consistency diagnostics host gate: PASS"
