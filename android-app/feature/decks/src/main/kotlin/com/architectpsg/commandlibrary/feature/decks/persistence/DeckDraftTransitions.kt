package com.architectpsg.commandlibrary.feature.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.decks.DeckPatchReducer
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.long
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.number
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.request

/** Fixed-depth canonical intent/result checks; historical replay never reads the mutable slot/head. */
internal class DeckDraftTransitions(private val store:DeckSnapshotStore,private val rows:DeckDraftRows,
    private val patchCodec:LocalDeckPatchCodecPort,private val draftCodec:DeckDraftPayloadCodecPort,private val planner:DeckDraftPlanner) {
    val receipts = DeckNamedVersionRows(store)
    data class Loaded(val row:DeckDraftRows.Intent,val raw:SnapshotBytes,val patchBytes:SnapshotBytes,val body:DeckDraftBody,
        val plan:DeckDraftPlan,val source:DeckSnapshot,val preparation:DeckDraftPreparation)
    data class State(val row:DeckDraftRows.Slot,val raw:SnapshotBytes?,val public:DeckDraftState,val latest:Loaded?)

    fun raw(body:DeckDraftBody):SnapshotBytes = value(draftCodec.encodeDraft(body)).also { requireBytes(it,DeckDraftLimits.MAX_ENCODED_BYTES,FailureCode.INVALID_INPUT) }
    fun patch(patch:DeckPatch):SnapshotBytes = value(patchCodec.encodePatch(patch)).also { requireBytes(it,DeckPayloadLimits.MAX_ENCODED_BYTES,FailureCode.INVALID_INPUT) }
    private fun requireBytes(bytes:SnapshotBytes,maximum:Int,code:FailureCode) { if(bytes.size !in 6..maximum) reject(code,"Invalid draft payload size") }
    private fun <T> decoded(outcome:Outcome<T>):T = when(outcome) {
        is Outcome.Success -> outcome.value
        is Outcome.Unavailable -> if(outcome.reason == UnavailableReason.UNSUPPORTED) throw DeckLibraryProblem(outcome) else reject(FailureCode.CORRUPT_DATA,"Invalid stored draft encoding")
        else -> reject(FailureCode.CORRUPT_DATA,"Invalid stored draft encoding")
    }
    private fun decodeRaw(bytes:SnapshotBytes,base:Long):DeckDraftBody = decoded(draftCodec.decodeDraft(bytes)).also {
        corrupt(it.baseRevision.value == base && decoded(draftCodec.encodeDraft(it)) == bytes)
    }
    fun plan(base:DeckSnapshot,body:DeckDraftBody,scope:DeckDraftCommitScope,patch:DeckPatch,code:FailureCode,cancel:CancelToken):DeckDraftPlan {
        val added = if(body is DeckDraftBody.Add) {
            if(patch.edits.size != 1 || patch.edits.single() !is DeckEdit.Add) reject(code,"Invalid frozen added entry")
            (patch.edits.single() as DeckEdit.Add).entry.id
        } else null
        val result = when(val planned=planner.propose(base,body,scope,added,cancel)) {
            is Outcome.Success -> planned.value
            is Outcome.Unavailable -> if(code == FailureCode.INVALID_INPUT) throw DeckLibraryProblem(planned) else reject(code,"Invalid pinned draft projection")
            is Outcome.Failure -> if(code == FailureCode.INVALID_INPUT) throw DeckLibraryProblem(planned) else reject(code,"Invalid pinned draft projection")
            is Outcome.Unknown -> reject(code,"Invalid pinned draft projection")
        }
        if(result.patch != patch) reject(code,"Draft patch is not canonical")
        return result
    }
    suspend fun source(tx:DbTransaction,id:DeckId,revision:Long,cancel:CancelToken):DeckSnapshot {
        val source = store.historical(tx,id,revision,cancel)
        if(revision != 0L) corrupt(store.historical(tx,id,0,cancel).createdAt == source.createdAt)
        return source
    }
    private fun admitReceipt(intent:DeckDraftRows.Intent,receipt:DeckNamedVersionRows.Receipt?) {
        when(val status=intent.status) {
            DeckDraftFrames.Status.Prepared, DeckDraftFrames.Status.Abandoned -> corrupt(receipt == null)
            is DeckDraftFrames.Status.Committed -> corrupt(receipt != null && receipt.sequence == status.receiptSequence && receipt.deck == intent.source.deckId &&
                receipt.operation == intent.operation && receipt.kind == "DRAFT_COMMIT" && receipt.sha == intent.requestSha && receipt.revision == status.resultRevision)
        }
    }
    suspend fun loaded(tx:DbTransaction,intent:DeckDraftRows.Intent,cancel:CancelToken):Loaded {
        admitReceipt(intent,receipts.receipt(tx,intent.operation,cancel))
        val raw = rows.bytes(tx,intent.source.deckId,intent.operation,intent.raw.payload,"raw",cancel)
        val patchBytes = rows.bytes(tx,intent.source.deckId,intent.operation,intent.patch,"patch",cancel)
        val body = decodeRaw(raw,intent.raw.baseRevision)
        val patch = decoded(patchCodec.decodePatch(patchBytes)); corrupt(decoded(patchCodec.encodePatch(patch)) == patchBytes)
        corrupt(checked(FailureCode.CORRUPT_DATA) { DeckDraftFrames.prepared(intent.source,intent.scope,patchBytes,cancel) } == intent.requestSha)
        val base = source(tx,intent.source.deckId,intent.raw.baseRevision,cancel)
        val plan = plan(base,body,intent.scope,patch,FailureCode.CORRUPT_DATA,cancel)
        val prepared = PreparedDeckDraft(intent.operation,intent.source,intent.scope,patch,intent.requestSha)
        val preparation = when(val status=intent.status) {
            DeckDraftFrames.Status.Prepared -> DeckDraftPreparation.Pending(prepared)
            DeckDraftFrames.Status.Abandoned -> DeckDraftPreparation.Abandoned(prepared)
            is DeckDraftFrames.Status.Committed -> {
                val expectedRevision = if(patch.edits.isEmpty()) base.revision.value else {
                    corrupt(base.revision.value < Long.MAX_VALUE); base.revision.value+1
                }
                corrupt(status.resultRevision == expectedRevision)
                val result = store.historical(tx,intent.source.deckId,status.resultRevision,cancel)
                val projected = if(patch.edits.isEmpty()) base else when(val proposed=DeckPatchReducer.propose(base,patch,base.revision,result.modifiedAt)) {
                    is Outcome.Success -> proposed.value
                    else -> reject(FailureCode.CORRUPT_DATA,"Invalid committed draft projection")
                }
                // ENTIRE snapshot equality is essential even for CLEARED/no-residual historical replay.
                corrupt(projected == result)
                when(val effect=status.effect) {
                    is DeckDraftSlotEffect.Cleared -> corrupt(plan.residual == null)
                    is DeckDraftSlotEffect.Residual -> {
                        val residual = plan.residual; corrupt(residual != null && residual.baseRevision == result.revision)
                        corrupt(rows.raw(residual!!,decoded(draftCodec.encodeDraft(residual))).payload.sha256 == effect.token.rawSha256)
                    }
                    is DeckDraftSlotEffect.PreservedLater -> {
                        val exists = value(tx.query(request("SELECT 1 AS found FROM deck_revision WHERE provider=:provider AND deck_id=:id AND revision=:revision",*store.keys(intent.source.deckId),"revision" to number(effect.observedToken.baseRevision.value)),cancel))
                        corrupt(exists.size == 1)
                    }
                }
                DeckDraftPreparation.Committed(prepared,DeckDraftCommitAck(intent.operation,intent.requestSha,result,intent.source,status.effect))
            }
        }
        return Loaded(intent,raw,patchBytes,body,plan,base,preparation)
    }
    suspend fun state(tx:DbTransaction,id:DeckId,cancel:CancelToken):State {
        val slot = rows.slot(tx,id,cancel)
        val latest = rows.intents(tx,id,"",cancel).singleOrNull()
        val pending = rows.intents(tx,id,"AND i.status='PREPARED'",cancel,2)
        corrupt(pending.size <= 1 && slot.latest == latest?.operation && (latest == null || slot.exists && latest.source.generation <= slot.generation))
        corrupt(pending.isEmpty() || pending.single().operation == latest?.operation && latest!!.status == DeckDraftFrames.Status.Prepared)
        if(latest != null) {
            if(latest.status == DeckDraftFrames.Status.Prepared) {
                corrupt(slot.raw != null)
                if(latest.source.generation == slot.generation) corrupt(slot.raw == latest.raw)
            } else corrupt(latest.source.generation < slot.generation)
        }
        if(slot.raw != null) {
            val sourceExists=value(tx.query(request("SELECT 1 AS found FROM deck_revision WHERE provider=:provider AND deck_id=:id AND revision=:revision",*store.keys(id),"revision" to number(slot.raw.baseRevision)),cancel))
            corrupt(sourceExists.size == 1)
        }
        val receipt = receipts.latestReceipt(tx,id,"DRAFT_COMMIT",cancel)
        val associated = receipt?.let { rows.association(tx,it.sequence,cancel) }
        if(receipt != null) {
            corrupt(associated != null)
            admitReceipt(associated!!,receipt)
        }
        if(latest != null && latest.operation != associated?.operation) admitReceipt(latest,receipts.receipt(tx,latest.operation,cancel))
        if(latest?.status is DeckDraftFrames.Status.Committed) corrupt(associated?.operation == latest.operation)
        if(slot.exists) when(slot.mutation) {
            DeckDraftFrames.Mutation.WRITE,DeckDraftFrames.Mutation.DISCARD_PREP -> corrupt(slot.raw != null)
            DeckDraftFrames.Mutation.DISCARD_INPUT -> corrupt(slot.raw == null)
            DeckDraftFrames.Mutation.COMMIT -> corrupt(associated != null)
        }
        val completion=associated?.status as? DeckDraftFrames.Status.Committed
        if(completion != null) {
            val effect=completion.effect
            val observed=when(effect) { is DeckDraftSlotEffect.Cleared -> effect.resultGeneration; is DeckDraftSlotEffect.Residual -> effect.token.generation; is DeckDraftSlotEffect.PreservedLater -> effect.observedToken.generation }
            corrupt(observed <= slot.generation)
            if(observed == slot.generation) {
                val currentToken=slot.raw?.let { DeckDraftToken(id,slot.generation,Revision(it.baseRevision),it.payload.sha256) }
                when(effect) {
                    is DeckDraftSlotEffect.Cleared -> corrupt(currentToken == null)
                    is DeckDraftSlotEffect.Residual -> corrupt(currentToken == effect.token)
                    is DeckDraftSlotEffect.PreservedLater -> corrupt(currentToken == effect.observedToken)
                }
            }
            if(slot.mutation == DeckDraftFrames.Mutation.COMMIT) {
                corrupt(effect !is DeckDraftSlotEffect.PreservedLater && observed == slot.generation)
                corrupt(slot.mutationSha == checked(FailureCode.CORRUPT_DATA) { DeckDraftFrames.commitMutation(associated!!.source,associated.operation,associated.requestSha,cancel) })
            }
        }
        // All bounded reached receipt/effect/slot metadata is admitted before either BLOB kind.
        val loaded = latest?.let { loaded(tx,it,cancel) }
        if(associated != null && associated.operation != latest?.operation) loaded(tx,associated,cancel)
        val bytes = slot.raw?.let { rows.bytes(tx,id,null,it.payload,"raw",cancel) }
        val body = bytes?.let { decodeRaw(it,slot.raw!!.baseRevision) }
        if(slot.exists && slot.mutation == DeckDraftFrames.Mutation.WRITE) {
            corrupt(bytes != null && DeckDraftFrames.write(id,slot.generation-1,bytes,cancel) == slot.mutationSha)
        }
        val saved = body?.let { SavedDeckDraft(DeckDraftToken(id,slot.generation,it.baseRevision,slot.raw!!.payload.sha256),it) }
        return State(slot,bytes,DeckDraftState(slot.generation,saved,loaded?.preparation),loaded)
    }
    suspend fun head(tx:DbTransaction,id:DeckId,expected:Revision,cancel:CancelToken,preparing:Boolean=false):DeckSnapshot {
        val current = store.current(tx,id,cancel)
        if(current.revision != expected) reject(FailureCode.STALE_REVISION,"Draft base changed")
        val latest = value(tx.query(request("SELECT ${DeckLibraryRows.integer("revision","revision")} FROM deck_revision WHERE provider=:provider AND deck_id=:id ORDER BY revision DESC LIMIT 1",*store.keys(id)),cancel))
        if(latest.size != 1 || latest.single().long("revision") != current.revision.value)
            reject(if(preparing) FailureCode.STALE_REVISION else FailureCode.CORRUPT_DATA,"Draft head is not the latest revision")
        return current
    }
    suspend fun requireDeck(tx:DbTransaction,id:DeckId,cancel:CancelToken) {
        if(!store.exists(tx,id,cancel)) throw DeckLibraryProblem(Outcome.Unavailable(UnavailableReason.NOT_FOUND,"Deck not found"))
    }
    fun id(id:DeckId) { store.id(id); if(id.source != LocalDeckLibraryLimits.SOURCE) reject(FailureCode.INVALID_INPUT,"Draft requires a local deck") }
    fun generation(expected:Long) { if(expected < 0) reject(FailureCode.INVALID_INPUT,"Invalid draft generation") }
    fun next(generation:Long):Long { if(generation == Long.MAX_VALUE) reject(FailureCode.PERSISTENCE,"Draft generation exhausted"); return generation+1 }
}
