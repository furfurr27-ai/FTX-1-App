#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
export JAVA_OPTS="-Xmx1536m -Xms128m"
find "$ROOT/core/src/main/kotlin" -name '*.kt' -print0 | sort -z | xargs -0 kotlinc -d "$BUILD/core.jar"
kotlinc "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/AuthenticatedArchiveEnvelopeV2Tests.kt" \
  -cp "$BUILD/core.jar" -include-runtime -d "$BUILD/tests.jar"
java -cp "$BUILD/tests.jar:$BUILD/core.jar" \
  dev.n0png.fieldops.core.AuthenticatedArchiveEnvelopeV2Tests
echo "CP-0009J AES-GCM host envelope contract: PASS"
