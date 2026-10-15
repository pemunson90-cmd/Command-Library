# Application-owned plain deck list codec worker

## Latest continuation delta

Complete pure `PlainTextDeckListCodec : PlainDeckListCodec`, bounded streaming parser, deterministic loss-aware renderer and independent parser/renderer tests are implemented. Focused `:feature:transfer:test` attempt01 succeeded in 1m16s, exit0, 26 actual tests in 2 XML reports, 0 failures/errors/skips; all three reviewed source hashes unchanged. Sole Gradle slot released to root. No other build/runtime/native task requested. Independent actual Astra refreshed source ACK received before the gate; root subsequently relayed final actual source/runtime ACK for all three pins,26tests/2XML and archived hashes, all HOLDs released. Next: exact four-path scoped commit, root-owned candidate/diff review and integration. No public/shared/build/schema/registry/DI/navigation/coordination/spec changes.

## Isolation and lineage

Before authoring verified pwd/repository top `/workspace/command-library-plain-deck-codec-01`, branch `agent/campaign-plain-deck-codec-20261015`, exact dispatch base/HEAD `a6731fee4cb264ca3a013674b7a195ebb7b1ffac`, common Git directory `/workspace/Command-Library/.git`, clean status. No GIT_DIR/GIT_WORK_TREE/GIT_INDEX_FILE overrides, GIT_PAGER=cat, core.bare=false with no worktree/hooks/include overrides. Exact four new delegated paths absent. Original completed worker branches/worktrees preserved. No base refresh or inherited dependency commit needed.

Read full packet, authoritative final root dispositions and actual public codec/stream/deck/value contracts. Accepted source pins: PlainDeckListContracts0407c92a9849ed0d5cccf93a1d3f6e3aa7c5e0b9934ce77742e89ab6c9be0fab; module3af9c51fb69925f0abf68797b45a7841d2018177e0758e0ff268a08d0b9001c2; settings9a92a2c9951b10a48c22a00e828f0a23453232f01a82f551c3d559925749b745. Module's earlier NO-SOURCE compile was not codec implementation evidence. Root owns canonical claim, accepted public types and integration. No children, network acquisition, publication, push, auth changes, reset/clean/stash/merge or other-process termination performed.

Exact four new allowed paths, no existing paths changed:

- android-app/feature/transfer/src/main/kotlin/com/architectpsg/commandlibrary/feature/transfer/PlainTextDeckListCodec.kt
- android-app/feature/transfer/src/test/kotlin/com/architectpsg/commandlibrary/feature/transfer/PlainTextDeckListParserTest.kt
- android-app/feature/transfer/src/test/kotlin/com/architectpsg/commandlibrary/feature/transfer/PlainTextDeckListRendererTest.kt
- coordination/handoffs/cx-campaign-plain-deck-codec-01.md

All four before-state values are ABSENT at dispatch base. Final three production/test SHA256 values:

- PlainTextDeckListCodec.kt: d218d8b101da3d26f8573c3f0d77fafe4335d252b4c1d98ea2f20cdf0b59ab65
- PlainTextDeckListParserTest.kt: 3889e9b66026fcc6d52a627e6c741caf83549bfe353ff8c4cdbfd705aff0309a
- PlainTextDeckListRendererTest.kt: bcfdc10efb790997599f44d487e214fa4f1489da7b60742b86d9025dba169924

Final commit/head/parent, clean status and handoff hash will be read back into external `final-return.json` under own evidence after commit (the handoff cannot embed its own final hash).

## Implementation and admission scope

Parser uses invocation-local state and manual strict UTF-8 decoding across arbitrary legal chunk partitions. It strips one BOM only at original byte0, counts physical offsets in original bytes, accepts LF/CRLF, rejects lone CR/malformed Unicode, retains blank/header/error rows, and emits no phantom final row. Initial MAIN and seven exact application headings are supported. Quantity is canonical positive ASCII Int, separator is maximal ASCII spaces/tabs, full unresolved name including punctuation/trailing whitespace survives. Marker family recognition is the exact untrimmed `Command Library Deck List` prefix. Row issues use static descriptions/resolution and actual row locations; ordinary errors retain rows and allow full bounded consumption.

Stream sequence/offset/final/empty-frame/normal-completion/known-length checks are explicit, size is checked before `SnapshotBytes.copyBytes`, mediaType/metadata/Unknown or Absent expected-length reasons/evidence have no authority. Upstream read Exceptions are sanitized TRANSPORT, cancellation propagates, fatal JVM Errors are not caught. Invalid framing/UTF-8/admission has no partial document. After a canonical first-line future version, body is opaque to v1 syntax/name/entry admission: global bytes, strict UTF-8, line bytes, physical-line count, stream framing and normal completion still apply before UNSUPPORTED. Trailing transport failure wins over unsupported version.

Renderer admits actual DeckSnapshot entries only: refuses missing/Unknown/Absent/blank/leading ASCII-space-tab/multiline/malformed/oversized originalName and duplicate entry IDs. Qualified identity/name resolution is not attempted. Retained input ID and loss-attribution entry IDs are strict UTF-8 <=1024 bytes. Omitted deck/identity/catalog/package/evidence graphs are not recursively validated. Names retain all admitted content, including duplicate names and header-like names.

