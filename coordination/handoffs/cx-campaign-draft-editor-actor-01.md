# Retained deck draft editor actor — scoped return

## Isolation and scope

Before authoring verified pwd/top `/workspace/command-library-draft-editor-actor-01`, branch `agent/campaign-draft-editor-actor-20261015`, exact base/HEAD `94e67b6cf75a261c84dc00658aecd3ac626d0f42`, common Git directory `/workspace/Command-Library/.git`, and clean status. No GIT_DIR/GIT_WORK_TREE/GIT_INDEX_FILE/etc overrides; only GIT_PAGER=cat, core.bare=false, no worktree/hooks/include override. Initial handoff recorded this before edits. HEAD remains the dispatch base before the single implementation commit; no dependency refresh or existing-file edits. Prior completed worktrees retained untouched.

Read the complete packet, accepted design and integration plan, actual internal DeckDraftEditorState/DeckDraftQueuedInput/PortCall/ShellPorts, public LocalDeckDrafts/LocalDeckLibrary contracts and existing library integration. Root subsequently persisted recovery/deadline/read-flush clarifications without changing this accepted ABI or worktree baseline. Exact four new delegated paths are RetainedDeckDraftEditor.kt, RetainedDeckDraftEditorTest.kt, RetainedDeckDraftEditorRecoveryTest.kt in the shell presentation source/test directories, and this handoff. Existing UI/state/helpers/library/ports/DI/navigation/schema/build/resources/coordination remain root-owned and unmodified. No children, network, push, destructive Git, lock removal, shared process termination, or resource changes.

## Result

Complete internal retained DeckDraftEditor with the accepted constructor: scope, LocalDeckLibrary, LocalDeckDrafts, DeckDraftPlanner, injected `(DeckSnapshot, DeckPatch, Revision, UtcTimestamp) -> Outcome<DeckSnapshot>` projection, dispatcher, ID supplier, monotonic clock, current-card predicate and post-reconciliation onCommitted callback. Projection receives the actual SOURCE/expected revision. No substitute public models, production fake backend, placeholder adapter, or binding.

The serial lane retains exact write/prepare/commit/discard arguments and uncertainty independently of phase. Explicit confirmation captures the body, edit sequence, selection and IDs before suspension; later input queues separately. ADD checks qualified oracle/name/language, absent-printing reason, no raw identity and exact current selection/provenance/active generation immediately before prepare and fresh commit. Only a live submitted uncertain commit retains exact retry authority. Recovered evidence never authorizes a fresh operation. Structural choices stay explicit. TEXT promotes independently valid eligible fields and retains invalid/staged residuals, including empty canonical acknowledgements.

First-dirty scheduling caps the wait at 500ms; it does not claim disk latency. Older raw acknowledgement and pinned own-later raw writes retain overdue dirty timing until the validated continuation becomes canonical. Timers may be replaced; submitted IO is never canceled. Full live ACK admission validates operation/request/source/effect and complete actual projection before releasing the original exact authority. All Prepare-to-Committed callers retain their call/rejected authority until admission succeeds. Malformed receipts cannot refresh a token or overwrite foreign raw.

Every own content ACK independently reads current head and draft slot before successors. Callbacks observe that published current head. Queued merge computations capture immutable body/session/sequence/click/head facts on the owner thread, execute pure work on the dispatcher, and revalidate/recompute before publication. Foreign head/raw or failed reads preserve local input and recovery conflict. Definitive replay-first rejection after a prior before-effect ambiguity uses actual read-backed resolution without invented historical outcome; failed reads retain exact uncertainty. Unauthorized uncertain PREPARE discovers current state without fresh catalog bypass and remains explicitly discardable through current CAS after disclosure.

Recovery preserves raw target/body, exposes requested-target conflicts, never fabricates a historical source, and treats PREPARED as recovery-only. Same retained target can resume visibility without replacing its owner. Typed exits retain session/ticket/action and consume once. Mutation/new-editor exits hold staged/invalid raw; browsing/closing can retain unresolved durable input. Failed initial reads reload the same session only when no exact mutation owner exists. All initial/recovery read completion branches resolve or drain queued exit/background intent; explicit events permit one eligible recovered TEXT attempt, while plain reconstruction remains read-only and genuine no-progress stays suppressed. Late same-session reads preserve hidden visibility after consumed navigation. PREPARED and staged ADD/REMOVE never gain automatic submission authority.

## Verification and lineage

Own evidence root `/tmp/cl-draft-editor-actor-01/evidence`; tmp `/tmp/cl-draft-editor-actor-01/tmp`. Each executed attempt retains exact production and both test source files in `attemptNN/source/`, SHA manifest `source.json`, exact command, log and summary. `executed-source-lineage.json` verifies snapshots for all failed executed attempts against their original pins; successful attempt04 retains its own unchanged sources. Earlier source-only held snapshots/manifests `prebuild-r1-source.json` through `prebuild-r4-source.json` and `prebuild-review-lineage.txt` distinguish prebuild review from execution. No prebuild HOLD is called a runtime failure.

