# PokeDaisy fork implementation status

Last updated: 2026-10-20. Requirements: [master brief](MASTER_PROMPT.md).
Architecture: [audit](ARCHITECTURE.md). Independent review: [log](REVIEW_LOG.md).

## Phase checklist

- [x] Confirm fork ownership and push permission; clone into the empty workspace.
- [x] Record clean baseline `a64c9172828bb2e3824d5211655091e27f7ff73c`.
- [x] Read project guidance and trace Android input through JNI to melonDS.
- [x] Initialize pinned mGBA, melonDS and rcheevos submodules.
- [x] Phase 0: baseline debug APK built; source path understood; Windows test blockers recorded below.
- [ ] Phase 1: DS input reliability and full controller profiles.
  - [x] First slice: explicit DS X/Y labels, held-input lifecycle fixes and DS/GBA output tests.
  - [ ] All 12 remappable buttons, including directions.
  - [ ] Global defaults, per-game overrides, reliable device profiles, reset and conflicts.
  - [ ] Chord-prefix isolation, tap/hold policy, screenshot/swap/companion shortcuts.
  - [ ] Controller disconnect/reconnect and touch-only configuration on hardware.
- [ ] Phase 2: audit lower-screen ownership, add assignable switching, verify themed navigation.
- [ ] Phase 3: exact ROM verification, capabilities, per-game settings and fail-closed patch lifecycle.
- [ ] Phase 4: independently verified EXP Share, caps/accessibility, text speed and HM actions.
- [ ] Phase 5: tracker/rule decisions, transactional challenge snapshots, optional enforcement/dashboard.
- [ ] Phase 6: expand verified adapters to Platinum, then Gen 5; separate playback/companion/modifiers.

## Evidence and limitations

The baseline debug APK builds with the pinned wrapper and native cores. The initial
full suite reports Windows failures in PerformanceGuardsTest (line-ending match),
SiteDataExportTest (generated JSON comparison), RomAddedTest, BestEffortTest and
SaveFileEditsTest (persistence/rollback behavior). No save-handling changes are made
in this slice. The current-source full run has 2,860 tests: 2,662 passed, six failed,
192 skipped. The same six failures were observed before the input changes. The
35 focused input/capture/translation tests pass. UI renders have been inspected;
the complete Phase 1 hardware and profile gates remain open.
The exact upstream baseline also passed CI; see [verification](VERIFICATION.md).
No Thor device or user-authorized ROM/save testing assets have been used. Hardware,
gameplay, native button observation and two-display UI behavior remain unverified.
The user clarified the problem as missing X/Y options on AYN Thor. The existing
START 2 / SELECT 2 bindings already send DS X/Y, but were labeled only as GBA controls.
The new UI names them explicitly without changing saved property names or mappings.
Bindings are still global across games; per-game/device profiles remain unfinished.

Branches: `feature/pokedaisy-ds-enhancements` is the integration baseline;
`feature/ds-input-reliability` contains this first reviewable slice. No upstream writes.

Implementation commits: `28b356f` fixes Windows manual parsing; `159d816` exposes
DS X/Y and fixes held-input lifecycle behavior, with tests and manual screenshots.

## Concrete continuation

1. Run the [Thor checklist](VERIFICATION.md#thor-acceptance-checklist-pending), recording
   the Android button codes if physical labels differ. No device was connected here.
2. Resolve the six Windows baseline failures in a separate portability change before
   claiming a clean local full suite. Keep synthetic rollback coverage intact.
3. Extend `GbaControls` and `GbaInput` with directional bindings and versioned profile
   storage. Migrate existing `controls.properties` without losing custom bindings;
   add reset/conflict handling and per-game/device selection before changing defaults.
4. Decide the delayed-prefix/tap/hold behavior, then update `Hotkeys` and activity
   event ownership together so shortcut-only input cannot reach a game.
5. Finish the Phase 1 hardware gate before moving to the lower-screen navigation
   audit. Exact ROM verification and safe reset/load behavior precede any new modifier.

## Open product decisions and safety gates

Keep the master brief's unresolved Dupes Clause, encounter categories, post-Gym caps,
enforcement re-enable, EXP Share interactions, HM possession/history, Gym accessibility
and rewind pairing decisions open. No new gameplay-changing patch is enabled by this
work. The first input slice does not satisfy the complete Phase 1 acceptance gate.
