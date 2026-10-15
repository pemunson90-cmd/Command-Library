# Collection mixed bulk edit — isolated worker return

## Latest continuation delta

Implementation and focused verification are complete. Frozen eight-file source matches attempt04-source.sha256. Runtime stage passed 54 tests in five XML suites with zero failures/errors/skips; lint passed with zero errors and one inherited warning. Sole Gradle slot released. Root reported final independent actual Astra source/runtime/lint ACK matching all eight pins, 54 tests/five XML, zero lint errors/one inherited warning, and both compile-only diffs; every HOLD is released. Next: commit exactly the nine delegated paths, then return exact commit/head to root. Root owns actual binding, assembled S12 and native acceptance.

## Isolation and lineage

Before source edits verified pwd/repository top `/workspace/command-library-collection-bulk-01`, branch `agent/campaign-collection-bulk-20261015`, exact dispatch HEAD/base `1d15b3196c0e0f01a3b7aed128dfc3419c7b5fc5`, common Git directory `/workspace/Command-Library/.git`, clean status. No GIT_DIR/GIT_WORK_TREE/GIT_INDEX_FILE overrides; GIT_PAGER=cat; core.bare=false with no worktree/hooks/include overrides. Read AGENTS/CONTINUE/ACTIVE-CHATS/full delegated packet and consumed contracts. Prerequisite `57cb78e`, accepted actual backend `975fe17`; root's concurrent dirty named-version assembly was deliberately excluded from this dispatch base. No base refresh occurred.

Four existing paths were explicitly transferred: CollectionController, CollectionUiState, CollectionScreen and collection_strings.xml. Four new helper/dialog/dedicated test paths were absent before edits. This handoff is the ninth owned path. No shared/core/schema/backend/build/DI/navigation/ShellControllers/global-string/global-coordination file changed. No new dependency, network/auth change, push, merge, reset, cleanup, stash, subagent or other-process kill. Previous worker worktrees remain untouched. Root retains integration/publication/final merge and APK holds.

## Frozen consumer seams

All eight existing CollectionController constructor parameters stay in order, followed by:

`currentCatalogGeneration: () -> GenerationId? = { null }`

New observer seam:

`fun onActiveGeneration(generation: GenerationId?)`

Root must bind its actual active catalog generation getter and forward active-generation observations. Absence fails closed only for explicit Set finish; Keep finish makes no catalog calls and supports unresolved lots. CollectionScreen(controller,widthClass,onBrowseCards) remains compatible. Root alone owns ShellControllers/ports/DI/navigation wiring.

## Resulting behavior

Bulk selection is separate from detail selection. It freezes the displayed snapshot/order and last anchor, supports arbitrary toggle and inclusive range, and rejects selection beyond InventoryProjectionLimits.MAX_EDITS=1,024 without truncation. Oversized ranges reject before scanning/allocating an entire range. Present selected IDs follow frozen display order; missing retained IDs sort last by exact ID, stay selected, and have actionable status. Refresh does not silently drop them. Explicit rebase preserves intent/IDs, obtains new originals and requires a fresh preview.

The form uses typed Keep/Set instructions for exact per-lot nonnegative quantity (zero stays an active lot), condition including UNKNOWN, location including empty clear, and finish. Common/mixed original summaries and exact missing-ID status appear before preview. All-Keep is unconfirmable. Each selected row has before/after/issues; any row or structural issue blocks the whole operation. Exclusion is an explicit changed selection and clears the old preview. One Replace(original.copy(requested fields)) is generated per selected lot. IDs, per-lot revisions, raw/unknown identity, language, notes, acquired evidence and all untouched fields survive unchanged.

For each explicit Set finish, every selected row needs proof even when the desired value equals its old finish. Each distinct Known qualified printing is looked up once per preview; exact returned ID, stored language, no Known Oracle contradiction, provider provenance, nonnull matching active generation, and Known desired finish availability are required. Returned catalog metadata never rewrites stored identity. Confirmation repeats proof and checks the direct generation getter, draft/selection ticket and inventory revision before generating an operation ID. Observer invalidation is supplemental; direct callback fences publication and submission. This is revalidated evidence, not an atomic catalog-plus-inventory transaction.

