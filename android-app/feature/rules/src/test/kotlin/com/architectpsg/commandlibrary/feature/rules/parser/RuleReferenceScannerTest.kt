package com.architectpsg.commandlibrary.feature.rules.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Every input in this class is SYNTHETIC. */
class RuleReferenceScannerTest {
    private val scanner = RuleReferenceScanner(maxRangeExpansion = 26)

    private fun scan(text: String): List<Pair<String, List<String>>> = scanner.scan(text).map { it.raw to it.identifiers }

    @Test
    fun singleTargetsOfEveryDepth() {
        assertEquals(listOf("100" to listOf("100")), scan("See rule 100."))
        assertEquals(listOf("100.1" to listOf("100.1")), scan("See rule 100.1."))
        assertEquals(listOf("100.1a" to listOf("100.1a")), scan("See rule 100.1a."))
    }

    @Test
    fun keywordIsCaseInsensitiveAndNeedsWhitespace() {
        assertEquals(listOf("702.19" to listOf("702.19")), scan("Rule 702.19 applies."))
        assertEquals(emptyList<Pair<String, List<String>>>(), scan("rule702.19 is not a reference"))
    }

    @Test
    fun listsWithCommasAndConjunctions() {
        assertEquals(
            listOf("100.1" to listOf("100.1"), "100.2" to listOf("100.2"), "100.3" to listOf("100.3")),
            scan("See rules 100.1, 100.2, and 100.3."),
        )
        assertEquals(listOf("100.1" to listOf("100.1"), "100.2" to listOf("100.2")), scan("rules 100.1 or 100.2"))
    }

    @Test
    fun letterRangesExpandOnOneRule() {
        assertEquals(listOf("702.19b-d" to listOf("702.19b", "702.19c", "702.19d")), scan("See rules 702.19b-d."))
        assertEquals(listOf("702.19b\u2013d" to listOf("702.19b", "702.19c", "702.19d")), scan("See rules 702.19b\u2013d."))
        assertEquals(
            listOf("702.19b through 702.19c" to listOf("702.19b", "702.19c")),
            scan("rules 702.19b through 702.19c"),
        )
    }

    @Test
    fun otherRangesKeepOnlyTheirEnds() {
        assertEquals(listOf("100.1-100.4" to listOf("100.1", "100.4")), scan("rules 100.1-100.4"))
        assertEquals(listOf("702.19d-702.20a" to listOf("702.19d", "702.20a")), scan("rules 702.19d-702.20a"))
    }

    @Test
    fun sectionsTakeSingleDigitTargets() {
        assertEquals(listOf("7" to listOf("7")), scan("as described in section 7."))
        assertEquals(listOf("1" to listOf("1"), "2" to listOf("2")), scan("sections 1 and 2"))
        assertEquals(listOf("702" to listOf("702")), scan("section 702"))
        // A single digit after "rule" is not a reference.
        assertEquals(emptyList<Pair<String, List<String>>>(), scan("rule 7"))
    }

    @Test
    fun bareNumbersAndOtherWordsAreNotReferences() {
        assertTrue(scan("The 100.1 value and 702.19b are plain text.").isEmpty())
        assertTrue(scan("rulebook 100.1").isEmpty())
        assertTrue(scan("rule 1000 is too long a number").isEmpty())
        assertTrue(scan("rules of thumb").isEmpty())
    }

    @Test
    fun expansionBeyondTheLimitFails() {
        try {
            RuleReferenceScanner(maxRangeExpansion = 2).scan("rules 100.1a-c")
            fail("Expected the range limit to fail")
        } catch (expected: RulesParseException) {
            assertEquals(RulesParseFailure.LimitExceeded(RulesLimit.RANGE_EXPANSION, 2, 3), expected.failure)
        }
    }
}
