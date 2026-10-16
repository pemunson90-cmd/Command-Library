package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration

/** Real bundled SQLite/import/READY preparation with synthetic source facts, never live legality claims. */
class LocalCatalogFormatLegalityTest {
    private val source = ProviderId("scryfall")
    private val oracle = OracleId(source, "o")
    private val format = FormatId(source, "commander")
    private val clock = object : Clock {
        override fun utcNow() = UtcTimestamp(3)
        override fun monotonicNow() = Duration.ZERO
    }
    private fun snapshot(g: GenerationId) = SnapshotReference(source, g, null, LanguageTag.ENGLISH)
    private fun record(id: String = "p", legalities: String? = "{\"commander\":\"legal\"}"): String =
        bulkRecord(id, "o").dropLast(1) + (legalities?.let { ",\"legalities\":$it" } ?: "") + "}"
    private suspend fun ready(db: SearchProjectionSqlDatabase, g: GenerationId) {
        val store = CatalogSearchPreparationStore(db, clock)
        repeat(500) {
            val next = store.advance(g, BULK_NO_CANCEL).bulkValue()
            if (next.status == SearchPreparationStatus.READY) return
            check(next.status != SearchPreparationStatus.FAILED)
        }
        error("Synthetic preparation did not finish")
    }
    private suspend fun install(db: SearchProjectionSqlDatabase, records: List<String>): GenerationId =
        db.install(records).also { ready(db, it) }
    private suspend fun read(db: Database, g: GenerationId, cancel: CancelToken = BULK_NO_CANCEL) =
        LocalCatalogFormatLegality(db).legality(oracle, format, snapshot(g), cancel)
    private fun failure(result: Outcome<*>, code: FailureCode = FailureCode.CORRUPT_DATA) =
        assertEquals(code, (result as Outcome.Failure).error.code)
    private fun unavailable(result: Outcome<*>, reason: UnavailableReason) =
        assertEquals(reason, (result as Outcome.Unavailable).reason)

    @Test fun supportedSourceStatusesAndActualProvenanceAreSeparateFromDeckValidation() = runBlocking {
        for ((literal, expected) in listOf("legal" to LegalityStatus.LEGAL, "banned" to LegalityStatus.BANNED,
            "not_legal" to LegalityStatus.NOT_LEGAL, "restricted" to LegalityStatus.RESTRICTED)) {
            SearchProjectionSqlDatabase().use { db ->
                val g = install(db, listOf(record(legalities = "{\"commander\":\"$literal\"}")))
                val evidence = read(db, g).bulkValue()
                val card = LocalCatalogIdentities(db).oracle(oracle, BULK_NO_CANCEL).bulkValue()
                assertEquals(Knowledge.Known(expected), evidence.status)
                assertEquals(card.provenance, evidence.provenance)
                assertEquals(card.provenance.version, evidence.provenance.version)
                assertEquals(snapshot(g), evidence.snapshot)
                assertEquals(oracle, evidence.oracleId)
                assertEquals(format, evidence.formatId)
            }
        }
    }

    @Test fun missingNullFutureAndConflictingValidatedSourceEvidenceRemainUnknown() = runBlocking {
        for (values in listOf(listOf<String?>(null), listOf("null"), listOf("{}"),
            listOf("{\"commander\":null}"), listOf("{\"commander\":\"future_status\"}"),
            listOf("{\"commander\":\"legal\"}", "{}"),
            listOf("{\"commander\":\"legal\"}", "{\"commander\":\"banned\"}"))) {
            SearchProjectionSqlDatabase().use { db ->
                val g = install(db, values.mapIndexed { index, value -> record("p$index", value) })
                assertTrue(read(db, g).bulkValue().status is Knowledge.Unknown)
            }
        }
        SearchProjectionSqlDatabase().use { db ->
            val g = install(db, listOf(record()))
            db.exec("DELETE FROM printing")
            assertTrue(read(db, g).bulkValue().status is Knowledge.Unknown)
        }
    }

