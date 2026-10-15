package com.architectpsg.commandlibrary.feature.decks

import com.architectpsg.commandlibrary.core.model.*

/**
 * Whole-deck structural graph validity, including original package contributions.
 * This is not a wire representability certificate: callers still require the complete CLDK codec.
 * Preflight bounds every list/string used by the reducer or graph maps before those operations.
 * The unchanged reducer is a bounded, non-cancellable substep; no polling latency is promised there.
 */
public object DeckSnapshotValidator {
    public fun validate(deck: DeckSnapshot, cancel: CancelToken): Outcome<Unit> {
        cancel.throwIfCancelled()
        return try {
            Preflight(cancel).visit(deck)
            cancel.throwIfCancelled()
            // Only this temporary base-validation input omits packages. The original is never
            // changed or returned, and its package graph is validated separately below.
            val base = DeckPatchReducer.propose(
                deck.copy(packageInstances = SnapshotList.empty()),
                DeckPatch(SnapshotList.empty(), ""), deck.revision, deck.modifiedAt,
            )
            cancel.throwIfCancelled()
            if (base !is Outcome.Success) invalid()

            val entries = HashMap<EntityId, Int>(deck.entries.size)
            for (entry in deck.entries) {
                cancel.throwIfCancelled()
                entries[entry.id] = entry.quantity
            }
            val instances = HashSet<EntityId>()
            val totals = HashMap<EntityId, Long>()
            for (instance in deck.packageInstances) {
                cancel.throwIfCancelled()
                if (!instances.add(instance.id)) invalid()
                val pairs = HashSet<Pair<EntityId, EntityId>>()
                for (contribution in instance.contributions) {
                    cancel.throwIfCancelled()
                    if (contribution.quantity <= 0 || !pairs.add(contribution.packageEntryId to contribution.deckEntryId)) invalid()
                    val available = entries[contribution.deckEntryId]?.toLong() ?: invalid()
                    val previous = totals[contribution.deckEntryId] ?: 0L
                    val quantity = contribution.quantity.toLong()
                    // Subtraction is safe: previous is always nonnegative and <= available.
                    if (quantity > available - previous) invalid()
                    totals[contribution.deckEntryId] = previous + quantity
                }
            }
            cancel.throwIfCancelled()
            Outcome.Success(Unit)
        } catch (_: InvalidGraph) {
            Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT, "Invalid deck structural graph.", SnapshotList.empty()))
        }
    }

    private class Preflight(private val cancel: CancelToken) {
        private var elements = 0L
        private var examinedUtf16 = 0L

        fun visit(deck: DeckSnapshot) {
            // Complete this phase before any reducer equality, whitespace scan or hash/map work.
            list(deck.entries.size, DeckPayloadLimits.MAX_ENTRIES)
            list(deck.categoryOrder?.size ?: 0, DeckPayloadLimits.MAX_CATEGORIES)
            list(deck.packageInstances.size)
            id(deck.id.source.value); id(deck.id.raw); name(deck.name)
            for (category in deck.categoryOrder ?: emptyList()) {
                cancel.throwIfCancelled()
                id(category.id.value); name(category.name)
            }
            for (entry in deck.entries) {
                cancel.throwIfCancelled()
                id(entry.id.value)
                list(entry.labels.size); list(entry.roleOverrides.size)
                for (label in entry.labels) {
                    cancel.throwIfCancelled()
                    id(label.id.value); name(label.name)
                }
                for (override in entry.roleOverrides) {
                    cancel.throwIfCancelled()
                    override.entryId?.let { id(it.value) }
                }
            }
            for (instance in deck.packageInstances) {
                cancel.throwIfCancelled()
                id(instance.id.value); id(instance.packageId.value); id(instance.versionId.value)
                list(instance.contributions.size)
                for (contribution in instance.contributions) {
                    cancel.throwIfCancelled()
                    id(contribution.packageEntryId.value); id(contribution.deckEntryId.value)
                }
            }
            cancel.throwIfCancelled()
        }

        private fun list(size: Int, maximum: Int = DeckPayloadLimits.MAX_LIST_ITEMS) {
            cancel.throwIfCancelled()
            if (size > maximum || size.toLong() > DeckPayloadLimits.MAX_COLLECTION_ELEMENTS - elements) invalid()
            elements += size
        }

        private fun id(value: String) = text(value, DeckPayloadLimits.MAX_ID_BYTES, true)
        private fun name(value: String) = text(value, DeckPayloadLimits.MAX_TEXT_BYTES, false)

        /** Strict UTF8 sizing without allocating an encoded string; polls during long scans. */
        private fun text(value: String, maximum: Int, identifier: Boolean) {
            cancel.throwIfCancelled()
            if (value.length > maximum || value.length.toLong() > DeckPayloadLimits.MAX_ENCODED_BYTES - examinedUtf16) invalid()
            examinedUtf16 += value.length
            var index = 0
            var bytes = 0L
            var nonblank = false
            var nextCheck = 0
            while (index < value.length) {
                if (index >= nextCheck) {
                    cancel.throwIfCancelled()
                    nextCheck = index + 1_024
                }
                val character = value[index]
                if (!character.isWhitespace()) nonblank = true
                when {
                    character.isHighSurrogate() -> {
                        if (index + 1 >= value.length || !value[index + 1].isLowSurrogate()) invalid()
                        bytes += 4; index += 2
                    }
                    character.isLowSurrogate() -> invalid()
                    character.code <= 0x7f -> { bytes++; index++ }
                    character.code <= 0x7ff -> { bytes += 2; index++ }
                    else -> { bytes += 3; index++ }
                }
                if (bytes > maximum) invalid()
            }
            if (identifier && !nonblank) invalid()
            cancel.throwIfCancelled()
        }
    }

    private class InvalidGraph : RuntimeException()
    private fun invalid(): Nothing = throw InvalidGraph()
}
