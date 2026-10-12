# Handoff cc-k3-rules-parser-01: private K3 rules TXT parser

Status: READY-FOR-REVIEW, two stacked candidates. Not accepted; root re-reviews and decides inclusion.
Owner: Claude Code (external-claude-code per `coordination/locks/rules.lock`, re-verified on `main` `f20ba4a11b217a771a27e5fc1a3563231dcf7fc8`). Model tier is set by the session launcher and is not selectable by the worker; no subagents (card rule).
Branch: `agent/cc-k3-rules-parser-01`. Base: `main` at `dd3abf7d2c3159d1e9b2553d5b1d2e32dfe86472`, unchanged (never merged, rebased or force-pushed). Reviewed candidate: `209a938` / code `796c665` (`docs/verification/k3-rules-parser/REVIEW.md`).
`main` has moved to `f20ba4a`; it changed no owned path since `dd3abf7`, and `git merge-tree --write-tree origin/main HEAD` reports no conflict.
Owned paths written: `android-app/feature/rules/**` and this file only.

## Candidates

`CLAUDE-PARALLEL.md` asks for F1 only, so the branch carries two stacked commits and root picks one:

1. **F1 only: `59c6dd1bb8b92de14dc4444bec9401e2855cb8a5`** (parent `209a938`). The review's fix and regressions, nothing else; its own handoff is complete for that candidate. 52 tests.
2. **F1 plus hardening: this commit** (parent `59c6dd1`). Five further defects found in a self-audit against the card and the catalog framer audit (`581126e`), each with a regression. Optional; merge `59c6dd1` instead to keep the re-review to F1. 63 tests.

## F1 correction

Finding: a `CancelToken` cancelled inside a callback while one final chunk was processed could let the parser emit later entries and return `Ok`.

Fix at `59c6dd1`:

- `Utf8LineReader.kt`: new `internal suspend fun CancelToken.checkpoint()` checks coroutine cancellation (`ensureActive`) and the token (`throwIfCancelled`). `LineAssembler` receives the token. The reader checks per chunk, before and after every line callback (including the unterminated last line delivered by `finish`), and before returning the digest. The old every-256-lines coroutine-only check is replaced by the per-line check.
- `RulesTextParser.kt`: `checkpoint()` before and after every `onEntry` call, and before `index` or `parse` returns a result. A cancelled caller receives `CancellationException`, never `Ok`.

Regression tests (all inputs synthetic), with their result against reviewed code `796c665` and against `59c6dd1` (unchanged at this commit):

| Test | `796c665` | `59c6dd1` and this commit |
|---|---|---|
| `RulesTextParserTest.tokenCancelledAfterTheFirstEntryOfOneFinalChunkThrowsAndEmitsNothingMore` (the test the review asks for: one final chunk, token cancelled after the first entry, asserts `CancellationException`, exactly one entry seen, no result) | failed | passed |
| `RulesTextParserTest.tokenCancelledDuringTheLastEntryCallbackNeverReturnsOk` | failed (returned Ok) | passed |
| `RulesTextParserTest.coroutineCancelledDuringTheLastEntryCallbackNeverReturnsOk` | failed (returned Ok) | passed |
| `RulesTextParserTest.indexPassNoticesTokenCancellationInsideOneFinalChunk` | failed | passed |
| `Utf8LineReaderTest.tokenCancelledByTheLastUnterminatedLineCallbackThrowsInsteadOfReturningADigest` | failed | passed |
| `RulesTextParserTest.cancelledCoroutineWithALiveTokenAndNonSuspendingInputEmitsNothing` (guard, same shape as the catalog framer audit test) | passed | passed |

## Hardening beyond F1 (this commit only)

