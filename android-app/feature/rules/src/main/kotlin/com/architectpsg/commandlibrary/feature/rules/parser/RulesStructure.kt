package com.architectpsg.commandlibrary.feature.rules.parser

import com.architectpsg.commandlibrary.core.model.RuleEntryKind

/** One recognized entry before identifiers are resolved and core DTOs are built. */
internal class DraftEntry(
    val kind: RuleEntryKind,
    val id: String,
    /** Source rule number for NUMBERED entries; null when the source has none. */
    val number: String?,
    /** Section or chapter heading, glossary term, or `Contents`; null when the source has none. */
    val title: String?,
    /** Exact source lines joined with `\n`, leading and trailing blank lines removed. */
    val text: String,
    val firstLine: Long,
)

internal class StructureStats(
    val entriesByKind: Map<RuleEntryKind, Long>,
    val duplicateIdentifiers: Long,
    val anomalies: Long,
    val firstAnomalies: List<RulesAnomaly>,
)

/**
 * Line-by-line recognizer for Comprehensive Rules style text. The same instance logic runs in the
 * index pass and the parse pass, so both produce identical identifiers for identical bytes.
 *
 * Regions, in order: front matter, an optional standalone `Contents` line and its listing, the body
 * (sections, chapters, rules, subrules, examples), a standalone `Glossary`, a standalone `Credits`.
 * The grammar follows the structure recorded in research R-03 and is not yet checked against a
 * genuine source file.
 */
