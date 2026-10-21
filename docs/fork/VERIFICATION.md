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

## Input slice checks

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

1. Back up the app's existing data before installing a development APK. The debug
   signer may differ from the installed release; do not uninstall to resolve this
   without preserving saves, states and settings.
2. Open a user-provided DS game. In companion SETTINGS > GAME BUTTONS, locate DS X
   and DS Y. Rebind each to a distinct physical button, leaving the capture via touch
   if necessary. Confirm X/Y act in the game's own menus and START/SELECT stay distinct.
3. Check the library's GAME BUTTONS page and first-time setup: X/Y must be discoverable
   there too. Close and relaunch, then suspend/resume, and verify the bindings persist.
4. Check A/B, X/Y, L/R, START/SELECT and four directions. Test the left stick, hat and
   a USB/Bluetooth pad. Direction remapping and separate per-device profiles remain
   future work; do not mistake the existing fixed directions for completed remapping.
5. Hold a button, then disconnect that controller. Confirm no movement or turbo stays
   active, touch controls appear in AUTO, and reconnect works. Repeat with FF HOLD.
6. Hold a game button or FF/slow-mo shortcut while opening Android UI or suspending.
   On return, input must be neutral until pressed again. A toggled FF preference should
   remain unchanged. Check ordinary input on the second screen and display swapping.
7. Run a GB/GBC/GBA game: the additional START/SELECT bindings retain their old behavior;
   turbo, chord shortcuts and manual/automatic states retain their existing behavior.
8. Chord-prefix leakage is a known remaining issue. This slice must not be accepted
   as satisfying the full hotkey-isolation requirement.

No new feature writes ROMs, saves, party/PC data or challenge state. No HGSS modifier
or Gen 5 capability is certified by this checklist.
