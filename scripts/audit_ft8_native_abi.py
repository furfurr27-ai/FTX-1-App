#!/usr/bin/env python3
"""Fail-closed static audit for from-source FT8/FT4/FT2 JNI decode DTO."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
native = root / "app/src/main/cpp/third_party/ft8af/ft8af_glue"
java = root / "android/compat/src/main/java/com/k1af/ft8af/Ft8Message.java"
s = java.read_text()
types = {"Z":"boolean", "I":"int", "F":"float", "J":"long",
         "Ljava/lang/String;":"String"}
public = {name: typ for typ,name in re.findall(
    r"\bpublic\s+(boolean|int|float|long|String)\s+(\w+)\b", s)}
need = {}
for src in ("ft8_decode_jni.cpp", "ft2_decode_jni.cpp"):
    text = (native / src).read_text()
    for name, sig in re.findall(
        r'GetFieldID\(c,\s*"([^"]+)",\s*"([^"]+)"\)', text):
        expected = types.get(sig)
        if expected is None:
            raise SystemExit(f"Unreviewed JNI signature {src} {name} {sig}")
        if public.get(name) != expected:
            raise SystemExit(f"JNI DTO mismatch: {src} requires {name}: {expected}, "
                             f"Java exposes {public.get(name)!r}")
        need[name] = expected
if len(need) < 20:
    raise SystemExit(f"Incomplete decoder JNI ABI contract: only {len(need)} fields")
print(f"FT-family JNI decoder DTO field lookups match {len(need)} public fields: PASS")

# Check JNI public methods that the original FieldOps adapter invokes.
listener = (root / "android/compat/src/main/java/com/k1af/ft8af/ft8listener/"
            "FT8SignalListener.java").read_text()
required = ("InitDecoder", "InitDecoderFt2", "DecoderMonitorPressFloat",
            "DecoderFt2MonitorPressFloat", "DecoderFt8FindSync",
            "DecoderFt2FindSync", "DecoderFt8Analysis",
            "DecoderFt2Analysis", "DeleteDecoder", "DeleteDecoderFt2")
for method in required:
    if not re.search(r"\bnative\s+\w+\s+" + method + r"\s*\(",listener):
        raise SystemExit(f"FieldOps JNI Java method missing: {method}")
print("FT-family Java decoder JNI method surface present: PASS")
