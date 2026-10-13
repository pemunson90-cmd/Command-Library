package com.architectpsg.commandlibrary.feature.catalog

import android.annotation.TargetApi
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.os.CancellationSignal
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.common.ConsentCoordinator
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.StorageDatabase
import com.architectpsg.commandlibrary.feature.settings.DataStoreSettingsRepository
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration

/** All bodies/authority profiles are synthetic and confined to this real-storage native fixture. */
@RunWith(AndroidJUnit4::class)
class AndroidArtifactLocatorTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val token = object : CancelToken {
        override val isCancelled = false
        override fun throwIfCancelled() = Unit
    }
    private val payload = "synthetic exact approved artifact\n".toByteArray()
    private val authority get() = context.packageName + ".acquisition.artifact"
    private fun <T> value(outcome: Outcome<T>): T {
        assertTrue("Expected success: $outcome", outcome is Outcome.Success)
        return (outcome as Outcome.Success).value
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun rejected(action: () -> Unit) {
        try { action(); fail("Expected rejected admission") }
        catch (_: java.io.FileNotFoundException) { }
        catch (_: SecurityException) { }
        catch (_: IllegalArgumentException) { }
        catch (_: UnsupportedOperationException) { }
    }
    private inner class Fixture(val db: StorageDatabase, val name: String, val directory: File, val settings: DataStoreSettingsRepository, val gate: ConsentCoordinator) {
        val clock = object : Clock {
            override fun utcNow() = UtcTimestamp(1000)
            override fun monotonicNow(): Duration = Duration.ZERO
        }
        val ids = object : IdSource { override fun nextId() = UUID.randomUUID().toString() }
        val request: AcquisitionRequest = run {
            val uri = ResourceUri("https://example.invalid/approved-artifact.txt")
            val checksum = ContentHash("SHA-256", hash(payload))
            AcquisitionRequest(
                DownloadIntent(EntityId("synthetic-approved"), DownloadKind.RULES, ProviderId("synthetic"), uri,
                    "synthetic-v1", LanguageTag.ENGLISH, null, checksum, Knowledge.Known(payload.size.toLong())),
                "Synthetic artifact", AcquisitionSource.RemoteFile(uri, SnapshotList.empty(), ArtifactFraming.TEXT,
                    ArtifactCompression.NONE, Knowledge.Known(payload.size.toLong()), Knowledge.Known(checksum)),
                AcquisitionLanguageScope.ENGLISH_RULES, Knowledge.Known("synthetic-v1"), Knowledge.Absent("Synthetic"),
                Knowledge.Absent("Synthetic"), AcquisitionLimits("synthetic-v1", 1024, 1024, 1024, 10, 1, 1024, 1024, 1024, 8), false,
            )
        }
        val files = androidArtifactFiles(directory)
        fun engine(): LocalAcquisition = LocalAcquisition(db, object : NetworkClient {
            override suspend fun send(request: RequestSpec, cancel: CancelToken): Outcome<ByteResponse> = error("Fixture streams only")
            override fun stream(request: RequestSpec, cancel: CancelToken): Flow<Outcome<ByteChunk>> = flowOf(Outcome.Success(
                ByteChunk(0, 0, SnapshotBytes(payload), HttpMetadata(200,
                    SnapshotList.of(HttpHeader("Content-Length", payload.size.toString()), HttpHeader("ETag", "\"synthetic\"")),
                    request.request.uri, UtcTimestamp(1000), Knowledge.Absent("Synthetic"), Knowledge.Known(false)), true),
            ))
            override suspend fun close() = Unit
        }, settings, gate, clock, ids, files,
            { supplied -> if (supplied == request) Outcome.Success(Unit) else Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT, "Fixture profile rejected", SnapshotList.empty())) },
            { _, _ -> EndpointProfile(EntityId("synthetic"), Origin("https", "example.invalid", 443), SnapshotList.empty(), null, 1,
                RetryPolicy(1, Duration.ZERO, Duration.ZERO, true)) }, { AcquisitionConnection(true, true) })
        val engine = engine()
        val locator = AndroidVerifiedArtifactLocator(context, engine)
        suspend fun completed(): AcquisitionSnapshot {
            val planned = value(engine.plan(request, token))
            val approval = value(settings.approve(request.intent, token))
            val queued = value(engine.start(planned.plan.jobId, planned.revision, approval.id, token))
            return value(engine.run(queued.plan.jobId, token)).also { assertEquals(AcquisitionState.COMPLETED, it.state) }
        }
    }
    private suspend fun fixture(action: suspend (Fixture) -> Unit) = withContext(Dispatchers.IO) {
        val name = "artifact-locator-${UUID.randomUUID()}.db"
        val dir = File(context.filesDir, "artifact-locator-${UUID.randomUUID()}")
        val store = File(context.filesDir, "artifact-settings-${UUID.randomUUID()}")
        val installation = File(context.noBackupFilesDir, "artifact-installation-${UUID.randomUUID()}")
        val gate = ConsentCoordinator()
        val settings = DataStoreSettingsRepository.create(store, installation, gate)
        val db = value(StorageDatabase.open(context, name))
        try { action(Fixture(db, name, dir, settings, gate)) }
        finally { withContext(NonCancellable) { settings.close(); db.close(); context.deleteDatabase(name); dir.deleteRecursively(); store.delete(); installation.delete() } }
    }
    private fun provider(uri: Uri): AcquisitionArtifactProvider {
        val client = context.contentResolver.acquireContentProviderClient(uri)!!
        try { return client.localContentProvider as AcquisitionArtifactProvider }
        finally { @Suppress("DEPRECATION") client.release() } // close()/AutoCloseable starts at API24.
    }
    private fun resolverBytes(uri: Uri): ByteArray = context.contentResolver.openFileDescriptor(uri, "r")!!.use { descriptor ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Api30.assertCloseOnExec(descriptor.fileDescriptor)
        // API23–29 exercise the same bytes/lifetime without a final-FD CLOEXEC guarantee.
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }
    @TargetApi(30)
    private object Api30 {
        fun assertCloseOnExec(descriptor: java.io.FileDescriptor) {
            val flags = Os.fcntlInt(descriptor, OsConstants.F_GETFD, 0)
            assertTrue("Actual resolver-returned descriptor must be close-on-exec", flags and OsConstants.FD_CLOEXEC != 0)
        }
    }

    @Test fun actualResolverTypedAndReadRoutesHaveExactBytesHashAndWholeFileMetadata(): Unit = runBlocking {
        fixture { f ->
            val completed = f.completed()
            var saved: Uri? = null
            f.locator.withPinnedArtifact(completed.plan.jobId, token) { resource, artifact, chunks ->
                val uri = Uri.parse(resource.value); saved = uri
                assertEquals(authority, uri.authority)
                assertArrayEquals(payload, resolverBytes(uri))
                assertEquals(artifact.sha256.hex, hash(resolverBytes(uri)))
                context.contentResolver.openTypedAssetFileDescriptor(uri, "*/*", null)!!.use { asset ->
                    assertEquals(0L, asset.startOffset); assertEquals(AssetFileDescriptor.UNKNOWN_LENGTH, asset.declaredLength)
                    ParcelFileDescriptor.AutoCloseInputStream(asset.parcelFileDescriptor).use { assertArrayEquals(payload, it.readBytes()) }
                }
                context.contentResolver.query(uri, null, null, null, null)!!.use { cursor ->
                    assertTrue(cursor.moveToFirst()); assertEquals(uri.lastPathSegment, cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)))
                    assertEquals(payload.size.toLong(), cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)))
                }
                assertEquals("application/octet-stream", context.contentResolver.getType(uri))
                assertEquals(1, chunks.toList().count { it.final })
            }
            rejected { resolverBytes(saved!!) }
            rejected { context.contentResolver.getType(saved!!) }
            rejected { context.contentResolver.query(saved!!, null, null, null, null)?.close() }
            assertEquals(AcquisitionState.COMPLETED, value(f.engine.status(completed.plan.jobId, token)).state)
        }
    }

    @Test fun uncompletedPreparedPartialAndSentinelEntriesNeverHaveAdmission(): Unit = runBlocking {
        fixture { f ->
            val planned = value(f.engine.plan(f.request, token))
            var entered = false
            try { f.locator.withPinnedArtifact(planned.plan.jobId, token) { _, _, _ -> entered = true }; fail("Uncompleted pin") }
            catch (_: ArtifactReadException) { }
            assertFalse(entered)
            val completed = f.completed()
            for (name in listOf("p-unregistered-a1", "p-unregistered-a1-verified", "sync-sentinel")) File(f.directory, name).writeBytes(payload)
            f.locator.withPinnedArtifact(completed.plan.jobId, token) { _, _, _ ->
                for (name in listOf("p-unregistered-a1", "p-unregistered-a1-verified", "sync-sentinel")) {
                    rejected { resolverBytes(Uri.parse("content://$authority/completed/$name")) }
                }
            }
        }
    }

    @Test fun everyOverloadRejectsWritesWrongUrisAndConversions(): Unit = runBlocking {
        fixture { f ->
            val completed = f.completed()
            f.locator.withPinnedArtifact(completed.plan.jobId, token) { resource, _, _ ->
                val uri = Uri.parse(resource.value); val p = provider(uri)
                for (mode in listOf("w", "rw", "wa", "rwt", "rt", "R", "")) {
                    rejected { p.openFile(uri, mode)?.close() }; rejected { p.openFile(uri, mode, CancellationSignal())?.close() }
                    rejected { p.openAssetFile(uri, mode)?.close() }; rejected { p.openAssetFile(uri, mode, CancellationSignal())?.close() }
                }
                for (bad in listOf("$uri?x=1", "$uri#x", resource.value.replace(authority, "wrong.authority"),
                    resource.value.replace("/completed/", "/completed/%2e%2e/"), resource.value.replace("/completed/", "/completed/%2f"),
                    resource.value.replace("content://", "content://user@"), resource.value.replace("/completed/", "/completed//"),
                    resource.value.replace("/completed/", "/completed/%70"))) {
                    val malformed = Uri.parse(bad)
                    rejected { p.openFile(malformed, "r")?.close() }
                    rejected { p.openAssetFile(malformed, "r", CancellationSignal())?.close() }
                    rejected { p.openTypedAssetFile(malformed, "*/*", null)?.close() }
                    rejected { p.openTypedAssetFile(malformed, "*/*", null, CancellationSignal())?.close() }
                    rejected { p.getType(malformed) }; rejected { p.query(malformed, null, null, null, null)?.close() }
                }
                rejected { p.openTypedAssetFile(uri, "text/plain", null)?.close() }
                rejected { p.insert(uri, null) }; rejected { p.update(uri, null, null, null) }; rejected { p.delete(uri, null, null) }
                rejected { p.bulkInsert(uri, emptyArray()) }
                rejected { p.call("anything", null, null) }; rejected { p.applyBatch(arrayListOf()) }
                for (open in listOf<() -> ParcelFileDescriptor?>( { p.openFile(uri, "r") }, { p.openFile(uri, "r", null) })) open()!!.use { assertEquals(payload.size.toLong(), it.statSize) }
                p.openAssetFile(uri, "r")!!.use { assertEquals(-1L, it.declaredLength) }
                p.openAssetFile(uri, "r", null)!!.use { assertEquals(-1L, it.declaredLength) }
                p.openTypedAssetFile(uri, "*/*", null)!!.use { assertEquals(-1L, it.declaredLength) }
                p.openTypedAssetFile(uri, "*/*", null, null)!!.use { assertEquals(-1L, it.declaredLength) }
            }
        }
    }

    @Test fun substitutedSymlinkFifoAndSameLengthNewInodeFailWithoutBlocking(): Unit = runBlocking {
        withTimeout(10_000) { fixture { f ->
            val completed = f.completed()
            f.locator.withPinnedArtifact(completed.plan.jobId, token) { resource, _, _ ->
                val uri = Uri.parse(resource.value); val registered = File(f.directory, uri.lastPathSegment!!)
                val original = File(f.directory, "preserved-original")
                assertTrue(registered.renameTo(original))
                try {
                    Os.symlink(original.path, registered.path); rejected { resolverBytes(uri) }; assertTrue(registered.delete())
                    Os.mkfifo(registered.path, 384)
                    // A removed O_NONBLOCK must fail this assertion without hanging the test process.
                    val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
                    val fifoOpen = executor.submit<Unit> { rejected { resolverBytes(uri) } }
                    try { fifoOpen.get(2, java.util.concurrent.TimeUnit.SECONDS) }
                    finally {
                        if (!fifoOpen.isDone) {
                            // Lexically unblock/join a regressed blocking reader before leaving the pin.
                            val writer = Os.open(registered.path, android.system.OsConstants.O_WRONLY or android.system.OsConstants.O_NONBLOCK, 0)
                            Os.close(writer)
                            fifoOpen.get(2, java.util.concurrent.TimeUnit.SECONDS)
                        }
                        executor.shutdown()
                        assertTrue(executor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS))
                    }
                    assertTrue(registered.delete())
                    registered.writeBytes(payload); rejected { resolverBytes(uri) }; assertTrue(registered.delete())
                } finally { registered.delete(); assertTrue(original.renameTo(registered)) }
                assertArrayEquals(payload, resolverBytes(uri))
            }
        } }
    }

    @Test fun symlinkBeforeRebindingFailsDurableVerificationWithoutPublishing(): Unit = runBlocking {
        fixture { f ->
            val completed = f.completed(); var uri: Uri? = null
            f.locator.withPinnedArtifact(completed.plan.jobId, token) { resource, _, _ -> uri = Uri.parse(resource.value) }
            val registered = File(f.directory, uri!!.lastPathSegment!!); val original = File(f.directory, "preserved-original")
            assertTrue(registered.renameTo(original))
            try {
                Os.symlink(original.path, registered.path)
                var entered = false
                try { f.locator.withPinnedArtifact(completed.plan.jobId, token) { _, _, _ -> entered = true }; fail("Symlink admitted") }
                catch (_: Exception) { }
                assertFalse(entered); rejected { resolverBytes(uri!!) }
            } finally { registered.delete(); assertTrue(original.renameTo(registered)) }
        }
    }

    @Test fun registrationRejectsOutsidePrivateRootSymlinkParentsAndPublicPermissions(): Unit = runBlocking {
        fixture { f ->
            val completed = f.completed()
            val outside = File(context.cacheDir, "artifact-outside-${UUID.randomUUID()}").also { it.writeBytes(payload) }
            val link = File(context.filesDir, "artifact-link-${UUID.randomUUID()}")
            try {
                f.engine.withArtifactFile(completed.plan.jobId, token) { file, artifact, _ ->
                    Os.symlink(f.directory.path, link.path)
                    for (candidate in listOf(outside, File(link, file.name))) rejected {
                        val lease = ArtifactLocatorRegistry.register(context.filesDir, authority, candidate, artifact.bytes)
                        try { fail("Nonprivate registration") } finally { ArtifactLocatorRegistry.close(lease) }
                    }
                }
                var actual: File? = null
                f.engine.withArtifactFile(completed.plan.jobId, token) { file, _, _ -> actual = file }
                suspend fun pinMustReject() {
                    var entered = false
                    try { f.locator.withPinnedArtifact(completed.plan.jobId, token) { _, _, _ -> entered = true }; fail("Public permission registration") }
                    catch (_: java.io.FileNotFoundException) { }
                    assertFalse(entered)
                }
                val originalFileMode = Os.lstat(actual!!.path).st_mode and 511
                val originalDirectoryMode = Os.lstat(f.directory.path).st_mode and 511
                try {
                    Os.chmod(actual!!.path, 420); pinMustReject() // 0644 exposes reads to other UIDs.
                    Os.chmod(actual!!.path, originalFileMode)
                    Os.chmod(f.directory.path, 511); pinMustReject() // 0777 admits foreign writes/listing.
                } finally { Os.chmod(actual!!.path, originalFileMode); Os.chmod(f.directory.path, originalDirectoryMode) }
                f.locator.withPinnedArtifact(completed.plan.jobId, token) { resource, _, _ -> assertArrayEquals(payload, resolverBytes(Uri.parse(resource.value))) }
            } finally { link.delete(); outside.delete() }
        }
    }

    @Test fun cancellationSignalsAndCoroutineExitCloseTrackedDescriptors(): Unit = runBlocking {
        fixture { f ->
            val completed = f.completed(); var raw: ParcelFileDescriptor? = null; var saved: Uri? = null
            val entered = CompletableDeferred<Unit>()
            val owner = launch(Dispatchers.IO) {
                f.locator.withPinnedArtifact(completed.plan.jobId, token) { resource, _, _ ->
                    val uri = Uri.parse(resource.value); saved = uri; val p = provider(uri)
                    val cancelled = CancellationSignal().also { it.cancel() }
                    for (open in listOf<() -> Unit>({ p.openFile(uri, "r", cancelled)?.close() },
                        { p.openAssetFile(uri, "r", cancelled)?.close() }, { p.openTypedAssetFile(uri, "*/*", null, cancelled)?.close() },
                        { context.contentResolver.openFileDescriptor(uri, "r", cancelled)?.close() })) {
                        try { open(); fail("Cancelled admission") } catch (_: android.os.OperationCanceledException) { }
                    }
                    // Raw provider-created handle is retained to check lease-owned cleanup on cancellation.
                    raw = p.openFile(uri, "r")!!
                    ParcelFileDescriptor.AutoCloseInputStream(context.contentResolver.openFileDescriptor(uri, "r")!!).use {
                        currentCoroutineContext().ensureActive(); token.throwIfCancelled(); assertTrue(it.read() >= 0)
                        entered.complete(Unit); awaitCancellation()
                    }
                }
            }
            entered.await(); owner.cancelAndJoin()
            assertFalse(raw!!.fileDescriptor.valid()); raw!!.close(); rejected { resolverBytes(saved!!) }
        }
    }

    @Test fun tokenCancellationAfterAdmissionPropagatesAndInvalidates(): Unit = runBlocking {
        fixture { f ->
            val completed = f.completed(); var saved: Uri? = null
            val cancelled = object : CancelToken {
                var now = false
                override val isCancelled get() = now
                override fun throwIfCancelled() { if (now) throw CancellationException("Synthetic token") }
            }
            try {
                f.locator.withPinnedArtifact(completed.plan.jobId, cancelled) { resource, _, chunks ->
                    saved = Uri.parse(resource.value); cancelled.now = true; chunks.collect()
                }; fail("Token cancellation swallowed")
            } catch (_: CancellationException) { }
            rejected { resolverBytes(saved!!) }
        }
    }

    @Test fun normallyCommittedCallbackResultSurvivesLateTokenCancellationAndClosesLease(): Unit = runBlocking {
        fixture { f ->
            val completed = f.completed(); var saved: Uri? = null; var raw: ParcelFileDescriptor? = null
            val lateCancellation = object : CancelToken {
                var now = false
                override val isCancelled get() = now
                override fun throwIfCancelled() { if (now) throw CancellationException("Synthetic late token") }
            }
            val result = f.locator.withPinnedArtifact(completed.plan.jobId, lateCancellation) { resource, _, chunks ->
                saved = Uri.parse(resource.value)
                assertArrayEquals(payload, resolverBytes(saved!!)); assertTrue(chunks.toList().last().final)
                raw = provider(saved!!).openFile(saved!!, "r") // Registry-owned raw handle verifies finally cleanup.
                val committed = f.db.transaction({ tx ->
                    value(tx.execute(SqlRequest("CREATE TABLE locator_callback_result (id INTEGER PRIMARY KEY)", SnapshotList.empty()), token))
                    value(tx.execute(SqlRequest("INSERT INTO locator_callback_result (id) VALUES (1)", SnapshotList.empty()), token))
                }, token)
                assertEquals(Outcome.Success(DbWriteResult(1)), committed)
                lateCancellation.now = true
                committed
            }
            assertEquals(Outcome.Success(DbWriteResult(1)), result)
            assertTrue(lateCancellation.isCancelled)
            assertFalse(raw!!.fileDescriptor.valid()); raw!!.close(); rejected { resolverBytes(saved!!) }
            val committedRows = value(f.db.query(SqlRequest("SELECT id FROM locator_callback_result", SnapshotList.empty()), token))
            assertEquals(SqlValue.Integer(1), committedRows.single().columns.single().value)
        }
    }

    @Test fun openVersusLeaseExitSerializesAndClosesEveryAdmittedHandle(): Unit = runBlocking {
        fixture { f ->
            val completed = f.completed(); var saved: Uri? = null
            val handles = java.util.Collections.synchronizedList(mutableListOf<ParcelFileDescriptor>())
            val opened = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); val start = CompletableDeferred<Uri>()
            val owner = launch(Dispatchers.IO) {
                f.locator.withPinnedArtifact(completed.plan.jobId, token) { resource, _, _ ->
                    saved = Uri.parse(resource.value); start.complete(saved!!); release.await()
                }
            }
            val uri = start.await(); val p = provider(uri)
            val opener = async(Dispatchers.IO) {
                handles += p.openFile(uri, "r")!!; opened.complete(Unit)
                repeat(100) { try { handles += p.openFile(uri, "r")!! } catch (_: java.io.FileNotFoundException) { } }
            }
            opened.await(); release.complete(Unit); owner.join(); opener.await()
            assertTrue(handles.isNotEmpty()); handles.forEach { assertFalse(it.fileDescriptor.valid()); it.close() }
            rejected { p.openFile(saved!!, "r")?.close() }
        }
    }

    @Test fun eofDoesNotReleasePinUntilCallbackEndsAndRemovalWaits(): Unit = runBlocking {
        fixture { f ->
            val completed = f.completed(); val eof = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            var saved: Uri? = null
            val owner = async(Dispatchers.IO) {
                f.locator.withPinnedArtifact(completed.plan.jobId, token) { resource, _, chunks ->
                    saved = Uri.parse(resource.value); assertArrayEquals(payload, resolverBytes(saved!!))
                    assertTrue(chunks.toList().last().final); eof.complete(Unit); release.await()
                }
            }
            eof.await(); val started = CompletableDeferred<Unit>()
            val removal = async(Dispatchers.IO) { started.complete(Unit); f.engine.removeArtifacts(completed.consentId!!, token) }
            started.await()
            // The owner still holds the real engine pin after EOF. Removal must remain pending.
            assertNull(withTimeoutOrNull(200) { removal.await() }); assertTrue(File(f.directory, saved!!.lastPathSegment!!).exists())
            release.complete(Unit); owner.await(); value(removal.await())
            assertFalse(File(f.directory, saved!!.lastPathSegment!!).exists()); rejected { resolverBytes(saved!!) }
        }
    }

    @Test fun durableDatabaseReopenAndNewEngineGenuinelyRebindSameLocator(): Unit = runBlocking {
        fixture { f ->
            val completed = f.completed(); var first: Uri? = null
            f.locator.withPinnedArtifact(completed.plan.jobId, token) { resource, _, _ -> first = Uri.parse(resource.value) }
            rejected { resolverBytes(first!!) }; f.db.close()
            val reopened = value(StorageDatabase.open(context, f.name))
            try {
                // Reconstruct the engine against the reopened real DB; registry remains empty between pins.
                val rebind = Fixture(reopened, f.name, f.directory, f.settings, f.gate)
                value(rebind.engine.recover(token))
                assertEquals(completed.artifact, value(rebind.engine.status(completed.plan.jobId, token)).artifact)
                rebind.locator.withPinnedArtifact(completed.plan.jobId, token) { resource, artifact, _ ->
                    assertEquals(first!!.toString(), resource.value); assertEquals((completed.artifact as Knowledge.Known).value, artifact)
                    assertArrayEquals(payload, resolverBytes(Uri.parse(resource.value)))
                }
                rejected { resolverBytes(first!!) }
            } finally { reopened.close() }
        }
    }

    @Test fun actualForeignShellUidCannotReadEvenWhileTrustedPinIsActive(): Unit = runBlocking {
        fixture { f ->
            val completed = f.completed()
            f.locator.withPinnedArtifact(completed.plan.jobId, token) { resource, _, _ ->
                val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
                val uid = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("id -u")).use { it.readBytes().toString(Charsets.UTF_8).trim() }
                assertNotEquals(android.os.Process.myUid().toString(), uid)
                // API23 automation captures stdout only and Runtime.exec splits whitespace.
                // This single sh argument uses IFS for its trusted URI and captures command stderr.
                val command = "sh -c (content\${IFS}read\${IFS}--uri\${IFS}${resource.value})2>&1"
                val reply = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes().toString(Charsets.UTF_8) }
                assertTrue("Foreign UID must be denied", reply.contains("Permission Denial") || reply.contains("SecurityException") || reply.contains("not exported"))
                assertFalse(reply.contains(payload.toString(Charsets.UTF_8)))
                assertArrayEquals(payload, resolverBytes(Uri.parse(resource.value)))
            }
        }
    }
}
