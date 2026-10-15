package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration

class LocalCatalogSearchPreparationTest {
    private val clock = object : Clock {
        override fun utcNow() = UtcTimestamp(3)
        override fun monotonicNow() = Duration.ZERO
    }
    private suspend fun finish(port: CatalogSearchPreparation, generation: GenerationId): CatalogSearchPreparationState {
        repeat(50) {
            val state = port.advance(generation, BULK_NO_CANCEL).bulkValue()
            if (state.readiness != CatalogSearchPreparationReadiness.PREPARING) return state
        }
        error("Preparation did not finish")
    }

    @Test fun completePortReportsActualAbsentProgressAndReadyWithoutResetting() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val generation = db.install(listOf(bulkRecord("p", "o")))
            val port: CatalogSearchPreparation = LocalCatalogSearchPreparation(db, clock)
            val absent = port.status(generation, BULK_NO_CANCEL).bulkValue()
            assertEquals(CatalogSearchPreparationReadiness.NOT_PREPARED, absent.readiness)
            assertNull(absent.phase); assertNull(absent.failure)
            assertTrue(db.rows("SELECT generation_id FROM catalog_search_build").isEmpty())
            val first = port.advance(generation, BULK_NO_CANCEL).bulkValue()
            assertEquals(CatalogSearchPreparationReadiness.PREPARING, first.readiness)
            assertEquals(CatalogSearchPreparationPhase.PRINTINGS, first.phase)
            assertEquals(1L, first.processed.cards)
            val ready = finish(port, generation)
            assertEquals(CatalogSearchPreparationReadiness.READY, ready.readiness)
            assertEquals(CatalogSearchPreparationPhase.VERIFY_TEXT_MIRRORS, ready.phase)
            assertEquals(1L, ready.processed.printings)
            assertEquals(ready, LocalCatalogSearchPreparation(db, clock).status(generation, BULK_NO_CANCEL).bulkValue())
            db.beforeWrite = { error("Terminal state must not write") }
            assertEquals(ready, port.advance(generation, BULK_NO_CANCEL).bulkValue())
        }
    }

    @Test fun invalidIdentityFailsBeforeAnyDatabaseAccess() = runBlocking {
        val inaccessible = object : Database {
            override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> = error("Must not read")
            override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> = error("Must not transact")
            override suspend fun capabilities(cancel: CancelToken): Outcome<DbCapabilities> = error("Must not inspect")
        }
        val port = LocalCatalogSearchPreparation(inaccessible, clock)
        for (input in listOf("x".repeat(129), "é".repeat(65), "bad\u0000id")) {
            assertEquals(FailureCode.INVALID_INPUT, (port.status(GenerationId(input), BULK_NO_CANCEL) as Outcome.Failure).error.code)
            assertEquals(FailureCode.INVALID_INPUT, (port.advance(GenerationId(input), BULK_NO_CANCEL) as Outcome.Failure).error.code)
        }
    }

    @Test fun retiredSnapshotIsExplicitAndMissingSnapshotNeverRebinds() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val retired = db.install(listOf(bulkRecord("p1", "o1")))
            val active = db.install(listOf(bulkRecord("p2", "o2")))
            val port = LocalCatalogSearchPreparation(db, clock)
            assertEquals(retired, finish(port, retired).generation)
            assertEquals(CatalogSearchPreparationReadiness.NOT_PREPARED, port.status(active, BULK_NO_CANCEL).bulkValue().readiness)
            assertEquals(UnavailableReason.NOT_INSTALLED, (port.status(GenerationId("missing"), BULK_NO_CANCEL) as Outcome.Unavailable).reason)
            assertEquals(UnavailableReason.NOT_INSTALLED, (port.advance(GenerationId("missing"), BULK_NO_CANCEL) as Outcome.Unavailable).reason)
        }
    }

    @Test fun failedStateHasRealDurableFailureAndPreservesOriginals() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val generation = db.install(listOf(bulkRecord("p", "o")))
            val port = LocalCatalogSearchPreparation(db, clock)
            port.advance(generation, BULK_NO_CANCEL).bulkValue()
            db.exec("UPDATE catalog_search_card_fact SET layout='wrong'")
            val failed = finish(port, generation)
            assertEquals(CatalogSearchPreparationReadiness.FAILED, failed.readiness)
            assertEquals(FailureCode.CORRUPT_DATA, failed.failure!!.code)
            assertEquals(1L, failed.processed.cards)
            assertEquals(failed, port.status(generation, BULK_NO_CANCEL).bulkValue())
            assertEquals(1, db.rows("SELECT * FROM oracle_card").size)
        }
    }

    @Test fun cancellationEscapesAndUnknownAcknowledgementIsNeverMappedToReady() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val generation = db.install(listOf(bulkRecord("p", "o")))
            val port = LocalCatalogSearchPreparation(db, clock)
            val cancelled = object : CancelToken { override val isCancelled = true; override fun throwIfCancelled() { throw CancellationException("Cancelled") } }
            try { port.advance(generation, cancelled); fail("Cancellation must escape") } catch (_: CancellationException) { }
            db.afterCommit = { db.beforeQuery = { if (it.statement.contains("FROM catalog_search_build")) error("No ACK") }; error("Lost ACK") }
            assertTrue(port.advance(generation, BULK_NO_CANCEL) is Outcome.Unknown)
            db.afterCommit = null; db.beforeQuery = null
            assertEquals(CatalogSearchPreparationReadiness.PREPARING, port.status(generation, BULK_NO_CANCEL).bulkValue().readiness)
        }
    }
}
