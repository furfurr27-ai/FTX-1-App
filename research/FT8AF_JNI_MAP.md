# FT8AF native ABI map — directly inspected from uploaded APK

Uploaded source checked: `FT8AF-android-dev.1312.apk`.

The ARM64 `libft8af.so` exports JNI entry points for:
- FT8/FT4 payload encode and GFSK synthesis (`GenerateFT8`)
- FT8/FT4 decoder allocation, PCM ingest, sync search, candidate analysis and teardown
- separate FT2 decoder allocation/ingest/search/analysis/teardown
- 8/16/32 kHz resamplers
- USB audio native start/write/stop
- Hamlib open/feed/frequency/mode/PTT calls

DEX method descriptors were parsed directly to reconstruct the Java ABI. Important signatures used by the shim:
- `InitDecoder(long,int,int,boolean): long`
- `DecoderMonitorPressFloat(float[],long): void`
- `DecoderFt8FindSync(long): int`
- `DecoderFt8Analysis(int,long,Ft8Message): boolean`
- `InitDecoderFt2(long,int,int,int): long`
- FT2 equivalents for ingest/find/analysis/delete
- static `ft8_encode(byte[],byte[])`, `ft4_encode(byte[],byte[])`, `packFreeTextTo77(String,byte[])`, and `synth_gfsk(...)`

The APK's mode enum was also directly inspected. It records:
- FT8: 15,000 ms slot, 13,500 ms early-decode point, 79 symbols, 0.160 s symbol period, BT=2.0, `isFt8=true`
- FT4: 7,500 ms slot, 6,500 ms early-decode point, 105 symbols, 0.048 s symbol period, BT=1.0, `isFt8=false`
- FT2: 3,750 ms slot, 3,000 ms early-decode point, 105 symbols, 0.024 s symbol period, BT=1.0, `isFt8=false`

The compatibility source lives under `android/compat/`. It is intentionally tiny so the rest of FieldOps is not coupled to FT8AF's application classes.

## Limitation
This environment cannot load an Android ARM64 library, so the ABI was statically verified but not executed here. Device execution on the S23 Ultra remains a required hardware test.
