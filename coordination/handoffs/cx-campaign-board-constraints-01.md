# Board-size constraint producer — attempt01 source freeze

Task: `cx-campaign-board-constraints-01`; owner: `/root/implement_board_constraints`.
Canonical root packet/lock read before implementation. Worktree `/workspace/command-library-board-constraints-01`, branch `agent/campaign-board-constraints-20261015`, base `1965c54dfca511d4a371270d806281e60ed9682a`.

## Latest continuation delta

Implemented the frozen `BoardConstraintFinding`, `BoardConstraintAssessment` and `LocalBoardConstraintChecks.assess` API in the assigned new production source; added its assigned new test source. The third new path is this handoff. No other paths changed. No commit created; HEAD remains the assigned base pending independent SOURCE review and root authorization.

The helper rejects empty/duplicate/over-seven constraints, entry counts above `DeckPayloadLimits.MAX_ENTRIES`, malformed/blank/over-budget deck source/raw and entry IDs, duplicate entry IDs and nonpositive quantities on all roles, including unconstrained entries. IDs receive the cheap UTF-16 length ceiling before strict UTF-8 sizing and before hashing; source-qualified IDs remain exact. Selected roles alone receive checked `Long` sums. Findings preserve supplied constraints and exact deck ID/revision, ordered by `BoardRole`. Labels, role overrides, card/printing metadata and package contributions do not add copies. `countsTowardDeckSize` adds no aggregate rule. No registry, format constants, whole-legality result, persistence change, structural validator copy or `feature:decks` dependency.

Tests use explicitly synthetic minimal snapshots based on the existing validator fixture shape. Cases cover independent literal counts for all seven roles, input-order independence, bounds/empty boards, metadata/package conservation and unchanged snapshots, 10,000 `Int.MAX_VALUE` quantities with literal `21_474_836_470_000L`, oversized entries, invalid constraints, duplicate/unconstrained IDs, Unicode and UTF-8 boundaries, exact foreign IDs, constructor-bypassed corrupt quantities/blank IDs, and original cancellation propagation before/during/at final polling. Reflection is test-only to admit corrupt values that core constructors prohibit.

Verification: source self-review completed; source/handoff runtime `not_run`. No builds, compilers, lint, test execution, network, child agents, shared-path edits or git mutations run. Root owns the absent module registration, whole-graph admission, retained controller/UI and single coherent focused integration gate. Source checks are admission/work ceilings, with no measured timing guarantee.

Next action: root obtains independent SOURCE review of the frozen exact three files/hashes returned in the worker message. Worker awaits explicit exact-three-path commit authorization, then returns the verified narrow commit. Any shared/API adjustment remains a proposal to root.
