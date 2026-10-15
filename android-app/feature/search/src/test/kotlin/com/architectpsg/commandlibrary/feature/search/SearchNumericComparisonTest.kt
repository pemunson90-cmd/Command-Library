package com.architectpsg.commandlibrary.feature.search

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.architectpsg.commandlibrary.core.model.Comparison
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

/** Synthetic arithmetic evidence using actual bundled SQLite BINARY, not corpus/device evidence. */
class SearchNumericComparisonTest {
    private fun key(value: String) = requireNotNull(SearchNumericComparison.sourceKey(value))
    private fun rejects(action: () -> Unit) { assertThrows(IllegalArgumentException::class.java, action) }
    private val operators = mapOf(Comparison.EQ to "=", Comparison.NE to "!=", Comparison.LT to "<", Comparison.LE to "<=", Comparison.GT to ">", Comparison.GE to ">=")
    private fun expected(order: Int, comparison: Comparison) = when (comparison) {
        Comparison.EQ -> order == 0; Comparison.NE -> order != 0
        Comparison.LT -> order < 0; Comparison.LE -> order <= 0
        Comparison.GT -> order > 0; Comparison.GE -> order >= 0
    }
    private fun verify(db: SQLiteConnection, source: String, threshold: BigDecimal) {
        val sourceKey = key(source); val queryKey = SearchNumericComparison.queryKey(threshold)
        val order = BigDecimal(source).compareTo(threshold)
        assertTrue(sourceKey.length <= 76)
        for ((comparison, operator) in operators) {
            val wanted = expected(order, comparison)
            db.prepare("SELECT CASE WHEN ? COLLATE BINARY $operator ? THEN 1 ELSE 0 END").use { statement ->
                statement.bindText(1, sourceKey); statement.bindText(2, queryKey)
                assertTrue(statement.step())
                assertEquals("$comparison source length=${source.length} threshold=$threshold", wanted, statement.getLong(0) == 1L)
            }
            assertEquals(if (wanted) SearchTruth.TRUE else SearchTruth.FALSE, SearchNumericComparison.evaluate(source, comparison, threshold))
        }
    }

    @Test fun canonicalZeroSignsAndRedundantZerosDoNotChangeComparisonKeys() {
        for (zero in listOf("0", "-0", "+0", "000.000", "-.0", "+.0", "0.")) assertEquals("1", key(zero))
        for (one in listOf("1", "01.000", "+1.", "0001")) assertEquals(key("1"), key(one))
        assertEquals(key(".5"), key("0.500")); assertEquals(key("-.5"), key("-0.500"))
    }

    @Test fun malformedAndAbsentStatsStayUnknownAndCannotMatchThroughNegation() {
        for (raw in listOf(null, "", ".", "+", "-", "1.2.3", " 1", "1 ", "1e2", "*", "1+*", "∞", "NaN", "１２")) {
            for (comparison in Comparison.entries) {
                val truth = SearchNumericComparison.evaluate(raw, comparison, BigDecimal.ZERO)
                assertEquals(SearchTruth.UNKNOWN, truth); assertEquals(SearchTruth.UNKNOWN, truth.not())
            }
        }
    }

    @Test fun queryBoundsAreCheckedBeforeExpansionEvenWhenSourceIsMissing() {
        assertEquals(key("100"), SearchNumericComparison.queryKey(BigDecimal("1E2")))
        assertEquals(key("1"), SearchNumericComparison.queryKey(BigDecimal("1." + "0".repeat(64))))
        assertNotNull(SearchNumericComparison.queryKey(BigDecimal("." + "1".repeat(63))))
        assertNotNull(SearchNumericComparison.queryKey(BigDecimal("9".repeat(64))))
        for (bad in listOf(BigDecimal("9".repeat(65)), BigDecimal("-." + "1".repeat(63)), BigDecimal(BigInteger.ONE, Int.MIN_VALUE), BigDecimal(BigInteger.ONE, Int.MAX_VALUE))) {
            rejects { SearchNumericComparison.queryKey(bad) }
            rejects { SearchNumericComparison.evaluate(null, Comparison.EQ, bad) }
            rejects { SearchNumericComparison.evaluate("*", Comparison.NE, bad) }
        }
    }

    @Test fun nonzeroSuffixAfterDigit64NeverEqualsTheAdmittedThreshold() {
        val prefix = "1".repeat(64)
        BundledSQLiteDriver().open(":memory:").use { db ->
            for (sign in listOf("", "-")) {
                val threshold = BigDecimal(sign + if (sign.isEmpty()) prefix else prefix.take(63))
                val source = if (sign.isEmpty()) prefix + "." + "0".repeat(512) + "1" else "-" + prefix.take(63) + "." + "0".repeat(512) + "1"
                verify(db, source, threshold)
            }
            val longA = "1." + "0".repeat(63) + "1"
            val longB = "1." + "0".repeat(63) + "2"
            assertEquals("Keys intentionally cannot establish source equality", key(longA), key(longB))
            verify(db, longA, BigDecimal.ONE); verify(db, longB, BigDecimal.ONE)
            assertEquals(key("1"), key("1." + "0".repeat(4096)))
        }
    }

