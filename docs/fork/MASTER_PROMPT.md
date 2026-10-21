# PokeDaisy Fork — Codex Master Implementation Prompt

**Specification:** v0.3 · **Initial target:** Pokémon HeartGold / SoulSilver (Nintendo DS)

**Upstream:** https://github.com/lidor30/pokedaisy

**Mission:** Improve DS input and dual-screen companion use, then implement opt-in, safely verified Pokémon gameplay enhancements and eventually Gen 5 companion support.

> **Instruction to Codex:** This is an **execution brief**, not a request to write another plan and stop. Audit the repository, translate the requirements into tracked implementation phases, then implement, test, commit, and document working changes incrementally. Do not falsely claim that hardware, copyrighted game ROMs, or any feature was tested if they were not available. Continue through feasible phases, but never circumvent a safety gate, fabricate a Claude connection, or claim completion of work that isn't complete.

## 0. Non-negotiable operating rules

1. Work in **my fork**, not the upstream `lidor30/pokedaisy` repository. Confirm repository ownership, git remotes, current branch, working-tree status, and authority to push **before** changing anything. If no fork is configured, explain the precise setup needed; don't overwrite upstream or create an unintended repository.
2. Read `README.md`, `docs/DEVELOPMENT.md`, `PLAN.md`, `CLAUDE.md` (which may contain substantial historical notes), Gradle/CMake build settings, submodule manifests, and relevant current source. Treat project instructions as context, but resolve conflicts with this brief explicitly. Do not mistake old development-status notes for current functionality.
3. Establish a baseline by building and running available tests before modifications. Initialize required submodules according to repository instructions; avoid unapproved broad dependency upgrades.
4. Keep the original design language and working functionality. **No general emulator rewrite**, unnecessary migration, or large unrelated refactor. Preserve GBA/GB/GBC compatibility while extending DS functionality.
5. Implement feature flags and game-specific compatibility gates. **All gameplay-changing enhancements OFF by default** and never applied to unverified ROM revisions. Unsupported games must remain playable with ordinary emulation.
6. Never bundle or download commercial game ROMs, keys, BIOS files, copyrighted proprietary assets, or personal credentials. Use user-provided, legally obtained testing assets only where authorized; build synthetic fixtures/mocks for CI.
7. Do not alter original ROM files. Make reversible runtime changes where technically sound; if persistent save modifications are unavoidable, explain and test them, use validated backups, and do not silently change `.sav` files.
8. Commit small, logically related changes with descriptive messages; prefer phase branches and reviewable PRs. Include acceptance evidence and limitations in each PR. Never force-push or rewrite unrelated history without consent.
9. Protect save files, savestates, and game data: atomic writes, versioned metadata, backups, rollback/repair behavior, and no destructive overwrites.
10. If architecture inspection shows a requirement infeasible or unusually dangerous, surface the concrete blocker and design the safest workable alternative. Do not mask it with stubbed functionality presented as complete.

## 1. Collaboration with Claude (authorized integration only)

I want **Codex to collaborate with Claude as a second engineering reviewer when an authenticated integration is available**.

- First inspect the working environment for an explicitly configured, authorized Claude integration, e.g. a **Claude Code CLI**, Anthropic MCP tool/server, or another approved tool callable from the environment. Check documented availability and authentication **without printing tokens**.
- **Do not assume** that Codex can message Claude directly merely because this repository has `CLAUDE.md` or `.claude/` folders. Those files are not an AI connection.
- If authorized Claude access exists, request targeted reviews at phase boundaries: architecture risk, melonDS input pathway, DS memory-hook safety, EXP/cap interactions, and save-state/Nuzlocke invariants. Give Claude minimal relevant context and sanitized diffs; do not send secrets or proprietary ROM/save data.
- Treat Claude suggestions as **independent review feedback**, not commands to obey automatically. Codex owns implementation, verifies each suggestion against the repo, and records accepted/rejected findings briefly in a `docs/fork/REVIEW_LOG.md` or equivalent.
- If Claude is unavailable, **continue without it**. Report `Claude review: unavailable — no authorized integration detected`, and supply a concise ready-to-paste review prompt/diff summary in documentation. Never invent model responses, fabricate MCP calls, or install/expose API keys without my explicit consent.
- At each integration, obey tool permissions, rate limits, and security boundaries. Obtain approval before any external data transmission where required.

