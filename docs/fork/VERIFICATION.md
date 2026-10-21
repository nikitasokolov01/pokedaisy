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


## Phase 2 lower-screen verification (2026-10-21)

Implemented on `feature/ds-companion-navigation`, based on Phase 1 commit `8a9b563`.
No ROM, BIOS or user-save assets were used. The ROM-revision/capability matrix is
unchanged: this phase changes player input and companion presentation only.

Evidence retained under the ignored `build/fork-review/` directory:

| Check | Evidence / scope |
| --- | --- |
| Initial focused Android tests | 44 passed, zero failures/skips; `phase2-build-tests.log` and `phase2-focused-results/` |
| Final committed-source tests and APK | 71 passed, zero failures/errors/skips; `phase2-final-tests.log` and `phase2-final-results/`; includes both Android host/focus tests and the final review corrections |
| Full Android suite | 2,908 tests: 2,710 passed, six known Windows baseline failures, 192 skipped; `phase2-full-tests.log` and `phase2-full-results/` |
| Actual Android host/stylus | `NdsBottomHostTest`: real FrameLayout/MotionEvent routing with a recording JNI sink; switch cancels touch, hidden moves are rejected, geometry excludes the switch bar |
| Actual Android focus adapter | `NdsBottomHostTest`: a D-pad event selects the second real Compose button and A activates it; hidden companion focus dispatch is rejected |
| Desktop companion interactions | `phase2-navigation-ui.log`: four sizes (1240x1080 @ 2.625, 1280x960 @ 2, 1920x1080 @ 2, 720x640 @ 2), controller A, touch selector, B, L/R; explicit text-entry activation/back scenario also passed |
| Rendered UI | Viewed the four final focus PNGs and text-entry PNG in `phase2-ui/`, Android host PNG, and `docs/manual/shots/ds-lower-companion.png` |
| Manual / translations | Eight manual/search checks passed; 1,234 translation entries, 1,107 used keys, zero issues; 28-page website build passed |
| Independent review | Actual tool-free Claude static review received; accepted fixes and rejected/qualified findings in REVIEW_LOG.md |
| Hardware | Escalated `adb devices` returned no connected devices; Thor firmware, lid sleep, external display transitions, software keyboard and real-core stylus remain unverified |

The desktop harness uses Compose directional focus search where Android uses View
D-pad search. The Android test separately exercises that View adapter, but layoutlib
cannot reproduce the Thor's second-display window manager. The real stylus sink in
the APK remains JNI `pkNdsTouch`; the integration test records its calls rather than
booting a copyrighted game. These are software checks, not a hardware acceptance pass.

The full suite reproduced the same six failures listed in the Windows baseline:
PerformanceGuardsTest, RomAddedTest, SaveFileEditsTest, SiteDataExportTest and two
BestEffortTest cases. It finished before the last review corrections; the final
focused run recorded above checks the committed corrections. No new failure was found in
the full run, but it is not a clean full-suite pass.

