package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import java.security.MessageDigest
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetainedDeckDraftEditorTest {
    @Test fun continuousTypingHasFirstDirty500msMaximumWait() = runTest {
        val f = ActorFixture(this); f.open()
        repeat(5) { index -> f.actor.change(f.details.copy(name = "Name$index")); advanceTimeBy(100); runCurrent() }
        assertEquals(1, f.writes.size)
        assertEquals("Name4", f.head.name)
        assertEquals(500L, f.writeTimes.single())
        assertFalse(f.actor.state.value.rawDirty)
        assertTrue(f.actor.state.value.visible)
    }
    @Test fun rawWriteGeneratesNoIdsAndPreservesInvalidUtf16() = runTest {
        val f = ActorFixture(this); f.open()
        f.actor.change(f.details.copy(name = "\uD800", description = "\uDFFF"))
        advanceTimeBy(500); runCurrent()
        assertEquals(0, f.ids)
        assertEquals("\uD800", (f.slot.draft!!.body as DeckDraftBody.Details).name)
        assertEquals("Original", f.head.name)
        assertTrue(f.prepares.isEmpty())
        assertFalse(f.actor.state.value.rawDirty)
        assertFalse(f.actor.state.value.explicitlyInvalid)
    }
    @Test fun validFieldsCommitIndependentlyAndRetainInvalidResidual() = runTest {
        val f = ActorFixture(this); f.open()
        f.actor.change(f.details.copy(name = "", primer = "New primer"))
        advanceTimeBy(500); runCurrent()
        assertEquals("Original", f.head.name); assertEquals("New primer", f.head.primer)
        assertEquals(Revision(1), f.head.revision)
        assertEquals("", f.details.name)
        assertEquals(Revision(1), f.details.baseRevision)
        assertEquals(f.details, f.slot.draft!!.body)
        assertEquals(1, f.commits.size)
    }
    @Test fun explicitButtonCapturesBodyWhileOlderRawWriteWaits() = runTest {
        val f = ActorFixture(this); f.open()
        val gate = f.gate("write.before")
        f.actor.change(f.details.copy(name = "Older")); advanceTimeBy(500); runCurrent()
        f.actor.change(f.details.copy(name = "Confirmed")); f.actor.confirmExplicit()
        f.actor.change(f.details.copy(name = "Unconfirmed"))
        gate.complete(Unit); runCurrent()
        assertEquals(listOf("Older", "Confirmed"), f.writes.map { (it.second as DeckDraftBody.Details).name })
        assertEquals("Confirmed", f.head.name)
        assertEquals("Unconfirmed", f.details.name)
        assertTrue(f.actor.state.value.rawDirty)
        assertEquals("Confirmed", (f.prepares.single().body as DeckDraftBody.Details).name)
        advanceTimeBy(500); runCurrent()
        assertEquals("Unconfirmed", f.head.name)
    }
    @Test fun overdueNewerRawDrainsImmediatelyAfterSuspendedWrite() = runTest {
        val f = ActorFixture(this); f.open()
        val gate = f.gate("write.before")
        f.actor.change(f.details.copy(name = "First")); advanceTimeBy(500); runCurrent()
        f.actor.change(f.details.copy(primer = "Later")); advanceTimeBy(600); runCurrent()
        gate.complete(Unit); runCurrent()
        assertEquals("Later", f.head.primer)
        assertTrue(f.writes.size >= 2)
        assertEquals(1100L, f.writeTimes.last())
    }
    @Test fun typingDuringPrepareCanOwnPreservedLaterAndRebaseExactly() = runTest {
        val f = ActorFixture(this); f.open()
        val gate = f.gate("prepare.after")
        f.actor.change(f.details.copy(name = "Saved")); advanceTimeBy(500); runCurrent()
        f.actor.change(f.details.copy(name = "\uD800", primer = "Later")); advanceTimeBy(500); runCurrent()
        gate.complete(Unit); runCurrent()
        assertEquals("Saved", f.head.name)
        assertEquals("\uD800", f.details.name)
        assertEquals("Later", f.details.primer)
        assertEquals(Revision(2), f.details.baseRevision)
        assertEquals(2, f.acks.size)
        assertTrue(f.acks.first().slotEffect is DeckDraftSlotEffect.PreservedLater)
        assertTrue(f.acks.last().slotEffect is DeckDraftSlotEffect.Residual)
        assertEquals("Later", f.head.primer)
        assertFalse(f.actor.state.value.conflict)
        advanceTimeBy(500); runCurrent()
        assertEquals("Later", f.head.primer)
        assertEquals("\uD800", f.details.name)
    }
    @Test fun typingDuringCommitSurvivesWithValueDifferenceMerge() = runTest {
        val f = ActorFixture(this); f.open()
        val gate = f.gate("commit.after")
        f.actor.change(f.details.copy(name = "Committed")); advanceTimeBy(500); runCurrent()
        f.actor.change(f.details.copy(name = "Changed then back"))
        f.actor.change(f.details.copy(name = "Committed", description = "Later"))
        gate.complete(Unit); runCurrent()
        assertEquals("Committed", f.details.name)
        assertEquals("Later", f.details.description)
        assertEquals(Revision(1), f.details.baseRevision)
        advanceTimeBy(500); runCurrent()
        assertEquals("Later", f.head.description)
        assertEquals(2, f.commits.size)
    }
    @Test fun unknownWriteRetriesExactArgumentsBeforeQueuedSuccessor() = runTest {
        val f = ActorFixture(this); f.open(); f.lose.add("write")
        f.actor.change(f.details.copy(name = "One")); advanceTimeBy(500); runCurrent()
        assertTrue(f.actor.state.value.uncertain)
        f.actor.change(f.details.copy(name = "Two")); advanceTimeBy(1000); runCurrent()
        assertEquals(1, f.writes.size)
        f.actor.retryExact(); runCurrent()
        assertEquals(f.writes[0], f.writes[1])
        assertEquals("Two", f.head.name)
        assertEquals(2L, f.head.revision.value)
    }
    @Test fun unknownPrepareRetriesOneOperationAndHandlesCommittedDirectly() = runTest {
        val f = ActorFixture(this); f.open(); f.lose.add("prepare")
        f.actor.change(f.details.copy(name = "One")); advanceTimeBy(500); runCurrent()
        assertTrue(f.actor.state.value.uncertain)
        val intent = (f.slot.latestPreparation as DeckDraftPreparation.Pending).intent
        f.commit(f.head.id, intent.operationId, intent.requestSha256, f.cancel)
        f.actor.retryExact(); runCurrent()
        assertEquals(1, f.commits.size)
        assertEquals(f.prepares[0].operation, f.prepares[1].operation)
        assertEquals("One", f.details.name)
        assertFalse(f.actor.state.value.hasFrozenCall)
    }
    @Test fun unknownCommitReplaysFrozenHashWithoutFreshCatalogProof() = runTest {
        val f = ActorFixture(this); f.open(DeckEditorTarget.Add); f.selectCard()
        f.lose.add("commit"); f.actor.confirmExplicit(); runCurrent()
        assertTrue(f.actor.state.value.uncertain)
        f.current = false; f.actor.onActiveGeneration(GenerationId("retired"))
        f.actor.retryExact(); runCurrent()
        assertEquals(2, f.commits.size); assertEquals(f.commits[0], f.commits[1])
        assertEquals(1, f.head.entries.size)
        assertTrue(f.actor.state.value.session!!.target is DeckEditorTarget.Entry)
        assertFalse(f.actor.state.value.uncertain)
    }
    @Test fun addAlwaysTransitionsToOneFrozenEntryEvenWithNullQueuedMerge() = runTest {
        val f = ActorFixture(this); f.open(DeckEditorTarget.Add); f.selectCard()
        f.actor.confirmExplicit(); runCurrent()
        val entry = f.head.entries.single()
        assertEquals(DeckEditorTarget.Entry(entry.id), f.actor.state.value.session!!.target)
        assertEquals(entry.id, (f.actor.state.value.body as DeckDraftBody.Entry).entryId)
        assertTrue(f.actor.state.value.visible)
        assertEquals(2, f.ids)
        val body = f.actor.state.value.body as DeckDraftBody.Entry
        f.actor.change(body.copy(notes = "After ADD")); advanceTimeBy(500); runCurrent()
        assertEquals(1, f.head.entries.size)
        assertEquals("After ADD", f.head.entries.single().notes)
        assertEquals(1, f.prepares.sumOf { p -> p.patch.edits.count { it is DeckEdit.Add } })
    }
    @Test fun pendingAddLocksSelectionButRetainsNewerText() = runTest {
        val f = ActorFixture(this); f.open(DeckEditorTarget.Add); f.selectCard()
        val gate = f.gate("write.before"); f.actor.confirmExplicit(); runCurrent()
        val before = f.actor.state.value.body as DeckDraftBody.Add
        f.actor.change(before.copy(selection = null))
        assertEquals(before, f.actor.state.value.body)
        f.actor.change(before.copy(quantity = "-", notes = "Later"))
        gate.complete(Unit); runCurrent()
        val queued = f.actor.state.value.body as DeckDraftBody.Entry
        assertEquals("-", queued.quantity); assertEquals("Later", queued.notes)
        advanceTimeBy(500); runCurrent()
        assertEquals(1, f.head.entries.size)
    }
    @Test fun backgroundFlushIsImmediateAndDoesNotNavigate() = runTest {
        val f = ActorFixture(this); f.open()
        f.actor.change(f.details.copy(description = "Now")); f.actor.onBackground(); runCurrent()
        assertEquals(0L, f.writeTimes.single()); assertEquals("Now", f.head.description)
        assertTrue(f.actor.state.value.visible); assertNull(f.actor.state.value.exit)
    }
    @Test fun emptyAliasConsumesRawWithoutInventingRevisionOrTimestamp() = runTest {
        val f = ActorFixture(this, withEntry = true); f.open(DeckEditorTarget.Entry(EntityId("entry")))
        val before = f.head
        f.actor.change((f.actor.state.value.body as DeckDraftBody.Entry).copy(quantity = "01"))
        advanceTimeBy(500); runCurrent()
        assertEquals(before, f.head); assertNull(f.slot.draft)
        assertEquals("1", (f.actor.state.value.body as DeckDraftBody.Entry).quantity)
        assertEquals(1, f.commits.size)
    }
    @Test fun stagedChoicesAndDefaultAddRemoveNeverAutomaticallyCommit() = runTest {
        val f = ActorFixture(this, withEntry = true); f.open(DeckEditorTarget.Entry(EntityId("entry")))
        val before = f.actor.state.value.body as DeckDraftBody.Entry
        f.actor.change(before.copy(board = BoardRole.SIDEBOARD, finish = Knowledge.Unknown("unresolved", SnapshotList.empty())))
        advanceTimeBy(500); runCurrent()
        assertTrue(f.prepares.isEmpty()); assertEquals(BoardRole.MAIN, f.head.entries.single().board)
        assertEquals(before.copy(board = BoardRole.SIDEBOARD, finish = Knowledge.Unknown("unresolved", SnapshotList.empty())), f.slot.draft!!.body)
        val add = ActorFixture(this); add.open(DeckEditorTarget.Add); add.selectCard(); advanceTimeBy(500); runCurrent()
        assertTrue(add.prepares.isEmpty()); assertTrue(add.head.entries.isEmpty())
        val remove = ActorFixture(this, withEntry = true); remove.open(DeckEditorTarget.Remove(EntityId("entry"))); remove.actor.onBackground(); runCurrent()
        assertTrue(remove.prepares.isEmpty()); assertEquals(1, remove.head.entries.size)
    }
    @Test fun fullProjectionMismatchStaysUnresolvedAndDoesNotClearInput() = runTest {
        val f = ActorFixture(this); f.open()
        f.corruptAck = { it.copy(snapshot = it.snapshot.copy(description = "Not projected")) }
        f.actor.change(f.details.copy(name = "Input")); advanceTimeBy(500); runCurrent()
        assertEquals("Input", f.details.name)
        assertTrue(f.actor.state.value.uncertain); assertTrue(f.actor.state.value.hasFrozenCall)
        assertEquals(Problem.CORRUPT_DATA, f.actor.state.value.contentProblem)
        f.corruptAck = { it }; f.actor.retryExact(); runCurrent()
        assertFalse(f.actor.state.value.uncertain)
    }
    @Test fun addProofRequiresExactQualifiedSelectionAndGeneration() = runTest {
        val changes: List<(DeckDraftSelection) -> DeckDraftSelection> = listOf(
            { it.copy(generation=null) }, { it.copy(generation=GenerationId("old")) },
            { it.copy(identity=it.identity.copy(oracleId=Knowledge.Known(OracleId(ProviderId("other"),"oracle")))) },
            { it.copy(identity=it.identity.copy(originalName=Knowledge.Known("Other"))) },
            { it.copy(identity=it.identity.copy(language=LanguageTag("de"))) },
            { it.copy(identity=it.identity.copy(printingId=Knowledge.Known(CardId(ProviderId("cards"),"p")))) },
            { it.copy(identity=it.identity.copy(raw=ProviderIdentity(ProviderId("cards"),"card","raw"))) },
        )
        for (change in changes) {
            val f=ActorFixture(this);f.open(DeckEditorTarget.Add);f.selectCard()
            val before=f.actor.state.value.body as DeckDraftBody.Add
            val changed=before.copy(selection=change(checkNotNull(before.selection)))
            f.actor.change(changed);f.actor.confirmExplicit();runCurrent()
            assertEquals(changed,f.slot.draft!!.body);assertTrue(f.prepares.isEmpty());assertTrue(f.commits.isEmpty())
            assertEquals(Problem.STALE_GENERATION,f.actor.state.value.contentProblem)
        }
    }
    @Test fun preprepareCatalogFailureStillPersistsQueuedTextAndRetainsConfirmation() = runTest {
        val f=ActorFixture(this);f.open(DeckEditorTarget.Add);f.selectCard();f.current=false
        f.actor.confirmExplicit();runCurrent()
        val confirmed=f.actor.state.value.body as DeckDraftBody.Add
        f.actor.change(confirmed.copy(notes="Later"));advanceTimeBy(500);runCurrent()
        assertEquals("Later",(f.slot.draft!!.body as DeckDraftBody.Add).notes)
        assertTrue(f.prepares.isEmpty());assertEquals(2,f.ids)
        f.current=true;f.actor.retryExact();runCurrent()
        assertEquals("",f.head.entries.single().notes)
        assertEquals("Later",(f.actor.state.value.body as DeckDraftBody.Entry).notes)
        assertEquals(2,f.ids)
    }
    @Test fun acknowledgementOperationHashSourceAndEffectAreValidatedIndependently() = runTest {
        val corruptions: List<(DeckDraftCommitAck)->DeckDraftCommitAck> = listOf(
            { it.copy(operationId=EntityId("foreign")) },
            { it.copy(preparedRequestSha256="f".repeat(64)) },
            { it.copy(sourceToken=it.sourceToken.copy(rawSha256="f".repeat(64))) },
            { it.copy(slotEffect=DeckDraftSlotEffect.Cleared(999)) },
            { it.copy(snapshot=it.snapshot.copy(revision=Revision(9))) },
        )
        for(corrupt in corruptions) {
            val f=ActorFixture(this);f.open();f.corruptAck=corrupt
            f.actor.change(f.details.copy(name="Keep input"));advanceTimeBy(500);runCurrent()
            assertTrue(f.actor.state.value.uncertain);assertEquals("Keep input",f.details.name)
            assertEquals(Revision(0),f.details.baseRevision);assertTrue(f.actor.hasMutationBarrier)
        }
    }
    @Test fun catalogRetiredBetweenPrepareAndFreshCommitPreservesPreparation() = runTest {
        val f = ActorFixture(this); f.open(DeckEditorTarget.Add); f.selectCard()
        val gate = f.gate("prepare.after"); f.actor.confirmExplicit(); runCurrent()
        f.current = false; gate.complete(Unit); runCurrent()
        assertTrue(f.commits.isEmpty()); assertTrue(f.slot.latestPreparation is DeckDraftPreparation.Pending)
        assertEquals(Problem.STALE_GENERATION, f.actor.state.value.contentProblem)
        val body = f.actor.state.value.body as DeckDraftBody.Add
        f.actor.change(body.copy(notes = "Still recoverable")); advanceTimeBy(500); runCurrent()
        assertEquals("Still recoverable", (f.slot.draft!!.body as DeckDraftBody.Add).notes)
        assertTrue(f.commits.isEmpty())
        f.current = true; f.actor.retryExact(); runCurrent()
        assertEquals(1, f.head.entries.size)
        assertEquals("Still recoverable", (f.actor.state.value.body as DeckDraftBody.Entry).notes)
    }
}

