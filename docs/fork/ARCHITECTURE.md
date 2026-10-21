# Fork architecture audit

Audit date: 2026-10-20. Baseline: `a64c9172828bb2e3824d5211655091e27f7ff73c`.
Source: the user's fork, `https://github.com/nikitasokolov01/pokedaisy`, whose parent is
`lidor30/pokedaisy`. The connected account owns the fork and reports push permission.
The workspace was empty; the fork was cloned with `origin` pointing only to the fork.

## Current architecture and evidence

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
