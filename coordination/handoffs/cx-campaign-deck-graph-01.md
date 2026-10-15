# Deck structural graph validator — scoped worker handoff

## Latest continuation delta

Complete on `agent/campaign-deck-graph-20261015`; exact dispatch/implementation base `f0373af55a0e7960b33995fafc0aa00aa223e918`. Implementation and handoff are the single commit containing this document (exact SHA returned to root). Root independently source/runtime ACKed final production/test hashes and final56-test XML/log evidence. Root alone integrates and runs assembled storage/Room/consumer gate; no push/merge performed.

Before edits independently verified pwd/repository `/workspace/command-library-deck-graph-01`, exact branch/base above, common Git directory `/workspace/Command-Library/.git`, clean status; no Git directory/worktree/index overrides, only GIT_PAGER=cat; core.bare=false, no worktree/include configuration override. Read only AGENTS/CONTINUE, self-contained task packet and its named model/limits/reducer/test context. Exactly three owned files: new `DeckSnapshotValidator.kt`, matching new `DeckSnapshotValidatorTest.kt`, this handoff. ALL other files including DeckPatchReducer/core/persistence/schema/build/DI/UI/shared coordination remain unchanged. No children/network/push/merge/prohibited Git/process actions.

Public pure `DeckSnapshotValidator.validate(deck,cancel):Outcome<Unit>` now validates the original whole package graph. Complete bounded preflight finishes before reducer/maps/hash/equality work: entries<=10000, explicit categories<=1024, each visited labels/overrides/instances/contributions list<=10000, total visited list elements<=100000; graph IDs<=1024 UTF16 units before strict<=1024UTF8 bytes; compared names<=1MiB with strict UTF8 sizing. Examined string UTF16 sum<=8MiB conservatively. Streaming Unicode/byte checks avoid encoded-string allocation and poll using a threshold so two-unit surrogate strides cannot skip cancellation polls. No unrelated nested metadata traversal/whole-payload copy/encoding or full wire validation certificate.

Existing reducer, unchanged, checks base semantics on a TEMPORARY copy with packageInstances empty and empty patch/current revision/current modified time. This remains an explicitly bounded NON-cancellable substep, with cancellation checked before/after; no <=1024-unit or hard-latency promise inside reducer/JVM operations. Original package graph is independently checked: unique instance IDs, bounded valid instance/package/version/contribution IDs, existing targets, positive quantities, unique(packageEntryId,deckEntryId) per instance, cross-instance Long totals<=actual entry quantity. Remaining-capacity check avoids Int overflow. Splits/many-to-one/repeated package versions/distinct instances/empty COPY or LINKED/zero and positive manual remainder are valid. Unknown/absent identity/evidence/foreign IDs/policies/order retained untouched; no registry/catalog/package-version authority. All graph/base failures become static user-text-free INVALID_INPUT; cancellation propagates unchanged. No replacement snapshot returned or package lineage stripped from stored/returned content.

Actual verification used root-granted exclusive Gradle slot, released after final run. Source frozen during each run; final hashes reverified. Existing pinned JAVA_HOME `/workspace/.command-library-env/jdk-17.0.16+8`, GRADLE_USER_HOME `/workspace/.command-library-env/gradle`, ANDROID_HOME `/workspace/.command-library-env/android-sdk`; inherited JAVA_TOOL_OPTIONS preserved, own tmp appended last:

```sh
TMPDIR=/tmp/cl-deck-graph-01/tmp \
JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Djava.io.tmpdir=/tmp/cl-deck-graph-01/tmp" \
ANDROID_HOME=/workspace/.command-library-env/android-sdk \
bash android-app/gradlew -p android-app --offline --console=plain --max-workers=2 :feature:decks:test
```

- Attempt1 SUCCESS1m8s,56tests/6XML0failure/error/skip;34existing+22new. Archived `attempt1-xml/`, counts/source hashes retained; log SHA256 `6005f31047e072fca4014365903f32371adc4c3dad772f6efda30b71dfb3cbe0`.
- Attempt2 final SUCCESS27s,56tests/6XML0failure/error/skip; 2tasks executed/7up-to-date. Only changed test was stronger midscan cancellation threshold50, requiring repeated polls inside long surrogate text rather than allowing cancellation at a later phase boundary. Production unchanged between runs. No compiler/test failure or weakened fixture. Initial execution-service stall resolved by resuming the SAME pending call/session exactly once, before either build; no duplicated write or build.
- Final suites: DeckDocumentDiff8, DeckEntryDiff5, DeckPatchReducer14, DeckSnapshotValidator22, DeckEditRequest2, DeckLibraryRequests5. `git diff --check` passed. Independent actual Astra source/runtime ACK received for exact final hashes and XML/log; reviewer ran no build.

Evidence `/tmp/cl-deck-graph-01/evidence/`: final `focused-attempt2.log` SHA256 `a0a64df93d6d57c3d4796aea436fbcefaf6f0ec16775346d6a87c64f4e2029c3`; `attempt2-counts.json` SHA256 `f8d83b9862a9414fd051d3b2ef22befe9be4435324ce23a729f66049c6195ba0`; complete56 actual testcase names/results in `attempt2-testcases.json` SHA256 `33df931fc25523ed44a271a25bc964e6c6d4cb6c93f5867970fdab6a09aa13cc`; archived original XML in `attempt2-xml/`. Source manifest `attempt2-sources.sha256`:

```text
707077f82447d5e555583ac08a0937582b9b78bb549f225e996068ebcee89459  android-app/feature/decks/src/main/kotlin/com/architectpsg/commandlibrary/feature/decks/DeckSnapshotValidator.kt
599eab5bda2dc45bebad572dbc66c18a198ce0321b6ae5b7b0b505ab3a13daa0  android-app/feature/decks/src/test/kotlin/com/architectpsg/commandlibrary/feature/decks/DeckSnapshotValidatorTest.kt
```

New tests exercise 2+3/5 vs2+4/5 across instances; Int.MAX overflow; splits/many-to-one; pair uniqueness within/across; duplicate instances/missing targets; repeated unresolved package/version IDs; empty LINKED/COPY; exact unknown-rich identity/evidence/order preservation; null versus explicit category registry; invalid base labels/overrides/time/name; every graph ID path and exact UTF8 boundary; count limits including valid10000-entry/split/instance,1024-category and100000-aggregate boundaries; compared-name and examined-UTF16 budgets; cancellation before/during long surrogate scan/late original graph traversal. Explicit test shows unexamined description/tags are NOT certified for CLDK representability; complete codec remains mandatory.

Unrun/limits: pure JVM only, no worker storage/Room/AppGraph/UI/native/emulator/Fold7/performance/finished APK evidence. Root retains full named-version storage draft, package update/removal policy, actual codec/storage/duplicate-remapping consumers and combined gate; no B05/B09/S11/full-version/package mutation acceptance or release authority. No worker open finding. Next root action: inspect/cherry-pick ONLY this three-file commit and run the assembled actual storage gate with retained consumer changes.
