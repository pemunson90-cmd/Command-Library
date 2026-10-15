package com.architectpsg.commandlibrary.feature.ai

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Native Messages text compatibility. No provider eligibility, discovery, tools or automatic retry. */
public class HttpsAnthropicMessagesClient(
    private val network: NetworkClient,
    private val vault: SecretVault,
    private val codec: AnthropicMessagesWireCodec = AnthropicMessagesWireCodec(),
) : ProviderTextClient {
    public override fun stream(profile: ProviderProfile, request: GenerationRequest, cancel: CancelToken): Flow<Outcome<TokenChunk>> = flow {
        cancel.throwIfCancelled()
        // Complete pure validation precedes credential access, let alone any wire dispatch.
        val encoded = when (val result = codec.encode(profile, request, cancel)) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> { emit(result); return@flow }
            is Outcome.Unavailable -> { emit(result); return@flow }
            is Outcome.Unknown -> { emit(result); return@flow }
        }
        val checked = when (val result = ProviderProfilePolicy.normalize(profile)) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> { emit(result); return@flow }
            is Outcome.Unavailable -> { emit(result); return@flow }
            is Outcome.Unknown -> { emit(result); return@flow }
        }
        val endpoint = checked.endpoint
        val base = checked.baseUri
        val body = encoded.body
        val version = (checked.apiVersion as? Knowledge.Known)?.value
        if (checked != profile || endpoint == null || base == null || checked.dialect != ProviderDialect.ANTHROPIC ||
            encoded.method != HttpMethod.POST || encoded.uri.value != base.value.trimEnd('/') + "/messages" ||
            body == null || body.size > ProviderTextLimits.MAX_REQUEST_BYTES ||
            encoded.headers.size != 3 || encoded.headers.count { it.name.equals("Content-Type", true) && it.value == "application/json" } != 1 ||
            encoded.headers.count { it.name.equals("Accept", true) && it.value == "text/event-stream" } != 1 ||
            encoded.headers.count { it.name.equals("anthropic-version", true) && it.value == version } != 1) {
            emit(invalid("Provider request does not match the approved HTTPS profile")); return@flow
        }
        var headers = encoded.headers
        val ref = checked.secretRef
        if (ref != null) {
            val scope = SecretScope(checked.id, endpoint.origin, "provider-auth-v1")
            if (endpoint.secret != ref || ref.scope != scope) {
                emit(invalid("Provider credential scope does not match the endpoint")); return@flow
            }
            when (val result = vault.reference(scope)) {
                is Outcome.Success -> if (result.value != ref) {
                    emit(invalid("Provider credential reference does not match its scope")); return@flow
                }
                is Outcome.Failure -> { emit(credentialFailure(result.error.code)); return@flow }
                is Outcome.Unavailable -> { emit(Outcome.Unavailable(result.reason, "Provider credential reference is unavailable")); return@flow }
                is Outcome.Unknown -> { emit(Outcome.Unknown("Provider credential reference is uncertain", SnapshotList.empty())); return@flow }
            }
            val key = when (val result = vault.read(ref, cancel)) {
                is Outcome.Success -> result.value
                is Outcome.Failure -> { emit(credentialFailure(result.error.code)); return@flow }
                is Outcome.Unavailable -> { emit(Outcome.Unavailable(result.reason, "Provider credential is unavailable")); return@flow }
                is Outcome.Unknown -> { emit(Outcome.Unknown("Provider credential read is uncertain", SnapshotList.empty())); return@flow }
            }
            if (!validApiKey(key)) {
                emit(invalid("Provider key is not a supported API credential")); return@flow
            }
            // Immutable Strings cannot promise erasure; the value is not retained in app state/logs.
            headers = SnapshotList.copyOf(headers + HttpHeader("X-Api-Key", key))
        } else if (endpoint.secret != null) {
            emit(invalid("Provider credential references do not agree")); return@flow
        }
        cancel.throwIfCancelled()
        currentCoroutineContext().ensureActive()
        val transport = endpoint.copy(secret = null, allowedRedirectOrigins = SnapshotList.empty(),
            retry = RetryPolicy(1, Duration.ZERO, Duration.ZERO, true))
        val spec = RequestSpec(transport, encoded.copy(headers = headers), 3.minutes,
            RequestCharge.POTENTIALLY_CHARGED, RetryClassification.NEVER_AUTOMATIC, null,
            ProviderTextLimits.MAX_WIRE_RESPONSE_BYTES)
        emitAll(codec.decode(request.id, network.stream(spec, cancel), cancel))
    }.catch { failure ->
        if (failure is CancellationException) throw failure
        currentCoroutineContext().ensureActive(); cancel.throwIfCancelled()
        emit(Outcome.Failure(DomainFailure(FailureCode.TRANSPORT, "Provider request failed", SnapshotList.empty())))
    }
    private fun validApiKey(value: String): Boolean =
        value.isNotEmpty() && value.length <= 16 * 1024 && value.all { it.code in 0x21..0x7e }
    private fun invalid(message: String): Outcome<Nothing> = Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT, message, SnapshotList.empty()))
    private fun credentialFailure(code: FailureCode): Outcome<Nothing> = Outcome.Failure(DomainFailure(code,
        "Provider credential could not be read", SnapshotList.empty()))
}
