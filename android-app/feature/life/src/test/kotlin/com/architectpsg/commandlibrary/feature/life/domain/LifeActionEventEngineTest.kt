package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.life.persistence.*
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.*
import org.junit.Test

class LifeActionEventEngineTest {
    private val reading = GameTimeObservation(10.seconds, UtcTimestamp(10), EntityId("process"))
    private val groupId = EntityId("group")
    private fun <T> ok(outcome: Outcome<T>): T { assertTrue("Expected success, got $outcome", outcome is Outcome.Success); return (outcome as Outcome.Success).value }
    private fun creation(): GameCreation {
        val defs = SnapshotList.copyOf(LifeFixtures.definitions().map { it.copy(role = when(it.id) {
            LifeFixtures.life -> CounterRole.LIFE
            LifeFixtures.commanderDamage -> CounterRole.COMMANDER_DAMAGE
            LifeFixtures.commanderTax -> CounterRole.COMMANDER_TAX
            else -> CounterRole.OTHER
        }) })
        return GameCreation(EntityId("operation"), LifeFixtures.snapshot(definitions = defs).settings, LifeFixtures.seats(), LifeFixtures.idleClock())
    }
    private fun game() = ok(LifeGameInitializer.initialize(creation(), LifeFixtures.gameId, reading, EntityId("opening")))
    private fun allocation(current: GameCheckpoint, id: Long = 1) = ActionEventAllocation(id, Revision(current.snapshot.revision.value + 1), groupId, EntityId("result"), EntityId("next-turn"))
    private fun packet(before: GameCheckpoint, result: ActionEventReduction) {
        lifeActionPacket(StoredLifeGame(creation(), before, 0), result.archive.event.id, LifeActionWrite(result.archive, result.checkpoint))
        lifeResultChange(before.snapshot, result.checkpoint.snapshot)
    }
    @Test fun mixedGroupCanonicalizesOnceAndPassesActualPacketGuards() {
        val input = game().copy(recoveredProcessSessionId = EntityId("recovered"))
        val action = GameAction.Group(SnapshotList.of(GameAction.SetCounter(LifeFixtures.seatA, LifeFixtures.life, null, 35),
            GameAction.RenameSeat(LifeFixtures.seatB, "Exact name"), GameAction.SetMonarch(LifeFixtures.seatA)), groupId)
        val result = ok(LifeActionEventEngine.dispatch(input, action, reading, allocation(input)))
        val logged = result.archive.event.action as GameAction.Group
        assertEquals(GameAction.AdjustCounter(LifeFixtures.seatA, LifeFixtures.life, null, -5), logged.actions.first())
        assertEquals(Revision(0), result.archive.provenance.before.revision); assertEquals(Revision(1), result.checkpoint.snapshot.revision)
        assertEquals(Knowledge.Known(1L), result.checkpoint.lastSettledEventId); assertEquals(input.recoveredProcessSessionId, result.checkpoint.recoveredProcessSessionId)
        assertEquals("Exact name", result.checkpoint.snapshot.seats.single { it.id == LifeFixtures.seatB }.playerName)
        assertTrue(result.archive.event.randomResults.isEmpty()); packet(input, result)
    }
    @Test fun invalidLateGroupReturnsNoPacketAndLeavesInputUntouched() {
        val input = game()
        val action = GameAction.Group(SnapshotList.of(GameAction.SetCounter(LifeFixtures.seatA, LifeFixtures.life, null, 35),
            GameAction.RenameSeat(EntityId("foreign"), "Invalid")), groupId)
        assertFalse(LifeActionEventEngine.dispatch(input, action, reading, allocation(input)) is Outcome.Success)
        assertEquals(game(), input)
    }
    @Test fun globalTurnResetOccursExactlyOnceAndFinalCandidatesIgnoreTransientThreshold() {
        val input = game()
        val original = input.snapshot
        val defs = SnapshotList.copyOf(original.settings.counterDefinitions.map { if(it.id == LifeFixtures.charge) it.copy(resetPolicy = CounterResetPolicy.ON_TURN_START) else it })
        val resetInput = input.copy(snapshot = original.copy(settings = original.settings.copy(counterDefinitions = defs), counters = SnapshotList.copyOf(original.counters.map {
            if(it.definitionId == LifeFixtures.charge) it.copy(value = 5, hidden = true) else it
        })))
        val action = GameAction.Group(SnapshotList.of(GameAction.SwitchTurn(LifeFixtures.seatA),
            GameAction.SetCounter(LifeFixtures.seatA, LifeFixtures.life, null, 0), GameAction.SetCounter(LifeFixtures.seatA, LifeFixtures.life, null, 40)), groupId)
        val result = ok(LifeActionEventEngine.dispatch(resetInput, action, reading, allocation(resetInput)))
        assertEquals(1L, result.checkpoint.snapshot.turnNumber); assertEquals(Knowledge.Known(EntityId("next-turn")), result.checkpoint.snapshot.elapsed.turnLineageId)
        assertTrue(result.checkpoint.snapshot.counters.filter { it.definitionId == LifeFixtures.charge }.all { it.value == 2 && it.hidden })
        assertTrue(result.newlyReached.isEmpty()); packet(resetInput, result)
    }
    @Test fun finishGroupSharesOneResultRevisionAndRejectsMultipleResultControls() {
        val input = game()
        val action = GameAction.Group(SnapshotList.of(GameAction.SetCounter(LifeFixtures.seatA, LifeFixtures.life, null, 0),
            GameAction.Finish(SnapshotList.of(LifeFixtures.seatB), "Explicit")), groupId)
        val result = ok(LifeActionEventEngine.dispatch(input, action, reading, allocation(input)))
        val after = result.checkpoint.snapshot
        assertEquals(after.revision, (after.result as Knowledge.Known).value.revision); assertEquals(GameStatus.COMPLETED, after.status)
        packet(input, result)
        val bad = GameAction.Group(SnapshotList.of(GameAction.Finish(SnapshotList.empty(), "Draw"), GameAction.Reopen("Again")), groupId)
        assertFalse(LifeActionEventEngine.dispatch(input, bad, reading, allocation(input)) is Outcome.Success)
    }
    @Test fun recordedRandomPacketReplaysExactRecordAndMixedGroupConsumesNoDraws() {
        val input = game(); var draws = 0
        val source = object : RandomSource { override fun nextInt(upperBound: Int): Int { draws++; return 0 } }
        val action = GameAction.RequestRandom(RandomRequest.Coin(EntityId("request")))
        val result = ok(LifeActionEventEngine.dispatch(input, action, reading, allocation(input), RandomExecution.Resolve(source)))
        assertEquals(1, draws); assertEquals(1, result.archive.event.randomResults.size); packet(input, result)
        val record = result.archive.event.randomResults.single().copy(at = UtcTimestamp(-1))
        val replay = ok(LifeActionEventEngine.dispatch(input, action, reading, allocation(input), RandomExecution.Replay(record)))
        assertEquals(record, replay.archive.event.randomResults.single()); assertEquals(1, draws)
        val mixed = GameAction.Group(SnapshotList.of(action, GameAction.RenameSeat(LifeFixtures.seatA, "Other")), groupId)
        assertFalse(LifeActionEventEngine.dispatch(input, mixed, reading, allocation(input), RandomExecution.Resolve(source)) is Outcome.Success)
        assertEquals(1, draws)
    }
    @Test fun undoPacketUsesSettledCurrentAuditAndStableResultRetraction() {
        val input = game()
        val finished = ok(LifeActionEventEngine.dispatch(input, GameAction.Finish(SnapshotList.empty(), "Draw"), reading, allocation(input)))
        val nextAllocation = allocation(finished.checkpoint, 2).copy(freshResultId = null)
        val undone = ok(LifeActionEventEngine.undo(finished.checkpoint, finished.archive, reading, nextAllocation))
        assertEquals(finished.checkpoint.snapshot.revision, undone.archive.provenance.before.revision)
        assertEquals(Knowledge.Known(1L), undone.archive.event.undoOfEventId)
        assertTrue((undone.checkpoint.snapshot.result as Knowledge.Known).value.retracted)
        assertTrue(undone.archive.event.randomResults.isEmpty()); packet(finished.checkpoint, undone)
    }
    private fun running(policy: ClockZeroPolicy = ClockZeroPolicy.ALERT, mode: SeatClockMode = SeatClockMode.CHESS): GameCheckpoint {
        val input = game(); val anchor = ClockAnchor(reading.wall, reading.monotonic, reading.processSessionId)
        val clock = SeatClockState(mode, SnapshotList.copyOf(input.snapshot.seats.map { SeatTime(it.id, 20.seconds,
            if(it.id == LifeFixtures.seatA) 1.seconds else if(it.id == LifeFixtures.seatB) 5.seconds else 7.seconds) }),
            LifeFixtures.seatA, anchor, false, policy, ClockUndoPolicy.KEEP_ELAPSED, SnapshotList.empty())
        return input.copy(snapshot = input.snapshot.copy(status = GameStatus.ACTIVE, activeSeatId = LifeFixtures.seatA, turnNumber = 1,
            settings = input.snapshot.settings.copy(clockMode = mode), clock = clock,
            elapsed = ElapsedTimerState(3.seconds, 1.seconds, false, anchor, Knowledge.Known(EntityId("turn-before")))))
    }
    @Test fun explicitPauseAtAutoPauseBoundaryMatchesDirectHelperAndRetainsOccurrence() {
        val now = GameTimeObservation(12.seconds, UtcTimestamp(12), reading.processSessionId)
        for(policy in listOf(ClockZeroPolicy.PAUSE, ClockZeroPolicy.ELIMINATE)) {
            val input = running(policy)
            val direct = ok(LifeTimingControls.countdownPause(input.snapshot, true, now))
            val result = ok(LifeActionEventEngine.dispatch(input, GameAction.ClockPause(true), now, allocation(input)))
            assertEquals(direct.snapshot.clock, result.checkpoint.snapshot.clock)
            assertEquals(1, result.alerts.size); assertEquals(direct.timedOutSeats, result.timedOutSeats)
            val repeated = GameAction.Group(SnapshotList.of(GameAction.ClockPause(true), GameAction.ClockPause(true)), groupId)
            assertFalse(LifeActionEventEngine.dispatch(input, repeated, now, allocation(input)) is Outcome.Success)
            packet(input, result)
        }
        val paused = game().copy(snapshot = running().snapshot.copy(clock = running().snapshot.clock.copy(paused = true)))
        assertFalse(LifeActionEventEngine.dispatch(paused, GameAction.ClockPause(true), now, allocation(paused)) is Outcome.Success)
    }
    @Test fun switchThenPassRefillsOnlySwitchTargetAndUsesOneObservation() {
        val input = running(mode = SeatClockMode.PER_TURN)
        val now = GameTimeObservation(12.seconds, UtcTimestamp(12), reading.processSessionId)
        val action = GameAction.Group(SnapshotList.of(GameAction.SwitchTurn(LifeFixtures.seatB), GameAction.PassClock(LifeFixtures.seatC)), groupId)
        val result = ok(LifeActionEventEngine.dispatch(input, action, now, allocation(input)))
        val after = result.checkpoint.snapshot
        assertEquals(LifeFixtures.seatB, after.activeSeatId); assertEquals(LifeFixtures.seatC, after.clock.activeSeat)
        assertEquals(20.seconds, after.clock.seats.single { it.seatId == LifeFixtures.seatB }.remaining)
        assertEquals(7.seconds, after.clock.seats.single { it.seatId == LifeFixtures.seatC }.remaining)
        assertEquals(5.seconds, after.elapsed.gameElapsed); assertEquals(0.seconds, after.elapsed.turnElapsed)
        assertEquals(2L, after.turnNumber); packet(input, result)
    }
    @Test fun finishThenAcknowledgmentReturnsOneNewOccurrenceWithFinalAcknowledgment() {
        val input = running(); val now = GameTimeObservation(12.seconds, UtcTimestamp(12), reading.processSessionId)
        val id = ok(LifeTimingEngine.settle(input.snapshot, now)).alerts.single().id
        val action = GameAction.Group(SnapshotList.of(GameAction.Finish(SnapshotList.empty(), "Explicit draw"), GameAction.AcknowledgeGameAlert(id)), groupId)
        val result = ok(LifeActionEventEngine.dispatch(input, action, now, allocation(input)))
        assertEquals(1, result.alerts.size); assertEquals(now.wall, result.alerts.single().acknowledgedAt)
        assertEquals(1, result.checkpoint.snapshot.alerts.size); packet(input, result)
    }
    @Test fun exhaustedRevisionAndMismatchedGroupOrMissingLineageFailBeforePacket() {
        val input = game()
        val action = GameAction.Group(SnapshotList.of(GameAction.RenameSeat(LifeFixtures.seatA, "Name")), groupId)
        assertFalse(LifeActionEventEngine.dispatch(input, action, reading, allocation(input).copy(groupId = EntityId("other"))) is Outcome.Success)
        assertFalse(LifeActionEventEngine.dispatch(input, GameAction.SwitchTurn(LifeFixtures.seatA), reading, allocation(input).copy(turnLineage = null)) is Outcome.Success)
        val exhausted = input.copy(snapshot = input.snapshot.copy(revision = Revision(Long.MAX_VALUE)))
        assertFalse(LifeActionEventEngine.dispatch(exhausted, action, reading, ActionEventAllocation(1, Revision(0), groupId)) is Outcome.Success)
    }
    private fun variantInput(): GameCheckpoint {
        val input = game(); val provider = LifeFixtures.provider; val absent = Knowledge.Absent("Synthetic source absence")
        val provenance = Provenance(provider, null, absent, absent, UtcTimestamp(0), absent, null, null, null)
        val definition = VariantDefinition(ProviderIdentity(provider, "variant", "one"), VariantNamespace.CUSTOM, "1", "Synthetic",
            SnapshotList.of(VariantActionDefinition("step", "Step", "Synthetic", "deck", "deck", 1)),
            SnapshotList.of(VariantZoneDefinition("deck", "Deck", 0, true)), SnapshotList.of("main"), SnapshotList.empty(), provenance,
            SourceNotice(provider, "Synthetic", absent, "Test", null))
        val state = VariantState(definition.id, "1", SnapshotList.of(VariantZone("deck", SnapshotList.empty())), SnapshotList.empty(), SnapshotList.empty(),
            "main", SnapshotList.empty(), 0, SnapshotList.empty())
        return input.copy(snapshot = input.snapshot.copy(settings = input.snapshot.settings.copy(variantDefinitions = SnapshotList.of(definition)), variants = SnapshotList.of(state)))
    }
    @Test fun variantBackendCanOnlyReturnMatchingValidTargetStateAndCannotBypassPreflight() {
        val input = variantInput(); val state = input.snapshot.variants.single()
        val action = GameAction.Variant(state.definitionId, "step", SnapshotList.empty())
        val missing = LifeActionEventEngine.dispatch(input, action, reading, allocation(input))
        assertTrue(missing is Outcome.Unavailable)
        val next = state.copy(actionCounts = SnapshotList.of(VariantActionCount("step", 1)))
        val result = ok(LifeActionEventEngine.dispatch(input, action, reading, allocation(input), variants = LifeVariantActionBackend { actual, command ->
            assertEquals(input.snapshot, actual); assertEquals(action, command); Outcome.Success(next)
        }))
        assertEquals(SnapshotList.of(next), result.checkpoint.snapshot.variants)
        assertEquals(input.snapshot.counters, result.checkpoint.snapshot.counters); packet(input, result)
        for(bad in listOf(next.copy(version = "other"), next.copy(definitionId = ProviderIdentity(LifeFixtures.provider, "variant", "redirect")), next.copy(phase = "missing"))) {
            assertFalse(LifeActionEventEngine.dispatch(input, action, reading, allocation(input), variants = LifeVariantActionBackend { _, _ -> Outcome.Success(bad) }) is Outcome.Success)
        }
        var called = false
        val backend = LifeVariantActionBackend { _, _ -> called = true; Outcome.Success(next) }
        assertFalse(LifeActionEventEngine.dispatch(result.checkpoint, action, reading, allocation(result.checkpoint), variants = backend) is Outcome.Success)
        assertFalse(called)
        val status = Outcome.Unknown("Missing source authority", SnapshotList.empty())
        assertSame(status, LifeActionEventEngine.dispatch(input, action, reading, allocation(input), variants = LifeVariantActionBackend { _, _ -> status }))
    }
}
