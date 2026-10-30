package com.architectpsg.commandlibrary

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Real AppGraph/Room/printing reader/DataStore/consent adapter; entirely synthetic local metadata.
 * This deliberately never approves a network load and does not claim native decode/rendering.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], shadows = [RealHostDirectoryLinux::class, RealHostBlockGuardOs::class])
class CardArtworkAppIntegrationTest {
    private val token = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private fun <T> ok(result: Outcome<T>): T {
        assertTrue("Expected success: $result", result is Outcome.Success)
        return (result as Outcome.Success).value
    }
    @Test fun realPrintingMetadataAndPersistedConsentAreRequiredWithoutAutomaticWire() = runBlocking {
        val app: CommandLibraryApplication = ApplicationProvider.getApplicationContext()
        val ports = ok(app.graph.ports())
        val artwork = checkNotNull(ports.cardArtwork)
        val bytes = """[{"object":"card","id":"artwork-synthetic-print","oracle_id":"artwork-synthetic-oracle","lang":"en","name":"Synthetic Artwork Card","set":"art","collector_number":"1","type_line":"Creature","oracle_text":"Synthetic local metadata","layout":"normal","image_uris":{"normal":"https://cards.scryfall.io/normal/synthetic-art.jpg?1"}}]""".toByteArray()
        val absent = Knowledge.Absent("Synthetic local fixture")
        val source = CatalogImportSource(ResourceUri("content://selected/artwork-synthetic"), "Synthetic artwork metadata",
            ProviderId("scryfall"), LanguageTag.ENGLISH, absent, absent, absent,
            Knowledge.Known(bytes.size.toLong()), absent)
        val generation = ok(ports.catalogImport.`import`(source,
            flowOf(Outcome.Success(ByteChunk(0, 0, SnapshotBytes(bytes), null, true))), {}, token)).generation.id
        val id = CardId(ProviderId("scryfall"), "artwork-synthetic-print")
        val offer = ok(artwork.prepare(id, generation, token)).single()
        assertEquals("https://cards.scryfall.io/normal/synthetic-art.jpg?1", offer.intent.resource.value)
        val before = ok(ports.settings.read(token))
        assertFalse(ok(artwork.permitted(offer, EntityId("no-consent"), token)))
        assertTrue(artwork.load(offer, EntityId("no-consent"), token) is Outcome.Unavailable)
        assertEquals(before, ok(ports.settings.read(token)))
        val record = ok(ports.settings.approve(offer.intent, token))
        assertTrue(ok(artwork.permitted(offer, record.id, token)))
        val approved = ok(ports.settings.read(token))
        ok(ports.settings.update(SettingsPatch(SnapshotList.of(SettingChange.RevokeConsent(record.id, false))), approved.revision, token))
        assertFalse(ok(artwork.permitted(offer, record.id, token)))
        assertTrue(artwork.load(offer, record.id, token) is Outcome.Unavailable)
        assertNotNull(ok(checkNotNull(ports.catalogPrintings).printing(id, token)))
        assertEquals(0L, ok(ports.settings.read(token)).cache.imageBytes)
    }
}
