package com.architectpsg.commandlibrary.feature.rules.parser

import java.util.regex.Pattern

/** Digits after the chapter in a rule number (`702.190`); longer runs are not rule numbers. */
internal const val MAX_RULE_NUMBER_DIGITS: Int = 4

/** One cross-reference as written in the source and the identifiers it names. */
internal class ScannedReference(val raw: String, val identifiers: List<String>)

/**
 * Finds contextual cross-references: `rule`, `rules`, `section` or `sections` followed by one or
 * more targets. Targets are joined by commas, `and`, `or`, or one range (`-`, en dash, em dash,
 * `through`, `to`). A letter range on one rule such as `702.19b-d` or `702.19b-702.19d` expands to
 * every letter; the letter-only form needs a dash with no spaces around it. Other ranges keep only
 * their two ends, and a chain stops after one range. Only spaces and tabs may separate a keyword,
 * its targets and separators, so a reference never spans lines. Bare numbers without a keyword are
 * not references.
 *
 * Targets and separators are read character by character in one forward pass per keyword, so time
 * is linear in the text on every regex engine.
 */
internal class RuleReferenceScanner(private val maxRangeExpansion: Int) {

    fun scan(text: String): List<ScannedReference> {
        val found = ArrayList<ScannedReference>()
        scanEach(text) { found += it }
        return found
    }

    /** Hands each reference to [onReference] as soon as it is complete, in text order. */
    fun scanEach(text: String, onReference: (ScannedReference) -> Unit) {
        val keywords = KEYWORD.matcher(text)
        while (keywords.find()) {
            val section = text[keywords.start()].lowercaseChar() == 's'
            val afterKeyword = keywords.end()
            var position = skipBlanks(text, afterKeyword)
            if (position == afterKeyword) continue
            var previous = target(text, position, section) ?: continue
            var pending = single(text, previous)
            var lastWasRange = false
            position = previous.end
            while (true) {
                val separator = separator(text, position) ?: break
                if (separator.range) {
                    if (lastWasRange) break
                    val end = (if (separator.tight) letterShorthand(text, separator.next, previous) else null)
                        ?: target(text, separator.next, section)
                        ?: break
                    pending = range(text, previous, end)
                    lastWasRange = true
                    previous = end
                } else {
                    val next = target(text, separator.next, section) ?: break
                    onReference(pending)
                    pending = single(text, next)
                    lastWasRange = false
                    previous = next
                }
                position = previous.end
            }
            onReference(pending)
        }
    }

    private class Target(val start: Int, val end: Int, val identifier: String) {
        /** For a subrule such as `702.19b`: the rule `702.19` and the letter `b`. */
        val rule: String? get() = if (identifier.last().isLetter()) identifier.dropLast(1) else null
        val letter: Char? get() = if (identifier.last().isLetter()) identifier.last() else null
    }

    private fun single(text: String, target: Target): ScannedReference =
        ScannedReference(text.substring(target.start, target.end), listOf(target.identifier))

    private fun range(text: String, from: Target, to: Target): ScannedReference {
        val raw = text.substring(from.start, to.end)
        val rule = from.rule
        val first = from.letter
        val last = to.letter
        if (rule == null || first == null || last == null || to.rule != rule || last < first) {
            return ScannedReference(raw, listOf(from.identifier, to.identifier))
        }
        val count = last - first + 1
        if (count > maxRangeExpansion) {
            throw RulesParseException(
                RulesParseFailure.LimitExceeded(RulesLimit.RANGE_EXPANSION, maxRangeExpansion.toLong(), count.toLong()),
            )
        }
        return ScannedReference(raw, (first..last).map { "$rule$it" })
    }

