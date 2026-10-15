package com.architectpsg.commandlibrary

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.StorageDatabase
import com.architectpsg.commandlibrary.data.storage.decks.codec.DeckDraftPayloadCodec
import com.architectpsg.commandlibrary.data.storage.decks.codec.DeckPayloadCodec
import com.architectpsg.commandlibrary.data.storage.decks.codec.LocalDeckPatchCodec
import com.architectpsg.commandlibrary.di.AppGraph
import com.architectpsg.commandlibrary.feature.decks.LocalDeckDraftPlanner
import com.architectpsg.commandlibrary.feature.decks.persistence.SqlLocalDeckDrafts
import com.architectpsg.commandlibrary.feature.decks.persistence.SqlLocalDeckLibrary
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.time.Duration

/** Actual AppGraph + Room on the host; not native execution or controller/autosave acceptance. */
@RunWith(AndroidJUnit4::class)
@Config(shadows=[RealHostDirectoryLinux::class,RealHostBlockGuardOs::class])
class DeckDraftRoomIntegrationTest {
    private val token=object:CancelToken { override val isCancelled=false;override fun throwIfCancelled()=Unit }
    private val noClock=object:Clock {
        override fun utcNow():UtcTimestamp=error("Read/raw/prepare/historical replay must not consume a clock")
        override fun monotonicNow():Duration=error("Draft repository does not consume monotonic time")
    }
    private val noIds=object:IdSource {override fun nextId():String=error("Independent reader must not generate IDs")}
    private fun <T> ok(result:Outcome<T>):T {assertTrue(result.toString(),result is Outcome.Success);return(result as Outcome.Success).value}
    private fun initial(suffix:String)=DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE,"draft-room-$suffix"),"Original",
        Knowledge.Absent("format"),"original description",Knowledge.Absent("folder"),SnapshotList.empty(),UtcTimestamp(1),UtcTimestamp(1),
        Revision(0),SnapshotList.empty(),Knowledge.Absent("budget"),null,SnapshotList.empty(),SnapshotList.empty(),"original primer",SnapshotList.empty())
    private fun reader(db:StorageDatabase)=SqlLocalDeckDrafts(db,DeckPayloadCodec(),LocalDeckPatchCodec(),DeckDraftPayloadCodec(),LocalDeckDraftPlanner(),noClock)

    @Test fun actualGraphRawSurrogateResidualGroupedUndoAndHistoricalReplaySurviveIndependentRoomReopen()=runBlocking<Unit> {
        val context=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val graph=(context as CommandLibraryApplication).graph
        val ports=ok(graph.ports());val drafts=checkNotNull(ports.localDeckDrafts);val planner=checkNotNull(ports.deckDraftPlanner)
        assertSame(drafts,ok(graph.ports()).localDeckDrafts);assertNotNull(ports.deckPatchProjection)
        val suffix=UUID.randomUUID().toString();val source=initial(suffix)
        assertEquals(source,ok(checkNotNull(ports.localDecks).create(source,EntityId("draft-create-$suffix"),token)))
        val body=DeckDraftBody.Details(Revision(0),"\uD800","saved description","saved primer")
        val written=ok(drafts.write(source.id,0,body,token));assertEquals(1L,written.generation)
        val operation=EntityId("draft-commit-$suffix")
        lateinit var prepared:PreparedDeckDraft
        ok(StorageDatabase.open(context,AppGraph.DATABASE_FILE)).use { db ->
            val independent=reader(db);val reopened=ok(independent.load(source.id,token))
            val saved=checkNotNull(reopened.draft)
            assertEquals(written,reopened);assertEquals(0xd800,(saved.body as DeckDraftBody.Details).name.single().code)
            val plan=ok(planner.propose(source,body,DeckDraftCommitScope.TEXT_AUTOSAVE,null,token))
            prepared=ok(independent.prepare(source.id,saved.token,DeckDraftCommitScope.TEXT_AUTOSAVE,plan.patch,operation,token)).intent
        }
        val ack=ok(drafts.commit(source.id,operation,prepared.requestSha256,token))
        val projected=ok(checkNotNull(ports.deckPatchProjection)(source,prepared.patch,source.revision,ack.snapshot.modifiedAt))
        assertEquals(ack.snapshot,projected)
        assertEquals(Revision(1),ack.snapshot.revision);assertEquals("Original",ack.snapshot.name)
        assertEquals("saved description",ack.snapshot.description);assertTrue(ack.slotEffect is DeckDraftSlotEffect.Residual)
        val residual=ok(drafts.load(source.id,token));val raw=residual.draft!!.body as DeckDraftBody.Details
        assertEquals(2L,residual.generation);assertEquals(Revision(1),raw.baseRevision);assertEquals("\uD800",raw.name)
        val undone=ok(checkNotNull(ports.localDeckEdits).undo(source.id,Revision(1),EntityId("draft-undo-$suffix"),token))
        assertEquals(source.copy(revision=Revision(2),modifiedAt=undone.modifiedAt),undone)
        ok(StorageDatabase.open(context,AppGraph.DATABASE_FILE)).use { db ->
            val independent=reader(db)
            assertEquals(residual,ok(independent.load(source.id,token)))
            assertEquals(ack,ok(independent.commit(source.id,operation,prepared.requestSha256,token)))
            val library=SqlLocalDeckLibrary(db,DeckPayloadCodec(),noClock,noIds)
            assertEquals(undone,ok(library.load(source.id,token)))
        }
        assertTrue(ok(ports.settings.read(token)).consents.isEmpty())
    }

    @Test fun actualRoomPreservesNewerRawExactlyThenExplicitDiscardAndHistoricalAckRemainIndependent()=runBlocking<Unit> {
        val context=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val ports=ok((context as CommandLibraryApplication).graph.ports())
        val drafts=checkNotNull(ports.localDeckDrafts);val planner=checkNotNull(ports.deckDraftPlanner)
        val suffix=UUID.randomUUID().toString();val source=initial(suffix)
        ok(checkNotNull(ports.localDecks).create(source,EntityId("later-create-$suffix"),token))
        val body=DeckDraftBody.Details(Revision(0),"First name",source.description,source.primer)
        val first=ok(drafts.write(source.id,0,body,token))
        val patch=ok(planner.propose(source,body,DeckDraftCommitScope.TEXT_AUTOSAVE,null,token)).patch
        val operation=EntityId("later-commit-$suffix")
        val prepared=ok(drafts.prepare(source.id,first.draft!!.token,DeckDraftCommitScope.TEXT_AUTOSAVE,patch,operation,token)).intent
        val later=body.copy(name="Still typing\uDFFF",description="later unconfirmed text")
        val newer=ok(drafts.write(source.id,first.generation,later,token))
        val ack=ok(drafts.commit(source.id,operation,prepared.requestSha256,token))
        assertEquals(DeckDraftSlotEffect.PreservedLater(newer.draft!!.token),ack.slotEffect)
        ok(StorageDatabase.open(context,AppGraph.DATABASE_FILE)).use { db ->
            val independent=reader(db);val reopened=ok(independent.load(source.id,token))
            assertEquals(newer.generation,reopened.generation);assertEquals(newer.draft,reopened.draft)
            assertTrue(reopened.latestPreparation is DeckDraftPreparation.Committed)
            val discarded=ok(independent.discard(source.id,reopened.generation,null,DeckDraftDiscardMode.INPUT,token))
            assertEquals(3L,discarded.generation);assertNull(discarded.draft)
            assertEquals(ack,ok(independent.commit(source.id,operation,prepared.requestSha256,token)))
        }
        assertEquals(ack.snapshot,ok(checkNotNull(ports.localDecks).load(source.id,token)))
    }
}
