# PokeDaisy fork implementation status

Last updated: 2026-10-21. Requirements: [master brief](MASTER_PROMPT.md).
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
- [x] Phase 2 implementation and synthetic navigation checks.
  - [x] Exclusive lower-screen switch mode, assignable shortcut and persistent touch affordance.
  - [x] Controller focus, remapped directions/actions, repeat, tab switching, back and direct return.
  - [x] Reserved stylus geometry, pointer ownership, hide/resize/suspend cancellation.
  - [x] Explicit text entry, original theme and Thor/4:3/wide/compact layout checks.
  - [ ] Live Thor display focus, lid sleep, rotation, keyboard and real-game stylus acceptance.
- [x] Phase 3 infrastructure and synthetic safeguard checks.
  - [x] Full-content fingerprints and catalog identity checks; header-only matches denied.
  - [x] Exact-ROM preferences, default-off choices and nondestructive invalid-file handling.
  - [x] Library/companion enhancement page with unavailable-feature reasons.
  - [x] Adapter contracts, empty production registry, guarded transactions and state boundaries.
  - [x] Final focused regression run, rendered UI review and independent static review.
  - [ ] Native adapter, legal-ROM gameplay and live Thor acceptance; no production hooks registered.
- [ ] Phase 4: independently verified EXP Share, caps/accessibility, text speed and HM actions.
- [ ] Phase 5: tracker/rule decisions, transactional challenge snapshots, optional enforcement/dashboard.
- [ ] Phase 6: expand verified adapters to Platinum, then Gen 5; separate playback/companion/modifiers.

## Phase 2 handoff

Branch `feature/ds-companion-navigation` builds on the Phase 1 branch. The lower
companion now owns controller navigation while visible. The top game continues
running; hidden surfaces receive no routed player input. B/device BACK unwind its
pages before returning to the DS touch screen, and START returns directly. The
COMPANION shortcut and touch switch preserve the selected lower mode. The DS
switch bar reserves space outside the stylus image at all tested aspect ratios.

The final 71 focused regression checks passed, including real Android host touch and
Compose focus dispatch. Five desktop UI scenarios (four screen sizes plus explicit
text entry), translations and eight manual/search checks also passed. The full suite
recorded 2,710 passes, the same six Windows baseline failures and 192 skips. The final
review corrections were checked by the subsequent focused rerun. Evidence, APK hash
and hardware checklist are in [VERIFICATION.md](VERIFICATION.md). No Android device
is connected. Hardware focus,
lid sleep, native core/stylus behavior and Android keyboard behavior are unverified.
The existing DS single-screen limitation remains: only the top game image is available.

[Draft PR #2](https://github.com/nikitasokolov01/pokedaisy/pull/2) targets the fork's
integration branch and includes the unmerged Phase 1 prerequisite from PR #1.
Implementation commit: `7858280e9042943a5a466c7e0e448581965856f3`.
Local debug APK: `build/fork-review/pokedaisy-phase2-7858280-debug.apk`.

## Phase 3 handoff

Branch `feature/enhancement-safeguards` builds on Phase 2. Full selected-ROM content
is hashed before the new framework can consider a game. SHA-256 isolates preferences;
the initial catalog recognizes two complete-image HGSS identities. Recognition grants
no patch permission. See the separate [ROM matrix](ROM_VERIFICATION.md).

The library and companion share an ENHANCEMENTS page with default-off permission,
four unavailable feature rows, reasons and reset. Settings use atomic replacement,
preserve unreadable files and reject stale overwrites. A guarded transaction layer
checks loaded identity, capability, progression, signatures and safe points; it rolls
back failed writes and removes owned patches before state transitions. The production
registry is empty, so this phase enables no new game modifier or native memory write.

Final focused verification passed 66 checks, including 23 new enhancement tests.
Five desktop UI scenarios, eight manual/search checks, translations and the Android
APK build passed. The broad suite recorded 2,731 passes, the same six Windows baseline
failures and 192 skips; final review corrections were checked by the focused rerun.
Actual independent Claude feedback and its disposition are in [REVIEW_LOG.md](REVIEW_LOG.md).
No device was connected and no ROM, BIOS or user-save asset was used. Adapter contracts
were exercised with synthetic memory only. Native hooks and live Thor validation
remain prerequisites for Phase 4; this is not gameplay certification.

## Evidence and limitations

Phase 1 is implemented and verified with synthetic inputs and rendered UI. The final
68 focused checks passed, including eight settings/touchscreen renders. The full run
has 2,891 tests: 2,693 passed, six baseline failures and 192 skipped. The refreshed
handoff is recorded in [VERIFICATION.md](VERIFICATION.md).
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
fixes initial held-input lifecycle behavior. `6434360` completes Phase 1 profiles,
routing, touch fallback, actions, tests and documentation.

## Concrete continuation

1. Install the linked debug APK without discarding existing saves/settings and run the
   [Thor checklist](VERIFICATION.md#thor-acceptance-checklist-pending). Record Android
   controller identity, button codes, hotkey behavior and both screen orientations.
2. Resolve the six Windows baseline failures in a separate portability change before
   claiming a clean local full suite. Keep synthetic rollback coverage intact.
3. Run the Phase 2 Thor acceptance checklist before calling the hardware gate passed.
   Synthetic input and layout tests do not replace Android firmware/display validation.
4. Run the Phase 3 Thor settings checklist. Before adding any Phase 4 modifier, provide
   independently verified loaded-image identities, native adapters, signatures and
   legal-ROM reset/load/gameplay evidence. Resolve its product decisions separately.

## Open product decisions and safety gates

Keep the master brief's unresolved Dupes Clause, encounter categories, post-Gym caps,
enforcement re-enable, EXP Share interactions, HM possession/history, Gym accessibility
and rewind pairing decisions open. No new gameplay-changing patch is enabled by this
work. Hardware validation remains explicitly separate from the passed synthetic Phase 1 checks.