    /** `\d{3}`, `\d{3}.\d{1,4}` or `\d{3}.\d{1,4}[a-z]`, or one digit 1 to 9 after `section`. */
    private fun target(text: String, at: Int, section: Boolean): Target? {
        val head = digitRun(text, at)
        if (head == 3) {
            var end = at + 3
            if (end < text.length && text[end] == '.') {
                val digits = digitRun(text, end + 1)
                if (digits > MAX_RULE_NUMBER_DIGITS) return null
                if (digits > 0) {
                    end += 1 + digits
                    if (end < text.length && text[end] in 'a'..'z') end++
                }
            }
            return if (boundary(text, end)) Target(at, end, text.substring(at, end)) else null
        }
        if (section && head == 1 && text[at] != '0' && boundary(text, at + 1)) {
            return Target(at, at + 1, text.substring(at, at + 1))
        }
        return null
    }

    /** The letter-only end of a shorthand range such as the `d` in `702.19b-d`. */
    private fun letterShorthand(text: String, at: Int, previous: Target): Target? {
        val rule = previous.rule ?: return null
        val first = previous.letter ?: return null
        if (at >= text.length || text[at] !in 'a'..'z' || text[at] <= first) return null
        if (!boundary(text, at + 1)) return null
        return Target(at, at + 1, "$rule${text[at]}")
    }

    /** [tight] when the separator is a hyphen or en dash with no blanks on either side. */
    private class Separator(val next: Int, val range: Boolean, val tight: Boolean)

    private fun separator(text: String, at: Int): Separator? {
        val start = skipBlanks(text, at)
        if (start < text.length) {
            when (text[start]) {
                ',' -> {
                    var next = skipBlanks(text, start + 1)
                    val afterWord = word(text, next, LIST_WORDS)
                    if (afterWord != -1) {
                        val blanks = skipBlanks(text, afterWord)
                        if (blanks > afterWord) next = blanks
                    }
                    return Separator(next, range = false, tight = false)
                }
                '-', EN_DASH, EM_DASH -> {
                    val next = skipBlanks(text, start + 1)
                    val tight = start == at && next == start + 1 && text[start] != EM_DASH
                    return Separator(next, range = true, tight = tight)
                }
            }
        }
        if (start == at) return null
        val afterList = word(text, start, LIST_WORDS)
        if (afterList != -1) {
            val next = skipBlanks(text, afterList)
            if (next > afterList) return Separator(next, range = false, tight = false)
        }
        val afterRange = word(text, start, RANGE_WORDS)
        if (afterRange != -1) {
            val next = skipBlanks(text, afterRange)
            if (next > afterRange) return Separator(next, range = true, tight = false)
        }
        return null
    }

    /** The index after the first of [words] starting at [at], or -1. */
    private fun word(text: String, at: Int, words: List<String>): Int {
        for (candidate in words) {
            if (text.regionMatches(at, candidate, 0, candidate.length)) return at + candidate.length
        }
        return -1
    }

    /** A target ends where no letter or digit follows; a period followed by a digit continues it. */
    private fun boundary(text: String, end: Int): Boolean {
        if (end >= text.length) return true
        val next = text[end]
        if (next.isLetterOrDigit()) return false
        if (next == '.' && end + 1 < text.length && text[end + 1].isDigit()) return false
        return true
    }

    private fun digitRun(text: String, from: Int): Int {
        var i = from
        while (i < text.length && text[i] in '0'..'9') i++
        return i - from
    }

    /** Spaces, tabs and no-break spaces only: a line break ends a reference. */
    private fun skipBlanks(text: String, from: Int): Int {
        var i = from
        while (i < text.length && (text[i] == ' ' || text[i] == '\t' || text[i] == NO_BREAK_SPACE)) i++
        return i
    }

    private companion object {
        /** The only regex: one matcher per text, advanced with find(), never reset or re-regioned. */
        val KEYWORD: Pattern = Pattern.compile("""\b(?:rules?|sections?)\b""", Pattern.CASE_INSENSITIVE)
        val LIST_WORDS = listOf("and", "or")
        val RANGE_WORDS = listOf("through", "to")
        const val EN_DASH = '\u2013'
        const val EM_DASH = '\u2014'
        const val NO_BREAK_SPACE = '\u00A0'
    }
}
