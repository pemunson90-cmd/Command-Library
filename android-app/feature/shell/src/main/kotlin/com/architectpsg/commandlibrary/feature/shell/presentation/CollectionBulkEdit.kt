package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*

/** Keep is an instruction, never a mixed-value placeholder that can become a write. */
internal sealed interface BulkValue<out T> {
    data object Keep : BulkValue<Nothing>
    data class Set<T>(val value: T) : BulkValue<T>
}
internal data class CollectionBulkDraft(
    val quantity: BulkValue<String> = BulkValue.Keep,
    val condition: BulkValue<CardCondition> = BulkValue.Keep,
    val location: BulkValue<String> = BulkValue.Keep,
    val finish: BulkValue<Finish> = BulkValue.Keep,
) {
    val changes: Boolean get() = quantity is BulkValue.Set || condition is BulkValue.Set || location is BulkValue.Set || finish is BulkValue.Set
}
internal data class CollectionBulkForm(val base: InventorySnapshot, val ids: SnapshotList<EntityId>, val originals: SnapshotList<OwnedLot?>, val draft: CollectionBulkDraft = CollectionBulkDraft())
internal enum class CollectionBulkIssue { MISSING, QUANTITY, PRINTING, CATALOG, FINISH }
internal data class CollectionBulkRow(
    val id: EntityId, val before: OwnedLot?, val after: OwnedLot?,
    val issues: SnapshotList<CollectionBulkIssue> = SnapshotList.empty(),
    val catalogProblem: Problem? = null,
    val structuralIssues: SnapshotList<DomainIssue> = SnapshotList.empty(),
) { val blocked: Boolean get() = issues.isNotEmpty() || catalogProblem != null || structuralIssues.isNotEmpty() }
internal data class CollectionBulkPreview(
    val form: CollectionBulkForm, val rows: SnapshotList<CollectionBulkRow>,
    val patch: CollectionPatch?, val projection: InventoryProjection?, val generation: GenerationId?,
) { val blocked: Boolean get() = rows.isEmpty() || rows.any { it.blocked } || projection == null || projection.issues.isNotEmpty() }

/** Exact replacement of requested fields only; identities, evidence and lot revisions remain intact. */
internal fun bulkRow(form: CollectionBulkForm, id: EntityId, originals: Map<EntityId, OwnedLot>, records: Map<CardId, PortResult<PrintingRecord>>, generation: GenerationId?): CollectionBulkRow {
    val before = originals[id] ?: return CollectionBulkRow(id,null,null,SnapshotList.of(CollectionBulkIssue.MISSING))
    val issues = mutableListOf<CollectionBulkIssue>()
    val draft = form.draft
    val quantity = when (val value = draft.quantity) {
        BulkValue.Keep -> before.quantity
        is BulkValue.Set -> value.value.takeIf { it.length <= 10 }?.toIntOrNull()?.takeIf { it >= 0 }
    }
    if (quantity == null) issues += CollectionBulkIssue.QUANTITY
    var catalogProblem: Problem? = null
    if (draft.finish is BulkValue.Set) {
        val idValue = (before.identity.printingId as? Knowledge.Known)?.value
        if (idValue == null) issues += CollectionBulkIssue.PRINTING
        else if (generation == null) catalogProblem = Problem.STALE_GENERATION
        else when (val result = records[idValue]) {
            is PortResult.Failed -> catalogProblem = result.problem
            is PortResult.Ok -> {
                val record = result.value
                val knownOracle = (before.identity.oracleId as? Knowledge.Known)?.value
                val returnedOracle = (record.oracleId as? Knowledge.Known)?.value
                if (record.id != idValue || record.language != before.identity.language ||
                    (knownOracle != null && returnedOracle != null && knownOracle != returnedOracle) ||
                    record.provenance.provider != idValue.source || record.provenance.generation != generation ||
                    (record.provenance.notice?.provider?.let { it != idValue.source } == true)) issues += CollectionBulkIssue.CATALOG
                val finishes = (record.finishes as? Knowledge.Known)?.value
                if (finishes == null || finishes.size > Finish.entries.size || finishes.distinct().size != finishes.size || draft.finish.value !in finishes) issues += CollectionBulkIssue.FINISH
            }
            null -> issues += CollectionBulkIssue.CATALOG
        }
    }
    val after = before.copy(quantity = quantity ?: before.quantity,
        condition = (draft.condition as? BulkValue.Set)?.value ?: before.condition,
        location = (draft.location as? BulkValue.Set)?.value ?: before.location,
        finish = (draft.finish as? BulkValue.Set)?.value?.let { Knowledge.Known(it) } ?: before.finish)
    return CollectionBulkRow(id,before,after,SnapshotList.copyOf(issues),catalogProblem)
}
