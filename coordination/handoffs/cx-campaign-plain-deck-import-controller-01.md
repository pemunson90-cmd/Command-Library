# Plain deck import retained controller — attempt01

## Latest continuation delta

Complete controller/state/29-test source independently source-ACKed by `campaign_engineering_review`, relayed by root; all three source pins unchanged. Runtime/compiler/build/lint **NOT_RUN**, as required by the packet. Root must integrate exact owned paths, bind accepted plain codec/library/graph validator and later file/paste/UI consumers, then execute its serialized gate. No UI/AppGraph/Room/native/codec integration acceptance is claimed.

## Isolation and ownership

Verified before edits: cwd/toplevel `/workspace/command-library-plain-deck-import-controller-01`, branch `agent/campaign-plain-deck-import-controller-20261015`, exact base `65bffe6f9a471275c71e8c02ec35082e5eabae1e`, common Git dir `/workspace/Command-Library/.git`, clean. Only Git environment variable `GIT_PAGER=cat`; no directory/index/worktree overrides. `core.bare=false`, no `core.worktree` or include configuration. Canonical root TAKEN packet/claim `fb3deb3` authorizes exactly four NEW paths:

- `android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/PlainDeckImportController.kt`
- same directory `PlainDeckImportState.kt`
- `android-app/feature/shell/src/test/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/PlainDeckImportControllerTest.kt`
- `coordination/handoffs/cx-campaign-plain-deck-import-controller-01.md`

All existing contracts/source/schema/resources/build/DI/UI/global coordination remained read-only. Previous worktrees/commits retained. No children, builds/compiler/lint, network, push/auth changes, resets/history rewrites or process/lock actions. Own temp/evidence: `/tmp/cl-plain-deck-import-controller-01/{tmp,evidence}`. Mark-owned UI is not a dependency.

## Frozen consumer API

Constructor exactly takes `scope: CoroutineScope, codec: PlainDeckListCodec, decks: LocalDeckLibrary, dispatcher: CoroutineDispatcher, nextEntityId: () -> EntityId, now: () -> UtcTimestamp, validate: (DeckSnapshot, CancelToken) -> Outcome<Unit>, onCommitted: (DeckSnapshot) -> Unit`.

Commands: `parse(TransferInput)`, `name(String)`, `preview()`, `confirm(expected: PlainDeckImportPreview)`, `retry()`, `close()`, `resume()`, `cancelParse()`, `discardInput()`. Read-only `state: StateFlow<PlainDeckImportState>` exposes stable formTicket, visibility, inputName, full `input: PlainDeckList?`, raw deck name, parsing/previewing, immutable preview, submitting/uncertain/awaitingFresh, typed Problem, acknowledgedId and separately admitted currentHead. `PlainDeckImportPreview(input: PlainDeckList, name: String)` contains no generated IDs/time. No UI caller exists yet.

Explicit parse starts a new formTicket, preserves the raw deck name, cancels/supersedes old parse/preview jobs and clears old preview authority. Name changes invalidate/cancel preview but do not consume or restart a parsing stream. Preview is explicit asynchronous bounded validation without IDs/time/write. Confirm requires visible current state and exact object identity `expected === state.preview` before construction; copied or obsolete equal previews cannot authorize creation. Close only hides. Resume only shows retained state and does no I/O. Successful admission clears retained input/preview and advances formTicket, keeping acknowledgedId and admitted currentHead. Explicit discard retires only unsubmitted input. No TransferInput/flow is retained for retry; consumed streams require caller-supplied new explicit parse, with opening/closure owned by future adapters.

## Admission / durable uncertainty

Parse delegates once to the injected accepted codec and admits only matching input ID and bounded typed physical rows; it does not infer readiness from complete. All headings/blanks/card/error rows, raw text, location and issues remain the exact returned immutable snapshot, without row filtering/truncation. Defensive checks cover row/entry/line/aggregate ceilings, physical location order/source, valid Unicode and positive quantity, typed name/raw quantity/name/board consistency, recognized structural rows, bounded static issue shape/code. Separate coarse raw/name/issue UTF16 budgets run before repeated body scans; strict UTF8 sizing polls every 1024 visits and row/model construction checks cancellation per row. No wire parsing, stream recollection, identity resolver or full package/category validator was introduced.

Creation requires complete input, no ERROR issues, at least one card, and an exact explicit nonblank valid-Unicode name within the local name byte limit. Each card physical row becomes one ordered entry, preserving duplicate names, quantity/name/board. Oracle/printing remain Knowledge.Unknown with static reasons/empty evidence, raw identity null, originalName exact Known, language `und` (unspecified), physical finish Unknown, empty notes/labels/overrides. Deck metadata uses existing honest empty create defaults, local identity/revision0, equal injected created/modified time, explicit empty category registry, no invented format/folder/budget/profile/catalog/packages. Future UI must disclose unresolved identity and unspecified language.