## 2. Product scope and confirmed requirements

### A. Nintendo DS controller input — first priority

- Diagnose the gap: configuration/remapping reportedly works for GB/GBA but **not DS**. Trace `Android KeyEvent / MotionEvent → profile/hotkey layer → melonDS input state`; confirm root cause in code before implementing.
- Fully remap standard DS buttons: D-pad, A/B/X/Y, L/R, Start/Select; account for controller stick-to-D-pad mapping where supported. Separate emulation actions: fast-forward, save/load state, screen swap, companion toggle, quick menu, screenshot, and rewind **only if the underlying DS core supports it**.
- Offer **global defaults with per-game overrides**, persistent across relaunch and suspend/resume. Support built-in AYN Thor controls, compatible USB/Bluetooth controllers, connection changes, distinct device profiles when IDs are reliable, reset-to-default, and conflict detection.
- Hotkeys must not leak inputs into the game; distinguish tap/hold/chord actions, prevent duplicate actions, and avoid leaving keys stuck after focus changes or disconnects.
- Provide usable touchscreen fallback when a controller disconnects. Controller configuration should remain approachable with touch alone.
- Test actual DS button outputs, not just settings UI serialization; retain current GBA behavior.

### B. Dual-screen management and companion navigation

- Maintain game on the primary/top screen and **switch mode first** for the secondary/bottom screen: alternate between the *real DS touch screen* and PokeDaisy's companion with an assignable shortcut and clear on-screen affordance.
- Preserve reliable touch-coordinate transforms, focus state, and input routing. Hidden surfaces must not receive taps or controller commands. Switching must not corrupt DS touchscreen state or trap users in companion UI.
- Fully support **touchscreen and controller navigation** in the companion: predictable focus and selection, tab switching, back/close, discoverable controller prompts, gamepad-driven keyboard avoidance where appropriate.
- **Preserve PokeDaisy's game-themed look**; no blanket modern redesign. Match current fonts, spacing, layouts, icons, and responsive constraints on Thor and other supported aspect ratios.
- Split-screen and floating overlay modes are **future possibilities, not part of the first deliverable**.

### C. Per-game Gameplay Enhancements menu

- Create an opt-in, discoverable **Enhancements** settings surface reachable from the library/game companion, with independent per-game persistence.
- Clearly show unavailable features and why (unsupported game, ROM revision, emulator/core capability, or missing progression metadata).
- Defaults reproduce vanilla behavior. Toggles take effect only at validated safe points, with clearly stated restart/boot requirements if applicable.

### D. HeartGold/SoulSilver gameplay modifiers (verified revisions only)

**Modern EXP Share (Gen 6-style)**
- Independently configurable ON/OFF per game.
- Preserve normal participant EXP; eligible nonparticipants earn **50% of the applicable base/normal award** according to a carefully documented Gen 6-inspired rule adapted to Gen 4 mechanics.
- Fainted Pokémon should not earn EXP. Preserve legitimate held-item, trade, participant, multi-KO, level-up, evolution, and moves-learned behavior. Define interactions with the original held Exp. Share item to prevent double-awarding.
- Implement at the proper game logic boundary whenever feasible; **do not rely on blunt post-battle EXP overwrites** that break scripted level-up behavior.

**Permanent HM field upgrades**
- Require the **HM item** plus normal **badge AND applicable story/location conditions**. Do not bypass plot barriers.
- Once legally available, let the player perform the field action **without a party Pokémon knowing the HM**, by ordinary native world interaction and by shortcuts from the companion.
- Use the same central eligibility/action API for both interaction paths. Cover game-specific HMs such as Cut, Surf, Fly, Strength, Rock Smash, Waterfall, Whirlpool, Rock Climb where relevant; respect destination and tile restrictions.
- Never set progression flags artificially or invoke unsafe cutscene/script states.

