package com.architectpsg.commandlibrary.data.storage

import com.architectpsg.commandlibrary.core.model.SqlParameter
import com.architectpsg.commandlibrary.core.model.SqlValue

/** SQLite indexes distinct named parameters in order of first appearance. */
internal object NamedParameters {
    fun bindOrder(sql: String, parameters: List<SqlParameter>): List<SqlValue> {
        val names = linkedSetOf<String>()
        var i = 0
        var terminated = false
        var firstWord: String? = null
        while (i < sql.length) {
            val c = sql[i]
            when {
                c.isWhitespace() -> i++
                sql.startsWith("--", i) -> {
                    i = sql.indexOf('\n', i + 2).let { if (it < 0) sql.length else it + 1 }
                }
                sql.startsWith("/*", i) -> {
                    val end = sql.indexOf("*/", i + 2)
                    require(end >= 0) { "Unterminated SQL comment" }
                    i = end + 2
                }
                terminated -> throw IllegalArgumentException("Only one SQL statement is supported")
                c == ';' -> { terminated = true; i++ }
                c == '\'' || c == '"' || c == '`' || c == '[' -> {
                    val end = if (c == '[') ']' else c
                    i++
                    var closed = false
                    while (i < sql.length) {
                        if (sql[i++] == end) {
                            if (end != ']' && i < sql.length && sql[i] == end) i++
                            else { closed = true; break }
                        }
                    }
                    require(closed) { "Unterminated SQL literal or identifier" }
                }
                c == ':' -> {
                    val start = ++i
                    require(i < sql.length && identifierStart(sql[i])) { "Use :identifier parameters" }
                    while (i < sql.length && identifierPart(sql[i])) i++
                    require(i == sql.length || (!sql[i].isLetterOrDigit() && sql[i] != '$')) {
                        "Parameter identifiers must use ASCII letters, digits and underscores"
                    }
                    names += sql.substring(start, i)
                }
                c == '?' || c == '@' || c == '$' ->
                    throw IllegalArgumentException("Only :identifier parameters are supported")
                identifierStart(c) -> {
                    val start = i++
                    while (i < sql.length && identifierPart(sql[i])) i++
                    if (firstWord == null) firstWord = sql.substring(start, i).uppercase(java.util.Locale.ROOT)
                }
                else -> i++
            }
        }
        require(firstWord != null) { "SQL must contain a statement" }
        require(firstWord !in setOf("BEGIN", "END", "COMMIT", "ROLLBACK", "SAVEPOINT", "RELEASE", "ATTACH", "DETACH", "PRAGMA", "VACUUM")) {
            "Transaction and connection control SQL is owned by the adapter"
        }
        val supplied = parameters.associate { it.name to it.value }
        require(supplied.size == parameters.size) { "Duplicate supplied parameters" }
        require(supplied.keys == names) { "Missing or unused supplied parameters" }
        return names.map { supplied.getValue(it) }
    }

    private fun identifierStart(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c == '_'
    private fun identifierPart(c: Char): Boolean = identifierStart(c) || c in '0'..'9'
}

/** The next row is never stepped once the caller's requested bound is reached. */
internal inline fun <T> boundedRows(maxRows: Int, next: () -> T?): List<T> {
    require(maxRows in 1..101)
    val rows = ArrayList<T>()
    while (rows.size < maxRows) rows += next() ?: break
    return rows
}
