package com.architectpsg.commandlibrary.feature.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.column
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.long
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.utf8
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.request
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.number
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.text

/** Bounded metadata admission precedes either raw/patch BLOB query. */
internal class DeckDraftRows(private val store: DeckSnapshotStore) {
    data class Slot(val id: DeckId, val generation: Long = 0, val raw: DeckDraftFrames.Raw? = null, val latest: EntityId? = null,
        val mutation: DeckDraftFrames.Mutation = DeckDraftFrames.Mutation.WRITE, val mutationSha: String = "", val exists: Boolean = false)
    data class Intent(val operation: EntityId, val source: DeckDraftToken, val raw: DeckDraftFrames.Raw, val scope: DeckDraftCommitScope,
        val patch: DeckDraftFrames.Payload, val requestSha: String, val status: DeckDraftFrames.Status)

    private val slotNullable = setOf("raw_base_revision", "raw_version", "raw_length", "raw_sha256", "latest_operation_id")
    private val completion = setOf("receipt_sequence", "result_revision", "effect_kind", "effect_generation", "effect_base_revision", "effect_raw_sha256")
    private fun projection(table: String, texts: Map<String, Int>, integers: Set<String>, nullable: Set<String>, blobs: Set<String>): String = buildList {
        texts.forEach { (column, maximum) -> add(DeckLibraryRows.text("$table.$column", maximum, column)) }
        integers.forEach { add(DeckLibraryRows.integer("$table.$it", it)) }
        nullable.forEach { add("CASE WHEN $table.$it IS NULL THEN 1 ELSE 0 END AS ${it}_null") }
        blobs.forEach { add("CASE WHEN typeof($table.$it)='blob' THEN length($table.$it) ELSE NULL END AS ${it}_actual"); add("CASE WHEN $table.$it IS NULL THEN 1 ELSE 0 END AS ${it}_null") }
    }.joinToString(",")
    private val slotProjection = projection("s", mapOf("provider" to 1024,"deck_id" to 1024,"raw_sha256" to 64,"latest_operation_id" to 1024,
        "last_mutation_kind" to 32,"last_mutation_sha256" to 64,"state_sha256" to 64), setOf("metadata_version","generation","raw_base_revision","raw_version","raw_length"), slotNullable, setOf("raw"))
    private val intentProjection = projection("i", mapOf("operation_id" to 1024,"provider" to 1024,"deck_id" to 1024,"raw_sha256" to 64,"scope" to 32,
        "patch_sha256" to 64,"request_sha256" to 64,"status" to 32,"effect_kind" to 32,"effect_raw_sha256" to 64,"state_sha256" to 64),
        setOf("metadata_version","source_generation","source_base_revision","raw_version","raw_length","patch_version","patch_length","request_version","receipt_sequence","result_revision","effect_generation","effect_base_revision"), completion, setOf("raw","patch"))

