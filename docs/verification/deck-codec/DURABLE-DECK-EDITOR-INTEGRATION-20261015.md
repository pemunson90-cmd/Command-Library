# Durable local deck editing — scoped integration accepted

Published continuation checkpoint170: cef028589e0202b5275bcde3fe6c8353f72bde6f. Exact tested android-app tree **5945509743a60cc9d16d101b5317fbbd57a97f25**. Actual gpt-6-astra high independently ACKed architecture, frozen backend/editor/DI source and final XML/log readback. Reviewers performed no builds or writes. Main merge/release is not authorized by this checkpoint.

## Delivered path and compatibility

Real create→installed offline card selection/add→deck text/entry quantity/board/finish/notes/existing category membership→accessible reorder/removal→persistent grouped undo and exact uncertain-ack retry. AppGraph binds complete SqlLocalDeckLibrary and SqlLocalDeckEdits over the same real database/CLDK codec, the real CLDP codec and existing API23-safe SystemUtcClock. No production doubles or partial aggregate DeckRepository. The same serialized controller queue handles create/duplicate/patch/undo; operation ID, revision and patch freeze once, later user selections remain protected, and uncertain outcomes retain the exact request.

Accepted shared prerequisite66302c4 adds schema11/CLDP/request framing, retaining all38oldentities/40total, unchanged CLDK1 and original CREATE/DUPLICATE wire bytes. Backend62f606fd6d75524d4987280e10d127dfe5469cc5 from c2509f0 integrateda38a45c: seven allowed files only, original worktree clean/preserved, canonical lock released. Root compared all four committed production hashes to independent ACK before integration. Backend details and full source hashes: coordination/handoffs/cx-campaign-deck-edits-store-01.md.

## Verification actually executed

| Gate | Result | Executed scope |
|---|---|---|
| Worker attempt1 | SUCCESS1m43s;77tests/7XML;0fail/error/skip | decks34, existinglibrary22, neweditstore21 |
| Worker attempt2 | SUCCESS21s;83tests/7XML;0fail/error/skip | decks34, existinglibrary22, neweditstore27 after review-driven regressions |
| Root presentationr1 | SUCCESS4m34s;23tests/4XML;0fail/error/skip; shelllint | 8newcontroller,10existingcontroller,3newCompose,2existingCompose |
| Root assembledr2 | SUCCESS1m48s;186tests/22XML;0fail/error/skip | decks34/5XML,pricing25/2XML,storage77/7XML,app10/2XML,shell40/6XML |

Root assembled command: configured existing JAVA_HOME/ANDROID_HOME/GRADLE_USER_HOME, own TMPDIR/java.io.tmpdir=/tmp/cl-deck-editor-01/tmp; `bash android-app/gradlew -p android-app --offline --console=plain --max-workers=2 :feature:pricing:test :feature:decks:test :data:storage:testDebugUnitTest` filtered to deck codec/migration/library/edit-store suites; `:app:testDebugUnitTest` filtered to DeckSchemaRoomIntegrationTest/EnglishCatalogAccessIntegrationTest; `:feature:shell:testDebugUnitTest` filtered to the two editor/two library suites plus ShellUiTest/PrintingSelectionUiTest; app/shell/storage `compileDebugAndroidTestKotlin`. Exact command/log and final XML: /tmp/cl-deck-editor-01/evidence/{presentation-r1.log,assembled-r2.log,final-xml,final-summary.json,tested-android-tree.txt}. Worker evidence: /tmp/cl-deck-edit-store-worker-01/evidence.

App/shell instrumentation-source compilation executed; storage was UP-TO-DATE from prior actual compilation. **No native instrumentation executed.** Actual Room tests run on host with the real-file OS bridge, distinct from raw host SQLite and physical Android execution. Three actualRoom probes cover fresh11/9→11 preserving profile0001FF; real library create/duplicate/independent reader; real grouped edit/undo/historical CREATE/APPLY/UNDO retries/cross-method conflict/independent Room reader. Applied direct assertions cover revision/count/quantity/identity/name; board/finish/notes/primer submissions also have component coverage, and undo asserts exact original snapshot except new revision/time. Reopen/cross-connection is not process-kill evidence. Seven existing English integration cases passed with the actual composition.

