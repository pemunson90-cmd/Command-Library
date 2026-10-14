package com.architectpsg.commandlibrary.feature.rules.parser

import com.architectpsg.commandlibrary.core.model.Knowledge
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic audit probe; no official source payload. */
class RulesMultiLetterAuditProbeTest {
    @Test fun twoLetterHeadingIsSeparateEntry() = runTest {
        val parsed = SyntheticRules.parseAll("704. Synthetic chapter\n704.5. Synthetic parent.\n704.5z Synthetic previous rule.\n704.5aa Synthetic independent rule.\n")
        assertTrue(parsed.result is RulesParseResult.Ok)
        assertTrue("Two-letter heading must have its own identity", parsed.entries.any { it.id.raw == "704.5aa" })
        assertEquals("704.5z Synthetic previous rule.", parsed.entry("704.5z").text)
    }

    @Test fun directMultiLetterReferencesKeepTheirOwnIdentities() {
        val references = RuleReferenceScanner(maxRangeExpansion = 26).scan("See rules 704.5aa and 704.5ab.")
        assertEquals(listOf("704.5aa", "704.5ab"), references.map { it.raw })
        assertEquals(listOf(listOf("704.5aa"), listOf("704.5ab")), references.map { it.identifiers })
    }

    @Test fun multiLetterFullRangeKeepsOnlyExactEndpoints() {
        val references = RuleReferenceScanner(maxRangeExpansion = 1).scan("See rules 704.5aa-704.5ad.")
        assertEquals("704.5aa-704.5ad", references.single().raw)
        assertEquals(listOf("704.5aa", "704.5ad"), references.single().identifiers)
    }

    @Test fun mixedLengthFullRangeKeepsOnlyExactEndpoints() {
        val references = RuleReferenceScanner(maxRangeExpansion = 1).scan("See rules 704.5z through 704.5ab.")
        assertEquals("704.5z through 704.5ab", references.single().raw)
        assertEquals(listOf("704.5z", "704.5ab"), references.single().identifiers)
    }

    @Test fun multiLetterShorthandRangeKeepsOnlyExactEndpoints() {
        val references = RuleReferenceScanner(maxRangeExpansion = 1).scan("See rules 704.5aa-ad.")
        assertEquals("704.5aa-ad", references.single().raw)
        assertEquals(listOf("704.5aa", "704.5ad"), references.single().identifiers)
    }

    @Test fun mixedLengthShorthandRangeKeepsOnlyExactEndpoints() {
        val references = RuleReferenceScanner(maxRangeExpansion = 1).scan("See rules 704.5z-aa.")
        assertEquals("704.5z-aa", references.single().raw)
        assertEquals(listOf("704.5z", "704.5aa"), references.single().identifiers)
    }

    @Test fun toBeforeProseDoesNotInventAnEndpoint() {
        assertOnlyInitialTarget("See rule 704.5a to determine the result.")
    }

    @Test fun throughBeforeProseDoesNotInventAnEndpoint() {
        assertOnlyInitialTarget("See rule 704.5a through another effect.")
    }

    @Test fun dashBeforeProseDoesNotInventAnEndpoint() {
        assertOnlyInitialTarget("See rule 704.5a-because it applies.")
    }

    @Test fun wordSeparatorsDoNotAcceptMultiLetterShorthand() {
        assertOnlyInitialTarget("See rules 704.5a to aa.")
        assertOnlyInitialTarget("See rules 704.5a through aa.")
    }

    private fun assertOnlyInitialTarget(text: String) {
        val references = RuleReferenceScanner(maxRangeExpansion = 26).scan(text)
        assertEquals(listOf("704.5a"), references.map { it.raw })
        assertEquals(listOf(listOf("704.5a")), references.map { it.identifiers })
    }

    @Test fun parsedReferencesResolveToSeparateMultiLetterEntries() = runTest {
        val parsed = SyntheticRules.parseAll(
            "704. Synthetic chapter\n704.5. See rules 704.5aa and 704.5ab.\n" +
                "704.5z Synthetic previous rule.\n704.5aa Synthetic first rule.\n704.5ab Synthetic second rule.\n",
        )
        assertTrue(parsed.result is RulesParseResult.Ok)
        assertEquals(listOf("704", "704.5", "704.5z", "704.5aa", "704.5ab"), parsed.entries.map { it.id.raw })
        assertEquals("704.5z Synthetic previous rule.", parsed.entry("704.5z").text)
        val references = parsed.entry("704.5").references
        assertEquals(listOf("704.5aa", "704.5ab"), references.map { (it.resolved as Knowledge.Known).value.raw })
        assertEquals(0L, parsed.summary.unresolvedReferences)
    }
}
