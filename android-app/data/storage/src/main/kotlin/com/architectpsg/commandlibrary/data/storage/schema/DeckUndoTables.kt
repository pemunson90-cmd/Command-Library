package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

/** Insert-only logical undo groups; undo itself appends content but creates no group. */
@Entity(tableName = "deck_undo_link", primaryKeys = ["provider", "deck_id", "link_revision"], foreignKeys = [
    ForeignKey(entity = DeckRevisionEntity::class, parentColumns = ["provider", "deck_id", "revision"],
        childColumns = ["provider", "deck_id", "link_revision"], onDelete = ForeignKey.NO_ACTION),
    ForeignKey(entity = DeckRevisionEntity::class, parentColumns = ["provider", "deck_id", "revision"],
        childColumns = ["provider", "deck_id", "before_revision"], onDelete = ForeignKey.NO_ACTION),
    ForeignKey(entity = DeckUndoLinkEntity::class, parentColumns = ["provider", "deck_id", "link_revision"],
        childColumns = ["provider", "deck_id", "previous_link_revision"], onDelete = ForeignKey.NO_ACTION),
], indices = [
    Index(value = ["provider", "deck_id", "before_revision"], name = "index_deck_undo_link_before"),
    Index(value = ["provider", "deck_id", "previous_link_revision"], name = "index_deck_undo_link_previous"),
])
internal data class DeckUndoLinkEntity(
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "deck_id") val deck_id: String,
    @ColumnInfo(name = "link_revision") val link_revision: Long,
    @ColumnInfo(name = "before_revision") val before_revision: Long,
    @ColumnInfo(name = "previous_link_revision") val previous_link_revision: Long?,
)

/** Mutable logical pointer bound to the independently monotonic immutable content head. */
@Entity(tableName = "deck_undo_state", primaryKeys = ["provider", "deck_id"], foreignKeys = [
    ForeignKey(entity = DeckRevisionEntity::class, parentColumns = ["provider", "deck_id", "revision"],
        childColumns = ["provider", "deck_id", "head_revision"], onDelete = ForeignKey.NO_ACTION),
    ForeignKey(entity = DeckUndoLinkEntity::class, parentColumns = ["provider", "deck_id", "link_revision"],
        childColumns = ["provider", "deck_id", "undo_link_revision"], onDelete = ForeignKey.NO_ACTION),
], indices = [
    Index(value = ["provider", "deck_id", "head_revision"], name = "index_deck_undo_state_head"),
    Index(value = ["provider", "deck_id", "undo_link_revision"], name = "index_deck_undo_state_link"),
])
internal data class DeckUndoStateEntity(
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "deck_id") val deck_id: String,
    @ColumnInfo(name = "head_revision") val head_revision: Long,
    @ColumnInfo(name = "undo_link_revision") val undo_link_revision: Long?,
)
