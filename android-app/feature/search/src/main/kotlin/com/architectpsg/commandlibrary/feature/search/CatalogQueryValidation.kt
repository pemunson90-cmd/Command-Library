package com.architectpsg.commandlibrary.feature.search

import com.architectpsg.commandlibrary.core.common.DecimalComparisonKeys
import com.architectpsg.commandlibrary.core.model.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

internal data class ValidatedCatalogQuery(val query: QueryAst, val fingerprint: ContentHash, val unsupported: Boolean)

/** Admission precedes SQL expansion; the fingerprint is a length-delimited, typed binary stream. */
internal class CatalogQueryValidation(private val cancel: CancelToken) {
    private var nodes = 0
    private var textBytes = 0
    private var unsupported = false
    private val bytes = ByteArrayOutputStream()
    private val fingerprint = DataOutputStream(bytes)

    fun validate(query: QueryAst, policy: FaceMatchPolicy): ValidatedCatalogQuery {
        token("catalog-query"); token("compiler1"); token("projection1"); token("single-printing1"); token(policy.name)
        val normalized = visit(query, 0)
        cancel.throwIfCancelled()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
        return ValidatedCatalogQuery(normalized, ContentHash("SHA-256", hash.joinToString("") { "%02x".format(it) }), unsupported)
    }

    private fun token(value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        fingerprint.writeInt(encoded.size); fingerprint.write(encoded)
    }

    private fun text(value: String, normalize: Boolean = true, categorical: Boolean = false): String {
        cancel.throwIfCancelled()
        require(value.length <= 4096) { "Query text exceeds value budget" }
        var at = 0
        while (at < value.length) {
            val c = value[at++]
            require(c != '\u0000') { "Query text contains NUL" }
            if (c.isHighSurrogate()) {
                require(at < value.length && value[at].isLowSurrogate()) { "Query text contains invalid Unicode" }; at++
            } else require(!c.isLowSurrogate()) { "Query text contains invalid Unicode" }
        }
        val rawBytes = value.toByteArray(Charsets.UTF_8).size
        require(rawBytes <= 4096) { "Query text exceeds value budget" }
        // READY1 keeps categorical source tokens verbatim: fold ASCII only, preserving whitespace
        // and non-ASCII. Human text instead follows the materializer's NFKC/lowercase/trim policy.
        val admitted = when {
            categorical -> buildString(value.length) {
                for (c in value) append(if (c in 'A'..'Z') (c.code + 32).toChar() else c)
            }
            normalize -> Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim()
            else -> value
        }
        val normalizedBytes = admitted.toByteArray(Charsets.UTF_8).size
        require(normalizedBytes <= 4096) { "Normalized query text exceeds value budget" }
        textBytes += maxOf(rawBytes, normalizedBytes)
        require(textBytes <= 32768) { "Query exceeds total text budget" }
        require(admitted.isNotBlank()) { "Query text is empty" }
        token(admitted)
        return admitted
    }

    private fun visit(query: QueryAst, depth: Int): QueryAst {
        cancel.throwIfCancelled()
        require(depth <= 32 && ++nodes <= 256) { "Query exceeds depth or node budget" }
        return when (query) {
            is QueryAst.And -> {
                token("and"); fingerprint.writeInt(query.terms.size)
                require(query.terms.isNotEmpty() && query.terms.size <= 256) { "Invalid AND arity" }
                QueryAst.And(SnapshotList.copyOf(query.terms.map { visit(it, depth + 1) }))
            }
            is QueryAst.Or -> {
                token("or"); fingerprint.writeInt(query.terms.size)
                require(query.terms.isNotEmpty() && query.terms.size <= 256) { "Invalid OR arity" }
                QueryAst.Or(SnapshotList.copyOf(query.terms.map { visit(it, depth + 1) }))
            }
            is QueryAst.Not -> { token("not"); QueryAst.Not(visit(query.term, depth + 1)) }
            is QueryAst.Text -> {
                token("text"); token(query.field.name); fingerprint.writeBoolean(query.exact)
                query.copy(value = text(query.value, categorical = query.field == SearchTextField.LAYOUT))
            }
            is QueryAst.Numeric -> {
                token("numeric"); token(query.field.name); token(query.comparison.name)
                token(DecimalComparisonKeys.queryKey(query.value)); query
            }
            is QueryAst.Colors -> {
                token("colors"); fingerprint.writeBoolean(query.identity); token(query.comparison.name)
                require(query.values.size <= 5 && query.values.distinct().size == query.values.size) { "Duplicate query colors" }
                val values = query.values.sortedBy { it.ordinal }
                fingerprint.writeInt(values.fold(0) { mask, color -> mask or (1 shl color.ordinal) })
                query.copy(values = SnapshotList.copyOf(values))
            }
            is QueryAst.PrintingText -> {
                token("printing"); token(query.field.name)
                val value = text(query.value, categorical = true)
                if (query.field == PrintingSearchField.FINISH)
                    require(value in listOf("nonfoil", "foil", "etched")) { "Unsupported finish token" }
                query.copy(value = value)
            }
            is QueryAst.Flag -> { token("flag"); token(query.field.name); fingerprint.writeBoolean(query.value); query }
            is QueryAst.LegalIn -> {
                token("legal"); text(query.format.source.value, false); text(query.format.raw, false); token(query.status.name)
                if (query.status == LegalityStatus.UNKNOWN || query.status == LegalityStatus.REVIEW_REQUIRED) unsupported = true
                query
            }
            is QueryAst.Owned -> {
                token("owned"); token(query.comparison.name); token(DecimalComparisonKeys.queryKey(query.quantity))
                require(query.quantity.signum() >= 0 && query.quantity.stripTrailingZeros().scale() <= 0) { "Invalid owned quantity" }
                unsupported = true; query
            }
            is QueryAst.Price -> {
                token("price"); token(query.comparison.name); token(DecimalComparisonKeys.queryKey(query.amount))
                require(query.amount.signum() >= 0) { "Invalid price" }; unsupported = true; query
            }
            QueryAst.InDeck -> { token("in-deck"); unsupported = true; query }
        }
    }
}
