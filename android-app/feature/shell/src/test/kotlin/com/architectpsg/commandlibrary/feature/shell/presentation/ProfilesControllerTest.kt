@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.failure
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class ProfilesControllerTest {
    private fun TestScope.controller(repo: ProfilesFixture) = ProfilesController(ownerScope(),repo,StandardTestDispatcher(testScheduler))
    private fun profile(id:String="one", revision:Long=4):PlayerProfileSnapshot {
        val life=CounterDefinition(EntityId("life"),"Retained life",-100,100,20,CounterLossPolicy.AtOrBelow(0),false,CounterRole.LIFE,DisplayColor(0xff00ff00))
        val charge=CounterDefinition(EntityId("charge"),"Charge",null,null,3,CounterLossPolicy.None,true,CounterRole.OTHER,DisplayColor(0xff0000ff),CounterPreset.CHARGE,CounterResetPolicy.ON_TURN_START)
        val card=CardIdentity(Knowledge.Absent("Synthetic"),Knowledge.Absent("Synthetic"),ProviderIdentity(ProviderId("synthetic"),"card","c"),Knowledge.Known("Synthetic commander"),LanguageTag("en"))
        return PlayerProfileSnapshot(EntityId(id),Revision(revision),PlayerProfileDraft("Original",20,SnapshotList.of(life,charge),SeatAppearance(DisplayColor(0xff123456),null,orientation=SeatOrientation.INVERTED),SnapshotList.of(CommanderSlot(EntityId("commander"),card,"Partner")),2))
    }

    @Test fun createsExactUnicodeNameAndSignedLifeOnlyAfterSuccessfulWrite()=runTest {
        val repo=ProfilesFixture();val c=controller(repo)
        c.create("Life");c.changeName("  Éve  ");c.changeLife("-10");c.changeStep("3")
        repo.gate=CompletableDeferred();c.save();c.save();runCurrent()
        assertTrue(c.state.value.busy);assertNotNull(c.state.value.editor);assertEquals(1,repo.writes.size)
        c.closeEditor();assertNotNull(c.state.value.editor)
        repo.gate!!.complete(Unit);advanceUntilIdle()
        val saved=repo.rows.values.single().value
        assertEquals("  Éve  ",saved.name);assertEquals(-10,saved.preferredLife);assertEquals(3,saved.adjustmentStep)
        assertEquals(-10,saved.counterDefinitions.single().initial);assertEquals(CounterRole.LIFE,saved.counterDefinitions.single().role)
        assertNull(c.state.value.editor);assertFalse(c.state.value.busy);assertEquals(1,c.state.value.items.size)
    }

    @Test fun basicEditPreservesEveryAdvancedFieldAndUsesExactRevision()=runTest {
        val repo=ProfilesFixture();val old=profile();repo.rows[old.id]=old;val c=controller(repo)
        c.edit(old);c.changeName("New");c.changeLife("30");c.changeStep("5");c.save();advanceUntilIdle()
        val expected=old.value.copy(name="New",preferredLife=30,adjustmentStep=5,counterDefinitions=SnapshotList.copyOf(old.value.counterDefinitions.map { if(it.role==CounterRole.LIFE)it.copy(initial=30)else it }))
        assertEquals(expected,repo.rows[old.id]!!.value);assertEquals(old.id to old.revision,repo.expected.single())
    }

    @Test fun unchangedLegacyBlankNameDoesNotPreventNumericEdit()=runTest {
        val repo=ProfilesFixture();val old=profile().let { it.copy(value=it.value.copy(name="  ")) }
        repo.rows[old.id]=old;val c=controller(repo)
        c.edit(old);c.changeLife("30");c.changeStep("5");c.save();advanceUntilIdle()
        val saved=repo.rows[old.id]!!
        assertEquals("  ",saved.value.name);assertEquals(30,saved.value.preferredLife)
        assertEquals(5,saved.value.adjustmentStep);assertEquals(old.revision.next(),saved.revision)
        assertNull(c.state.value.editor)
    }

    @Test fun invalidNumericAndRetainedCounterBoundsRejectBeforeWrites()=runTest {
        val repo=ProfilesFixture();val c=controller(repo)
        for((name,life,step) in listOf(Triple(" ","20","1"),Triple("A","2147483648","1"),Triple("A","20","0"),Triple("A","20","-1"),Triple("A","20","2147483648"))) {
            c.create("Life");c.changeName(name);c.changeLife(life);c.changeStep(step);c.save();advanceUntilIdle()
            assertNotNull(c.state.value.validation);assertNotNull(c.state.value.editor)
        }
        c.edit(profile());c.changeLife("101");c.save();advanceUntilIdle()
        assertNotNull(c.state.value.validation);assertTrue(repo.writes.isEmpty())
    }

    @Test fun allNonSuccessWritesRetainDraftAndExplicitReloadAloneReconcilesStaleRevision()=runTest {
        val repo=ProfilesFixture();val old=profile();repo.rows[old.id]=old;val c=controller(repo)
        c.edit(old);c.changeName("My unsaved name")
        val cases=listOf(failure(FailureCode.PERSISTENCE) to Problem.PERSISTENCE,Outcome.Unavailable(UnavailableReason.NOT_FOUND,"Missing") to Problem.NOT_FOUND,Outcome.Unknown("Unknown commit",SnapshotList.empty()) to Problem.UNKNOWN_RESULT)
        for((status,problem) in cases) {
            repo.writeStatus=status;c.save();advanceUntilIdle()
            assertEquals(problem,c.state.value.problem);assertEquals("My unsaved name",c.state.value.editor!!.name);assertEquals(old,c.state.value.editor!!.original)
        }
        repo.writeStatus=null;val latest=old.copy(revision=Revision(9),value=old.value.copy(name="Other writer"));repo.rows[old.id]=latest
        c.save();advanceUntilIdle();assertEquals(Problem.STALE_REVISION,c.state.value.problem)
        assertEquals("My unsaved name",c.state.value.editor!!.name);assertEquals(latest,repo.rows[old.id])
        c.reloadEditor();advanceUntilIdle();assertEquals(latest,c.state.value.editor!!.original);assertEquals("Other writer",c.state.value.editor!!.name)
        assertEquals(Revision(4),repo.expected.last().second)
    }

    @Test fun deletionRequiresConfirmationUsesExactRevisionAndRefreshesCommittedList()=runTest {
        val repo=ProfilesFixture();val old=profile();repo.rows[old.id]=old;val c=controller(repo)
        c.refresh();advanceUntilIdle();c.confirmDelete();advanceUntilIdle();assertTrue(repo.deletes.isEmpty())
        c.requestDelete(old);c.dismissDelete();c.confirmDelete();advanceUntilIdle();assertTrue(repo.deletes.isEmpty())
        c.requestDelete(old);repo.writeStatus=failure(FailureCode.STALE_REVISION);c.confirmDelete();advanceUntilIdle()
        assertEquals(old,c.state.value.pendingDelete);assertEquals(Problem.STALE_REVISION,c.state.value.problem)
        repo.writeStatus=null;c.confirmDelete();advanceUntilIdle()
        assertEquals(old.id to old.revision,repo.deletes.last());assertTrue(repo.rows.isEmpty());assertTrue(c.state.value.items.isEmpty());assertNull(c.state.value.pendingDelete)
    }

    @Test fun dismissingPendingDeleteDoesNotPromiseRollbackOrLoseCommittedResult()=runTest {
        val repo=ProfilesFixture();val old=profile();repo.rows[old.id]=old;val c=controller(repo)
        c.refresh();advanceUntilIdle();c.requestDelete(old);repo.gate=CompletableDeferred()
        c.confirmDelete();runCurrent();assertTrue(c.state.value.busy)
        c.dismissDelete();assertNull(c.state.value.pendingDelete);assertTrue(c.state.value.busy)
        assertEquals(old,repo.rows[old.id]);repo.gate!!.complete(Unit);advanceUntilIdle()
        assertFalse(c.state.value.busy);assertTrue(repo.rows.isEmpty());assertTrue(c.state.value.items.isEmpty())
    }

    @Test fun oldMorePageCannotAppendAfterRefreshAndRepeatedIdsAreNotDuplicated()=runTest {
        val repo=ProfilesFixture();val first=profile("first");val second=profile("second");val late=CompletableDeferred<Unit>();var phase=0
        repo.listBlock={ page,_ ->
            when {
                page.cursor=="next" -> { withContext(NonCancellable){late.await()};repo.page(listOf(first,second),null) }
                phase==0 -> repo.page(listOf(first),"next")
                else -> repo.page(listOf(second),null)
            }
        }
        val c=controller(repo);c.refresh();advanceUntilIdle();assertEquals("next",c.state.value.nextCursor)
        c.loadMore();runCurrent();phase=1;c.refresh();runCurrent();late.complete(Unit);advanceUntilIdle()
        assertEquals(listOf(second),c.state.value.items.toList());assertNull(c.state.value.nextCursor)
        phase=0;c.refresh();advanceUntilIdle();c.loadMore();advanceUntilIdle()
        assertEquals(listOf(first,second),c.state.value.items.toList());assertEquals("next",repo.pages.last().cursor)
    }

    @Test fun clearingOwnerCancelsPendingWriteWithoutPublishingProposedProfile()=runTest {
        val repo=ProfilesFixture();repo.gate=CompletableDeferred();val owner=ownerScope();val c=ProfilesController(owner,repo,StandardTestDispatcher(testScheduler))
        c.create("Life");c.changeName("Pending");c.save();runCurrent();owner.cancel();repo.gate!!.complete(Unit);advanceUntilIdle()
        assertTrue(repo.rows.isEmpty());assertNotNull(c.state.value.editor);assertTrue(c.state.value.items.isEmpty())
    }
}

