package com.architectpsg.commandlibrary.feature.validation

import com.architectpsg.commandlibrary.core.model.*

public data class BoardConstraintFinding(
    public val constraint: BoardConstraint,
    public val quantity: Long,
    public val satisfied: Boolean,
)

public data class BoardConstraintAssessment(
    public val deckId: DeckId,
    public val deckRevision: Revision,
    public val findings: SnapshotList<BoardConstraintFinding>,
)

/**
 * Counts copies for explicitly supplied board-size checks, without an overall legality verdict.
 * The caller retains responsibility for complete structural/package validation and wire admission.
 * Only deck/entry IDs, quantities and supplied constraints are admitted here; no metadata is resolved.
 */
public object LocalBoardConstraintChecks {
    public fun assess(
        deck: DeckSnapshot,
        constraints: SnapshotList<BoardConstraint>,
        cancel: CancelToken,
    ): Outcome<BoardConstraintAssessment> {
        cancel.throwIfCancelled()
        return try {
            val roles = BoardRole.values()
            if (constraints.isEmpty() || constraints.size > roles.size ||
                deck.entries.size > DeckPayloadLimits.MAX_ENTRIES || deck.revision.value < 0) invalid()
            admitId(deck.id.source.value, cancel)
            admitId(deck.id.raw, cancel)

            val selected = arrayOfNulls<BoardConstraint>(roles.size)
            for (constraint in constraints) {
                cancel.throwIfCancelled()
                val index = constraint.board.ordinal
                val maximum = constraint.maximum
                if (selected[index] != null || constraint.minimum < 0 ||
                    (maximum != null && maximum < constraint.minimum)) invalid()
                selected[index] = constraint
            }

            val quantities = LongArray(roles.size)
            val entryIds = HashSet<String>(deck.entries.size)
            for (entry in deck.entries) {
                cancel.throwIfCancelled()
                admitId(entry.id.value, cancel)
                if (!entryIds.add(entry.id.value) || entry.quantity <= 0) invalid()
                val index = entry.board.ordinal
                if (selected[index] == null) continue
                val quantity = entry.quantity.toLong()
                if (quantity > Long.MAX_VALUE - quantities[index]) invalid()
                quantities[index] += quantity
            }

            val findings = ArrayList<BoardConstraintFinding>(constraints.size)
            for (role in roles) {
                cancel.throwIfCancelled()
                val constraint = selected[role.ordinal] ?: continue
                val quantity = quantities[role.ordinal]
                val maximum = constraint.maximum
                findings.add(BoardConstraintFinding(constraint, quantity,
                    quantity >= constraint.minimum.toLong() &&
                        (maximum == null || quantity <= maximum.toLong())))
            }
            cancel.throwIfCancelled()
            Outcome.Success(BoardConstraintAssessment(deck.id, deck.revision, SnapshotList.copyOf(findings)))
        } catch (_: InvalidInput) {
            Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT,
                "Invalid board-size check input.", SnapshotList.empty()))
        }
    }

    /** Bounded strict UTF-8 sizing without allocating bytes or normalizing foreign IDs. */
    private fun admitId(value: String, cancel: CancelToken) {
        cancel.throwIfCancelled()
        if (value.length > DeckPayloadLimits.MAX_ID_BYTES) invalid()
        var index = 0
        var bytes = 0
        var nonblank = false
        while (index < value.length) {
            val character = value[index]
            if (!character.isWhitespace()) nonblank = true
            when {
                character.isHighSurrogate() -> {
                    if (index + 1 >= value.length || !value[index + 1].isLowSurrogate()) invalid()
                    bytes += 4
                    index += 2
                }
                character.isLowSurrogate() -> invalid()
                character.code <= 0x7f -> { bytes++; index++ }
                character.code <= 0x7ff -> { bytes += 2; index++ }
                else -> { bytes += 3; index++ }
            }
            if (bytes > DeckPayloadLimits.MAX_ID_BYTES) invalid()
        }
        if (!nonblank) invalid()
        cancel.throwIfCancelled()
    }

    private class InvalidInput : RuntimeException()
    private fun invalid(): Nothing = throw InvalidInput()
}