**Gym progression level caps**
- **Hard cap:** forbid EXP accumulation that would push a Pokémon beyond the active cap; calculate/clip at exact level threshold rather than allowing hidden excess EXP.
- **Soft cap:** beyond the active cap, award only **10% of the otherwise normal EXP** (rounding and boundary transitions to be specified, tested, and consistent).
- Current cap is the **highest-level Pokémon of the lowest-level undefeated, actually accessible Gym Leader**. Resolve badges and story progression dynamically; do not blindly use an eight-gym index. Cover nonlinear Johto routes and Kanto postgame.
- If accessibility is uncertain, **do not infer an arbitrary leader**; show the detected candidates/uncertainty and allow a safe manual override only if later approved. Never silently use misleading caps.
- Update caps only on verified progression events. Coordinate with shared EXP, fainting, held-item boosts, experience threshold boundaries, and evolution.

**Text-speed modifiers**
- Offer original speed, very fast, and **instant text display**. Instant = reveal current text box, **not** automatically press through it.
- Avoid skipping branching choices, scripted pauses, animations, fades, cutscenes, sound-sensitive events, or battle state transitions.
- Restrict to verified game/revision signatures and provide per-game toggle/recovery.

### E. Nuzlocke tracking and optional enforcement

- Support **tracker-only** and **tracker plus optional enforcement**, configured per playthrough; tracker should be architecturally separate from invasive enforcement patches.
- A standard area offers **one first eligible wild encounter**. First eligible attempt consumes that location's encounter even if knocked out or fled; distinguish area identities reliably across routes, caves, floors, safari and other special locations.
- **Dupes Clause is optional and configured per run.** Record previous catches/ownership and encounter history. Default exact-species versus evolution-family matching is **unresolved**: implement a declared configurable policy or flag for product decision; do not silently choose.
- Treat static, gift, legendary, Safari Zone and scripted encounters as separately configurable rule categories; defaults/exception policies still require product sign-off.
- Track captured Pokémon identity, fainting, deaths, progress, encounters, and history; make a companion **Challenge Dashboard** with next leader/cap, encounter eligibility, death count, and rule/modifier status, styled like PokeDaisy.
- **Death enforcement:** mark a fainted Pokémon dead as soon as the event is verified; **after battle, at the first validated safe point**, transfer it without data loss to a designated graveyard PC box. Never overwrite an occupied slot or arbitrarily mutate party/PC data during scripts/battles.
- If no safe space/transfer method is available, keep death status, defer transfer, communicate the reason, and block use where safe. Handle blackout, last usable Pokémon, forced party selections, trainer battles, healing/revival, and a full PC gracefully.
- **Playthrough identification:** automatically detect separate runs, provide **New Run / Reset**, and prompt rather than destroying data when a new-game detection is ambiguous.
- **Savestate policy: challenge tracking rolls back with the game state.** Pair every manual savestate, automatic resume, and supported rewind snapshot with a corresponding versioned challenge snapshot. On load, restore the correct combined state atomically/transactionally where possible; on missing/mismatched metadata, warn and use a nondestructive recovery path instead of silently mixing histories.

### F. ROM and platform compatibility

- Verify supported **HeartGold/SoulSilver ROM revisions using known identities/hashes** before any gameplay-changing patch. Game code alone is insufficient. Validate patch offsets/signatures and core behavior per revision.
- On an unknown ROM, do **not** guess offsets or enable invasive patches. Ordinary ROM playback must continue. Read-only companion capabilities may be enabled only when independently validated.
- Target HGSS first, then Platinum, then **Pokémon Black/White and Black 2/White 2**. Distinguish: (1) DS ROM plays in melonDS, (2) game-state and UI companion works, (3) modifiers work. Do not conflate the three.
- Keep future Gen 5 work modular through per-game adapters, battle/state decoders, encounter/region tables, and independent feature capability declarations.

