package com.architectpsg.commandlibrary.feature.life.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.life.domain.LifeGameInitializer
import com.architectpsg.commandlibrary.feature.life.domain.LifeTimingControls
import com.architectpsg.commandlibrary.feature.life.domain.LifeTimingEngine
import com.architectpsg.commandlibrary.feature.life.domain.LifeVariantInitializer
import kotlin.coroutines.cancellation.CancellationException

/** Actual lifecycle composition only; no registered GameService, cadence, publication or source authority. */
public class SqlLifeGameLifecycle internal constructor(db: Database, codec: LifePayloadCodecPort,
    private val ids: IdSource, private val gameSource: ProviderId, private val variants: LifeVariantInitializer?) {
    public constructor(db: Database, codec: LifePayloadCodecPort, ids: IdSource, gameSource: ProviderId) : this(db, codec, ids, gameSource, null)
    private val games = SqlLifeGameStore(db, codec)
    private val mutations = SqlLifeGameMutator(db, codec)

    public suspend fun create(input: GameCreation, reading: GameTimeObservation, cancel: CancelToken): Outcome<StoredLifeGame> = games.create(input, {
        // Existing committed operation retries never execute this callback or consume identities.
        gameIdentity(gameSource.value, FailureCode.INVALID_INPUT)
        gameIdentity(reading.processSessionId.value, FailureCode.INVALID_INPUT)
        val id = GameId(gameSource, nextIdentity())
        val lineage = EntityId(nextIdentity())
        LifeGameInitializer.initialize(input, id, reading, lineage, variants)
    }, cancel)

    public suspend fun read(id: GameId, cancel: CancelToken): Outcome<StoredLifeGame> = mutations.readCurrent(id, cancel)

    public suspend fun resume(id: GameId, reading: GameTimeObservation, cancel: CancelToken): Outcome<StoredLifeGame> =
        mutations.systemCurrent(id, reading.wall, { current -> Outcome.Success(settled(current, reading)) }, cancel)

    public suspend fun checkpoint(id: GameId, expected: Revision, expectedSequence: Long, reading: GameTimeObservation, cancel: CancelToken): Outcome<StoredLifeGame> =
        mutations.checkpoint(id, expected, expectedSequence, reading.wall, { current -> Outcome.Success(settled(current, reading)) }, cancel)

    private fun settled(current: StoredLifeGame, reading: GameTimeObservation): GameCheckpoint {
        gameIdentity(reading.processSessionId.value, FailureCode.INVALID_INPUT)
        // Pause/reanchor foreign-process or unsafe lineage first; never settle an unmeasurable wall gap.
        val recovered = gameValue(LifeTimingControls.recover(current.checkpoint.snapshot, reading))
        val reduced = gameValue(LifeTimingEngine.settle(recovered.snapshot, reading))
        return current.checkpoint.copy(snapshot = reduced.snapshot, recoveredProcessSessionId = reading.processSessionId)
    }

    private fun nextIdentity(): String {
        val raw = try { ids.nextId() } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { rejectGame(FailureCode.INTERNAL, "Game identity source failed") }
        gameIdentity(raw, FailureCode.INTERNAL)
        return raw
    }
}
