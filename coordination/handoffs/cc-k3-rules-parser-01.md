# Handoff cc-k3-rules-parser-01: private K3 rules TXT parser

Status: READY-FOR-REVIEW, one candidate: this commit. Not accepted; root re-reviews and decides inclusion.
Owner: Claude Code (external-claude-code per `coordination/locks/rules.lock`, re-verified on `main` `f20ba4a11b217a771a27e5fc1a3563231dcf7fc8`). Pat lifted the card's temporary restrictions (no subagents; F1 only), so the earlier split between an F1-only and a hardening candidate is withdrawn: review this commit. Model tier is set by the session launcher and is not selectable by the worker.
Branch: `agent/cc-k3-rules-parser-01`. Base: `main` at `dd3abf7d2c3159d1e9b2553d5b1d2e32dfe86472`, unchanged (never merged, rebased or force-pushed). Root reviewed `209a938` / code `796c665` (`docs/verification/k3-rules-parser/REVIEW.md`). Since then: `59c6dd1` (F1), `4971166` (self-audit hardening H1 to H5), this commit (multi-agent review round, R1 to R18).
`main` changed no owned path since `dd3abf7`, and `git merge-tree --write-tree origin/main HEAD` reports no conflict.
Owned paths written: `android-app/feature/rules/**` and this file only.

## Changes since root's review

