# Clean hidden draft owner head refresh

## Isolation

Before edits verified pwd/top `/workspace/command-library-draft-head-refresh-01`, branch `agent/campaign-draft-head-refresh-20261015`, exact base/HEAD `558a02831576c24ecf722ee4d6d0956f9adf57e9`, clean status, common Git dir `/workspace/Command-Library/.git`, GIT_PAGER=cat only, core.bare=false and no worktree/hooks/include override. Recorded this handoff before authoring. Read canonical root packet/active lock (root14edabc), local AGENTS/CONTINUE, accepted actor/interface and actual library/ShellControllers head notifications. Root explicitly subdivided these three paths; no inherited coordination copy treated as authority.

Exact allowed paths: existing `android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/RetainedDeckDraftEditor.kt`; existing corresponding test `RetainedDeckDraftEditorRecoveryTest.kt`; this NEW `coordination/handoffs/cx-campaign-draft-head-refresh-01.md`. Every other path remains unchanged. Original actor worktree/commit d2df02b retained untouched. No API/contract/schema/dependency change, new production adapter, children, network, push, destructive Git, process termination, or lock changes.

## Change

A newer same-deck head notice now retires an owner only when hidden, IDLE, based on the exact current snapshot with canonical saved body (including a proven removed/missing-target null body), and without any durable raw/Pending intent, dirty/conflicted/uncertain/error/explicit-invalid state, exit, structural lock, captured/frozen/submitted/rejected/known-ACK/discard/recovery authority, pending TEXT candidate, deadline/flush, or active read/drain/timer. Existing active/unsafe owners retain the original conflict behavior, body, session and exact requests. A body-null check alone never proves safety.

Retirement does not manufacture a snapshot or start IO. It closes the demonstrably clean owner, removing its library mutation barrier, and retains one bounded deck/highest-notified-revision pair. Further notices for that retired deck raise but never lower the fence. The next actual same-deck open uses that fence; a stale returned head exposes conflict/no base, rather than claiming the notified revision was loaded. Same-deck new-target loads also retain the current notification fence. Switching to a different deck neither applies nor clears the retained qualified fence; only actual same-deck read admission at or above its highest known fence clears it. The existing read/ACK/visibility/exit/deadline guards are unchanged.

Ten new bounded recovery regression methods prepare coverage for actual clean Details reload; highest notice versus stale return; canonical ENTRY and post-remove proven null body after restore; active/dirty preservation; hidden invalid raw/staged ENTRY/PREPARED preservation; hidden uncertain exact write and suspended exact retry; pending exit/read-failure owners; newer notification during actual successor load; qualified A-retirement/B-open/A-stale-read fence retention; and a canceled same-deck reload followed by B/A with a newer matching notice. All pre-existing assertions remain unchanged. Tests reuse the existing stateful fixture; no other test/helper file changed.

## Verification and continuation

Worker Gradle/test/compile verification: **not_run**. Root explicitly retained the sole active build slot and directed source-reviewed three-path return for the later combined assembled gate. No new runtime count or compilation success is claimed. The earlier 94 passing actor tests belong exclusively to the old e053d207 producer, not this changed source. Root must compile and execute the new cases with its actual UI/Room/AppGraph journey after integration; native/device/Fold7/APK acceptance remains separate.

Source-only checks actually run: `git diff --check` passed; exact two-file source copies, before/after SHA256 manifest and patch frozen in `/tmp/cl-draft-head-refresh-01/evidence/attempt03/{source.json,diff.patch,source/}`. No worker build/failure log or XML exists because no gate was launched. Final return records exact commit/parent/clean status and three-path hashes externally in `/tmp/cl-draft-head-refresh-01/evidence/final-return.json`.

| Source | Base SHA256 | Frozen SHA256 |
| --- | --- | --- |
| RetainedDeckDraftEditor.kt | e053d207acee577db42ccfd51407494378239260bdd5926e6324f42210f02db5 | d7c9249e999bad629ed9bf0adcac598f9cc7ff8f23d350ae533bd039efbe5479 |
| RetainedDeckDraftEditorRecoveryTest.kt | fa267b3c91fcf3251a2983faa24e3e7f5968175ee70071b1056dab72ea695647 | ba07726e9e6aa74e235ad71a7e67bf3169fff0e82d66b3ceb8e0bc8dba66a70f |

Prebuild source-only review HOLD in attempt01: opening an unrelated deck cleared the retired qualified fence. Attempt02 retains it across unrelated opens, consumes it only for the same deck and adds the A→B→A regression; attempt01 source snapshots remain retrievable. Further self-review found same-deck load cancellation could consume the pair before actual read admission. Attempt03 retains and raises the pair through suspended/canceled same-deck reads and clears only after actual read admission at/above the fence; adds deterministic gated canceled-A/B/A coverage. Attempt02 independently source-ACKed snapshots remain held/retrievable. No build/runtime failure implied.

Latest continuation delta: final actual independent Astra attempt03 SOURCE ACK received, both full pins verified. Reviewer confirmed conservative authority-preserving retirement and qualified fence retention through unrelated/canceled reads, with actual-read-only fence admission. Ten authored regressions remain not_run. Commit exactly these three paths and return clean isolated branch; root integrates and executes the combined assembled gate, then releases canonical claim. No runtime acceptance inferred.