/** Independent stateful port oracle: generations/receipts are storage facts, never actor-derived expectations. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ActorFixture(private val test: TestScope, withEntry: Boolean = false, manual: Boolean = false) : LocalDeckLibrary {
    val cancel = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private val absent = Knowledge.Absent("Absent")
    val cardIdentity = CardIdentity(Knowledge.Known(OracleId(ProviderId("cards"), "oracle")), Knowledge.Absent("Original absence reason"), null, Knowledge.Known("Card"), LanguageTag("en"))
    val example = DeckEntry(EntityId("entry"), cardIdentity, 1, BoardRole.MAIN, absent, "", SnapshotList.empty(), SnapshotList.empty())
    var head = DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE,"deck"),"Original", absent,"",absent,SnapshotList.empty(),UtcTimestamp(1),UtcTimestamp(1),Revision(0),
        if (withEntry) SnapshotList.of(example) else SnapshotList.empty(),absent,null,SnapshotList.empty(),SnapshotList.empty())
    var slot = DeckDraftState(0,null,null)
    var ids = 0
    var current = true
    var corruptAck: (DeckDraftCommitAck) -> DeckDraftCommitAck = { it }
    val lose = mutableSetOf<String>()
    val beforeLose = mutableSetOf<String>()
    var fixedPointPlanner = false
    val plannerCalls = mutableListOf<DeckDraftCommitScope>()
    val io = if(manual) ActorManualDispatcher() else null
    val gates = mutableMapOf<String,CompletableDeferred<Unit>>()
    val writes = mutableListOf<Pair<Long,DeckDraftBody>>()
    val writeTimes = mutableListOf<Long>()
    data class PreparationCall(val body: DeckDraftBody, val token: DeckDraftToken, val scope: DeckDraftCommitScope, val patch: DeckPatch, val operation: EntityId)
    val prepares = mutableListOf<PreparationCall>()
    val commits = mutableListOf<Pair<EntityId,String>>()
    val discards = mutableListOf<Triple<Long,EntityId?,DeckDraftDiscardMode>>()
    val acks = mutableListOf<DeckDraftCommitAck>()
    val notices = mutableListOf<Pair<DeckId,Revision>>()
    val callbackHeads = mutableListOf<DeckSnapshot?>()
    val reservations = mutableMapOf<EntityId,DeckDraftPreparation>()
    private var writeReceipt: Pair<Pair<Long,DeckDraftBody>,Long>? = null
    private var discardReceipt: Pair<Triple<Long,EntityId?,DeckDraftDiscardMode>,Long>? = null
    val planner = object : DeckDraftPlanner {
        override fun propose(base: DeckSnapshot, body: DeckDraftBody, scope: DeckDraftCommitScope, addedEntryId: EntityId?, cancel: CancelToken): Outcome<DeckDraftPlan> {
            plannerCalls.add(scope)
            return if(fixedPointPlanner && scope==DeckDraftCommitScope.TEXT_AUTOSAVE) Outcome.Success(DeckDraftPlan(DeckPatch(SnapshotList.empty(),DeckDraftLimits.TEXT_REASON),body)) else referencePlan(base,body,scope,addedEntryId)
        }
    }
    val draftPort = object : LocalDeckDrafts {
        override suspend fun load(id: DeckId, cancel: CancelToken) = loadDraft(id, cancel)
        override suspend fun write(id: DeckId, expectedGeneration: Long, body: DeckDraftBody, cancel: CancelToken) = this@ActorFixture.write(id,expectedGeneration,body,cancel)
        override suspend fun prepare(id: DeckId, token: DeckDraftToken, scope: DeckDraftCommitScope, patch: DeckPatch, operationId: EntityId, cancel: CancelToken) = this@ActorFixture.prepare(id,token,scope,patch,operationId,cancel)
        override suspend fun commit(id: DeckId, operationId: EntityId, preparedRequestSha256: String, cancel: CancelToken) = this@ActorFixture.commit(id,operationId,preparedRequestSha256,cancel)
        override suspend fun discard(id: DeckId, expectedGeneration: Long, expectedPendingOperationId: EntityId?, mode: DeckDraftDiscardMode, cancel: CancelToken) = this@ActorFixture.discard(id,expectedGeneration,expectedPendingOperationId,mode,cancel)
    }
    val actor: RetainedDeckDraftEditor = RetainedDeckDraftEditor(test,this,draftPort,planner,::referenceProject,io ?: StandardTestDispatcher(test.testScheduler),
        { EntityId("generated-${++ids}") }, { test.testScheduler.currentTime }, { current }, ::recordCommit)
    private fun recordCommit(id: DeckId, revision: Revision) { notices.add(id to revision); callbackHeads.add(actor.state.value.currentHead) }
    val details get() = actor.state.value.body as DeckDraftBody.Details
    fun open(target: DeckEditorTarget = DeckEditorTarget.Details) { actor.open(head.id,target); pump() }
    fun pump() {
        test.runCurrent()
        repeat(1000) { if(io?.runOne()!=true) return; test.runCurrent() }
        error("Actor did not settle")
    }
    fun gate(stage: String) = CompletableDeferred<Unit>().also { gates[stage] = it }
    private suspend fun wait(stage: String) { gates.remove(stage)?.await() }
    private fun <T> lost(stage: String, value: T): Outcome<T> = if (lose.remove(stage)) Outcome.Unknown("Lost acknowledgement", SnapshotList.empty()) else Outcome.Success(value)
    private fun stale(): Outcome<Nothing> = Outcome.Failure(DomainFailure(FailureCode.STALE_REVISION,"CAS conflict",SnapshotList.empty()))
    private fun invalid(): Outcome<Nothing> = Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT,"Invalid",SnapshotList.empty()))
    fun raw(body: DeckDraftBody) { val next=slot.generation+1; slot=slot.copy(generation=next,draft=SavedDeckDraft(DeckDraftToken(head.id,next,body.baseRevision,digest(body)),body)) }
    fun selectCard() {
        val absent = Knowledge.Absent("Not supplied")
        val provenance = Provenance(ProviderId("cards"),null,absent,absent,UtcTimestamp(0),absent,GenerationId("active"),null,null)
        val card = CardRecord(OracleId(ProviderId("cards"),"oracle"),"Card",absent,absent,absent,absent,absent,absent,
            CardStats(absent,absent,absent,absent),absent,absent,absent,"card",LanguageTag("en"),SnapshotList.empty(),SnapshotList.empty(),provenance,null)
        actor.onActiveGeneration(GenerationId("active"));actor.provideCurrentCard(card)
        val before=actor.state.value.body as DeckDraftBody.Add
        actor.change(before.copy(selection=DeckDraftSelection(cardIdentity,GenerationId("active"))))
    }
    override suspend fun load(id: DeckId, cancel: CancelToken): Outcome<DeckSnapshot> { wait("head.load"); return lost("head.load",head) }
    suspend fun loadDraft(id: DeckId, cancel: CancelToken): Outcome<DeckDraftState> { wait("draft.load"); return lost("load",slot) }
    suspend fun write(id: DeckId, expected: Long, body: DeckDraftBody, cancel: CancelToken): Outcome<DeckDraftState> {
        writes.add(expected to body); writeTimes.add(test.testScheduler.currentTime); wait("write.before")
        if(beforeLose.remove("write")) return Outcome.Unknown("Before effect",SnapshotList.empty())
        val request=expected to body
        if (writeReceipt?.first==request && slot.generation==writeReceipt?.second) return lost("write",slot)
        if (id!=head.id || expected!=slot.generation) return stale()
        raw(body);writeReceipt=request to slot.generation
        wait("write.after");return lost("write",slot)
    }
    suspend fun prepare(id: DeckId, token: DeckDraftToken, scope: DeckDraftCommitScope, patch: DeckPatch, operation: EntityId, cancel: CancelToken): Outcome<DeckDraftPreparation> {
        val raw=slot.draft
        val body=raw?.body ?: pinnedBodies[operation] ?: return stale()
        prepares.add(PreparationCall(body,token,scope,patch,operation));wait("prepare.before")
        if(beforeLose.remove("prepare")) return Outcome.Unknown("Before effect",SnapshotList.empty())
        val original=reservations[operation]
        if (original!=null) {
            if (original.intent.sourceToken!=token || original.intent.scope!=scope || original.intent.patch!=patch) return invalid()
            return lost("prepare",original)
        }
        if (slot.draft?.token!=token || head.revision!=token.baseRevision || slot.latestPreparation is DeckDraftPreparation.Pending) return stale()
        val added=patch.edits.filterIsInstance<DeckEdit.Add>().singleOrNull()?.entry?.id
        val plan=referencePlan(head,body,scope,added) as? Outcome.Success ?: return invalid()
        if (plan.value.patch!=patch) return invalid()
        val intent=PreparedDeckDraft(operation,token,scope,patch,digest(listOf(operation,token,scope,patch)))
        val pending=DeckDraftPreparation.Pending(intent);pinnedBodies[operation]=body;reservations[operation]=pending;slot=slot.copy(latestPreparation=pending)
        wait("prepare.after");return lost("prepare",pending)
    }
    suspend fun commit(id: DeckId, operation: EntityId, hash: String, cancel: CancelToken): Outcome<DeckDraftCommitAck> {
        commits.add(operation to hash);wait("commit.before")
        if(beforeLose.remove("commit")) return Outcome.Unknown("Before effect",SnapshotList.empty())
        val status=reservations[operation] ?: return invalid()
        if (hash!=status.intent.requestSha256 || id!=head.id) return invalid()
        if (status is DeckDraftPreparation.Committed) return lost("commit",corruptAck(status.acknowledgement))
        if (status is DeckDraftPreparation.Abandoned) return stale()
        val intent=status.intent
        if (head.revision!=intent.sourceToken.baseRevision) return stale()
        val submitted=pinnedBodies[operation] ?: return invalid()
        val added=intent.patch.edits.filterIsInstance<DeckEdit.Add>().singleOrNull()?.entry?.id
        val plan=(referencePlan(head,submitted,intent.scope,added) as Outcome.Success).value
        val next=if(intent.patch.edits.isEmpty()) head.revision else Revision(head.revision.value+1)
        val result=(referenceProject(head,intent.patch,head.revision,UtcTimestamp(100+next.value)) as Outcome.Success).value
        val effect: DeckDraftSlotEffect
        if (slot.draft?.token==intent.sourceToken) {
            val gen=slot.generation+1
            val residual=plan.residual
            if (residual==null) { slot=slot.copy(generation=gen,draft=null);effect=DeckDraftSlotEffect.Cleared(gen) }
            else { val saved=SavedDeckDraft(DeckDraftToken(id,gen,result.revision,digest(residual)),residual);slot=slot.copy(generation=gen,draft=saved);effect=DeckDraftSlotEffect.Residual(saved.token) }
        } else effect=DeckDraftSlotEffect.PreservedLater(slot.draft!!.token)
        head=result
        val ack=DeckDraftCommitAck(operation,hash,result,intent.sourceToken,effect)
        val terminal=DeckDraftPreparation.Committed(intent,ack);reservations[operation]=terminal;slot=slot.copy(latestPreparation=terminal);acks.add(ack)
        wait("commit.after");return lost("commit",corruptAck(ack))
    }
    suspend fun discard(id: DeckId, expected: Long, pending: EntityId?, mode: DeckDraftDiscardMode, cancel: CancelToken): Outcome<DeckDraftState> {
        val request=Triple(expected,pending,mode);discards.add(request);wait("discard.before")
        if(beforeLose.remove("discard")) return Outcome.Unknown("Before effect",SnapshotList.empty())
        if (discardReceipt?.first==request && slot.generation==discardReceipt?.second) return lost("discard",slot)
        if (expected!=slot.generation || (slot.latestPreparation as? DeckDraftPreparation.Pending)?.intent?.operationId!=pending) return stale()
        val status=slot.latestPreparation
        val next=slot.generation+1
        if (pending!=null) {
            val abandoned=DeckDraftPreparation.Abandoned(checkNotNull(status).intent);reservations[pending]=abandoned;slot=slot.copy(latestPreparation=abandoned)
        }
        val saved=if(mode==DeckDraftDiscardMode.INPUT) null else slot.draft?.let { it.copy(token=it.token.copy(generation=next,rawSha256=digest(it.body))) }
        slot=slot.copy(generation=next,draft=saved);discardReceipt=request to next
        wait("discard.after");return lost("discard",slot)
    }
    val pinnedBodies=mutableMapOf<EntityId,DeckDraftBody>()
    fun prepared(body: DeckDraftBody, scope: DeckDraftCommitScope = DeckDraftCommitScope.EXPLICIT_CONFIRMED, added: EntityId? = null): PreparedDeckDraft {
        raw(body)
        val plan=(referencePlan(head,body,scope,added) as Outcome.Success).value
        val intent=PreparedDeckDraft(EntityId("recovered-op"),slot.draft!!.token,scope,plan.patch,digest(plan))
        slot=slot.copy(latestPreparation=DeckDraftPreparation.Pending(intent));reservations[intent.operationId]=checkNotNull(slot.latestPreparation);pinnedBodies[intent.operationId]=body
        return intent
    }
    override suspend fun create(initial: DeckSnapshot, operationId: EntityId, cancel: CancelToken): Outcome<DeckSnapshot> { head=initial;return Outcome.Success(head) }
    override suspend fun list(page: PageRequest, cancel: CancelToken): Outcome<Page<DeckSummary>> = Outcome.Success(Page(SnapshotList.of(DeckSummary(head.id,head.name,head.revision,head.createdAt,head.modifiedAt,head.entries.size,head.entries.sumOf { it.quantity.toLong() })),null,SnapshotReference(LocalDeckLibraryLimits.SOURCE,null,Revision(0),null)))
    override suspend fun duplicate(id: DeckId, expected: Revision, name: String, operationId: EntityId, cancel: CancelToken): Outcome<DeckSnapshot> = if(head.revision!=expected) stale() else Outcome.Success(head.copy(id=DeckId(LocalDeckLibraryLimits.SOURCE,"duplicate"),name=name,revision=Revision(0)))
}

internal fun digest(value: Any): String {
    val text=value.toString();val bytes=ByteArray(text.length*2)
    for(index in text.indices) { bytes[index*2]=(text[index].code ushr 8).toByte();bytes[index*2+1]=text[index].code.toByte() }
    return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
internal fun referenceProject(base: DeckSnapshot, patch: DeckPatch, expected: Revision, modified: UtcTimestamp): Outcome<DeckSnapshot> {
    if(expected!=base.revision) return Outcome.Failure(DomainFailure(FailureCode.STALE_REVISION,"Expected source revision",SnapshotList.empty()))
    if(patch.edits.isEmpty()) return Outcome.Success(base)
    var result=base
    for(edit in patch.edits) result=when(edit) {
        is DeckEdit.Rename -> result.copy(name=edit.name)
        is DeckEdit.Description -> result.copy(description=edit.text)
        is DeckEdit.Primer -> result.copy(primer=edit.markdown)
        is DeckEdit.Replace -> result.copy(entries=SnapshotList.copyOf(result.entries.map { if(it.id==edit.entry.id) edit.entry else it }))
        is DeckEdit.Add -> result.copy(entries=SnapshotList.copyOf(result.entries+edit.entry))
        is DeckEdit.Remove -> result.copy(entries=SnapshotList.copyOf(result.entries.filterNot { it.id==edit.entryId }))
        else -> return Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT,"Unexpected primitive",SnapshotList.empty()))
    }
    return Outcome.Success(result.copy(revision=Revision(base.revision.value+1),modifiedAt=modified))
}
internal fun referencePlan(base: DeckSnapshot, body: DeckDraftBody, scope: DeckDraftCommitScope, added: EntityId?): Outcome<DeckDraftPlan> {
    val explicit=scope==DeckDraftCommitScope.EXPLICIT_CONFIRMED
    fun invalid(): Outcome<Nothing> = Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT,"Invalid input",SnapshotList.empty()))
    fun valid(text:String, name:Boolean=false):Boolean {
        if(name && text.isBlank()) return false
        for(i in text.indices) {
            if(text[i].isHighSurrogate() && (i+1==text.length || !text[i+1].isLowSurrogate())) return false
            if(text[i].isLowSurrogate() && (i==0 || !text[i-1].isHighSurrogate())) return false
        }
        return text.toByteArray().size <= if(name) 1024 else 1048576
    }
    if(body.baseRevision!=base.revision) return invalid()
    val edits=mutableListOf<DeckEdit>()
    var residual: DeckDraftBody?=null
    when(body) {
        is DeckDraftBody.Details -> {
            val name=valid(body.name,true);val description=valid(body.description);val primer=valid(body.primer)
            if(explicit && !(name&&description&&primer)) return invalid()
            if(name&&body.name!=base.name) edits.add(DeckEdit.Rename(body.name))
            if(description&&body.description!=base.description) edits.add(DeckEdit.Description(body.description))
            if(primer&&body.primer!=base.primer) edits.add(DeckEdit.Primer(body.primer))
            if(!explicit && edits.isEmpty()) return invalid()
            val rev=if(edits.isEmpty()) base.revision else Revision(base.revision.value+1)
            if(!name||!description||!primer) residual=body.copy(baseRevision=rev)
        }
        is DeckDraftBody.Entry -> {
            val old=base.entries.single { it.id==body.entryId };val qty=body.quantity.toIntOrNull()?.takeIf { it>0 };val notes=valid(body.notes)
            if(explicit && (qty==null||!notes)) return invalid()
            val updated=old.copy(quantity=qty?:old.quantity,notes=if(notes) body.notes else old.notes,
                board=if(explicit) body.board else old.board,finish=if(explicit) body.finish else old.finish,labels=if(explicit) body.labels else old.labels)
            if(updated!=old) edits.add(DeckEdit.Replace(updated))
            if(!explicit && !(qty!=null && body.quantity!=old.quantity.toString()) && !(notes&&body.notes!=old.notes)) return invalid()
            val rev=if(edits.isEmpty()) base.revision else Revision(base.revision.value+1)
            val remaining=body.copy(baseRevision=rev,quantity=if(qty==null) body.quantity else updated.quantity.toString(),notes=if(notes) updated.notes else body.notes)
            val canonical=DeckDraftBody.Entry(rev,old.id,updated.quantity.toString(),updated.notes,updated.board,updated.finish,updated.labels)
            if(remaining!=canonical) residual=remaining
        }
        is DeckDraftBody.Add -> {
            if(!explicit || body.selection==null || added==null || !valid(body.notes)) return invalid()
            val qty=body.quantity.toIntOrNull()?.takeIf { it>0 }?:return invalid()
            edits.add(DeckEdit.Add(DeckEntry(added,checkNotNull(body.selection).identity,qty,body.board,body.finish,body.notes,body.labels,SnapshotList.empty())))
        }
        is DeckDraftBody.Remove -> { if(!explicit) return invalid();edits.add(DeckEdit.Remove(body.entryId)) }
    }
    return Outcome.Success(DeckDraftPlan(DeckPatch(SnapshotList.copyOf(edits),if(explicit) DeckDraftLimits.EXPLICIT_REASON else DeckDraftLimits.TEXT_REASON),residual))
}

/** Separate IO queue lets a test run pure computation without immediately resuming the owner. */
internal class ActorManualDispatcher : CoroutineDispatcher() {
    private val tasks=java.util.ArrayDeque<Runnable>()
    override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.addLast(block) }
    fun runOne(): Boolean { val task=tasks.pollFirst() ?: return false;task.run();return true }
}
