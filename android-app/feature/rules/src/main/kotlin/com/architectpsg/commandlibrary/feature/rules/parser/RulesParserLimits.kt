package com.architectpsg.commandlibrary.feature.rules.parser

/**
 * Parser safeguards, not facts about any corpus. Input over a limit fails with
 * [RulesParseFailure.LimitExceeded]; nothing is truncated. Root reviews these against genuine bytes.
 *
 * Each limit has a hard ceiling: a caller may tighten the defaults but cannot configure the parser
 * into unbounded memory or work. The index pass retains one identifier per entry, and one entry's
 * lines, text and references are held while it is assembled, so entries, entry bytes, line bytes,
 * continuation lines and references bound memory directly.
 */
internal data class RulesParserLimits(
    val maxInputBytes: Long = 8L * 1024 * 1024,
    val maxLineBytes: Int = 16 * 1024,
    val maxEntries: Int = 20_000,
    /** Accumulated lines of one entry, including blank lines later trimmed, joined by one byte each. */
    val maxEntryBytes: Int = 64 * 1024,
    /** Lines after an entry's first line, blank lines included. */
    val maxContinuationLines: Int = 512,
    val maxReferencesPerEntry: Int = 256,
    /** Targets produced by one letter range; a range never spans more than the letters a to z. */
    val maxRangeExpansion: Int = 26,
) {
    init {
        require(maxInputBytes in 1..INPUT_BYTES_CEILING) { "maxInputBytes must be in 1..$INPUT_BYTES_CEILING" }
        require(maxLineBytes in 1..LINE_BYTES_CEILING) { "maxLineBytes must be in 1..$LINE_BYTES_CEILING" }
        require(maxEntries in 1..ENTRIES_CEILING) { "maxEntries must be in 1..$ENTRIES_CEILING" }
        require(maxEntryBytes in 1..ENTRY_BYTES_CEILING) { "maxEntryBytes must be in 1..$ENTRY_BYTES_CEILING" }
        require(maxContinuationLines in 0..CONTINUATION_LINES_CEILING) {
            "maxContinuationLines must be in 0..$CONTINUATION_LINES_CEILING"
        }
        require(maxReferencesPerEntry in 0..REFERENCES_PER_ENTRY_CEILING) {
            "maxReferencesPerEntry must be in 0..$REFERENCES_PER_ENTRY_CEILING"
        }
        require(maxRangeExpansion in 1..RANGE_EXPANSION_CEILING) { "maxRangeExpansion must be in 1..$RANGE_EXPANSION_CEILING" }
    }

    companion object {
        // Proposed ceilings, four to five times the proposed defaults; root reviews both against genuine bytes.
        const val INPUT_BYTES_CEILING: Long = 32L * 1024 * 1024
        const val LINE_BYTES_CEILING: Int = 64 * 1024
        const val ENTRIES_CEILING: Int = 100_000
        const val ENTRY_BYTES_CEILING: Int = 256 * 1024
        const val CONTINUATION_LINES_CEILING: Int = 2_048
        const val REFERENCES_PER_ENTRY_CEILING: Int = 1_024
        const val RANGE_EXPANSION_CEILING: Int = 26
    }
}

internal enum class RulesLimit {
    INPUT_BYTES,
    LINE_BYTES,
    ENTRIES,
    ENTRY_BYTES,
    CONTINUATION_LINES,
    REFERENCES_PER_ENTRY,
    RANGE_EXPANSION,
}
