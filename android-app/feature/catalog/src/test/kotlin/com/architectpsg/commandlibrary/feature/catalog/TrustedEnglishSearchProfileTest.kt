package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import org.junit.Assert.*
import org.junit.Test

class TrustedEnglishSearchProfileTest {
    private val profile = TrustedEnglishSearchProfile
    private val initial = EnglishSearchPageCodec.INITIAL_URI
    private fun request() = profile.request("English cards", false)

    private fun rejected(candidate: AcquisitionRequest) {
        val result = profile.validate(candidate)
        assertTrue(result.toString(), result is Outcome.Failure)
        assertEquals(FailureCode.INVALID_INPUT, (result as Outcome.Failure).error.code)
    }

    @Test fun canonicalRequestAdmitsInformationalPreferencesWithoutChangingAuthority() {
        val standard = request()
        assertEquals(DownloadKind.CATALOG, standard.intent.kind)
        assertEquals(ProviderId("scryfall"), standard.intent.source)
        assertEquals(LanguageTag.ENGLISH, standard.intent.language)
        assertEquals(ResourceUri(initial), standard.intent.resource)
        assertEquals(AcquisitionSource.EnglishSearch(ResourceUri(initial), "english-search-query-v1"), standard.source)
        assertEquals(AcquisitionLanguageScope.ENGLISH_CARDS, standard.languageScope)
        assertTrue(standard.sourceVersion is Knowledge.Unknown)
        assertTrue(standard.sourceDate is Knowledge.Unknown)
        assertTrue(standard.notice is Knowledge.Unknown)
        assertNull(standard.intent.checksum)
        assertTrue(standard.intent.expectedBytes is Knowledge.Unknown)
        assertTrue(profile.validate(standard) is Outcome.Success)
        assertTrue(profile.validate(profile.request("English printings", true)) is Outcome.Success)
        assertThrows(IllegalArgumentException::class.java) { profile.request(" ", false) }
        assertThrows(IllegalArgumentException::class.java) { profile.request("x".repeat(513), false) }
    }

    @Test fun rejectsForgedIntentAndSearchScope() {
        val original = request()
        listOf(
            original.copy(intent = original.intent.copy(id = EntityId("other"))),
            original.copy(intent = original.intent.copy(kind = DownloadKind.RULES)),
            original.copy(intent = original.intent.copy(source = ProviderId("other"))),
            original.copy(intent = original.intent.copy(resource = ResourceUri(initial.replace("page=1", "page=2")))),
            original.copy(intent = original.intent.copy(version = "other")),
            original.copy(intent = original.intent.copy(language = LanguageTag("fr"))),
            original.copy(intent = original.intent.copy(packId = EntityId("other"))),
            original.copy(source = AcquisitionSource.EnglishSearch(ResourceUri(initial.replace("lang%3Aen", "lang%3Afr")), "english-search-query-v1")),
            original.copy(source = AcquisitionSource.EnglishSearch(ResourceUri(initial), "other")),
            original.copy(source = AcquisitionSource.RemoteFile(ResourceUri(initial), SnapshotList.empty(), ArtifactFraming.JSONL,
                ArtifactCompression.NONE, Knowledge.Unknown("unspecified", SnapshotList.empty()), Knowledge.Unknown("unspecified", SnapshotList.empty()))),
            original.copy(languageScope = AcquisitionLanguageScope.ENGLISH_RULES),
        ).forEach(::rejected)
    }

    @Test fun rejectsCallerMetadataAndAnyLimitChange() {
        val original = request()
        val checksum = ContentHash("SHA-256", "a".repeat(64))
        listOf(
            original.copy(intent = original.intent.copy(checksum = checksum)),
            original.copy(intent = original.intent.copy(expectedBytes = Knowledge.Known(100L))),
            original.copy(sourceVersion = Knowledge.Known("2026-10-13")),
            original.copy(sourceDate = Knowledge.Known(UtcTimestamp(1000))),
            original.copy(notice = Knowledge.Known(SourceNotice(ProviderId("scryfall"), "Forged notice", Knowledge.Known("license"), "attribution", null))),
            original.copy(limits = original.limits.copy(profileVersion = "other")),
            original.copy(limits = original.limits.copy(maxTransferBytes = 2_147_483_649L)),
            original.copy(limits = original.limits.copy(maxTransferBytes = 2_147_483_647L)),
            original.copy(limits = original.limits.copy(maxRecords = original.limits.maxRecords + 1)),
            original.copy(limits = original.limits.copy(maxPages = original.limits.maxPages + 1)),
            original.copy(limits = original.limits.copy(maxNesting = original.limits.maxNesting - 1)),
        ).forEach(::rejected)
    }

    @Test fun endpointRequiresValidatedRequestAndCodecValidSameScopePage() {
        val original = request()
        val page2 = ResourceUri(initial.replace("page=1", "page=2"))
        val endpoint = profile.endpointFor(original, page2)
        assertEquals(EntityId("scryfall-search-v1"), endpoint.id)
        assertEquals(Origin("https", "api.scryfall.com", 443), endpoint.origin)
        assertTrue(endpoint.allowedRedirectOrigins.isEmpty())
        assertNull(endpoint.secret)
        assertEquals(2, endpoint.requestsPerSecond)
        assertEquals(1, endpoint.retry.maxAttempts)
        assertTrue(endpoint.retry.honorRetryAfter)
        assertEquals(endpoint, profile.endpointFor(original, ResourceUri(initial)))

        listOf(
            initial.replace("api.scryfall.com", "evil.example"),
            initial.replace("api.scryfall.com", "user@api.scryfall.com"),
            initial.replace("api.scryfall.com", "api.scryfall.com:444"),
            initial.replace("https://", "http://"),
            initial + "#fragment",
            initial.replace("lang%3Aen", "lang%3Afr"),
            initial.replace("/cards/search", "/cards/named"),
            initial.replace("page=1", "page=0"),
            initial.replace("page=1", "page=01"),
            initial.replace("page=1", "page=2859"),
            initial + "&unknown=1",
        ).forEach { uri ->
            assertThrows(IllegalArgumentException::class.java) { profile.endpointFor(original, ResourceUri(uri)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            profile.endpointFor(original.copy(intent = original.intent.copy(version = "forged")), page2)
        }
    }
}
