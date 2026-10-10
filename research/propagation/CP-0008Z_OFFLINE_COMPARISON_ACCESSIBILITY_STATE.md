# CP-0008Z — Historical Offline Comparison Accessibility and Host Interaction State

Parent: **CP-0008Y-PROPAGATION_OFFLINE_COMPARISON_DISPLAY_CONTRACT**, main `c2a780517dda9cf810be74dcb491bd7aedc8d1a0`.

## Intent and scope

This checkpoint adds a **platform-neutral host integration state machine** over CP-0008Y's strictly validated historical display DTOs. It does NOT install Android UI, invoke Android accessibility services, move actual keyboard focus, or conduct hardware testing.

`PropagationOfflineReportComparisonInteractionService.open` accepts a complete canonical CP-0008T/U/V comparison and optional paired CP-0008P report artifacts. It constructs a bounded CP-0008Y archived display view and an immutable state carrying an unkeyed artifact SHA-256, independent source and selected-evidence page queries, a typed focus intent and a controlled announcement. SHA-256 is solely a self-consistency binding for the historical artifact, **not authenticated origin**.

`interact` strictly decodes and validates the artifact and both original reports (when provided) on **every** transition, rejects applying state to a different artifact, and emits a new screen without mutating previous state. Typed actions support source/evidence next/previous independently, source change/ID-prefix/limit filters, evidence change/kind/ID-prefix/limit filters, and focus intent for summary, sources, selected evidence, or mandatory provenance notices. Changing a section's filters resets only its page to zero. Navigation preserves other section filters, offsets and the original receipts and global change counts. End-of-list navigation is a safe no-op for its cursor. All queries preserve CP-0008X size/prefix restrictions; case-sensitive prefixes and stable order are unchanged.

The accessible screen includes plain-text historical row descriptors with controlled selected-view-only wording; numbered 1-based ranges derived from the filtered result, explicit empty results, navigation enabled flags, typed focus intent and controlled announcement. Source/evidence identifiers are untrusted data within text; future hosts MUST escape these when rendering, and decide their own platform-specific semantics. Neither a real screen reader nor genuine TalkBack focus is asserted. Notice reading order always retains all four CP-0008Y mandatory warnings, regardless of focus, filter, page or optional original-report consistency result. Read-only outer lists do not establish deep immutable object graphs under hostile reflection.

## Evidence boundaries

ADDED_TO_VIEW/REMOVED_FROM_VIEW means only presence in the selected historical comparison, not actual source deletion. The canonical export receipt is unkeyed integrity, not authentication. Matching original reports proves derivative-to-reports consistency only, not live provider truth. No cross-store atomicity, Android device UI, RF measurements, CAT/PTT, USB, phone, network or account/certificate validation is performed.

Host regression tests cover at least 100 records in both sections, independent cursor changes, round trips and filter resets, page-range accessible text, no-match screens, end-of-list behavior, stale canonical artifact rejection, paired original-report consistency and mismatch, required warning persistence and outer-list mutation checks. Full inherited CP-0008P/Q/R/S/T/U/V/W/X/Y matrix plus new focused gate and immutable checkpoint dry-run are mandatory.

CP-0003C remains **DEFERRED** until owner explicitly says `resume CP-0003C`. CP-0004A/B/C remain incomplete.
