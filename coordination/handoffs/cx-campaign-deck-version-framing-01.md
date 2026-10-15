# Named-version framing — scoped worker handoff

## Latest continuation delta

Complete on `agent/campaign-deck-version-framing-20261015`; exact dispatch/implementation base `3afad036e3a13c8f655780c06518133667317009` (accepted compiled/source-ACK b2ac8de plus root claim). The single commit containing this document is the implementation/final handoff; exact SHA returned to root. Root alone integrates and owns schema/backend/DI/evidence acceptance. No remote push/merge/auth change; publication barrier remains root-owned, local canonical claim remains authority.

Before edits independently verified pwd/repository `/workspace/command-library-deck-version-framing-01`, exact branch/base above, common Git directory `/workspace/Command-Library/.git`, clean status; no Git directory/worktree/index overrides, only GIT_PAGER=cat; core.bare=false, no worktree/include configuration override. Read AGENTS/CONTINUE, self-contained packet, accepted named-version design/retained independent oracle JSON, exact summary model and shared request source. Exactly three NEW allowed files: DeckVersionFraming.kt, matching test, this handoff. Existing requests/stores/core/schema/build/global coordination unchanged. No subagents/network/push/merge/prohibited Git/process actions.

Complete internal `DeckVersionFraming` implements every accepted signature: raw defensive32-byte `scope(DeckId)`, lowercase64hex `metadata(summary,creatingSequence)`, exact shared CAPTURE/RESTORE `requestFingerprint(summary)`, and canonical scoped cursor encode/decode with `Cursor(epoch,afterSequence)`. No DB/repository/Clock/IDsource/placeholder/Outcome abstraction. Every field prebound to1024UTF16 BEFORE any UTF8 encoding/hash/string equality, then shared strict nonblankUTF8<=1024bytes. Explicit USER0/RECOVERY1 tags, positive creating sequence, USERtargetNULL, RECOVERYtargetnonnull/sourceRevision<Long.MAX, no self predecessor/target. Exact labels preserved. Request delegates unchanged DeckLibraryRequests.capture/restore using sourceRevision and exact label/target, excluding generated version IDs/time/predecessor.

Exact big-endian layouts from accepted design: F(s)=u32byte length+strict nonblankUTF8<=1024; N(s)=u8(0) oru8(1)+F(s). CLVD+u16(1)+F(provider)+F(deck), SHA256 raw32. CLNV+u16(1)+F(versionId)+F(provider)+F(deck)+F(label)+i64(createdSeconds)+i32(createdNanos)+i64(sourceRevision)+N(predecessor)+u8(kind USER0/RECOVERY1)+N(target)+i64(creatingSequence), SHA256 lowercasehex64. CLVC+u16(1)+i64(epoch)+i64(afterSequence)+scope32, exactly54bytes/canonicalunpaddedbase64url72chars; epoch>=0 and0<after<=epoch. Decoder bounds exact raw length BEFORE alphabet/decode, checks exact magic/version/length/canonical reencode/ranges/deck scope before exposing cursor.

Caller limitations: metadata-version admission/future UNSUPPORTED, receipt/request/result/metadata associations, recomputed historical payload/hash/graph integrity, predecessor/target existence/order, current receipt epoch comparison and indexed cursor anchor remain SQL caller-owned. Metadata digest is consistency data, not authenticity or storage certification. No recursive history validation, raw-ID-only scope, label normalization, encoded payload or generated time/ID included in creating CLDR. Invalid inputs throw IllegalArgumentException or shared CharacterCodingException, caller maps via existing checked context. Small bounded pure work; no cancellation/benchmark/heap-latency claim.

Independent fixed-oracle provenance: all six literals copied exactly from retained `docs/verification/deck-codec/named-version-proposed-v1-oracles.json`, generated independently by Python before this implementation (historical filename/status preserved; accepted design selects these layouts). Standard-library Python independently checked literal hex/length/SHA256 and canonical cursor token BEFORE production gate; `independent-oracle-check.json` SHA256 `27f1a5cb67d77980669c632b8de151228d1bd57c5b84577a35cface110dd62fc`. No expected bytes/hashes generated from production output, no fixture regeneration. Existing CLDR0..3 and new accepted4/5 source/goldens unchanged.