/** Test-only semantic port fixture; persistence is covered by actual SQLite and compiled app probes. */
private class ProfilesFixture:PlayerProfileRepository {
    val rows=linkedMapOf<EntityId,PlayerProfileSnapshot>();val writes=mutableListOf<PlayerProfileDraft>()
    val expected=mutableListOf<Pair<EntityId,Revision>>();val deletes=mutableListOf<Pair<EntityId,Revision>>()
    val pages=mutableListOf<PageRequest>();var gate:CompletableDeferred<Unit>?=null;var writeStatus:Outcome<Nothing>?=null
    var listBlock:(suspend(PageRequest,CancelToken)->Outcome<Page<PlayerProfileSnapshot>>)?=null
    fun page(values:List<PlayerProfileSnapshot>,cursor:String?)=Outcome.Success(Page(SnapshotList.copyOf(values),cursor,SnapshotReference(ProviderId("synthetic-profiles"),null,null,null)))
    override suspend fun list(page:PageRequest,cancel:CancelToken):Outcome<Page<PlayerProfileSnapshot>> { pages+=page;return listBlock?.invoke(page,cancel)?:this.page(rows.values.take(page.limit),null) }
    override suspend fun read(id:EntityId,cancel:CancelToken):Outcome<PlayerProfileSnapshot> = rows[id]?.let{Outcome.Success(it)}?:Outcome.Unavailable(UnavailableReason.NOT_FOUND,"Missing")
    override suspend fun create(value:PlayerProfileDraft,cancel:CancelToken):Outcome<PlayerProfileSnapshot> {
        writes+=value;gate?.await();cancel.throwIfCancelled();writeStatus?.let{return it}
        val row=PlayerProfileSnapshot(EntityId("created-${writes.size}"),Revision(0),value);rows[row.id]=row;return Outcome.Success(row)
    }
    override suspend fun update(id:EntityId,value:PlayerProfileDraft,expected:Revision,cancel:CancelToken):Outcome<PlayerProfileSnapshot> {
        writes+=value;this.expected+=id to expected;gate?.await();cancel.throwIfCancelled();writeStatus?.let{return it}
        if(rows[id]?.revision!=expected)return failure(FailureCode.STALE_REVISION)
        val row=PlayerProfileSnapshot(id,Revision(expected.value+1),value);rows[id]=row;return Outcome.Success(row)
    }
    override suspend fun delete(id:EntityId,expected:Revision,cancel:CancelToken):Outcome<Unit> {
        deletes+=id to expected;gate?.await();cancel.throwIfCancelled();writeStatus?.let{return it}
        if(rows[id]?.revision!=expected)return failure(FailureCode.STALE_REVISION)
        rows.remove(id);return Outcome.Success(Unit)
    }
}
