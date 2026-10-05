# License / provenance notes

This project is presently for one private Galaxy S23 Ultra and is not intended for redistribution.

- Uploaded `FT8AF-android-dev.1312.apk`: behavioral/native ABI source checked directly. Its project is MIT, but the native binary also contains Hamlib; the binary itself contains `hamlib_license`, `LGPL`, and `GNU LGPL` strings. Preserve Hamlib notices if this build is ever redistributed.
- `ft8_lib`: public reference is MIT and supports FT8 + FT4 encode/decode.
- JS8Call-improved Android port: GPLv3. Keep its core/JNI integration as an explicit module boundary and include corresponding source/license if distributed.
- WSPR/wsprd: GPLv3. Same distribution requirement applies.
- APRSdroid: GPLv2 reference. This v3 pack implements its own small Bell-202/HDLC path rather than copying the embedded Scala AFSK classes.
- qFT8: used only for behavioral comparison because the complete source is not publicly available.

Do not publish a compiled FieldOps APK until all bundled native-code notices and corresponding-source obligations have been reviewed.
