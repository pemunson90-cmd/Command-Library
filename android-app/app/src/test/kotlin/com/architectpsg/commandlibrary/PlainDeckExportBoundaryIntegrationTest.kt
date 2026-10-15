package com.architectpsg.commandlibrary

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.StorageDatabase
import com.architectpsg.commandlibrary.data.storage.decks.codec.DeckPayloadCodec
import com.architectpsg.commandlibrary.di.AppGraph
import com.architectpsg.commandlibrary.feature.decks.persistence.SqlLocalDeckLibrary
import java.util.UUID
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.time.Duration

/** Actual render/graph/Room compatibility, not export-controller, picker or writer UI evidence. */
@RunWith(AndroidJUnit4::class)
@Config(shadows = [RealHostDirectoryLinux::class, RealHostBlockGuardOs::class])
class PlainDeckExportBoundaryIntegrationTest {
    private val cancel = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private fun <T> ok(value: Outcome<T>): T {
        assertTrue(value.toString(), value is Outcome.Success)
        return (value as Outcome.Success).value
    }

    @Test fun realGraphRoomAndRendererAgreeWithIndependentAllBoardBytesAndCompleteLossOracle() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val ports = ok((context as CommandLibraryApplication).graph.ports())
        val library = checkNotNull(ports.localDecks)
        val codec = checkNotNull(ports.plainDeckLists)
        val validate = checkNotNull(ports.deckSnapshotValidation)
        val suffix = UUID.randomUUID().toString()
        val source = PlainDeckExportBoundaryFixture.source(DeckId(LocalDeckLibraryLimits.SOURCE, "export-boundary-$suffix"),
            "Export boundary Ω $suffix", checkNotNull(ports.utcClock).utcNow())
        ok(validate(source, cancel))
        assertEquals(source, ok(library.create(source, EntityId("seed-export-$suffix"), cancel)))
        val inventory = checkNotNull(ports.localInventory)
        val beforeInventory = ok(inventory.snapshot(cancel))
        val beforeSettings = ok(ports.settings.read(cancel))
        val beforeCatalog = ok(ports.catalogStatus.status(cancel))
        val loaded = ok(library.load(source.id, cancel))
        assertEquals(source, loaded)
        val rendered = ok(codec.render(loaded, cancel))
        val expectedBytes = PlainDeckExportBoundaryFixture.expectedBytes
        val expectedLosses = PlainDeckExportBoundaryFixture.expectedLosses
        assertEquals(229, expectedBytes.size)
        assertArrayEquals(expectedBytes, rendered.bytes.copyBytes())
        assertEquals(81, expectedLosses.size)
        assertEquals(expectedLosses, rendered.losses.toList())
        val inputId = EntityId("export-roundtrip-$suffix")
        val parsed = ok(codec.parse(TransferInput(inputId, "Synthetic application-owned export", Knowledge.Known("text/plain"),
            Knowledge.Known(expectedBytes.size.toLong()), flowOf(ByteChunk(0, 0, SnapshotBytes(expectedBytes), null, true))), cancel))
        assertTrue(parsed.complete); assertEquals(16, parsed.rows.size)
        assertTrue(parsed.rows.all { it.issues.isEmpty() })
        assertEquals(listOf(
            PlainDeckListEntry(1, "司令官 Ω", BoardRole.COMMANDER),
            PlainDeckListEntry(1, "Partner Æ", BoardRole.PARTNER),
            PlainDeckListEntry(1, "Background É", BoardRole.BACKGROUND),
            PlainDeckListEntry(1, "Companion 日本語", BoardRole.COMPANION),
            PlainDeckListEntry(2, "Café // (SET)  ", BoardRole.MAIN),
            PlainDeckListEntry(3, "Café // (SET)  ", BoardRole.MAIN),
            PlainDeckListEntry(4, "Réserve 日本語", BoardRole.SIDEBOARD),
            PlainDeckListEntry(5, "Maybe Ω", BoardRole.MAYBEBOARD),
        ), parsed.rows.mapNotNull { it.entry })
        assertEquals(source, ok(library.load(source.id, cancel)))
        assertEquals(beforeInventory, ok(inventory.snapshot(cancel)))
        assertEquals(beforeSettings, ok(ports.settings.read(cancel)))
        assertEquals(beforeCatalog, ok(ports.catalogStatus.status(cancel)))
        ok(StorageDatabase.open(context, AppGraph.DATABASE_FILE)).use { db ->
            val reader = SqlLocalDeckLibrary(db, DeckPayloadCodec(), object : Clock {
                override fun utcNow(): UtcTimestamp = error("Read must not consume a clock")
                override fun monotonicNow(): Duration = error("Read must not consume a clock")
            }, object : IdSource { override fun nextId(): String = error("Read must not generate identities") })
            assertEquals(source, ok(reader.load(source.id, cancel)))
        }
    }

}

