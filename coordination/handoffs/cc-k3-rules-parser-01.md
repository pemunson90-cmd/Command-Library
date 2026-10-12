# Handoff cc-k3-rules-parser-01: private K3 rules TXT parser

Status: READY-FOR-REVIEW. Not accepted; root reviews and decides inclusion.
Owner: Claude Code (external-claude-code per `coordination/locks/rules.lock`). Model tier: strongest tier, no subagents (card rule).
Branch: `agent/cc-k3-rules-parser-01`. Base: `main` at `dd3abf7d2c3159d1e9b2553d5b1d2e32dfe86472` (contains the task card and `rules.lock`, verified before any write). Code commit: `796c665`; this handoff is the commit after it.
Owned paths written: `android-app/feature/rules/**`, this file. Nothing else; `git diff --name-only dd3abf7d2c3159d1e9b2553d5b1d2e32dfe86472...HEAD` lists only those.

## Changed files

- `android-app/feature/rules/build.gradle.kts`: Android library, compileSdk 37, minSdk 23, Java 17, explicit API, fatal NewApi lint; depends on `:core:model` (api), `:core:common`, coroutines; tests on JUnit 4 and coroutines-test. All through existing catalog aliases.
- `src/main/kotlin/.../feature/rules/parser/`: `RulesTextParser.kt` (two entry points), `Utf8LineReader.kt`, `RulesStructure.kt`, `RuleReferenceScanner.kt`, `RulesParseResult.kt` (result, failures, index, summary), `RulesParserLimits.kt`. Every declaration is `internal`; the module exposes no public API yet.
- `src/test/kotlin/.../feature/rules/parser/`: `SyntheticRules.kt` (labelled synthetic corpus, chunker, provenance with unknown version and dates), `RulesTextParserTest.kt` (27), `Utf8LineReaderTest.kt` (11), `RuleReferenceScannerTest.kt` (8).

## Verification actually run

In a throwaway copy of `android-app/` at this branch whose only difference is `":feature:rules"` added to `settings.gradle.kts` (JDK 17, Gradle wrapper 9.6.0, local SDK with platform 37.0):

- `./gradlew --no-daemon :feature:rules:check :feature:rules:assembleDebug`: BUILD SUCCESSFUL. JUnit reports: 46 tests, 0 failures, 0 errors, 0 skipped. Lint report: "No issues found" (an earlier run caught a literal byte-order-mark character in source; fixed by escaping, and all new files are ASCII-only).
- Same test count with chunk sizes 1, 3, 7, 64 and 4096 producing identical entries (inside the 46).

not_run: hosted CI for this module (the branch's `settings.gradle.kts` does not include it, so `android-k2.yml` runs on this branch only rebuild main's modules); any genuine Comprehensive Rules bytes; Android instrumentation (none needed for a pure parser); performance on a full corpus.

## Entry signature as built

```kotlin
internal class RulesTextParser(limits: RulesParserLimits = RulesParserLimits()) {
    suspend fun index(chunks: Flow<ByteChunk>, cancel: CancelToken): RulesParseResult<RulesIndex>
    suspend fun parse(
        chunks: Flow<ByteChunk>,
        index: RulesIndex,
        provenance: Provenance,
        corpusId: GenerationId,
        cancel: CancelToken,
        onEntry: suspend (RuleEntry) -> Unit,
    ): RulesParseResult<RulesParseSummary>
}
```

`RulesParseSummary` reports entries, entries by kind, references, unresolved references, duplicate identifiers, anomalies (count plus the first 20 with line numbers), byte count and SHA-256.

## Open grammar and limit questions for root (need genuine bytes)

1. Two passes versus one pass plus a reference update in SQL after staging. Two passes keep every emitted DTO final; the cost is reading the file twice.
2. Glossary definitions that contain a blank line would be read as a new term. R-03 did not establish whether that occurs.
3. Body section headings must match their Contents line exactly (after trailing whitespace). A real file where they differ would surface as anomalies and fold that section into the previous entry; the anomaly count makes this visible.
4. `Example:` must start at column 0. Indented examples would be continuation text.
5. Subrule letters accept any lowercase letter; the l/o skip is not validated.
6. Numeric ranges (`rules 100.1-100.4`) keep only their ends; only letter ranges on one rule expand.
7. Exact (raw target, identifier) duplicates within one entry are collapsed to the first occurrence.
8. Examples carry `number` Absent and encode their parent in the id (`<parent>#example-<n>`); root may prefer a parent field in a later contract.
9. Limits are proposals (table above). The card's genuine-fixture review should set them.

## Design

### Why two passes

`RuleCrossReference.resolved` is `Knowledge<RuleId>`. A single streaming pass can resolve only backward references; the CR refers forward constantly (for example rule 100 sections cite 702 keyword rules). Buffering the corpus to resolve later is ruled out by the card. So the parser reads the same bytes twice:

1. `index` pass: runs the full structure recognizer but keeps only the set of entry identifiers (bounded by `maxEntries`), the byte count and the SHA-256. No entry text is retained.
2. `parse` pass: runs the identical recognizer again, emits each `RuleEntry` in document order through a suspending callback with every reference resolved against the index, and fails with `SourceChanged` if the bytes differ from the index pass (byte count or SHA-256).