Preflight bounds entry count, per-name bytes and total encoded output before encoding. Seven fixed stable board buckets preserve within-board order. ENTRY_ORDER disclosure is equivalent to a decrease in fixed board rank in the original sequence, with unique entry IDs already admitted. Losses follow exact declared deck field order, then original entry order/exact per-entry field order; omitted Knowledge fields always disclose loss including Unknown/Absent reasons/evidence. Nonnull empty categoryOrder discloses loss. Populated omitted strings/collections are checked only for emptiness/null, not traversed. Maximum losses16+9*10000=90016.

Explicit scans charge Work.visit per input byte, scanned character/code unit, row/entry/loss/index candidate or immutable-list iterator element, polling every1024 charged visits, plus phase checks and final check after immutable output construction. Encoding charges each scanned code unit/scalar (one scalar emits1–4 bytes); this is source-level cooperative polling rather than a hard physical byte-latency guarantee. ID digest construction and exact collision comparison are explicitly polled; no String/list hash or equality is accepted as identity proof. SnapshotList.copyOf receives a non-Collection Iterable whose next() is charged. Existing SnapshotBytes copies, bounded StringBuilder/string copies, JVM array allocation/reference moves and ByteArrayOutputStream native growth/copies are not physically interruptible; their sizes are bounded by admitted input/output caps. JVM tests do not establish hard physical cancellation latency, throughput or measured performance.

## Verification and retained review lineage

All evidence lives under `/tmp/cl-plain-deck-codec-01/evidence`, temp under `/tmp/cl-plain-deck-codec-01/tmp`.

Initial prebuild source freeze: `attempt01-source.json` (productiond2bc5036/parser23c40f3a/rendererbcfdc10e), 25 prepared tests, no build. Independent source review clarified that a canonical future marker makes body opaque to v1 grammar; original implementation incorrectly applied v1 name/entry caps. Before any build, repaired physical-line accounting and future-body skip, added explicit oversized-name/entry-count opaque-body tests and trailing transport/global-bound precedence. Original pins and explanation retained in `attempt01-prebuild-lineage.txt`; refreshed `attempt01r1-source.json` holds final three pins and26 prepared tests. Root relayed actual source ACK/all HOLDs resolved before build grant. No failed compile or runtime attempt occurred.

Actual only build invocation: `./gradlew :feature:transfer:test --offline --no-daemon --console=plain --max-workers=2`, root-granted sole slot, pinned JAVA_HOME `/workspace/.command-library-env/jdk-17.0.16+8`, ANDROID_HOME `/workspace/.command-library-env/android-sdk`, GRADLE_USER_HOME `/workspace/.command-library-env/gradle`, own TMPDIR and appended own java.io.tmpdir preserving inherited truststore options. Single-use daemon exits normally under --no-daemon; no shared daemon stopped. Wrapper session72266 exited0; BUILD SUCCESSFUL in1m16s,6 actionable tasks executed, including actual implementation/test compilation and test runtime.

Archived under `attempt01/`: exactcommand, java-version, exitcode, Gradle log, original test-results XML/binary outputs, HTML report and `summary.json` with source/hash readback and actual counts. Log SHA256 f7a79d242135d823d7630ed0ad8ff557cac2baf0a49af7dbb1b18b08b60cfa25.

Actual XML:

- PlainTextDeckListParserTest:14 tests,0 failures/errors/skips; XML SHA2562c396ca95ff18920f53e6ba0118416a8c24e248f3a833efc94049976e55aa69d.
- PlainTextDeckListRendererTest:12 tests,0 failures/errors/skips; XML SHA25622814c216ab447cc4dbd7aed30b5cd7a646f6ffbd06664d54bc30a928a36779a.
- Total26 tests in2 XML,0 failures/errors/skips. Source hashes match reviewed freeze after exit. No runtime counts inferred from annotations.

Independent expected objects check physical byte offsets/BOM/terminator/blank handling; every legal partition of representative BOM+UTF8+CRLF; all boards and exact headings; canonical/overflow/invalid quantities; unknown/malformed/misplaced/repeated markers and future opaque body; strict invalid UTF-8/lone CR; stream violations/wrong known length/postfinal/trailing errors; cancellation/fatal error propagation; all parser caps exact/+1. Renderer independent byte goldens and typed loss lists cover all seven boards, punctuation/header-like names/duplicate same-names/unique IDs and collision-shaped IDs, stable grouping, rich all25 fields, nonnull empty category registry and Unknown/Absent identity evidence, omitted malformed/oversized metadata preservation by loss rather than recursive admission, exact10000 entries/90016 losses/8MiB output and cancellation. Generated parse(render(snapshot)) conservation is supplemental, not the only oracle. Source and input snapshots are read-only; no ports/persistence writes.

## Limits and next ownership

This establishes only the bounded pure application-owned text projection. No authentic vendor exports/provenance, vendor grammar compatibility, catalog lookup/resolution, real import UI, identity readiness, storage/atomic apply/undo, native backup I01, aggregate TransferService binding, full feature delivery, app assembly, device/emulator or finished APK acceptance. No new dependencies/pins/modules, no native/broad suite/lint run requested. Root retains shared integration, independent final review and merge/release authority. Continue from exact committed readback in external final-return.json; do not refresh base, modify shared files or claim unrun scopes.
