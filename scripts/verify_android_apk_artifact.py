#!/usr/bin/env python3
"""Reject a missing, corrupt, or placeholder Android APK; CI artifact shape only."""
from __future__ import annotations
import argparse
import pathlib
import zipfile

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("apk")
    args = parser.parse_args()
    apk = pathlib.Path(args.apk)
    if not apk.is_file() or apk.stat().st_size < 16_384:
        raise SystemExit("Not a nonempty generated Android APK")
    with zipfile.ZipFile(apk) as bundle:
        names = set(bundle.namelist())
        required = {"AndroidManifest.xml", "classes.dex", "resources.arsc"}
        if not required.issubset(names):
            raise SystemExit("Incomplete APK ZIP: " + ", ".join(sorted(required - names)))
        if bundle.testzip() is not None:
            raise SystemExit("APK ZIP member failed integrity check")
        for name in required:
            if bundle.getinfo(name).file_size < 64:
                raise SystemExit("Truncated APK entry: " + name)
    print("CP-0009D APK archive verification PASS; bytes=" + str(apk.stat().st_size))
    print("Boundary: ZIP structure is not phone-install, app-launch, RF or CAT verification.")

if __name__ == "__main__":
    main()
