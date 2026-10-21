# PokeDaisy fork implementation status

Last updated: 2026-10-20. Requirements: [master brief](MASTER_PROMPT.md).
Architecture: [audit](ARCHITECTURE.md). Independent review: [log](REVIEW_LOG.md).

## Phase checklist

- [x] Confirm fork ownership and push permission; clone into the empty workspace.
- [x] Record clean baseline `a64c9172828bb2e3824d5211655091e27f7ff73c`.
- [x] Read project guidance and trace Android input through JNI to melonDS.
- [x] Initialize pinned mGBA, melonDS and rcheevos submodules.
- [x] Phase 0: baseline debug APK built; source path understood; Windows test blockers recorded below.
- [x] Phase 1 implementation and synthetic input checks.
  - [x] Explicit DS X/Y labels and all 12 remappable controls, including directions and left stick.
  - [x] Global defaults, per-game overrides, descriptor-based device profiles, reset and conflicts.
  - [x] Modifier-first chord isolation, tap/hold policy, screenshot/swap/companion shortcuts.
  - [x] Per-device releases, duplicate analog/digital report merging and DS X/Y touch fallback.
  - [x] Profile, routing, capture, GBA regression and native DS mask contract tests.
  - [ ] Live AYN Thor / USB / Bluetooth acceptance and suspend/two-display checks.
- [ ] Phase 2: audit lower-screen ownership, add assignable switching, verify themed navigation.
- [ ] Phase 3: exact ROM verification, capabilities, per-game settings and fail-closed patch lifecycle.
- [ ] Phase 4: independently verified EXP Share, caps/accessibility, text speed and HM actions.
- [ ] Phase 5: tracker/rule decisions, transactional challenge snapshots, optional enforcement/dashboard.
- [ ] Phase 6: expand verified adapters to Platinum, then Gen 5; separate playback/companion/modifiers.

## Evidence and limitations

Phase 1 is implemented and verified with synthetic inputs and rendered UI. The initial
65 focused checks passed, including eight settings/touchscreen renders. A full final
suite and refreshed handoff are recorded in [VERIFICATION.md](VERIFICATION.md).
The debug APK builds with the pinned native cores. The six existing Windows baseline
failures remain tracked separately; a full-suite failure is not called a clean pass.

The user identified missing configurable X/Y options on AYN Thor. DS companion rows
now name DS X and DS Y explicitly. Physical buttons, hat and left-stick directions
all resolve through the active profile. The Thor's actual Android controller name and
stable descriptor select its profile; no unverified vendor/product IDs are hard-coded.

Existing global bindings are inherited without rewriting their files. Narrower profiles
persist independently, use atomic replacement, and preserve unreadable/unknown-version
files. Equal/subset shortcut conflicts and combined-profile button conflicts fail closed
and are visible in settings. Chord modifiers are reserved until release or completion;
unused modifier taps last two emulated frames. This policy is documented in the manual.

No Android device was connected during verification. Live Thor input, pairing/reconnect,
stylus and display switching, screenshots from a running core, and actual JNI-to-melonDS
execution with a legal ROM remain unverified. The native check compiles assertions against
the same mask functions used by the adapter; it does not boot a ROM. No user ROM/save
assets were accessed. DS rewind remains unavailable rather than claiming unsupported work.

Branches: `feature/pokedaisy-ds-enhancements` is the integration baseline;
`feature/ds-input-reliability` holds Phase 1. [Draft PR #1](https://github.com/nikitasokolov01/pokedaisy/pull/1)
targets the user's fork only. No upstream write or merge has been made.

Earlier commits: `28b356f` fixes Windows manual parsing; `159d816` exposes DS X/Y and
fixes initial held-input lifecycle behavior. Subsequent Phase 1 commits extend that slice.

## Concrete continuation

1. Install the linked debug APK without discarding existing saves/settings and run the
   [Thor checklist](VERIFICATION.md#thor-acceptance-checklist-pending). Record Android
   controller identity, button codes, hotkey behavior and both screen orientations.
2. Resolve the six Windows baseline failures in a separate portability change before
   claiming a clean local full suite. Keep synthetic rollback coverage intact.
3. Proceed with Phase 2's lower-screen navigation, focus and stylus ownership audit.
   The assignable Phase 1 shortcuts call existing screen behavior; they do not certify
   the full lower-screen UX gate.
4. Exact ROM verification and safe reset/load behavior precede any new gameplay modifier.

## Open product decisions and safety gates

Keep the master brief's unresolved Dupes Clause, encounter categories, post-Gym caps,
enforcement re-enable, EXP Share interactions, HM possession/history, Gym accessibility
and rewind pairing decisions open. No new gameplay-changing patch is enabled by this
work. Hardware validation remains explicitly separate from the passed synthetic Phase 1 checks.