/** Independent literal all-board oracle, shared by compatibility and later real UI tests only. */
internal object PlainDeckExportBoundaryFixture {
    val expectedBytes: ByteArray get() = "Command Library Deck List 1\nCommander\n1 司令官 Ω\nPartner\n1 Partner Æ\nBackground\n1 Background É\nCompanion\n1 Companion 日本語\nDeck\n2 Café // (SET)  \n3 Café // (SET)  \nSideboard\n4 Réserve 日本語\nMaybeboard\n5 Maybe Ω\n".toByteArray(Charsets.UTF_8)

    fun source(id: DeckId, name: String, time: UtcTimestamp): DeckSnapshot {
        require(id.source == LocalDeckLibraryLimits.SOURCE)
        val category = EntryLabel(EntityId("export-category-a"), "Strategy Ω")
        fun entry(raw: String, name: String, quantity: Int, board: BoardRole, rawIdentity: Boolean): DeckEntry {
            val entryId = EntityId(raw)
            val identity = if (rawIdentity) CardIdentity(
                Knowledge.Unknown("Original Oracle unresolved", SnapshotList.of(Evidence("Original Oracle evidence", null, time))),
                Knowledge.Unknown("Original printing unresolved", SnapshotList.of(Evidence("Original printing evidence", null, time))),
                ProviderIdentity(ProviderId("synthetic-archive"), "Original printed identifier", "raw id/$raw:001"),
                Knowledge.Known(name), LanguageTag("ja"),
            ) else CardIdentity(
                Knowledge.Known(OracleId(ProviderId("synthetic-export-fixture"), "oracle-$raw")),
                Knowledge.Known(CardId(ProviderId("synthetic-export-fixture"), "printing-$raw")),
                null, Knowledge.Known(name), LanguageTag("ja"),
            )
            return DeckEntry(entryId, identity, quantity, board,
                if (rawIdentity) Knowledge.Unknown("Original physical finish unresolved", SnapshotList.of(Evidence("Finish evidence", null, time)))
                else Knowledge.Known(Finish.FOIL),
                "Private strategy for $raw", SnapshotList.of(category),
                SnapshotList.of(RoleOverride(entryId, identity.oracleId, "Fixture role", true, time)))
        }
        return DeckSnapshot(id, name, Knowledge.Unknown("Format rules not recorded", SnapshotList.empty()),
            "Description omitted from plain projection.", Knowledge.Known("Fixture folder"),
            SnapshotList.of("export-fixture", "loss-aware"), time, time, Revision(0), SnapshotList.of(
                entry("export-main-a", "Café // (SET)  ", 2, BoardRole.MAIN, false),
                entry("export-side", "Réserve 日本語", 4, BoardRole.SIDEBOARD, true),
                entry("export-commander", "司令官 Ω", 1, BoardRole.COMMANDER, false),
                entry("export-partner", "Partner Æ", 1, BoardRole.PARTNER, true),
                entry("export-background", "Background É", 1, BoardRole.BACKGROUND, true),
                entry("export-companion", "Companion 日本語", 1, BoardRole.COMPANION, false),
                entry("export-maybe", "Maybe Ω", 5, BoardRole.MAYBEBOARD, true),
                entry("export-main-b", "Café // (SET)  ", 3, BoardRole.MAIN, false),
            ), Knowledge.Known(DecimalAmount.exact("12.50", DecimalUnit.Fiat("USD"))), null,
            SnapshotList.empty(), SnapshotList.empty(), "## Strategy\nNot exported.", SnapshotList.of(category))
    }

