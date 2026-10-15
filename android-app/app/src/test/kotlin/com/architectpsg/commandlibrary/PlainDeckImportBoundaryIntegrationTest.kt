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

/** Actual codec/graph validator/storage contract fixture, not import-controller or UI acceptance. */
@RunWith(AndroidJUnit4::class)
@Config(shadows = [RealHostDirectoryLinux::class, RealHostBlockGuardOs::class])
class PlainDeckImportBoundaryIntegrationTest {
    private val cancel = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private fun <T> ok(value: Outcome<T>): T {
        assertTrue(value.toString(), value is Outcome.Success)
        return (value as Outcome.Success).value
    }

    @Test fun realCodecAndGraphValidatorAgreeOnOrderedUnresolvedShapeThatRoomPreserves() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val ports = ok((context as CommandLibraryApplication).graph.ports())
        val codec = checkNotNull(ports.plainDeckLists)
        val validate = checkNotNull(ports.deckSnapshotValidation)
        val library = checkNotNull(ports.localDecks)
        val suffix = UUID.randomUUID().toString()
        val bytes = "Command Library Deck List 1\nDeck\n2 Café // (SET)  \n1 Café // (SET)  \n\nSideboard\n1 Unresolved card\n".toByteArray(Charsets.UTF_8)
        val split = bytes.indexOf(0xC3.toByte()) + 1 // Deliberately split the first multibyte scalar.
        val inputId = EntityId("plain-boundary-input-$suffix")
        val parsed = ok(codec.parse(TransferInput(inputId, "Synthetic list", Knowledge.Known("text/plain"),
            Knowledge.Known(bytes.size.toLong()), flowOf(
                ByteChunk(0, 0, SnapshotBytes(bytes.copyOfRange(0, split)), null, false),
                ByteChunk(1, split.toLong(), SnapshotBytes(bytes.copyOfRange(split, bytes.size)), null, true),
            )), cancel))
        val expected = listOf(PlainDeckListEntry(2, "Café // (SET)  ", BoardRole.MAIN),
            PlainDeckListEntry(1, "Café // (SET)  ", BoardRole.MAIN),
            PlainDeckListEntry(1, "Unresolved card", BoardRole.SIDEBOARD))
        assertTrue(parsed.complete); assertEquals(inputId, parsed.inputId)
        assertEquals(7, parsed.rows.size); assertEquals(expected, parsed.rows.mapNotNull { it.entry })
        assertTrue(parsed.rows.all { it.issues.isEmpty() })
        // Construct the frozen public shape from an independent fixed expected list. This
        // certifies producer/consumer compatibility, not execution of the retained controller.
        val entries = SnapshotList.copyOf(expected.mapIndexed { index, row -> DeckEntry(EntityId("plain-entry-$index-$suffix"),
            CardIdentity(Knowledge.Unknown("Plain list does not identify an Oracle card", SnapshotList.empty()),
                Knowledge.Unknown("Plain list does not identify a printing", SnapshotList.empty()), null,
                Knowledge.Known(row.name), LanguageTag("und")), row.quantity, row.board,
            Knowledge.Unknown("Physical finish not supplied by plain list", SnapshotList.empty()), "", SnapshotList.empty(), SnapshotList.empty()) })
        val time = checkNotNull(ports.utcClock).utcNow()
        val frozen = DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE, "plain-deck-$suffix"), "Plain boundary $suffix",
            Knowledge.Absent("No format selected"), "", Knowledge.Absent("No folder selected"), SnapshotList.empty(),
            time, time, Revision(0), entries, Knowledge.Absent("No budget set"), null,
            SnapshotList.empty(), SnapshotList.empty(), categoryOrder = SnapshotList.empty())
        ok(validate(frozen, cancel))
        assertTrue(validate(frozen.copy(entries = SnapshotList.of(entries[0], entries[0])), cancel) is Outcome.Failure)
        val operation = EntityId("plain-create-$suffix")
        assertEquals(frozen, ok(library.create(frozen, operation, cancel)))
        assertEquals(frozen, ok(library.create(frozen, operation, cancel)))
        ok(StorageDatabase.open(context, AppGraph.DATABASE_FILE)).use { db ->
            val reader = SqlLocalDeckLibrary(db, DeckPayloadCodec(), object : Clock {
                override fun utcNow(): UtcTimestamp = error("Read must not consume a clock")
                override fun monotonicNow(): Duration = error("Read must not consume a clock")
            }, object : IdSource { override fun nextId(): String = error("Read must not generate identities") })
            assertEquals(frozen, ok(reader.load(frozen.id, cancel)))
        }
    }
}
