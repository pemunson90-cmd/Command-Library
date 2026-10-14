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
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
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
        fun access(): AppEnglishCatalogAccess = AppEnglishCatalogAccess(context, db, settings, consent).also { accesses += it }
    }

    @Test fun actualApplicationExposesLazyDownloadAccessAlongsideCachedOfflinePorts() = runBlocking<Unit> {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CommandLibraryApplication
        val ports = ok(app.graph.ports())
        assertNotNull(ports.englishCatalog); assertNotNull(ports.playerProfiles)
        assertSame(ports, ok(app.graph.ports()))
        // Opening offline ports does not open acquisition, plan a job or grant download consent.
        assertSame(ports.englishCatalog, ok(app.graph.ports()).englishCatalog)
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
            val child = File(f.context.filesDir, "acquisition-artifacts")
            assertFalse(child.exists()); child.writeText("Synthetic non-directory")
            assertTrue(access.open(token) is Outcome.Failure)
            assertTrue(child.delete())
            assertNotNull(ok(access.open(token)))
            access.close(); assertTrue(access.open(token) is Outcome.Unavailable)
            // This access never owns or closes these shared stores.
            assertTrue(ok(f.settings.read(token)).consents.isEmpty())
            assertNotNull(ok(f.db.query(SqlRequest("SELECT 1", SnapshotList.empty()), token)))
        }
    }
}
