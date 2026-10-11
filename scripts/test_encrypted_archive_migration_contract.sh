#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
export JAVA_OPTS="-Xmx1536m -Xms128m"
find "$ROOT/core/src/main/kotlin" -name '*.kt' -print0 | sort -z | xargs -0 kotlinc -d "$BUILD/core.jar"
kotlinc "$ROOT/core/src/test/kotlin/dev/n0png/fieldops/core/EncryptedArchiveMigrationContractTests.kt" \
  -cp "$BUILD/core.jar" -include-runtime -d "$BUILD/tests.jar"
java -cp "$BUILD/tests.jar:$BUILD/core.jar" \
  dev.n0png.fieldops.core.EncryptedArchiveMigrationContractTests
SRC="$ROOT/core/src/main/kotlin/dev/n0png/fieldops/core/propagation/EncryptedArchiveMigrationContract.kt"
! grep -Eq 'javax\.crypto|Cipher\.|KeyStore|android\.|FileOutputStream|Files\.move|HttpURLConnection|URL\.open' "$SRC"
grep -Fq 'ENCRYPTED_EXECUTOR_NOT_IMPLEMENTED' "$SRC"
grep -Fq 'PropagationArchiveStorageCodec.decode' "$SRC"
echo "CP-0009I non-executing encryption migration contract: PASS"
