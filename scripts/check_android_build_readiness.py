#!/usr/bin/env python3
"""Static Android build prerequisites; NOT an APK compile or device test."""
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
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--expect-not-ready", action="store_true")
    mode.add_argument("--expect-ready", action="store_true")
    args = parser.parse_args()
    checks = {}
    for label, paths in REQUIREMENTS.items():
        present = (all((ROOT / path).is_file() for path in paths)
                   if label == "gradle_wrapper" else
                   any((ROOT / path).is_file() for path in paths))
        checks[label] = {"present": present, "paths_checked": list(paths)}
    ready = all(row["present"] for row in checks.values())
    print(json.dumps({
        "checkpoint": "CP-0009C/CP-0009D",
        "kind": "REPOSITORY_STATIC_PREREQUISITE_AUDIT_NOT_A_BUILD",
        "status": "STATIC_PREREQUISITES_PRESENT_UNVERIFIED" if ready else "NOT_READY",
        "can_claim_installable_apk": False,
        "checks": checks,
        "missing": sorted(key for key, row in checks.items() if not row["present"]),
        "boundaries": [
            "Static file checks are NOT Android compilation or APK artifact proof.",
            "Android packaging presence is NOT archive engine Activity wiring.",
            "No Galaxy S23 Ultra, FTX-1, CAT/audio, RF, account or certificate proof.",
        ],
    }, indent=2, sort_keys=True))
    if args.expect_not_ready and ready:
        raise SystemExit("Expected packaging deficit but scaffolding now exists")
    if args.expect_ready and not ready:
        raise SystemExit("Android app packaging prerequisites missing")

if __name__ == "__main__":
    main()
