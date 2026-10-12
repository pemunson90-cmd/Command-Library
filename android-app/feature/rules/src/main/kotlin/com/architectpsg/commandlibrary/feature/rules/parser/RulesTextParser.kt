package com.architectpsg.commandlibrary.feature.rules.parser

import com.architectpsg.commandlibrary.core.model.ByteChunk
import com.architectpsg.commandlibrary.core.model.CancelToken
import com.architectpsg.commandlibrary.core.model.ContentHash
import com.architectpsg.commandlibrary.core.model.Evidence
import com.architectpsg.commandlibrary.core.model.GenerationId
import com.architectpsg.commandlibrary.core.model.Knowledge
import com.architectpsg.commandlibrary.core.model.Provenance
import com.architectpsg.commandlibrary.core.model.RuleCrossReference
import com.architectpsg.commandlibrary.core.model.RuleEntry
import com.architectpsg.commandlibrary.core.model.RuleId
import com.architectpsg.commandlibrary.core.model.SnapshotList
import kotlinx.coroutines.flow.Flow

/**
 * Streaming parser for Comprehensive Rules style UTF-8 text. It reads the same bytes twice and never
 * holds the corpus: [index] keeps only entry identifiers, byte count and SHA-256; [parse] emits every
 * entry in document order with each cross-reference resolved against that index.
 *
 * The caller supplies [Provenance] and the corpus generation; both are copied unchanged into every
 * entry. The parser never infers a provider, source version, source date or effective date.
 * A [RulesParseResult.Failed] from [parse] means every entry it emitted must be discarded.
 *
 * Cancellation (the coroutine's job or the caller's [CancelToken]) is checked for every chunk and
 * line, before and after every `onEntry` call, and before a result is returned, so a cancelled
 * caller always receives `CancellationException` and never [RulesParseResult.Ok].
 */
internal class RulesTextParser(private val limits: RulesParserLimits = RulesParserLimits()) {
    private val scanner = RuleReferenceScanner(limits.maxRangeExpansion)

    suspend fun index(chunks: Flow<ByteChunk>, cancel: CancelToken): RulesParseResult<RulesIndex> = attempt {
        val identifiers = HashSet<String>()
        val structure = RulesStructure(limits) { draft ->
            identifiers += draft.id
            // Same bound as the parse pass, so an over-referenced entry fails before anything is emitted.
            referenceCount(draft)
        }
        var lastLine = 0L
        val read = readUtf8Lines(chunks, limits, cancel) { line, number ->
            lastLine = number
            structure.line(line, number)
        }
        structure.finish(lastLine)
        cancel.checkpoint()
        RulesIndex(identifiers, read.byteCount, ContentHash(SHA_256, read.sha256Hex))
    }

    suspend fun parse(
        chunks: Flow<ByteChunk>,
        index: RulesIndex,
        provenance: Provenance,
        corpusId: GenerationId,
        cancel: CancelToken,
        onEntry: suspend (RuleEntry) -> Unit,
    ): RulesParseResult<RulesParseSummary> = attempt {
        var ordinal = 0L
        var references = 0L
        var unresolved = 0L
        val structure = RulesStructure(limits) { draft ->
            val crossReferences = resolve(draft, index, provenance)
            references += crossReferences.size
            unresolved += crossReferences.count { it.resolved !is Knowledge.Known }
            val entry = RuleEntry(
                id = RuleId(provenance.provider, draft.id),
                corpusId = corpusId,
                kind = draft.kind,
                number = draft.number?.let { Knowledge.Known(it) } ?: Knowledge.Absent(NO_NUMBER),
                title = draft.title?.let { Knowledge.Known(it) } ?: Knowledge.Absent(NO_TITLE),
                text = draft.text,
                ordinal = ordinal++,
                references = SnapshotList.copyOf(crossReferences),
                provenance = provenance,
            )
            cancel.checkpoint()
            onEntry(entry)
            // A callback that does not suspend gives the coroutine no other chance to notice a cancel.
            cancel.checkpoint()
        }
        var lastLine = 0L
        val read = readUtf8Lines(chunks, limits, cancel) { line, number ->
            lastLine = number
            structure.line(line, number)
        }
        val stats = structure.finish(lastLine)
        if (read.byteCount != index.byteCount || !read.sha256Hex.equals(index.sha256.hex, ignoreCase = true)) {
            throw RulesParseException(
                RulesParseFailure.SourceChanged(index.byteCount, index.sha256.hex, read.byteCount, read.sha256Hex),
            )
        }
        cancel.checkpoint()
        RulesParseSummary(
            entries = ordinal,
            entriesByKind = stats.entriesByKind,
            references = references,
            unresolvedReferences = unresolved,
            duplicateIdentifiers = stats.duplicateIdentifiers,
            anomalies = stats.anomalies,
            firstAnomalies = stats.firstAnomalies,
            byteCount = read.byteCount,
            sha256 = ContentHash(SHA_256, read.sha256Hex),
        )
    }

    private fun referenceCount(draft: DraftEntry): Int {
        val count = distinctReferences(draft).size
        if (count > limits.maxReferencesPerEntry) {
            throw RulesParseException(
                RulesParseFailure.LimitExceeded(RulesLimit.REFERENCES_PER_ENTRY, limits.maxReferencesPerEntry.toLong(), count.toLong()),
            )
        }
        return count
    }

    /** Each (written target, identifier) pair once, in first-occurrence order. */
    private fun distinctReferences(draft: DraftEntry): List<Pair<String, String>> {
        val pairs = LinkedHashSet<Pair<String, String>>()
        for (reference in scanner.scan(draft.text)) {
            for (identifier in reference.identifiers) pairs += reference.raw to identifier
        }
        return pairs.toList()
    }

    private fun resolve(draft: DraftEntry, index: RulesIndex, provenance: Provenance): List<RuleCrossReference> {
        referenceCount(draft)
        return distinctReferences(draft).map { (raw, identifier) ->
            val resolved: Knowledge<RuleId> = if (index.contains(identifier)) {
                Knowledge.Known(RuleId(provenance.provider, identifier))
            } else {
                Knowledge.Unknown(
                    UNRESOLVED,
                    SnapshotList.of(Evidence("Cross-reference target $identifier is not an entry of this corpus", provenance.source, null)),
                )
            }
            RuleCrossReference(raw, resolved, SnapshotList.empty())
        }
    }

    private inline fun <T> attempt(block: () -> T): RulesParseResult<T> =
        try {
            RulesParseResult.Ok(block())
        } catch (failure: RulesParseException) {
            RulesParseResult.Failed(failure.failure)
        }

    private companion object {
        const val SHA_256 = "SHA-256"
        const val NO_NUMBER = "The source gives this entry no rule number"
        const val NO_TITLE = "The source gives this entry no title"
        const val UNRESOLVED = "Target not found in this corpus"
    }
}
