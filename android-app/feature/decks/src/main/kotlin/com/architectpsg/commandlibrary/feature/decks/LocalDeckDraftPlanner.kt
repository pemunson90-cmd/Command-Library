package com.architectpsg.commandlibrary.feature.decks

import com.architectpsg.commandlibrary.core.model.*

/** Deterministic raw-to-primitive projection. Recovery evidence confers no catalog/write authority. */
public class LocalDeckDraftPlanner : DeckDraftPlanner {
    public override fun propose(base: DeckSnapshot, body: DeckDraftBody, scope: DeckDraftCommitScope,
        addedEntryId: EntityId?, cancel: CancelToken): Outcome<DeckDraftPlan> {
        cancel.throwIfCancelled()
        if (body.baseRevision != base.revision) return failure(FailureCode.STALE_REVISION, "Draft base changed")
        return try {
            val bounds = Bounds(cancel)
            bounds.identifier(base.id.source.value); bounds.identifier(base.id.raw)
            require(base.id.source == LocalDeckLibraryLimits.SOURCE)
            baseTextPreflight(base, cancel)
            when (val valid = DeckSnapshotValidator.validate(base, cancel)) {
                is Outcome.Success -> Unit
                is Outcome.Failure -> return valid
                is Outcome.Unavailable -> return valid
                is Outcome.Unknown -> return valid
            }
            require(text(base.name, DeckPayloadLimits.MAX_ID_BYTES, cancel, nonblank = true))
            require(text(base.description, DeckPayloadLimits.MAX_TEXT_BYTES, cancel) && text(base.primer, DeckPayloadLimits.MAX_TEXT_BYTES, cancel))
            base.entries.forEach { cancel.throwIfCancelled(); require(text(it.notes, DeckPayloadLimits.MAX_TEXT_BYTES, cancel)) }
            // Reject repeated oversized staged bodies by lengths/lists before any strict scan.
            Bounds(cancel, preflight = true).body(body)
            bounds.body(body)
            if (body !is DeckDraftBody.Add) require(addedEntryId == null)
            if (base.packageInstances.isNotEmpty()) return Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Primitive draft edits require contribution-safe package policy")
            val explicit = scope == DeckDraftCommitScope.EXPLICIT_CONFIRMED
            val edits = mutableListOf<DeckEdit>()
            var residual: DeckDraftBody? = null
            when (body) {
                is DeckDraftBody.Details -> {
                    val name = text(body.name, DeckPayloadLimits.MAX_ID_BYTES, cancel, nonblank = true)
                    val description = text(body.description, DeckPayloadLimits.MAX_TEXT_BYTES, cancel)
                    val primer = text(body.primer, DeckPayloadLimits.MAX_TEXT_BYTES, cancel)
                    if (explicit) require(name && description && primer)
                    val progress = name && body.name != base.name || description && body.description != base.description || primer && body.primer != base.primer
                    if (!explicit) require(progress)
                    if (name && body.name != base.name) edits += DeckEdit.Rename(body.name)
                    if (description && body.description != base.description) edits += DeckEdit.Description(body.description)
                    if (primer && body.primer != base.primer) edits += DeckEdit.Primer(body.primer)
                    if (!explicit && (!name || !description || !primer)) residual = body
                }
                is DeckDraftBody.Entry -> {
                    val source = base.entries.singleOrNull { it.id == body.entryId } ?: invalid()
                    val quantity = quantity(body.quantity)
                    val notes = text(body.notes, DeckPayloadLimits.MAX_TEXT_BYTES, cancel)
                    if (explicit) require(quantity != null && notes)
                    val progress = quantity != null && body.quantity != source.quantity.toString() || notes && body.notes != source.notes
                    if (!explicit) require(progress)
                    val candidate = source.copy(quantity = quantity ?: source.quantity, notes = if (notes) body.notes else source.notes,
                        board = if (explicit) body.board else source.board, finish = if (explicit) body.finish else source.finish,
                        labels = if (explicit) body.labels else source.labels)
                    if (candidate != source) edits += DeckEdit.Replace(candidate)
                    if (!explicit && (quantity == null || !notes || body.board != source.board || body.finish != source.finish || body.labels != source.labels))
                        residual = body.copy(quantity = quantity?.toString() ?: body.quantity, notes = if (notes) candidate.notes else body.notes)
                }
                is DeckDraftBody.Add -> {
                    require(explicit)
                    val id = requireNotNull(addedEntryId); bounds.identifier(id.value)
                    require(base.entries.none { it.id == id })
                    val selection = requireNotNull(body.selection)
                    require(selection.identity.oracleId is Knowledge.Known && selection.generation != null)
                    val quantity = requireNotNull(quantity(body.quantity))
                    require(text(body.notes, DeckPayloadLimits.MAX_TEXT_BYTES, cancel))
                    edits += DeckEdit.Add(DeckEntry(id, selection.identity, quantity, body.board, body.finish, body.notes, body.labels, SnapshotList.empty()))
                }
                is DeckDraftBody.Remove -> {
                    require(explicit && base.entries.any { it.id == body.entryId })
                    edits += DeckEdit.Remove(body.entryId)
                }
            }
            cancel.throwIfCancelled()
            if (edits.isNotEmpty() && base.revision.value == Long.MAX_VALUE) return failure(FailureCode.PERSISTENCE, "Deck revision exhausted")
            val patch = DeckPatch(SnapshotList.copyOf(edits), if (explicit) DeckDraftLimits.EXPLICIT_REASON else DeckDraftLimits.TEXT_REASON)
            val projected = when (val result = DeckPatchReducer.propose(base, patch, base.revision, base.modifiedAt)) {
                is Outcome.Success -> result.value
                is Outcome.Failure -> return result
                is Outcome.Unavailable -> return result
                is Outcome.Unknown -> return result
            }
            cancel.throwIfCancelled()
            when (val validation = DeckSnapshotValidator.validate(projected, cancel)) {
                is Outcome.Success -> Unit
                is Outcome.Failure -> return validation
                is Outcome.Unavailable -> return validation
                is Outcome.Unknown -> return validation
            }
            residual = when (val remaining = residual) {
                is DeckDraftBody.Details -> remaining.copy(baseRevision = projected.revision)
                is DeckDraftBody.Entry -> remaining.copy(baseRevision = projected.revision)
                null -> null
                else -> error("Only existing text forms have residual input")
            }
            Outcome.Success(DeckDraftPlan(patch, residual))
        } catch (_: IllegalArgumentException) { failure(FailureCode.INVALID_INPUT, "Invalid deck draft projection") }
    }

