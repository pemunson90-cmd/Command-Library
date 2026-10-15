package com.architectpsg.commandlibrary.di

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.ai.codec.AiHistoryPayloadCodec
import com.architectpsg.commandlibrary.feature.ai.SqlLocalAiHistory
import com.architectpsg.commandlibrary.feature.shell.AiHistoryAccess
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One epoch for this process, including an independently recreated graph/Room handle. */
private object AiHistoryProcess { val epoch = EntityId(UUID.randomUUID().toString()) }

/** Publishes the real capability only after recovery succeeds; failures leave other app ports usable. */
internal class AppAiHistoryAccess(db: Database, clock: Clock = SystemUtcClock,
    private val epoch: EntityId = AiHistoryProcess.epoch) : AiHistoryAccess {
    private val mutex = Mutex()
    private val history = SqlLocalAiHistory(db, AiHistoryPayloadCodec(), clock)
    private var ready = false
    override suspend fun open(cancel: CancelToken): Outcome<LocalAiHistory> = mutex.withLock {
        cancel.throwIfCancelled()
        if (ready) return@withLock Outcome.Success(history)
        when (val recovered = history.recover(epoch, cancel)) {
            is Outcome.Success -> { ready = true; Outcome.Success(history) }
            is Outcome.Failure -> recovered
            is Outcome.Unavailable -> recovered
            is Outcome.Unknown -> recovered
        }
    }
}
