package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.common.ConsentCoordinator
import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Synthetic seams deliberately execute the actual engine/SQL/codec/file helper. No source bodies. */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalAcquisitionTest {
    private val token = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private fun <T> value(outcome: Outcome<T>): T { assertTrue(outcome.toString(), outcome is Outcome.Success); return (outcome as Outcome.Success).value }
    private class Fixture(val scope: TestScope, val directorySync: (File) -> Unit = {}) : AutoCloseable {
        val directory = Files.createTempDirectory("synthetic-acquisition").toFile()
        val gate = ConsentCoordinator()
        val database = TestDatabase()
        val noCancel = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
        var serial = 0
        val clock = object : Clock {
            override fun utcNow() = UtcTimestamp(1000 + scope.testScheduler.currentTime / 1000)
            override fun monotonicNow() = scope.testScheduler.currentTime.milliseconds
        }
        var live = AcquisitionConnection(true, true)
        var allowed = true
        var state = SettingsSnapshot(Revision(0), cache = CachePolicy(0, false, false), budget = Knowledge.Absent("Synthetic"),
            providerProfileIds = SnapshotList.empty(), sourceNotices = SnapshotList.empty(), consents = SnapshotList.empty(), updatedAt = UtcTimestamp(1000))
        val settings = object : SettingsRepository {
            override suspend fun read(cancel: CancelToken): Outcome<SettingsSnapshot> = Outcome.Success(state)
            override suspend fun update(patch: SettingsPatch, expected: Revision, cancel: CancelToken): Outcome<SettingsSnapshot> = gate.withExclusive(cancel) {
                state = state.copy(revision = state.revision.next(), consents = SnapshotList.copyOf(state.consents.map { it.copy(revokedAt = UtcTimestamp(1001)) }))
                Outcome.Success(state)
            }
            override suspend fun approve(intent: DownloadIntent, cancel: CancelToken): Outcome<ConsentRecord> = gate.withExclusive(cancel) {
                val consent = ConsentRecord(EntityId("consent-${++serial}"), intent, UtcTimestamp(1000), null, true)
                state = state.copy(consents = SnapshotList.copyOf(state.consents + consent)); Outcome.Success(consent)
            }
        }
        val requests = mutableListOf<RequestSpec>()
        val starts = mutableListOf<Long>()
        val wireEnqueues = mutableListOf<Pair<RequestSpec, ResourceUri>>()
        val wireStarts = mutableListOf<Long>()
        val endpointCalls = mutableListOf<ResourceUri>()
        var guardedWire = false
        var wireHops: (RequestSpec) -> List<ResourceUri> = { listOf(it.request.uri) }
        var beforeWire: suspend (RequestSpec, ResourceUri) -> Unit = { _, _ -> }
        var response: (RequestSpec) -> Flow<Outcome<ByteChunk>> = { spec -> flowOf(Outcome.Success(chunk("abcdef", metadata = metadata(spec, length = 6)))) }
        val network: NetworkClient = object : NetworkClient {
            override suspend fun send(request: RequestSpec, cancel: CancelToken): Outcome<ByteResponse> = error("stream required")
            override fun stream(request: RequestSpec, cancel: CancelToken): Flow<Outcome<ByteChunk>> = flow {
                requests += request; starts += scope.testScheduler.currentTime
                if (guardedWire) {
                    try {
                        for (hop in wireHops(request)) {
                            beforeWire(request, hop)
                            engine.authorizeWireDispatch(request, hop, cancel) {
                                wireEnqueues += request to hop; wireStarts += scope.testScheduler.currentTime
                            }
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) {
                        // Match the transport's deliberate callback-error scrubbing.
                        emit(Outcome.Failure(DomainFailure(FailureCode.TRANSPORT, "Synthetic denied wire dispatch", SnapshotList.empty())))
                        return@flow
                    }
                }
                emitAll(response(request))
            }
            override suspend fun close() = Unit
        }
        val files = DurableArtifactFiles(directory, { file -> when { Files.isSymbolicLink(file.toPath()) -> ArtifactEntryKind.SYMLINK; !file.exists() -> ArtifactEntryKind.MISSING; file.isFile -> ArtifactEntryKind.REGULAR; else -> ArtifactEntryKind.OTHER } }, directorySync)
        val engine: LocalAcquisition = LocalAcquisition(database, network, settings, gate, clock, object : IdSource { override fun nextId() = "synthetic-${++serial}" }, files,
            { if (allowed) Outcome.Success(Unit) else Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT, "Synthetic rejected profile", SnapshotList.empty())) },
            { _, uri -> endpointCalls += uri; EndpointProfile(EntityId("synthetic"), Origin("https", java.net.URI(uri.value).host, 443), SnapshotList.empty(), null, 1,
                RetryPolicy(1, Duration.ZERO, Duration.ZERO, true)) }, { live })
        val limits = AcquisitionLimits("synthetic-v1", 100000, 30000, 30000, 350, 2, 15000, 128, 65536, 32)
        fun request(expected: Knowledge<Long> = Knowledge.Known(6L), sha: Knowledge<ContentHash> = Knowledge.Absent("Synthetic unavailable"), search: Boolean = false): AcquisitionRequest {
            val uri = ResourceUri(if (search) EnglishSearchPageCodec.INITIAL_URI else "https://example.invalid/synthetic.txt")
            val source = if (search) AcquisitionSource.EnglishSearch(uri, "synthetic-v1") else AcquisitionSource.RemoteFile(uri, SnapshotList.empty(), ArtifactFraming.TEXT, ArtifactCompression.NONE, expected, sha)
            return AcquisitionRequest(DownloadIntent(EntityId("synthetic-intent"), if (search) DownloadKind.CATALOG else DownloadKind.RULES, ProviderId("synthetic"), uri,
                "synthetic-v1", LanguageTag.ENGLISH, null, (sha as? Knowledge.Known)?.value, if (search) Knowledge.Unknown("Derived bytes", SnapshotList.empty()) else expected),
                "Synthetic", source, if (search) AcquisitionLanguageScope.ENGLISH_CARDS else AcquisitionLanguageScope.ENGLISH_RULES,
                Knowledge.Unknown("Not asserted", SnapshotList.empty()), Knowledge.Absent("Not asserted"), Knowledge.Absent("Synthetic"), limits, false)
        }
        suspend fun queued(request: AcquisitionRequest = request()): AcquisitionSnapshot {
            val planned = value(engine.plan(request, noCancel))
            val approval = value(settings.approve(request.intent, noCancel))
            return value(engine.start(planned.plan.jobId, planned.revision, approval.id, noCancel))
        }
        fun row(id: EntityId) = database.jobs.getValue(id.value)
        fun metadata(spec: RequestSpec, status: Int = 200, length: Long? = null, etag: String? = "\"synthetic\"", extra: List<HttpHeader> = emptyList()) = HttpMetadata(status,
            SnapshotList.copyOf(extra + listOfNotNull(length?.let { HttpHeader("Content-Length", it.toString()) }, etag?.let { HttpHeader("ETag", it) })), spec.request.uri, UtcTimestamp(1000), Knowledge.Absent("Not supplied"), Knowledge.Known(false))
        override fun close() { directory.deleteRecursively() }
    }
    companion object {
        private fun <T> value(outcome: Outcome<T>): T { assertTrue(outcome.toString(), outcome is Outcome.Success); return (outcome as Outcome.Success).value }
        private fun chunk(bytes: String, sequence: Long = 0, offset: Long = 0, metadata: HttpMetadata? = null, final: Boolean = true) = ByteChunk(sequence, offset, SnapshotBytes(bytes.toByteArray()), metadata, final)
        private fun page(number: Int, total: Int, firstId: Int = (number - 1) * 175): String {
            val count = minOf(175, total - (number - 1) * 175)
            val records = (firstId until firstId + count).joinToString(",") { "{\"object\":\"card\",\"id\":\"synthetic-$it\",\"lang\":\"en\"}" }
            val next = if (number * 175 < total) ",\"next_page\":\"${EnglishSearchPageCodec.INITIAL_URI.replace("page=1", "page=${number + 1}")}\"" else ""
            return "{\"object\":\"list\",\"total_cards\":$total,\"has_more\":${number * 175 < total},\"data\":[$records]$next}"
        }
    }

    /** Synthetic metadata/state fixtures; this is not real adapter-to-engine composition evidence. */
    @Test fun headerOnly429WaitsThenFailsWithoutArtifactOrAutomaticRetry() = runTest {
        for (search in listOf(false, true)) for (retryMillis in listOf(30_000L, 60_000L)) Fixture(this).use { f ->
            f.guardedWire = true
            f.response = { spec -> flowOf(Outcome.Success(chunk("", metadata = f.metadata(spec, status = 429, length = 10_000_000)
                .copy(retryAfter = if (retryMillis == 30_000L) Knowledge.Absent("Not supplied") else Knowledge.Known(60.seconds))))) }
            val q = f.queued(f.request(search = search))
            val runner = async { f.engine.run(q.plan.jobId, token) }
            runCurrent()
            assertEquals(AcquisitionState.RUNNING, value(f.engine.status(q.plan.jobId, token)).state)
            assertEquals(1, f.wireEnqueues.size)
            advanceTimeBy(retryMillis - 1); runCurrent()
            assertFalse(runner.isCompleted)
            assertEquals(1, f.requests.size)
            advanceTimeBy(1); runCurrent()
            val failed = value(runner.await())
            assertEquals(AcquisitionState.FAILED, failed.state)
            assertEquals(FailureCode.TRANSPORT, failed.failure?.code)
            assertEquals(if (search) 15_000L else 6L, failed.transferredBytes)
            assertEquals(0L, failed.durableArtifactBytes)
            assertTrue(failed.artifact !is Knowledge.Known)
            advanceTimeBy(60_000); runCurrent()
            assertEquals(1, f.requests.size)
        }
    }

    @Test fun explicitResumeAfter429RetainsReservationsAndExhaustsAttemptLimit() = runTest {
        Fixture(this).use { f ->
            f.guardedWire = true
            f.response = { spec -> flowOf(Outcome.Success(chunk("", metadata = f.metadata(spec, status = 429)))) }
            var queued = f.queued()
            repeat(3) { index ->
                val running = async { f.engine.run(queued.plan.jobId, token) }
                runCurrent(); advanceTimeBy(30_000); runCurrent()
                val failed = value(running.await())
                assertEquals(AcquisitionState.FAILED, failed.state)
                assertEquals(index + 1, failed.attempt)
                assertEquals((index + 1) * 6L, failed.transferredBytes)
                assertEquals(index + 1, f.wireEnqueues.size)
                if (index < 2) queued = value(f.engine.resume(failed.plan.jobId, failed.revision, requireNotNull(failed.consentId), token))
                else {
                    assertTrue(f.engine.resume(failed.plan.jobId, failed.revision, requireNotNull(failed.consentId), token) is Outcome.Failure)
                    assertEquals(3, f.requests.size)
                }
            }
        }
    }

    @Test fun cancellationDuring429WaitSettlesInterruptedAndRevocationBlocksResume() = runTest {
        Fixture(this).use { f ->
            f.guardedWire = true
            f.response = { spec -> flowOf(Outcome.Success(chunk("", metadata = f.metadata(spec, status = 429)))) }
            val q = f.queued()
            val runner = async { f.engine.run(q.plan.jobId, token) }
            runCurrent(); advanceTimeBy(1000); runCurrent()
            runner.cancelAndJoin()
            val interrupted = value(f.engine.status(q.plan.jobId, token))
            assertEquals(AcquisitionState.INTERRUPTED, interrupted.state)
            assertEquals(6L, interrupted.transferredBytes)
            value(f.settings.update(SettingsPatch(SnapshotList.empty()), f.state.revision, token))
            assertTrue(f.engine.resume(interrupted.plan.jobId, interrupted.revision, requireNotNull(interrupted.consentId), token) is Outcome.Unavailable)
            assertEquals(1, f.requests.size)
            assertEquals(1, f.wireEnqueues.size)
        }
    }

    @Test fun longAndInfinite429AreTransportFailuresWithoutAutomaticRetry() = runTest {
        for (retry in listOf(86_401.seconds, Duration.INFINITE)) Fixture(this).use { f ->
            f.guardedWire = true
            f.response = { spec -> flowOf(Outcome.Success(chunk("", metadata = f.metadata(spec, status = 429)
                .copy(retryAfter = Knowledge.Known(retry))))) }
            val q = f.queued()
            val failed = value(f.engine.run(q.plan.jobId, token))
            assertEquals(AcquisitionState.FAILED, failed.state)
            assertEquals(FailureCode.TRANSPORT, requireNotNull(failed.failure).code)
            assertEquals(1, f.wireEnqueues.size)
            assertTrue(f.database.artifacts.isEmpty())
        }
    }

    @Test fun revocationDuring429WaitSettlesWaitingConsentPromptly() = runTest {
        Fixture(this).use { f ->
            f.guardedWire = true
            f.response = { spec -> flowOf(Outcome.Success(chunk("", metadata = f.metadata(spec, status = 429)))) }
            val q = f.queued()
            val runner = async { f.engine.run(q.plan.jobId, token) }
            runCurrent()
            value(f.settings.update(SettingsPatch(SnapshotList.empty()), f.state.revision, token))
            advanceTimeBy(250); runCurrent()
            assertEquals(AcquisitionState.WAITING_CONSENT, value(f.engine.status(q.plan.jobId, token)).state)
            assertEquals(AcquisitionState.WAITING_CONSENT, value(runner.await()).state)
            assertEquals(1, f.wireEnqueues.size)
        }
    }

    @Test fun oneJobsIndefinite429DeniesAnotherJobsPhysicalEnqueue() = runTest {
        Fixture(this).use { f ->
            f.guardedWire = true
            val first = f.queued()
            val second = f.queued()
            f.response = { spec -> flowOf(Outcome.Success(chunk("", metadata = f.metadata(spec, status = 429)
                .copy(retryAfter = Knowledge.Known(Duration.INFINITE))))) }
            assertEquals(FailureCode.TRANSPORT, value(f.engine.run(first.plan.jobId, token)).failure?.code)
            f.response = { spec -> flowOf(Outcome.Success(chunk("abcdef", metadata = f.metadata(spec, length = 6)))) }
            assertEquals(FailureCode.TRANSPORT, value(f.engine.run(second.plan.jobId, token)).failure?.code)
            assertEquals(1, f.wireEnqueues.size)
            assertTrue(f.database.artifacts.isEmpty())
        }
    }

    @Test fun cancellationAt429CommitAcknowledgementRetainsTheOriginBlock() = runTest {
        Fixture(this).use { f ->
            f.guardedWire = true
            val first = f.queued()
            val second = f.queued()
            f.response = { spec -> flowOf(Outcome.Success(chunk("", metadata = f.metadata(spec, status = 429)))) }
            lateinit var runner: Deferred<Outcome<AcquisitionSnapshot>>
            f.database.onCooldownCommit = { runner.cancel() }
            runner = async(start = CoroutineStart.LAZY) { f.engine.run(first.plan.jobId, token) }
            runner.start(); runner.join()
            assertTrue(runner.isCancelled)
            assertEquals(AcquisitionState.INTERRUPTED, value(f.engine.status(first.plan.jobId, token)).state)
            f.response = { spec -> flowOf(Outcome.Success(chunk("abcdef", metadata = f.metadata(spec, length = 6)))) }
            assertEquals(FailureCode.TRANSPORT, value(f.engine.run(second.plan.jobId, token)).failure?.code)
            assertEquals(1, f.wireEnqueues.size)
        }
    }

    @Test fun resumeDuringKnownCooldownDoesNotConsumeAttemptsOrAlterTheJob() = runTest {
        Fixture(this).use { f ->
            f.guardedWire = true
            f.response = { spec -> flowOf(Outcome.Success(chunk("", metadata = f.metadata(spec, status = 429)
                .copy(retryAfter = Knowledge.Known(Duration.INFINITE))))) }
            val q = f.queued()
            val failed = value(f.engine.run(q.plan.jobId, token))
            repeat(4) {
                val denied = f.engine.resume(failed.plan.jobId, failed.revision, requireNotNull(failed.consentId), token)
                assertTrue(denied.toString(), denied is Outcome.Failure)
                assertEquals(FailureCode.TRANSPORT, (denied as Outcome.Failure).error.code)
                assertEquals(failed, value(f.engine.status(failed.plan.jobId, token)))
            }
            assertEquals(1, f.wireEnqueues.size)
        }
    }

    @Test fun startDuringKnownCooldownKeepsTheNewPlanUncharged() = runTest {
        Fixture(this).use { f ->
            f.guardedWire = true
            f.response = { spec -> flowOf(Outcome.Success(chunk("", metadata = f.metadata(spec, status = 429)
                .copy(retryAfter = Knowledge.Known(Duration.INFINITE))))) }
            val q = f.queued()
            value(f.engine.run(q.plan.jobId, token))
            val request = f.request()
            val planned = value(f.engine.plan(request, token))
            val approval = value(f.settings.approve(request.intent, token))
            val denied = f.engine.start(planned.plan.jobId, planned.revision, approval.id, token)
            assertTrue(denied.toString(), denied is Outcome.Failure)
            assertEquals(FailureCode.TRANSPORT, (denied as Outcome.Failure).error.code)
            assertEquals(planned, value(f.engine.status(planned.plan.jobId, token)))
            assertEquals(0L, planned.transferredBytes)
            assertEquals(1, f.wireEnqueues.size)
        }
    }

    @Test fun planIsDurableAndDoesNotApproveOrDispatch() = runTest {
        Fixture(this).use { f ->
            val planned = value(f.engine.plan(f.request(), token))
            assertEquals(AcquisitionState.PLANNED, planned.state); assertTrue(f.requests.isEmpty()); assertTrue(f.state.consents.isEmpty())
            assertEquals(planned, value(f.engine.status(planned.plan.jobId, token)))
            f.allowed = false
            assertTrue(f.engine.plan(f.request(), token) is Outcome.Failure)
        }
    }
    @Test fun immutablePlanHashAndColumnIdentityRejectTampering() = runTest {
        Fixture(this).use { f ->
            val q = f.queued(); f.row(q.plan.jobId)["plan_sha256"] = SqlValue.Text("0".repeat(64))
            assertEquals(FailureCode.CORRUPT_DATA, (f.engine.status(q.plan.jobId, token) as Outcome.Failure).error.code)
        }
    }
    @Test fun callerConsentIdMustMatchAuthoritativeExactIntent() = runTest {
        Fixture(this).use { f ->
            val planned = value(f.engine.plan(f.request(), token))
            assertTrue(f.engine.start(planned.plan.jobId, planned.revision, EntityId("fabricated"), token) is Outcome.Unavailable)
            val mismatched = value(f.settings.approve(f.request().intent.copy(version = "different"), token))
            assertTrue(f.engine.start(planned.plan.jobId, planned.revision, mismatched.id, token) is Outcome.Unavailable)
            assertTrue(f.requests.isEmpty())
        }
    }
    @Test fun revocationSettlesBeforeQueueGateCanCommit() = runTest {
        Fixture(this).use { f ->
            val p = value(f.engine.plan(f.request(), token)); val c = value(f.settings.approve(p.plan.request.intent, token))
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val writer = launch { f.gate.withExclusive(token) { entered.complete(Unit); release.await(); f.state = f.state.copy(consents = SnapshotList.of(c.copy(revokedAt = UtcTimestamp(1001)))) } }
            entered.await()
            val starter = async { f.engine.start(p.plan.jobId, p.revision, c.id, token) }
            runCurrent(); assertFalse(starter.isCompleted); release.complete(Unit); writer.join()
            assertTrue(starter.await() is Outcome.Unavailable)
            assertEquals(AcquisitionState.PLANNED, value(f.engine.status(p.plan.jobId, token)).state)
        }
    }
    @Test fun successfulRawArtifactReopensAndHasOneVerifiedFinalChunk() = runTest {
        Fixture(this).use { f ->
            val q = f.queued(); val result = value(f.engine.run(q.plan.jobId, token))
            assertEquals(AcquisitionState.COMPLETED, result.state); assertEquals(6L, result.transferredBytes)
            assertEquals(AcquisitionConsistency.FILE_REPRESENTATION, (result.artifact as Knowledge.Known).value.provenance.consistency)
            repeat(2) {
                val chunks = f.engine.openArtifact(q.plan.jobId, token).toList()
                assertEquals("abcdef", String(chunks.filterNot { it.final }.flatMap { it.bytes.copyBytes().toList() }.toByteArray()))
                assertEquals(1, chunks.count { it.final }); assertEquals(6L, chunks.last().offset)
            }
            assertTrue(f.engine.cancel(q.plan.jobId, result.revision, token) is Outcome.Failure)
            assertEquals(AcquisitionState.COMPLETED, value(f.engine.status(q.plan.jobId, token)).state)
        }
    }
    @Test fun revocationCancelsAnActiveResponseWithoutAnyIncomingBytes() = runTest {
        Fixture(this).use { f ->
            var closed = false
            f.response = { flow { try { awaitCancellation() } finally { closed = true } } }
            val q = f.queued(); val runner = async { f.engine.run(q.plan.jobId, token) }
            runCurrent(); assertEquals(1, f.requests.size)
            f.settings.update(SettingsPatch(SnapshotList.of(SettingChange.RevokeConsent(q.consentId!!, false))), f.state.revision, token)
            advanceTimeBy(101); runCurrent()
            assertEquals(AcquisitionState.WAITING_CONSENT, value(runner.await()).state); assertTrue(closed)
            assertEquals(6L, value(f.engine.status(q.plan.jobId, token)).transferredBytes)
        }
    }
    @Test fun wifiLossStopsActiveTransferAndPreventsFreshDispatch() = runTest {
        Fixture(this).use { f ->
            f.response = { flow { awaitCancellation() } }
            val q = f.queued(f.request().copy(wifiOnly = true)); val runner = async { f.engine.run(q.plan.jobId, token) }
            runCurrent(); f.live = AcquisitionConnection(true, false); advanceTimeBy(101); runCurrent()
            assertEquals(AcquisitionState.WAITING_WIFI, value(runner.await()).state)
            assertTrue(f.engine.resume(q.plan.jobId, value(f.engine.status(q.plan.jobId, token)).revision, q.consentId!!, token) is Outcome.Unavailable)
            assertEquals(1, f.requests.size)
        }
    }
    @Test fun pauseJoinsWriterThenStrongRangeResumeAppendsOnlyMatchingRepresentation() = runTest {
        Fixture(this).use { f ->
            var closed = false
            f.response = { spec -> flow { try { emit(Outcome.Success(chunk("abc", metadata = f.metadata(spec, length = 6), final = false))); awaitCancellation() } finally { closed = true } } }
            val q = f.queued(); val runner = async { f.engine.run(q.plan.jobId, token) }
            runCurrent(); val current = value(f.engine.status(q.plan.jobId, token))
            val paused = value(f.engine.pause(q.plan.jobId, current.revision, token)); assertTrue(closed); assertEquals(3L, paused.durableArtifactBytes)
            try { runner.await(); fail("Cancelled execution") } catch (_: CancellationException) { }
            f.response = { spec -> flowOf(Outcome.Success(chunk("def", metadata = f.metadata(spec, 206, 3, extra = listOf(HttpHeader("Content-Range", "bytes 3-5/6")))))) }
            value(f.engine.resume(q.plan.jobId, paused.revision, q.consentId!!, token))
            val completed = value(f.engine.run(q.plan.jobId, token))
            assertEquals(AcquisitionState.COMPLETED, completed.state)
            assertEquals("bytes=3-", f.requests.last().request.headers.single { it.name == "Range" }.value)
            assertEquals(9L, completed.transferredBytes) // Ambiguous interrupted reservation remains charged.
        }
    }
    @Test fun range200RestartsInsteadOfAppendingAndRetainsAmbiguousBudget() = runTest {
        Fixture(this).use { f ->
            f.response = { spec -> flow { emit(Outcome.Success(chunk("abc", metadata = f.metadata(spec, length = 6), final = false))); awaitCancellation() } }
            val q = f.queued(); val runner = async { f.engine.run(q.plan.jobId, token) }; runCurrent()
            val paused = value(f.engine.pause(q.plan.jobId, value(f.engine.status(q.plan.jobId, token)).revision, token))
            try { runner.await() } catch (_: CancellationException) { }
            f.response = { spec -> flowOf(Outcome.Success(chunk("abcdef", metadata = f.metadata(spec, length = 6)))) }
            value(f.engine.resume(q.plan.jobId, paused.revision, q.consentId!!, token))
            val done = value(f.engine.run(q.plan.jobId, token)); assertEquals(2, done.attempt); assertEquals(6L, done.durableArtifactBytes)
            assertEquals(18L, done.transferredBytes); assertEquals(3, f.requests.size)
        }
    }
    @Test fun weakEtagRestartsAndMismatchRangeNeverCompletes() = runTest {
        for (weak in listOf(true, false)) Fixture(this).use { f ->
            f.response = { spec -> flow { emit(Outcome.Success(chunk("abc", metadata = f.metadata(spec, length = 6, etag = if (weak) "W/\"synthetic\"" else "\"synthetic\""), final = false))); awaitCancellation() } }
            val q = f.queued(); val runner = async { f.engine.run(q.plan.jobId, token) }; runCurrent()
            val paused = value(f.engine.pause(q.plan.jobId, value(f.engine.status(q.plan.jobId, token)).revision, token)); try { runner.await() } catch (_: CancellationException) { }
            f.response = { spec -> if (weak) flowOf(Outcome.Success(chunk("abcdef", metadata = f.metadata(spec, length = 6)))) else
                flowOf(Outcome.Success(chunk("def", metadata = f.metadata(spec, 206, 3, extra = listOf(HttpHeader("Content-Range", "bytes 2-4/6")))))) }
            value(f.engine.resume(q.plan.jobId, paused.revision, q.consentId!!, token))
            val result = value(f.engine.run(q.plan.jobId, token))
            if (weak) { assertEquals(AcquisitionState.COMPLETED, result.state); assertEquals(2, result.attempt); assertFalse(f.requests.last().request.headers.any { it.name == "Range" }) }
            else assertEquals(AcquisitionState.FAILED, result.state)
        }
    }
    @Test fun finiteAttemptsAndBudgetCannotResetAcrossFailureResume() = runTest {
        Fixture(this).use { f ->
            f.response = { flow { throw IOException("Synthetic dropped response") } }
            var q = f.queued()
            repeat(3) { index ->
                q = value(f.engine.run(q.plan.jobId, token)); assertEquals(AcquisitionState.FAILED, q.state); assertEquals(index + 1, q.attempt)
                assertEquals(6L * (index + 1), q.transferredBytes)
                if (index < 2) q = value(f.engine.resume(q.plan.jobId, q.revision, q.consentId!!, token))
            }
            assertTrue(f.engine.resume(q.plan.jobId, q.revision, q.consentId!!, token) is Outcome.Failure)
            assertEquals(3, f.requests.size)
        }
    }
    @Test fun exhaustedBudgetStopsBeforeDispatchAndCannotOverflow() = runTest {
        Fixture(this).use { f ->
            val q = f.queued(f.request().copy(limits = f.limits.copy(maxTransferBytes = 6)))
            f.response = { flow { throw IOException("Synthetic") } }
            var failed = value(f.engine.run(q.plan.jobId, token)); assertEquals(6L, failed.transferredBytes)
            val queued = value(f.engine.resume(q.plan.jobId, failed.revision, q.consentId!!, token))
            failed = value(f.engine.run(q.plan.jobId, token)); assertEquals(AcquisitionState.FAILED, failed.state); assertEquals(1, f.requests.size)
            assertEquals(queued.attempt, failed.attempt)
        }
    }
    @Test fun streamSequenceLengthHashEncodingAndFinalAreStrict() = runTest {
        val variants: List<(Fixture, RequestSpec) -> Flow<Outcome<ByteChunk>>> = listOf(
            { f, s -> flowOf(Outcome.Success(chunk("abcdef", sequence = 1, metadata = f.metadata(s, length = 6)))) },
            { f, s -> flowOf(Outcome.Success(chunk("abc", metadata = f.metadata(s, length = 6)))) },
            { f, s -> flowOf(Outcome.Success(chunk("abcdef", metadata = f.metadata(s, length = 6), final = false))) },
            { f, s -> flowOf(Outcome.Success(chunk("abcdef", metadata = f.metadata(s, length = 6, extra = listOf(HttpHeader("Content-Encoding", "gzip")))))) },
            { f, s -> flowOf(Outcome.Success(chunk("abcdef", metadata = f.metadata(s, length = 6))), Outcome.Success(chunk("", sequence = 1, offset = 6))) })
        for (variant in variants) Fixture(this).use { f -> f.response = { variant(f, it) }; val q = f.queued(); assertEquals(AcquisitionState.FAILED, value(f.engine.run(q.plan.jobId, token)).state) }
        Fixture(this).use { f -> val q = f.queued(f.request(sha = Knowledge.Known(ContentHash("SHA-256", "0".repeat(64))))); assertEquals(AcquisitionState.FAILED, value(f.engine.run(q.plan.jobId, token)).state) }
    }
    @Test fun englishPagesPersistUniqueIdsAndTruthfulDerivedProvenanceAt600msStarts() = runTest {
        Fixture(this).use { f ->
            f.response = { spec -> val number = EnglishSearchPageCodec.pageNumber(spec.request.uri.value); val bytes = page(number, 176); flowOf(Outcome.Success(chunk(bytes, metadata = f.metadata(spec, length = bytes.toByteArray().size.toLong())))) }
            val q = f.queued(f.request(search = true)); val done = value(f.engine.run(q.plan.jobId, token))
            assertEquals(AcquisitionState.COMPLETED, done.state); assertEquals(176L, done.acquiredRecords); assertEquals(2L, done.acquiredPages)
            assertEquals(176, f.database.seen.size); assertTrue(f.starts[1] - f.starts[0] >= 600)
            val artifact = (done.artifact as Knowledge.Known).value
            assertEquals(ArtifactFraming.JSONL, artifact.framing); assertEquals(AcquisitionConsistency.NON_ATOMIC_TRAVERSAL, artifact.provenance.consistency)
            assertTrue(artifact.provenance.etag is Knowledge.Absent); assertTrue(artifact.provenance.sourceDate is Knowledge.Absent)
            assertEquals(176, f.engine.openArtifact(q.plan.jobId, token).filterNot { it.final }.toList().sumOf { String(it.bytes.copyBytes()).count { c -> c == '\n' } })
        }
    }
    @Test fun duplicateAcrossPagesRollsBackIdsCursorAndOffset() = runTest {
        Fixture(this).use { f ->
            f.response = { spec -> val n = EnglishSearchPageCodec.pageNumber(spec.request.uri.value); val bytes = page(n, 176, if (n == 1) 0 else 0); flowOf(Outcome.Success(chunk(bytes, metadata = f.metadata(spec)))) }
            val q = f.queued(f.request(search = true)); val result = value(f.engine.run(q.plan.jobId, token))
            assertEquals(AcquisitionState.FAILED, result.state); assertEquals(175L, result.acquiredRecords); assertEquals(1L, result.acquiredPages)
            assertEquals(175, f.database.seen.size)
        }
    }
    @Test fun zeroRowCasAbortsWholePageTransactionIncludingSeenIds() = runTest {
        Fixture(this).use { f ->
            f.database.zeroPageCheckpoint = true
            f.response = { spec -> flowOf(Outcome.Success(chunk(page(1, 1), metadata = f.metadata(spec)))) }
            val q = f.queued(f.request(search = true)); val result = value(f.engine.run(q.plan.jobId, token))
            assertEquals(AcquisitionState.FAILED, result.state); assertEquals(0L, result.acquiredRecords); assertTrue(f.database.seen.isEmpty())
        }
    }
    @Test fun malformedUtf8AndFinalCountDriftFailWithoutSuccess() = runTest {
        Fixture(this).use { f ->
            f.response = { spec -> flowOf(Outcome.Success(ByteChunk(0, 0, SnapshotBytes(byteArrayOf(0xc3.toByte(), 0x28)), f.metadata(spec), true))) }
            val q = f.queued(f.request(search = true)); assertEquals(AcquisitionState.FAILED, value(f.engine.run(q.plan.jobId, token)).state)
        }
        Fixture(this).use { f ->
            f.response = { spec -> val n = EnglishSearchPageCodec.pageNumber(spec.request.uri.value); flowOf(Outcome.Success(chunk(page(n, if (n == 1) 176 else 177), metadata = f.metadata(spec)))) }
            val q = f.queued(f.request(search = true)); assertEquals(AcquisitionState.FAILED, value(f.engine.run(q.plan.jobId, token)).state)
        }
    }
    @Test fun firstPageRevalidationDriftRestartsIdsWithoutResettingBudget() = runTest {
        Fixture(this).use { f ->
            f.response = { spec -> val n = EnglishSearchPageCodec.pageNumber(spec.request.uri.value); if (n == 1) flowOf(Outcome.Success(chunk(page(1, 176), metadata = f.metadata(spec)))) else flow { awaitCancellation() } }
            val q = f.queued(f.request(search = true)); val runner = async { f.engine.run(q.plan.jobId, token) }
            runCurrent(); advanceTimeBy(601); runCurrent()
            val paused = value(f.engine.pause(q.plan.jobId, value(f.engine.status(q.plan.jobId, token)).revision, token)); try { runner.await() } catch (_: CancellationException) { }
            f.response = { spec -> flowOf(Outcome.Success(chunk(page(1, 1), metadata = f.metadata(spec)))) }
            value(f.engine.resume(q.plan.jobId, paused.revision, q.consentId!!, token)); val done = value(f.engine.run(q.plan.jobId, token))
            assertEquals(AcquisitionState.COMPLETED, done.state); assertEquals(2, done.attempt); assertEquals(1, f.database.seen.size)
            assertTrue(done.transferredBytes > paused.transferredBytes)
        }
    }
    @Test fun renameCrashRecoveryUsesPreparedDescriptorAndNeverGuessesEof() = runTest {
        var syncs = 0
        Fixture(this) { if (++syncs == 2) throw IOException("Synthetic directory sync failure after rename") }.use { f ->
            val q = f.queued(); val failed = value(f.engine.run(q.plan.jobId, token))
            assertEquals(AcquisitionState.FAILED, failed.state); assertEquals(AcquisitionPhase.VERIFYING, failed.phase)
            assertTrue(f.directory.listFiles()!!.any { it.name.endsWith("-verified") })
            value(f.engine.recover(token)); val recovered = value(f.engine.status(q.plan.jobId, token))
            assertEquals(AcquisitionState.COMPLETED, recovered.state); assertEquals(1, f.requests.size)
            assertEquals(failed.transferredBytes, recovered.transferredBytes)
        }
    }
    @Test fun cancelledOrRevokedPreparedCandidatesCannotResurrect() = runTest {
        for (cancelJob in listOf(true, false)) {
            var syncs = 0
            Fixture(this) { if (++syncs == 2) throw IOException("Synthetic rename crash") }.use { f ->
                val q = f.queued(); val failed = value(f.engine.run(q.plan.jobId, token))
                if (cancelJob) value(f.engine.cancel(q.plan.jobId, failed.revision, token)) else f.settings.update(SettingsPatch(SnapshotList.empty()), f.state.revision, token)
                value(f.engine.recover(token)); assertEquals(if (cancelJob) AcquisitionState.CANCELLED else AcquisitionState.WAITING_CONSENT, value(f.engine.status(q.plan.jobId, token)).state)
                assertTrue(f.database.artifacts.isEmpty())
            }
        }
    }
    @Test fun cancellationAfterCompletionCommitPreservesCompletedArtifact() = runTest {
        Fixture(this).use { f ->
            val q = f.queued(); val runner = async {
                f.database.onCompletionCommit = { currentCoroutineContext().cancel() }
                f.engine.run(q.plan.jobId, token)
            }
            try { runner.await() } catch (_: CancellationException) { }
            assertEquals(AcquisitionState.COMPLETED, value(f.engine.status(q.plan.jobId, token)).state)
        }
    }
    @Test fun artifactCorruptionThrowsTypedFailureBeforeAnyFinalChunk() = runTest {
        Fixture(this).use { f ->
            val q = f.queued(); value(f.engine.run(q.plan.jobId, token))
            val artifact = f.directory.listFiles()!!.single { it.name.endsWith("-verified") }; artifact.writeText("wrong!")
            val emitted = mutableListOf<ByteChunk>()
            try { f.engine.openArtifact(q.plan.jobId, token).collect { emitted += it }; fail("Corrupt artifact") } catch (_: ArtifactReadException) { }
            assertFalse(emitted.any { it.final })
        }
    }
    @Test fun pinnedImportFlowDoesNotReenterLockAndRemovalWaitsForReaders() = runTest {
        Fixture(this).use { f ->
            val q = f.queued(); value(f.engine.run(q.plan.jobId, token))
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val reader = async { f.engine.withVerifiedArtifact(q.plan.jobId, token) { artifact, bytes -> entered.complete(Unit); release.await(); assertEquals(6L, artifact.bytes); bytes.toList().last().final } }
            entered.await(); val removal = async { f.engine.removeArtifacts(q.consentId!!, token) }
            runCurrent(); assertFalse(removal.isCompleted); release.complete(Unit); assertTrue(reader.await()); value(removal.await())
            assertEquals(AcquisitionState.CANCELLED, value(f.engine.status(q.plan.jobId, token)).state)
            assertTrue(f.directory.listFiles()!!.isEmpty())
            try { f.engine.openArtifact(q.plan.jobId, token).toList(); fail("Removed artifact") } catch (_: ArtifactReadException) { }
        }
    }
    @Test fun versionedKeysetCursorAndObserveReadActualDurableState() = runTest {
        Fixture(this).use { f ->
            repeat(3) { value(f.engine.plan(f.request(), token)) }
            val first = value(f.engine.list(PageRequest(2), token)); assertEquals(2, first.items.size); assertNotNull(first.nextCursor)
            val second = value(f.engine.list(PageRequest(2, first.nextCursor), token)); assertEquals(1, second.items.size)
            assertTrue(first.items.none { it.plan.jobId == second.items.single().plan.jobId })
            assertTrue(f.engine.list(PageRequest(1, "wrong-cursor"), token) is Outcome.Failure)
            val snapshot = first.items.first(); val seen = mutableListOf<AcquisitionState>()
            val observer = launch { f.engine.observe(snapshot.plan.jobId, token).collect { seen += value(it).state } }
            runCurrent(); value(f.engine.cancel(snapshot.plan.jobId, snapshot.revision, token)); advanceTimeBy(201); runCurrent(); observer.join()
            assertEquals(listOf(AcquisitionState.PLANNED, AcquisitionState.CANCELLED), seen)
        }
    }

    @Test fun physicalDispatchRejectsEqualForgedAndReleasedSpecsButAcceptsRegisteredIdentity() = runTest {
        Fixture(this).use { f ->
            f.guardedWire = true
            var forgedDenied = false; var forgedEnqueue = false
            f.beforeWire = { spec, actual ->
                try { f.engine.authorizeWireDispatch(spec.copy(), actual, token) { forgedEnqueue = true } }
                catch (_: Exception) { forgedDenied = true }
            }
            val q = f.queued(); assertEquals(AcquisitionState.COMPLETED, value(f.engine.run(q.plan.jobId, token)).state)
            assertTrue(forgedDenied); assertFalse(forgedEnqueue); assertEquals(1, f.wireEnqueues.size)
            val exactSpec = f.requests.single()
            assertTrue(exactSpec === f.wireEnqueues.single().first)
            var releasedEnqueue = false; var releasedDenied = false
            try { f.engine.authorizeWireDispatch(exactSpec, exactSpec.request.uri, token) { releasedEnqueue = true } }
            catch (_: Exception) { releasedDenied = true }
            assertTrue(releasedDenied); assertFalse(releasedEnqueue)
        }
    }
    @Test fun physicalDispatchChecksCurrentOwnerAttemptRevisionConsentAndRunningState() = runTest {
        Fixture(this).use { f ->
            f.guardedWire = true
            val q = f.queued(); val replacement = value(f.settings.approve(q.plan.request.intent, token))
            var denied = 0; var wrongEnqueues = 0
            f.beforeWire = { spec, actual ->
                val row = f.row(q.plan.jobId)
                val original = row.toMap()
                val variations = listOf(
                    mapOf("owner_token" to SqlValue.Text("synthetic-replacement-owner")),
                    mapOf("attempt" to SqlValue.Integer(2)),
                    mapOf("revision" to SqlValue.Integer((row.getValue("revision") as SqlValue.Integer).value + 1)),
                    mapOf("consent_id" to SqlValue.Text(replacement.id.value)),
                    mapOf("state" to SqlValue.Text("PAUSED"), "owner_token" to SqlValue.Null))
                for (variation in variations) {
                    row.putAll(variation)
                    try { f.engine.authorizeWireDispatch(spec, actual, token) { wrongEnqueues++ } }
                    catch (_: Exception) { denied++ }
                    finally { row.clear(); row.putAll(original) }
                }
            }
            assertEquals(AcquisitionState.COMPLETED, value(f.engine.run(q.plan.jobId, token)).state)
            assertEquals(5, denied); assertEquals(0, wrongEnqueues); assertEquals(1, f.wireEnqueues.size)
        }
    }
    @Test fun revocationDuringDelayedPhysicalSlotPreventsEnqueueAndReportsWaitingConsent() = runTest {
        Fixture(this).use { f ->
            f.guardedWire = true
            val waiting = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            f.beforeWire = { _, _ -> waiting.complete(Unit); release.await() }
            val q = f.queued(); val runner = async { f.engine.run(q.plan.jobId, token) }
            waiting.await()
            value(f.settings.update(SettingsPatch(SnapshotList.empty()), f.state.revision, token))
            release.complete(Unit)
            assertEquals(AcquisitionState.WAITING_CONSENT, value(runner.await()).state)
            assertTrue(f.wireEnqueues.isEmpty()); assertEquals(6L, value(f.engine.status(q.plan.jobId, token)).transferredBytes)
        }
    }
    @Test fun actualRedirectHopIsRevalidatedAndRevocationPreventsItsEnqueue() = runTest {
        for (revoke in listOf(false, true)) Fixture(this).use { f ->
            val mirror = ResourceUri("https://mirror.invalid/synthetic.txt")
            val request = f.request().let { it.copy(source = (it.source as AcquisitionSource.RemoteFile).copy(allowedRedirectOrigins = SnapshotList.of(Origin("https", "mirror.invalid", 443)))) }
            f.guardedWire = true; f.wireHops = { listOf(it.request.uri, mirror) }
            val waiting = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            f.beforeWire = { _, actual -> if (revoke && actual == mirror) { waiting.complete(Unit); release.await() } }
            f.response = { spec -> flowOf(Outcome.Success(chunk("abcdef", metadata = f.metadata(spec, length = 6).copy(finalUri = mirror)))) }
            val q = f.queued(request); val runner = async { f.engine.run(q.plan.jobId, token) }
            if (revoke) { waiting.await(); value(f.settings.update(SettingsPatch(SnapshotList.empty()), f.state.revision, token)); release.complete(Unit) }
            val result = value(runner.await())
            if (revoke) { assertEquals(AcquisitionState.WAITING_CONSENT, result.state); assertEquals(1, f.wireEnqueues.size) }
            else {
                assertEquals(AcquisitionState.COMPLETED, result.state); assertEquals(2, f.wireEnqueues.size)
                assertEquals(mirror, f.endpointCalls.last()); assertEquals(mirror, (result.artifact as Knowledge.Known).value.provenance.finalUri)
            }
        }
    }
    @Test fun unapprovedPhysicalTargetFailsWithoutAnyEnqueue() = runTest {
        Fixture(this).use { f ->
            f.guardedWire = true; f.wireHops = { listOf(ResourceUri("https://unapproved.invalid/synthetic.txt")) }
            val q = f.queued(); assertEquals(AcquisitionState.FAILED, value(f.engine.run(q.plan.jobId, token)).state)
            assertTrue(f.wireEnqueues.isEmpty())
        }
    }
    @Test fun physicalEnglishStartsStay600msApartAfterAnOriginSlotDelay() = runTest {
        Fixture(this).use { f ->
            f.guardedWire = true
            f.beforeWire = { _, _ -> if (f.wireEnqueues.isEmpty()) delay(500) }
            f.response = { spec -> flowOf(Outcome.Success(chunk(page(1, 1), metadata = f.metadata(spec)))) }
            val first = f.queued(f.request(search = true)); val second = f.queued(f.request(search = true))
            assertEquals(AcquisitionState.COMPLETED, value(f.engine.run(first.plan.jobId, token)).state)
            assertEquals(AcquisitionState.COMPLETED, value(f.engine.run(second.plan.jobId, token)).state)
            assertEquals(500L, f.wireStarts.first()); assertTrue(f.wireStarts[1] - f.wireStarts[0] >= 600)
        }
    }

    @Test fun coroutineCancellationEndsArtifactPinWaitWithoutDisturbingPinnedReader() = runTest {
        Fixture(this).use { f ->
            val q = f.queued(); value(f.engine.run(q.plan.jobId, token))
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val pin = async { f.engine.withVerifiedArtifact(q.plan.jobId, token) { _, bytes -> entered.complete(Unit); release.await(); bytes.toList().last().final } }
            entered.await()
            val reader = async { f.engine.openArtifact(q.plan.jobId, token).toList() }
            runCurrent(); reader.cancel(); runCurrent()
            assertTrue(reader.isCompleted && reader.isCancelled); assertFalse(pin.isCompleted)
            release.complete(Unit); assertTrue(pin.await())
            assertTrue(f.engine.openArtifact(q.plan.jobId, token).toList().last().final)
        }
    }
    @Test fun tokenCancellationCanWithdrawJoinWaitWhileWriterKeepsItsFileFence() = runTest {
        Fixture(this).use { f ->
            val settling = CompletableDeferred<Unit>(); val settled = CompletableDeferred<Unit>()
            f.response = { spec -> flow {
                try { emit(Outcome.Success(chunk("abc", metadata = f.metadata(spec, length = 6), final = false))); awaitCancellation() }
                finally { withContext(NonCancellable) { settling.complete(Unit); settled.await() } }
            } }
            val q = f.queued(); val runner = async { f.engine.run(q.plan.jobId, token) }; runCurrent()
            var cancelled = false
            val stop = object : CancelToken { override val isCancelled get() = cancelled; override fun throwIfCancelled() { if (cancelled) throw CancellationException() } }
            val pause = async { f.engine.pause(q.plan.jobId, value(f.engine.status(q.plan.jobId, token)).revision, stop) }
            settling.await(); cancelled = true; advanceTimeBy(101); runCurrent()
            val waitEndedBeforeWriter = pause.isCompleted && pause.isCancelled && !runner.isCompleted
            assertEquals(AcquisitionState.RUNNING, value(f.engine.status(q.plan.jobId, token)).state)
            settled.complete(Unit)
            try { pause.await(); fail("Token cancelled") } catch (_: CancellationException) { }
            try { runner.await(); fail("Runner cancelled") } catch (_: CancellationException) { }
            assertTrue("A waiting caller can withdraw without publishing pause before writer settlement", waitEndedBeforeWriter)
            assertEquals(AcquisitionState.INTERRUPTED, value(f.engine.status(q.plan.jobId, token)).state)
        }
    }
    @Test fun missingAndShortPartialJobsFailDurablyWithoutAbortingLaterRecoveryJobs() = runTest {
        Fixture(this).use { f ->
            val healthy = f.queued()
            val missing = f.queued(); val short = f.queued()
            f.response = { spec -> flow { emit(Outcome.Success(chunk("abc", metadata = f.metadata(spec, length = 6), final = false))); awaitCancellation() } }
            val writers = listOf(missing, short).map { q -> async { f.engine.run(q.plan.jobId, token) } }
            runCurrent()
            for (q in listOf(missing, short)) value(f.engine.pause(q.plan.jobId, value(f.engine.status(q.plan.jobId, token)).revision, token))
            for (writer in writers) try { writer.await(); fail("Paused writer") } catch (_: CancellationException) { }
            fun partial(q: AcquisitionSnapshot) = File(f.directory, (f.row(q.plan.jobId).getValue("partial_key") as SqlValue.Text).value + "-a1")
            assertTrue(partial(missing).delete()); partial(short).writeText("ab")
            value(f.engine.recover(token))
            for (q in listOf(missing, short)) {
                val failed = value(f.engine.status(q.plan.jobId, token))
                assertEquals(AcquisitionState.FAILED, failed.state); assertEquals(FailureCode.CORRUPT_DATA, failed.failure?.code)
                assertEquals(3L, failed.durableArtifactBytes); assertEquals(6L, failed.transferredBytes)
            }
            assertEquals(AcquisitionState.INTERRUPTED, value(f.engine.status(healthy.plan.jobId, token)).state)
            f.response = { spec -> flowOf(Outcome.Success(chunk("abcdef", metadata = f.metadata(spec, length = 6)))) }
            val failed = value(f.engine.status(missing.plan.jobId, token))
            value(f.engine.resume(missing.plan.jobId, failed.revision, missing.consentId!!, token))
            val done = value(f.engine.run(missing.plan.jobId, token))
            assertEquals(AcquisitionState.COMPLETED, done.state); assertEquals(2, done.attempt); assertEquals(12L, done.transferredBytes)
        }
    }

    @Test fun committedRemovalSurvivesCancellationBeforeUnlinkAndRecoveryFinishesIt() = runTest {
        Fixture(this).use { f ->
            val q = f.queued(); value(f.engine.run(q.plan.jobId, token))
            f.database.onRemovalCommit = { currentCoroutineContext().cancel() }
            val remover = async { f.engine.removeArtifacts(q.consentId!!, token) }
            try { remover.await(); fail("Synthetic process interruption") } catch (_: CancellationException) { }
            assertEquals(AcquisitionState.CANCELLED, value(f.engine.status(q.plan.jobId, token)).state)
            assertEquals(SqlValue.Text("removal-pending-v1"), f.row(q.plan.jobId)["failure_payload"])
            assertTrue(f.directory.listFiles()!!.any { it.name.endsWith("-verified") })
            assertTrue(f.database.artifacts.isEmpty())
            f.database.onRemovalCommit = null
            value(f.engine.recover(token))
            assertTrue(f.directory.listFiles()!!.isEmpty())
            assertEquals(SqlValue.Null, f.row(q.plan.jobId)["failure_payload"])
            assertEquals(AcquisitionState.CANCELLED, value(f.engine.status(q.plan.jobId, token)).state)
        }
    }
    @Test fun unlinkDirectorySyncFailureRetainsRemovalMarkerUntilDurableRecoveryBarrier() = runTest {
        var failNextSync = false
        var syncs = 0
        Fixture(this) { syncs++; if (failNextSync) { failNextSync = false; throw IOException("Synthetic unlink directory sync failure") } }.use { f ->
            val q = f.queued(); value(f.engine.run(q.plan.jobId, token))
            failNextSync = true
            assertTrue(f.engine.removeArtifacts(q.consentId!!, token) is Outcome.Failure)
            assertEquals(SqlValue.Text("removal-pending-v1"), f.row(q.plan.jobId)["failure_payload"])
            assertTrue(f.directory.listFiles()!!.isEmpty()) // Unlink happened; durability was not established.
            val before = syncs
            value(f.engine.recover(token))
            assertTrue(syncs >= before + 2)
            assertEquals(SqlValue.Null, f.row(q.plan.jobId)["failure_payload"])
            assertEquals(AcquisitionState.CANCELLED, value(f.engine.status(q.plan.jobId, token)).state)
        }
    }
    @Test fun removalRevalidatesConsentMembershipAfterItsLiveListing() = runTest {
        Fixture(this).use { f ->
            val q = f.queued(); value(f.engine.run(q.plan.jobId, token))
            val newConsent = value(f.settings.approve(q.plan.request.intent, token))
            // Test seam models a consent writer winning after list's read, before per-job exclusion.
            f.database.afterListing = { f.row(q.plan.jobId)["consent_id"] = SqlValue.Text(newConsent.id.value) }
            value(f.engine.removeArtifacts(q.consentId!!, token))
            assertEquals(AcquisitionState.COMPLETED, value(f.engine.status(q.plan.jobId, token)).state)
            assertEquals(newConsent.id, value(f.engine.status(q.plan.jobId, token)).consentId)
            assertTrue(f.directory.listFiles()!!.any { it.name.endsWith("-verified") })
        }
    }

    @Test fun tokenOnlyCancellationEndsAllArtifactPinWaitsWithoutReleasingExistingPin() = runTest {
        Fixture(this).use { f ->
            val q = f.queued(); value(f.engine.run(q.plan.jobId, token))
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val pin = async { f.engine.withVerifiedArtifact(q.plan.jobId, token) { _, bytes -> entered.complete(Unit); release.await(); bytes.toList().last().final } }
            entered.await()
            var cancelled = false
            val stop = object : CancelToken {
                override val isCancelled get() = cancelled
                override fun throwIfCancelled() { if (cancelled) throw CancellationException("Synthetic token cancellation") }
            }
            val publicReader = async { f.engine.openArtifact(q.plan.jobId, stop).toList() }
            val importer = async { f.engine.withVerifiedArtifact(q.plan.jobId, stop) { _, bytes -> bytes.toList() } }
            val provider = async { f.engine.withArtifactFile(q.plan.jobId, stop) { _, _, bytes -> bytes.toList() } }
            runCurrent(); cancelled = true; advanceTimeBy(101); runCurrent()
            val allEndedBeforeRelease = listOf(publicReader, importer, provider).all { it.isCompleted && it.isCancelled }
            val originalPinUnaffected = !pin.isCompleted
            release.complete(Unit)
            assertTrue(pin.await())
            for (reader in listOf(publicReader, importer, provider)) try { reader.await(); fail("Token cancellation") } catch (_: CancellationException) { }
            assertTrue("Cancelled-token readers must leave contended pins within100ms", allEndedBeforeRelease)
            assertTrue(originalPinUnaffected)
            assertEquals(AcquisitionState.COMPLETED, value(f.engine.status(q.plan.jobId, token)).state)
        }
    }
    @Test fun removalStopsOnlySelectedConsentWorkerAndUnrelatedTransferCompletes() = runTest {
        Fixture(this).use { f ->
            val unrelated = f.queued(); val removable = f.queued()
            val release = CompletableDeferred<Unit>()
            var closed = 0
            f.response = { spec -> flow {
                try {
                    emit(Outcome.Success(chunk("abc", metadata = f.metadata(spec, length = 6), final = false)))
                    release.await()
                    emit(Outcome.Success(chunk("def", sequence = 1, offset = 3)))
                } finally { closed++ }
            } }
            val unrelatedRunner = async { f.engine.run(unrelated.plan.jobId, token) }
            val removableRunner = async { f.engine.run(removable.plan.jobId, token) }
            runCurrent()
            val before = value(f.engine.status(unrelated.plan.jobId, token))
            value(f.engine.removeArtifacts(removable.consentId!!, token))
            val unrelatedStillRunning = !unrelatedRunner.isCompleted && value(f.engine.status(unrelated.plan.jobId, token)) == before
            assertEquals(AcquisitionState.CANCELLED, value(f.engine.status(removable.plan.jobId, token)).state)
            try { removableRunner.await(); fail("Selected worker must stop") } catch (_: CancellationException) { }
            release.complete(Unit)
            val unrelatedResult = try { value(unrelatedRunner.await()) } catch (_: CancellationException) { null }
            assertTrue("Unrelated transfer must stay live during scoped removal", unrelatedStillRunning)
            assertEquals(AcquisitionState.COMPLETED, unrelatedResult?.state)
            assertEquals(2, closed)
        }
    }

    @Test fun staleStartAndResumeLeaveLiveTransferUntouchedUntilCompletion() = runTest {
        Fixture(this).use { f ->
            val release = CompletableDeferred<Unit>()
            var closed = false
            f.response = { spec -> flow {
                try {
                    emit(Outcome.Success(chunk("abc", metadata = f.metadata(spec, length = 6), final = false)))
                    release.await()
                    emit(Outcome.Success(chunk("def", sequence = 1, offset = 3)))
                } finally { closed = true }
            } }
            val q = f.queued()
            val runner = async { f.engine.run(q.plan.jobId, token) }
            runCurrent()
            val before = value(f.engine.status(q.plan.jobId, token))
            assertEquals(AcquisitionState.RUNNING, before.state)
            val staleStart = f.engine.start(q.plan.jobId, q.revision, q.consentId!!, token)
            assertEquals(FailureCode.STALE_REVISION, (staleStart as Outcome.Failure).error.code)
            assertFalse(closed); assertFalse(runner.isCompleted)
            val staleResume = f.engine.resume(q.plan.jobId, q.revision, q.consentId!!, token)
            assertEquals(FailureCode.STALE_REVISION, (staleResume as Outcome.Failure).error.code)
            assertFalse(closed); assertFalse(runner.isCompleted)
            val wrongState = f.engine.resume(q.plan.jobId, before.revision, q.consentId!!, token)
            assertEquals(FailureCode.INVALID_INPUT, (wrongState as Outcome.Failure).error.code)
            assertEquals(before, value(f.engine.status(q.plan.jobId, token)))
            assertFalse(closed); assertFalse(runner.isCompleted)
            release.complete(Unit)
            assertEquals(AcquisitionState.COMPLETED, value(runner.await()).state)
            assertTrue(closed); assertEquals(1, f.requests.size)
        }
    }

    @Test fun stalePauseDoesNotSignalCurrentWriterAndRecoveryJoinsIt() = runTest {
        Fixture(this).use { f ->
            var closed = false
            f.response = { spec -> flow { try { emit(Outcome.Success(chunk("abc", metadata = f.metadata(spec, length = 6), final = false))); awaitCancellation() } finally { closed = true } } }
            val q = f.queued(); val runner = async { f.engine.run(q.plan.jobId, token) }; runCurrent()
            assertTrue(f.engine.pause(q.plan.jobId, q.revision, token) is Outcome.Failure); assertFalse(closed)
            value(f.engine.recover(token)); assertTrue(closed)
            try { runner.await(); fail("Cancelled") } catch (_: CancellationException) { }
            val interrupted = value(f.engine.status(q.plan.jobId, token))
            assertEquals(AcquisitionState.INTERRUPTED, interrupted.state); assertEquals(6L, interrupted.transferredBytes)
        }
    }
    @Test fun statusRejectsCorruptArtifactDescriptorAndUnsupportedCursorOrder() = runTest {
        Fixture(this).use { f ->
            val q = f.queued(); value(f.engine.run(q.plan.jobId, token))
            f.database.artifacts.getValue(q.plan.jobId.value)["framing"] = SqlValue.Text("JSONL")
            assertEquals(FailureCode.CORRUPT_DATA, (f.engine.status(q.plan.jobId, token) as Outcome.Failure).error.code)
            assertTrue(f.engine.list(PageRequest(1, "acq-v2-asc:00"), token) is Outcome.Failure)
        }
    }
    @Test fun response416CannotProveCompletionAndCompressionLabelsMustMatchBytes() = runTest {
        Fixture(this).use { f ->
            f.response = { spec -> flowOf(Outcome.Success(chunk("", metadata = f.metadata(spec, 416)))) }
            val q = f.queued(); assertEquals(AcquisitionState.FAILED, value(f.engine.run(q.plan.jobId, token)).state)
            assertTrue(f.database.artifacts.isEmpty())
        }
        Fixture(this).use { f ->
            val request = f.request().let { it.copy(source = (it.source as AcquisitionSource.RemoteFile).copy(compression = ArtifactCompression.GZIP)) }
            val q = f.queued(request); assertEquals(AcquisitionState.FAILED, value(f.engine.run(q.plan.jobId, token)).state)
        }
    }

    /** Minimal SQL interpreter only for tests. Transactions copy/restore all three tables and latch
     * rollback; zero-row writes are deliberately Success so the engine must explicitly abort.
     */
    private class TestDatabase : Database {
        val jobs = linkedMapOf<String, MutableMap<String, SqlValue>>()
        val artifacts = linkedMapOf<String, MutableMap<String, SqlValue>>()
        val seen = linkedMapOf<Pair<String, String>, MutableMap<String, SqlValue>>()
        val cooldowns = linkedMapOf<String, MutableMap<String, SqlValue>>()
        var zeroPageCheckpoint = false
        var onCompletionCommit: (suspend () -> Unit)? = null
        var onRemovalCommit: (suspend () -> Unit)? = null
        var onCooldownCommit: (() -> Unit)? = null
        var afterListing: (() -> Unit)? = null
        private fun params(request: SqlRequest) = request.parameters.associate { it.name to it.value }
        override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> {
            cancel.throwIfCancelled()
            val p = params(request)
            if ("FROM acquisition_origin_cooldown" in request.statement) {
                val row = cooldowns[(p.getValue("origin") as SqlValue.Text).value]
                return Outcome.Success(SnapshotList.copyOf(listOfNotNull(row?.let {
                    RowData(SnapshotList.copyOf(it.map { (name, value) -> RowColumn(name, value) }))
                })))
            }
            var rows = jobs.values.toList()
            if ("WHERE j.job_id = :id" in request.statement) rows = rows.filter { it["job_id"] == p["id"] }
            if ("j.created_seconds < :cs" in request.statement) rows = rows.filter {
                val seconds = (it.getValue("created_seconds") as SqlValue.Integer).value; val nanos = (it.getValue("created_nano") as SqlValue.Integer).value; val id = (it.getValue("job_id") as SqlValue.Text).value
                val cs = (p.getValue("cs") as SqlValue.Integer).value; val cn = (p.getValue("cn") as SqlValue.Integer).value; val ci = (p.getValue("ci") as SqlValue.Text).value
                seconds < cs || seconds == cs && (nanos < cn || nanos == cn && id < ci)
            }
            rows = rows.sortedWith(compareByDescending<MutableMap<String, SqlValue>> { (it.getValue("created_seconds") as SqlValue.Integer).value }
                .thenByDescending { (it.getValue("created_nano") as SqlValue.Integer).value }.thenByDescending { (it.getValue("job_id") as SqlValue.Text).value })
            rows = rows.take((p["lim"] as? SqlValue.Integer)?.value?.toInt() ?: request.maxRows)
            val result = Outcome.Success(SnapshotList.copyOf(rows.map { j ->
                val a = artifacts[(j.getValue("job_id") as SqlValue.Text).value]
                val aliases = mapOf("artifact_key" to "artifact_key", "artifact_bytes" to "byte_count", "artifact_sha256" to "sha256", "artifact_framing" to "framing", "artifact_compression" to "compression", "artifact_payload" to "verified_payload", "artifact_version" to "payload_version", "artifact_seconds" to "completed_seconds", "artifact_nano" to "completed_nano")
                RowData(SnapshotList.copyOf((j + aliases.mapValues { a?.get(it.value) ?: SqlValue.Null }).map { RowColumn(it.key, it.value) }))
            }))
            if ("ORDER BY" in request.statement) afterListing?.let { callback -> afterListing = null; callback() }
            return result
        }
        private fun execute(request: SqlRequest): Outcome<DbWriteResult> {
            val p = params(request)
            if (request.statement.startsWith("INSERT INTO")) {
                val table = request.statement.substringAfter("INSERT INTO ").takeWhile { !it.isWhitespace() && it != '(' }
                val values = p.toMutableMap()
                when (table) {
                    "acquisition_job" -> { val id = (p.getValue("job_id") as SqlValue.Text).value; if (jobs.containsKey(id)) return failure(); jobs[id] = values }
                    "acquisition_artifact" -> { val id = (p.getValue("job_id") as SqlValue.Text).value; if (artifacts.containsKey(id)) return failure(); artifacts[id] = values }
                    "acquisition_seen_id" -> { val key = (p.getValue("job_id") as SqlValue.Text).value to (p.getValue("raw_id") as SqlValue.Text).value; if (seen.containsKey(key)) return failure(); seen[key] = values }
                    "acquisition_origin_cooldown" -> { val origin = (p.getValue("origin") as SqlValue.Text).value; if (cooldowns.containsKey(origin)) return failure(); cooldowns[origin] = values }
                    else -> error("Unexpected test SQL")
                }
                return Outcome.Success(DbWriteResult(1))
            }
            if (request.statement.startsWith("UPDATE acquisition_origin_cooldown")) {
                val row = cooldowns[(p.getValue("origin") as SqlValue.Text).value] ?: return Outcome.Success(DbWriteResult(0))
                if (row["revision"] != p["expected_revision"]) return Outcome.Success(DbWriteResult(0))
                if ("new_revision" in p) {
                    row["revision"] = p.getValue("new_revision")
                    row["interval_seconds"] = p.getValue("interval_seconds")
                    row["interval_nano"] = p.getValue("interval_nano")
                } else {
                    row["revision"] = SqlValue.Integer((row.getValue("revision") as SqlValue.Integer).value + 1)
                    row["interval_seconds"] = SqlValue.Integer(0)
                    row["interval_nano"] = SqlValue.Integer(0)
                }
                return Outcome.Success(DbWriteResult(1))
            }
            if (request.statement.startsWith("UPDATE acquisition_job")) {
                val row = jobs[(p.getValue("id") as SqlValue.Text).value] ?: return Outcome.Success(DbWriteResult(0))
                if (row["revision"] != p["rev"] || row["state"] != p["state"] || row["owner_token"] != p["owner"]) return Outcome.Success(DbWriteResult(0))
                if (zeroPageCheckpoint && "u_page_count" in p) { zeroPageCheckpoint = false; return Outcome.Success(DbWriteResult(0)) }
                p.filterKeys { it.startsWith("u_") }.forEach { row[it.key.removePrefix("u_")] = it.value }
                return Outcome.Success(DbWriteResult(1))
            }
            if (request.statement.startsWith("DELETE FROM acquisition_seen_id")) {
                val id = (p.getValue("id") as SqlValue.Text).value; val count = seen.keys.count { it.first == id }; seen.keys.removeAll { it.first == id }; return Outcome.Success(DbWriteResult(count.toLong()))
            }
            if (request.statement.startsWith("DELETE FROM acquisition_artifact")) {
                val count = if (artifacts.remove((p.getValue("id") as SqlValue.Text).value) == null) 0L else 1L; return Outcome.Success(DbWriteResult(count))
            }
            error("Unexpected test SQL: ${request.statement}")
        }
        private fun failure(): Outcome<Nothing> = Outcome.Failure(DomainFailure(FailureCode.PERSISTENCE, "Synthetic constraint", SnapshotList.empty()))
        override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> {
            val oldJobs = jobs.mapValues { it.value.toMutableMap() }; val oldArtifacts = artifacts.mapValues { it.value.toMutableMap() }; val oldSeen = seen.mapValues { it.value.toMutableMap() }
            val oldCooldowns = cooldowns.mapValues { it.value.toMutableMap() }
            var rollback: Outcome<Nothing>? = null
            val tx = object : DbTransaction {
                override val state: DbTransactionState get() = rollback?.let { DbTransactionState.RollbackOnly(it) } ?: DbTransactionState.Active
                override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> = this@TestDatabase.query(request, cancel)
                override suspend fun execute(request: SqlRequest, cancel: CancelToken): Outcome<DbWriteResult> {
                    cancel.throwIfCancelled(); return this@TestDatabase.execute(request).also { if (it !is Outcome.Success && rollback == null) rollback = when (it) {
                        is Outcome.Failure -> it
                        is Outcome.Unavailable -> it
                        is Outcome.Unknown -> it
                        is Outcome.Success -> error("Unreachable")
                    } }
                }
                override fun abort(error: DomainFailure): Nothing { if (rollback == null) rollback = Outcome.Failure(error); throw TransactionAbort(error) }
            }
            fun restore() { jobs.clear(); jobs.putAll(oldJobs); artifacts.clear(); artifacts.putAll(oldArtifacts); seen.clear(); seen.putAll(oldSeen); cooldowns.clear(); cooldowns.putAll(oldCooldowns) }
            try {
                currentCoroutineContext().ensureActive(); cancel.throwIfCancelled()
                val result = action(tx)
                currentCoroutineContext().ensureActive(); cancel.throwIfCancelled()
                if (rollback != null) { restore(); return rollback!! }
                if (result is Outcome<*> && result !is Outcome.Success<*>) { restore(); @Suppress("UNCHECKED_CAST") return result as Outcome<T> }
                if (artifacts.size > oldArtifacts.size) onCompletionCommit?.invoke()
                if (jobs.values.any { it["failure_payload"] == SqlValue.Text("removal-pending-v1") } && oldJobs.values.none { it["failure_payload"] == SqlValue.Text("removal-pending-v1") }) onRemovalCommit?.invoke()
                if (cooldowns != oldCooldowns) onCooldownCommit?.let { callback -> onCooldownCommit = null; callback() }
                return Outcome.Success(result)
            } catch (e: CancellationException) { restore(); throw e }
            catch (e: TransactionAbort) { restore(); return rollback ?: Outcome.Failure(e.failure) }
            catch (_: Exception) { restore(); return rollback ?: failure() }
        }
        override suspend fun capabilities(cancel: CancelToken): Outcome<DbCapabilities> = Outcome.Success(DbCapabilities("synthetic", 3, true, true, true, 999))
    }
}