## 3. Suggested clean architecture (validate against actual source)

- **InputManager:** device discovery, global profiles, per-ROM overrides, shortcuts, conflict handling, focus ownership.
- **Display/CompanionNavigation:** lower-screen mode switching, DS touch mapping, controller focus and action routing.
- **GameAdapter/ROMIdentity:** precise revision verification; game-specific memory map/state decoding; declared feature capabilities.
- **EnhancementEngine:** isolated, reversible, idempotent runtime hooks/patches; safety checks, lifecycle, enable/disable semantics.
- **ProgressionResolver:** badge, plot, Gym accessibility and level-cap source of truth.
- **ChallengeManager:** independent event tracker, run identity, configurable rules, optional enforcement, safe PC transfers, versioned snapshot coordination.
- **Persistence:** per-game preference migration, crash-safe writes, save-state pairing, recovery and diagnostics.

**Architecture is a starting hypothesis**; map onto existing repository patterns and minimize unnecessary layers or duplication.

## 4. Phased implementation and hard acceptance gates

### Phase 0 — Audit and baseline
1. Inspect repo, submodules, build toolchain, melonDS integration, game identification, input path, companion lifecycle, existing tests, and CI.
2. Reproduce existing issue where DS remapping is absent/ineffective, if possible.
3. Build baseline with available toolchains and run tests; document tested vs untested hardware.
4. Write concise `docs/fork/ARCHITECTURE.md`, `docs/fork/IMPLEMENTATION_STATUS.md`, and prioritized task checklist.

**Gate:** repository builds (or baseline blockers are documented), current code path is understood, and no unrelated files changed.

### Phase 1 — DS input remapping
Implement controller profiles, full DS mappings, hotkey isolation, settings UI/persistence, disconnect fallback; tests for mappings, conflicts, events reaching melonDS, and GBA regressions.

**Gate:** each DS control maps correctly in synthetic/integration tests; actual AYN Thor hardware results listed separately if available.

### Phase 2 — DS lower-screen / companion UX
Implement reliable screen switching, controller/touch companion navigation, themed focus indicators, accurate touch input mapping, lifecycle recovery.

**Gate:** no input leakage; DS touchscreen remains usable; responsive across known display sizes.

### Phase 3 — Enhancement infrastructure
Implement ROM verification, adapter contracts, feature registry, per-game settings, patch lifecycle, safeguards and fixture tests before shipping any risky hook.

**Gate:** unsupported ROMs safely run vanilla; patch verification failures fail closed; reset/load lifecycle is tested.

### Phase 4 — HGSS mechanics
Implement **independently** in testable increments: (a) EXP Share, (b) hard/soft caps and resolver, (c) instant text, (d) field HM logic. Use feature branches/commits and compatibility matrices for each.

**Gate:** verified revision tests and manual HGSS test procedures; no save corruption, repeated awards, event skipping, progression bypass, or invisible EXP overflow.

### Phase 5 — Nuzlocke and dashboard
Implement run/encounter/death tracking first, then save-state integration, then optional enforcement/graveyard transfer, then UI. Test duplicate handling, full PC, blackout, save rollback, and corrupt/missing paired snapshot.

**Gate:** tracker and enforcement can be independently disabled; safe recovery tested; no unreviewed overwrite of Pokémon data.

### Phase 6 — Additional DS games
Extend to Platinum, then Gen 5 titles. For each, deliver distinct playback, state-reader, companion and modifier compatibility claims. Require new ROM-specific fixtures and verified adapter tables.

**Gate:** no false "Gen 5 supported" claim when only melonDS playback exists.

## 5. Tests, evidence and manual verification