| # | Defect at `796c665` (unchanged by F1) | Fix | Regression |
|---|---|---|---|
| H1 | Generated duplicate ids could equal a source id: terms `X`, `X`, `X~2` emitted `glossary:X~2` twice, breaking id uniqueness. | `RulesStructure.uniqueIdentifier` tracks every emitted id and skips taken suffixes (`glossary:X~2~2`); linear overall. | `generatedDuplicateIdentifiersNeverCollideWithSourceIdentifiers` (failed at `796c665`) |
| H2 | A Contents listing only chapters swallowed the body's chapter headings into the Contents NOTICE, losing those ids (references to them became Unknown). | The body starts at the repeat of the first section **or chapter** heading Contents lists. Unchanged when a section is listed first. | `contentsListingOnlyChaptersKeepsTheBodyChapterEntries` (failed at `796c665`) |
| H3 | `RulesParserLimits` accepted values up to `Int.MAX_VALUE`/`Long.MAX_VALUE`, although entries, entry bytes, line bytes, continuation lines and references bound retained memory. | Constructor requires each limit within a hard ceiling (table below), as the catalog framer audit did. | `RulesParserLimitsTest` (6 tests) |
| H4 | An over-limit chunk was copied before rejection, and every chunk allocated a char buffer as large as the chunk plus a combined byte copy. | Size checked before `copyBytes()`; one reusable 8 Ki-char decode window looped on overflow; no combined copy unless a partial UTF-8 sequence is carried. Cancellation also checked per decode window. | `oversizedChunkIsRejectedWithItsFullSize`, `oneLargeChunkDecodesInBoundedWindowsWithoutLosingCharacters` (guards: results were already correct, only allocation changed) |
| H5 | Coverage gaps: limits were tested only over the bound, CRLF/BOM only at the line reader. | Tests only. | `everyLimitAcceptsInputThatUsesExactlyTheLimit` / `...RejectsInputThatUsesOneMoreThanTheLimit` (all 7 limits, exact observed value), `crlfAndByteOrderMarkGiveTheSameEntriesAtEveryChunkSize` (sizes 1, 2, 3, 5, 4096), `RulesParserMutationTest` |

`RulesParserMutationTest` applies 400 seeded edits to the synthetic corpus (random bytes, deletions, structure-like fragments, duplicated runs, random chunk sizes) and requires Ok or a typed failure, never another exception; when the index pass succeeds the parse pass must succeed with unique ids equal to the index set, contiguous ordinals, and Known targets present in the index. It says nothing about genuine files.

API 23 check: the decode window uses `flip()`/`clear()`, which `android-37.0` also declares with covariant returns. `javap` on the compiled classes shows every call site bound to `()Ljava/nio/Buffer;`, present since API 1.

## Verification actually run

Throwaway copy of `android-app/` whose only differences are `":feature:rules"` added to the `settings.gradle.kts` include list and `local.properties` pointing at a local SDK (JDK 17, Gradle wrapper 9.6.0, platform `android-37.0`, build-tools 36.0.0). `./gradlew --no-daemon :feature:rules:check :feature:rules:assembleDebug` on each candidate's exact rules tree:

- `59c6dd1` (F1 only): BUILD SUCCESSFUL. JUnit XML: 52 tests (RulesTextParserTest 32, Utf8LineReaderTest 12, RuleReferenceScannerTest 8), 0 failures, 0 errors, 0 skipped. Lint "No issues found". `rules-debug.aar` produced. The same 52 tests with `src/main` from `796c665`: 5 failed, exactly the five marked failed in the F1 table.
- This commit: BUILD SUCCESSFUL. 63 tests (RulesTextParserTest 35, Utf8LineReaderTest 13, RuleReferenceScannerTest 8, RulesParserLimitsTest 6, RulesParserMutationTest 1), 0 failures, 0 errors, 0 skipped. Lint "No issues found". `rules-debug.aar` produced. Earlier red run of the new tests (limits test excluded, it needs this commit's constants) against `796c665` sources: 5 of 55 failed, namely H1, H2 and three cancellation tests (one later replaced by the review's first-entry variant, covered by the F1 red run).
- Longer local fuzz, with the mutation test's seed and round count overridden in the throwaway copy only (not committed); every round ended in Ok or a typed failure, no other exception. On this commit's main sources: committed seed 20261012 at 400 rounds, 280 Ok / 120 Failed; seeds 11, 12, 13 at 20,000 rounds each, 14,068/5,932, 14,133/5,867, 14,054/5,946. Before the last two F1 checkpoints were added, seeds 1 to 5 at 20,000 rounds each also passed.

