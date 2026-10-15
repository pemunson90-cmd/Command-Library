package com.architectpsg.commandlibrary.di

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.network.OkHttpNetworkClient
import com.architectpsg.commandlibrary.feature.ai.DurableProviderGeneration
import com.architectpsg.commandlibrary.feature.ai.HttpsChatCompletionsClient
import com.architectpsg.commandlibrary.feature.shell.ProviderGenerationAccess

/** No transport is created until an explicitly approved attempt. No model discovery or replay. */
internal class AppProviderGenerationAccess(private val profiles: LocalProviderProfiles,
    private val history: AppAiHistoryAccess, private val vault: SecretVault,
    private val clientFactory: () -> NetworkClient = { OkHttpNetworkClient(SystemUtcClock,
        userAgent = "CommandLibrary/1 Android (explicit provider request)") }) : ProviderGenerationAccess {
    override suspend fun run(profile: StoredProviderProfile, conversation: NewConversation,
        input: NewHistoryTurn, cancel: CancelToken, onProgress: suspend (Turn) -> Unit): Outcome<Turn> {
        cancel.throwIfCancelled()
        when (val current = profiles.load(profile.profile.id, cancel)) {
            is Outcome.Success -> if (current.value != profile) return Outcome.Failure(DomainFailure(
                FailureCode.STALE_REVISION, "Provider configuration changed; review it again", SnapshotList.empty()))
            is Outcome.Failure -> return current
            is Outcome.Unavailable -> return current
            is Outcome.Unknown -> return current
        }
        val port = when (val opened = history.open(cancel)) {
            is Outcome.Success -> opened.value
            is Outcome.Failure -> return opened
            is Outcome.Unavailable -> return opened
            is Outcome.Unknown -> return opened
        }
        val network = clientFactory()
        try {
            return DurableProviderGeneration(HttpsChatCompletionsClient(network, vault)).run(port, history.epoch,
                profile.profile, conversation, input.copy(ownerEpoch = history.epoch), cancel, onProgress)
        } finally { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { kotlinx.coroutines.withTimeout(5_000) { network.close() } } }
    }
}
