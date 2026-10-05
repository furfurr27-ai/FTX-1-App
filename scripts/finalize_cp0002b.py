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
(root / "VERSION").write_text("v8-js8-native-tx\n")

readme_path = root / "README.md"
readme = readme_path.read_text()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** `CP-0002B-JS8_NATIVE_TX`

Parent: `CP-0002A-JS8_NATIVE_RX`.

CP-0002B is a **GREEN host/CI integration checkpoint with YELLOW hardware/RF status**. It adds the JS8 transmit boundary without claiming actual S23 Ultra / FTX-1 RF proof.

CP-0002B adds and proves:

- The pinned JS8 native TX audio tap is enabled while upstream rig/PTT ownership remains unused.
- `Js8TxController` queues native JS8 modulation with the native transmit gate closed.
- CAT PTT and USB TX audio are reachable only through the FieldOps-owned `Ftx1RadioSession` / `RadioModeArbiter` path.
- FieldOps pre-keys CAT PTT before opening the native transmit gate.
- Native TX PCM is statefully rate-adapted from its callback rate (11,520 Hz at the pinned upstream build) to the configured 48 kHz FTX-1 playback domain.
- Normal completion and failure/cancel/close paths attempt `TX0`, close TX audio and release radio ownership.
- Deterministic JS8 TX safety tests pass with **39 assertions**; JS8 RX remains green with **19 assertions**.
- The inherited core regression remains green: **42,062 core assertions, 56 pipeline assertions, 19 LoTW assertions**.
- The exact pinned ARM64 artifact was re-checked for required JS8 TX/lifecycle JNI exports.

Pinned upstream JS8 source:

`JS8Call-improved/Android-port@9996202f355569c5ee7b97fae539f3b763081dc2`

Exact upstream Android Build run checked: `36659533828`, conclusion **success**.

Exact checked artifact and TX-boundary evidence is recorded in `research/js8/CP-0002B_JS8_TX_INTEGRATION.md`.

- CP-0002B finalization workflow run: `{run_id}`

### Inherited verified ancestry

`CP-0002A-JS8_NATIVE_RX` remains the verified JS8 receive parent checkpoint. `CP-0001-GITHUB_SURVEY` remains its verified source/research ancestor.

### Hardware/RF boundary

Actual Galaxy S23 Ultra + FTX-1 USB enumeration/playback, real JS8 RF transmission, output-level/ALC calibration and RF spectral-quality validation are **not** proven by CP-0002B. Final Android AAR/APK packaging also remains a later task.

The older project-library `FTX1_FieldOps_CP-0002_JS8_NATIVE.zip` remains an inaccessible recovery/comparison lead and was not used as proof for this checkpoint.

"""

readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Engineering rules",
    baseline + "## Engineering rules",
    "verified durable baseline",
)

old_repo_note = "- Current Git source baseline before this finalization run: `CP-0002A-JS8_NATIVE_RX`"
if old_repo_note in readme:
    readme = readme.replace(
        old_repo_note,
        "- Current Git source baseline: `CP-0002B-JS8_NATIVE_TX`",
        1,
    )

next_section = """## Current exact next action

**CP-0002C — WSPR native RX only.**

1. Vendor the pinned pure-C WSPR decoder from `Guenael/rtlsdr-wsprd@1ca9b83dd2562ce9ef2453aacdd5bc3aab982c7d`.
2. Keep the shared 12 kHz FieldOps receive branch as the source.
3. Mix the WSPR passband to complex baseband, low-pass it and decimate to the decoder's 375 Hz complex-I/Q input.
4. Assemble the 120-second / 45,000-complex-sample WSPR decoder window without changing JS8 or FT-family timing.
5. Exit gate: pinned/native WSPR encoding fixture -> synthesized receive waveform -> FieldOps downconverter -> correct decoded message.
6. Do not implement WSPR TX in CP-0002C; that is CP-0002D.
7. Keep Android/radio hardware status YELLOW/RED until actual S23 Ultra + FTX-1 testing.

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

**CP-0002C — WSPR native RX only**

Vendor and integrate the pinned pure-C WSPR decoder from `Guenael/rtlsdr-wsprd@1ca9b83dd2562ce9ef2453aacdd5bc3aab982c7d`. Feed it from the existing 12 kHz receive branch through a complex-baseband mixer, low-pass filter and /32 decimation to 375 Hz. Prove the complete receive path with a pinned/native encoder fixture and synthesized WSPR waveform that decodes to the expected message.

Do **not** implement WSPR TX in this checkpoint.
"""
)

history_path = root / "checkpoints" / "CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0002B — JS8 native TX" not in history:
    history = history.rstrip() + "\n\n" + f"""## CP-0002B — JS8 native TX

Parent: `CP-0002A-JS8_NATIVE_RX`

FieldOps now captures the pinned JS8 native TX audio tap while retaining exclusive CAT/PTT/USB-audio ownership. Native modulation is gated until FieldOps acquires the JS8 radio owner and completes PTT lead, then callback PCM is statefully adapted to the 48 kHz playback domain. Normal completion and all tested error/cancel paths collapse to RX-safe state.

Host/CI gate: core 42,062; pipeline 56; LoTW 19; JS8 RX 19; JS8 TX 39 assertions, all PASS.

Finalization workflow run: `{run_id}`.

Evidence: `research/js8/CP-0002B_JS8_TX_INTEGRATION.md`.
"""
    history_path.write_text(history)
