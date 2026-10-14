package com.architectpsg.commandlibrary.feature.search

import com.architectpsg.commandlibrary.core.model.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Pinned invented query strings; no publisher corpus or execution results are represented. */
@RunWith(Parameterized::class)
class LocalCardQueryParserTest(private val input: String, private val expected: QueryAst?, private val span: Pair<Int, Int>?) {
    @Test fun parsesTypedSyntaxOrReportsItsOriginalPosition() {
        when (val result = LocalCardQueryParser().parse(input)) {
            is CardQueryParseResult.Valid -> { assertNull(span); assertEquals(expected, result.query) }
            is CardQueryParseResult.Invalid -> {
                assertNull(expected)
                assertNotNull(span)
                val diagnostic = result.diagnostics.single()
                assertEquals(span!!.first, diagnostic.start)
                assertEquals(span.second, diagnostic.end)
                assertTrue(diagnostic.message.isNotBlank())
                assertTrue(diagnostic.end <= input.length)
            }
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{index}: {0}") fun cases(): Collection<Array<Any?>> {
            fun text(value: String, exact: Boolean = false, field: SearchTextField = SearchTextField.NAME) = QueryAst.Text(field, value, exact)
            fun numeric(field: SearchNumericField, op: Comparison, value: String) = QueryAst.Numeric(field, op, BigDecimal(value))
            fun good(input: String, ast: QueryAst): Array<Any?> = arrayOf(input, ast, null)
            fun bad(input: String, start: Int, end: Int): Array<Any?> = arrayOf(input, null, start to end)
            val a = text("alpha"); val b = text("beta"); val c = text("gamma")
            return listOf(
                good("alpha", a),
                good("  alpha  ", a),
                good("\"Synthetic Name\"", text("Synthetic Name")),
                good("name:Æther", text("Æther")),
                good("NAME=\"Synthetic Name\"", text("Synthetic Name", true)),
                good("name:\"😀 Élan\"", text("😀 Élan")),
                good("name:\"A \\\"quoted\\\" word\"", text("A \"quoted\" word")),
                good("name:\"A \\\\ path\"", text("A \\ path")),
                good("synthetic-card", text("synthetic-card")),
                good("o:\"draw a card\"", text("draw a card", field = SearchTextField.ORACLE_TEXT)),
                good("ORACLE:draw", text("draw", field = SearchTextField.ORACLE_TEXT)),
                good("t:creature", text("creature", field = SearchTextField.TYPE)),
                good("type=Creature", text("Creature", true, SearchTextField.TYPE)),
                good("keyword:Flying", text("Flying", field = SearchTextField.KEYWORD)),
                good("layout:transform", text("transform", true, SearchTextField.LAYOUT)),
                good("rarity:rare", QueryAst.PrintingText(PrintingSearchField.RARITY, "rare")),
                good("set:SYN", QueryAst.PrintingText(PrintingSearchField.SET, "SYN")),
                good("lang:en", QueryAst.PrintingText(PrintingSearchField.LANGUAGE, "en")),
                good("language=fr", QueryAst.PrintingText(PrintingSearchField.LANGUAGE, "fr")),
                good("finish:foil", QueryAst.PrintingText(PrintingSearchField.FINISH, "foil")),
                good("finish:nonfoil", QueryAst.PrintingText(PrintingSearchField.FINISH, "nonfoil")),
                good("c:WU", QueryAst.Colors(SnapshotList.of(Color.WHITE, Color.BLUE), false, Comparison.GE)),
                good("color=red", QueryAst.Colors(SnapshotList.of(Color.RED), false, Comparison.EQ)),
                good("id:UB", QueryAst.Colors(SnapshotList.of(Color.BLUE, Color.BLACK), true, Comparison.GE)),
                good("identity<=g", QueryAst.Colors(SnapshotList.of(Color.GREEN), true, Comparison.LE)),
                good("c:c", QueryAst.Colors(SnapshotList.empty(), false, Comparison.EQ)),
                good("identity:colorless", QueryAst.Colors(SnapshotList.empty(), true, Comparison.EQ)),
                good("color=colorless", QueryAst.Colors(SnapshotList.empty(), false, Comparison.EQ)),
                good("c!=w", QueryAst.Colors(SnapshotList.of(Color.WHITE), false, Comparison.NE)),
                good("c>u", QueryAst.Colors(SnapshotList.of(Color.BLUE), false, Comparison.GT)),
                good("c<b", QueryAst.Colors(SnapshotList.of(Color.BLACK), false, Comparison.LT)),
                good("c>=r", QueryAst.Colors(SnapshotList.of(Color.RED), false, Comparison.GE)),
                good("mv:2", numeric(SearchNumericField.MANA_VALUE, Comparison.EQ, "2")),
                good("cmc>=2.5", numeric(SearchNumericField.MANA_VALUE, Comparison.GE, "2.5")),
                good("pow=-2", numeric(SearchNumericField.POWER, Comparison.EQ, "-2")),
                good("power!=3", numeric(SearchNumericField.POWER, Comparison.NE, "3")),
                good("tou<4", numeric(SearchNumericField.TOUGHNESS, Comparison.LT, "4")),
                good("toughness<=5", numeric(SearchNumericField.TOUGHNESS, Comparison.LE, "5")),
                good("loyalty>0", numeric(SearchNumericField.LOYALTY, Comparison.GT, "0")),
                good("defense=+6", numeric(SearchNumericField.DEFENSE, Comparison.EQ, "+6")),
                good("mv=.5", numeric(SearchNumericField.MANA_VALUE, Comparison.EQ, ".5")),
                good("legal:commander", QueryAst.LegalIn(FormatId(ProviderId("scryfall"), "commander"), LegalityStatus.LEGAL)),
                good("format=modern", QueryAst.LegalIn(FormatId(ProviderId("scryfall"), "modern"), LegalityStatus.LEGAL)),
                good("is:reserved", QueryAst.Flag(SearchFlag.RESERVED, true)),
                good("IS:GAMECHANGER", QueryAst.Flag(SearchFlag.GAMECHANGER, true)),
                good("owned", QueryAst.Owned(Comparison.GT, BigDecimal.ZERO)),
                good("owned>=2", QueryAst.Owned(Comparison.GE, BigDecimal("2"))),
                good("owned:0", QueryAst.Owned(Comparison.EQ, BigDecimal.ZERO)),
                good("in:deck", QueryAst.InDeck),
                good("price<2.50", QueryAst.Price(Comparison.LT, BigDecimal("2.50"))),
                good("alpha beta", QueryAst.And(SnapshotList.of(a, b))),
                good("alpha AND beta", QueryAst.And(SnapshotList.of(a, b))),
                good("alpha OR beta gamma", QueryAst.Or(SnapshotList.of(a, QueryAst.And(SnapshotList.of(b, c))))),
                good("(alpha OR beta) gamma", QueryAst.And(SnapshotList.of(QueryAst.Or(SnapshotList.of(a, b)), c))),
                good("-alpha", QueryAst.Not(a)),
                good("NOT (alpha OR beta)", QueryAst.Not(QueryAst.Or(SnapshotList.of(a, b)))),
                good("-is:reserved", QueryAst.Not(QueryAst.Flag(SearchFlag.RESERVED, true))),
                good("\"OR\"", text("OR")),
                bad("", 0, 0), bad("  ", 2, 2),
                bad("name:", 5, 5), bad("name:\"\"", 5, 7),
                bad("unknown:alpha", 0, 7), bad("alpha OR", 8, 8),
                bad("OR alpha", 0, 2), bad("alpha AND", 9, 9),
                bad("(alpha", 6, 6), bad("alpha)", 5, 6), bad("()", 1, 2),
                bad("name:\"open", 5, 10), bad("name:\"a\\q\"", 7, 9),
                bad("name>alpha", 4, 5), bad("finish!=foil", 6, 8),
                bad("c:wc", 2, 4), bad("c:purple", 2, 8),
                bad("mv:NaN", 3, 6), bad("power:*", 6, 7),
                bad("mv:1e2147483647", 3, 15), bad("price:-1", 6, 8),
                bad("owned:1.5", 6, 9), bad("owned:-1", 6, 8),
                bad("is:unknown", 3, 10), bad("in:other", 3, 8),
                bad("legal:\" \"", 6, 9), bad("format:\"\t\"", 7, 10),
                bad("mv!2", 2, 3), bad("name==alpha", 5, 6),
                bad("name:\"😀\" unknown:x", 10, 17),
            )
        }
    }
}
