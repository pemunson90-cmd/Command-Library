package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.DomainFailure
import com.architectpsg.commandlibrary.core.model.EntityId
import com.architectpsg.commandlibrary.core.model.FailureCode
import com.architectpsg.commandlibrary.core.model.GameSnapshot
import com.architectpsg.commandlibrary.core.model.Outcome
import com.architectpsg.commandlibrary.core.model.Seat
import com.architectpsg.commandlibrary.core.model.SnapshotList

/** A rejected request; the caller's snapshot is never modified. */
internal fun invalidInput(message: String): Outcome.Failure =
    Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT, message, SnapshotList.empty()))

/** The supplied snapshot contradicts itself; nothing derived from it can be trusted. */
internal fun corruptData(message: String): Outcome.Failure =
    Outcome.Failure(DomainFailure(FailureCode.CORRUPT_DATA, message, SnapshotList.empty()))

/** Unwraps success or hands every other outcome to [onOther], which must leave the caller (usually `return it`). */
internal inline fun <T> Outcome<T>.valueOr(onOther: (Outcome<Nothing>) -> Nothing): T = when (this) {
    is Outcome.Success -> value
    is Outcome.Failure -> onOther(this)
    is Outcome.Unavailable -> onOther(this)
    is Outcome.Unknown -> onOther(this)
}

/** SPEC G01 table size; PX07-049 skips more than six seats. */
internal const val MIN_SEATS: Int = 1
internal const val MAX_SEATS: Int = 6

/** Seat and commander-slot identity for one snapshot. Slots are keyed by ID, never by label or card name. */
internal class SeatIndex private constructor(
    val seats: Map<EntityId, Seat>,
    private val slotOwners: Map<EntityId, EntityId>,
) {
    fun contains(seatId: EntityId): Boolean = seatId in seats

    fun ownerOfSlot(slotId: EntityId): EntityId? = slotOwners[slotId]

    fun slotsOwnedBy(seatId: EntityId): List<EntityId> =
        seats[seatId]?.commanders?.map { it.id }.orEmpty()

    fun orderedSeatIds(): List<EntityId> = seats.values.sortedBy { it.order }.map { it.id }

    /** Commander slots of every other seat, in table order. */
    fun slotsOpposing(seatId: EntityId): List<EntityId> =
        seats.values.sortedBy { it.order }.filter { it.id != seatId }.flatMap { seat -> seat.commanders.map { it.id } }

    companion object {
        fun of(snapshot: GameSnapshot): Outcome<SeatIndex> {
            val seats = snapshot.seats
            if (seats.size !in MIN_SEATS..MAX_SEATS) return corruptData("Game has ${seats.size} seats; supported range is $MIN_SEATS..$MAX_SEATS")
            val byId = LinkedHashMap<EntityId, Seat>()
            val orders = HashSet<Int>()
            val owners = HashMap<EntityId, EntityId>()
            for (seat in seats) {
                if (byId.put(seat.id, seat) != null) return corruptData("Duplicate seat ID ${seat.id.value}")
                if (!orders.add(seat.order)) return corruptData("Duplicate seat order ${seat.order}")
                for (slot in seat.commanders) {
                    if (owners.put(slot.id, seat.id) != null) return corruptData("Duplicate commander slot ID ${slot.id.value}")
                }
            }
            return Outcome.Success(SeatIndex(byId, owners))
        }
    }
}
