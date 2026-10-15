package com.architectpsg.commandlibrary

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.common.ConsentCoordinator
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.StorageDatabase
import com.architectpsg.commandlibrary.di.AppEnglishCatalogAccess
import com.architectpsg.commandlibrary.feature.settings.DataStoreSettingsRepository
import com.architectpsg.commandlibrary.feature.catalog.api.AndroidCatalogServices
import com.architectpsg.commandlibrary.feature.catalog.LocalCatalog
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual factory/guard/Room/DataStore. No synthetic transport and no approved network transfer. */
@RunWith(AndroidJUnit4::class)
abstract class EnglishCatalogAccessCases {
    private val token = object : CancelToken {
        override val isCancelled = false
        override fun throwIfCancelled() = Unit
    }
    private fun <T> ok(result: Outcome<T>): T {
        assertTrue("Expected success: $result", result is Outcome.Success)
        return (result as Outcome.Success).value
    }
    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val name = "english-access-${UUID.randomUUID()}.db"
        val root = File(app.cacheDir, "english-access-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = File(root, "files").apply { check(isDirectory || mkdirs()) }
        }
        val db = ok(StorageDatabase.open(app, name))
        val consent = ConsentCoordinator()
        val settings = DataStoreSettingsRepository.create(File(root, "settings.pb"), File(root, "installation"), consent)
        val f = Fixture(context, db, settings, consent)
        try { block(f) } finally {
            f.accesses.forEach { it.close() }; settings.close(); db.close()
            app.deleteDatabase(name); root.deleteRecursively()
        }
    }
    private class Fixture(val context: Context, val db: StorageDatabase, val settings: DataStoreSettingsRepository, val consent: ConsentCoordinator) {
        val accesses = mutableListOf<AppEnglishCatalogAccess>()
        fun access(database: Database = db): AppEnglishCatalogAccess = AppEnglishCatalogAccess(context, database, settings, consent).also { accesses += it }
    }

    @Test fun actualApplicationExposesLazyDownloadAccessAlongsideCachedOfflinePorts() = runBlocking<Unit> {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CommandLibraryApplication
        val ports = ok(app.graph.ports())
        assertNotNull(ports.englishCatalog); assertNotNull(ports.playerProfiles); assertNotNull(ports.rulings); assertNotNull(ports.catalogPrintings); assertNotNull(ports.cardLinks)
        assertSame(ports.englishCatalog, ports.rulingsDownloads)
        assertSame(ports, ok(app.graph.ports()))
        // Opening offline ports does not open acquisition, plan a job or grant download consent.
        assertSame(ports.englishCatalog, ok(app.graph.ports()).englishCatalog)
    }

    @Test fun pureLocalSearchPreparationUsesActualRoom9WithoutOpeningAcquisition() = runBlocking<Unit> {
        fixture { f ->
            val clock = object : Clock {
                override fun utcNow() = UtcTimestamp(3)
                override fun monotonicNow() = kotlin.time.Duration.ZERO
            }
            var next = 0
            val ids = object : IdSource { override fun nextId() = "index-probe-${++next}" }
            val raw = """[{"object":"card","id":"printing","oracle_id":"oracle","lang":"en","name":"Synthetic","set":"tst","collector_number":"1","keywords":["Flying"],"scryfall_uri":"https://scryfall.com/card/tst/1/synthetic?utm_source=fixture","oracle_text":"Draw a card.","layout":"normal"}]""".toByteArray(Charsets.UTF_8)
            val source = CatalogImportSource(ResourceUri("content://selected/index-probe"), "Synthetic", ProviderId("scryfall"), LanguageTag.ENGLISH,
                Knowledge.Unknown("Date not supplied", SnapshotList.empty()), Knowledge.Absent("Source not supplied"), Knowledge.Absent("Hash not supplied"),
                Knowledge.Known(raw.size.toLong()), Knowledge.Absent("Notice not supplied"))
            val installed = ok(LocalCatalog(f.db, clock, ids).`import`(source, flowOf(ByteChunk(0, 0, SnapshotBytes(raw), null, true)), {}, token))
            val generation = installed.generation.id
            val printingReader = AndroidCatalogServices.localPrintings(f.db)
            val printingPage = ok(printingReader.printings(OracleId(ProviderId("scryfall"), "oracle"), PageRequest(10), token))
            assertEquals(generation, printingPage.snapshot.generation)
            assertEquals(CardId(ProviderId("scryfall"), "printing"), printingPage.items.single().id)
            assertEquals(printingPage.items.single(), ok(AndroidCatalogServices.localPrintings(f.db).printing(printingPage.items.single().id, token)))
            val importedCard = ok(LocalCatalog(f.db, clock, ids).lookupName("Synthetic", PageRequest(10), token)).items.single()
            val destination = LinkDestination(EntityId("scryfall"), "Scryfall", SnapshotList.of(Origin("https", "scryfall.com", 443)), true)
            val link = ok(com.architectpsg.commandlibrary.feature.links.LocalCardLinks().resolve(importedCard, printingPage.items.single(),
                LinkPreferences(SnapshotList.of(destination), SnapshotList.empty(), true, false))).single()
            assertEquals("https://scryfall.com/card/tst/1/synthetic?utm_source=fixture", link.uri.value)
            assertEquals(printingPage.items.single().id, link.selectedPrinting)
            val preparation = AndroidCatalogServices.localSearchPreparation(f.db, clock)
            assertEquals(CatalogSearchPreparationReadiness.NOT_PREPARED, ok(preparation.status(generation, token)).readiness)
            var state = ok(preparation.advance(generation, token))
            var steps = 1
            while (state.readiness == CatalogSearchPreparationReadiness.PREPARING && steps++ < 32)
                state = ok(preparation.advance(generation, token))
            assertEquals(CatalogSearchPreparationReadiness.READY, state.readiness)
            assertEquals(1L, state.processed.cards); assertEquals(1L, state.processed.printings)
            assertEquals(state, ok(AndroidCatalogServices.localSearchPreparation(f.db, clock).status(generation, token)))
            assertFalse(File(f.context.filesDir, "acquisition-artifacts").exists())
            assertTrue(ok(f.settings.read(token)).consents.isEmpty())
            val jobs = ok(f.db.query(SqlRequest("SELECT count(*) AS n FROM acquisition_job", SnapshotList.empty(), 1), token)).single()
            assertEquals(SqlValue.Integer(0), jobs.columns.single().value)
        }
    }

    @Test fun offlineRulingsAndMixedFamilyOpensShareOneLazyOwnerAndRecoveryBoundary() = runBlocking<Unit> {
        fixture { f ->
            val offline = AndroidCatalogServices.localRulings(f.db)
            assertNull(ok(offline.status(token)).active)
            assertFalse(File(f.context.filesDir, "acquisition-artifacts").exists())
            val access = f.access()
            val (english, rulings) = coroutineScope {
                val first = async(Dispatchers.IO) { ok(access.open(token)) }
                val second = async(Dispatchers.IO) { ok(access.openRulings(token)) }
                first.await() to second.await()
            }
            assertSame(english.jobs, rulings.jobs); assertSame(english.runner, rulings.runner)
            assertSame(rulings, ok(access.openRulings(token))); assertSame(english, ok(access.open(token)))
            assertTrue(ok(f.settings.read(token)).consents.isEmpty())
            val discovery = rulings.discoveryRequest("Ruling metadata probe", true)
            assertNull(discovery.intent.language)
            val planned = ok(rulings.jobs.plan(discovery, token))
            assertFalse(rulings.fileRequest(planned.plan.jobId, "Ruling file probe", true, token) is Outcome.Success)
            assertFalse(rulings.install(planned.plan.jobId, {}, token) is Outcome.Success)
            ok(f.db.transaction({ tx ->
                ok(tx.execute(SqlRequest("UPDATE acquisition_job SET state='RUNNING',owner_token='synthetic-orphan',revision=revision+1 WHERE job_id=:id",
                    SnapshotList.of(SqlParameter("id", SqlValue.Text(planned.plan.jobId.value)))), token)); Unit
            }, token))
            val before = ok(rulings.jobs.status(planned.plan.jobId, token))
            assertEquals(AcquisitionState.RUNNING, before.state)
            assertSame(english, ok(access.open(token))); assertSame(rulings, ok(access.openRulings(token)))
            assertEquals(before, ok(english.jobs.status(planned.plan.jobId, token)))
            access.close()
            assertTrue(access.open(token) is Outcome.Unavailable); assertTrue(access.openRulings(token) is Outcome.Unavailable)
            val next = f.access(); val nextRulings = ok(next.openRulings(token))
            val recovered = ok(nextRulings.jobs.status(planned.plan.jobId, token))
            assertEquals(AcquisitionState.INTERRUPTED, recovered.state); assertEquals(before.revision.next(), recovered.revision)
            assertSame(nextRulings.jobs, ok(next.open(token)).jobs)
            assertEquals(recovered, ok(nextRulings.jobs.status(planned.plan.jobId, token)))
            assertNull(ok(offline.status(token)).active); assertTrue(ok(f.settings.read(token)).consents.isEmpty())
        }
    }

    @Test fun cancelledOrInconclusiveRecoveryCannotPublishAHalfSessionAndOtherFamilyCanRetry() = runBlocking<Unit> {
        fixture { f ->
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            var block = true
            val database = object : Database by f.db {
                override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> {
                    if(block && request.statement.contains("FROM acquisition_job")) { block = false; entered.complete(Unit); release.await() }
                    return f.db.query(request, cancel)
                }
            }
            val access = f.access(database)
            coroutineScope {
                val initializing = async(Dispatchers.IO) { access.openRulings(token) }
                withTimeout(5_000) { entered.await() }
                initializing.cancelAndJoin()
            }
            assertTrue(ok(f.settings.read(token)).consents.isEmpty())
            val english = ok(access.open(token)); assertSame(english.jobs, ok(access.openRulings(token)).jobs)
        }
        fixture { f ->
            var fail = true
            val database = object : Database by f.db {
                override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> {
                    if(fail && request.statement.contains("FROM acquisition_job")) { fail = false; return Outcome.Unknown("Synthetic recovery unavailable", SnapshotList.empty()) }
                    return f.db.query(request, cancel)
                }
            }
            val access = f.access(database)
            // Existing engine maps an inconclusive durable read to CORRUPT_DATA, never success.
            val rejected = access.openRulings(token)
            assertEquals(FailureCode.CORRUPT_DATA, (rejected as Outcome.Failure).error.code)
            val english = ok(access.open(token)); assertSame(english.jobs, ok(access.openRulings(token)).jobs)
            assertTrue(ok(f.settings.read(token)).consents.isEmpty())
        }
    }

    @Test fun realSessionCachesAfterRecoveryAndNeverRecoversExistingWorkersOnReopen() = runBlocking<Unit> {
        fixture { f ->
            val access = f.access()
            val session = coroutineScope {
                val opens = (1..8).map { async(Dispatchers.IO) { ok(access.open(token)) } }
                opens.map { it.await() }.also { rows -> rows.forEach { assertSame(rows.first(), it) } }.first()
            }
            val planned = ok(session.jobs.plan(session.request("English probe", true), token))
            // A real persisted orphan simulates a prior worker; cached open must not alter it.
            ok(f.db.transaction({ tx ->
                ok(tx.execute(SqlRequest("UPDATE acquisition_job SET state='RUNNING',owner_token='synthetic-orphan',revision=revision+1 WHERE job_id=:id",
                    SnapshotList.of(SqlParameter("id", SqlValue.Text(planned.plan.jobId.value)))), token))
                Unit
            }, token))
            val before = ok(session.jobs.status(planned.plan.jobId, token))
            assertEquals(AcquisitionState.RUNNING, before.state)
            assertSame(session, ok(access.open(token))); assertEquals(before, ok(session.jobs.status(planned.plan.jobId, token)))
            access.close()
            val reopened = ok(f.access().open(token))
            val recovered = ok(reopened.jobs.status(planned.plan.jobId, token))
            assertEquals(AcquisitionState.INTERRUPTED, recovered.state); assertEquals(before.revision.next(), recovered.revision)
        }
    }

    @Test fun unapprovedStartAndUnverifiedInstallCannotCreateConsentOrInstallCatalog() = runBlocking<Unit> {
        fixture { f ->
            val session = ok(f.access().open(token))
            val request = session.request("English probe", true)
            assertEquals(LanguageTag.ENGLISH, request.intent.language); assertEquals(ProviderId("scryfall"), request.intent.source)
            assertTrue(ok(f.settings.read(token)).consents.isEmpty())
            val planned = ok(session.jobs.plan(request, token))
            val denied = session.jobs.start(planned.plan.jobId, planned.revision, EntityId("not-approved"), token)
            assertTrue(denied is Outcome.Unavailable)
            assertEquals(UnavailableReason.CONSENT_REQUIRED, (denied as Outcome.Unavailable).reason)
            assertEquals(planned, ok(session.jobs.status(planned.plan.jobId, token)))
            assertTrue(ok(f.settings.read(token)).consents.isEmpty())
            var progress = 0
            assertFalse(session.install(planned.plan.jobId, { progress++ }, token) is Outcome.Success)
            assertEquals(0, progress)
            val rows = ok(f.db.query(SqlRequest("SELECT generation_id FROM catalog_active", SnapshotList.empty()), token))
            assertTrue(rows.isEmpty())
        }
    }

    @Test fun cancelledAndFailedInitializationAreNotCachedAndOwnedCloseIsFinal() = runBlocking<Unit> {
        fixture { f ->
            val access = f.access()
            val cancelled = object : CancelToken {
                override val isCancelled = true
                override fun throwIfCancelled() { throw CancellationException("Synthetic caller cancellation") }
            }
            try { access.open(cancelled); fail("Cancelled caller must not initialize access") } catch (_: CancellationException) { }
            try { access.openRulings(cancelled); fail("Cancelled ruling caller must not initialize access") } catch (_: CancellationException) { }
            val child = File(f.context.filesDir, "acquisition-artifacts")
            assertFalse(child.exists()); child.writeText("Synthetic non-directory")
            assertTrue(access.open(token) is Outcome.Failure)
            assertTrue(access.openRulings(token) is Outcome.Failure)
            assertTrue(child.delete())
            assertNotNull(ok(access.openRulings(token))); assertNotNull(ok(access.open(token)))
            access.close(); assertTrue(access.open(token) is Outcome.Unavailable); assertTrue(access.openRulings(token) is Outcome.Unavailable)
            // This access never owns or closes these shared stores.
            assertTrue(ok(f.settings.read(token)).consents.isEmpty())
            assertNotNull(ok(f.db.query(SqlRequest("SELECT 1", SnapshotList.empty()), token)))
        }
    }
}
