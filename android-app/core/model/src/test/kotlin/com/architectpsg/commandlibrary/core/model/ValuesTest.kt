package com.architectpsg.commandlibrary.core.model

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/** Synthetic fixtures prove contract semantics; they claim no provider/source measurements. */
class ValuesTest {
    private val provider = ProviderId("synthetic-provider")
    private val at = UtcTimestamp(100, 12)
    private fun provenance(): Provenance = Provenance(provider, ResourceUri("https://example.invalid/source"), Knowledge.Known("fixture-v1"), Knowledge.Unknown("Not supplied", SnapshotList.empty()), at, Knowledge.Known(UtcTimestamp(90)), GenerationId("fixture-generation"), null, null)

    @Test fun pagesAreBoundedAndRetainProducingSnapshot() {
        assertThrows(IllegalArgumentException::class.java) { PageRequest(0) }
        assertThrows(IllegalArgumentException::class.java) { PageRequest(101) }
        assertThrows(IllegalArgumentException::class.java) { PageRequest(10, "") }
        val reference = SnapshotReference(provider, GenerationId("gen"), Revision(8), LanguageTag.ENGLISH)
        val page = Page(SnapshotList.copyOf((1..100).toList()), "opaque-next", reference)
        assertEquals(100, page.items.size)
        assertEquals(reference, page.snapshot)
        assertEquals(Revision(8), page.revision)
        assertThrows(IllegalArgumentException::class.java) { Page(SnapshotList.copyOf((1..101).toList()), null, reference) }
    }

    @Test fun opaqueCursorsAreBoundedByUtf8BytesInRequestsAndResponses() {
        assertEquals("a".repeat(2048), PageRequest(1, "a".repeat(2048)).cursor)
        assertThrows(IllegalArgumentException::class.java) { PageRequest(1, "a".repeat(2049)) }
        assertThrows(IllegalArgumentException::class.java) { PageRequest(1, "界".repeat(683)) }
        val reference = SnapshotReference(provider, GenerationId("gen"), Revision(8), LanguageTag.ENGLISH)
        assertEquals("界".repeat(682), Page<Int>(SnapshotList.empty(), "界".repeat(682), reference).nextCursor)
        assertThrows(IllegalArgumentException::class.java) {
            Page<Int>(SnapshotList.empty(), "界".repeat(683), reference)
        }
    }

    @Test fun sqlLookaheadAllows101RowsWithoutRelaxingPublicPageBound() {
        val query = SqlRequest("SELECT bound_values", SnapshotList.empty(), 101)
        assertEquals(101, query.maxRows)
        assertThrows(IllegalArgumentException::class.java) {
            SqlRequest("SELECT bound_values", SnapshotList.empty(), 102)
        }
        assertThrows(IllegalArgumentException::class.java) { PageRequest(101) }
        val reference = SnapshotReference(provider, GenerationId("gen"), Revision(8), LanguageTag.ENGLISH)
        assertThrows(IllegalArgumentException::class.java) {
            Page(SnapshotList.copyOf((1..101).toList()), null, reference)
        }
    }

    @Test fun snapshotsDefensivelyCopyListsAndBytes() {
        val input = mutableListOf("original")
        val snapshot = SnapshotList.copyOf(input)
        input[0] = "changed"
        assertEquals("original", snapshot[0])
        assertThrows(ClassCastException::class.java) { (snapshot as MutableList<String>).add("bad") }
        val bytes = byteArrayOf(1, 2)
        val saved = SnapshotBytes(bytes)
        bytes[0] = 9
        val copy = saved.copyBytes()
        copy[1] = 8
        assertArrayEquals(byteArrayOf(1, 2), saved.copyBytes())
    }

