package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.JsonParser
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration

/** Actual schema9 SQLite/preparation/decoder tests. Fixed plans are isolated reader oracles;
 * these doubles deliberately make no claim about the production compiler's AST semantics. */
class LocalPreparedCardQueryTest {
    private val ast = QueryAst.Text(SearchTextField.NAME, "fixture", true)
    private val clock = object : Clock {
        override fun utcNow() = UtcTimestamp(3)
        override fun monotonicNow() = Duration.ZERO
    }
    private fun context(g: GenerationId, policy: FaceMatchPolicy = FaceMatchPolicy.ANY_FACE) = SearchContext(
        SnapshotList.of(SnapshotReference(ProviderId("scryfall"), g, null, LanguageTag.ENGLISH)), SnapshotList.of(LanguageTag.ENGLISH), policy)
    private fun plan(predicate: String = "1", parameters: List<SqlParameter> = emptyList(), policy: FaceMatchPolicy = FaceMatchPolicy.ANY_FACE, marker: String = predicate): CompiledCatalogQuery =
        CompiledCatalogQuery(predicate, SnapshotList.copyOf(parameters), ContentHash("SHA-256", MessageDigest.getInstance("SHA-256").digest(marker.toByteArray()).joinToString("") { "%02x".format(it) }), policy)
    private fun compiler(plan: CompiledCatalogQuery = plan()) = object : CatalogQueryCompiler {
        override fun compile(query: QueryAst, facePolicy: FaceMatchPolicy, cancel: CancelToken): Outcome<CompiledCatalogQuery> {
            cancel.throwIfCancelled(); return Outcome.Success(plan)
        }
    }
    private fun record(id: String, name: String = "Synthetic", extra: (com.google.gson.JsonObject.() -> Unit)? = null): String =
        JsonParser.parseString(bulkRecord("p$id", "o$id")).asJsonObject.apply { addProperty("name", name); extra?.invoke(this) }.toString()
    private suspend fun ready(db: SearchProjectionSqlDatabase, generation: GenerationId) {
        val store = CatalogSearchPreparationStore(db, clock)
        repeat(500) {
            val next = store.advance(generation, BULK_NO_CANCEL).bulkValue()
            if (next.status == SearchPreparationStatus.READY) return
            check(next.status != SearchPreparationStatus.FAILED)
        }
        error("Fixture preparation did not finish")
    }
    private suspend fun run(db: Database, generation: GenerationId, limit: Int = 100, cursor: String? = null,
        compiled: CompiledCatalogQuery = plan(), cancel: CancelToken = BULK_NO_CANCEL) =
        LocalPreparedCardQuery(db, compiler(compiled)).local(ast, context(generation, compiled.facePolicy), PageRequest(limit, cursor), cancel)
    private fun failure(code: FailureCode, outcome: Outcome<*>) = assertEquals(code, (outcome as Outcome.Failure).error.code)

    /** Ensures the production reader never issues an outside query or a transaction write. */
    private class ReadOnlyProbe(private val db: Database, private val bound: Int = 999) : Database {
        val requests = ArrayList<SqlRequest>()
        var transactions = 0
        override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> = error("Reader query escaped transaction")
        override suspend fun capabilities(cancel: CancelToken): Outcome<DbCapabilities> = when (val result = db.capabilities(cancel)) {
            is Outcome.Success -> Outcome.Success(result.value.copy(maxBoundParameters = bound))
            else -> result
        }
        override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> {
            transactions++
            return db.transaction({ tx -> action(object : DbTransaction {
                override val state get() = tx.state
                override fun abort(error: DomainFailure): Nothing = tx.abort(error)
                override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> {
                    requests.add(request); return tx.query(request, cancel)
                }
                override suspend fun execute(request: SqlRequest, cancel: CancelToken): Outcome<DbWriteResult> = error("Reader attempted write")
            }) }, cancel)
        }
    }

