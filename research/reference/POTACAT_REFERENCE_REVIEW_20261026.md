# POTACAT operating-UX reference — 2026-10-26 (reference only)

This review is limited to the existing finite FieldOps P0–P3 scope and is **not** a new feature request or a change to CP-0009S acceptance. The third-party source was read directly from `Waffleslop/POTACAT` on GitHub on 2026-10-26: `README.md`, `package.json`, `LICENSE`, `NOTICE`, and the root repository directory. Do not treat the project as a dependency.

## What POTACAT actually is

- Project: https://github.com/Waffleslop/POTACAT
- Author attribution: Casey Stanton (K3SBP), per public project materials.
- Desktop program: Electron/Node; Windows, macOS, Linux; spot aggregation, CAT tuning, map/table, integrated FT-family, JS8 and WSPR interfaces, QSO logs and remote operation.
- Its ECHOCAT iOS/Android mobile companion talks to a separate POTACAT desktop program. That is **not** equivalent to a stand-alone native Android modem/radio app.
- Read package metadata declared version `1.13.4` on the inspection date. This is an inspected source-version string, not a claim about latest published binaries.

## Transferable experience patterns — already inside the FieldOps scope

| POTACAT observed design | Existing FieldOps P0–P3 acceptance requirement | Decision |
| --- | --- | --- |
| De-duplicated, filterable operating spots and station context | Existing heard-signal / propagation / QSO / map workspaces | Reference UX; connect existing providers with one shared selection, **without** adding eight new remote services |
| Spot / callsign selected, radio tuned, log prefilled | CAT-safe FTX-1 session, common digital/CW/SSB workspaces and logbook | Reference interaction; manual user authorization and TX ownership remain mandatory |
| Single table, map and projection using common filters | Map layers for QSOs, heard signals, propagation, APRS and award regions | Reference layout; mobile-first single-pane/tabbed design, offline-friendly |
| FT8, JS8, WSPR tools integrated with CAT and logging | Original P0 mode engines and session/logging bridge | Integration benchmark only; no unsupported code copying |
| Consistent rig state in multiple views | Shared operating session state, RX/TX interlocks, diagnostics | Favor canonical state, explicit disconnected/unverified labels |
| Desktop-to-phone via ECHOCAT | Target is **direct** S23 Ultra + FTX-1 operation | Do **not** add a required desktop companion, cloud account or remote relay |

## Explicitly out of scope for this review

No new POTA/SOTA/GMA/DX-feed service family, extra radios, SmartSDR/Flex panadapter, Winlink, FreeDV, Mercury, remote rig/cloud relay, rotator integration, paid companion, or new digital mode is approved by this reference. If a concept overlaps existing maps/logbook, implement the **existing** acceptance matrix item first rather than creating a new major capability.

## Source/license boundary

`Waffleslop/POTACAT/LICENSE` is GPL-3.0; `package.json` declares `GPL-3.0-or-later`. `NOTICE` states the combined application is GPLv3-or-later due to JS8 and that identified original `SPDX-License-Identifier: Apache-2.0` files are also offered separately under Apache-2.0. Additional bundled packages have other licenses. Product names, marks and logos are not licensed for FieldOps reuse.

**Decision: no code or graphical assets are copied or linked from POTACAT.** General UX ideas and functional comparisons are recorded for integration planning only. Any future direct code reuse would require source-specific license review and separate owner approval; keep the current FT8AF and JS8/other source-obligation holds intact.

## Evidence limits and priority

This is a README/metadata/license/source-directory review; it is **not** a runtime test of POTACAT, an FTX-1 compatibility certification, or a FieldOps software acceptance result. No merge, CP promotion, CAT/RF proof or APK distribution authorization is implied.

Continue current CP-0009S recorded FT8 native/Robolectric CI, close its objective safely, then advance original integrated FTX-1 operating workflows according to `RELEASE_ACCEPTANCE_MATRIX.md`. Software RC stop rule is unchanged.
