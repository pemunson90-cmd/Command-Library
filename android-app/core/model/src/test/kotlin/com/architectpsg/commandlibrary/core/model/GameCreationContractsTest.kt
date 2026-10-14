package com.architectpsg.commandlibrary.core.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Complete immutable new-game input validation; no service or persistence proof. */
class GameCreationContractsTest {
    private val operationId = EntityId("synthetic-create")
    private fun definition(id: String, role: CounterRole, initial: Int = 20) = CounterDefinition(
        EntityId(id), "Synthetic label", null, null, initial, CounterLossPolicy.None, role == CounterRole.OTHER, role)
    private fun seats(count: Int = 2) = SnapshotList.copyOf((0 until count).map { index ->
        Seat(EntityId("seat-$index"), "Synthetic $index", null, Knowledge.Absent("No deck"), index, SnapshotList.empty())
    })
    private fun settings(mode: SeatClockMode = SeatClockMode.NONE, definitions: SnapshotList<CounterDefinition> =
        SnapshotList.of(definition("life", CounterRole.LIFE))) = GameSettings(
        Knowledge.Unknown("Synthetic format", SnapshotList.empty()), 20, definitions, SnapshotList.empty(), mode, 0)
    private fun clock(mode: SeatClockMode = SeatClockMode.NONE, seats: SnapshotList<Seat> = seats(), initial: Duration = 30.seconds) =
        SeatClockState(mode, if (mode in listOf(SeatClockMode.CHESS, SeatClockMode.PER_TURN))
            SnapshotList.copyOf(seats.map { SeatTime(it.id, initial, initial) }) else SnapshotList.empty(),
            null, null, true, ClockZeroPolicy.ALERT, ClockUndoPolicy.KEEP_ELAPSED, SnapshotList.empty(),
            if (mode == SeatClockMode.SHARED) SharedCountdown(initial, initial) else null)
    private fun create(settings: GameSettings = settings(), seats: SnapshotList<Seat> = seats(),
        clock: SeatClockState = clock()) = GameCreation(operationId, settings, seats, clock)
    private fun rejected(settings: GameSettings = settings(), seats: SnapshotList<Seat> = seats(),
        clock: SeatClockState = clock()) {
        assertThrows(IllegalArgumentException::class.java) { create(settings, seats, clock) }
    }
    private fun commander(id: String) = CommanderSlot(EntityId(id), CardIdentity(
        Knowledge.Unknown("No oracle", SnapshotList.empty()), Knowledge.Unknown("No printing", SnapshotList.empty()),
        ProviderIdentity(ProviderId("synthetic"), "card", "raw-$id"), Knowledge.Known("Synthetic commander"), LanguageTag.ENGLISH), "Synthetic")