| Attempt | Actual result | Evidence |
| --- | --- | --- |
| 01 | Failed unit-test compilation, 0 runtime tests / 0 XML; production Kotlin compiled. Cross-module public selection smart cast corrected only in fixture. | `attempt01/gradle.log`, `summary.json`, `source/` |
| 02 | Failed runtime: 81 tests / 4 XML, 8 failures, 0 errors/skips. Later queued valid text was suppressed despite retained UI/click state. | `attempt02/gradle.log`, `test-results/`, `summary.json`, `source/` |
| 03 | Failed runtime: 84 tests / 4 XML, 2 failures, 0 errors/skips. Exact ACK authority and dirty suppression repaired; two overdue deadline failures remained. | `attempt03/gradle.log`, `test-results/`, `summary.json`, `source/` |
| 04 | SUCCESS 1m52s, exit0: 94 tests / 4 XML, 0 failures/errors/skips; 5 tasks executed / 29 up-to-date. | `attempt04/gradle.log`, `test-results/`, `test-report/`, `summary.json`, `source/` |

Attempt04 actual classes: RetainedDeckDraftEditorTest 20, RetainedDeckDraftEditorRecoveryTest 51, existing DeckDraftQueuedInputTest 12, existing DeckEditorControllerTest 11. These are 94 tests, not the sum of repeated attempts. The source fixture supplies independent planning/projection and immutable generation/reservation/receipt semantics, before/after-effect failures, controlled port gates and a separate manual dispatcher; expectations do not come from the production actor. Coverage includes queued conservation, exact retry identity, catalog retirement, recovery terminal states, malformed receipt authority, dispatcher publication races, deadline max-wait and retained typed exits/read-time lifecycle matrix.

Attempt04 retains the original two failing final assertions unchanged. The passing `typingDuringPrepareCanOwnPreservedLaterAndRebaseExactly` intermediate previously expected revision1 and one PreservedLater receipt until another 500ms; that encoded the diagnosed deadline bug. With overdue timing retained, the strengthened oracle requires immediate revision2, first PreservedLater then Residual receipts, valid primer already persisted and invalid name retained byte-for-byte. No other fixture loosening. Existing inherited compile warning `LifeTable.kt:88` was not changed; no warning waiver/check suppression.

Exact command from android-app:

```
./gradlew :feature:shell:testDebugUnitTest --tests com.architectpsg.commandlibrary.feature.shell.presentation.RetainedDeckDraftEditorTest --tests com.architectpsg.commandlibrary.feature.shell.presentation.RetainedDeckDraftEditorRecoveryTest --tests com.architectpsg.commandlibrary.feature.shell.presentation.DeckDraftQueuedInputTest --tests com.architectpsg.commandlibrary.feature.shell.presentation.DeckEditorControllerTest --offline --no-daemon --console=plain --max-workers=2
```

Explicit pins: JAVA_HOME `/workspace/.command-library-env/jdk-17.0.16+8`, ANDROID_HOME `/workspace/.command-library-env/android-sdk`, GRADLE_USER_HOME `/workspace/.command-library-env/gradle`; own TMPDIR and java.io.tmpdir, inherited system truststore preserved. Used login:false because inherited login shell read stalls were diagnosed externally; no shell/environment configuration changed. Sole Gradle slot respected throughout, released after actual attempt04 exit; no further job planned.

Final frozen SHA256 source pins (before and after successful gate):

- RetainedDeckDraftEditor.kt: `e053d207acee577db42ccfd51407494378239260bdd5926e6324f42210f02db5`
- RetainedDeckDraftEditorTest.kt: `85f2a5dc7c6a6bfa9e130f23635e289ce0e35635a907fa506ad4a9599a8c5dbd`
- RetainedDeckDraftEditorRecoveryTest.kt: `fa267b3c91fcf3251a2983faa24e3e7f5968175ee70071b1056dab72ea695647`

Final log SHA256 `c60e200b225c96d847341420ddb5e2bba748930f9fdb2c806df4053008780a59`; final XML hashes and exact counts are in attempt04/summary.json. Failed logs: attempt01 `cecf09ca89a77c29695c354bbbe8de866a9ab7db08060527d7c87c272e04f3ff`; attempt02 `6b5223aa4764cf5e0bfc31f2c7093323788dcedda1414525450643fbf9d1ea2c`; attempt03 `0ad6117c6eb3a4a7f2f766321f9d5cc8ee081e2a65b9914ebd5f8c1ecd27cccf`. All retained, no unchanged rerun and no broad suite.

Independent inventory reviewer source readiness ACKed every executed behavioral freeze including attempt04 three full pins. Final inventory SOURCE/RUNTIME ACK independently parsed 94 tests / 4 XML with zero failures/errors/skips, verified executed shell task/SUCCESS1m52s/log SHA, working and archived source pins, the two repaired deadline cases, all ten added read/exit cases and three malformed-ACK regressions. Complementary campaign reviewer ACKed actual runtime for the ten read-family cases. Root received both final ACKs and authorized the exact four-path commit; neither reviewer ran builds or edited actor source.

## Limits and recovery

This producer gate proves the scoped actor against complete injectable host fixtures and the two existing regression classes. It does not prove actual SQL durability, AppGraph wiring, Compose navigation/dialog lifecycle, process restart, native/device/Fold7 behavior or assembled APK. Root owns the real integration and combined acceptance. No native/lint/broad feature suite was run by this producer. Retained uncertainty/recovery stays visible and exact; caller must continue using the shared typed exit interface rather than bypassing its mutation barrier. Root alone integrates the scoped commit and owns final release/merge authority. Final return records exact implementation HEAD/parent, clean status, four-path allowlist and handoff hash externally to avoid self-referential commit hashes.
