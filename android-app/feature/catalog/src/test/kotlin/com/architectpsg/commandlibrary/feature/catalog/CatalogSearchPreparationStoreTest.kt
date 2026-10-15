package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.JsonParser
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration

class CatalogSearchPreparationStoreTest {
    private val clock = object : Clock {
        override fun utcNow() = UtcTimestamp(3)
        override fun monotonicNow() = Duration.ZERO
    }
    private fun SearchProjectionSqlDatabase.store() = CatalogSearchPreparationStore(this, clock)
    private fun SearchProjectionSqlDatabase.count(table: String) = rows("SELECT count(*) AS n FROM $table").single().bulkLong("n")

    @Test fun actualSchema9FactsAndMirrorsCommitWithCheckpointWithoutReadyOrSourceMutation() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val generation = db.install(listOf(bulkRecord("p", "o")))
            val originals = db.rows("SELECT * FROM oracle_card")
            val card = db.store().advance(generation, BULK_NO_CANCEL).bulkValue()
            assertEquals(SearchPreparationPhase.PRINTING, card.phase)
            assertEquals(1L, card.cards)
            assertEquals(3L, card.texts) // name and two actual keywords
            assertEquals(originals, db.rows("SELECT * FROM oracle_card"))
            assertEquals(card.texts, db.count("catalog_search_text_fts"))
            assertEquals(0L, db.rows("SELECT count(*) AS n FROM catalog_search_text_fact t LEFT JOIN catalog_search_text_fts f ON f.rowid=t.rowid WHERE f.rowid IS NULL OR t.normalized_value IS NOT f.value OR t.oracle_id IS NOT f.oracle_id").single().bulkLong("n"))
            val printing = db.store().advance(generation, BULK_NO_CANCEL).bulkValue()
            assertEquals(SearchPreparationPhase.VERIFY, printing.phase)
            assertEquals(1L, printing.printings)
            assertEquals(printing, db.store().advance(generation, BULK_NO_CANCEL).bulkValue())
            assertEquals("PREPARING", db.rows("SELECT status FROM catalog_search_build").single().bulkText("status"))
            assertEquals(0L, db.rows("SELECT count(*) AS n FROM pragma_foreign_key_check").single().bulkLong("n"))
        }
    }

    @Test fun writeFailureRollsBackBuildFactsAndFtsThenFreshAttemptResumes() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val generation = db.install(listOf(bulkRecord("p", "o")))
            db.beforeWrite = { if (it.statement.startsWith("INSERT INTO catalog_search_text_fts")) error("Injected mirror write failure") }
            assertTrue(db.store().advance(generation, BULK_NO_CANCEL) is Outcome.Failure)
            for (table in listOf("catalog_search_build", "catalog_search_card_fact", "catalog_search_text_fact", "catalog_search_text_fts")) assertEquals(0L, db.count(table))
            db.beforeWrite = null
            assertEquals(1L, db.store().advance(generation, BULK_NO_CANCEL).bulkValue().cards)
            assertEquals(1L, db.count("oracle_card"))
        }
    }

    @Test fun lostCommitAcknowledgementReturnsExactCommittedCheckpointWithoutReplay() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val generation = db.install(listOf(bulkRecord("p", "o")))
            db.afterCommit = { error("Lost ACK") }
            assertEquals(1L, db.store().advance(generation, BULK_NO_CANCEL).bulkValue().cards)
            db.afterCommit = null
            assertEquals(1L, db.count("catalog_search_card_fact"))
            assertEquals(1L, db.store().advance(generation, BULK_NO_CANCEL).bulkValue().printings)
        }
    }

    @Test fun cancellationBeforeCommitRollsBackAndEscapesButAfterCommitIsAcknowledged() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val generation = db.install(listOf(bulkRecord("p", "o")))
            db.beforeWrite = { if (it.statement.startsWith("UPDATE catalog_search_build")) throw CancellationException("Before commit") }
            try { db.store().advance(generation, BULK_NO_CANCEL); fail("Cancellation must escape") } catch (_: CancellationException) { }
            assertEquals(0L, db.count("catalog_search_build"))
            db.beforeWrite = null
            db.afterCommit = { throw CancellationException("After commit") }
            assertEquals(1L, db.store().advance(generation, BULK_NO_CANCEL).bulkValue().cards)
            assertEquals(1L, db.count("catalog_search_card_fact"))
        }
    }

    @Test fun inconclusiveCommitReadbackIsUnknownAndFreshCallUsesActualCommittedPrefix() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val generation = db.install(listOf(bulkRecord("p", "o")))
            db.afterCommit = {
                db.beforeQuery = { if (it.statement.contains("FROM catalog_search_build")) error("Unavailable ACK") }
                error("Lost ACK")
            }
            assertTrue(db.store().advance(generation, BULK_NO_CANCEL) is Outcome.Unknown)
            db.afterCommit = null; db.beforeQuery = null
            val next = db.store().advance(generation, BULK_NO_CANCEL).bulkValue()
            assertEquals(SearchPreparationPhase.VERIFY, next.phase)
            assertEquals(1L, db.count("catalog_search_card_fact"))
        }
    }

    @Test fun originalIdentityAndDigestCorruptionNeverWritesFacts() = runBlocking {
        for (mutation in listOf("UPDATE oracle_card SET record_payload=replace(record_payload,'\"oracle_id\":\"o\"','\"oracle_id\":\"other\"')", "UPDATE oracle_card SET canonical_digest='bad'")) {
            SearchProjectionSqlDatabase().use { db ->
                val generation = db.install(listOf(bulkRecord("p", "o")))
                db.exec(mutation)
                assertTrue(db.store().advance(generation, BULK_NO_CANCEL) is Outcome.Failure)
                assertEquals(0L, db.count("catalog_search_build"))
                assertEquals(0L, db.count("catalog_search_card_fact"))
            }
        }
    }

    @Test fun optionalOversizeMetadataIsNotSilentlyTreatedAsAbsent() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val generation = db.install(listOf(bulkRecord("p", "o")))
            db.exec("UPDATE catalog_generation SET source_version=printf('%01048577d',0)")
            assertTrue(db.store().advance(generation, BULK_NO_CANCEL) is Outcome.Failure)
            assertEquals(0L, db.count("catalog_search_build"))
        }
    }

    @Test fun checkpointFenceRejectsChangedCursorAndRollsBackNewFacts() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val generation = db.install((1..17).map { bulkRecord("p$it", "o%02d".format(it)) })
            val prefix = db.store().advance(generation, BULK_NO_CANCEL).bulkValue()
            assertEquals(16L, prefix.cards)
            db.beforeWrite = { if (it.statement.startsWith("UPDATE catalog_search_build")) db.exec("UPDATE catalog_search_build SET card_count=999") }
            assertTrue(db.store().advance(generation, BULK_NO_CANCEL) is Outcome.Failure)
            db.beforeWrite = null
            assertEquals(16L, db.count("catalog_search_card_fact"))
            assertEquals(16L, db.rows("SELECT card_count FROM catalog_search_build").single().bulkLong("card_count"))
            assertEquals(17L, db.store().advance(generation, BULK_NO_CANCEL).bulkValue().cards)
        }
    }

    @Test fun committedPrefixSurvivesFileReopenAndRetiredSourceRemainsPinned() = runBlocking {
        val file = File.createTempFile("search-projection-", ".sqlite")
        try {
            val generation = SearchProjectionSqlDatabase(file.path).use { db ->
                db.install((1..17).map { bulkRecord("p$it", "o%02d".format(it)) }).also {
                    assertEquals(16L, db.store().advance(it, BULK_NO_CANCEL).bulkValue().cards)
                    db.exec("UPDATE catalog_generation SET status='RETIRED'")
                }
            }
            SearchProjectionSqlDatabase(file.path).use { db ->
                assertEquals(17L, db.store().advance(generation, BULK_NO_CANCEL).bulkValue().cards)
                assertEquals(17L, db.count("catalog_search_card_fact"))
                assertEquals("RETIRED", db.rows("SELECT status FROM catalog_generation").single().bulkText("status"))
                assertEquals(generation.value, db.rows("SELECT generation_id FROM catalog_active").single().bulkText("generation_id"))
            }
        } finally { file.delete() }
    }

    @Test fun invalidSourceOrMalformedCheckpointFailsWithoutSubstitution() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val generation = db.install(listOf(bulkRecord("p", "o")))
            assertTrue(db.store().advance(GenerationId("missing"), BULK_NO_CANCEL) is Outcome.Unavailable)
            db.store().advance(generation, BULK_NO_CANCEL).bulkValue()
            db.exec("UPDATE catalog_search_build SET cursor_id='partial',cursor_provider=NULL")
            assertTrue(db.store().advance(generation, BULK_NO_CANCEL) is Outcome.Failure)
            assertEquals(0L, db.count("catalog_search_printing_fact"))
        }
    }

    @Test fun storedFaceAndPrintingScalarCorruptionCannotBeHiddenByValidRawPayload() = runBlocking {
        val faceRecord = JsonParser.parseString(bulkRecord("p", "o")).asJsonObject.apply {
            add("card_faces", JsonParser.parseString("""[{"object":"card_face","name":"Front","id":"face-id","oracle_text":"Draw."}]"""))
        }.toString()
        for (mutation in listOf("UPDATE card_face SET name='Wrong'", "UPDATE card_face SET provider_face_id='Wrong'", "UPDATE card_face SET face_payload='{}'", "UPDATE card_face SET face_index=7")) {
            SearchProjectionSqlDatabase().use { db ->
                val generation = db.install(listOf(faceRecord))
                db.exec(mutation)
                assertTrue(mutation, db.store().advance(generation, BULK_NO_CANCEL) is Outcome.Failure)
                assertEquals(0L, db.count("catalog_search_card_fact"))
            }
        }
        for (mutation in listOf("UPDATE printing SET rarity='mythic'", "UPDATE printing SET finishes='FOIL'", "UPDATE printing SET collector_number='wrong'", "UPDATE printing SET oracle_id=NULL,oracle_provider=NULL,oracle_language=NULL")) {
            SearchProjectionSqlDatabase().use { db ->
                val generation = db.install(listOf(bulkRecord("p", "o")))
                db.store().advance(generation, BULK_NO_CANCEL).bulkValue()
                db.exec(mutation)
                assertTrue(mutation, db.store().advance(generation, BULK_NO_CANCEL) is Outcome.Failure)
                assertEquals(0L, db.count("catalog_search_printing_fact"))
                assertEquals("PRINTING", db.rows("SELECT phase FROM catalog_search_build").single().bulkText("phase"))
            }
        }
    }

    @Test fun fullDerivedBudgetCommitsOnlyWholeRecordsAndNeverLoopsAtSameCursor() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val keywords = List(510) { "\"K\"" }.joinToString(",", "[", "]")
            val generation = db.install((1..3).map { bulkRecord("p$it", "o$it", keywords) })
            var operations = 0
            db.beforeQuery = { operations++ }
            db.beforeWrite = { operations++ }
            val first = db.store().advance(generation, BULK_NO_CANCEL).bulkValue()
            assertEquals(1L, first.cards)
            assertEquals("o1", first.id)
            assertTrue("Actual SQL operation limit: $operations", operations <= 4096)
            val second = db.store().advance(generation, BULK_NO_CANCEL).bulkValue()
            assertEquals(2L, second.cards)
            assertEquals("o2", second.id)
            assertEquals(2L, db.count("catalog_search_card_fact"))
        }
    }

    @Test fun rawByteBudgetDoesNotFetchPayloadThatCannotFitRemainingBatch() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val records = (1..6).map { index -> JsonParser.parseString(bulkRecord("p$index", "o$index")).asJsonObject.apply {
                addProperty("uninterpreted_padding", "x".repeat(3 * 1024 * 1024))
            }.toString() }
            val generation = db.install(records)
            val first = db.store().advance(generation, BULK_NO_CANCEL).bulkValue()
            assertEquals(5L, first.cards)
            assertEquals("o5", first.id)
            val next = db.store().advance(generation, BULK_NO_CANCEL).bulkValue()
            assertEquals(6L, next.cards)
            assertEquals(SearchPreparationPhase.PRINTING, next.phase)
        }
    }

    @Test fun duplicateFactFailsAtomicallyInsteadOfReplacingUnexpectedStoredEvidence() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val generation = db.install(listOf(bulkRecord("p", "o")))
            db.store().advance(generation, BULK_NO_CANCEL).bulkValue()
            val texts = db.rows("SELECT * FROM catalog_search_text_fact")
            db.exec("UPDATE catalog_search_build SET phase='CARD',cursor_provider=NULL,cursor_id=NULL,cursor_language=NULL,card_count=0,text_count=0")
            assertTrue(db.store().advance(generation, BULK_NO_CANCEL) is Outcome.Failure)
            assertEquals(texts, db.rows("SELECT * FROM catalog_search_text_fact"))
            assertEquals(1L, db.count("catalog_search_card_fact"))
        }
    }

    @Test fun validLargeSourceScalarsAndUnicodeDisplayNameAreNotRejectedByNewGuards() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val input = JsonParser.parseString(bulkRecord("p", "o")).asJsonObject.apply {
                addProperty("set", "s".repeat(500))
                addProperty("rarity", "r".repeat(500))
                addProperty("set_name", "Set".repeat(4000))
                addProperty("collector_number", "c".repeat(10000))
                add("card_faces", JsonParser.parseString("""[{"object":"card_face","name":"${"F".repeat(10000)}","id":"${"f".repeat(500)}"}]"""))
            }.toString()
            val generation = db.install(listOf(input))
            // Installed-reader model permits512 UTF16 characters. Current import adapters
            // separately cap512 UTF8 bytes; this is a retained legacy-row compatibility probe.
            db.exec("UPDATE catalog_generation SET source_payload=NULL,display_name='${"é".repeat(300)}'")
            assertEquals(1L, db.store().advance(generation, BULK_NO_CANCEL).bulkValue().faces)
            assertEquals(1L, db.store().advance(generation, BULK_NO_CANCEL).bulkValue().printings)
        }
    }
}
