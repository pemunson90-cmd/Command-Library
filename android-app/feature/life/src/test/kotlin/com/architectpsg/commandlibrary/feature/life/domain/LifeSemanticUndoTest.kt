package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.life.persistence.*
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.*
import org.junit.Test

class LifeSemanticUndoTest {
    private val reading = GameTimeObservation(10.seconds, UtcTimestamp(10), EntityId("process"))
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
    private fun game() = ok(LifeGameInitializer.initialize(creation(), LifeFixtures.gameId, reading, EntityId("opening"))).snapshot
    private fun archive(before: GameSnapshot, after: GameSnapshot, action: GameAction, id: Long): GameEventArchive {
        val next = after.copy(revision = Revision(before.revision.value + 1), lastEventId = Knowledge.Known(id))
        return GameEventArchive(1, GameEvent(id, before.id, (action as? GameAction.Group)?.groupId ?: EntityId("group-$id"), action, reading.wall,
            Knowledge.Absent("User action"), next.revision), GameActionProvenance(id, before, next, reading))
    }
    private fun undo(target: GameEventArchive, current: GameSnapshot = target.provenance.afterAction, event: Long = 50): SemanticUndoReduction =
        ok(LifeSemanticUndo.undo(target, current, reading, ResultEventAllocation(event, Revision(current.revision.value + 1))))
    private fun checkPacket(current: GameSnapshot, reduction: SemanticUndoReduction, target: GameEventArchive, id: Long) {
        val after = reduction.snapshot
        val event = GameEvent(id, current.id, (target.event.action as? GameAction.Group)?.groupId ?: EntityId("undo-$id"), target.event.action, reading.wall, Knowledge.Known(target.event.id), after.revision)
        val packet = GameEventArchive(1, event, GameActionProvenance(id, reduction.settledCurrent, after, reading))
        lifeActionPacket(StoredLifeGame(creation(), GameCheckpoint(current, current.lastEventId, null), 0), id,
            LifeActionWrite(packet, GameCheckpoint(after, Knowledge.Known(id), null)))
        lifeResultChange(current, after)
    }
    @Test fun counterPreferenceAndPresentationRestoreWithoutMutatingOriginals() {
        val before = game()
        val action = GameAction.Group(SnapshotList.of(GameAction.SetCounter(LifeFixtures.seatA, LifeFixtures.life, null, 11), GameAction.SetAdjustmentStep(9)), EntityId("edit"))
        val after = ok(LifeCounterEngine.reduce(before, action)).snapshot
        val target = archive(before, after, action, 1)
        val reduced = undo(target)
        assertEquals(before.settings, reduced.snapshot.settings); assertEquals(before.counters, reduced.snapshot.counters)
        assertEquals(Revision(2), reduced.snapshot.revision); assertEquals(Knowledge.Known(50L), reduced.snapshot.lastEventId)
        assertEquals(11, after.counters.single { it.definitionId == LifeFixtures.life && it.seatId == LifeFixtures.seatA }.value)
        checkPacket(target.provenance.afterAction, reduced, target, 50)
    }
    @Test fun addedDefinitionsRemainHiddenAndRepeatedUndoCarriesLaterTombstones() {
        val original = game()
        val a = CounterDefinition(EntityId("added-a"), "A", 0, 9, 3, CounterLossPolicy.None, true)
        val b = a.copy(id = EntityId("added-b"), label = "B", initial = 4)
        val addA = archive(original, ok(LifeCounterEngine.reduce(original, GameAction.AddCounterDefinition(a))).snapshot, GameAction.AddCounterDefinition(a), 1)
        val addB = archive(addA.provenance.afterAction, ok(LifeCounterEngine.reduce(addA.provenance.afterAction, GameAction.AddCounterDefinition(b))).snapshot, GameAction.AddCounterDefinition(b), 2)
        val undoB = undo(addB, event = 3); checkPacket(addB.provenance.afterAction, undoB, addB, 3)
        val undoA = undo(addA, undoB.snapshot, 4); checkPacket(undoB.snapshot, undoA, addA, 4)
        assertTrue(undoA.snapshot.settings.counterDefinitions.containsAll(listOf(a,b)))
        for(d in listOf(a,b)) {
            val rows = undoA.snapshot.counters.filter { it.definitionId == d.id }
            assertEquals(original.seats.size, rows.size); assertTrue(rows.all { it.value == d.initial && it.hidden })
        }
    }
    @Test fun undoFinishRetainsCurrentConfirmationFactsAndStableIdentity() {
        val before = game()
        val action = GameAction.Finish(SnapshotList.of(LifeFixtures.seatA), "First finish")
        val result = ok(LifeResultEngine.reduce(before, action, reading, ResultEventAllocation(1, Revision(1), EntityId("result"))))
        val target = archive(before, result.snapshot, action, 1)
        val reduced = undo(target)
        val confirmed = (target.provenance.afterAction.result as Knowledge.Known).value
        assertEquals(confirmed.copy(revision = reduced.snapshot.revision, retracted = true), (reduced.snapshot.result as Knowledge.Known).value)
        assertEquals(GameStatus.PAUSED, reduced.snapshot.status); assertTrue(reduced.snapshot.endedAt is Knowledge.Absent)
        checkPacket(target.provenance.afterAction, reduced, target, 50)
    }
    @Test fun undoReopenRestoresFullHistoricalConfirmationWithCurrentStableIdentity() {
        val original = game()
        val finish = ok(LifeResultEngine.reduce(original, GameAction.Finish(SnapshotList.empty(), "Draw"), reading, ResultEventAllocation(1, Revision(1), EntityId("result")))).snapshot
        val action = GameAction.Reopen("Correction")
        val reopened = ok(LifeResultEngine.reduce(finish, action, reading, ResultEventAllocation(2, Revision(2)))).snapshot
        val target = archive(finish, reopened, action, 2)
        val reduced = undo(target)
        val old = (finish.result as Knowledge.Known).value
        assertEquals(old.copy(revision = reduced.snapshot.revision), (reduced.snapshot.result as Knowledge.Known).value)
        assertEquals(GameStatus.COMPLETED, reduced.snapshot.status)
        checkPacket(reopened, reduced, target, 50)
    }
    @Test fun completedAcknowledgmentUndoPreservesAcknowledgmentAndResultRevision() {
        val original = game()
        val finished = ok(LifeResultEngine.reduce(original, GameAction.Finish(SnapshotList.empty(), "Draw"), reading, ResultEventAllocation(1, Revision(1), EntityId("result")))).snapshot
        val alert = GameAlert(EntityId("alert"), finished.id, GameAlertTarget.GameTimer, UtcTimestamp(1), null)
        val before = finished.copy(alerts = SnapshotList.of(alert))
        val action = GameAction.AcknowledgeGameAlert(alert.id)
        val changed = ok(LifeTableEngine.reduce(before, action, reading)).snapshot
        val target = archive(before, changed, action, 2)
        val reduced = undo(target)
        assertEquals(changed.alerts, reduced.snapshot.alerts); assertEquals(changed.result, reduced.snapshot.result)
        checkPacket(target.provenance.afterAction, reduced, target, 50)
    }
    @Test fun unknownOrUndoTargetMetadataAndBadAllocationNeverProduceSuccess() {
        val before = game(); val target = archive(before, before.copy(settings = before.settings.copy(adjustmentStep = 2)), GameAction.SetAdjustmentStep(2), 1)
        for(metadata in listOf(Knowledge.Known(0L), Knowledge.Unknown("Unrepaired target", SnapshotList.empty()))) {
            val bad = target.copy(event = target.event.copy(undoOfEventId = metadata))
            assertFalse(LifeSemanticUndo.undo(bad, target.provenance.afterAction, reading, ResultEventAllocation(5, Revision(2))) is Outcome.Success)
        }
        assertFalse(LifeSemanticUndo.undo(target, target.provenance.afterAction, reading, ResultEventAllocation(5, Revision(8))) is Outcome.Success)
    }
    @Test fun groupedAdditionUpdateAndValueUndoRetainsFinalMetadataAsHiddenInitial() {
        val before = game()
        val added = CounterDefinition(EntityId("added"), "First", 0, 20, 2, CounterLossPolicy.None, true)
        val updated = added.copy(label = "Final metadata", initial = 4)
        val action = GameAction.Group(SnapshotList.of(GameAction.AddCounterDefinition(added), GameAction.UpdateCounterDefinition(updated),
            GameAction.SetCounter(LifeFixtures.seatA, added.id, null, 9)), EntityId("atomic-add-update"))
        val after = ok(LifeCounterEngine.reduce(before, action)).snapshot
        val target = archive(before, after, action, 1)
        val reduced = undo(target)
        assertEquals(updated, reduced.snapshot.settings.counterDefinitions.single { it.id == added.id })
        val rows = reduced.snapshot.counters.filter { it.definitionId == added.id }
        assertEquals(before.seats.size, rows.size); assertTrue(rows.all { it.value == 4 && it.hidden })
        assertEquals(9, after.counters.single { it.definitionId == added.id && it.seatId == LifeFixtures.seatA }.value)
        checkPacket(target.provenance.afterAction, reduced, target, 50)
    }
    @Test fun undoReopenAfterLaterRefinishUndoRestoresEarlierConfirmationFacts() {
        val before = game()
        val first = ok(LifeResultEngine.reduce(before, GameAction.Finish(SnapshotList.empty(), "Earlier draw"), reading, ResultEventAllocation(1, Revision(1), EntityId("result")))).snapshot
        val reopenAction = GameAction.Reopen("Revisit")
        val reopened = ok(LifeResultEngine.reduce(first, reopenAction, reading, ResultEventAllocation(2, Revision(2)))).snapshot
        val reopen = archive(first, reopened, reopenAction, 2)
        val finishAction = GameAction.Finish(SnapshotList.of(LifeFixtures.seatB), "Later winner")
        val refinish = ok(LifeResultEngine.reduce(reopened, finishAction, reading, ResultEventAllocation(3, Revision(3)))).snapshot
        val finish = archive(reopened, refinish, finishAction, 3)
        val undoneFinish = undo(finish, event = 4)
        assertEquals("Later winner", (undoneFinish.snapshot.result as Knowledge.Known).value.reason)
        checkPacket(refinish, undoneFinish, finish, 4)
        val undoneReopen = undo(reopen, undoneFinish.snapshot, 5)
        assertEquals((first.result as Knowledge.Known).value.copy(revision = Revision(5)), (undoneReopen.snapshot.result as Knowledge.Known).value)
        checkPacket(undoneFinish.snapshot, undoneReopen, reopen, 5)
    }
    @Test fun legacyWinnerOnlyReconfirmationNeedsExplicitRepair() {
        val before = game()
        val first = ok(LifeResultEngine.reduce(before, GameAction.Finish(SnapshotList.of(LifeFixtures.seatA), "Historical"), reading, ResultEventAllocation(1, Revision(1), EntityId("result")))).snapshot
        val legacy = first.copy(result = Knowledge.Known((first.result as Knowledge.Known).value.copy(seatResults = SnapshotList.empty())))
        val action = GameAction.Reopen("Correction")
        val reopened = ok(LifeResultEngine.reduce(legacy, action, reading, ResultEventAllocation(2, Revision(2)))).snapshot
        val target = archive(legacy, reopened, action, 2)
        val outcome = LifeSemanticUndo.undo(target, reopened, reading, ResultEventAllocation(3, Revision(3)))
        assertTrue(outcome is Outcome.Unavailable); assertEquals(UnavailableReason.UNSUPPORTED, (outcome as Outcome.Unavailable).reason)
    }
    @Test fun semanticUndoKeepsCurrentElapsedAndAcknowledgmentWhileRestoringTableState() {
        val initial = game()
        val anchor = ClockAnchor(reading.wall, reading.monotonic, reading.processSessionId)
        val before = initial.copy(status = GameStatus.ACTIVE, activeSeatId = LifeFixtures.seatA, turnNumber = 1,
            elapsed = ElapsedTimerState(2.seconds, 1.seconds, false, anchor, Knowledge.Known(EntityId("turn"))))
        val action = GameAction.Group(SnapshotList.of(GameAction.RenameSeat(LifeFixtures.seatA, "Changed"),
            GameAction.EliminateSeat(LifeFixtures.seatB, SeatExitKind.MANUAL, "Explicit", null), GameAction.SetMonarch(LifeFixtures.seatA)), EntityId("table"))
        val changed = ok(LifeTableEngine.reduce(before, action, reading)).snapshot
        val target = archive(before, changed, action, 1)
        val alert = GameAlert(EntityId("retained-alert"), before.id, GameAlertTarget.GameTimer, UtcTimestamp(11), UtcTimestamp(12))
        val current = target.provenance.afterAction.copy(alerts = SnapshotList.of(alert))
        val now = GameTimeObservation(15.seconds, UtcTimestamp(15), reading.processSessionId)
        val reduced = ok(LifeSemanticUndo.undo(target, current, now, ResultEventAllocation(2, Revision(2))))
        assertEquals(before.seats, reduced.snapshot.seats); assertEquals(before.designations, reduced.snapshot.designations)
        assertEquals(7.seconds, reduced.snapshot.elapsed.gameElapsed); assertEquals(6.seconds, reduced.snapshot.elapsed.turnElapsed)
        assertEquals(SnapshotList.of(alert), reduced.snapshot.alerts)
        assertEquals(7.seconds, reduced.settledCurrent.elapsed.gameElapsed)
        assertTrue(current.seats.single { it.id == LifeFixtures.seatB }.participation is SeatParticipation.Eliminated)
    }
}
