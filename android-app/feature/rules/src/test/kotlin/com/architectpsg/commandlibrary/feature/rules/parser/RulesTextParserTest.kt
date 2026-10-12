package com.architectpsg.commandlibrary.feature.rules.parser

import com.architectpsg.commandlibrary.core.model.ByteChunk
import com.architectpsg.commandlibrary.core.model.CancelToken
import com.architectpsg.commandlibrary.core.model.Knowledge
import com.architectpsg.commandlibrary.core.model.RuleEntry
import com.architectpsg.commandlibrary.core.model.RuleEntryKind
import com.architectpsg.commandlibrary.core.model.RuleId
import com.architectpsg.commandlibrary.core.model.SnapshotBytes
import com.architectpsg.commandlibrary.feature.rules.parser.SyntheticRules.CORPUS
import com.architectpsg.commandlibrary.feature.rules.parser.SyntheticRules.CORPUS_ID
import com.architectpsg.commandlibrary.feature.rules.parser.SyntheticRules.NeverCancelled
import com.architectpsg.commandlibrary.feature.rules.parser.SyntheticRules.PROVENANCE
import com.architectpsg.commandlibrary.feature.rules.parser.SyntheticRules.PROVIDER
import com.architectpsg.commandlibrary.feature.rules.parser.SyntheticRules.chunks
import com.architectpsg.commandlibrary.feature.rules.parser.SyntheticRules.indexFailure
import com.architectpsg.commandlibrary.feature.rules.parser.SyntheticRules.parseAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Every input in this class is SYNTHETIC; no official rules text is used. */
class RulesTextParserTest {

    @Test
    fun syntheticCorpusYieldsEveryEntryKindInDocumentOrder() = runTest {
        val parsed = parseAll(CORPUS)
        assertTrue(parsed.result is RulesParseResult.Ok)
        assertEquals(
            listOf(
                "front:1", "front:2", "contents",
                "1", "100", "100.1", "100.1a", "100.1a#example-1", "100.2", "101", "101.1",
                "2", "200", "200.1",
                "glossary:Synthetic Term", "glossary:Synthetic Term~2",
                "credits:1", "credits:2",
            ),
            parsed.entries.map { it.id.raw },
        )
        assertEquals((0L until parsed.entries.size).toList(), parsed.entries.map { it.ordinal })
        val kinds = parsed.summary.entriesByKind
        assertEquals(10L, kinds[RuleEntryKind.NUMBERED])
        assertEquals(1L, kinds[RuleEntryKind.EXAMPLE])
        assertEquals(2L, kinds[RuleEntryKind.GLOSSARY])
        assertEquals(5L, kinds[RuleEntryKind.NOTICE])
        assertEquals(18L, parsed.summary.entries)
        assertEquals(CORPUS.toByteArray().size.toLong(), parsed.summary.byteCount)
    }

    @Test
    fun numberedEntriesCarrySourceNumbersAndHeadingTitles() = runTest {
        val parsed = parseAll(CORPUS)
        val section = parsed.entry("1")
        assertEquals(Knowledge.Known("1"), section.number)
        assertEquals(Knowledge.Known("Synthetic Concepts"), section.title)
        assertEquals(Knowledge.Known("Synthetic General"), parsed.entry("100").title)
        val rule = parsed.entry("100.1a")
        assertEquals(RuleEntryKind.NUMBERED, rule.kind)
        assertEquals(Knowledge.Known("100.1a"), rule.number)
        assertTrue(rule.title is Knowledge.Absent)
        assertEquals(RuleId(PROVIDER, "100.1a"), rule.id)
    }

    @Test
    fun entryTextIsTheExactSourceLinesWithContinuations() = runTest {
        val parsed = parseAll(CORPUS)
        assertEquals(
            "100.2. Synthetic rules 100.1a-c are listed here; rule 999.9 does not exist.\nA synthetic continuation line.",
            parsed.entry("100.2").text,
        )
        assertEquals("Synthetic Term\nA synthetic glossary definition. See rule 100.1.", parsed.entry("glossary:Synthetic Term").text)
    }