internal class RulesStructure(
    private val limits: RulesParserLimits,
    private val emit: suspend (DraftEntry) -> Unit,
) {
    private enum class Region { FRONT, CONTENTS, BODY, GLOSSARY, CREDITS }

    private var region = Region.FRONT

    // The entry being assembled.
    private var openKind: RuleEntryKind? = null
    private var openId = ""
    private var openNumber: String? = null
    private var openTitle: String? = null
    private var openFirstLine = 0L
    private val openLines = ArrayList<String>()
    private var openBytes = 0

    // Body position used to accept or reject heading-like lines.
    private val contentsSections = HashSet<String>()
    private var firstContentsHeading: String? = null
    private var currentSection: Int? = null
    private var lastChapter: Int? = null
    private var currentChapter: Int? = null
    private var lastNumberedId: String? = null
    private val examplesPerParent = HashMap<String, Int>()

    // Block counters for regions split on blank lines.
    private var frontBlocks = 0
    private var creditBlocks = 0

    // Every identifier emitted so far, and the next suffix to try for each repeated base identifier.
    private val emittedIdentifiers = HashSet<String>()
    private val nextSuffix = HashMap<String, Int>()
    private val entriesByKind = HashMap<RuleEntryKind, Long>()
    private var entries = 0L
    private var duplicates = 0L
    private var anomalies = 0L
    private val firstAnomalies = ArrayList<RulesAnomaly>()

    suspend fun line(text: String, lineNumber: Long) {
        when (region) {
            Region.FRONT -> front(text, lineNumber)
            Region.CONTENTS -> contents(text, lineNumber)
            Region.BODY -> body(text, lineNumber)
            Region.GLOSSARY -> glossary(text, lineNumber)
            Region.CREDITS -> credits(text, lineNumber)
        }
    }

    suspend fun finish(lastLine: Long): StructureStats {
        close()
        if (region == Region.CONTENTS) {
            throw RulesParseException(
                RulesParseFailure.Structure(lastLine, "Contents is not followed by a body that repeats its first heading"),
            )
        }
        if ((entriesByKind[RuleEntryKind.NUMBERED] ?: 0L) == 0L) throw RulesParseException(RulesParseFailure.NoNumberedRules)
        return StructureStats(entriesByKind.toMap(), duplicates, anomalies, firstAnomalies.toList())
    }

    private suspend fun front(text: String, lineNumber: Long) {
        val trimmed = text.trimEnd()
        when {
            trimmed == CONTENTS -> {
                close()
                region = Region.CONTENTS
                open(RuleEntryKind.NOTICE, "contents", null, CONTENTS, text, lineNumber)
            }
            heading(text) != null -> {
                close()
                region = Region.BODY
                body(text, lineNumber)
            }
            else -> block(text, lineNumber) { "front:${++frontBlocks}" }
        }
    }

    /**
     * Contents repeats body headings. The body starts when the first section or chapter heading
     * listed in Contents appears a second time, so listed headings never become entries of their own
     * and the body headings alone own their identifiers.
     */
    private suspend fun contents(text: String, lineNumber: Long) {
        val trimmed = text.trimEnd()
        val first = firstContentsHeading
        val bodyStarts = if (first != null) {
            trimmed == first
        } else {
            // A listing without section or chapter headings: the body starts at the first numbered rule.
            RULE.matches(trimmed) || SUBRULE.matches(trimmed)
        }
        if (bodyStarts) {
            if (first == null) {
                anomaly(lineNumber, "Contents lists no section or chapter headings; the body starts at the first numbered rule")
            }
            close()
            region = Region.BODY
            body(text, lineNumber)
            return
        }
        val section = SECTION.matches(trimmed)
        if (section) contentsSections += trimmed
        if (first == null && (section || CHAPTER.matches(trimmed))) firstContentsHeading = trimmed
        append(text)
    }

    private suspend fun body(text: String, lineNumber: Long) {
        val trimmed = text.trimEnd()
        when {
            trimmed == GLOSSARY -> {
                close()
                region = Region.GLOSSARY
            }
            trimmed == CREDITS -> {
                close()
                region = Region.CREDITS
            }
            text.isBlank() -> append(text)
            text.startsWith(EXAMPLE) -> {
                val parent = lastNumberedId
                if (parent == null) {
                    anomaly(lineNumber, "Example without a preceding rule")
                    continuation(text, lineNumber)
                } else {
                    close()
                    val index = (examplesPerParent[parent] ?: 0) + 1
                    examplesPerParent[parent] = index
                    open(RuleEntryKind.EXAMPLE, "$parent#example-$index", null, null, text, lineNumber)
                }
            }
            else -> {
                val match = heading(text)
                if (match == null) {
                    continuation(text, lineNumber)
                } else {
                    val rejection = accept(match, trimmed)
                    if (rejection != null) {
                        anomaly(lineNumber, rejection)
                        continuation(text, lineNumber)
                    } else {
                        close()
                        open(RuleEntryKind.NUMBERED, match.number, match.number, match.title, text, lineNumber)
                        lastNumberedId = openId
                    }
                }
            }
        }
    }

    private suspend fun glossary(text: String, lineNumber: Long) {
        if (text.trimEnd() == CREDITS) {
            close()
            region = Region.CREDITS
            return
        }
        when {
            text.isBlank() -> close()
            openKind == null -> {
                val term = text.trim()
                open(RuleEntryKind.GLOSSARY, "glossary:$term", null, term, text, lineNumber)
            }
            else -> append(text)
        }
    }

    private suspend fun credits(text: String, lineNumber: Long) {
        block(text, lineNumber) { "credits:${++creditBlocks}" }
    }

    /** Blank-line separated blocks: a blank line ends the open block, a non-blank line starts or extends one. */
    private suspend fun block(text: String, lineNumber: Long, nextId: () -> String) {
        if (text.isBlank()) {
            close()
        } else if (openKind == null) {
            open(RuleEntryKind.NOTICE, nextId(), null, null, text, lineNumber)
        } else {
            append(text)
        }
    }

    private suspend fun continuation(text: String, lineNumber: Long) {
        if (openKind == null) {
            throw RulesParseException(RulesParseFailure.Structure(lineNumber, "Text outside any entry"))
        }
        append(text)
    }

    private class Heading(val kind: Kind, val number: String, val title: String?, val chapter: Int, val section: Int) {
        enum class Kind { SECTION, CHAPTER, RULE, SUBRULE }
    }

    private fun heading(text: String): Heading? {
        val line = text.trimEnd()
        SUBRULE.matchEntire(line)?.let { m ->
            val chapter = m.groupValues[1].toInt()
            return Heading(Heading.Kind.SUBRULE, "${m.groupValues[1]}.${m.groupValues[2]}${m.groupValues[3]}", null, chapter, chapter / 100)
        }
        RULE.matchEntire(line)?.let { m ->
            val chapter = m.groupValues[1].toInt()
            return Heading(Heading.Kind.RULE, "${m.groupValues[1]}.${m.groupValues[2]}", null, chapter, chapter / 100)
        }
        CHAPTER.matchEntire(line)?.let { m ->
            val chapter = m.groupValues[1].toInt()
            return Heading(Heading.Kind.CHAPTER, m.groupValues[1], m.groupValues[2].trim(), chapter, chapter / 100)
        }
        SECTION.matchEntire(line)?.let { m ->
            val section = m.groupValues[1].toInt()
            return Heading(Heading.Kind.SECTION, m.groupValues[1], m.groupValues[2].trim(), -1, section)
        }
        return null
    }

    /** Returns null when the heading is accepted, otherwise why it is kept as text. */
    private fun accept(heading: Heading, trimmed: String): String? {
        val section = currentSection
        val chapter = currentChapter
        return when (heading.kind) {
            Heading.Kind.SECTION -> when {
                section != null && heading.section != section + 1 -> "Section ${heading.number} out of sequence after section $section"
                contentsSections.isNotEmpty() && trimmed !in contentsSections -> "Section heading not listed in Contents"
                else -> {
                    currentSection = heading.section
                    lastChapter = null
                    currentChapter = null
                    null
                }
            }
            Heading.Kind.CHAPTER -> {
                val last = lastChapter
                when {
                    section != null && heading.section != section -> "Chapter ${heading.number} outside section $section"
                    last != null && heading.chapter <= last -> "Chapter ${heading.number} not after chapter $last"
                    else -> {
                        lastChapter = heading.chapter
                        currentChapter = heading.chapter
                        null
                    }
                }
            }
            Heading.Kind.RULE, Heading.Kind.SUBRULE -> when {
                chapter != null && heading.chapter != chapter -> "Rule ${heading.number} outside chapter $chapter"
                chapter == null && section != null && heading.section != section -> "Rule ${heading.number} outside section $section"
                else -> null
            }
        }
    }

    private suspend fun open(kind: RuleEntryKind, baseId: String, number: String?, title: String?, firstText: String, lineNumber: Long) {
        openKind = kind
        openId = uniqueIdentifier(baseId)
        openNumber = number
        openTitle = title
        openFirstLine = lineNumber
        openLines.clear()
        openBytes = 0
        appendLine(firstText, continuation = false)
    }

    /**
     * [baseId] the first time; later repeats become `<baseId>~2`, `~3` and so on, skipping any
     * identifier already emitted (a source term may itself end in `~2`), so identifiers stay unique.
     */
    private fun uniqueIdentifier(baseId: String): String {
        if (emittedIdentifiers.add(baseId)) return baseId
        duplicates += 1
        var suffix = nextSuffix[baseId] ?: 2
        var candidate = "$baseId~$suffix"
        while (!emittedIdentifiers.add(candidate)) {
            suffix += 1
            candidate = "$baseId~$suffix"
        }
        nextSuffix[baseId] = suffix + 1
        return candidate
    }

    private fun append(text: String) {
        if (openKind == null) return
        appendLine(text, continuation = true)
    }

    private fun appendLine(text: String, continuation: Boolean) {
        val bytes = utf8Length(text) + if (openLines.isEmpty()) 0 else 1
        openBytes += bytes
        if (openBytes > limits.maxEntryBytes) {
            throw RulesParseException(RulesParseFailure.LimitExceeded(RulesLimit.ENTRY_BYTES, limits.maxEntryBytes.toLong(), openBytes.toLong()))
        }
        openLines += text
        if (continuation && openLines.size - 1 > limits.maxContinuationLines) {
            throw RulesParseException(
                RulesParseFailure.LimitExceeded(RulesLimit.CONTINUATION_LINES, limits.maxContinuationLines.toLong(), (openLines.size - 1).toLong()),
            )
        }
    }

    private suspend fun close() {
        val kind = openKind ?: return
        openKind = null
        var start = 0
        var end = openLines.size
        while (start < end && openLines[start].isBlank()) start++
        while (end > start && openLines[end - 1].isBlank()) end--
        val text = openLines.subList(start, end).joinToString("\n")
        openLines.clear()
        entries += 1
        if (entries > limits.maxEntries) {
            throw RulesParseException(RulesParseFailure.LimitExceeded(RulesLimit.ENTRIES, limits.maxEntries.toLong(), entries))
        }
        entriesByKind[kind] = (entriesByKind[kind] ?: 0L) + 1
        emit(DraftEntry(kind, openId, openNumber, openTitle, text, openFirstLine))
    }

    private fun anomaly(lineNumber: Long, reason: String) {
        anomalies += 1
        if (firstAnomalies.size < RulesParseSummary.MAX_REPORTED_ANOMALIES) firstAnomalies += RulesAnomaly(lineNumber, reason)
    }

    private companion object {
        const val CONTENTS = "Contents"
        const val GLOSSARY = "Glossary"
        const val CREDITS = "Credits"
        const val EXAMPLE = "Example:"
        // The reader splits physical LF lines; Unicode paragraph/line separators remain body text.
        val SECTION = Regex("""([1-9])\.\s+(\S.*)""", RegexOption.DOT_MATCHES_ALL)
        val CHAPTER = Regex("""(\d{3})\.\s+(\S.*)""", RegexOption.DOT_MATCHES_ALL)
        val RULE = Regex("""(\d{3})\.(\d+)\.?(?:\s+.*)?""", RegexOption.DOT_MATCHES_ALL)
        val SUBRULE = Regex("""(\d{3})\.(\d+)([a-z]+)\.?(?:\s+.*)?""", RegexOption.DOT_MATCHES_ALL)

        fun utf8Length(text: String): Int {
            var bytes = 0
            for (c in text) {
                bytes += when {
                    c.isHighSurrogate() -> 4
                    c.isLowSurrogate() -> 0
                    c.code < 0x80 -> 1
                    c.code < 0x800 -> 2
                    else -> 3
                }
            }
            return bytes
        }
    }
}