Actual root-granted exclusive Gradle run, source frozen and reverified, slot released promptly; used --no-daemon per root resource instruction, no daemon/process kill. Existing pinned JAVA_HOME `/workspace/.command-library-env/jdk-17.0.16+8`, GRADLE_USER_HOME `/workspace/.command-library-env/gradle`, ANDROID_HOME `/workspace/.command-library-env/android-sdk`, inherited JAVA_TOOL_OPTIONS preserved and own tmp appended last:

```sh
TMPDIR=/tmp/cl-deck-version-framing-01/tmp \
JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Djava.io.tmpdir=/tmp/cl-deck-version-framing-01/tmp" \
ANDROID_HOME=/workspace/.command-library-env/android-sdk \
bash android-app/gradlew -p android-app --offline --console=plain --max-workers=2 --no-daemon :feature:decks:test
```

First/only attempt SUCCESS2m13s,9tasks executed. Actual74tests/8XML,0failures/errors/skips: DeckDocumentDiff8, DeckEntryDiff5, DeckPatchReducer14, DeckSnapshotValidator22, DeckEditRequest2, DeckLibraryRequests5, DeckVersionFraming15, DeckVersionRequest3. No compiler/test failure, retry or post-test source change. Initial informal authored-count16 corrected to actual15 before runtime; executed counts are parsed XML, not estimates. `git diff --check` passed. Independent actual Astra source ACK received for exact final two-source pins and all six fixed oracles; runtime readback remains root-owned.

Evidence `/tmp/cl-deck-version-framing-01/evidence/`: `focused-attempt1.log` SHA256 `a0dd9ebed21515c93c4c5ce57e859a6f2b6399a9713b463515a6d17c0d58e965`; `attempt1-counts.json` SHA256 `9969dc29a147c8bfd35817ad7caf74c5f02c88547bf7b340e065c37a21b98aa4`; all74 actual testcase names/results in `attempt1-testcases.json` SHA256 `2807687e266fdc1e3e85e09f1d48d34ae56d6e06a94b77e2186e0549238d8937`; original XML copied to `attempt1-xml/`. Source manifest `attempt1-sources.sha256`:

```text
c3f21babeed05fc5ccbb5bdb3588942cd96de872b2605848473f9f7bb6528800  android-app/feature/decks/src/main/kotlin/com/architectpsg/commandlibrary/feature/decks/persistence/DeckVersionFraming.kt
a5c7c7bb8228aeb4f97a4ad7b94a6a6d471f76e4ac73ff341814f4b2cd44ba33  android-app/feature/decks/src/test/kotlin/com/architectpsg/commandlibrary/feature/decks/persistence/DeckVersionFramingTest.kt
```

New15 tests cover all six oracle preimages/hash lengths, both metadata kinds and explicit tags, raw scope/defensive bytes, fixed/shared CLDR4/5 fingerprints, cursor byte/token golden and canonical roundtrip, source qualification/framed-identity separation, every metadata field/sequence and target influence, excluded generated request fields, shape/self/null/MAX constraints, exact Unicode/byte boundaries, malformed/oversized/blank fields, no trim, fixed cursor alphabet/padding/length/trailing/magic/version/scope/ranges/MAX. No claim from unexecuted tests.

Unrun/scope: pure JVM only. No storage/migration/Room/AppGraph/version repository/UI/native/Fold7/process-kill/performance/finished APK verification or implementation; no wire/schema redefinition/new dependency. Root schema14 and other S11 writer remain disjoint. Root must complete independent runtime readback, inspect/cherry-pick ONLY this three-file commit, then assembled actual backend gates. No open worker finding; no main merge/release authority.
