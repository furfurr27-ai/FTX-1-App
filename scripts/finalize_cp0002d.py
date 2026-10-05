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
(root / "VERSION").write_text("v10-wspr-native-tx\n")

readme_path = root / "README.md"
readme = readme_path.read_text()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** `CP-0002D-WSPR_NATIVE_TX`

Parent: `CP-0002C-WSPR_NATIVE_RX`.

CP-0002D is a **GREEN host/CI WSPR transmit-integration checkpoint with YELLOW Android/device/RF status**. It promotes the exact pinned upstream WSPR channel-symbol encoder to the production TX codec boundary, synthesizes the complete WSPR waveform in FieldOps' 12 kHz modem domain, and routes all WSPR transmit audio through the common FieldOps radio owner.

CP-0002D adds and proves:

- Message packing/channel symbols come from `Guenael/rtlsdr-wsprd@1ca9b83dd2562ce9ef2453aacdd5bc3aab982c7d`; FieldOps does not create a second WSPR message codec.
- Production WSPR TX uses **162 channel symbols**, **8,192 samples/symbol at 12 kHz**, **1.46484375 Hz tone spacing**, **1,327,104 total samples**, and **110.592 seconds** of encoded waveform.
- FieldOps keeps one phase accumulator across symbol boundaries for continuous-phase 4-FSK.
- A production waveform generated from the pinned native encoder is independently decoded through the pinned native WSPR RX path as `K1JT FN20 20`.
- `WsprTxController` has no direct CAT/PTT or USB-audio path; it can transmit only through `Ftx1RadioSession` with `Owner.WSPR`.
- A competing JS8 transmitter cannot be stolen or disturbed by WSPR.
- Normal completion, cancellation and injected audio failure return to RX-safe state with `TX0`, audio closed and WSPR ownership released.
- Malformed nonempty WSPR input fails before PTT, TX audio open or radio ownership.
- WSPR TX tests pass **56/56**; WSPR RX regression passes **21/21**.
- Inherited JS8 RX/TX regression remains green: **19 RX assertions and 39 TX assertions**.
- Inherited core regression remains green: **42,062 core assertions, 56 pipeline assertions and 19 LoTW assertions**.

Pinned upstream WSPR source:

`Guenael/rtlsdr-wsprd@1ca9b83dd2562ce9ef2453aacdd5bc3aab982c7d`

Exact implementation and test evidence is recorded in `research/wspr/CP-0002D_WSPR_TX_INTEGRATION.md`.

- CP-0002D finalization workflow run: `{run_id}`

### Inherited verified ancestry

`CP-0002C-WSPR_NATIVE_RX` remains the verified WSPR receive parent checkpoint. `CP-0002B-JS8_NATIVE_TX`, `CP-0002A-JS8_NATIVE_RX`, and `CP-0001-GITHUB_SURVEY` remain verified ancestry.

### Android/device/RF boundary

Actual Android arm64-v8a WSPR + FFTW3 packaging is still unverified. The host checkpoint also does not prove whether the FTX-1 USB output endpoint should receive 12 kHz directly or through a final device-rate adapter.

Actual Galaxy S23 Ultra execution, FTX-1 TX audio routing/level, WSPR UTC beacon scheduling, RF power/ALC, spectral purity, frequency accuracy and off-air transmission remain unverified. CP-0002D therefore proves software codec/waveform/TX ownership behavior, not real RF operation.

"""

readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Engineering rules",
    baseline + "## Engineering rules",
    "verified durable baseline",
)

readme = readme.replace(
    "- Current Git source baseline: `CP-0002C-WSPR_NATIVE_RX`",
    "- Current Git source baseline: `CP-0002D-WSPR_NATIVE_TX`",
    1,
)

tx_insert = """For WSPR TX:

`message -> pinned get_wspr_channel_symbols() -> 162 symbols -> continuous-phase 12 kHz 4-FSK -> WsprTxController -> Ftx1RadioSession`

The WSPR modem layer never owns physical PTT. The current host-proven waveform is 1,327,104 samples / 110.592 seconds at 12 kHz; final device-rate handling remains hardware-gated.

"""
anchor = "### TX ownership\n\n"
if tx_insert not in readme:
    if anchor not in readme:
        raise SystemExit("Expected TX ownership section was not found")
    readme = readme.replace(anchor, anchor + tx_insert, 1)

next_section = """## Current exact next action

**CP-0002E — Native-mode regression.**

1. Run the FT8/FT4/FT2, JS8, WSPR and APRS regression families separately.
2. Keep the suites independent so a failure in one mode family does not hide or invalidate already-proven work in another.
3. Verify the shared 48 kHz / 12 kHz signal paths, slot/continuous timing boundaries and common TX ownership still compose without regressions.
4. Create one immutable `CP-0002E` native-modes checkpoint only after every required regression family is green.
5. Do not begin CP-0003A TrustedQSL signer work until CP-0002E is durable.
6. Continue to label Android/FTX-1 hardware and RF status separately from host/CI status.

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

**CP-0002E — Native-mode regression**

Run the FT8/FT4/FT2, JS8, WSPR and APRS regression families separately. Keep their gates independent, verify the shared audio/timing/TX-ownership composition, and create one immutable native-modes checkpoint only after every required family is green.

Do **not** begin CP-0003A TrustedQSL signer integration until CP-0002E is durable. Android/FTX-1 device and RF status remain separately hardware-gated.
"""
)

history_path = root / "checkpoints" / "CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0002D — WSPR native TX" not in history:
    history = history.rstrip() + "\n\n" + f"""## CP-0002D — WSPR native TX

Parent: `CP-0002C-WSPR_NATIVE_RX`

FieldOps now uses the pinned upstream WSPR channel-symbol encoder as the production message-codec boundary and synthesizes the full continuous-phase 12 kHz 4-FSK waveform. WSPR transmission is chunked through the common `Ftx1RadioSession` under `Owner.WSPR`; no WSPR modem code owns CAT/PTT or USB audio directly. Competing-owner, cancel, audio-failure and malformed-input paths are proven fail-closed.

Host/CI gate: core 42,062; pipeline 56; LoTW 19; WSPR RX 21; WSPR TX 56; JS8 RX 19; JS8 TX 39 assertions, all PASS.

Finalization workflow run: `{run_id}`.

Evidence: `research/wspr/CP-0002D_WSPR_TX_INTEGRATION.md`.
"""
    history_path.write_text(history)
