# Durable draft dialog UI tests — attempt01

## Latest continuation delta

Source authored and independently source-ACKed; runtime **not_run**. Root must assemble this test-only return with its production dialog repairs and the disjoint clean-head actor return, then run the serialized host Compose gate. No production, instrumentation, Room/AppGraph, SQLite or native acceptance is claimed.

## Isolation and ownership

Verified before edits: cwd/top `/workspace/command-library-draft-dialog-ui-tests-01`; branch `agent/campaign-draft-dialog-ui-tests-20261015`; exact base/parent `558a02831576c24ecf722ee4d6d0956f9adf57e9`; common Git dir `/workspace/Command-Library/.git`; initially clean. Only Git environment variable `GIT_PAGER=cat`; no Git directory/index/worktree overrides; `core.bare=false`, no `core.worktree` or include configuration. Root dispatch and canonical `coordination/tasks/cx-campaign-draft-dialog-ui-tests-01.md` authorize exactly two NEW paths:

- `android-app/feature/shell/src/test/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/DeckDraftDialogUiTest.kt`
- `coordination/handoffs/cx-campaign-draft-dialog-ui-tests-01.md`

All other paths remained read-only. Existing worktrees/commits preserved. No children, builds, compiler, lint, network, push, auth changes or shared process actions.

## Source and behavioral scope

Five authored host Compose tests use real `DurableDeckEditorDialog`, `DraftExitDialog` and `RetainedDeckDraftEditor`, with the existing same-module `ActorFixture` faithful stateful test port. `createComposeRule` v2 and AndroidJUnit4 follow existing host/shared UI patterns. Each test owns a `TestScope`; callbacks enqueue real actor work, the test pumps its scheduler explicitly, and cleanup cancels/drains only that scope. No Main or clock monkeypatch.

1. Body-null failed read: disabled explicit Save, visible/enabled distinct Retry click, same session and exact saved invalid raw recovered, no writes/prepares/commits/discards/IDs.
2. Failed-read exit: actual Close UI click, exit Retry click, same typed close action/ticket/session held through the controlled real port commit gate, no premature release, exact one prepare/commit, no raw write/discard; release exactly once after acknowledgement.
3. Recovered PREPARED Details: distinct Retry is read-only; explicit Save click commits original operation/hash/source token once, no new prepare/write/ID, exact requested content and revision, one notification.
4. Missing historical source: controlled fixture head revision advances past pinned raw; Retry exposes no historical base, disabled Save click cannot mutate raw/intent/head or invent IDs.
5. Recovered PREPARED ADD without fresh current-card proof: Retry then explicit Save preserves raw selection, exact printing-absence reason, reserved entry ID inside immutable intent and full slot; refuses with STALE_GENERATION and no mutations/IDs.

Persisted-state seeding and commit-response gate are explicitly labeled fixture setup. Tested admissions use Compose controls. This bounded return does not add the optional search-picker proof-refresh workflow; it deliberately passes no SearchController and verifies the safe missing-proof branch. Root owns picker production and assembled integration coverage.

## Verification and review

- Runtime/build/compiler/lint: **not_run**, per packet; no worker build slot granted. Five methods are authored, not an executed test count.
- Static source readback checks actor API, exact fixture visibility/types, source-token/generation recovery paths, resource/tag contracts, existing Compose host patterns and root-owned frozen `canConfirm` conflict/uncertain admission. No deliberate red run against base UI, which lacks new Retry controls.
- Test SHA-256: `ced16a8f45d085b15f138924a3f029f196be86562b5930be4088758f61528899`.
- Evidence directory: `/tmp/cl-draft-dialog-ui-tests-01/evidence/`; own temp `/tmp/cl-draft-dialog-ui-tests-01/tmp/`.
- Independent actual source review: `campaign_engineering_review` ACKed the exact test hash above and pre-ACK handoff hash `0c1a4e9cb5df0076517f22c9fae78a5721224166b0130b1ca262f529c26ea639`. Reviewer confirmed all five real dialog/actor workflows, scheduler/admission checks, explicit fixture setup and scoped nonclaims; no source blocker. This final handoff changes review status only. No runtime review claim.

## Recovery / next action

Commit only the two owned paths. Root cherry-picks the returned exact commit, combines its repaired dialog and clean-head producer, and runs its explicitly serialized host shell test gate including `DeckDraftDialogUiTest`. Diagnose any assembled failure before repair; do not treat source ACK as runtime success. Root retains production/UI/DI/navigation/contracts/global coordination and final integration authority.
