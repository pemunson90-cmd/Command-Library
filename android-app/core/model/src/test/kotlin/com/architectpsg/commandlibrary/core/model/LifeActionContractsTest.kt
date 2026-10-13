package com.architectpsg.commandlibrary.core.model

import org.junit.Assert.*
import org.junit.Test

class LifeActionContractsTest {
    private val a = EntityId("synthetic-a")
    private val b = EntityId("synthetic-b")
    private val evidence = LossEvidence(null, null, null, null, Knowledge.Unknown("Synthetic ruling", SnapshotList.empty()), "Synthetic observation")
    private fun result() = GameResult(EntityId("result"), GameId(ProviderId("synthetic"), "game"), SnapshotList.of(a),
        UtcTimestamp(100), "Synthetic finish", Revision(1), false)

    @Test fun adjustmentStepAndOperationalReasonsRejectInvalidInput() {
        assertThrows(IllegalArgumentException::class.java) { GameAction.SetAdjustmentStep(0) }
        assertThrows(IllegalArgumentException::class.java) { GameAction.SetAdjustmentStep(-1) }
        assertEquals(Int.MAX_VALUE, GameAction.SetAdjustmentStep(Int.MAX_VALUE).step)
        assertThrows(IllegalArgumentException::class.java) { GameAction.EliminateSeat(a, SeatExitKind.CONFIRMED_LOSS, " ", evidence) }
        assertThrows(IllegalArgumentException::class.java) { GameAction.RestoreSeat(a, "") }
        assertThrows(IllegalArgumentException::class.java) { GameAction.DismissLossCandidate(evidence, a, "\n") }
        assertThrows(IllegalArgumentException::class.java) { GameAction.FinishWithOutcomes(SnapshotList.of(SeatResult(a, SeatResultKind.WIN, null)), " ") }
    }
    @Test fun explicitFinishRejectsEmptyDuplicateOrConflictingOutcomes() {
        assertThrows(IllegalArgumentException::class.java) { GameAction.FinishWithOutcomes(SnapshotList.empty(), "Finish") }
        assertThrows(IllegalArgumentException::class.java) {
            GameAction.FinishWithOutcomes(SnapshotList.of(SeatResult(a, SeatResultKind.WIN, null), SeatResult(a, SeatResultKind.LOSS, null)), "Finish")
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameAction.FinishWithOutcomes(SnapshotList.of(SeatResult(a, SeatResultKind.WIN, null), SeatResult(b, SeatResultKind.DRAW, null)), "Finish")
        }
        val action = GameAction.FinishWithOutcomes(SnapshotList.of(SeatResult(a, SeatResultKind.WIN, null), SeatResult(b, SeatResultKind.LOSS, null)), "Finish")
        assertNull(action.seatResults[1].exit)
    }
    @Test fun resultRetainsLegacyAbsenceAndChecksExplicitWinners() {
        assertTrue(result().seatResults.isEmpty())
        val explicit = result().copy(seatResults = SnapshotList.of(SeatResult(a, SeatResultKind.WIN, null), SeatResult(b, SeatResultKind.LOSS, SeatExitKind.CONCEDED)))
        assertEquals(SeatExitKind.CONCEDED, explicit.seatResults[1].exit)
        assertThrows(IllegalArgumentException::class.java) { explicit.copy(winners = SnapshotList.of(b)) }
        assertThrows(IllegalArgumentException::class.java) { explicit.copy(winners = SnapshotList.of(a, a)) }
        assertThrows(IllegalArgumentException::class.java) { explicit.copy(seatResults = SnapshotList.of(explicit.seatResults[0], explicit.seatResults[0])) }
        assertThrows(IllegalArgumentException::class.java) { explicit.copy(seatResults = SnapshotList.of(explicit.seatResults[0], SeatResult(b, SeatResultKind.DRAW, null))) }
        val draw = result().copy(winners = SnapshotList.empty(), seatResults = SnapshotList.of(SeatResult(a, SeatResultKind.DRAW, null), SeatResult(b, SeatResultKind.LOSS, null)))
        assertTrue(draw.winners.isEmpty())
    }
    @Test fun requestsPreserveExplicitSettingsAndObservationsWithoutExecutingThem() {
        val exact = GameAction.SetCounter(a, EntityId("life"), null, Int.MIN_VALUE)
        assertEquals(Int.MIN_VALUE, exact.value)
        assertEquals(evidence, GameAction.DismissLossCandidate(evidence, a, "Effect prevents loss").candidate)
        assertEquals(a, GameAction.PassClock(a).seatId)
        assertEquals(GameAlertPreferences(false, true, false), GameAction.SetAlertPreferences(GameAlertPreferences(false, true, false)).preferences)
        assertEquals(EntityId("occurrence"), GameAction.AcknowledgeGameAlert(EntityId("occurrence")).alertId)
    }
}