    @Test
    fun contentsHeadingsAreKeptAsOneNoticeAndNeverDuplicateBodyIdentifiers() = runTest {
        val parsed = parseAll(CORPUS)
        val contents = parsed.entry("contents")
        assertEquals(RuleEntryKind.NOTICE, contents.kind)
        assertEquals(Knowledge.Known("Contents"), contents.title)
        assertTrue(contents.text.startsWith("Contents\n"))
        assertTrue(contents.text.contains("100. Synthetic General"))
        assertTrue(contents.text.endsWith("Credits"))
        val numbered = parsed.entries.filter { it.kind == RuleEntryKind.NUMBERED }.map { it.id.raw }
        assertEquals(numbered.distinct(), numbered)
        assertEquals(1, numbered.count { it == "100" })
    }

    @Test
    fun duplicateGlossaryTermIsKeptUnderADisambiguatedIdentifier() = runTest {
        val parsed = parseAll(CORPUS)
        val second = parsed.entry("glossary:Synthetic Term~2")
        assertEquals(Knowledge.Known("Synthetic Term"), second.title)
        assertEquals("Synthetic Term\nA second synthetic definition with the same term.", second.text)
        assertEquals(1L, parsed.summary.duplicateIdentifiers)
    }

    @Test
    fun duplicateBodyRuleIsKeptAndReferencesResolveToTheFirst() = runTest {
        val text = """
            100. Synthetic General

            100.1. First synthetic copy.

            100.1. Second synthetic copy with the same number.

            100.2. Points at rule 100.1.
        """.trimIndent()
        val parsed = parseAll(text)
        assertEquals(listOf("100", "100.1", "100.1~2", "100.2"), parsed.entries.map { it.id.raw })
        assertEquals(Knowledge.Known("100.1"), parsed.entry("100.1~2").number)
        assertEquals(1L, parsed.summary.duplicateIdentifiers)
        assertEquals(Knowledge.Known(RuleId(PROVIDER, "100.1")), parsed.entry("100.2").references.single().resolved)
    }

    @Test
    fun forwardAndBackwardReferencesResolveThroughTheIndex() = runTest {
        val parsed = parseAll(CORPUS)
        val forward = parsed.entry("100.1").references.single()
        assertEquals("200.1", forward.rawTarget)
        assertEquals(Knowledge.Known(RuleId(PROVIDER, "200.1")), forward.resolved)
        val back = parsed.entry("101.1").references
        assertEquals(listOf("100.1", "2"), back.map { it.rawTarget })
        assertTrue(back.all { it.resolved is Knowledge.Known })
        assertEquals(Knowledge.Known(RuleId(PROVIDER, "100.1a")), parsed.entry("100.1a#example-1").references.single().resolved)
    }

    @Test
    fun targetsMissingFromTheCorpusStayUnknownWithEvidence() = runTest {
        val parsed = parseAll(CORPUS)
        val references = parsed.entry("100.2").references
        assertEquals(listOf("100.1a-c", "100.1a-c", "100.1a-c", "999.9"), references.map { it.rawTarget })
        assertTrue(references[0].resolved is Knowledge.Known)
        for (missing in references.drop(1)) {
            val unknown = missing.resolved as Knowledge.Unknown
            assertEquals("Target not found in this corpus", unknown.reason)
            assertTrue(unknown.evidence.isNotEmpty())
        }
        assertEquals(3L, parsed.summary.unresolvedReferences)
    }

    @Test
    fun provenanceCorpusAndUnknownEffectiveDateAreCopiedUnchanged() = runTest {
        val parsed = parseAll(CORPUS)
        for (entry in parsed.entries) {
            assertTrue(entry.provenance === PROVENANCE)
            assertEquals(CORPUS_ID, entry.corpusId)
            assertTrue(entry.provenance.effectiveAt is Knowledge.Unknown)
            assertEquals(PROVIDER, entry.id.source)
        }
    }

