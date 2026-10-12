package com.architectpsg.commandlibrary.feature.rules.parser

import java.util.regex.Matcher
import java.util.regex.Pattern

/** One cross-reference as written in the source and the identifiers it names. */
internal class ScannedReference(val raw: String, val identifiers: List<String>)

/**
 * Finds contextual cross-references: `rule`, `rules`, `section` or `sections` followed by one or
 * more targets. Targets are joined by commas, `and`, `or`, or a range (`-`, en dash, em dash,
 * `through`, `to`). A letter range on one rule such as `702.19b-d` or `702.19b-702.19d` expands to
 * the numbered subrules, skipping intermediate l/o letters. Explicit endpoints remain targets,
 * including l/o, so malformed explicit references are not silently removed. Other ranges keep
 * only their two ends. Bare numbers without a keyword are not
 * references.
 */
internal class RuleReferenceScanner(private val maxRangeExpansion: Int) {

    fun scan(text: String): List<ScannedReference> {
        val found = ArrayList<ScannedReference>()
        val keywords = KEYWORD.matcher(text)
        while (keywords.find()) {
            val section = keywords.group(1).orEmpty().startsWith("s", ignoreCase = true)
            var position = keywords.end()
            val spaces = skipSpaces(text, position)
            if (spaces == position) continue
            position = spaces
            var previous = target(text, position, section) ?: continue
            val group = ArrayList<ScannedReference>()
            group += single(text, previous)
            position = previous.end
            while (true) {
                val separator = separator(text, position) ?: break
                if (separator.range) {
                    val letterEnd = letterShorthand(text, separator.next, previous)
                    val fullEnd = if (letterEnd == null) target(text, separator.next, section) else null
                    val end = letterEnd ?: fullEnd ?: break
                    group.removeAt(group.size - 1)
                    group += range(text, previous, end)
                    previous = end
                } else {
                    val next = target(text, separator.next, section) ?: break
                    group += single(text, next)
                    previous = next
                }
                position = previous.end
            }
            found += group
        }
        return found
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
        // CR subrule numbering omits l and o; keep either when the source names it as an endpoint.
        val identifiers = (first..last).filter { it == first || it == last || (it != 'l' && it != 'o') }.map { "$rule$it" }
        val count = identifiers.size
        if (count > maxRangeExpansion) {
            throw RulesParseException(
                RulesParseFailure.LimitExceeded(RulesLimit.RANGE_EXPANSION, maxRangeExpansion.toLong(), count.toLong()),
            )
        }
        return ScannedReference(raw, identifiers)
    }

    private fun target(text: String, at: Int, section: Boolean): Target? {
        if (at >= text.length) return null
        val numbered = NUMBERED.matcher(text).regionAt(at)
        if (numbered.lookingAt() && boundary(text, numbered.end())) {
            return Target(at, numbered.end(), numbered.group())
        }
        if (section) {
            val single = SECTION_NUMBER.matcher(text).regionAt(at)
            if (single.lookingAt() && boundary(text, single.end())) return Target(at, single.end(), single.group())
        }
        return null
    }

    /** The letter-only end of a shorthand range such as the `d` in `702.19b-d`. */
    private fun letterShorthand(text: String, at: Int, previous: Target): Target? {
        val rule = previous.rule ?: return null
        if (at >= text.length || text[at] !in 'a'..'z') return null
        if (!boundary(text, at + 1)) return null
        return Target(at, at + 1, "$rule${text[at]}")
    }

    private class Separator(val next: Int, val range: Boolean)

    private fun separator(text: String, at: Int): Separator? {
        val list = LIST_SEPARATOR.matcher(text).regionAt(at)
        if (list.lookingAt()) return Separator(list.end(), range = false)
        val range = RANGE_SEPARATOR.matcher(text).regionAt(at)
        if (range.lookingAt()) return Separator(range.end(), range = true)
        return null
    }

    /** A target ends where no letter or digit follows; a period followed by a digit continues it. */
    private fun boundary(text: String, end: Int): Boolean {
        if (end >= text.length) return true
        val next = text[end]
        if (next.isLetterOrDigit()) return false
        if (next == '.' && end + 1 < text.length && text[end + 1].isDigit()) return false
        return true
    }

    private fun skipSpaces(text: String, from: Int): Int {
        var i = from
        while (i < text.length && text[i].isWhitespace()) i++
        return i
    }

    private fun Matcher.regionAt(start: Int): Matcher = region(start, regionEnd())

    private companion object {
        val KEYWORD: Pattern = Pattern.compile("""\b(rules?|sections?)\b""", Pattern.CASE_INSENSITIVE)
        val NUMBERED: Pattern = Pattern.compile("""\d{3}(?:\.\d+[a-z]?)?""")
        val SECTION_NUMBER: Pattern = Pattern.compile("""[1-9]""")
        val LIST_SEPARATOR: Pattern = Pattern.compile("""\s*,\s*(?:(?:and|or)\s+)?|\s+(?:and|or)\s+""")
        val RANGE_SEPARATOR: Pattern = Pattern.compile("""\s*[-\u2013\u2014]\s*|\s+(?:through|to)\s+""")
    }
}
