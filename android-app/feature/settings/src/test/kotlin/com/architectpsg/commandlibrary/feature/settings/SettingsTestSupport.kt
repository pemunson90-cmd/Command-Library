package com.architectpsg.commandlibrary.feature.settings

import com.architectpsg.commandlibrary.core.model.CancelToken
import com.architectpsg.commandlibrary.core.model.Clock
import com.architectpsg.commandlibrary.core.model.ContentHash
import com.architectpsg.commandlibrary.core.model.DomainFailure
import com.architectpsg.commandlibrary.core.model.DownloadIntent
import com.architectpsg.commandlibrary.core.model.DownloadKind
import com.architectpsg.commandlibrary.core.model.EntityId
import com.architectpsg.commandlibrary.core.model.FailureCode
import com.architectpsg.commandlibrary.core.model.IdSource
import com.architectpsg.commandlibrary.core.model.Knowledge
import com.architectpsg.commandlibrary.core.model.LanguageTag
import com.architectpsg.commandlibrary.core.model.Outcome
import com.architectpsg.commandlibrary.core.model.ProviderId
import com.architectpsg.commandlibrary.core.model.ResourceUri
import com.architectpsg.commandlibrary.core.model.SettingChange
import com.architectpsg.commandlibrary.core.model.SettingsPatch
import com.architectpsg.commandlibrary.core.model.SnapshotList
import com.architectpsg.commandlibrary.core.model.UtcTimestamp
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** Runs a suspending test body on the JVM with a hard timeout so a hung store fails instead of stalling. */
internal fun <T> blocking(body: suspend CoroutineScope.() -> T): T = runBlocking { withTimeout(60_000) { body() } }

internal class FakeClock(start: UtcTimestamp = UtcTimestamp(1_760_000_000)) : Clock {
    @Volatile
    var now: UtcTimestamp = start

    override fun utcNow(): UtcTimestamp = now
    override fun monotonicNow(): Duration = System.nanoTime().nanoseconds
}

internal class SequenceIds : IdSource {
    private val next = AtomicInteger(1)
    override fun nextId(): String = "consent-${next.getAndIncrement()}"
}

internal object LiveToken : CancelToken {
    override val isCancelled: Boolean get() = false
    override fun throwIfCancelled() = Unit
}

internal object CancelledToken : CancelToken {
    override val isCancelled: Boolean get() = true
    override fun throwIfCancelled(): Unit = throw CancellationException("cancelled by test")
}

/** Becomes cancelled on the n-th throwIfCancelled call, so a test can cancel at each checkpoint of an operation. */
internal class CancelOnCallToken(private val cancelOnCall: Int) : CancelToken {
    private val calls = AtomicInteger(0)
    private val cancelled = AtomicBoolean(false)
    override val isCancelled: Boolean get() = cancelled.get()
    override fun throwIfCancelled() {
        if (calls.incrementAndGet() >= cancelOnCall) {
            cancelled.set(true)
            throw CancellationException("cancelled by test at call $cancelOnCall")
        }
    }
}

/** Files and repository instances for one test, with reopen to simulate a process restart. */
internal class Fixture(val directory: File, val clock: FakeClock = FakeClock(), val ids: SequenceIds = SequenceIds()) {
    val storeFile = File(directory, "files/datastore/settings.bin")
    val idFile = File(directory, "nobackup/installation_id")
    val consentCoordinator = com.architectpsg.commandlibrary.core.common.ConsentCoordinator()
    private val opened = mutableListOf<DataStoreSettingsRepository>()

    fun open(): DataStoreSettingsRepository =
        DataStoreSettingsRepository.create(storeFile, idFile, consentCoordinator, clock, ids).also { opened += it }

    var repo: DataStoreSettingsRepository = open()
        private set

    /** Closes the current instance, waits for the file to be released, and opens a new one on the same files. */
    fun reopen(): DataStoreSettingsRepository {
        blocking { repo.close() }
        repo = open()
        return repo
    }

    fun closeAll() = blocking { opened.forEach { it.close() } }
}

internal fun intent(
    id: String = "intent-1",
    kind: DownloadKind = DownloadKind.CATALOG,
    language: LanguageTag? = null,
    packId: EntityId? = null,
    checksum: ContentHash? = null,
    expectedBytes: Knowledge<Long> = Knowledge.Absent("size not announced"),
): DownloadIntent = DownloadIntent(
    id = EntityId(id),
    kind = kind,
    source = ProviderId("test-provider"),
    resource = ResourceUri("https://example.invalid/$id"),
    version = "2026-10-01",
    language = language,
    packId = packId,
    checksum = checksum,
    expectedBytes = expectedBytes,
)

internal fun patch(vararg changes: SettingChange): SettingsPatch = SettingsPatch(SnapshotList.copyOf(changes.asList()))

internal fun <T> Outcome<T>.success(): T {
    assertTrue("expected Success but was $this", this is Outcome.Success<T>)
    return (this as Outcome.Success<T>).value
}

internal fun Outcome<*>.failure(code: FailureCode): DomainFailure {
    assertTrue("expected Failure($code) but was $this", this is Outcome.Failure)
    val error = (this as Outcome.Failure).error
    assertEquals("failure message: ${error.message}", code, error.code)
    return error
}
