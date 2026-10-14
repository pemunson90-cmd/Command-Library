package com.architectpsg.commandlibrary.feature.life.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.life.domain.LifeVariantManager
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/** Instance-local committed-state ordering; no GameService binding, background jobs or cross-process watch. */
public class SqlLifeGameSessions internal constructor(db: Database, codec: LifePayloadCodecPort, ids: IdSource, random: RandomSource,
    private val clock: Clock, private val processSessionId: EntityId, gameSource: ProviderId, variants: LifeVariantManager?) {
    public constructor(db: Database, codec: LifePayloadCodecPort, ids: IdSource, random: RandomSource,
        clock: Clock, processSessionId: EntityId, gameSource: ProviderId) : this(db, codec, ids, random, clock, processSessionId, gameSource, null)

    public companion object {
        /** Caller must retain and supply the exact manifest configuration again after reopening saved games. */
        public fun withCustomVariants(db: Database, codec: LifePayloadCodecPort, ids: IdSource, random: RandomSource,
            clock: Clock, processSessionId: EntityId, gameSource: ProviderId,
            manifests: SnapshotList<CustomVariantManifest>): Outcome<SqlLifeGameSessions> {
            return try {
                val manager = gameValue(customVariantManager(manifests, processSessionId, gameSource))
                Outcome.Success(SqlLifeGameSessions(db, codec, ids, random, clock, processSessionId, gameSource, manager))
            } catch (problem: GameStoreProblem) { problem.status }
        }
    }
    private val sourceMonitor = Any()
    private val clockMonitor = Any()
    private val safeIds = object : IdSource { override fun nextId(): String = synchronized(sourceMonitor) { ids.nextId() } }
    private val safeRandom = object : RandomSource { override fun nextInt(upperBound: Int): Int = synchronized(sourceMonitor) { random.nextInt(upperBound) } }
    private val lifecycle = SqlLifeGameLifecycle(db, codec, safeIds, gameSource, variants)
    private val commands = SqlLifeGameCommands(db, codec, safeIds, safeRandom, variants)
    private class Entry {
        val lock = Mutex()
        var references = 0
        var latest: StoredLifeGame? = null
        val watchers = mutableSetOf<Channel<StoredLifeGame>>()
    }
    private val registryLock = Mutex()
    private val entries = mutableMapOf<GameId, Entry>()

    /** Unlike the raw idempotent creation receipt, returns latest checked current after creation/retry. */
    public suspend fun create(input: GameCreation, cancel: CancelToken): Outcome<StoredLifeGame> = outcome {
        cancel.throwIfCancelled()
        val created = gameValue(lifecycle.create(input, observation(), cancel))
        locked(created.checkpoint.snapshot.id) { entry ->
            committed(entry, lifecycle.read(created.checkpoint.snapshot.id, cancel))
        }
    }
    /** Checked serialized read without sampling the clock or implicitly recovering/settling timers. */
    public suspend fun read(id: GameId, cancel: CancelToken): Outcome<StoredLifeGame> = outcome {
        locked(id) { entry -> cancel.throwIfCancelled(); committed(entry, lifecycle.read(id, cancel)) }
    }
    public suspend fun resume(id: GameId, cancel: CancelToken): Outcome<StoredLifeGame> = outcome {
        locked(id) { entry -> cancel.throwIfCancelled(); committed(entry, lifecycle.resume(id, observation(), cancel)) }
    }
    public suspend fun dispatch(id: GameId, action: GameAction, expected: Revision, cancel: CancelToken): Outcome<StoredLifeGame> = outcome {
        locked(id) { entry ->
            cancel.throwIfCancelled()
            val reading = observation()
            committed(entry, lifecycle.resume(id, reading, cancel))
            committed(entry, commands.dispatch(id, action, expected, reading, cancel))
        }
    }
    public suspend fun undo(id: GameId, cancel: CancelToken): Outcome<StoredLifeGame> = undoCurrent(id, null, cancel)
    public suspend fun undo(id: GameId, expected: Revision, cancel: CancelToken): Outcome<StoredLifeGame> = undoCurrent(id, expected, cancel)
    private suspend fun undoCurrent(id: GameId, expected: Revision?, cancel: CancelToken): Outcome<StoredLifeGame> = outcome {
        locked(id) { entry ->
            cancel.throwIfCancelled()
            val reading = observation()
            committed(entry, lifecycle.resume(id, reading, cancel))
            committed(entry, commands.undo(id, expected, reading, cancel))
        }
    }

    /** Cancel the collector Job to stop an idle watch; token is checked at load and each emission. */
    public fun watch(id: GameId, cancel: CancelToken): Flow<Outcome<StoredLifeGame>> = flow {
        cancel.throwIfCancelled()
        val entry = acquire(id)
        val channel = Channel<StoredLifeGame>(Channel.CONFLATED)
        try {
            val initial = outcome {
                entry.lock.withLock {
                    cancel.throwIfCancelled()
                    val current = committed(entry, lifecycle.read(id, cancel))
                    entry.watchers += channel
                    current
                }
            }
            cancel.throwIfCancelled()
            emit(initial)
            if (initial is Outcome.Success) for (current in channel) {
                cancel.throwIfCancelled()
                emit(Outcome.Success(current))
            }
        } finally {
            withContext(NonCancellable) {
                entry.lock.withLock { entry.watchers -= channel; channel.close() }
                release(id, entry)
            }
        }
    }

    private suspend fun acquire(id: GameId): Entry = registryLock.withLock {
        entries.getOrPut(id) { Entry() }.also { it.references++ }
    }
    private suspend fun release(id: GameId, entry: Entry) = registryLock.withLock {
        entry.references--
        if (entry.references == 0) entries.remove(id)
    }
    private suspend fun <T> locked(id: GameId, action: suspend (Entry) -> T): T {
        val entry = acquire(id)
        try { return entry.lock.withLock { action(entry) } }
        finally { withContext(NonCancellable) { release(id, entry) } }
    }
    private suspend fun committed(entry: Entry, result: Outcome<StoredLifeGame>): StoredLifeGame {
        val current = gameValue(result)
        // Database already returned success. Publish before another same-game operation can run.
        return withContext(NonCancellable) {
            val old = entry.latest
            if (old != null && old != current) {
                val oldRevision = old.checkpoint.snapshot.revision.value
                val revision = current.checkpoint.snapshot.revision.value
                requireGame(revision >= oldRevision && current.sequence >= old.sequence &&
                    (revision != oldRevision || current.sequence != old.sequence), FailureCode.CORRUPT_DATA,
                    "Committed game state regressed or conflicted at the same revision/sequence")
            }
            if (old != current) {
                entry.latest = current
                entry.watchers.forEach { it.trySend(current) }
            }
            current
        }
    }
    private fun observation(): GameTimeObservation {
        gameIdentity(processSessionId.value, FailureCode.INVALID_INPUT)
        return try { synchronized(clockMonitor) { GameTimeObservation(clock.monotonicNow(), clock.utcNow(), processSessionId) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { rejectGame(FailureCode.INTERNAL, "Game clock source failed") }
    }
    private suspend fun <T> outcome(action: suspend () -> T): Outcome<T> =
        try { Outcome.Success(action()) } catch (problem: GameStoreProblem) { problem.status }
}
