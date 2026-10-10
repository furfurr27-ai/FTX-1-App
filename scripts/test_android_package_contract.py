#!/usr/bin/env python3
"""Static CP-0009D safety: a packaged offline shell cannot quietly acquire RF privileges."""
from __future__ import annotations
import pathlib
import re
import xml.etree.ElementTree as ET

root = pathlib.Path(__file__).resolve().parents[1]
manifest = root / "app/src/main/AndroidManifest.xml"
source = root / "app/src/main/java/dev/n0png/fieldops/app/MainActivity.java"
appgradle = root / "app/build.gradle.kts"
app = ET.parse(manifest).getroot()
android = "{http://schemas.android.com/apk/res/android}"
if app.findall("uses-permission"):
    raise SystemExit("CP-0009D shell must declare NO runtime permissions")
if app.findall("uses-feature"):
    raise SystemExit("CP-0009D shell must not depend on radio hardware")
activity = app.find("application/activity")
if activity is None or activity.get(android + "exported") != "true":
    raise SystemExit("Missing explicit launcher Activity export")
if not any(a.get(android + "name") == "android.intent.action.MAIN"
           for a in activity.findall("intent-filter/action")):
    raise SystemExit("Missing Android MAIN launcher intent")
text = source.read_text()
for token in ("startService(", "sendBroadcast(", "UsbManager", "AudioRecord(",
              "requestPermissions(", "getSystemService(", "HttpURLConnection",
              "DatagramSocket", "android.permission"):
    if token in text:
        raise SystemExit("Unexpected integration beyond offline packaging: " + token)
if "does not connect to a radio" not in text:
    raise SystemExit("Missing explicit no-radio developer UI notice")
gradle = appgradle.read_text()
for field in ("applicationId = \"dev.n0png.fieldops\"", "compileSdk = 35",
              "targetSdk = 35", "versionCode = 55"):
    if field not in gradle:
        raise SystemExit("Android packaging contract missing: " + field)
print("CP-0009D offline application shell/manifest safety contract: PASS")
