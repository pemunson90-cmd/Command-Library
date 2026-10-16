package com.architectpsg.commandlibrary.feature.shell

import com.architectpsg.commandlibrary.core.model.*

/** Pure proposals only; root applies through existing revision-fenced durable edits and undo. */
public interface ManualTagPlanning {
    public fun assign(deck: DeckSnapshot, pool: PersonalTagPool, entryIds: SnapshotList<EntityId>, tagIds: SnapshotList<EntityId>): Outcome<DeckPatch>
    public fun remove(deck: DeckSnapshot, entryIds: SnapshotList<EntityId>, tagIds: SnapshotList<EntityId>): Outcome<DeckPatch>
    public fun categories(deck: DeckSnapshot, categories: SnapshotList<EntryLabel>): Outcome<DeckPatch>
}
