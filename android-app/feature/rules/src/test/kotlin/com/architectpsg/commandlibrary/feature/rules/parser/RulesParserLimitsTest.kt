package com.architectpsg.commandlibrary.feature.rules.parser

import com.architectpsg.commandlibrary.feature.rules.parser.SyntheticRules.indexFailure
import com.architectpsg.commandlibrary.feature.rules.parser.SyntheticRules.parseAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every input in this class is SYNTHETIC. Limits are parser safeguards, not facts about any corpus. */
class RulesParserLimitsTest {

    @Test
    fun ceilingsAreAcceptedAndDefaultsSitBelowThem() {
        val atCeilings = RulesParserLimits(
            maxInputBytes = RulesParserLimits.INPUT_BYTES_CEILING,
            maxLineBytes = RulesParserLimits.LINE_BYTES_CEILING,
            maxEntries = RulesParserLimits.ENTRIES_CEILING,
            maxEntryBytes = RulesParserLimits.ENTRY_BYTES_CEILING,
            maxContinuationLines = RulesParserLimits.CONTINUATION_LINES_CEILING,
            maxReferencesPerEntry = RulesParserLimits.REFERENCES_PER_ENTRY_CEILING,
            maxRangeExpansion = RulesParserLimits.RANGE_EXPANSION_CEILING,
            maxIdentifierBytes = RulesParserLimits.IDENTIFIER_BYTES_CEILING,
        )
        val defaults = RulesParserLimits()
        assertTrue(defaults.maxInputBytes <= atCeilings.maxInputBytes)
        assertTrue(defaults.maxLineBytes <= atCeilings.maxLineBytes)
        assertTrue(defaults.maxEntries <= atCeilings.maxEntries)
        assertTrue(defaults.maxEntryBytes <= atCeilings.maxEntryBytes)
        assertTrue(defaults.maxContinuationLines <= atCeilings.maxContinuationLines)
        assertTrue(defaults.maxReferencesPerEntry <= atCeilings.maxReferencesPerEntry)
        assertTrue(defaults.maxRangeExpansion <= atCeilings.maxRangeExpansion)
        assertTrue(defaults.maxIdentifierBytes <= atCeilings.maxIdentifierBytes)
        // Zero is a meaningful setting for these two: no continuation lines, no references.
        assertEquals(0, RulesParserLimits(maxContinuationLines = 0, maxReferencesPerEntry = 0).maxContinuationLines)
    }

    @Test
    fun limitsAboveTheirCeilingOrBelowTheirFloorAreRejected() {
        val rejected = listOf(
            { RulesParserLimits(maxInputBytes = 0) },
            { RulesParserLimits(maxInputBytes = RulesParserLimits.INPUT_BYTES_CEILING + 1) },
            { RulesParserLimits(maxInputBytes = Long.MAX_VALUE) },
            { RulesParserLimits(maxLineBytes = 0) },
            { RulesParserLimits(maxLineBytes = RulesParserLimits.LINE_BYTES_CEILING + 1) },
            { RulesParserLimits(maxEntries = 0) },
            { RulesParserLimits(maxEntries = RulesParserLimits.ENTRIES_CEILING + 1) },
            { RulesParserLimits(maxEntries = Int.MAX_VALUE) },
            { RulesParserLimits(maxEntryBytes = 0) },
            { RulesParserLimits(maxEntryBytes = RulesParserLimits.ENTRY_BYTES_CEILING + 1) },
            { RulesParserLimits(maxContinuationLines = -1) },
            { RulesParserLimits(maxContinuationLines = RulesParserLimits.CONTINUATION_LINES_CEILING + 1) },
            { RulesParserLimits(maxReferencesPerEntry = -1) },
            { RulesParserLimits(maxReferencesPerEntry = RulesParserLimits.REFERENCES_PER_ENTRY_CEILING + 1) },
            { RulesParserLimits(maxRangeExpansion = 0) },
            { RulesParserLimits(maxRangeExpansion = RulesParserLimits.RANGE_EXPANSION_CEILING + 1) },
            { RulesParserLimits(maxIdentifierBytes = 0) },
            { RulesParserLimits(maxIdentifierBytes = RulesParserLimits.IDENTIFIER_BYTES_CEILING + 1) },
        )
        for ((i, build) in rejected.withIndex()) {
            assertThrows("case $i", IllegalArgumentException::class.java) { build() }
        }
    }