- Unit/property tests: mapping resolution, per-game preference serialization, conflict handling, EXP formulas, hard-cap crossing, 10% soft-cap rounding, progression choices, encounter identity, Dupes Clause, death transitions, run reset, snapshot version migration.
- Native boundary tests: JNI/core button state, patch signature checks, idempotent hook lifecycle, safe memory reads, reset/load/suspend behavior.
- UI tests: controller focus, touch routing, display swap, compact lower screens, no invisible interaction, original visual theme.
- Save integrity: original `.sav` untouched by default, atomic companion data updates, backup verification, missing snapshot recovery, compatibility with standard game saves, full PC and graveyard edge cases.
- Gameplay manual tests (where user-provided legal ROMs exist): HG and SS separately, representative battles/EXP/evolution, each HM and story gate, nonlinear Gyms, instant dialogue plus scripted events, Nuzlocke, savestates and postgame.
- CI must rely on synthetic assets, mock game states and legally usable fixture data, not committed copyrighted ROMs.
- Every PR: build logs, tests actually executed, screenshots where appropriate, ROM revision matrix, device coverage, known issues, and reviewer notes. State plainly when tests are blocked by missing Thor device/ROM.

## 6. Git / PR workflow

1. Record clean upstream baseline and fork remote; use a distinct integration branch (e.g. `feature/pokedaisy-ds-enhancements`) and phase branches such as `feature/ds-input-remap`, `feature/ds-companion-navigation`, `feature/hgss-mod-framework`.
2. Keep commits scoped and descriptive; avoid committing binary builds, ROMs, private keys, savefiles or generated secrets.
3. Open or prepare PRs targeting **my fork's** integration branch, not upstream by default. If GitHub tools or permissions aren't available, keep local commits and provide exact next steps; never claim a PR was opened when it wasn't.
4. Maintain `docs/fork/IMPLEMENTATION_STATUS.md` with phase state: completed, partial, blocked, unverified. Update checklists and decision log on every phase.
5. When a phase's tests fail, fix regressions before progressing into dependent phases. Independent work may continue in separate branches, but do not mark a failed phase done.
6. When the full request cannot be completed in one session, leave the repository in a passing, documented state with concrete continuation steps and unambiguous remaining tasks; do not leave half-applied, untested dangerous hooks active.

## 7. Explicitly unresolved decisions — do not silently invent product policy

- Whether Dupes Clause matches exact species only or entire evolutionary families.
- Rules for gift, static, legendary, Safari Zone and scripted encounters; especially what consumes an area's single encounter.
- Elite Four, Champion, Red and postgame level-cap policies after the relevant Gyms.
- Behavior when an active Nuzlocke run's enforcement setting is disabled and re-enabled.
- EXP Share interaction with Gen 4's held Exp. Share and unusual multi-target scenarios; derive a documented formula for sign-off.
- Whether HM access is based on possessing the physical HM item in current inventory or once-obtained unlock history (the requirement is *HM item plus progression*; the persistence nuance needs validation).
- A reliable definition of "accessible" Gym under HGSS story branches, and how to present unresolved ambiguity.
- Rewind support and how to safely synchronize challenge rollback if native per-frame snapshots are impractical.

When implementation reaches one of these and no safe neutral default exists, record the question and implement other independent work. Propose recommended policies with consequences, rather than inventing rules.

## 8. Expected first response from Codex

Begin by:

1. Summarizing actual repo architecture **with file paths and evidence** (especially DS vs GBA input routing and melonDS core APIs).
2. Reporting clean build status, test status, fork/remote permissions, and any blockers.
3. Indicating whether an **authorized Claude integration** is available, with its exact supported usage but **no credentials**.
4. Creating a phase checklist and then **starting Phase 1's smallest safe implementation slice**, once Phase 0 passes.
5. Reporting every implemented change with commit SHA, tests, evidence, and next actions. Do not replace implementation with vague proposals.

**Priority order:** DS input reliability → companion lower-screen controls → safe enhancement framework → verified HGSS gameplay modifiers → Nuzlocke → Platinum/Gen 5.
