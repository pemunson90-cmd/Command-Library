@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Independent bounded synthetic ports, not acceptance of real allocation, merging or storage. */
class DeckMarkOwnedControllerTest {
    @Test fun wholeDeckAllocationDoesNotSpendSharedSupplyAgainForSelectedSubset() = runTest {
        val f=MarkOwnedFixture(this);f.open()
        assertEquals(listOf("a","b"),f.allocations.single().entries.map { it.id.value })
        val a=f.row("a");val b=f.row("b")
        assertEquals(2,a.estimate!!.ownedQuantity);assertEquals(0,b.estimate!!.ownedQuantity)
        assertEquals("0",a.input.quantity);assertEquals("2",b.input.quantity)
        assertTrue(f.controller.state.value.rows.none { it.selected });assertEquals(0,f.ids)
        f.controller.select(b.entry.id,true);f.controller.preview();runCurrent()
        val add=f.controller.state.value.preview!!.patch.edits.single() as CollectionEdit.Add
        assertEquals(2,add.lot.quantity);assertEquals(Revision(0),add.lot.revision)
        assertTrue(add.lot.finish is Knowledge.Unknown);assertEquals(CardCondition.UNKNOWN,add.lot.condition)
        assertEquals("",add.lot.notes);assertEquals("",add.lot.location)
        assertEquals(Knowledge.Absent("Acquisition time not supplied"),add.lot.acquiredAt)
        assertEquals(1,f.ids);assertTrue(f.calls.isEmpty())
    }
    @Test fun fullyOwnedEntryCanExplicitlyRecordMoreAndPhysicalFieldsStayIndependent() = runTest {
        val f=MarkOwnedFixture(this);f.open();val before=f.row("a")
        f.controller.select(before.entry.id,true)
        f.controller.change(before.entry.id,before.input,before.input.copy(quantity="3",condition=CardCondition.LIGHT_PLAYED,location="Binder",notes="Physical note"))
        f.controller.preview();runCurrent();f.controller.confirm();runCurrent()
        val add=f.calls.single().patch.edits.single() as CollectionEdit.Add
        assertEquals(3,add.lot.quantity);assertEquals("Binder",add.lot.location);assertEquals("Physical note",add.lot.notes)
        assertEquals("Strategy must not transfer",before.entry.notes);assertEquals(1,f.groups)
        assertFalse(f.gate.busy.value);assertEquals(f.stored,f.notices.single())
        assertFalse(f.controller.state.value.visible);assertEquals(Revision(4),f.controller.state.value.savedRevision)
    }
    @Test fun foreignUnknownIdentityWorksOfflineWithoutCatalogLookupOrFinishGuess() = runTest {
        val f=MarkOwnedFixture(this)
        val foreign=CardIdentity(Knowledge.Unknown("Original oracle evidence",SnapshotList.of(Evidence("Original evidence",null,null))),
            Knowledge.Absent("No resolved printing"),ProviderIdentity(ProviderId("foreign"),"Original discriminator","Raw exact"),Knowledge.Known("Original name"),LanguageTag("ja"))
        f.head=f.head.copy(entries=SnapshotList.of(f.head.entries.first().copy(identity=foreign,finish=Knowledge.Known(Finish.FOIL))))
        f.catalogResponse=Outcome.Unavailable(UnavailableReason.OFFLINE,"Offline")
        f.open();assertEquals("",f.row("a").input.quantity);assertFalse(f.row("a").estimate!!.complete)
        f.ready("a","4");f.controller.confirm();runCurrent()
        val lot=(f.calls.single().patch.edits.single() as CollectionEdit.Add).lot
        assertEquals(foreign,lot.identity);assertTrue(lot.finish is Knowledge.Unknown);assertTrue(f.catalogCalls.isEmpty())
        assertEquals(4,lot.quantity);assertEquals(1,f.groups)
    }
    @Test fun unavailableOrMalformedEstimateNeverBecomesProvenZero() = runTest {
        for(malformed in listOf(false,true)) {
            val f=MarkOwnedFixture(this)
            f.allocationFailure=if(malformed) null else Outcome.Unknown("Estimate unavailable",SnapshotList.empty())
            if(malformed) f.alterPlan={ it.copy(deckRevision=Revision(999)) }
            f.open()
            assertNull(f.controller.state.value.allocation);assertNotNull(f.controller.state.value.estimateProblem)
            assertTrue(f.controller.state.value.rows.all { it.estimate==null && it.input.quantity.isEmpty() && !it.selected })
            f.ready("b","2");assertNotNull(f.controller.state.value.preview);assertTrue(f.calls.isEmpty())
        }
    }
    @Test fun duplicateDeckIdsAndBoundsRejectWithoutTruncation() = runTest {
        val f=MarkOwnedFixture(this);f.head=f.head.copy(entries=SnapshotList.of(f.head.entries.first(),f.head.entries.first()))
        f.open();assertEquals(Problem.INVALID_INPUT,f.controller.state.value.problem);assertTrue(f.controller.state.value.rows.isEmpty());assertTrue(f.allocations.isEmpty())
        val tooMany=MarkOwnedFixture(this);val entry=tooMany.head.entries.first()
        tooMany.head=tooMany.head.copy(entries=SnapshotList.copyOf((0..OwnedQuantityLimits.MAX_ENTRIES).map { entry.copy(id=EntityId("row-$it")) }))
        tooMany.open();assertEquals(Problem.INVALID_INPUT,tooMany.controller.state.value.problem);assertTrue(tooMany.allocations.isEmpty())
        val lots=MarkOwnedFixture(this);lots.stored=lots.stored.copy(lots=SnapshotList.copyOf((0..InventoryProjectionLimits.MAX_LOTS).map { lots.supply.copy(id=EntityId("lot-$it")) }))
        lots.open();assertEquals(Problem.CORRUPT_DATA,lots.controller.state.value.problem);assertTrue(lots.allocations.isEmpty())
    }
    @Test fun duplicateSelectionCannotExceedOneAddAndQuantityMustBePositiveInt() = runTest {
        val f=MarkOwnedFixture(this);f.open();val row=f.row("b")
        f.controller.select(row.entry.id,true);f.controller.select(row.entry.id,true)
        for(raw in listOf("0","-1","2147483648","1.0"," 2")) {
            val old=f.row("b").input;f.controller.change(row.entry.id,old,old.copy(quantity=raw));f.controller.preview();runCurrent()
            assertNull(f.controller.state.value.preview);assertEquals(Problem.INVALID_INPUT,f.row("b").problem)
        }
        f.ready("b","2");assertEquals(1,f.controller.state.value.preview!!.patch.edits.size)
        assertEquals(1,f.ids);assertTrue(f.calls.isEmpty())
    }
    @Test fun knownFinishRequiresExactCurrentQualifiedPrintingAndSupportedFinish() = runTest {
        for(mode in 0..5) {
            val f=MarkOwnedFixture(this);f.open();val row=f.row("b")
            f.controller.select(row.entry.id,true);f.controller.change(row.entry.id,row.input,row.input.copy(finish=Knowledge.Known(Finish.FOIL)))
            f.printing=when(mode) {
                0->f.printing.copy(id=CardId(ProviderId("foreign"),f.printing.id.raw))
                1->f.printing.copy(language=LanguageTag("ja"))
                2->f.printing.copy(oracleId=Knowledge.Absent("Unknown oracle cannot confirm known oracle"))
                3->f.printing.copy(finishes=Knowledge.Unknown("Unknown finishes",SnapshotList.empty()))
                4->f.printing.copy(provenance=f.printing.provenance.copy(generation=GenerationId("old")))
                else->f.printing.copy(finishes=Knowledge.Known(SnapshotList.of(Finish.NONFOIL)))
            }
            f.controller.preview();runCurrent();assertNull(f.controller.state.value.preview)
            assertEquals(Problem.STALE_GENERATION,f.row("b").problem);assertTrue(f.calls.isEmpty());assertEquals(0,f.ids)
            assertEquals(Knowledge.Known(Finish.FOIL),f.row("b").input.finish)
        }
    }
    @Test fun initialConfirmationRechecksWholeDeckNotOnlyRevision() = runTest {
        val f=MarkOwnedFixture(this);f.ready();val input=f.row("b").input
        f.head=f.head.copy(description="Changed without revision in deliberately malformed source")
        f.controller.confirm();assertTrue(f.gate.busy.value);runCurrent()
        assertEquals(Problem.STALE_REVISION,f.controller.state.value.problem);assertNull(f.controller.state.value.preview)
        assertEquals(input,f.row("b").input);assertFalse(f.gate.busy.value);assertTrue(f.calls.isEmpty())
    }
    @Test fun catalogProofRetirementDuringDeckRecheckBlocksFreshSubmission() = runTest {
        val f=MarkOwnedFixture(this);f.ready(known=true)
        val deck=f.gated("deck");f.controller.confirm();runCurrent()
        f.active=GenerationId("retired");f.controller.onActiveGeneration(f.active)
        deck.complete(Unit);runCurrent()
        assertTrue(f.calls.isEmpty());assertFalse(f.gate.busy.value)
        assertEquals(Knowledge.Known(Finish.FOIL),f.row("b").input.finish)
        assertNull(f.controller.state.value.preview)
    }
    @Test fun unnotifiedDeckChangeDuringCatalogRecheckIsCaughtByFinalFullRead() = runTest {
        val f=MarkOwnedFixture(this);f.ready(known=true)
        val catalog=f.gated("catalog");f.controller.confirm();runCurrent()
        f.head=f.head.copy(primer="External edit while catalog suspended")
        catalog.complete(Unit);runCurrent()
        assertEquals(Problem.STALE_REVISION,f.controller.state.value.problem)
        assertTrue(f.calls.isEmpty());assertFalse(f.gate.busy.value)
    }
    @Test fun staleWholeRowCallbackAndCanceledPreviewCannotOverwriteOrAllocateIds() = runTest {
        val f=MarkOwnedFixture(this);f.open();val row=f.row("b")
        f.controller.select(row.entry.id,true)
        val known=row.input.copy(finish=Knowledge.Known(Finish.FOIL))
        f.controller.change(row.entry.id,row.input,known)
        val gate=f.gated("catalog");f.controller.preview();runCurrent()
        val newer=known.copy(notes="Newer exact physical note")
        f.controller.change(row.entry.id,known,newer)
        f.controller.change(row.entry.id,known,known.copy(location="Stale callback"))
        gate.complete(Unit);runCurrent()
        assertEquals(newer,f.row("b").input);assertEquals(0,f.ids);assertNull(f.controller.state.value.preview)
        assertFalse(f.controller.state.value.previewing);assertTrue(f.calls.isEmpty())
        f.controller.discardInput();assertFalse(f.controller.state.value.visible)
    }
    @Test fun projectionIssuesAllBlockOneAtomicRequest() = runTest {
        val f=MarkOwnedFixture(this);f.open();f.controller.select(EntityId("a"),true);f.controller.select(EntityId("b"),true)
        val a=f.row("a");f.controller.change(a.entry.id,a.input,a.input.copy(quantity="1"))
        f.projectionIssues=true;f.controller.preview();runCurrent()
        assertEquals(2,f.controller.state.value.issues.size);assertNotNull(f.controller.state.value.preview)
        f.controller.confirm();runCurrent();assertTrue(f.calls.isEmpty());assertFalse(f.gate.busy.value)
        f.projectionIssues=false;f.controller.preview();runCurrent();f.controller.confirm();runCurrent()
        assertEquals(2,f.calls.single().patch.edits.size);assertEquals(1,f.groups)
    }
    @Test fun selectionLimitIsExplicitAndPolicyNeverInfersPinsOrIncludesOtherBoards() = runTest {
        val f=MarkOwnedFixture(this);val entry=f.head.entries.first()
        f.head=f.head.copy(entries=SnapshotList.copyOf((0..InventoryProjectionLimits.MAX_EDITS).map { entry.copy(id=EntityId("entry-$it")) }+entry.copy(id=EntityId("side"),board=BoardRole.SIDEBOARD)))
        f.open();assertEquals(InventoryProjectionLimits.MAX_EDITS+1,f.controller.state.value.rows.size)
        for(i in 0 until InventoryProjectionLimits.MAX_EDITS) f.controller.select(EntityId("entry-$i"),true)
        f.controller.select(EntityId("entry-${InventoryProjectionLimits.MAX_EDITS}"),true)
        assertEquals(InventoryProjectionLimits.MAX_EDITS,f.controller.state.value.rows.count{it.selected})
        assertEquals(Problem.INVALID_INPUT,f.controller.state.value.problem)
        assertTrue(f.controller.state.value.allocation!!.policy.pins.isEmpty())
        assertEquals(f.head,f.allocations.single());assertEquals(0,f.ids);assertTrue(f.calls.isEmpty())
    }
    @Test fun knownFinishIsRecheckedAfterTheFinalDeckAwait() = runTest {
        val f=MarkOwnedFixture(this);f.ready(known=true)
        val catalog=f.gated("catalog");f.controller.confirm();runCurrent()
        val finalDeck=f.gated("deck");catalog.complete(Unit);runCurrent()
        f.active=null;f.controller.onActiveGeneration(null)
        finalDeck.complete(Unit);runCurrent()
        assertTrue(f.calls.isEmpty());assertFalse(f.gate.busy.value);assertNull(f.controller.state.value.preview)
        assertEquals(Knowledge.Known(Finish.FOIL),f.row("b").input.finish)
    }

