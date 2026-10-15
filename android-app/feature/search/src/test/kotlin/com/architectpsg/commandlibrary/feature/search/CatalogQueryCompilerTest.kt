package com.architectpsg.commandlibrary.feature.search

import com.architectpsg.commandlibrary.core.model.*
import java.math.BigDecimal
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

internal val COMPILER_NO_CANCEL: CancelToken = object : CancelToken {
    override val isCancelled: Boolean = false
    override fun throwIfCancelled() = Unit
}

class CatalogQueryCompilerTest {
    private val compiler = LocalCatalogQueryCompiler()
    private fun name(value: String) = QueryAst.Text(SearchTextField.NAME, value, false)
    private fun compile(query: QueryAst, policy: FaceMatchPolicy = FaceMatchPolicy.ANY_FACE) =
        compiler.compile(query, policy, COMPILER_NO_CANCEL)
    private fun plan(query: QueryAst, policy: FaceMatchPolicy = FaceMatchPolicy.ANY_FACE) =
        (compile(query, policy) as Outcome.Success).value
    private fun invalid(query: QueryAst) {
        val result = compile(query)
        assertTrue(result.toString(), result is Outcome.Failure)
        assertEquals(FailureCode.INVALID_INPUT, (result as Outcome.Failure).error.code)
    }

    @Test fun validatesDirectAstBudgetsUnicodeDecimalsAndWholeContextBeforeSqlExpansion() {
        listOf(QueryAst.And(SnapshotList.empty()), QueryAst.Or(SnapshotList.empty()), name(""), name(" \t"),
            name("a\u0000b"), name("\uD800"), name("\uDC00"), name("é".repeat(2049)),
            name("a".repeat(4097)), name("\uFDFA".repeat(250)),
            QueryAst.Colors(SnapshotList.of(Color.WHITE, Color.WHITE), false, Comparison.EQ),
            QueryAst.PrintingText(PrintingSearchField.FINISH, "foil,nonfoil"),
            QueryAst.PrintingText(PrintingSearchField.FINISH, "ＦＯＩＬ"),
            QueryAst.PrintingText(PrintingSearchField.RARITY, "rare\u0000suffix"),
            QueryAst.Text(SearchTextField.LAYOUT, "normal\u0000suffix", true),
            QueryAst.Numeric(SearchNumericField.POWER, Comparison.EQ, BigDecimal("1E2147483647")),
            QueryAst.Numeric(SearchNumericField.POWER, Comparison.EQ, BigDecimal("1E-2147483647")),
            QueryAst.Owned(Comparison.EQ, BigDecimal("1.5")), QueryAst.Price(Comparison.EQ, BigDecimal("-1")))
            .forEach(::invalid)
        invalid(QueryAst.And(SnapshotList.copyOf(List(256) { name("a") })))
        invalid(QueryAst.And(SnapshotList.copyOf(List(9) { name("a".repeat(4096)) })))
        var deep: QueryAst = name("a")
        repeat(32) { deep = QueryAst.Not(deep) }
        assertTrue(compile(deep) is Outcome.Success)
        invalid(QueryAst.Not(deep))
        // 510 distinct identities plus four statuses cross the 512 binding ceiling.
        val tooManyParameters = QueryAst.And(SnapshotList.copyOf(List(255) {
            QueryAst.LegalIn(FormatId(ProviderId("source$it"), "format$it"), LegalityStatus.entries[it % 4])
        }))
        val parameterFailure = compile(tooManyParameters) as Outcome.Failure
        assertEquals(FailureCode.INVALID_INPUT, parameterFailure.error.code)
        assertTrue(parameterFailure.error.message.contains("parameter budget"))
        val tooMuchSql = QueryAst.And(SnapshotList.copyOf(List(254) { QueryAst.Flag(SearchFlag.GAMECHANGER, true) } +
            QueryAst.PrintingText(PrintingSearchField.SET, "aaa")))
        val sqlFailure = compile(tooMuchSql, FaceMatchPolicy.SAME_FACE) as Outcome.Failure
        assertEquals(FailureCode.INVALID_INPUT, sqlFailure.error.code)
        assertTrue(sqlFailure.error.message.contains("SQL budget"))
        for (context in listOf(QueryAst.Owned(Comparison.GT, BigDecimal.ZERO), QueryAst.InDeck,
            QueryAst.Price(Comparison.LT, BigDecimal.ONE),
            QueryAst.LegalIn(FormatId(ProviderId("scryfall"), "modern"), LegalityStatus.UNKNOWN),
            QueryAst.LegalIn(FormatId(ProviderId("scryfall"), "modern"), LegalityStatus.REVIEW_REQUIRED))) {
            for (query in listOf(context, QueryAst.Not(context), QueryAst.Or(SnapshotList.of(name("a"), context))))
                assertEquals(UnavailableReason.UNSUPPORTED, (compile(query) as Outcome.Unavailable).reason)
        }
    }