    @Test fun completeExplicitRoleInputsPreserveValuesForEveryClockMode() {
        val definitions = SnapshotList.of(definition("life", CounterRole.LIFE), definition("damage", CounterRole.COMMANDER_DAMAGE, 0),
            definition("tax", CounterRole.COMMANDER_TAX, 0), definition("custom-a", CounterRole.OTHER, -2), definition("custom-b", CounterRole.OTHER, 7))
        for (mode in SeatClockMode.entries) {
            val input = create(settings(mode, definitions), seats(), clock(mode))
            assertEquals(operationId, input.operationId); assertEquals(definitions, input.settings.counterDefinitions)
            assertEquals(mode, input.clock.mode); assertTrue(input.clock.paused)
            assertNull(input.clock.activeSeat); assertNull(input.clock.anchor)
        }
    }
    @Test fun oneAndSixSeatsAndZeroInitialClockBanksRemainValid() {
        for (count in listOf(1, 6)) for (mode in SeatClockMode.entries) {
            val seats = seats(count)
            val input = create(settings(mode), seats, clock(mode, seats, Duration.ZERO))
            assertEquals(count, input.seats.size)
        }
    }
    @Test fun negativeCustomStartingLifeAndUnresolvedArtDoNotInventExtraRestrictions() {
        val first = seats().first().copy(commanders = SnapshotList.of(commander("slot-a"), commander("slot-b"), commander("slot-c")),
            appearance = SeatAppearance(null, GameArtReference.CachedCard(commander("art").identity, EntityId("unavailable-cache-asset"))))
        val setup = settings(definitions = SnapshotList.of(definition("life", CounterRole.LIFE, -5))).copy(initialLife = -5)
        val input = create(setup, SnapshotList.of(first, seats().last()))
        assertEquals(-5, input.settings.initialLife); assertEquals(3, input.seats.first().commanders.size)
    }
    @Test fun seatAndClockBankListOrderNeedNotMatchStableOrderFields() {
        val seats = SnapshotList.copyOf(seats().reversed())
        val clock = clock(SeatClockMode.CHESS).copy(seats = SnapshotList.copyOf(clock(SeatClockMode.CHESS).seats.reversed()))
        assertEquals(seats, create(settings(SeatClockMode.CHESS), seats, clock).seats)
    }
    @Test fun creationRejectsUnsupportedSeatCountsDuplicateIdentityAndNoncontiguousOrder() {
        rejected(seats = seats(0)); rejected(seats = seats(7))
        val seats = seats()
        rejected(seats = SnapshotList.of(seats.first(), seats.last().copy(id = seats.first().id)))
        rejected(seats = SnapshotList.of(seats.first(), seats.last().copy(order = 0)))
        rejected(seats = SnapshotList.of(seats.first(), seats.last().copy(order = 2)))
        rejected(seats = SnapshotList.of(seats.first().copy(order = 1), seats.last().copy(order = 2)))
    }
    @Test fun commanderSlotIdentityMustBeUniqueWithinAndAcrossSeats() {
        val seats = seats(); val slot = commander("duplicate-slot")
        rejected(seats = SnapshotList.of(seats.first().copy(commanders = SnapshotList.of(slot, slot)), seats.last()))
        rejected(seats = SnapshotList.of(seats.first().copy(commanders = SnapshotList.of(slot)), seats.last().copy(commanders = SnapshotList.of(slot))))
    }
    @Test fun eliminatedSeatsCannotEnterANewGame() {
        for (kind in SeatExitKind.entries) {
            val seats = seats()
            rejected(seats = SnapshotList.of(seats.first().copy(participation =
                SeatParticipation.Eliminated(kind, "Synthetic previous exit", null, UtcTimestamp(1000))), seats.last()))
        }
    }
    @Test fun roleBindingsRequireUniqueDefinitionsOneExplicitLifeAndAtMostOneCommanderRole() {
        val life = definition("life", CounterRole.LIFE)
        rejected(settings(definitions = SnapshotList.empty()))
        rejected(settings(definitions = SnapshotList.of(definition("label-life", CounterRole.OTHER).copy(label = "Life"))))
        rejected(settings(definitions = SnapshotList.of(life, life.copy(role = CounterRole.OTHER))))
        for (role in listOf(CounterRole.LIFE, CounterRole.COMMANDER_DAMAGE, CounterRole.COMMANDER_TAX)) {
            val duplicateRoles = SnapshotList.of(definition("first", role), definition("second", role))
            val definitions = if (role == CounterRole.LIFE) duplicateRoles else SnapshotList.copyOf(listOf(life) + duplicateRoles)
            rejected(settings(definitions = definitions))
        }
        rejected(settings().copy(initialLife = 21))
    }
    @Test fun creationRejectsClockModeMismatchOrAlreadyRunningClockMetadata() {
        rejected(clock = clock(SeatClockMode.SHARED))
        for (mode in SeatClockMode.entries) {
            val settings = settings(mode); val clock = clock(mode)
            rejected(settings, clock = clock.copy(paused = false))
            rejected(settings, clock = clock.copy(activeSeat = seats().first().id))
            rejected(settings, clock = clock.copy(activeSeat = EntityId("unknown-seat")))
            rejected(settings, clock = clock.copy(anchor = ClockAnchor(UtcTimestamp(1000), Duration.ZERO, EntityId("old-process"))))
            rejected(settings, clock = clock.copy(firedAlertIds = SnapshotList.of(EntityId("old-occurrence"))))
        }
    }
    @Test fun noneAndSharedModesRequireExactlyTheirOwnUntouchedBankRepresentation() {
        val bank = SeatTime(seats().first().id, 30.seconds, 30.seconds)
        rejected(clock = clock().copy(seats = SnapshotList.of(bank)))
        rejected(clock = clock().copy(shared = SharedCountdown(30.seconds, 30.seconds)))
        val setup = settings(SeatClockMode.SHARED); val shared = clock(SeatClockMode.SHARED)
        rejected(setup, clock = shared.copy(shared = null))
        rejected(setup, clock = shared.copy(seats = SnapshotList.of(bank)))
        rejected(setup, clock = shared.copy(shared = SharedCountdown(30.seconds, 29.seconds)))
        rejected(setup, clock = shared.copy(shared = SharedCountdown(30.seconds, (-1).seconds)))
        rejected(setup, clock = shared.copy(shared = SharedCountdown(30.seconds, 31.seconds)))
    }
    @Test fun seatBankModesRejectMissingDuplicateForeignAndSharedBanks() {
        for (mode in listOf(SeatClockMode.CHESS, SeatClockMode.PER_TURN)) {
            val setup = settings(mode); val clock = clock(mode); val first = clock.seats.first()
            rejected(setup, clock = clock.copy(seats = SnapshotList.empty()))
            rejected(setup, clock = clock.copy(seats = SnapshotList.of(first)))
            rejected(setup, clock = clock.copy(seats = SnapshotList.of(first, first)))
            rejected(setup, clock = clock.copy(seats = SnapshotList.of(first, clock.seats.last().copy(seatId = EntityId("foreign")))))
            rejected(setup, clock = clock.copy(seats = SnapshotList.copyOf(clock.seats + SeatTime(EntityId("extra"), 30.seconds, 30.seconds))))
            rejected(setup, clock = clock.copy(shared = SharedCountdown(30.seconds, 30.seconds)))
        }
    }
    @Test fun seatBanksRejectNonfiniteOrPreviouslyConsumedBalancesAtCreationBoundary() {
        for (mode in listOf(SeatClockMode.CHESS, SeatClockMode.PER_TURN)) {
            val setup = settings(mode); val clock = clock(mode)
            val invalidBanks = listOf(
                SeatTime(clock.seats.first().seatId, Duration.INFINITE, Duration.INFINITE),
                SeatTime(clock.seats.first().seatId, 30.seconds, Duration.INFINITE),
                SeatTime(clock.seats.first().seatId, 30.seconds, -Duration.INFINITE),
                SeatTime(clock.seats.first().seatId, 30.seconds, 29.seconds),
                SeatTime(clock.seats.first().seatId, 30.seconds, (-1).seconds),
                SeatTime(clock.seats.first().seatId, 30.seconds, 31.seconds))
            invalidBanks.forEach { bank -> rejected(setup, clock = clock.copy(seats = SnapshotList.of(bank, clock.seats.last()))) }
        }
    }
}