A local user-selected file can be opened twice; the repository that will own corpus activation (root, later) stages emitted entries and discards them on any failure. A failed result means every entry emitted in that pass must be discarded. Root may prefer a single pass with a post-pass reference update in SQL; that is a design question below.

### Entry points (internal to `:feature:rules`)

```kotlin
internal class RulesTextParser(limits: RulesParserLimits = RulesParserLimits()) {
    suspend fun index(chunks: Flow<ByteChunk>, cancel: CancelToken): RulesParseResult<RulesIndex>
    suspend fun parse(
        chunks: Flow<ByteChunk>,
        index: RulesIndex,
        provenance: Provenance,
        corpusId: GenerationId,
        cancel: CancelToken,
        onEntry: suspend (RuleEntry) -> Unit,
    ): RulesParseResult<RulesParseSummary>
}
```

`RulesParseResult` is `Ok(value)` or `Failed(RulesParseFailure)`. Failures are typed: malformed UTF-8 (byte offset), noncontiguous or repeated chunk, missing final chunk (truncated), each limit exceeded (limit name, limit, observed), structure errors (Contents without a body, duplicate structural headings that cannot be ordered), and `SourceChanged`. `CancellationException` is never converted; `cancel.throwIfCancelled()` and coroutine cancellation are checked per chunk and every 256 lines.

The caller supplies `provenance` and `corpusId` and they are copied unchanged into every entry. The parser never derives a source version, provider, source date or effective date. The CR states an effective date in its front matter; the parser keeps that line only as raw NOTICE text.

### Input decoding

Strict UTF-8 (`CodingErrorAction.REPORT`), multi-byte sequences may span chunks. A leading BOM is removed. Line terminators `\r\n`, `\n` and lone `\r` all end a line; entry text joins its lines with `\n`. Line content is otherwise exact. Chunk contract is the K2 one used by the catalog framer: `sequence` and `offset` contiguous from 0, exactly one `final = true` chunk last; an empty file is one empty final chunk.

### Proposed limits (parser safeguards, not corpus facts; root reviews against genuine bytes)

| Limit | Default | Reason |
|---|---|---|
| `maxInputBytes` | 8 MiB | Current CR TXT is expected near 1 MiB (unverified); 8 MiB matches the K2 import ceiling. |
| `maxLineBytes` | 16 KiB | Longest CR paragraph lines are expected well under this. |
| `maxEntries` | 20,000 | Rules, subrules, examples, glossary and notices together are expected in the low thousands. |
| `maxEntryBytes` | 64 KiB | One entry's text including continuation lines. |
| `maxContinuationLines` | 512 | Lines appended to one entry (continuation depth). |
| `maxReferencesPerEntry` | 256 | Cross-references emitted for one entry. |
| `maxRangeExpansion` | 26 | Targets produced by one letter range such as `702.19b-d`. |

### Grammar (from R-03 observations; unverified against genuine bytes)

Regions in order: front matter, optional `Contents`, body, `Glossary`, `Credits`.

- Front matter: blank-line separated blocks before a standalone `Contents` line (or before the body when there is no Contents). Each block is one NOTICE entry.
- Contents: from the standalone `Contents` line until the first section heading listed in Contents appears again. Emitted as one NOTICE entry titled `Contents` with its exact lines; its headings are not emitted as rule entries, so body headings are the only owners of their identifiers. This is the deterministic duplicate-TOC rule: nothing is dropped, nothing is duplicated.
- Body section heading `^[1-9]\. \S`: accepted only as the next section number in sequence (and, when Contents exists, only if Contents listed it); otherwise the line is continuation text and an anomaly is counted.
- Chapter heading `^\d{3}\. \S` whose first digit is the current section and number greater than the previous chapter.
- Rule `^\d{3}\.\d+\. ` and subrule `^\d{3}\.\d+[a-z]\.? `, accepted only inside the matching chapter (and rule for subrules). Letters are not validated against the l/o skip; any lowercase letter is accepted.
- `Example:` at line start begins an EXAMPLE entry attached to the current rule or subrule.
- Any other non-blank line is continuation text of the current entry; blank lines inside an entry are kept, leading and trailing blank lines are trimmed.
- A body heading identifier seen twice keeps both entries: the second gets a disambiguated id (`<id>~2`), its `number` stays the source number, and the summary counts the duplicate.
- `Glossary` (standalone, after the body started): blank-line separated blocks, first line is the term, remaining lines the definition. Duplicate terms are disambiguated the same way.
- `Credits` (standalone, after the body started): blank-line blocks, each one NOTICE entry.

### Entry mapping

