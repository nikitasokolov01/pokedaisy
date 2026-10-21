# Fork architecture audit

Audit date: 2026-10-20. Baseline: `a64c9172828bb2e3824d5211655091e27f7ff73c`.
Source: the user's fork, `https://github.com/nikitasokolov01/pokedaisy`, whose parent is
`lidor30/pokedaisy`. The connected account owns the fork and reports push permission.
The workspace was empty; the fork was cloned with `origin` pointing only to the fork.

## Baseline architecture and evidence

- Android Kotlin/Compose application (`app/build.gradle.kts`), one process with JNI
  (`MgbaCore.kt`, `app/src/main/cpp/pokedaisy_jni.c`). Gradle 8.10.2, AGP 8.5.2,
  Kotlin 1.9.24, Android API 34, NDK 28.2.13676358, CMake 3.22.1; arm64-v8a.
- GB/GBC/GBA use mGBA; DS uses melonDS. Pinned submodules also include rcheevos.
  `app/src/main/cpp/CMakeLists.txt` builds all three and applies the existing melonDS
  source patch from `third_party/patches/`. No core migration is needed.
- `PokeDaisyActivity.dispatchKeyEvent` routes capture, companion and quick-menu
  input first, then `Hotkeys`, then `GbaInput`. Joystick events enter
  `dispatchGenericMotionEvent` / `onGenericMotionEvent`; triggers become L2/R2 keys.
  `GbaControls` loads the shared `controls.properties`; hotkeys use their own file.
- `GbaInput` produces a pressed-high mask. `EmulatorEngine` combines it with turbo
  and scripted input and calls `MgbaCore.pkSetKeys` each frame. JNI chooses
  `pk_nds_set_keys` for DS or mGBA's `setKeys` otherwise. In `nds/pk_nds.cpp`, DS
  retains bits 0..11 and calls `NDS::SetKeyMask(~keys & 0xFFF)` each frame.
  Bits 0..9 agree with GBA; DS X/Y are bits 10/11.
- DS remapping is already connected: `input.ds = nds` changes the bindings called
  START 2 / SELECT 2 into DS X/Y. The settings' GBA wording hides this behavior.
  The user clarified the AYN Thor issue: X/Y had no recognizable configurable options.
  This is consistent with the misleading GBA-only settings names. It is not evidence
  of a missing native route. The first slice exposes the existing bindings by DS name.
- Confirmed gaps: D-pad and stick directions are hard-coded; no per-ROM or device
  profiles; `setControls` replaces the map without releasing old holds; controller
  removal and focus loss do not clear game/hotkey state. Keys are tracked by keycode,
  not device. Hotkey chord prefixes can reach the game before the chord completes.
- `NdsBottomScreen.kt` already switches the real stylus screen and companion, persists
  that choice, fits the touch image to 4:3, and releases the stylus on hiding/detach.
  Companion key navigation is implemented in `SidePanel` / `CompanionKeys` and activity
  handlers, but the lower-display companion needs its own routing audit.
- `RomIdentity.kt` has whole-file hashes, DS header code/revision and ROM detection.
  `Gen4Reader.kt`, `Gen4Config`, `GuideGen4.kt` and generated tables already read
  Platinum/HGSS state. This does not establish hash-verified modifier support.
  Existing DS native field/function hooks are separate from the new enhancement scope.
- `SaveStates.kt` uses per-ROM CRC folders with manual slots, resume and undo state.
  The engine coordinates state jobs between frames. DS states carry a save-memory
  CRC guard. No versioned challenge snapshot pairing currently exists.
- CI (`.github/workflows/ci.yml`) runs app unit tests/build, UI preview stress, docs
  checks and translations. Some tests depend on existing RAM fixtures or optional
  private ROM assets; new fork tests must use synthetic values only.

## Guidance reconciliation

`PLAN.md` and the introductory status in `CLAUDE.md` / `docs/DEVELOPMENT.md` contain
historical mGBA-only descriptions. Their later DS sections and current code take
precedence for architecture claims. Preserve the existing Pixel Operator/game-themed
UI, manual updates and local translation conventions. Upstream release/deployment
instructions do not authorize releases from this fork. The master brief governs
new gameplay-changing features: off by default, exact revision and signature gates,
no ROM/save modification, and no assumed progression or challenge policies.

The existing DS companion means later phases should extend and audit current code,
not rebuild it. Existing invasive features still require a separate safety audit;
this input slice does not certify them for unknown ROMs.

## First implementation boundary

Expose explicit DS labels, make held input release reliable and verify the current
DS/GBA mapping contract first. Then add directional remapping, profile persistence and chord
isolation. Do not add gameplay hooks until ROM identity, reset/load behavior and
synthetic safety tests are independently established. See IMPLEMENTATION_STATUS.md.

## Phase 1 implementation

`ControllerDevices` derives persistent profile keys from Android's nonvirtual device
descriptor, never the runtime integer ID. Empty or duplicated descriptors use shared
defaults. `InputProfiles` reads the old properties without rewriting them and overlays
versioned sparse settings in this order: global/shared, global/device, game/shared,
game/device. Game keys reuse the existing ROM CRC identity; this is a settings identity,
not the cryptographic ROM verification required for gameplay patches. Profile files live
under `input-profiles/v1`, with hashed filenames and same-directory temporary writes,
file sync and replace. Directory sync is attempted where the host supports it. Unknown
schema versions remain untouched and settings UI reports inherited fallback.

Both `KeyCapture` and `ControllerInputRouter` merge digital and analog reports per
device. Hats map to the physical D-pad names; left stick directions use distinct
`STICK_*` names; triggers use L2/R2. `GbaInput` owns keys by (device, code), combines
touch/script channels and returns a frame mask. No legacy analog path bypasses remapping.
The engine calls the existing JNI function with that mask; `nds/input_mask.h` is used
by the actual core adapter and a synthetic compile-time native contract test in CI.

`ShortcutRouter` reserves each chord's first listed key. Tap an unused modifier to
replay a two-emulated-frame game press on release. Its hold is reserved; ordinary
non-modifier shoulder holds remain immediate. A key already delivered to the game
cannot later complete a shortcut. Equal/subset shortcuts are conflicts; UI asks before
moving them. Imported/combined ambiguous mappings fail closed and show KEY IN USE.
Chords stay device-local, and held actions are reference-counted across controllers.
Opening UI stops the remaining game edges of that event; quick-menu routing retains
shortcut releases so the next press works. Focus loss and rebind cancel player input.

New unbound actions invoke existing display-swap and companion switching, or queue a
PNG capture after a completed emulation frame. DS capture includes both real screens.
DS rewind remains unavailable. Touch fallback includes DS X/Y independently of physical
profiles. Full lower-display focus/navigation and stylus ownership remain Phase 2.
