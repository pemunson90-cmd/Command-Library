# Ordinary life UI — SOURCE01 M1 + NEW5 frozen return

## Latest continuation delta

- Worktree `/workspace/command-library-ordinary-life-ui-01`; branch `agent/campaign-ordinary-life-ui-20261016`.
- Unchanged base HEAD `24dfc88155cafaeb79613859835c2638e9d229ca`, the independently reviewed real service binding.
- Changed exactly the six locked paths below: existing LifeTable plus five NEW files. No navigation, ShellControllers, AppGraph, ShellPorts, shared strings, schema, service, engine or other worker edits.
- Verification: source inspection and tracked `git diff --check` only. Compiler/build/tests/network **NOT_RUN** as delegated. No children, commits or push; root build slot unused.
- Next action: independent source review, root exact-byte integration and real AppGraph/Room ordinary journey plus relevant existing service regressions in the assembled root gate. Native/TalkBack/Fold7/signing and broader G01–G14 acceptance remain open.

## Root integration API

Construct internal `OrdinaryLifeController(scope, service: PersistedGameService, dispatcher, nextId = UUID)` in the root retained ViewModel, with UI-dispatcher-owned scope/entry points and the actual bound ordinary service. Route internal `OrdinaryLifeScreen(controller, widthClass, modifier = Modifier)` when the real port exists. The screen performs read-only opening/history access, owns composition leave cleanup, and on reentry reloads the current game without replaying commands. No adapter or substitute service was introduced.

Useful proof selectors: `ordinary-life-new`, `ordinary-life-seat-count-N`, `ordinary-life-initial`, `ordinary-life-player-I`, `ordinary-life-commanders-I-N`, `ordinary-life-commander-I-S`, `ordinary-life-create`, `ordinary-life-step-1/5`, `ordinary-life-deduct`, `ordinary-life-finish`, `ordinary-life-result-reason`, `ordinary-life-result-confirm`, `ordinary-life-reopen`, `ordinary-life-saved`, `ordinary-life-open-GAME_RAW`, `ordinary-life-reload`, `ordinary-life-retry-create`. Table +/- and Undo/Log retain the existing label/content-description semantics; step5 descriptions also state the amount.

Controller exposes `state: StateFlow<OrdinaryLifeState>`; `newSetup`, exact shown-setup edit methods, `create(shown, labels)`, `retryCreation`, `saved/refreshHistory/moreHistory/select`, `reload`, `action(frame, action)`, `undo(frame)`, `finish(frame,winnerIds,draw,reason)`, `openEvents(frame)/moreEvents/table`, and `open/leave`. Screen state/form callbacks capture immutable rendered frames; game command callbacks capture the actual GameSnapshot object and never substitute the latest frame.

## Behavior and boundaries

Setup supports1–6 explicit names, visible editable40 default/20 shortcut/custom Int, and0–2 manually labeled commander slots per seat. Fresh distinct operation/definition/seat/slot IDs are allocated only by explicit create. Actual GameCreation has exactly one LIFE role, damage/tax roles, POISON/ENERGY/EXPERIENCE OTHER presets, empty variants, NONE countdown and disabled elapsed timers. Manual commanders have absent Oracle/printing identities and undefined source language, not fabricated card facts. Initial linked profiles/decks are explicitly absent; existing resumed snapshots and their linked metadata remain untouched. Linked selectors remain named following work.

One in-flight port call is fenced synchronously before launch. All IO/flow production uses the supplied dispatcher. Create uncertainty preserves the exact GameCreation object/op ID and raw setup; only explicit exact creation retry or saved-history inspection is offered. No action/undo is automatically retried. Nonacknowledged mutations block until explicit read reconciliation; unresolved creation also blocks table mutations. Cancellation propagates, leaves uncertainty, and never proves rollback. Existing callPort policy turns unrequested leaked port cancellation into static UNEXPECTED.

Command/undo uses the captured game ID and revision and validates acknowledgment identity/next revision. Typed observe may advance concurrently; older acknowledgments cannot replace a newer observed frame. Contradictory same-revision reads cannot clear the fence. Typed observation failures remain explicit; no legacy watch/exception bridge is used. Leaving composition cancels the observer without cancelling a potentially committed command; returning only reloads. No polling, ticker or autonomous background work.

Commander damage and optional opposite life change form one flat GameAction.Group and one undoable command. Raw adjustments remain literal when deduction is off. Commander tax explicitly counts own-slot casts (tax2×casts), matching the existing engine policy. Table mode can show initially implicit own-commander damage at the initial value for stolen commanders; it writes nothing to display and inherits hidden sibling policy. Existing LifeTable callers keep default behavior/visual structure through compatible optional parameters. Step/bound arithmetic uses Long before permitting counter changes.

Explicit finish confirms complete WIN/LOSS or all-DRAW outcomes plus user reason. Completed table controls are read-only until explicit reasoned Reopen. Saved-game and event lists page only on user request. Closing returns to saved games and preserves service-committed games. Product text uses the owned `ordinary_life_*` resources; UI uses shared panels/actions,48dp targets, adaptive table columns, scrollable setup/results/events and shared localized UTC formatting without introducing API26-only calls.

## Authored focused oracles (19; all NOT_RUN)

Synthetic persisted-port tests cover no automatic mutation;1–6 seats/custom life/unique fresh IDs/empty variants/NONE timing/presets; invalid raw setup/Unicode/duplicate identity bounds; double creation/inflight/stale setup; double-click/stale/future game frame; exact flat damage/life group and conditional undo; literal damage and own-slot tax; unknown/stale acknowledgment fencing and explicit read/no replay; exact frozen creation after lost ACK; leaked and caller cancellation; newer observer versus older ACK; typed observation failure without legacy watch; explicit winner/loss/draw/read-only/reopen; saved/events pagination and close preservation; observer cleanup/read-only reentry; failed and contradictory reload retaining the fence.

The fake models CAS, immutable historical creation receipts, counter/group atomicity, conditional undo, typed observation, outcomes, and paging for these controller oracles. It is not substituted into product or evidence of SQLite/engine/app execution.

## Frozen SHA-256

- `android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/OrdinaryLifeController.kt`: `52bf8142ebbb72c818288ef8c5336a6a21d6f9ee686ed68ef173ca70f8404d12`
- `android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/ui/life/OrdinaryLifeScreen.kt`: `35b99fc434c972677f5fc8504023ff8e469af9cc82f9ca5da4ae94d09b95204e`
- `android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/ui/life/LifeTable.kt`: `2ca2f0ba5d25935e234b7ee719ed480333467bf7816df31dafcacea717806952`
- `android-app/feature/shell/src/main/res/values/life_game_strings.xml`: `39aa697f4ea2208e7f97b6decd8e5c1349c9ed6e57464050c784f4387978a3dc`
- `android-app/feature/shell/src/test/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/OrdinaryLifeControllerTest.kt`: `769783d884c844f2d218548462dc7392971a4ac68a4bf956a475ccf09dd5aac9`

Handoff's sixth hash is returned externally to avoid a recursive manifest.