    @Test
    fun examplesBelongToTheirRuleAndCarryNoNumber() = runTest {
        val parsed = parseAll(CORPUS)
        val example = parsed.entry("100.1a#example-1")
        assertEquals(RuleEntryKind.EXAMPLE, example.kind)
        assertEquals("Example: A synthetic example for rule 100.1a.", example.text)
        assertTrue(example.number is Knowledge.Absent)
        assertTrue(example.title is Knowledge.Absent)
    }

    @Test
    fun headingOutsideItsChapterStaysTextAndIsReported() = runTest {
        val text = """
            100. Synthetic General

            100.1. A synthetic rule.
            300.1. A synthetic line that looks like a rule from another chapter.
        """.trimIndent()
        val parsed = parseAll(text)
        assertEquals(listOf("100", "100.1"), parsed.entries.map { it.id.raw })
        assertTrue(parsed.entry("100.1").text.endsWith("from another chapter."))
        assertEquals(1L, parsed.summary.anomalies)
        assertEquals(4L, parsed.summary.firstAnomalies.single().line)
    }

    @Test
    fun sectionOutOfSequenceStaysText() = runTest {
        val text = """
            1. Synthetic One

            100.1. A synthetic rule.

            3. Synthetic section that skips two
        """.trimIndent()
        val parsed = parseAll(text)
        assertEquals(listOf("1", "100.1"), parsed.entries.map { it.id.raw })
        assertEquals(1L, parsed.summary.anomalies)
    }

    @Test
    fun withoutContentsTheBodyStartsAtTheFirstHeading() = runTest {
        val text = "Synthetic preface line.\n\n100. Synthetic General\n\n100.1. A synthetic rule.\n"
        val parsed = parseAll(text)
        assertEquals(listOf("front:1", "100", "100.1"), parsed.entries.map { it.id.raw })
    }

    @Test
    fun contentsWithoutABodyFails() = runTest {
        val failure = indexFailure("Contents\n\n1. Synthetic Concepts\n100. Synthetic General\n")
        assertTrue(failure is RulesParseFailure.Structure)
    }

    @Test
    fun textWithoutNumberedRulesFails() = runTest {
        assertEquals(RulesParseFailure.NoNumberedRules, indexFailure("Synthetic notice only.\n\nAnother synthetic block.\n"))
    }

    @Test
    fun emptyInputFails() = runTest {
        assertEquals(RulesParseFailure.NoNumberedRules, indexFailure(chunks(ByteArray(0))))
    }

    @Test
    fun bytesChangedBetweenPassesFail() = runTest {
        val parser = RulesTextParser()
        val index = (parser.index(chunks(CORPUS), NeverCancelled) as RulesParseResult.Ok).value
        val changed = CORPUS.replace("synthetic part rule", "synthetic part RULE")
        val result = parser.parse(chunks(changed), index, PROVENANCE, CORPUS_ID, NeverCancelled) {}
        val failure = (result as RulesParseResult.Failed).failure as RulesParseFailure.SourceChanged
        assertEquals(index.sha256.hex, failure.indexedSha256)
        assertFalse(failure.indexedSha256 == failure.parsedSha256)
    }

    @Test
    fun chunkSizeDoesNotChangeTheEntries() = runTest {
        val whole = parseAll(CORPUS).entries
        for (size in listOf(1, 3, 7, 64)) {
            assertEquals("chunk size $size", whole, parseAll(CORPUS, chunkSize = size).entries)
        }
    }