    @Test fun fingerprintAndBindingsPreserveTypesSourceIdentityOrderAndNormalization() {
        val original = plan(name("  ＡLPHA  "))
        assertEquals(plan(name("alpha")).fingerprint, original.fingerprint)
        assertEquals("SHA-256", original.fingerprint.algorithm)
        assertTrue(original.fingerprint.hex.matches(Regex("[0-9a-f]{64}")))
        assertNotEquals(original.fingerprint, plan(name("alpha"), FaceMatchPolicy.SAME_FACE).fingerprint)
        assertNotEquals(plan(QueryAst.And(SnapshotList.of(name("a"), name("bc")))).fingerprint,
            plan(QueryAst.And(SnapshotList.of(name("ab"), name("c")))).fingerprint)
        assertNotEquals(plan(QueryAst.And(SnapshotList.of(name("a"), name("b")))).fingerprint,
            plan(QueryAst.And(SnapshotList.of(name("b"), name("a")))).fingerprint)
        fun legal(source: String, raw: String) = QueryAst.LegalIn(FormatId(ProviderId(source), raw), LegalityStatus.LEGAL)
        assertNotEquals(plan(legal("scryfall", "modern")).fingerprint, plan(legal("other", "modern")).fingerprint)
        assertNotEquals(plan(legal("scryfall", "Modern")).fingerprint, plan(legal("scryfall", "modern")).fingerprint)
        assertEquals(plan(QueryAst.Numeric(SearchNumericField.POWER, Comparison.EQ, BigDecimal("1.00"))).fingerprint,
            plan(QueryAst.Numeric(SearchNumericField.POWER, Comparison.EQ, BigDecimal.ONE)).fingerprint)
        val colors = QueryAst.Colors(SnapshotList.of(Color.WHITE, Color.BLUE), false, Comparison.EQ)
        assertEquals(plan(colors).fingerprint, plan(colors.copy(values = SnapshotList.of(Color.BLUE, Color.WHITE))).fingerprint)
        fun layout(value: String) = QueryAst.Text(SearchTextField.LAYOUT, value, true)
        assertEquals(plan(layout("NORMAL")).fingerprint, plan(layout("normal")).fingerprint)
        assertNotEquals(plan(layout("normal")).fingerprint, plan(layout(" normal ")).fingerprint)
        assertNotEquals(plan(layout("normal")).fingerprint, plan(layout("ｎｏｒｍａｌ")).fingerprint)
        assertNotEquals(plan(layout("nórmal")).fingerprint, plan(layout("NÓRMAL")).fingerprint)
        fun set(value: String) = QueryAst.PrintingText(PrintingSearchField.SET, value)
        assertEquals(plan(set("AAA")).fingerprint, plan(set("aaa")).fingerprint)
        assertNotEquals(plan(set("aaa")).fingerprint, plan(set(" aaa ")).fingerprint)
        assertNotEquals(plan(set("aaa")).fingerprint, plan(set("ＡＡＡ")).fingerprint)
        assertEquals(plan(QueryAst.PrintingText(PrintingSearchField.FINISH, "FOIL")).fingerprint,
            plan(QueryAst.PrintingText(PrintingSearchField.FINISH, "foil")).fingerprint)
        val keyword = QueryAst.Text(SearchTextField.KEYWORD, "strike", false)
        assertTrue(plan(keyword).predicateSql.contains("instr("))
        assertFalse(plan(keyword.copy(exact = true)).predicateSql.contains("instr("))
        val hostile = "%' OR 1=1 -- :qp999 _ \\"
        val escaped = plan(name(hostile))
        assertFalse(escaped.predicateSql.contains(hostile))
        assertEquals(SqlValue.Text(hostile.lowercase(java.util.Locale.ROOT).trim()), escaped.parameters.single().value)
        assertEquals(1, plan(QueryAst.And(SnapshotList.of(name("a"), name("a")))).parameters.size)
        for (query in listOf(escaped, plan(legal("scryfall", "modern")), plan(colors))) {
            assertEquals(query.parameters.map { it.name }.toSet(),
                Regex(":(qp[0-9]+)").findAll(query.predicateSql).map { it.groupValues[1] }.toSet())
            assertEquals(query.parameters.indices.map { "qp$it" }, query.parameters.map { it.name })
            assertTrue(query.parameters.all { it.value is SqlValue.Text || it.value is SqlValue.Integer })
            assertFalse(query.predicateSql.contains("NOCASE"))
            assertEquals(1, query.compilerVersion); assertEquals(1, query.projectionVersion)
        }
    }

    @Test fun cancellationEscapesBeforeAndDuringValidationAndCompilation() {
        for (stop in listOf(1, 5, 70, 330)) {
            var calls = 0
            val token = object : CancelToken {
                override val isCancelled: Boolean get() = calls >= stop
                override fun throwIfCancelled() { if (++calls >= stop) throw CancellationException("fixture") }
            }
            val query = QueryAst.And(SnapshotList.copyOf(List(100) { name("value$it") }))
            assertThrows(CancellationException::class.java) { compiler.compile(query, FaceMatchPolicy.ANY_FACE, token) }
        }
    }
}