Bulk Apply shares the existing single edit/add/undo request queue. Submitted patch/expected revision/operation ID are frozen and survive uncertainty, form closure, resize and exact retry. Retry bypasses new catalog work. Obsolete backend issues clear after explicit draft change/exclusion/rebase/repreview. Committed bulk delta must equal the frozen proposed delta; wrong success remains uncertain. Historical acknowledgements refresh actual inventory and never reconstruct current state. Cancel before submission writes nothing; cancellation after submission retains uncertainty. Submitted requests are excluded from observer/read cancellation. Explicit disclosed abandon remains the existing reconciliation escape.

Inventory position indexing runs on the supplied dispatcher, polling cancellation every 1,024 visits, rather than preparing up to 100,000 lots on UI scope. The frozen form carries at most 1,024 aligned original rows, and preview preparation/projection run on that dispatcher. Selection sorting is bounded to at most 1,024 IDs. JVM/native allocation and deep model equality do not establish a hard physical cancellation-latency claim.

Adaptive existing collection layout and the new lazy bulk dialog use resource text, plurals, 48dp controls, selected Keep/Set semantics, exact paged location text, keyboard toggle/Shift range and retained controller state. Preview/cancel/resize/navigation do not generate operation IDs or writes.

## Review repairs

Initial independent review required common/mixed originals and prepreview missing status, dispatcher/cancellation preparation, bounded range rejection, and clearing obsolete backend issues. These were repaired before the first build. Preflight also corrected fixture type/suspension mistakes, normal printing provenance provider, and undo conservation: only committed changed IDs receive undo revisions; unselected lot c stays exact. Controlled suspending catalog gates preserve in-flight inventory/generation preview and confirmation race oracles.

Final prebuild review found rebase→discard→toggle could crash while sorting a retained missing ID. Deterministic missing-ID ordering/status plus the exact regression repaired it. Injected structural rejection now has empty changes/merges like the accepted producer. Root reported actual Astra source ACK matching all eight prebuild hashes with every HOLD resolved. Two subsequent compile-only diffs are separately preserved and included in the final fresh-review request: explicit snapshot/index result type, and required actualRevision in a synthetic rejected response. No check was weakened or skipped for green.

## Verification and failure lineage

Evidence root `/tmp/cl-collection-bulk-01/evidence`, own temp `/tmp/cl-collection-bulk-01/tmp`. Pinned JAVA_HOME `/workspace/.command-library-env/jdk-17.0.16+8`, ANDROID_HOME `/workspace/.command-library-env/android-sdk`, GRADLE_USER_HOME `/workspace/.command-library-env/gradle`. Existing JAVA_TOOL_OPTIONS retained with appended own java.io.tmpdir. Every invocation used `--offline --no-daemon --console=plain --max-workers=2`, sole root-granted build slot, and frozen source. Exact command files, logs, source manifests, summaries and evidence SHA manifests remain under the attempt prefixes.

| Attempt | Actual result | Scope |
|---|---|---|
| 01 | Production compile failure, exit 1, 1m55s | Kotlin Nothing/NewConstraintError for inferred snapshot/index Pair. Zero tests/XML; lint not run. |
| 02 | Production compile passed; unit compile failed; root-directed graceful cancellation exit 130 | Missing required actualRevision in fixture Rejected. Partial lint NOT completed; zero tests/XML. |
| 03 | Compile-only SUCCESS, exit 0, 1m33s | :feature:shell:compileDebugUnitTestKotlin; 25 tasks, 1 executed/24 up-to-date. Zero tests/XML, no behavioral claim. |
| 04 runtime | SUCCESS, exit 0, 1m59s | Five focused suites, actual 54 tests/5 XML, zero failures/errors/skips. |
| 04 lint | SUCCESS, exit 0, 6m38s | :feature:shell:lintDebug, same eight source hashes; zero errors/one inherited warning. |

