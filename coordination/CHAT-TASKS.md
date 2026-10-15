# Tasks for concurrent chats

Current assignment board,2026-10-15. Base your checkout on the latest published `agent/cx-thread-recovery-20261013`, not the historical native branch. This board controls these assignments; machine state/CONTINUE retain overall project authority.

| ID | Task | Status | Owner | Task card |
|---|---|---|---|---|
| campaign-deck-edits | Pure deck edit/entry-diff producer | DONE (scoped producer) | 71096d9 → root3623bcf/5925081;13focusedpass/sourceACK | [packet](tasks/cx-campaign-deck-edits-01.md) |
| campaign-links | Supplied/template CardLinks producer | DONE (scoped producer) | 45b3822 → rootdf1d323/86f692a;35focusedpass; final root integration pending | [packet](tasks/cx-campaign-links-01.md) |
| life-profiles | Real SQLite player profile persistence and model codec port | DONE | This recovery chat: codex-thread-recovery-20261013 | [details](tasks/cx-m2-life-profiles-01.md) |
| scryfall-integrate | Bring the completed reviewed metadata decoder and13 tests onto the recovery baseline; verify locally | DONE | codex-scryfall-integration-20261014 / ad3f9aa | [details](tasks/cx-k3-scryfall-integration-01.md) |
| life-history-audit | Map retained history/undo/result/alert requirements to current contracts and missing service checks | DONE | claude-code-life-history-audit-20261014 / component/life-history-audit-20261014 | [details](tasks/cx-m2-life-history-audit-01.md) |
| life-variant-source-audit | Inventory authentic variant rules/cards/placement/source gaps without code edits | OPEN | — | [details](tasks/cx-m2-life-variant-source-audit-01.md) |
| life-game-store | Checked game creation/read persistence foundation | DONE | This recovery chat: codex-thread-recovery-20261013 | [details](tasks/cx-m2-life-game-store-01.md) |
| life-snapshot-graph | Structural snapshot validation before mutation/service binding | DONE | This recovery chat: codex-thread-recovery-20261013 | [details](tasks/cx-m2-life-snapshot-graph-01.md) |
| life-event-reads | Checked immutable event/archive reads and bounded pages | DONE | This recovery chat: codex-thread-recovery-20261013 | [details](tasks/cx-m2-life-event-reads-01.md) |
| life-game-mutations | Transactional revision/sequence CAS and atomic persisted mutation adapter | DONE | This recovery chat: codex-thread-recovery-20261013 | [details](tasks/cx-m2-life-game-mutations-01.md) |
| life-timing-engine | Modern SHARED/elapsed/priority/recovery/undo timing calculations | DONE | This recovery chat: codex-thread-recovery-20261013 | [details](tasks/cx-m2-life-timing-engine-01.md) |
| life-action-engine | Modern semantic actions and actual initialization design/implementation | TAKEN | This recovery chat: codex-thread-recovery-20261013 | [details](tasks/cx-m2-life-action-engine-01.md) |
| frozen-integration-pr | Prepare isolated tested integration PR from exact freeze5a88846; root retains active development/final merge | DONE / Pat-approved guarded PR21 merge be663b2 | Root merged; live development stays on recovery | [decision](integration/FREEZE-5a88846-20261014.md) |
| life-game-service | Bind reviewed persisted service to real platform/source providers and operational UI | BLOCKED | Unbound facade/platform providers implemented; named variant/source/DI/UI prerequisites remain | cx-m2-life-functional-01 |

Native campaign workers are root-managed: root publishes their exact disjoint claims/completions; workers write only packet-listed code and own handoff. See [campaign](../docs/superpowers/plans/2026-10-15-dependency-aware-campaign.md).

## Claim and completion protocol

1. Fetch/read this board and current locks before choosing an OPEN task. Preserve dirty work; use a separate checkout/worktree and a `component/` branch so publishing does not automatically launch broad Android workflows.
2. Before implementation, change ONLY the chosen row to TAKEN with your unique session/branch, update its task card, and create its dedicated lock with exact owned paths. Publish that claim and tell Pat the branch/commit. A private/unpushed claim does not coordinate other environments. Do not take a second task at the same time.
3. Your worker may update its own board row, dedicated task/lock/handoff and explicitly owned deliverable paths. Do not change global lease/state/queue/CONTINUE, app/DI, shared contracts/schema/configuration or another task's paths. Recovery remains the sole orchestrator. A production defect outside scope is a finding for root, not permission to expand ownership.
4. Work locally; use the task's meaningful verification. Record actual commands/results and limitations. No routine emulator/CI dispatch. Do not copy inherited old global pointers from component branches.
5. When done, mark your row DONE on your published branch, update the task/handoff with exact commit/evidence/next step and release the dedicated lock. Root consumes scoped changes and reconciles the canonical board. If blocked, mark BLOCKED with the concrete reason. Refresh the replaceable Latest continuation delta; do not leave obsolete next actions mixed with live instructions.

No automatic takeover from lease expiry. If two branches claim the same task, stop overlapping writes and ask root to reconcile. Completed Scryfall boundary-test task0db3298 and integration ad3f9aa are DONE; history audit a47d1cc is DONE and consumed; variant-source audit remains OPEN.

| campaign-deck-codec | Complete bounded CLDK1 snapshot codec | DONE scoped producer | Integrated2ae5f55; Astra reviewed | [packet](tasks/cx-campaign-deck-codec-01.md) |
| campaign-deck-diff | Complete immutable document comparison | DONE scoped producer | Integrated125fb83; root reviewed | [packet](tasks/cx-campaign-deck-diff-01.md) |
