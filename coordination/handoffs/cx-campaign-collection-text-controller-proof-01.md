# Collection text export controller fault proof — SOURCE01

## Latest continuation delta

Authored only the new focused `CollectionTextExportControllerTest.kt` and this handoff in `/workspace/command-library-collection-text-controller-proof-01`, branch `agent/campaign-collection-text-controller-proof-20261016`, exact unchanged HEAD `864e3801907f45ac76e4625be08c4337ea9dd2a4`. Canonical root task and post-baseline `campaign-collection-text-controller-proof.lock` authorize these NEW2 paths. Common Git directory is `/workspace/Command-Library/.git`; relevant GIT_DIR/GIT_WORK_TREE/GIT_INDEX_FILE/GIT_COMMON_DIR environment overrides were absent. Initial worktree was clean. All prior worker worktrees/drafts and existing tests remain untouched.

Compilation, tests, builds, lint, network, children, commits and push: **NOT_RUN**. ROOT owns the sole build slot. This is authored source for independent review, not passing runtime evidence. Source/API readback and untracked-file whitespace inspection were performed; `git diff --no-index --check /dev/null <new-test>` produced no diagnostics (its status1 denotes different file contents). No product change or identified product blocker is being proposed.

## Authored oracles and seams

The real controller is exercised with a faithful read-only LocalInventory double, a small explicitly synthetic renderer and typed suspend writer double. Inventory apply/undo count and fail if invoked. A real private-marker lot is retained, and its saved quantity/notes/location are checked after export preparation and writing. Synthetic renderer output checks establish controller flag forwarding and frozen capture only; production encoder privacy/grammar remains the separate encoder proof.

The test journeys cover:

- Default false/false privacy choices, separate notes opt-in, then explicit notes+location; hidden preparation refusal; no file call for open/prepare/request-picker; immutable reviewed source/artifact bytes even after current inventory revision changes; no reload/re-render at destination selection.
- Exact preview identity, distinct owner nonces, stale rendered frames and foreign/duplicate picker callbacks; hide revokes pending authority, reopening does not restore it; matching failure/null selection consumption and stale failure preservation.
- Incomplete snapshot refusal before rendering, empty/overbudget/tampered bytes, checksum mismatch/algorithm mismatch, unsafe filename/media type and excessive loss metadata refusal before preview/writer. Revision negativity cannot be represented through the real Revision constructor and is not fabricated in this proof.
- Typed read/render failures and uncertainty remain distinct. Cancelled noncooperative preparation cannot publish an old snapshot or clear its still-pending successor; successor privacy capture and original source conservation remain checked.
- Noncooperative write work and separately gated cleanup retain the owner through CANCELLING, block discard/preparation/second selection, and report PossiblyModified(UNKNOWN_RESULT) after cancellation even if the port would otherwise return Written. A later explicit fresh destination can succeed with the same frozen bytes.
- NotStarted(PERMISSION), PossiblyModified(PERSISTENCE) and Written remain distinct. Ordinary throw becomes PossiblyModified(UNEXPECTED); writer CancellationException becomes PossiblyModified(UNKNOWN_RESULT), after the writer's finally cleanup.
- A→B→A URI reuse is rejected even when A returned NotStarted; copied-preview discard and discard during pending picker cannot reset the export; exact discard allows a new preview with a fresh ticket and URI history. An already cancelled owning scope cannot strand write ownership or claim rollback.

No injected product adapters, UI/AppGraph/Room execution, actual file-provider behavior, provider atomicity, remote durability, full backup/import, device or release acceptance is claimed.

## Freeze and next action

Owned test:
`android-app/feature/shell/src/test/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/CollectionTextExportControllerTest.kt`

SHA256: `736c02790ff799af50da045d3408d4048c16551ab7bdc01a0d0aa3de8a4e4be2`.

Read-only baseline controller SHA256: `7a6bfcc37f9582c8cb5e1d16573bf14aca1a60fb378de42dddceb65a09f91512`. The final two-path manifest, including this handoff's hash, is returned to ROOT outside this document to avoid a self-referential hash.

Next: ROOT independent exact-source review; integrate only NEW2 after approval and run its assembled encoder/controller/actual-file-app gate. Keep this source frozen pending review. Report any discovered controller defect rather than weakening these authority/cleanup assertions.
