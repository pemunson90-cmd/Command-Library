package com.architectpsg.commandlibrary.feature.search

import com.architectpsg.commandlibrary.core.model.Comparison
import java.math.BigDecimal

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
    private const val SOURCE_LIMIT: Int = 8 * 1024 * 1024
    private const val DIGITS: Int = 64
    private const val EXPONENT_BIAS: Long = 2_147_483_648L

    /**
     * ASCII fixed decimals only, including a leading or trailing decimal point and optional sign.
     * Symbols, exponent notation and whitespace return null (unknown numeric comparison).
     * @throws IllegalArgumentException above the 8 Mi-character ceiling; callers must not turn a
     * budget failure into an ordinary missing value. Caller cancellation/budgets remain necessary.
     */
    public fun sourceKey(raw: String): String? {
        require(raw.length <= SOURCE_LIMIT) { "Numeric source exceeds comparison budget" }
        var at = 0
        val negative = raw.startsWith('-')
        if (raw.startsWith('-') || raw.startsWith('+')) at++
        var dot = false
        var digits = 0
        var beforeDot = 0
        var first = -1
        var last = -1
        val prefix = StringBuilder(DIGITS)
        while (at < raw.length) {
            val c = raw[at++]
            if (c == '.' && !dot) { dot = true; continue }
            if (c !in '0'..'9') return null
            if (!dot) beforeDot++
            if (c != '0') {
                if (first < 0) first = digits
                last = digits
            }
            if (first >= 0 && prefix.length < DIGITS) prefix.append(c)
            digits++
        }
        if (digits == 0) return null
        if (first < 0) return "1"
        val significant = last - first + 1
        val overflow = significant > DIGITS
        if (!overflow) prefix.setLength(significant)
        val exponent = beforeDot.toLong() - first.toLong() - 1L
        val biased = Math.addExact(EXPONENT_BIAS, exponent)
        check(biased in 0..9_999_999_999L)
        val magnitude = biased.toString().padStart(10, '0') + prefix
        return buildString(76) {
            append(if (negative) '0' else '2')
            for (c in magnitude) append(if (negative) ('9'.code - (c - '0')).toChar() else c)
            append(if (negative) { if (overflow) '/' else ':' } else { if (overflow) ':' else '/' })
        }
    }

    /**
     * Admit only values representable by the parser's <=64-character fixed decimal grammar.
     * Raw precision/scale are bounded before expansion, then redundant zeros are removed and the
     * optional leading fractional zero omitted. Bounded equivalents such as BigDecimal("1E2") work.
     * @throws IllegalArgumentException for an unsupported threshold, including extreme raw scale.
     */
    public fun queryKey(value: BigDecimal): String {
        require(value.precision() <= 128 && value.scale() in -64..64) { "Query decimal exceeds comparison budget" }
        val plain = value.stripTrailingZeros().toPlainString()
        val shortest = when {
            plain.startsWith("0.") -> plain.substring(1)
            plain.startsWith("-0.") -> "-" + plain.substring(2)
            else -> plain
        }
        require(shortest.length <= 64) { "Query decimal exceeds 64 characters" }
        return checkNotNull(sourceKey(shortest))
    }

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
