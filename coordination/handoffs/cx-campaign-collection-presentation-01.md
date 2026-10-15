# Collection presentation worker handoff

## Latest continuation delta

Complete delegated controller/state/adaptive Compose screen/dialog/resources and focused tests are implemented on `agent/campaign-collection-presentation-20261015`, worktree `/workspace/command-library-collection-presentation-01`. Final focused attempt04 passed32 tests/3 XML with0 failures/errors/skips and completed lintDebug with0 errors. Source and inherited-dependency hashes were verified after the run. Root independent source/evidence readback and assembled actual backend/AppGraph journey remain required; no app/device/backend acceptance claim here.

## Isolation and exact lineage

Before edits verified pwd/repository top/worktree branch/HEAD/common Git directory. Original dispatch base exactly `b5fd7c11940c19fc36d015ad62781e75c26dbacf`; common Git directory `/workspace/Command-Library/.git`; no GIT_DIR/GIT_WORK_TREE/GIT_INDEX_FILE overrides; core.bare=false and no core.worktree/hooks/include/worktree-config overrides; clean before writes. Read AGENTS/CONTINUE/full packet and minimum actual core/shell contracts/harness context. No child agents, network, push, merge, reset/clean/stash, dependency/configuration change or worker shared-file edit.

Root explicitly refreshed this idle worktree with its own two-path dependency correction: original root commit `2542f7efd910e07b62418d13561064565234e881`, cherry-picked by root as `922962da5c6de31ab191f883bfd5be52df8afbd2`. It moves the unchanged existing deck finish sentinel out of inline UI text via DeckLibraryController.clearFinishRestriction. Only root-owned DeckLibraryController.kt/DeckEditorDialog.kt changed in that inherited commit. Worker did not modify these files. Root already owns the dependency; integrate ONLY worker implementation/handoff commit(s) after922962d, not this inherited dependency again. Final dependency manifest: `/tmp/cl-collection-presentation-01/evidence/attempt-04-dependency-sha256.txt`; DeckLibraryController SHA256 `d6bbb9a61645e3ea315267bb7065e66d250fff2b4245dfbc0d7f65a881eb736f`, DeckEditorDialog SHA256 `669072b32c881cd83f8a718f24fd7e5846f58acf8d9398a394390255c773c115`.

## Delivered behavior and integration seams

All new types are internal to shell, with core ports only. Actual constructor:

`CollectionController(scope:CoroutineScope, inventory:LocalInventory, decks:LocalDeckLibrary, allocator:OwnedQuantityAllocator, catalog:CatalogPrintingLookup, project:(InventorySnapshot,CollectionPatch,Revision,CancelToken)->Outcome<InventoryProjection>, dispatcher:CoroutineDispatcher, nextEntityId:()->EntityId)`

`CollectionScreen(controller:CollectionController,widthClass:WidthClass,onBrowseCards:(()->Unit)?=null)` renders an actual Cards button only when root supplies the navigation callback. Root detail calls `beginAdd(record:PrintingRecord)`; it retains direct-entry intent even before the collection has ever loaded by obtaining a validated complete base, then checking exact current catalog identity/language/oracle and Known supported finishes. Users choose an actual offered finish; no Finish.entries eligibility inference, printing-ID entry, inferred name/raw identity or acquired time. Confirmation rereads current catalog proof before the first submission. Exact already-submitted reconciliation bypasses new catalog eligibility checks so an old committed receipt can be recovered.

Explicit preview/cancel/confirm supports quantity including zero, condition/location/notes, removal and grouped undo. Existing unresolved identity/finish/raw/notes/acquired metadata are retained through replacements. IDs are allocated only on explicit preparation/confirmation/undo, not composition or resize. A single mutation request retains exact patch/expected revision/operation ID; unknown acknowledgments latch through later failures/rejections. Forms can close while reconciliation remains available outside the dialog; forgetting a request requires a separate warned abandon action. Rejected row issues never appear as committed success. Historical receipts trigger a fresh complete snapshot read rather than replaying deltas into current state. Draft changes persist in the retained controller only; explicit save/discard disclosure makes no durable-draft/autosave claim.

