package com.architectpsg.commandlibrary.feature.catalog

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.common.ConsentCoordinator
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.StorageDatabase
import com.architectpsg.commandlibrary.feature.settings.DataStoreSettingsRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration

/** Synthetic producer bytes, real completed acquisition, private provider, settings and SQLite. */
@RunWith(AndroidJUnit4::class)
class AndroidCatalogVerifiedInputBindingTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val token = BULK_NO_CANCEL
    private val card = """{"object":"card","id":"synthetic-print","oracle_id":"synthetic-oracle","lang":"en","name":"Synthetic","set":"tst","collector_number":"007a","keywords":[],"layout":"normal"}"""
    private val page = """{"object":"list","total_cards":1,"has_more":false,"data":[$card]}""".toByteArray()
    private fun <T> value(result: Outcome<T>): T {
        assertTrue("Expected success: $result", result is Outcome.Success)
        return (result as Outcome.Success).value
    }
    private suspend fun mismatch(block: suspend () -> Unit) {
        try { block(); fail("Expected source mismatch") }
        catch (error: ArtifactReadException) { assertEquals(FailureCode.SOURCE_MISMATCH, error.failure.code) }
    }

    /** Observes the real engine's successful job revalidation immediately before its file-guard wait. */
    private class RemovalRevalidationDatabase(private val real: StorageDatabase) : Database by real {
        @Volatile private var watchedJob: EntityId? = null
        private val revalidated = CompletableDeferred<Unit>()
        fun watch(jobId: EntityId): Deferred<Unit> { watchedJob = jobId; return revalidated }
        override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> {
            val result = real.query(request, cancel)
            val jobId = watchedJob
            if (jobId != null && result is Outcome.Success && result.value.size == 1 &&
                request.statement.endsWith(" WHERE j.job_id = :id") &&
                request.parameters.any { it.name == "id" && it.value == SqlValue.Text(jobId.value) })
                revalidated.complete(Unit)
            return result
        }
    }

    private inner class Fixture(val db: StorageDatabase, val directory: File, val settings: DataStoreSettingsRepository, val gate: ConsentCoordinator) {
        private val removalDatabase = RemovalRevalidationDatabase(db)
        fun watchRemovalRevalidation(jobId: EntityId): Deferred<Unit> = removalDatabase.watch(jobId)
        val clock = object : Clock {
            override fun utcNow() = UtcTimestamp(1000)
            override fun monotonicNow(): Duration = Duration.ZERO
        }
        val ids = object : IdSource { override fun nextId() = UUID.randomUUID().toString() }
        val request = TrustedEnglishSearchProfile.request("Synthetic English printing", false)
        val engine = LocalAcquisition(removalDatabase, object : NetworkClient {
            override suspend fun send(request: RequestSpec, cancel: CancelToken): Outcome<ByteResponse> = error("Only streaming is used")
            override fun stream(request: RequestSpec, cancel: CancelToken): Flow<Outcome<ByteChunk>> = flowOf(Outcome.Success(
                ByteChunk(0, 0, SnapshotBytes(page), HttpMetadata(200,
                    SnapshotList.of(HttpHeader("Content-Length", page.size.toString())), request.request.uri,
                    UtcTimestamp(1000), Knowledge.Absent("Synthetic"), Knowledge.Known(false)), true)))
            override suspend fun close() = Unit
        }, settings, gate, clock, ids, androidArtifactFiles(directory), TrustedEnglishSearchProfile::validate,
            TrustedEnglishSearchProfile::endpointFor, { AcquisitionConnection(true, true) })
        val binding = AndroidCatalogVerifiedInputBinding(context, engine)
        suspend fun completed(): AcquisitionSnapshot {
            val planned = value(engine.plan(request, token))
            val consent = value(settings.approve(request.intent, token))
            val queued = value(engine.start(planned.plan.jobId, planned.revision, consent.id, token))
            return value(engine.run(queued.plan.jobId, token)).also { assertEquals(AcquisitionState.COMPLETED, it.state) }
        }
        fun importer() = LocalBulkCatalog(db, clock, ids, binding)
    }

    private suspend fun fixture(action: suspend (Fixture) -> Unit) = withContext(Dispatchers.IO) {
        val name = "catalog-binding-${UUID.randomUUID()}.db"
        val dir = File(context.filesDir, "catalog-binding-${UUID.randomUUID()}")
        val store = File(context.filesDir, "catalog-binding-settings-${UUID.randomUUID()}")
        val installation = File(context.noBackupFilesDir, "catalog-binding-installation-${UUID.randomUUID()}")
        val gate = ConsentCoordinator()
        val settings = DataStoreSettingsRepository.create(store, installation, gate)
        val db = value(StorageDatabase.open(context, name))
        try { action(Fixture(db, dir, settings, gate)) }
        finally { withContext(NonCancellable) { settings.close(); db.close(); context.deleteDatabase(name); dir.deleteRecursively(); store.delete(); installation.delete() } }
    }

    @Test fun genuineSourceAndImportUsePinnedBytesAndCompletedConsent(): Unit = runBlocking {
        fixture { f ->
            val completed = f.completed()
            val source = value(f.binding.catalogSource(completed.plan.jobId, token))
            val uri = Uri.parse(source.contentUri.value)
            assertEquals(SourceInputKind.VERIFIED_ARTIFACT, source.inputKind)
            assertEquals((completed.artifact as Knowledge.Known).value, source.verifiedArtifact)
            assertNull(context.contentResolver.getType(uri))
            try { context.contentResolver.openFileDescriptor(uri, "r")?.close(); fail("URI escaped pin") }
            catch (_: java.io.FileNotFoundException) { }
            val installed = value(f.importer().`import`(source, flow { error("Caller chunks consumed") }, {}, token))
            assertEquals(1L, installed.installed.counts.printings)
            assertEquals(completed.consentId?.value, f.db.query(bulkSql("SELECT source_payload FROM catalog_generation WHERE status='ACTIVE'", 1), token).bulkValue().single().bulkText("source_payload").let { BulkSourceEnvelope.decode(it).consentId?.value })
            assertEquals("synthetic-print", value(LocalCatalogIdentities(f.db).printing(CardId(ProviderId("scryfall"), "synthetic-print"), token)).id.raw)
            assertNull(context.contentResolver.getType(uri))
        }
    }

    @Test fun forgedFullSourceUriDescriptorAndUnfinishedOrMissingArtifactRejectBeforeConsume(): Unit = runBlocking {
        fixture { f ->
            val planned = value(f.engine.plan(f.request, token))
            assertEquals(FailureCode.CORRUPT_DATA, (f.binding.catalogSource(planned.plan.jobId, token) as Outcome.Failure).error.code)
            val completed = f.completed()
            val source = value(f.binding.catalogSource(completed.plan.jobId, token))
            suspend fun rejects(candidate: CatalogBulkImportSource) {
                var called = false
                mismatch { f.binding.withPinnedInput(candidate, token) { _, _ -> called = true; error("Forged source consumed") } }
                assertFalse(called)
                assertNull(context.contentResolver.getType(Uri.parse(source.contentUri.value)))
            }
            rejects(source.copy(displayName = "forged"))
            rejects(source.copy(contentUri = ResourceUri(source.contentUri.value + "?forged=1")))
            val descriptor = requireNotNull(source.verifiedArtifact)
            rejects(source.copy(verifiedArtifact = descriptor.copy(pageCount = descriptor.pageCount + 1)))
            f.engine.withArtifactFile(completed.plan.jobId, token) { file, _, _ -> file.delete() }
            assertEquals(FailureCode.CORRUPT_DATA, (f.binding.catalogSource(completed.plan.jobId, token) as Outcome.Failure).error.code)
            assertNull(context.contentResolver.getType(Uri.parse(source.contentUri.value)))
            val cancelled = object : CancelToken {
                override val isCancelled = true
                override fun throwIfCancelled(): Unit = throw CancellationException("Source cancelled")
            }
            try { f.binding.catalogSource(completed.plan.jobId, cancelled); fail("Cancellation swallowed") }
            catch (_: CancellationException) { }
        }
    }

    /** Delivers an ambiguous transaction result only after real SQLite COMMIT, then gates its readback. */
    private class AmbiguousAcknowledgementDatabase(private val real: StorageDatabase) : Database {
        val committed = CompletableDeferred<Unit>()
        val readingAcknowledgement = CompletableDeferred<Unit>()
        val releaseAcknowledgement = CompletableDeferred<Unit>()
        override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> {
            if (request.statement.startsWith("SELECT g.*,(SELECT generation_id FROM catalog_active")) {
                readingAcknowledgement.complete(Unit)
                releaseAcknowledgement.await()
            }
            return real.query(request, cancel)
        }
        override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> {
            var pointerChanged = false
            val result = real.transaction({ transaction ->
                action(object : DbTransaction by transaction {
                    override suspend fun execute(request: SqlRequest, cancel: CancelToken): Outcome<DbWriteResult> {
                        val write = transaction.execute(request, cancel)
                        if (write is Outcome.Success && (request.statement.startsWith("INSERT INTO catalog_active(") ||
                                request.statement.startsWith("UPDATE catalog_active SET generation_id="))) pointerChanged = true
                        return write
                    }
                })
            }, cancel)
            if (pointerChanged && result is Outcome.Success) {
                committed.complete(Unit)
                throw CancellationException("Injected after durable catalog COMMIT")
            }
            return result
        }
        override suspend fun capabilities(cancel: CancelToken): Outcome<DbCapabilities> = real.capabilities(cancel)
    }

    @Test fun committedActivationReconcilesUnderPinAndSurvivesRawDeletion(): Unit = runBlocking {
        withTimeout(15_000) { fixture { f ->
            val completed = f.completed()
            val source = value(f.binding.catalogSource(completed.plan.jobId, token))
            val gated = AmbiguousAcknowledgementDatabase(f.db)
            val importing = async { LocalBulkCatalog(gated, f.clock, f.ids, f.binding).`import`(source,
                flow { error("Caller chunks consumed") }, {}, token) }
            gated.committed.await()
            gated.readingAcknowledgement.await()
            val revalidated = f.watchRemovalRevalidation(completed.plan.jobId)
            val removing = async { f.engine.removeArtifacts(completed.consentId!!, token) }
            revalidated.await()
            assertFalse("Raw removal acknowledged during pinned COMMIT reconciliation", removing.isCompleted)
            gated.releaseAcknowledgement.complete(Unit)
            val installed = value(importing.await())
            value(removing.await())
            assertEquals(1L, installed.installed.counts.printings)
            assertEquals("synthetic-print", value(LocalCatalogIdentities(f.db).printing(
                CardId(ProviderId("scryfall"), "synthetic-print"), token)).id.raw)
            assertNull(context.contentResolver.getType(Uri.parse(source.contentUri.value)))
        } }
    }

    @Test fun eofBlockedCallbackKeepsRemovalPendingUntilPinExits(): Unit = runBlocking {
        withTimeout(10_000) { fixture { f ->
            val completed = f.completed()
            val source = value(f.binding.catalogSource(completed.plan.jobId, token))
            val eof = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val reading = async {
                try { f.binding.withPinnedInput(source, token) { chunks, _ ->
                    chunks.toList(); eof.complete(Unit); release.await()
                    error("Intentional callback exit after verified EOF")
                } } catch (expected: IllegalStateException) {
                    assertEquals("Intentional callback exit after verified EOF", expected.message)
                }
            }
            eof.await()
            val revalidated = f.watchRemovalRevalidation(completed.plan.jobId)
            val removal = async { f.engine.removeArtifacts(completed.consentId!!, token) }
            revalidated.await()
            assertFalse(removal.isCompleted)
            release.complete(Unit); reading.await(); value(removal.await())
            assertNull(context.contentResolver.getType(Uri.parse(source.contentUri.value)))
        } }
    }
}