    @Test fun onDemandChoicesExposeOnlyActualSupportedFinishesAndNeverChangeInput() = runTest {
        val f=MarkOwnedFixture(this);f.open();val before=f.row("b").input
        f.printing=f.printing.copy(finishes=Knowledge.Known(SnapshotList.of(Finish.NONFOIL)))
        f.controller.loadFinishChoices(EntityId("b"));assertTrue(f.row("b").loadingFinishes);runCurrent()
        assertEquals(SnapshotList.of(Finish.NONFOIL),f.row("b").supportedFinishes)
        assertEquals(f.active,f.row("b").finishGeneration);assertNull(f.row("b").finishProblem)
        assertFalse(f.row("b").loadingFinishes);assertEquals(before,f.row("b").input)
        assertEquals(1,f.catalogCalls.size);assertEquals(0,f.ids);assertTrue(f.calls.isEmpty())
        f.active=null;f.controller.onActiveGeneration(null)
        assertTrue(f.row("b").supportedFinishes.isEmpty());assertEquals(before,f.row("b").input)
    }
    @Test fun unresolvedPrintingHasNoChoiceLookupAndUnknownRemainsOfflineUsable() = runTest {
        val f=MarkOwnedFixture(this)
        val entry=f.head.entries.first().copy(identity=f.identity.copy(printingId=Knowledge.Absent("Raw foreign printing"),raw=ProviderIdentity(ProviderId("foreign"),"Exact kind","raw-id")))
        f.head=f.head.copy(entries=SnapshotList.of(entry));f.open()
        val raw=f.row("a").input;f.controller.loadFinishChoices(entry.id);runCurrent()
        assertEquals(Problem.NOT_FOUND,f.row("a").finishProblem);assertEquals(raw,f.row("a").input)
        assertTrue(f.catalogCalls.isEmpty());assertEquals(0,f.ids);assertTrue(f.calls.isEmpty())
        f.ready("a","1");assertTrue((f.controller.state.value.preview!!.patch.edits.single() as CollectionEdit.Add).lot.finish is Knowledge.Unknown)
    }
    @Test fun staleFinishLookupCannotPublishAcrossInputGenerationSessionOrSourceRefresh() = runTest {
        for(mode in 0..3) {
            val f=MarkOwnedFixture(this);f.open();val gate=f.gated("catalog")
            f.controller.loadFinishChoices(EntityId("b"));runCurrent()
            when(mode) {
                0->{val row=f.row("b");f.controller.change(row.entry.id,row.input,row.input.copy(notes="Later input"))}
                1->{f.active=null;f.controller.onActiveGeneration(null)}
                2->{f.head=f.head.copy(id=DeckId(LocalDeckLibraryLimits.SOURCE,"other-source"));f.controller.open(f.head.id,f.policy);runCurrent()}
                else->{f.head=f.head.copy(entries=SnapshotList.copyOf(f.head.entries.map {if(it.id==EntityId("b"))it.copy(identity=it.identity.copy(language=LanguageTag("ja")))else it}));f.controller.refreshSources();runCurrent()}
            }
            gate.complete(Unit);runCurrent()
            assertTrue(f.row("b").supportedFinishes.isEmpty());assertFalse(f.row("b").loadingFinishes)
            assertEquals(0,f.ids);assertTrue(f.calls.isEmpty())
            if(mode==0)assertEquals("Later input",f.row("b").input.notes)
            if(mode==2)assertEquals(f.head.id,f.controller.state.value.deckId)
            if(mode==3)assertNotNull(f.row("b").sourceProblem)
        }
    }
    @Test fun explicitSourceRefreshAfterStaleCasPreservesRawAndAllowsNewPreview() = runTest {
        val f=MarkOwnedFixture(this);f.ready();f.beforeUnknown=true
        f.controller.confirm();runCurrent();val first=f.calls.single();val raw=f.row("b").input
        f.stored=f.stored.copy(revision=Revision(9),capturedAt=UtcTimestamp(9));f.controller.onInventoryChanged(Revision(9))
        f.controller.retry();runCurrent();assertFalse(f.gate.busy.value)
        assertEquals(listOf(first,first),f.calls)
        val ids=f.ids;f.controller.refreshSources();runCurrent()
        assertEquals(raw,f.row("b").input);assertTrue(f.row("b").selected)
        assertEquals(ids,f.ids);assertEquals(2,f.calls.size);assertEquals(Revision(9),f.controller.state.value.inventory!!.revision)
        f.controller.preview();runCurrent();f.controller.confirm();runCurrent()
        assertEquals(3,f.calls.size);assertEquals(listOf(first,first),f.calls.take(2));assertNotEquals(first.operation,f.calls.last().operation)
        assertEquals(Revision(9),f.calls.last().expected);assertEquals(1,f.groups)
    }
    @Test fun changedIdentityRetainsOldRawUntilExplicitAcceptClearsOnlyOneRow() = runTest {
        val f=MarkOwnedFixture(this);f.open()
        val a=f.row("a");f.controller.change(a.entry.id,a.input,a.input.copy(location="Other exact location"))
        val b=f.row("b");val raw=b.input.copy(quantity="5",finish=Knowledge.Known(Finish.FOIL),notes="Old physical notes")
        f.controller.change(b.entry.id,b.input,raw);f.controller.select(b.entry.id,true)
        val other=f.row("a").input
        val changed=b.entry.identity.copy(language=LanguageTag("ja"),raw=ProviderIdentity(ProviderId("foreign"),"replacement","raw"))
        f.head=f.head.copy(entries=SnapshotList.copyOf(f.head.entries.map{if(it.id==b.entry.id)it.copy(identity=changed)else it}))
        f.controller.refreshSources();runCurrent()
        assertEquals(b.entry.identity,f.row("b").entry.identity);assertEquals(changed,f.row("b").currentEntry!!.identity)
        assertEquals(raw,f.row("b").input);assertTrue(f.row("b").selected);assertEquals(Problem.STALE_REVISION,f.row("b").sourceProblem)
        f.controller.preview();runCurrent();assertNull(f.controller.state.value.preview);assertEquals(0,f.ids)
        f.controller.select(b.entry.id,false);f.controller.select(b.entry.id,true)
        assertFalse(f.row("b").selected);assertEquals(raw,f.row("b").input)
        f.controller.acceptCurrentEntry(b.entry.id,changed,f.row("b").currentEntry!!,raw);assertEquals(raw,f.row("b").input)
        f.controller.acceptCurrentEntry(b.entry.id,b.entry.identity,f.row("b").currentEntry!!,raw)
        assertEquals(changed,f.row("b").entry.identity);assertNull(f.row("b").sourceProblem)
        assertFalse(f.row("b").selected);assertTrue(f.row("b").input.finish is Knowledge.Unknown)
        assertEquals("",f.row("b").input.notes);assertEquals(other,f.row("a").input)
        assertEquals(0,f.ids);assertTrue(f.calls.isEmpty())
    }
    @Test fun missingOrExcludedEntryRetainsInputAndCanOnlyBeExplicitlyExcluded() = runTest {
        for(missing in listOf(false,true)) {
            val f=MarkOwnedFixture(this);f.open();val b=f.row("b")
            val raw=b.input.copy(notes="Keep missing physical note");f.controller.change(b.entry.id,b.input,raw);f.controller.select(b.entry.id,true)
            f.head=f.head.copy(entries=SnapshotList.copyOf(if(missing)f.head.entries.filter{it.id!=b.entry.id}else f.head.entries.map{if(it.id==b.entry.id)it.copy(board=BoardRole.SIDEBOARD)else it}))
            f.controller.refreshSources();runCurrent()
            assertEquals(raw,f.row("b").input);assertTrue(f.row("b").selected);assertNull(f.row("b").currentEntry)
            f.controller.acceptCurrentEntry(b.entry.id,b.entry.identity,b.entry,raw);assertEquals(raw,f.row("b").input)
            f.controller.select(b.entry.id,false)
            val a=f.row("a");f.controller.change(a.entry.id,a.input,a.input.copy(quantity="1"));f.controller.select(a.entry.id,true)
            f.controller.preview();runCurrent();assertEquals(1,f.controller.state.value.preview!!.patch.edits.size)
            assertEquals(raw,f.row("b").input);assertTrue(f.calls.isEmpty())
        }
    }
    @Test fun sourceRefreshMergedRowBudgetRejectsWithoutDroppingOldInput() = runTest {
        val f=MarkOwnedFixture(this);val entry=f.head.entries.first()
        f.head=f.head.copy(entries=SnapshotList.copyOf((0 until OwnedQuantityLimits.MAX_ENTRIES).map{entry.copy(id=EntityId("old-$it"))}));f.open()
        val old=f.controller.state.value.rows.first();val raw=old.input.copy(notes="Retain exact old raw")
        f.controller.change(old.entry.id,old.input,raw)
        f.head=f.head.copy(entries=SnapshotList.copyOf((0 until OwnedQuantityLimits.MAX_ENTRIES).map{entry.copy(id=EntityId("new-$it"))}))
        f.controller.refreshSources();runCurrent()
        assertEquals(Problem.INVALID_INPUT,f.controller.state.value.problem)
        assertEquals(OwnedQuantityLimits.MAX_ENTRIES,f.controller.state.value.rows.size)
        assertEquals(raw,f.controller.state.value.rows.first().input);assertEquals(0,f.ids);assertTrue(f.calls.isEmpty())
    }