Only explicit confirmation allocates deck/entry/operation IDs and time. All generated IDs are strict valid/bounded and mutually unique before any write. Checks run on the dispatcher; an already-cancelled owner never starts generation. Snapshot construction is cancellation-aware; bounded native list copies are not claimed interruptible. The injected existing graph validator must succeed before freezing/first create. Its immutable validated request is reused on retries; no IDs/time or snapshot regeneration. Structural graph validation does not certify CLDK encoded size: storage refusal retains the entire input/preview and typed problem; no trimming is attempted.

Exact create ACK must equal the entire frozen snapshot. Ambiguous result, exception, cancellation or mismatched ACK retains the exact request; a later negative response cannot erase prior uncertainty. Initial definitive refusal may return the full editable preview. While request/build/readback is retained, name/new parse/discard are blocked. Valid ACK is recorded separately before readback; retry then only loads its identity and never creates again. Current head must retain ID/createdAt, have revision/time at least the ACK, pass bounded scalar checks and the injected graph validator. Revision0 requires entire equality with historical ACK; strictly later content may differ. Read failure/malformed head retains read-only recovery and does not publish currentHead or notify. Authority is cleared before the exactly-once accepted-read callback, so callback failure cannot cause another create/notification. Finalizers are fenced by formTicket to avoid clobbering a reentrant newer form.

## Authored verification scope

29 authored focused `@Test` methods use the real controller over explicitly scripted immutable codec results and a faithful library port with permanent exact create receipts separate from mutable heads. The fixture fully consumes a single-use input flow once, but is not a replacement codec or real SQLite adapter. Complete expected imported snapshots are independently specified, without deriving them from the produced snapshot or calling a reducer.

Coverage includes all seven boards/physical order/duplicate names/exact unresolved metadata, error-row preservation/no subset, incomplete/no-card/false-complete inputs, typed shape/location/name/line/entry/aggregate bounds, input-ID mismatch, cancelled late parse and fresh stream ownership, hide vs cancel, exact preview-reference authority, invalid and exact-boundary Unicode names, generated ID collisions/malformed/big IDs, injected validator refusal, cancelled owner and mid-construction cancellation, hide/block edits during submitted create, unknown ACK plus later negative response, before/after-effect cancellation, thrown create, initial definitive refusal, full ACK component mismatch, read-only known-ACK retry, revision-zero content/time equality, changed ID/creation/time, loaded graph rejection then legitimate later head, cancelled known read, callback failure and explicit discard. Since create ACK is necessarily revision0, a lower nonnegative Revision cannot exist; readback tests instead exercise revision0 changed-content and time/creation regressions without fabricating a negative Revision.

Static readback/whitespace checks pass for the three source files. No test/compiler/build/lint command was run and no runtime failure/pass claim is made. Existing accepted codec/graph/SQL tests are dependencies, not executed evidence for this controller. Actual UI/AppGraph/Room/native/Fold7/vendor/export/CSV/full-restore acceptance remains separate.

## Frozen pins and review

- Controller SHA-256 `a7ded4cf5b0bd8e7af7ecb4eac7da754ed9670249bf136764ffe9c045336e925`.
- State SHA-256 `e8f568c1779ff229a0ea21a85e5d0e675a9975541240d48efafee2696642d195`.
- Test SHA-256 `9c498d54f1f5cdac558e723074ad0fd0ffa6501ff0a05ec9ce1e9f8bf67da5cb`.
- Independent actual source review: root relayed `campaign_engineering_review` ACK for all four frozen pins, no HOLD. Reviewed handoff SHA-256 `730fa30953d699026a754261468259551283912fa218956eba081ec68cd539bf`; final handoff changes ACK/continuation status only. Controller/state/test pins unchanged. No implementation HOLD/runtime lineage exists. The canonical packet records earlier design-only READY repair for graph seam, revision-zero/createdAt admission and exact preview authority; those were requirements before this implementation, not executed failures.

## Recovery / next action

Commit only the four NEW paths over the accepted base; return full commit/parent/clean state and final manifest. Root verifies the scoped return, integrates with accepted actual codec/library and graph validator, and executes the serialized focused controller/codec/SQL or eventual actual AppGraph/UI gate. Root reports its disjoint real binding/input-factory source integrated as `1175ae5`, also source-only; that is not an inherited worker dependency or runtime result. Diagnose failures before any changed retry. Root retains all existing/shared/DI/adapters/UI/coordination/final integration authority. Runtime remains **NOT_RUN** until actual gate evidence exists.
