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