    @Test fun stableFormTicketSurvivesRetainedReadsAndChangesOnlyForNewForm() = runTest {
        val f=MarkOwnedFixture(this);f.open();val first=f.controller.state.value.formTicket
        assertTrue(first>0)
        f.controller.close();f.controller.resume();assertEquals(first,f.controller.state.value.formTicket)
        f.controller.refreshSources();runCurrent();assertEquals(first,f.controller.state.value.formTicket)
        f.controller.loadFinishChoices(EntityId("b"));runCurrent();assertEquals(first,f.controller.state.value.formTicket)
        f.ready();assertEquals(first,f.controller.state.value.formTicket)
        f.controller.discardInput();val discarded=f.controller.state.value.formTicket
        assertTrue(discarded>first);assertFalse(f.controller.state.value.visible)
        f.open();assertTrue(f.controller.state.value.formTicket>discarded)
        assertEquals(f.head.id,f.controller.state.value.deckId);assertTrue(f.calls.isEmpty())
    }

    @Test fun delayedAcceptWarningCannotClearANewerTargetOrEditedPhysicalInput() = runTest {
        val f=MarkOwnedFixture(this);f.open();val old=f.row("b")
        val raw=old.input.copy(notes="Warned physical input",quantity="6")
        f.controller.change(old.entry.id,old.input,raw);f.controller.select(old.entry.id,true)
        val targetB=old.entry.copy(identity=old.entry.identity.copy(language=LanguageTag("ja")))
        f.head=f.head.copy(entries=SnapshotList.of(f.head.entries.first(),targetB));f.controller.refreshSources();runCurrent()
        val ticket=f.controller.state.value.formTicket;val warned=f.row("b")
        val targetC=targetB.copy(identity=targetB.identity.copy(language=LanguageTag("de")))
        f.head=f.head.copy(entries=SnapshotList.of(f.head.entries.first(),targetC));f.controller.refreshSources();runCurrent()
        assertEquals(ticket,f.controller.state.value.formTicket)
        f.controller.acceptCurrentEntry(old.entry.id,old.entry.identity,warned.currentEntry!!,warned.input)
        assertEquals(raw,f.row("b").input);assertEquals(old.entry.identity,f.row("b").entry.identity)
        assertEquals(targetC,f.row("b").currentEntry)
        val edited=raw.copy(notes="Typed after warning")
        f.controller.change(old.entry.id,raw,edited)
        f.controller.acceptCurrentEntry(old.entry.id,old.entry.identity,targetC,raw)
        assertEquals(edited,f.row("b").input);assertEquals(old.entry.identity,f.row("b").entry.identity)
        f.controller.acceptCurrentEntry(old.entry.id,old.entry.identity,targetC,edited)
        assertEquals(targetC,f.row("b").entry);assertFalse(f.row("b").selected)
        assertEquals("",f.row("b").input.notes);assertEquals(0,f.ids);assertTrue(f.calls.isEmpty())
    }

}

