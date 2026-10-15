# Durable draft dialog UI tests — attempt01

## Latest continuation delta

Initial source was committed as `c2975951739a9abc7d858d7ff3948f93c0e4c9ed`, independently source-ACKed, then compiled/executed in root's combined attempt2 with one test assertion failure. Root-authorized test-only correction preserves the actual automatic flush/acknowledgement barrier and strengthens exact request checks; corrected source independently ACKed, repaired-source runtime **not_run**. Root must integrate the correction and rerun its serialized assembled host gate. No production, instrumentation, Room/AppGraph, SQLite or native acceptance is claimed.

## Isolation and ownership

Verified before edits: cwd/top `/workspace/command-library-draft-dialog-ui-tests-01`; branch `agent/campaign-draft-dialog-ui-tests-20261015`; exact base/parent `558a02831576c24ecf722ee4d6d0956f9adf57e9`; common Git dir `/workspace/Command-Library/.git`; initially clean. Only Git environment variable `GIT_PAGER=cat`; no Git directory/index/worktree overrides; `core.bare=false`, no `core.worktree` or include configuration. Root dispatch and canonical `coordination/tasks/cx-campaign-draft-dialog-ui-tests-01.md` authorize exactly two NEW paths:

- `android-app/feature/shell/src/test/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/DeckDraftDialogUiTest.kt`
- `coordination/handoffs/cx-campaign-draft-dialog-ui-tests-01.md`

All other paths remained read-only. Existing worktrees/commits preserved. No children, builds, compiler, lint, network, push, auth changes or shared process actions.

## Source and behavioral scope

Five authored host Compose tests use real `DurableDeckEditorDialog`, `DraftExitDialog` and `RetainedDeckDraftEditor`, with the existing same-module `ActorFixture` faithful stateful test port. `createComposeRule` v2 and AndroidJUnit4 follow existing host/shared UI patterns. Each test owns a `TestScope`; callbacks enqueue real actor work, the test pumps its scheduler explicitly, and cleanup cancels/drains only that scope. No Main or clock monkeypatch.

1. Body-null failed read: disabled explicit Save, visible/enabled distinct Retry click, same session and exact saved invalid raw recovered, no writes/prepares/commits/discards/IDs.
2. Failed-read exit: actual Close UI click, exit Retry click, same typed close action/ticket/session held through the controlled real port commit gate, actual actor COMMITTING, no premature automatic release, exact one prepare/commit bound to original saved raw/token/scope/intent/hash and unchanged full head/saved raw, no raw write/discard; release exactly once after acknowledgement. The retained exit display phase is not used to infer mutation completion. This does not assert that the separate explicit Continue browsing choice is unavailable.
3. Recovered PREPARED Details: distinct Retry is read-only; explicit Save click commits original operation/hash/source token once, no new prepare/write/ID, exact requested content and revision, one notification.
4. Missing historical source: controlled fixture head revision advances past pinned raw; Retry exposes no historical base, disabled Save click cannot mutate raw/intent/head or invent IDs.
5. Recovered PREPARED ADD without fresh current-card proof: Retry then explicit Save preserves raw selection, exact printing-absence reason, reserved entry ID inside immutable intent and full slot; refuses with STALE_GENERATION and no mutations/IDs.

Persisted-state seeding and commit-response gate are explicitly labeled fixture setup. Tested admissions use Compose controls. This bounded return does not add the optional search-picker proof-refresh workflow; it deliberately passes no SearchController and verifies the safe missing-proof branch. Root owns picker production and assembled integration coverage.

## Verification and review

- Worker runtime/build/compiler/lint: **not_run**, per packet; no worker build slot granted. Root executed the initial source in its combined gate as recorded below; repaired-source runtime remains **not_run**.
- Static source readback checks actor API, exact fixture visibility/types, source-token/generation recovery paths, resource/tag contracts, existing Compose host patterns and root-owned frozen `canConfirm` conflict/uncertain admission. No deliberate red run against base UI, which lacks new Retry controls.
- Corrected test SHA-256: `e0f8b24b27dbd0b83550854b790a6b10d9966e0ba63e598bfcef050767e84e1e`. Original reviewed/executed pin remains `ced16a8f45d085b15f138924a3f029f196be86562b5930be4088758f61528899` in commit `c2975951739a9abc7d858d7ff3948f93c0e4c9ed`.
- Evidence directory: `/tmp/cl-draft-dialog-ui-tests-01/evidence/`; own temp `/tmp/cl-draft-dialog-ui-tests-01/tmp/`.
- Initial independent source review: `campaign_engineering_review` ACKed original test pin `ced16a8f...` and pre-ACK handoff hash `0c1a4e9cb5df0076517f22c9fae78a5721224166b0130b1ca262f529c26ea639`. Root's later runtime failure supersedes the original source-only readiness claim.
- Corrected independent source review: `campaign_engineering_review` read back/ACKed exact corrected test pin `e0f8b24b...` and pre-ACK handoff `67f6327bb9b4db19afad7057eb7dffaedf570c3c934883688b7a5f3b30df2373`; independently confirmed initial XML's sole display-phase mismatch. Reviewer confirms automatic-release barrier and stronger source/intent/head checks, with the explicit Continue qualification above. Final handoff changes review status/wording only; corrected test pin unchanged. No corrected runtime review claim.

## Actual failure lineage and correction

Root combined attempt2 compiled production and tests, then exited1: shell154 tests/13XML, one failure/zero errors/skips, 2m35s. Its `DeckDraftDialogUiTest` XML records five executed methods, four passed, with `exitRetryKeepsItsTicketAndActionUntilRecoveredRawHasActuallyFlushed` failing original line108: expected FLUSHING, actual NEEDS_CHOICE. Root log `/tmp/cl-draft-app-integration-01/evidence/attempt2/gradle.log`, SHA-256 `379feed273dc830322927003829dc2cebd5fe64f4a9714fd8e0d9467b00fdddd`. Root app tests and instrumentation compilation were not run after shell failure. Preserve that attempt unchanged; it is not a passing result.

Read-only diagnosis: valid Rename is TEXT_AUTOSAVE eligible in the actual fixture `referencePlan` and the real planner, so changing to description text would not fix the cause. `requestExit` after a failed initial read records NEEDS_CHOICE through `finishExit`; recovery `adoptOpen` retains that exit object, and `afterRead` starts `kick`. The drain updates exit phase in its `finally`, which has not run while the faithful commit port is suspended at `commit.before`. Existing actor recovery tests request exit while a read is still active, starting from FLUSHING; that different fixture topology explains their display-phase assertion.

The initial test overconstrained this display phase. Root authorized the correction after reviewing the source evidence. It now asserts actual actor COMMITTING and exact pending request/immutable intent bindings plus original full head/saved raw, while retaining same ticket/action/session, null `takeReadyExit` before acknowledgement, single prepare/commit, and exactly-once automatic READY/release only after completing the gate. No production behavior, fixture port, or original automatic flush-held-until-acknowledgement invariant changed. No new test/build was run by this worker.

## Recovery / next action

Commit only the two owned paths as a new repair commit on retained `c297595...`; no reset/history rewrite. Root cherry-picks the repair, combines its repaired dialog and clean-head producer, and reruns its explicitly serialized host shell test gate including `DeckDraftDialogUiTest`. Diagnose any assembled failure before repair; do not treat source ACK as runtime success. Root retains production/UI/DI/navigation/contracts/global coordination and final integration authority.
