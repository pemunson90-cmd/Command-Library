package com.architectpsg.commandlibrary.feature.pricing

import com.architectpsg.commandlibrary.core.model.*

/** Pure, stateless allocation: each invocation independently uses its inventory snapshot. */
public class LocalOwnedQuantityAllocator public constructor() : OwnedQuantityAllocator {
    public override fun allocate(deck: DeckSnapshot, inventory: InventorySnapshot, policy: OwnedQuantityPolicy, cancel: CancelToken): Outcome<OwnedQuantityPlan> {
        val work = QuantityWork(cancel)
        work.phase()
        return try {
            val plan = allocateChecked(deck, inventory, policy, work)
            work.phase()
            Outcome.Success(plan)
        } catch (invalid: InvalidQuantity) {
            Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT, invalid.message!!, SnapshotList.empty()))
        }
    }

    private fun allocateChecked(deck: DeckSnapshot, inventory: InventorySnapshot, policy: OwnedQuantityPolicy, work: QuantityWork): OwnedQuantityPlan {
        if (deck.entries.size > OwnedQuantityLimits.MAX_ENTRIES) throw InvalidQuantity("Owned quantity entry budget exceeded")
        if (policy.includeBoards.size > BoardRole.entries.size || policy.pins.size > OwnedQuantityLimits.MAX_ENTRIES) throw InvalidQuantity("Invalid owned quantity policy size")
        val ids = HashMap<EntityId, ByteArray>()
        val order = Comparator<EntityId> { left, right ->
            work.tick()
            val a = ids.getValue(left); val b = ids.getValue(right)
            var result = 0; var index = 0
            while (index < minOf(a.size, b.size) && result == 0) {
                work.tick(); result = (a[index].toInt() and 255).compareTo(b[index].toInt() and 255); index++
            }
            if (result == 0) a.size.compareTo(b.size) else result
        }
        fun validateId(id: EntityId) { ids[id] = checkedBytes(id.value, work) }
        checkedBytes(deck.id.source.value, work); checkedBytes(deck.id.raw, work)
        val boards = HashSet<BoardRole>()
        for (board in policy.includeBoards) { work.tick(); if (!boards.add(board)) throw InvalidQuantity("Duplicate owned quantity board selector") }
        val entriesById = HashMap<EntityId, DeckEntry>()
        val included = ArrayList<DeckEntry>()
        for (entry in deck.entries) {
            work.tick(); validateId(entry.id); validateIdentity(entry.identity, work)
            if (entriesById.put(entry.id, entry) != null) throw InvalidQuantity("Duplicate owned quantity entry ID")
            if (entry.board in boards) included.add(entry)
        }
        work.phase()
        val pins = HashMap<EntityId, PrintingPin>()
        for (pin in policy.pins) {
            work.tick(); validateId(pin.entryId)
            checkedBytes(pin.printingId.source.value, work); checkedBytes(pin.printingId.raw, work)
            if (pins.put(pin.entryId, pin) != null) throw InvalidQuantity("Duplicate owned quantity pin")
            val entry = entriesById[pin.entryId]
            val entryFinish = entry?.finish
            if (entry == null || entry.board !in boards || (entry.identity.printingId as? Knowledge.Known)?.value != pin.printingId ||
                (entryFinish is Knowledge.Known && entryFinish.value != pin.finish)) throw InvalidQuantity("Invalid owned quantity printing pin")
        }
        val canonicalPolicy = policy.copy(
            includeBoards = SnapshotList.copyOf(boards.sortedBy { work.tick(); it.name }),
            pins = snapshot(scanning(pins.values, work).sortedWith { a, b -> order.compare(a.entryId, b.entryId) }, work),
        )
        val outputEntries = scanning(included, work).sortedWith { a, b -> order.compare(a.id, b.id) }
        // IGNORE must return before even reading inventory.lots.size or any lot field.
        if (policy.ownership == OwnershipPolicy.IGNORE) {
            val demands = ArrayList<OwnedQuantityDemand>()
            work.phase()
            for (entry in outputEntries) { work.tick(); demands.add(OwnedQuantityDemand(entry.id, entry.quantity, 0, entry.quantity, true)) }
            work.phase()
            return OwnedQuantityPlan(deck.id, deck.revision, inventory.revision, canonicalPolicy, SnapshotList.empty(), snapshot(demands, work), SnapshotList.empty(), true)
        }
        if (inventory.lots.size > OwnedQuantityLimits.MAX_LOTS) throw InvalidQuantity("Owned quantity lot budget exceeded")
        val lotIds = HashSet<EntityId>()
        val lots = ArrayList<OwnedLot>()
        val mapping = HashMap<CardId, OracleId>()
        val conflicting = HashSet<CardId>()
        fun assertMapping(identity: CardIdentity) {
            work.tick()
            val printing = (identity.printingId as? Knowledge.Known)?.value
            val oracle = (identity.oracleId as? Knowledge.Known)?.value
            if (printing != null && oracle != null) {
                val previous = mapping.putIfAbsent(printing, oracle)
                if (previous != null && previous != oracle) conflicting.add(printing)
            }
        }
        for (entry in included) assertMapping(entry.identity)
        for (lot in inventory.lots) {
            work.tick(); validateId(lot.id); validateIdentity(lot.identity, work)
            if (!lotIds.add(lot.id)) throw InvalidQuantity("Duplicate owned quantity lot ID")
            if (lot.quantity > 0) { lots.add(lot); assertMapping(lot.identity) }
        }
        work.phase()
        val orderedLots = scanning(lots, work).sortedWith { a, b -> order.compare(a.id, b.id) }
        fun pinRank(entry: DeckEntry): Int = if (entry.id in pins) 0 else if (policy.ownership == OwnershipPolicy.EXACT_PRINTING) 1 else 2
        fun finishRank(entry: DeckEntry): Int = if (entry.id in pins) 0 else when (entry.finish) { is Knowledge.Known -> 0; is Knowledge.Absent -> 1; is Knowledge.Unknown -> 2 }
        val orderedEntries = scanning(included, work).sortedWith { a, b ->
            work.tick()
            val rank = pinRank(a).compareTo(pinRank(b))
            if (rank != 0) rank else {
                val finish = finishRank(a).compareTo(finishRank(b))
                if (finish != 0) finish else order.compare(a.id, b.id)
            }
        }
        val source = orderedEntries.size + orderedLots.size
        val sink = source + 1
        val flow = QuantityFlow(sink + 1, work)
        for ((index, entry) in orderedEntries.withIndex()) { work.tick(); flow.add(source, index, entry.quantity.toLong()) }
        for ((index, lot) in orderedLots.withIndex()) { work.tick(); flow.add(orderedEntries.size + index, sink, lot.quantity.toLong()) }
        val uncertain = HashSet<EntityId>()
        data class Candidate(val entry: DeckEntry, val lot: OwnedLot, val edge: QuantityFlow.Edge)
        val candidates = ArrayList<Candidate>()
        var comparisons = 0L
        for ((entryIndex, entry) in orderedEntries.withIndex()) {
            work.tick()
            for ((lotIndex, lot) in orderedLots.withIndex()) {
                work.tick()
                if (++comparisons > OwnedQuantityLimits.MAX_CANDIDATE_COMPARISONS) throw InvalidQuantity("Owned quantity candidate comparison budget exceeded")
                when (ownershipCompatibility(entry, lot, pins[entry.id], policy.ownership, conflicting)) {
                    OwnershipCompatibility.INDETERMINATE -> uncertain.add(entry.id)
                    OwnershipCompatibility.INELIGIBLE -> Unit
                    OwnershipCompatibility.ELIGIBLE -> {
                        if (candidates.size >= OwnedQuantityLimits.MAX_COMPATIBILITY_EDGES) throw InvalidQuantity("Owned quantity compatibility edge budget exceeded")
                        candidates.add(Candidate(entry, lot, flow.add(entryIndex, orderedEntries.size + lotIndex, minOf(entry.quantity, lot.quantity).toLong())))
                    }
                }
            }
        }
        work.phase(); flow.maximize(source, sink); work.phase()
        val allocations = ArrayList<OwnedAllocation>()
        val totals = HashMap<EntityId, Long>()
        for (candidate in candidates) {
            work.tick()
            val quantity = candidate.edge.initial - candidate.edge.capacity
            if (quantity > 0) {
                if (allocations.size >= OwnedQuantityLimits.MAX_ALLOCATIONS) throw InvalidQuantity("Owned quantity allocation budget exceeded")
                // An edge is bounded by both original Int capacities; row totals use Long.
                allocations.add(OwnedAllocation(candidate.lot.id, candidate.entry.id, quantity.toInt()))
                totals[candidate.entry.id] = (totals[candidate.entry.id] ?: 0L) + quantity
            }
        }
        val demands = ArrayList<OwnedQuantityDemand>()
        val incomplete = ArrayList<EntityId>()
        for (entry in outputEntries) {
            work.tick()
            val owned = (totals[entry.id] ?: 0L).toInt()
            val complete = owned == entry.quantity || (inventory.complete && entry.id !in uncertain)
            demands.add(OwnedQuantityDemand(entry.id, entry.quantity, owned, entry.quantity - owned, complete))
            if (!complete) incomplete.add(entry.id)
        }
        allocations.sortWith { a, b ->
            val entry = order.compare(a.entryId, b.entryId)
            if (entry != 0) entry else order.compare(a.lotId, b.lotId)
        }
        work.phase()
        return OwnedQuantityPlan(deck.id, deck.revision, inventory.revision, canonicalPolicy, snapshot(allocations, work), snapshot(demands, work), snapshot(incomplete, work), incomplete.isEmpty())
    }

    /** Library copies must also participate in cancellation, including final immutable snapshots. */
    private fun <T> scanning(values: Iterable<T>, work: QuantityWork): Iterable<T> = Iterable {
        val iterator = values.iterator()
        object : Iterator<T> {
            override fun hasNext(): Boolean = iterator.hasNext()
            override fun next(): T { work.tick(); return iterator.next() }
        }
    }

    private fun <T> snapshot(values: Iterable<T>, work: QuantityWork): SnapshotList<T> = SnapshotList.copyOf(scanning(values, work))

    private fun validateIdentity(identity: CardIdentity, work: QuantityWork) {
        work.tick(); checkedBytes(identity.language.value, work)
        (identity.printingId as? Knowledge.Known)?.value?.let { checkedBytes(it.source.value, work); checkedBytes(it.raw, work) }
        (identity.oracleId as? Knowledge.Known)?.value?.let { checkedBytes(it.source.value, work); checkedBytes(it.raw, work) }
        identity.raw?.let { checkedBytes(it.provider.value, work); checkedBytes(it.kind, work); checkedBytes(it.rawId, work) }
    }

    /** Reject malformed UTF-16 rather than silently replacing it while encoding UTF-8. */
    private fun checkedBytes(value: String, work: QuantityWork): ByteArray {
        work.tick()
        if (value.length > OwnedQuantityLimits.MAX_ID_BYTES) throw InvalidQuantity("Owned quantity identifier byte budget exceeded")
        var bytes = 0; var index = 0
        while (index < value.length) {
            work.tick()
            val char = value[index++]
            bytes += when {
                char.code < 0x80 -> 1
                char.code < 0x800 -> 2
                char.isHighSurrogate() -> {
                    if (index >= value.length || !value[index].isLowSurrogate()) throw InvalidQuantity("Invalid owned quantity identifier Unicode")
                    work.tick(); index++; 4
                }
                char.isLowSurrogate() -> throw InvalidQuantity("Invalid owned quantity identifier Unicode")
                else -> 3
            }
            if (bytes > OwnedQuantityLimits.MAX_ID_BYTES) throw InvalidQuantity("Owned quantity identifier byte budget exceeded")
        }
        return value.toByteArray(Charsets.UTF_8)
    }
}
