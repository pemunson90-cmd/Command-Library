package com.architectpsg.commandlibrary.feature.rules.parser

/**
 * Parser safeguards, not facts about any corpus. Input over a limit fails with
 * [RulesParseFailure.LimitExceeded]; nothing is truncated. Root reviews these against genuine bytes.
 */
internal data class RulesParserLimits(
    val maxInputBytes: Long = 8L * 1024 * 1024,
    val maxLineBytes: Int = 16 * 1024,
    val maxEntries: Int = 20_000,
    val maxEntryBytes: Int = 64 * 1024,
    val maxContinuationLines: Int = 512,
    val maxReferencesPerEntry: Int = 256,
    val maxRangeExpansion: Int = 26,
) {
    init {
        require(maxInputBytes > 0)
        require(maxLineBytes > 0)
        require(maxEntries > 0)
        require(maxEntryBytes > 0)
        require(maxContinuationLines >= 0)
        require(maxReferencesPerEntry >= 0)
        require(maxRangeExpansion > 0)
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
