package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration

/** Actual bundled SQLite/FTS and production importer/reader; records are explicitly invented. */
class CatalogTextLookupTest {
    private fun lookup(db: Database) = LocalCatalog(db, object : Clock {
        override fun utcNow() = UtcTimestamp(100)
        override fun monotonicNow() = Duration.ZERO
    }, object : IdSource { override fun nextId() = error("Read must not create identities") })
    private fun record(index: Int, name: String = "Synthetic %03d".format(index), text: String = "Draw a synthetic card."): String =
        JsonParser.parseString(bulkRecord("p$index", "o$index")).asJsonObject.apply {
            addProperty("name", name); addProperty("oracle_text", text)
        }.toString()

    @Test fun actualIndexedTextIsIndependentOfNameAndPreservesUnicodeAndLiteralPhraseOrder() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            db.install(listOf(record(1, "Élan — Synthetic", "Draw an Æther token."), record(2, "Draw an Æther token", "Unrelated rules.")))
            val reader = lookup(db)
            assertEquals(listOf("o1"), reader.lookupText(CatalogTextField.ORACLE_TEXT, "draw an æth", PageRequest(10), BULK_NO_CANCEL).bulkValue().items.map { it.oracleId.raw })
            assertEquals(listOf("o2"), reader.lookupName("draw an æth", PageRequest(10), BULK_NO_CANCEL).bulkValue().items.map { it.oracleId.raw })
            assertEquals(listOf("o1"), reader.lookupName("elan synthetic", PageRequest(10), BULK_NO_CANCEL).bulkValue().items.map { it.oracleId.raw })
            assertTrue(reader.lookupText(CatalogTextField.ORACLE_TEXT, "token draw", PageRequest(10), BULK_NO_CANCEL).bulkValue().items.isEmpty())
            assertTrue(reader.lookupText(CatalogTextField.ORACLE_TEXT, "\" OR NOT * : ()", PageRequest(10), BULK_NO_CANCEL).bulkValue().items.isEmpty())
            val plan = db.rows("EXPLAIN QUERY PLAN SELECT rowid FROM catalog_name_fts WHERE catalog_name_fts MATCH 'text : \"draw\"*'")
            assertTrue(plan.any { it.bulkText("detail").contains("VIRTUAL TABLE INDEX", true) })
        }
    }
    @Test fun textPaginationBindsQueryFieldAndGenerationEvenForEmptyTokenQueries() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            db.install((1..7).map { record(it, "Draw Synthetic %03d".format(it)) })
            val reader = lookup(db)
            val first = reader.lookupText(CatalogTextField.ORACLE_TEXT, "draw", PageRequest(2), BULK_NO_CANCEL).bulkValue()
            val ids = first.items.map { it.oracleId.raw }.toMutableList()
            var cursor = first.nextCursor
            while (cursor != null) {
                val page = reader.lookupText(CatalogTextField.ORACLE_TEXT, "draw", PageRequest(2, cursor), BULK_NO_CANCEL).bulkValue()
                assertEquals(first.snapshot, page.snapshot); ids += page.items.map { it.oracleId.raw }; cursor = page.nextCursor
            }
            assertEquals((1..7).map { "o$it" }, ids); assertEquals(ids.size, ids.toSet().size)
            for ((field, text) in listOf(CatalogTextField.NAME to "draw", CatalogTextField.ORACLE_TEXT to "synthetic", CatalogTextField.ORACLE_TEXT to "!!!")) {
                val failed = reader.lookupText(field, text, PageRequest(2, first.nextCursor), BULK_NO_CANCEL) as Outcome.Failure
                assertEquals(FailureCode.INVALID_INPUT, failed.error.code)
            }
            db.install(listOf(record(8)))
            assertEquals(FailureCode.STALE_GENERATION, (reader.lookupText(CatalogTextField.ORACLE_TEXT, "draw", PageRequest(2, first.nextCursor), BULK_NO_CANCEL) as Outcome.Failure).error.code)
        }
    }
    @Test fun absentCorpusAndPunctuationAreEmptyButMalformedCursorIsNeverIgnored() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            val reader = lookup(db)
            val empty = reader.lookupText(CatalogTextField.ORACLE_TEXT, "draw", PageRequest(10), BULK_NO_CANCEL).bulkValue()
            assertTrue(empty.items.isEmpty()); assertNull(empty.snapshot.generation)
            assertEquals(FailureCode.INVALID_INPUT, (reader.lookupText(CatalogTextField.ORACLE_TEXT, "!!!", PageRequest(10, "invalid"), BULK_NO_CANCEL) as Outcome.Failure).error.code)
        }
    }
    @Test fun recordedFaceAggregateParticipatesWithoutInventingCardLevelText() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            val raw = JsonParser.parseString(bulkRecord("face-p", "face-o")).asJsonObject.apply {
                addProperty("layout", "transform")
                add("card_faces", JsonParser.parseString("""[{"object":"card_face","name":"Synthetic front","oracle_text":"Create a synthetic token."},{"object":"card_face","name":"Synthetic back","oracle_text":"Draw a synthetic card."}]"""))
            }.toString()
            db.install(listOf(raw))
            val page = lookup(db).lookupText(CatalogTextField.ORACLE_TEXT, "draw a synthetic", PageRequest(10), BULK_NO_CANCEL).bulkValue()
            assertEquals("face-o", page.items.single().oracleId.raw)
            assertFalse(page.items.single().originalText is Knowledge.Known)
            assertEquals(2, page.items.single().faces.size)
        }
    }
}