    /** One synthetic input per limit, the exact amount it uses, and the limits that set only that bound. */
    private class Bound(val limit: RulesLimit, val text: String, val used: Long, val limits: (Long) -> RulesParserLimits)

    private val bounds = listOf(
        // 19 bytes in total.
        Bound(RulesLimit.INPUT_BYTES, "100.1. Synthetic.\n\n", 19) { RulesParserLimits(maxInputBytes = it) },
        // The longest line, "100.1. Synthetic abc", is 20 bytes.
        Bound(RulesLimit.LINE_BYTES, "100.1. Synthetic abc\n", 20) { RulesParserLimits(maxLineBytes = it.toInt()) },
        Bound(RulesLimit.ENTRIES, "100.1. One.\n\n100.2. Two.\n\n100.3. Three.\n", 3) { RulesParserLimits(maxEntries = it.toInt()) },
        // "100.1. Synthetic." (17) + "\n" + "more" (4) = 22 bytes of entry text.
        Bound(RulesLimit.ENTRY_BYTES, "100.1. Synthetic.\nmore\n", 22) { RulesParserLimits(maxEntryBytes = it.toInt()) },
        Bound(RulesLimit.CONTINUATION_LINES, "100.1. Synthetic.\none\ntwo\n", 2) { RulesParserLimits(maxContinuationLines = it.toInt()) },
        Bound(RulesLimit.REFERENCES_PER_ENTRY, "100.1. See rules 100.1 and 100.2.\n\n100.2. Two.\n", 2) {
            RulesParserLimits(maxReferencesPerEntry = it.toInt())
        },
        Bound(RulesLimit.RANGE_EXPANSION, "100.1. See rules 100.1a-c.\n", 3) { RulesParserLimits(maxRangeExpansion = it.toInt()) },
        // The longest identifier, "glossary:Abcdef", is 15 bytes.
        Bound(RulesLimit.IDENTIFIER_BYTES, "100.1. Synthetic.\n\nGlossary\n\nAbcdef\nSynthetic definition.\n", 15) {
            RulesParserLimits(maxIdentifierBytes = it.toInt())
        },
    )

    @Test
    fun everyLimitIsCoveredBySomeBoundCase() {
        assertEquals(RulesLimit.entries.toSet(), bounds.map { it.limit }.toSet())
    }

    @Test
    fun everyLimitAcceptsInputThatUsesExactlyTheLimit() = runTest {
        for (bound in bounds) {
            val parsed = parseAll(bound.text, bound.limits(bound.used))
            assertTrue("${bound.limit} at ${bound.used}", parsed.result is RulesParseResult.Ok)
        }
    }

    @Test
    fun everyLimitRejectsInputThatUsesOneMoreThanTheLimit() = runTest {
        for (bound in bounds) {
            val failure = indexFailure(bound.text, bound.limits(bound.used - 1))
            assertEquals(
                "${bound.limit} at ${bound.used - 1}",
                RulesParseFailure.LimitExceeded(bound.limit, bound.used - 1, bound.used),
                failure,
            )
        }
    }

    @Test
    fun oversizedChunkIsRejectedWithItsFullSize() = runTest {
        val text = "100.1. Synthetic.\n".repeat(10)
        val failure = indexFailure(SyntheticRules.chunks(text, size = 1 shl 20), RulesParserLimits(maxInputBytes = 20))
        assertEquals(RulesParseFailure.LimitExceeded(RulesLimit.INPUT_BYTES, 20, text.length.toLong()), failure)
    }
}