    @Test fun boundaryValuesAndAllSixOperatorsMatchBigDecimalInActualSQLite() {
        val values = listOf("-" + "9".repeat(128), "-100.0001", "-100", "-10", "-1.2", "-1.19", "-.00001", "-0", "0", ".00001", ".1", ".10001", "1.19", "1.2", "10", "100", "100.0001", "9".repeat(128), "." + "0".repeat(4096) + "1")
        val thresholds = listOf("-100", "-1.2", "-.0001", "0", ".0001", "1.19", "1.2", "100", "9".repeat(64), "." + "1".repeat(63))
        BundledSQLiteDriver().open(":memory:").use { db ->
            for (source in values) for (threshold in thresholds) verify(db, source, BigDecimal(threshold))
        }
    }

    @Test fun deterministicBoundaryBiasedRandomComparisonsMatchIndependentArithmetic() {
        val random = Random(20261015)
        fun number(maxDigits: Int): String {
            val n = random.nextInt(1, maxDigits + 1)
            val digits = CharArray(n) { ('0'.code + random.nextInt(10)).toChar() }.concatToString()
            val point = random.nextInt(n + 1)
            return (if (random.nextBoolean()) "-" else "") + digits.substring(0, point) + "." + digits.substring(point)
        }
        BundledSQLiteDriver().open(":memory:").use { db ->
            repeat(600) {
                val source = number(if (it % 2 == 0) 256 else 64)
                val threshold = BigDecimal(number(60))
                verify(db, source, threshold)
            }
        }
    }

    @Test fun textBinaryIndexUsesTheSameKeysWithoutNumericAffinity() {
        BundledSQLiteDriver().open(":memory:").use { db ->
            db.prepare("CREATE TABLE numeric_fact(id INTEGER PRIMARY KEY, comparison_key TEXT COLLATE BINARY NOT NULL)").use { it.step() }
            db.prepare("CREATE INDEX numeric_fact_key ON numeric_fact(comparison_key)").use { it.step() }
            val values = listOf("-100", "-1.2", "-.5", "0", ".5", "1.2", "100")
            values.forEachIndexed { i, value ->
                db.prepare("INSERT INTO numeric_fact VALUES(?,?)").use { it.bindLong(1, i.toLong()); it.bindText(2, key(value)); it.step() }
            }
            val query = SearchNumericComparison.queryKey(BigDecimal(".5"))
            db.prepare("SELECT id FROM numeric_fact WHERE comparison_key >= ? COLLATE BINARY ORDER BY id").use { statement ->
                statement.bindText(1, query)
                val ids = buildList { while (statement.step()) add(statement.getLong(0)) }
                assertEquals(listOf(4L, 5L, 6L), ids)
            }
            db.prepare("EXPLAIN QUERY PLAN SELECT id FROM numeric_fact WHERE comparison_key >= ? COLLATE BINARY").use { statement ->
                statement.bindText(1, query)
                val plans = buildList { while (statement.step()) add(statement.getText(3)) }
                assertTrue(plans.any { it.contains("USING COVERING INDEX numeric_fact_key") })
            }
        }
    }

    @Test fun completeKleeneTruthTablesPreserveUnknownEvidence() {
        val values = listOf(SearchTruth.TRUE, SearchTruth.FALSE, SearchTruth.UNKNOWN)
        val and = listOf(listOf(SearchTruth.TRUE, SearchTruth.FALSE, SearchTruth.UNKNOWN), List(3) { SearchTruth.FALSE }, listOf(SearchTruth.UNKNOWN, SearchTruth.FALSE, SearchTruth.UNKNOWN))
        val or = listOf(List(3) { SearchTruth.TRUE }, listOf(SearchTruth.TRUE, SearchTruth.FALSE, SearchTruth.UNKNOWN), listOf(SearchTruth.TRUE, SearchTruth.UNKNOWN, SearchTruth.UNKNOWN))
        for ((i, a) in values.withIndex()) for ((j, b) in values.withIndex()) {
            assertEquals(and[i][j], a.and(b)); assertEquals(or[i][j], a.or(b))
            assertEquals(a.and(b).not(), a.not().or(b.not()))
            assertEquals(a.or(b).not(), a.not().and(b.not()))
        }
    }

    @Test fun sourceSizeCeilingIsExplicitAndLongZerosNeedNoDecimalExpansion() {
        val raw = "0".repeat(8 * 1024 * 1024)
        assertEquals("1", key(raw))
        rejects { SearchNumericComparison.sourceKey(raw + "0") }
        rejects { SearchNumericComparison.evaluate(raw + "0", Comparison.EQ, BigDecimal.ZERO) }
        assertEquals(key("1"), key("0".repeat(100_000) + "1." + "0".repeat(100_000)))
    }
}
