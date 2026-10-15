package com.architectpsg.commandlibrary.feature.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.column
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.long
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.number
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.request

/** Bounded validation of the logical undo head, independent of monotonic content revisions. */
internal class DeckUndoRows(private val store: DeckSnapshotStore) {
    data class State(val exists: Boolean, val pointer: Long?)
    data class Link(val revision: Long, val before: Long, val previous: Long?)

    suspend fun state(tx: DbTransaction, current: DeckSnapshot, cancel: CancelToken): State = with(store) {
        val rows = value(tx.query(request("SELECT ${DeckLibraryRows.integer("head_revision", "head")},${nullable("undo_link_revision", "pointer")} FROM deck_undo_state WHERE provider=:provider AND deck_id=:id", *keys(current.id)), cancel))
        val state = if (rows.isEmpty()) {
            corrupt(current.revision.value == 0L)
            val links = value(tx.query(request("SELECT 1 AS found FROM deck_undo_link WHERE provider=:provider AND deck_id=:id LIMIT 1", *keys(current.id)), cancel))
            corrupt(links.isEmpty())
            State(false, null)
        } else {
            // No legal edit creates state at revision zero: empty patches leave it absent.
            corrupt(current.revision.value > 0)
            corrupt(rows.single().long("head") == current.revision.value)
            State(true, rows.single().nullableLong("pointer"))
        }
        validatePointer(tx, current, state.pointer, current.revision.value, cancel)
        state
    }

    /** Exact equality ignores ONLY revision and modification time; every identity/evidence field stays. */
    private fun sameContent(a: DeckSnapshot, b: DeckSnapshot): Boolean =
        a.copy(revision = b.revision, modifiedAt = b.modifiedAt) == b

    suspend fun validatePointer(tx: DbTransaction, current: DeckSnapshot, pointer: Long?, maximum: Long, cancel: CancelToken): Link? = with(store) {
        // Every reached snapshot must retain the original deck identity and creation timestamp.
        val initial = historical(tx, current.id, 0, cancel)
        corrupt(initial.createdAt == current.createdAt)
        if (pointer == null) {
            corrupt(sameContent(current, initial))
            return@with null
        }
        val selected = link(tx, current.id, pointer, maximum, cancel)
        val result = historical(tx, current.id, selected.revision, cancel)
        val before = historical(tx, current.id, selected.before, cancel)
        corrupt(result.createdAt == current.createdAt && before.createdAt == current.createdAt)
        corrupt(sameContent(current, result))
        val predecessorResult = if (selected.previous == null) initial else {
            val predecessor = link(tx, current.id, selected.previous, selected.before, cancel)
            historical(tx, current.id, predecessor.revision, cancel)
        }
        corrupt(predecessorResult.createdAt == current.createdAt && sameContent(before, predecessorResult))
        selected
    }

    private suspend fun link(tx: DbTransaction, id: DeckId, revision: Long, maximum: Long, cancel: CancelToken): Link = with(store) {
        val rows = value(tx.query(request("SELECT ${DeckLibraryRows.integer("l.link_revision", "revision")},${DeckLibraryRows.integer("l.before_revision", "before")},${nullable("l.previous_link_revision", "previous")},EXISTS(SELECT 1 FROM deck_revision r WHERE r.provider=l.provider AND r.deck_id=l.deck_id AND r.revision=l.link_revision) AS result_exists,EXISTS(SELECT 1 FROM deck_revision r WHERE r.provider=l.provider AND r.deck_id=l.deck_id AND r.revision=l.before_revision) AS before_exists,CASE WHEN l.previous_link_revision IS NULL OR EXISTS(SELECT 1 FROM deck_undo_link p WHERE p.provider=l.provider AND p.deck_id=l.deck_id AND p.link_revision=l.previous_link_revision) THEN 1 ELSE 0 END AS previous_exists FROM deck_undo_link l WHERE l.provider=:provider AND l.deck_id=:id AND l.link_revision=:revision", *keys(id), "revision" to number(revision)), cancel))
        corrupt(rows.size == 1)
        val row = rows.single()
        corrupt(row.long("result_exists") == 1L && row.long("before_exists") == 1L && row.long("previous_exists") == 1L)
        val link = Link(row.long("revision"), row.long("before"), row.nullableLong("previous"))
        corrupt(link.revision == revision && revision > 0 && revision <= maximum && link.before == revision - 1)
        corrupt(link.previous == null || link.previous > 0 && link.previous < revision && link.previous <= link.before)
        link
    }

    suspend fun append(tx: DbTransaction, id: DeckId, revision: Long, before: Long, previous: Long?, cancel: CancelToken) = with(store) {
        write(tx, request("INSERT INTO deck_undo_link (provider,deck_id,link_revision,before_revision,previous_link_revision) VALUES (:provider,:id,:revision,:before,:previous)", *keys(id),
            "revision" to number(revision), "before" to number(before), "previous" to sql(previous)), cancel)
    }

    suspend fun advance(tx: DbTransaction, id: DeckId, old: Revision, revision: Revision, state: State, pointer: Long?, cancel: CancelToken) = with(store) {
        val parameters = keys(id) + arrayOf("revision" to number(revision.value), "pointer" to sql(pointer))
        if (!state.exists) write(tx, request("INSERT INTO deck_undo_state (provider,deck_id,head_revision,undo_link_revision) VALUES (:provider,:id,:revision,:pointer)", *parameters), cancel)
        else write(tx, request("UPDATE deck_undo_state SET head_revision=:revision,undo_link_revision=:pointer WHERE provider=:provider AND deck_id=:id AND head_revision=:expected AND undo_link_revision IS :previous", *parameters,
            "expected" to number(old.value), "previous" to sql(state.pointer)), cancel)
    }

    private fun nullable(column: String, alias: String): String =
        "${DeckLibraryRows.integer(column, alias)},CASE WHEN $column IS NULL OR typeof($column)='integer' THEN 1 ELSE 0 END AS ${alias}_valid"
    private fun RowData.nullableLong(name: String): Long? {
        corrupt(long("${name}_valid") == 1L)
        return if (column(name) == SqlValue.Null) null else long(name)
    }
    private fun sql(value: Long?): SqlValue = value?.let { number(it) } ?: SqlValue.Null
}
