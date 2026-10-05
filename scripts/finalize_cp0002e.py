#!/usr/bin/env python3
import os
import pathlib
import re


def replace_one(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected exactly one {label} section, found {count}")
    return updated


root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
(root / "VERSION").write_text("v11-native-modes-regression\n")

readme_path = root / "README.md"
readme = readme_path.read_text()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** `CP-0002E-NATIVE_MODE_REGRESSION`

Parent: `CP-0002D-WSPR_NATIVE_TX`.

CP-0002E is a **GREEN host/CI native-mode regression checkpoint with YELLOW/RED Android/device/RF status**. It does not add another operating mode. Its purpose is to prove that the already-integrated FT8/FT4/FT2, JS8, WSPR and APRS paths remain independently testable and still compose correctly around the shared audio/timing/TX-ownership architecture.

CP-0002E independently proves:

- FT8 adapter regression: **20/20 PASS**.
- FT4 adapter regression: **20/20 PASS**.
- FT2 adapter regression: **20/20 PASS**.
- JS8 RX: **19/19 PASS** and JS8 TX: **39/39 PASS**.
- WSPR RX: **21/21 PASS** and WSPR TX: **56/56 PASS**.
- APRS/AX.25/KISS/Bell-202/SmartBeaconing regression: **2,048/2,048 PASS**.
- Shared native-mode composition: **130/130 PASS**.
- Inherited core: **42,062 PASS**, pipeline: **56 PASS**, LoTW: **19 PASS**.
- The common 48 kHz capture / 12 kHz weak-signal fanout still preserves JS8 continuous-stream behavior and FT-family UTC-window behavior.
- FT8, FT4, FT2, JS8, WSPR and APRS TX owners cannot steal a radio already transmitting for another owner.
- The common `Ftx1RadioSession` still produces the same guarded `TX1 -> audio -> TX0 -> release` lifecycle for all six mode owners.

### FT-family host-test boundary

The public repository intentionally omits the extracted ARM64 `libft8af.so`. CP-0002E therefore compiles the real production Java ABI declarations and `FtFamilyNativeEngine`, then executes the production Kotlin adapter against deterministic host-only ABI fixtures. This verifies adapter routing/mapping and FT8/FT4/FT2 symbol/timing/waveform-selection logic, but **does not** claim x86_64 execution of the actual FT8AF native DSP binary.

The omitted historical ARM64 binary remains recorded as SHA-256:

`858a6ab58bb89bbc3e9f9e81effb899a03c121c9cccdc81b2803440e97d348a1`

Pinned/reference sources retained through this checkpoint:

- `patrickrb/FT8AF@c2f63e8b37fcd484fd2eb2049494425dd2414971`
- `JS8Call-improved/Android-port@9996202f355569c5ee7b97fae539f3b763081dc2`
- `Guenael/rtlsdr-wsprd@1ca9b83dd2562ce9ef2453aacdd5bc3aab982c7d`

Full regression evidence is recorded in `research/CP-0002E_NATIVE_MODE_REGRESSION.md`.

- CP-0002E finalization workflow run: `{run_id}`

### Inherited verified ancestry

`CP-0002D-WSPR_NATIVE_TX`, `CP-0002C-WSPR_NATIVE_RX`, `CP-0002B-JS8_NATIVE_TX`, `CP-0002A-JS8_NATIVE_RX`, and `CP-0001-GITHUB_SURVEY` remain verified ancestry.

### Android/device/RF boundary

CP-0002E is a host/CI regression checkpoint, not a hardware checkpoint. Actual Galaxy S23 Ultra execution, FTX-1 CAT/USB enumeration and routing, FT-family native decode on the phone, JS8 native execution on the phone, Android arm64 WSPR/FFTW3 packaging, real APRS off-air reception, and RF TX/ALC/spectral/watchdog behavior remain unverified.

"""

readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Engineering rules",
    baseline + "## Engineering rules",
    "verified durable baseline",
)

readme = readme.replace(
    "- Current Git source baseline: `CP-0002D-WSPR_NATIVE_TX`",
    "- Current Git source baseline: `CP-0002E-NATIVE_MODE_REGRESSION`",
    1,
)

next_section = """## Current exact next action

**CP-0003A — TrustedQSL signer bridge.**

1. Pin the official TrustedQSL source/version before implementing the signer.
2. Build a narrow JNI boundary for PKCS#12 certificate import, explicit station-location selection, and ADIF -> signed GABBI/TQ8 generation.
3. Keep certificate material, PKCS#12 passwords and signing secrets out of logs, crash reports, support bundles and this public repository.
4. Fail closed if the signer/certificate/station location is unavailable or ambiguous; do not fall back to unsigned LoTW upload.
5. Keep signing separate from HTTP upload/reconciliation so CP-0003B can make the full LoTW transaction atomic.
6. Do not enable automatic LoTW upload until later real-device/test-account validation passes.

"""

readme = replace_one(
    readme,
    r"## Current exact next action\n.*?\n## README maintenance contract",
    next_section + "## README maintenance contract",
    "current exact next action",
)
readme_path.write_text(readme)

(root / "NEXT_ACTION.md").write_text(
    """# NEXT ACTION — FTX-1 FieldOps

**CP-0003A — TrustedQSL signer bridge**

Pin the official TrustedQSL source/version. Add a narrow JNI API for PKCS#12 certificate import, explicit station-location selection, and ADIF -> signed GABBI/TQ8 output. Fail closed if signing material or location is unavailable/ambiguous, and never write certificates, passwords or keys to logs or this public repository.

Keep signing separate from upload/reconciliation. CP-0003B will own the transaction-safe sign -> upload -> verify -> duplicate-state commit flow. Automatic LoTW upload remains disabled until later real-device/test-account validation.
"""
)

history_path = root / "checkpoints" / "CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0002E — Native-mode regression" not in history:
    history = history.rstrip() + "\n\n" + f"""## CP-0002E — Native-mode regression

Parent: `CP-0002D-WSPR_NATIVE_TX`

Independent host/CI gates now cover FT8, FT4, FT2, JS8, WSPR and APRS, followed by a shared composition test for the 48 kHz / 12 kHz audio split, continuous-vs-windowed timing and the common radio TX owner. This checkpoint adds regression evidence rather than a new operating mode.

Host/CI gate: core 42,062; pipeline 56; LoTW 19; FT8 20; FT4 20; FT2 20; JS8 RX 19; JS8 TX 39; WSPR RX 21; WSPR TX 56; APRS 2,048; composition 130 assertions, all PASS.

The public repository still omits the extracted ARM64 FT8AF native binary, so FT-family host execution uses deterministic ABI fixtures after separately compiling the production ABI declarations/adapter. Actual phone/radio proof remains hardware-gated.

Finalization workflow run: `{run_id}`.

Evidence: `research/CP-0002E_NATIVE_MODE_REGRESSION.md`.
"""
    history_path.write_text(history)
