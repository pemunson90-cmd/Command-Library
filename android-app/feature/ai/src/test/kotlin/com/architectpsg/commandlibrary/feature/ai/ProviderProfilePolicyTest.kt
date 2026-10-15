package com.architectpsg.commandlibrary.feature.ai

import com.architectpsg.commandlibrary.core.model.*
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.*
import org.junit.Test

/** Synthetic noncredential fixtures; no provider calls or vault-presence claims. */
internal object ProviderProfileFixtures {
    fun profile(id: String = "provider"): ProviderProfile {
        val entity = EntityId(id)
        return ProviderProfile(entity, "Provider 日本語", ProviderDialect.OPENAI_CHAT,
            EndpointProfile(entity, Origin("https", "example.test", 443), SnapshotList.empty(), null, 2,
                RetryPolicy(2, 1.seconds, 3.seconds, true)), "manual-model",
            ProviderCapabilities(true, true, false, true, false, true), null,
            Knowledge.Unknown("Not supplied", SnapshotList.empty()), SnapshotList.empty(), ResourceUri("https://example.test/v1"))
    }
    fun <T> ok(value: Outcome<T>): T { assertTrue("Expected success: $value", value is Outcome.Success); return (value as Outcome.Success).value }
    fun invalid(value: Outcome<*>) { assertEquals(FailureCode.INVALID_INPUT, (value as Outcome.Failure).error.code) }
}

class ProviderProfilePolicyTest {
    private val f = ProviderProfileFixtures
    @Test fun canonicalHttpsBaseKeepsAdmittedPathAndEffectiveOrigin() {
        val input = f.profile().copy(baseUri = ResourceUri("HTTPS://EXAMPLE.TEST:443/v1/"))
        val value = f.ok(ProviderProfilePolicy.normalize(input))
        assertEquals("https://example.test/v1", value.baseUri!!.value)
        assertEquals(input.endpoint, value.endpoint)
        for ((base, origin) in listOf("https://example.test" to Origin("https", "example.test", 443),
            "https://127.0.0.1:8443/a-b._~" to Origin("https", "127.0.0.1", 8443),
            "https://[::1]/" to Origin("https", "[::1]", 443))) {
            assertEquals(origin, f.ok(ProviderProfilePolicy.canonicalOrigin(ResourceUri(base))))
        }
    }
    @Test fun ambiguousAndCredentialBearingUrisAreRejected() {
        val invalid = listOf("http://example.test/v1", "https://user:password@example.test/v1", "https://example.test/v1?q=x",
            "https://example.test/#fragment", "https://example.test/a%2fb", "https://example.test/a/../b",
            "https://example.test/./b", "https://example.test/a//b", "https://example.test/\\x", "https://example.test:/v1",
            "https://éxample.test/v1", "https://example.test/a b", "https://example.test/\u0001", "https://example.test/\uD800")
        invalid.forEach { f.invalid(ProviderProfilePolicy.canonicalOrigin(ResourceUri(it))) }
    }
    @Test fun rejectsUnsupportedHeadersEvidenceAndMismatchedExactScope() {
        val p = f.profile(); val endpoint = p.endpoint!!
        val scope = SecretScope(p.id, endpoint.origin, "provider-auth-v1")
        val ref = SecretRef(EntityId("synthetic-ref"), scope)
        val attached = p.copy(secretRef = ref, endpoint = endpoint.copy(secret = ref))
        assertEquals(attached, f.ok(ProviderProfilePolicy.normalize(attached)))
        val bad = listOf(p.copy(endpoint = null), p.copy(baseUri = null), p.copy(endpoint = endpoint.copy(id = EntityId("other"))),
            p.copy(extraHeaders = SnapshotList.of(HttpHeader("Authorization", "synthetic-value"))),
            p.copy(endpoint = endpoint.copy(allowedRedirectOrigins = SnapshotList.of(endpoint.origin))),
            p.copy(secretRef = ref), attached.copy(secretRef = ref.copy(scope = scope.copy(purpose = "other")),
                endpoint = endpoint.copy(secret = ref.copy(scope = scope.copy(purpose = "other")))),
            p.copy(apiVersion = Knowledge.Unknown("Unknown", SnapshotList.of(Evidence("Unsupported evidence", null, null)))))
        bad.forEach { f.invalid(ProviderProfilePolicy.normalize(it)) }
    }
    @Test fun strictByteAndRetryBoundsRejectRatherThanTruncate() {
        val p = f.profile()
        f.ok(ProviderProfilePolicy.normalize(p.copy(name = "é".repeat(512))))
        listOf(p.copy(name = "é".repeat(513)), p.copy(name = "bad\uD800"), p.copy(selectedModelId = "x".repeat(1025)),
            p.copy(name = "bad\nname"), p.copy(endpoint = p.endpoint!!.copy(retry = RetryPolicy(11, 1.seconds, 2.seconds, false))),
            p.copy(endpoint = p.endpoint!!.copy(retry = RetryPolicy(1, 1.seconds, 86_401.seconds, false))))
            .forEach { f.invalid(ProviderProfilePolicy.normalize(it)) }
    }
}
