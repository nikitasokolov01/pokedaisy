# HGSS mechanics: rules and hook admission

Status on 2026-10-21: **partial Phase 4, no enabled gameplay modifier**.
The user confirmed that no local legal HGSS ROM or disposable save assets are
available. No production adapter, patch bytes, native action port or registry entry
has been added. The new code is independent rule logic exercised by synthetic tests;
it is not connected to the emulation loop. Both known retail identities still show
N/A in ENHANCEMENTS. Ordinary emulation and existing legacy features are unchanged.

## Source evidence

The public `pret/pokeheartgold` source was inspected at commit
`6988158691eb6178cff697cda0428045ddcba5fb`. Only source text and repository metadata
were fetched. Source excerpts are kept under ignored `build/fork-review/hgss-source/`;
no commercial ROM, BIOS, compiled game or save was downloaded. No external source
code or game asset was copied into the application.

| Area | Primary source and relevant boundary |
| --- | --- |
| EXP calculation | [battle_command.c](https://github.com/pret/pokeheartgold/blob/6988158691eb6178cff697cda0428045ddcba5fb/src/battle/battle_command.c), `BtlCmd_CalcExpGain`, `Task_GetExp` |
| Award scheduling | [GrantExp script](https://github.com/pret/pokeheartgold/blob/6988158691eb6178cff697cda0428045ddcba5fb/files/battledata/script/subscript/subscript_0276_GrantExp.s), native calculation then start/wait task |
| Growth thresholds | [growtbl.csv](https://github.com/pret/pokeheartgold/blob/6988158691eb6178cff697cda0428045ddcba5fb/files/poketool/personal/growtbl.csv), six used curves, levels 1 through 100 |
| Interactive text | [text.c](https://github.com/pret/pokeheartgold/blob/6988158691eb6178cff697cda0428045ddcba5fb/src/text.c) and [render_text.c](https://github.com/pret/pokeheartgold/blob/6988158691eb6178cff697cda0428045ddcba5fb/src/render_text.c), printer tasks, callbacks and states |
| Field eligibility | [field_move.c](https://github.com/pret/pokeheartgold/blob/6988158691eb6178cff697cda0428045ddcba5fb/src/field_move.c), `FieldMove_Check*` and action tasks |
| Item/badge identity | [item.c](https://github.com/pret/pokeheartgold/blob/6988158691eb6178cff697cda0428045ddcba5fb/src/item.c) HM-to-move table, [items.h](https://github.com/pret/pokeheartgold/blob/6988158691eb6178cff697cda0428045ddcba5fb/include/constants/items.h), [badge.h](https://github.com/pret/pokeheartgold/blob/6988158691eb6178cff697cda0428045ddcba5fb/include/constants/badge.h) |

Source correspondence is evidence for arithmetic and control flow. It does not prove
loaded overlay addresses, instruction signatures, hook compatibility, native memory
ownership, save integrity or device timing. Existing native companion addresses were
not promoted to enhancement approval merely because their comments mention HGSS.

`scripts/verify_hgss_rule_sources.py --source-dir <public-source-checkout>` checks
five pinned source blobs and reproduces the six growth digests plus the HM mapping
digest. It performs no network or ROM access. HM associations are extracted from
the native item-to-move table and each field check's actual badge guard. Source-level
mapping/arithmetic checks, planner tests and unexecuted native tests are separate
evidence categories; none substitutes for the last category.

## Decisions

- **Approved by the user in this chat:** participants retain their vanilla awards;
  nonparticipants receive half their individual unsplit award, rounded down; held
  Exp. Share recipients receive the larger of vanilla and modern awards, never both.
- **Pending:** the proposed soft-cap boundary is the start of the cap level, with
  floor rounding per award and no stored fractional credit. The implementation takes
  an explicit policy argument; null disables that calculation. It is not a default.
- **Pending:** current HM possession versus once-obtained history. The candidate
  current-bag rule requires an explicit policy argument. There is no unlock-history
  store, default policy or item/progression write.
- Still unresolved: actual Gym accessibility evidence under story branches, Elite
  Four/Champion/Red/post-Gym caps, manual overrides, and behavior for new shared
  recipients' EVs. None is silently inferred. Native Task_GetExp grants EVs too, so
  adding recipients requires an explicit audit separate from EXP arithmetic.

## Independent implementations

### EXP awards

`enhancements/hgss/HgssExperience.kt` plans a single defeated enemy's awards using
the player-party snapshot. It retains the native participant/held-item pools and
sequential integer rounding for held boosts, trainer battles and traded recipients.
The modern half is taken after the recipient's unsplit boosts. Fainted, empty, egg
and level-100 slots receive zero; living level-100 slots still count in native pool
denominators. Invalid/incomplete slot sequences, zero living participants and special/unknown battle
kinds return no plan. Single and ordinary double battle calculations are covered;
multi/partner ownership remains unapproved. Turning sharing off selects vanilla.

There is no accumulated EXP balance or event journal. Repeating a calculation has
no side effect. A native hook must extend recipient selection and substitute the
award inside the original task, before its native update, while retaining the
level-up/move/evolution path. A post-battle overwrite is specifically excluded.
Exactly-once delivery across task repeats, multi-KO and state loads is a future
native integration gate, not something these pure tests certify.

### Caps and progression

`HgssLevelCaps.kt` uses the six existing numerical growth tables, with HGSS's zero
EXP at level 1. Independent digests from the pinned HGSS CSV check all 600 thresholds.
Hard caps clip at `threshold(cap + 1) - 1`; cap 100 clips at the level-100 maximum.
Already-over-cap EXP is never removed. Candidate soft caps split a crossing award
into its normal portion and a 10% remainder. Arithmetic is bounded even for a huge
input award. Invalid growth-state values or unresolved caps yield no adjustment.

The resolver accepts an observed defeated/accessibility state for every one of the
16 Gyms and selects the smallest accessible undefeated ace level, retaining ties.
Missing or uncertain observations remain explicit. With no accessible Gym or after
all Gyms, it returns an unresolved state rather than inventing a league/postgame cap.
Ace levels are cross-checked against the existing generated HGSS trainer teams.
There is no live progression decoder and no claim that the guide's linear next-boss
ordering proves accessibility. Future verified progression events must supply a
fresh complete observation; reset/load must invalidate it before another award.

### Text speed

`HgssText.kt` plans only a prefix of ordinary glyphs. ORIGINAL leaves the native
step alone; candidate VERY FAST allows four glyphs and INSTANT at most 1,024 per
invocation. Every newline, control code, page/end marker or unknown token yields to
the original task. Nonzero printer states, callback-driven text, automatic scrolling
and unidentified dialogue cannot accelerate. It never produces controller input.

The game's synchronous instant-speed constructor is unsuitable as a blanket patch:
its rendering loop has different printer lifetime and callback behavior. The future
hook must validate the printer context, classify tokens without treating control
payloads as glyphs, and retain waits, choices, sound callbacks, fades and scripts.
The current code is a planner, not a native decoder or renderer hook. Stopping at
newlines is deliberately conservative until real text-box traces are available.

### HM actions

`HgssFieldMoves.kt` maps all eight HGSS HMs to their own item and badge. WORLD and
COMPANION requests enter the same fresh-context eligibility/action gate. It requires
opt-in permission, an explicit possession policy, idle field state, the item, the
badge, story approval and the complete native location check. Fly additionally needs
a destination within the existing HGSS fly table and an unlock result bound to that
exact request; other moves reject a destination argument. Invalid targets are rejected
before a port can index a table. A stale generation or a result for a different
action/destination is rejected. No party-knows-move condition
is introduced, and the API exposes no progression or save mutation operation.

A future native port must preserve all FieldMove_Check conditions, including map
type, avatar, follower/costume restrictions and special tiles/areas. It must atomically
recheck generation and busy state when scheduling an ordinary action, without a
fabricated party slot. Removing a move requirement also needs world-interaction
hooks; the shared gate alone does not implement those hooks. No native port exists
in this build, and repeat presses/cutscene transitions need real-core verification.

## Compatibility and admission matrix

| Mechanic | HG US revision 0 | SS US revision 0 | Unknown/modified/other ROMs |
| --- | --- | --- | --- |
| EXP Share | Rule planner tested; native hook unverified/off | Same | No new hook |
| Hard/soft caps | Arithmetic/resolver tested; live progression absent/off | Same | No new hook |
| Text speed | Glyph/barrier planner tested; renderer hook absent/off | Same | No new hook |
| HM upgrades | Shared eligibility/action contract tested; native paths absent/off | Same | No new hook |

Neither retail SHA-1 recognition nor synthetic metadata grants these capabilities.
Before registering any production feature, independently verify the exact ROM's
SHA-256, pristine loaded image, native signatures and safe-point behavior. Then run
each feature's legal-asset checks separately on both games. Enablement stays per
feature, per revision and default off. The Phase 3 registry remains empty.

## Required game/device checks (not executed)

1. Preserve original files and use disposable save copies. Confirm both exact ROM
   identities, all native signatures and default-off vanilla behavior. A modified
   or unknown image must receive no enhancement writes.
2. EXP: participant counts, held Share and boost items, trades/languages, fainted and
   level-100 slots, eggs, double knockouts, excluded battle modes, level-ups, moves
   and evolution. Capture awards at the native boundary; ensure one award per enemy
   per recipient, with no unintended EV or script changes.
3. Caps: all six curves, crossing/exact boundaries, already-over-cap creatures,
   simultaneous shared awards, nonlinear Johto and Kanto access, uncertain story
   data, and state load/reset. No hidden EXP or arbitrary postgame cap is permitted.
4. Text: multiple pages, choices, scripted pauses, callbacks/sounds, battles, fades,
   cutscenes and changing speed mid-box. Reveal text without advancing a prompt;
   validate printer allocation/lifetime and every supported control token.
5. HMs: all eight through world and companion paths without a known party move;
   missing item/badge, blocked story, wrong tile, follower/costume, locked Fly target,
   map change, repeated request, script/battle busy state and state load/reset.
   Confirm ordinary native scripts/animations and unchanged progression flags.
6. Toggle each verified hook off and on at its safe points; save/load, reset, sleep,
   close and reopen. Validate rollback failure stops the core and does not persist
   corrupted state. Record AYN Thor controller/touch and dual-display behavior.
