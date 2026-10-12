package com.architectpsg.commandlibrary.feature.rules

import com.google.gson.JsonObject
import java.security.MessageDigest
import java.text.Normalizer

internal data class RulesCursor(val generation: String, val query: String, val ordinal: Long)
internal object RulesCursors {
    fun key(text: String, kinds: List<String>): String = MessageDigest.getInstance("SHA-256").digest((text + "\u0000" + kinds.sorted().joinToString(",")).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun encode(v: RulesCursor): String = JsonObject().apply { addProperty("schema", 1); addProperty("generation", v.generation); addProperty("query", v.query); addProperty("ordinal", v.ordinal) }.toString()
    fun decode(v: String): RulesCursor = RulesCodec.decode(v).let {
        require(it.keySet() == setOf("schema", "generation", "query", "ordinal"))
        RulesCursor(it["generation"].asString, it["query"].asString, it["ordinal"].asLong).also { c -> require(c.generation.isNotBlank() && c.generation.length <= 128 && c.query.matches(Regex("[a-f0-9]{64}")) && c.ordinal >= 0) }
    }
    /** Literal words only; quote FTS tokens so punctuation/operators cannot become MATCH syntax. */
    fun tokens(text: String): List<String> = Regex("[\\p{L}\\p{M}\\p{N}]+").findAll(Normalizer.normalize(text, Normalizer.Form.NFC)).map { it.value }.toList()
    fun match(tokens: List<String>): String = tokens.joinToString(" AND ") { "\"$it\"" }
}
