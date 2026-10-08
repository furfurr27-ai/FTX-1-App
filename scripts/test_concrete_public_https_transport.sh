#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

MAIN_JAR="$BUILD/fieldops-core-main.jar"
TEST_JAR="$BUILD/cp0008i-https-transport-tests.jar"

TRANSPORT="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/HttpsUrlConnectionPublicPropagationTransport.kt"
SOURCES="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PublicPropagationSourceAdapters.kt"
TEST="$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/HttpsUrlConnectionPublicPropagationTransportTests.kt"
EVIDENCE="$ROOT/research/propagation/CP-0008I_CONCRETE_PUBLIC_HTTPS_TRANSPORT.md"

echo "[1/8] Compile FieldOps core main"
mapfile -t CORE_MAIN < <(find "$ROOT/core/src/main/kotlin" -name '*.kt' | sort)
kotlinc "${CORE_MAIN[@]}" -d "$MAIN_JAR"

echo "[2/8] Compile CP-0008I tests separately"
kotlinc "$TEST" -cp "$MAIN_JAR" -include-runtime -d "$TEST_JAR"

echo "[3/8] Run deterministic concrete HTTPS transport tests"
java -cp "$TEST_JAR:$MAIN_JAR" dev.n0png.fieldops.core.HttpsUrlConnectionPublicPropagationTransportTests

echo "[4/8] Enforce HTTPS method timeout and redirect policy"
grep -Fq 'HttpsURLConnection' "$TRANSPORT"
grep -Fq 'requestMethod = "GET"' "$TRANSPORT"
grep -Fq 'instanceFollowRedirects = false' "$TRANSPORT"
grep -Fq 'connectTimeout = config.connectTimeoutMillis' "$TRANSPORT"
grep -Fq 'readTimeout = config.readTimeoutMillis' "$TRANSPORT"

echo "[5/8] Enforce bounded streaming and strict UTF-8"
grep -Fq 'contentLength > maximumBytes.toLong()' "$TRANSPORT"
grep -Fq 'if (total > maximumBytes)' "$TRANSPORT"
grep -Fq 'CodingErrorAction.REPORT' "$TRANSPORT"
grep -Fq 'Accept-Encoding", "identity"' "$TRANSPORT"
grep -Fq 'Unsupported public propagation Content-Encoding' "$TRANSPORT"

echo "[6/8] Enforce cleanup and retryability bridge"
grep -Fq 'connection.disconnect()' "$TRANSPORT"
grep -Fq 'PublicPropagationTransportException' "$TRANSPORT"
grep -Fq 'retryable = e.retryable' "$SOURCES"

echo "[7/8] Enforce platform/account/radio separation"
! grep -Eq 'android\.|androidx\.|WorkManager|OkHttp|Retrofit|Compose|UsbManager|Ftx1|RadioSession|PTT|apiKey|password|PKCS|privateKey'   "$TRANSPORT" || {
    echo "CP-0008I concrete transport must remain Android-framework/account/radio independent" >&2
    exit 1
  }

echo "[8/8] Verify evidence boundary and owner override"
grep -Fq 'deterministic injectable HttpsURLConnection tests' "$EVIDENCE"
grep -Fq 'Bounded streaming reads' "$EVIDENCE"
grep -Fq 'strict UTF-8' "$EVIDENCE"
grep -Fq 'CP-0003C remains **DEFERRED**' "$EVIDENCE"

echo "CP-0008I concrete public HTTPS transport host gate: PASS"
