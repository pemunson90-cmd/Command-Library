package com.architectpsg.commandlibrary.feature.rules

import org.junit.Assert.*
import org.junit.Test

class RulesCursorTest {
    @Test fun cursorKeepsSnapshotQueryAndOrdinal() {
        val cursor = RulesCursor("g1", RulesCursors.key("café", listOf("NUMBERED")), 42)
        assertEquals(cursor, RulesCursors.decode(RulesCursors.encode(cursor)))
        assertNotEquals(cursor.query, RulesCursors.key("café", listOf("EXAMPLE")))
        assertNotEquals(cursor.query, RulesCursors.key("cafe", listOf("NUMBERED")))
    }
    @Test fun operatorsAndPunctuationBecomeLiteralWords() {
        assertEquals(listOf("café", "OR", "100", "1", "Ω"), RulesCursors.tokens("café OR (100.1) - Ω"))
        assertEquals("\"café\" AND \"OR\"", RulesCursors.match(listOf("café", "OR")))
        assertEquals(RulesCursors.tokens("café"), RulesCursors.tokens("cafe\u0301"))
    }
    @Test fun fractionalQuotedOverflowAndWrongPrimitiveCursorsAreRejected() {
        val key = "f".repeat(64)
        for (ordinal in listOf("0.5", "1e0", "\"1\"", "9223372036854775808", "true", "null")) {
            assertThrows(Exception::class.java) { RulesCursors.decode("{\"schema\":1,\"generation\":\"g\",\"query\":\"$key\",\"ordinal\":$ordinal}") }
        }
        for (schema in listOf("1.5", "1e0", "\"1\"", "true")) {
            assertThrows(Exception::class.java) { RulesCursors.decode("{\"schema\":$schema,\"generation\":\"g\",\"query\":\"$key\",\"ordinal\":0}") }
        }
        assertThrows(Exception::class.java) { RulesCursors.decode("{\"schema\":1,\"generation\":5,\"query\":\"$key\",\"ordinal\":0}") }
        assertThrows(Exception::class.java) { RulesCursors.decode("{\"schema\":1,\"generation\":\"g\",\"query\":42,\"ordinal\":0}") }
    }
    @Test fun corruptAndNegativeCursorsAreRejected() {
        assertThrows(Exception::class.java) { RulesCursors.decode("{}") }
        assertThrows(IllegalArgumentException::class.java) { RulesCursors.decode(RulesCursors.encode(RulesCursor("g", "f".repeat(64), -1))) }
    }
}
