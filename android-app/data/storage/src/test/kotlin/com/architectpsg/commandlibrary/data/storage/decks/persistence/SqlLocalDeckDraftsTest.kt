package com.architectpsg.commandlibrary.data.storage.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.decks.persistence.DraftFixture.Companion.ok
import com.architectpsg.commandlibrary.data.storage.decks.persistence.DraftFixture.Companion.failure
import com.architectpsg.commandlibrary.data.storage.decks.persistence.DraftFixture.Companion.unavailable
import com.architectpsg.commandlibrary.data.storage.decks.persistence.DraftFixture.Companion.content
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SqlLocalDeckDraftsTest {
    @Test fun absentSlotIsGenerationZeroAndRawWriteDoesNotTouchContentClockOrEpoch()=runBlocking {
        DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); assertEquals(DeckDraftState(0,null,null),ok(f.service().load(a.id,f.cancel)))
            val before=f.db.state(); val s=f.write(f.details(a,"")); assertEquals(1L,s.generation); assertEquals("",(s.draft!!.body as DeckDraftBody.Details).name)
            assertEquals(0,f.clockCalls); assertEquals(before.filterKeys { it != "deck_draft_state" },f.db.state().filterKeys { it != "deck_draft_state" })
            assertEquals(s,ok(f.service().write(a.id,0,s.draft!!.body,f.cancel)))
        }
    }
    @Test fun rawPartialNumbersLoneSurrogatesAndStagedChoicesSurviveReopenExactly()=runBlocking {
        val file=HostDeckDraftDatabase.databaseFile(); lateinit var state:DeckDraftState
        DraftFixture(HostDeckDraftDatabase(file)).use { f ->
            val a=f.create(); state=f.write(f.form(a).copy(quantity="-",notes="\uD800x\uDFFF",board=BoardRole.COMMANDER,labels=SnapshotList.of(EntryLabel(EntityId("raw"),""))))
        }
        DraftFixture(HostDeckDraftDatabase(file,false)).use { f -> assertEquals(state,ok(f.service().load(f.deck().id,f.cancel))); assertEquals(0,f.clockCalls) }
    }
    @Test fun partialDetailsCommitKeepsExactInvalidNameAtNewRevisionAndOneUndoGroup()=runBlocking {
        DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); val body=f.details(a,"\uD800").copy(primer="new primer"); val s=f.write(body); val p=f.prepare(a,s); val ack=f.commit(p)
            assertEquals("new primer",ack.snapshot.primer); assertEquals(a.name,ack.snapshot.name); assertEquals(Revision(1),ack.snapshot.revision)
            assertTrue(ack.slotEffect is DeckDraftSlotEffect.Residual); val loaded=ok(f.service().load(a.id,f.cancel)); assertEquals(2L,loaded.generation)
            assertEquals(body.copy(baseRevision=Revision(1)),loaded.draft!!.body); assertEquals((ack.slotEffect as DeckDraftSlotEffect.Residual).token,loaded.draft!!.token)
            assertTrue(loaded.latestPreparation is DeckDraftPreparation.Committed); assertEquals(1L,f.db.count("deck_undo_link")); assertEquals(1,f.clockCalls)
            content(a,ok(f.edits.undo(a.id,ack.snapshot.revision,EntityId("undo"),f.cancel)))
        }
    }
    @Test fun entryTextDoesNotLeakStagedChoicesThenExplicitCommitUsesAllConfirmedValues()=runBlocking {
        DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); val body=f.form(a).copy(quantity="-",notes="new notes",board=BoardRole.COMMANDER,finish=Knowledge.Known(Finish.FOIL))
            val p=f.prepare(a,f.write(body)); val ack=f.commit(p); assertEquals(a.entries.first().copy(notes="new notes"),ack.snapshot.entries.first())
            val state=ok(f.service().load(a.id,f.cancel)); assertEquals(body.copy(baseRevision=Revision(1)),state.draft!!.body)
            val valid=(state.draft!!.body as DeckDraftBody.Entry).copy(quantity="03"); val next=f.write(valid,state.generation)
            val explicit=f.prepare(ack.snapshot,next,"explicit",DeckDraftCommitScope.EXPLICIT_CONFIRMED); val confirmed=f.commit(explicit)
            assertEquals(3,confirmed.snapshot.entries.first().quantity); assertEquals(BoardRole.COMMANDER,confirmed.snapshot.entries.first().board)
            assertEquals(Knowledge.Known(Finish.FOIL),confirmed.snapshot.entries.first().finish); assertNull(ok(f.service().load(a.id,f.cancel)).draft)
        }
    }
    @Test fun explicitAddFreezesOneEntryIdAndRemoveIsNeverInferredFromRaw()=runBlocking {
        DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); val body=DeckDraftBody.Add(a.revision,"1","new",BoardRole.SIDEBOARD,Knowledge.Known(Finish.ETCHED),SnapshotList.empty(),DeckDraftSelection(a.entries.first().identity,GenerationId("pinned")))
            val s=f.write(body); val empty=DeckPatch(SnapshotList.empty(),DeckDraftLimits.TEXT_REASON)
            failure(f.service().prepare(a.id,s.draft!!.token,DeckDraftCommitScope.TEXT_AUTOSAVE,empty,EntityId("not-auto"),f.cancel),FailureCode.INVALID_INPUT)
            val p=f.prepare(a,s,"add",DeckDraftCommitScope.EXPLICIT_CONFIRMED,EntityId("new-entry")); val ack=f.commit(p)
            assertEquals(2,ack.snapshot.entries.size); assertEquals(EntityId("new-entry"),ack.snapshot.entries.last().id); assertEquals(body.selection!!.identity,ack.snapshot.entries.last().identity)
            assertEquals(ack,f.commit(p)); assertEquals(2L,f.db.count("deck_revision"))
            val removed=f.write(DeckDraftBody.Remove(ack.snapshot.revision,EntityId("new-entry")),2)
            val rp=f.prepare(ack.snapshot,removed,"remove",DeckDraftCommitScope.EXPLICIT_CONFIRMED); val r=f.commit(rp)
            assertEquals(a.entries,r.snapshot.entries); assertEquals(2L,f.db.count("deck_undo_link"))
        }
    }
    @Test fun aliasOnlyEmptyCommitConsumesRawWithoutClockRevisionOrUndo()=runBlocking {
        DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); val s=f.write(f.form(a).copy(quantity="02")); val p=f.prepare(a,s); assertTrue(p.intent.patch.edits.isEmpty())
            val before=f.db.state(); val ack=f.commit(p); assertEquals(a,ack.snapshot); assertEquals(DeckDraftSlotEffect.Cleared(2),ack.slotEffect)
            assertEquals(0,f.clockCalls); assertEquals(0L,f.db.count("deck_undo_link")); assertEquals(before.getValue("deck_revision"),f.db.state().getValue("deck_revision"))
            assertEquals(ack,f.commit(p)); assertNull(ok(f.service().load(a.id,f.cancel)).draft)
        }
    }
    @Test fun unchangedInvalidOnlyAndForgedNoncanonicalPlansCannotPrepare()=runBlocking {
        DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); val s=f.write(f.details(a,"")); val before=f.db.state()
            for(patch in listOf(DeckPatch(SnapshotList.empty(),DeckDraftLimits.TEXT_REASON),DeckPatch(SnapshotList.of(DeckEdit.Primer("invented")),DeckDraftLimits.TEXT_REASON),DeckPatch(SnapshotList.empty(),"wrong reason")))
                failure(f.service().prepare(a.id,s.draft!!.token,DeckDraftCommitScope.TEXT_AUTOSAVE,patch,EntityId("no"),f.cancel),FailureCode.INVALID_INPUT)
            assertEquals(before,f.db.state()); assertEquals(0L,f.db.count("deck_draft_intent"))
        }
    }
    @Test fun preparationPinsWithoutChangingRawAndOnlyOneUnresolvedIntentExists()=runBlocking {
        DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); val s=f.write(f.details(a)); val before=f.db.state(); val p=f.prepare(a,s)
            val loaded=ok(f.service().load(a.id,f.cancel)); assertEquals(s.draft,loaded.draft); assertEquals(s.generation,loaded.generation); assertEquals(p,loaded.latestPreparation)
            assertEquals(before.filterKeys { it !in setOf("deck_draft_state","deck_draft_intent") },f.db.state().filterKeys { it !in setOf("deck_draft_state","deck_draft_intent") })
            assertEquals(0,f.clockCalls); assertEquals(p,f.prepare(a,s)); failure(f.service().prepare(a.id,s.draft!!.token,p.intent.scope,p.intent.patch,EntityId("other"),f.cancel),FailureCode.STALE_REVISION)
        }
    }
    @Test fun rawWriteRetryReportsCurrentPendingAndPreservedLaterCommittedStatus()=runBlocking {
        DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); val body=f.details(a); val s=f.write(body); val p=f.prepare(a,s)
            assertEquals(p,ok(f.service().write(a.id,0,body,f.cancel)).latestPreparation)
            val laterBody=f.details(a,"later"); val later=f.write(laterBody,1); val slotBefore=f.db.state().getValue("deck_draft_state"); val ack=f.commit(p)
            assertEquals(DeckDraftSlotEffect.PreservedLater(later.draft!!.token),ack.slotEffect)
            val after=ok(f.service().load(a.id,f.cancel)); assertEquals(later.draft,after.draft); assertEquals(later.generation,after.generation)
            assertEquals(slotBefore,f.db.state().getValue("deck_draft_state"))
            val retry=ok(f.service().write(a.id,1,laterBody,f.cancel)); assertTrue(retry.latestPreparation is DeckDraftPreparation.Committed); assertEquals(after,retry)
            failure(f.service().write(a.id,0,body,f.cancel),FailureCode.STALE_REVISION)
            failure(f.service().prepare(a.id,later.draft!!.token,p.intent.scope,DeckPatch(SnapshotList.of(DeckEdit.Rename("later")),DeckDraftLimits.TEXT_REASON),EntityId("stale-base"),f.cancel),FailureCode.STALE_REVISION)
        }
    }
    @Test fun prepareAndCommitReplayBeforeCorruptHeadSlotAndClockAfterReopen()=runBlocking {
        val file=HostDeckDraftDatabase.databaseFile(); lateinit var p:DeckDraftPreparation; lateinit var ack:DeckDraftCommitAck
        DraftFixture(HostDeckDraftDatabase(file)).use { f ->
            val a=f.create(); p=f.prepare(a,f.write(f.details(a))); ack=f.commit(p); val b=f.rename(ack.snapshot)
            ok(f.edits.undo(a.id,b.revision,EntityId("undo"),f.cancel)); f.db.sql("UPDATE deck_head SET name='corrupt'"); f.db.sql("UPDATE deck_draft_state SET state_sha256='corrupt'")
        }
        DraftFixture(HostDeckDraftDatabase(file,false)).use { f ->
            val before=f.db.state(); val intent=p.intent
            assertEquals(ack,ok(f.service().commit(intent.sourceToken.deckId,intent.operationId,intent.requestSha256,f.cancel)))
            val replay=ok(f.service().prepare(intent.sourceToken.deckId,intent.sourceToken,intent.scope,intent.patch,intent.operationId,f.cancel))
            assertEquals(ack,(replay as DeckDraftPreparation.Committed).acknowledgement); assertEquals(0,f.clockCalls); assertEquals(before,f.db.state())
        }
    }
    @Test fun freshPrepareRejectsStaleRawBaseWithoutReservingTheOperation()=runBlocking {
        DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); val s=f.write(f.details(a)); val changed=f.rename(a); val before=f.db.state(); val patch=ok(f.planner.propose(a,s.draft!!.body,DeckDraftCommitScope.TEXT_AUTOSAVE,null,f.cancel)).patch
            failure(f.service().prepare(a.id,s.draft!!.token,DeckDraftCommitScope.TEXT_AUTOSAVE,patch,EntityId("stale"),f.cancel),FailureCode.STALE_REVISION)
            assertEquals(before,f.db.state()); assertEquals(changed,ok(f.library.load(a.id,f.cancel)))
        }
    }
    @Test fun discardInputRequiresNamedPendingAndAbandonsPermanentlyWithImmediateReplay()=runBlocking {
        DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); val s=f.write(f.details(a)); val p=f.prepare(a,s); val before=f.db.state()
            failure(f.service().discard(a.id,1,null,DeckDraftDiscardMode.INPUT,f.cancel),FailureCode.STALE_REVISION); assertEquals(before,f.db.state())
            val cleared=ok(f.service().discard(a.id,1,p.intent.operationId,DeckDraftDiscardMode.INPUT,f.cancel)); assertEquals(2L,cleared.generation); assertNull(cleared.draft); assertTrue(cleared.latestPreparation is DeckDraftPreparation.Abandoned)
            assertEquals(cleared,ok(f.service().discard(a.id,1,p.intent.operationId,DeckDraftDiscardMode.INPUT,f.cancel)))
            failure(f.service().commit(a.id,p.intent.operationId,p.intent.requestSha256,f.cancel),FailureCode.STALE_REVISION); assertEquals(0,f.clockCalls)
        }
    }
    @Test fun preparationOnlyDiscardRetainsExactRawAndAllowsNewGenerationPreparation()=runBlocking {
        DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); val s=f.write(f.details(a)); val p=f.prepare(a,s)
            val kept=ok(f.service().discard(a.id,1,p.intent.operationId,DeckDraftDiscardMode.PREPARATION_ONLY,f.cancel)); assertEquals(s.draft!!.body,kept.draft!!.body); assertEquals(2L,kept.generation)
            val next=f.prepare(a,kept,"new"); assertEquals(next,ok(f.service().load(a.id,f.cancel)).latestPreparation)
            val retry=ok(f.service().discard(a.id,1,p.intent.operationId,DeckDraftDiscardMode.PREPARATION_ONLY,f.cancel)); assertEquals(next,retry.latestPreparation)
            failure(f.service().prepare(a.id,kept.draft!!.token,next.intent.scope,next.intent.patch,p.intent.operationId,f.cancel),FailureCode.INVALID_INPUT)
            assertEquals(2L,f.db.count("deck_draft_intent"))
        }
    }
    @Test fun commitWinningBeforeDiscardCannotBeAbandoned()=runBlocking {
        DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); val p=f.prepare(a,f.write(f.details(a))); val ack=f.commit(p); val before=f.db.state()
            failure(f.service().discard(a.id,1,p.intent.operationId,DeckDraftDiscardMode.INPUT,f.cancel),FailureCode.STALE_REVISION)
            failure(f.service().discard(a.id,2,p.intent.operationId,DeckDraftDiscardMode.PREPARATION_ONLY,f.cancel),FailureCode.STALE_REVISION)
            assertEquals(before,f.db.state()); assertEquals(ack,f.commit(p))
        }
    }
    @Test fun twoIndependentConnectionsUseGenerationCasAndDiscoverPreparedRestart()=runBlocking {
        val file=HostDeckDraftDatabase.databaseFile()
        DraftFixture(HostDeckDraftDatabase(file)).use { first ->
            val a=first.create(); val s=first.write(first.details(a)); val p=first.prepare(a,s)
            DraftFixture(HostDeckDraftDatabase(file,false)).use { second ->
                assertEquals(p,ok(second.service().load(a.id,second.cancel)).latestPreparation)
                val next=second.write(second.details(a,"later"),1); val before=first.db.state()
                failure(first.service().write(a.id,1,first.details(a,"conflict"),first.cancel),FailureCode.STALE_REVISION)
                assertEquals(before,first.db.state()); val ack=first.commit(p); assertEquals(DeckDraftSlotEffect.PreservedLater(next.draft!!.token),ack.slotEffect)
                assertEquals(ack,second.commit(p))
            }
        }
    }
    @Test fun everyLegacyCapabilityRejectsPendingCommittedAndAbandonedReservedIds()=runBlocking {
        for(status in 0..2) DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); val p=f.prepare(a,f.write(f.details(a))); val current=when(status) { 1 -> f.commit(p).snapshot; 2 -> { ok(f.service().discard(a.id,1,p.intent.operationId,DeckDraftDiscardMode.INPUT,f.cancel)); a }; else -> a }
            val target=ok(f.versions.capture(a.id,current.revision,"named",EntityId("named"),f.cancel)); val before=f.db.state(); val op=p.intent.operationId
            failure(f.library.create(a,op,f.cancel),FailureCode.INVALID_INPUT); failure(f.library.duplicate(a.id,current.revision,"Copy",op,f.cancel),FailureCode.INVALID_INPUT)
            failure(f.edits.apply(a.id,DeckPatch(SnapshotList.empty(),"empty"),current.revision,op,f.cancel),FailureCode.INVALID_INPUT); failure(f.edits.undo(a.id,current.revision,op,f.cancel),FailureCode.INVALID_INPUT)
            failure(f.versions.capture(a.id,current.revision,"named",op,f.cancel),FailureCode.INVALID_INPUT); failure(f.versions.restore(a.id,target.id,current.revision,"R",op,f.cancel),FailureCode.INVALID_INPUT)
            assertEquals(before,f.db.state()); if(status == 1) assertEquals("Changed",f.commit(p).snapshot.name)
        }
    }
    @Test fun legacyReceiptsCrossDeckIdsAndInvalidClientFramesCannotBePrepared()=runBlocking {
        DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); val s=f.write(f.details(a)); val patch=ok(f.planner.propose(a,s.draft!!.body,DeckDraftCommitScope.TEXT_AUTOSAVE,null,f.cancel)).patch; val before=f.db.state()
            failure(f.service().prepare(a.id,s.draft!!.token,DeckDraftCommitScope.TEXT_AUTOSAVE,patch,EntityId("create"),f.cancel),FailureCode.INVALID_INPUT)
            failure(f.service().prepare(a.id,s.draft!!.token.copy(deckId=a.id.copy(raw="other")),DeckDraftCommitScope.TEXT_AUTOSAVE,patch,EntityId("other"),f.cancel),FailureCode.INVALID_INPUT)
            failure(f.service().write(a.id,-1,s.draft!!.body,f.cancel),FailureCode.INVALID_INPUT); failure(f.service().write(a.id.copy(source=ProviderId("foreign")),0,s.draft!!.body,f.cancel),FailureCode.INVALID_INPUT)
            failure(f.service().write(a.id,1,s.draft!!.body.let { (it as DeckDraftBody.Details).copy(baseRevision=Revision(99)) },f.cancel),FailureCode.INVALID_INPUT)
            unavailable(f.service().load(a.id.copy(raw="missing"),f.cancel),UnavailableReason.NOT_FOUND); assertEquals(before,f.db.state())
        }
    }
    @Test fun allOldReceiptKindsAndCrossDeckIntentIdsRemainGloballyReserved()=runBlocking {
        DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); val b=f.rename(a,"B","apply"); val u=ok(f.edits.undo(a.id,b.revision,EntityId("undo"),f.cancel))
            ok(f.library.duplicate(a.id,u.revision,"Copy",EntityId("duplicate"),f.cancel))
            val captured=ok(f.versions.capture(a.id,u.revision,"Checkpoint",EntityId("capture"),f.cancel))
            val restored=ok(f.versions.restore(a.id,captured.id,u.revision,"Recovery",EntityId("restore"),f.cancel)).snapshot
            val state=f.write(f.details(restored)); val patch=ok(f.planner.propose(restored,state.draft!!.body,DeckDraftCommitScope.TEXT_AUTOSAVE,null,f.cancel)).patch
            val before=f.db.state()
            for(operation in listOf("create","apply","undo","duplicate","capture","restore"))
                failure(f.service().prepare(a.id,state.draft!!.token,DeckDraftCommitScope.TEXT_AUTOSAVE,patch,EntityId(operation),f.cancel),FailureCode.INVALID_INPUT)
            assertEquals(before,f.db.state())
            val p=f.prepare(restored,state)
            val other=f.deck().copy(id=a.id.copy(raw="another")); ok(f.library.create(other,EntityId("create-other"),f.cancel))
            val otherState=ok(f.service().write(other.id,0,f.details(other),f.cancel))
            failure(f.service().prepare(other.id,otherState.draft!!.token,p.intent.scope,patch,p.intent.operationId,f.cancel),FailureCode.INVALID_INPUT)
            failure(f.service().commit(other.id,p.intent.operationId,p.intent.requestSha256,f.cancel),FailureCode.INVALID_INPUT)
        }
    }
    @Test fun generatedResidualBranchesConserveIdentityAndUndoOneGroupPerCommit()=runBlocking {
        DraftFixture(HostDeckDraftDatabase()).use { f ->
            val initial=f.create(); var current=initial; var generation=0L
            repeat(8) { index ->
                val body=f.form(current).copy(quantity="0002",notes="notes-$index",board=BoardRole.SIDEBOARD)
                val state=f.write(body,generation); val p=f.prepare(current,state,"generated-$index"); val ack=f.commit(p)
                assertEquals(initial.entries.first().identity,ack.snapshot.entries.first().identity); assertEquals(BoardRole.MAIN,ack.snapshot.entries.first().board)
                val loaded=ok(f.service().load(initial.id,f.cancel)); assertEquals(body.copy(baseRevision=ack.snapshot.revision,quantity="2"),loaded.draft!!.body)
                assertEquals(ack,f.commit(p)); generation=loaded.generation; current=ack.snapshot
            }
            assertEquals(8L,f.db.count("deck_undo_link"))
            repeat(8) { current=ok(f.edits.undo(initial.id,current.revision,EntityId("undo-generated-$it"),f.cancel)) }
            content(initial,current); assertEquals(Revision(16),current.revision)
            assertTrue(ok(f.service().load(initial.id,f.cancel)).latestPreparation is DeckDraftPreparation.Committed)
        }
    }
    @Test fun projectedCombinedDeckBudgetIsCheckedAtCommitWithoutLosingRawOrIntent()=runBlocking {
        DraftFixture(HostDeckDraftDatabase()).use { f ->
            val large="x".repeat(1_048_576); val a=f.create(f.deck().copy(entries=SnapshotList.copyOf(List(7) { f.entry("e$it").copy(notes=large) })))
            val s=f.write(f.details(a,a.name).copy(description=large,primer=large)); val p=f.prepare(a,s); val before=f.db.state()
            failure(f.service().commit(a.id,p.intent.operationId,p.intent.requestSha256,f.cancel),FailureCode.INVALID_INPUT)
            assertEquals(before,f.db.state()); assertEquals(s.draft,ok(f.service().load(a.id,f.cancel)).draft); assertTrue(ok(f.service().load(a.id,f.cancel)).latestPreparation is DeckDraftPreparation.Pending)
        }
    }
    @Test fun actualPendingLatestAndReverseReceiptPlansAreBoundedIndexedSeeks()=runBlocking {
        DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); val p=f.prepare(a,f.write(f.details(a))); f.commit(p); ok(f.service().load(a.id,f.cancel))
            val plans=f.db.plans(); val evidence=File("/tmp/cl-deck-draft-store-01/evidence"); evidence.mkdirs(); File(evidence,"query-plans.txt").writeText(plans.joinToString("\n\n"))
            assertTrue(plans.toString(),plans.any { it.contains("index_deck_draft_intent_pending") }); assertTrue(plans.toString(),plans.any { it.contains("index_deck_draft_intent_order") })
            assertTrue(plans.toString(),plans.any { it.contains("index_deck_draft_intent_receipt") }); assertTrue(plans.toString(),plans.any { it.contains("index_deck_operation_version_latest") })
            assertFalse(plans.toString(),plans.any { it.contains("SCAN ") || it.contains("TEMP B-TREE") })
        }
    }
}
