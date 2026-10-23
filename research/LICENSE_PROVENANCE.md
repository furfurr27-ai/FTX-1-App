# License / provenance notes

This project is presently for one private Galaxy S23 Ultra and is not intended for redistribution.

- Uploaded `FT8AF-android-dev.1312.apk`: behavioral/native ABI source checked directly. Its project is MIT, but the native binary also contains Hamlib; the binary itself contains `hamlib_license`, `LGPL`, and `GNU LGPL` strings. Preserve Hamlib notices if this build is ever redistributed.
- `ft8_lib`: public reference is MIT and supports FT8 + FT4 encode/decode.
- JS8Call-improved Android port: GPLv3. Keep its core/JNI integration as an explicit module boundary and include corresponding source/license if distributed.
- WSPR/wsprd: GPLv3. Same distribution requirement applies.
- APRSdroid: GPLv2 reference. This v3 pack implements its own small Bell-202/HDLC path rather than copying the embedded Scala AFSK classes.
- qFT8: used only for behavioral comparison because the complete source is not publicly available.

Do not publish a compiled FieldOps APK until all bundled native-code notices and corresponding-source obligations have been reviewed.

## CP-0009S pinned native-source import / distribution hold

FieldOps now vendors the minimum FT-family decoder/encoder and JNI DSP glue
from `patrickrb/FT8AF` at immutable commit
`c2f63e8b37fcd484fd2eb2049494425dd2414971`.
That repository identifies the embedded `kgoba/ft8_lib` revision as
`6f528128ee3ebf4d08ba2313f6c5d3913eda5608`.
The checked-in sources and explicit notices are under
`app/src/main/cpp/third_party/ft8af`, including `LICENSE.FT8AF`,
`ft8_lib/LICENSE`, and `ft8_lib/fft/COPYING`.

**License discrepancy requiring review before public distribution:**
the upstream GitHub repository presently presents its root LICENSE as MIT,
whereas `https://ft8af.app/download` states GPL-3.0 for the complete
application. Those facts do NOT establish that a downstream binary bundle
may be marketed as MIT-only or redistributed without GPL review. Preserve
all notices, source pins, per-file notices and corresponding-source obligations.
No distribution approval is claimed. FieldOps deliberately does **not**
vendor FT8AF/libusb USB audio, Hamlib or its PTT/radio control path; the
local CMake target compiles FT8/FT4/FT2 DSP and JNI only.
