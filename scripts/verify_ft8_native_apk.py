#!/usr/bin/env python3
"""Verify the APK actually embeds the source-built ARM64 FT8/FT4/FT2 codec."""
from pathlib import Path
from zipfile import ZipFile
import hashlib
import struct
import sys

apk = Path(sys.argv[1])
if not apk.is_file():
    raise SystemExit(f"Missing built APK: {apk}")
with ZipFile(apk) as archive:
    libraries = [n for n in archive.namelist() if n.endswith("/libft8af.so")]
    if libraries != ["lib/arm64-v8a/libft8af.so"]:
        raise SystemExit(f"Expected one S23 Ultra arm64 FT8AF library: {libraries}")
    raw = archive.read(libraries[0])
if raw[:4] != b"\x7fELF" or raw[4] != 2 or raw[5] != 1:
    raise SystemExit("JNI library is not a little-endian ELF64")
machine = struct.unpack_from("<H", raw, 18)[0]
if machine != 183:
    raise SystemExit(f"ELF machine {machine} is not AARCH64")
needed = (
    b"Java_com_k1af_ft8af_ft8listener_FT8SignalListener_InitDecoder",
    b"Java_com_k1af_ft8af_ft8listener_FT8SignalListener_InitDecoderFt2",
    b"Java_com_k1af_ft8af_ft8transmit_GenerateFT8_packFreeTextTo77",
)
for symbol in needed:
    if symbol not in raw:
        raise SystemExit(f"Expected native JNI symbol absent: {symbol.decode()}")
print(f"FT8/FT4/FT2 ARM64 native JNI package: PASS bytes={len(raw)} "
      f"sha256={hashlib.sha256(raw).hexdigest()}")
