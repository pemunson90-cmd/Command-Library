package com.architectpsg.commandlibrary.feature.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.long
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.number
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.request
import kotlin.coroutines.cancellation.CancellationException

/** Durable raw recovery, pinned canonical preparation, atomic grouped commit and explicit discard. */
public class SqlLocalDeckDrafts(db:Database,codec:DeckPayloadCodecPort,patchCodec:LocalDeckPatchCodecPort,
    draftCodec:DeckDraftPayloadCodecPort,planner:DeckDraftPlanner,private val clock:Clock):LocalDeckDrafts {
    private val store=DeckSnapshotStore(db,codec)
    private val rows=DeckDraftRows(store)
    private val transitions=DeckDraftTransitions(store,rows,patchCodec,draftCodec,planner)
    private val primitive=DeckPrimitiveEdits(store)

    public override suspend fun load(id:DeckId,cancel:CancelToken):Outcome<DeckDraftState> = store.operation(cancel) { tx ->
        transitions.id(id); transitions.requireDeck(tx,id,cancel)
        transitions.state(tx,id,cancel).public
    }
    public override suspend fun write(id:DeckId,expectedGeneration:Long,body:DeckDraftBody,cancel:CancelToken):Outcome<DeckDraftState> = store.operation(cancel) { tx ->
        transitions.id(id); transitions.generation(expectedGeneration)
        val bytes=transitions.raw(body)
        val fingerprint=checked(FailureCode.INVALID_INPUT) { DeckDraftFrames.write(id,expectedGeneration,bytes,cancel) }
        transitions.requireDeck(tx,id,cancel)
        val state=transitions.state(tx,id,cancel)
        if(immediate(state.row,expectedGeneration,DeckDraftFrames.Mutation.WRITE,fingerprint)) return@operation state.public
        cas(state.row,expectedGeneration)
        val generation=transitions.next(state.row.generation)
        val source=value(tx.query(request("SELECT 1 AS found FROM deck_revision WHERE provider=:provider AND deck_id=:id AND revision=:revision",*keys(id),"revision" to number(body.baseRevision.value)),cancel))
        if(source.isEmpty()) reject(FailureCode.INVALID_INPUT,"Draft base revision does not exist")
        val next=state.row.copy(generation=generation,raw=rows.raw(body,bytes),mutation=DeckDraftFrames.Mutation.WRITE,mutationSha=fingerprint,exists=true)
        rows.writeSlot(tx,state.row,next,bytes,cancel)
        DeckDraftState(generation,SavedDeckDraft(DeckDraftToken(id,generation,body.baseRevision,next.raw!!.payload.sha256),body),state.public.latestPreparation)
    }
    public override suspend fun prepare(id:DeckId,token:DeckDraftToken,scope:DeckDraftCommitScope,patch:DeckPatch,operationId:EntityId,cancel:CancelToken):Outcome<DeckDraftPreparation> = store.operation(cancel) { tx ->
        transitions.id(id); identifier(operationId.value)
        if(token.deckId != id) reject(FailureCode.INVALID_INPUT,"Draft token belongs to another deck")
        val patchBytes=transitions.patch(patch)
        val fingerprint=checked(FailureCode.INVALID_INPUT) { DeckDraftFrames.prepared(token,scope,patchBytes,cancel) }
        val existing=rows.intent(tx,operationId,cancel)
        if(existing != null) {
            if(existing.source != token || existing.scope != scope || existing.requestSha != fingerprint) reject(FailureCode.INVALID_INPUT,"Operation identity was reused")
            return@operation transitions.loaded(tx,existing,cancel).preparation
        }
        requireUnreceipted(tx,operationId,cancel)
        transitions.requireDeck(tx,id,cancel)
        val state=transitions.state(tx,id,cancel)
        if(state.public.draft?.token != token) reject(FailureCode.STALE_REVISION,"Draft token changed")
        if(state.latest?.row?.status == DeckDraftFrames.Status.Prepared) reject(FailureCode.STALE_REVISION,"A draft preparation is pending")
        if(state.latest != null && state.latest.row.source.generation >= token.generation) reject(FailureCode.STALE_REVISION,"Preparation source generation was already used")
        val source=transitions.head(tx,id,token.baseRevision,cancel,preparing=true)
        corrupt(transitions.source(tx,id,token.baseRevision.value,cancel) == source)
        transitions.plan(source,state.public.draft!!.body,scope,patch,FailureCode.INVALID_INPUT,cancel)
        val intent=DeckDraftRows.Intent(operationId,token,state.row.raw!!,scope,rows.payload(patchBytes),fingerprint,DeckDraftFrames.Status.Prepared)
        rows.insert(tx,intent,state.raw!!,patchBytes,cancel)
        rows.writeSlot(tx,state.row,state.row.copy(latest=operationId),state.raw,cancel)
        DeckDraftPreparation.Pending(PreparedDeckDraft(operationId,token,scope,patch,fingerprint))
    }
    public override suspend fun commit(id:DeckId,operationId:EntityId,preparedRequestSha256:String,cancel:CancelToken):Outcome<DeckDraftCommitAck> = store.operation(cancel) { tx ->
        transitions.id(id); identifier(operationId.value); hash(preparedRequestSha256)
        val intent=rows.intent(tx,operationId,cancel) ?: run {
            requireUnreceipted(tx,operationId,cancel)
            throw DeckLibraryProblem(Outcome.Unavailable(UnavailableReason.NOT_FOUND,"Draft preparation not found"))
        }
        if(intent.source.deckId != id || intent.requestSha != preparedRequestSha256) reject(FailureCode.INVALID_INPUT,"Draft preparation identity changed")
        when(intent.status) {
            is DeckDraftFrames.Status.Committed -> {
                val historical=transitions.loaded(tx,intent,cancel).preparation as DeckDraftPreparation.Committed
                return@operation historical.acknowledgement
            }
            DeckDraftFrames.Status.Abandoned -> {
                transitions.loaded(tx,intent,cancel)
                reject(FailureCode.STALE_REVISION,"Draft preparation was abandoned")
            }
            DeckDraftFrames.Status.Prepared -> Unit
        }
        // Fresh publication must admit every reached metadata association before pending bytes.
        val state=transitions.state(tx,id,cancel)
        val loaded=state.latest
        corrupt(loaded != null && loaded.row == intent && state.public.draft != null)
        val admitted=loaded!!
        corrupt(state.row.generation >= intent.source.generation)
        val exact=state.row.generation == intent.source.generation
        if(exact) corrupt(state.public.draft!!.token == intent.source)
        val nextGeneration=if(exact) transitions.next(state.row.generation) else state.row.generation
        transitions.head(tx,id,intent.source.baseRevision,cancel)
        writableEpoch(epoch(tx,cancel))
        val sequence=value(tx.query(request("SELECT ${DeckLibraryRows.integer("seq","sequence")} FROM sqlite_sequence WHERE name='deck_operation'"),cancel))
        if(sequence.isNotEmpty()) writableEpoch(sequence.single().long("sequence"))
        val result=primitive.apply(tx,id,admitted.plan.patch,intent.source.baseRevision,cancel,::now)
        receipt(tx,result,operationId,intent.requestSha,"DRAFT_COMMIT",cancel)
        val committedReceipt=transitions.receipts.receipt(tx,operationId,cancel)!!
        val effect:DeckDraftSlotEffect = if(exact) {
            val residual=admitted.plan.residual
            if(residual != null) corrupt(residual.baseRevision == result.revision)
            val residualBytes=residual?.let { transitions.raw(it) }
            val descriptor=if(residual == null) null else rows.raw(residual,residualBytes!!)
            val mutation=DeckDraftFrames.commitMutation(intent.source,operationId,intent.requestSha,cancel)
            rows.writeSlot(tx,state.row,state.row.copy(generation=nextGeneration,raw=descriptor,mutation=DeckDraftFrames.Mutation.COMMIT,mutationSha=mutation),residualBytes,cancel)
            if(descriptor == null) DeckDraftSlotEffect.Cleared(nextGeneration) else DeckDraftSlotEffect.Residual(DeckDraftToken(id,nextGeneration,result.revision,descriptor.payload.sha256))
        } else DeckDraftSlotEffect.PreservedLater(state.public.draft!!.token)
        rows.complete(tx,intent.copy(status=DeckDraftFrames.Status.Committed(committedReceipt.sequence,result.revision.value,effect)),cancel)
        DeckDraftCommitAck(operationId,intent.requestSha,result,intent.source,effect)
    }
    public override suspend fun discard(id:DeckId,expectedGeneration:Long,expectedPendingOperationId:EntityId?,mode:DeckDraftDiscardMode,cancel:CancelToken):Outcome<DeckDraftState> = store.operation(cancel) { tx ->
        transitions.id(id); transitions.generation(expectedGeneration); expectedPendingOperationId?.let { identifier(it.value) }
        val fingerprint=checked(FailureCode.INVALID_INPUT) { DeckDraftFrames.discard(id,expectedGeneration,expectedPendingOperationId,mode,cancel) }
        transitions.requireDeck(tx,id,cancel)
        val state=transitions.state(tx,id,cancel)
        val mutation=if(mode == DeckDraftDiscardMode.INPUT) DeckDraftFrames.Mutation.DISCARD_INPUT else DeckDraftFrames.Mutation.DISCARD_PREP
        if(immediate(state.row,expectedGeneration,mutation,fingerprint)) return@operation state.public
        cas(state.row,expectedGeneration)
        val pending=state.latest?.takeIf { it.row.status == DeckDraftFrames.Status.Prepared }
        if(pending?.row?.operation != expectedPendingOperationId) reject(FailureCode.STALE_REVISION,"Pending draft preparation changed")
        val generation=transitions.next(state.row.generation)
        if(pending != null) rows.complete(tx,pending.row.copy(status=DeckDraftFrames.Status.Abandoned),cancel)
        val next=state.row.copy(generation=generation,raw=if(mode == DeckDraftDiscardMode.INPUT) null else state.row.raw,mutation=mutation,mutationSha=fingerprint,exists=true)
        rows.writeSlot(tx,state.row,next,if(mode == DeckDraftDiscardMode.INPUT) null else state.raw,cancel)
        val latest=pending?.let { DeckDraftPreparation.Abandoned(it.preparation.intent) } ?: state.public.latestPreparation
        val draft=if(mode == DeckDraftDiscardMode.INPUT) null else state.public.draft?.let { it.copy(token=it.token.copy(generation=generation)) }
        DeckDraftState(generation,draft,latest)
    }
    private fun immediate(slot:DeckDraftRows.Slot,expected:Long,kind:DeckDraftFrames.Mutation,sha:String):Boolean =
        expected < Long.MAX_VALUE && slot.exists && slot.generation == expected+1 && slot.mutation == kind && slot.mutationSha == sha
    private fun cas(slot:DeckDraftRows.Slot,expected:Long) { if(slot.generation != expected) reject(FailureCode.STALE_REVISION,"Draft generation changed") }
    private fun hash(value:String) { if(value.length != 64 || value.any { it !in "0123456789abcdef" }) reject(FailureCode.INVALID_INPUT,"Invalid prepared request hash") }
    private suspend fun requireUnreceipted(tx:DbTransaction,operation:EntityId,cancel:CancelToken) {
        val receipt=transitions.receipts.receipt(tx,operation,cancel)
        if(receipt != null) {
            if(receipt.kind == "DRAFT_COMMIT") reject(FailureCode.CORRUPT_DATA,"Draft receipt lost its intent")
            reject(FailureCode.INVALID_INPUT,"Operation identity is already used")
        }
    }
    private fun now():UtcTimestamp = try { clock.utcNow() }
    catch(e:CancellationException) { throw e }
    catch(e:Exception) { reject(FailureCode.INTERNAL,"Deck clock failed") }
}
