#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$ROOT/core/build-test"
rm -rf "$BUILD" && mkdir -p "$BUILD"
mapfile -t SRC < <(find "$ROOT/core/src/main/kotlin" "$ROOT/core/src/test/kotlin" -name '*.kt' | sort)
kotlinc "${SRC[@]}" -include-runtime -d "$BUILD/fieldops-core-tests.jar"
java -jar "$BUILD/fieldops-core-tests.jar"
