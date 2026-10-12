package com.architectpsg.commandlibrary.feature.rules.parser

import com.architectpsg.commandlibrary.core.model.ContentHash
import com.architectpsg.commandlibrary.core.model.RuleEntryKind

/** A parse either completes or fails with a typed reason. Cancellation is thrown, never returned. */
internal sealed interface RulesParseResult<out T> {
    data class Ok<T>(val value: T) : RulesParseResult<T>
    data class Failed(val failure: RulesParseFailure) : RulesParseResult<Nothing>
}

internal sealed interface RulesParseFailure {
    /** The bytes at [byteOffset] are not valid UTF-8, including a sequence cut off at end of input. */
    data class MalformedUtf8(val byteOffset: Long) : RulesParseFailure

    /** Chunks must start at sequence 0, offset 0 and follow each other without gaps or repeats. */
    data class NoncontiguousChunk(
        val expectedSequence: Long,
        val expectedOffset: Long,
        val actualSequence: Long,
        val actualOffset: Long,
    ) : RulesParseFailure

    /** A chunk arrived after the chunk marked final. */
    data object ChunkAfterFinal : RulesParseFailure

    /** The stream ended without a chunk marked final, so the input may be cut short. */
    data object Truncated : RulesParseFailure

    /** [observedAtLeast] is a lower bound: reading stops as soon as the limit is passed. */
    data class LimitExceeded(val limit: RulesLimit, val maximum: Long, val observedAtLeast: Long) : RulesParseFailure

    /** The text does not have the structure the grammar requires; [line] is 1-based. */
    data class Structure(val line: Long, val reason: String) : RulesParseFailure

    /** The input decoded but contains no numbered rule, so it is not a rules corpus. */
    data object NoNumberedRules : RulesParseFailure

    /**
     * The bytes read by [RulesTextParser.parse] differ from the bytes the index was built from. Reported
     * when the parse pass reaches the end of input; changed bytes can also stop the pass earlier with
     * another typed failure. Either way every entry the pass emitted must be discarded.
     */
    data class SourceChanged(
        val indexedBytes: Long,
        val indexedSha256: String,
        val parsedBytes: Long,
        val parsedSha256: String,
    ) : RulesParseFailure
}

/** A heading-like line the grammar did not accept as a heading and kept as text. [line] is 1-based. */
internal data class RulesAnomaly(val line: Long, val reason: String)

/**
 * Identifiers produced by the index pass, plus what the parse pass must match. Holds no entry text.
 */
internal class RulesIndex internal constructor(
    val identifiers: Set<String>,
    val byteCount: Long,
    val sha256: ContentHash,
) {
    fun contains(identifier: String): Boolean = identifier in identifiers
}

internal data class RulesParseSummary(
    val entries: Long,
    val entriesByKind: Map<RuleEntryKind, Long>,
    val references: Long,
    val unresolvedReferences: Long,
    val duplicateIdentifiers: Long,
    val anomalies: Long,
    /** At most [MAX_REPORTED_ANOMALIES] of the anomalies, in document order. */
    val firstAnomalies: List<RulesAnomaly>,
    val byteCount: Long,
    val sha256: ContentHash,
) {
    companion object {
        const val MAX_REPORTED_ANOMALIES: Int = 20
    }
}

/** Thrown inside the parser to unwind to the public entry points, which return [RulesParseResult.Failed]. */
internal class RulesParseException(val failure: RulesParseFailure) : RuntimeException(failure.toString())
