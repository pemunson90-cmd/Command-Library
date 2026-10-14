package com.architectpsg.commandlibrary.core.model

import org.junit.Assert.*
import org.junit.Test

/** Synthetic identities and prose only; no acquired body or source authority. */
class RulingsContractsTest {
    private val provider = ProviderId("scryfall")
    private val oracle = OracleId(provider, "11111111-2222-3333-4444-555555555555")
    private val generation = GenerationId("synthetic-generation")
    private fun content(comment: String = "  Synthetic prose\n\u0000\uD83E\uDDE9  ") =
        RulingContent(oracle, RulingPublisher.WOTC, RulingPublicationDay("2024-02-29"), comment)
    private fun provenance(id: GenerationId = generation) = Provenance(provider, null, Knowledge.Absent("Synthetic"),
        Knowledge.Unknown("Synthetic", SnapshotList.empty()), UtcTimestamp(17), Knowledge.Absent("Synthetic"), id, null, null)
    private fun generation(status: GenerationStatus = GenerationStatus.ACTIVE) = RulingsGeneration(generation, provenance(), 2, status)

    @Test fun publicationDaysRetainExactCalendarValuesWithoutTimezoneOrInstantConversion() {
        for (value in listOf("0001-01-01", "1500-03-01", "1600-02-29", "1900-02-28", "2000-02-29", "9999-12-31")) {
            assertEquals(value, RulingPublicationDay(value).value)
        }
        for (value in listOf("0000-01-01", "1500-02-29", "1900-02-29", "2023-02-29", "2024-04-31", "2024-00-01",
            "2024-13-01", "2024-01-00", "2024-01-32", "2024-2-29", "2024-02-29T00:00:00Z", "10000-01-01", " 2024-02-29"))
            assertThrows(IllegalArgumentException::class.java) { RulingPublicationDay(value) }
    }

    @Test fun publisherAndUntrimmedValidUnicodeContentRemainLiteralIncludingEscapedNul() {
        assertEquals(listOf("wotc", "scryfall"), RulingPublisher.entries.map { it.upstreamValue })
        val text = "  Synthetic prose\n\u0000\uD83E\uDDE9  "
        for (publisher in RulingPublisher.entries) {
            val value = content(text).copy(publisher = publisher)
            assertEquals(text, value.comment); assertEquals(publisher, value.publisher)
            assertEquals(oracle, value.oracleId); assertEquals("2024-02-29", value.publishedAt.value)
        }
        for (text in listOf("", " \t\n", "\uD800", "x\uDC00", "\uD800x"))
            assertThrows(IllegalArgumentException::class.java) { content(text) }
    }

    @Test fun contentRequiresCanonicalScryfallOracleWithoutInferringFromAName() {
        for (bad in listOf(OracleId(ProviderId("other"), oracle.raw), OracleId(provider, "1-2-3-4-5"),
            OracleId(provider, "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE"), OracleId(provider, oracle.raw + " ")))
            assertThrows(IllegalArgumentException::class.java) { content().copy(oracleId = bad) }
    }

    @Test fun duplicateContentHasDistinctLocalOrdinalsWithoutClaimingCorpusWideUniqueness() {
        val value = content(); val first = StoredRuling(RulingId(generation, 1), value, provenance())
        val second = first.copy(id = RulingId(generation, 2))
        assertNotEquals(first.id, second.id); assertEquals(first.content, second.content)
        assertEquals(first, first.copy()); assertEquals(RulingId(generation, 1), first.id)
        for (ordinal in listOf(0L, -1L, Long.MIN_VALUE)) assertThrows(IllegalArgumentException::class.java) { RulingId(generation, ordinal) }
        assertEquals(Long.MAX_VALUE, RulingId(generation, Long.MAX_VALUE).ordinal)
    }

    @Test fun retrievalProviderAndGenerationMustAgreeWhilePublisherAndSourceDayStaySeparate() {
        val stored = StoredRuling(RulingId(generation, 1), content(), provenance())
        assertEquals(RulingPublisher.WOTC, stored.content.publisher); assertEquals(provider, stored.provenance.provider)
        assertEquals(provenance(), stored.provenance); assertEquals("2024-02-29", stored.content.publishedAt.value)
        for (bad in listOf(provenance(GenerationId("different")), provenance().copy(generation = null),
            provenance().copy(provider = ProviderId("wotc")))) {
            assertThrows(IllegalArgumentException::class.java) { stored.copy(provenance = bad) }
            assertThrows(IllegalArgumentException::class.java) { generation().copy(provenance = bad) }
        }
    }

