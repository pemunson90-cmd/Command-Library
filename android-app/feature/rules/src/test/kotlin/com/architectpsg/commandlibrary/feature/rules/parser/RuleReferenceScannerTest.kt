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
    fun sameRuleRangesSkipOnlyIntermediateUnnumberedLetters() {
        assertEquals(listOf("111.10j-r" to listOf("111.10j", "111.10k", "111.10m", "111.10n", "111.10p", "111.10q", "111.10r")), scan("See rules 111.10j-r."))
        assertEquals(listOf("608.2c-m" to listOf("608.2c", "608.2d", "608.2e", "608.2f", "608.2g", "608.2h", "608.2i", "608.2j", "608.2k", "608.2m")), scan("See rules 608.2c-m."))
        assertEquals(listOf("111.10j through 111.10r" to listOf("111.10j", "111.10k", "111.10m", "111.10n", "111.10p", "111.10q", "111.10r")), scan("rules 111.10j through 111.10r"))
    }

    @Test
    fun explicitlyNamedSkippedLettersAndRangeEndpointsAreRetained() {
        assertEquals(listOf("111.10l" to listOf("111.10l"), "111.10o" to listOf("111.10o")), scan("rules 111.10l and 111.10o"))
        assertEquals(listOf("111.10j-l" to listOf("111.10j", "111.10k", "111.10l")), scan("rules 111.10j-l"))
        assertEquals(listOf("111.10l-o" to listOf("111.10l", "111.10m", "111.10n", "111.10o")), scan("rules 111.10l-o"))
        assertEquals(listOf("111.10o-r" to listOf("111.10o", "111.10p", "111.10q", "111.10r")), scan("rules 111.10o-r"))
    }

    @Test
    fun rangeLimitCountsActualEmittedTargets() {
        assertEquals(7, RuleReferenceScanner(maxRangeExpansion = 7).scan("rules 111.10j-r").single().identifiers.size)
        try {
            RuleReferenceScanner(maxRangeExpansion = 6).scan("rules 111.10j-r")
            fail("Expected seven emitted targets to exceed limit six")
        } catch (expected: RulesParseException) {
            assertEquals(RulesParseFailure.LimitExceeded(RulesLimit.RANGE_EXPANSION, 6, 7), expected.failure)
        }
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
