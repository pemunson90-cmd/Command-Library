package com.architectpsg.commandlibrary.data.storage

import com.architectpsg.commandlibrary.core.model.*
import org.junit.Assert.*
import org.junit.Test

class NamedParametersTest {
    @Test fun bindsFirstAppearanceOnceRatherThanCallerOrder() {
        val values = NamedParameters.bindOrder("SELECT :b, :a, :b", listOf(
            SqlParameter("a", SqlValue.Integer(1)), SqlParameter("b", SqlValue.Text("two")),
        ))
        assertEquals(listOf(SqlValue.Text("two"), SqlValue.Integer(1)), values)
    }

    @Test fun ignoresLiteralIdentifiersAndBothCommentForms() {
        val sql = "SELECT ':literal''?@', \"a:quoted\", `b:quoted`, [c:quoted], :real /* :block ? */ -- :line\n; /* tail */"
        assertEquals(listOf(SqlValue.Null), NamedParameters.bindOrder(sql, listOf(SqlParameter("real", SqlValue.Null))))
    }

    @Test fun rejectsAmbiguousAndUnsupportedBindings() {
        val invalid = listOf("SELECT ?", "SELECT ?12", "SELECT @value", "SELECT ${'$'}value", "SELECT :1", "SELECT :é", "SELECT :a${'$'}b", "SELECT :aé")
        invalid.forEach { sql -> assertThrows(IllegalArgumentException::class.java) { NamedParameters.bindOrder(sql, emptyList()) } }
        assertThrows(IllegalArgumentException::class.java) { NamedParameters.bindOrder("SELECT :a", emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { NamedParameters.bindOrder("SELECT 1", listOf(SqlParameter("a", SqlValue.Null))) }
        assertThrows(IllegalArgumentException::class.java) { NamedParameters.bindOrder("SELECT :a", listOf(SqlParameter("a", SqlValue.Null), SqlParameter("a", SqlValue.Null))) }
    }

    @Test fun rejectsMultipleStatementsUnterminatedTokensAndManualCommit() {
        listOf("SELECT 1; SELECT 2", "SELECT 1;;", "SELECT ':bad", "SELECT 1 /*bad", "-- only comment", "/* comment */ COMMIT", "ROLLBACK", "PRAGMA user_version=9", "ATTACH 'other.db' AS other").forEach { sql ->
            assertThrows(IllegalArgumentException::class.java) { NamedParameters.bindOrder(sql, emptyList()) }
        }
        assertEquals(emptyList<SqlValue>(), NamedParameters.bindOrder("SELECT ';'; -- trailing", emptyList()))
    }

    @Test fun returns101WithoutSteppingLookahead102() {
        var steps = 0
        assertEquals(101, boundedRows(101) { ++steps }.size)
        assertEquals(101, steps)
        assertEquals(listOf(1, 2), boundedRows(101) { if (steps++ < 103) steps - 101 else null })
        assertThrows(IllegalArgumentException::class.java) { SqlRequest("SELECT 1", SnapshotList.empty(), 102) }
    }
}
