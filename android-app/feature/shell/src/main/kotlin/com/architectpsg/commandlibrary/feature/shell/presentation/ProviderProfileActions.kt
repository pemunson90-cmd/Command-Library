package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class ProviderProfileActionResult<T>(val value: T, val settingsWarning: Boolean = false)

/** One app-process writer for profile/key actions; keys never enter profile or settings storage. */
internal class ProviderProfileActions(
    private val profiles: LocalProviderProfiles,
    private val vault: SecretVault,
    private val settings: SettingsRepository,
) {

    suspend fun page(page: PageRequest, cancel: CancelToken): Outcome<Page<StoredProviderProfile>> = profiles.list(page, cancel)
    suspend fun load(id: EntityId, cancel: CancelToken): Outcome<StoredProviderProfile> = profiles.load(id, cancel)

    suspend fun save(profile: ProviderProfile, expected: Revision?, cancel: CancelToken): Outcome<ProviderProfileActionResult<StoredProviderProfile>> = action(cancel) {
        if (expected == null) {
            if (profile.secretRef != null || profile.endpoint?.secret != null) reject(FailureCode.INVALID_INPUT)
        } else {
            val old = checkedCurrent(profile.id, expected, cancel).profile
            if (profile.secretRef != old.secretRef || profile.endpoint?.secret != old.endpoint?.secret) reject(FailureCode.INVALID_INPUT)
        }
        val saved = success(if (expected == null) profiles.create(profile, cancel) else profiles.replace(profile, expected, cancel))
        ProviderProfileActionResult(saved, !reconcileSettings(cancel))
    }

    suspend fun delete(id: EntityId, expected: Revision, cancel: CancelToken): Outcome<ProviderProfileActionResult<Unit>> = action(cancel) {
        val current = checkedCurrent(id, expected, cancel)
        if (current.profile.secretRef != null || current.profile.endpoint?.secret != null) reject(FailureCode.INVALID_INPUT)
        success(profiles.remove(id, expected, cancel))
        ProviderProfileActionResult(Unit, !reconcileSettings(cancel))
    }

    /** Persist the recoverable reference before touching the vault. Never retry put automatically. */
    suspend fun putKey(id: EntityId, expected: Revision, value: String, cancel: CancelToken): Outcome<StoredProviderProfile> = action(cancel) {
        var current = checkedCurrent(id, expected, cancel)
        val endpoint = current.profile.endpoint ?: reject(FailureCode.INVALID_INPUT)
        val ref = success(vault.reference(SecretScope(id, endpoint.origin, PURPOSE)))
        if (current.profile.secretRef != null && current.profile.secretRef != ref) reject(FailureCode.SOURCE_MISMATCH)
        if (current.profile.secretRef == null) {
            current = success(profiles.replace(current.profile.copy(secretRef = ref, endpoint = endpoint.copy(secret = ref)), expected, cancel))
        } else if (endpoint.secret != ref) reject(FailureCode.SOURCE_MISMATCH)
        cancel.throwIfCancelled()
        val written = success(vault.put(ref.scope, value, cancel))
        if (written != ref) reject(FailureCode.CORRUPT_DATA)
        current
    }

    /** A failed/uncertain vault removal or stale detach retains the persisted recovery reference. */
    suspend fun removeKey(id: EntityId, expected: Revision, cancel: CancelToken): Outcome<StoredProviderProfile> = action(cancel) {
        val current = checkedCurrent(id, expected, cancel)
        val endpoint = current.profile.endpoint ?: reject(FailureCode.INVALID_INPUT)
        val ref = current.profile.secretRef ?: return@action current
        if (endpoint.secret != ref || success(vault.reference(SecretScope(id, endpoint.origin, PURPOSE))) != ref) reject(FailureCode.SOURCE_MISMATCH)
        success(vault.remove(ref, cancel))
        // Cancellation here leaves an absent key with a recoverable reference, never a hidden key.
        cancel.throwIfCancelled()
        success(profiles.replace(current.profile.copy(secretRef = null, endpoint = endpoint.copy(secret = null)), expected, cancel))
    }

    private suspend fun checkedCurrent(id: EntityId, expected: Revision, cancel: CancelToken): StoredProviderProfile {
        val current = success(profiles.load(id, cancel))
        if (current.revision != expected) reject(FailureCode.STALE_REVISION)
        return current
    }

    /** Compatibility IDs only; failure never reverses a successful authoritative profile mutation. */
    private suspend fun reconcileSettings(cancel: CancelToken): Boolean = try {
        reconcileSettingsChecked(cancel)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    private suspend fun reconcileSettingsChecked(cancel: CancelToken): Boolean {
        repeat(3) {
            val ids = mutableListOf<EntityId>()
            var cursor: String? = null
            var complete = false
            var failed = false
            for (pageNumber in 0 until 100) {
                when (val result = profiles.list(PageRequest(100, cursor), cancel)) {
                    is Outcome.Success -> {
                        ids += result.value.items.map { it.profile.id }
                        cursor = result.value.nextCursor
                        if (cursor == null) { complete = true; break }
                    }
                    else -> { failed = true; break }
                }
            }
            if (failed || !complete) return@repeat
            val old = when (val result = settings.read(cancel)) {
                is Outcome.Success -> result.value
                else -> return@repeat
            }
            if (old.providerProfileIds.toList() == ids) return true
            when (settings.update(SettingsPatch(SnapshotList.of(SettingChange.ProviderProfiles(SnapshotList.copyOf(ids)))), old.revision, cancel)) {
                is Outcome.Success -> return true
                else -> Unit
            }
        }
        return false
    }

    private suspend fun <T> action(cancel: CancelToken, block: suspend () -> T): Outcome<T> = try {
        mutex.withLock { cancel.throwIfCancelled(); Outcome.Success(block()) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (abort: ActionAbort) {
        abort.outcome
    } catch (_: Exception) {
        Outcome.Failure(DomainFailure(FailureCode.INTERNAL, "Provider action failed", SnapshotList.empty()))
    }

    private fun <T> success(value: Outcome<T>): T = when (value) {
        is Outcome.Success -> value.value
        is Outcome.Failure -> throw ActionAbort(value)
        is Outcome.Unavailable -> throw ActionAbort(value)
        is Outcome.Unknown -> throw ActionAbort(value)
    }
    private fun reject(code: FailureCode): Nothing = throw ActionAbort(Outcome.Failure(DomainFailure(code, "Provider action rejected", SnapshotList.empty())))
    private class ActionAbort(val outcome: Outcome<Nothing>) : RuntimeException("Provider action stopped")
    private companion object {
        const val PURPOSE = "provider-auth-v1"
        val mutex = Mutex() // Shared even across activity/controller instances.
    }
}
