package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.JsonParser
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration

class CatalogSearchReadinessTest {
    private val clock = object : Clock {
        override fun utcNow() = UtcTimestamp(3)
        override fun monotonicNow() = Duration.ZERO
    }
    private fun SearchProjectionSqlDatabase.store() = CatalogSearchPreparationStore(this, clock)
    private suspend fun SearchProjectionSqlDatabase.materialize(g: GenerationId) {
        repeat(100) { if (store().advance(g, BULK_NO_CANCEL).bulkValue().phase == SearchPreparationPhase.VERIFY) return }
        error("Materialization did not finish within fixture limit")
    }
    private suspend fun SearchProjectionSqlDatabase.toPhase(g: GenerationId, phase: SearchPreparationPhase): SearchPreparationCheckpoint {
        repeat(200) {
            val state = store().status(g, BULK_NO_CANCEL).bulkValue()
            if (state?.phase == phase) return state
            check(state?.status != SearchPreparationStatus.FAILED)
            store().advance(g, BULK_NO_CANCEL).bulkValue()
        }
        error("Verification phase did not arrive")
    }
    private suspend fun SearchProjectionSqlDatabase.finish(g: GenerationId): SearchPreparationCheckpoint {
        repeat(200) {
            val state = store().advance(g, BULK_NO_CANCEL).bulkValue()
            if (state.status != SearchPreparationStatus.PREPARING) return state
        }
        error("Preparation did not finish within fixture limit")
    }
    private fun record(): String = JsonParser.parseString(bulkRecord("p", "o")).asJsonObject.apply {
        add("legalities", JsonParser.parseString("""{"commander":"legal","future":"unrecognized"}"""))
        add("card_faces", JsonParser.parseString("""[{"object":"card_face","name":"Front","power":"1"},{"object":"card_face","name":"Back","oracle_text":"Draw."}]"""))
    }.toString()

