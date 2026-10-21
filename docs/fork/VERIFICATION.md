# Verification and device handoff

No device/gameplay results are implied by JVM tests or screenshot renders.
The test device list was empty on 2026-10-20. The user identified an AYN Thor and
clarified that X/Y had no recognizable configurable options; code shows those
bindings were exposed under GBA START 2 / SELECT 2.

## Baseline

Starting commit: `a64c9172828bb2e3824d5211655091e27f7ff73c`.
[Upstream CI for that exact commit](https://github.com/lidor30/pokedaisy/actions/runs/39457400767)
passed Unit tests + debug build, UI preview + stress and Docs. This is independent
baseline evidence, not evidence about the fork changes.

Local baseline Android debug APK compiled successfully with the pinned native cores
and dependencies. Six failures were reproduced before the input changes:

| Test | Observed Windows failure |
| --- | --- |
| PerformanceGuardsTest: screen only shows finished frames | Literal LF source match does not match CRLF checkout |
| SiteDataExportTest: website compat JSON is up to date | Generated JSON line-ending comparison |
| RomAddedTest: keptAcrossLaunches | Persistence across store reload |
| BestEffortTest: noneOfTheseIsKeptApart | Persistence across store reload |
| BestEffortTest: storeKeepsAMatch | Persistence across store reload |
| SaveFileEditsTest: aTradeWritesBothOrNeither | Synthetic rollback does not restore the second file |

The persistence failures involve existing replacement/rename behavior; this slice
does not change save or store writes. They remain blockers for a clean Windows suite.
The first full run also reported a translation failure because newly edited strings
were scanned against already compiled baseline translations. That mixed-source result
is not counted as an original defect; the subsequent compiled-source translation tests pass.

The unmodified manual parser failed on CRLF checkouts (two docs/search tests). Commit
`28b356f` normalizes line endings before parsing; all eight docs/search tests pass,
including equal API output for a synthetic LF/CRLF document. No source files were
mass-converted and no dependency versions were changed.

## Historical first-slice checks

- Full current-source unit suite: 2,860 tests, 2,662 passed, six failures matching
  the baseline table above, 192 skipped. No new failing test was observed. Optional
  ROM-backed checks remain skipped. This is not a clean full-suite pass.
- Focused input, hotkey, capture and translation tests: 35 tests, zero failures.
  Includes 12 new synthetic tests for the DS 12-bit mask, X/Y persistence, GBA
  aliases, reset on rebinding/core change, turbo cancellation, script preservation
  and hotkey release without one-shot activation.
- `python -X utf8 scripts/check_translations.py`: 1,220 entries, 1,094 used keys,
  zero problems. UTF-8 mode is necessary with this Windows Python.
- Manual parsing, links, setting coverage and search: eight passing tests, run
  with `node --test test/docs.test.js test/docsearch.test.js` from `website/`.
- Actual generated controls images are inspected, including global dual labels,
  capture UI and the DS companion in light/dark themes. These are synthetic renders,
  not emulator or hardware observations.
- Claude supplied two independent source reviews; findings and decisions are in
  [REVIEW_LOG.md](REVIEW_LOG.md).

Raw local logs/reports are under ignored `build/fork-review/`; the original baseline
APK and test report are under `build/fork-baseline/`. No generated APK is committed.
The debug APK for implementation commit `159d816` is copied to
`build/fork-review/pokedaisy-ds-input-159d816-debug.apk` for local device handoff.
SHA-256: `db877c26d6bbce9028f247b0eb9d7ada919516aa607b16c99a6c5732fa54e297`.

## Completed Phase 1 checks (2026-10-20)

- Final broad run: **2,891 tests, 2,693 passed, six failed, 192 skipped**. Each failure
  is the same Windows baseline case listed above; no new failing case was observed.
  This remains a failed full suite, not a clean pass. Optional ROM checks skipped.
- **68 focused checks passed** in that run: profiles, controller routing/capture,
  all twelve remapped DS outputs, GBA aliases/turbo, touch geometry and eight rendered
  DS profile/touch variants. The earlier isolated focused run passed 65 checks; the
  final run includes three additional diagonal/menu/profile-conflict regressions.
- Native DS contract: NDK clang compiled `app/src/test/native/nds_input_contract_test.cpp`
  for Android arm64. Compile-time assertions check twelve independently specified
  pressed-low register masks, all-up/all-down, X/Y separation and high-bit rejection.
  The production adapter uses those exact conversion functions. CI now runs this check.
- Android debug build passed with the pinned mGBA, melonDS and rcheevos sources.
- Translations: 1,232 entries, 1,105 used keys, zero problems.
- Manual/docs search: eight tests passed. Website production build passed (28 pages);
  nothing was deployed. Six updated manual images were generated from real UI sources.
- Inspected actual PNGs for global full controls, global hotkeys, DS controller/game
  profile settings and DS X/Y touch fallback. The global hotkey screenshot harness
  was corrected to activate the row semantically and assert SAVE STATE is present.
  Compact profile names can ellipsize; the controls list scrolls as before.
- Claude review was received and evaluated; decisions are in [REVIEW_LOG.md](REVIEW_LOG.md).
- `adb devices` returned an empty device list. No ROM, BIOS, save, party or PC data
  was used. Hardware button codes, live JNI execution, screenshots from a running core,
  touch transforms and physical display switching remain unverified.

Local evidence: `build/fork-review/phase1-full-tests.log`, `phase1-full-results/`,
`phase1-build-tests.log`, `phase1-focused-results/`, `phase1-ui-preview.log`,
`phase1-ui-hotkeys.log`, `phase1-docs-tests.log`, `phase1-website-build.log`.
The only app change after the full run was the offline selected-profile label; the
desktop UI and Android handoff rebuild both compiled it successfully.

Handoff source commit: `6434360a266f400e354b786acee80a1624a4f917`.
Local APK: `build/fork-review/pokedaisy-phase1-6434360-debug.apk` (37,336,787 bytes).
SHA-256: `6ad065b50f2adb45341c02d9f9ac64a7a0ab88b3641be60773606115d80b0ae1`.
The committed-source `:app:assembleDebug` finished successfully; log:
`build/fork-review/phase1-handoff-build.log`. The APK is ignored, not committed.

## Windows build command

The repository has a Unix launcher only. From PowerShell, use a compatible JDK
(this session uses the existing JDK 21) and the checked-in wrapper jar:

```powershell
$env:JAVA_HOME = '<installed JDK 17 or 21 directory>'
$env:ANDROID_HOME = '<installed Android SDK directory>'
& "$env:JAVA_HOME\bin\java.exe" -classpath gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :app:testDebugUnitTest :app:assembleDebug --console=plain
```

No ROM directory is configured; optional ROM-backed tests must skip. Do not download
game ROMs or borrow unrelated local saves to satisfy test prerequisites.

## Thor acceptance checklist (pending)

1. Preserve existing app data when installing the development APK. A debug signer may
   differ from the installed release; do not uninstall without keeping saves/settings.
2. Open a user-provided DS game. In companion SETTINGS > GAME BUTTONS select the Thor
   under CONTROLLER PROFILE and THIS GAME under APPLIES TO. Rebind DS X and DS Y and
   confirm each in the game's menus; START/SELECT remain independent.
3. Test A/B/X/Y/L/R/START/SELECT and all four directions with alternate bindings. Capture
   the left stick and D-pad independently. Reset and confirm the inherited bindings return.
4. Create different ALL GAMES and THIS GAME mappings, then a second-controller profile.
   Confirm precedence, relaunch persistence and reconnect persistence when Android changes
   the temporary device ID. Duplicate/empty descriptors should use shared defaults.
5. Hold the first listed shortcut button before its remaining buttons. Confirm zero game
   input from completed chords, one action despite repeats, and a short game tap for an
   unused modifier. Verify ordinary L/R holds remain immediate. Test moving conflicting
   chords, disabling HOTKEYS, and clearing a single-button hotkey used as a game button.
6. Pull a trigger that reports analog plus digital input; FF HOLD must start once and
   stop only after both reports release. Hold FF from two controllers, then disconnect
   one; the remaining controller should keep FF held. Different devices cannot form a chord.
7. Disconnect a controller while holding movement/turbo. Its holds must clear while
   another pad's holds remain. With no pad connected and SHOW set to AUTO, use the touch
   D-pad, A/B/X/Y/L/R/START/SELECT. Change/cancel/reset profiles using touch alone.
8. Suspend, lose focus, rebind and return to the game. No stale player hold or deferred
   shortcut tap should remain; toggled FF is preserved. Test QUICK MENU via an analog
   trigger, including release and the next press, without duplicate menu/game actions.
9. Bind SWAP SCREENS, COMPANION and SCREENSHOT. Verify existing display swap and DS
   lower-screen switching, touch release/coordinates, and a PNG containing both DS
   game screens under the app's external files/screenshots folder. DS rewind stays N/A.
10. Repeat the main controls in GB/GBC/GBA: X/Y aliases still press extra START/SELECT,
    turbo works, and state/undo/fast-forward shortcuts preserve existing behavior.

No new feature writes ROMs, saves, party/PC data or challenge state. No HGSS modifier
or Gen 5 capability is certified by this checklist.
