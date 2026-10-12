package com.architectpsg.commandlibrary.feature.rules.parser

import com.architectpsg.commandlibrary.core.model.Knowledge
import com.architectpsg.commandlibrary.core.model.RuleEntry
import com.architectpsg.commandlibrary.feature.rules.parser.SyntheticRules.CORPUS
import com.architectpsg.commandlibrary.feature.rules.parser.SyntheticRules.CORPUS_ID
import com.architectpsg.commandlibrary.feature.rules.parser.SyntheticRules.NeverCancelled
import com.architectpsg.commandlibrary.feature.rules.parser.SyntheticRules.PROVENANCE
import com.architectpsg.commandlibrary.feature.rules.parser.SyntheticRules.chunks
import java.util.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * SYNTHETIC robustness check: seeded edits of the synthetic corpus, including invalid UTF-8 and
 * structure-like fragments, must end in Ok or a typed failure and never in any other exception.
 * When the index pass succeeds, the parse pass over the same bytes must succeed and agree with it.
 * The fixed seed makes every run identical; this says nothing about genuine rules files.
 */
class RulesParserMutationTest {

    @Test
    fun seededEditsEndInOkOrATypedFailure() = runTest {
        val random = Random(SEED)
        val base = CORPUS.toByteArray(Charsets.UTF_8)
        var ok = 0
        var failed = 0
        repeat(ROUNDS) { round ->
            val bytes = mutate(base, random)
            val chunkSize = 1 + random.nextInt(97)
            val parser = RulesTextParser()
            val indexed = try {
                parser.index(chunks(bytes, chunkSize), NeverCancelled)
            } catch (unexpected: RuntimeException) {
                throw AssertionError("Round $round: index pass threw instead of returning a result", unexpected)
            }
            when (indexed) {
                is RulesParseResult.Failed -> failed++
                is RulesParseResult.Ok -> {
                    ok++
                    val entries = ArrayList<RuleEntry>()
                    val parsed = try {
                        parser.parse(chunks(bytes, chunkSize), indexed.value, PROVENANCE, CORPUS_ID, NeverCancelled) { entries += it }
                    } catch (unexpected: RuntimeException) {
                        throw AssertionError("Round $round: parse pass threw instead of returning a result", unexpected)
                    }
                    val summary = (parsed as? RulesParseResult.Ok)?.value
                        ?: throw AssertionError("Round $round: index succeeded but parse returned $parsed")
                    checkConsistent(round, indexed.value, summary, entries)
                }
            }
        }
        // Both outcomes must actually occur, or the edits are not exercising the parser.
        assertTrue("ok=$ok", ok > ROUNDS / 10)
        assertTrue("failed=$failed", failed > ROUNDS / 10)
    }

    private fun checkConsistent(round: Int, index: RulesIndex, summary: RulesParseSummary, entries: List<RuleEntry>) {
        val ids = entries.map { it.id.raw }
        assertEquals("Round $round: unique identifiers", ids.size, ids.toSet().size)
        assertEquals("Round $round: index holds exactly the emitted identifiers", index.identifiers, ids.toSet())
        assertEquals("Round $round: ordinals", (0L until entries.size).toList(), entries.map { it.ordinal })
        assertEquals("Round $round: entry count", entries.size.toLong(), summary.entries)
        for (entry in entries) {
            for (reference in entry.references) {
                when (val resolved = reference.resolved) {
                    is Knowledge.Known -> assertTrue("Round $round: known target", index.contains(resolved.value.raw))
                    is Knowledge.Unknown -> assertTrue("Round $round: unknown target has evidence", resolved.evidence.isNotEmpty())
                    is Knowledge.Absent -> fail("Round $round: references are Known or Unknown, never Absent")
                }
            }
        }
    }

    private fun mutate(base: ByteArray, random: Random): ByteArray {
        val out = base.toMutableList()
        repeat(1 + random.nextInt(4)) {
            when (random.nextInt(5)) {
                // Any byte, which often breaks UTF-8.
                0 -> if (out.isNotEmpty()) out[random.nextInt(out.size)] = random.nextInt(256).toByte()
                // Delete a short run.
                1 -> if (out.isNotEmpty()) {
                    val start = random.nextInt(out.size)
                    val end = minOf(out.size, start + 1 + random.nextInt(40))
                    out.subList(start, end).clear()
                }
                // Insert a fragment that looks like structure.
                2, 3 -> {
                    val fragment = FRAGMENTS[random.nextInt(FRAGMENTS.size)].toByteArray(Charsets.UTF_8)
                    out.addAll(random.nextInt(out.size + 1), fragment.toList())
                }
                // Duplicate a run, which repeats headings and identifiers.
                else -> if (out.isNotEmpty()) {
                    val start = random.nextInt(out.size)
                    val end = minOf(out.size, start + 1 + random.nextInt(120))
                    out.addAll(end, out.subList(start, end).toList())
                }
            }
        }
        return out.toByteArray()
    }

    private companion object {
        const val SEED = 20261012L
        const val ROUNDS = 400
        val FRAGMENTS = listOf(
            "\n", "\r", "\r\n", "\n\n", "\uFEFF", "1. Synthetic\n", "3. Synthetic\n", "100. Synthetic\n", "999. Synthetic\n",
            "100.9 ", "100.1z ", "\n100.1. Synthetic\n", "\nExample: synthetic\n", "\nContents\n", "\nGlossary\n",
            "\nCredits\n", "rule ", "rules 100.1a-", "rules 100.1a-z", "section 9", "~2", "\u2013", "\u00E9", "\uD83C\uDCCF",
        )
    }
}