    @Test fun actualAbsentStatusIsDistinctFromActiveGenerationAndRulingsNeverAssertEnglish() {
        assertNull(RulingsStatus(null).active)
        val active = generation(); assertEquals(active, RulingsStatus(active).active)
        assertEquals(AcquisitionLanguageScope.RULINGS_LANGUAGE_NOT_ASSERTED, active.languageScope)
        assertEquals(0L, active.copy(recordCount = 0).recordCount)
        assertThrows(IllegalArgumentException::class.java) { active.copy(recordCount = -1) }
        for (scope in AcquisitionLanguageScope.entries.filter { it != AcquisitionLanguageScope.RULINGS_LANGUAGE_NOT_ASSERTED })
            assertThrows(IllegalArgumentException::class.java) { active.copy(languageScope = scope) }
        for (status in GenerationStatus.entries.filter { it != GenerationStatus.ACTIVE })
            assertThrows(IllegalArgumentException::class.java) { RulingsStatus(generation(status)) }
    }

    private fun installed(): RulingsImportResult {
        val hash = ContentHash("SHA-256", "a".repeat(64))
        val uri = ResourceUri("https://example.invalid/synthetic.gz")
        val artifact = VerifiedArtifact(EntityId("job"), ArtifactFraming.JSONL, ArtifactCompression.GZIP, 100, hash, false,
            Knowledge.Known(2L), 0, AcquisitionProvenance(provider, uri, uri, Knowledge.Absent("Synthetic"),
                Knowledge.Unknown("Synthetic", SnapshotList.empty()), UtcTimestamp(16), UtcTimestamp(17),
                Knowledge.Absent("Synthetic"), Knowledge.Absent("Synthetic"), AcquisitionConsistency.FILE_REPRESENTATION,
                AcquisitionLanguageScope.RULINGS_LANGUAGE_NOT_ASSERTED, Knowledge.Absent("Synthetic")))
        val corpus = generation().copy(provenance = provenance().copy(source = uri, checksum = hash))
        return RulingsImportResult(corpus, 100, hash, 200, hash, UtcTimestamp(18), artifact)
    }

    @Test fun installedResultRequiresConsistentArtifactCountsProofAndActiveGeneration() {
        val result = installed(); assertEquals(2L, result.generation.recordCount)
        for(action in listOf<() -> Unit>(
            { result.copy(rawBytes = 101) }, { result.copy(rawBytes = 0) }, { result.copy(decodedBytes = 0) },
            { result.copy(rawSha256 = ContentHash("SHA-256", "b".repeat(64))) },
            { result.copy(decodedSha256 = ContentHash("SHA-256", "a")) },
            { result.copy(generation = result.generation.copy(status = GenerationStatus.STAGING)) },
            { result.copy(generation = result.generation.copy(recordCount = 1)) },
            { result.copy(acquiredArtifact = result.acquiredArtifact.copy(pageCount = 1)) },
            { result.copy(acquiredArtifact = result.acquiredArtifact.copy(compression = ArtifactCompression.NONE)) }))
            assertThrows(IllegalArgumentException::class.java) { action() }
    }

    @Test fun installedResultRetainsActualRetrievalMetadataWithoutAssertingCallerAuthority() {
        val result = installed()
        val changes = listOf(result.generation.provenance.copy(fetchedAt = UtcTimestamp(0)),
            result.generation.provenance.copy(source = null), result.generation.provenance.copy(version = Knowledge.Known("Invented")),
            result.generation.provenance.copy(sourceDate = Knowledge.Absent("Invented")), result.generation.provenance.copy(checksum = null))
        for(provenance in changes) assertThrows(IllegalArgumentException::class.java) { result.copy(generation = result.generation.copy(provenance = provenance)) }
        // Constructor consistency does not authenticate even this synthetic URI; trusted binding is mandatory.
        assertEquals(ResourceUri("https://example.invalid/synthetic.gz"), result.generation.provenance.source)
        assertNull(result.generation.provenance.notice)
    }
}
