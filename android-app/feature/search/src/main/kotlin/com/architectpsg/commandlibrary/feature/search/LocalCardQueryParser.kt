package com.architectpsg.commandlibrary.feature.search

import com.architectpsg.commandlibrary.core.model.*
import java.math.BigDecimal
import java.util.Locale

/** Syntax only. Parsing grants neither a data source nor permission to execute online. */
public class LocalCardQueryParser {
    public fun parse(input: String): CardQueryParseResult = try {
        if (input.length > 4096 || input.codePointCount(0, input.length) > 2048)
            fail(0, input.length, "Query exceeds 2048 code points")
        CardQueryParseResult.Valid(Parser(tokenize(input), input.length).parse())
    } catch (error: Syntax) {
        CardQueryParseResult.Invalid(SnapshotList.of(error.diagnostic))
    }
}

private class Syntax(val diagnostic: QueryDiagnostic) : RuntimeException()
private fun fail(start: Int, end: Int, message: String): Nothing = throw Syntax(QueryDiagnostic(start, end, message))
private enum class Kind { WORD, STRING, LEFT, RIGHT, COLON, OPERATOR, MINUS }
private data class Token(val kind: Kind, val value: String, val start: Int, val end: Int)

private fun tokenize(input: String): List<Token> {
    val tokens = mutableListOf<Token>()
    var at = 0
    while (at < input.length) {
        if (input[at].isWhitespace()) { at++; continue }
        if (tokens.size == 256) fail(at, input.length, "Query exceeds 256 tokens")
        val start = at
        val c = input[at++]
        val token = when (c) {
            '(' -> Token(Kind.LEFT, "(", start, at)
            ')' -> Token(Kind.RIGHT, ")", start, at)
            ':' -> Token(Kind.COLON, ":", start, at)
            '-' -> Token(Kind.MINUS, "-", start, at)
            '=', '!', '<', '>' -> {
                if (at < input.length && input[at] == '=' && c != '=') at++
                if (c == '!' && at == start + 1) fail(start, at, "Use != for inequality")
                Token(Kind.OPERATOR, input.substring(start, at), start, at)
            }
            '"' -> {
                val value = StringBuilder()
                var closed = false
                while (at < input.length) {
                    val next = input[at++]
                    if (next == '"') { closed = true; break }
                    if (next == '\\') {
                        if (at == input.length) fail(at - 1, at, "Incomplete quoted escape")
                        val escaped = input[at++]
                        if (escaped != '"' && escaped != '\\') fail(at - 2, at, "Only quoted quote and backslash escapes are supported")
                        value.append(escaped)
                    } else value.append(next)
                }
                if (!closed) fail(start, input.length, "Unclosed quoted string")
                Token(Kind.STRING, value.toString(), start, at)
            }
            else -> {
                while (at < input.length && !input[at].isWhitespace() && input[at] !in "():=!<>\"") at++
                Token(Kind.WORD, input.substring(start, at), start, at)
            }
        }
        tokens += token
    }
    return tokens
}

