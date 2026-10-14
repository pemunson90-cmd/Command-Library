package com.architectpsg.commandlibrary.core.model

import org.junit.Assert.*
import org.junit.Test

/** Synthetic protocol checks, no authentic named card/source fixture. */
class VariantShuffleContractsTest {
    private val provider=ProviderId("synthetic-shuffle")
    private val definition=ProviderIdentity(provider,"variant","manual")
    private val a=ProviderIdentity(provider,"card","a")
    private val b=ProviderIdentity(provider,"card","b")
    private val before=SnapshotList.of(a,a,b)
    private fun value()=RecordedRandomValue.VariantZoneShuffle(definition,"v1","shuffle","deck","policy","plan-v1",before,SnapshotList.of(b,a,a))
    private fun request()=RandomRequest.VariantZoneShuffle(EntityId("request"),definition,"shuffle","deck","policy","plan-v1")
    @Test fun recordsActualBeforeAndAfterOrderWithDuplicateCardMultiplicity() {
        val value=value(); assertEquals(listOf(a,a,b),value.beforeOrder); assertEquals(listOf(b,a,a),value.afterOrder)
        assertEquals(definition,request().definitionId); assertEquals("plan-v1",request().policyValue)
        assertThrows(IllegalArgumentException::class.java) { value.copy(afterOrder=SnapshotList.of(a,b,b)) }
        assertThrows(IllegalArgumentException::class.java) { value.copy(afterOrder=SnapshotList.of(a,b)) }
    }
    @Test fun acceptsExactEmptySingletonAndMaximumWithoutTruncation() {
        val value=value(); assertTrue(value.copy(beforeOrder=SnapshotList.empty(),afterOrder=SnapshotList.empty()).afterOrder.isEmpty())
        assertEquals(listOf(a),value.copy(beforeOrder=SnapshotList.of(a),afterOrder=SnapshotList.of(a)).afterOrder)
        val max=SnapshotList.copyOf(List(10_000) { a }); assertEquals(10_000,value.copy(beforeOrder=max,afterOrder=max).afterOrder.size)
        val tooMany=SnapshotList.copyOf(List(10_001) { a })
        assertThrows(IllegalArgumentException::class.java) { value.copy(beforeOrder=tooMany,afterOrder=tooMany) }
    }
    @Test fun rejectsMissingExplicitVersionActionZoneAndPolicyMetadata() {
        val value=value()
        assertThrows(IllegalArgumentException::class.java) { value.copy(definitionVersion="") }
        assertThrows(IllegalArgumentException::class.java) { value.copy(actionId=" ") }
        assertThrows(IllegalArgumentException::class.java) { value.copy(zoneId="") }
        assertThrows(IllegalArgumentException::class.java) { value.copy(policyKey="") }
        assertThrows(IllegalArgumentException::class.java) { value.copy(policyValue=" ") }
        assertThrows(IllegalArgumentException::class.java) { request().copy(actionId="") }
        assertThrows(IllegalArgumentException::class.java) { request().copy(zoneId="") }
        assertThrows(IllegalArgumentException::class.java) { request().copy(policyKey="") }
        assertThrows(IllegalArgumentException::class.java) { request().copy(policyValue="") }
    }
}
