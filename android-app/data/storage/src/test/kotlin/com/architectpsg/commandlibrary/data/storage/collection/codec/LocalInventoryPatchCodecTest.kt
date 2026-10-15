package com.architectpsg.commandlibrary.data.storage.collection.codec

import com.architectpsg.commandlibrary.core.model.*
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class LocalInventoryPatchCodecTest {
    private val codec=LocalInventoryPatchCodec()
    private fun <T> ok(result:Outcome<T>):T=(result as Outcome.Success).value
    private fun bytes(patch:CollectionPatch)=ok(codec.encodePatch(patch)).copyBytes()
    private fun read(bytes:ByteArray)=codec.decodePatch(SnapshotBytes(bytes))
    private fun lot()=OwnedLot(EntityId("foreign lot"),CardIdentity(Knowledge.Unknown("oracle",SnapshotList.empty()),
        Knowledge.Known(CardId(ProviderId("other"),"printing")),ProviderIdentity(ProviderId("raw"),"kind","raw"),
        Knowledge.Absent("name"),LanguageTag("ja")),Knowledge.Known(Finish.ETCHED),CardCondition.DAMAGED,
        "Shelf Ω",0,"Notes 🐉",Revision(7),Knowledge.Known(UtcTimestamp(-2,4)))
    private fun bad(value:ByteArray){assertEquals(FailureCode.CORRUPT_DATA,(read(value) as Outcome.Failure).error.code)}
    @Test fun manuallyFramedEmptyGoldenIsExact(){
        val expected=requireNotNull(javaClass.getResourceAsStream("/empty-inventory-patch-v1.hex")).bufferedReader().use{it.readText().trim()}
            .chunked(2).map{it.toInt(16).toByte()}.toByteArray()
        val patch=CollectionPatch(SnapshotList.empty());assertArrayEquals(expected,bytes(patch));assertEquals(patch,ok(read(expected)))
    }
    @Test fun everyVariantRetainsExactOrderReasonsSignedDeltaAndForeignMetadata(){
        val patch=CollectionPatch(SnapshotList.of(CollectionEdit.Remove(EntityId("z"),"remove\nΩ"),CollectionEdit.Add(lot()),
            CollectionEdit.Adjust(EntityId("a"),Int.MIN_VALUE,"exact reason 🐉"),CollectionEdit.Replace(lot().copy(quantity=Int.MAX_VALUE))))
        assertEquals(patch,ok(read(bytes(patch))))
        assertFalse(bytes(patch).contentEquals(bytes(patch.copy(edits=SnapshotList.copyOf(patch.edits.reversed())))))
    }
    @Test fun malformedVersionsTagsCountsTruncationAndTrailingFailWithoutPartialPatch(){
        val full=bytes(CollectionPatch(SnapshotList.of(CollectionEdit.Adjust(EntityId("a"),-7,"reason"))))
        for(end in full.indices)bad(full.copyOf(end))
        bad(full+byteArrayOf(0));bad(full.copyOf().also{it[0]=0});bad(full.copyOf().also{it[5]=0})
        bad(full.copyOf().also{it[10]=4});bad(full.copyOf().also{ByteBuffer.wrap(it).putInt(6,-1)})
        assertEquals(UnavailableReason.UNSUPPORTED,(read(full.copyOf().also{it[5]=2}) as Outcome.Unavailable).reason)
    }
    @Test fun limitsRejectWithoutTruncation(){
        val edit=CollectionEdit.Remove(EntityId("x"),"")
        val many=CollectionPatch(SnapshotList.copyOf(List(InventoryProjectionLimits.MAX_EDITS+1){edit}))
        assertEquals(FailureCode.INVALID_INPUT,(codec.encodePatch(many) as Outcome.Failure).error.code)
        val huge=CollectionPatch(SnapshotList.of(CollectionEdit.Remove(EntityId("x"),"x".repeat(DeckPayloadLimits.MAX_TEXT_BYTES+1))))
        assertEquals(FailureCode.INVALID_INPUT,(codec.encodePatch(huge) as Outcome.Failure).error.code)
        bad(ByteArray(DeckPayloadLimits.MAX_ENCODED_BYTES+1))
    }
}
