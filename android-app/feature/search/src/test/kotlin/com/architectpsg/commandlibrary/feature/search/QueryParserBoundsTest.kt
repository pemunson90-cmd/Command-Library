package com.architectpsg.commandlibrary.feature.search

import com.architectpsg.commandlibrary.core.model.*
import org.junit.Assert.*
import org.junit.Test

class QueryParserBoundsTest {
    private val parser = LocalCardQueryParser()
    private fun invalid(input: String) { assertTrue(input.take(80), parser.parse(input) is CardQueryParseResult.Invalid) }
    @Test fun nestedAndFlatInputsHaveFiniteIndependentBudgets() {
        assertTrue(parser.parse("(".repeat(32) + "a" + ")".repeat(32)) is CardQueryParseResult.Valid)
        invalid("(".repeat(33) + "a" + ")".repeat(33))
        invalid("NOT ".repeat(33) + "a")
        assertTrue(parser.parse(List(128) { "a" }.joinToString(" ")) is CardQueryParseResult.Valid)
        invalid(List(129) { "a" }.joinToString(" "))
        invalid(List(86) { "name:a" }.joinToString(" "))
    }
    @Test fun unicodeLengthAndFixedDecimalsAreBoundedWithoutDestructiveNormalization() {
        val value = "😀".repeat(2048)
        assertEquals(CardQueryParseResult.Valid(QueryAst.Text(SearchTextField.NAME, value, false)), parser.parse(value))
        invalid(value + "😀")
        invalid("a".repeat(2049))
        assertTrue(parser.parse("mv:" + "9".repeat(64)) is CardQueryParseResult.Valid)
        invalid("mv:" + "9".repeat(65))
        invalid("mv:1e-2147483647")
        invalid("mv:1E+3")
        invalid("mv:Infinity")
    }
}
