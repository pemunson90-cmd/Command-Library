package com.architectpsg.commandlibrary.core.common

import com.architectpsg.commandlibrary.core.model.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CanonicalCardComparisonTest {
    private val source = ProviderId("synthetic-provider")
    private val oracle = OracleId(source, "synthetic-oracle")
    private val absent = Knowledge.Absent("Not applicable")
    private val unknown = Knowledge.Unknown("Not supplied", SnapshotList.empty())
    private val stats = CardStats(absent, absent, absent, absent)
    private val provenance = Provenance(source, null, Knowledge.Known("fixture-v1"), unknown, UtcTimestamp(100), unknown, GenerationId("generation"), null, null)

    private fun card(): CardRecord = CardRecord(
        oracle, "Synthetic front // Synthetic back", absent, absent, Knowledge.Known(BigDecimal.ONE),
        Knowledge.Known(SnapshotList.empty()), Knowledge.Known(SnapshotList.empty()), absent, stats,
        Knowledge.Known(SnapshotList.empty()), Knowledge.Known("transform"), Knowledge.Known(false),
        "synthetic front synthetic back", LanguageTag.ENGLISH,
        SnapshotList.of(
            CardFace(FaceIdentity(oracle, 0, absent), "Synthetic front", Knowledge.Known("Front rules"), Knowledge.Known("{1}"), unknown, stats, SnapshotList.empty()),
            CardFace(FaceIdentity(oracle, 1, absent), "Synthetic back", Knowledge.Known("Back rules"), absent, unknown, stats, SnapshotList.empty()),
        ),
        SnapshotList.empty(), provenance, Revision(1),
    )

    @Test fun differentAcquisitionMetadataAndMappingsCannotCauseCanonicalConflict() {
        val first = card()
        val second = first.copy(
            provenance = provenance.copy(
                source = ResourceUri("content://synthetic/second-printing-file"),
                fetchedAt = UtcTimestamp(200),
                checksum = ContentHash("SHA-256", "abcd"),
            ),
            providerIdentities = SnapshotList.of(ProviderIdentity(source, "oracle-alternate-mapping", "producer-oracle-id")),
            revision = Revision(7),
            normalizedSearchText = "different derived search normalization",
        )
        assertNotEquals(first, second)
        assertTrue(sameCanonicalGameData(first, second))
        assertTrue(sameCanonicalGameData(second, first))
    }

    @Test fun realGameDifferencesAndUnknownVersusZeroRemainConflicts() {
        val first = card()
        val changedFace = first.faces[1].copy(text = Knowledge.Known("Changed back rules"))
        assertFalse(sameCanonicalGameData(first, first.copy(faces = SnapshotList.of(first.faces[0], changedFace))))
        assertFalse(sameCanonicalGameData(first.copy(manaValue = unknown), first.copy(manaValue = Knowledge.Known(BigDecimal.ZERO))))
        assertFalse(sameCanonicalGameData(first, first.copy(oracleId = OracleId(ProviderId("different-source"), oracle.raw))))
    }

    @Test fun diagnosticEvidenceAndProducerFaceMappingStayStoredWithoutChangingGameIdentity() {
        val first = card()
        val evidence = SnapshotList.of(Evidence("Synthetic second source observation", ResourceUri("https://example.invalid/second"), UtcTimestamp(200)))
        val secondFace = first.faces[0].copy(
            identity = first.faces[0].identity.copy(providerFaceId = Knowledge.Known("actual-producer-canonical-face-id")),
            types = Knowledge.Unknown("Second acquisition lacks type data", evidence),
        )
        val second = first.copy(faces = SnapshotList.of(secondFace, first.faces[1]))
        assertTrue(sameCanonicalGameData(first, second))
        assertEquals(Knowledge.Known("actual-producer-canonical-face-id"), second.faces[0].identity.providerFaceId)
        assertEquals(evidence, (second.faces[0].types as Knowledge.Unknown).evidence)
    }
}
