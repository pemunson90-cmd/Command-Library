package com.architectpsg.commandlibrary.feature.rules.parser

import com.architectpsg.commandlibrary.core.model.ByteChunk
import com.architectpsg.commandlibrary.core.model.CancelToken
import com.architectpsg.commandlibrary.core.model.GenerationId
import com.architectpsg.commandlibrary.core.model.Knowledge
import com.architectpsg.commandlibrary.core.model.Provenance
import com.architectpsg.commandlibrary.core.model.ProviderId
import com.architectpsg.commandlibrary.core.model.RuleEntry
import com.architectpsg.commandlibrary.core.model.SnapshotBytes
import com.architectpsg.commandlibrary.core.model.SnapshotList
import com.architectpsg.commandlibrary.core.model.UtcTimestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * SYNTHETIC test inputs only. Nothing here is official rules text; the structure imitates the
 * Comprehensive Rules layout recorded in research R-03 so the parser's grammar can be exercised.
 */
internal object SyntheticRules {
    val CORPUS: String = """
        SYNTHETIC TEST RULES (not official text)

        These synthetic rules exist only for parser tests.

        Contents

        1. Synthetic Concepts
        100. Synthetic General
        101. Synthetic Ordering
        2. Synthetic Parts
        200. Synthetic Parts General
        Glossary
        Credits

        1. Synthetic Concepts

        100. Synthetic General

        100.1. A synthetic rule for every synthetic game. See rule 200.1.

        100.1a A synthetic subrule.

        Example: A synthetic example for rule 100.1a.

        100.2. Synthetic rules 100.1a-c are listed here; rule 999.9 does not exist.
        A synthetic continuation line.

        101. Synthetic Ordering

        101.1. Refers back to rule 100.1 and forward to section 2.

        2. Synthetic Parts

        200. Synthetic Parts General

        200.1. A synthetic part rule.

        Glossary

        Synthetic Term
        A synthetic glossary definition. See rule 100.1.

        Synthetic Term
        A second synthetic definition with the same term.

        Credits

        Synthetic credits line one.

        Synthetic credits block two.
    """.trimIndent() + "\n"

    val PROVIDER: ProviderId = ProviderId("synthetic-test-provider")
    val CORPUS_ID: GenerationId = GenerationId("synthetic-corpus-1")

    /** Truthfully unknown version, dates and effective date, as a caller without source evidence must supply. */
    val PROVENANCE: Provenance = Provenance(
        provider = PROVIDER,
        source = null,
        version = Knowledge.Unknown("Synthetic input has no version", SnapshotList.empty()),
        sourceDate = Knowledge.Unknown("Synthetic input has no source date", SnapshotList.empty()),
        fetchedAt = UtcTimestamp(0),
        effectiveAt = Knowledge.Unknown("Synthetic input states no effective date", SnapshotList.empty()),
        generation = CORPUS_ID,
        checksum = null,
        notice = null,
    )

    /** Splits [bytes] into contiguous chunks of at most [size] bytes; the last is final when [final] is true. */
    fun chunks(bytes: ByteArray, size: Int = 4096, final: Boolean = true): Flow<ByteChunk> = flow {
        if (bytes.isEmpty()) {
            emit(ByteChunk(0, 0, SnapshotBytes(ByteArray(0)), null, final))
            return@flow
        }
        var sequence = 0L
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + size, bytes.size)
            emit(ByteChunk(sequence++, offset.toLong(), SnapshotBytes(bytes.copyOfRange(offset, end)), null, final && end == bytes.size))
            offset = end
        }
    }

    fun chunks(text: String, size: Int = 4096): Flow<ByteChunk> = chunks(text.toByteArray(Charsets.UTF_8), size)

    object NeverCancelled : CancelToken {
        override val isCancelled: Boolean = false
        override fun throwIfCancelled() = Unit
    }

    class ParsedCorpus(val entries: List<RuleEntry>, val result: RulesParseResult<RulesParseSummary>) {
        val summary: RulesParseSummary get() = (result as RulesParseResult.Ok).value
        fun entry(id: String): RuleEntry = entries.single { it.id.raw == id }
    }

    /** Runs both passes over the same bytes, failing the test if the index pass fails. */
    suspend fun parseAll(
        text: String,
        limits: RulesParserLimits = RulesParserLimits(),
        chunkSize: Int = 4096,
    ): ParsedCorpus {
        val parser = RulesTextParser(limits)
        val index = when (val indexed = parser.index(chunks(text, chunkSize), NeverCancelled)) {
            is RulesParseResult.Ok -> indexed.value
            is RulesParseResult.Failed -> throw AssertionError("Index pass failed: ${indexed.failure}")
        }
        val entries = ArrayList<RuleEntry>()
        val result = parser.parse(chunks(text, chunkSize), index, PROVENANCE, CORPUS_ID, NeverCancelled) { entries += it }
        return ParsedCorpus(entries, result)
    }

    /** The failure of the index pass, which runs the same structure, reader and limits as the parse pass. */
    suspend fun indexFailure(text: String, limits: RulesParserLimits = RulesParserLimits()): RulesParseFailure =
        indexFailure(chunks(text), limits)

    suspend fun indexFailure(chunks: Flow<ByteChunk>, limits: RulesParserLimits = RulesParserLimits()): RulesParseFailure =
        when (val indexed = RulesTextParser(limits).index(chunks, NeverCancelled)) {
            is RulesParseResult.Failed -> indexed.failure
            is RulesParseResult.Ok -> throw AssertionError("Expected the index pass to fail")
        }
}
