package com.architectpsg.commandlibrary

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.StorageDatabase
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Actual Room on host with real-file OS bridge; not native/device execution. */
@RunWith(AndroidJUnit4::class)
@Config(shadows = [RealHostDirectoryLinux::class, RealHostBlockGuardOs::class])
class DeckSchemaRoomIntegrationTest {
    private val token = object : CancelToken {
        override val isCancelled = false
        override fun throwIfCancelled() = Unit
    }
    private fun <T> ok(value: Outcome<T>): T = (value as Outcome.Success).value
    @Test fun actualRoomFreshAndSchema9And11And12UpgradesPreserveOldBytesAndOpenInventory() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (baseVersion in listOf<Int?>(null, 9, 11, 12)) {
            val name = "deck-schema-${UUID.randomUUID()}.db"
            try {
                if (baseVersion != null) {
                    val file = context.getDatabasePath(name); file.parentFile!!.mkdirs()
                    BundledSQLiteDriver().open(file.path).use { c ->
                        val fixture = File(checkNotNull(System.getProperty("commandlibrary.schema${baseVersion}.fixture"))).readText()
                        fixture.split(";\n").map(String::trim).filter(String::isNotEmpty).forEach { q -> c.prepare(q).use { it.step() } }
                        c.prepare("PRAGMA user_version=$baseVersion").use { it.step() }
                        c.prepare("INSERT INTO life_profile VALUES('profile',0,'É','é',X'0001FF',7,9)").use { it.step() }
                    }
                }
                ok(StorageDatabase.open(context, name)).use { db ->
                    assertEquals(13, ok(db.capabilities(token)).schemaVersion)
                    for (table in listOf("deck_head", "deck_revision", "deck_operation", "deck_undo_link", "deck_undo_state", "inventory_state", "inventory_lot", "inventory_operation", "inventory_group", "inventory_change")) {
                        val rows = ok(db.query(SqlRequest("SELECT count(*) AS n FROM $table", SnapshotList.empty(), 1), token))
                        assertEquals(SqlValue.Integer(0), rows.single().columns.single().value)
                    }
                    if (baseVersion != null) {
                        val rows = ok(db.query(SqlRequest("SELECT hex(payload) AS payload FROM life_profile WHERE profile_id='profile'", SnapshotList.empty(), 1), token))
                        assertEquals(SqlValue.Text("0001FF"), rows.single().columns.single().value)
                    }
                }
            } finally { context.deleteDatabase(name) }
        }
    }
    @Test fun actualAppGraphLibraryCreatesDuplicatesAndIndependentRoomReaderReopensExactData() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val graph = (context as CommandLibraryApplication).graph
        val ports = ok(graph.ports())
        val library = checkNotNull(ports.localDecks)
        assertSame(library, ok(graph.ports()).localDecks)
        val suffix = UUID.randomUUID().toString()
        val entry = DeckEntry(EntityId("entry-$suffix"), CardIdentity(Knowledge.Absent("missing Oracle"), Knowledge.Absent("missing printing"),
            ProviderIdentity(ProviderId("foreign"),"raw","do-not-coalesce"), Knowledge.Known("Foreign card"), LanguageTag("ja")),
            2, BoardRole.COMMANDER, Knowledge.Unknown("finish unresolved",SnapshotList.empty()),"notes",SnapshotList.empty(),SnapshotList.empty())
        val initial = DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE,"room-$suffix"),"Room deck",Knowledge.Absent("none"),"description",
            Knowledge.Absent("none"),SnapshotList.empty(),UtcTimestamp(1),UtcTimestamp(1),Revision(0),SnapshotList.of(entry),Knowledge.Absent("none"),null,
            SnapshotList.empty(),SnapshotList.empty(),primer="# Retained primer",categoryOrder=SnapshotList.empty())
        val operation = EntityId("create-$suffix")
        assertEquals(initial,ok(library.create(initial,operation,token)))
        assertEquals(initial,ok(library.create(initial,operation,token)))
        val copy = ok(library.duplicate(initial.id,Revision(0),"Copy",EntityId("duplicate-$suffix"),token))
        assertNotEquals(initial.id,copy.id);assertNotEquals(entry.id,copy.entries.single().id)
        assertEquals(entry.identity,copy.entries.single().identity);assertEquals(initial.primer,copy.primer)
        assertEquals(initial,ok(library.load(initial.id,token)))
        val page = ok(library.list(PageRequest(100),token))
        assertTrue(page.items.any{it.id==initial.id});assertTrue(page.items.any{it.id==copy.id})
        ok(StorageDatabase.open(context,com.architectpsg.commandlibrary.di.AppGraph.DATABASE_FILE)).use { db ->
            val reader = com.architectpsg.commandlibrary.feature.decks.persistence.SqlLocalDeckLibrary(db,
                com.architectpsg.commandlibrary.data.storage.decks.codec.DeckPayloadCodec(),
                object:Clock {override fun utcNow()=UtcTimestamp(1);override fun monotonicNow()=kotlin.time.Duration.ZERO},
                object:IdSource {override fun nextId():String=error("Independent reader must not generate IDs")})
            assertEquals(initial,ok(reader.load(initial.id,token)));assertEquals(copy,ok(reader.load(copy.id,token)))
        }
        assertTrue(ok(ports.settings.read(token)).consents.isEmpty())
    }

    @Test fun actualAppGraphGroupedEditsUndoHistoricalReceiptsAndIndependentRoomReaderAgree() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val graph = (context as CommandLibraryApplication).graph
        val ports = ok(graph.ports())
        val library = checkNotNull(ports.localDecks)
        val edits = checkNotNull(ports.localDeckEdits)
        assertSame(edits, ok(graph.ports()).localDeckEdits)
        val suffix = UUID.randomUUID().toString()
        val category = EntryLabel(EntityId("category-$suffix"), "Unused or used category")
        val first = DeckEntry(EntityId("first-$suffix"), CardIdentity(Knowledge.Absent("foreign Oracle"), Knowledge.Absent("foreign printing"),
            ProviderIdentity(ProviderId("foreign"),"raw","retained-$suffix"), Knowledge.Known("Foreign card"), LanguageTag("ja")),
            2,BoardRole.SIDEBOARD,Knowledge.Unknown("finish unresolved",SnapshotList.empty()),"old notes",SnapshotList.of(category),SnapshotList.empty())
        val second = first.copy(id=EntityId("second-$suffix"),quantity=3,labels=SnapshotList.empty())
        val initial = DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE,"room-edit-$suffix"),"Original",Knowledge.Absent("none"),"description",
            Knowledge.Absent("none"),SnapshotList.empty(),UtcTimestamp(1),UtcTimestamp(1),Revision(0),SnapshotList.of(first,second),Knowledge.Absent("none"),null,
            SnapshotList.empty(),SnapshotList.empty(),primer="# Original primer",categoryOrder=SnapshotList.of(category))
        val createOp = EntityId("create-edit-$suffix")
        assertEquals(initial,ok(library.create(initial,createOp,token)))
        val patch = DeckPatch(SnapshotList.of(DeckEdit.Replace(first.copy(quantity=7,board=BoardRole.MAIN,finish=Knowledge.Known(Finish.FOIL),notes="new notes")),
            DeckEdit.Remove(second.id),DeckEdit.Rename("Edited"),DeckEdit.Primer("# Changed primer")),"One grouped edit")
        val applyOp=EntityId("apply-$suffix")
        val applied=ok(edits.apply(initial.id,patch,Revision(0),applyOp,token))
        assertEquals(Revision(1),applied.revision);assertEquals(1,applied.entries.size);assertEquals(7,applied.entries.single().quantity)
        assertEquals(first.identity,applied.entries.single().identity);assertEquals("Edited",applied.name)
        val undoOp=EntityId("undo-$suffix")
        val restored=ok(edits.undo(initial.id,Revision(1),undoOp,token))
        assertEquals(initial.copy(revision=Revision(2),modifiedAt=restored.modifiedAt),restored)
        assertEquals(initial,ok(library.create(initial,createOp,token)))
        assertEquals(applied,ok(edits.apply(initial.id,patch,Revision(0),applyOp,token)))
        assertEquals(restored,ok(edits.undo(initial.id,Revision(1),undoOp,token)))
        assertEquals(restored,ok(library.load(initial.id,token)))
        assertEquals(FailureCode.INVALID_INPUT,(edits.apply(initial.id,patch,Revision(0),createOp,token) as Outcome.Failure).error.code)
        ok(StorageDatabase.open(context,com.architectpsg.commandlibrary.di.AppGraph.DATABASE_FILE)).use { db ->
            val reader=com.architectpsg.commandlibrary.feature.decks.persistence.SqlLocalDeckLibrary(db,
                com.architectpsg.commandlibrary.data.storage.decks.codec.DeckPayloadCodec(),
                object:Clock {override fun utcNow()=error("Reader must not consume clock");override fun monotonicNow()=kotlin.time.Duration.ZERO},
                object:IdSource {override fun nextId():String=error("Reader must not generate IDs")})
            assertEquals(restored,ok(reader.load(initial.id,token)))
        }
        assertTrue(ok(ports.settings.read(token)).consents.isEmpty())
    }

}
