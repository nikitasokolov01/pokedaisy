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

## Phase 3 enhancement review (2026-10-21)

The authorized Claude CLI completed an actual source-only, tool-free review of the
verification, settings, runtime, session, UI and engine integration. Request and
response are retained as `build/fork-review/phase3-review*.txt`. It found no issue in
the bounded full-file hashing, header-spoof denial, empty production registry or
normal signature/transaction checks. Its findings were independently assessed:

- Accepted: distinguish a blocked state transition from a failed native state call.
  The runtime now returns BLOCKED/FAILED/SUCCEEDED. Blocked resume keeps its file;
  ordinary state requests stay queued before touching backups. Normal shutdown
  still finishes SRAM writes, then destroys the core and discards runtime ownership.
  Failed rollback prevents additional game frames. No production adapter is active.
- Accepted: a bridge Error after a partial write must run rollback too. The forward
  transaction now catches Throwable and a synthetic LinkageError test checks that
  every attempted site is restored before the game can continue.
- Accepted: reset should not appear usable for unreadable settings. Its row is now
  disabled whenever settings are not editable, preserving the original file.
- Accepted: independent library/game settings instances could overwrite stale
  choices. Atomic writes now share a lock and compare expected previous values;
  a two-session test proves a stale reset is rejected without losing the newer setting.
- Qualified: SHA-1 is used only for catalog recognition because the primary source
  publishes those complete-image identities. No SHA-256 reference was invented.
  A production plan must additionally pin SHA-256 and its adapter must verify the
  actual loaded image. The production registry remains empty. See ROM_VERIFICATION.md.
- Kept: permission may be recorded for an unknown or unsupported ROM. It grants no
  capability, cannot turn any unavailable choice on, and leaves all individual
  choices off. This is explicit in the UI/manual and covered by tests.
- Documentation not included in the external source bundle was reviewed locally:
  recognition, companion support and gameplay modifiers are separate claims;
  no new modifier or native-device certification is stated.

Local UI review also replaced polling with observable state, fixed subtitle clipping,
and adjusted compact text/column sizing. The initial desktop harness needed to account
for the existing four pixel-shadow text nodes; final assertions check the rendered
status and actual session state. All screenshot evidence is synthetic.
Final local review also uses the existing option picker for future choices with more
than two values, in accordance with the project menu rules. Those choices remain
unavailable with the empty production registry. Stale-page recovery instructions
explicitly reopen a library session or relaunch the game.

## Phase 4 mechanics review (2026-10-21)

The authorized authenticated Claude CLI completed a tool-free review of the new rule
sources, tests, feature/admission document and native EXP source excerpts. Request and
response are retained as `build/fork-review/phase4-review*.txt`. No ROM, save or secret
was supplied. The review correctly treats these modules as disconnected rule logic,
not live gameplay support. Its concrete findings were assessed as follows:

- Accepted: an inaccessible Gym with an unknown defeat flag was omitted from the
  uncertainty result. Unknown defeat/accessibility now takes priority over the
  inaccessible branch, with a regression covering ambiguous post-Gym state.
- Accepted: Fly needs a bounded destination and request-bound validation. Invalid
  destinations are rejected before inspecting native data; results carry the exact
  checked request and stale action/destination results cannot invoke the port.
- Accepted as future-proofing: HM policy handling is an exhaustive enum branch,
  so adding history-based possession requires an explicit implementation change.
- Strengthened: HM constants had been inspected against the primary source locally,
  but the reviewer only received EXP excerpts. A reproducible source checker now
  validates pinned blobs and independently extracts the HM/item/badge associations;
  its digest is checked by the Kotlin test, alongside the six growth-curve digests.
- Qualified: source excerpts absent from the external bundle do not mean they were
  not inspected locally. HGSS_MECHANICS.md separates source checks, synthetic contract
  tests and pending native evidence. No native compatibility claim was made.
- The reviewer found no EXP pooling/boost/rounding or cap arithmetic defect. It agreed
  that callback/text barriers and unknown native field conditions must remain gated.

Local review also rejects incomplete party slot sequences before calculating pools.
Final focused tests cover these fixes; the earlier full suite predates the review
corrections. Soft-cap and HM-possession proposals remain unapproved explicit arguments.
No native hook, adapter, progression decoder or production patch is registered.