Handoff source commit: `7858280e9042943a5a466c7e0e448581965856f3`.
[Draft PR #2](https://github.com/nikitasokolov01/pokedaisy/pull/2) targets the fork's
integration branch and includes Phase 1 until PR #1 is merged there.
Local APK: `build/fork-review/pokedaisy-phase2-7858280-debug.apk` (37,336,787 bytes).
SHA-256: `1d5816d8a3278a0bbc6b65dcb919f1b0072fb2d5622cc9e05976e8943d30b95d`.
The committed-source `:app:assembleDebug` completed successfully in
`build/fork-review/phase2-final-tests.log`. The packaged DEX contains the new
navigation and touch classes. The APK is ignored, not committed.
The final manual/search rerun passed all eight checks, translations again reported
zero issues, and `phase2-website-final.log` records a successful 28-page local build.

### Phase 2 Thor acceptance checklist (pending)

1. Keep existing saves/settings when installing the debug APK. Launch a legal DS game
   on two displays. Confirm the top screen keeps running while COMPANION/GAME SCREEN
   alternates only the lower surface. Assign the COMPANION hotkey and test it too.
2. In the companion, test mapped D-pad/stick directions, held repeat, A selection,
   B through a popup/subpage and back to the game, START directly to the game, and
   L/R tab cycling. Test DS X on the map where zoom is offered. Repeat after remapping.
3. Hold a direction, trigger, shortcut modifier or touch game button while switching.
   Confirm zero game presses while the companion is open and no resumed movement
   until the old control is released. Turn a held stick to another direction before
   centring it. Check unmapped-button repeats and two controllers independently.
4. Touch every DS image corner and centre, then the letterbox and switch bar. Try a
   drag outside and back, a second finger, lifting the first finger, hiding during a
   drag and returning. Only the original finger controls the stylus until lifted;
   hiding/resize/sleep releases it, and a fresh down starts the next touch.
5. Swap screens both ways, open/close the lid, HOME and resume, rotate where supported,
   disconnect/reconnect a controller and add/remove an external display. Confirm
   usable controller focus, no hidden touches, no stuck map cursor, and correct sizing.
6. Browse settings and HELP with the controller. Confirm the row highlight remains
   visible and scrolling reaches the last row. Passing a search field must not open
   a keyboard. Explicitly edit it by touch/A, type, use B and resume navigation;
   actual Android keyboard/display behavior needs hardware confirmation.
7. Confirm remembered lower mode after relaunch and sleep. A companion action that
   temporarily returns to the game must preserve the preferred mode. CLOSE COMPANION
   returns to the DS touch screen. Ordinary GB/GBC/GBA and one-screen window navigation
   should retain their existing behavior.

Single-screen DS playback still shows only the top image; switch mode here requires
an actual second display. Split and floating companion modes are not implemented.

## Phase 3 enhancement infrastructure verification (2026-10-21)

Implemented on `feature/enhancement-safeguards`, based on Phase 2 handoff commit
`4f4bd7dda1238960c583c059988483aef723e6bc`. This phase adds verification, settings and
transaction contracts. No production patch or native enhancement adapter is enabled.
Catalog recognition, companion coverage and modifier approval remain separate claims
in [ROM_VERIFICATION.md](ROM_VERIFICATION.md).

| Check | Actual result and local evidence |
| --- | --- |
| Phase baseline | Existing 12 RomArchive tests and debug build passed; `phase3-baseline.log`, `phase3-baseline-results/` |
| Final focused Android tests | 66 passed, zero failures/errors/skips; `phase3-final-tests.log`, `phase3-final-results/` |
| New enhancement fixtures | 23 tests: whole-image and archive identity, short/oversize/error streams, spoofed headers, per-ROM persistence, corrupt-file preservation, stale writes, default-off gates, transactional rollback, safe-point deferral, state/load/reset boundaries and ownership changes |
| Existing focused regressions | ROM archive handling, translations, controller routing, companion ownership, Android lower-screen host/touch/focus and GBA turbo |
| Full Android suite | 2,929 tests: 2,731 passed, six known baseline failures, 192 skipped; `phase3-full-tests.log`, `phase3-full-results/` |
| Desktop UI | Five scenarios passed: Thor 1240x1080 @ 2.625, wide 1920x1080 @ 2, compact 720x640 @ 2, known-revision fixture, actual shared library dialog; `phase3-ui-final.log` |
| UI interactions and pixels | Controller selection, touch permission/reset and back checked; known/unknown identities never activate a modifier. Viewed final PNGs in `phase3-ui/` and `docs/manual/shots/enhancements.png`; list scrolls to remaining rows |
| Android APK | Debug build passed with pinned native cores; no new native memory-write implementation |
| Manual and translations | Eight manual/search tests passed; 1,261 translation entries, 1,113 used keys, zero issues; local website build passed with 29 pages, nothing deployed |
| Independent review | Authenticated, tool-free Claude source review completed; accepted fixes and qualified findings recorded in REVIEW_LOG.md |
| Hardware and assets | Escalated `adb devices` returned an empty list. No ROM, BIOS or user-save assets were accessed; no live gameplay or Thor acceptance claimed |

All log/report paths above are under ignored `build/fork-review/`. The full suite
finished before the final review fixes; the subsequent focused run verifies those
changes. Its six failures match the Windows baseline table above. This is not a clean
full-suite pass. The production registry is intentionally empty, so mock adapter tests
cannot certify JNI writes, actual game code signatures, safe-point timing or SRAM.

The final corrections preserve a blocked automatic-resume file, keep state requests
queued until safe, roll back partial writes even when the bridge throws an Error,
prevent stale settings overwrites, and disable reset for unreadable settings.
Ordinary core shutdown still finishes SRAM saves; failed rollback prevents further
game frames. Existing bounded shutdown timeouts remain and need native-adapter tests
before admitting a real hook. Existing cheats and legacy gameplay options are separate.

Handoff source commit: `47ec1ad943309d817439ad1146b5cd099fcc6382`.
[Draft PR #3](https://github.com/nikitasokolov01/pokedaisy/pull/3) targets the fork's
integration branch and includes the unmerged Phase 1 and Phase 2 prerequisites.
Local APK: `build/fork-review/pokedaisy-phase3-47ec1ad-debug.apk` (37,336,846 bytes).
SHA-256: `c0f2241718a5e75d0b2959f4c5df3e1f56f19e50964ef5dd0625d31dc6b31d29`.
The committed-source build passed in `phase3-handoff-build.log`; packaged DEX was
checked for the fingerprint, guarded runtime and enhancement-page classes. The APK
is ignored and is not included in Git. Later handoff documentation does not change it.

### Phase 3 Thor settings acceptance checklist (pending)

1. Keep existing app data while installing the debug build. For a legal DS game,
   open ENHANCEMENTS from its library menu and companion SETTINGS. Confirm readable
   status/reasons, visible controller focus, touch selection and scrolling to RESET.
2. Enable permission, close and relaunch the game, and verify it persists for that
   exact ROM. The four gameplay modifiers must stay N/A even for a known revision.
3. Rename or archive the same ROM and verify settings follow its contents. A different
   revision or modified image must have separate settings and no approved modifier.
4. Reset one ROM, then relaunch and confirm all its choices are off while another
   game's permission is unchanged. Unsupported games must retain ordinary playback.
5. Suspend, resume, use save/load and soft reset with no production hooks. Check
   ordinary game behavior and existing save files. These real-core checks remain
   pending; synthetic state-boundary tests do not replace them.
