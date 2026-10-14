package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.*
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.*
import org.junit.Test

/** Synthetic internal authority only, not positive public source-factory/SQLite composition. */
class LifeVariantShuffleTest {
    private val provider=LifeFixtures.provider
    private val absent=Knowledge.Absent("Synthetic source absence")
    private val provenance=Provenance(provider,null,absent,absent,UtcTimestamp(0),absent,null,null,null)
    private val a=ProviderIdentity(provider,"card","a"); private val b=ProviderIdentity(provider,"card","b")
    private val definition=VariantDefinition(ProviderIdentity(provider,"variant","synthetic-shuffle"),VariantNamespace.CUSTOM,"v1","Synthetic instructions",
        SnapshotList.of(VariantActionDefinition("shuffle","Shuffle","Synthetic","deck","deck",3)),SnapshotList.of(VariantZoneDefinition("deck","Deck",null,true)),
        SnapshotList.of("ready","closed"),SnapshotList.of(VariantCard(a,"Synthetic A","Synthetic",absent,SnapshotList.of("shuffle"),provenance),VariantCard(b,"Synthetic B","Synthetic",absent,SnapshotList.of("shuffle"),provenance)),
        provenance,SourceNotice(provider,"Synthetic",absent,"Mechanical fixture",null))
    private val initial=VariantState(definition.id,"v1",SnapshotList.of(VariantZone("deck",SnapshotList.of(a,a,b))),SnapshotList.of(b),SnapshotList.of(a),"ready",
        SnapshotList.of(VariantProperty("policy",VariantValue.Text("plan-v1")),VariantProperty("score",VariantValue.Integer(7))),99,SnapshotList.empty())
    private val transition=VariantManagerTransition("shuffle",SnapshotList.of("ready"),"deck","deck",VariantManagerSelection.Shuffle)
    private val plan=VariantManagerPlan(definition,initial,"policy","plan-v1",SnapshotList.of(transition))
    private val request=RandomRequest.VariantZoneShuffle(EntityId("request"),definition.id,"shuffle","deck","policy","plan-v1")
    private val action=GameAction.RequestRandom(request)
    private val reading=GameTimeObservation(0.seconds,UtcTimestamp(10),EntityId("process"))
    private class Source:RandomSource { var calls=0; override fun nextInt(upperBound:Int):Int { calls++; return 0 } }
    private fun <T> ok(o:Outcome<T>):T { assertTrue("Expected success, got $o",o is Outcome.Success); return (o as Outcome.Success).value }
    private fun failed(o:Outcome<*>,code:FailureCode) { assertTrue("Expected $code, got $o",o is Outcome.Failure); assertEquals(code,(o as Outcome.Failure).error.code) }
    private fun manager(p:VariantManagerPlan=plan)=LifeVariantManager(listOf(p))
    private fun game(p:VariantManagerPlan=plan):GameCheckpoint {
        val base=LifeFixtures.snapshot(); val definitions=SnapshotList.copyOf(base.settings.counterDefinitions.map { it.copy(role=when(it.id) { LifeFixtures.life->CounterRole.LIFE; LifeFixtures.commanderDamage->CounterRole.COMMANDER_DAMAGE; LifeFixtures.commanderTax->CounterRole.COMMANDER_TAX; else->CounterRole.OTHER }) })
        val input=GameCreation(EntityId("operation"),base.settings.copy(counterDefinitions=definitions,variantDefinitions=SnapshotList.of(p.definition)),base.seats,LifeFixtures.idleClock())
        return ok(LifeGameInitializer.initialize(input,LifeFixtures.gameId,reading,EntityId("opening"),manager(p)))
    }
    @Test fun authorizedResolutionRecordsActualOrderAndReplayNeedsNoSourceOrInstalledManager() {
        val before=game().snapshot; val source=Source()
        val resolved=ok(LifeRandomEngine.resolve(before,action,reading.wall,source,variants=manager()))
        val value=resolved.recorded.value as RecordedRandomValue.VariantZoneShuffle
        assertEquals(2,source.calls); assertEquals(listOf(a,a,b),value.beforeOrder); assertEquals(listOf(a,b,a),value.afterOrder)
        val expected=initial.copy(zones=SnapshotList.of(VariantZone("deck",value.afterOrder)),actionCounts=SnapshotList.of(VariantActionCount("shuffle",1)))
        assertEquals(before.copy(variants=SnapshotList.of(expected)),resolved.snapshot)
        assertEquals(resolved,ok(LifeRandomEngine.replay(before,action,resolved.recorded))); assertEquals(2,source.calls)
        assertEquals(initial,game().snapshot.variants.single())
    }
    @Test fun missingOrWrongAuthorityPolicyPhaseQuotaAndBoundNeverDraw() {
        val before=game().snapshot; val source=Source()
        assertTrue(LifeRandomEngine.resolve(before,action,reading.wall,source) is Outcome.Unavailable)
        val deterministic=plan.copy(transitions=SnapshotList.of(transition.copy(selection=VariantManagerSelection.Top(1))))
        assertTrue(LifeRandomEngine.resolve(before,action,reading.wall,source,variants=manager(deterministic)) is Outcome.Unavailable)
        for(state in listOf(initial.copy(phase="closed"),initial.copy(actionCounts=SnapshotList.of(VariantActionCount("shuffle",3)))))
            failed(LifeRandomEngine.resolve(before.copy(variants=SnapshotList.of(state)),action,reading.wall,source,variants=manager()),FailureCode.INVALID_INPUT)
        val large=plan.copy(initial=initial.copy(zones=SnapshotList.of(VariantZone("deck",SnapshotList.copyOf(List(10_001) { a })))))
        failed(LifeRandomEngine.resolve(game(large).snapshot,action,reading.wall,source,variants=manager(large)),FailureCode.INVALID_INPUT)
        val wrongPolicy=initial.copy(properties=SnapshotList.of(VariantProperty("policy",VariantValue.Text("other")),initial.properties[1]))
        assertTrue(LifeRandomEngine.resolve(before.copy(variants=SnapshotList.of(wrongPolicy)),action,reading.wall,source,variants=manager()) is Outcome.Unavailable)
        assertEquals(0,source.calls)
    }
    @Test fun emptySingletonAndMaximumStillCountOnceWithExactBoundedDraws() {
        for(n in listOf(0,1,10_000)) {
            val p=plan.copy(initial=initial.copy(zones=SnapshotList.of(VariantZone("deck",SnapshotList.copyOf(List(n) { a }))))); val source=Source()
            val resolved=ok(LifeRandomEngine.resolve(game(p).snapshot,action,reading.wall,source,variants=manager(p)))
            assertEquals(maxOf(0,n-1),source.calls); assertEquals(n,(resolved.recorded.value as RecordedRandomValue.VariantZoneShuffle).afterOrder.size)
            assertEquals(listOf(VariantActionCount("shuffle",1)),resolved.snapshot.variants.single().actionCounts)
        }
    }
    @Test fun exactHistoricalBeforeMetadataAndRequestCannotBeSubstituted() {
        val before=game().snapshot; val resolved=ok(LifeRandomEngine.resolve(before,action,reading.wall,Source(),variants=manager()))
        val value=resolved.recorded.value as RecordedRandomValue.VariantZoneShuffle
        for(bad in listOf(value.copy(definitionVersion="other"),value.copy(actionId="other"),value.copy(zoneId="other"),value.copy(policyKey="other"),value.copy(policyValue="other"),
            value.copy(beforeOrder=SnapshotList.of(a,b,a)))) failed(LifeRandomEngine.replay(before,action,resolved.recorded.copy(value=bad)),FailureCode.INVALID_INPUT)
        failed(LifeRandomEngine.replay(before,action,resolved.recorded.copy(value=RecordedRandomValue.Coin(true))),FailureCode.INVALID_INPUT)
        val source=Source(); failed(LifeRandomEngine.resolve(before,action,reading.wall,source,RandomRequestContext(request,resolved.recorded),manager()),FailureCode.INVALID_INPUT)
        assertEquals(0,source.calls)
    }
    @Test fun actualPacketUsesResolveAuthorityAndUndoRestoresOrderWithoutRedraw() {
        val before=game(); val source=Source(); val allocation=ActionEventAllocation(0,Revision(1),EntityId("group"))
        val applied=ok(LifeActionEventEngine.dispatch(before,action,reading,allocation,RandomExecution.Resolve(source),manager()))
        assertEquals(1,applied.archive.event.randomResults.size); assertEquals(2,source.calls)
        val undone=ok(LifeActionEventEngine.undo(applied.checkpoint,applied.archive,reading,ActionEventAllocation(1,Revision(2),EntityId("undo"))))
        assertEquals(initial,undone.checkpoint.snapshot.variants.single()); assertEquals(2,source.calls)
        assertTrue(manager().reduce(before.snapshot,GameAction.Variant(definition.id,"shuffle",SnapshotList.empty())) is Outcome.Unavailable)
        val group=GameAction.Group(SnapshotList.of(action,GameAction.RenameSeat(LifeFixtures.seatA,"No")),EntityId("group"))
        failed(LifeActionEventEngine.dispatch(before,group,reading,allocation,RandomExecution.Resolve(source),manager()),FailureCode.INVALID_INPUT); assertEquals(2,source.calls)
    }
    @Test fun shufflePlansRejectCompoundEffectsAndDoNotUseStoredSeed() {
        for(t in listOf(transition.copy(nextPhase="ready"),transition.copy(reset=true),transition.copy(appendDrawn=true),transition.copy(appendDiscarded=true),
            transition.copy(properties=SnapshotList.of(VariantProperty("score",VariantValue.Integer(1)))))) {
            val p=plan.copy(transitions=SnapshotList.of(t)); val base=game().snapshot
            failed(manager(p).authorizeShuffle(base,request),FailureCode.INVALID_INPUT)
        }
        val before=game().snapshot; val changed=before.copy(variants=SnapshotList.of(initial.copy(seed=Long.MIN_VALUE)))
        val one=ok(LifeRandomEngine.resolve(before,action,reading.wall,Source(),variants=manager()))
        val two=ok(LifeRandomEngine.resolve(changed,action,reading.wall,Source(),variants=manager()))
        assertEquals(one.recorded,two.recorded); assertEquals(Long.MIN_VALUE,two.snapshot.variants.single().seed)
    }
}
