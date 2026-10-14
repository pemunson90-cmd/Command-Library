package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.*
import org.junit.Assert.*
import org.junit.Test

class LifeCounterEngineTest {
    private fun game(states: SnapshotList<CounterState> = SnapshotList.empty()): GameSnapshot {
        val definitions=LifeFixtures.definitions().map { d -> d.copy(role=when(d.id) {
            LifeFixtures.life -> CounterRole.LIFE
            LifeFixtures.commanderDamage -> CounterRole.COMMANDER_DAMAGE
            LifeFixtures.commanderTax -> CounterRole.COMMANDER_TAX
            else -> CounterRole.OTHER
        }) }
        return LifeFixtures.snapshot(counters=states,definitions=SnapshotList.copyOf(definitions))
    }
    private fun <T> ok(outcome: Outcome<T>): T {assertTrue("Expected success, got $outcome",outcome is Outcome.Success);return(outcome as Outcome.Success<T>).value}
    private fun invalid(outcome: Outcome<*>) {assertTrue("Expected invalid input, got $outcome",outcome is Outcome.Failure);assertEquals(FailureCode.INVALID_INPUT,(outcome as Outcome.Failure).error.code)}
    private fun value(game: GameSnapshot,id: EntityId,seat: EntityId=LifeFixtures.seatA,slot: EntityId?=null): Int =
        game.counters.singleOrNull { it.definitionId==id && it.seatId==seat && it.commanderSlotId==slot }?.value ?: game.settings.counterDefinitions.single { it.id==id }.initial