internal class MarkOwnedFixture(private val test: TestScope, owner: CoroutineScope = test) : LocalInventory, LocalDeckLibrary, CatalogPrintingLookup, OwnedQuantityAllocator {
    private val absent=Knowledge.Absent("Not supplied")
    val identity=CardIdentity(Knowledge.Known(OracleId(ProviderId("cards"),"oracle")),Knowledge.Known(CardId(ProviderId("cards"),"printing")),null,Knowledge.Known("Original"),LanguageTag.ENGLISH)
    private val entry=DeckEntry(EntityId("a"),identity,2,BoardRole.MAIN,Knowledge.Absent("Unrestricted deck finish"),"Strategy must not transfer",SnapshotList.empty(),SnapshotList.empty())
    var head=DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE,"deck"),"Deck",absent,"",absent,SnapshotList.empty(),UtcTimestamp(1),UtcTimestamp(1),Revision(1),SnapshotList.of(entry,entry.copy(id=EntityId("b"))),absent,null,SnapshotList.empty(),SnapshotList.empty())
    val supply=OwnedLot(EntityId("supply"),identity,Knowledge.Known(Finish.NONFOIL),CardCondition.UNKNOWN,"",2,"",Revision(3),absent)
    var stored=InventorySnapshot(Revision(3),UtcTimestamp(3),SnapshotList.of(supply),true)
    var active:GenerationId?=GenerationId("active")
    var printing=PrintingRecord(CardId(ProviderId("cards"),"printing"),identity.oracleId,"test",absent,"1",LanguageTag.ENGLISH,absent,
        Knowledge.Known(SnapshotList.of(Finish.NONFOIL,Finish.FOIL)),absent,SnapshotList.empty(),SnapshotList.empty(),SnapshotList.empty(),SnapshotList.empty(),SnapshotList.empty(),
        Provenance(ProviderId("cards"),null,absent,absent,UtcTimestamp(1),absent,active,null,null))
    val gate=InventoryMutationGate()
    var ids=0;var groups=0;var snapshotCalls=0
    data class Apply(val patch:CollectionPatch,val expected:Revision,val operation:EntityId)
    val calls=mutableListOf<Apply>();val allocations=mutableListOf<DeckSnapshot>();val catalogCalls=mutableListOf<CardId>();val notices=mutableListOf<InventorySnapshot>()
    val receipts=mutableMapOf<EntityId,Pair<Apply,InventoryCommit>>()
    var snapshotResponse:Outcome<InventorySnapshot>?=null
    var catalogResponse:Outcome<PrintingRecord>?=null
    var allocationFailure:Outcome<OwnedQuantityPlan>?=null
    var alterPlan:(OwnedQuantityPlan)->OwnedQuantityPlan={it}
    var alterCommit:(InventoryCommit)->InventoryCommit={it}
    var response:Outcome<InventoryApplyResult>?=null
    var beforeUnknown=false;var afterUnknown=false;var projectionIssues=false;var cancelNextSnapshot=false;var cancelNextApplyAck=false
    val gates=mutableMapOf<String,CompletableDeferred<Unit>>()
    val policy=OwnedQuantityPolicy(SnapshotList.of(BoardRole.MAIN))
    val controller=DeckMarkOwnedController(owner,this,this,this,this,::project,StandardTestDispatcher(test.testScheduler),{EntityId("new-${++ids}")},{active},gate,{notices.add(it)})
    fun gated(stage:String)=CompletableDeferred<Unit>().also {gates[stage]=it}
    private suspend fun wait(stage:String){gates.remove(stage)?.await()}
    fun open(){controller.open(head.id,policy);test.runCurrent()}
    fun row(id:String)=controller.state.value.rows.first {it.entry.id==EntityId(id)}
    fun ready(id:String="b",quantity:String="2",known:Boolean=false) {
        if(controller.state.value.deck==null)open()
        val row=row(id);controller.select(row.entry.id,true)
        controller.change(row.entry.id,row.input,row.input.copy(quantity=quantity,finish=if(known) Knowledge.Known(Finish.FOIL) else row.input.finish))
        controller.preview();test.runCurrent();check(controller.state.value.preview!=null)
    }
    override suspend fun snapshot(cancel:CancelToken):Outcome<InventorySnapshot>{snapshotCalls++;if(cancelNextSnapshot){cancelNextSnapshot=false;currentCoroutineContext().cancel();yield()};val result=snapshotResponse?:Outcome.Success(stored);wait("snapshot");return result}
    override suspend fun load(id:DeckId,cancel:CancelToken):Outcome<DeckSnapshot>{val result=Outcome.Success(head);wait("deck");return result}
    override suspend fun printing(id:CardId,cancel:CancelToken):Outcome<PrintingRecord>{catalogCalls.add(id);val result=catalogResponse?:Outcome.Success(printing);wait("catalog");return result}
    override suspend fun printings(id:OracleId,page:PageRequest,cancel:CancelToken):Outcome<Page<PrintingRecord>> = error("No catalog acquisition/page")
    override suspend fun list(page:PageRequest,cancel:CancelToken):Outcome<Page<DeckSummary>> = error("No library listing")
    override suspend fun create(initial:DeckSnapshot,operationId:EntityId,cancel:CancelToken):Outcome<DeckSnapshot> = error("No deck mutation")
    override suspend fun duplicate(id:DeckId,expected:Revision,name:String,operationId:EntityId,cancel:CancelToken):Outcome<DeckSnapshot> = error("No deck mutation")
    override suspend fun undo(expected:Revision,operationId:EntityId,cancel:CancelToken):Outcome<InventoryCommit> = error("Other lane owns undo")
    override fun allocate(deck:DeckSnapshot,inventory:InventorySnapshot,policy:OwnedQuantityPolicy,cancel:CancelToken):Outcome<OwnedQuantityPlan> {
        allocations.add(deck);allocationFailure?.let{return it}
        val available=inventory.lots.associate {it.id to it.quantity}.toMutableMap();val links=mutableListOf<OwnedAllocation>();val demands=mutableListOf<OwnedQuantityDemand>()
        for(e in deck.entries.filter {it.board in policy.includeBoards}.sortedBy {it.id.value}) {
            var owned=0;val oracle=(e.identity.oracleId as? Knowledge.Known)?.value
            for(lot in inventory.lots.sortedBy {it.id.value}) {
                val same=if(policy.ownership==OwnershipPolicy.EXACT_PRINTING) e.identity.printingId is Knowledge.Known && e.identity.printingId==lot.identity.printingId else oracle!=null && lot.identity.oracleId==Knowledge.Known(oracle)
                if(same && e.identity.language==lot.identity.language){val n=minOf(e.quantity-owned,available.getValue(lot.id));if(n>0){links.add(OwnedAllocation(lot.id,e.id,n));available[lot.id]=available.getValue(lot.id)-n;owned+=n}}
            }
            demands.add(OwnedQuantityDemand(e.id,e.quantity,owned,e.quantity-owned,owned==e.quantity || oracle!=null))
        }
        val incomplete=demands.filter{!it.complete}.map{it.entryId}
        return Outcome.Success(alterPlan(OwnedQuantityPlan(deck.id,deck.revision,inventory.revision,policy.copy(includeBoards=SnapshotList.copyOf(policy.includeBoards.sortedBy{it.name})),SnapshotList.copyOf(links),SnapshotList.copyOf(demands),SnapshotList.copyOf(incomplete),incomplete.isEmpty())))
    }
    fun project(base:InventorySnapshot,patch:CollectionPatch,expected:Revision,cancel:CancelToken):Outcome<InventoryProjection> {
        if(expected!=base.revision || base.lots.map{it.id}.distinct().size!=base.lots.size)return fail(FailureCode.INVALID_INPUT)
        if(patch.edits.isEmpty())return Outcome.Success(InventoryProjection(expected,expected,SnapshotList.empty(),SnapshotList.empty(),SnapshotList.empty()))
        if(projectionIssues){val issues=patch.edits.map{DomainIssue(InventoryIssueCodes.QUANTITY_OVERFLOW,IssueSeverity.ERROR,"Synthetic structural issue",(it as CollectionEdit.Add).lot.id,SnapshotList.empty())};return Outcome.Success(InventoryProjection(expected,Revision(expected.value+1),SnapshotList.empty(),SnapshotList.empty(),SnapshotList.copyOf(issues)))}
        if(expected.value==Long.MAX_VALUE)return fail(FailureCode.PERSISTENCE)
        val next=Revision(expected.value+1)
        val changes=patch.edits.map { val lot=(it as CollectionEdit.Add).lot;InventoryLotChange(lot.id,null,lot.copy(revision=next)) }
        return Outcome.Success(InventoryProjection(expected,next,SnapshotList.copyOf(changes),SnapshotList.empty(),SnapshotList.empty()))
    }
    override suspend fun apply(patch:CollectionPatch,expected:Revision,operationId:EntityId,cancel:CancelToken):Outcome<InventoryApplyResult> {
        val call=Apply(patch,expected,operationId);calls.add(call);wait("apply.before")
        receipts[operationId]?.let {if(it.first!=call)return fail(FailureCode.INVALID_INPUT);return Outcome.Success(InventoryApplyResult.Committed(alterCommit(it.second)))}
        if(beforeUnknown){beforeUnknown=false;return Outcome.Unknown("Before effect",SnapshotList.empty())}
        response?.let{return it}
        if(stored.revision!=expected)return fail(FailureCode.STALE_REVISION)
        val projection=(project(stored,patch,expected,cancel) as Outcome.Success).value
        val commit=InventoryCommit(projection,UtcTimestamp(stored.capturedAt.epochSeconds+1))
        receipts[operationId]=call to commit;groups++
        stored=stored.copy(revision=projection.resultRevision,capturedAt=commit.modifiedAt,lots=SnapshotList.copyOf(stored.lots+projection.changes.mapNotNull{it.after}))
        wait("apply.after")
        if(cancelNextApplyAck){cancelNextApplyAck=false;currentCoroutineContext().cancel();yield()}
        if(afterUnknown){afterUnknown=false;return Outcome.Unknown("Lost receipt",SnapshotList.empty())}
        return Outcome.Success(InventoryApplyResult.Committed(alterCommit(commit)))
    }
    private fun fail(code:FailureCode):Outcome<Nothing> = Outcome.Failure(DomainFailure(code,"Synthetic bounded failure",SnapshotList.empty()))
}