## Review corrections and scope

Both worker gates and both root gates passed; there was no failing-build/test lineage in this slice. Independent review before final source freeze required:

- Bind receipt result ID/revision to immutable APPLY/UNDO input, overflow-safely; strengthen baseline CREATE exact input and DUPLICATE local fresh ID/revision0/name checks. Valid-FK wrong-target receipts fail closed.
- Reject impossible revision-zero undo state, and validate bounded predecessor references/content-to-pointer agreement. Preserve exact five-table state on injected failure/cancellation; immutable history and monotonic grouped undo survive reopen.
- Let a pending editor Close retain its exact request and expose explicit warned abandonment; synchronously check current catalog generation, retain category accessibility names/search validation, and generalize uncertain-save warnings to edits/undo.

Final reviewed source hashes: AppGraph e7bd21cbb14d37182baeee42e8a5c0e7e59838f94a21243637500c162649d98e; actualRoomtest7afaea6ce64576539ab3deacd0c20d44bb3bdce9211e4b43abb4d2a5bdc18e5a; controller71d2096a99bb07bcaefc2a10c0302cf34c993315cb6a2241379a9f0e3f64f1d1; ShellControllers7802ddbe5a57fb1f81aab0b20716b30ebab718e391b2cfc3b1f749b1d96230e5; editorDialog45e9e7e9721ecbfb1e69c397dac834a533b2910918f6e3d3d9a0616ae53d037f. No staged/unstaged Android drift at final independent readback.

Controller/Compose fixtures are explicitly synthetic and do not prove SQL durability. Explicit-save forms retain input across resize/navigation; separately durable invalid-text drafts, <=500ms autosave/background flush remain retained successors. Full S05/S06 organization/category creation/query, named versions/restore/packages, collection/pricing and other retained v1 features remain mapped open. Native/process-kill/Fold7/TalkBack/performance/canonical release and finished-APK holds remain. No new skip/deferral or completed APK delivery.

One Gradle slot throughout. At about3.3GiB available, root released only verified idle own previous-boundary daemon99678 after exact tmp/PID/idle-log verification; no worker/shared/active process was stopped. Current root tmp/daemon reused across integration checks. No new infrastructure/account/billing changes.

Next current work is the reviewed stored-inventory→quantity boundary, tracked by the existing collection/pricing campaign tasks and current root task. See coordination/tasks/orch-android-v1-campaign-01.md for the live action; this report is immutable scoped evidence, not a competing queue.

## Shared primitive transaction prerequisite, 2026-10-15

Root extracted the existing fresh primitive APPLY body into internal DeckPrimitiveEdits. Public adapter keeps validation/codec/fingerprint/replay/epoch admission and receipt in its original single transaction; helper keeps current/CAS/graph/package/undo-state/empty/max-revision/clock/reducer/encoding/revision/head/undo-link order. Undo unchanged. Helper never opens transaction or writes receipt. Actual independent Astra design/source-equivalence ACK and separate Astra runtime readback ACK. Focused existing SqlLocalDeckEditsTest SUCCESS41s,29tests1XML,0failures/errors/skips; no retry/new fixture or new public/schema/draft-implementation claim. Adapter SHA c985810ef2e80446faf45cfe1cb61c80e5690a4da80a6aa6e5326c14b24fc24f, helper9b387360ebcbdbf89ed7fc876918659c54aa1029aefa8ce11a4f0385232b1bcf. Evidence /tmp/cl-deck-primitive-extraction-01/evidence/attempt1.log, attempt1-source.sha256, attempt1-counts.json and attempt1-xml. Future draft adapter still requires its separate exact-contract/schema/durability review.
