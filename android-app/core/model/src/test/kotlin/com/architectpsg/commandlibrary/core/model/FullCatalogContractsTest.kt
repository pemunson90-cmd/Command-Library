package com.architectpsg.commandlibrary.core.model

import org.junit.Assert.*
import org.junit.Test

class FullCatalogContractsTest {
    private val unknown = Knowledge.Unknown("No upstream snapshot date", SnapshotList.empty())
    private val absent = Knowledge.Absent("Not supplied")
    private val hash = ContentHash("SHA-256", "a".repeat(64))
    private fun local() = CatalogBulkImportSource(ResourceUri("content://local/catalog"), "test.jsonl", ProviderId("scryfall"),
        LanguageTag.ENGLISH, unknown, absent, absent, absent, absent, ArtifactFraming.JSONL, ArtifactCompression.NONE)
    private fun artifact() = VerifiedArtifact(EntityId("job"), ArtifactFraming.JSONL, ArtifactCompression.NONE,
        3, hash, false, Knowledge.Known(1), 1, AcquisitionProvenance(ProviderId("scryfall"), ResourceUri("https://api.scryfall.com/cards/search"),
            ResourceUri("https://api.scryfall.com/cards/search"), unknown, unknown, UtcTimestamp(1), UtcTimestamp(2),
            unknown, unknown, AcquisitionConsistency.NON_ATOMIC_TRAVERSAL, AcquisitionLanguageScope.ENGLISH_CARDS, absent))
    private fun downloaded(): CatalogBulkImportSource {
        val a = artifact()
        return local().copy(inputKind = SourceInputKind.VERIFIED_ARTIFACT, verifiedArtifact = a,
            sourceUri = Knowledge.Known(a.provenance.requestedUri), expectedBytes = Knowledge.Known(a.bytes), expectedChecksum = Knowledge.Known(a.sha256))
    }
    @Test fun fullLimitsPreserveTinyCapabilityAndExplicitSingletonAllowance() {
        assertEquals(8L * 1024 * 1024, CatalogImportLimits().maxInputBytes)
        assertEquals(10_000, CatalogImportLimits().maxPrintingRecords)
        val full = CatalogBulkImportLimits()
        assertTrue(full.maxRecordBytes > full.maxOrdinaryBatchBytes)
        assertThrows(IllegalArgumentException::class.java) { full.copy(maxPrintingRecords = 500_001) }
        assertThrows(IllegalArgumentException::class.java) { full.copy(maxRawBytes = Long.MAX_VALUE) }
        assertThrows(IllegalArgumentException::class.java) { full.copy(maxRecordBytes = 8 * 1024 * 1024 + 1) }
        assertThrows(IllegalArgumentException::class.java) { full.copy(maxChunkBytes = 65537) }
        assertThrows(IllegalArgumentException::class.java) { full.copy(maxBatchRecords = 1001) }
    }
    @Test fun downloadedSourceMustPreserveExactDescriptorAndTruthfulOrigin() {
        val d = downloaded()
        assertEquals(AcquisitionConsistency.NON_ATOMIC_TRAVERSAL, d.verifiedArtifact!!.provenance.consistency)
        assertThrows(IllegalArgumentException::class.java) { d.copy(expectedBytes = Knowledge.Known(4)) }
        assertThrows(IllegalArgumentException::class.java) { d.copy(expectedChecksum = absent) }
        assertThrows(IllegalArgumentException::class.java) { d.copy(sourceDate = Knowledge.Known(UtcTimestamp(9))) }
        assertThrows(IllegalArgumentException::class.java) { d.copy(framing = ArtifactFraming.JSON_ARRAY) }
        assertThrows(IllegalArgumentException::class.java) { d.copy(sourceUri = absent) }
        assertThrows(IllegalArgumentException::class.java) { d.copy(inputKind = SourceInputKind.USER_CONTENT_URI) }
        assertThrows(IllegalArgumentException::class.java) { d.copy(verifiedArtifact = null) }
    }
    @Test fun selectedSourceAndLocatorCannotImpersonateDownloadedArtifact() {
        assertNull(local().verifiedArtifact)
        assertThrows(IllegalArgumentException::class.java) { local().copy(contentUri = ResourceUri("file:///private/catalog")) }
        assertThrows(IllegalArgumentException::class.java) { local().copy(contentUri = ResourceUri("content:///missing-authority")) }
        assertThrows(IllegalArgumentException::class.java) { local().copy(language = LanguageTag("ja")) }
        assertThrows(IllegalArgumentException::class.java) { local().copy(framing = ArtifactFraming.TEXT) }
        assertThrows(IllegalArgumentException::class.java) { local().copy(expectedBytes = Knowledge.Known(0)) }
        assertThrows(IllegalArgumentException::class.java) { local().copy(expectedChecksum = Knowledge.Known(ContentHash("sha256", "a".repeat(64)))) }
    }
}
