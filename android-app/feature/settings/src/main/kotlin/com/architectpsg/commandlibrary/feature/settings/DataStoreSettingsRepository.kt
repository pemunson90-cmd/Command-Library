package com.architectpsg.commandlibrary.feature.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import com.architectpsg.commandlibrary.core.common.cancellableOutcome
import com.architectpsg.commandlibrary.core.common.requireCurrentRevision
import com.architectpsg.commandlibrary.core.common.revisionForCommittedEdit
import com.architectpsg.commandlibrary.core.model.CancelToken
import com.architectpsg.commandlibrary.core.model.Clock
import com.architectpsg.commandlibrary.core.model.ConsentRecord
import com.architectpsg.commandlibrary.core.model.DomainFailure
import com.architectpsg.commandlibrary.core.model.DownloadIntent
import com.architectpsg.commandlibrary.core.model.EntityId
import com.architectpsg.commandlibrary.core.model.Evidence
import com.architectpsg.commandlibrary.core.model.FailureCode
import com.architectpsg.commandlibrary.core.model.IdSource
import com.architectpsg.commandlibrary.core.model.Knowledge
import com.architectpsg.commandlibrary.core.model.Outcome
import com.architectpsg.commandlibrary.core.model.Revision
import com.architectpsg.commandlibrary.core.model.SettingChange
import com.architectpsg.commandlibrary.core.model.SettingsPatch
import com.architectpsg.commandlibrary.core.model.SettingsRepository
import com.architectpsg.commandlibrary.core.model.SettingsSnapshot
import com.architectpsg.commandlibrary.core.model.SnapshotList
import com.architectpsg.commandlibrary.core.model.UtcTimestamp
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

/**
 * Settings and consent persisted in one DataStore file, so a revision bump and a consent change
 * always commit together.
 *
 * - Revision check: `update` compares the caller's expected revision with the stored one inside the
 *   DataStore transaction. A mismatch commits nothing and returns a STALE_REVISION failure.
 * - Revision increments: every committed `update` or `approve` increments the revision exactly once.
 * - No-op rule: a call that changes nothing (empty patch, all changes already in effect, or approving
 *   an intent that an effective consent already permits) commits nothing, keeps the revision and
 *   `updatedAt`, and returns the current state. The stale-revision check still applies to `update`.
 * - Restore never grants acquisition: each stored consent carries the installation ID that approved it.
 *   `read` and `update` act only on consents approved under this installation's ID (kept under
 *   `Context.noBackupFilesDir`); consents from a restored or transferred store stay inert in the file
 *   and are never reported, revoked or granted.
 * - `approve` records the intent and returns the record. It starts no job and makes no network call.
 * - Cancellation is checked before and after the work and inside the transaction before it commits;
 *   it is rethrown, never converted to an Outcome.
 *
 * Create exactly one instance per store file per process. DataStore rejects a second live instance
 * for the same file, and that rejection is reported as an INTERNAL failure.
 */
