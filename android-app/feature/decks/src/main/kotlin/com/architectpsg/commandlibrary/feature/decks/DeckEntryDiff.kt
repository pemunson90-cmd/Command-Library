package com.architectpsg.commandlibrary.feature.decks

import com.architectpsg.commandlibrary.core.model.*

/** Entry-content diff only: deck metadata and entry order are outside this projection. */
public object DeckEntryDiff {
    public fun compare(before: DeckSnapshot, after: DeckSnapshot): Outcome<SnapshotList<CardChange>> {
        if (before.id != after.id) return invalidDeckEdit("Cannot compare entries from different decks")
        if (before.entries.size > 10_000 || after.entries.size > 10_000) return invalidDeckEdit("Deck exceeds supported size")
        val oldEntries = before.entries.associateBy { it.id }
        val newEntries = after.entries.associateBy { it.id }
        if (oldEntries.size != before.entries.size || newEntries.size != after.entries.size) return invalidDeckEdit("Duplicate deck entry ID")
        val changes = mutableListOf<CardChange>()
        for (old in before.entries) {
            val new = newEntries[old.id]
            if (old != new) changes += CardChange(old.id, old, new, if (new == null) "Entry removed" else "Entry changed", SnapshotList.empty())
        }
        for (new in after.entries) {
            if (new.id !in oldEntries) changes += CardChange(new.id, null, new, "Entry added", SnapshotList.empty())
        }
        return Outcome.Success(SnapshotList.copyOf(changes))
    }
}