    @Test
    fun cancellationThroughTheTokenPropagatesWithoutAResult() = runTest {
        val parser = RulesTextParser()
        val index = (parser.index(chunks(CORPUS), NeverCancelled) as RulesParseResult.Ok).value
        val token = CountdownToken(remaining = 3)
        var result: RulesParseResult<RulesParseSummary>? = null
        try {
            result = parser.parse(chunks(CORPUS, size = 16), index, PROVENANCE, CORPUS_ID, token) {}
            fail("Expected cancellation")
        } catch (expected: CancellationException) {
            assertEquals(null, result)
        }
    }

    @Test
    fun coroutineCancellationStopsTheParseWithoutAResult() = runTest {
        val parser = RulesTextParser()
        val started = CompletableDeferred<Unit>()
        var finished = false
        val job = launch {
            parser.index(
                flow {
                    emit(ByteChunk(0, 0, SnapshotBytes("100.1. Synthetic.\n".toByteArray()), null, false))
                    started.complete(Unit)
                    awaitCancellation()
                },
                NeverCancelled,
            )
            finished = true
        }
        started.await()
        yield()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(finished)
    }

    @Test
    fun entryLimitFails() = runTest {
        val failure = indexFailure(CORPUS, RulesParserLimits(maxEntries = 3)) as RulesParseFailure.LimitExceeded
        assertEquals(RulesLimit.ENTRIES, failure.limit)
        assertEquals(4L, failure.observedAtLeast)
    }

    @Test
    fun entryByteLimitFails() = runTest {
        val failure = indexFailure(CORPUS, RulesParserLimits(maxEntryBytes = 40)) as RulesParseFailure.LimitExceeded
        assertEquals(RulesLimit.ENTRY_BYTES, failure.limit)
    }

    @Test
    fun continuationLineLimitFails() = runTest {
        val text = "100.1. Synthetic rule.\nline one\nline two\nline three\n"
        val failure = indexFailure(text, RulesParserLimits(maxContinuationLines = 2)) as RulesParseFailure.LimitExceeded
        assertEquals(RulesLimit.CONTINUATION_LINES, failure.limit)
        assertEquals(3L, failure.observedAtLeast)
    }

    @Test
    fun referencesPerEntryLimitFails() = runTest {
        val failure = indexFailure(CORPUS, RulesParserLimits(maxReferencesPerEntry = 3)) as RulesParseFailure.LimitExceeded
        assertEquals(RulesLimit.REFERENCES_PER_ENTRY, failure.limit)
        assertEquals(4L, failure.observedAtLeast)
    }

    @Test
    fun rangeExpansionLimitFails() = runTest {
        val failure = indexFailure(CORPUS, RulesParserLimits(maxRangeExpansion = 2)) as RulesParseFailure.LimitExceeded
        assertEquals(RulesLimit.RANGE_EXPANSION, failure.limit)
        assertEquals(3L, failure.observedAtLeast)
    }

    @Test
    fun inputByteLimitFailsBeforeReadingFurther() = runTest {
        val failure = indexFailure(CORPUS, RulesParserLimits(maxInputBytes = 100)) as RulesParseFailure.LimitExceeded
        assertEquals(RulesLimit.INPUT_BYTES, failure.limit)
    }

    @Test
    fun parsePassEmitsCoreEntriesWithoutRetainingThem() = runTest {
        // The callback receives each entry once; the parser returns only counts.
        val seen = ArrayList<RuleEntry>()
        val parser = RulesTextParser()
        val index = (parser.index(chunks(CORPUS), NeverCancelled) as RulesParseResult.Ok).value
        val summary = (parser.parse(chunks(CORPUS), index, PROVENANCE, CORPUS_ID, NeverCancelled) { seen += it } as RulesParseResult.Ok).value
        assertEquals(seen.size.toLong(), summary.entries)
        assertEquals(seen.map { it.id.raw }.toSet().size, seen.size)
    }

