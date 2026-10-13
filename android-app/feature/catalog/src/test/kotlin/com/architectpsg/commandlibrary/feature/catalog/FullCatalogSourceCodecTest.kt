package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class FullCatalogSourceCodecTest {
    private val unknown = Knowledge.Unknown("Snapshot not asserted", SnapshotList.of(Evidence("Traversal is non-atomic", ResourceUri("https://api.scryfall.com"), UtcTimestamp(7))))
    private val absent = Knowledge.Absent("Not supplied")
    private val hash = ContentHash("SHA-256", "a".repeat(64))
    private fun selected() = CatalogBulkImportSource(ResourceUri("content://local/cards"), "English cards", ProviderId("scryfall"),
        LanguageTag.ENGLISH, unknown, absent, absent, absent, absent, ArtifactFraming.JSONL, ArtifactCompression.NONE)
    private fun acquired(): CatalogBulkImportSource {
        val artifact = VerifiedArtifact(EntityId("job"), ArtifactFraming.JSONL, ArtifactCompression.NONE, 99, hash,
            false, Knowledge.Known(2), 1, AcquisitionProvenance(ProviderId("scryfall"), ResourceUri("https://api.scryfall.com/cards/search"),
                ResourceUri("https://api.scryfall.com/cards/search"), unknown, unknown, UtcTimestamp(8), UtcTimestamp(9),
                absent, absent, AcquisitionConsistency.NON_ATOMIC_TRAVERSAL, AcquisitionLanguageScope.ENGLISH_CARDS, absent))
        return selected().copy(inputKind = SourceInputKind.VERIFIED_ARTIFACT, verifiedArtifact = artifact,
            sourceUri = Knowledge.Known(artifact.provenance.requestedUri), expectedBytes = Knowledge.Known(99), expectedChecksum = Knowledge.Known(hash))
    }
    @Test fun selectedAndAcquiredPreserveExactKnowledgeAndTraversalProvenance() {
        for (source in listOf(selected(), acquired())) {
            val encoded = AcquisitionPayloadCodec.encodeCatalogSource(source)
            assertEquals(source, AcquisitionPayloadCodec.decodeCatalogSource(encoded))
            assertEquals(encoded, AcquisitionPayloadCodec.encodeCatalogSource(AcquisitionPayloadCodec.decodeCatalogSource(encoded)))
        }
    }
    @Test fun mutationCannotTurnSelectedInputIntoVerifiedDownloadOrChangeArtifactHash() {
        rejectMutation(selected()) { it.addProperty("inputKind", "VERIFIED_ARTIFACT") }
        rejectMutation(acquired()) { it.getAsJsonObject("expectedChecksum").getAsJsonObject("value").addProperty("hex", "b".repeat(64)) }
        rejectMutation(acquired()) { it.addProperty("contentUri", "https://api.scryfall.com/cards/search") }
        rejectMutation(acquired()) { it.addProperty("unexpected", "field") }
    }
    @Test fun strictEnvelopeRejectsVersionsWrongTypesAndOversizeWithoutSourceLeak() {
        val encoded = AcquisitionPayloadCodec.encodeCatalogSource(selected())
        for (payload in listOf(encoded.replace("\"version\":1", "\"version\":2"), encoded.replace("\"catalogSource\"", "\"artifact\""),
            "secret".repeat(AcquisitionPayloadCodec.MAX_BYTES))) assertRejected(payload)
        rejectMutation(selected()) { it.addProperty("expectedBytes", 1.5) }
    }
    private fun rejectMutation(source: CatalogBulkImportSource, mutation: (com.google.gson.JsonObject) -> Unit) {
        val root = JsonParser.parseString(AcquisitionPayloadCodec.encodeCatalogSource(source)).asJsonObject
        mutation(root.getAsJsonObject("value")); assertRejected(root.toString())
    }
    private fun assertRejected(payload: String) {
        try { AcquisitionPayloadCodec.decodeCatalogSource(payload); fail("Accepted malformed source") }
        catch (e: IllegalArgumentException) { assertEquals("Invalid acquisition payload", e.message); assertNull(e.cause) }
    }
}