| Kind | `id.raw` | `number` | `title` | `text` |
|---|---|---|---|---|
| NUMBERED section | `1` | Known `1` | Known heading text | exact heading line plus continuation |
| NUMBERED chapter | `100` | Known `100` | Known heading text | same |
| NUMBERED rule / subrule | `100.1`, `100.1a` | Known | Absent (no title in source) | exact lines |
| EXAMPLE | `<parent id>#example-<n>` | Absent | Absent | exact lines starting `Example:` |
| GLOSSARY | `glossary:<term>` | Absent | Known term | exact block |
| NOTICE | `front:<n>`, `contents`, `credits:<n>` | Absent | Known `Contents` for contents, otherwise Absent | exact block |

`RuleId.source` is `provenance.provider`. `ordinal` is the 0-based emission order.

### Cross-references

Only contextual references: `rule`, `rules`, `section`, `sections` (case-insensitive) followed by one or more targets separated by `,`, `and`, `or`, `through`, `to`, `-`, en dash U+2013, em dash U+2014. Targets are `\d{3}(\.\d+[a-z]?)?` or a single digit section after `section`. Letter ranges (`702.19b-d`, `702.19b-702.19d`) expand to each letter up to `maxRangeExpansion`. Each produced reference keeps `rawTarget` as the exact matched source text; `resolved` is Known when the identifier is in the index, otherwise Unknown with reason "Target not found in this corpus" and one Evidence naming the target. Bare decimal numbers are never linked.

## Synthetic test plan (all inputs labelled synthetic in test names and comments)

Four entry kinds; section/chapter/rule/subrule boundaries; title extraction; continuation and blank-line handling; Contents duplicate headings not duplicated and Contents preserved as NOTICE; duplicate body identifier and duplicate glossary term kept and disambiguated; known and unknown references, lists, ranges, `section N`; forward reference resolved through the index; provenance and unknown effective date copied unchanged; every line ending style; BOM; multi-byte UTF-8 split across chunks; malformed UTF-8 with offset; empty input; truncated (no final chunk); noncontiguous chunks; each limit; `SourceChanged`; cancellation through the token and through coroutine cancellation with no partial success.

## Shared-file requests for root

1. `android-app/settings.gradle.kts`: add `":feature:rules"` to the include list when root decides to build it in CI. Until then the module is built in a throwaway root by this worker (recorded below).
2. No new version-catalog entries: the module uses `libs.plugins.android.library`, `project(":core:model")`, `project(":core:common")`, `libs.coroutines.core`, `libs.junit`, `libs.coroutines.test`.

## Notes for root from the K2 shell review (not in this task's scope; no code changed)

A fresh-context multi-lens review of `agent/cc-k2-shell-01` ran under this Claude Code session (the shell's author session, so not independent). It was cut short by a usage limit: verifiers did not run. Claude Code then checked these three against current `main` by reading the code:

1. **Shell import leaks the document stream when cancelled during open.** `ImportController.open()` returns `withContext(ioDispatcher) { documents.open(ref) }`. If Cancel or ViewModel clear lands while a slow provider is opening the file, `withContext` throws `CancellationException` after the block returns and the `InputStream` is dropped unclosed; the `finally` that closes streams starts after `open()` returns. Fix: hold the opened stream in an atomic reference set inside the block and close it in the cancellation path. Reported independently by four review lenses.
2. **Catalog cleanup can delete the active generation's name index.** In `LocalCatalog`, `staging = false` is set only after the final activation transaction returns. `StorageDatabase.transaction` commits inside Room's non-cancellable section and then returns through `withContext`, which throws `CancellationException` if the job was cancelled meanwhile (the storage code's own comment at its line 76 says a post-commit cancellation cannot undo the commit). `LocalCatalog` then still sees `staging = true` and runs `cleanup`, whose first statement `DELETE FROM catalog_name_fts WHERE generation_id=:id` has no status guard. The generation stays ACTIVE (the second delete is guarded) but its FTS rows are gone, so every search returns no results until the next import. Fix: guard the FTS delete with `AND EXISTS (SELECT 1 FROM catalog_generation WHERE generation_id=:id AND status='STAGING')`, or re-read status before cleanup.
3. **Status bar icons ignore the in-app theme.** `MainActivity` calls `enableEdgeToEdge()` with the default `SystemBarStyle.auto`, which follows the system night mode, while the shell paints its own Light/Dark choice behind the bars. Choosing Dark under a light system leaves dark icons on a dark background. Fix needs the shell to expose its effective theme (or apply the window insets controller itself).

Unverified minor findings from the same review are listed in the session transcript; the most useful: import phase reads "Waiting to start" while the real importer is reading (it reports progress only per committed batch); every import failure says the old catalog is unchanged, including Unknown outcomes; a first-run search followed by an import shows a stale banner about a catalog that never existed.

## Latest continuation delta

Parser and 46 synthetic tests committed at `796c665` on `agent/cc-k3-rules-parser-01` (base `dd3abf7d2c3159d1e9b2553d5b1d2e32dfe86472`); this handoff marks READY-FOR-REVIEW. Verification: `:feature:rules:check` and `:feature:rules:assembleDebug` passed in a throwaway root that only adds the include; hosted CI and genuine-bytes checks not_run. Next (root): review the branch against the card, decide the two-pass question, add `":feature:rules"` to `android-app/settings.gradle.kts` so hosted CI builds it, then merge or return findings. The worker stops here.
