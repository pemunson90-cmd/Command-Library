package com.architectpsg.commandlibrary.feature.collection

import com.architectpsg.commandlibrary.core.model.*
import java.util.TreeMap
import java.util.TreeSet

/** Structural, pure proposal. Catalog finish eligibility and durable write authority belong to callers. */
public object InventoryPatchReducer {
    public fun propose(inventory: InventorySnapshot, patch: CollectionPatch, expected: Revision, cancel: CancelToken): Outcome<InventoryProjection> {
        val work = InventoryProjectionWork(cancel)
        try {
            work.phase()
            if (expected.value != inventory.revision.value) work.fail(FailureCode.STALE_REVISION, "Inventory revision changed.")
            if (!inventory.complete) work.reject("A complete inventory snapshot is required.")
            if (inventory.lots.size > InventoryProjectionLimits.MAX_LOTS || patch.edits.size > InventoryProjectionLimits.MAX_EDITS) work.reject("Inventory input count limit exceeded.")
            work.items(inventory.lots.size); work.items(patch.edits.size); work.timestamp(inventory.capturedAt)
            for (lot in inventory.lots) {
                work.spend(); work.lot(lot)
                if (lot.revision.value > inventory.revision.value) work.reject("Stored lot revision exceeds inventory revision.")
            }
            for (edit in patch.edits) {
                work.spend()
                when (edit) {
                    is CollectionEdit.Add -> work.lot(edit.lot)
                    is CollectionEdit.Replace -> work.lot(edit.lot)
                    is CollectionEdit.Adjust -> { work.text(edit.lotId.value, true); work.text(edit.reason); work.spend() }
                    is CollectionEdit.Remove -> { work.text(edit.lotId.value, true); work.text(edit.reason) }
                }
            }
            work.phase()
            val byId = TreeMap<String, OwnedLot>(Comparator(work::compare))
            for (lot in inventory.lots) {
                work.spend()
                if (byId.put(lot.id.value, lot) != null) work.reject("Stored inventory lot identifiers must be unique.")
            }
            if (patch.edits.isEmpty()) {
                work.phase()
                return Outcome.Success(InventoryProjection(inventory.revision, inventory.revision, SnapshotList.empty(), SnapshotList.empty(), SnapshotList.empty()))
            }
            if (inventory.revision.value == Long.MAX_VALUE) work.fail(FailureCode.PERSISTENCE, "Inventory revision is exhausted.")
            val resultRevision = Revision(inventory.revision.value + 1)
            fun id(edit: CollectionEdit): EntityId = when (edit) {
                is CollectionEdit.Add -> edit.lot.id
                is CollectionEdit.Replace -> edit.lot.id
                is CollectionEdit.Adjust -> edit.lotId
                is CollectionEdit.Remove -> edit.lotId
            }
            val issues = TreeMap<EntityId, TreeSet<String>>(Comparator { a, b -> work.compare(a.value, b.value) })
            var issueCount = 0
            fun issue(lotId: EntityId, code: String) {
                work.spend()
                var codes = issues[lotId]
                if (codes == null) { codes = TreeSet(Comparator(work::compare)); issues[lotId] = codes }
                if (codes.add(code) && ++issueCount > InventoryProjectionLimits.MAX_ISSUES) work.reject("Inventory issue limit exceeded.")
            }
            val selected = TreeMap<String, CollectionEdit>(Comparator(work::compare))
            val selectedNonAdd = TreeSet<String>(Comparator(work::compare))
            val duplicated = TreeSet<String>(Comparator(work::compare))
            val edits = work.sorted(patch.edits, Comparator { a, b -> work.compare(id(a).value, id(b).value) })
            for (edit in edits) {
                work.spend(); val key = id(edit)
                if (selected.put(key.value, edit) != null) { duplicated.add(key.value); issue(key, InventoryIssueCodes.DUPLICATE_SELECTION) }
                if (edit !is CollectionEdit.Add) selectedNonAdd.add(key.value)
            }
            val adds = ArrayList<OwnedLot>(patch.edits.size)
            val changes = ArrayList<InventoryLotChange>(patch.edits.size)
            val merges = ArrayList<InventoryMerge>(patch.edits.size)
            for (edit in edits) {
                work.spend(); val key = id(edit); val before = byId[key.value]
                when (edit) {
                    is CollectionEdit.Add -> {
                        if (before != null) issue(key, InventoryIssueCodes.LOT_ALREADY_EXISTS)
                        if (edit.lot.revision.value != 0L) issue(key, InventoryIssueCodes.INVALID_ADD_REVISION)
                        if (before == null && edit.lot.revision.value == 0L && !duplicated.contains(key.value)) { work.spend(); adds.add(edit.lot) }
                    }
                    is CollectionEdit.Replace -> {
                        if (before == null) issue(key, InventoryIssueCodes.LOT_NOT_FOUND)
                        else if (edit.lot.revision.value != before.revision.value) issue(key, InventoryIssueCodes.STALE_LOT)
                        else { work.spend(2); changes.add(InventoryLotChange(key, before, edit.lot.copy(revision = resultRevision))) }
                    }
                    is CollectionEdit.Adjust -> {
                        if (before == null) issue(key, InventoryIssueCodes.LOT_NOT_FOUND)
                        else {
                            work.spend(); val quantity = before.quantity.toLong() + edit.delta.toLong()
                            if (quantity !in 0L..Int.MAX_VALUE.toLong()) issue(key, InventoryIssueCodes.QUANTITY_RANGE)
                            else { work.spend(2); changes.add(InventoryLotChange(key, before, before.copy(quantity = quantity.toInt(), revision = resultRevision))) }
                        }
                    }
                    is CollectionEdit.Remove -> {
                        if (before == null) issue(key, InventoryIssueCodes.LOT_NOT_FOUND)
                        else { work.spend(); changes.add(InventoryLotChange(key, before, null)) }
                    }
                }
            }
            work.phase()
            if (adds.isNotEmpty()) {
                val index = InventoryProjectionIndex(work)
                for (lot in inventory.lots) { work.spend(); index.original(lot) }
                // Edits are already canonical: the first incoming row is the minimum new ID.
                for (lot in adds) { work.spend(); index.add(lot) }
                work.phase()
                for (group in index.touched) {
                    work.spend(); val original = group.original
                    val survivor = original ?: group.incoming.first()
                    val overlap = original != null && selectedNonAdd.contains(original.id.value)
                    if (overlap) {
                        issue(survivor.id, InventoryIssueCodes.MERGE_TARGET_SELECTED)
                        for (lot in group.incoming) { work.spend(); issue(lot.id, InventoryIssueCodes.MERGE_TARGET_SELECTED) }
                    }
                    val overflow = group.quantity > Int.MAX_VALUE.toLong()
                    if (overflow) for (lot in group.incoming) { work.spend(); issue(lot.id, InventoryIssueCodes.QUANTITY_OVERFLOW) }
                    if (!overlap && !overflow) {
                        work.spend(2); changes.add(InventoryLotChange(survivor.id, original, survivor.copy(quantity = group.quantity.toInt(), revision = resultRevision)))
                        for (lot in group.incoming) {
                            work.spend()
                            if (original != null || !work.same(lot.id.value, survivor.id.value)) { work.spend(); merges.add(InventoryMerge(lot.id, survivor.id, lot.quantity)) }
                        }
                    }
                }
            }
            work.phase()
            if (issueCount != 0) {
                val rows = ArrayList<DomainIssue>(issueCount)
                for ((key, codes) in issues) for (code in codes) {
                    work.spend(2)
                    rows.add(DomainIssue(code, IssueSeverity.ERROR, message(code), key, SnapshotList.empty()))
                }
                val frozenIssues = work.freeze(rows)
                work.phase()
                return Outcome.Success(InventoryProjection(inventory.revision, resultRevision, SnapshotList.empty(), SnapshotList.empty(), frozenIssues))
            }
            val orderedChanges = work.sorted(changes, Comparator { a, b -> work.compare(a.lotId.value, b.lotId.value) })
            val orderedMerges = work.sorted(merges, Comparator { a, b -> work.compare(a.requestedLotId.value, b.requestedLotId.value) })
            val frozenChanges = work.freeze(orderedChanges); val frozenMerges = work.freeze(orderedMerges)
            work.phase()
            return Outcome.Success(InventoryProjection(inventory.revision, resultRevision, frozenChanges, frozenMerges, SnapshotList.empty()))
        } catch (failure: InventoryProjectionWork.Rejected) {
            return Outcome.Failure(DomainFailure(failure.code, failure.message, SnapshotList.empty()))
        }
    }
    private fun message(code: String): String = when (code) {
        InventoryIssueCodes.DUPLICATE_SELECTION -> "A lot is selected more than once."
        InventoryIssueCodes.LOT_ALREADY_EXISTS -> "The requested lot identifier already exists."
        InventoryIssueCodes.LOT_NOT_FOUND -> "The selected lot does not exist."
        InventoryIssueCodes.STALE_LOT -> "The selected lot revision changed."
        InventoryIssueCodes.INVALID_ADD_REVISION -> "A new lot must have revision zero."
        InventoryIssueCodes.QUANTITY_RANGE -> "The resulting quantity is outside the allowed range."
        InventoryIssueCodes.MERGE_TARGET_SELECTED -> "The original merge survivor is also selected for editing."
        InventoryIssueCodes.QUANTITY_OVERFLOW -> "The merged quantity exceeds the allowed range."
        else -> error("Unknown inventory issue code")
    }
}
