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
        input: NewHistoryTurn, request: GenerationRequest, cancel: CancelToken, onProgress: suspend (Turn) -> Unit): Outcome<Turn> {
        cancel.throwIfCancelled()
        when (val current = profiles.load(profile.profile.id, cancel)) {
            is Outcome.Success -> if (current.value != profile) return Outcome.Failure(DomainFailure(
                FailureCode.STALE_REVISION, "Provider configuration changed; review it again", SnapshotList.empty()))
            is Outcome.Failure -> return current
            is Outcome.Unavailable -> return current
            is Outcome.Unknown -> return current
        }
        // Pure admission precedes even a durable PENDING row; it performs no credential or wire work.
        val admission = when (profile.profile.dialect) {
            ProviderDialect.OPENAI_CHAT -> com.architectpsg.commandlibrary.feature.ai.ChatCompletionsWireCodec().encode(profile.profile, request, cancel)
            ProviderDialect.OPENAI_RESPONSES -> com.architectpsg.commandlibrary.feature.ai.ResponsesWireCodec().encode(profile.profile, request, cancel)
            ProviderDialect.ANTHROPIC -> com.architectpsg.commandlibrary.feature.ai.AnthropicMessagesWireCodec().encode(profile.profile, request, cancel)
            else -> return Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Provider text dialect is not implemented")
        }
        when (val encoded = admission) {
            is Outcome.Success -> Unit
            is Outcome.Failure -> return encoded
            is Outcome.Unavailable -> return encoded
            is Outcome.Unknown -> return encoded
        }
        val port = when (val opened = history.open(cancel)) {
            is Outcome.Success -> opened.value
            is Outcome.Failure -> return opened
            is Outcome.Unavailable -> return opened
            is Outcome.Unknown -> return opened
        }
        val network = clientFactory()
        try {
            val client: ProviderTextClient = when (profile.profile.dialect) {
                ProviderDialect.OPENAI_CHAT -> HttpsChatCompletionsClient(network, vault)
                ProviderDialect.OPENAI_RESPONSES -> com.architectpsg.commandlibrary.feature.ai.HttpsResponsesClient(network, vault)
                ProviderDialect.ANTHROPIC -> com.architectpsg.commandlibrary.feature.ai.HttpsAnthropicMessagesClient(network, vault)
                else -> return Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Provider text dialect is not implemented")
            }
            return DurableProviderGeneration(client).run(port, history.epoch,
                profile.profile, conversation, input.copy(ownerEpoch = history.epoch), request, cancel, onProgress)
        } finally { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { kotlinx.coroutines.withTimeout(5_000) { network.close() } } }
    }
}
