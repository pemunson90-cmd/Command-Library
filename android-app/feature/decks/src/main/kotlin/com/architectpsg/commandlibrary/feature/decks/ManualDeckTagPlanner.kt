package com.architectpsg.commandlibrary.feature.decks

import com.architectpsg.commandlibrary.core.model.*
import java.text.Normalizer
import java.util.Locale

/** Pure manual membership proposals; persistence, CAS, acknowledgement and grouped undo belong to the caller. */
public object ManualDeckTagPlanner {
    public fun assign(deck: DeckSnapshot, pool: PersonalTagPool, entryIds: SnapshotList<EntityId>,
        tagIds: SnapshotList<EntityId>): Outcome<DeckPatch> = plan {
        current(deck)
        val selected = selection(deck, entryIds, tagIds)
        requireValid(pool.tags.size <= PersonalTagLimits.MAX_TAGS)
        requireValid(if (pool.revision.value == 0L) pool.tags.isEmpty() && pool.modifiedAt == null
            else pool.modifiedAt != null)
        val tags = linkedMapOf<EntityId, PersonalTag>()
        val activeNames = hashSetOf<String>()
        for (tag in pool.tags) {
            identifier(tag.id); name(tag.name)
            requireValid(tags.put(tag.id, tag) == null)
            if (!tag.retired) requireValid(activeNames.add(Normalizer.normalize(tag.name,
                Normalizer.Form.NFKC).trim().lowercase(Locale.ROOT)))
        }
        val definitions = definitions(deck)
        for (id in tagIds) {
            val tag = tags[id] ?: invalid()
            requireValid(!tag.retired)
            if (id !in definitions) definitions[id] = EntryLabel(id, tag.name)
        }
        requireValid(definitions.size <= DeckPayloadLimits.MAX_CATEGORIES)
        val edits = mutableListOf<DeckEdit>()
        val categories = SnapshotList.copyOf(definitions.values)
        // Preserve null legacy state for a genuine no-op; materialize it when memberships change.
        val replacements = mutableListOf<DeckEdit>()
        for (entry in deck.entries) if (entry.id in selected) {
            val labels = entry.labels.toMutableList()
            val present = labels.mapTo(hashSetOf()) { it.id }
            for (id in tagIds) if (present.add(id)) labels += definitions.getValue(id)
            if (labels != entry.labels) {
                requireValid(replacements.size < 1_024 - (if (deck.categoryOrder != categories) 1 else 0))
                replacements += DeckEdit.Replace(entry.copy(labels = SnapshotList.copyOf(labels)))
            }
        }
        if (replacements.isNotEmpty()) {
            if (deck.categoryOrder != categories) edits += DeckEdit.Categories(categories)
            edits += replacements
        }
        finish(deck, edits)
    }

    public fun remove(deck: DeckSnapshot, entryIds: SnapshotList<EntityId>,
        tagIds: SnapshotList<EntityId>): Outcome<DeckPatch> = plan {
        current(deck)
        val selected = selection(deck, entryIds, tagIds)
        val definitions = definitions(deck)
        requireValid(tagIds.all { it in definitions })
        val removed = tagIds.toSet()
        val edits = deck.entries.filter { it.id in selected }.mapNotNull { entry ->
            val labels = entry.labels.filterNot { it.id in removed }
            if (labels == entry.labels) null else DeckEdit.Replace(entry.copy(labels = SnapshotList.copyOf(labels)))
        }.toMutableList<DeckEdit>()
        // Materialize legacy definitions so removing their last membership never retires them.
        if (edits.isNotEmpty() && deck.categoryOrder == null)
            edits.add(0, DeckEdit.Categories(SnapshotList.copyOf(definitions.values)))
        finish(deck, edits)
    }

