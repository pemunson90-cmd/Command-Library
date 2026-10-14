package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.life.persistence.validateInitialGame
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.*
import org.junit.Test

class LifeGameInitializerTest {
    private val lineage = EntityId("fresh-opening")
    private val reading = GameTimeObservation(999.seconds, UtcTimestamp(12), EntityId("process"))
    private fun input(seats: SnapshotList<Seat> = LifeFixtures.seats(), mode: SeatClockMode = SeatClockMode.NONE): GameCreation {
        val defs = SnapshotList.copyOf(LifeFixtures.definitions().map { it.copy(role = when(it.id) {
            LifeFixtures.life -> CounterRole.LIFE
            LifeFixtures.commanderDamage -> CounterRole.COMMANDER_DAMAGE
            LifeFixtures.commanderTax -> CounterRole.COMMANDER_TAX
            else -> CounterRole.OTHER
        }) })
        val clock = LifeFixtures.idleClock().copy(mode = mode,
            seats = if(mode == SeatClockMode.CHESS || mode == SeatClockMode.PER_TURN) SnapshotList.copyOf(seats.map { SeatTime(it.id, 20.seconds, 20.seconds) }) else SnapshotList.empty(),
            shared = if(mode == SeatClockMode.SHARED) SharedCountdown(30.seconds, 30.seconds) else null)
        return GameCreation(EntityId("operation"), LifeFixtures.snapshot(definitions = defs, clock = clock).settings, seats, clock)
    }
    private fun initialize(input: GameCreation, backend: LifeVariantInitializer? = null): GameCheckpoint {
        val result = LifeGameInitializer.initialize(input, LifeFixtures.gameId, reading, lineage, backend)
        assertTrue("Expected initialized game, got $result", result is Outcome.Success)
        return (result as Outcome.Success).value
    }
    @Test fun everyClockModeAndSeatCountPreservesCreationAndPassesActualStoreGuard() {
        for(count in 1..6) for(mode in SeatClockMode.entries) {
            val seats = SnapshotList.copyOf((0 until count).map { LifeFixtures.seat(EntityId("seat-$it"), it) })
            val input = input(seats, mode)
            val checkpoint = initialize(input)
            validateInitialGame(input, checkpoint)
            val game = checkpoint.snapshot
            assertEquals(input.settings, game.settings); assertEquals(input.seats, game.seats); assertEquals(input.clock, game.clock)
            assertEquals(Revision(0), game.revision); assertEquals(1, game.version); assertEquals(reading.wall, game.startedAt)
            assertEquals(GameStatus.PAUSED, game.status); assertEquals(0L, game.turnNumber); assertNull(game.activeSeatId)
            assertEquals(ElapsedTimerState(Duration.ZERO, Duration.ZERO, true, null, Knowledge.Known(lineage)), game.elapsed)
            assertTrue(game.lastEventId is Knowledge.Absent); assertTrue(checkpoint.lastSettledEventId is Knowledge.Absent)
            assertNull(checkpoint.recoveredProcessSessionId); assertTrue(game.result is Knowledge.Absent)
        }
    }
    @Test fun counterScopesRetainPartnerIdentityAndConfiguredInitialValues() {
        val input = input()
        val game = initialize(input).snapshot
        for(def in input.settings.counterDefinitions) for(seat in input.seats) {
            val rows = game.counters.filter { it.definitionId == def.id && it.seatId == seat.id }
            val slots = when(def.role) {
                CounterRole.COMMANDER_DAMAGE -> input.seats.filter { it.id != seat.id }.flatMap { it.commanders.map { it.id } }
                CounterRole.COMMANDER_TAX -> seat.commanders.map { it.id }
                else -> listOf(null)
            }
            assertEquals(slots.toSet(), rows.map { it.commanderSlotId }.toSet()); assertEquals(slots.size, rows.size)
            assertTrue(rows.all { it.value == def.initial && !it.hidden })
        }
        assertEquals(2, game.counters.count { it.definitionId == LifeFixtures.commanderDamage && it.seatId == LifeFixtures.seatA })
    }
    @Test fun practiceOwnCommanderIsNotPrepopulatedAsOpposingDamage() {
        val input = input(SnapshotList.of(LifeFixtures.seats().first()))
        val game = initialize(input).snapshot
        assertFalse(game.counters.any { it.definitionId == LifeFixtures.commanderDamage })
        assertEquals(1, game.counters.count { it.definitionId == LifeFixtures.commanderTax })
        assertEquals(input.seats, game.seats)
    }
    @Test fun emptyVariantDefinitionsNeverInvokeBackend() {
        val game = initialize(input(), LifeVariantInitializer { error("Unexpected backend call") }).snapshot
        assertTrue(game.variants.isEmpty()); assertTrue(game.alerts.isEmpty()); assertEquals(GameDesignations(), game.designations)
    }
    private fun variantInput(): Pair<GameCreation, VariantState> {
        // Synthetic declared placement only, no source or real variant authority.
        val provider = LifeFixtures.provider
        val absent = Knowledge.Absent("Synthetic source absence")
        val provenance = Provenance(provider, null, absent, absent, UtcTimestamp(0), absent, null, null, null)
        val definition = VariantDefinition(ProviderIdentity(provider, "variant", "one"), VariantNamespace.CUSTOM, "1", "Synthetic",
            SnapshotList.empty(), SnapshotList.of(VariantZoneDefinition("deck", "Deck", 0, true)),
            SnapshotList.of("main"), SnapshotList.empty(), provenance, SourceNotice(provider, "Synthetic", absent, "Test", null))
        val input = input()
        return input.copy(settings = input.settings.copy(variantDefinitions = SnapshotList.of(definition))) to
            VariantState(definition.id, "1", SnapshotList.of(VariantZone("deck", SnapshotList.empty())), SnapshotList.empty(),
                SnapshotList.empty(), "main", SnapshotList.empty(), 123, SnapshotList.empty())
    }
    @Test fun missingVariantPolicyIsUnsupportedAndBackendOutcomesPassThroughUnchanged() {
        val (input, _) = variantInput()
        val missing = LifeGameInitializer.initialize(input, LifeFixtures.gameId, reading, lineage)
        assertTrue(missing is Outcome.Unavailable)
        assertEquals(UnavailableReason.UNSUPPORTED, (missing as Outcome.Unavailable).reason)
        val statuses: List<Outcome<SnapshotList<VariantState>>> = listOf(
            Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "No authentic placement policy"),
            Outcome.Unknown("Missing version evidence", SnapshotList.empty()),
            invalidInput("Backend rejected declared policy"))
        for(status in statuses) assertSame(status, LifeGameInitializer.initialize(input, LifeFixtures.gameId, reading, lineage, LifeVariantInitializer { status }))
    }
    @Test fun explicitBackendStateMustMatchWholeDeclaredGraph() {
        val (input, state) = variantInput()
        val valid = initialize(input, LifeVariantInitializer { actual -> assertEquals(input, actual); Outcome.Success(SnapshotList.of(state)) })
        assertEquals(SnapshotList.of(state), valid.snapshot.variants)
        validateInitialGame(input, valid)
        for(states in listOf(SnapshotList.empty(), SnapshotList.of(state, state), SnapshotList.of(state.copy(version = "other")),
            SnapshotList.of(state.copy(definitionId = ProviderIdentity(LifeFixtures.provider, "variant", "extra"))),
            SnapshotList.of(state.copy(zones = SnapshotList.empty())), SnapshotList.of(state.copy(phase = "missing")))) {
            val result = LifeGameInitializer.initialize(input, LifeFixtures.gameId, reading, lineage, LifeVariantInitializer { Outcome.Success(states) })
            assertTrue("Malformed new graph: $result", result is Outcome.Failure)
            assertEquals(FailureCode.INVALID_INPUT, (result as Outcome.Failure).error.code)
        }
    }
    @Test fun richMetadataAndAlreadyReachedLossThresholdRemainExactWithoutAutomaticElimination() {
        val original = input()
        val art = GameArtReference.CachedCard(LifeFixtures.identity("unresolved-art"), EntityId("cached-asset"))
        val seats = SnapshotList.copyOf(original.seats.map { it.copy(playerName = "  名字  ", profileId = EntityId("profile"),
            deck = Knowledge.Unknown("Unresolved deck version", SnapshotList.empty()),
            appearance = SeatAppearance(DisplayColor(0xff123456), art, ArtFit.CONTAIN, ArtCrop(0.1, 0.2, 0.8, 0.9), SeatOrientation.INVERTED)) })
        val defs = SnapshotList.copyOf(original.settings.counterDefinitions.map { if(it.role == CounterRole.LIFE) it.copy(lossPolicy = CounterLossPolicy.AtOrBelow(it.initial)) else it })
        val input = original.copy(seats = seats, settings = original.settings.copy(counterDefinitions = defs,
            presentation = GamePresentation(GameLayoutMode.OPERATOR, "exact-layout", true), adjustmentStep = 7, deductLifeForCommanderDamage = true))
        val checkpoint = initialize(input)
        validateInitialGame(input, checkpoint)
        assertEquals(input.settings, checkpoint.snapshot.settings); assertEquals(seats, checkpoint.snapshot.seats)
        assertTrue(checkpoint.snapshot.seats.all { it.participation == SeatParticipation.Active })
        assertTrue(checkpoint.snapshot.result is Knowledge.Absent)
    }
}
