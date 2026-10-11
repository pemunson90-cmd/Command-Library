package com.architectpsg.commandlibrary.core.model

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/** Synthetic multi-face fixtures isolate oracle game data from printing-specific producer data. */
class PrintingFaceAssetTest {
    private val source = ProviderId("synthetic-provider")
    private val oracle = OracleId(source, "canonical-oracle")
    private val unknown = Knowledge.Unknown("Not supplied by synthetic source", SnapshotList.empty())
    private val absent = Knowledge.Absent("Not applicable")
    private val provenance = Provenance(
        source, null, Knowledge.Known("fixture-v1"), unknown, UtcTimestamp(100),
        unknown, GenerationId("fixture-generation"), null, null,
    )
    private val stats = CardStats(absent, absent, absent, absent)

    private fun canonicalCard(): CardRecord = CardRecord(
        oracleId = oracle,
        name = "Synthetic front // Synthetic back",
        originalText = absent,
        manaCost = absent,
        manaValue = Knowledge.Known(BigDecimal.ONE),
        colors = Knowledge.Known(SnapshotList.empty()),
        colorIdentity = Knowledge.Known(SnapshotList.empty()),
        types = absent,
        stats = stats,
        keywords = Knowledge.Known(SnapshotList.empty()),
        layout = Knowledge.Known("transform"),
        reserved = Knowledge.Known(false),
        normalizedSearchText = "synthetic front synthetic back",
        language = LanguageTag.ENGLISH,
        faces = SnapshotList.of(
            CardFace(FaceIdentity(oracle, 0, absent), "Synthetic front", Knowledge.Known("Front rules"), Knowledge.Known("{1}"), unknown, stats, SnapshotList.empty()),
            CardFace(FaceIdentity(oracle, 1, absent), "Synthetic back", Knowledge.Known("Back rules"), absent, unknown, stats, SnapshotList.empty()),
        ),
        providerIdentities = SnapshotList.empty(),
        provenance = provenance,
        revision = Revision(1),
    )

    private fun printing(id: CardId, artPrefix: String): PrintingRecord = PrintingRecord(
        id = id,
        oracleId = Knowledge.Known(oracle),
        setCode = "SYN",
        setName = Knowledge.Known("Synthetic set"),
        collectorNumber = id.raw,
        language = LanguageTag.ENGLISH,
        rarity = Knowledge.Known("rare"),
        finishes = Knowledge.Known(SnapshotList.of(Finish.NONFOIL)),
        imageUri = absent,
        images = SnapshotList.empty(),
        faceAssets = SnapshotList.copyOf((0..1).map { index ->
            PrintingFaceAsset(
                identity = PrintingFaceIdentity(id, index, absent),
                language = LanguageTag.ENGLISH,
                printedName = Knowledge.Known(if (index == 0) "Synthetic front" else "Synthetic back"),
                printedText = unknown,
                printedTypeLine = unknown,
                flavorText = unknown,
                artist = Knowledge.Known("Synthetic artist $artPrefix"),
                illustrationIdentity = Knowledge.Known(ProviderIdentity(source, "illustration", "$artPrefix-$index")),
                images = SnapshotList.of(PrintingImageAsset("normal", ResourceUri("https://example.invalid/$artPrefix-$index.png"), unknown, unknown)),
                relationships = SnapshotList.empty(),
            )
        }),
        relationships = SnapshotList.empty(),
        suppliedLinks = SnapshotList.empty(),
        vendorIds = SnapshotList.empty(),
        provenance = provenance,
    )

    @Test fun twoEnglishPrintingsShareCanonicalFacesAndRetainTheirOwnArt() {
        val cardForFirstPrinting = canonicalCard()
        val cardForSecondPrinting = canonicalCard()
        val first = printing(CardId(source, "printing-one"), "art-one")
        val second = printing(CardId(source, "printing-two"), "art-two").copy(
            provenance = provenance.copy(source = ResourceUri("content://synthetic/second-printing"), fetchedAt = UtcTimestamp(200)),
            vendorIds = SnapshotList.of(ProviderIdentity(source, "vendor", "second-printing-vendor-id")),
        )

        assertEquals(cardForFirstPrinting, cardForSecondPrinting)
        assertEquals(first.oracleId, second.oracleId)
        assertNotEquals(first.provenance, second.provenance)
        assertNotEquals(first.vendorIds, second.vendorIds)
        assertEquals(2, first.faceAssets.size)
        assertEquals(2, second.faceAssets.size)
        assertEquals(first.language, second.language)
        assertNotEquals(first.faceAssets[0].images[0].uri, second.faceAssets[0].images[0].uri)
        assertNotEquals(first.faceAssets[1].images[0].uri, second.faceAssets[1].images[0].uri)
        assertEquals(first.id, first.faceAssets[0].identity.printingId)
        assertEquals(second.id, second.faceAssets[1].identity.printingId)
        assertTrue(first.faceAssets.all { it.identity.providerFaceId is Knowledge.Absent })
        assertEquals(cardForFirstPrinting, canonicalCard())
    }

    @Test fun unresolvedRelatedPrintingRetainsRawSourceIdentityWithoutOracleGuess() {
        val relatedPrinting = CardId(source, "producer-meld-result-printing")
        val relatedRaw = ProviderIdentity(source, "printing", relatedPrinting.raw)
        val relationship = FaceRelationship(
            FaceRelationshipKind.MELD_RESULT, unknown, unknown,
            Knowledge.Known(relatedPrinting), Knowledge.Known(relatedRaw), SnapshotList.empty(),
        )
        val record = printing(CardId(source, "producer-meld-part-printing"), "part-art")
            .copy(relationships = SnapshotList.of(relationship))

        assertEquals(Knowledge.Known(relatedPrinting), record.relationships[0].relatedPrinting)
        assertEquals(Knowledge.Known(relatedRaw), record.relationships[0].rawRelatedIdentity)
        assertTrue(record.relationships[0].relatedOracle is Knowledge.Unknown)
        assertTrue(record.relationships[0].relatedFace is Knowledge.Unknown)
        assertEquals(source, relatedPrinting.source)
        assertTrue(canonicalCard().faces.all { it.relationships.isEmpty() })
    }
}