Snapshot/catalog/preview/deck/allocation responses are generation-fenced. Newer selection during catalog confirmation is preserved. Explicit useRefreshedInventory preserves draft fields while updating the before-image/base for another preview. Allocation obtains fresh complete inventory and a real stored deck, starts SAME_ORACLE with explicit board choices and no pins, and reports exact deck/inventory revisions. Unknown remaining quantities are labeled uncovered/uncertain. The entire inventory goes to the allocator, including its explicit >10000-lot failure; nothing is truncated. Root forwards `onDeckChanged(id:DeckId,revision:Revision)` to a monotonic fence bounded to the selected deck. Same/older/other-deck observations preserve plans, newer revisions invalidate; late noncooperative loads cannot republish below the observed fence. Explicit calculation reloads fresh inputs without automatic recomputation loops.

Compact tabs and wider split panes use existing WidthClass/theme components, lazy inventory/deck-demand lists,48dp targets, semantic/visible finish selection, labeled board controls, localized new strings and bounded paged text/issues. All100 dedicated XML resource IDs use collection_ prefix. Screens do not expose domain failure messages/evidence or execute stored text.

## Verification, repairs and retained evidence

Root serialized the sole Gradle slot and authorized each coherent attempt; source remained frozen during each active build. Final command (same focused tests/lint throughout):

`JAVA_HOME=/workspace/.command-library-env/jdk-17.0.16+8 ANDROID_HOME=/workspace/.command-library-env/android-sdk GRADLE_USER_HOME=/workspace/.command-library-env/gradle TMPDIR=/tmp/cl-collection-presentation-01/tmp JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Djava.io.tmpdir=/tmp/cl-collection-presentation-01/tmp" bash android-app/gradlew -p android-app --offline --console=plain --max-workers=2 :feature:shell:testDebugUnitTest --tests '*CollectionControllerTest' --tests '*CollectionUiTest' --tests '*ExternalizedStringsTest' :feature:shell:lintDebug`

- Before attempt01, independent source review identified allocation freshness at final publication, selection-generation capture before suspended catalog confirmation, contradictory pending precommit assertion, premature fresh-snapshot success text, and missing visible/semantic finish selection. All repaired, with regressions.
- Attempt01 FAILED6m2s: compilation succeeded;30 tests/3XML,4 failures. All23 controller cases passed;3/4 new UI cases timed out because custom await omitted existing paused-looper waitForIdle convention. The remaining failure was the inherited DeckEditorDialog inline-sentinel ExternalizedStrings check, repaired only by root's explicit dependency refresh above. lintAnalyzeDebug ran but final lint reporting did not complete; no lint-pass claim. Own harness now follows existing waitForIdle and actual ForcedSize/FontScale overrides. Also repaired first-use Add intent and late-preview freshness, adding two controller regressions and honest unresolved allocator doubles (0 proven,5 uncovered).
- Attempt02 FAILED15s at test compilation only: incorrect nested imports for top-level ForcedSize/FontScale extensions. No new behavior tests executed. Fixed to exact existing harness imports.
- Attempt03 FAILED2m7s:32 tests/3XML,2 failures. Externalized3/3 passed and prior UI await/finish/pending/resize scenarios passed. The two remaining test mechanics were an uncomposed lazy demand targeted before scrolling its LazyColumn host, and an Unconfined test that did not establish its claimed refresh-before-projection interleaving. Corrected host scrolling and used a gated background projection, awaiting publication of the newer snapshot before callback release; the strong stale-preview assertion remained.
- Attempt04 SUCCESS1m48s:32 tests/3XML,0 failures/errors/skips: CollectionControllerTest25, CollectionUiTest4, unchanged ExternalizedStringsTest3. LintDebug fully completed,0 errors. Source and root-dependency manifests revalidated. git diff --check passed. Sole Gradle slot released after actual result readback.

