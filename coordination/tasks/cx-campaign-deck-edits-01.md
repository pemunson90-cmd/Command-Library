# cx-campaign-deck-edits-01 — attempt1, packet r1

Status: ready after engineering review and root shared-baseline compile. Owner: assigned Sol implementer; integration: recovery root. Requirements B01/B02/B05/B10 and exact parity joins in registry `execution.tasks[deck-edits]`. This is a pure producer, not deck repository/version/UI acceptance.

Base: product `43f258295cfb946983d1f35cab80fedf4f105978`; dispatch supplies exact descendant containing reviewed r1 packet and root module config. Branch `agent/campaign-deck-edits-20261015`, worktree `/workspace/command-library-deck-edits-01`. Verify pwd, repository top, branch, HEAD and `git rev-parse --git-common-dir`; inspect only Git directory/worktree/index environment names, never dump credentials. Stop on baseline/path mismatch and report to root.

Read: AGENTS/CONTINUE; SPEC B01/B02/B05/B10; DATA_MODEL deck/version/package integrity; `DeckCollectionContracts.kt`, `Values.kt`, `PricingValidationContracts.kt` CardChange; `core/common/ContractSemantics.kt`; this packet. Root retains all shared contracts/config/schema/DI/navigation/coordination. No other writer owns these new files.

Allowed writes only: `android-app/feature/decks/src/main/kotlin/com/architectpsg/commandlibrary/feature/decks/DeckPatchReducer.kt`, `DeckEntryDiff.kt`, corresponding `src/test/kotlin/.../DeckPatchReducerTest.kt`, `DeckEntryDiffTest.kt`, and `coordination/handoffs/cx-campaign-deck-edits-01.md`. Root owns `build.gradle.kts`; request shared changes rather than editing them.

Consume existing DeckSnapshot/DeckPatch/Revision/UtcTimestamp/CardChange/Outcome. Produce public module API `DeckPatchReducer.propose(deck: DeckSnapshot, patch: DeckPatch, expected: Revision, modifiedAt: UtcTimestamp): Outcome<DeckSnapshot>` (object) and `DeckEntryDiff.compare(before: DeckSnapshot, after: DeckSnapshot): Outcome<SnapshotList<CardChange>>` (object). No replacement domain models.

Proposal is immutable and **not durable**: for successful nonempty edits project revision+1 once and supplied modifiedAt, preserve createdAt/identity/order/untouched values. Empty patch preserves exact snapshot after validation/stale check. Timestamp cannot precede existing modifiedAt or createdAt; no clock or I/O. Revision exhaustion is typed failure using existing helper. Root repository later commits candidate conditionally in one transaction; no success-before-persistence claim.

Support Rename/Description/Format/Metadata/Add/Replace/Remove sequentially, all-or-nothing. Add appends; Replace keeps position; Remove removes only exact ID. Reject duplicate base entry IDs, Add collision/missing Replace/Remove, duplicate label IDs within an entry, role-override entryId that conflicts with its owning entry. Preserve foreign raw/source-qualified identity and all metadata/labels; never coalesce by name/Oracle/printing. Bound input to10000 entries/1024 edits; text follows existing DTO contract (do not invent destructive truncation). Empty names invalid, retain nonblank original spelling. Reject package-bearing snapshots with UNSUPPORTED until explicit contribution-safe policy exists; RestoreVersion/ApplyPackage rejected UNSUPPORTED anywhere in a patch before applying results. This is an explicit capability boundary, not implementation of those retained features.

Entry diff validates same deck identity and unique entry IDs. Compare by entry ID, preserving before-order for removals/changes and after-order for additions. Every entry field participates (quantity/board/exact identity/finish/notes/labels/roleOverrides); label-only edits don't duplicate demand. CardChange before/after are exact inputs, reasons nonblank. Metadata-only deck changes yield no entry changes; explicitly not complete deck-version diff or reorder detection. Full version/restore/undo/package tasks remain registry successors.

Implement and batch meaningful regression fixtures: late invalid edit leaves original exact; stale/overflow/timestamp; printing-only, board-only, label-only and unknown-vs-known identity diff; no duplicate demand; source-qualified foreign identities; unsupported operations reject entire batch; package lineage stays protected. Use existing immutable snapshots, no broad framework.

Check: `JAVA_HOME=/workspace/.command-library-env/jdk-17.0.16+8 ANDROID_HOME=/workspace/.command-library-env/android-sdk GRADLE_USER_HOME=/workspace/.command-library-env/gradle bash android-app/gradlew -p android-app --offline --console=plain :feature:decks:test`. **Request root build-slot grant before running Gradle**; one job across worktrees. Task-local `TMPDIR=/tmp/cl-deck-edits-01`, logs/XML copies `/tmp/cl-deck-edits-01/evidence`; never edit source while your build runs. No full suite/emulator/network. Known baseline: frozen native9c1573b FAILED; unrelated, do not rerun or repair it. No device evidence.

Return committed code with exact base/head, diff paths, actual command/environment/XML counts/results, retained evidence path, self-review and missing acceptance in allowed handoff. No pushes/main merges/reset/clean/stash/force-push/killing others/removing locks. If interrupted preserve dirty files and report exact next action. Root reviews exact diff and integrates; no unsupported completion summary.

## Latest continuation delta

Packet prepared; implementation not_run. Await root-published reviewed shared baseline and explicit assignment.