    @Test fun preparedOriginalRecordsAreDecodedInOneReadOnlyTransactionWithRealProvenance() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val g = db.install(listOf(record("1", "Éclair") { addProperty("oracle_text", "Draw a card.") }))
            ready(db, g)
            val expected = LocalCatalogIdentities(db).oracle(OracleId(ProviderId("scryfall"), "o1"), BULK_NO_CANCEL).bulkValue()
            val before = db.rows("SELECT * FROM catalog_generation")
            val probe = ReadOnlyProbe(db)
            val page = run(probe, g).bulkValue()
            assertEquals(listOf(expected), page.items); assertEquals(g, page.snapshot.generation); assertNull(page.nextCursor)
            assertEquals(1, probe.transactions)
            assertEquals(before, db.rows("SELECT * FROM catalog_generation"))
            assertFalse(probe.requests.any { it.statement.trimStart().startsWith("SELECT *") })
        }
    }

    @Test fun stableKeysetUsesNameThenProviderThenOracleAndCompleteLookahead() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val g = db.install(listOf(record("3", "Beta"), record("2", "Alpha"), record("1", "Alpha"), record("4", "Ωmega")))
            ready(db, g)
            val probe = ReadOnlyProbe(db)
            val ids = ArrayList<String>(); var cursor: String? = null
            repeat(2) {
                val page = run(probe, g, 2, cursor).bulkValue()
                assertEquals(2, page.items.size); ids.addAll(page.items.map { it.oracleId.raw }); cursor = page.nextCursor
            }
            assertEquals(listOf("o1", "o2", "o3", "o4"), ids); assertNull(cursor)
            val keys = probe.requests.filter { it.statement.contains("FROM oracle_card oc JOIN") }
            assertEquals(2, keys.size); assertTrue(keys.all { it.maxRows == 3 && !it.statement.contains("AS record_payload") })
            assertEquals(4, probe.requests.count { it.statement.contains("END AS record_payload") })
        }
    }

    @Test fun boundPredicateAndThreeValuedTruthSelectOnlyTrue() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val g = db.install(listOf(record("1"), record("2"))); ready(db, g)
            val exact = plan("c.oracle_id=:qp0", listOf(SqlParameter("qp0", SqlValue.Text("o2"))))
            assertEquals(listOf("o2"), run(db, g, compiled = exact).bulkValue().items.map { it.oracleId.raw })
            for (predicate in listOf("0", "NULL", "NOT NULL")) assertTrue(run(db, g, compiled = plan(predicate)).bulkValue().items.isEmpty())
        }
    }

    @Test fun everyFacePolicyCanConsumeItsMatchingPreparedPlan() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val g = db.install(listOf(record("1"))); ready(db, g)
            for (policy in FaceMatchPolicy.entries)
                assertEquals(listOf("o1"), run(db, g, compiled = plan(policy = policy)).bulkValue().items.map { it.oracleId.raw })
        }
    }

    @Test fun noPreparationAndInProgressAndFailedNeverBecomeEmptySuccess() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val g = db.install(listOf(record("1")))
            assertEquals(UnavailableReason.SOURCE_UNAVAILABLE, (run(db, g, compiled = plan("0")) as Outcome.Unavailable).reason)
            CatalogSearchPreparationStore(db, clock).advance(g, BULK_NO_CANCEL).bulkValue()
            assertTrue(run(db, g) is Outcome.Unavailable)
            db.exec("UPDATE catalog_search_build SET status='FAILED',diagnostic='INVALID_SOURCE_OR_PROJECTION'")
            assertTrue(run(db, g) is Outcome.Unavailable)
        }
    }

    @Test fun explicitRetiredGenerationNeverRebindsToActive() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val old = db.install(listOf(record("old"))); ready(db, old)
            val current = db.install(listOf(record("new"))); ready(db, current)
            assertEquals(listOf("oold"), run(db, old).bulkValue().items.map { it.oracleId.raw })
            assertEquals(listOf("onew"), run(db, current).bulkValue().items.map { it.oracleId.raw })
            failure(FailureCode.STALE_GENERATION, run(db, GenerationId("missing"), compiled = plan("0")))
        }
    }

    @Test fun deletedGenerationWithCursorReportsStaleBeforeEmptyShortcut() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val g = db.install(listOf(record("1"), record("2"))); ready(db, g)
            val cursor = run(db, g, 1).bulkValue().nextCursor!!
            db.exec("DELETE FROM catalog_active"); db.exec("DELETE FROM catalog_search_text_fts")
            db.exec("DELETE FROM catalog_generation")
            failure(FailureCode.STALE_GENERATION, run(db, g, 1, cursor))
        }
    }

    @Test fun reopenPreservesPreparedGenerationAndContinuation() = runBlocking {
        val directory = File("/tmp/cl-indexed-query-reader-01").apply { mkdirs() }
        val file = File.createTempFile("query-reader-", ".db", directory)
        try {
            val saved = SearchProjectionSqlDatabase(file.path).use { db ->
                val g = db.install(listOf(record("1"), record("2"))); ready(db, g)
                g to run(db, g, 1).bulkValue().nextCursor!!
            }
            SearchProjectionSqlDatabase(file.path).use { db ->
                assertEquals(listOf("o2"), run(db, saved.first, 1, saved.second).bulkValue().items.map { it.oracleId.raw })
            }
        } finally { file.delete() }
    }

    @Test fun cursorRejectsWrongQueryFacePolicyAndSnapshotIncludingEmptyQuery() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val g = db.install(listOf(record("1"), record("2"))); ready(db, g)
            val cursor = run(db, g, 1).bulkValue().nextCursor!!
            failure(FailureCode.INVALID_INPUT, run(db, g, 1, cursor, plan("0")))
            failure(FailureCode.INVALID_INPUT, run(db, g, 1, cursor, plan(policy = FaceMatchPolicy.SAME_FACE)))
            failure(FailureCode.STALE_GENERATION, run(db, GenerationId("another"), 1, cursor))
            failure(FailureCode.INVALID_INPUT, run(db, g, 1, "malformed", plan("0")))
        }
    }

    @Test fun malformedPlansFailBeforeAnyTransaction() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val probe = ReadOnlyProbe(db)
            val qp = SqlParameter("qp0", SqlValue.Text("value"))
            val bad = listOf(plan().copy(compilerVersion = 2), plan().copy(projectionVersion = 2),
                plan().copy(facePolicy = FaceMatchPolicy.CANONICAL), plan().copy(fingerprint = ContentHash("SHA-1", "a".repeat(64))),
                plan().copy(fingerprint = ContentHash("SHA-256", "A".repeat(64))), plan().copy(fingerprint = ContentHash("SHA-256", "a".repeat(63))),
                plan(":qp0", listOf(qp, qp)), plan(":qp0"), plan("1", listOf(qp)), plan("':qp0'", listOf(qp)),
                plan(":qp512", listOf(SqlParameter("qp512", SqlValue.Integer(1)))), plan(":rq_generation", listOf(SqlParameter("rq_generation", SqlValue.Text("g")))),
                plan("1; SELECT 1"), plan("1 -- comment"), plan("1 /* comment */"), plan("?"), plan("(1"), plan("'unterminated"),
                plan(":qp0", listOf(SqlParameter("qp0", SqlValue.Text("\ud800")))),
                plan(":qp0", listOf(SqlParameter("qp0", SqlValue.Text("x".repeat(32769))))),
                plan(":qp0", listOf(SqlParameter("qp0", SqlValue.Text("é".repeat(16385))))),
                plan(":qp0", listOf(SqlParameter("qp" + "x".repeat(128), SqlValue.Integer(1)))),
                plan("1".repeat(262145)), plan("é".repeat(131073)))
            for (invalid in bad) failure(FailureCode.INVALID_INPUT,
                LocalPreparedCardQuery(probe, compiler(invalid)).local(ast, context(GenerationId("g")), PageRequest(1), BULK_NO_CANCEL))
            assertEquals(0, probe.transactions)
        }
    }

    @Test fun combinedCompilerAndReaderBindingsUseActualDatabaseCeiling() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val g = db.install(listOf(record("1"), record("2"))); ready(db, g)
            val p = plan("c.oracle_id>=:qp0", listOf(SqlParameter("qp0", SqlValue.Text("o1"))))
            val first = run(ReadOnlyProbe(db, 8), g, 1, compiled = p).bulkValue()
            // Continuation adds three reader parameters: 2 base + 1 compiler + 3 seek = 6.
            val tight = ReadOnlyProbe(db, 5)
            failure(FailureCode.INVALID_INPUT, run(tight, g, 1, first.nextCursor, p))
            assertEquals(0, tight.transactions)
            assertEquals(1, run(ReadOnlyProbe(db, 8), g, 1, first.nextCursor, p).bulkValue().items.size)
        }
    }

    @Test fun fullCompilerParameterBudgetIncludesReaderSeekBindings() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val g = db.install(listOf(record("1"), record("2"))); ready(db, g)
            val parameters = (0 until 512).map { SqlParameter("qp$it", SqlValue.Text(if (it == 0) "o1" else "o2")) }
            val p = plan("c.oracle_id IN (${parameters.joinToString(",") { ":${it.name}" }})", parameters)
            val first = run(ReadOnlyProbe(db, 516), g, 1, compiled = p).bulkValue()
            val tight = ReadOnlyProbe(db, 516)
            failure(FailureCode.INVALID_INPUT, run(tight, g, 1, first.nextCursor, p))
            assertEquals(0, tight.transactions)
            assertEquals(listOf("o2"), run(ReadOnlyProbe(db, 517), g, 1, first.nextCursor, p).bulkValue().items.map { it.oracleId.raw })
        }
    }

    @Test fun unsupportedContextsAndInvalidGenerationAreRejectedBeforeDatabase() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val probe = ReadOnlyProbe(db); val good = context(GenerationId("g")); val ref = good.snapshots.single()
            val invalid = listOf(good.copy(snapshots = SnapshotList.empty()), good.copy(snapshots = SnapshotList.of(ref, ref)),
                good.copy(snapshots = SnapshotList.of(ref.copy(source = ProviderId("foreign")))),
                good.copy(snapshots = SnapshotList.of(ref.copy(generation = null))), good.copy(snapshots = SnapshotList.of(ref.copy(revision = Revision(1)))),
                good.copy(snapshots = SnapshotList.of(ref.copy(language = LanguageTag("fr")))), good.copy(languages = SnapshotList.of(LanguageTag.ENGLISH, LanguageTag("fr"))))
            for (ctx in invalid) assertEquals(UnavailableReason.UNSUPPORTED,
                (LocalPreparedCardQuery(probe, compiler()).local(ast, ctx, PageRequest(1), BULK_NO_CANCEL) as Outcome.Unavailable).reason)
            failure(FailureCode.INVALID_INPUT, run(probe, GenerationId("g".repeat(129))))
            failure(FailureCode.INVALID_INPUT, run(probe, GenerationId("é".repeat(65))))
            failure(FailureCode.INVALID_INPUT, run(probe, GenerationId("bad\u0000id")))
            assertEquals(0, probe.transactions)
        }
    }

    @Test fun compilerUnsupportedAndFailureAndUnknownRemainTyped() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val probe = ReadOnlyProbe(db)
            val failures: List<Outcome<CompiledCatalogQuery>> = listOf(Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Context unavailable"),
                Outcome.Failure(bulkError(FailureCode.INVALID_INPUT, "Invalid AST")), Outcome.Unknown("Compiler evidence missing", SnapshotList.empty()))
            for (expected in failures) {
                val port = LocalPreparedCardQuery(probe, object : CatalogQueryCompiler {
                    override fun compile(query: QueryAst, facePolicy: FaceMatchPolicy, cancel: CancelToken) = expected
                })
                assertEquals(expected, port.local(QueryAst.Not(QueryAst.InDeck), context(GenerationId("g")), PageRequest(1), BULK_NO_CANCEL))
            }
            assertEquals(0, probe.transactions)
        }
    }

    @Test fun lookaheadPayloadIsNeverReadOrDecoded() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val g = db.install(listOf(record("1"), record("2"))); ready(db, g)
            db.exec("UPDATE oracle_card SET record_payload='not-json' WHERE oracle_id='o2'")
            val probe = ReadOnlyProbe(db)
            val first = run(probe, g, 1).bulkValue()
            assertEquals(listOf("o1"), first.items.map { it.oracleId.raw }); assertNotNull(first.nextCursor)
            assertEquals(1, probe.requests.count { it.statement.contains("END AS record_payload") })
            failure(FailureCode.CORRUPT_DATA, run(db, g, 1, first.nextCursor))
        }
    }

    @Test fun oversizeRecordAndPageFailBeforeAnyPayloadRead() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val g = db.install(listOf(record("1"), record("2"), record("3"))); ready(db, g)
            // Real SQL byte lengths; payload bodies intentionally need not be valid JSON here.
            db.exec("UPDATE oracle_card SET record_payload=printf('%.*c',6000000,'x')")
            val probe = ReadOnlyProbe(db)
            assertEquals(UnavailableReason.SOURCE_UNAVAILABLE, (run(probe, g, 3) as Outcome.Unavailable).reason)
            assertFalse(probe.requests.any { it.statement.contains("END AS record_payload") })
            db.exec("UPDATE oracle_card SET record_payload=printf('%.*c',8388609,'x') WHERE oracle_id='o1'")
            val oversized = ReadOnlyProbe(db)
            assertTrue(run(oversized, g, 1) is Outcome.Unavailable)
            assertFalse(oversized.requests.any { it.statement.contains("END AS record_payload") })
        }
    }

    @Test fun generatedCursorOverflowIsExplicitBeforeDecoding() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val g = db.install(listOf(record("1", "a"), record("2", "z"))); ready(db, g)
            // The importer admits names <=512 UTF8 bytes. Explicit retained metadata corruption
            // exercises cursor overflow without pretending the original source admitted this name.
            db.exec("UPDATE oracle_card SET normalized_name='${"a".repeat(1500)}' WHERE oracle_id='o1'")
            val probe = ReadOnlyProbe(db)
            assertEquals(UnavailableReason.SOURCE_UNAVAILABLE, (run(probe, g, 1) as Outcome.Unavailable).reason)
            assertFalse(probe.requests.any { it.statement.contains("END AS record_payload") })
        }
    }

    @Test fun corruptOriginalIdentityDigestAndScalarsNeverEscapeAsCards() = runBlocking {
        for (mutation in listOf("canonical_digest='${"0".repeat(64)}'", "name='wrong'", "normalized_search_text='wrong'", "original_text='wrong'",
            "record_payload=replace(record_payload,'o1','wrong-oracle')")) {
            SearchProjectionSqlDatabase().use { db ->
                val g = db.install(listOf(record("1"))); ready(db, g)
                db.exec("UPDATE oracle_card SET $mutation")
                failure(FailureCode.CORRUPT_DATA, run(db, g))
            }
        }
    }

    @Test fun originalFaceRowsAreProvedWithoutLoadingDuplicatePayloads() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val g = db.install(listOf(record("1") { add("card_faces", JsonParser.parseString("""[{"object":"card_face","name":"Front"},{"object":"card_face","name":"Back"}]""")) }))
            ready(db, g)
            assertEquals(2, run(db, g).bulkValue().items.single().faces.size)
            db.exec("UPDATE card_face SET face_payload='wrong' WHERE face_index=1")
            failure(FailureCode.CORRUPT_DATA, run(db, g))
        }
    }

    @Test fun incompatibleInstalledSourceProofCannotBecomeSuccessfulEmptyPage() = runBlocking {
        for (mutation in listOf("provider='foreign'", "payload_schema_version=999", "parser_version='other'", "format='other'", "language_scope='fr'", "content_sha256='${"0".repeat(64)}'")) {
            SearchProjectionSqlDatabase().use { db ->
                val g = db.install(listOf(record("1"))); ready(db, g)
                db.exec("UPDATE catalog_generation SET $mutation")
                failure(if (mutation.startsWith("content_sha256")) FailureCode.CORRUPT_DATA else FailureCode.STALE_GENERATION,
                    run(db, g, compiled = plan("0")))
            }
        }
    }

    @Test fun cancellationBeforeCompilationBetweenReadsAndDuringDecodeEscapes() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val g = db.install(listOf(record("1"), record("2"))); ready(db, g)
            var cancelled = true
            val token = object : CancelToken {
                override val isCancelled get() = cancelled
                override fun throwIfCancelled() { if (cancelled) throw CancellationException("Reader cancelled") }
            }
            try { run(db, g, cancel = token); fail("Cancellation must escape") } catch (_: CancellationException) { }
            for (boundary in listOf("FROM catalog_search_build", "END AS record_payload", "AS matches")) {
                cancelled = false
                db.beforeQuery = { if (it.statement.contains(boundary)) cancelled = true }
                try { run(db, g, cancel = token); fail("Cancellation must escape at $boundary") } catch (_: CancellationException) { }
                db.beforeQuery = null
            }
        }
    }

    @Test fun actualReadFailureIsPreservedAndDoesNotInitializeOrPrepare() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val g = db.install(listOf(record("1"))); ready(db, g)
            db.beforeQuery = { if (it.statement.contains("FROM oracle_card oc JOIN")) error("Injected read failure") }
            failure(FailureCode.PERSISTENCE, run(db, g))
            db.beforeQuery = null
            assertEquals("READY", db.rows("SELECT status FROM catalog_search_build").single().bulkText("status"))
        }
    }
}
