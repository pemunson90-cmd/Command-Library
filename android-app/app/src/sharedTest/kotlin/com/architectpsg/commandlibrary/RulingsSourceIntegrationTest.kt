package com.architectpsg.commandlibrary

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.common.ConsentCoordinator
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.StorageDatabase
import com.architectpsg.commandlibrary.feature.catalog.api.AndroidCatalogServices
import com.architectpsg.commandlibrary.feature.settings.DataStoreSettingsRepository
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.GZIPOutputStream
import java.security.MessageDigest
import org.json.JSONObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.milliseconds

/** Actual Room/DataStore/factory/Android locator/import; synthetic wire bodies, no publisher TLS. */
@RunWith(AndroidJUnit4::class)
abstract class RulingsSourceIntegrationCases {
    protected open fun prepareEnvironment(context: Context) = Unit
    private val token = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private fun <T> ok(value: Outcome<T>): T { assertTrue("Expected success: $value", value is Outcome.Success); return (value as Outcome.Success).value }
    private val raw = """{"object":"ruling","oracle_id":"00000000-0000-0000-0000-000000000001","source":"wotc","published_at":"2026-10-14","comment":"  Synthetic café 😀  "}"""
    private val corpus = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(raw.toByteArray()) } }.toByteArray()
    private fun metadata(size: Int = corpus.size) = """{"object":"bulk_data","id":"06f54c0b-ab9c-452d-b35a-8297db5eb940","type":"rulings","updated_at":"2026-10-14T09:00:34.909Z","uri":"https://api.scryfall.com/bulk-data/06f54c0b-ab9c-452d-b35a-8297db5eb940","jsonl_download_uri":"https://data.scryfall.io/rulings/synthetic.jsonl.gz","compressed_size":$size}""".toByteArray()
    private inner class Fixture(val context: Context, val db: StorageDatabase, val settings: DataStoreSettingsRepository, val gate: ConsentCoordinator) {
        val opened = mutableListOf<AndroidCatalogServices>()
        val wire = mutableListOf<ResourceUri>()
        var body = metadata()
        var hop: ResourceUri? = null
        var builders = 0
        var database: Database = db
        val clock = object : Clock { override fun utcNow() = UtcTimestamp(System.currentTimeMillis() / 1000); override fun monotonicNow() = (System.nanoTime() / 1_000_000).milliseconds }
        suspend fun create(): AndroidCatalogServices = AndroidCatalogServices.create(context, database, settings, gate, clock,
            object : IdSource { override fun nextId() = UUID.randomUUID().toString() }, { authorize ->
                builders++
                object : NetworkClient {
                    override suspend fun send(request: RequestSpec, cancel: CancelToken): Outcome<ByteResponse> = error("Streaming only")
                    override fun stream(request: RequestSpec, cancel: CancelToken) = flow {
                        authorize(request, hop ?: request.request.uri, cancel) { wire += hop ?: request.request.uri }
                        val evidence = HttpMetadata(200, SnapshotList.of(HttpHeader("Content-Length", body.size.toString())), request.request.uri,
                            clock.utcNow(), Knowledge.Absent("No retry"), Knowledge.Known(false))
                        emit(Outcome.Success(ByteChunk(0, 0, SnapshotBytes(body), evidence, true)))
                    }
                    override suspend fun close() = Unit
                }
            }).also { opened += it }
        suspend fun complete(services: AndroidCatalogServices, request: AcquisitionRequest): AcquisitionSnapshot {
            val plan = ok(services.downloads.plan(request, token))
            val approval = ok(settings.approve(request.intent, token))
            ok(services.downloads.start(plan.plan.jobId, plan.revision, approval.id, token))
            return ok(services.runner.run(plan.plan.jobId, token)).also { assertEquals(AcquisitionState.COMPLETED, it.state) }
        }
        suspend fun sql(statement: String) { ok(db.transaction({ tx -> ok(tx.execute(SqlRequest(statement, SnapshotList.empty()), token)); Unit }, token)) }
        suspend fun count(table: String) = (ok(db.query(SqlRequest("SELECT count(*) AS n FROM $table", SnapshotList.empty()), token)).single().columns.single().value as SqlValue.Integer).value
        suspend fun rows(statement: String) = ok(db.query(SqlRequest(statement, SnapshotList.empty()), token))
    }
    private suspend fun fixture(block: suspend (Fixture) -> Unit) = withContext(Dispatchers.IO) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        prepareEnvironment(app)
        val name = "rulings-source-${UUID.randomUUID()}.db"
        val root = File(app.cacheDir, "rulings-source-${UUID.randomUUID()}").apply { check(mkdirs()) }
        // Match app-private Android filesDir rather than a host mkdirs() default0755 directory.
        val privateFiles = File(root, "files").apply {
            check(mkdir())
            check(setReadable(false, false) && setWritable(false, false) && setExecutable(false, false))
            check(setReadable(true, true) && setWritable(true, true) && setExecutable(true, true))
        }
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = privateFiles
        }
        val db = ok(StorageDatabase.open(app, name)); val gate = ConsentCoordinator()
        val settings = DataStoreSettingsRepository.create(File(root, "settings.pb"), File(root, "installation"), gate)
        val f = Fixture(context, db, settings, gate)
        try { block(f) } finally { withContext(NonCancellable) {
            f.opened.forEach { it.close() }; settings.close(); db.close(); app.deleteDatabase(name); root.deleteRecursively()
        } }
    }

    @Test fun fixedMetadataAndExactFileNeedTwoApprovalsThenActualPinnedImportSurvivesRetention() = runBlocking<Unit> {
        fixture { f ->
            val services = f.create()
            assertSame(services.downloads, services.runner)
            val discovery = services.rulingsDiscoveryRequest("Synthetic discovery", false)
            val plan = ok(services.downloads.plan(discovery, token))
            assertEquals(0, f.wire.size); assertTrue(ok(f.settings.read(token)).consents.isEmpty())
            assertFalse(services.rulingsFileRequest(plan.plan.jobId, "Synthetic file", false, token) is Outcome.Success)
            assertEquals(0L, f.count("catalog_source_receipt"))
            val approval = ok(f.settings.approve(discovery.intent, token))
            ok(services.downloads.start(plan.plan.jobId, plan.revision, approval.id, token))
            assertEquals(AcquisitionState.COMPLETED, ok(services.runner.run(plan.plan.jobId, token)).state)
            val file = ok(services.rulingsFileRequest(plan.plan.jobId, "Synthetic file", false, token))
            assertEquals(1, f.wire.size); assertEquals(1L, f.count("catalog_source_receipt"))
            val filePlan = ok(services.downloads.plan(file, token))
            assertTrue(services.downloads.start(filePlan.plan.jobId, filePlan.revision, approval.id, token) is Outcome.Unavailable)
            assertEquals(1, ok(f.settings.read(token)).consents.size)
            f.body = corpus
            val secondApproval = ok(f.settings.approve(file.intent, token))
            assertNotEquals(approval.id, secondApproval.id)
            ok(services.downloads.start(filePlan.plan.jobId, filePlan.revision, secondApproval.id, token))
            assertEquals(AcquisitionState.COMPLETED, ok(services.runner.run(filePlan.plan.jobId, token)).state)
            val installed = ok(services.rulingsImport.`import`(filePlan.plan.jobId, {}, token))
            assertEquals(1L, installed.generation.recordCount); assertEquals(2, f.wire.size)
            f.sql("DELETE FROM acquisition_job")
            assertEquals(file, ok(services.rulingsFileRequest(plan.plan.jobId, "Synthetic file", false, token)))
            f.sql("DELETE FROM catalog_source_receipt")
            services.close()
            val reopened = f.create()
            assertEquals(installed.generation, ok(reopened.rulings.status(token)).active)
            val oracle = OracleId(ProviderId("scryfall"), "00000000-0000-0000-0000-000000000001")
            assertEquals("  Synthetic café 😀  ", ok(reopened.rulings.rulings(oracle, PageRequest(10, null), token)).items.single().content.comment)
            assertEquals(2, f.builders); assertEquals(2, f.wire.size)
        }
    }

    @Test fun persistedReceiptRehydratesExactFileAuthorityAndFactoryDoesNotRecoverAnotherOwnersJobs() = runBlocking<Unit> {
        fixture { f ->
            val first = f.create(); val metadata = f.complete(first, first.rulingsDiscoveryRequest("Synthetic discovery", false))
            val file = ok(first.rulingsFileRequest(metadata.plan.jobId, "Synthetic file", false, token))
            val queued = ok(first.downloads.plan(file, token))
            f.sql("UPDATE acquisition_job SET state='RUNNING',owner_token='synthetic-orphan',revision=revision+1 WHERE job_id='${queued.plan.jobId.value}'")
            val before = ok(first.downloads.status(queued.plan.jobId, token)); first.close()
            val second = f.create()
            assertEquals(before, ok(second.downloads.status(queued.plan.jobId, token)))
            assertNotNull(ok(second.downloads.plan(file, token)))
            assertFalse(second.downloads.plan(file.copy(sourceDate = Knowledge.Absent("Forged")), token) is Outcome.Success)
            assertFalse(second.downloads.plan(file.copy(intent = file.intent.copy(version = "RULINGS_FILE_v1:unissued")), token) is Outcome.Success)
            assertNotNull(ok(second.downloads.plan(second.englishSearchRequest("Synthetic English", false), token)))
            assertEquals(1, f.wire.size)
        }
    }

    @Test fun sameHostRedirectCannotReachPhysicalEnqueueAndMalformedMetadataCannotIssueReceipt() = runBlocking<Unit> {
        fixture { f ->
            val services = f.create(); val request = services.rulingsDiscoveryRequest("Synthetic discovery", false)
            val plan = ok(services.downloads.plan(request, token)); val approval = ok(f.settings.approve(request.intent, token))
            ok(services.downloads.start(plan.plan.jobId, plan.revision, approval.id, token))
            f.hop = ResourceUri("https://api.scryfall.com/bulk-data")
            assertEquals(AcquisitionState.FAILED, ok(services.runner.run(plan.plan.jobId, token)).state)
            assertEquals(0, f.wire.size)
            f.hop = null; f.body = byteArrayOf(0xC3.toByte(), 0x28)
            val bad = f.complete(services, request)
            assertFalse(services.rulingsFileRequest(bad.plan.jobId, "Synthetic file", false, token) is Outcome.Success)
            assertEquals(0L, f.count("catalog_source_receipt")); assertNull(ok(services.rulings.status(token)).active)
        }
    }

    @Test fun inconclusiveReceiptSavePublishesNoFileAuthorityUntilExactDurableReconciliation() = runBlocking<Unit> {
        fixture { f ->
            val faults = ReceiptAckFaults(f.db); f.database = faults
            val services = f.create(); val discovery = services.rulingsDiscoveryRequest("Synthetic discovery", false)
            val first = f.complete(services, discovery)
            val template = ok(services.rulingsFileRequest(first.plan.jobId, "Synthetic file", false, token))
            val second = f.complete(services, discovery)
            faults.armed = true
            assertTrue(services.rulingsFileRequest(second.plan.jobId, "Synthetic file", false, token) is Outcome.Unknown)
            assertEquals(2L, f.count("catalog_source_receipt"))
            val receiptId = (f.rows("SELECT receipt_id FROM catalog_source_receipt WHERE metadata_job_id='${second.plan.jobId.value}'")
                .single().columns.single().value as SqlValue.Text).value
            val exact = template.copy(intent = template.intent.copy(version = "RULINGS_FILE_v1:$receiptId"))
            assertFalse(services.downloads.plan(exact, token) is Outcome.Success)
            faults.blockReads = false
            assertEquals(exact, ok(services.rulingsFileRequest(second.plan.jobId, "Synthetic file", false, token)))
            assertNotNull(ok(services.downloads.plan(exact, token))); assertEquals(2, f.wire.size)
        }
    }

    @Test fun durableReceiptDriftCannotUseOlderCachedAuthorityToImportAFile() = runBlocking<Unit> {
        fixture { f ->
            val services = f.create(); val metadata = f.complete(services, services.rulingsDiscoveryRequest("Synthetic discovery", false))
            val file = ok(services.rulingsFileRequest(metadata.plan.jobId, "Synthetic file", false, token))
            f.body = corpus; val completed = f.complete(services, file)
            val payload = (f.rows("SELECT receipt_payload FROM catalog_source_receipt").single().columns.single().value as SqlValue.Text).value
            val json = JSONObject(payload); val body = json.getString("metadataBody") + " "
            json.put("metadataBody", body)
            val artifact = json.getJSONObject("artifact").getJSONObject("value")
            artifact.put("bytes", body.toByteArray().size)
            artifact.getJSONObject("sha256").put("hex", MessageDigest.getInstance("SHA-256").digest(body.toByteArray()).joinToString("") { "%02x".format(it) })
            ok(f.db.transaction({ tx -> ok(tx.execute(SqlRequest("UPDATE catalog_source_receipt SET receipt_payload=:p",
                SnapshotList.of(SqlParameter("p", SqlValue.Text(json.toString())))), token)); Unit }, token))
            val rejected = services.rulingsImport.`import`(completed.plan.jobId, {}, token)
            assertEquals(FailureCode.SOURCE_MISMATCH, (rejected as Outcome.Failure).error.code)
            assertEquals(0L, f.count("rulings_generation")); assertEquals(2, f.wire.size)
        }
    }
}

/** Only loses the outer ACK after a real SQLite COMMIT; never fabricates stored evidence. */
private class ReceiptAckFaults(private val db: Database) : Database by db {
    var armed = false
    var blockReads = false
    override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> =
        if(blockReads && request.statement.contains("FROM catalog_source_receipt WHERE")) Outcome.Unknown("Synthetic lost receipt read", SnapshotList.empty())
        else db.query(request, cancel)
    override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> {
        var receipt = false
        val result = db.transaction({ tx -> action(object : DbTransaction by tx {
            override suspend fun execute(request: SqlRequest, cancel: CancelToken): Outcome<DbWriteResult> {
                if(request.statement.startsWith("INSERT INTO catalog_source_receipt")) receipt = true
                return tx.execute(request, cancel)
            }
        }) }, cancel)
        if(armed && receipt && result is Outcome.Success) { armed = false; blockReads = true; return Outcome.Unknown("Synthetic lost receipt ACK", SnapshotList.empty()) }
        return result
    }
}
