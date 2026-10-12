package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.common.sameCanonicalGameData
import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ScryfallCodecTest {
    private val provider = ProviderId("scryfall")
    private val generation = GenerationId("test")
    private val observed = UtcTimestamp(123)
    private fun map(raw: String) = ScryfallCodec.mapped(raw, provider, generation, null,
        Knowledge.Unknown("No source date", SnapshotList.empty()), observed, null, null, 32)
    @Test fun genuineCardsRetainCanonicalAndPrintingDifferences() = runBlocking {
        val bytes = javaClass.getResourceAsStream("/english-three-printings.json")!!.readBytes()
        val framed = mutableListOf<String>()
        val framer = JsonArrayFramer(CatalogImportLimits())
        framer.accept(ByteChunk(0, 0, SnapshotBytes(bytes), null, true)) { framed.add(it) }
        framer.finish()
        val rows = framed.map(::map)
        assertEquals(3, rows.size)
        val bolts = rows.filter { it.first.name == "Lightning Bolt" }
        assertEquals(2, bolts.size)
        assertTrue(sameCanonicalGameData(bolts[0].first, bolts[1].first))
        assertNotEquals(bolts[0].second.id, bolts[1].second.id)
        assertNotEquals(bolts[0].second.images, bolts[1].second.images)
        val delver = rows.single { it.first.name.startsWith("Delver") }
        assertEquals(2, delver.first.faces.size)
        assertFalse((delver.first.types as Knowledge.Known).value.subtypes.contains("//"))
        assertEquals(2, delver.second.faceAssets.size)
        assertTrue(delver.second.faceAssets.all { it.images.isNotEmpty() })
        assertTrue(delver.second.relationships.isNotEmpty())
        assertTrue(delver.second.relationships.all { it.rawRelatedIdentity is Knowledge.Known })
        assertTrue(delver.first.provenance.sourceDate is Knowledge.Unknown)
    }
    @Test fun strictJsonAndTypedFields() {
        assertThrows(Exception::class.java) { ScryfallCodec.parse("{\"x\":1,\"x\":2}", 32) }
        assertThrows(Exception::class.java) { ScryfallCodec.parse("{\"x\":NaN}", 32) }
        assertThrows(Exception::class.java) { ScryfallCodec.parse("{\"a\":[[[1]]]}", 2) }
        val raw = """{"object":"card","id":"print","oracle_id":"oracle","name":"Éclair","lang":"en","set":"a","collector_number":"001a","cmc":1.25,"colors":[],"color_identity":[]}"""
        val (card, printing) = map(raw)
        assertEquals("001a", printing.collectorNumber)
        assertEquals("éclair", card.normalizedSearchText)
        assertEquals("1.25", (card.manaValue as Knowledge.Known).value.toPlainString())
        assertTrue(card.originalText is Knowledge.Absent)
        assertThrows(Exception::class.java) { map(raw.replace("\"lang\":\"en\"", "\"lang\":\"fr\"")) }
        assertThrows(Exception::class.java) { map(raw.replace("\"cmc\":1.25", "\"cmc\":-1")) }
        assertThrows(Exception::class.java) { map(raw.replace("\"cmc\":1.25", "\"cmc\":1e99999999")) }
        assertThrows(Exception::class.java) { map(raw.replace("\"name\":\"Éclair\"", "\"name\":\"Éclair\",\"card_faces\":[{\"object\":\"wrong\",\"name\":\"Face\"}]")) }
        assertThrows(Exception::class.java) { map(raw.replace("Éclair", "ﷺ".repeat(100))) }
        assertThrows(Exception::class.java) { map(raw.replace("Éclair", "bad\\u0000name")) }
    }
    @Test fun genuineMeldComponentsKeepRawPrintingIdentities() {
        val raw = javaClass.getResourceAsStream("/hanweir-battlements.json")!!.bufferedReader().readText()
        val (_, printing) = map(raw)
        assertEquals(4, printing.relationships.size)
        assertEquals(2, printing.relationships.count { it.kind == FaceRelationshipKind.MELD_PART })
        assertEquals(1, printing.relationships.count { it.kind == FaceRelationshipKind.MELD_RESULT })
        assertTrue(printing.relationships.all { it.rawRelatedIdentity is Knowledge.Known && it.relatedOracle is Knowledge.Unknown })
        assertThrows(Exception::class.java) { map(raw.replace("\"component\":\"meld_part\"", "\"component\":\"invented\"")) }
    }
}
