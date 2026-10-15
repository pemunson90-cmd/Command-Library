package com.architectpsg.commandlibrary.feature.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.*
import org.junit.Test

/** Expected payloads and digests copied from independently generated/reviewed Python vectors. */
class DeckDraftFramesTest {
    private val id = DeckId(LocalDeckLibraryLimits.SOURCE, "golden-deck")
    private val cancel = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private fun token(g: Long, b: Long, h: String) = DeckDraftToken(id, g, Revision(b), h)
    private fun bytes(hex: String) = SnapshotBytes(ByteArray(hex.length / 2) { hex.substring(it*2,it*2+2).toInt(16).toByte() })
    @Test fun discard_input_named() {
        assertEquals("d945f901e39c76d43e20d8fefa7501775062748989049ee129d9367a654918ad", DeckDraftFrames.discard(id, 3L, EntityId("draft-op"), DeckDraftDiscardMode.INPUT, cancel))
    }
    @Test fun discard_input_null() {
        assertEquals("03e657ee756caeaf5f3021510e8674adb9ede30b0240aa92f5e939aa951b9b21", DeckDraftFrames.discard(id, 4L, null, DeckDraftDiscardMode.INPUT, cancel))
    }
    @Test fun discard_preparation_only() {
        assertEquals("1fd6db6ca139e50ef03f7ea5b1f83b26ea734361cd9f13f664a268563ae02369", DeckDraftFrames.discard(id, 3L, EntityId("draft-op"), DeckDraftDiscardMode.PREPARATION_ONLY, cancel))
    }
    @Test fun intent_abandoned() {
        assertEquals("ddf0e19aca1d58eeab86bdb2b39f4fde37b48216b88af4be7cfc39969d92720a", DeckDraftFrames.intent(id, EntityId("draft-op"), 3L, DeckDraftFrames.Raw(2L, DeckDraftFrames.Payload(1, 24L, "2e6ebdbfa35015417d17950f6435b67f725346c397d2dc4c9f0cf25824fe0867")), DeckDraftCommitScope.EXPLICIT_CONFIRMED, DeckDraftFrames.Payload(1, 33L, "0b186dd3ed217714bb4d619cdcf948c0a98c83f035c14aaecfcbca15ffa09004"), "3443e5fae53eb9cb002293f7a0a97377f72f5eceb92bb99a12af3f728997ce83", DeckDraftFrames.Status.Abandoned, cancel))
    }
    @Test fun intent_committed_cleared() {
        assertEquals("ab7b37397c8babe25339e2225167e9e7f95162e7c829d41bc329ab1c1cc5c496", DeckDraftFrames.intent(id, EntityId("draft-op"), 3L, DeckDraftFrames.Raw(2L, DeckDraftFrames.Payload(1, 24L, "2e6ebdbfa35015417d17950f6435b67f725346c397d2dc4c9f0cf25824fe0867")), DeckDraftCommitScope.EXPLICIT_CONFIRMED, DeckDraftFrames.Payload(1, 33L, "0b186dd3ed217714bb4d619cdcf948c0a98c83f035c14aaecfcbca15ffa09004"), "3443e5fae53eb9cb002293f7a0a97377f72f5eceb92bb99a12af3f728997ce83", DeckDraftFrames.Status.Committed(42L, 3L, DeckDraftSlotEffect.Cleared(4L)), cancel))
    }
    @Test fun intent_committed_preserved_later() {
        assertEquals("3faa219b54641323bbd560fa329b624ff42a68623d73af153835692910c01a1d", DeckDraftFrames.intent(id, EntityId("draft-op"), 3L, DeckDraftFrames.Raw(2L, DeckDraftFrames.Payload(1, 24L, "2e6ebdbfa35015417d17950f6435b67f725346c397d2dc4c9f0cf25824fe0867")), DeckDraftCommitScope.EXPLICIT_CONFIRMED, DeckDraftFrames.Payload(1, 33L, "0b186dd3ed217714bb4d619cdcf948c0a98c83f035c14aaecfcbca15ffa09004"), "3443e5fae53eb9cb002293f7a0a97377f72f5eceb92bb99a12af3f728997ce83", DeckDraftFrames.Status.Committed(42L, 3L, DeckDraftSlotEffect.PreservedLater(token(4L, 2L, "043585b724694a0030ca85c01951487e1d131f9482b6d372515feab9a019be8f"))), cancel))
    }
    @Test fun intent_committed_residual() {
        assertEquals("8aa53558bfc94bbb964375c2b1aa2c433b128a0d11cf701a105e7c4f050dea2e", DeckDraftFrames.intent(id, EntityId("text-op"), 3L, DeckDraftFrames.Raw(2L, DeckDraftFrames.Payload(1, 35L, "e0dadd48c86f3e91ddd198cfb0a8c20013d8e08e1bc3b55afc2972e507922b92")), DeckDraftCommitScope.TEXT_AUTOSAVE, DeckDraftFrames.Payload(1, 40L, "7bec5c897b940eb8a43adc6737f626855bff38d542ca1fa5f067b1d172c7aa53"), "7afe22ce303a35bf0b03c31b1088a7bdef4b41993b1269285ad984c4edc9ae18", DeckDraftFrames.Status.Committed(43L, 3L, DeckDraftSlotEffect.Residual(token(4L, 3L, "25ebd98955f726e096b938eefe130ab54bbc594fb072511cceaeefc441392551"))), cancel))
    }
    @Test fun intent_pending() {
        assertEquals("a18c5e62b525edaf75880906672a6996760bdc57f5cd4838ba207afe9daa146a", DeckDraftFrames.intent(id, EntityId("draft-op"), 3L, DeckDraftFrames.Raw(2L, DeckDraftFrames.Payload(1, 24L, "2e6ebdbfa35015417d17950f6435b67f725346c397d2dc4c9f0cf25824fe0867")), DeckDraftCommitScope.EXPLICIT_CONFIRMED, DeckDraftFrames.Payload(1, 33L, "0b186dd3ed217714bb4d619cdcf948c0a98c83f035c14aaecfcbca15ffa09004"), "3443e5fae53eb9cb002293f7a0a97377f72f5eceb92bb99a12af3f728997ce83", DeckDraftFrames.Status.Prepared, cancel))
    }
    @Test fun prepared_request() {
        assertEquals("3443e5fae53eb9cb002293f7a0a97377f72f5eceb92bb99a12af3f728997ce83", DeckDraftFrames.prepared(token(3L, 2L, "2e6ebdbfa35015417d17950f6435b67f725346c397d2dc4c9f0cf25824fe0867"), DeckDraftCommitScope.EXPLICIT_CONFIRMED, bytes("434c44500001000000010900000005656e7472790000000945646974206465636b"), cancel))
    }
    @Test fun prepared_text_request() {
        assertEquals("7afe22ce303a35bf0b03c31b1088a7bdef4b41993b1269285ad984c4edc9ae18", DeckDraftFrames.prepared(token(3L, 2L, "e0dadd48c86f3e91ddd198cfb0a8c20013d8e08e1bc3b55afc2972e507922b92"), DeckDraftCommitScope.TEXT_AUTOSAVE, bytes("434c445000010000000102000000036e6577000000124175746f73617665206465636b2074657874"), cancel))
    }
    @Test fun raw_commit_effect() {
        assertEquals("03cbdba1066cca6a7941a5dacb76d901254b5cc7460f1583a2f74e82004177b8", DeckDraftFrames.commitMutation(token(3L, 2L, "2e6ebdbfa35015417d17950f6435b67f725346c397d2dc4c9f0cf25824fe0867"), EntityId("draft-op"), "3443e5fae53eb9cb002293f7a0a97377f72f5eceb92bb99a12af3f728997ce83", cancel))
    }
    @Test fun raw_write() {
        assertEquals("42aa8e5d7be79861119a80d482924bc5039aa5adec985d7f4845aa5d308ea4c5", DeckDraftFrames.write(id, 2L, bytes("434c4446000103000000000000000200000005656e747279"), cancel))
    }
    @Test fun slot_pending() {
        assertEquals("09cd7c8b507f4b08ca434fe2207b324795ad91fb1f98eb4561bcb7f0b0b4eb2e", DeckDraftFrames.slot(id, 3L, DeckDraftFrames.Raw(2L, DeckDraftFrames.Payload(1, 24L, "2e6ebdbfa35015417d17950f6435b67f725346c397d2dc4c9f0cf25824fe0867")), EntityId("draft-op"), DeckDraftFrames.Mutation.WRITE, "42aa8e5d7be79861119a80d482924bc5039aa5adec985d7f4845aa5d308ea4c5", cancel))
    }
    @Test fun slot_tombstone() {
        assertEquals("6c1d842286b515eeca2810bc1fe958a859b8019c2a5ce5d55511a52879001103", DeckDraftFrames.slot(id, 4L, null, EntityId("draft-op"), DeckDraftFrames.Mutation.COMMIT, "03cbdba1066cca6a7941a5dacb76d901254b5cc7460f1583a2f74e82004177b8", cancel))
    }
    private val zero = "0".repeat(64)
    private fun invalid(action: () -> Unit) { try { action(); fail("Expected admission failure") } catch (_: IllegalArgumentException) { } }
    @Test fun invalidMetadataIsRejectedBeforeNarrowingOrHashing() {
        val raw = DeckDraftFrames.Raw(2, DeckDraftFrames.Payload(1,24,zero))
        val patch = DeckDraftFrames.Payload(1,33,zero)
        invalid { DeckDraftFrames.commitMutation(token(0,2,zero),EntityId("op"),zero,cancel) }
        invalid { DeckDraftFrames.slot(id,0,null,null,DeckDraftFrames.Mutation.WRITE,zero,cancel) }
        invalid { DeckDraftFrames.slot(id,1,raw.copy(payload=raw.payload.copy(version=65536)),null,DeckDraftFrames.Mutation.WRITE,zero,cancel) }
        invalid { DeckDraftFrames.slot(id,1,raw.copy(payload=raw.payload.copy(length=Long.MAX_VALUE)),null,DeckDraftFrames.Mutation.WRITE,zero,cancel) }
        invalid { DeckDraftFrames.slot(id,1,raw.copy(baseRevision=-1),null,DeckDraftFrames.Mutation.WRITE,zero,cancel) }
        invalid { DeckDraftFrames.slot(id,1,null,null,DeckDraftFrames.Mutation.WRITE,"A".repeat(64),cancel) }
        invalid { DeckDraftFrames.discard(id,0,null,DeckDraftDiscardMode.PREPARATION_ONLY,cancel) }
        invalid { DeckDraftFrames.intent(id,EntityId("op"),Long.MAX_VALUE,raw,DeckDraftCommitScope.EXPLICIT_CONFIRMED,patch,zero,
            DeckDraftFrames.Status.Committed(1,3,DeckDraftSlotEffect.Cleared(Long.MIN_VALUE)),cancel) }
        invalid { DeckDraftFrames.intent(id,EntityId("op"),3,raw,DeckDraftCommitScope.EXPLICIT_CONFIRMED,patch,zero,
            DeckDraftFrames.Status.Committed(0,3,DeckDraftSlotEffect.Cleared(4)),cancel) }
        invalid { DeckDraftFrames.intent(id,EntityId("op"),3,raw,DeckDraftCommitScope.EXPLICIT_CONFIRMED,patch,zero,
            DeckDraftFrames.Status.Committed(1,5,DeckDraftSlotEffect.Cleared(4)),cancel) }
        invalid { DeckDraftFrames.intent(id,EntityId("op"),3,raw,DeckDraftCommitScope.EXPLICIT_CONFIRMED,patch,zero,
            DeckDraftFrames.Status.Committed(1,3,DeckDraftSlotEffect.Residual(token(4,2,zero))),cancel) }
        invalid { DeckDraftFrames.intent(id,EntityId("op"),3,raw,DeckDraftCommitScope.EXPLICIT_CONFIRMED,patch,zero,
            DeckDraftFrames.Status.Committed(1,3,DeckDraftSlotEffect.PreservedLater(token(3,2,zero))),cancel) }
    }
    @Test fun mutationAndSourceIdentityFencesChangeFingerprints() {
        val a = DeckDraftFrames.discard(id,0,null,DeckDraftDiscardMode.INPUT,cancel)
        assertNotEquals(a,DeckDraftFrames.discard(id,1,null,DeckDraftDiscardMode.INPUT,cancel))
        assertNotEquals(a,DeckDraftFrames.discard(id,0,EntityId("op"),DeckDraftDiscardMode.INPUT,cancel))
        assertNotEquals(a,DeckDraftFrames.discard(id.copy(raw="different"),0,null,DeckDraftDiscardMode.INPUT,cancel))
        assertNotEquals(DeckDraftFrames.commitMutation(token(3,2,zero),EntityId("a"),zero,cancel),
            DeckDraftFrames.commitMutation(token(3,2,zero),EntityId("b"),zero,cancel))
        invalid { DeckDraftFrames.discard(id.copy(source=ProviderId("foreign")),0,null,DeckDraftDiscardMode.INPUT,cancel) }
    }
    @Test fun largePayloadHashingRemainsCancellable() {
        val data = ByteArray(1024*1024); "CLDF".toByteArray().copyInto(data); data[5]=1
        var checks=0
        val stopped = object : CancelToken { override val isCancelled = false
            override fun throwIfCancelled() { if (++checks==20) throw CancellationException("stop") } }
        try { DeckDraftFrames.write(id,0,SnapshotBytes(data),stopped); fail("Expected cancellation") }
        catch (_: CancellationException) { assertEquals(20,checks) }
    }
}