    @Test fun knownZeroAbsentAndUnknownAreDistinct() {
        val zero: Knowledge<BigDecimal> = Knowledge.Known(BigDecimal.ZERO)
        val absent: Knowledge<BigDecimal> = Knowledge.Absent("Not applicable to this layout")
        val unknown: Knowledge<BigDecimal> = Knowledge.Unknown("Source did not provide this field", SnapshotList.empty())
        assertNotEquals(zero, absent)
        assertNotEquals(absent, unknown)
        assertNotEquals(Outcome.Success(0), Outcome.Unknown("No quote", SnapshotList.empty()))
        assertNotEquals(Outcome.Unknown("Missing", SnapshotList.empty()), Outcome.Unavailable(UnavailableReason.OFFLINE, "Offline"))
        assertNotEquals(Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Missing"), Outcome.Failure(DomainFailure(FailureCode.CORRUPT_DATA, "Invalid", SnapshotList.empty())))
    }

    @Test fun foreignIdentityIsQualifiedAndUnresolvedWithoutChangingLanguage() {
        val raw = ProviderIdentity(provider, "printing", "0007-a")
        val identity = CardIdentity(Knowledge.Unknown("Pack absent", SnapshotList.empty()), Knowledge.Known(CardId(provider, raw.rawId)), raw, Knowledge.Known("原名"), LanguageTag("ja"))
        assertEquals("0007-a", (identity.printingId as Knowledge.Known<CardId>).value.raw)
        assertEquals("ja", identity.language.value)
        assertTrue(identity.oracleId is Knowledge.Unknown)
        assertNotEquals(CardId(provider, "0007-a"), CardId(ProviderId("different-provider"), "0007-a"))
        assertEquals(raw, identity.raw)
    }

    @Test fun provenanceKeepsSourceFetchedEffectiveAndGenerationIndependent() {
        val p = provenance()
        assertTrue(p.sourceDate is Knowledge.Unknown)
        assertEquals(UtcTimestamp(100, 12), p.fetchedAt)
        assertEquals(Knowledge.Known(UtcTimestamp(90)), p.effectiveAt)
        assertEquals(GenerationId("fixture-generation"), p.generation)
        assertThrows(IllegalArgumentException::class.java) { UtcTimestamp(1, -1) }
        assertThrows(IllegalArgumentException::class.java) { UtcTimestamp(1, 1_000_000_000) }
    }

    @Test fun cardSnapshotPreservesMultifaceStatsTextAndPrintingIdentity() {
        val oracle = OracleId(provider, "oracle-one")
        val unknown = Knowledge.Unknown("Not supplied", SnapshotList.empty())
        val stats = CardStats(Knowledge.Known("*"), unknown, Knowledge.Absent("Not planeswalker"), Knowledge.Known("0"))
        val faceId = FaceIdentity(oracle, 0, Knowledge.Known("face-raw"))
        val face = CardFace(faceId, "Synthetic front", Knowledge.Known(""), Knowledge.Known("{0}"), unknown, stats, SnapshotList.empty())
        val card = CardRecord(oracle, "Synthetic card", unknown, Knowledge.Known("{0}"), Knowledge.Known(BigDecimal.ZERO), Knowledge.Known(SnapshotList.empty()), Knowledge.Known(SnapshotList.empty()), unknown, stats, Knowledge.Known(SnapshotList.empty()), Knowledge.Known("transform"), Knowledge.Known(false), "synthetic card", LanguageTag.ENGLISH, SnapshotList.of(face), SnapshotList.empty(), provenance(), Revision(2))
        assertEquals(Knowledge.Known("0"), card.stats.defense)
        assertEquals(Knowledge.Known(""), card.faces[0].text)
        assertTrue(card.originalText is Knowledge.Unknown)
        assertEquals(faceId, card.faces[0].identity)
        val printing = PrintingRecord(CardId(provider, "print-raw"), Knowledge.Known(oracle), "SYN", unknown, "001a", LanguageTag("ja"), unknown, Knowledge.Known(SnapshotList.of(Finish.FOIL)), unknown, SnapshotList.empty(), SnapshotList.empty(), SnapshotList.empty(), SnapshotList.empty(), SnapshotList.empty(), provenance())
        assertEquals("001a", printing.collectorNumber)
        assertEquals(LanguageTag("ja"), printing.language)
    }

    @Test fun consentIsExplicitExactScopeAndRevocable() {
        val intent = DownloadIntent(EntityId("intent"), DownloadKind.LANGUAGE_PACK, provider, ResourceUri("https://example.invalid/ja-v1"), "v1", LanguageTag("ja"), EntityId("pack"), null, Knowledge.Unknown("Unknown size", SnapshotList.empty()))
        assertThrows(IllegalArgumentException::class.java) { ConsentRecord(EntityId("consent"), intent, at, null, false) }
        val consent = ConsentRecord(EntityId("consent"), intent, at, null, true)
        assertTrue(consent.permits(intent))
        assertFalse(consent.permits(intent.copy(version = "v2")))
        assertFalse(consent.permits(intent.copy(language = LanguageTag("fr"))))
        assertFalse(consent.copy(revokedAt = UtcTimestamp(101)).permits(intent))
        val settings = SettingsSnapshot(Revision(0), cache = CachePolicy(0, true, true), budget = Knowledge.Absent("Unset"), providerProfileIds = SnapshotList.empty(), sourceNotices = SnapshotList.empty(), consents = SnapshotList.empty(), updatedAt = at)
        assertEquals(LanguageTag.ENGLISH, settings.dataLanguage)
        assertTrue(settings.consents.isEmpty())
    }

    @Test fun exactScaleAndNativeUnitCannotBeSilentlyReplaced() {
        val tix = DecimalAmount.exact("1.234567", DecimalUnit.Tix)
        assertEquals(6, tix.scale)
        assertNotEquals(tix, DecimalAmount.exact("1.234567", DecimalUnit.Fiat("USD")))
        assertThrows(IllegalArgumentException::class.java) { DecimalAmount(BigDecimal("1.00"), DecimalUnit.Tix, 0) }
        assertThrows(IllegalArgumentException::class.java) { DecimalUnit.Fiat("usd") }
    }

    @Test fun localImportLimitsAndVerificationDoNotInventSourceDates() {
        assertEquals(8L * 1024 * 1024, CatalogImportLimits().maxInputBytes)
        assertThrows(IllegalArgumentException::class.java) { CatalogImportLimits(maxRecordBytes = 128 * 1024 + 1) }
        assertThrows(IllegalArgumentException::class.java) { CatalogImportLimits(batchSize = 129) }
        assertEquals(32, CatalogImportLimits().maxNesting)
        assertThrows(IllegalArgumentException::class.java) { CatalogImportLimits(maxNesting = 33) }
        val source = CatalogImportSource(ResourceUri("content://synthetic/input"), "Synthetic input", provider, LanguageTag.ENGLISH, Knowledge.Unknown("No source date", SnapshotList.empty()), Knowledge.Absent("Local file only"), Knowledge.Unknown("No expected digest", SnapshotList.empty()), Knowledge.Unknown("Unknown before streaming", SnapshotList.empty()), Knowledge.Unknown("No notice", SnapshotList.empty()))
        assertTrue(source.sourceDate is Knowledge.Unknown)
        val verified = CatalogImportVerification(1234, ContentHash("SHA-256", "abcd"), ChecksumVerification.COMPUTED_WITHOUT_EXPECTATION, true, 0)
        assertEquals(1234L, verified.observedBytes)
        assertEquals(ChecksumVerification.COMPUTED_WITHOUT_EXPECTATION, verified.checksumVerification)
    }
}
