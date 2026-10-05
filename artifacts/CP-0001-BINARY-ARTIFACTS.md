# CP-0001 binary/build artifacts not imported to the public Git tree

The original verified recovery package was:

`FTX1_FieldOps_v6_GitHub_reference_checkpoint.zip`

Before GitHub initialization its checkpoint verifier reported **PASS** for `CP-0001-GITHUB_SURVEY` with **157 file hashes verified**.

This repository is currently public, and the original project provenance notes say the project was intended for one private device and that bundled native-code redistribution obligations require review. For that reason, generated build output and third-party/native binary artifacts were deliberately not copied into the public Git tree during the source import.

The source/checkpoint text state is preserved separately and restored into the working tree. The original checkpoint manifest remains for provenance, but the original verifier should not be expected to pass against this public Git checkout because the binary snapshot/build products below are intentionally absent.

## Important omitted artifacts

| Original path | Size | SHA-256 | Reason |
|---|---:|---|---|
| `android/app/src/main/jniLibs/arm64-v8a/libft8af.so` | 12,261,976 | `858a6ab58bb89bbc3e9f9e81effb899a03c121c9cccdc81b2803440e97d348a1` | Extracted native binary; redistribution/license review required |
| `checkpoints/snapshots/CP-0001-GITHUB_SURVEY.tar.gz` | 13,980,914 | `115829a241e090e7c4927e1b721f04520d3a5d130da1e4ef607e289654bb1219` | Immutable recovery snapshot already verified before import; large binary archive |
| `core/build-test/fieldops-core-tests.jar` | 5,120,792 | `1cd3b60a3b460286385e6f7425207433d21c83e9fd55edc87e8b0e204378d205` | Generated test output |
| `core/build-test-v3/fieldops-pipeline-tests.jar` | 5,120,792 | `1cd3b60a3b460286385e6f7425207433d21c83e9fd55edc87e8b0e204378d205` | Generated test output |
| `ui/assets/vintage_fieldops_tactical_map_ui.png` | 3,168,553 | `64a354c56cc415d8b05830897e9916e67e14d57b7a398908c6c1ef9e9905ff0d` | Binary UI study asset |
| `core/build-test-v3/adapter-check/**` | generated classes | recorded in original checkpoint manifest | Generated compiler output |

## Native provenance note

The CP-0001 source notes record that the FT8AF project is MIT, while the extracted native binary contains Hamlib/LGPL notices. JS8Call Android and wsprd are GPLv3 references/modules. Do not publish a compiled FieldOps APK until bundled native-code notices and corresponding-source obligations have been reviewed.

## Recovery rule

If the original CP-0001 ZIP or a newer immutable checkpoint is recovered, keep it as recovery evidence. Do not silently replace the current verified-state labels with assumptions based only on chat history.
