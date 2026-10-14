package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*

/** Resumable nonactive staging cleanup. Never cascade a large corpus or touch card generations. */
internal class RulingsGenerationCleanup(private val database: Database, private val pageSize: Int = 100) {
    init { require(pageSize in 1..100) }
    suspend fun recover(cancel: CancelToken, maxTransactions: Int = 32): Outcome<Boolean> = withCatalogImportOwner(cancel) { owner ->
        bulkAttempt {
            require(maxTransactions in 1..10_000)
            val request = bulkSql("""SELECT generation_id FROM rulings_generation g WHERE status IN ('STAGING','FAILED')
                AND NOT EXISTS (SELECT 1 FROM rulings_active a WHERE a.generation_id=g.generation_id)
                ORDER BY generation_id LIMIT 1""", 1)
            val row = database.query(request, cancel).bulkValue().firstOrNull() ?: return@bulkAttempt true
            reclaimOwned(GenerationId(row.bulkText("generation_id")), owner, cancel, maxTransactions).bulkValue() &&
                database.query(request, cancel).bulkValue().isEmpty()
        }
    }
    suspend fun reclaimOwned(id: GenerationId, owner: Any, cancel: CancelToken, maxTransactions: Int): Outcome<Boolean> = bulkAttempt {
        check(CatalogImportOwnership.mutex.holdsLock(owner))
        require(maxTransactions in 1..10_000)
        var remaining = maxTransactions
        val eligible = "generation_id=:g AND status IN ('STAGING','FAILED') AND NOT EXISTS (SELECT 1 FROM rulings_active a WHERE a.generation_id=:g)"
        val row = database.query(bulkSql("SELECT status FROM rulings_generation WHERE $eligible", 1, "g" to id.value), cancel).bulkValue().firstOrNull()
            ?: return@bulkAttempt true
        if (row.bulkText("status") != "FAILED") {
            val marked = database.transaction({ tx -> tx.bulkWrite("UPDATE rulings_generation SET status='FAILED' WHERE $eligible", cancel, "g" to id.value) }, cancel).bulkValue()
            remaining--
            if (marked != 1L) return@bulkAttempt false
        }
        val rowGuard = "generation_id=:g AND EXISTS (SELECT 1 FROM rulings_generation g WHERE g.generation_id=:g AND g.status='FAILED') AND NOT EXISTS (SELECT 1 FROM rulings_active a WHERE a.generation_id=:g)"
        while (remaining > 1) {
            cancel.throwIfCancelled()
            if (database.query(bulkSql("SELECT ordinal FROM ruling_record WHERE $rowGuard LIMIT 1", 1, "g" to id.value), cancel).bulkValue().isEmpty()) break
            val deleted = database.transaction({ tx ->
                tx.bulkWrite("DELETE FROM ruling_record WHERE rowid IN (SELECT rowid FROM ruling_record WHERE $rowGuard ORDER BY ordinal LIMIT :limit)", cancel,
                    "g" to id.value, "limit" to pageSize)
            }, cancel).bulkValue()
            remaining--
            if (deleted == 0L) return@bulkAttempt false
        }
        if (remaining == 0 || database.query(bulkSql("SELECT ordinal FROM ruling_record WHERE generation_id=:g LIMIT 1", 1, "g" to id.value), cancel).bulkValue().isNotEmpty()) return@bulkAttempt false
        database.transaction({ tx ->
            tx.bulkWrite("DELETE FROM rulings_generation WHERE $eligible AND status='FAILED' AND NOT EXISTS (SELECT 1 FROM ruling_record r WHERE r.generation_id=:g)", cancel, "g" to id.value)
        }, cancel).bulkValue() == 1L
    }
}
