package com.architectpsg.commandlibrary.feature.decks

import com.architectpsg.commandlibrary.core.common.requireCurrentRevision
import com.architectpsg.commandlibrary.core.common.revisionForCommittedEdit
import com.architectpsg.commandlibrary.core.model.*

/** Pure candidate projection. The repository must still conditionally persist it in one transaction. */
public object DeckPatchReducer {
    public fun propose(
        deck: DeckSnapshot,
        patch: DeckPatch,
        expected: Revision,
        modifiedAt: UtcTimestamp,
    ): Outcome<DeckSnapshot> {
        val current = requireCurrentRevision(expected, deck.revision)
        if (current !is Outcome.Success) return currentFailure(current)
        if (deck.entries.size > MAX_ENTRIES || patch.edits.size > MAX_EDITS) return invalidDeckEdit("Deck or patch exceeds supported size")
        if (deck.packageInstances.isNotEmpty() || patch.edits.any {
                it is DeckEdit.RestoreVersion || it is DeckEdit.ApplyPackage
            }) return Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Package lineage and version restore require a contribution-safe repository policy")
        if (deck.name.isBlank()) return invalidDeckEdit("Deck name must not be blank")
        if (modifiedAt.precedes(deck.modifiedAt) || modifiedAt.precedes(deck.createdAt)) return invalidDeckEdit("Modification time precedes the deck timestamps")
        val entries = linkedMapOf<EntityId, DeckEntry>()
        for (entry in deck.entries) {
            if (entries.put(entry.id, entry) != null) return invalidDeckEdit("Duplicate deck entry ID")
            entryProblem(entry)?.let { return invalidDeckEdit(it) }
        }
        categoryProblem(deck.categoryOrder, entries.values)?.let { return invalidDeckEdit(it) }
        if (patch.edits.isEmpty()) return Outcome.Success(deck)
        var candidate = deck
        for (edit in patch.edits) {
            when (edit) {
                is DeckEdit.Rename -> {
                    if (edit.name.isBlank()) return invalidDeckEdit("Deck name must not be blank")
                    candidate = candidate.copy(name = edit.name)
                }
                is DeckEdit.Description -> candidate = candidate.copy(description = edit.text)
                is DeckEdit.Primer -> candidate = candidate.copy(primer = edit.markdown)
                is DeckEdit.ReorderEntries -> {
                    if (edit.entryIds.size != entries.size || edit.entryIds.toSet().size != entries.size ||
                        edit.entryIds.any { it !in entries }) return invalidDeckEdit("Entry order must be an exact permutation")
                    val reordered = edit.entryIds.map { entries.getValue(it) }
                    entries.clear()
                    reordered.forEach { entries[it.id] = it }
                }
                is DeckEdit.Categories -> {
                    categoryDefinitionProblem(edit.categories)?.let { return invalidDeckEdit(it) }
                    val definitions = edit.categories.associateBy { it.id }
                    for ((id, entry) in entries.toMap()) {
                        val labels = SnapshotList.copyOf(entry.labels.mapNotNull { definitions[it.id] })
                        if (labels != entry.labels) entries[id] = entry.copy(labels = labels)
                    }
                    candidate = candidate.copy(categoryOrder = edit.categories)
                }
                is DeckEdit.Format -> candidate = candidate.copy(format = edit.format)
                is DeckEdit.Metadata -> candidate = candidate.copy(folder = edit.folder, tags = edit.tags, budget = edit.budget, profileId = edit.profileId)
                is DeckEdit.Add -> {
                    entryProblem(edit.entry)?.let { return invalidDeckEdit(it) }
                    if (edit.entry.id in entries) return invalidDeckEdit("Added entry ID already exists")
                    if (entries.size >= MAX_ENTRIES) return invalidDeckEdit("Deck exceeds supported size")
                    entries[edit.entry.id] = edit.entry
                }
                is DeckEdit.Replace -> {
                    entryProblem(edit.entry)?.let { return invalidDeckEdit(it) }
                    if (edit.entry.id !in entries) return invalidDeckEdit("Replaced entry ID does not exist")
                    entries[edit.entry.id] = edit.entry
                }
                is DeckEdit.Remove -> if (entries.remove(edit.entryId) == null) return invalidDeckEdit("Removed entry ID does not exist")
                is DeckEdit.RestoreVersion, is DeckEdit.ApplyPackage -> return Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Unsupported deck edit")
            }
            categoryProblem(candidate.categoryOrder, entries.values)?.let { return invalidDeckEdit(it) }
        }
        // This helper only calculates a revision here; it makes no persistence claim.
        return when (val revision = revisionForCommittedEdit(expected, deck.revision)) {
            is Outcome.Success -> Outcome.Success(candidate.copy(entries = SnapshotList.copyOf(entries.values), revision = revision.value, modifiedAt = modifiedAt))
            else -> currentFailure(revision)
        }
    }

    private const val MAX_ENTRIES = 10_000
    private const val MAX_EDITS = 1_024

    private fun entryProblem(entry: DeckEntry): String? = when {
        entry.labels.map { it.id }.toSet().size != entry.labels.size -> "Duplicate label ID within entry"
        entry.roleOverrides.any { it.entryId != null && it.entryId != entry.id } -> "Role override belongs to another entry"
        else -> null
    }

    private fun categoryDefinitionProblem(categories: SnapshotList<EntryLabel>): String? = when {
        categories.size > DeckPayloadLimits.MAX_CATEGORIES -> "Too many categories"
        categories.any { it.name.isBlank() } -> "Category names must not be blank"
        categories.map { it.id }.toSet().size != categories.size -> "Duplicate category ID"
        else -> null
    }

    /** Null is legacy implicit state. A nonnull empty registry never acquires implicit categories. */
    private fun categoryProblem(categories: SnapshotList<EntryLabel>?, entries: Collection<DeckEntry>): String? {
        if (categories != null) categoryDefinitionProblem(categories)?.let { return it }
        val definitions = categories?.associateBy { it.id }?.toMutableMap() ?: mutableMapOf()
        for (entry in entries) for (label in entry.labels) {
            if (label.name.isBlank()) return "Category names must not be blank"
            val existing = definitions[label.id]
            if (categories != null && existing != label) return "Entry label does not match explicit category registry"
            if (categories == null) {
                if (existing != null && existing != label) return "Conflicting implicit category definitions"
                definitions[label.id] = label
                if (definitions.size > DeckPayloadLimits.MAX_CATEGORIES) return "Too many categories"
            }
        }
        return null
    }

    private fun UtcTimestamp.precedes(other: UtcTimestamp): Boolean =
        epochSeconds < other.epochSeconds || (epochSeconds == other.epochSeconds && nanoAdjustment < other.nanoAdjustment)

    private fun <T> currentFailure(outcome: Outcome<*>): Outcome<T> = when (outcome) {
        is Outcome.Failure -> outcome
        is Outcome.Unavailable -> outcome
        is Outcome.Unknown -> outcome
        is Outcome.Success -> error("Expected unsuccessful outcome")
    }
}

internal fun invalidDeckEdit(message: String): Outcome.Failure =
    Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT, message, SnapshotList.empty()))
