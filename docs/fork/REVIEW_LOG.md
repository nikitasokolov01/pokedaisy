# Independent review log

## 2026-10-20: phase 0 and first input slice

Claude Code 2.1.218 is installed and reports an authenticated first-party session.
The master brief authorizes this second review. Invocation uses `--safe-mode --print
--tools= --no-session-persistence --output-format text`: customizations and tools are
disabled; only supplied public source excerpts are reviewed. No ROM/save/credential
content is included. Local request/output files live under ignored `build/fork-review/`.

Status: actual review received. Accepted after inspecting the call sites: DS mappings
already reach the core; release stale holds on map/core-mode change, controller loss,
focus loss and suspend; add all-12-button and GBA regressions. A conservative global
release temporarily affects another connected pad too; per-device ownership remains
unfinished Phase 1 work.

Corrections/limits: Claude described the mapping as "verified correct" from excerpts;
that is a source review, not a hardware or JNI execution result. Its suggestion that
an old key always self-corrects on release is too broad: removing the key's binding
makes the old `onKey` return before removing the hold. The proposed reset fixes both.
The `ds` flag is assigned at game start in the activity, but clearing holds on a mode
change is still useful defensive behavior. Its separate scripted `pressBits` concern
is deferred to the lifecycle audit; synthetic presses belong to engine jobs and must
not be silently canceled by an ordinary controller disconnect. Chord-prefix leakage
also remains open; this slice must not claim complete hotkey isolation.

## Reusable review request

Review the public PokeDaisy input diff for stale held keys after rebind, focus loss,
suspend or controller removal; verify DS X/Y marker translation and GBA compatibility.
Trace Android events through GbaInput, EmulatorEngine, JNI and melonDS SetKeyMask.
Check turbo, multi-key mappings and hotkey cancellation without firing one-shot actions.
Treat chord-prefix leakage and per-device ownership as separate unfinished requirements.
Use synthetic inputs; do not request ROMs, saves, credentials or guessed patch offsets.
Return actionable findings and missing tests, with no source edits.

## First-slice diff review

Actual second Claude response received (same authenticated tool-free invocation).
Accepted: ignore removal of a peripheral that has not supplied game input; track
observed key/joystick device IDs until reset. Full per-device button ownership remains
open. Removed duplicate setup explanatory copy. Verified `input` is an eagerly
initialized `val`, not `lateinit`; setters are called on startup/resume/rebinding,
not per-frame or during UI recomposition. Turbo is derived from `turboMask` per engine
frame, and cancellation tests assert it clears. Engine-script channels intentionally
remain independent.

Not adopted: keeping held keys through display-move focus loss. This fork intentionally
neutralizes player input at every focus boundary because Android can lose key-up;
emulation continues during the existing display-move exception. Hardware verification
must check that tradeoff. Not adopted: restoring GBA prefixes on common global labels.
Those bindings are shared with DS; explicit dual names on X/Y explain their differing
semantics. DS companion rows use DS labels; GBA companion rows retain GBA labels.

This review is source feedback, not executed tests or Thor acceptance.

## Phase 1 profile and routing review (2026-10-20)

An actual third Claude review was received through the same authenticated, tool-free
CLI. Input was limited to the public profile store, shortcut/router and input-mask
sources, with the explicit modifier-first policy. Request and response are retained
locally as `build/fork-review/phase1-profiles-review*.txt`.

- Accepted: independently edited parent profiles can combine into duplicate controls.
  Effective mappings now fail closed for duplicate keys and equal/subset shortcuts,
  with KEY IN USE indicators and a combined-scope repair test. Move confirmation removes
  inherited conflicts only in the edited scope.
- Accepted: the unused `GbaInput.onMotion` global hat state could bypass per-device
  ownership. Removed it after confirming that all live motion uses ControllerInputRouter.
  The pure motion-to-direction helper remains for existing companion navigation.
- Accepted in part: sync the profile directory after rename where Java/the host permits.
  File content is synced before replace; Windows can reject directory sync. No stronger
  power-loss durability claim is made. Legacy input files are read without rewriting;
  unknown-version profiles are preserved and reported. Save files are not involved.
- Superset shadowing: current runtime rejects all ambiguous equal/subset bindings and
  the UI detects them before save. Therefore the reported permanently shadowed smaller
  chord is addressed by explicit conflict handling, not a hidden timeout.
- Chord order: deliberate policy, documented in the manual. The first captured/listed
  button is the reserved modifier. Pressing a shoulder first is ordinary game input;
  adding the modifier afterward cannot retroactively create an isolated shortcut.
  Reserving every possible chord participant would delay ordinary game holds.
- `setControls` clears per-device maps intentionally: activity changes first cancel the
  router, and new device states resolve their current profiles. Toggle/rebind cancellation
  is covered by focused tests. Device removal otherwise releases only that device.

Additional local review found reentrant UI changes during a multi-axis event and analog
quick-menu releases being skipped. Routing now ends the game batch when UI takes input,
and menus still receive shortcut releases for the next press. Regression tests cover both. No review here
constitutes live Thor testing or native execution with a ROM.


## Phase 2 lower-screen review (2026-10-21)

An actual Claude review was received through the authorized, authenticated CLI in
safe, tool-free print mode. Only the public routing/touch/navigation sources and
integration diff were supplied. Request and response are retained locally as
`build/fork-review/phase2-review*.txt`. This was static review, not hardware testing.

- Accepted: an automatic return after a companion battle/field action must not
  overwrite the user's remembered lower-screen choice. `NdsBottomHost.show` now
  distinguishes a temporary transition from a remembered choice while notifying
  ownership changes in both cases. The Android host test checks this distinction.
- Accepted: unmapped controller repeats should be consumed just like initial presses
  while the companion owns input. The activity now consumes gamepad/button/D-pad
  repeats without allowing the main window's default dispatch to use them.
- Camera reset report: not a regression. The full `releasePlayerInput` body already
  zeros both right-stick camera components; that unchanged context was absent from
  the supplied diff. Window changes call this helper.
- Touch release report: the initial unconditional neutral requirement was corrected
  during local review. It is armed only when touch input was held, and callbacks now
  update the observed touch mask even when gameplay is blocked. Holding across a
  boundary intentionally requires release; a fresh first tap with no prior hold is
  not discarded.
- Secondary-display focus remains a hardware gate. The layoutlib test exercises the
  real Android ComposeView D-pad/activation adapter, and the desktop checks exercise
  selection, tabs, overlays and explicit text entry. Neither emulates Thor firmware,
  Android's multi-display focus service, lid sleep or its software keyboard.
- Quick menu scope checked in the complete source: `quickMenuTakes` returns immediately
  when closed. It runs before companion routing only while the quick menu is open.
- B from explicit text editing was exercised: the editor becomes its ordinary focus
  stop again and no keyboard is requested by simply traversing the field.
- The reviewer found no multi-touch ownership defect. Multiple controllers can each
  advance navigation independently; their releases and repeats remain device-local.

No review author attribution is added to commits. No ROM assets or credentials were
sent to the reviewer, and no gameplay hook or save format was changed.