Attempt02 cancellation followed exact own wrapper client PID119222/cwd/cmdline/TMPDIR identity verification. SIGINT targeted only that client, at root instruction after known compile failure. Session exited130; single-use daemon119263 exited after interrupt. No daemon/other process was signaled. Identity/cancel request evidence is retained. Source stayed frozen until both job/client and own single-use daemon had exited.

Runtime XML counts: CollectionBulkControllerTest 19 (0.737s), CollectionBulkUiTest 3 (66.943s), unchanged CollectionControllerTest 25 (0.312s), CollectionUiTest 4 (5.003s), ExternalizedStringsTest 3 (0.302s). All failures/errors/skips zero. These are 54 unique methods, not repeated compile-probe counts. The dedicated tests cover exact mixed/zero/unknown preservation, explicit exclusion/repreview/apply/one undo, per-printing proof, generation and inventory races, historical uncertain replay, rejection/wrong-success handling, missing retained IDs, selection bounds, pre/post-submit cancellation, shared queue, 200%/real resize and keyboard range semantics. Synthetic ports support presentation evidence only.

The sole lint warning is inherited global strings.xml:288 PluralsCandidate for counters, outside this allowlist. No delegated-file lint issue. Existing compile warnings are LifeTable.kt:88 always-true condition and CoilCompatibilityProbeTest.kt:18 nullable File receiver. Actual lint XML/HTML/SARIF/text are copied into attempt04-lint-results; actual five runtime XML copies are in attempt04-runtime-results. Summaries/evidence manifests preserve both scopes. git diff --check passed; source manifests verified before/after the finished runs.

The focused runtime command (after pinned environment prefix) is:

`bash android-app/gradlew -p android-app --offline --no-daemon --console=plain --max-workers=2 :feature:shell:testDebugUnitTest --tests '*CollectionBulkControllerTest' --tests '*CollectionBulkUiTest' --tests '*CollectionControllerTest' --tests '*CollectionUiTest' --tests '*ExternalizedStringsTest'`

Only after runtime green, the same source ran:

`bash android-app/gradlew -p android-app --offline --no-daemon --console=plain --max-workers=2 :feature:shell:lintDebug`

Base/final source comparison (four existing paths plus four new paths) is preserved in /tmp/cl-collection-bulk-01/evidence/final-base-and-source.json; final-source.sha256 repeats the exact runtime/lint-reviewed eight source pins.

## Final source hashes

ff2a0a5cb59dfa80d201cc2b80735a3c66b830d811ba54cc13ac98ad77cb3253  android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/CollectionController.kt
1795121e72ae6f205453a807bfa8b5aa0c204ed37d963449d75fd692e39bf896  android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/CollectionUiState.kt
2dbe4812a20eab5d19c20f7405f0fee859e4b60a367dd21992683a005685898b  android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/CollectionBulkEdit.kt
66785c99670da85bc10ef0809fac68ef659db813d5f2cd524e05dc8b29825acd  android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/ui/collection/CollectionScreen.kt
df7b5a6f46f7721448af50ee57917d49ef98aefc6e829bf60142baa2712ef3cd  android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/ui/collection/CollectionBulkEditDialog.kt
6b2dc9f9e2e12d1d5dc735a0cdfe19adab230a9638475423cd5adcb88069fd8d  android-app/feature/shell/src/main/res/values/collection_strings.xml
fa59487eaee139e09021f82c34842a843979d8da86845baf6534057e6600750f  android-app/feature/shell/src/test/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/CollectionBulkControllerTest.kt
1969957b908bf8f83880a8d4680a5cf41b6bcec37f3a8e5210fa675f8ef06a50  android-app/feature/shell/src/sharedTest/kotlin/com/architectpsg/commandlibrary/feature/shell/CollectionBulkUiTest.kt

## Remaining root gates

Actual current-generation callback/observer binding, AppGraph/navigation composition, actual reopened Room mixed bulk journey with catalog proof, assembled S12/accessibility and native/physical Fold7 acceptance remain root-owned. No full S12, backend durability, finished APK, main merge, release, catalog acquisition, durable drafts/autosave, or other feature acceptance is claimed. Root authored its separate actual-backend journey and will verify the assembled tree after consuming this scoped return.