Logs/manifests/XML are under `/tmp/cl-collection-presentation-01/evidence`. Final log `attempt-04-gradle.log` SHA256 `6bb0350ec52d35f90641ea401a59c032eb57a7f33f2626c305bb398b9eacaf0b`. Final XML directory `attempt-04-xml`: UI SHA256 `ab20a10d93014a7e9fedb5f2f9448046bc825de7a69cd74a805071a862af11ec`; controller SHA256 `d536ee66fb7b892cf97b07d63591a26cfcf5440c432f67081cd28585d6d37cfc`; unchanged externalized SHA256 `a81ea88052f844df31a0b157016cfbcc6c09b7d9815cae22d2feb470e08d5f7d`. Retained final lint XML `attempt-04-lint.xml` SHA256 `250010172860de4ab3965aef14a20a5e27ebbc7e638ba917733fff690404707d`, plus HTML.

Failure lineage is retained: attempt01 log SHA256 `9f50f73cdc66e2e7fd35c1597873dc9e04d2bacd5507671f9e34a1648939a453`; attempt02 log SHA256 `feaf5dccdf46d1f4e3dcb283009861a43eecdf2cb3313e2f2c6b22edeb1833fc`; attempt03 log SHA256 `89a63ee66e03d70b06414ba47ecccfeecc211d631615704d8befb809f734c2b1`. Source manifests exist for all attempts and XML archives for attempts01/03/04. No attempt02 XML behavior result is claimed.

## Final owned source hashes

Manifest `attempt-04-source-sha256.txt` covers exactly seven delegated source/test/resource files (paths below relative to android-app/feature/shell/src):

- `main/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/CollectionController.kt`: `325d82d100d56b3f896ccf5fbfa1ffb102b35e5b957c19b4e41b107e8d878856`
- `main/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/CollectionUiState.kt`: `3934cb9c740daaaf7de883bab6e0375edb9f79898dfaea49ce1493ca3ed55ef9`
- `main/kotlin/com/architectpsg/commandlibrary/feature/shell/ui/collection/CollectionEditDialog.kt`: `da38158b0c2a3d1ae7b836412f805fe5d395cb8c94334f87d795c87081485661`
- `main/kotlin/com/architectpsg/commandlibrary/feature/shell/ui/collection/CollectionScreen.kt`: `d0df10e00edeb37513d1d1d7f9b06482539aa1c0f743d9e0eec184cf61c2b76e`
- `main/res/values/collection_strings.xml`: `a00f405c5670933e31fb5191d7ae5b17b866e6e021ea4cd5060ea895527ee5fe`
- `test/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/CollectionControllerTest.kt`: `c644c7215ab7980ad63bf00feb5ec48441d1b468d4a24cccd12f10be4179775f`
- `sharedTest/kotlin/com/architectpsg/commandlibrary/feature/shell/CollectionUiTest.kt`: `0a7db77c3258d21fcf2bab6ddc8cac5eab4f18dc671ae96a2d642d7120cd4911`

Only those seven files and this handoff belong to the worker return. Root core/model/build/schema/storage/root composition/ShellPorts/ShellControllers/ShellRoot/AppGraph/global strings remained worker read-only, including inherited dependency paths.

## Findings, unrun scope and recovery action

No remaining known functional worker blocker. Lint is successful with3 nonblocking warnings: new touch() modifier-factory extension style suggestion; new preview-summary plural candidate; inherited global profile_counter_limit plural candidate. None were suppressed or hidden. Root may resolve the two presentation warning nits during reviewed integration; inherited global strings stay root-owned.

Focused tests use bounded honest synthetic ports and prove component/controller/Compose behavior only. No actual backend/AppGraph integration, authoritative DB/codec/receipt/tombstone/undo durability, real catalog source, broad suite, native/emulator/device/TalkBack/performance, physical Fold7, APK/release or complete C01/C02/B10/B11/S12/PX06-049 acceptance claim. Root's actual backend producer and real detail/nav/DI integration are separate disjoint work. Existing collection/pricing map retains full CSV/import/quickadd/priced acquisition/mixed-value bulk/durable-draft successors. User finished-APK hold/final merge authority unchanged.

Next: root final independent exact-source/evidence review, integrate only this worker's scoped commit(s), then run assembled actual LocalInventory/deck/allocator/AppGraph/detail→collection journey gate. Preserve all prior failed evidence; do not rerun historical frozen native failures or duplicate builds.