    public fun categories(deck: DeckSnapshot, categories: SnapshotList<EntryLabel>): Outcome<DeckPatch> = plan {
        current(deck)
        requireValid(categories.size <= DeckPayloadLimits.MAX_CATEGORIES)
        val existing = definitions(deck)
        val ids = hashSetOf<EntityId>()
        for (category in categories) {
            identifier(category.id); name(category.name)
            requireValid(category.id in existing && ids.add(category.id))
        }
        finish(deck, if (categories == SnapshotList.copyOf(existing.values)) emptyList()
            else listOf(DeckEdit.Categories(categories)))
    }

    private val token = object : CancelToken {
        override val isCancelled: Boolean = false
        override fun throwIfCancelled() = Unit
    }
    private fun current(deck: DeckSnapshot) {
        accept(DeckSnapshotValidator.validate(deck, token))
        accept(DeckPatchReducer.propose(deck, DeckPatch(SnapshotList.empty(), ""), deck.revision, deck.modifiedAt))
    }
    private fun selection(deck: DeckSnapshot, entries: SnapshotList<EntityId>, tags: SnapshotList<EntityId>): Set<EntityId> {
        requireValid(entries.isNotEmpty() && entries.size <= deck.entries.size && tags.isNotEmpty() &&
            tags.size <= DeckPayloadLimits.MAX_CATEGORIES)
        entries.forEach(::identifier); tags.forEach(::identifier)
        val selected = entries.toSet()
        requireValid(selected.size == entries.size && tags.toSet().size == tags.size)
        val existing = deck.entries.mapTo(hashSetOf()) { it.id }
        requireValid(selected.all { it in existing })
        return selected
    }
    private fun definitions(deck: DeckSnapshot): LinkedHashMap<EntityId, EntryLabel> {
        val result = linkedMapOf<EntityId, EntryLabel>()
        for (label in deck.categoryOrder ?: deck.entries.flatMap { it.labels }) {
            val previous = result.putIfAbsent(label.id, label)
            requireValid(previous == null || previous == label)
        }
        return result
    }
    private fun finish(deck: DeckSnapshot, edits: List<DeckEdit>): DeckPatch {
        requireValid(edits.size <= 1_024)
        val patch = DeckPatch(SnapshotList.copyOf(edits), "Manual deck tags")
        // Validate the complete primitive proposal without returning its hypothetical revision.
        val candidate = DeckPatchReducer.propose(deck, patch, deck.revision, deck.modifiedAt)
        accept(candidate)
        if (candidate is Outcome.Success) accept(DeckSnapshotValidator.validate(candidate.value, token))
        return patch
    }
    private fun identifier(id: EntityId) = text(id.value, DeckPayloadLimits.MAX_ID_BYTES, false)
    private fun name(value: String) = text(value, PersonalTagLimits.MAX_NAME_BYTES, true)
    private fun text(value: String, maximum: Int, rejectControls: Boolean) {
        requireValid(value.length <= maximum && value.isNotBlank())
        var index = 0
        var bytes = 0
        while (index < value.length) {
            val c = value[index++]
            requireValid(!rejectControls || !c.isISOControl())
            bytes += when {
                c.isHighSurrogate() -> {
                    requireValid(index < value.length && value[index].isLowSurrogate()); index++; 4
                }
                c.isLowSurrogate() -> invalid()
                c.code <= 0x7f -> 1
                c.code <= 0x7ff -> 2
                else -> 3
            }
            requireValid(bytes <= maximum)
        }
    }
    private class Rejected(val result: Outcome<Nothing>) : RuntimeException()
    private fun invalid(): Nothing = throw Rejected(invalidDeckEdit("Invalid manual tag proposal"))
    private fun requireValid(condition: Boolean) { if (!condition) invalid() }
    private fun accept(result: Outcome<*>) {
        when (result) {
            is Outcome.Success -> Unit
            is Outcome.Failure -> throw Rejected(result)
            is Outcome.Unavailable -> throw Rejected(result)
            is Outcome.Unknown -> throw Rejected(result)
        }
    }
    private inline fun plan(block: () -> DeckPatch): Outcome<DeckPatch> = try {
        Outcome.Success(block())
    } catch (rejected: Rejected) { rejected.result }
}
