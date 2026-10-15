package com.architectpsg.commandlibrary.feature.search

import com.architectpsg.commandlibrary.core.model.Comparison
import java.math.BigDecimal
import com.architectpsg.commandlibrary.core.common.DecimalComparisonKeys

/** Missing comparison evidence remains unknown under negation and boolean composition. */
public enum class SearchTruth {
    TRUE, FALSE, UNKNOWN;

    public fun not(): SearchTruth = when (this) { TRUE -> FALSE; FALSE -> TRUE; UNKNOWN -> UNKNOWN }
    public fun and(other: SearchTruth): SearchTruth = when {
        this == FALSE || other == FALSE -> FALSE
        this == UNKNOWN || other == UNKNOWN -> UNKNOWN
        else -> TRUE
    }
    public fun or(other: SearchTruth): SearchTruth = when {
        this == TRUE || other == TRUE -> TRUE
        this == UNKNOWN || other == UNKNOWN -> UNKNOWN
        else -> FALSE
    }
}

/**
 * BINARY text keys preserve exact source-versus-admitted-query comparisons without floating point.
 * These are NOT source equality, uniqueness or general numeric sorting keys: long sources can
 * collide. No database, context, indexed execution or source knownness is established here.
 */
public object SearchNumericComparison {
    /**
     * ASCII fixed decimals only, including a leading or trailing decimal point and optional sign.
     * Symbols, exponent notation and whitespace return null (unknown numeric comparison).
     * @throws IllegalArgumentException above the 8 Mi-character ceiling; callers must not turn a
     * budget failure into an ordinary missing value. Caller cancellation/budgets remain necessary.
     */
    public fun sourceKey(raw: String): String? = DecimalComparisonKeys.sourceKey(raw)

    /**
     * Admit only values representable by the parser's <=64-character fixed decimal grammar.
     * Raw precision/scale are bounded before expansion, then redundant zeros are removed and the
     * optional leading fractional zero omitted. Bounded equivalents such as BigDecimal("1E2") work.
     * @throws IllegalArgumentException for an unsupported threshold, including extreme raw scale.
     */
    public fun queryKey(value: BigDecimal): String = DecimalComparisonKeys.queryKey(value)

    /** Query validation happens even for null/symbolic source values. No numeric unknown becomes 0. */
    public fun evaluate(raw: String?, comparison: Comparison, threshold: BigDecimal): SearchTruth {
        val query = queryKey(threshold)
        val source = raw?.let(::sourceKey) ?: return SearchTruth.UNKNOWN
        val order = source.compareTo(query)
        val matches = when (comparison) {
            Comparison.EQ -> order == 0
            Comparison.NE -> order != 0
            Comparison.LT -> order < 0
            Comparison.LE -> order <= 0
            Comparison.GT -> order > 0
            Comparison.GE -> order >= 0
        }
        return if (matches) SearchTruth.TRUE else SearchTruth.FALSE
    }
}
