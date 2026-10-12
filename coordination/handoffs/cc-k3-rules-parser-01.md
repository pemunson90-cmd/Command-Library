# Handoff cc-k3-rules-parser-01: private K3 rules TXT parser

Status: IN-PROGRESS (design committed; implementation next).
Owner: Claude Code (external-claude-code per `coordination/locks/rules.lock`). Model tier: strongest tier, no subagents (card rule).
Branch: `agent/cc-k3-rules-parser-01`. Base: `main` at `dd3abf7b` (contains the task card and `rules.lock`, verified before any write).
Owned paths written: `android-app/feature/rules/**`, this file. Nothing else.

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

Only contextual references: `rule`, `rules`, `section`, `sections` (case-insensitive) followed by one or more targets separated by `,`, `and`, `or`, `through`, `to`, `-`, `–`. Targets are `\d{3}(\.\d+[a-z]?)?` or a single digit section after `section`. Letter ranges (`702.19b-d`, `702.19b–702.19d`) expand to each letter up to `maxRangeExpansion`. Each produced reference keeps `rawTarget` as the exact matched source text; `resolved` is Known when the identifier is in the index, otherwise Unknown with reason "Target not found in this corpus" and one Evidence naming the target. Bare decimal numbers are never linked.

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

Design, limits, grammar, ID scheme and test plan committed on `agent/cc-k3-rules-parser-01` from base `dd3abf7`. No parser code yet. Verification: not_run. Next: implement `android-app/feature/rules` (build file, decoder, recognizer, reference tokenizer, two-pass parser) and the synthetic JVM tests, build in a throwaway root that includes `:feature:rules`, then publish READY-FOR-REVIEW.
