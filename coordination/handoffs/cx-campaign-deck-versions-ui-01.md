# Named deck versions presentation — isolated return

## Latest continuation delta

Implementation source is complete in the seven-path delegated allowlist. Attempt01 focused shell controller/Compose/unchanged ExternalizedStrings plus lint completed SUCCESS in 13m 21s: 3 XML suites,24 unique tests,0 failures/errors/skips; lint 0 errors/1 inherited warning. After a fixture-only recovery-target bookkeeping correction, root-authorized attempt02 controller-only verification completed SUCCESS in 1m 10s: 1 XML, 18 tests, 0 failures/errors/skips. Production/resource/Compose source remains identical to the passing attempt01 hashes. No integration, backend, native, APK or finished-feature acceptance claim.

## Isolation and lineage

Before any source edit verified exact dispatch base `0188aa498581455134b3e93d6339690ea79a4be1`, branch `agent/campaign-deck-versions-ui-20261015`, pwd/repository top `/workspace/command-library-deck-versions-ui-01`, common Git directory `/workspace/Command-Library/.git`, clean status. No GIT_DIR/GIT_WORK_TREE/GIT_INDEX_FILE overrides; GIT_PAGER=cat; core.bare=false, no worktree/hooks/include overrides. Accepted shared product ancestor `b2ac8de263a7455e6989a097f172d81ea006c0c9`. Read AGENTS/CONTINUE/full packet, reviewed r2 design plus recovery-target clarification, actual complete core port and model/difference/callPort contracts. New owned paths were absent before editing. No base refresh, network/auth changes, subagents, push, merge, resets, cleanup, locks, shared-file edits or process kills. Root controls local publication/integration.

Complete LocalDeckVersions port SHA remains `b8abaf1ca5d75a93063aca63582b5c885b9be42291a116fc4db136a5d5ee7e1c`. No consumed DTO/interface, codec, pins, schema, build, DI, navigation or backend change.

## Component seams

`internal DeckVersionsController(scope: CoroutineScope, versions: LocalDeckVersions, decks: LocalDeckLibrary, dispatcher: CoroutineDispatcher, nextEntityId: () -> EntityId, onCommitted: (DeckId, Revision) -> Unit = { _, _ -> })`

`fun openDeck(id: DeckId)`

`@Composable internal fun DeckVersionsScreen(controller: DeckVersionsController, widthClass: WidthClass, onClose: (() -> Unit)? = null)`

Root must supply actual ports/operation IDs, invoke openDeck for actual selected-deck entry, and retain the screen while requestClose/discardDraft returns false. Screen invokes onClose only after safe close or confirmed unsaved-name discard. Known restore acknowledgement invokes onCommitted synchronously before any reload, including historical replay; root owns deck/collection invalidation and actual navigation. Capture does not emit content-change callback.

## Behavior

The retained controller freezes exact operation ID, source revision, target and label once; serializes writes; keeps unknown acknowledgement latched through later failures and safe close; retries identical requests only on explicit action; and exposes disclosed abandonment. Definite STALE requires actual refresh/fresh preview rather than CAS substitution. Generation fences reject obsolete open/head/list/target/preview replies, preserve stable target IDs and prevent old head regression. Pages retain actual port order/cursors/epoch, validate row shape/duplicates and perform one stale-cursor first-page reload without eagerly fetching history. Missing deck differs from empty history.

Labels remain exact, nonblank, strict UTF-8 up to 1,024 bytes, including preserved whitespace and legal duplicates. Draft names are session-only unsaved input with explicit discard. Capture validates exact complete source manifest and never predicts a content revision advance. Preview uses actual current-to-historical DeckDifference without writes or predicted commit time. Restore validates complete target replacement, checked revision+1, immutable creation anchor, monotonic recorded time, exact original pre-restore recovery manifest/name/source and shared recorded timestamp before reporting success/callback. Actual head/list are reread after acknowledgement; historical receipt result is never substituted as current head. Cancellation keeps truthful pending state and propagates.

Adaptive Compose shows USER and RECOVERY checkpoints as direct selectable restore targets, historical revisions and honest numeric UTC recorded timestamps. Compact single-pane and wider split-pane layouts use lazy lists, resource strings and 48dp controls. Field inspection exposes every DeckChangedField, with exact current/historical values including primer, category registry/order, budget unit/precision/scale, references, ordered entry IDs and complete package instance/contribution graph. Entry differences expose quantity/board, exact identities/printing/finish with Unknown/Absent reasons/evidence, notes, categories and role overrides. Text uses 4,096-unit readers preserving surrogate boundaries; nested lists show one indexed value at a time without flattening package/history graphs. Unknown reconciliation hides the precommit preview assertion. Composition/resize/navigation generate no IDs or writes.

## Review and repair lineage

Root's independent review identified cross-form draft loss: beginning capture over a nonempty recovery name allowed capture success or capture-form discard to erase that unrelated name. Repaired before attempt01 by prompting before the transition; Keep preserves exact recovery draft/preview, explicit Discard opens capture, and recovery-name API ignores hidden edits during capture form. Two regressions cover canceled capture and successful capture after explicit consent. Self-review also preserves drafts on repeated beginCapture/same-target selection and prompts before discarding preview/recovery name. Root reported no other concrete source HOLD in the completed review batch; final refreshed review/integration authority remains root.