    private fun quantity(raw: String): Int? {
        // ASCII decimal aliases (01, +1) retain their exact raw representation until consumed.
        if (raw.isEmpty() || raw.any { it !in '0'..'9' && it != '+' && it != '-' }) return null
        return raw.toIntOrNull()?.takeIf { it > 0 }
    }
    private class Bounds(private val cancel: CancelToken, private val preflight: Boolean = false) {
        private var elements = 0
        private var units = 0L
        private fun reserve(count: Long) {
            cancel.throwIfCancelled()
            require(count <= DeckDraftLimits.MAX_ENCODED_BYTES - units)
            units += count
        }
        private fun field(value: String, maximum: Int, identifier: Boolean = false) {
            cancel.throwIfCancelled()
            require(value.length <= maximum)
            if (preflight) reserve(value.length.toLong()) else require(text(value, maximum, cancel, identifier))
        }
        fun identifier(value: String) = field(value, DeckPayloadLimits.MAX_ID_BYTES, true)
        private fun structured(value: String) = field(value, DeckPayloadLimits.MAX_TEXT_BYTES)
        private fun count(size: Int) {
            cancel.throwIfCancelled()
            require(size <= DeckPayloadLimits.MAX_LIST_ITEMS && size <= DeckPayloadLimits.MAX_COLLECTION_ELEMENTS - elements)
            elements += size
        }
        fun body(body: DeckDraftBody) {
            fun raw(value: String, maximum: Int = DeckDraftLimits.MAX_TEXT_CODE_UNITS) {
                cancel.throwIfCancelled(); require(value.length <= maximum)
                if (preflight) reserve(value.length.toLong() * 2)
            }
            fun choices(finish: Knowledge<Finish>, labels: SnapshotList<EntryLabel>) {
                knowledge(finish) { }; count(labels.size)
                labels.forEach { cancel.throwIfCancelled(); identifier(it.id.value); structured(it.name) }
            }
            when (body) {
                is DeckDraftBody.Details -> { raw(body.name, DeckDraftLimits.MAX_NAME_CODE_UNITS); raw(body.description); raw(body.primer) }
                is DeckDraftBody.Entry -> { identifier(body.entryId.value); raw(body.quantity, DeckDraftLimits.MAX_QUANTITY_CODE_UNITS); raw(body.notes); choices(body.finish, body.labels) }
                is DeckDraftBody.Add -> {
                    raw(body.quantity, DeckDraftLimits.MAX_QUANTITY_CODE_UNITS); raw(body.notes); choices(body.finish, body.labels)
                    body.selection?.let { selection ->
                        val identity = selection.identity
                        knowledge(identity.oracleId) { identifier(it.source.value); identifier(it.raw) }
                        knowledge(identity.printingId) { identifier(it.source.value); identifier(it.raw) }
                        identity.raw?.let { identifier(it.provider.value); structured(it.kind); identifier(it.rawId) }
                        knowledge(identity.originalName, ::structured); identifier(identity.language.value)
                        selection.generation?.let { identifier(it.value) }
                    }
                }
                is DeckDraftBody.Remove -> identifier(body.entryId.value)
            }
        }
        private fun <T> knowledge(value: Knowledge<T>, known: (T) -> Unit) {
            cancel.throwIfCancelled()
            when (value) {
                is Knowledge.Known -> known(value.value)
                is Knowledge.Absent -> structured(value.reason)
                is Knowledge.Unknown -> {
                    structured(value.reason); count(value.evidence.size)
                    value.evidence.forEach { cancel.throwIfCancelled(); structured(it.description); it.source?.let { uri -> structured(uri.value) } }
                }
            }
        }
    }
    private companion object {
        /** Independent lower-bound budget; complete CLDK representability remains the codec's job. */
        fun baseTextPreflight(base: DeckSnapshot, cancel: CancelToken) {
            cancel.throwIfCancelled()
            require(base.entries.size <= DeckPayloadLimits.MAX_ENTRIES)
            var units = 0L
            fun field(value: String, maximum: Int) {
                cancel.throwIfCancelled()
                require(value.length <= maximum && value.length.toLong() <= DeckPayloadLimits.MAX_ENCODED_BYTES - units)
                units += value.length
            }
            field(base.name, DeckPayloadLimits.MAX_ID_BYTES)
            field(base.description, DeckPayloadLimits.MAX_TEXT_BYTES); field(base.primer, DeckPayloadLimits.MAX_TEXT_BYTES)
            base.entries.forEach { field(it.notes, DeckPayloadLimits.MAX_TEXT_BYTES) }
        }
        fun invalid(): Nothing = throw IllegalArgumentException()
        fun failure(code: FailureCode, message: String) = Outcome.Failure(DomainFailure(code, message, SnapshotList.empty()))
        /** Strict UTF8 byte admission without replacement/encoder allocation; polls during long input. */
        fun text(value: String, maximum: Int, cancel: CancelToken, nonblank: Boolean = false): Boolean {
            cancel.throwIfCancelled()
            if (value.length > maximum) return false
            var index = 0; var bytes = 0; var visible = false; var nextCheck = 0
            while (index < value.length) {
                if (index >= nextCheck) { cancel.throwIfCancelled(); nextCheck = index + 1024 }
                val ch = value[index++]
                if (!ch.isWhitespace()) visible = true
                val width = when {
                    ch.isHighSurrogate() -> { if (index >= value.length || !value[index].isLowSurrogate()) return false; index++; 4 }
                    ch.isLowSurrogate() -> return false
                    ch.code < 128 -> 1
                    ch.code < 2048 -> 2
                    else -> 3
                }
                if (width > maximum - bytes) return false
                bytes += width
            }
            cancel.throwIfCancelled()
            return !nonblank || visible
        }
    }
}
