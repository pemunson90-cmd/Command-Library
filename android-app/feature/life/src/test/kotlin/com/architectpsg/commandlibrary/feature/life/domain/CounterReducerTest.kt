package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.CommanderSlot
import com.architectpsg.commandlibrary.core.model.CounterDefinition
import com.architectpsg.commandlibrary.core.model.CounterLossPolicy
import com.architectpsg.commandlibrary.core.model.EntityId
import com.architectpsg.commandlibrary.core.model.FailureCode
import com.architectpsg.commandlibrary.core.model.GameAction
import com.architectpsg.commandlibrary.core.model.GameStatus
import com.architectpsg.commandlibrary.core.model.SnapshotList
import com.architectpsg.commandlibrary.feature.life.domain.LifeFixtures.charge
import com.architectpsg.commandlibrary.feature.life.domain.LifeFixtures.commanderDamage
import com.architectpsg.commandlibrary.feature.life.domain.LifeFixtures.commanderTax
import com.architectpsg.commandlibrary.feature.life.domain.LifeFixtures.life
import com.architectpsg.commandlibrary.feature.life.domain.LifeFixtures.poison
import com.architectpsg.commandlibrary.feature.life.domain.LifeFixtures.rad
import com.architectpsg.commandlibrary.feature.life.domain.LifeFixtures.roles
import com.architectpsg.commandlibrary.feature.life.domain.LifeFixtures.seatA
import com.architectpsg.commandlibrary.feature.life.domain.LifeFixtures.seatB
import com.architectpsg.commandlibrary.feature.life.domain.LifeFixtures.seatC
import com.architectpsg.commandlibrary.feature.life.domain.LifeFixtures.slotA
import com.architectpsg.commandlibrary.feature.life.domain.LifeFixtures.slotB1
import com.architectpsg.commandlibrary.feature.life.domain.LifeFixtures.slotB2
import com.architectpsg.commandlibrary.feature.life.domain.LifeFixtures.snapshot
import com.architectpsg.commandlibrary.feature.life.domain.LifeFixtures.state
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CounterReducerTest {
    private val group = EntityId("group-1")

    private fun adjust(seat: EntityId, definition: EntityId, delta: Int, slot: EntityId? = null) =
        GameAction.AdjustCounter(seat, definition, slot, delta)

    @Test fun adjustmentChangesOnlyTheTargetAndKeepsRevisionForTheCommittingTransaction() {
        val input = snapshot(listOf(state(life, seatA, 40), state(life, seatB, 40)))
        val reduced = CounterReducer.reduce(input, roles, adjust(seatA, life, -3)).success()
        assertEquals(37, reduced.snapshot.counter(life, seatA)!!.value)
        assertEquals(40, reduced.snapshot.counter(life, seatB)!!.value)
        assertEquals(input.revision, reduced.snapshot.revision)
        assertEquals(input.lastEventId, reduced.snapshot.lastEventId)
        assertEquals(40, input.counter(life, seatA)!!.value)
        assertEquals(adjust(seatA, life, -3), reduced.action)
    }

    @Test fun absentStateStartsFromTheDefinitionInitialValue() {
        val reduced = CounterReducer.reduce(snapshot(), roles, adjust(seatC, charge, 1)).success()
        assertEquals(3, reduced.snapshot.counter(charge, seatC)!!.value)
        assertFalse(reduced.snapshot.counter(charge, seatC)!!.hidden)
        assertEquals(39, CounterReducer.reduce(snapshot(), roles, adjust(seatC, life, -1)).success().snapshot.counter(life, seatC)!!.value)
    }

    @Test fun zeroAdjustmentIsRejected() {
        CounterReducer.reduce(snapshot(), roles, adjust(seatA, life, 0)).failure(FailureCode.INVALID_INPUT)
    }

    @Test fun boundsAreRejectedNotClampedAndTheInputStaysIntact() {
        val input = snapshot(listOf(state(poison, seatA, 0), state(charge, seatA, 5)))
        CounterReducer.reduce(input, roles, adjust(seatA, poison, -1)).failure(FailureCode.INVALID_INPUT)
        CounterReducer.reduce(input, roles, adjust(seatA, charge, 1)).failure(FailureCode.INVALID_INPUT)
        assertEquals(0, input.counter(poison, seatA)!!.value)
        assertEquals(5, input.counter(charge, seatA)!!.value)
        assertEquals(4, CounterReducer.reduce(input, roles, adjust(seatA, charge, -1)).success().snapshot.counter(charge, seatA)!!.value)
    }

    @Test fun integerOverflowIsRejectedInBothDirections() {
        val input = snapshot(listOf(state(life, seatA, Int.MAX_VALUE - 1), state(life, seatB, Int.MIN_VALUE + 1)))
        CounterReducer.reduce(input, roles, adjust(seatA, life, 2)).failure(FailureCode.INVALID_INPUT)
        CounterReducer.reduce(input, roles, adjust(seatB, life, -2)).failure(FailureCode.INVALID_INPUT)
        CounterReducer.reduce(input, roles, adjust(seatA, life, Int.MAX_VALUE)).failure(FailureCode.INVALID_INPUT)
        assertEquals(Int.MAX_VALUE, CounterReducer.reduce(input, roles, adjust(seatA, life, 1)).success().snapshot.counter(life, seatA)!!.value)
    }

    @Test fun lifeWithoutMinimumGoesNegativeAndIsReportedAsLossCandidate() {
        val reduced = CounterReducer.reduce(snapshot(listOf(state(life, seatA, 2))), roles, adjust(seatA, life, -5)).success()
        assertEquals(-3, reduced.snapshot.counter(life, seatA)!!.value)
        val candidate = reduced.newlyReached.single()
        assertEquals(seatA, candidate.seatId)
        assertEquals(CounterLossPolicy.AtOrBelow(0), candidate.policy)
        assertEquals(GameStatus.ACTIVE, reduced.snapshot.status)
        assertEquals(LifeFixtures.seats(), reduced.snapshot.seats)
    }

    @Test fun hidingKeepsTheValueAndAdjustingAHiddenCounterKeepsItHidden() {
        val hidden = CounterReducer.reduce(snapshot(listOf(state(poison, seatA, 4))), roles, GameAction.SetCounterHidden(seatA, poison, true)).success().snapshot
        assertEquals(state(poison, seatA, 4, hidden = true), hidden.counter(poison, seatA))
        val adjusted = CounterReducer.reduce(hidden, roles, adjust(seatA, poison, 2)).success().snapshot
        assertEquals(state(poison, seatA, 6, hidden = true), adjusted.counter(poison, seatA))
        val shown = CounterReducer.reduce(adjusted, roles, GameAction.SetCounterHidden(seatA, poison, false)).success().snapshot
        assertEquals(state(poison, seatA, 6, hidden = false), shown.counter(poison, seatA))
        CounterReducer.reduce(shown, roles, GameAction.SetCounterHidden(seatA, poison, false)).failure(FailureCode.INVALID_INPUT)
    }

    @Test fun hidingAnAbsentCounterRecordsItsInitialValue() {
        val hidden = CounterReducer.reduce(snapshot(), roles, GameAction.SetCounterHidden(seatC, charge, true)).success().snapshot
        assertEquals(state(charge, seatC, 2, hidden = true), hidden.counter(charge, seatC))
        CounterReducer.reduce(snapshot(), roles, GameAction.SetCounterHidden(seatC, charge, false)).failure(FailureCode.INVALID_INPUT)
    }

    @Test fun hidingCommanderDamageCoversEverySlotAndLaterSlotsFollow() {
        val input = snapshot(listOf(state(commanderDamage, seatA, 9, slotB1)))
        val hidden = CounterReducer.reduce(input, roles, GameAction.SetCounterHidden(seatA, commanderDamage, true)).success().snapshot
        assertEquals(state(commanderDamage, seatA, 9, slotB1, hidden = true), hidden.counter(commanderDamage, seatA, slotB1))
        assertEquals(state(commanderDamage, seatA, 0, slotB2, hidden = true), hidden.counter(commanderDamage, seatA, slotB2))
        // A seat's own commander is a legal damage source after a control change (CR 903.10a); new slots inherit hidden.
        val ownSlot = CounterReducer.reduce(hidden, roles, adjust(seatA, commanderDamage, 3, slotA)).success().snapshot
        assertEquals(state(commanderDamage, seatA, 3, slotA, hidden = true), ownSlot.counter(commanderDamage, seatA, slotA))
        CounterReducer.reduce(snapshot(), roles, GameAction.SetCounterHidden(seatA, commanderTax, true)).success()
        CounterReducer.reduce(snapshot(), roles, GameAction.SetCounterHidden(seatC, commanderTax, true)).failure(FailureCode.INVALID_INPUT)
    }

    @Test fun partnersKeepSeparateSubtotalsAndEachUsesTheSingleCommanderThreshold() {
        var current = snapshot(listOf(state(life, seatA, 40)))
        current = CounterReducer.commanderDamage(current, roles, seatA, slotB1, 11, deductLife = false, groupId = group).success().snapshot
        val bothPartners = CounterReducer.commanderDamage(current, roles, seatA, slotB2, 11, deductLife = false, groupId = group).success()
        assertEquals(11, bothPartners.snapshot.counter(commanderDamage, seatA, slotB1)!!.value)
        assertEquals(11, bothPartners.snapshot.counter(commanderDamage, seatA, slotB2)!!.value)
        assertTrue("22 split across partners is not lethal", bothPartners.lossCandidates.isEmpty())
        val lethal = CounterReducer.commanderDamage(bothPartners.snapshot, roles, seatA, slotB1, 10, deductLife = false, groupId = group).success()
        val candidate = lethal.newlyReached.single()
        assertEquals(slotB1, candidate.commanderSlotId)
        assertEquals(21, candidate.value)
        assertEquals(40, lethal.snapshot.counter(life, seatA)!!.value)
    }

    @Test fun commanderDamageWithLifeDeductionIsOneGroupedResult() {
        val reduced = CounterReducer.commanderDamage(snapshot(listOf(state(life, seatA, 40))), roles, seatA, slotB2, 6, deductLife = true, groupId = group).success()
        assertEquals(
            GameAction.Group(SnapshotList.of(adjust(seatA, commanderDamage, 6, slotB2), adjust(seatA, life, -6)), group),
            reduced.action,
        )
        assertEquals(6, reduced.snapshot.counter(commanderDamage, seatA, slotB2)!!.value)
        assertEquals(34, reduced.snapshot.counter(life, seatA)!!.value)
    }

    @Test fun failedLifeDeductionRejectsTheWholeCommanderDamageGroup() {
        val input = snapshot(listOf(state(life, seatA, Int.MIN_VALUE + 3), state(commanderDamage, seatA, 4, slotB1)))
        CounterReducer.commanderDamage(input, roles, seatA, slotB1, 5, deductLife = true, groupId = group).failure(FailureCode.INVALID_INPUT)
        assertEquals(4, input.counter(commanderDamage, seatA, slotB1)!!.value)
        // A correction below zero damage is rejected before life is touched.
        CounterReducer.commanderDamage(input, roles, seatA, slotB1, -5, deductLife = true, groupId = group).failure(FailureCode.INVALID_INPUT)
        val corrected = CounterReducer.commanderDamage(input, roles, seatA, slotB1, -4, deductLife = true, groupId = group).success().snapshot
        assertEquals(0, corrected.counter(commanderDamage, seatA, slotB1)!!.value)
        assertEquals(Int.MIN_VALUE + 7, corrected.counter(life, seatA)!!.value)
        CounterReducer.commanderDamage(input, roles, seatA, slotB1, Int.MIN_VALUE, deductLife = true, groupId = group).failure(FailureCode.INVALID_INPUT)
        CounterReducer.commanderDamage(input, roles, seatA, slotB1, 0, deductLife = false, groupId = group).failure(FailureCode.INVALID_INPUT)
        CounterReducer.commanderDamage(input, roles.copy(commanderDamage = null), seatA, slotB1, 1, deductLife = false, groupId = group).failure(FailureCode.INVALID_INPUT)
    }

    @Test fun groupsApplyInOrderAndAnyFailingStepRejectsTheWholeGroup() {
        val input = snapshot(listOf(state(charge, seatA, 4)))
        val hold = GameAction.Group(SnapshotList.of(adjust(seatA, charge, -1), adjust(seatA, charge, -1), adjust(seatA, charge, 1)), group)
        assertEquals(3, CounterReducer.reduce(input, roles, hold).success().snapshot.counter(charge, seatA)!!.value)
        val overshoot = GameAction.Group(SnapshotList.of(adjust(seatA, charge, 1), adjust(seatA, charge, 1), adjust(seatA, charge, -1)), group)
        CounterReducer.reduce(input, roles, overshoot).failure(FailureCode.INVALID_INPUT)
        CounterReducer.reduce(input, roles, GameAction.Group(SnapshotList.empty(), group)).failure(FailureCode.INVALID_INPUT)
        val mixed = GameAction.Group(SnapshotList.of(adjust(seatA, charge, -1), GameAction.ClockPause(true)), group)
        CounterReducer.reduce(input, roles, mixed).failure(FailureCode.INVALID_INPUT)
        assertEquals(4, input.counter(charge, seatA)!!.value)
    }

    @Test fun slotScopesAreEnforcedByIdentity() {
        val input = snapshot()
        CounterReducer.reduce(input, roles, adjust(seatA, commanderDamage, 1)).failure(FailureCode.INVALID_INPUT)
        CounterReducer.reduce(input, roles, adjust(seatA, commanderDamage, 1, EntityId("slot-missing"))).failure(FailureCode.INVALID_INPUT)
        CounterReducer.reduce(input, roles, adjust(seatA, poison, 1, slotB1)).failure(FailureCode.INVALID_INPUT)
        CounterReducer.reduce(input, roles, adjust(seatA, commanderTax, 2, slotB1)).failure(FailureCode.INVALID_INPUT)
        CounterReducer.reduce(input, roles, adjust(seatB, commanderTax, 2)).failure(FailureCode.INVALID_INPUT)
        val taxed = CounterReducer.reduce(input, roles, adjust(seatB, commanderTax, 2, slotB2)).success().snapshot
        assertEquals(2, taxed.counter(commanderTax, seatB, slotB2)!!.value)
        assertNull(taxed.counter(commanderTax, seatB, slotB1))
    }

    @Test fun rolesFollowDefinitionIdsNotLabels() {
        val swappedLabels = SnapshotList.copyOf(LifeFixtures.definitions().map {
            when (it.id) {
                life -> it.copy(label = "Poison")
                poison -> it.copy(label = "Life")
                else -> it
            }
        })
        val input = snapshot(listOf(state(life, seatA, 1)), definitions = swappedLabels)
        val reduced = CounterReducer.reduce(input, roles, adjust(seatA, life, -1)).success()
        assertEquals(CounterLossPolicy.AtOrBelow(0), reduced.newlyReached.single().policy)
        val deducted = CounterReducer.commanderDamage(input, roles, seatA, slotB1, 1, deductLife = true, groupId = group).success().snapshot
        assertEquals(0, deducted.counter(life, seatA)!!.value)
        assertNull(deducted.counter(poison, seatA))
    }

    @Test fun invalidRoleBindingsAreRejected() {
        CounterReducer.reduce(snapshot(), CounterRoles(EntityId("counter-unknown"), commanderDamage, null), adjust(seatA, rad, 1)).failure(FailureCode.INVALID_INPUT)
        CounterReducer.reduce(snapshot(), CounterRoles(life, life, null), adjust(seatA, rad, 1)).failure(FailureCode.INVALID_INPUT)
    }

    @Test fun radIsNotPoisonAndExistingCandidatesAreNotReportedAsNew() {
        val input = snapshot(listOf(state(rad, seatB, 9), state(poison, seatC, 10)))
        val reduced = CounterReducer.reduce(input, roles, adjust(seatB, rad, 5)).success()
        assertEquals(14, reduced.snapshot.counter(rad, seatB)!!.value)
        assertEquals(listOf(poison), reduced.lossCandidates.map { it.definitionId })
        assertTrue(reduced.newlyReached.isEmpty())
        assertEquals(reduced.lossCandidates, CounterReducer.assessLoss(reduced.snapshot, roles).success())
    }

    @Test fun completedGamesAndNonCounterActionsAreRejected() {
        CounterReducer.reduce(snapshot(status = GameStatus.COMPLETED), roles, adjust(seatA, life, -1)).failure(FailureCode.INVALID_INPUT)
        CounterReducer.reduce(snapshot(), roles, GameAction.RenameSeat(seatA, "New name")).failure(FailureCode.INVALID_INPUT)
        CounterReducer.reduce(snapshot(), roles, adjust(EntityId("seat-missing"), life, -1)).failure(FailureCode.INVALID_INPUT)
        CounterReducer.reduce(snapshot(), roles, adjust(seatA, EntityId("counter-missing"), -1)).failure(FailureCode.INVALID_INPUT)
        CounterReducer.reduce(snapshot(status = GameStatus.PAUSED), roles, adjust(seatA, life, -1)).success()
    }

    @Test fun inconsistentSnapshotsAreReportedAsCorrupt() {
        val action = adjust(seatA, rad, 1)
        CounterReducer.reduce(snapshot(listOf(state(rad, seatA, 1), state(rad, seatA, 2))), roles, action).failure(FailureCode.CORRUPT_DATA)
        CounterReducer.reduce(snapshot(listOf(state(charge, seatA, 6))), roles, action).failure(FailureCode.CORRUPT_DATA)
        CounterReducer.reduce(snapshot(listOf(state(rad, EntityId("seat-gone"), 1))), roles, action).failure(FailureCode.CORRUPT_DATA)
        CounterReducer.reduce(snapshot(listOf(state(commanderTax, seatA, 1, slotB1))), roles, action).failure(FailureCode.CORRUPT_DATA)
        CounterReducer.reduce(snapshot(initialLife = 20), roles, action).failure(FailureCode.CORRUPT_DATA)
        val duplicateDefinition = SnapshotList.copyOf(LifeFixtures.definitions() + CounterDefinition(rad, "Rad copy", 0, null, 0, CounterLossPolicy.None, false))
        CounterReducer.reduce(snapshot(definitions = duplicateDefinition), roles, action).failure(FailureCode.CORRUPT_DATA)
        val sharedSlot = SnapshotList.of(
            LifeFixtures.seat(seatA, 0, CommanderSlot(slotA, LifeFixtures.identity("a"), "Commander")),
            LifeFixtures.seat(seatB, 1, CommanderSlot(slotA, LifeFixtures.identity("b"), "Commander")),
        )
        CounterReducer.reduce(snapshot(seats = sharedSlot), roles, action).failure(FailureCode.CORRUPT_DATA)
        val seven = SnapshotList.copyOf((0 until 7).map { LifeFixtures.seat(EntityId("seat-$it"), it) })
        CounterReducer.reduce(snapshot(seats = seven), roles, adjust(EntityId("seat-0"), rad, 1)).failure(FailureCode.CORRUPT_DATA)
        val reusedOrder = SnapshotList.of(LifeFixtures.seat(seatA, 0), LifeFixtures.seat(seatB, 0))
        CounterReducer.reduce(snapshot(seats = reusedOrder), roles, action).failure(FailureCode.CORRUPT_DATA)
    }

    @Test fun singleSeatPracticeGameIsSupported() {
        val solo = snapshot(seats = SnapshotList.of(LifeFixtures.seat(seatA, 0, CommanderSlot(slotA, LifeFixtures.identity("solo"), "Commander"))))
        assertEquals(39, CounterReducer.reduce(solo, roles, adjust(seatA, life, -1)).success().snapshot.counter(life, seatA)!!.value)
    }
}