public class DataStoreSettingsRepository private constructor(
    storeFile: File,
    installationIdFile: File,
    private val clock: Clock,
    private val idSource: IdSource,
    dispatcher: CoroutineDispatcher,
) : SettingsRepository {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val installation = InstallationIdStore(installationIdFile, dispatcher)
    private val store: DataStore<StoredSettings> =
        DataStoreFactory.create(serializer = SettingsStoreSerializer, scope = scope) { storeFile }

    public override suspend fun read(cancel: CancelToken): Outcome<SettingsSnapshot> = operate(cancel) {
        val installationId = installation.get()
        store.data.first().toSnapshot(installationId)
    }

    public override suspend fun update(
        patch: SettingsPatch,
        expected: Revision,
        cancel: CancelToken,
    ): Outcome<SettingsSnapshot> = operate(cancel) {
        validate(patch)
        val installationId = installation.get()
        val committed = store.updateData { current ->
            cancel.throwIfCancelled()
            reject(requireCurrentRevision(expected, current.revision))
            val now = clock.utcNow()
            val working = applyChanges(current, installationId, patch.changes, now)
            if (working == current) {
                current
            } else {
                working.copy(revision = nextRevision(expected, current.revision), updatedAt = now)
            }
        }
        committed.toSnapshot(installationId)
    }

    public override suspend fun approve(intent: DownloadIntent, cancel: CancelToken): Outcome<ConsentRecord> = operate(cancel) {
        val installationId = installation.get()
        val committed = store.updateData { current ->
            cancel.throwIfCancelled()
            if (current.effectiveConsentPermitting(installationId, intent) != null) {
                current
            } else {
                val now = clock.utcNow()
                val record = ConsentRecord(
                    id = EntityId(idSource.nextId()),
                    intent = intent,
                    approvedAt = now,
                    revokedAt = null,
                    explicitUserApproval = true,
                )
                if (current.consents.any { it.record.id == record.id }) {
                    throw SettingsRejection(failure(FailureCode.INTERNAL, "Generated consent id is already in use"))
                }
                current.copy(
                    revision = nextRevision(current.revision, current.revision),
                    updatedAt = now,
                    consents = current.consents + StoredConsent(installationId, record),
                )
            }
        }
        checkNotNull(committed.effectiveConsentPermitting(installationId, intent)) {
            "Committed settings do not contain the approved consent"
        }
    }

    /**
     * Stops this instance and waits until DataStore has released the store file, so a new instance
     * for the same files can be created. Later calls on this instance return a PERSISTENCE failure.
     */
    public suspend fun close() {
        scope.cancel()
        scope.coroutineContext[Job]?.join()
    }

    private suspend fun <T> operate(cancel: CancelToken, action: suspend () -> T): Outcome<T> =
        try {
            cancellableOutcome(cancel, ::classify, action)
        } catch (cancelled: CancellationException) {
            val callerCancelled = !currentCoroutineContext().isActive || cancel.isCancelled
            if (callerCancelled || scope.isActive) throw cancelled
            Outcome.Failure(failure(FailureCode.PERSISTENCE, "Settings repository is closed"))
        }

    private fun classify(error: Exception): DomainFailure = when (error) {
        is SettingsRejection -> error.failure
        is UnsupportedSettingsStoreVersionException -> DomainFailure(
            FailureCode.CORRUPT_DATA,
            "The settings store was written by a newer version of the app (store version " +
                "${error.foundVersion}, supported ${error.supportedVersion}); it was not modified",
            SnapshotList.of(Evidence("stored-version=${error.foundVersion} supported-version=${error.supportedVersion}", null, null)),
        )
        is CorruptSettingsStoreException -> failure(FailureCode.CORRUPT_DATA, "The settings store is damaged and was not modified")
        is IOException -> failure(FailureCode.PERSISTENCE, "The settings store could not be read or written")
        else -> failure(FailureCode.INTERNAL, "Settings operation failed unexpectedly (${error::class.java.simpleName})")
    }

    public companion object {
        /** Path of the store below `Context.filesDir`. Back up or exclude it through the app's backup rules. */
        public const val STORE_PATH_UNDER_FILES_DIR: String = "datastore/command_library_settings.bin"

        /** Name of the installation ID file below `Context.noBackupFilesDir`. */
        public const val INSTALLATION_ID_NAME_UNDER_NO_BACKUP_DIR: String = "command_library_installation_id"

        /** Production factory: store under filesDir, installation ID under noBackupFilesDir. */
        public fun create(
            context: Context,
            clock: Clock = SystemUtcClock,
            idSource: IdSource = RandomUuidIdSource,
            dispatcher: CoroutineDispatcher = Dispatchers.IO,
        ): DataStoreSettingsRepository {
            val application = context.applicationContext
            return create(
                storeFile = File(application.filesDir, STORE_PATH_UNDER_FILES_DIR),
                installationIdFile = File(application.noBackupFilesDir, INSTALLATION_ID_NAME_UNDER_NO_BACKUP_DIR),
                clock = clock,
                idSource = idSource,
                dispatcher = dispatcher,
            )
        }

        /** File based factory with no Android dependency at run time, used by JVM tests. */
        public fun create(
            storeFile: File,
            installationIdFile: File,
            clock: Clock = SystemUtcClock,
            idSource: IdSource = RandomUuidIdSource,
            dispatcher: CoroutineDispatcher = Dispatchers.IO,
        ): DataStoreSettingsRepository =
            DataStoreSettingsRepository(storeFile, installationIdFile, clock, idSource, dispatcher)
    }
}

/** Thrown inside the DataStore transaction to abort it with a typed failure; nothing is written. */
private class SettingsRejection(val failure: DomainFailure) : RuntimeException(failure.message)