    private fun version(value: Long) { corrupt(value > 0); if (value > 1) throw DeckLibraryProblem(Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Newer deck draft metadata")) }
    private fun RowData.optionalLong(name: String): Long? = if (long("${name}_null") == 1L) { corrupt(column(name) == SqlValue.Null); null } else long(name)
    private fun RowData.optionalText(name: String): String? = if (long("${name}_null") == 1L) { corrupt(column(name) == SqlValue.Null); null } else utf8(name)
    private fun RowData.hash(name: String): String = utf8(name).also { corrupt(it.length == 64 && it.all { ch -> ch in "0123456789abcdef" }) }
    private fun deck(row: RowData): DeckId = DeckId(ProviderId(row.utf8("provider")), row.utf8("deck_id")).also { corrupt(it.source == LocalDeckLibraryLimits.SOURCE) }
    private fun descriptor(row: RowData, name: String): DeckDraftFrames.Payload {
        val v = row.long("${name}_version"); version(v)
        val length = row.long("${name}_length")
        corrupt(length in 6..DeckDraftLimits.MAX_ENCODED_BYTES.toLong() && row.long("${name}_actual") == length && row.long("${name}_null") == 0L)
        return DeckDraftFrames.Payload(v.toInt(), length, row.hash("${name}_sha256"))
    }
    suspend fun slot(tx: DbTransaction, id: DeckId, cancel: CancelToken): Slot {
        val selected = value(tx.query(request("SELECT $slotProjection FROM deck_draft_state s WHERE s.provider=:provider AND s.deck_id=:id", *store.keys(id)), cancel))
        if (selected.isEmpty()) return Slot(id)
        val row = selected.single(); version(row.long("metadata_version")); corrupt(deck(row) == id)
        val generation = row.long("generation"); corrupt(generation > 0)
        val base = row.optionalLong("raw_base_revision")
        val raw = if (base == null) {
            corrupt(listOf("raw_version","raw_length","raw_sha256","raw").all { row.long("${it}_null") == 1L })
            corrupt(row.column("raw_version") == SqlValue.Null && row.column("raw_length") == SqlValue.Null && row.column("raw_sha256") == SqlValue.Null && row.column("raw_actual") == SqlValue.Null)
            null
        } else {
            corrupt(base >= 0 && listOf("raw_version","raw_length","raw_sha256").all { row.long("${it}_null") == 0L })
            DeckDraftFrames.Raw(base, descriptor(row, "raw"))
        }
        val latest = row.optionalText("latest_operation_id")?.let(::EntityId)
        val kind = DeckDraftFrames.Mutation.entries.singleOrNull { it.sql == row.utf8("last_mutation_kind") } ?: reject(FailureCode.CORRUPT_DATA, "Invalid draft mutation")
        val mutationSha = row.hash("last_mutation_sha256")
        corrupt(row.hash("state_sha256") == checked(FailureCode.CORRUPT_DATA) { DeckDraftFrames.slot(id,generation,raw,latest,kind,mutationSha,cancel) })
        return Slot(id,generation,raw,latest,kind,mutationSha,true)
    }
    private fun intent(row: RowData, cancel: CancelToken): Intent {
        version(row.long("metadata_version")); version(row.long("request_version"))
        val id = deck(row); val operation = EntityId(row.utf8("operation_id"))
        val generation = row.long("source_generation"); val base = row.long("source_base_revision")
        corrupt(generation > 0 && base >= 0)
        val raw = DeckDraftFrames.Raw(base,descriptor(row,"raw")); val patch = descriptor(row,"patch")
        val scope = when (row.utf8("scope")) { "TEXT_AUTOSAVE" -> DeckDraftCommitScope.TEXT_AUTOSAVE; "EXPLICIT_CONFIRMED" -> DeckDraftCommitScope.EXPLICIT_CONFIRMED; else -> reject(FailureCode.CORRUPT_DATA,"Invalid draft scope") }
        val status = when (row.utf8("status")) {
            "PREPARED", "ABANDONED" -> {
                corrupt(completion.all { row.long("${it}_null") == 1L && row.column(it) == SqlValue.Null })
                if (row.utf8("status") == "PREPARED") DeckDraftFrames.Status.Prepared else DeckDraftFrames.Status.Abandoned
            }
            "COMMITTED" -> {
                val receipt = row.optionalLong("receipt_sequence"); val result = row.optionalLong("result_revision"); val effectGeneration = row.optionalLong("effect_generation")
                corrupt(receipt != null && receipt > 0 && result != null && result >= 0 && effectGeneration != null && effectGeneration > 0)
                val effectBase = row.optionalLong("effect_base_revision"); val effectSha = row.optionalText("effect_raw_sha256")
                val effect = when (row.optionalText("effect_kind")) {
                    "CLEARED" -> { corrupt(effectBase == null && effectSha == null); DeckDraftSlotEffect.Cleared(effectGeneration!!) }
                    "RESIDUAL", "PRESERVED_LATER" -> {
                        corrupt(effectBase != null && effectBase >= 0 && effectSha != null)
                        val token = DeckDraftToken(id,effectGeneration!!,Revision(effectBase!!),effectSha!!)
                        if (row.optionalText("effect_kind") == "RESIDUAL") DeckDraftSlotEffect.Residual(token) else DeckDraftSlotEffect.PreservedLater(token)
                    }
                    else -> reject(FailureCode.CORRUPT_DATA,"Invalid draft effect")
                }
                DeckDraftFrames.Status.Committed(receipt!!,result!!,effect)
            }
            else -> reject(FailureCode.CORRUPT_DATA,"Invalid draft status")
        }
        val sha = row.hash("request_sha256")
        corrupt(row.hash("state_sha256") == checked(FailureCode.CORRUPT_DATA) { DeckDraftFrames.intent(id,operation,generation,raw,scope,patch,sha,status,cancel) })
        return Intent(operation,DeckDraftToken(id,generation,Revision(base),raw.payload.sha256),raw,scope,patch,sha,status)
    }
    suspend fun intents(tx: DbTransaction, id: DeckId, predicate: String, cancel: CancelToken, limit: Int = 1): List<Intent> =
        value(tx.query(request("SELECT $intentProjection FROM deck_draft_intent i WHERE i.provider=:provider AND i.deck_id=:id $predicate ORDER BY i.source_generation DESC LIMIT $limit",*store.keys(id),maxRows=limit),cancel)).map { cancel.throwIfCancelled(); intent(it,cancel).also { n -> corrupt(n.source.deckId == id) } }
    suspend fun intent(tx: DbTransaction, operation: EntityId, cancel: CancelToken): Intent? =
        value(tx.query(request("SELECT $intentProjection FROM deck_draft_intent i WHERE i.operation_id=:operation", "operation" to text(operation.value)),cancel)).singleOrNull()?.let { intent(it,cancel) }
    suspend fun association(tx: DbTransaction, sequence: Long, cancel: CancelToken): Intent? =
        value(tx.query(request("SELECT $intentProjection FROM deck_draft_intent i WHERE i.receipt_sequence=:sequence", "sequence" to number(sequence)),cancel)).singleOrNull()?.let { intent(it,cancel) }
    suspend fun bytes(tx: DbTransaction, id: DeckId, operation: EntityId?, descriptor: DeckDraftFrames.Payload, name: String, cancel: CancelToken): SnapshotBytes {
        val table = if (operation == null) "deck_draft_state" else "deck_draft_intent"
        val predicate = if (operation == null) "provider=:provider AND deck_id=:id" else "operation_id=:operation"
        val params = if (operation == null) store.keys(id) else arrayOf("operation" to text(operation.value))
        val selected = value(tx.query(request("SELECT $name FROM $table WHERE $predicate",*params),cancel)); corrupt(selected.size == 1)
        val blob = (selected.single().column(name) as? SqlValue.Blob)?.value ?: reject(FailureCode.CORRUPT_DATA,"Invalid draft bytes")
        corrupt(blob.size.toLong() == descriptor.length && DeckLibraryRequests.sha256(blob.copyBytes()) == descriptor.sha256)
        return blob
    }
    fun raw(body: DeckDraftBody, bytes: SnapshotBytes): DeckDraftFrames.Raw = DeckDraftFrames.Raw(body.baseRevision.value,payload(bytes))
    fun payload(bytes: SnapshotBytes) = DeckDraftFrames.Payload(1,bytes.size.toLong(),DeckLibraryRequests.sha256(bytes.copyBytes()))
    suspend fun writeSlot(tx: DbTransaction, old: Slot, next: Slot, bytes: SnapshotBytes?, cancel: CancelToken) = with(store) {
        val params = keys(next.id) + arrayOf("generation" to number(next.generation),"base" to sql(next.raw?.baseRevision),"version" to sql(next.raw?.payload?.version?.toLong()),
            "length" to sql(next.raw?.payload?.length),"sha" to sql(next.raw?.payload?.sha256),"raw" to (bytes?.let { SqlValue.Blob(it) } ?: SqlValue.Null),"latest" to sql(next.latest?.value),
            "kind" to text(next.mutation.sql),"mutation" to text(next.mutationSha),"state" to text(DeckDraftFrames.slot(next.id,next.generation,next.raw,next.latest,next.mutation,next.mutationSha,cancel)))
        val columns = "provider,deck_id,metadata_version,generation,raw_base_revision,raw_version,raw_length,raw_sha256,raw,latest_operation_id,last_mutation_kind,last_mutation_sha256,state_sha256"
        if (!old.exists) write(tx,request("INSERT INTO deck_draft_state ($columns) VALUES (:provider,:id,1,:generation,:base,:version,:length,:sha,:raw,:latest,:kind,:mutation,:state)",*params),cancel)
        else write(tx,request("UPDATE deck_draft_state SET generation=:generation,raw_base_revision=:base,raw_version=:version,raw_length=:length,raw_sha256=:sha,raw=:raw,latest_operation_id=:latest,last_mutation_kind=:kind,last_mutation_sha256=:mutation,state_sha256=:state WHERE provider=:provider AND deck_id=:id AND generation=:old_generation AND latest_operation_id IS :old_latest",*params,"old_generation" to number(old.generation),"old_latest" to sql(old.latest?.value)),cancel)
    }
    suspend fun insert(tx: DbTransaction, intent: Intent, raw: SnapshotBytes, patch: SnapshotBytes, cancel: CancelToken) = with(store) {
        write(tx,request("INSERT INTO deck_draft_intent (operation_id,provider,deck_id,metadata_version,source_generation,source_base_revision,raw_version,raw_length,raw_sha256,raw,scope,patch_version,patch_length,patch_sha256,patch,request_version,request_sha256,status,state_sha256) VALUES (:operation,:provider,:id,1,:generation,:base,1,:raw_length,:raw_sha,:raw,:scope,1,:patch_length,:patch_sha,:patch,1,:request,'PREPARED',:state)",
            *keys(intent.source.deckId),"operation" to text(intent.operation.value),"generation" to number(intent.source.generation),"base" to number(intent.source.baseRevision.value),
            "raw_length" to number(raw.size.toLong()),"raw_sha" to text(intent.raw.payload.sha256),"raw" to SqlValue.Blob(raw),"scope" to text(intent.scope.name),
            "patch_length" to number(patch.size.toLong()),"patch_sha" to text(intent.patch.sha256),"patch" to SqlValue.Blob(patch),"request" to text(intent.requestSha),"state" to text(stateSha(intent,cancel))),cancel)
    }
    fun stateSha(intent: Intent,cancel:CancelToken) = DeckDraftFrames.intent(intent.source.deckId,intent.operation,intent.source.generation,intent.raw,intent.scope,intent.patch,intent.requestSha,intent.status,cancel)
    suspend fun complete(tx:DbTransaction,intent:Intent,cancel:CancelToken) = with(store) {
        val committed = intent.status as? DeckDraftFrames.Status.Committed
        val effect = committed?.effect
        val generation = when(effect) { is DeckDraftSlotEffect.Cleared -> effect.resultGeneration; is DeckDraftSlotEffect.Residual -> effect.token.generation; is DeckDraftSlotEffect.PreservedLater -> effect.observedToken.generation; null -> null }
        val token = when(effect) { is DeckDraftSlotEffect.Residual -> effect.token; is DeckDraftSlotEffect.PreservedLater -> effect.observedToken; else -> null }
        val effectKind = when(effect) { is DeckDraftSlotEffect.Cleared -> "CLEARED"; is DeckDraftSlotEffect.Residual -> "RESIDUAL"; is DeckDraftSlotEffect.PreservedLater -> "PRESERVED_LATER"; null -> null }
        write(tx,request("UPDATE deck_draft_intent SET status=:status,receipt_sequence=:receipt,result_revision=:result,effect_kind=:effect,effect_generation=:generation,effect_base_revision=:base,effect_raw_sha256=:sha,state_sha256=:state WHERE operation_id=:operation AND status='PREPARED'",
            "operation" to text(intent.operation.value),"status" to text(if(committed == null) "ABANDONED" else "COMMITTED"),"receipt" to sql(committed?.receiptSequence),"result" to sql(committed?.resultRevision),
            "effect" to sql(effectKind),"generation" to sql(generation),"base" to sql(token?.baseRevision?.value),"sha" to sql(token?.rawSha256),"state" to text(stateSha(intent,cancel))),cancel)
    }
    private fun sql(value:Long?):SqlValue = value?.let(::number) ?: SqlValue.Null
    private fun sql(value:String?):SqlValue = value?.let(::text) ?: SqlValue.Null
}
