package com.architectpsg.commandlibrary.data.storage.collection.codec

import com.architectpsg.commandlibrary.core.model.*
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class OwnedLotPayloadCodecTest {
    private val codec=OwnedLotPayloadCodec()
    private fun minimal()=OwnedLot(EntityId("lot"),CardIdentity(Knowledge.Absent("none"),Knowledge.Absent("none"),null,
        Knowledge.Absent("none"),LanguageTag.ENGLISH),Knowledge.Absent("none"),CardCondition.UNKNOWN,"",0,"",Revision(0),Knowledge.Absent("none"))
    private fun <T> ok(value:Outcome<T>):T=(value as Outcome.Success).value
    private fun encoded(lot:OwnedLot)=ok(codec.encodeOwnedLot(lot)).copyBytes()
    private fun decoded(bytes:ByteArray)=ok(codec.decodeOwnedLot(SnapshotBytes(bytes)))
    private fun golden()=requireNotNull(javaClass.getResourceAsStream("/minimal-owned-lot-v1.hex")).bufferedReader().use{it.readText().trim()}
        .chunked(2).map{it.toInt(16).toByte()}.toByteArray()
    private fun corrupt(bytes:ByteArray){
        val error=(codec.decodeOwnedLot(SnapshotBytes(bytes)) as Outcome.Failure).error
        assertEquals(FailureCode.CORRUPT_DATA,error.code);assertEquals("Invalid owned lot payload",error.message)
    }
    @Test fun independentZeroQuantityGoldenIsExactInBothDirections(){
        assertEquals(96,golden().size);assertEquals(minimal(),decoded(golden()));assertArrayEquals(golden(),encoded(minimal()))
    }
    @Test fun completeRichForeignIdentityKnowledgeAndAllConditionFinishValuesRoundTripExactly(){
        val unknown=Knowledge.Unknown("Unresolved Ω",SnapshotList.of(Evidence("Evidence 🐉",ResourceUri("https://example.invalid/参考"),UtcTimestamp(-9,123)),Evidence("No URL",null,null)))
        val value=minimal().copy(id=EntityId("lot Ω"),identity=CardIdentity(unknown,Knowledge.Known(CardId(ProviderId("foreign"),"raw/絵")),
            ProviderIdentity(ProviderId("uninstalled"),"foreign kind","untouched raw"),Knowledge.Known("元の名前"),LanguageTag("ja")),
            finish=unknown,location="Shelf\nΩ",quantity=Int.MAX_VALUE,notes="Notes\n# plain text",revision=Revision(Long.MAX_VALUE),acquiredAt=unknown)
        assertEquals(value,decoded(encoded(value)))
        val known=value.copy(identity=value.identity.copy(oracleId=Knowledge.Known(OracleId(ProviderId("different oracle source"),"o"))),
            acquiredAt=Knowledge.Known(UtcTimestamp(Long.MIN_VALUE,999999999)))
        for(condition in CardCondition.entries) for(finish in Finish.entries){
            val row=known.copy(condition=condition,finish=Knowledge.Known(finish))
            assertEquals(row,decoded(encoded(row)))
        }
        assertEquals(unknown,value.identity.oracleId);assertEquals(Int.MAX_VALUE,value.quantity)
    }
    @Test fun missingAndUnresolvedMetadataRemainDifferentAndNoIdentityResolutionIsImplied(){
        val absent=minimal();val unknown=Knowledge.Unknown("not known",SnapshotList.empty())
        val unresolved=absent.copy(identity=absent.identity.copy(oracleId=unknown,printingId=unknown,originalName=unknown),finish=unknown,acquiredAt=unknown)
        assertEquals(unresolved,decoded(encoded(unresolved)));assertFalse(encoded(absent).contentEquals(encoded(unresolved)))
        val raw=unresolved.copy(identity=unresolved.identity.copy(raw=ProviderIdentity(ProviderId("foreign"),"oracle-or-printing-unresolved","same raw")))
        assertEquals(raw,decoded(encoded(raw)));assertTrue(decoded(encoded(raw)).identity.printingId is Knowledge.Unknown)
    }
    @Test fun malformedCountsTagsEnumsUnicodeNegativeQuantitiesAndTrailingBytesFailClosed(){
        val bytes=golden()
        for(size in 0 until bytes.size)corrupt(bytes.copyOf(size))
        corrupt(bytes+byteArrayOf(0));corrupt(bytes.copyOf().also{it[0]=0});corrupt(bytes.copyOf().also{it[5]=0})
        assertEquals(UnavailableReason.UNSUPPORTED,(codec.decodeOwnedLot(SnapshotBytes(bytes.copyOf().also{it[5]=2})) as Outcome.Unavailable).reason)
        corrupt(bytes.copyOf().also{it[13]=3});corrupt(bytes.copyOf().also{it[87]=3})
        corrupt(bytes.copyOf().also{it[18]=-1});corrupt(bytes.copyOf().also{it[60]='?'.code.toByte()})
        corrupt(bytes.copyOf().also{for(i in 71..74)it[i]=-1})
        corrupt(bytes.copyOf().also{for(i in 6..9)it[i]=-1})
        // Original finish starts at47; use Unknown(reason none), then an oversized evidence count.
        val reason=byteArrayOf(2)+ByteBuffer.allocate(4).putInt(4).array()+"none".toByteArray()+ByteBuffer.allocate(4).putInt(10001).array()
        corrupt(bytes.copyOfRange(0,47)+reason+bytes.copyOfRange(56,bytes.size))
    }
    @Test fun identifierTextAndPayloadBudgetsRejectRatherThanTruncateOrLeakInput(){
        val exact=minimal().copy(id=EntityId("x".repeat(1024)),notes="n".repeat(DeckPayloadLimits.MAX_TEXT_BYTES))
        assertEquals(exact,decoded(encoded(exact)))
        val invalid=listOf(minimal().copy(id=EntityId("x".repeat(1025))),minimal().copy(location="x".repeat(DeckPayloadLimits.MAX_TEXT_BYTES+1)),
            minimal().copy(notes="\ud800"),minimal().copy(finish=Knowledge.Unknown("reason",SnapshotList.copyOf(List(10001){Evidence("e",null,null)}))))
        for(value in invalid){val failure=(codec.encodeOwnedLot(value) as Outcome.Failure).error;assertEquals(FailureCode.INVALID_INPUT,failure.code);assertEquals("Invalid owned lot payload",failure.message)}
        corrupt(ByteArray(DeckPayloadLimits.MAX_ENCODED_BYTES+1))
        val long="x".repeat(DeckPayloadLimits.MAX_TEXT_BYTES)
        val many=minimal().copy(acquiredAt=Knowledge.Unknown("reason",SnapshotList.copyOf(List(9){Evidence(long,null,null)})))
        assertEquals(FailureCode.INVALID_INPUT,(codec.encodeOwnedLot(many) as Outcome.Failure).error.code)
    }
}
