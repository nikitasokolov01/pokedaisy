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
profiles. Phase 2 adds explicit lower-display focus/navigation and stylus ownership, described below.


## Phase 2 implementation

`NdsBottomHost` keeps the DS touch view and companion on the secondary display (or
main display after a swap). Only the selected view is visible. A reserved switch bar
sits outside both surfaces; its bounds are assigned during measurement, including
wide displays without natural space below the DS image. `NdsTouchRect` supplies the
same 4:3 geometry to rendering and hit testing. `NdsTouchInput` tracks one pointer,
checks floating-point bounds before integer conversion, and releases on cancel,
hide, resize, detach, focus loss and pause. The production sink remains `pkNdsTouch`.

The activity routes the lower companion through its own `CompanionKeys` bus.
`DsCompanionNavigation` reuses the existing overlay stack, focus starts, row cursor,
and L/R tab hints. Android key focus stays with the activity; `companionFocus` sends
D-pad/activation events directly to the visible Compose view. The Presentation also
forwards joystick events. The existing one-screen window still works; DS two-display
mode now uses the lower switch instead of leaving another companion over the game.
B/device BACK unwind the active companion, then return to the touch image; START
returns immediately. CLOSE COMPANION also returns to the DS touch image.

`ControllerInputRouter` now emits ordinary menu edges after profile resolution,
shortcut isolation and digital/analog merging. `CompanionNavigationInput` repeats
only directions and owns holds by device and physical code. Switching surfaces clears
game input and requires held keys/axes to release before reuse, even if a stick turns
to another direction. Reentrant switches stop the rest of the current event batch.
Default sticks retain the map's smooth cursor; custom stick bindings take the remapped
edge path. Touch game controls are hidden while the companion owns input. Text fields
require explicit activation rather than opening an editor when focus merely passes.

Synthetic geometry/router tests and the desktop Compose checks use no ROM or save
assets. The desktop harness substitutes Compose direction search for Android View
focus search; it is not a test of Android display routing or the Thor's firmware.
A separate Android layoutlib test exercises the real lower host and MotionEvent path
with a recording JNI sink. Live Thor acceptance remains a separate hardware gate.

## Phase 3 enhancement infrastructure

`RomFingerprint` reads the entire selected ROM in bounded chunks, including padding,
and computes SHA-256 and SHA-1 in one pass. `RomIdentity.enhancementFingerprint`
uses the same archive entry as playback, rejects short/extra bytes and read errors,
and checks file size/mtime around inspection. Inspection happens on a worker/core
thread, never in Compose. A header match alone has no enhancement authority.

`RomVerification` recognizes the US revision-0 HGSS SHA-1 identities published by
[pret/pokeheartgold](https://github.com/pret/pokeheartgold/blob/master/README.md).
That list establishes identity only. It does not approve an adapter or patch.
Every future `EnhancementPlan` also pins SHA-256, revision, required core capability,
progression prerequisites and exact original/replacement bytes. Its adapter must
attest the pristine image actually loaded by the core, independently of the file
inspection; a filename or cached header is insufficient. No native adapter or
production patch plan is registered in Phase 3.

`EnhancementSettings` stores a versioned file per SHA-256 under `enhancements/v1`.
Permission and individual choices default off. Only valid enum values and a matching
identity/schema load. Unknown/corrupt files disable their preferences and remain
untouched, including on reset. Writes sync a same-directory temporary file and
require atomic replacement; a failed save does not change the in-memory choice.
Writes from different page sessions share a lock and compare the expected previous
values, so a stale page reports failure instead of overwriting a newer choice.
This is independent of the older CRC-keyed input profiles and existing gameplay
options. No settings migration guesses equivalence between ROM revisions.

`EnhancementSession` exposes the same state to library and companion pages. A
permission switch alone cannot enable an unavailable feature. The page shows ROM
identity and per-feature reasons, with a NEW badge and existing menu/focus styling.
`EnhancementRuntime` is the future adapter boundary: all signatures are checked
before the first write, sites cannot overlap, writes have readback checks, rollback
is verified, and a failed verification remains disabled until a new core session.
An unverified rollback or lost memory ownership requires stopping the core.

The engine invokes this boundary on its core thread before save/load, automatic
resume and soft reset. State actions defer/fail without executing
when installed hooks cannot safely be removed. Owned patches are removed before a
state is saved and revalidated before reapplication after load/reset. Startup
reinspects the ROM, and normal lifecycle pauses retain the same session. With no
production plans, per-frame processing returns immediately and playback continues
without enhancement memory access. Synthetic adapters exercise the transaction
contract; they do not certify real DS patch locations, native reset behavior or
external savestates containing third-party modifications.

Blocked state operations remain queued within the existing sleep/stop timeouts;
blocked automatic resume never deletes its source snapshot. Normal shutdown still
lets the game's pending SRAM write finish, then destroys the core and discards all
runtime ownership. A failed patch rollback prevents those extra game frames. This
does not change native save serialization or certify any future adapter's safe-point
timing; those require adapter-specific and device tests before registration.

This gate governs the new framework. Existing opt-in cheats and legacy gameplay
features are not migrated or certified by Phase 3. Phase 4 must audit their
interactions before registering any new gameplay implementation.
