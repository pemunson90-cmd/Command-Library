package com.architectpsg.commandlibrary.feature.ai

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Synthetic credential/network oracles only; not native vault or provider certification. */
class HttpsChatCompletionsClientTest {
    private val cancel = object : CancelToken {
        override val isCancelled = false
        override fun throwIfCancelled() = Unit
    }
    private val request = GenerationRequest(EntityId("attempt"),
        SnapshotList.of(GenerationMessage(MessageRole.USER, "exact prompt", null)),
        null, null, null, null, SnapshotList.empty())
    private class Vault : SecretVault {
        var reads = 0
        var key: Outcome<String> = Outcome.Success("synthetic-token")
        override fun reference(scope: SecretScope): Outcome<SecretRef> = Outcome.Success(SecretRef(EntityId("ref"), scope))
        override suspend fun read(ref: SecretRef, cancel: CancelToken): Outcome<String> { reads++; return key }
        override suspend fun put(scope: SecretScope, value: String, cancel: CancelToken): Outcome<SecretRef> = error("not used")
        override suspend fun remove(ref: SecretRef, cancel: CancelToken): Outcome<Unit> = error("not used")
    }
    private class Network : NetworkClient {
        val requests = mutableListOf<RequestSpec>()
        override suspend fun send(request: RequestSpec, cancel: CancelToken): Outcome<ByteResponse> = error("not used")
        override fun stream(request: RequestSpec, cancel: CancelToken): Flow<Outcome<ByteChunk>> = flow {
            requests += request
            emit(Outcome.Failure(DomainFailure(FailureCode.TRANSPORT, "Synthetic failure", SnapshotList.empty())))
        }
        override suspend fun close() = Unit
    }
    private fun attached(vault: Vault): ProviderProfile {
        val p = ProviderProfileFixtures.profile()
        val endpoint = p.endpoint!!
        val ref = (vault.reference(SecretScope(p.id, endpoint.origin, "provider-auth-v1")) as Outcome.Success).value
        return p.copy(secretRef = ref, endpoint = endpoint.copy(secret = ref))
    }
    @Test fun exactScopeAuthIsTransientAndPotentiallyChargedRequestNeverRetries() = runBlocking {
        val vault = Vault(); val network = Network(); val profile = attached(vault)
        HttpsChatCompletionsClient(network, vault).stream(profile, request, cancel).toList()
        assertEquals(1, vault.reads); assertEquals(1, network.requests.size)
        val sent = network.requests.single()
        assertEquals(RequestCharge.POTENTIALLY_CHARGED, sent.charge)
        assertEquals(RetryClassification.NEVER_AUTOMATIC, sent.retryClassification)
        assertNull(sent.idempotencyKey)
        assertNull(sent.endpoint.secret)
        assertEquals(1, sent.endpoint.retry.maxAttempts)
        assertEquals("Bearer synthetic-token", sent.request.headers.single { it.name == "Authorization" }.value)
        assertEquals(profile.secretRef, profile.endpoint!!.secret)
    }
    @Test fun missingAttachedCredentialAndHeaderInjectionNeverReachWire() = runBlocking {
        for (key in listOf<Outcome<String>>(
            Outcome.Failure(DomainFailure(FailureCode.PERSISTENCE, "Private vault diagnostic", SnapshotList.empty())),
            Outcome.Success("key\r\nInjected: bad"), Outcome.Success(""), Outcome.Success("white space"))) {
            val vault = Vault().also { it.key = key }; val network = Network()
            val result = HttpsChatCompletionsClient(network, vault).stream(attached(vault), request, cancel).toList()
            assertEquals(1, result.size); assertTrue(result.single() is Outcome.Failure)
            assertTrue(network.requests.isEmpty())
            assertFalse(result.toString().contains("Private vault diagnostic"))
        }
    }
    @Test fun invalidInputPrecedesVaultAndKeylessCustomEndpointOmitsAuthorization() = runBlocking {
        val vault = Vault(); val network = Network(); val client = HttpsChatCompletionsClient(network, vault)
        client.stream(attached(vault), request.copy(messages = SnapshotList.of(
            GenerationMessage(MessageRole.TOOL, "unsupported", "call"))), cancel).toList()
        assertEquals(0, vault.reads); assertTrue(network.requests.isEmpty())
        client.stream(ProviderProfileFixtures.profile(), request, cancel).toList()
        assertEquals(0, vault.reads)
        assertFalse(network.requests.single().request.headers.any { it.name.equals("Authorization", true) })
    }
    @Test fun downstreamFailurePropagatesByIdentityWithoutSecondEmission() = runBlocking {
        val sentinel = IllegalStateException("history collector failure")
        var received = 0
        try {
            HttpsChatCompletionsClient(Network(), Vault()).stream(ProviderProfileFixtures.profile(), request, cancel).collect {
                received++; throw sentinel
            }
            fail("Expected collector failure")
        } catch (actual: IllegalStateException) { assertSame(sentinel, actual) }
        assertEquals(1, received)
    }
}
