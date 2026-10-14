package com.architectpsg.commandlibrary.feature.life.persistence

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Terminal typed status for legacy GameService.watch; observe retains the full Outcome in its flow. */
public class LifeGameObservationException internal constructor(public val status: Outcome<Nothing>) :
    IllegalStateException("Game observation ended without a snapshot") {
    init { require(status !is Outcome.Success) { "Observation exception requires a non-success status" } }
}

/** Real persisted-port composition. No application DI, source installation, UI or background cadence. */
public class SqlPersistedGameService private constructor(private val sessions: SqlLifeGameSessions,
    db: Database, codec: LifePayloadCodecPort) : PersistedGameService {
    public constructor(db: Database, codec: LifePayloadCodecPort, ids: IdSource, random: RandomSource,
        clock: Clock, processSessionId: EntityId, gameSource: ProviderId) :
        this(SqlLifeGameSessions(db, codec, ids, random, clock, processSessionId, gameSource), db, codec)

    private val eventReader = SqlLifeEventReader(db, codec)
    private val historyReader = SqlLifeGameHistory(db, codec)

    public companion object {
        /** Configuration validation is the same no-source/no-database operation as the session factory. */
        public fun withCustomVariants(db: Database, codec: LifePayloadCodecPort, ids: IdSource, random: RandomSource,
            clock: Clock, processSessionId: EntityId, gameSource: ProviderId,
            manifests: SnapshotList<CustomVariantManifest>): Outcome<SqlPersistedGameService> =
            SqlLifeGameSessions.withCustomVariants(db, codec, ids, random, clock, processSessionId, gameSource, manifests)
                .mapServiceValue { SqlPersistedGameService(it, db, codec) }
    }

    override suspend fun create(input: GameCreation, cancel: CancelToken): Outcome<GameSnapshot> = sessions.create(input, cancel).snapshot()
    override suspend fun read(id: GameId, cancel: CancelToken): Outcome<GameSnapshot> = sessions.read(id, cancel).snapshot()
    override suspend fun resume(id: GameId, cancel: CancelToken): Outcome<GameSnapshot> = sessions.resume(id, cancel).snapshot()
    override suspend fun dispatch(id: GameId, action: GameAction, expected: Revision, cancel: CancelToken): Outcome<GameSnapshot> =
        sessions.dispatch(id, action, expected, cancel).snapshot()
    override suspend fun undo(id: GameId, cancel: CancelToken): Outcome<GameSnapshot> = sessions.undo(id, cancel).snapshot()
    override suspend fun undo(id: GameId, expected: Revision, cancel: CancelToken): Outcome<GameSnapshot> = sessions.undo(id, expected, cancel).snapshot()
    override suspend fun events(id: GameId, page: PageRequest, cancel: CancelToken): Outcome<Page<GameEvent>> = eventReader.events(id, page, cancel)
    override suspend fun history(filter: GameHistoryFilter, page: PageRequest, cancel: CancelToken): Outcome<GameHistoryPage> = historyReader.history(filter, page, cancel)
    override fun observe(id: GameId, cancel: CancelToken): Flow<Outcome<GameSnapshot>> = sessions.watch(id, cancel).map { it.snapshot() }
    override fun watch(id: GameId): Flow<GameSnapshot> = observe(id, IdleWatchToken).map { status ->
        when (status) {
            is Outcome.Success -> status.value
            is Outcome.Failure -> throw LifeGameObservationException(status)
            is Outcome.Unavailable -> throw LifeGameObservationException(status)
            is Outcome.Unknown -> throw LifeGameObservationException(status)
        }
    }
}

/** Job cancellation closes an idle legacy collector; its port has no caller token parameter. */
private object IdleWatchToken : CancelToken {
    override val isCancelled: Boolean = false
    override fun throwIfCancelled(): Unit = Unit
}
private fun Outcome<StoredLifeGame>.snapshot(): Outcome<GameSnapshot> = mapServiceValue { it.checkpoint.snapshot }
private fun <T, R> Outcome<T>.mapServiceValue(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
    is Outcome.Unavailable -> this
    is Outcome.Unknown -> this
}