**F1 (`59c6dd1`).** A `CancelToken` cancelled inside a callback during one final chunk could emit later entries and return `Ok`. Now `CancelToken.checkpoint()` (coroutine `ensureActive` plus token) runs per chunk, per decode window, before and after every line callback, before and after every `onEntry`, and before any result. Regressions: `tokenCancelledAfterTheFirstEntryOfOneFinalChunkThrowsAndEmitsNothingMore` (the review's case), `tokenCancelledDuringTheLastEntryCallbackNeverReturnsOk`, `coroutineCancelledDuringTheLastEntryCallbackNeverReturnsOk`, `indexPassNoticesTokenCancellationInsideOneFinalChunk`, `tokenCancelledByTheLastUnterminatedLineCallbackThrowsInsteadOfReturningADigest` (all five failed on `796c665`), plus the guard `cancelledCoroutineWithALiveTokenAndNonSuspendingInputEmitsNothing`.

**H1 to H5 (`4971166`).** Generated duplicate ids skip ids already emitted (a term named `X~2` collided); a chapter-only Contents keeps the body's chapter entries; limit ceilings; over-limit chunks rejected before copying and decoding through one fixed window; exact-bound tests for every limit, CRLF/BOM chunk invariance, and a seeded mutation test.

**Review round (this commit).** Six independent review lenses (cancellation, decoding, structure, references, bounds, contract and test quality) ran over `4971166`. Five completed with 19 findings; the contract/test-quality lens and the per-finding verifier agents stopped at a usage limit, so every finding was checked here instead: a regression test that asserts the correct behavior was run against `4971166` sources, and it failed for the reported reason before the fix (marked red).

| # | Finding | Fix | Regression |
|---|---|---|---|
| R1 | A cancelled caller could receive `Failed(Truncated)` (or another typed failure) instead of cancellation. | Typed failures pass through `checkpoint()` before being returned. | `cancelledCallerReceivesCancellationRatherThanATypedFailure` (red) |
| R2 | `NoNumberedRules` was satisfied by section or chapter headings alone: a numbered list parsed Ok as a corpus. | Only accepted rule and subrule headings count. | `sectionAndChapterHeadingsWithoutRulesAreNotARulesCorpus` (red) |
| R3 | H2 regression: a chapter-only Contents followed by body sections absorbed the first section and rejected later ones. | Such a Contents also ends at the body heading of its first chapter's section. | `contentsListingOnlyChaptersEndsAtTheFirstBodySection` (red) |
| R4 (high) | Rule numbers had an unbounded digit run, so one long `100.111...` line plus repeated `Example:` lines made the index hold about 317 MiB from about 196 KB of input (reviewer's HotSpot measurement). Glossary terms had the same exposure. | Rule numbers take at most 4 digits after the chapter; new limit `maxIdentifierBytes` (128, ceiling 256) on every retained identifier; entries ceiling lowered to 50,000; the index reuses the recognizer's identifier set instead of a second copy. | `ruleNumbersLongerThanFourDigitsAreNotHeadings` (red), IDENTIFIER_BYTES bound and ceiling cases |
| R5 (high) | The rule and subrule heading patterns backtracked quadratically: a 64 KiB line with U+0085 cost about 11 s per pass (reviewer's measurement). | DOTALL patterns with a single whitespace before `.*`, linear per line; U+0085, U+2028 and U+2029 no longer stop a heading match. | `headingRecognitionIsLinearAndAcceptsLineSeparatorCharacters` (red: timed out at 20 s) |
| R6 | `maxReferencesPerEntry` was checked after every pair was built (29 MiB, 112 MiB at the ceilings, reviewer's measurement); references were scanned twice per entry. | Checked as each pair is produced (fails at limit + 1); scanned once per entry. | `referenceLimitFailsAtTheFirstPairOverTheLimit` (red: observed 78) |
| R7 | Reference targets had an unbounded digit run, so a letter range emitted 26 line-length identifiers. | Same 4-digit cap; longer runs are not references. | `ruleNumbersLongerThanFourDigitsAreNotTargets` (red) |
| R8 | A keyword ending one line linked to a number starting the next (false references inside Contents). | Only spaces, tabs and no-break spaces separate a keyword, targets and separators. | `referencesNeverSpanLines` (red) |
| R9 | The article "a" after `to` or a spaced dash became a subrule letter (`rule 613.1d to a copy` also cited 613.1a). | A letter-only range end needs a hyphen or en dash with no blanks, and a later letter. | `aWordAfterARangeWordIsNotASubruleLetter` (red) |
| R10 | A second range in a chain deleted the first range's targets. | A chain stops after one range. | `aChainStopsAfterOneRangeAndKeepsItsTargets` (red) |
| R11 | The scanner built new regex matchers over the whole entry per target, quadratic on Android's ICU regex backend. | Hand-written scanner: one keyword matcher advanced with `find()`, targets and separators read by character. | none JVM-observable; linear by construction |
| R12 | `MalformedUtf8` offsets came from `CharsetDecoder` buffer positions, which per the decoding reviewer's replay of Android's ICU calls point past the bad bytes on Android (not run on a device). | Own strict UTF-8 decoder (RFC 3629 well-formed table), state carried across chunks, offsets of the sequence's first byte; no platform decoder. | `decodingAgreesWithTheJdkStrictDecoderOnSeededInputs`: 3,007 seeded and fixed cases, identical lines or offset to the JVM's REPORT decoder (passes on both; an equivalence guard, not a red test) |
| R13 | Two tests did not check what their names claimed. | `sectionOutOfSequenceStaysText` asserts the kept text; new `parsePassEmitsTheFirstEntryBeforeTheInputEnds`; the old test renamed `parsePassEmitsEachEntryOnceWithUniqueIdentifiers`. | tests only |
| R14 to R18 | Documentation did not match code. | Documented below, no behavior change: standalone `Glossary`/`Credits` lines belong to no entry; a Contents that never ends can fail on an entry limit instead of `Structure`; a heading-less Contents followed by a body section or chapter fails `Structure`; changed bytes can fail `parse` with another typed failure before `SourceChanged`; repeated section and chapter headings are kept as text with an anomaly, not as `~2` entries. | none |

R6 was reported by two lenses; 19 findings, 18 distinct.

## Verification actually run

Throwaway copy of `android-app/` whose only differences are `":feature:rules"` added to the `settings.gradle.kts` include list and `local.properties` pointing at a local SDK (JDK 17, Gradle wrapper 9.6.0, platform `android-37.0`, build-tools 36.0.0), with this commit's exact rules tree:

- `./gradlew --no-daemon :feature:rules:check :feature:rules:assembleDebug`: BUILD SUCCESSFUL. JUnit XML: 75 tests (RulesTextParserTest 42, Utf8LineReaderTest 14, RuleReferenceScannerTest 12, RulesParserLimitsTest 6, RulesParserMutationTest 1), 0 failures, 0 errors, 0 skipped. Lint "No issues found". `rules-debug.aar` produced. All sources ASCII-only.
- Red run: the same tests (limits test excluded, it needs the new limit) against `4971166` main sources: 69 tests, 10 failed, exactly the ten marked red above.
- Earlier rounds: F1 red/green against `796c665`; `javap` showed every NIO call site API-23 safe (the reader no longer uses NIO buffers).
- Longer local fuzz with the mutation test's seed and rounds overridden in the throwaway copy only (not committed); every round ended in Ok or a typed failure, no other exception: committed seed 20261012 at 400 rounds, 267 Ok / 133 Failed; seed 21 at 20,000 rounds, 13,044 Ok / 6,956 Failed.

not_run: hosted CI for this module (`settings.gradle.kts` on this branch does not include it, so `android-k2.yml` runs here rebuild only main's modules); any genuine Comprehensive Rules bytes; Android device or emulator runs (the decoder change removes the platform dependency R12 describes, but no device confirmed the original offset defect); performance on a full corpus.

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

`RulesParseResult` is `Ok(value)` or `Failed(RulesParseFailure)`; cancellation is thrown, never returned, and wins over any typed failure. `RulesParseSummary` reports entries, entries by kind, references, unresolved references, duplicate identifiers, anomalies (count plus the first 20 with line numbers), byte count and SHA-256. Every declaration is `internal`; the module exposes no public API.

## Changed files since base `dd3abf7`

- `android-app/feature/rules/build.gradle.kts`: Android library, compileSdk 37, minSdk 23, Java 17, explicit API, fatal NewApi lint; `api(:core:model)`, `:core:common`, coroutines; JUnit 4 and coroutines-test. Existing catalog aliases only.
- `src/main/kotlin/.../feature/rules/parser/`: `RulesTextParser.kt`, `Utf8LineReader.kt`, `RulesStructure.kt`, `RuleReferenceScanner.kt`, `RulesParseResult.kt`, `RulesParserLimits.kt`.
- `src/test/kotlin/.../feature/rules/parser/`: `SyntheticRules.kt` (labelled synthetic corpus, chunker, provenance with unknown version and dates), `RulesTextParserTest.kt`, `Utf8LineReaderTest.kt`, `RuleReferenceScannerTest.kt`, `RulesParserLimitsTest.kt`, `RulesParserMutationTest.kt`.

## Design

### Two passes

`RuleCrossReference.resolved` is `Knowledge<RuleId>`, and Comprehensive Rules text refers forward constantly. Buffering the corpus is ruled out by the card, so the parser reads the same bytes twice:

1. `index`: runs the full structure recognizer but keeps only entry identifiers (bounded by `maxEntries` and `maxIdentifierBytes`), byte count and SHA-256. No entry text is retained.
2. `parse`: runs the identical recognizer, emits each `RuleEntry` in document order through the suspending callback with references resolved against the index, and fails with `SourceChanged` at end of input if byte count or SHA-256 differs from the index pass. Changed bytes can also stop the pass earlier with another typed failure.

A failed `parse` result means every entry it emitted must be discarded; the future repository that stages entries owns that discard (root's review notes this still needs its integration test).

### Input decoding

Strict UTF-8 decoded by the reader itself (well-formed byte table: no overlongs, surrogates, values above U+10FFFF, stray continuation bytes or truncated sequences), with state carried across chunks; `MalformedUtf8` gives the absolute offset of the bad sequence's first byte, as the JDK decoder does. A leading BOM is removed. `\r\n`, `\n` and lone `\r` each end a line; entry text joins its lines with `\n`. Line content is otherwise exact. Chunk contract as in the K2 catalog framer: `sequence` and `offset` contiguous from 0, exactly one `final = true` chunk last; an empty file is one empty final chunk.

### Proposed limits (parser safeguards, not corpus facts; root reviews against genuine bytes)

| Limit | Default | Ceiling | Reason |
|---|---|---|---|
| `maxInputBytes` | 8 MiB | 32 MiB | Matches the K2 import ceiling; current CR TXT size unverified. |
| `maxLineBytes` | 16 KiB | 64 KiB | Longest CR paragraph line expected well under this. |
| `maxEntries` | 20,000 | 50,000 | Rules, subrules, examples, glossary and notices together expected in the low thousands. |
| `maxEntryBytes` | 64 KiB | 256 KiB | One entry's accumulated lines, blank lines included, joined by one byte each. |
| `maxContinuationLines` | 512 | 2,048 | Lines after an entry's first line, blank lines included; 0 allowed. |
| `maxReferencesPerEntry` | 256 | 1,024 | Distinct cross-references for one entry, checked as each is produced; 0 allowed. |
| `maxRangeExpansion` | 26 | 26 | Targets produced by one letter range such as `702.19b-d`; a to z is at most 26. |
| `maxIdentifierBytes` | 128 | 256 | One retained identifier such as `glossary:<term>`; entries times this bounds the index (at most 50,000 x 256 UTF-8 bytes of identifier text at the ceilings). |

### Grammar (from R-03 observations; unverified against genuine bytes)

Regions in order: front matter, optional `Contents`, body, `Glossary`, `Credits`. Every non-blank line belongs to exactly one entry except the standalone `Glossary` and `Credits` lines in the body, which only mark region changes.

- Front matter: blank-line separated blocks before a standalone `Contents` line (or before the first heading when there is no Contents). Each block is one NOTICE.
- Contents: from the standalone `Contents` line until the first section or chapter heading it lists appears again, or, when it lists a chapter first, until the body heading of that chapter's section; one NOTICE titled `Contents` with its exact lines. Listed headings never become entries, so body headings alone own their identifiers. A listing with no section or chapter heading ends at the first rule line, but a section or chapter heading appearing before any rule is taken as the listing's first heading, so such input fails `Structure` unless that heading repeats. A Contents that never ends fails `Structure`, or an entry limit if it grows past one first.
- Section heading `^[1-9]\. \S`: accepted only as the next section number (and, when Contents lists sections, only if listed); otherwise continuation text plus an anomaly.
- Chapter heading `^\d{3}\. \S`: accepted when its first digit is the current section and its number is greater than the previous chapter. A repeated section or chapter heading is therefore kept as text with an anomaly.
- Rule `^\d{3}\.\d{1,4}\.? ` and subrule `^\d{3}\.\d{1,4}[a-z]\.? `: accepted inside the matching chapter (or section when no chapter is open). Longer digit runs are not headings. A subrule's parent rule is not checked; letters are not checked against the l/o skip. At least one rule or subrule is required (`NoNumberedRules`).
- `Example:` at column 0 starts an EXAMPLE attached to the latest rule, subrule, chapter or section.
- Any other non-blank line continues the current entry; inner blank lines are kept, leading and trailing blank lines trimmed.
- A repeated rule, subrule or glossary identifier keeps both entries; the second gets `<id>~2` (`~3` and so on, skipping any id already emitted) with its source `number` unchanged, and the summary counts it.
- `Glossary` (standalone, in the body): blank-line separated blocks; first line is the term, the rest its definition.
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

Contextual only: `rule`, `rules`, `section`, `sections` (case-insensitive) followed by targets on the same line. Targets are `\d{3}`, `\d{3}.\d{1,4}`, `\d{3}.\d{1,4}[a-z]`, or one digit 1 to 9 after `section`, each ending where no letter or digit follows. Separators are `,` (optionally followed by `and`/`or`), `and`, `or`, and one range: hyphen, U+2013, U+2014, `through`, `to`; only spaces, tabs and no-break spaces may surround them. A letter range on one rule (`702.19b-d`, `702.19b-702.19d`) expands per letter up to `maxRangeExpansion`; the letter-only end needs a hyphen or en dash with no blanks and a later letter. Other ranges keep their two ends, and a chain stops after one range. `rawTarget` is the exact matched text; `resolved` is Known when the identifier is in the index, otherwise Unknown ("Target not found in this corpus") with one Evidence naming the target. Bare numbers are never linked.

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
10. Rule numbers are capped at 4 digits after the chapter and identifiers at 128 bytes; the longest genuine rule number and glossary term should confirm both.
11. Limits are proposals; the genuine-fixture review should set them.

## Shared-file requests for root

1. `android-app/settings.gradle.kts`: add `":feature:rules"` to the include list when root builds it in CI.
2. A focused JVM gate like `k3-catalog-jvm.yml`, if wanted before the full matrix: push on `agent/cc-k3-rules-parser-01` with paths `android-app/feature/rules/**`, run `:feature:rules:testDebugUnitTest`, and require that the `RulesTextParserTest`, `Utf8LineReaderTest`, `RuleReferenceScannerTest`, `RulesParserLimitsTest` and `RulesParserMutationTest` XML reports executed with 0 failures, errors and skips. It needs request 1 on the tested tree.
3. No version-catalog changes: the module uses `libs.plugins.android.library`, `project(":core:model")`, `project(":core:common")`, `libs.coroutines.core`, `libs.junit`, `libs.coroutines.test`.

## Earlier K2 notes

The K2 shell/catalog observations this handoff carried at `272f177`/`209a938` were acknowledged by root at `cp-20261012-k3-guards-30` (catalog FTS cleanup race assigned to the catalog task; shell stream-open guard merged at `9d7e0fd`; status-bar theme mismatch queued). Their full text remains in this file at `209a938`.

## Latest continuation delta

Single candidate at this commit: F1 (`59c6dd1`), hardening (`4971166`) and a six-lens review round (R1 to R18, ten red-to-green regressions, own strict UTF-8 decoder, linear grammar and scanner, identifier and reference bounds). 75 synthetic tests pass with `check` and `assembleDebug`, lint clean, in the throwaway include root; base `dd3abf7` unchanged. Hosted CI, devices and genuine bytes not_run. Next (root): re-review this commit, then shared include and hosted evidence. The worker stops here.