private class Parser(private val tokens: List<Token>, private val end: Int) {
    private var at = 0
    private var leaves = 0
    private fun peek(): Token? = tokens.getOrNull(at)
    private fun keyword(word: String): Boolean = peek()?.let { it.kind == Kind.WORD && it.value.equals(word, true) } == true
    private fun error(message: String): Nothing = peek()?.let { fail(it.start, it.end, message) } ?: fail(end, end, message)
    fun parse(): QueryAst {
        if (tokens.isEmpty()) error("Enter a query")
        val query = or(0)
        if (peek() != null) error("Unexpected query token")
        return query
    }
    private fun or(depth: Int): QueryAst {
        val terms = mutableListOf(and(depth))
        while (keyword("OR")) { at++; terms += and(depth) }
        return if (terms.size == 1) terms.single() else QueryAst.Or(SnapshotList.copyOf(terms))
    }
    private fun and(depth: Int): QueryAst {
        val terms = mutableListOf(unary(depth))
        while (peek() != null && peek()?.kind != Kind.RIGHT && !keyword("OR")) {
            if (keyword("AND")) at++
            terms += unary(depth)
        }
        return if (terms.size == 1) terms.single() else QueryAst.And(SnapshotList.copyOf(terms))
    }
    private fun unary(depth: Int): QueryAst {
        if (depth > 32) error("Query nesting exceeds 32")
        if (peek()?.kind == Kind.MINUS || keyword("NOT")) { at++; return QueryAst.Not(unary(depth + 1)) }
        if (peek()?.kind == Kind.LEFT) {
            at++
            val query = or(depth + 1)
            if (peek()?.kind != Kind.RIGHT) error("Expected closing parenthesis")
            at++
            return query
        }
        val first = peek() ?: error("Expected a query clause")
        if (first.kind !in listOf(Kind.WORD, Kind.STRING) || keyword("AND") || keyword("OR")) error("Expected a query clause")
        at++
        if (++leaves > 128) fail(first.start, first.end, "Query exceeds 128 clauses")
        val op = peek()
        if (first.kind == Kind.WORD && op?.kind in listOf(Kind.COLON, Kind.OPERATOR)) {
            at++
            return field(first, requireNotNull(op), value())
        }
        if (first.value.isEmpty()) fail(first.start, first.end, "Empty search text")
        return if (first.kind == Kind.WORD && first.value.equals("owned", true)) QueryAst.Owned(Comparison.GT, BigDecimal.ZERO)
            else QueryAst.Text(SearchTextField.NAME, first.value, false)
    }
    private fun value(): Token {
        val minus = peek()?.takeIf { it.kind == Kind.MINUS }?.also { at++ }
        val token = peek() ?: error("Expected a field value")
        if (token.kind !in listOf(Kind.WORD, Kind.STRING)) error("Expected a field value")
        at++
        val value = if (minus == null) token else token.copy(value = "-" + token.value, start = minus.start)
        if (value.value.isEmpty()) fail(value.start, value.end, "Empty field value")
        return value
    }
    private fun field(field: Token, op: Token, value: Token): QueryAst {
        val key = field.value.lowercase(Locale.ROOT)
        val comparison = when (op.value) {
            ":", "=" -> Comparison.EQ
            "!=" -> Comparison.NE
            "<" -> Comparison.LT
            "<=" -> Comparison.LE
            ">" -> Comparison.GT
            ">=" -> Comparison.GE
            else -> fail(op.start, op.end, "Unsupported comparison")
        }
        fun literalOperator() {
            if (op.value !in listOf(":", "=")) fail(op.start, op.end, "Use : or = for this field; negate the clause with NOT")
        }
        fun text(kind: SearchTextField): QueryAst {
            literalOperator()
            return QueryAst.Text(kind, value.value, op.value == "=" || kind == SearchTextField.LAYOUT)
        }
        fun printing(kind: PrintingSearchField): QueryAst {
            literalOperator()
            return QueryAst.PrintingText(kind, value.value)
        }
        fun numeric(kind: SearchNumericField): QueryAst = QueryAst.Numeric(kind, comparison, decimal(value))
        return when (key) {
            "name" -> text(SearchTextField.NAME)
            "o", "oracle" -> text(SearchTextField.ORACLE_TEXT)
            "t", "type" -> text(SearchTextField.TYPE)
            "keyword" -> text(SearchTextField.KEYWORD)
            "layout" -> text(SearchTextField.LAYOUT)
            "rarity" -> printing(PrintingSearchField.RARITY)
            "set" -> printing(PrintingSearchField.SET)
            "lang", "language" -> printing(PrintingSearchField.LANGUAGE)
            "finish" -> printing(PrintingSearchField.FINISH)
            "mv", "cmc" -> numeric(SearchNumericField.MANA_VALUE)
            "pow", "power" -> numeric(SearchNumericField.POWER)
            "tou", "toughness" -> numeric(SearchNumericField.TOUGHNESS)
            "loyalty" -> numeric(SearchNumericField.LOYALTY)
            "defense" -> numeric(SearchNumericField.DEFENSE)
            "c", "color", "id", "identity" -> {
                val colors = colors(value)
                // Includes(empty) would match every known set, rather than colorless cards.
                QueryAst.Colors(colors, key in listOf("id", "identity"),
                    if (op.value == ":") { if (colors.isEmpty()) Comparison.EQ else Comparison.GE } else comparison)
            }
            "legal", "format" -> {
                literalOperator()
                if (value.value.isBlank()) fail(value.start, value.end, "Expected a nonblank format identity")
                QueryAst.LegalIn(FormatId(ProviderId("scryfall"), value.value), LegalityStatus.LEGAL)
            }
            "is" -> {
                literalOperator()
                val flag = when (value.value.lowercase(Locale.ROOT)) {
                    "reserved" -> SearchFlag.RESERVED
                    "gamechanger" -> SearchFlag.GAMECHANGER
                    else -> fail(value.start, value.end, "Expected reserved or gamechanger")
                }
                QueryAst.Flag(flag, true)
            }
            "in" -> {
                literalOperator()
                if (!value.value.equals("deck", true)) fail(value.start, value.end, "Only in:deck is supported")
                QueryAst.InDeck
            }
            "owned" -> {
                val quantity = decimal(value)
                if (quantity.signum() < 0 || quantity.stripTrailingZeros().scale() > 0) fail(value.start, value.end, "Owned quantity must be a nonnegative integer")
                QueryAst.Owned(comparison, quantity)
            }
            "price" -> {
                val amount = decimal(value)
                if (amount.signum() < 0) fail(value.start, value.end, "Price must be nonnegative")
                QueryAst.Price(comparison, amount)
            }
            else -> fail(field.start, field.end, "Unsupported local query field")
        }
    }
}

private fun decimal(token: Token): BigDecimal {
    if (token.value.length > 64 || !token.value.matches(Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)")))
        fail(token.start, token.end, "Expected a plain decimal of at most 64 characters, without exponent notation")
    return BigDecimal(token.value)
}

private fun colors(token: Token): SnapshotList<Color> {
    val value = token.value.lowercase(Locale.ROOT)
    val letters = when (value) {
        "white" -> "w"
        "blue" -> "u"
        "black" -> "b"
        "red" -> "r"
        "green" -> "g"
        "colorless" -> "c"
        else -> value
    }
    if (letters == "c") return SnapshotList.empty()
    if (letters.isEmpty() || letters.any { it !in "wubrg" }) fail(token.start, token.end, "Expected WUBRG colors or C for colorless")
    return SnapshotList.copyOf(listOf('w' to Color.WHITE, 'u' to Color.BLUE, 'b' to Color.BLACK, 'r' to Color.RED, 'g' to Color.GREEN).filter { it.first in letters }.map { it.second })
}
