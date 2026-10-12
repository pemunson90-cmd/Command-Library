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
    @Test fun corruptAndNegativeCursorsAreRejected() {
        assertThrows(Exception::class.java) { RulesCursors.decode("{}") }
        assertThrows(IllegalArgumentException::class.java) { RulesCursors.decode(RulesCursors.encode(RulesCursor("g", "f".repeat(64), -1))) }
    }
}