    @Test
    fun tokenCancelledDuringTheLastEntryCallbackNeverReturnsOk() = runTest {
        val parser = RulesTextParser()
        val index = (parser.index(chunks(CORPUS), NeverCancelled) as RulesParseResult.Ok).value
        val token = SwitchToken()
        var result: RulesParseResult<RulesParseSummary>? = null
        try {
            // The callback does not suspend, so only the parser's own check can notice the cancel.
            result = parser.parse(chunks(CORPUS), index, PROVENANCE, CORPUS_ID, token) { entry ->
                if (entry.id.raw == "credits:2") token.cancelled = true
            }
            fail("Expected cancellation, got $result")
        } catch (expected: CancellationException) {
            assertEquals(null, result)
        }
    }

    @Test
    fun coroutineCancelledDuringTheLastEntryCallbackNeverReturnsOk() = runTest {
        val parser = RulesTextParser()
        val index = (parser.index(chunks(CORPUS), NeverCancelled) as RulesParseResult.Ok).value
        var result: RulesParseResult<RulesParseSummary>? = null
        val job = launch {
            result = parser.parse(chunks(CORPUS), index, PROVENANCE, CORPUS_ID, NeverCancelled) { entry ->
                if (entry.id.raw == "credits:2") currentCoroutineContext().cancel()
            }
        }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(null, result)
    }

    @Test
    fun cancelledCoroutineWithALiveTokenAndNonSuspendingInputEmitsNothing() = runTest {
        val parser = RulesTextParser()
        val index = (parser.index(chunks(CORPUS), NeverCancelled) as RulesParseResult.Ok).value
        // asFlow() does not check cancellation itself, so the parser's checks are the only guard.
        val input = chunks(CORPUS, size = 16).toList().asFlow()
        var emitted = 0
        var result: RulesParseResult<RulesParseSummary>? = null
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            coroutineContext.job.cancel()
            result = parser.parse(input, index, PROVENANCE, CORPUS_ID, NeverCancelled) { emitted++ }
        }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(0, emitted)
        assertEquals(null, result)
    }

    @Test
    fun tokenCancelledAfterTheFirstEntryOfOneFinalChunkThrowsAndEmitsNothingMore() = runTest {
        // Review finding F1: one final chunk holds the whole synthetic corpus, so a per-chunk check alone misses this.
        val parser = RulesTextParser()
        val index = (parser.index(chunks(CORPUS), NeverCancelled) as RulesParseResult.Ok).value
        val token = SwitchToken()
        val seen = ArrayList<String>()
        var result: RulesParseResult<RulesParseSummary>? = null
        try {
            result = parser.parse(chunks(CORPUS, size = 1 shl 20), index, PROVENANCE, CORPUS_ID, token) { entry ->
                seen += entry.id.raw
                token.cancelled = true
            }
            fail("Expected cancellation, got $result")
        } catch (expected: CancellationException) {
            assertEquals(listOf("front:1"), seen)
            assertEquals(null, result)
        }
    }

    @Test
    fun indexPassNoticesTokenCancellationInsideOneFinalChunk() = runTest {
        var result: RulesParseResult<RulesIndex>? = null
        try {
            result = RulesTextParser().index(chunks(CORPUS, size = 1 shl 20), CountdownToken(remaining = 5))
            fail("Expected cancellation, got $result")
        } catch (expected: CancellationException) {
            assertEquals(null, result)
        }
    }

    /** Lets [remaining] checks pass, then reports cancellation the way a cancelled caller would. */
    private class CountdownToken(private var remaining: Int) : CancelToken {
        override val isCancelled: Boolean get() = remaining <= 0
        override fun throwIfCancelled() {
            if (remaining-- <= 0) throw CancellationException("Synthetic caller cancelled")
        }
    }

    /** A caller's token that the test flips at a chosen moment. */
    private class SwitchToken : CancelToken {
        var cancelled = false
        override val isCancelled: Boolean get() = cancelled
        override fun throwIfCancelled() {
            if (cancelled) throw CancellationException("Synthetic caller cancelled")
        }
    }
}
