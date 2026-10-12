package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.CardIdentity
import com.architectpsg.commandlibrary.core.model.ClockUndoPolicy
import com.architectpsg.commandlibrary.core.model.ClockZeroPolicy
import com.architectpsg.commandlibrary.core.model.CommanderSlot
import com.architectpsg.commandlibrary.core.model.CounterDefinition
import com.architectpsg.commandlibrary.core.model.CounterLossPolicy
import com.architectpsg.commandlibrary.core.model.CounterState
import com.architectpsg.commandlibrary.core.model.DomainFailure
import com.architectpsg.commandlibrary.core.model.EntityId
import com.architectpsg.commandlibrary.core.model.FailureCode
import com.architectpsg.commandlibrary.core.model.GameId
import com.architectpsg.commandlibrary.core.model.GameSettings
import com.architectpsg.commandlibrary.core.model.GameSnapshot
import com.architectpsg.commandlibrary.core.model.GameStatus
import com.architectpsg.commandlibrary.core.model.Knowledge
import com.architectpsg.commandlibrary.core.model.LanguageTag
import com.architectpsg.commandlibrary.core.model.OracleId
import com.architectpsg.commandlibrary.core.model.Outcome
import com.architectpsg.commandlibrary.core.model.ProviderId
import com.architectpsg.commandlibrary.core.model.Revision
import com.architectpsg.commandlibrary.core.model.Seat
import com.architectpsg.commandlibrary.core.model.SeatClockMode
import com.architectpsg.commandlibrary.core.model.SeatClockState
import com.architectpsg.commandlibrary.core.model.SnapshotList
import com.architectpsg.commandlibrary.core.model.UtcTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.fail

/** Synthetic life-domain fixtures. No card, player, or source data here is authentic or measured. */
internal object LifeFixtures {
    val provider = ProviderId("synthetic-life")
    val gameId = GameId(provider, "game-1")

    val seatA = EntityId("seat-a")
    val seatB = EntityId("seat-b")
    val seatC = EntityId("seat-c")

    /** Seat A has one commander; seat B has two partners sharing a label to prove identity is by slot ID. */
    val slotA = EntityId("slot-a")
    val slotB1 = EntityId("slot-b1")
    val slotB2 = EntityId("slot-b2")

    val life = EntityId("counter-life")
    val commanderDamage = EntityId("counter-commander-damage")
    val commanderTax = EntityId("counter-commander-tax")
    val poison = EntityId("counter-poison")
    val rad = EntityId("counter-rad")
    val charge = EntityId("counter-custom-charge")

    val roles = CounterRoles(life = life, commanderDamage = commanderDamage, commanderTax = commanderTax)

    fun definitions(lifeInitial: Int = 40): SnapshotList<CounterDefinition> = SnapshotList.of(
        CounterDefinition(life, "Life", null, null, lifeInitial, CounterLossPolicy.AtOrBelow(0), false),
        CounterDefinition(commanderDamage, "Commander damage", 0, null, 0, CounterLossPolicy.AtOrAbove(21), false),
        CounterDefinition(commanderTax, "Commander tax", 0, null, 0, CounterLossPolicy.None, false),
        CounterDefinition(poison, "Poison", 0, null, 0, CounterLossPolicy.AtOrAbove(10), false),
        CounterDefinition(rad, "Rad", 0, null, 0, CounterLossPolicy.None, false),
        CounterDefinition(charge, "Charge", 0, 5, 2, CounterLossPolicy.None, true),
    )

    fun identity(name: String): CardIdentity = CardIdentity(
        oracleId = Knowledge.Known(OracleId(provider, "oracle-$name")),
        printingId = Knowledge.Absent("Synthetic fixture names no printing"),
        raw = null,
        originalName = Knowledge.Known("Synthetic $name"),
        language = LanguageTag.ENGLISH,
    )

    fun seat(id: EntityId, order: Int, vararg slots: CommanderSlot): Seat = Seat(
        id = id,
        playerName = "Player ${id.value}",
        profileId = null,
        deck = Knowledge.Absent("No deck linked in synthetic fixture"),
        order = order,
        commanders = SnapshotList.copyOf(slots.asList()),
    )

    fun seats(): SnapshotList<Seat> = SnapshotList.of(
        seat(seatA, 0, CommanderSlot(slotA, identity("commander-a"), "Commander")),
        seat(seatB, 1, CommanderSlot(slotB1, identity("partner-one"), "Partner"), CommanderSlot(slotB2, identity("partner-two"), "Partner")),
        seat(seatC, 2),
    )

    fun idleClock(): SeatClockState = SeatClockState(
        mode = SeatClockMode.NONE,
        seats = SnapshotList.empty(),
        activeSeat = null,
        anchor = null,
        paused = true,
        zeroPolicy = ClockZeroPolicy.ALERT,
        undoPolicy = ClockUndoPolicy.KEEP_ELAPSED,
        firedAlertIds = SnapshotList.empty(),
    )

    fun snapshot(
        counters: List<CounterState> = emptyList(),
        seats: SnapshotList<Seat> = seats(),
        definitions: SnapshotList<CounterDefinition> = definitions(),
        initialLife: Int = 40,
        status: GameStatus = GameStatus.ACTIVE,
        clock: SeatClockState = idleClock(),
        clockMode: SeatClockMode = clock.mode,
        activeSeatId: EntityId? = null,
        turnNumber: Long = 0,
    ): GameSnapshot = GameSnapshot(
        id = gameId,
        version = 1,
        revision = Revision(7),
        settings = GameSettings(
            format = Knowledge.Unknown("Synthetic fixture has no format", SnapshotList.empty()),
            initialLife = initialLife,
            counterDefinitions = definitions,
            variantDefinitions = SnapshotList.empty(),
            clockMode = clockMode,
            randomSeed = 0,
        ),
        status = status,
        startedAt = UtcTimestamp(1_000),
        endedAt = Knowledge.Absent("Game not finished"),
        seats = seats,
        counters = SnapshotList.copyOf(counters),
        activeSeatId = activeSeatId,
        turnNumber = turnNumber,
        clock = clock,
        variants = SnapshotList.empty(),
        lastEventId = Knowledge.Absent("No events in synthetic fixture"),
        result = Knowledge.Absent("No result"),
    )

    fun state(definition: EntityId, seat: EntityId, value: Int, slot: EntityId? = null, hidden: Boolean = false): CounterState =
        CounterState(definition, seat, slot, value, hidden)

}

internal fun <T> Outcome<T>.success(): T =
    if (this is Outcome.Success) value else throw AssertionError("Expected success, got $this")

internal fun Outcome<*>.failure(code: FailureCode): DomainFailure {
    if (this !is Outcome.Failure) fail("Expected $code failure, got $this")
    val failure = (this as Outcome.Failure).error
    assertEquals("Unexpected failure: ${failure.message}", code, failure.code)
    return failure
}

internal fun GameSnapshot.counter(definition: EntityId, seat: EntityId, slot: EntityId? = null): CounterState? =
    counters.singleOrNull { it.definitionId == definition && it.seatId == seat && it.commanderSlotId == slot }
