package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetainedDeckDraftEditorRecoveryTest {
    @Test fun durableRawTakesPrecedenceOverRequestedTarget() = runTest {
        val f=ActorFixture(this,true)
        val raw=DeckDraftBody.Details(Revision(0),"\uD800","recover me","")
        f.raw(raw);f.open(DeckEditorTarget.Entry(EntityId("entry")))
        assertEquals(raw,f.actor.state.value.body)
        assertEquals(DeckEditorTarget.Details,f.actor.state.value.session!!.target)
        assertEquals(DeckEditorTarget.Entry(EntityId("entry")),f.actor.state.value.requestedTarget)
        assertTrue(f.actor.state.value.conflict);assertTrue(f.actor.hasMutationBarrier)
        assertTrue(f.writes.isEmpty())
        f.actor.discardInput();runCurrent()
        assertNull(f.slot.draft)
        assertFalse(f.actor.state.value.conflict)
    }
    @Test fun staleRawNeverRelabelsCurrentHeadAsHistoricalBase() = runTest {
        val f=ActorFixture(this)
        val raw=DeckDraftBody.Details(Revision(0),"Old input","","")
        f.raw(raw);f.head=f.head.copy(name="External",revision=Revision(7));f.open()
        assertEquals(raw,f.actor.state.value.body);assertNull(f.actor.state.value.base)
        assertEquals(Revision(7),f.actor.state.value.currentHead!!.revision)
        assertTrue(f.actor.state.value.conflict)
        f.actor.confirmExplicit();f.actor.onBackground();advanceTimeBy(1000);runCurrent()
        assertTrue(f.prepares.isEmpty());assertEquals(raw,f.slot.draft!!.body)
    }
    @Test fun recoveredPreparedWithoutExposedPinnedBodyOnlyReloadsOrAbandons() = runTest {
        val f=ActorFixture(this)
        val source=DeckDraftBody.Details(Revision(0),"Pinned","","")
        val intent=f.prepared(source)
        val later=source.copy(name="Later raw")
        f.raw(later);f.open();f.actor.retryExact();runCurrent()
        assertEquals(later,f.actor.state.value.body)
        assertTrue(f.commits.isEmpty());assertTrue(f.prepares.isEmpty())
        f.actor.confirmExplicit();runCurrent();assertTrue(f.commits.isEmpty())
        val before=f.slot.generation;f.actor.discardPreparation();runCurrent()
        assertEquals(before+1,f.slot.generation);assertEquals(later,f.slot.draft!!.body)
        assertEquals(intent.operationId,(f.slot.latestPreparation as DeckDraftPreparation.Abandoned).intent.operationId)
    }
    @Test fun recoveredPreparedExactSourceRequiresExplicitConfirmation() = runTest {
        val f=ActorFixture(this)
        f.prepared(DeckDraftBody.Details(Revision(0),"Recovered","",""));f.open()
        f.actor.retryExact();runCurrent();advanceTimeBy(1000);runCurrent()
        assertTrue(f.commits.isEmpty())
        f.actor.confirmExplicit();runCurrent()
        assertEquals("Recovered",f.head.name)
        assertEquals(1,f.commits.size);assertTrue(f.prepares.isEmpty())
        assertFalse(f.actor.state.value.hasFrozenCall)
    }
    @Test fun recoveredPreparedAddEvidenceAloneCannotAuthorizeCommit() = runTest {
        val f=ActorFixture(this)
        val selected=DeckDraftSelection(f.cardIdentity,GenerationId("active"))
        val raw=DeckDraftBody.Add(Revision(0),"1","",BoardRole.MAIN,Knowledge.Absent("Absent"),SnapshotList.empty(),selected)
        f.prepared(raw,added=EntityId("one-entry"));f.open(DeckEditorTarget.Add)
        f.actor.confirmExplicit();runCurrent()
        assertTrue(f.commits.isEmpty());assertEquals(Problem.STALE_GENERATION,f.actor.state.value.contentProblem)
        f.selectCard();f.actor.confirmExplicit();runCurrent()
        assertEquals(EntityId("one-entry"),f.head.entries.single().id)
        assertEquals(DeckEditorTarget.Entry(EntityId("one-entry")),f.actor.state.value.session!!.target)
    }
    @Test fun recoveredConfirmedCommitNeverUsesLiveQueuedMergeAuthority() = runTest {
        val f=ActorFixture(this)
        val raw=DeckDraftBody.Details(Revision(0),"Recovered","","")
        f.prepared(raw);f.open()
        val gate=f.gate("commit.after");f.actor.confirmExplicit();runCurrent()
        f.actor.change(f.details.copy(primer="Typed later"));gate.complete(Unit);runCurrent()
        assertTrue(f.actor.state.value.conflict)
        assertEquals("Typed later",f.details.primer)
        assertEquals(Revision(0),f.details.baseRevision)
        assertEquals(Revision(1),f.head.revision)
    }
    @Test fun committedRecoveryUsesHistoricalReceiptWithoutCatalogOrFabricatedSource() = runTest {
        val f=ActorFixture(this)
        val raw=DeckDraftBody.Details(Revision(0),"Historical","","")
        val intent=f.prepared(raw)
        f.commit(f.head.id,intent.operationId,intent.requestSha256,f.cancel)
        f.head=f.head.copy(name="New current",revision=Revision(5))
        val later=DeckDraftBody.Details(Revision(5),"\uD800","current raw","")
        f.raw(later);f.current=false;f.open()
        assertEquals(later,f.actor.state.value.body)
        assertEquals(Revision(5),f.actor.state.value.base!!.revision)
        assertTrue(f.actor.state.value.recovered)
        assertEquals(1,f.commits.size)
        assertTrue(f.actor.state.value.durable!!.latestPreparation is DeckDraftPreparation.Committed)
        assertTrue(f.prepares.isEmpty())
        assertEquals(f.head,f.callbackHeads.single())
        assertEquals(Revision(5),f.notices.single().second)
    }
    @Test fun malformedRecoveredAcknowledgementIsBlocked() = runTest {
        val f=ActorFixture(this)
        val intent=f.prepared(DeckDraftBody.Details(Revision(0),"One","",""))
        f.commit(f.head.id,intent.operationId,intent.requestSha256,f.cancel)
        val committed=f.slot.latestPreparation as DeckDraftPreparation.Committed
        f.slot=f.slot.copy(latestPreparation=committed.copy(acknowledgement=committed.acknowledgement.copy(preparedRequestSha256="bad")))
        f.open()
        assertEquals(Problem.CORRUPT_DATA,f.actor.state.value.rawProblem)
        assertTrue(f.actor.hasMutationBarrier)
        assertTrue(f.actor.state.value.conflict)
    }
    @Test fun externalHeadAfterKnownCommitRetainsInputAndAllowsExplicitDiscard() = runTest {
        val f=ActorFixture(this);f.open()
        val gate=f.gate("commit.after")
        f.actor.change(f.details.copy(name="Saved"));advanceTimeBy(500);runCurrent()
        f.actor.change(f.details.copy(primer="Queued"))
        f.head=f.head.copy(revision=Revision(9),description="External")
        gate.complete(Unit);runCurrent()
        assertTrue(f.actor.state.value.conflict);assertFalse(f.actor.state.value.uncertain)
        assertEquals(Revision(0),f.details.baseRevision);assertEquals("Queued",f.details.primer)
        assertEquals(1,f.commits.size)
        assertEquals(f.head,f.callbackHeads.single())
        assertEquals(Revision(9),f.notices.single().second)
        f.actor.discardInput();runCurrent()
        assertNull(f.slot.draft);assertEquals(Revision(9),f.actor.state.value.base!!.revision)
    }
    @Test fun foreignPreservedLaterCannotMasqueradeAsOwnContinuation() = runTest {
        val f=ActorFixture(this);f.open()
        val gate=f.gate("commit.before")
        f.actor.change(f.details.copy(name="One"));advanceTimeBy(500);runCurrent()
        f.actor.change(f.details.copy(primer="Mine"))
        val foreign=f.details.copy(name="Foreign",primer="Other owner")
        f.raw(foreign);gate.complete(Unit);runCurrent()
        assertTrue(f.acks.single().slotEffect is DeckDraftSlotEffect.PreservedLater)
        assertTrue(f.actor.state.value.conflict)
        assertEquals("Mine",f.details.primer);assertEquals(Revision(0),f.details.baseRevision)
        assertEquals(foreign,f.slot.draft!!.body)
    }
    @Test fun knownCommitReadFailureRetriesReadsWithoutResubmission() = runTest {
        val f=ActorFixture(this);f.open();f.lose.add("load")
        f.actor.change(f.details.copy(name="Committed"));advanceTimeBy(500);runCurrent()
        assertFalse(f.actor.state.value.uncertain);assertTrue(f.actor.state.value.hasFrozenCall)
        assertEquals(Problem.UNKNOWN_RESULT,f.actor.state.value.contentProblem)
        assertEquals("Committed",f.details.name)
        f.actor.retryExact();runCurrent()
        assertEquals(1,f.commits.size);assertFalse(f.actor.state.value.hasFrozenCall)
        assertEquals(Revision(1),f.details.baseRevision)
    }
    @Test fun prepareRetryReturningAbandonedNeverDispatchesCommit() = runTest {
        val f=ActorFixture(this);f.open();f.lose.add("prepare")
        f.actor.change(f.details.copy(name="Input"));advanceTimeBy(500);runCurrent()
        val pending=f.slot.latestPreparation as DeckDraftPreparation.Pending
        f.discard(f.head.id,f.slot.generation,pending.intent.operationId,DeckDraftDiscardMode.PREPARATION_ONLY,f.cancel)
        f.actor.retryExact();runCurrent()
        assertTrue(f.commits.isEmpty());assertEquals("Input",f.details.name)
        assertFalse(f.actor.state.value.uncertain)
    }
    @Test fun exitFlushFreezesInputAndTicketIsOnceOnly() = runTest {
        val f=ActorFixture(this);f.open()
        f.actor.change(f.details.copy(name="Leaving"))
        val ticket=f.actor.requestExit(DeferredDeckAction.Navigate(com.architectpsg.commandlibrary.feature.shell.ShellDestination.COLLECTION))
        f.actor.change(f.details.copy(name="Must not enter"));runCurrent()
        assertEquals("Leaving",f.head.name)
        assertEquals(DraftExitPhase.READY,f.actor.state.value.exit!!.phase)
        assertNull(f.actor.takeReadyExit(ticket+1))
        assertTrue(f.actor.takeReadyExit(ticket) is DeferredDeckAction.Navigate)
        assertNull(f.actor.takeReadyExit(ticket));assertFalse(f.actor.state.value.visible)
    }
    @Test fun stayCancelsOnlyNavigationWhileSubmittedIoContinues() = runTest {
        val f=ActorFixture(this);f.open()
        val gate=f.gate("write.before")
        f.actor.change(f.details.copy(name="Saved"))
        val ticket=f.actor.requestExit(DeferredDeckAction.CloseEditor);runCurrent()
        f.actor.chooseExit(ticket,DraftExitChoice.STAY)
        f.actor.change(f.details.copy(primer="Still typing"))
        gate.complete(Unit);runCurrent();advanceTimeBy(500);runCurrent()
        assertNull(f.actor.state.value.exit);assertTrue(f.actor.state.value.visible)
        assertEquals("Still typing",f.head.primer)
        assertNull(f.actor.takeReadyExit(ticket))
    }
    @Test fun continueRetainsUncertainOwnerAndSameOpenResumesExactSession() = runTest {
        val f=ActorFixture(this);f.open();f.lose.add("commit")
        f.actor.change(f.details.copy(name="Uncertain"));advanceTimeBy(500);runCurrent()
        val session=f.actor.state.value.session
        val ticket=f.actor.requestExit(DeferredDeckAction.CloseEditor);runCurrent()
        assertEquals(DraftExitPhase.NEEDS_CHOICE,f.actor.state.value.exit!!.phase)
        f.actor.chooseExit(ticket,DraftExitChoice.CONTINUE)
        assertEquals(DeferredDeckAction.CloseEditor,f.actor.takeReadyExit(ticket))
        assertTrue(f.actor.hasMutationBarrier)
        f.actor.open(f.head.id,DeckEditorTarget.Details)
        assertTrue(f.actor.state.value.visible);assertEquals(session,f.actor.state.value.session)
        f.actor.retryExact();runCurrent()
        assertEquals(f.commits.first(),f.commits.last())
        assertFalse(f.actor.state.value.uncertain)
    }
    @Test fun continueCannotAuthorizeNewEditorOrMutation() = runTest {
        val f=ActorFixture(this);f.open();f.lose.add("write")
        f.actor.change(f.details.copy(name="Input"));advanceTimeBy(500);runCurrent()
        val ticket=f.actor.requestExit(DeferredDeckAction.OpenEditor(f.head.id,DeckEditorTarget.Add));runCurrent()
        assertFalse(f.actor.canContinueExit)
        f.actor.chooseExit(ticket,DraftExitChoice.CONTINUE)
        assertNull(f.actor.takeReadyExit(ticket))
        f.actor.chooseExit(ticket,DraftExitChoice.STAY)
        f.actor.open(DeckId(LocalDeckLibraryLimits.SOURCE,"other"),DeckEditorTarget.Add)
        assertEquals(f.head.id,f.actor.state.value.session!!.deckId)
    }
    @Test fun exitDiscardReconcilesUnknownCommitThenUsesActualInputCas() = runTest {
        val f=ActorFixture(this);f.open();f.lose.add("commit")
        f.actor.change(f.details.copy(name="Committed"));advanceTimeBy(500);runCurrent()
        val ticket=f.actor.requestExit(DeferredDeckAction.CloseEditor);runCurrent()
        f.actor.chooseExit(ticket,DraftExitChoice.DISCARD_INPUT);runCurrent()
        assertEquals(2,f.commits.size);assertEquals(f.commits[0],f.commits[1])
        assertEquals(DeckDraftDiscardMode.INPUT,f.discards.single().third)
        assertEquals(2L,f.discards.single().first)
        assertEquals("Committed",f.head.name)
        assertEquals(DeferredDeckAction.CloseEditor,f.actor.takeReadyExit(ticket))
    }
    @Test fun unknownDiscardRetriesSameModeGenerationAndPendingIdentity() = runTest {
        val f=ActorFixture(this)
        val raw=DeckDraftBody.Details(Revision(0),"Raw","","")
        val pending=f.prepared(raw);f.open();f.lose.add("discard")
        f.actor.discardPreparation();runCurrent()
        assertTrue(f.actor.state.value.uncertain)
        f.actor.retryExact();runCurrent()
        assertEquals(2,f.discards.size);assertEquals(f.discards[0],f.discards[1])
        assertEquals(pending.operationId,f.discards[0].second)
        assertEquals(raw,f.slot.draft!!.body)
        assertFalse(f.actor.state.value.uncertain)
    }
    @Test fun exitRequestedWhileOpeningSurvivesReadCompletion() = runTest {
        val f=ActorFixture(this);val gate=f.gate("head.load")
        f.actor.open(f.head.id,DeckEditorTarget.Details);runCurrent()
        val ticket=f.actor.requestExit(DeferredDeckAction.CloseEditor)
        gate.complete(Unit);runCurrent()
        assertEquals(DeferredDeckAction.CloseEditor,f.actor.takeReadyExit(ticket))
    }
    @Test fun headNotificationsNeverDowngradeFreshBaseAndNewerHeadBlocksPromotion() = runTest {
        val f=ActorFixture(this);f.head=f.head.copy(revision=Revision(4));f.open()
        f.actor.onDeckHeadChanged(f.head.id,Revision(2));assertFalse(f.actor.state.value.conflict)
        f.actor.change(f.details.copy(name="Pending"));f.actor.onDeckHeadChanged(f.head.id,Revision(5))
        advanceTimeBy(500);runCurrent()
        assertTrue(f.actor.state.value.conflict);assertTrue(f.writes.isEmpty())
        assertEquals(Revision(4),f.details.baseRevision)
    }
    @Test fun persistedStagedInputHoldsMutationAndNewEditorExitsButAllowsBrowse() = runTest {
        val actions=listOf<DeferredDeckAction>(
            DeferredDeckAction.UndoDeck(DeckId(LocalDeckLibraryLimits.SOURCE,"deck")),
            DeferredDeckAction.ReorderEntry(DeckId(LocalDeckLibraryLimits.SOURCE,"deck"),EntityId("entry"),1),
            DeferredDeckAction.OpenEditor(DeckId(LocalDeckLibraryLimits.SOURCE,"deck"),DeckEditorTarget.Details),
            DeferredDeckAction.CreateDeck,
        )
        for(action in actions) {
            val f=ActorFixture(this,true);f.open(DeckEditorTarget.Entry(EntityId("entry")))
            val staged=(f.actor.state.value.body as DeckDraftBody.Entry).copy(board=BoardRole.SIDEBOARD)
            f.actor.change(staged);advanceTimeBy(500);runCurrent()
            val ticket=f.actor.requestExit(action);runCurrent()
            assertEquals(DraftExitPhase.NEEDS_CHOICE,f.actor.state.value.exit!!.phase)
            assertFalse(f.actor.canContinueExit);assertNull(f.actor.takeReadyExit(ticket))
            f.actor.chooseExit(ticket,DraftExitChoice.CONTINUE);assertNull(f.actor.takeReadyExit(ticket))
            assertEquals(staged,f.slot.draft!!.body)
            f.actor.chooseExit(ticket,DraftExitChoice.DISCARD_INPUT);runCurrent()
            assertEquals(action,f.actor.takeReadyExit(ticket));assertNull(f.slot.draft)
        }
        val f=ActorFixture(this,true);f.open(DeckEditorTarget.Entry(EntityId("entry")))
        val invalid=(f.actor.state.value.body as DeckDraftBody.Entry).copy(quantity="-")
        f.actor.change(invalid);advanceTimeBy(500);runCurrent()
        val ticket=f.actor.requestExit(DeferredDeckAction.CloseEditor);runCurrent()
        assertEquals(DeferredDeckAction.CloseEditor,f.actor.takeReadyExit(ticket))
        assertEquals(invalid,f.slot.draft!!.body)
    }
    @Test fun keystrokeAfterOffThreadMergeBeforePublicationIsRecomputed() = runTest {
        val f=ActorFixture(this,manual=true);f.open()
        f.actor.change(f.details.copy(name="Saved"));f.actor.onBackground();runCurrent()
        var steps=0
        while(f.notices.isEmpty()) { assertTrue(f.io!!.runOne());runCurrent();assertTrue(++steps<30) }
        // Current-head read has published; the queued task is the pure continuation calculation.
        assertTrue(f.io!!.runOne())
        f.actor.change(f.details.copy(primer="After calculation"))
        runCurrent();f.pump()
        assertEquals("After calculation",f.details.primer)
        assertEquals("After calculation",f.head.primer)
        assertFalse(f.actor.state.value.rawDirty)
    }
    @Test fun textPlannerSuspensionPreservesExactClickAndTypingDuringWaitingClickRebase() = runTest {
        val f=ActorFixture(this,true,manual=true);f.open(DeckEditorTarget.Entry(EntityId("entry")))
        val start=f.actor.state.value.body as DeckDraftBody.Entry
        f.actor.change(start.copy(quantity="2",notes="Automatic"));f.actor.onBackground();runCurrent()
        assertTrue(f.io!!.runOne());runCurrent() // raw write ACK, TEXT planning now queued
        assertEquals(DraftPhase.PREPARING,f.actor.state.value.phase)
        val click=(f.actor.state.value.body as DeckDraftBody.Entry).copy(board=BoardRole.SIDEBOARD,finish=Knowledge.Known(Finish.FOIL))
        f.actor.change(click);f.actor.confirmExplicit()
        f.actor.change(click.copy(board=BoardRole.MAYBEBOARD,notes="Later"))
        var steps=0
        while(f.notices.isEmpty()) { assertTrue(f.io.runOne());runCurrent();assertTrue(++steps<40) }
        assertTrue(f.io.runOne()) // pure queue + waiting-click rebase calculated, owner has not resumed
        f.actor.change((f.actor.state.value.body as DeckDraftBody.Entry).copy(notes="Latest keystroke"))
        runCurrent();f.pump()
        val explicit=f.prepares.single { it.scope==DeckDraftCommitScope.EXPLICIT_CONFIRMED }
        val confirmed=explicit.body as DeckDraftBody.Entry
        assertEquals(BoardRole.SIDEBOARD,confirmed.board);assertEquals("Automatic",confirmed.notes)
        assertEquals(Knowledge.Known(Finish.FOIL),confirmed.finish)
        assertEquals(BoardRole.SIDEBOARD,f.head.entries.single().board)
        assertEquals("Latest keystroke",f.head.entries.single().notes)
        val retained=f.actor.state.value.body as DeckDraftBody.Entry
        assertEquals(BoardRole.MAYBEBOARD,retained.board);assertEquals("Latest keystroke",retained.notes)
        assertEquals(retained,f.slot.draft!!.body)
    }
    @Test fun textInvalidAndFixedPointOutcomesNeverClearASeparateButtonCapture() = runTest {
        for(fixedPoint in listOf(false,true)) {
            val f=ActorFixture(this,true,manual=true);f.open(DeckEditorTarget.Entry(EntityId("entry")))
            f.fixedPointPlanner=fixedPoint
            val original=f.actor.state.value.body as DeckDraftBody.Entry
            f.actor.change(original.copy(board=BoardRole.SIDEBOARD));f.actor.onBackground();runCurrent()
            assertTrue(f.io!!.runOne());runCurrent()
            assertEquals(DraftPhase.PREPARING,f.actor.state.value.phase)
            f.actor.confirmExplicit()
            f.actor.change((f.actor.state.value.body as DeckDraftBody.Entry).copy(board=BoardRole.MAYBEBOARD))
            f.pump()
            assertEquals(BoardRole.SIDEBOARD,f.head.entries.single().board)
            assertEquals(1,f.prepares.count { it.scope==DeckDraftCommitScope.EXPLICIT_CONFIRMED })
            assertEquals(BoardRole.MAYBEBOARD,(f.actor.state.value.body as DeckDraftBody.Entry).board)
        }
    }
    @Test fun unknownWriteBeforeEffectThenSupersessionDisclosesConflictWithoutGuessedSuccess() = runTest {
        val f=ActorFixture(this);f.open();f.beforeLose.add("write")
        f.actor.change(f.details.copy(name="Mine"));advanceTimeBy(500);runCurrent()
        assertEquals(0L,f.slot.generation);assertTrue(f.actor.state.value.uncertain)
        val foreign=f.details.copy(name="Foreign")
        f.raw(foreign);f.actor.retryExact();runCurrent()
        assertEquals(foreign,f.slot.draft!!.body);assertEquals("Mine",f.details.name)
        assertTrue(f.actor.state.value.conflict);assertFalse(f.actor.state.value.uncertain)
        assertTrue(f.commits.isEmpty());assertEquals(2,f.writes.size)
        f.actor.discardInput();runCurrent();assertNull(f.slot.draft)
    }
    @Test fun negativeRawRetryReadFailureRetainsOriginalRequestAndOnlyRetriesReads() = runTest {
        val f=ActorFixture(this);f.open();f.beforeLose.add("write")
        f.actor.change(f.details.copy(name="Mine"));advanceTimeBy(500);runCurrent()
        f.raw(f.details.copy(name="Foreign"));f.lose.add("load")
        f.actor.retryExact();runCurrent()
        assertTrue(f.actor.state.value.uncertain);assertTrue(f.actor.state.value.hasFrozenCall)
        assertEquals(2,f.writes.size)
        f.actor.retryExact();runCurrent()
        assertEquals(2,f.writes.size);assertTrue(f.actor.state.value.conflict)
        assertFalse(f.actor.state.value.uncertain);assertEquals("Mine",f.details.name)
    }
    @Test fun unknownDiscardBeforeEffectCannotAutomaticallyDiscardSupersedingForeignRaw() = runTest {
        val f=ActorFixture(this)
        val mine=DeckDraftBody.Details(Revision(0),"Mine","","")
        f.raw(mine);f.open();f.beforeLose.add("discard")
        f.actor.discardInput();runCurrent();assertTrue(f.actor.state.value.uncertain)
        val foreign=mine.copy(name="Foreign");f.raw(foreign)
        f.actor.retryExact();runCurrent()
        assertEquals(foreign,f.slot.draft!!.body);assertEquals(mine,f.actor.state.value.body)
        assertTrue(f.actor.state.value.conflict);assertFalse(f.actor.state.value.uncertain)
        assertEquals(2,f.discards.size)
        f.actor.discardInput();runCurrent();assertNull(f.slot.draft)
        assertEquals(2L,f.discards.last().first)
    }
    @Test fun unknownCommitBeforeEffectThenExternalHeadUsesPermanentNegativeReplayFence() = runTest {
        val f=ActorFixture(this);f.open();f.beforeLose.add("commit")
        f.actor.change(f.details.copy(name="Mine"));advanceTimeBy(500);runCurrent()
        assertTrue(f.acks.isEmpty());assertTrue(f.actor.state.value.uncertain)
        val intent=(f.slot.latestPreparation as DeckDraftPreparation.Pending).intent
        f.head=f.head.copy(revision=Revision(7),name="External")
        f.actor.retryExact();runCurrent()
        assertEquals(2,f.commits.size);assertTrue(f.acks.isEmpty())
        assertFalse(f.actor.state.value.uncertain);assertTrue(f.actor.state.value.conflict)
        assertEquals("Mine",f.details.name);assertNull(f.actor.state.value.base)
        f.actor.discardInput();runCurrent()
        assertEquals(intent.operationId,f.discards.single().second)
        assertNull(f.slot.draft);assertEquals("External",f.head.name)
    }
    @Test fun unknownPrepareBeforeEffectThenRawSupersessionDoesNotCreateReplacementIntent() = runTest {
        val f=ActorFixture(this);f.open();f.beforeLose.add("prepare")
        f.actor.change(f.details.copy(name="Mine"));advanceTimeBy(500);runCurrent()
        assertTrue(f.reservations.isEmpty());assertTrue(f.actor.state.value.uncertain)
        val foreign=f.details.copy(name="Foreign");f.raw(foreign)
        f.actor.retryExact();runCurrent()
        assertTrue(f.reservations.isEmpty());assertTrue(f.commits.isEmpty())
        assertTrue(f.actor.state.value.conflict);assertFalse(f.actor.state.value.uncertain)
        assertEquals("Mine",f.details.name);assertEquals(foreign,f.slot.draft!!.body)
        assertEquals(f.prepares[0].operation,f.prepares[1].operation)
    }
    @Test fun rawRetryReturnsCurrentPreparationStatusAndNeverInventsOldMetadata() = runTest {
        val f=ActorFixture(this);f.open();f.lose.add("write")
        f.actor.change(f.details.copy(name="Mine"));advanceTimeBy(500);runCurrent()
        val body=f.slot.draft!!.body
        val plan=(referencePlan(f.head,body,DeckDraftCommitScope.EXPLICIT_CONFIRMED,null) as Outcome.Success).value
        val operation=EntityId("foreign-prepare")
        f.prepare(f.head.id,f.slot.draft!!.token,DeckDraftCommitScope.EXPLICIT_CONFIRMED,plan.patch,operation,f.cancel)
        f.actor.retryExact();runCurrent()
        assertEquals(operation,(f.actor.state.value.durable!!.latestPreparation as DeckDraftPreparation.Pending).intent.operationId)
        assertTrue(f.actor.state.value.conflict);assertFalse(f.actor.state.value.uncertain)
        assertTrue(f.commits.isEmpty());assertEquals(1,f.prepares.size)
    }
    @Test fun fixtureChecksCurrentRawCasAfterPreparationSuspension() = runTest {
        val f=ActorFixture(this);f.open();val gate=f.gate("prepare.before")
        f.actor.change(f.details.copy(name="Mine"));advanceTimeBy(500);runCurrent()
        val foreign=f.details.copy(name="Foreign");f.raw(foreign)
        gate.complete(Unit);runCurrent()
        assertTrue(f.reservations.isEmpty());assertTrue(f.commits.isEmpty())
        assertEquals(foreign,f.slot.draft!!.body);assertTrue(f.actor.state.value.conflict)
    }

    @Test fun resumeOriginalTargetClearsOnlyItsRequestedTargetConflict() = runTest {
        val f=ActorFixture(this,true)
        val raw=DeckDraftBody.Details(Revision(0),"Recovered original","","")
        f.raw(raw);f.open(DeckEditorTarget.Entry(EntityId("entry")))
        val ticket=f.actor.state.value.session!!.ticket
        assertTrue(f.actor.state.value.conflict)
        f.actor.open(f.head.id,DeckEditorTarget.Details)
        assertFalse(f.actor.state.value.conflict);assertEquals(ticket,f.actor.state.value.session!!.ticket)
        assertEquals(raw,f.actor.state.value.body)
        f.actor.confirmExplicit();runCurrent()
        assertEquals("Recovered original",f.head.name)
        assertTrue(f.actor.state.value.visible)
    }
    @Test fun canonicalAliasAtMaximumContentRevisionStillUsesActualUnchangedAck() = runTest {
        val f=ActorFixture(this,true);f.head=f.head.copy(revision=Revision(Long.MAX_VALUE))
        f.open(DeckEditorTarget.Entry(EntityId("entry")))
        val original=f.head
        f.actor.change((f.actor.state.value.body as DeckDraftBody.Entry).copy(quantity="01"))
        advanceTimeBy(500);runCurrent()
        assertEquals(original,f.head);assertNull(f.slot.draft)
        assertEquals(Revision(Long.MAX_VALUE),f.actor.state.value.base!!.revision)
    }

    @Test fun resumeCannotClearANewerObservedHeadBehindItsCachedTargetConflict() = runTest {
        val f=ActorFixture(this,true)
        val raw=DeckDraftBody.Details(Revision(0),"Recovered original","","")
        f.raw(raw);f.open(DeckEditorTarget.Entry(EntityId("entry")))
        f.actor.onDeckHeadChanged(f.head.id,Revision(1))
        f.actor.open(f.head.id,DeckEditorTarget.Details)
        assertTrue(f.actor.state.value.conflict)
        assertEquals(raw,f.actor.state.value.body)
        f.actor.confirmExplicit();advanceTimeBy(1000);runCurrent()
        assertTrue(f.prepares.isEmpty());assertTrue(f.commits.isEmpty())
        assertEquals(raw,f.slot.draft!!.body)
    }

    @Test fun retiredCatalogUnknownBeforePrepareResolvesOnlyThroughActualConflictReads() = runTest {
        for(foreign in listOf("none","raw","pending")) {
            val f=ActorFixture(this);f.open(DeckEditorTarget.Add);f.selectCard();f.beforeLose.add("prepare")
            f.actor.confirmExplicit();runCurrent()
            assertTrue(f.actor.state.value.uncertain)
            val local=(f.actor.state.value.body as DeckDraftBody.Add).copy(notes="My retained input")
            f.actor.change(local)
            var pending:EntityId?=null
            if(foreign!="none") {
                val remote=local.copy(notes="Foreign input");f.raw(remote)
                if(foreign=="pending") {
                    val plan=(referencePlan(f.head,remote,DeckDraftCommitScope.EXPLICIT_CONFIRMED,EntityId("foreign-entry")) as Outcome.Success).value
                    pending=EntityId("foreign-operation")
                    f.prepare(f.head.id,f.slot.draft!!.token,DeckDraftCommitScope.EXPLICIT_CONFIRMED,plan.patch,pending,f.cancel)
                }
            }
            val expectedRaw=f.slot.draft
            val prepareCount=f.prepares.size
            f.current=false;f.actor.onActiveGeneration(GenerationId("retired"))
            f.actor.retryExact();runCurrent()
            assertEquals(prepareCount,f.prepares.size);assertTrue(f.commits.isEmpty())
            assertFalse(f.actor.state.value.uncertain);assertTrue(f.actor.state.value.conflict)
            assertEquals(local,f.actor.state.value.body);assertEquals(expectedRaw,f.slot.draft)
            assertTrue(f.discards.isEmpty())
            f.actor.discardInput();runCurrent()
            assertNull(f.slot.draft);assertEquals(pending,f.discards.single().second)
            assertEquals(expectedRaw!!.token.generation,f.discards.single().first)
        }
    }
    @Test fun retiredUnknownPrepareReadFailureRetainsExactOwnerUntilFreshDisclosure() = runTest {
        val f=ActorFixture(this);f.open(DeckEditorTarget.Add);f.selectCard();f.beforeLose.add("prepare")
        f.actor.confirmExplicit();runCurrent()
        val body=f.actor.state.value.body
        f.current=false;f.actor.onActiveGeneration(GenerationId("retired"));f.lose.add("load")
        f.actor.retryExact();runCurrent()
        assertTrue(f.actor.state.value.uncertain);assertTrue(f.actor.state.value.hasFrozenCall)
        assertEquals(body,f.actor.state.value.body);assertEquals(1,f.prepares.size)
        f.actor.discardInput();runCurrent()
        assertFalse(f.actor.state.value.uncertain);assertTrue(f.actor.state.value.conflict)
        assertEquals(body,f.actor.state.value.body);assertTrue(f.discards.isEmpty())
        f.actor.discardInput();runCurrent();assertNull(f.slot.draft)
        assertEquals(1,f.prepares.size);assertTrue(f.commits.isEmpty())
    }
    @Test fun matchingUnknownPreparedAddRemainsDiscardableAfterCatalogRetirement() = runTest {
        val f=ActorFixture(this);f.open(DeckEditorTarget.Add);f.selectCard();f.lose.add("prepare")
        f.actor.confirmExplicit();runCurrent()
        val pending=(f.slot.latestPreparation as DeckDraftPreparation.Pending).intent
        f.current=false;f.actor.onActiveGeneration(GenerationId("retired"));f.actor.retryExact();runCurrent()
        assertFalse(f.actor.state.value.uncertain);assertTrue(f.commits.isEmpty())
        f.actor.discardInput();runCurrent()
        assertEquals(pending.operationId,f.discards.single().second);assertNull(f.slot.draft)
    }
    @Test fun matchingCommittedPrepareReplayNeedsNoRenewedCatalogAuthority() = runTest {
        val f=ActorFixture(this);f.open(DeckEditorTarget.Add);f.selectCard();f.lose.add("prepare")
        f.actor.confirmExplicit();runCurrent()
        val pending=(f.slot.latestPreparation as DeckDraftPreparation.Pending).intent
        f.commit(f.head.id,pending.operationId,pending.requestSha256,f.cancel)
        f.current=false;f.actor.onActiveGeneration(GenerationId("retired"));f.actor.retryExact();runCurrent()
        assertFalse(f.actor.state.value.uncertain);assertFalse(f.actor.state.value.hasFrozenCall)
        assertEquals(1,f.prepares.size);assertEquals(1,f.commits.size)
        assertEquals(DeckEditorTarget.Entry(f.head.entries.single().id),f.actor.state.value.session!!.target)
        assertEquals(f.head,f.callbackHeads.single())
    }

    @Test fun malformedNormalPrepareCommittedReplayRetainsExactPrepareRequest() = runTest {
        val f=ActorFixture(this);f.open();f.lose.add("prepare")
        f.actor.change(f.details.copy(name="Mine"));advanceTimeBy(500);runCurrent()
        val intent=(f.slot.latestPreparation as DeckDraftPreparation.Pending).intent
        f.commit(f.head.id,intent.operationId,intent.requestSha256,f.cancel)
        val terminal=f.slot.latestPreparation as DeckDraftPreparation.Committed
        val bad=terminal.copy(acknowledgement=terminal.acknowledgement.copy(snapshot=terminal.acknowledgement.snapshot.copy(description="Not projected")))
        f.reservations[intent.operationId]=bad;f.slot=f.slot.copy(latestPreparation=bad)
        val foreign=DeckDraftBody.Details(f.head.revision,"Foreign durable","","");f.raw(foreign)
        val current=f.slot.draft
        repeat(3) { f.actor.retryExact();runCurrent() }
        assertTrue(f.actor.state.value.uncertain);assertEquals(Problem.CORRUPT_DATA,f.actor.state.value.contentProblem)
        assertEquals("Mine",f.details.name);assertEquals(current,f.slot.draft)
        assertEquals(1,f.writes.size);assertEquals(1,f.commits.size)
        assertEquals(1,f.prepares.map { listOf(it.token,it.scope,it.patch,it.operation) }.distinct().size)
    }
    @Test fun malformedUnauthorizedCommittedDiscoveryNeverRefreshesTokenOrWritesForeignRaw() = runTest {
        val f=ActorFixture(this);f.open(DeckEditorTarget.Add);f.selectCard();f.lose.add("prepare")
        f.actor.confirmExplicit();runCurrent()
        val local=f.actor.state.value.body
        val intent=(f.slot.latestPreparation as DeckDraftPreparation.Pending).intent
        f.commit(f.head.id,intent.operationId,intent.requestSha256,f.cancel)
        val terminal=f.slot.latestPreparation as DeckDraftPreparation.Committed
        val bad=terminal.copy(acknowledgement=terminal.acknowledgement.copy(snapshot=terminal.acknowledgement.snapshot.copy(primer="Not projected")))
        f.reservations[intent.operationId]=bad;f.slot=f.slot.copy(latestPreparation=bad)
        f.raw(DeckDraftBody.Details(f.head.revision,"Foreign recovery","",""))
        val foreign=f.slot.draft
        f.current=false;f.actor.onActiveGeneration(GenerationId("retired"))
        repeat(3) { f.actor.retryExact();runCurrent() }
        f.actor.discardInput();runCurrent()
        assertTrue(f.actor.state.value.uncertain);assertEquals(local,f.actor.state.value.body)
        assertEquals(foreign,f.slot.draft);assertEquals(1,f.writes.size)
        assertEquals(1,f.prepares.size);assertEquals(1,f.commits.size);assertTrue(f.discards.isEmpty())
    }
    @Test fun malformedSameOperationDiscoveryKeepsRejectedAuthorityAcrossRetryAndDiscard() = runTest {
        val f=ActorFixture(this);f.open(DeckEditorTarget.Add);f.selectCard();f.beforeLose.add("prepare")
        f.actor.confirmExplicit();runCurrent()
        val local=f.actor.state.value.body
        val operation=f.prepares.single().operation
        val foreignAdd=(local as DeckDraftBody.Add).copy(notes="Different source")
        f.raw(foreignAdd)
        val foreignPlan=(referencePlan(f.head,foreignAdd,DeckDraftCommitScope.EXPLICIT_CONFIRMED,EntityId("foreign-entry")) as Outcome.Success).value
        f.prepare(f.head.id,f.slot.draft!!.token,DeckDraftCommitScope.EXPLICIT_CONFIRMED,foreignPlan.patch,operation,f.cancel)
        val intent=(f.slot.latestPreparation as DeckDraftPreparation.Pending).intent
        f.commit(f.head.id,operation,intent.requestSha256,f.cancel)
        f.raw(DeckDraftBody.Details(f.head.revision,"Foreign current recovery","",""))
        val foreign=f.slot.draft
        f.current=false;f.actor.onActiveGeneration(GenerationId("retired"))
        repeat(3) { f.actor.retryExact();runCurrent() }
        f.actor.discardInput();runCurrent()
        assertTrue(f.actor.state.value.uncertain);assertEquals(Problem.CORRUPT_DATA,f.actor.state.value.contentProblem)
        assertEquals(local,f.actor.state.value.body);assertEquals(foreign,f.slot.draft)
        assertEquals(1,f.writes.size);assertEquals(2,f.prepares.size);assertEquals(1,f.commits.size)
        assertTrue(f.discards.isEmpty())
    }

    @Test fun initialReadFailuresRetainSessionAndTypedExitAcrossRetries() = runTest {
        for (stage in listOf("head.load","load")) for (mutation in listOf(false,true)) {
            val f=ActorFixture(this)
            f.lose.add(stage)
            val gate=f.gate(if(stage=="load") "draft.load" else stage)
            f.actor.open(f.head.id,DeckEditorTarget.Details);runCurrent()
            val session=f.actor.state.value.session
            val action=if(mutation) DeferredDeckAction.UndoDeck(f.head.id) else DeferredDeckAction.CloseEditor
            val ticket=f.actor.requestExit(action)
            gate.complete(Unit);runCurrent()
            assertEquals(DraftExitPhase.NEEDS_CHOICE,f.actor.state.value.exit!!.phase)
            assertEquals(session,f.actor.state.value.session)
            f.lose.add(stage);f.actor.retryExact();runCurrent()
            assertEquals(ticket,f.actor.state.value.exit!!.ticket)
            assertEquals(action,f.actor.state.value.exit!!.action)
            assertEquals(DraftExitPhase.NEEDS_CHOICE,f.actor.state.value.exit!!.phase)
            f.actor.retryExact();runCurrent()
            assertEquals(session,f.actor.state.value.session)
            assertEquals(DraftExitPhase.READY,f.actor.state.value.exit!!.phase)
            assertEquals(action,f.actor.takeReadyExit(ticket));assertNull(f.actor.takeReadyExit(ticket))
            assertTrue(f.writes.isEmpty());assertTrue(f.prepares.isEmpty());assertTrue(f.commits.isEmpty())
        }
    }
    @Test fun failedInitialReadRecoversInvalidRawWithoutOverwritingAndHoldsMutationExit() = runTest {
        val f=ActorFixture(this)
        val raw=DeckDraftBody.Details(Revision(0),"\uD800","","");f.raw(raw)
        f.lose.add("load");f.open()
        val session=f.actor.state.value.session
        val action=DeferredDeckAction.UndoDeck(f.head.id)
        val ticket=f.actor.requestExit(action);runCurrent()
        f.actor.retryExact();runCurrent()
        assertEquals(session,f.actor.state.value.session);assertEquals(raw,f.actor.state.value.body)
        assertEquals(raw,f.slot.draft!!.body);assertTrue(f.writes.isEmpty());assertTrue(f.prepares.isEmpty())
        assertEquals(1,f.plannerCalls.size)
        assertEquals(DraftExitPhase.NEEDS_CHOICE,f.actor.state.value.exit!!.phase)
        f.actor.discardInput();runCurrent()
        assertEquals(action,f.actor.takeReadyExit(ticket));assertNull(f.actor.takeReadyExit(ticket))
    }
    @Test fun explicitReadTimeFlushPromotesRecoveredTextWhilePlainOpenStaysReadOnly() = runTest {
        for(background in listOf(false,true)) {
            val f=ActorFixture(this)
            val raw=DeckDraftBody.Details(Revision(0),"Recovered eligible","","");f.raw(raw)
            val gate=f.gate("draft.load");f.actor.open(f.head.id,DeckEditorTarget.Details);runCurrent()
            val ticket=if(background) { f.actor.onBackground();null } else f.actor.requestExit(DeferredDeckAction.CloseEditor)
            val committing=f.gate("commit.before")
            gate.complete(Unit);runCurrent()
            if(ticket!=null) { assertEquals(DraftExitPhase.FLUSHING,f.actor.state.value.exit!!.phase);assertNull(f.actor.takeReadyExit(ticket)) }
            committing.complete(Unit);runCurrent()
            assertEquals("Recovered eligible",f.head.name);assertEquals(1,f.commits.size)
            assertTrue(f.writes.isEmpty())
            if(ticket!=null) { assertEquals(DeferredDeckAction.CloseEditor,f.actor.takeReadyExit(ticket));assertNull(f.actor.takeReadyExit(ticket)) }
        }
        val plain=ActorFixture(this);plain.raw(DeckDraftBody.Details(Revision(0),"Read only","",""));plain.open()
        advanceTimeBy(1000);runCurrent();assertTrue(plain.plannerCalls.isEmpty());assertTrue(plain.commits.isEmpty())
    }
    @Test fun recoveryReloadDrainsQueuedExitOrBackgroundWithoutSubmittingPreparedIntent() = runTest {
        for(background in listOf(false,true)) for(failure in listOf(false,true)) {
            val f=ActorFixture(this);f.prepared(DeckDraftBody.Details(Revision(0),"Prepared","",""));f.open()
            val gate=f.gate("draft.load");if(failure) f.lose.add("load")
            f.actor.retryExact();runCurrent()
            val ticket=if(background) { f.actor.onBackground();null } else f.actor.requestExit(DeferredDeckAction.UndoDeck(f.head.id))
            gate.complete(Unit);runCurrent()
            if(ticket!=null) { assertEquals(ticket,f.actor.state.value.exit!!.ticket);assertEquals(DraftExitPhase.NEEDS_CHOICE,f.actor.state.value.exit!!.phase) }
            assertTrue(f.prepares.isEmpty());assertTrue(f.commits.isEmpty());assertTrue(f.writes.isEmpty())
        }
    }
    @Test fun recoveryReloadConflictCompletesRetainedExitDecision() = runTest {
        val f=ActorFixture(this);f.prepared(DeckDraftBody.Details(Revision(0),"Prepared","",""));f.open()
        f.actor.change(f.details.copy(primer="Local retained"))
        val gate=f.gate("draft.load");f.actor.retryExact();runCurrent()
        val ticket=f.actor.requestExit(DeferredDeckAction.CloseEditor)
        gate.complete(Unit);runCurrent()
        assertEquals("Local retained",f.details.primer);assertTrue(f.actor.state.value.conflict)
        assertEquals(ticket,f.actor.state.value.exit!!.ticket);assertEquals(DraftExitPhase.NEEDS_CHOICE,f.actor.state.value.exit!!.phase)
        assertTrue(f.writes.isEmpty());assertTrue(f.commits.isEmpty())
    }
    @Test fun consumedBrowseExitNeverResurrectsEditorAfterLateRecoveryRead() = runTest {
        for(failure in listOf(false,true)) {
            val f=ActorFixture(this);f.prepared(DeckDraftBody.Details(Revision(0),"Prepared","",""));f.open()
            val ticket=f.actor.requestExit(DeferredDeckAction.CloseEditor);runCurrent()
            val gate=f.gate("draft.load");if(failure) f.lose.add("load")
            f.actor.retryExact();runCurrent()
            f.actor.chooseExit(ticket,DraftExitChoice.CONTINUE)
            assertEquals(DeferredDeckAction.CloseEditor,f.actor.takeReadyExit(ticket));assertFalse(f.actor.state.value.visible)
            gate.complete(Unit);runCurrent()
            assertFalse(f.actor.state.value.visible);assertNull(f.actor.state.value.exit);assertNull(f.actor.takeReadyExit(ticket))
            assertTrue(f.prepares.isEmpty());assertTrue(f.commits.isEmpty())
        }
    }
    @Test fun genuineNoProgressIsNotRetriedByRepeatedLifecycleEvents() = runTest {
        val f=ActorFixture(this);f.fixedPointPlanner=true
        val raw=DeckDraftBody.Details(Revision(0),"No progress","","");f.raw(raw);f.open()
        f.actor.onBackground();runCurrent();assertEquals(1,f.plannerCalls.size)
        repeat(3) { f.actor.onBackground();runCurrent() }
        val ticket=f.actor.requestExit(DeferredDeckAction.UndoDeck(f.head.id));runCurrent()
        assertEquals(1,f.plannerCalls.size);assertEquals(raw,f.slot.draft!!.body)
        assertEquals(DraftExitPhase.NEEDS_CHOICE,f.actor.state.value.exit!!.phase)
        f.actor.chooseExit(ticket,DraftExitChoice.STAY);assertTrue(f.commits.isEmpty());assertEquals(0,f.ids)
    }
    @Test fun bodyNullOwnedDiscardRetriesExactMutationRatherThanInitialReload() = runTest {
        val f=ActorFixture(this,true);f.open(DeckEditorTarget.Remove(EntityId("entry")))
        f.actor.confirmExplicit();runCurrent();assertNull(f.actor.state.value.body);assertTrue(f.head.entries.isEmpty())
        val session=f.actor.state.value.session
        f.lose.add("discard");f.actor.discardInput();runCurrent()
        assertTrue(f.actor.state.value.uncertain);assertNotNull(f.actor.state.value.rawProblem)
        val request=f.discards.single();f.actor.retryExact();runCurrent()
        assertEquals(listOf(request,request),f.discards);assertEquals(session,f.actor.state.value.session)
        assertFalse(f.actor.state.value.uncertain);assertNull(f.actor.state.value.rawProblem)
        assertNull(f.actor.state.value.body);assertEquals(1,f.commits.size)
    }

    @Test fun backgroundFlushSurvivesFailedInitialReadAndReloadsSameOwner() = runTest {
        for(stage in listOf("head.load","load")) {
            val f=ActorFixture(this);f.raw(DeckDraftBody.Details(Revision(0),"After read retry","",""))
            f.lose.add(stage);val gate=f.gate(if(stage=="load") "draft.load" else stage)
            f.actor.open(f.head.id,DeckEditorTarget.Details);runCurrent()
            val session=f.actor.state.value.session
            f.actor.onBackground();gate.complete(Unit);runCurrent()
            assertNotNull(f.actor.state.value.rawProblem);assertTrue(f.commits.isEmpty())
            f.actor.retryExact();runCurrent()
            assertEquals(session,f.actor.state.value.session);assertEquals("After read retry",f.head.name)
            assertEquals(1,f.commits.size);assertTrue(f.writes.isEmpty())
        }
    }
    @Test fun explicitReadTimeFlushNeverPromotesRecoveredStagedAddOrRemove() = runTest {
        for(remove in listOf(false,true)) {
            val f=ActorFixture(this,true)
            val raw=if(remove) DeckDraftBody.Remove(Revision(0),EntityId("entry")) else
                DeckDraftBody.Add(Revision(0),"2","Staged",BoardRole.MAIN,Knowledge.Absent("Exact absence"),SnapshotList.empty(),DeckDraftSelection(f.cardIdentity,GenerationId("active")))
            f.raw(raw);val gate=f.gate("draft.load")
            f.actor.open(f.head.id,if(remove) DeckEditorTarget.Remove(EntityId("entry")) else DeckEditorTarget.Add);runCurrent()
            f.actor.onBackground();val ticket=f.actor.requestExit(DeferredDeckAction.UndoDeck(f.head.id))
            gate.complete(Unit);runCurrent()
            assertEquals(raw,f.actor.state.value.body);assertEquals(raw,f.slot.draft!!.body)
            assertEquals(DraftExitPhase.NEEDS_CHOICE,f.actor.state.value.exit!!.phase)
            assertTrue(f.plannerCalls.isEmpty());assertTrue(f.prepares.isEmpty());assertTrue(f.commits.isEmpty());assertTrue(f.writes.isEmpty())
            f.actor.chooseExit(ticket,DraftExitChoice.STAY)
        }
    }

}