not_run: hosted CI for this module (`settings.gradle.kts` on this branch does not include it, so `android-k2.yml` runs here rebuild only main's modules); any genuine Comprehensive Rules bytes; Android instrumentation (none needed for a pure parser); performance on a full corpus.

## Entry signature (unchanged)

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

`RulesParseResult` is `Ok(value)` or `Failed(RulesParseFailure)`; cancellation is thrown, never returned. `RulesParseSummary` reports entries, entries by kind, references, unresolved references, duplicate identifiers, anomalies (count plus the first 20 with line numbers), byte count and SHA-256. Every declaration is `internal`; the module exposes no public API.

## Changed files since base `dd3abf7`

- `android-app/feature/rules/build.gradle.kts`: Android library, compileSdk 37, minSdk 23, Java 17, explicit API, fatal NewApi lint; `api(:core:model)`, `:core:common`, coroutines; JUnit 4 and coroutines-test. Existing catalog aliases only.
- `src/main/kotlin/.../feature/rules/parser/`: `RulesTextParser.kt`, `Utf8LineReader.kt`, `RulesStructure.kt`, `RuleReferenceScanner.kt`, `RulesParseResult.kt`, `RulesParserLimits.kt`.
- `src/test/kotlin/.../feature/rules/parser/`: `SyntheticRules.kt` (labelled synthetic corpus, chunker, provenance with unknown version and dates), `RulesTextParserTest.kt`, `Utf8LineReaderTest.kt`, `RuleReferenceScannerTest.kt`; at this commit also `RulesParserLimitsTest.kt` and `RulesParserMutationTest.kt`.

## Design

### Two passes

`RuleCrossReference.resolved` is `Knowledge<RuleId>`, and Comprehensive Rules text refers forward constantly. Buffering the corpus is ruled out by the card, so the parser reads the same bytes twice:

1. `index`: runs the full structure recognizer but keeps only entry identifiers (bounded by `maxEntries`), byte count and SHA-256. No entry text is retained.
2. `parse`: runs the identical recognizer, emits each `RuleEntry` in document order through the suspending callback with references resolved against the index, and fails with `SourceChanged` if byte count or SHA-256 differs from the index pass.

A failed `parse` result means every entry it emitted must be discarded; the future repository that stages entries owns that discard (the review notes this still needs its integration test).

### Input decoding

Strict UTF-8 (`CodingErrorAction.REPORT`); multi-byte sequences may span chunks. A leading BOM is removed. `\r\n`, `\n` and lone `\r` each end a line; entry text joins its lines with `\n`. Line content is otherwise exact. Chunk contract as in the K2 catalog framer: `sequence` and `offset` contiguous from 0, exactly one `final = true` chunk last; an empty file is one empty final chunk.

### Proposed limits (parser safeguards, not corpus facts; root reviews against genuine bytes)

Ceilings exist at this commit only (H3); at `59c6dd1` any positive value is accepted.

| Limit | Default | Ceiling | Reason |
|---|---|---|---|
| `maxInputBytes` | 8 MiB | 32 MiB | Matches the K2 import ceiling; current CR TXT size unverified. |
| `maxLineBytes` | 16 KiB | 64 KiB | Longest CR paragraph line expected well under this. |
| `maxEntries` | 20,000 | 100,000 | Rules, subrules, examples, glossary and notices together expected in the low thousands. |
| `maxEntryBytes` | 64 KiB | 256 KiB | One entry's accumulated lines, blank lines included, joined by one byte each. |
| `maxContinuationLines` | 512 | 2,048 | Lines after an entry's first line, blank lines included; 0 allowed. |
| `maxReferencesPerEntry` | 256 | 1,024 | Cross-references emitted for one entry; 0 allowed. |
| `maxRangeExpansion` | 26 | 26 | Targets produced by one letter range such as `702.19b-d`; a to z is at most 26. |

### Grammar (from R-03 observations; unverified against genuine bytes)

Regions in order: front matter, optional `Contents`, body, `Glossary`, `Credits`.

- Front matter: blank-line separated blocks before a standalone `Contents` line (or before the first heading when there is no Contents). Each block is one NOTICE.
- Contents: from the standalone `Contents` line until the first section or chapter heading it lists appears again (section only at `59c6dd1`, see H2); one NOTICE titled `Contents` with its exact lines. Listed headings never become entries, so body headings alone own their identifiers: nothing is dropped or duplicated. A listing with neither starts the body at the first rule and records an anomaly.
- Section heading `^[1-9]\. \S`: accepted only as the next section number (and, when Contents lists sections, only if listed); otherwise continuation text plus an anomaly.
- Chapter heading `^\d{3}\. \S`: accepted when its first digit is the current section and its number is greater than the previous chapter.
- Rule `^\d{3}\.\d+\.? ` and subrule `^\d{3}\.\d+[a-z]\.? `: accepted inside the matching chapter (or section when no chapter is open). A subrule's parent rule is not checked. Letters are not checked against the l/o skip.
- `Example:` at column 0 starts an EXAMPLE attached to the latest rule, subrule, chapter or section.
- Any other non-blank line continues the current entry; inner blank lines are kept, leading and trailing blank lines trimmed.
- A repeated identifier keeps both entries; the second gets `<id>~2` (`~3` and so on, skipping any id already emitted, see H1) with its source `number` unchanged, and the summary counts it.
- `Glossary` (standalone, in the body): blank-line separated blocks; first line is the term, the rest its definition. Repeated terms are disambiguated the same way.
- `Credits` (standalone, after the body started): blank-line blocks, each a NOTICE.

### Entry mapping

| Kind | `id.raw` | `number` | `title` | `text` |
|---|---|---|---|---|
| NUMBERED section | `1` | Known `1` | Known heading text | exact heading line plus continuation |
| NUMBERED chapter | `100` | Known `100` | Known heading text | same |
| NUMBERED rule / subrule | `100.1`, `100.1a` | Known | Absent (no title in source) | exact lines |
| EXAMPLE | `<parent id>#example-<n>` | Absent | Absent | exact lines starting `Example:` |
| GLOSSARY | `glossary:<term>` | Absent | Known term | exact block |
| NOTICE | `front:<n>`, `contents`, `credits:<n>` | Absent | Known `Contents` for contents, otherwise Absent | exact block |

`RuleId.source` is `provenance.provider`; `ordinal` is 0-based emission order; `provenance` and `corpusId` are the caller's values, copied unchanged. The parser never derives a source version, provider, source date or effective date; the CR's own effective-date line stays raw NOTICE text.

### Cross-references

Contextual only: `rule`, `rules`, `section`, `sections` (case-insensitive) followed by targets separated by `,`, `and`, `or`, `through`, `to`, `-`, U+2013, U+2014. Targets are `\d{3}(\.\d+[a-z]?)?`, or one digit after `section`. Letter ranges on one rule (`702.19b-d`, `702.19b-702.19d`) expand per letter up to `maxRangeExpansion`; other ranges keep their two ends. `rawTarget` is the exact matched text; `resolved` is Known when the identifier is in the index, otherwise Unknown ("Target not found in this corpus") with one Evidence naming the target. Bare numbers are never linked.

## Open grammar and limit questions for root (need genuine bytes)

1. Two passes versus one pass plus a reference update in SQL after staging.
2. Glossary definitions containing a blank line would read as a new term.
3. Body section headings must match their Contents line exactly (after trailing whitespace); a mismatch surfaces as anomalies and folds that section into the previous entry.
4. `Example:` must start at column 0; indented rules or examples are continuation text.
5. Subrule letters accept any lowercase letter (no l/o check), and a subrule's parent rule is not checked. Letter ranges crossing the skip (for example `704.5k-n`) produce an `l` target that resolves Unknown.
6. Numeric ranges keep only their ends; only letter ranges on one rule expand.
7. Exact (raw target, identifier) duplicates within one entry collapse to the first occurrence.
8. Examples carry `number` Absent and encode their parent in the id; root may prefer a parent field in a later contract.
9. Genuine encoding and terminators are unverified: a non-UTF-8 file fails `MalformedUtf8` rather than being mis-decoded, and a doubled `\r\r\n` terminator would insert a blank line after every line, splitting glossary, front-matter and credit blocks.
10. Limits are proposals; the genuine-fixture review should set them.

## Shared-file requests for root

1. `android-app/settings.gradle.kts`: add `":feature:rules"` to the include list when root builds it in CI.
2. A focused JVM gate like `k3-catalog-jvm.yml`, if wanted before the full matrix: push on `agent/cc-k3-rules-parser-01` with paths `android-app/feature/rules/**`, run `:feature:rules:testDebugUnitTest`, and require that `RulesTextParserTest`, `Utf8LineReaderTest` and `RuleReferenceScannerTest` (plus `RulesParserLimitsTest` and `RulesParserMutationTest` for the hardening candidate) XML reports executed with 0 failures, errors and skips. It needs request 1 on the tested tree.
3. No version-catalog changes: the module uses `libs.plugins.android.library`, `project(":core:model")`, `project(":core:common")`, `libs.coroutines.core`, `libs.junit`, `libs.coroutines.test`.

## Earlier K2 notes

The K2 shell/catalog observations this handoff carried at `272f177`/`209a938` were acknowledged by root at `cp-20261012-k3-guards-30` (catalog FTS cleanup race assigned to the catalog task; shell stream-open guard merged at `9d7e0fd`; status-bar theme mismatch queued). Their full text remains in this file at `209a938`.

## Latest continuation delta

F1 fixed at `59c6dd1` (52 tests; 5 fail against `796c665`); optional hardening H1 to H5 stacked at this commit (63 tests). Both pass `check` and `assembleDebug` with lint clean in the throwaway include root; base `dd3abf7` unchanged. Hosted CI and genuine bytes not_run. Next (root): pick `59c6dd1` (F1 only) or this commit, re-review that SHA, then shared include and hosted evidence. The worker stops here.