    @Test fun actualDirectAndMembershipDomainDeduplicatesAcrossBoundedKeysetPages() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val g = install(db, List(129) { record("p${it.toString().padStart(3, '0')}") })
            assertEquals(129L, db.rows("SELECT count(*) AS n FROM printing_oracle_membership").single().bulkLong("n"))
            var pages = 0
            var writes = 0
            db.beforeWrite = { writes++ }
            db.beforeQuery = { request ->
                if (request.statement.contains("ORDER BY pr.printing_id LIMIT :limit")) {
                    pages++
                    assertEquals(100, request.maxRows)
                }
            }
            assertEquals(Knowledge.Known(LegalityStatus.LEGAL), read(db, g).bulkValue().status)
            assertEquals(2, pages)
            assertEquals(0, writes)
        }
    }

    @Test fun reversibleMembershipOnlyPrintingContributesItsOwnEligibility() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val reversible = bulkReversible("rev", "o", "o").dropLast(1) + ",\"legalities\":{\"commander\":\"banned\"}}"
            val g = install(db, listOf(record(), reversible))
            assertTrue(LocalCatalogIdentities(db).printing(CardId(source, "rev"), BULK_NO_CANCEL).bulkValue().oracleId is Knowledge.Unknown)
            assertTrue(read(db, g).bulkValue().status is Knowledge.Unknown) // legal + banned, no arbitrary first printing.
        }
    }

    @Test fun readyProjectionDeletionOrDisagreementNeverBecomesUnknownOrLegal() = runBlocking {
        val changes = listOf("DELETE FROM catalog_search_legality_fact", "UPDATE catalog_search_legality_fact SET status='BANNED'",
            "UPDATE catalog_search_printing_fact SET legalities_known=0", "DELETE FROM catalog_search_printing_fact",
            "DELETE FROM catalog_search_card_fact", "UPDATE printing_oracle_membership SET source_kind='FACE'",
            "UPDATE oracle_card SET canonical_digest='private-marker'", "UPDATE printing SET collector_number='wrong'")
        for (sql in changes) SearchProjectionSqlDatabase().use { db ->
            val g = install(db, listOf(record()))
            db.exec(sql)
            val result = read(db, g)
            failure(result)
            assertFalse((result as Outcome.Failure).error.message.contains("private-marker"))
        }
    }

    @Test fun malformedRawCategoriesCannotHideBehindReadyUnknownProjection() = runBlocking {
        for (raw in listOf("[]", "12", "{\"commander\":5}", "{\"commander\":false}")) {
            SearchProjectionSqlDatabase().use { db ->
                val g = install(db, listOf(record(legalities = raw))) // Existing projector truthfully stores UNKNOWN/non-known.
                failure(read(db, g)) // New strict evidence reader rejects malformed categories.
            }
        }
        SearchProjectionSqlDatabase().use { db ->
            val g = install(db, listOf(record()))
            val raw = record().dropLast(1) + ",\"legalities\":{\"commander\":\"banned\"}}"
            db.exec("UPDATE printing SET metadata_payload='${raw.replace("'", "''")}'")
            failure(read(db, g)) // Actual strict duplicate-field parser, no convenient fallback.
        }
    }

    @Test fun orphanMembershipCannotDisappearIntoAnOtherwiseLegalDiscoveredDomain() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val g = install(db, listOf(record()))
            db.exec("PRAGMA foreign_keys=OFF")
            db.exec("INSERT INTO printing_oracle_membership SELECT generation_id,printing_provider,'missing-printing',source_slot,source_kind,oracle_provider,oracle_id,oracle_language FROM printing_oracle_membership")
            failure(read(db, g))
        }
    }

    @Test fun aggregateCountAndByteBoundsFailBeforeAnyLegacyPrintingValidationOrPayloadFetch() = runBlocking {
        for (kind in listOf("count", "sum", "record")) SearchProjectionSqlDatabase().use { db ->
            val g = install(db, listOf(record()))
            when (kind) {
                "count" -> db.exec("""WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i+1 FROM n WHERE i<10000)
                    INSERT INTO printing SELECT generation_id,provider,'extra-'||i,oracle_provider,oracle_id,oracle_language,set_code,set_name,collector_number,language,rarity,finishes,image_uri,metadata_payload FROM printing CROSS JOIN n WHERE printing_id='p'""".trimIndent())
                "sum" -> {
                    db.exec("""WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i+1 FROM n WHERE i<5)
                        INSERT INTO printing SELECT generation_id,provider,'extra-'||i,oracle_provider,oracle_id,oracle_language,set_code,set_name,collector_number,language,rarity,finishes,image_uri,metadata_payload FROM printing CROSS JOIN n WHERE printing_id='p'""".trimIndent())
                    db.exec("UPDATE printing SET metadata_payload=printf('%.*c',${8 * 1024 * 1024},'x')")
                }
                else -> db.exec("UPDATE printing SET metadata_payload=printf('%.*c',${8 * 1024 * 1024 + 1},'x')")
            }
            var legacy = 0
            var rawFetches = 0
            db.beforeQuery = { request ->
                if (request.statement.startsWith("SELECT * FROM printing WHERE")) legacy++
                if (request.statement.startsWith("SELECT CASE WHEN length(CAST(metadata_payload")) rawFetches++
            }
            failure(read(db, g))
            assertEquals(0, legacy)
            assertEquals(0, rawFetches)
        }
    }

    @Test fun perRecordFaceBudgetPrecedesLegacyPerFaceSql() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val g = install(db, listOf(record()))
            val faces = List(17) { """{"object":"card_face","name":"Face $it"}""" }.joinToString(",")
            val changed = record().dropLast(1) + ",\"card_faces\":[$faces]}"
            db.exec("UPDATE printing SET metadata_payload='${changed.replace("'", "''")}'")
            var legacy = 0
            db.beforeQuery = { if (it.statement.startsWith("SELECT * FROM printing WHERE")) legacy++ }
            failure(read(db, g))
            assertEquals(0, legacy)
        }
    }

    @Test fun noInstallNoReadyMissingOracleAndInvalidRequestsHaveDistinctOutcomes() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            unavailable(read(db, GenerationId("absent")), UnavailableReason.NOT_INSTALLED)
            val g = db.install(listOf(record()))
            unavailable(read(db, g), UnavailableReason.SOURCE_UNAVAILABLE)
            ready(db, g)
            val lookup = LocalCatalogFormatLegality(db)
            unavailable(lookup.legality(OracleId(source, "missing"), format, snapshot(g), BULK_NO_CANCEL), UnavailableReason.NOT_FOUND)
            failure(lookup.legality(oracle, FormatId(source, "Commander"), snapshot(g), BULK_NO_CANCEL), FailureCode.INVALID_INPUT)
            failure(lookup.legality(oracle, format, snapshot(g).copy(revision = Revision(1)), BULK_NO_CANCEL), FailureCode.INVALID_INPUT)
            failure(lookup.legality(oracle, format, snapshot(g).copy(generation = null), BULK_NO_CANCEL), FailureCode.INVALID_INPUT)
            failure(lookup.legality(oracle, format, snapshot(g).copy(language = LanguageTag("fr")), BULK_NO_CANCEL), FailureCode.INVALID_INPUT)
            failure(read(db, GenerationId("stale")), FailureCode.STALE_GENERATION)
        }
    }

    @Test fun finalGenerationFenceRunsAfterSuccessfulAndFailedTransactions() = runBlocking {
        for (missing in listOf(false, true)) SearchProjectionSqlDatabase().use { db ->
            val g = install(db, listOf(record()))
            val changing = object : Database by db {
                override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> {
                    val result = db.transaction(action, cancel)
                    db.exec("DELETE FROM catalog_active")
                    return result
                }
            }
            val requested = if (missing) OracleId(source, "missing") else oracle
            failure(LocalCatalogFormatLegality(changing).legality(requested, format, snapshot(g), BULK_NO_CANCEL), FailureCode.STALE_GENERATION)
        }
    }

    @Test fun cancellationBeforeIoAndDuringPagedValidationPropagates() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val g = install(db, listOf(record()))
            val marker = CancellationException("Synthetic cancellation")
            var queries = 0
            db.beforeQuery = { queries++ }
            val token = object : CancelToken {
                override val isCancelled = true
                override fun throwIfCancelled(): Unit = throw marker
            }
            try { read(db, g, token); fail("Cancellation swallowed") }
            catch (actual: CancellationException) { assertSame(marker, actual) }
            assertEquals(0, queries)
            db.beforeQuery = { if (it.statement.contains("ORDER BY pr.printing_id LIMIT :limit")) throw marker }
            try { read(db, g); fail("Cancellation swallowed") }
            catch (actual: CancellationException) { assertSame(marker, actual) }
        }
    }

    @Test fun actualFileReopenPreservesPreparedConsensusWithoutAcquisitionOrWrites() = runBlocking {
        val file = File.createTempFile("synthetic-legality-", ".sqlite")
        file.delete()
        try {
            val g = SearchProjectionSqlDatabase(file.absolutePath).use { db -> install(db, listOf(record())) }
            SearchProjectionSqlDatabase(file.absolutePath).use { db ->
                var writes = 0
                db.beforeWrite = { writes++ }
                assertEquals(Knowledge.Known(LegalityStatus.LEGAL), read(db, g).bulkValue().status)
                assertEquals(0, writes)
            }
        } finally {
            file.delete()
            File(file.absolutePath + "-wal").delete()
            File(file.absolutePath + "-shm").delete()
        }
    }
}