private fun failure(code: FailureCode, message: String): DomainFailure = DomainFailure(code, message, SnapshotList.empty())

/** Fails the transaction when the outcome is anything other than success. */
private fun reject(outcome: Outcome<*>) {
    when (outcome) {
        is Outcome.Success -> Unit
        is Outcome.Failure -> throw SettingsRejection(outcome.error)
        is Outcome.Unavailable -> throw SettingsRejection(failure(FailureCode.INTERNAL, outcome.detail))
        is Outcome.Unknown -> throw SettingsRejection(failure(FailureCode.INTERNAL, outcome.reason))
    }
}

/** The revision a committed edit gets, from the shared core rule; the transaction fails on exhaustion. */
private fun nextRevision(expected: Revision, actual: Revision): Revision =
    when (val outcome = revisionForCommittedEdit(expected, actual)) {
        is Outcome.Success -> outcome.value
        else -> {
            reject(outcome)
            throw SettingsRejection(failure(FailureCode.INTERNAL, "Revision rule returned no revision"))
        }
    }

private fun StoredSettings.toSnapshot(installationId: String): SettingsSnapshot = SettingsSnapshot(
    revision = revision,
    dataLanguage = dataLanguage,
    theme = theme,
    storageMode = storageMode,
    cache = cache,
    budget = budget,
    providerProfileIds = SnapshotList.copyOf(providerProfileIds),
    sourceNotices = SnapshotList.empty(),
    consents = SnapshotList.copyOf(consents.filter { it.installationId == installationId }.map { it.record }),
    updatedAt = updatedAt,
)

private fun StoredSettings.effectiveConsentPermitting(installationId: String, intent: DownloadIntent): ConsentRecord? =
    consents.firstOrNull { it.installationId == installationId && it.record.permits(intent) }?.record

/** State-independent checks; they run before the store is touched. */
private fun validate(patch: SettingsPatch) {
    patch.changes.forEach { change ->
        when (change) {
            is SettingChange.Budget -> {
                val amount = change.amount
                if (amount is Knowledge.Known && amount.value.value.signum() < 0) {
                    throw SettingsRejection(failure(FailureCode.INVALID_INPUT, "Budget must not be negative"))
                }
            }
            is SettingChange.ProviderProfiles -> {
                if (change.ids.toSet().size != change.ids.size) {
                    throw SettingsRejection(failure(FailureCode.INVALID_INPUT, "Provider profile ids must be unique"))
                }
            }
            else -> Unit
        }
    }
}

/** Applies the changes in order to a working copy. Revision and updatedAt are the caller's concern. */
private fun applyChanges(
    current: StoredSettings,
    installationId: String,
    changes: List<SettingChange>,
    now: UtcTimestamp,
): StoredSettings = changes.fold(current) { state, change ->
    when (change) {
        is SettingChange.DataLanguage -> state.copy(dataLanguage = change.language)
        is SettingChange.Theme -> state.copy(theme = change.theme)
        is SettingChange.Storage -> state.copy(storageMode = change.mode)
        is SettingChange.Cache -> state.copy(cache = change.policy)
        is SettingChange.Budget -> state.copy(budget = change.amount)
        is SettingChange.ProviderProfiles -> state.copy(providerProfileIds = change.ids.toList())
        is SettingChange.RevokeConsent -> revoke(state, installationId, change.id, now)
    }
}

/**
 * Marks the effective consent revoked at the real current time. Revoking an already revoked consent
 * keeps its original revokedAt. `removeDownloadedData` is not actionable in K2 because no downloaded
 * data exists; nothing is deleted and nothing claims a deletion.
 */
private fun revoke(
    state: StoredSettings,
    installationId: String,
    id: EntityId,
    now: UtcTimestamp,
): StoredSettings {
    val index = state.consents.indexOfFirst { it.installationId == installationId && it.record.id == id }
    if (index < 0) throw SettingsRejection(failure(FailureCode.INVALID_INPUT, "No consent record with id ${id.value}"))
    val stored = state.consents[index]
    if (stored.record.revokedAt != null) return state
    val updated = state.consents.toMutableList()
    updated[index] = stored.copy(record = stored.record.copy(revokedAt = now))
    return state.copy(consents = updated)
}