Final test-port self-review corrected only controller fixture RECOVERY summary target bookkeeping (previous constant old target was wrong when restoring a recovery checkpoint) and added an assertion that the generated summary retains its actual selected recovery target. Exact 4-hunk diff is preserved as attempt02-fixture.diff; production/Compose/resources unchanged. Attempt01 runtime evidence remains archived, rather than relabeled as repaired-fixture evidence.

## Verification evidence

Own evidence root `/tmp/cl-deck-versions-ui-01/evidence`, own TMPDIR `/tmp/cl-deck-versions-ui-01/tmp`. Root granted each sole Gradle slot; source froze for both invocations. JAVA_HOME `/workspace/.command-library-env/jdk-17.0.16+8`, ANDROID_HOME `/workspace/.command-library-env/android-sdk`, GRADLE_USER_HOME `/workspace/.command-library-env/gradle`; existing JAVA_TOOL_OPTIONS retained with appended own java.io.tmpdir. No pins changed. Both use offline, no-daemon, console plain, maxworkers2.

Attempt01 exact command in attempt01-command.txt:

`bash android-app/gradlew -p android-app --offline --no-daemon --console=plain --max-workers=2 :feature:shell:testDebugUnitTest --tests '*DeckVersionsControllerTest' --tests '*DeckVersionsUiTest' --tests '*ExternalizedStringsTest' :feature:shell:lintDebug`

SUCCESS exit0 in 13m 21s,55tasks executed. Actual XML counts: DeckVersionsControllerTest 18, DeckVersionsUiTest 3, unchanged ExternalizedStringsTest 3 (the packet's ExternalizedUiTextTest class is absent); all failures/errors/skips 0. Compose 56.002s/controller 3.0s/strings 0.389s. Lint 0 errors/1 inherited PluralsCandidate in existing strings.xml:288 (counters), no delegated source warning. Existing compile warnings: LifeTable.kt:88 always-true condition and CoilCompatibilityProbeTest.kt:18 nullable File receiver. All 6 frozen source hashes verified after run. Captured own-JVM active lint UAST/FIR thread dump after slow readback; no OOM/deadlock evidence, no process action. Single-use daemon exited after completion. Archived log,3 XML and lint XML/HTML/SARIF/text in attempt01-results; counts attempt01-summary.json; hashes attempt01-evidence.sha256. Pre-review source hashes retained as attempt01-source-before-review.sha256.

Attempt02 root-authorized command in attempt02-command.txt:

`bash android-app/gradlew -p android-app --offline --no-daemon --console=plain --max-workers=2 :feature:shell:testDebugUnitTest --tests '*DeckVersionsControllerTest'`

SUCCESS exit 0 in 1m 10s, 34 tasks (2 executed/32 up-to-date). Actual single controller XML has 18 tests, 0 failures/errors/skips. Archived log/XML, attempt02-summary.json and attempt02-evidence.sha256 preserve final fixture/source association. All six final source hashes verified. Sole slot released after process exit. Root explicitly excluded lint/Compose/full repeat because only fixture bookkeeping/assertion changed. Unique coverage is 24 tests, not 42. No failed invocation or skipped check is concealed; this is a fresh controller-only run after test-fixture correction.

## Frozen source hashes

Final six-file manifest attempt02-source.sha256:

c2606bdd431f5e0ced668fed3c3cf99247d5e85d953789d8caa3b63b887373e8  android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/DeckVersionsController.kt
8ba118b1eb93cc077f3211e94c086dbba8266fa1646a85d16147c4f422eb9878  android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/DeckVersionsUiState.kt
980b96c5d509587f924bc177ab4b9d042c56646e8c81074008b1c5c8c1ff8bdf  android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/ui/decks/DeckVersionsScreen.kt
2fd602a9f15e01525c6919991aa03fdf8f721f1f20e7ba49d3e9b903b8e3e669  android-app/feature/shell/src/main/res/values/deck_versions_strings.xml
fdb7984404cf688e9b6257c81a850c76de646f854b1486564e2d58bd47f44805  android-app/feature/shell/src/sharedTest/kotlin/com/architectpsg/commandlibrary/feature/shell/DeckVersionsUiTest.kt
936e413136f5022b95c8469a074234ac827d4dbcf12c6e21ff36b3719099a965  android-app/feature/shell/src/test/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/DeckVersionsControllerTest.kt

## Remaining root gates

Real backend/schema/receipts/Room durability, actual production port binding/AppGraph, deck entrypoint/navigation, restore acknowledgement invalidation consumers, assembled S11 actual database journey, shared accessibility integration, emulator/native/Fold7/canonical/release/finished APK remain root-owned and unclaimed. Synthetic ports support presentation tests only. No package editing, durable draft/autosave, provider acquisition or full backup scope is claimed. This return is one isolated presentation component, not main merge/release authorization.
