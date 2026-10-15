package com.architectpsg.commandlibrary.feature.collection.persistence

import com.architectpsg.commandlibrary.core.model.*
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class InventoryRequestsTest {
    private val token=object:CancelToken { override val isCancelled=false;override fun throwIfCancelled()=Unit }
    private val empty=SnapshotBytes(byteArrayOf(67,76,73,80,0,1,0,0,0,0))
    @Test fun independentFrozenRequestGoldensMatch(){
        assertEquals("0f2fbe33508586a7bdfa3cac35f33c74c3c08e52b67ccfcbcd8f43a27a43dd1c",InventoryRequests.apply(Revision(0),empty,token))
        assertEquals("815e01ab5aa9f26e147bd0147e93ae38276314199e82340027088e19cff76f46",InventoryRequests.undo(Revision(7),token))
        assertNotEquals(InventoryRequests.undo(Revision(0),token),InventoryRequests.apply(Revision(0),empty,token))
        assertNotEquals(InventoryRequests.apply(Revision(0),empty,token),InventoryRequests.apply(Revision(1),empty,token))
    }
    @Test fun futureOrOversizedPayloadCannotChangeFrozenRequestEncoding(){
        for(payload in listOf(SnapshotBytes(byteArrayOf(67,76,73,80,0,2,0,0,0,0)),SnapshotBytes(ByteArray(DeckPayloadLimits.MAX_ENCODED_BYTES+1)))){
            try{InventoryRequests.apply(Revision(0),payload,token);fail()}catch(expected:IllegalArgumentException){}
        }
    }
    @Test fun cancelledRequestPropagatesBeforeInputWorkAndDuringChunks(){
        var calls=0
        val cancel=object:CancelToken{override val isCancelled get()=calls>=5;override fun throwIfCancelled(){if(++calls>=5)throw CancellationException()}}
        val bytes=ByteArray(8192).also{System.arraycopy(empty.copyBytes(),0,it,0,10)}
        try{InventoryRequests.apply(Revision(0),SnapshotBytes(bytes),cancel);fail()}catch(expected:CancellationException){}
        assertEquals(5,calls)
    }
}