    @Test fun absentNeverMeansReadyAndCompleteVerificationPreservesSourceAndStopsWriting() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val g = db.install(listOf(record()))
            assertNull(db.store().status(g, BULK_NO_CANCEL).bulkValue())
            val original = db.rows("SELECT * FROM oracle_card")
            db.materialize(g)
            assertEquals(SearchPreparationStatus.PREPARING, db.store().status(g, BULK_NO_CANCEL).bulkValue()!!.status)
            val ready = db.finish(g)
            assertEquals(SearchPreparationStatus.READY, ready.status)
            assertEquals(2L, ready.faces)
            assertEquals(2L, ready.legalities)
            assertEquals(original, db.rows("SELECT * FROM oracle_card"))
            db.beforeWrite = { error("Terminal READY must not write") }
            assertEquals(ready, db.store().advance(g, BULK_NO_CANCEL).bulkValue())
        }
    }

    @Test fun missingChangedExtraAndMiscountedFactsCannotBecomeReady() = runBlocking {
        val mutations = listOf(
            "DELETE FROM catalog_search_card_fact",
            "UPDATE catalog_search_face_fact SET power_key='bad' WHERE face_index=0",
            "UPDATE catalog_search_printing_fact SET rarity='invented'",
            "DELETE FROM catalog_search_legality_fact WHERE format='commander'",
            "UPDATE catalog_search_text_fact SET normalized_value='wrong' WHERE field='NAME'",
            "UPDATE catalog_search_build SET text_count=text_count+1",
            "INSERT INTO catalog_search_text_fact SELECT generation_id,projection_version,provider,oracle_id,language,'KEYWORD',999,field,normalized_value FROM catalog_search_text_fact WHERE field='NAME' LIMIT 1",
            "INSERT INTO catalog_search_legality_fact SELECT generation_id,projection_version,provider,printing_id,'extra','LEGAL' FROM catalog_search_legality_fact LIMIT 1"
        )
        for (mutation in mutations) SearchProjectionSqlDatabase().use { db ->
            val g = db.install(listOf(record()))
            db.materialize(g); db.exec(mutation)
            val failed = db.finish(g)
            assertEquals(mutation, SearchPreparationStatus.FAILED, failed.status)
            assertEquals("INVALID_SOURCE_OR_PROJECTION", failed.diagnostic)
            assertEquals(failed, db.store().advance(g, BULK_NO_CANCEL).bulkValue())
            assertEquals(1, db.rows("SELECT oracle_id FROM oracle_card").size)
        }
    }

    @Test fun ftsMissingChangedOrDuplicatedIdentityFailsIndexedBidirectionalProof() = runBlocking {
        val fields = "value,generation_id,projection_version,provider,oracle_id,language,scope_kind,scope_index,field"
        for (mutation in listOf(
            "DELETE FROM catalog_search_text_fts WHERE rowid=(SELECT min(rowid) FROM catalog_search_text_fts)",
            "UPDATE catalog_search_text_fts SET value='wrong' WHERE field='NAME'",
            "UPDATE catalog_search_text_fts SET scope_index=999 WHERE field='NAME'",
            "INSERT INTO catalog_search_text_fts(rowid,$fields) SELECT 999999,$fields FROM catalog_search_text_fts LIMIT 1"
        )) SearchProjectionSqlDatabase().use { db ->
            val g = db.install(listOf(record()))
            db.materialize(g); db.exec(mutation)
            assertEquals(mutation, SearchPreparationStatus.FAILED, db.finish(g).status)
        }
    }

    @Test fun nonpositiveTargetRowidsAreNotHiddenOnFirstPageOrExpectedFactReads() = runBlocking {
        for (table in listOf("oracle_card", "card_face", "printing", "catalog_search_card_fact", "catalog_search_face_fact", "catalog_search_printing_fact", "catalog_search_legality_fact", "catalog_search_text_fact", "catalog_search_text_fts")) {
            SearchProjectionSqlDatabase().use { db ->
                val g = db.install(listOf(record()))
                db.materialize(g); db.exec("UPDATE $table SET rowid=-rowid")
                assertEquals(table, SearchPreparationStatus.FAILED, db.finish(g).status)
            }
        }
    }

    @Test fun deletedUnrelatedGlobalWatermarkAndNegativeUnrelatedRowsDoNotPoisonTarget() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val unrelated = db.install((1..65).map { bulkRecord("unrelated-p$it", "unrelated-o$it") })
            db.exec("UPDATE oracle_card SET rowid=-1 WHERE rowid=1")
            val target = db.install(listOf(bulkRecord("target-p", "target-o")))
            db.materialize(target)
            db.toPhase(target, SearchPreparationPhase.VERIFY_CARD)
            val page = db.store().advance(target, BULK_NO_CANCEL).bulkValue()
            assertEquals(SearchPreparationPhase.VERIFY_CARD, page.phase)
            assertEquals("0,0,0,0,0", page.provider)
            val watermark = requireNotNull(page.id).toLong()
            assertEquals(64L, watermark)
            db.exec("UPDATE catalog_generation SET status='FAILED' WHERE generation_id='${unrelated.value}'")
            assertTrue(CatalogGenerationCleanup(db).recover(BULK_NO_CANCEL, 1000).bulkValue())
            assertTrue(db.rows("SELECT rowid FROM oracle_card WHERE rowid=$watermark").isEmpty())
            assertEquals(SearchPreparationStatus.READY, db.finish(target).status)
        }
    }

    @Test fun verificationPositionAndExpectedCountersSurviveActualFileReopen() = runBlocking {
        val file = File.createTempFile("search-verify-", ".sqlite")
        try {
            val g = SearchProjectionSqlDatabase(file.path).use { db ->
                db.install((1..17).map { bulkRecord("p$it", "o$it") }).also {
                    db.materialize(it); db.toPhase(it, SearchPreparationPhase.VERIFY_CARD)
                    val prefix = db.store().advance(it, BULK_NO_CANCEL).bulkValue()
                    assertEquals("16,0,0,48,0", prefix.provider)
                    assertNotNull(prefix.id)
                }
            }
            SearchProjectionSqlDatabase(file.path).use { db -> assertEquals(SearchPreparationStatus.READY, db.finish(g).status) }
        } finally { file.delete() }
    }

    @Test fun readyCommitLostAcknowledgementAndPostcommitCancellationReturnKnownReady() = runBlocking {
        for (cancelled in listOf(false, true)) SearchProjectionSqlDatabase().use { db ->
            val g = db.install(listOf(record()))
            db.toPhase(g, SearchPreparationPhase.VERIFY_FTS)
            db.afterCommit = { if (cancelled) throw CancellationException("Lost final ACK") else error("Lost final ACK") }
            assertEquals(SearchPreparationStatus.READY, db.store().advance(g, BULK_NO_CANCEL).bulkValue().status)
            db.afterCommit = null
            assertEquals(SearchPreparationStatus.READY, db.store().status(g, BULK_NO_CANCEL).bulkValue()!!.status)
        }
    }

    @Test fun cancellationBeforeReadyCasPreservesResumablePriorAndNoFalseFailure() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val g = db.install(listOf(record()))
            val prior = db.toPhase(g, SearchPreparationPhase.VERIFY_FTS)
            db.beforeWrite = { request -> if (request.parameters.any { it.name == "n_status" && it.value == SqlValue.Text("READY") }) throw CancellationException("Before final CAS") }
            try { db.store().advance(g, BULK_NO_CANCEL); fail("Cancellation must escape") } catch (_: CancellationException) { }
            assertEquals(prior, db.store().status(g, BULK_NO_CANCEL).bulkValue())
            db.beforeWrite = null
            assertEquals(SearchPreparationStatus.READY, db.store().advance(g, BULK_NO_CANCEL).bulkValue().status)
        }
    }

    @Test fun failedStateLostAcknowledgementIsReconciledAndActiveOriginalIsNotReclaimed() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val g = db.install(listOf(record()))
            db.materialize(g)
            db.exec("UPDATE catalog_search_card_fact SET layout='wrong'")
            db.toPhase(g, SearchPreparationPhase.VERIFY_CARD)
            db.afterCommit = { error("Lost failed-state ACK") }
            assertEquals(SearchPreparationStatus.FAILED, db.store().advance(g, BULK_NO_CANCEL).bulkValue().status)
            db.afterCommit = null
            assertTrue(CatalogGenerationCleanup(db).recover(BULK_NO_CANCEL).bulkValue())
            assertEquals(1, db.rows("SELECT * FROM oracle_card").size)
            assertTrue(db.rows("SELECT rowid FROM catalog_search_text_fts").isNotEmpty())
        }
    }

    @Test fun verificationBudgetIncludesFtsMirrorsBeforeAdmittingAnotherSourceRecord() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val records = (1..3).map { index -> JsonParser.parseString(bulkRecord("p$index", "o$index")).asJsonObject.apply {
                addProperty("oracle_text", "x".repeat(3 * 1024 * 1024))
            }.toString() }
            val g = db.install(records)
            db.materialize(g); db.toPhase(g, SearchPreparationPhase.VERIFY_CARD)
            val prefix = db.store().advance(g, BULK_NO_CANCEL).bulkValue()
            assertEquals(SearchPreparationPhase.VERIFY_CARD, prefix.phase)
            assertEquals("2,0,0,8,0", prefix.provider)
            assertEquals(SearchPreparationStatus.READY, db.finish(g).status)
        }
    }

    @Test fun unknownReadyAcknowledgementIsNotReplayedAndFreshStatusReadsActualCommit() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val g = db.install(listOf(record()))
            db.toPhase(g, SearchPreparationPhase.VERIFY_FTS)
            db.afterCommit = { db.beforeQuery = { if (it.statement.contains("FROM catalog_search_build")) error("Readback unavailable") }; error("Lost ACK") }
            assertTrue(db.store().advance(g, BULK_NO_CANCEL) is Outcome.Unknown)
            db.afterCommit = null; db.beforeQuery = null
            assertEquals(SearchPreparationStatus.READY, db.store().status(g, BULK_NO_CANCEL).bulkValue()!!.status)
        }
    }

    @Test fun globalWatermarkAndMirrorJoinsUseRowidAccessInsteadOfGenerationFilteredScans() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val g = db.install(listOf(record()))
            db.materialize(g)
            val sourcePlan = db.rows("EXPLAIN QUERY PLAN SELECT rowid FROM oracle_card WHERE rowid>1 ORDER BY rowid LIMIT 1").joinToString { it.bulkText("detail") }
            val ftsPlan = db.rows("EXPLAIN QUERY PLAN SELECT rowid FROM catalog_search_text_fts WHERE rowid>1 ORDER BY rowid LIMIT 1").joinToString { it.bulkText("detail") }
            val mirrorPlan = db.rows("EXPLAIN QUERY PLAN SELECT t.rowid FROM catalog_search_text_fts f LEFT JOIN catalog_search_text_fact t ON t.rowid=f.rowid WHERE f.rowid=1").joinToString { it.bulkText("detail") }
            assertTrue(sourcePlan, sourcePlan.contains("INTEGER PRIMARY KEY"))
            assertTrue(ftsPlan, ftsPlan.contains("VIRTUAL TABLE INDEX"))
            assertFalse(ftsPlan, ftsPlan.contains("TEMP B-TREE"))
            assertTrue(mirrorPlan, mirrorPlan.contains("INTEGER PRIMARY KEY"))
        }
    }

    @Test fun failedSourceGenerationReclaimsMirrorsInBoundedTransactionsAndPreservesOtherActiveData() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val old = db.install(listOf(record()))
            db.materialize(old)
            val active = db.install(listOf(bulkRecord("new-p", "new-o")))
            db.exec("UPDATE catalog_generation SET status='FAILED' WHERE generation_id='${old.value}'")
            var complete = false
            var calls = 0
            while (!complete && calls++ < 100) complete = CatalogGenerationCleanup(db, 1).recover(BULK_NO_CANCEL, 1).bulkValue()
            assertTrue(complete)
            assertTrue(calls > 1)
            assertTrue(db.rows("SELECT rowid FROM catalog_search_text_fts WHERE generation_id='${old.value}'").isEmpty())
            assertTrue(db.rows("SELECT generation_id FROM catalog_search_build WHERE generation_id='${old.value}'").isEmpty())
            assertEquals(active.value, db.rows("SELECT generation_id FROM catalog_active").single().bulkText("generation_id"))
            assertEquals("new-o", db.rows("SELECT oracle_id FROM oracle_card").single().bulkText("oracle_id"))
        }
    }

    @Test fun verificationRawAllowanceLeavesNextSourcePayloadUnconsumed() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val records = (1..6).map { index -> JsonParser.parseString(bulkRecord("p$index", "o$index")).asJsonObject.apply {
                addProperty("uninterpreted_padding", "x".repeat(3 * 1024 * 1024))
            }.toString() }
            val g = db.install(records)
            db.materialize(g); db.toPhase(g, SearchPreparationPhase.VERIFY_CARD)
            val prefix = db.store().advance(g, BULK_NO_CANCEL).bulkValue()
            assertEquals("5,0,0,15,0", prefix.provider)
            assertEquals(SearchPreparationPhase.VERIFY_CARD, prefix.phase)
            assertEquals(SearchPreparationStatus.READY, db.finish(g).status)
        }
    }
}
