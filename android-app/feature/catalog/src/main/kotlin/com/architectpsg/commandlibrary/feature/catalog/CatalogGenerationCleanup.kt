package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.sync.withLock

/** Bounded resumable reclamation. Retired/active generations and user identities are never eligible. */
internal class CatalogGenerationCleanup(private val database: Database, private val pageSize: Int = 100) {
    init { require(pageSize in 1..100) }
    suspend fun recover(cancel: CancelToken, maxTransactions: Int = 32): Outcome<Boolean> {
        val owner = Any()
        return CatalogImportOwnership.mutex.withLock(owner) {
            bulkAttempt {
                require(maxTransactions in 1..10_000)
                val request = bulkSql("SELECT generation_id FROM catalog_generation WHERE status IN ('STAGING','VALIDATED','FAILED') AND NOT EXISTS (SELECT 1 FROM catalog_active a WHERE a.generation_id=catalog_generation.generation_id) ORDER BY generation_id LIMIT 1", 1)
                val ids = database.query(request, cancel).bulkValue()
                if (ids.isEmpty()) true
                else if (!reclaimOwned(GenerationId(ids.single().bulkText("generation_id")), cancel, owner, maxTransactions).bulkValue()) false
                else database.query(request, cancel).bulkValue().isEmpty()
            }
        }
    }
    /** Caller must hold the shared import owner, excluding every live tiny/full staging writer. */
    suspend fun reclaimOwned(id: GenerationId, cancel: CancelToken, owner: Any, maxTransactions: Int = 32): Outcome<Boolean> = bulkAttempt {
        check(CatalogImportOwnership.mutex.holdsLock(owner))
        require(maxTransactions in 1..10_000)
        var remaining = maxTransactions
        val eligible = "generation_id=:id AND EXISTS (SELECT 1 FROM catalog_generation g WHERE g.generation_id=:id AND g.status='FAILED') AND NOT EXISTS (SELECT 1 FROM catalog_active a WHERE a.generation_id=:id)"
        val row = database.query(bulkSql("SELECT status FROM catalog_generation WHERE generation_id=:id AND status IN ('STAGING','VALIDATED','FAILED') AND NOT EXISTS (SELECT 1 FROM catalog_active a WHERE a.generation_id=:id)", 1, "id" to id.value), cancel).bulkValue().firstOrNull() ?: return@bulkAttempt true
        if (row.bulkText("status") != "FAILED") {
            val marked = database.transaction({ tx ->
                tx.bulkWrite("UPDATE catalog_generation SET status='FAILED' WHERE generation_id=:id AND status IN ('STAGING','VALIDATED','FAILED') AND NOT EXISTS (SELECT 1 FROM catalog_active a WHERE a.generation_id=:id)", cancel, "id" to id.value)
            }, cancel).bulkValue()
            remaining--
            if (marked == 0L) return@bulkAttempt true
        }
        for (table in listOf("catalog_name_fts", "printing_oracle_membership", "printing_face_asset", "printing", "card_face", "oracle_card")) {
            while (remaining > 0) {
                cancel.throwIfCancelled()
                if (database.query(bulkSql("SELECT rowid FROM $table WHERE $eligible LIMIT 1", 1, "id" to id.value), cancel).bulkValue().isEmpty()) break
                val deleted = database.transaction({ tx ->
                    tx.bulkWrite("DELETE FROM $table WHERE rowid IN (SELECT rowid FROM $table WHERE $eligible LIMIT :limit)", cancel, "id" to id.value, "limit" to pageSize)
                }, cancel).bulkValue()
                remaining--
                if (deleted == 0L) break
            }
            if (remaining == 0) return@bulkAttempt false
        }
        database.transaction({ tx ->
            tx.bulkWrite("DELETE FROM catalog_generation WHERE $eligible", cancel, "id" to id.value)
        }, cancel).bulkValue() == 1L
    }
}