    @Test fun exactTargetLogsCheckedDeltaIndependentlyOfAdjustmentStep() {
        val input=game().let { it.copy(settings=it.settings.copy(adjustmentStep=7)) }
        val reduced=ok(LifeCounterEngine.reduce(input,GameAction.SetCounter(LifeFixtures.seatA,LifeFixtures.life,null,31)))
        assertEquals(31,value(reduced.snapshot,LifeFixtures.life))
        assertEquals(GameAction.AdjustCounter(LifeFixtures.seatA,LifeFixtures.life,null,-9),reduced.action)
        assertEquals(input.revision,reduced.snapshot.revision)
        assertEquals(input.lastEventId,reduced.snapshot.lastEventId)
        assertEquals(40,value(input,LifeFixtures.life))
    }
    @Test fun extremeExactDifferencesRejectInsteadOfOverflowOrClamping() {
        for((start,target) in listOf(Int.MIN_VALUE to Int.MAX_VALUE,Int.MAX_VALUE to Int.MIN_VALUE)) {
            val input=game(SnapshotList.of(CounterState(LifeFixtures.life,LifeFixtures.seatA,null,start,false)))
            invalid(LifeCounterEngine.reduce(input,GameAction.SetCounter(LifeFixtures.seatA,LifeFixtures.life,null,target)))
            assertEquals(start,value(input,LifeFixtures.life))
        }
        invalid(LifeCounterEngine.reduce(game(),GameAction.SetCounter(LifeFixtures.seatA,LifeFixtures.life,null,40)))
    }
    @Test fun flatGroupUsesSequentialTargetsAndRollsBackInvalidTail() {
        val input=game()
        val id=EntityId("group")
        val group=GameAction.Group(SnapshotList.of(GameAction.SetCounter(LifeFixtures.seatA,LifeFixtures.life,null,30),GameAction.SetCounter(LifeFixtures.seatA,LifeFixtures.life,null,35)),id)
        val reduced=ok(LifeCounterEngine.reduce(input,group))
        assertEquals(listOf(-10,5),(reduced.action as GameAction.Group).actions.map { (it as GameAction.AdjustCounter).delta })
        val bad=group.copy(actions=SnapshotList.copyOf(group.actions+GameAction.SetCounter(LifeFixtures.seatA,EntityId("missing"),null,2)))
        invalid(LifeCounterEngine.reduce(input,bad))
        assertEquals(40,value(input,LifeFixtures.life))
        invalid(LifeCounterEngine.reduce(input,GameAction.Group(SnapshotList.of(group),id)))
    }
    @Test fun commanderHelperDeductsLifeExactlyOnceAndRawDamageRemainsLiteral() {
        val enabled=ok(LifeCounterEngine.reduce(game(),GameAction.SetCommanderLifeDeduction(true))).snapshot
        val damage=ok(LifeCounterEngine.commanderDamage(enabled,LifeFixtures.seatA,LifeFixtures.slotA,5,EntityId("damage")))
        assertEquals(35,value(damage.snapshot,LifeFixtures.life))
        assertEquals(5,value(damage.snapshot,LifeFixtures.commanderDamage,slot=LifeFixtures.slotA))
        assertEquals(2,(damage.action as GameAction.Group).actions.size)
        val literal=ok(LifeCounterEngine.reduce(enabled,GameAction.AdjustCounter(LifeFixtures.seatA,LifeFixtures.commanderDamage,LifeFixtures.slotB1,4)))
        assertEquals(40,value(literal.snapshot,LifeFixtures.life))
        assertEquals(4,value(literal.snapshot,LifeFixtures.commanderDamage,slot=LifeFixtures.slotB1))
        assertEquals(0,value(literal.snapshot,LifeFixtures.commanderDamage,slot=LifeFixtures.slotB2))
        invalid(LifeCounterEngine.reduce(enabled,GameAction.SetCounter(LifeFixtures.seatA,LifeFixtures.commanderTax,LifeFixtures.slotB1,2)))
    }
    @Test fun definitionChangesKeepRolesBoundsAndHiddenState() {
        val state=CounterState(LifeFixtures.charge,LifeFixtures.seatA,null,4,true)
        val input=game(SnapshotList.of(state))
        val definition=input.settings.counterDefinitions.single { it.id==LifeFixtures.charge }
        invalid(LifeCounterEngine.reduce(input,GameAction.UpdateCounterDefinition(definition.copy(maximum=3))))
        invalid(LifeCounterEngine.reduce(input,GameAction.UpdateCounterDefinition(definition.copy(role=CounterRole.LIFE))))
        invalid(LifeCounterEngine.reduce(input,GameAction.AddCounterDefinition(definition.copy(id=EntityId("new"),role=CounterRole.LIFE))))
        val renamed=definition.copy(label="原文 Energy",color=DisplayColor(0xff123456))
        val updated=ok(LifeCounterEngine.reduce(input,GameAction.UpdateCounterDefinition(renamed))).snapshot
        assertEquals(state,updated.counters.single())
        assertEquals(renamed,updated.settings.counterDefinitions.single { it.id==renamed.id })
        val life=input.settings.counterDefinitions.single { it.id==LifeFixtures.life }
        invalid(LifeCounterEngine.reduce(input,GameAction.UpdateCounterDefinition(life.copy(initial=20))))
    }
    @Test fun policyChangesAssessOldAndFinalCandidateMembershipWithoutTransientLoss() {
        val input=game(SnapshotList.of(CounterState(LifeFixtures.charge,LifeFixtures.seatA,null,0,false)))
        val definition=input.settings.counterDefinitions.single { it.id==LifeFixtures.charge }.copy(lossPolicy=CounterLossPolicy.AtOrBelow(0))
        val reduced=ok(LifeCounterEngine.reduce(input,GameAction.UpdateCounterDefinition(definition)))
        assertEquals(1,reduced.newlyReached.size)
        assertEquals(LifeFixtures.charge,reduced.newlyReached.single().definitionId)
        val renamed=ok(LifeCounterEngine.reduce(reduced.snapshot,GameAction.UpdateCounterDefinition(definition.copy(label="Renamed"))))
        assertTrue(renamed.newlyReached.isEmpty())
        val stillMet=ok(LifeCounterEngine.reduce(reduced.snapshot,GameAction.UpdateCounterDefinition(definition.copy(lossPolicy=CounterLossPolicy.AtOrBelow(1)))))
        assertTrue(stillMet.newlyReached.isEmpty())
        val group=GameAction.Group(SnapshotList.of(GameAction.UpdateCounterDefinition(definition),GameAction.SetCounter(LifeFixtures.seatA,LifeFixtures.charge,null,3)),EntityId("group"))
        val final=ok(LifeCounterEngine.reduce(input,group))
        assertTrue(final.lossCandidates.isEmpty())
        assertTrue(final.newlyReached.isEmpty())
        assertEquals(input.seats,final.snapshot.seats)
    }
    @Test fun presetsDoNotInventLossRulesAndNewImplicitPoliciesAreAssessed() {
        val definition=CounterDefinition(EntityId("custom"),"Poison label",0,null,0,CounterLossPolicy.None,true,CounterRole.OTHER,preset=CounterPreset.POISON)
        val literal=ok(LifeCounterEngine.reduce(game(),GameAction.AddCounterDefinition(definition)))
        assertTrue(literal.lossCandidates.isEmpty())
        val policy=definition.copy(id=EntityId("explicit-loss"),lossPolicy=CounterLossPolicy.AtOrAbove(0))
        val reduced=ok(LifeCounterEngine.reduce(game(),GameAction.AddCounterDefinition(policy)))
        assertEquals(3,reduced.newlyReached.size)
        assertEquals(game().seats,reduced.snapshot.seats)
        invalid(LifeCounterEngine.reduce(literal.snapshot,GameAction.AddCounterDefinition(definition)))
    }
    @Test fun turnBoundaryResetsAllMarkedSeatAndSlotStatesAndPreservesHiddenFlags() {
        val states=SnapshotList.of(CounterState(LifeFixtures.charge,LifeFixtures.seatA,null,4,true),CounterState(LifeFixtures.charge,LifeFixtures.seatB,null,5,false),
            CounterState(LifeFixtures.commanderDamage,LifeFixtures.seatB,LifeFixtures.slotA,7,true),CounterState(LifeFixtures.poison,LifeFixtures.seatA,null,3,false))
        val input=game(states).let { g -> g.copy(settings=g.settings.copy(counterDefinitions=SnapshotList.copyOf(g.settings.counterDefinitions.map {
            if(it.id==LifeFixtures.charge || it.id==LifeFixtures.commanderDamage) it.copy(resetPolicy=CounterResetPolicy.ON_TURN_START) else it
        }))) }
        val reset=ok(LifeCounterEngine.resetForTurn(input,LifeFixtures.seatC))
        assertEquals(2,value(reset,LifeFixtures.charge,LifeFixtures.seatA))
        assertEquals(2,value(reset,LifeFixtures.charge,LifeFixtures.seatB))
        assertEquals(0,value(reset,LifeFixtures.commanderDamage,LifeFixtures.seatB,LifeFixtures.slotA))
        assertEquals(3,value(reset,LifeFixtures.poison))
        assertEquals(states.map { it.hidden },reset.counters.map { it.hidden })
        assertEquals(input.clock,reset.clock)
        assertEquals(input.turnNumber,reset.turnNumber)
        assertEquals(input.revision,reset.revision)
        assertEquals(states,input.counters)
    }
    @Test fun policyResetAssignsInitialDirectlyEvenWhenDeltaWouldNotFitInt() {
        val definition=CounterDefinition(EntityId("wide"),"Wide",null,null,Int.MAX_VALUE,CounterLossPolicy.None,true,CounterRole.OTHER,resetPolicy=CounterResetPolicy.ON_TURN_START)
        val input=game().let { it.copy(settings=it.settings.copy(counterDefinitions=SnapshotList.copyOf(it.settings.counterDefinitions+definition)),
            counters=SnapshotList.of(CounterState(definition.id,LifeFixtures.seatA,null,Int.MIN_VALUE,true))) }
        val reset=ok(LifeCounterEngine.resetForTurn(input,LifeFixtures.seatB))
        assertEquals(Int.MAX_VALUE,value(reset,definition.id))
        assertTrue(reset.counters.single().hidden)
    }
    @Test fun definitionCapacityAndCompletedStateAreExplicitFailures() {
        val base=game()
        val definitions=base.settings.counterDefinitions.toMutableList()
        repeat(128-definitions.size) { i -> definitions+=CounterDefinition(EntityId("extra-$i"),"Extra",null,null,0,CounterLossPolicy.None,true,CounterRole.OTHER) }
        val full=base.copy(settings=base.settings.copy(counterDefinitions=SnapshotList.copyOf(definitions)))
        val extra=CounterDefinition(EntityId("overflow"),"Extra",null,null,0,CounterLossPolicy.None,true,CounterRole.OTHER)
        invalid(LifeCounterEngine.reduce(full,GameAction.AddCounterDefinition(extra)))
        invalid(LifeCounterEngine.reduce(base.copy(status=GameStatus.COMPLETED),GameAction.SetAdjustmentStep(2)))
        invalid(LifeCounterEngine.resetForTurn(base.copy(status=GameStatus.COMPLETED),LifeFixtures.seatA))
        invalid(LifeCounterEngine.resetForTurn(base,EntityId("missing")))
    }
}
