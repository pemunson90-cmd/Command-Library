package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.*
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.*
import org.junit.Test

/** Synthetic authored mechanics, not authentic named-module/card/source evidence. */
class LifeVariantManagerTest {
    private val provider=LifeFixtures.provider
    private val absent=Knowledge.Absent("Synthetic source absence")
    private val provenance=Provenance(provider,null,absent,absent,UtcTimestamp(0),absent,null,null,null)
    private val a=ProviderIdentity(provider,"card","a"); private val b=ProviderIdentity(provider,"card","b")
    private val ids=SnapshotList.of("move","top","next","reset","return")
    private val definition=VariantDefinition(ProviderIdentity(provider,"variant","synthetic"),VariantNamespace.CUSTOM,"synthetic-v1","Synthetic manual instructions",
        SnapshotList.of(VariantActionDefinition("move","Move","Synthetic","deck","table",2),VariantActionDefinition("top","Top","Synthetic","deck","table",null),
            VariantActionDefinition("next","Next","Synthetic",null,null,1),VariantActionDefinition("reset","Reset fixed order","Synthetic",null,null,1),VariantActionDefinition("return","Return","Synthetic","table","deck",null)),
        SnapshotList.of(VariantZoneDefinition("table","First zone is NOT deck",2,true),VariantZoneDefinition("deck","Deck",3,true)),SnapshotList.of("closed","ready"),
        SnapshotList.of(VariantCard(a,"Synthetic A","Synthetic",absent,ids,provenance),VariantCard(b,"Synthetic B","Synthetic",absent,ids,provenance)),provenance,
        SourceNotice(provider,"Synthetic",absent,"Mechanical fixture only",null))
    private val policy=VariantProperty("synthetic-policy",VariantValue.Text("mechanics-plan-v1"))
    private val initial=VariantState(definition.id,definition.version,SnapshotList.of(VariantZone("table",SnapshotList.empty()),VariantZone("deck",SnapshotList.of(a,a,b))),
        SnapshotList.empty(),SnapshotList.empty(),"ready",SnapshotList.of(policy,VariantProperty("score",VariantValue.Integer(0))),77,SnapshotList.empty())
    private val phases=SnapshotList.of("ready","closed")
    private val transitions=SnapshotList.of(
        VariantManagerTransition("move",phases,"deck","table",VariantManagerSelection.Explicit(1,2),appendDrawn=true),
        VariantManagerTransition("top",phases,"deck","table",VariantManagerSelection.Top(1),appendDrawn=true),
        VariantManagerTransition("next",phases,null,null,nextPhase="closed",properties=SnapshotList.of(VariantProperty("score",VariantValue.Integer(7)))),
        VariantManagerTransition("reset",phases,null,null,reset=true),
        VariantManagerTransition("return",phases,"table","deck",VariantManagerSelection.Explicit(1,1),appendDiscarded=true))
    private val plan=VariantManagerPlan(definition,initial,policy.key,"mechanics-plan-v1",transitions)
    private val reading=GameTimeObservation(0.seconds,UtcTimestamp(1),EntityId("process"))
    private fun input(def:VariantDefinition=definition):GameCreation {
        val base=LifeFixtures.snapshot()
        val defs=SnapshotList.copyOf(base.settings.counterDefinitions.map { it.copy(role=when(it.id) { LifeFixtures.life->CounterRole.LIFE; LifeFixtures.commanderDamage->CounterRole.COMMANDER_DAMAGE; LifeFixtures.commanderTax->CounterRole.COMMANDER_TAX; else->CounterRole.OTHER }) })
        return GameCreation(EntityId("operation"),base.settings.copy(counterDefinitions=defs,variantDefinitions=SnapshotList.of(def)),base.seats,LifeFixtures.idleClock())
    }
    private fun <T> ok(o:Outcome<T>):T { assertTrue("Expected success, got $o",o is Outcome.Success); return (o as Outcome.Success).value }
    private fun failed(o:Outcome<*>,code:FailureCode) { assertTrue("Expected $code, got $o",o is Outcome.Failure); assertEquals(code,(o as Outcome.Failure).error.code) }
    private fun manager(p:VariantManagerPlan=plan)=LifeVariantManager(listOf(p))
    private fun game(p:VariantManagerPlan=plan)=ok(LifeGameInitializer.initialize(input(p.definition),LifeFixtures.gameId,reading,EntityId("opening"),manager(p)))
    private fun action(id:String,vararg selected:ProviderIdentity)=GameAction.Variant(definition.id,id,SnapshotList.copyOf(selected.asList()))
    private fun state(game:GameSnapshot)=game.variants.single()
    private fun VariantState.zone(id:String)=zones.single { it.zoneId==id }.cardIds
    private fun snapshot(state:VariantState)=game().snapshot.copy(variants=SnapshotList.of(state))
    @Test fun authoredSetupUsesExactNonFirstPhaseZonesCopiesPropertiesAndSeed() {
        assertEquals(SnapshotList.of(initial),ok(manager().initialize(input())))
        assertEquals(initial,state(game().snapshot)); assertEquals(listOf(a,a,b),initial.zone("deck")); assertEquals("ready",initial.phase)
        assertTrue(manager().initialize(input(definition.copy(version="changed"))) is Outcome.Unavailable)
        assertTrue(manager().initialize(input(definition.copy(instructions="same identity changed content"))) is Outcome.Unavailable)
        assertTrue(LifeVariantManager(emptyList()).initialize(input()) is Outcome.Unavailable)
    }
    @Test fun explicitMultisetMoveRemovesOnlySelectedCopiesAndPreservesHistories() {
        val before=game().snapshot
        val moved=ok(manager().reduce(before,action("move",a))); assertEquals(listOf(a,b),moved.zone("deck")); assertEquals(listOf(a),moved.zone("table")); assertEquals(listOf(a),moved.drawn)
        val back=ok(manager().reduce(snapshot(moved),action("return",a))); assertEquals(listOf(a,b,a),back.zone("deck")); assertTrue(back.zone("table").isEmpty())
        assertEquals(listOf(a),back.drawn); assertEquals(listOf(a),back.discarded); assertEquals(initial,state(before))
        failed(manager().reduce(snapshot(moved),action("move",a,a)),FailureCode.INVALID_INPUT)
    }
    @Test fun topSelectionCannotBeInjectedAndFinalCapacityFailureLeavesInputUnchanged() {
        val before=game().snapshot
        failed(manager().reduce(before,action("top",b)),FailureCode.INVALID_INPUT)
        val first=ok(manager().reduce(before,action("top"))); assertEquals(listOf(a,b),first.zone("deck"))
        val second=ok(manager().reduce(snapshot(first),action("top"))); assertEquals(listOf(a,a),second.zone("table"))
        failed(manager().reduce(snapshot(second),action("top")),FailureCode.INVALID_INPUT); assertEquals(listOf(b),second.zone("deck"))
    }
    @Test fun phaseResetPropertiesAndDeterministicResetAreExactAndCounted() {
        val moved=ok(manager().reduce(game().snapshot,action("move",a)))
        val phase=ok(manager().reduce(snapshot(moved),action("next"))); assertEquals("closed",phase.phase); assertEquals(listOf(VariantActionCount("next",1)),phase.actionCounts)
        assertEquals(VariantValue.Integer(7),phase.properties.single { it.key=="score" }.value)
        val reset=ok(manager().reduce(snapshot(phase),action("reset"))); assertEquals(initial.copy(actionCounts=SnapshotList.of(VariantActionCount("reset",1))),reset)
        failed(manager().reduce(snapshot(reset),action("reset")),FailureCode.INVALID_INPUT)
    }
    @Test fun policyMismatchIsUnavailableAndInvalidPlanIsTypedWithoutGuessing() {
        val badPolicy=initial.copy(properties=SnapshotList.of(policy.copy(value=VariantValue.Text("different")),initial.properties[1]))
        assertTrue(manager().reduce(snapshot(badPolicy),action("top")) is Outcome.Unavailable)
        for(p in listOf(plan.copy(transitions=SnapshotList.copyOf(transitions.drop(1))),plan.copy(transitions=SnapshotList.copyOf(transitions+transitions[0])),
            plan.copy(initial=initial.copy(phase="missing")),plan.copy(policyValue=""),plan.copy(transitions=SnapshotList.copyOf(transitions.map { if(it.actionId=="top") it.copy(fromZone="table") else it }))))
            failed(manager(p).initialize(input()),FailureCode.INVALID_INPUT)
        failed(LifeVariantManager(listOf(plan,plan)).initialize(input()),FailureCode.INVALID_INPUT)
    }
    @Test fun quotaBeforePhaseOrResetMatchesActualEventPacketPreflight() {
        val base=game()
        for(id in listOf("next","reset")) {
            val current=base.copy(snapshot=base.snapshot.copy(variants=SnapshotList.of(initial.copy(actionCounts=SnapshotList.of(VariantActionCount(id,1))))))
            failed(manager().reduce(current.snapshot,action(id)),FailureCode.INVALID_INPUT)
            failed(LifeActionEventEngine.dispatch(current,action(id),reading,ActionEventAllocation(0,Revision(1),EntityId("group")),variants=manager()),FailureCode.INVALID_INPUT)
        }
    }
    @Test fun actualPacketGroupAndUndoRestoreExactVariantOrderAndMetadata() {
        val before=game(); val group=GameAction.Group(SnapshotList.of(action("move",a),GameAction.RenameSeat(LifeFixtures.seatA,"Renamed")),EntityId("group"))
        val applied=ok(LifeActionEventEngine.dispatch(before,group,reading,ActionEventAllocation(0,Revision(1),group.groupId),variants=manager()))
        assertEquals(listOf(a,b),state(applied.checkpoint.snapshot).zone("deck")); assertEquals(group,applied.archive.event.action); assertTrue(applied.archive.event.randomResults.isEmpty())
        val undone=ok(LifeActionEventEngine.undo(applied.checkpoint,applied.archive,reading,ActionEventAllocation(1,Revision(2),group.groupId)))
        assertEquals(initial,state(undone.checkpoint.snapshot)); assertEquals(before.snapshot.seats,undone.checkpoint.snapshot.seats)
    }
    @Test fun sameZoneReorderingUsesSelectedOrderAndFinalCapacityNotTransientSize() {
        val d=definition.copy(allowedActions=SnapshotList.copyOf(definition.allowedActions.map { if(it.id=="move") it.copy(toZone="deck") else it }))
        val p=plan.copy(definition=d,transitions=SnapshotList.copyOf(transitions.map { if(it.actionId=="move") it.copy(toZone="deck",insertion=VariantInsertion.TOP) else it }))
        val current=game(p).snapshot; val reordered=ok(manager(p).reduce(current,action("move",b,a)))
        assertEquals(listOf(b,a,a),reordered.zone("deck")); assertEquals(listOf(b,a),reordered.drawn); assertTrue(reordered.zone("table").isEmpty())
        assertEquals(listOf(a,a,b),state(current).zone("deck"))
    }
    @Test fun samePhaseDoesNotResetOtherQuotasAndOverflowNeverReturnsPartialState() {
        val d=definition.copy(allowedActions=SnapshotList.copyOf(definition.allowedActions.map { if(it.id=="next") it.copy(maximumPerPhase=2) else it }))
        val p=plan.copy(definition=d,transitions=SnapshotList.copyOf(transitions.map { if(it.actionId=="next") it.copy(nextPhase="ready") else it }))
        val before=game(p).snapshot
        val moved=ok(manager(p).reduce(before,action("move",a)))
        val current=before.copy(variants=SnapshotList.of(moved))
        val next=ok(manager(p).reduce(current,action("next")))
        assertEquals(1,next.actionCounts.single { it.actionId=="move" }.count); assertEquals(1,next.actionCounts.single { it.actionId=="next" }.count)
        val overflow=initial.copy(actionCounts=SnapshotList.of(VariantActionCount("top",Int.MAX_VALUE)))
        failed(manager().reduce(snapshot(overflow),action("top")),FailureCode.INVALID_INPUT); assertEquals(initial.zones,overflow.zones)
    }
    @Test fun missingPolicyAndPropertyCorruptionAreDistinctAndRegistryDefensivelyCopiesInput() {
        val mutable=mutableListOf(plan); val copied=LifeVariantManager(mutable); mutable.clear()
        assertEquals(listOf(initial),ok(copied.initialize(input())))
        assertTrue(manager().reduce(snapshot(initial.copy(properties=SnapshotList.of(initial.properties[1]))),action("top")) is Outcome.Unavailable)
        val corrupt=initial.copy(properties=SnapshotList.of(policy,VariantProperty("score",VariantValue.Text("wrong type"))))
        failed(manager().reduce(snapshot(corrupt),action("top")),FailureCode.CORRUPT_DATA)
        val bad=plan.copy(transitions=SnapshotList.copyOf(transitions.map { if(it.actionId=="next") it.copy(properties=SnapshotList.of(policy)) else it }))
        failed(manager(bad).initialize(input()),FailureCode.INVALID_INPUT)
    }
    @Test fun exactVersionsAndEmptySelectionRetainExplicitAuthoredSemantics() {
        val newerDefinition=definition.copy(version="synthetic-v2")
        val newer=plan.copy(definition=newerDefinition,initial=initial.copy(version=newerDefinition.version))
        val registry=LifeVariantManager(listOf(plan,newer))
        assertEquals(listOf(initial),ok(registry.initialize(input()))); assertEquals(listOf(newer.initial),ok(registry.initialize(input(newerDefinition))))
        val zero=plan.copy(transitions=SnapshotList.copyOf(transitions.map { if(it.actionId=="move") it.copy(selection=VariantManagerSelection.Explicit(0,0)) else it }))
        val current=game(zero).snapshot; val accepted=ok(manager(zero).reduce(current,action("move")))
        assertEquals(initial.zones,accepted.zones); assertTrue(accepted.drawn.isEmpty()); assertEquals(listOf(VariantActionCount("move",1)),accepted.actionCounts)
    }
}
