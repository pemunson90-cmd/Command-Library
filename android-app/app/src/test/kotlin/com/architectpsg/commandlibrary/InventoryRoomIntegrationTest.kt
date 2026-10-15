package com.architectpsg.commandlibrary

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.StorageDatabase
import com.architectpsg.commandlibrary.data.storage.collection.codec.LocalInventoryPatchCodec
import com.architectpsg.commandlibrary.data.storage.collection.codec.OwnedLotPayloadCodec
import com.architectpsg.commandlibrary.feature.collection.persistence.SqlLocalInventory
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Real application composition plus independent Room connection; not native/process-kill/device proof. */
@RunWith(AndroidJUnit4::class)
@Config(shadows = [RealHostDirectoryLinux::class, RealHostBlockGuardOs::class])
class InventoryRoomIntegrationTest {
    @get:Rule val folder=TemporaryFolder()
    private val token=object:CancelToken{override val isCancelled=false;override fun throwIfCancelled()=Unit}
    private fun <T> ok(value:Outcome<T>):T=(value as Outcome.Success).value
    @Test fun actualGraphInventoryMergeDeckAllocationUndoReplayReservationsAndIndependentReaderAgree()=runBlocking<Unit>{
        val context=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val graph=(context as CommandLibraryApplication).graph
        val ports=ok(graph.ports());val inventory=checkNotNull(ports.localInventory)
        val allocator=checkNotNull(ports.ownedQuantity);val decks=checkNotNull(ports.localDecks)
        assertSame(inventory,ok(graph.ports()).localInventory);assertSame(allocator,ok(graph.ports()).ownedQuantity)
        val original=ok(inventory.snapshot(token));assertTrue(original.complete)
        val suffix=UUID.randomUUID().toString()
        val identity=CardIdentity(Knowledge.Known(OracleId(ProviderId("host-fixture"),"oracle-$suffix")),
            Knowledge.Known(CardId(ProviderId("host-fixture"),"printing-$suffix")),ProviderIdentity(ProviderId("foreign"),"raw","retained-$suffix"),
            Knowledge.Known("Synthetic host fixture"),LanguageTag.ENGLISH)
        val first=OwnedLot(EntityId("room-a-$suffix"),identity,Knowledge.Known(Finish.NONFOIL),CardCondition.UNKNOWN,
            "Shelf",3,"Preserved metadata",Revision(0),Knowledge.Unknown("Date unavailable",SnapshotList.empty()))
        val second=first.copy(id=EntityId("room-z-$suffix"),quantity=2)
        val patch=CollectionPatch(SnapshotList.of(CollectionEdit.Add(second),CollectionEdit.Add(first)))
        val operation=EntityId("inventory-apply-$suffix")
        val committed=(ok(inventory.apply(patch,original.revision,operation,token)) as InventoryApplyResult.Committed).commit
        assertEquals(Revision(original.revision.value+1),committed.projection.resultRevision)
        assertEquals(InventoryMerge(second.id,first.id,2),committed.projection.merges.single())
        val current=ok(inventory.snapshot(token));val stored=current.lots.single{it.id==first.id}
        assertEquals(5,stored.quantity);assertEquals(first.identity,stored.identity);assertEquals(first.acquiredAt,stored.acquiredAt)
        assertFalse(current.lots.any{it.id==second.id})
        val entry=DeckEntry(EntityId("entry-$suffix"),identity,7,BoardRole.MAIN,Knowledge.Absent("No finish restriction"),"",SnapshotList.empty(),SnapshotList.empty())
        val deck=DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE,"inventory-deck-$suffix"),"Inventory allocation",Knowledge.Absent("none"),"",
            Knowledge.Absent("none"),SnapshotList.empty(),UtcTimestamp(1),UtcTimestamp(1),Revision(0),SnapshotList.of(entry),Knowledge.Absent("none"),null,
            SnapshotList.empty(),SnapshotList.empty())
        ok(decks.create(deck,EntityId("inventory-deck-create-$suffix"),token))
        val loaded=ok(decks.load(deck.id,token));val plan=ok(allocator.allocate(loaded,current,OwnedQuantityPolicy(SnapshotList.of(BoardRole.MAIN)),token))
        assertEquals(current.revision,plan.inventoryRevision);assertEquals(loaded.revision,plan.deckRevision)
        assertEquals(5,plan.demands.single().ownedQuantity);assertEquals(2,plan.demands.single().remainingQuantity);assertTrue(plan.complete)
        val undo=ok(inventory.undo(current.revision,EntityId("inventory-undo-$suffix"),token))
        val restored=ok(inventory.snapshot(token));assertEquals(undo.projection.resultRevision,restored.revision)
        assertEquals(original.lots,restored.lots)
        assertEquals(committed,(ok(inventory.apply(patch,original.revision,operation,token)) as InventoryApplyResult.Committed).commit)
        assertEquals(restored,ok(inventory.snapshot(token)))
        val rejected=ok(inventory.apply(CollectionPatch(SnapshotList.of(CollectionEdit.Add(second))),restored.revision,EntityId("reserved-$suffix"),token)) as InventoryApplyResult.Rejected
        assertTrue(rejected.issues.any{it.entryId==second.id&&it.code==InventoryIssueCodes.LOT_ALREADY_EXISTS})
        assertEquals(restored.revision,rejected.actualRevision)
        ok(StorageDatabase.open(context,com.architectpsg.commandlibrary.di.AppGraph.DATABASE_FILE)).use{db->
            val reader=SqlLocalInventory(db,OwnedLotPayloadCodec(),LocalInventoryPatchCodec(),object:Clock{
                override fun utcNow():UtcTimestamp=error("Snapshot/stale request must not read clock")
                override fun monotonicNow():kotlin.time.Duration=error("Inventory does not use monotonic clock")
            },ok(db.capabilities(token)).maxBoundParameters)
            assertEquals(restored,ok(reader.snapshot(token)))
            val stale=reader.apply(CollectionPatch(SnapshotList.empty()),current.revision,EntityId("stale-$suffix"),token)
            assertEquals(FailureCode.STALE_REVISION,(stale as Outcome.Failure).error.code)
        }
        assertTrue(ok(ports.settings.read(token)).consents.isEmpty())
    }
    /** Storage finish replacement is not catalog eligibility proof; the bulk consumer supplies that separately. */
    @Test fun actualGraphMixedBulkPreservesZeroUnknownEvidenceAndUnselectedRowsAcrossReopenUndoAndReplay()=runBlocking<Unit>{
        val context=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val ports=ok((context as CommandLibraryApplication).graph.ports())
        val inventory=checkNotNull(ports.localInventory);val project=checkNotNull(ports.inventoryProjection)
        val original=ok(inventory.snapshot(token));val suffix=UUID.randomUUID().toString()
        fun lot(index:Int)=OwnedLot(EntityId("bulk-$index-$suffix"),CardIdentity(
            Knowledge.Unknown("Unresolved Oracle",SnapshotList.empty()),Knowledge.Absent("No printing"),
            ProviderIdentity(ProviderId("foreign-bulk"),"raw","row-$index-$suffix"),Knowledge.Known("Synthetic bulk $index"),LanguageTag.ENGLISH),
            Knowledge.Unknown("Unresolved finish",SnapshotList.empty()),CardCondition.UNKNOWN,"Shelf $index",index+2,
            "Retained note $index",Revision(0),Knowledge.Unknown("Acquisition unknown",SnapshotList.empty()))
        val lots=(0..2).map(::lot)
        ok(inventory.apply(CollectionPatch(SnapshotList.copyOf(lots.map{CollectionEdit.Add(it)})),original.revision,EntityId("bulk-seed-$suffix"),token))
        val before=ok(inventory.snapshot(token));val a=before.lots.single{it.id==lots[0].id};val b=before.lots.single{it.id==lots[1].id}
        val untouched=before.lots.single{it.id==lots[2].id}
        val patch=CollectionPatch(SnapshotList.of(CollectionEdit.Replace(a.copy(quantity=0,condition=CardCondition.NEAR_MINT,location="")),
            CollectionEdit.Replace(b.copy(quantity=7,condition=CardCondition.LIGHT_PLAYED,location="Box",finish=Knowledge.Known(Finish.FOIL)))))
        val preview=ok(project(before,patch,before.revision,token))
        assertTrue(preview.issues.isEmpty());assertEquals(setOf(a.id,b.id),preview.changes.map{it.lotId}.toSet())
        assertEquals(before,ok(inventory.snapshot(token))) // Preview alone has no write effect; cancellation is covered separately by consumer tests.
        val operation=EntityId("bulk-edit-$suffix")
        val commit=(ok(inventory.apply(patch,before.revision,operation,token)) as InventoryApplyResult.Committed).commit
        assertEquals(preview,commit.projection)
        val after=ok(inventory.snapshot(token));assertEquals(Revision(before.revision.value+1),after.revision)
        assertEquals(a.copy(quantity=0,condition=CardCondition.NEAR_MINT,location="",revision=after.revision),after.lots.single{it.id==a.id})
        assertEquals(b.copy(quantity=7,condition=CardCondition.LIGHT_PLAYED,location="Box",finish=Knowledge.Known(Finish.FOIL),revision=after.revision),after.lots.single{it.id==b.id})
        assertEquals(untouched,after.lots.single{it.id==untouched.id})
        ok(StorageDatabase.open(context,com.architectpsg.commandlibrary.di.AppGraph.DATABASE_FILE)).use{db->
            val reopened=SqlLocalInventory(db,OwnedLotPayloadCodec(),LocalInventoryPatchCodec(),com.architectpsg.commandlibrary.di.SystemUtcClock,
                ok(db.capabilities(token)).maxBoundParameters)
            assertEquals(after,ok(reopened.snapshot(token)))
            val undo=ok(reopened.undo(after.revision,EntityId("bulk-undo-$suffix"),token))
            val restored=ok(reopened.snapshot(token));assertEquals(undo.projection.resultRevision,restored.revision)
            assertEquals(before.lots.map{it.id}.toSet(),restored.lots.map{it.id}.toSet())
            for(lot in before.lots){
                val value=restored.lots.single{it.id==lot.id}
                assertEquals(if(lot.id==a.id||lot.id==b.id)lot.copy(revision=restored.revision)else lot,value)
            }
            assertEquals(commit,(ok(reopened.apply(patch,before.revision,operation,token)) as InventoryApplyResult.Committed).commit)
            assertEquals(restored,ok(reopened.snapshot(token))) // Historical ack never overwrites the undone head.
        }
        ok(StorageDatabase.open(context,com.architectpsg.commandlibrary.di.AppGraph.DATABASE_FILE)).use{db->
            val reopened=SqlLocalInventory(db,OwnedLotPayloadCodec(),LocalInventoryPatchCodec(),com.architectpsg.commandlibrary.di.SystemUtcClock,
                ok(db.capabilities(token)).maxBoundParameters)
            val restored=ok(reopened.snapshot(token))
            assertEquals(before.revision.value+2,restored.revision.value)
            assertEquals(a.copy(revision=restored.revision),restored.lots.single{it.id==a.id})
            assertEquals(b.copy(revision=restored.revision),restored.lots.single{it.id==b.id})
            assertEquals(untouched,restored.lots.single{it.id==untouched.id})
        }
    }
    @Test fun cancelledCompositionClosesUnpublishedDatabaseAndRetryPublishesLiveRealPorts()=runBlocking<Unit>{
        val base=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val root=folder.newFolder("isolated-composition")
        val context=object:android.content.ContextWrapper(base){
            override fun getApplicationContext():android.content.Context=this
            override fun getFilesDir()=java.io.File(root,"files").apply{mkdirs()}
            override fun getNoBackupFilesDir()=java.io.File(root,"no-backup").apply{mkdirs()}
            override fun getDatabasePath(name:String)=java.io.File(root,"databases/$name").apply{parentFile!!.mkdirs()}
        }
        val opened=CompletableDeferred<StorageDatabase>();var probes=0;var published:StorageDatabase?=null
        val graph=com.architectpsg.commandlibrary.di.AppGraph(context){db->
            if(++probes==1){opened.complete(db);awaitCancellation()}
            published=db;db.capabilities(token)
        }
        val first=launch(Dispatchers.Default){graph.ports()}
        val unpublished=opened.await();first.cancelAndJoin()
        assertTrue(first.isCancelled)
        val query=SqlRequest("SELECT 1 AS n",SnapshotList.empty(),1)
        assertEquals(FailureCode.INTERNAL,(unpublished.query(query,token) as Outcome.Failure).error.code)
        try{
            val ports=ok(graph.ports());assertEquals(2,probes)
            assertSame(ports,ok(graph.ports()));assertTrue(ok(checkNotNull(ports.localInventory).snapshot(token)).complete)
            assertTrue(checkNotNull(published).query(query,token) is Outcome.Success)
        }finally{published?.close()}
    }

}