    val expectedLosses = listOf(
        PlainDeckListLoss(null, PlainDeckListLossField.DECK_ID),
        PlainDeckListLoss(null, PlainDeckListLossField.DECK_NAME),
        PlainDeckListLoss(null, PlainDeckListLossField.FORMAT),
        PlainDeckListLoss(null, PlainDeckListLossField.DESCRIPTION),
        PlainDeckListLoss(null, PlainDeckListLossField.FOLDER),
        PlainDeckListLoss(null, PlainDeckListLossField.TAGS),
        PlainDeckListLoss(null, PlainDeckListLossField.CREATED_AT),
        PlainDeckListLoss(null, PlainDeckListLossField.MODIFIED_AT),
        PlainDeckListLoss(null, PlainDeckListLossField.REVISION),
        PlainDeckListLoss(null, PlainDeckListLossField.BUDGET),
        PlainDeckListLoss(null, PlainDeckListLossField.PRIMER),
        PlainDeckListLoss(null, PlainDeckListLossField.CATEGORY_ORDER),
        PlainDeckListLoss(null, PlainDeckListLossField.ENTRY_ORDER),
        PlainDeckListLoss(EntityId("export-main-a"), PlainDeckListLossField.ENTRY_ID),
        PlainDeckListLoss(EntityId("export-main-a"), PlainDeckListLossField.ORACLE_ID),
        PlainDeckListLoss(EntityId("export-main-a"), PlainDeckListLossField.PRINTING_ID),
        PlainDeckListLoss(EntityId("export-main-a"), PlainDeckListLossField.LANGUAGE),
        PlainDeckListLoss(EntityId("export-main-a"), PlainDeckListLossField.FINISH),
        PlainDeckListLoss(EntityId("export-main-a"), PlainDeckListLossField.NOTES),
        PlainDeckListLoss(EntityId("export-main-a"), PlainDeckListLossField.LABELS),
        PlainDeckListLoss(EntityId("export-main-a"), PlainDeckListLossField.ROLE_OVERRIDES),
        PlainDeckListLoss(EntityId("export-side"), PlainDeckListLossField.ENTRY_ID),
        PlainDeckListLoss(EntityId("export-side"), PlainDeckListLossField.ORACLE_ID),
        PlainDeckListLoss(EntityId("export-side"), PlainDeckListLossField.PRINTING_ID),
        PlainDeckListLoss(EntityId("export-side"), PlainDeckListLossField.RAW_IDENTITY),
        PlainDeckListLoss(EntityId("export-side"), PlainDeckListLossField.LANGUAGE),
        PlainDeckListLoss(EntityId("export-side"), PlainDeckListLossField.FINISH),
        PlainDeckListLoss(EntityId("export-side"), PlainDeckListLossField.NOTES),
        PlainDeckListLoss(EntityId("export-side"), PlainDeckListLossField.LABELS),
        PlainDeckListLoss(EntityId("export-side"), PlainDeckListLossField.ROLE_OVERRIDES),
        PlainDeckListLoss(EntityId("export-commander"), PlainDeckListLossField.ENTRY_ID),
        PlainDeckListLoss(EntityId("export-commander"), PlainDeckListLossField.ORACLE_ID),
        PlainDeckListLoss(EntityId("export-commander"), PlainDeckListLossField.PRINTING_ID),
        PlainDeckListLoss(EntityId("export-commander"), PlainDeckListLossField.LANGUAGE),
        PlainDeckListLoss(EntityId("export-commander"), PlainDeckListLossField.FINISH),
        PlainDeckListLoss(EntityId("export-commander"), PlainDeckListLossField.NOTES),
        PlainDeckListLoss(EntityId("export-commander"), PlainDeckListLossField.LABELS),
        PlainDeckListLoss(EntityId("export-commander"), PlainDeckListLossField.ROLE_OVERRIDES),
        PlainDeckListLoss(EntityId("export-partner"), PlainDeckListLossField.ENTRY_ID),
        PlainDeckListLoss(EntityId("export-partner"), PlainDeckListLossField.ORACLE_ID),
        PlainDeckListLoss(EntityId("export-partner"), PlainDeckListLossField.PRINTING_ID),
        PlainDeckListLoss(EntityId("export-partner"), PlainDeckListLossField.RAW_IDENTITY),
        PlainDeckListLoss(EntityId("export-partner"), PlainDeckListLossField.LANGUAGE),
        PlainDeckListLoss(EntityId("export-partner"), PlainDeckListLossField.FINISH),
        PlainDeckListLoss(EntityId("export-partner"), PlainDeckListLossField.NOTES),
        PlainDeckListLoss(EntityId("export-partner"), PlainDeckListLossField.LABELS),
        PlainDeckListLoss(EntityId("export-partner"), PlainDeckListLossField.ROLE_OVERRIDES),
        PlainDeckListLoss(EntityId("export-background"), PlainDeckListLossField.ENTRY_ID),
        PlainDeckListLoss(EntityId("export-background"), PlainDeckListLossField.ORACLE_ID),
        PlainDeckListLoss(EntityId("export-background"), PlainDeckListLossField.PRINTING_ID),
        PlainDeckListLoss(EntityId("export-background"), PlainDeckListLossField.RAW_IDENTITY),
        PlainDeckListLoss(EntityId("export-background"), PlainDeckListLossField.LANGUAGE),
        PlainDeckListLoss(EntityId("export-background"), PlainDeckListLossField.FINISH),
        PlainDeckListLoss(EntityId("export-background"), PlainDeckListLossField.NOTES),
        PlainDeckListLoss(EntityId("export-background"), PlainDeckListLossField.LABELS),
        PlainDeckListLoss(EntityId("export-background"), PlainDeckListLossField.ROLE_OVERRIDES),
        PlainDeckListLoss(EntityId("export-companion"), PlainDeckListLossField.ENTRY_ID),
        PlainDeckListLoss(EntityId("export-companion"), PlainDeckListLossField.ORACLE_ID),
        PlainDeckListLoss(EntityId("export-companion"), PlainDeckListLossField.PRINTING_ID),
        PlainDeckListLoss(EntityId("export-companion"), PlainDeckListLossField.LANGUAGE),
        PlainDeckListLoss(EntityId("export-companion"), PlainDeckListLossField.FINISH),
        PlainDeckListLoss(EntityId("export-companion"), PlainDeckListLossField.NOTES),
        PlainDeckListLoss(EntityId("export-companion"), PlainDeckListLossField.LABELS),
        PlainDeckListLoss(EntityId("export-companion"), PlainDeckListLossField.ROLE_OVERRIDES),
        PlainDeckListLoss(EntityId("export-maybe"), PlainDeckListLossField.ENTRY_ID),
        PlainDeckListLoss(EntityId("export-maybe"), PlainDeckListLossField.ORACLE_ID),
        PlainDeckListLoss(EntityId("export-maybe"), PlainDeckListLossField.PRINTING_ID),
        PlainDeckListLoss(EntityId("export-maybe"), PlainDeckListLossField.RAW_IDENTITY),
        PlainDeckListLoss(EntityId("export-maybe"), PlainDeckListLossField.LANGUAGE),
        PlainDeckListLoss(EntityId("export-maybe"), PlainDeckListLossField.FINISH),
        PlainDeckListLoss(EntityId("export-maybe"), PlainDeckListLossField.NOTES),
        PlainDeckListLoss(EntityId("export-maybe"), PlainDeckListLossField.LABELS),
        PlainDeckListLoss(EntityId("export-maybe"), PlainDeckListLossField.ROLE_OVERRIDES),
        PlainDeckListLoss(EntityId("export-main-b"), PlainDeckListLossField.ENTRY_ID),
        PlainDeckListLoss(EntityId("export-main-b"), PlainDeckListLossField.ORACLE_ID),
        PlainDeckListLoss(EntityId("export-main-b"), PlainDeckListLossField.PRINTING_ID),
        PlainDeckListLoss(EntityId("export-main-b"), PlainDeckListLossField.LANGUAGE),
        PlainDeckListLoss(EntityId("export-main-b"), PlainDeckListLossField.FINISH),
        PlainDeckListLoss(EntityId("export-main-b"), PlainDeckListLossField.NOTES),
        PlainDeckListLoss(EntityId("export-main-b"), PlainDeckListLossField.LABELS),
        PlainDeckListLoss(EntityId("export-main-b"), PlainDeckListLossField.ROLE_OVERRIDES),
    )
}
