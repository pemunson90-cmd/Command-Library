package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.common.ConsentCoordinator
import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Required adapters must enforce reviewed provider/source profiles, not trust caller labels.
 * One engine and ConsentCoordinator per application graph. No detached execution scopes.
 * transferred_bytes includes reservations until a response settles; crash uncertainty stays charged.
 * List membership and states are live, ordered by immutable creation time and job ID.
 */
internal class LocalAcquisition(
    private val database: Database,
    private val network: NetworkClient,
    private val settings: SettingsRepository,
    private val consent: ConsentCoordinator,
    private val clock: Clock,
    private val ids: IdSource,
    private val files: DurableArtifactFiles,
    private val validateTrustedRequest: (AcquisitionRequest) -> Outcome<Unit>,
    private val endpointFor: (AcquisitionRequest, ResourceUri) -> EndpointProfile,
    private val connection: suspend () -> AcquisitionConnection,
) : DownloadService, DownloadRunner {
    private val admission = Mutex()
    private val controls = ConcurrentHashMap<String, Mutex>()
    private val fileGuards = ConcurrentHashMap<String, Mutex>()
    private val workers = ConcurrentHashMap<String, Deferred<Outcome<AcquisitionSnapshot>>>()
    private val searchStarts = Mutex()
    private var lastSearchStart: Duration? = null
    private fun control(id: EntityId) = controls[id.value] ?: Mutex().let { controls.putIfAbsent(id.value, it) ?: it }
    private fun fileGuard(id: EntityId) = fileGuards[id.value] ?: Mutex().let { fileGuards.putIfAbsent(id.value, it) ?: it }

    override suspend fun plan(request: AcquisitionRequest, cancel: CancelToken): Outcome<AcquisitionSnapshot> = outcome(cancel) {
        validate(request)
        val id = EntityId(ids.nextId()).also { bounded(it.value, 128) }
        val at = clock.utcNow()
        val unhashed = DownloadPlan(id, request, ZERO_HASH, at)
        val plan = unhashed.copy(planHash = hash(AcquisitionPayloadCodec.encodePlan(unhashed).toByteArray(Charsets.UTF_8)))
        val key = "p-" + hash(ids.nextId().toByteArray(Charsets.UTF_8)).hex
        val values = linkedMapOf<String, SqlValue>(
            "job_id" to text(id.value), "payload_version" to number(AcquisitionPayloadCodec.VERSION.toLong()),
            "plan_payload" to text(AcquisitionPayloadCodec.encodePlan(plan)), "plan_sha256" to text(plan.planHash.hex),
            "state" to text(AcquisitionState.PLANNED.name), "phase" to text(AcquisitionPhase.TRANSFERRING.name),
            "revision" to number(0), "attempt" to number(0), "owner_token" to SqlValue.Null,
            "created_seconds" to number(at.epochSeconds), "created_nano" to number(at.nanoAdjustment.toLong()),
            "updated_seconds" to number(at.epochSeconds), "updated_nano" to number(at.nanoAdjustment.toLong()),
            "started_seconds" to SqlValue.Null, "started_nano" to SqlValue.Null, "consent_id" to SqlValue.Null,
            "partial_key" to text(key), "durable_offset" to number(0), "transferred_bytes" to number(0),
            "record_count" to number(0), "page_count" to number(0), "expected_records" to SqlValue.Null,
            "next_page_uri" to SqlValue.Null, "first_page_sha256" to SqlValue.Null, "final_uri" to SqlValue.Null,
            "strong_etag" to SqlValue.Null, "last_modified" to SqlValue.Null,
            "expected_representation_bytes" to SqlValue.Null, "response_encoding" to SqlValue.Null, "failure_payload" to SqlValue.Null)
        requireSuccess(database.transaction({ tx ->
            checked(tx, insert("acquisition_job", values), cancel)
            load(id, cancel, tx).snapshot()
        }, cancel))
    }

    override suspend fun start(jobId: EntityId, expected: Revision, consentId: EntityId, cancel: CancelToken) =
        queue(jobId, expected, consentId, setOf(AcquisitionState.PLANNED), cancel)
    override suspend fun resume(jobId: EntityId, expected: Revision, consentId: EntityId, cancel: CancelToken) =
        queue(jobId, expected, consentId, setOf(AcquisitionState.PAUSED, AcquisitionState.INTERRUPTED,
            AcquisitionState.WAITING_CONSENT, AcquisitionState.WAITING_NETWORK, AcquisitionState.WAITING_WIFI, AcquisitionState.FAILED), cancel)

    private suspend fun queue(id: EntityId, expected: Revision, consentId: EntityId, allowed: Set<AcquisitionState>, cancel: CancelToken): Outcome<AcquisitionSnapshot> = outcome(cancel) {
        control(id).withLock {
            workers[id.value]?.cancelAndJoin()
            fileGuard(id).withLock {
                var row = load(id, cancel)
                expect(row, expected, allowed)
                if (row.state == AcquisitionState.FAILED && row.phase != AcquisitionPhase.VERIFYING && row.attempt > 0) row = restart(row, cancel)
                consent.withExclusive(cancel) {
                    policy(row, consentId, cancel)?.let { throw Stop(it) }
                    mutate(row, mapOf("consent_id" to text(consentId.value), "state" to text(AcquisitionState.QUEUED.name),
                        "owner_token" to SqlValue.Null), cancel).snapshot()
                }
            }
        }
    }

    override suspend fun pause(jobId: EntityId, expected: Revision, cancel: CancelToken) =
        stop(jobId, expected, AcquisitionState.PAUSED, cancel)
    override suspend fun cancel(jobId: EntityId, expected: Revision, cancel: CancelToken) =
        stop(jobId, expected, AcquisitionState.CANCELLED, cancel)

    private suspend fun stop(id: EntityId, expected: Revision, target: AcquisitionState, cancel: CancelToken): Outcome<AcquisitionSnapshot> = outcome(cancel) {
        control(id).withLock {
            // Validate before signalling. A stale caller cannot cancel a newer execution.
            val initial = load(id, cancel)
            expect(initial, expected, AcquisitionState.entries.toSet() - setOf(AcquisitionState.CANCELLED, AcquisitionState.COMPLETED))
            // File guard excludes completion/CAS. Worker cancellation joins before acknowledgment.
            // The signal may interrupt a transfer but not a synchronous fsync/transaction.
            workers[id.value]?.cancelAndJoin()
            fileGuard(id).withLock {
                val settled = load(id, cancel)
                if (settled.state == AcquisitionState.COMPLETED) throw Reject(failure(FailureCode.STALE_REVISION))
                // Internal cancellation settlement may advance revision; external competing controls are excluded.
                mutate(settled, mapOf("state" to text(target.name), "owner_token" to SqlValue.Null), cancel).snapshot()
            }
        }
    }

    override suspend fun status(jobId: EntityId, cancel: CancelToken): Outcome<AcquisitionSnapshot> = outcome(cancel) { load(jobId, cancel).snapshot() }

    override suspend fun list(page: PageRequest, cancel: CancelToken): Outcome<Page<AcquisitionSnapshot>> = outcome(cancel) {
        val after = page.cursor?.let(::decodeCursor)
        val where = if (after == null) "" else " WHERE (j.created_seconds < :cs OR (j.created_seconds = :cs AND (j.created_nano < :cn OR (j.created_nano = :cn AND j.job_id < :ci))))"
        val parameters = if (after == null) emptyMap() else mapOf("cs" to number(after.first.epochSeconds), "cn" to number(after.first.nanoAdjustment.toLong()), "ci" to text(after.second))
        val raw = requireSuccess(database.query(sql(SELECT + where + " ORDER BY j.created_seconds DESC,j.created_nano DESC,j.job_id DESC LIMIT :lim",
            parameters + ("lim" to number(page.limit + 1L)), page.limit + 1), cancel))
        val rows = raw.map(::decode)
        val items = rows.take(page.limit)
        Page(SnapshotList.copyOf(items.map { it.snapshot() }), if (rows.size > page.limit) encodeCursor(items.last()) else null,
            SnapshotReference(ProviderId("command-library"), null, null, null))
    }

    override fun observe(jobId: EntityId, cancel: CancelToken): Flow<Outcome<AcquisitionSnapshot>> = flow {
        var previous: Outcome<AcquisitionSnapshot>? = null
        while (true) {
            check(cancel)
            val current = status(jobId, cancel)
            if (current != previous) { emit(current); previous = current }
            if (current !is Outcome.Success || current.value.state in setOf(AcquisitionState.CANCELLED, AcquisitionState.COMPLETED)) break
            delay(200)
        }
    }

    override suspend fun run(jobId: EntityId, cancel: CancelToken): Outcome<AcquisitionSnapshot> = coroutineScope {
        var task: Deferred<Outcome<AcquisitionSnapshot>>? = null
        admission.withLock {
            control(jobId).withLock {
                if (workers[jobId.value]?.isActive == true) return@coroutineScope Outcome.Failure(failure(FailureCode.STALE_REVISION))
                task = async(start = CoroutineStart.LAZY) {
                    fileGuard(jobId).withLock { execute(jobId, cancel) }
                }
                workers[jobId.value] = task!!
                task!!.start()
            }
        }
        try { task!!.await() } finally { workers.remove(jobId.value, task!!) }
    }

    private suspend fun execute(id: EntityId, cancel: CancelToken): Outcome<AcquisitionSnapshot> {
        var owned: Stored? = null
        try {
            check(cancel)
            var row = load(id, cancel)
            if (row.state == AcquisitionState.COMPLETED) return Outcome.Success(row.snapshot())
            if (row.state != AcquisitionState.QUEUED) throw Reject(failure(FailureCode.INVALID_INPUT))
            validate(row.plan.request)
            val changes = mutableMapOf<String, SqlValue>("state" to text(AcquisitionState.RUNNING.name),
                "owner_token" to text(ids.nextId().also { bounded(it, 128) }))
            if (row.attempt == 0) changes["attempt"] = number(1)
            if (row.started == null) { val t = clock.utcNow(); changes["started_seconds"] = number(t.epochSeconds); changes["started_nano"] = number(t.nanoAdjustment.toLong()) }
            row = consent.withExclusive(cancel) {
                policy(row, row.consentId, cancel)?.let { throw Stop(it) }
                mutate(row, changes, cancel)
            }
            owned = row
            if (row.phase == AcquisitionPhase.VERIFYING) return Outcome.Success(reconcileCompletion(row, cancel).snapshot())
            row = preparePartial(row, cancel)
            owned = row
            row = when (row.plan.request.source) {
                is AcquisitionSource.RemoteFile -> remote(row, cancel) { owned = it }
                is AcquisitionSource.EnglishSearch -> search(row, cancel) { owned = it }
            }
            owned = row
            return Outcome.Success(complete(row, cancel).snapshot())
        } catch (e: CancellationException) {
            // A committed artifact stays completed even if cancellation arrives on return.
            withContext(NonCancellable) {
                withTimeoutOrNull(5000) {
                    try { settleStopped(id, owned, AcquisitionState.INTERRUPTED, null) }
                    catch (cleanup: CancellationException) { throw cleanup }
                    catch (_: Exception) { /* Recovery retains the durable owner/checkpoint. */ }
                }
            }
            throw e
        } catch (e: Stop) {
            return outcome(NO_CANCEL) { settleStopped(id, owned, e.state, null).snapshot() }
        } catch (e: Exception) {
            val error = when (e) { is Reject -> e.failure; is IllegalArgumentException -> failure(FailureCode.SOURCE_MISMATCH); is IOException -> failure(FailureCode.PERSISTENCE); else -> failure(FailureCode.INTERNAL) }
            if (owned == null) return Outcome.Failure(error)
            return outcome(NO_CANCEL) { settleStopped(id, owned, AcquisitionState.FAILED, error).snapshot() }
        }
    }

    private suspend fun settleStopped(id: EntityId, owned: Stored?, state: AcquisitionState, failure: DomainFailure?): Stored {
        val row = load(id, NO_CANCEL)
        if (owned == null) {
            if (row.state == AcquisitionState.QUEUED) return mutate(row, mapOf("state" to text(state.name), "owner_token" to SqlValue.Null), NO_CANCEL)
            return row
        }
        if (row.state == AcquisitionState.COMPLETED || row.owner != owned.owner || row.state != AcquisitionState.RUNNING) return row
        return mutate(row, mapOf("state" to text(state.name), "owner_token" to SqlValue.Null) +
            if (row.phase == AcquisitionPhase.VERIFYING) emptyMap() else mapOf("failure_payload" to (failure?.let { text("failure-v1:${it.code.name}") } ?: SqlValue.Null)), NO_CANCEL)
    }

    private suspend fun preparePartial(row: Stored, cancel: CancelToken): Stored {
        val key = partial(row)
        val length = files.length(key)
        if (length == null) {
            if (row.offset != 0L) return restart(row, cancel)
            files.create(key)
        } else files.truncateToCheckpoint(key, row.offset)
        return row
    }

    private suspend fun restart(row: Stored, cancel: CancelToken): Stored {
        if (row.attempt >= MAX_ATTEMPTS) throw Reject(failure(FailureCode.SOURCE_MISMATCH))
        val result = requireSuccess(database.transaction({ tx ->
            requireSuccess(tx.execute(sql("DELETE FROM acquisition_seen_id WHERE job_id = :id", mapOf("id" to text(row.id.value))), cancel))
            cas(tx, row, mapOf("attempt" to number(row.attempt + 1L), "durable_offset" to number(0), "record_count" to number(0), "page_count" to number(0),
                "expected_records" to SqlValue.Null, "next_page_uri" to SqlValue.Null, "first_page_sha256" to SqlValue.Null,
                "final_uri" to SqlValue.Null, "strong_etag" to SqlValue.Null, "last_modified" to SqlValue.Null,
                "expected_representation_bytes" to SqlValue.Null, "response_encoding" to SqlValue.Null, "failure_payload" to SqlValue.Null,
                "phase" to text(AcquisitionPhase.TRANSFERRING.name)), cancel)
            load(row.id, cancel, tx)
        }, cancel))
        files.delete(partial(row)); files.delete(candidate(row))
        files.create(partial(result))
        return result
    }

    private suspend fun remote(initial: Stored, cancel: CancelToken, checkpoint: (Stored) -> Unit): Stored {
        var row = initial
        val source = row.plan.request.source as AcquisitionSource.RemoteFile
        if (row.offset > 0 && (row.stringOrNull("strong_etag") == null || row.stringOrNull("final_uri") == null || row.stringOrNull("response_encoding") != "identity")) {
            row = restart(row, cancel); checkpoint(row)
        }
        while (true) {
            val offset = row.offset
            val bounds = row.plan.request.limits
            val knownLength = (source.expectedBytes as? Knowledge.Known)?.value
            val maximum = minOf(bounds.maxArtifactBytes, knownLength ?: bounds.maxArtifactBytes)
            val allowance = maximum // A ranged request may legally receive a full 200 representation.
            if (offset >= maximum) throw Reject(failure(FailureCode.SOURCE_MISMATCH)) // EOF cannot be guessed after a crash.
            val headers = mutableListOf(HttpHeader("Accept-Encoding", "identity"))
            if (offset > 0) { headers += HttpHeader("Range", "bytes=$offset-"); headers += HttpHeader("If-Range", row.string("strong_etag")) }
            var actual = 0L
            var metadata: HttpMetadata? = null
            var restartResponse = false
            row = reserve(row, allowance, cancel); checkpoint(row)
            val beforeReservation = row.transferred - allowance
            val reserved = row
            transfer(row, source.uri, headers, allowance, cancel) { chunk ->
                if (metadata == null) {
                    metadata = requireNotNull(chunk.metadata)
                    val m = metadata!!
                    validateMetadata(row.plan.request, source.uri, m)
                    if (m.statusCode == 429) throw RateLimited(m)
                    if (offset > 0 && m.statusCode == 200) { restartResponse = true; throw RestartResponse() }
                    if (offset == 0L) require(m.statusCode == 200) else require(m.statusCode == 206)
                    val encoding = header(m, "Content-Encoding") ?: "identity"
                    require(encoding == "identity")
                    val length = header(m, "Content-Length")?.let(::unsignedLong)
                    val representationLength = if (offset > 0) {
                        require(m.finalUri.value == row.string("final_uri") && strongEtag(m) == row.string("strong_etag"))
                        val range = requireNotNull(header(m, "Content-Range"))
                        val match = requireNotNull(Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matchEntire(range))
                        val start = unsignedLong(match.groupValues[1]); val end = unsignedLong(match.groupValues[2]); val total = unsignedLong(match.groupValues[3])
                        require(start == offset && end >= start && end < total && end == total - 1 && total <= maximum)
                        require(row.longOrNull("expected_representation_bytes") == total)
                        if (length != null) require(length == total - offset)
                        total
                    } else length
                    if (representationLength != null) require(representationLength in 1..maximum)
                    if (knownLength != null && representationLength != null) require(representationLength == knownLength)
                    row = mutate(row, mapOf("final_uri" to text(m.finalUri.value), "strong_etag" to nullable(strongEtag(m)),
                        "last_modified" to nullable(header(m, "Last-Modified")), "response_encoding" to text(encoding),
                        "expected_representation_bytes" to nullableNumber(representationLength)), cancel); checkpoint(row)
                } else if (chunk.metadata != null) require(chunk.metadata == metadata)
                val bytes = chunk.bytes.copyBytes()
                require(bytes.size <= bounds.maxChunkBytes && bytes.size.toLong() <= allowance - actual)
                actual += bytes.size
                val durable = files.append(partial(row), row.offset, bytes, bounds.maxArtifactBytes)
                row = mutate(row, mapOf("durable_offset" to number(durable)), cancel); checkpoint(row)
                if (chunk.final) {
                    row.longOrNull("expected_representation_bytes")?.let { require(row.offset == it) }
                    knownLength?.let { require(row.offset == it) }
                }
            }.let { signal ->
                when (signal) {
                    is RestartResponse -> restartResponse = true
                    is RateLimited -> {
                        // Known settled error response still consumes its conservative reservation;
                        // a user/scheduler retry waits rather than hammering the endpoint.
                        waitRateLimit(signal.metadata, cancel)
                        throw Reject(failure(FailureCode.TRANSPORT))
                    }
                    null -> Unit
                    else -> throw signal
                }
            }
            if (restartResponse) {
                // A response stopped before EOF remains conservatively reserved.
                row = restart(row, cancel); checkpoint(row); continue
            }
            require(metadata != null && row.offset > 0)
            row = mutate(row, mapOf("transferred_bytes" to number(checkedAdd(beforeReservation, actual))), cancel)
            checkpoint(row)
            // Reservation row is intentionally not used as a counter checkpoint after response settlement.
            require(row.transferred <= reserved.transferred)
            return row
        }
    }

    private suspend fun search(initial: Stored, cancel: CancelToken, checkpoint: (Stored) -> Unit): Stored {
        var row = initial
        val source = row.plan.request.source as AcquisitionSource.EnglishSearch
        if (row.pages > 0) {
            val fetched = page(row, source.firstPage, cancel) { row = it; checkpoint(it) }
            row = fetched.first
            if (fetched.second.first != row.string("first_page_sha256") || fetched.second.second.totalCards != row.long("expected_records")) {
                row = restart(row, cancel); checkpoint(row)
            }
        }
        while (true) {
            if (row.pages > 0 && row.stringOrNull("next_page_uri") == null) {
                require(row.records == row.long("expected_records")); return row
            }
            val limits = row.plan.request.limits
            require(row.pages < limits.maxPages)
            val uri = ResourceUri(row.stringOrNull("next_page_uri") ?: source.firstPage.value)
            require(EnglishSearchPageCodec.pageNumber(uri.value).toLong() == row.pages + 1)
            val fetched = page(row, uri, cancel) { row = it; checkpoint(it) }
            row = fetched.first
            val (digest, decoded) = fetched.second
            row.longOrNull("expected_records")?.let { require(it == decoded.totalCards) }
            require(decoded.records.size.toLong() <= limits.maxRecords - row.records)
            var offset = row.offset
            for (record in decoded.records) {
                val bytes = (record + "\n").toByteArray(Charsets.UTF_8)
                require(bytes.size.toLong() <= minOf(limits.maxArtifactBytes, limits.maxDecodedBytes) - offset)
                var position = 0
                while (position < bytes.size) {
                    val end = minOf(bytes.size, position + 64 * 1024)
                    offset = files.append(partial(row), offset, bytes.copyOfRange(position, end), minOf(limits.maxArtifactBytes, limits.maxDecodedBytes))
                    position = end
                }
            }
            // All page bytes are synced before IDs, cursor and checkpoint commit atomically.
            val previous = row
            row = requireSuccess(database.transaction({ tx ->
                for (record in decoded.records) {
                    val id = ScryfallCodec.parse(record, limits.maxNesting)["id"].asString.also { bounded(it, 256) }
                    checked(tx, insert("acquisition_seen_id", mapOf("job_id" to text(previous.id.value), "raw_id" to text(id), "page_number" to number(previous.pages + 1))), cancel)
                }
                cas(tx, previous, mapOf("durable_offset" to number(offset), "record_count" to number(previous.records + decoded.records.size),
                    "page_count" to number(previous.pages + 1), "expected_records" to number(decoded.totalCards), "next_page_uri" to nullable(decoded.nextPage),
                    "first_page_sha256" to text(previous.stringOrNull("first_page_sha256") ?: digest), "final_uri" to text(uri.value)), cancel)
                load(previous.id, cancel, tx)
            }, cancel)); checkpoint(row)
            if (decoded.nextPage == null) { require(row.records == decoded.totalCards); return row }
        }
    }

    private suspend fun page(initial: Stored, uri: ResourceUri, cancel: CancelToken, checkpoint: (Stored) -> Unit): Pair<Stored, Pair<String, EnglishSearchPage>> {
        val allowance = initial.plan.request.limits.maxPageBytes.toLong()
        var row = reserve(initial, allowance, cancel); checkpoint(row)
        val out = ByteArrayOutputStream()
        var metadata: HttpMetadata? = null
        val signal = searchStarts.withLock {
            val previous = lastSearchStart
            if (previous != null) {
                val elapsed = clock.monotonicNow() - previous
                require(elapsed >= Duration.ZERO)
                if (elapsed < 600.milliseconds) delay(600.milliseconds - elapsed)
            }
            transfer(row, uri, listOf(HttpHeader("Accept-Encoding", "identity")), allowance, cancel, beforeDispatch = { lastSearchStart = clock.monotonicNow() }) { chunk ->
                if (metadata == null) {
                    metadata = requireNotNull(chunk.metadata)
                    validateMetadata(row.plan.request, uri, metadata!!)
                    if (metadata!!.statusCode == 429) throw RateLimited(metadata!!)
                    require(metadata!!.statusCode == 200 && (header(metadata!!, "Content-Encoding") ?: "identity") == "identity")
                    header(metadata!!, "Content-Length")?.let { require(unsignedLong(it) <= allowance) }
                } else if (chunk.metadata != null) require(chunk.metadata == metadata)
                val bytes = chunk.bytes.copyBytes()
                require(bytes.size.toLong() <= allowance - out.size())
                out.write(bytes)
                if (chunk.final) header(metadata!!, "Content-Length")?.let { require(unsignedLong(it) == out.size().toLong()) }
            }
        }
        if (signal is RateLimited) { waitRateLimit(signal.metadata, cancel); throw Reject(failure(FailureCode.TRANSPORT)) }
        if (signal != null) throw signal
        val bytes = out.toByteArray()
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val raw = decoder.decode(ByteBuffer.wrap(bytes)).toString()
        val parsed = EnglishSearchPageCodec.decode(raw, uri.value, row.plan.request.limits.maxRecords, row.plan.request.limits.maxRecordBytes)
        row = mutate(row, mapOf("transferred_bytes" to number(row.transferred - allowance + bytes.size)), cancel); checkpoint(row)
        return row to (hash(bytes).hex to parsed)
    }

    /** Starts collection under the gate until its first suspension/handoff. A rendezvous prevents
     * a synchronous producer from doing a whole transfer while holding the settings gate.
     * A structured monitor cancels the active producer even when no bytes are arriving.
     */
    private suspend fun transfer(row: Stored, uri: ResourceUri, headers: List<HttpHeader>, allowance: Long, cancel: CancelToken,
        beforeDispatch: () -> Unit = {}, consume: suspend (ByteChunk) -> Unit): Exception? = try {
        coroutineScope {
            val channel = Channel<Outcome<ByteChunk>>(Channel.RENDEZVOUS)
            lateinit var producer: Job
            consent.withExclusive(cancel) {
                policy(row, row.consentId, cancel)?.let { throw Stop(it) }
                val endpoint = endpointFor(row.plan.request, uri)
                require(endpoint.secret == null && endpoint.retry.maxAttempts == 1)
                val spec = RequestSpec(endpoint, Request(HttpMethod.GET, uri, SnapshotList.copyOf(headers), null),
                    120.seconds, RequestCharge.NONCHARGED, RetryClassification.NEVER_AUTOMATIC, null, allowance)
                beforeDispatch()
                producer = launch(start = CoroutineStart.UNDISPATCHED) {
                    try { network.stream(spec, cancel).collect { channel.send(it) }; channel.close() }
                    catch (e: Throwable) { channel.close(e); throw e }
                }
            }
            val monitor = launch {
                while (isActive) { delay(100); check(cancel); policy(row, row.consentId, cancel)?.let { throw Stop(it) } }
            }
            try {
                var offset = 0L; var sequence = 0L; var ended = false
                for (result in channel) {
                    check(cancel)
                    val chunk = requireSuccess(result)
                    require(!ended && chunk.sequence == sequence && chunk.offset == offset && chunk.bytes.size <= row.plan.request.limits.maxChunkBytes)
                    if (sequence == 0L) require(chunk.metadata != null)
                    consume(chunk)
                    require(chunk.bytes.size.toLong() <= allowance - offset)
                    offset += chunk.bytes.size; sequence = checkedAdd(sequence, 1)
                    ended = chunk.final
                }
                require(ended)
                producer.join()
            } finally { monitor.cancelAndJoin(); producer.cancelAndJoin(); channel.cancel() }
        }
        null
    } catch (e: CancellationException) { throw e } catch (e: Exception) { e }

    private suspend fun reserve(row: Stored, allowance: Long, cancel: CancelToken): Stored {
        require(allowance > 0 && allowance <= row.plan.request.limits.maxTransferBytes - row.transferred) { "Transfer budget exhausted" }
        return consent.withExclusive(cancel) {
            policy(row, row.consentId, cancel)?.let { throw Stop(it) }
            mutate(row, mapOf("transferred_bytes" to number(row.transferred + allowance)), cancel)
        }
    }

    private suspend fun complete(row: Stored, cancel: CancelToken): Stored {
        val request = row.plan.request
        val source = request.source
        val context = currentCoroutineContext()
        val digest = files.verify(partial(row), row.offset, request.limits.maxArtifactBytes) { context.ensureActive(); cancel.throwIfCancelled() }
        if (source is AcquisitionSource.RemoteFile) {
            (source.expectedBytes as? Knowledge.Known)?.let { require(it.value == row.offset) }
            (source.expectedSha256 as? Knowledge.Known)?.let { require(it.value.hex.equals(digest, true)) }
            files.openRead(partial(row)).use {
                val gzip = it.read() == 0x1f && it.read() == 0x8b
                require(gzip == (source.compression == ArtifactCompression.GZIP))
            }
        } else require(row.records == row.long("expected_records") && row.stringOrNull("next_page_uri") == null)
        val artifact = VerifiedArtifact(row.id, if (source is AcquisitionSource.RemoteFile) source.framing else ArtifactFraming.JSONL,
            if (source is AcquisitionSource.RemoteFile) source.compression else ArtifactCompression.NONE, row.offset, ContentHash("SHA-256", digest),
            source is AcquisitionSource.RemoteFile && source.expectedSha256 is Knowledge.Known,
            if (source is AcquisitionSource.RemoteFile) Knowledge.Unknown("Raw representation; records not parsed", SnapshotList.empty()) else Knowledge.Known(row.records),
            row.pages, AcquisitionProvenance(request.intent.source, request.intent.resource, ResourceUri(row.string("final_uri")),
                request.sourceVersion, request.sourceDate, requireNotNull(row.started), clock.utcNow(),
                if (source is AcquisitionSource.RemoteFile) headerKnowledge(row.stringOrNull("strong_etag")) else Knowledge.Absent("Page validators do not describe derived JSONL"),
                if (source is AcquisitionSource.RemoteFile) headerKnowledge(row.stringOrNull("last_modified")) else Knowledge.Absent("Page validators do not describe derived JSONL"),
                if (source is AcquisitionSource.RemoteFile) AcquisitionConsistency.FILE_REPRESENTATION else AcquisitionConsistency.NON_ATOMIC_TRAVERSAL,
                request.languageScope, request.notice))
        // The complete verified descriptor is a durable prepare record. Recovery never guesses EOF.
        val prepared = consent.withExclusive(cancel) {
            policy(row, row.consentId, cancel)?.let { throw Stop(it) }
            mutate(row, mapOf("phase" to text(AcquisitionPhase.VERIFYING.name), "failure_payload" to text("prepared-v1:" + AcquisitionPayloadCodec.encodeArtifact(artifact))), cancel)
        }
        return reconcileCompletion(prepared, cancel)
    }

    private suspend fun reconcileCompletion(row: Stored, cancel: CancelToken): Stored {
        require(row.phase == AcquisitionPhase.VERIFYING)
        val artifact = preparedArtifact(row)
        if (files.length(candidate(row)) == null) files.promote(partial(row), candidate(row))
        // Reestablish directory durability after a previous rename/fsync failure before publishing.
        // The helper has no separate directory sync operation; a durable sentinel creates that barrier.
        val barrier = candidate(row) + "-sync"
        files.delete(barrier); files.create(barrier); files.delete(barrier)
        val context = currentCoroutineContext()
        require(files.verify(candidate(row), artifact.bytes, row.plan.request.limits.maxArtifactBytes) { context.ensureActive(); cancel.throwIfCancelled() } == artifact.sha256.hex)
        return consent.withExclusive(cancel) {
            policy(row, row.consentId, cancel)?.let { throw Stop(it) }
            requireSuccess(database.transaction({ tx ->
                checked(tx, insert("acquisition_artifact", mapOf("job_id" to text(row.id.value), "artifact_key" to text(candidate(row)),
                    "byte_count" to number(artifact.bytes), "sha256" to text(artifact.sha256.hex), "framing" to text(artifact.framing.name),
                    "compression" to text(artifact.compression.name), "verified_payload" to text(AcquisitionPayloadCodec.encodeArtifact(artifact)),
                    "payload_version" to number(AcquisitionPayloadCodec.VERSION.toLong()), "completed_seconds" to number(artifact.provenance.retrievalCompletedAt.epochSeconds),
                    "completed_nano" to number(artifact.provenance.retrievalCompletedAt.nanoAdjustment.toLong()))), cancel)
                cas(tx, row, mapOf("state" to text(AcquisitionState.COMPLETED.name), "phase" to text(AcquisitionPhase.COMPLETE.name),
                    "owner_token" to SqlValue.Null, "failure_payload" to SqlValue.Null), cancel)
                load(row.id, cancel, tx)
            }, cancel))
        }
    }

    override suspend fun recover(cancel: CancelToken): Outcome<Unit> = outcome(cancel) {
        admission.withLock {
            // Only live structured jobs prove quiescence; timestamps are not death evidence.
            workers.values.toList().forEach { it.cancelAndJoin() }
            var cursor: String? = null
            do {
                val batch = requireSuccess(list(PageRequest(100, cursor), cancel))
                for (snapshot in batch.items) control(snapshot.plan.jobId).withLock {
                    fileGuard(snapshot.plan.jobId).withLock {
                        var row = load(snapshot.plan.jobId, cancel)
                        if (row.state in setOf(AcquisitionState.CANCELLED, AcquisitionState.COMPLETED, AcquisitionState.PLANNED)) return@withLock
                        if (row.phase == AcquisitionPhase.VERIFYING) {
                            if (row.state == AcquisitionState.PAUSED) return@withLock
                            val blocked = consent.withExclusive(cancel) { policy(row, row.consentId, cancel) }
                            if (blocked == null) {
                                row = mutate(row, mapOf("state" to text(AcquisitionState.RUNNING.name), "owner_token" to text(ids.nextId())), cancel)
                                try { reconcileCompletion(row, cancel) }
                                catch (e: CancellationException) { throw e }
                                catch (e: Stop) { mutate(row, mapOf("state" to text(e.state.name), "owner_token" to SqlValue.Null), cancel) }
                                catch (_: Exception) { mutate(row, mapOf("state" to text(AcquisitionState.FAILED.name), "owner_token" to SqlValue.Null), cancel) }
                            } else mutate(row, mapOf("state" to text(blocked.name), "owner_token" to SqlValue.Null), cancel)
                        } else {
                            val length = files.length(partial(row))
                            if (length != null) files.truncateToCheckpoint(partial(row), row.offset)
                            else require(row.offset == 0L)
                            if (row.state in setOf(AcquisitionState.RUNNING, AcquisitionState.QUEUED)) mutate(row,
                                mapOf("state" to text(AcquisitionState.INTERRUPTED.name), "owner_token" to SqlValue.Null), cancel)
                        }
                    }
                }
                cursor = batch.nextCursor
            } while (cursor != null)
        }
    }

    override fun openArtifact(jobId: EntityId, cancel: CancelToken): Flow<ByteChunk> = flow {
        fileGuard(jobId).withLock { emitAll(verifiedBytes(loadForRead(jobId, cancel), cancel)) }
    }

    private suspend fun loadForRead(id: EntityId, cancel: CancelToken): Stored = try {
        load(id, cancel).also { requireArtifact(it) }
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { throw ArtifactReadException(failure(FailureCode.CORRUPT_DATA)) }

    /** Caller holds the pin; used by public open and internal importer/provider callbacks. */
    private fun verifiedBytes(row: Stored, cancel: CancelToken): Flow<ByteChunk> = flow {
        val artifact = requireArtifact(row)
        val digest = MessageDigest.getInstance("SHA-256")
        var offset = 0L; var sequence = 0L
        val input = try { files.openRead(row.string("artifact_key")) }
            catch (_: Exception) { throw ArtifactReadException(failure(FailureCode.CORRUPT_DATA)) }
        input.use {
            val buffer = ByteArray(64 * 1024)
            while (true) {
                check(cancel)
                val count = try { input.read(buffer) } catch (_: IOException) { throw ArtifactReadException(failure(FailureCode.CORRUPT_DATA)) }
                if (count == -1) break
                if (count.toLong() > artifact.bytes - offset) throw ArtifactReadException(failure(FailureCode.CORRUPT_DATA))
                digest.update(buffer, 0, count)
                emit(ByteChunk(sequence++, offset, SnapshotBytes(buffer.copyOf(count)), null, false)); offset += count
            }
        }
        if (offset != artifact.bytes || hex(digest.digest()) != artifact.sha256.hex) throw ArtifactReadException(failure(FailureCode.CORRUPT_DATA))
        emit(ByteChunk(sequence, offset, SnapshotBytes(ByteArray(0)), null, true))
    }

    /** Pin spans the entire action. Supplied flow must be consumed inside that action. */
    internal suspend fun <T> withVerifiedArtifact(jobId: EntityId, cancel: CancelToken,
        action: suspend (VerifiedArtifact, Flow<ByteChunk>) -> T): T = fileGuard(jobId).withLock {
        val row = loadForRead(jobId, cancel)
        action(requireArtifact(row), verifiedBytes(row, cancel))
    }

    /** Platform-only private provider callback. The descriptor must close before action returns. */
    internal suspend fun <T> withArtifactFile(jobId: EntityId, cancel: CancelToken,
        action: suspend (File, VerifiedArtifact, Flow<ByteChunk>) -> T): T = fileGuard(jobId).withLock {
        val row = loadForRead(jobId, cancel)
        val artifact = requireArtifact(row)
        val context = currentCoroutineContext()
        if (files.verify(row.string("artifact_key"), artifact.bytes, row.plan.request.limits.maxArtifactBytes) { context.ensureActive(); cancel.throwIfCancelled() } != artifact.sha256.hex)
            throw ArtifactReadException(failure(FailureCode.CORRUPT_DATA))
        action(files.locatorFile(row.string("artifact_key")), artifact, verifiedBytes(row, cancel))
    }

    /** Settings removal handler calls after its consent mutation has settled, outside the gate. */
    internal suspend fun removeArtifacts(consentId: EntityId, cancel: CancelToken): Outcome<Unit> = outcome(cancel) {
        admission.withLock {
            workers.values.toList().forEach { it.cancelAndJoin() }
            var cursor: String? = null
            do {
                val batch = requireSuccess(list(PageRequest(100, cursor), cancel))
                for (snapshot in batch.items) if (snapshot.consentId == consentId) control(snapshot.plan.jobId).withLock {
                    fileGuard(snapshot.plan.jobId).withLock {
                        val row = load(snapshot.plan.jobId, cancel)
                        // Mark unavailable before deletion. Recovery never republishes cancellation.
                        requireSuccess(database.transaction({ tx ->
                            requireSuccess(tx.execute(sql("DELETE FROM acquisition_artifact WHERE job_id = :id", mapOf("id" to text(row.id.value))), cancel))
                            cas(tx, row, mapOf("state" to text(AcquisitionState.CANCELLED.name), "phase" to text(AcquisitionPhase.TRANSFERRING.name),
                                "owner_token" to SqlValue.Null, "failure_payload" to SqlValue.Null), cancel)
                        }, cancel))
                        files.delete(partial(row)); files.delete(candidate(row))
                    }
                }
                cursor = batch.nextCursor
            } while (cursor != null)
        }
    }

    private fun requireArtifact(row: Stored): VerifiedArtifact {
        require(row.state == AcquisitionState.COMPLETED && row.phase == AcquisitionPhase.COMPLETE)
        return requireNotNull(row.artifact)
    }
    private suspend fun policy(row: Stored, consentId: EntityId?, cancel: CancelToken): AcquisitionState? {
        val s = requireSuccess(settings.read(cancel))
        if (consentId == null || s.consents.none { it.id == consentId && it.explicitUserApproval && it.permits(row.plan.request.intent) }) return AcquisitionState.WAITING_CONSENT
        val live = connection()
        if (!live.connected) return AcquisitionState.WAITING_NETWORK
        if ((row.plan.request.wifiOnly || s.cache.wifiOnlyDownloads) && !live.wifi) return AcquisitionState.WAITING_WIFI
        return null
    }
    private fun validate(request: AcquisitionRequest) {
        val uri = when (val source = request.source) { is AcquisitionSource.RemoteFile -> source.uri; is AcquisitionSource.EnglishSearch -> source.firstPage }
        validUri(uri)
        require(uri == request.intent.resource)
        bounded(request.intent.id.value, 128); bounded(request.intent.source.value, 128); bounded(request.intent.version, 256)
        (request.intent.expectedBytes as? Knowledge.Known)?.let { require(it.value in 1..request.limits.maxTransferBytes) }
        when (val source = request.source) {
            is AcquisitionSource.RemoteFile -> {
                require(source.allowedRedirectOrigins.size <= 16 && source.allowedRedirectOrigins.all { it.scheme == "https" })
                require(source.expectedBytes == request.intent.expectedBytes)
                require((source.expectedSha256 as? Knowledge.Known)?.value == request.intent.checksum)
                (source.expectedBytes as? Knowledge.Known)?.let { require(it.value <= request.limits.maxArtifactBytes) }
            }
            is AcquisitionSource.EnglishSearch -> {
                require(source.firstPage.value == EnglishSearchPageCodec.INITIAL_URI && source.queryPolicyVersion.isNotBlank())
                require(request.languageScope == AcquisitionLanguageScope.ENGLISH_CARDS && request.intent.kind == DownloadKind.CATALOG && request.intent.language == LanguageTag.ENGLISH)
                require(request.intent.checksum == null && request.limits.maxNesting >= 32)
            }
        }
        if (request.languageScope == AcquisitionLanguageScope.ENGLISH_RULES) require(request.intent.kind == DownloadKind.RULES && request.intent.language == LanguageTag.ENGLISH)
        if (request.languageScope == AcquisitionLanguageScope.RULINGS_LANGUAGE_NOT_ASSERTED) require(request.intent.kind == DownloadKind.CATALOG && request.intent.language == null)
        requireSuccess(validateTrustedRequest(request))
    }
    private fun validUri(uri: ResourceUri): URI {
        bounded(uri.value, 4096)
        return URI(uri.value).also { require(it.scheme == "https" && !it.host.isNullOrBlank() && it.rawUserInfo == null && it.rawFragment == null && it.port in listOf(-1, 443)) }
    }
    private fun validateMetadata(request: AcquisitionRequest, requested: ResourceUri, metadata: HttpMetadata) {
        val target = validUri(metadata.finalUri)
        val origin = Origin("https", target.host, if (target.port == -1) 443 else target.port)
        val initial = validUri(requested)
        when (val source = request.source) {
            is AcquisitionSource.RemoteFile -> require(origin == Origin("https", initial.host, if (initial.port == -1) 443 else initial.port) || origin in source.allowedRedirectOrigins)
            is AcquisitionSource.EnglishSearch -> require(metadata.finalUri == requested && EnglishSearchPageCodec.pageNumber(metadata.finalUri.value) > 0)
        }
        require(metadata.headers.size <= 128)
        for (h in metadata.headers) { bounded(h.name, 128); require(h.value.length <= 8192) }
    }
    private fun header(metadata: HttpMetadata, name: String): String? {
        val matches = metadata.headers.filter { it.name.equals(name, true) }
        require(matches.size <= 1)
        return matches.singleOrNull()?.value?.also { require(it.length <= 8192) }
    }
    private fun strongEtag(metadata: HttpMetadata): String? = header(metadata, "ETag")?.let {
        if (it.matches(Regex("\"[\\x21\\x23-\\x7e\\x80-\\xff]*\"")) && it.length <= 1024) it else null
    }
    private suspend fun waitRateLimit(metadata: HttpMetadata, cancel: CancelToken) {
        val wait = maxOf(30.seconds, (metadata.retryAfter as? Knowledge.Known)?.value ?: Duration.ZERO)
        require(wait.isFinite() && wait <= (24 * 60 * 60).seconds)
        var elapsed = Duration.ZERO
        while (elapsed < wait) { check(cancel); val step = minOf(100.milliseconds, wait - elapsed); delay(step); elapsed += step }
    }

    private suspend fun load(id: EntityId, cancel: CancelToken, tx: DbTransaction? = null): Stored {
        bounded(id.value, 128)
        val request = sql(SELECT + " WHERE j.job_id = :id", mapOf("id" to text(id.value)), 1)
        val rows = requireSuccess(if (tx == null) database.query(request, cancel) else tx.query(request, cancel))
        if (rows.size != 1) throw Reject(failure(FailureCode.INVALID_INPUT))
        return decode(rows.single())
    }
    private suspend fun mutate(row: Stored, changes: Map<String, SqlValue>, cancel: CancelToken): Stored = requireSuccess(database.transaction({ tx ->
        cas(tx, row, changes, cancel); load(row.id, cancel, tx)
    }, cancel))
    private suspend fun cas(tx: DbTransaction, row: Stored, changes: Map<String, SqlValue>, cancel: CancelToken) {
        if (row.revision.value == Long.MAX_VALUE) tx.abort(failure(FailureCode.STALE_REVISION))
        val now = clock.utcNow()
        val update = changes + mapOf("revision" to number(row.revision.value + 1), "updated_seconds" to number(now.epochSeconds), "updated_nano" to number(now.nanoAdjustment.toLong()))
        require(update.keys.all { it in MUTABLE })
        val params = update.mapKeys { "u_${it.key}" } + mapOf("id" to text(row.id.value), "rev" to number(row.revision.value), "state" to text(row.state.name), "owner" to nullable(row.owner))
        checked(tx, sql("UPDATE acquisition_job SET " + update.keys.joinToString(",") { "$it = :u_$it" } +
            " WHERE job_id = :id AND revision = :rev AND state = :state AND owner_token IS :owner", params), cancel)
    }
    private suspend fun checked(tx: DbTransaction, request: SqlRequest, cancel: CancelToken) {
        val write = requireSuccess(tx.execute(request, cancel))
        if (write.affectedRows != 1L) tx.abort(failure(FailureCode.STALE_REVISION))
    }
    private fun decode(data: RowData): Stored {
        try {
            val map = data.columns.associate { it.name to it.value }
            val plan = AcquisitionPayloadCodec.decodePlan((map.getValue("plan_payload") as SqlValue.Text).value)
            val row = Stored(plan, map)
            require(row.id.value == row.string("job_id") && row.long("payload_version") == AcquisitionPayloadCodec.VERSION.toLong())
            require(row.string("plan_sha256") == plan.planHash.hex && hash(AcquisitionPayloadCodec.encodePlan(plan.copy(planHash = ZERO_HASH)).toByteArray(Charsets.UTF_8)) == plan.planHash)
            require(row.created == plan.createdAt)
            row.revision; row.started
            if (row.attempt > 0) require(row.started != null && row.consentId != null)
            validate(plan.request)
            UtcTimestamp(row.long("updated_seconds"), exactInt(row.long("updated_nano")))
            require(row.attempt in 0..MAX_ATTEMPTS && row.offset >= 0 && row.transferred >= 0 && row.records >= 0 && row.pages >= 0)
            val limits = plan.request.limits
            require(row.offset <= limits.maxArtifactBytes && row.transferred <= limits.maxTransferBytes && row.records <= limits.maxRecords && row.pages <= limits.maxPages)
            row.longOrNull("expected_records")?.let { require(it in 1..limits.maxRecords) }
            row.longOrNull("expected_representation_bytes")?.let { require(it in 1..limits.maxArtifactBytes) }
            require(row.string("partial_key").matches(Regex("[a-z0-9][a-z0-9-]{0,89}")))
            require((row.state == AcquisitionState.RUNNING) == (row.owner != null))
            if (row.owner != null) bounded(row.owner!!, 128)
            if (row.phase == AcquisitionPhase.VERIFYING) preparedArtifact(row)
            if (row.state == AcquisitionState.COMPLETED) {
                require(row.phase == AcquisitionPhase.COMPLETE)
                val artifact = requireNotNull(row.artifact)
                require(artifact.jobId == row.id && artifact.bytes == row.offset && artifact.bytes == row.long("artifact_bytes") && artifact.sha256.hex == row.string("artifact_sha256"))
                require(artifact.framing.name == row.string("artifact_framing") && artifact.compression.name == row.string("artifact_compression"))
                require(row.long("artifact_version") == AcquisitionPayloadCodec.VERSION.toLong() && row.string("artifact_key") == candidate(row))
                require(artifact.provenance.provider == plan.request.intent.source && artifact.provenance.requestedUri == plan.request.intent.resource && artifact.provenance.languageScope == plan.request.languageScope)
                require(artifact.provenance.finalUri.value == row.string("final_uri") && artifact.provenance.retrievalStartedAt == row.started)
                require(artifact.provenance.sourceVersion == plan.request.sourceVersion && artifact.provenance.sourceDate == plan.request.sourceDate && artifact.provenance.notice == plan.request.notice)
                require(artifact.pageCount == row.pages)
                when (val source = plan.request.source) {
                    is AcquisitionSource.RemoteFile -> require(artifact.framing == source.framing && artifact.compression == source.compression && artifact.pageCount == 0L && row.records == 0L && artifact.provenance.consistency == AcquisitionConsistency.FILE_REPRESENTATION)
                    is AcquisitionSource.EnglishSearch -> require(artifact.framing == ArtifactFraming.JSONL && artifact.compression == ArtifactCompression.NONE && artifact.recordCount == Knowledge.Known(row.records) && artifact.provenance.consistency == AcquisitionConsistency.NON_ATOMIC_TRAVERSAL)
                }
                require(artifact.provenance.retrievalCompletedAt == UtcTimestamp(row.long("artifact_seconds"), exactInt(row.long("artifact_nano"))))
            } else { require(row.artifact == null && row.phase != AcquisitionPhase.COMPLETE) }
            row.stringOrNull("failure_payload")?.let { if (row.phase != AcquisitionPhase.VERIFYING) require(it.startsWith("failure-v1:") && FailureCode.valueOf(it.removePrefix("failure-v1:")) in FailureCode.entries) }
            return row
        } catch (e: Exception) { throw Reject(failure(FailureCode.CORRUPT_DATA)) }
    }
    private fun preparedArtifact(row: Stored): VerifiedArtifact {
        val payload = row.string("failure_payload")
        require(payload.startsWith("prepared-v1:"))
        return AcquisitionPayloadCodec.decodeArtifact(payload.removePrefix("prepared-v1:")).also {
            require(it.jobId == row.id && it.bytes == row.offset && it.pageCount == row.pages && it.provenance.requestedUri == row.plan.request.intent.resource)
            require(it.provenance.provider == row.plan.request.intent.source && it.provenance.languageScope == row.plan.request.languageScope && it.provenance.retrievalStartedAt == row.started)
            require(it.provenance.finalUri.value == row.string("final_uri") && it.provenance.sourceVersion == row.plan.request.sourceVersion && it.provenance.sourceDate == row.plan.request.sourceDate && it.provenance.notice == row.plan.request.notice)
            when (val source = row.plan.request.source) {
                is AcquisitionSource.RemoteFile -> require(it.framing == source.framing && it.compression == source.compression && it.provenance.consistency == AcquisitionConsistency.FILE_REPRESENTATION)
                is AcquisitionSource.EnglishSearch -> require(it.framing == ArtifactFraming.JSONL && it.compression == ArtifactCompression.NONE && it.recordCount == Knowledge.Known(row.records) && row.records == row.long("expected_records") && row.stringOrNull("next_page_uri") == null && it.provenance.consistency == AcquisitionConsistency.NON_ATOMIC_TRAVERSAL)
            }
        }
    }
    private inner class Stored(val plan: DownloadPlan, val values: Map<String, SqlValue>) {
        val id get() = plan.jobId
        val revision get() = Revision(long("revision"))
        val state get() = AcquisitionState.valueOf(string("state"))
        val phase get() = AcquisitionPhase.valueOf(string("phase"))
        val attempt get() = exactInt(long("attempt"))
        val owner get() = stringOrNull("owner_token")
        val offset get() = long("durable_offset")
        val transferred get() = long("transferred_bytes")
        val records get() = long("record_count")
        val pages get() = long("page_count")
        val created get() = UtcTimestamp(long("created_seconds"), exactInt(long("created_nano")))
        val started get(): UtcTimestamp? { val seconds = longOrNull("started_seconds"); val nano = longOrNull("started_nano"); require((seconds == null) == (nano == null)); return seconds?.let { UtcTimestamp(it, exactInt(nano!!)) } }
        val consentId get() = stringOrNull("consent_id")?.let(::EntityId)
        val artifact get() = stringOrNull("artifact_payload")?.let(AcquisitionPayloadCodec::decodeArtifact)
        fun string(key: String) = (values.getValue(key) as SqlValue.Text).value
        fun stringOrNull(key: String): String? = when (val v = values.getValue(key)) { SqlValue.Null -> null; is SqlValue.Text -> v.value; else -> error("Invalid text") }
        fun long(key: String) = (values.getValue(key) as SqlValue.Integer).value
        fun longOrNull(key: String): Long? = when (val v = values.getValue(key)) { SqlValue.Null -> null; is SqlValue.Integer -> v.value; else -> error("Invalid integer") }
        fun snapshot() = AcquisitionSnapshot(plan, revision, attempt, state, phase, transferred, offset, records, pages,
            longOrNull("expected_records")?.let { Knowledge.Known(it) } ?: Knowledge.Unknown("Not yet asserted by source", SnapshotList.empty()),
            consentId, UtcTimestamp(long("updated_seconds"), exactInt(long("updated_nano"))),
            artifact?.let { Knowledge.Known(it) } ?: Knowledge.Absent("No completed artifact"),
            stringOrNull("failure_payload")?.takeIf { it.startsWith("failure-v1:") }?.let { failure(FailureCode.valueOf(it.removePrefix("failure-v1:"))) })
    }
    private fun expect(row: Stored, expected: Revision, states: Set<AcquisitionState>) {
        if (row.revision != expected) throw Reject(failure(FailureCode.STALE_REVISION))
        if (row.state !in states) throw Reject(failure(FailureCode.INVALID_INPUT))
    }
    private fun partial(row: Stored) = row.string("partial_key") + "-a${row.attempt}"
    private fun candidate(row: Stored) = partial(row) + "-verified"
    private fun encodeCursor(row: Stored): String = "acq-v1-desc:" + hex("${row.created.epochSeconds}:${row.created.nanoAdjustment}:${row.id.value}".toByteArray(Charsets.UTF_8))
    private fun decodeCursor(cursor: String): Pair<UtcTimestamp, String> {
        require(cursor.startsWith("acq-v1-desc:") && cursor.length <= PageRequest.MAX_CURSOR_BYTES)
        val encoded = cursor.removePrefix("acq-v1-desc:")
        require(encoded.isNotEmpty() && encoded.length % 2 == 0 && encoded.all { it in "0123456789abcdef" })
        val raw = ByteArray(encoded.length / 2) { encoded.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        val decoded = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString()
        val fields = decoded.split(':', limit = 3); require(fields.size == 3)
        val time = UtcTimestamp(fields[0].toLong(), fields[1].toInt()); bounded(fields[2], 128)
        require(fields[0] == time.epochSeconds.toString() && fields[1] == time.nanoAdjustment.toString())
        return time to fields[2]
    }
    private suspend fun check(cancel: CancelToken) { currentCoroutineContext().ensureActive(); cancel.throwIfCancelled() }
    private suspend fun <T> outcome(cancel: CancelToken, action: suspend () -> T): Outcome<T> = try { check(cancel); Outcome.Success(action()) }
        catch (e: CancellationException) { throw e }
        catch (e: Reject) { Outcome.Failure(e.failure) }
        catch (e: Stop) { Outcome.Unavailable(if (e.state == AcquisitionState.WAITING_CONSENT) UnavailableReason.CONSENT_REQUIRED else UnavailableReason.OFFLINE, "Acquisition policy prevents transfer") }
        catch (_: IllegalArgumentException) { Outcome.Failure(failure(FailureCode.INVALID_INPUT)) }
        catch (_: IOException) { Outcome.Failure(failure(FailureCode.PERSISTENCE)) }
        catch (_: Exception) { Outcome.Failure(failure(FailureCode.INTERNAL)) }
    private fun <T> requireSuccess(result: Outcome<T>): T = when (result) {
        is Outcome.Success -> result.value
        is Outcome.Failure -> throw Reject(result.error)
        is Outcome.Unavailable -> throw Reject(failure(if (result.reason == UnavailableReason.CONSENT_REQUIRED || result.reason == UnavailableReason.REVOKED) FailureCode.PERMISSION else FailureCode.TRANSPORT))
        is Outcome.Unknown -> throw Reject(failure(FailureCode.CORRUPT_DATA))
    }
    private class Reject(val failure: DomainFailure) : RuntimeException()
    private class Stop(val state: AcquisitionState) : RuntimeException()
    private class RestartResponse : RuntimeException()
    private class RateLimited(val metadata: HttpMetadata) : RuntimeException()
    private companion object {
        const val MAX_ATTEMPTS = 3
        val ZERO_HASH = ContentHash("SHA-256", "0".repeat(64))
        val NO_CANCEL = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
        const val SELECT = "SELECT j.*,a.artifact_key AS artifact_key,a.byte_count AS artifact_bytes,a.sha256 AS artifact_sha256,a.framing AS artifact_framing,a.compression AS artifact_compression,a.verified_payload AS artifact_payload,a.payload_version AS artifact_version,a.completed_seconds AS artifact_seconds,a.completed_nano AS artifact_nano FROM acquisition_job j LEFT JOIN acquisition_artifact a ON a.job_id=j.job_id"
        val MUTABLE = setOf("state", "phase", "revision", "attempt", "owner_token", "updated_seconds", "updated_nano", "started_seconds", "started_nano", "consent_id", "durable_offset", "transferred_bytes", "record_count", "page_count", "expected_records", "next_page_uri", "first_page_sha256", "final_uri", "strong_etag", "last_modified", "expected_representation_bytes", "response_encoding", "failure_payload")
        fun text(value: String): SqlValue = SqlValue.Text(value)
        fun number(value: Long): SqlValue = SqlValue.Integer(value)
        fun nullable(value: String?): SqlValue = value?.let(::text) ?: SqlValue.Null
        fun nullableNumber(value: Long?): SqlValue = value?.let(::number) ?: SqlValue.Null
        fun sql(statement: String, values: Map<String, SqlValue>, maxRows: Int = 1) = SqlRequest(statement, SnapshotList.copyOf(values.map { SqlParameter(it.key, it.value) }), maxRows)
        fun insert(table: String, values: Map<String, SqlValue>) = sql("INSERT INTO $table (${values.keys.joinToString(",")}) VALUES (${values.keys.joinToString(",") { ":$it" }})", values)
        fun failure(code: FailureCode) = DomainFailure(code, "Acquisition ${code.name.lowercase().replace('_', ' ')}", SnapshotList.empty())
        fun hash(bytes: ByteArray) = ContentHash("SHA-256", hex(MessageDigest.getInstance("SHA-256").digest(bytes)))
        fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
        fun checkedAdd(left: Long, right: Long): Long { require(left >= 0 && right >= 0 && right <= Long.MAX_VALUE - left); return left + right }
        fun exactInt(value: Long): Int { require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()); return value.toInt() }
        fun unsignedLong(value: String): Long { require(value.matches(Regex("[0-9]{1,19}"))); return value.toLong().also { require(it >= 0) } }
        fun bounded(value: String, max: Int) { require(value.isNotBlank() && value.length <= max && value.toByteArray(Charsets.UTF_8).size <= max) }
        fun headerKnowledge(value: String?): Knowledge<String> = value?.let { Knowledge.Known(it) } ?: Knowledge.Absent("Not supplied by source")
    }
}

internal data class AcquisitionConnection(val connected: Boolean, val wifi: Boolean)
