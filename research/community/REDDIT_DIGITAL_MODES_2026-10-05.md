# Community source intake — Reddit digital-mode usage discussion

Source date checked: 2026-10-05

Primary community source:

- Reddit /r/amateurradio
- Post: "What digital modes do yall enjoy - that people actually use regularly?"
- Canonical URL: https://www.reddit.com/r/amateurradio/comments/1wxzr8q/what_digital_modes_do_yall_enjoy_that_people/
- User-supplied short link: https://www.reddit.com/r/amateurradio/s/tirEQyxhVw

## Source type and limits

This is a community-sentiment source, not a technical specification and not a statistically representative usage survey. It is useful for feature prioritization, operator expectations, mode-discovery UX, and identifying active communities that FieldOps should investigate. It must not be used as authority for protocol details, frequencies, or standards without separate technical sources.

## Directly observed community themes

The discussion strongly reinforces that operators want more than FT8/FT4-style minimal exchanges.

Repeated/important themes in the thread:

- **JS8Call** remains attractive for keyboard/chat and message handling.
- Several users report JS8 activity is much easier to find on **40 m** than on 20 m.
- **PSK31** still has loyal users and is repeatedly described as enjoyable for keyboard-to-keyboard QSOs, but activity can be sparse compared with its historical peak.
- **Olivia** is still valued for conversational weak-signal text, though users report difficulty finding activity.
- **Hellschreiber** has a small but enthusiastic niche and scheduled nets.
- **RTTY** remains an active/recognizable traditional data mode.
- **Winlink / VARA** is cited as a practical data/message mode rather than only a contact mode.
- **VarAC** and **FreeDV** are mentioned as newer systems worth watching.
- **SSTV** is repeatedly described as fun and active even though it is not technically a digital data mode in the same sense as PSK/RTTY.
- **JTTY** is mentioned as an emerging conversational mode that some operators hope will gain adoption because it is being integrated into the WSJT-X ecosystem.
- **CW** appears in the discussion despite the recurring debate over whether it belongs in the "digital" category.

## Implications for FieldOps

This source supports the existing FieldOps direction of becoming a broader operating environment rather than an FT8-only app.

### Keep high priority

- FT8 / FT4 / FT2
- JS8
- WSPR
- APRS
- RTTY
- PSK31 / PSK63
- Olivia
- MFSK family
- Contestia
- Hellschreiber
- SSTV later

### Raise product priority / research priority

1. **Keyboard-conversation workspace**
   - Treat conversational modes as a first-class UX, not as generic modem output.
   - Shared transcript, type-ahead, macro/message history, RX/TX pane, contact logging, and per-mode presets should be reusable across PSK31, Olivia, Contestia, MFSK, Hellschreiber, JS8, and emerging conversational modes.

2. **Mode activity/discovery assistance**
   - Operators repeatedly describe not knowing whether a mode is dead, on the wrong band, or simply concentrated elsewhere.
   - FieldOps should eventually surface mode-specific calling-frequency hints, band/time-of-day activity expectations, spotting/activity sources when available, and scheduled nets.
   - JS8 should specifically include a 40 m activity hint based on community reports, clearly labeled as community-derived rather than protocol fact.

3. **Winlink / VARA research**
   - Add Winlink/VARA to the research backlog as a practical messaging/emergency-communications workflow.
   - Do not commit to implementation until licensing, platform constraints, protocol access, and Android feasibility are reviewed.

4. **JTTY watch item**
   - Track JTTY as an emerging WSJT-X-adjacent conversational mode.
   - Do not prioritize implementation ahead of established modes until protocol/source maturity and real-world adoption are clearer.

5. **VarAC / FreeDV watch items**
   - Keep both as ecosystem/reference candidates.
   - FreeDV is digital voice rather than the same class of text/data modem and should remain architecturally separate.
   - VarAC depends on VARA and has platform/licensing implications that need explicit review.

## Product conclusion

This thread does **not** justify changing the current checkpoint order. It does strengthen the long-term product requirement that FieldOps make less-common modes easier to discover and use.

The strongest community signal is not merely "add more decoders." It is:

> operators want conversational and practical data modes, but they struggle to find activity, know where to call, and move efficiently between software/modes.

That should inform the later mode-workspace and activity-discovery UX.
