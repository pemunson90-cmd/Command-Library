package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CatalogGenerationCleanupTest {
    @Test fun recoveryIsBoundedRestartableAndPreservesActiveAndRetiredGenerations() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            val old=db.install(listOf(bulkRecord("old","old-o")))
            val current=db.install(listOf(bulkRecord("new","new-o")))
            // Crash fixture: a complete inactive generation abandoned before pointer commit.
            db.exec("UPDATE catalog_generation SET status='VALIDATED' WHERE generation_id='${old.installed.generation.id.value}'")
            val cleanup=CatalogGenerationCleanup(db,1)
            assertFalse(cleanup.recover(BULK_NO_CANCEL,2).bulkValue())
            assertEquals(current.installed.generation.id.value,db.active())
            assertTrue(LocalCatalogIdentities(db).printing(CardId(ProviderId("scryfall"),"new"),BULK_NO_CANCEL) is Outcome.Success)
            var attempts=0
            while(!cleanup.recover(BULK_NO_CANCEL,8).bulkValue()) { assertTrue(++attempts<20) }
            assertEquals(1L,db.count("catalog_generation")); assertEquals(1L,db.count("printing"))
            db.install(listOf(bulkRecord("latest","latest-o")))
            assertTrue(cleanup.recover(BULK_NO_CANCEL).bulkValue()); assertEquals(2L,db.count("catalog_generation"))
        }
    }
    @Test fun failedCleanupAndHandleReopenKeepOldActiveReadable() = runBlocking {
        val file=java.io.File.createTempFile("synthetic-catalog-reopen", ".sqlite").apply { delete() }
        try {
            var active: String?=null
            BulkSqlTestDatabase(file.path).use { db ->
                active=db.install(listOf(bulkRecord("old","old-o"))).installed.generation.id.value
                db.failCleanup=true
                assertTrue(db.importer(CatalogBulkImportLimits(maxBatchRecords=1)).`import`(bulkSelected(),bulkChunks(listOf(bulkRecord("a","o"),bulkRecord("a","o")).joinToString("\n")),{},BULK_NO_CANCEL) is Outcome.Failure)
                assertEquals(1L,db.rows("SELECT count(*) AS n FROM catalog_generation WHERE status='FAILED'").single().bulkLong("n"))
                assertTrue(LocalCatalogIdentities(db).printing(CardId(ProviderId("scryfall"),"old"),BULK_NO_CANCEL) is Outcome.Success)
            }
            BulkSqlTestDatabase(file.path).use { db ->
                assertEquals(active,db.active()); assertTrue(LocalCatalogIdentities(db).printing(CardId(ProviderId("scryfall"),"old"),BULK_NO_CANCEL) is Outcome.Success)
                var calls=0
                while(!CatalogGenerationCleanup(db,1).recover(BULK_NO_CANCEL,1).bulkValue()) assertTrue(++calls<20)
                assertEquals(1L,db.count("catalog_generation"))
            }
        } finally { file.delete() }
    }
    @Test fun wrongOwnerTokenCannotReclaimAnotherLiveAttempt() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            val generation=db.install(listOf(bulkRecord("a","o"))).installed.generation.id
            db.exec("DELETE FROM catalog_active"); db.exec("UPDATE catalog_generation SET status='STAGING'")
            val owner=Any(); CatalogImportOwnership.mutex.lock(owner)
            try {
                assertTrue(CatalogGenerationCleanup(db).reclaimOwned(generation,BULK_NO_CANCEL,Any()) is Outcome.Failure)
                assertEquals(1L,db.count("printing"))
            } finally { CatalogImportOwnership.mutex.unlock(owner) }
        }
    }

    @Test fun processOwnerExcludesRecoveryAndCancellationLeavesRestartableState() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            db.install(listOf(bulkRecord("a","o")))
            db.exec("DELETE FROM catalog_active"); db.exec("UPDATE catalog_generation SET status='STAGING'")
            val cancel=BulkMutableCancel().apply { cancelled=true }
            try { CatalogGenerationCleanup(db).recover(cancel); fail() } catch(_: kotlinx.coroutines.CancellationException) {}
            assertEquals(1L,db.count("catalog_generation"))
            assertTrue(CatalogGenerationCleanup(db).recover(BULK_NO_CANCEL).bulkValue())
            assertEquals(0L,db.count("catalog_generation"))
        }
    }
}
