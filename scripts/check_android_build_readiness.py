#!/usr/bin/env python3
"""CP-0009C: truthful, deterministic Android APK build-readiness GAP audit.

This is not a build. It never asserts an APK exists or that a physical radio,
phone, USB CAT/audio, certificate, RF chain or Android UI was tested.
"""
from __future__ import annotations
import argparse
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
REQUIREMENTS = {
    "gradle_wrapper": ("gradlew", "gradle/wrapper/gradle-wrapper.jar",
                       "gradle/wrapper/gradle-wrapper.properties"),
    "gradle_settings": ("settings.gradle.kts", "settings.gradle"),
    "application_module": ("app/build.gradle.kts", "app/build.gradle"),
    "android_manifest": ("app/src/main/AndroidManifest.xml",),
    "installable_apk_ci_workflow": (".github/workflows/build-android-apk.yml",),
}

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--expect-not-ready", action="store_true")
    args = parser.parse_args()
    checks = {}
    for label, paths in REQUIREMENTS.items():
        if label == "gradle_wrapper":
            present = all((ROOT / path).is_file() for path in paths)
        else:
            present = any((ROOT / path).is_file() for path in paths)
        checks[label] = {"present": present, "paths_checked": list(paths)}
    ready = all(row["present"] for row in checks.values())
    audit = {
        "checkpoint": "CP-0009C",
        "kind": "REPOSITORY_STATIC_GAP_AUDIT_NOT_BUILD",
        "status": "STATIC_PREREQUISITES_PRESENT_UNVERIFIED" if ready else "NOT_READY",
        "can_claim_installable_apk": False,
        "checks": checks,
        "missing": sorted(key for key, row in checks.items() if not row["present"]),
        "boundaries": [
            "Static file checks are not Gradle/Android compilation or an APK artifact.",
            "Host-facing archive models are not an implemented Android Activity.",
            "No Galaxy S23 Ultra, FTX-1, CAT/audio, RF, account or certificate proof.",
        ],
    }
    print(json.dumps(audit, indent=2, sort_keys=True))
    if args.expect_not_ready and ready:
        raise SystemExit("The CP-0009C expected-gap fixture is stale; replace with a real Android APK CI gate.")

if __name__ == "__main__":
    main()
