package com.architectpsg.commandlibrary.feature.search

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.architectpsg.commandlibrary.core.common.DecimalComparisonKeys
import com.architectpsg.commandlibrary.core.model.*
import java.io.File
import java.math.BigDecimal
import java.text.Normalizer
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

/** Invented facts on actual exported schema9 DDL; independent 3VL reference, no source/corpus claim.
 * Foreign keys are disabled for isolated fact fixtures, so this is not import/readiness evidence.
 */
class CatalogQuerySqlOracleTest {
    private data class View(val name: String, val text: String?, val type: String?,
        val stats: List<String?>, val colors: Int?)
    private data class Print(val id: String, val set: String, val rarity: String?, val finish: Int?,
        val changer: Boolean?, val legal: LegalityStatus?, val memberOnly: Boolean = false, val language: String = "en")
    private data class Card(val id: String, val canonical: View, val faces: List<View>, val mana: String?,
        val identity: Int?, val layout: String?, val reserved: Boolean?, val keywords: List<String>?, val prints: List<Print>)
    private enum class Truth {
        T, F, U;
        fun not(): Truth = when (this) { T -> F; F -> T; U -> U }
        fun and(other: Truth): Truth = if (this == F || other == F) F else if (this == U || other == U) U else T
        fun or(other: Truth): Truth = if (this == T || other == T) T else if (this == U || other == U) U else F
    }
    private fun truth(value: Boolean?): Truth = when (value) { true -> Truth.T; false -> Truth.F; null -> Truth.U }
    private fun any(values: List<Truth>): Truth = if (values.isEmpty()) Truth.U else values.fold(Truth.F, Truth::or)
    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim()
    private fun ascii(value: String): String = value.map { if (it in 'A'..'Z') (it.code + 32).toChar() else it }.joinToString("")
    private fun compare(order: Int, op: Comparison): Boolean = when (op) {
        Comparison.EQ -> order == 0; Comparison.NE -> order != 0; Comparison.LT -> order < 0
        Comparison.LE -> order <= 0; Comparison.GT -> order > 0; Comparison.GE -> order >= 0
    }
    private fun numeric(raw: String?, query: QueryAst.Numeric): Truth {
        if (raw == null || !raw.matches(Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))) return Truth.U
        return truth(compare(BigDecimal(raw).compareTo(query.value), query.comparison))
    }
    private fun <T> consensus(values: List<T?>): T? =
        if (values.isNotEmpty() && values.all { it != null && it == values.first() }) values.first() else null

    private fun reference(query: QueryAst, card: Card, policy: FaceMatchPolicy): Truth {
        fun hasPrinting(node: QueryAst): Boolean = when (node) {
            is QueryAst.PrintingText -> true
            is QueryAst.And -> node.terms.any(::hasPrinting)
            is QueryAst.Or -> node.terms.any(::hasPrinting)
            is QueryAst.Not -> hasPrinting(node.term)
            else -> false
        }
        fun evaluate(node: QueryAst, selectedFace: View?, selectedPrint: Print?): Truth {
            fun faceValue(test: (View) -> Truth): Truth = when (policy) {
                FaceMatchPolicy.CANONICAL -> test(card.canonical)
                FaceMatchPolicy.SAME_FACE -> test(requireNotNull(selectedFace))
                FaceMatchPolicy.ANY_FACE -> any((card.faces.ifEmpty { listOf(card.canonical) }).map(test))
            }
            return when (node) {
                is QueryAst.And -> node.terms.map { evaluate(it, selectedFace, selectedPrint) }.fold(Truth.T, Truth::and)
                is QueryAst.Or -> node.terms.map { evaluate(it, selectedFace, selectedPrint) }.fold(Truth.F, Truth::or)
                is QueryAst.Not -> evaluate(node.term, selectedFace, selectedPrint).not()
                is QueryAst.Numeric -> if (node.field == SearchNumericField.MANA_VALUE) numeric(card.mana, node)
                    else faceValue { numeric(it.stats[node.field.ordinal - 1], node) }
                is QueryAst.Colors -> {
                    val wanted = node.values.map { it.ordinal }.toSet()
                    fun colors(mask: Int?): Truth {
                        if (mask == null) return Truth.U
                        val actual = (0..4).filter { (mask and (1 shl it)) != 0 }.toSet()
                        return truth(when (node.comparison) {
                            Comparison.EQ -> actual == wanted; Comparison.NE -> actual != wanted
                            Comparison.LE -> wanted.containsAll(actual); Comparison.LT -> wanted.containsAll(actual) && actual != wanted
                            Comparison.GE -> actual.containsAll(wanted); Comparison.GT -> actual.containsAll(wanted) && actual != wanted
                        })
                    }
                    if (node.identity) colors(card.identity) else faceValue { colors(it.colors) }
                }
                is QueryAst.Text -> {
                    val wanted = normalize(node.value)
                    fun matches(raw: String?): Truth = truth(raw?.let { if (node.exact) normalize(it) == wanted else normalize(it).contains(wanted) })
                    when (node.field) {
                        SearchTextField.LAYOUT -> truth(card.layout?.let {
                            if (node.exact) ascii(it) == ascii(node.value) else ascii(it).contains(ascii(node.value))
                        })
                        SearchTextField.KEYWORD -> truth(card.keywords?.any {
                            if (node.exact) normalize(it) == wanted else normalize(it).contains(wanted)
                        })
                        else -> faceValue { view -> when (node.field) {
                            SearchTextField.NAME -> matches(card.canonical.name).or(matches(view.name))
                            SearchTextField.ORACLE_TEXT -> matches(view.text)
                            SearchTextField.TYPE -> matches(view.type)
                            else -> error("Not a face field")
                        } }
                    }
                }
                is QueryAst.PrintingText -> if (selectedPrint == null) Truth.U else {
                    val value = ascii(node.value)
                    truth(when (node.field) {
                        PrintingSearchField.SET -> ascii(selectedPrint.set) == value
                        PrintingSearchField.RARITY -> selectedPrint.rarity?.let { ascii(it) == value }
                        PrintingSearchField.LANGUAGE -> ascii(selectedPrint.language) == value
                        PrintingSearchField.FINISH -> selectedPrint.finish?.let { mask -> (mask and when (value) {
                            "nonfoil" -> 1; "foil" -> 2; "etched" -> 4; else -> error("Unsupported fixture") }) != 0 }
                    })
                }
                is QueryAst.Flag -> truth((if (node.field == SearchFlag.RESERVED) card.reserved
                    else consensus(card.prints.map { it.changer }))?.let { it == node.value })
                is QueryAst.LegalIn -> {
                    val known = if (node.format.source.value != "scryfall" || node.format.raw != "modern") null else
                        consensus(card.prints.map { it.legal?.takeIf { status -> status != LegalityStatus.UNKNOWN } })
                    truth(known?.let { it == node.status })
                }
                else -> error("Unsupported contextual oracle input")
            }
        }
        fun faces(print: Print?): Truth = if (policy == FaceMatchPolicy.SAME_FACE)
            any(card.faces.ifEmpty { listOf(card.canonical) }.map { evaluate(query, it, print) })
        else evaluate(query, null, print)
        return if (hasPrinting(query)) any(card.prints.map(::faces).ifEmpty { listOf(faces(null)) }) else faces(null)
    }

    private fun cards(): List<Card> {
        fun view(name: String, power: String?, colors: Int?, text: String? = "", type: String? = "creature") =
            View(name, text, type, listOf(power, power, power, power), colors)
        return listOf(
            Card("split", view("Alpha // Beta", null, 3, null, null),
                listOf(view("Alpha", "2", 1, "Draw a card."), view("Beta", "5", 2, "Burn.", "sorcery")),
                "2.50", 3, "transform", true, listOf("Flying", "First strike"),
                listOf(Print("split-a", "aaa", "common", 2, false, LegalityStatus.LEGAL),
                    Print("split-b", "bbb", "rare", 1, true, LegalityStatus.BANNED))),
            Card("empty", view("Empty", "0", 0), emptyList(), "0", 0, "normal", false, emptyList(), emptyList()),
            Card("unknown", view("Unknown", "*", null, null, null), emptyList(), null, null, null, null, null,
                listOf(Print("unknown-a", "aaa", null, null, null, null))),
            Card("consensus", view("Consensus", "-2", 8, "literal 100%_ \\ path"), emptyList(), "-0", 8, "Normal", false,
                listOf("First strike"), listOf(Print("consensus-a", "AAA", "Rare", 6, true, LegalityStatus.LEGAL),
                    Print("consensus-b", "bbb", "rare", 0, true, LegalityStatus.LEGAL, true))),
            Card("notlegal", view("Not legal", "123456789012345678901234567890.5", 16), emptyList(), "3", 16,
                "normal", false, emptyList(), listOf(Print("notlegal-a", "ccc", "uncommon", 1, false, LegalityStatus.NOT_LEGAL))),
            Card("mixed", view("Mixed", "9", 1), listOf(view("Mixed left", "*", null, null, null),
                view("Mixed right", "0", 0)), "9", 1, "modal_dfc", null, listOf("Flying"),
                listOf(Print("mixed-a", "aaa", "rare", 3, true, LegalityStatus.UNKNOWN))),
            Card("numeric", view("Numeric", "1.00000000000000000000000000000000000000000000000000000000000000001", 31),
                emptyList(), "100", 31, "normal", true, listOf("First", "strike"),
                listOf(Print("numeric-a", "aaa", "common", 4, null, LegalityStatus.RESTRICTED))),
            Card("unicode", view("Æther Élan 😀", "1e2", 2, "Draw a card. Flying", "artifact creature"),
                emptyList(), "1", 2, "normal", false, listOf("Flying"),
                listOf(Print("unicode-a", "ddd", "mythic", 7, false, LegalityStatus.RESTRICTED, language = "FR"))),
            Card("partial", view("Partial", "3", 4), emptyList(), "3", 4, " Ｎormal ", false, null,
                listOf(Print("partial-a", "aaa", "rare", 1, true, LegalityStatus.LEGAL),
                    Print("partial-b", " BbB ", "Ｒare", null, null, null))),
            Card("edge", view("Edge", "4", 0), emptyList(), "4", 0, "Nórmal\u0000suffix", false, emptyList(),
                listOf(Print("edge-a", "AaA\u0000suffix", "Rare\u0000suffix", 0, false, null)))
        )
    }

    private fun queries(): List<QueryAst> {
        fun text(value: String, field: SearchTextField = SearchTextField.NAME, exact: Boolean = false) = QueryAst.Text(field, value, exact)
        fun print(field: PrintingSearchField, value: String) = QueryAst.PrintingText(field, value)
        fun and(vararg values: QueryAst) = QueryAst.And(SnapshotList.of(*values))
        fun or(vararg values: QueryAst) = QueryAst.Or(SnapshotList.of(*values))
        val out = mutableListOf<QueryAst>()
        for (field in SearchNumericField.entries) for (op in Comparison.entries) for (value in listOf("0", "2.5"))
            out += QueryAst.Numeric(field, op, BigDecimal(value))
        for (op in Comparison.entries) for (identity in listOf(false, true)) for (colors in listOf(SnapshotList.empty(), SnapshotList.of(Color.WHITE, Color.BLUE)))
            out += QueryAst.Colors(colors, identity, op)
        for (field in SearchTextField.entries) for (exact in listOf(false, true))
            out += text(when (field) { SearchTextField.KEYWORD -> "first strike"; SearchTextField.LAYOUT -> "normal"; else -> "alpha" }, field, exact)
        out += listOf(text("Alpha // Beta", exact = true), text("a // b"), text("ÆTHER ÉLAN 😀", exact = true),
            text("100%_ \\ path", SearchTextField.ORACLE_TEXT), text("first", SearchTextField.KEYWORD),
            text("strike", SearchTextField.KEYWORD), text("draw", SearchTextField.ORACLE_TEXT),
            text("creature", SearchTextField.TYPE), text("draw a card. flying", SearchTextField.ORACLE_TEXT),
            QueryAst.Not(text("draw", SearchTextField.ORACLE_TEXT)),
            and(text("draw", SearchTextField.ORACLE_TEXT), text("sorcery", SearchTextField.TYPE)),
            and(QueryAst.Numeric(SearchNumericField.POWER, Comparison.LT, BigDecimal("3")),
                QueryAst.Numeric(SearchNumericField.TOUGHNESS, Comparison.GT, BigDecimal("4"))),
            QueryAst.Not(QueryAst.Numeric(SearchNumericField.POWER, Comparison.GT, BigDecimal.ONE)),
            and(text("Alpha // Beta", exact = true), text("sorcery", SearchTextField.TYPE)),
            and(print(PrintingSearchField.SET, "aaa"), print(PrintingSearchField.RARITY, "rare")),
            and(print(PrintingSearchField.SET, "aaa"), print(PrintingSearchField.FINISH, "nonfoil")),
            QueryAst.Not(print(PrintingSearchField.RARITY, "rare")),
            or(text("empty", exact = true), print(PrintingSearchField.SET, "aaa")),
            QueryAst.Not(or(text("empty", exact = true), print(PrintingSearchField.SET, "aaa"))),
            and(QueryAst.Not(print(PrintingSearchField.SET, "aaa")), print(PrintingSearchField.RARITY, "rare")),
            or(text("empty", exact = true), and(text("draw", SearchTextField.ORACLE_TEXT), print(PrintingSearchField.SET, "bbb"))))
        for (field in PrintingSearchField.entries) for (value in when (field) {
            PrintingSearchField.SET -> listOf("AAA", "bbb", "missing")
            PrintingSearchField.RARITY -> listOf("rare", "common", "uncommon", "mythic")
            PrintingSearchField.LANGUAGE -> listOf("EN", "fr")
            PrintingSearchField.FINISH -> listOf("foil", "nonfoil", "etched")
        }) out += print(field, value)
        for (flag in SearchFlag.entries) for (value in listOf(false, true)) {
            out += QueryAst.Flag(flag, value); out += QueryAst.Not(QueryAst.Flag(flag, value))
        }
        for (status in listOf(LegalityStatus.LEGAL, LegalityStatus.BANNED, LegalityStatus.RESTRICTED, LegalityStatus.NOT_LEGAL)) {
            val legal = QueryAst.LegalIn(FormatId(ProviderId("scryfall"), "modern"), status)
            out += legal; out += QueryAst.Not(legal)
        }
        out += QueryAst.Not(QueryAst.LegalIn(FormatId(ProviderId("foreign"), "modern"), LegalityStatus.LEGAL))
        out += QueryAst.Not(QueryAst.LegalIn(FormatId(ProviderId("scryfall"), "Modern"), LegalityStatus.LEGAL))
        out += and(QueryAst.Flag(SearchFlag.GAMECHANGER, true), print(PrintingSearchField.SET, "aaa"))
        out += listOf(text("NORMAL", SearchTextField.LAYOUT, true), text(" ＮORMAL ", SearchTextField.LAYOUT, true),
            text(" normal ", SearchTextField.LAYOUT, true), text("Nórmal", SearchTextField.LAYOUT, true),
            text("Nórmal", SearchTextField.LAYOUT), text("NÓRMAL", SearchTextField.LAYOUT),
            print(PrintingSearchField.SET, " bBb "), print(PrintingSearchField.SET, "ＡＡＡ"),
            print(PrintingSearchField.RARITY, "ＲARE"), print(PrintingSearchField.RARITY, "ＲÁRE"),
            and(print(PrintingSearchField.SET, " bbb "), print(PrintingSearchField.RARITY, "rare")),
            text("strike", SearchTextField.KEYWORD, true), text("first strike", SearchTextField.KEYWORD, true))
        return out
    }

    @Test fun compiledSqlMatchesIndependentTruthAndExactIdsForAllPoliciesAndMoreThanFiftyQueries() {
        val cards = cards(); val queries = queries()
        assertTrue(queries.size >= 50)
        BundledSQLiteDriver().open(":memory:").use { db ->
            actualSchema(db); cards.forEach { install(db, it) }
            // Same identity noise must never leak across generation/version/provider/language.
            val noise: Map<String, Any?> = mapOf("generation_id" to "fixture", "projection_version" to 1,
                "provider" to "scryfall", "oracle_id" to "empty", "language" to "en", "scope_kind" to "CANONICAL",
                "scope_index" to 0, "field" to "ORACLE_TEXT", "normalized_value" to "draw a card.")
            for (change in listOf("generation_id" to "other", "projection_version" to 2,
                "provider" to "foreign", "language" to "fr")) insert(db, "catalog_search_text_fact", noise + change)
            for (policy in FaceMatchPolicy.entries) for ((index, query) in queries.withIndex()) {
                val plan = (LocalCatalogQueryCompiler().compile(query, policy, COMPILER_NO_CANCEL) as Outcome.Success).value
                val sql = "SELECT c.oracle_id,(${plan.predicateSql}) AS truth FROM catalog_search_card_fact c " +
                    "WHERE c.generation_id='fixture' AND c.projection_version=1 AND c.provider='scryfall' AND c.language='en' ORDER BY c.oracle_id"
                val names = Regex(":(qp[0-9]+)").findAll(sql).map { it.groupValues[1] }.distinct().toList()
                assertEquals(plan.parameters.map { it.name }.toSet(), names.toSet())
                val actual = linkedMapOf<String, Truth>()
                db.prepare(sql).use { statement ->
                    names.forEachIndexed { position, name -> when (val value = plan.parameters.single { it.name == name }.value) {
                        is SqlValue.Text -> statement.bindText(position + 1, value.value)
                        is SqlValue.Integer -> statement.bindLong(position + 1, value.value)
                        else -> error("Unexpected compiler binding")
                    } }
                    while (statement.step()) actual[statement.getText(0)] = if (statement.isNull(1)) Truth.U else truth(statement.getLong(1) == 1L)
                }
                val expected = cards.sortedBy { it.id }.associate { it.id to reference(query, it, policy) }
                assertEquals("case=$index policy=$policy query=$query", expected, actual)
                assertEquals(expected.filterValues { it == Truth.T }.keys, actual.filterValues { it == Truth.T }.keys)
            }
        }
    }

    @Test fun explainReportsIndexedCorrelatedLookupsForTheActualCompiledPredicate() {
        val query = QueryAst.And(SnapshotList.of(QueryAst.Text(SearchTextField.NAME, "alpha", true),
            QueryAst.PrintingText(PrintingSearchField.SET, "aaa"), QueryAst.Flag(SearchFlag.GAMECHANGER, true)))
        val plan = (LocalCatalogQueryCompiler().compile(query, FaceMatchPolicy.ANY_FACE, COMPILER_NO_CANCEL) as Outcome.Success).value
        BundledSQLiteDriver().open(":memory:").use { db ->
            actualSchema(db); cards().forEach { install(db, it) }
            val sql = "EXPLAIN QUERY PLAN SELECT c.oracle_id FROM catalog_search_card_fact c WHERE " +
                "c.generation_id='fixture' AND c.projection_version=1 AND (${plan.predicateSql}) IS TRUE"
            val names = Regex(":(qp[0-9]+)").findAll(sql).map { it.groupValues[1] }.distinct().toList()
            val details = mutableListOf<String>()
            db.prepare(sql).use { statement ->
                names.forEachIndexed { index, name -> when (val value = plan.parameters.single { it.name == name }.value) {
                    is SqlValue.Text -> statement.bindText(index + 1, value.value)
                    is SqlValue.Integer -> statement.bindLong(index + 1, value.value)
                    else -> error("Unexpected compiler binding")
                } }
                while (statement.step()) details += statement.getText(3)
            }
            assertTrue(details.toString(), details.any { it.contains("SEARCH t USING") })
            assertTrue(details.toString(), details.any { it.contains("SEARCH pm USING") })
            assertTrue(details.toString(), details.any { it.contains("SEARCH fl USING") })
        }
    }

    /** Read the actual checked-in Room export without adding a JSON dependency to search. */
    private fun actualSchema(db: SQLiteConnection) {
        // Facts are isolated compiler fixtures, not an importer or source/FK integrity proof.
        db.prepare("PRAGMA foreign_keys=OFF").use { it.step() }
        val relative = "android-app/data/storage/schemas/com.architectpsg.commandlibrary.data.storage.schema.StorageSchemaDatabase/9.json"
        var directory = File(requireNotNull(System.getProperty("user.dir")))
        while (!File(directory, relative).isFile) directory = directory.parentFile ?: error("Actual schema9 absent")
        val json = File(directory, relative).readText()
        val entries = Regex("\"(tableName|createSql)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        var table = ""; var tables = 0
        for (match in entries.findAll(json)) {
            val value = decodeJsonString(match.groupValues[2])
            if (match.groupValues[1] == "tableName") { table = value; tables++ }
            else db.prepare(value.replace("\${TABLE_NAME}", table)).use { it.step() }
        }
        assertEquals(35, tables)
    }

    private fun decodeJsonString(encoded: String): String = buildString {
        var at = 0
        while (at < encoded.length) {
            val c = encoded[at++]
            if (c != '\\') append(c) else when (val escaped = encoded[at++]) {
                '"', '\\', '/' -> append(escaped)
                'n' -> append('\n'); 'r' -> append('\r'); 't' -> append('\t'); 'b' -> append('\b'); 'f' -> append('\u000C')
                'u' -> { append(encoded.substring(at, at + 4).toInt(16).toChar()); at += 4 }
                else -> error("Invalid export escape")
            }
        }
    }

    private fun insert(db: SQLiteConnection, table: String, values: Map<String, Any?>) {
        val row = linkedMapOf<String, Any?>()
        db.prepare("PRAGMA table_info(`$table`)").use { info ->
            while (info.step()) {
                val name = info.getText(1)
                row[name] = if (name in values) values[name] else if (info.getLong(3) == 0L) null
                    else if (info.getText(2) == "INTEGER") 0 else ""
            }
        }
        db.prepare("INSERT INTO `$table` (${row.keys.joinToString(",") { "`$it`" }}) VALUES (${row.keys.joinToString(",") { "?" }})").use { statement ->
            row.values.forEachIndexed { index, value -> when (value) {
                null -> statement.bindNull(index + 1)
                is String -> statement.bindText(index + 1, value)
                is Boolean -> statement.bindLong(index + 1, if (value) 1 else 0)
                is Int -> statement.bindLong(index + 1, value.toLong())
                else -> error("Unsupported fixture value")
            } }
            statement.step()
        }
    }

    private fun install(db: SQLiteConnection, card: Card) {
        val key: Map<String, Any?> = mapOf("generation_id" to "fixture", "projection_version" to 1,
            "provider" to "scryfall", "oracle_id" to card.id, "language" to "en")
        fun stats(view: View): Map<String, Any?> = listOf("power", "toughness", "loyalty", "defense").zip(view.stats)
            .associate { (name, value) -> "${name}_key" to value?.let(DecimalComparisonKeys::sourceKey) }
        insert(db, "catalog_search_card_fact", key + stats(card.canonical) + mapOf("mana_value_key" to card.mana?.let(DecimalComparisonKeys::sourceKey),
            "colors_mask" to card.canonical.colors, "identity_mask" to card.identity, "layout" to card.layout,
            "reserved" to card.reserved, "keywords_known" to (card.keywords != null), "face_count" to card.faces.size,
            "oracle_text_known" to (card.canonical.text != null), "type_known" to (card.canonical.type != null)))
        fun text(scope: String, index: Int, field: String, value: String) = insert(db, "catalog_search_text_fact",
            key + mapOf("scope_kind" to scope, "scope_index" to index, "field" to field, "normalized_value" to normalize(value)))
        fun texts(scope: String, index: Int, view: View) {
            text(scope, index, "NAME", view.name)
            view.text?.let { text(scope, index, "ORACLE_TEXT", it) }
            view.type?.let { text(scope, index, "TYPE", it) }
        }
        texts("CANONICAL", 0, card.canonical)
        card.keywords?.forEachIndexed { index, value -> text("KEYWORD", index, "KEYWORD", value) }
        card.faces.forEachIndexed { index, face ->
            insert(db, "catalog_search_face_fact", key + stats(face) + mapOf("face_index" to index, "colors_mask" to face.colors,
                "oracle_text_known" to (face.text != null), "type_known" to (face.type != null)))
            texts("FACE", index, face)
        }
        for (print in card.prints) {
            val printKey = mapOf("generation_id" to "fixture", "provider" to "scryfall", "printing_id" to print.id)
            insert(db, "printing", printKey + mapOf("oracle_provider" to if (print.memberOnly) null else "scryfall",
                "oracle_id" to if (print.memberOnly) null else card.id, "oracle_language" to if (print.memberOnly) null else "en"))
            // Duplicate source slots and a duplicate direct relation must not duplicate consensus evidence.
            for (slot in 0..1) insert(db, "printing_oracle_membership", mapOf("generation_id" to "fixture",
                "printing_provider" to "scryfall", "printing_id" to print.id, "source_slot" to slot,
                "source_kind" to "FACE", "oracle_provider" to "scryfall", "oracle_id" to card.id, "oracle_language" to "en"))
            insert(db, "catalog_search_printing_fact", printKey + mapOf("projection_version" to 1,
                "set_code" to print.set, "language" to print.language, "rarity" to print.rarity,
                "finish_mask" to print.finish, "gamechanger" to print.changer, "legalities_known" to (print.legal != null)))
            print.legal?.let { insert(db, "catalog_search_legality_fact", printKey + mapOf("projection_version" to 1,
                "format" to "modern", "status" to it.name)) }
        }
    }
}
