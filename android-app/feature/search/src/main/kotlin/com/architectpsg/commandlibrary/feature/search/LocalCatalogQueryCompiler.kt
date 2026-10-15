package com.architectpsg.commandlibrary.feature.search

import com.architectpsg.commandlibrary.core.model.*

/** Pure schema9/projection1 compiler. It neither selects a snapshot nor performs database IO. */
public class LocalCatalogQueryCompiler : CatalogQueryCompiler {
    override fun compile(query: QueryAst, facePolicy: FaceMatchPolicy, cancel: CancelToken): Outcome<CompiledCatalogQuery> {
        cancel.throwIfCancelled()
        return try {
            val validated = CatalogQueryValidation(cancel).validate(query, facePolicy)
            if (validated.unsupported) {
                Outcome.Unavailable(UnavailableReason.UNSUPPORTED,
                    "Query requires contextual sources or a legality status unavailable in projection 1")
            } else {
                val sql = CatalogQuerySql(cancel)
                val predicate = sql.compile(validated.query, facePolicy)
                cancel.throwIfCancelled()
                require(predicate.toByteArray(Charsets.UTF_8).size <= 262144) { "Compiled query exceeds SQL budget" }
                Outcome.Success(CompiledCatalogQuery(predicate, SnapshotList.copyOf(sql.parameters),
                    validated.fingerprint, facePolicy))
            }
        } catch (invalid: IllegalArgumentException) {
            // Cancellation is never converted to a domain result, including custom token exceptions.
            cancel.throwIfCancelled()
            Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT,
                invalid.message ?: "Invalid catalog query", SnapshotList.empty()))
        }
    }
}
