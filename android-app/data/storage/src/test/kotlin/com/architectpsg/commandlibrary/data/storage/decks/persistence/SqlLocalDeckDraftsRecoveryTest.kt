package com.architectpsg.commandlibrary.data.storage.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.decks.persistence.DraftFixture.Companion.ok
import com.architectpsg.commandlibrary.data.storage.decks.persistence.DraftFixture.Companion.failure
import com.architectpsg.commandlibrary.data.storage.decks.persistence.DraftFixture.Companion.unavailable
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SqlLocalDeckDraftsRecoveryTest {
    @Test fun everyMutationPrefixFailureRollsBackAllDeckDraftTablesAndSequence()=runBlocking { prefixes(false) }
    @Test fun everyMutationPrefixCancellationEscapesUnchangedAndRollsBackEverything()=runBlocking { prefixes(true) }
    private suspend fun prefixes(cancellation:Boolean) {
        for(mode in 0..9) for(prefix in 1..when(mode) { 0,1,4 -> 1; 2,3,9 -> 2; 7 -> 3; 8 -> 6; else -> 7 }) DraftFixture(HostDeckDraftDatabase()).use { f ->
            var base=f.create(); if(mode == 6) base=f.rename(base)
            val state=if(mode == 0 || mode == 4) null else f.write(if(mode == 7) f.form(base).copy(quantity="02") else f.details(base))
            val prepared=if(mode in setOf(3,5,6,7,8,9)) f.prepare(base,state!!) else null
            if(mode == 8) f.write(f.details(base,"later"),1)
            val before=f.db.state(); var writes=0; val exception=CancellationException("exact prefix")
            f.db.host.afterWrite={ if(++writes == prefix) { if(cancellation) throw exception else error("injected SQL failure") } }
            try {
                val result:Outcome<*> = when(mode) {
                    0 -> f.service().write(base.id,0,f.details(base),f.cancel)
                    1 -> f.service().write(base.id,1,f.details(base,"second"),f.cancel)
                    2 -> f.service().prepare(base.id,state!!.draft!!.token,DeckDraftCommitScope.TEXT_AUTOSAVE,ok(f.planner.propose(base,state.draft!!.body,DeckDraftCommitScope.TEXT_AUTOSAVE,null,f.cancel)).patch,EntityId("fault"),f.cancel)
                    3 -> f.service().discard(base.id,1,prepared!!.intent.operationId,DeckDraftDiscardMode.INPUT,f.cancel)
                    4 -> f.service().discard(base.id,0,null,DeckDraftDiscardMode.INPUT,f.cancel)
                    9 -> f.service().discard(base.id,1,prepared!!.intent.operationId,DeckDraftDiscardMode.PREPARATION_ONLY,f.cancel)
                    else -> f.service().commit(base.id,prepared!!.intent.operationId,prepared.intent.requestSha256,f.cancel)
                }
                if(cancellation) fail("expected cancellation mode=$mode prefix=$prefix") else failure(result,FailureCode.PERSISTENCE)
            } catch(actual:CancellationException) { assertTrue(cancellation); assertSame(exception,actual) }
            f.db.host.afterWrite=null; assertEquals("mode=$mode prefix=$prefix",before,f.db.state())
        }
    }
    @Test fun lostAcknowledgementsForWritePrepareCommitDiscardReconcileAfterReopen()=runBlocking {
        val file=HostDeckDraftDatabase.databaseFile(); lateinit var base:DeckSnapshot; lateinit var prepared:DeckDraftPreparation
        DraftFixture(HostDeckDraftDatabase(file)).use { f ->
            base=f.create(); f.db.loseAcknowledgement=true
            try { f.service().write(base.id,0,f.details(base),f.cancel); fail("lost ack") } catch(_:CancellationException) { }
            f.db.loseAcknowledgement=false; val state=f.write(f.details(base)); val before=f.db.state()
            assertEquals(state,ok(f.service().write(base.id,0,f.details(base),f.cancel))); assertEquals(before,f.db.state())
            f.db.loseAcknowledgement=true
            try { f.prepare(base,state); fail("lost ack") } catch(_:CancellationException) { }
            f.db.loseAcknowledgement=false; prepared=f.prepare(base,state); f.db.loseAcknowledgement=true
            try { f.commit(prepared); fail("lost ack") } catch(_:CancellationException) { }
        }
        DraftFixture(HostDeckDraftDatabase(file,false)).use { f ->
            val before=f.db.state(); val ack=f.commit(prepared); assertEquals(Revision(1),ack.snapshot.revision); assertEquals(before,f.db.state())
            f.db.loseAcknowledgement=true
            try { f.service().discard(base.id,2,null,DeckDraftDiscardMode.INPUT,f.cancel); fail("lost ack") } catch(_:CancellationException) { }
            f.db.loseAcknowledgement=false; val discarded=f.db.state(); assertEquals(3L,ok(f.service().discard(base.id,2,null,DeckDraftDiscardMode.INPUT,f.cancel)).generation); assertEquals(discarded,f.db.state())
        }
    }
    @Test fun allMalformedSlotMetadataRejectsBeforeAnyRawOrPatchBlobFetch()=runBlocking {
        for(change in listOf("metadata_version=0","generation=0","generation='bad'","raw_length=9000000","raw_length='bad'","raw=CAST('wrong' AS TEXT)","raw_version=NULL","raw_sha256='bad'","raw_base_revision=NULL","state_sha256='bad'","last_mutation_kind='other'")) DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); f.write(f.details(a)); f.db.sql("PRAGMA foreign_keys=OFF"); f.db.sql("UPDATE deck_draft_state SET $change"); f.db.sql("PRAGMA foreign_keys=ON")
            val before=f.db.state(); f.db.queries.clear(); failure(f.service().load(a.id,f.cancel),FailureCode.CORRUPT_DATA); assertEquals(0,f.db.draftBlobs()); assertEquals(before,f.db.state())
        }
    }
    @Test fun malformedIntentMetadataAndCompletionRejectBeforeBothBlobKinds()=runBlocking {
        for(change in listOf("metadata_version=0","source_generation=0","raw_length=9000000","patch_length=9000000","patch=CAST('bad' AS TEXT)","patch_sha256='bad'","request_sha256='bad'","status='other'","receipt_sequence=1","effect_kind='CLEARED'","state_sha256='bad'")) DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); f.prepare(a,f.write(f.details(a))); f.db.sql("UPDATE deck_draft_intent SET $change")
            val before=f.db.state(); f.db.queries.clear(); failure(f.service().load(a.id,f.cancel),FailureCode.CORRUPT_DATA); assertEquals(0,f.db.draftBlobs()); assertEquals(before,f.db.state())
        }
    }
    @Test fun newerSlotIntentRawPatchAndRequestVersionsAreTypedUnsupportedBeforeBlobs()=runBlocking {
        for((table,column) in listOf("deck_draft_state" to "metadata_version","deck_draft_state" to "raw_version","deck_draft_intent" to "metadata_version","deck_draft_intent" to "raw_version","deck_draft_intent" to "patch_version","deck_draft_intent" to "request_version")) DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); f.prepare(a,f.write(f.details(a))); f.db.sql("UPDATE $table SET $column=2"); f.db.queries.clear()
            unavailable(f.service().load(a.id,f.cancel),UnavailableReason.UNSUPPORTED); assertEquals(0,f.db.draftBlobs())
        }
    }
    @Test fun selectedPayloadHashMismatchIsObservedOnlyAfterMetadataAdmission()=runBlocking {
        DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); f.prepare(a,f.write(f.details(a)))
            val bytes=f.db.host.connection.prepare("SELECT raw FROM deck_draft_intent").use { check(it.step()); it.getBlob(0) }; bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
            f.db.host.connection.prepare("UPDATE deck_draft_intent SET raw=?").use { it.bindBlob(1,bytes); it.step() }
            f.db.queries.clear(); failure(f.service().load(a.id,f.cancel),FailureCode.CORRUPT_DATA)
            assertEquals(1,f.db.queries.count { it.statement.startsWith("SELECT raw FROM deck_draft_intent") }); assertFalse(f.db.queries.any { it.statement.startsWith("SELECT patch FROM") })
        }
    }
    @Test fun coherentUnrelatedHistoricalResultFailsAllCommittedAdmissionPathsIncludingCleared()=runBlocking {
        DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); val s=f.write(f.details(a)); val p=f.prepare(a,s); val ack=f.commit(p); assertTrue(ack.slotEffect is DeckDraftSlotEffect.Cleared)
            replaceRevision(f,ack.snapshot.copy(primer="coherently encoded unrelated content")); val before=f.db.state()
            failure(f.service().load(a.id,f.cancel),FailureCode.CORRUPT_DATA)
            failure(f.service().prepare(a.id,p.intent.sourceToken,p.intent.scope,p.intent.patch,p.intent.operationId,f.cancel),FailureCode.CORRUPT_DATA)
            failure(f.service().commit(a.id,p.intent.operationId,p.intent.requestSha256,f.cancel),FailureCode.CORRUPT_DATA)
            assertEquals(before,f.db.state()); assertEquals(0L,f.db.host.scalar("SELECT count(*) FROM pragma_foreign_key_check"))
        }
    }
    @Test fun committedReceiptSwapAndNonterminalReceiptContradictionsAreRejectedBeforeBlobs()=runBlocking {
        for(terminal in listOf(false,true)) DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); val p=f.prepare(a,f.write(f.details(a)))
            if(terminal) { f.commit(p); f.db.sql("UPDATE deck_draft_intent SET receipt_sequence=1"); rehashIntent(f,"prepared") }
            else f.db.sql("INSERT INTO deck_operation(operation_id,request_version,request_sha256,kind,provider,deck_id,result_revision) VALUES ('prepared',1,'${"a".repeat(64)}','APPLY','local-decks','deck',0)")
            f.db.queries.clear(); failure(f.service().load(a.id,f.cancel),FailureCode.CORRUPT_DATA); assertEquals(0,f.db.draftBlobs())
        }
    }
    @Test fun deletedNewestIntentWithSurvivingReceiptCannotDisappearBehindNullLatest()=runBlocking {
        DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); val p=f.prepare(a,f.write(f.details(a))); f.commit(p)
            f.db.sql("UPDATE deck_draft_state SET latest_operation_id=NULL"); rehashSlot(f); f.db.sql("DELETE FROM deck_draft_intent")
            val before=f.db.state(); f.db.queries.clear(); failure(f.service().load(a.id,f.cancel),FailureCode.CORRUPT_DATA); assertEquals(0,f.db.draftBlobs())
            failure(f.service().commit(a.id,p.intent.operationId,p.intent.requestSha256,f.cancel),FailureCode.CORRUPT_DATA); assertEquals(before,f.db.state())
        }
    }
    @Test fun validHashLatestPointerAndMultiplePreparedRowsMustRespectIndexedDiscovery()=runBlocking {
        for(multiple in listOf(false,true)) DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); val first=f.prepare(a,f.write(f.details(a)))
            val next=ok(f.service().discard(a.id,1,first.intent.operationId,DeckDraftDiscardMode.PREPARATION_ONLY,f.cancel)); f.prepare(a,next,"second")
            if(multiple) { f.db.sql("UPDATE deck_draft_intent SET status='PREPARED' WHERE operation_id='prepared'"); rehashIntent(f,"prepared") }
            else { f.db.sql("UPDATE deck_draft_state SET latest_operation_id='prepared'"); rehashSlot(f) }
            val before=f.db.state(); f.db.queries.clear(); failure(f.service().load(a.id,f.cancel),FailureCode.CORRUPT_DATA); assertEquals(0,f.db.draftBlobs()); assertEquals(before,f.db.state())
        }
    }
    @Test fun maximumRawGenerationAndSequenceFailBeforeClockWithoutMutation()=runBlocking {
        DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); val state=f.write(f.details(a)); val raw=ok(f.rawCodec.encodeDraft(state.draft!!.body)).copyBytes()
            val writeSha=frame("CLDM") { byte(0); deck(); long(Long.MAX_VALUE-1); bytes(raw) }
            f.db.sql("UPDATE deck_draft_state SET generation=9223372036854775807,last_mutation_sha256='$writeSha'"); rehashSlot(f)
            val before=f.db.state(); failure(f.service().write(a.id,Long.MAX_VALUE,f.details(a,"next"),f.cancel),FailureCode.PERSISTENCE)
            failure(f.service().discard(a.id,Long.MAX_VALUE,null,DeckDraftDiscardMode.INPUT,f.cancel),FailureCode.PERSISTENCE); assertEquals(before,f.db.state())
            val max=ok(f.service().load(a.id,f.cancel)); val p=f.prepare(a,max); val prepared=f.db.state()
            failure(f.service().commit(a.id,p.intent.operationId,p.intent.requestSha256,f.cancel),FailureCode.PERSISTENCE); assertEquals(prepared,f.db.state()); assertEquals(0,f.clockCalls)
        }
        DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); val p=f.prepare(a,f.write(f.details(a))); f.db.sql("UPDATE sqlite_sequence SET seq=9223372036854775807 WHERE name='deck_operation'")
            val before=f.db.state(); failure(f.service().commit(a.id,p.intent.operationId,p.intent.requestSha256,f.cancel),FailureCode.PERSISTENCE); assertEquals(before,f.db.state()); assertEquals(0,f.clockCalls)
        }
    }
    @Test fun maximumContentRevisionAllowsEmptyAliasButNoNonemptyPreparation()=runBlocking {
        DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); f.db.sql("INSERT INTO deck_revision SELECT provider,deck_id,9223372036854775807,name,created_seconds,created_nano,modified_seconds,modified_nano,entry_count,total_quantity,payload_bytes,payload_sha256,payload FROM deck_revision WHERE revision=0")
            val max=a.copy(revision=Revision(Long.MAX_VALUE)); replaceRevision(f,max); f.db.sql("UPDATE deck_head SET revision=9223372036854775807"); f.db.sql("INSERT INTO deck_undo_state VALUES ('local-decks','deck',9223372036854775807,NULL)")
            val s=f.write(f.form(max).copy(quantity="02")); val p=f.prepare(max,s); val ack=f.commit(p); assertEquals(max,ack.snapshot); assertEquals(0,f.clockCalls)
            val changed=f.write(f.details(max),2); val patch=DeckPatch(SnapshotList.of(DeckEdit.Rename("Changed")),DeckDraftLimits.TEXT_REASON)
            failure(f.service().prepare(a.id,changed.draft!!.token,DeckDraftCommitScope.TEXT_AUTOSAVE,patch,EntityId("exhausted"),f.cancel),FailureCode.PERSISTENCE)
        }
    }
    @Test fun staleAndCorruptCurrentHeadsDoNotAffectHistoricalReplayButBlockFreshCommit()=runBlocking {
        DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); val p=f.prepare(a,f.write(f.details(a))); f.rename(a); val before=f.db.state()
            failure(f.service().commit(a.id,p.intent.operationId,p.intent.requestSha256,f.cancel),FailureCode.STALE_REVISION); assertEquals(before,f.db.state())
            f.db.sql("UPDATE deck_head SET revision=0,name='Name',modified_seconds=2,modified_nano=0"); f.db.sql("DELETE FROM deck_undo_state")
            val corrupt=f.db.state(); failure(f.service().commit(a.id,p.intent.operationId,p.intent.requestSha256,f.cancel),FailureCode.CORRUPT_DATA); assertEquals(corrupt,f.db.state())
        }
    }
    @Test fun ignoredWritesAndClockExceptionsCancelWithoutPartialCommit()=runBlocking {
        DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); val p=f.prepare(a,f.write(f.details(a))); val before=f.db.state()
            f.db.sql("CREATE TRIGGER reject_intent BEFORE UPDATE ON deck_draft_intent BEGIN SELECT RAISE(IGNORE); END")
            failure(f.service().commit(a.id,p.intent.operationId,p.intent.requestSha256,f.cancel),FailureCode.PERSISTENCE); assertEquals(before,f.db.state()); f.db.sql("DROP TRIGGER reject_intent")
            val exception=CancellationException("clock cancellation")
            for(cancel in listOf(false,true)) {
                val clock=object:Clock { override fun utcNow():UtcTimestamp { if(cancel) throw exception else error("private detail") }; override fun monotonicNow()=kotlin.time.Duration.ZERO }
                try { failure(f.service(clock).commit(a.id,p.intent.operationId,p.intent.requestSha256,f.cancel),FailureCode.INTERNAL); if(cancel) fail("cancellation") }
                catch(actual:CancellationException) { assertTrue(cancel); assertSame(exception,actual) }
                assertEquals(before,f.db.state())
            }
        }
    }
    @Test fun packagePrimitiveUnsupportedAndCancelledAdmissionPreserveRecoveryAndIntent()=runBlocking {
        DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(f.deck().copy(packageInstances=SnapshotList.of(PackageInstance(EntityId("i"),EntityId("p"),EntityId("v"),PackageLinkPolicy.COPY,SnapshotList.empty()))))
            val s=f.write(f.details(a)); val before=f.db.state()
            unavailable(f.service().prepare(a.id,s.draft!!.token,DeckDraftCommitScope.TEXT_AUTOSAVE,DeckPatch(SnapshotList.of(DeckEdit.Rename("Changed")),DeckDraftLimits.TEXT_REASON),EntityId("unsupported"),f.cancel),UnavailableReason.UNSUPPORTED)
            val exact=CancellationException("admission"); val token=object:CancelToken { var checks=0; override val isCancelled get()=checks>10; override fun throwIfCancelled() { if(++checks>10) throw exact } }
            try { f.service().load(a.id,token); fail("cancel") } catch(actual:CancellationException) { assertSame(exact,actual) }
            assertEquals(before,f.db.state())
        }
    }
    @Test fun equalGenerationCommittedEffectsBindExactCurrentRawShapeAndTokenBeforeBlobs()=runBlocking {
        for(mode in 0..2) DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); val body=if(mode == 1) f.details(a,"").copy(primer="changed") else f.details(a)
            val p=f.prepare(a,f.write(body))
            if(mode == 2) f.write(f.details(a,"later"),1)
            val ack=f.commit(p)
            coherentRaw(f,f.details(if(mode == 2) a else ack.snapshot,"contradictory same-generation recovery"),"WRITE")
            val before=f.db.state(); f.db.queries.clear()
            failure(f.service().load(a.id,f.cancel),FailureCode.CORRUPT_DATA); assertEquals(0,f.db.draftBlobs()); assertEquals(before,f.db.state())
        }
    }
    @Test fun commitMutationRequiresExactRetainedProvenanceBeforeBlobs()=runBlocking {
        for(missing in listOf(false,true)) DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); val p=f.prepare(a,f.write(f.details(a))); f.commit(p)
            if(missing) {
                f.db.sql("PRAGMA foreign_keys=OFF"); f.db.sql("UPDATE deck_draft_state SET latest_operation_id=NULL"); f.db.sql("DELETE FROM deck_draft_intent"); f.db.sql("DELETE FROM deck_operation WHERE operation_id='prepared'"); f.db.sql("PRAGMA foreign_keys=ON")
            } else f.db.sql("UPDATE deck_draft_state SET last_mutation_sha256='${"a".repeat(64)}'")
            rehashSlot(f); f.db.queries.clear(); failure(f.service().load(a.id,f.cancel),FailureCode.CORRUPT_DATA); assertEquals(0,f.db.draftBlobs())
        }
    }
    @Test fun mutationKindsConstrainRawShapeEvenWithCoherentSlotHash()=runBlocking {
        for(kind in listOf("WRITE","DISCARD_INPUT","DISCARD_PREP")) DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); f.write(f.details(a))
            coherentRaw(f,if(kind == "DISCARD_INPUT") f.details(a) else null,kind)
            f.db.queries.clear(); failure(f.service().load(a.id,f.cancel),FailureCode.CORRUPT_DATA); assertEquals(0,f.db.draftBlobs())
        }
    }
    @Test fun olderReachedReceiptAndEffectCorruptionAreRejectedBeforeLatestPendingPayloads()=runBlocking {
        for(badEffect in listOf(false,true)) DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); val p=f.prepare(a,f.write(f.details(a))); val ack=f.commit(p)
            f.prepare(ack.snapshot,f.write(f.details(ack.snapshot,"next"),2),"next")
            if(badEffect) {
                f.db.sql("UPDATE deck_draft_intent SET effect_kind='PRESERVED_LATER',effect_generation=99,effect_base_revision=0,effect_raw_sha256='${"b".repeat(64)}' WHERE operation_id='prepared'"); rehashIntent(f,"prepared")
            } else f.db.sql("UPDATE deck_operation SET request_sha256='${"a".repeat(64)}' WHERE operation_id='prepared'")
            f.db.queries.clear(); failure(f.service().load(a.id,f.cancel),FailureCode.CORRUPT_DATA); assertEquals(0,f.db.draftBlobs())
        }
    }
    @Test fun freshCommitAdmitsOlderReachedMetadataBeforePendingPayloadsClockOrWrites()=runBlocking {
        for(badEffect in listOf(false,true)) DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); val p=f.prepare(a,f.write(f.details(a))); val ack=f.commit(p)
            val pending=f.prepare(ack.snapshot,f.write(f.details(ack.snapshot,"next"),2),"next")
            if(badEffect) {
                f.db.sql("UPDATE deck_draft_intent SET effect_kind='PRESERVED_LATER',effect_generation=99,effect_base_revision=0,effect_raw_sha256='${"b".repeat(64)}' WHERE operation_id='prepared'"); rehashIntent(f,"prepared")
            } else f.db.sql("UPDATE deck_operation SET request_sha256='${"a".repeat(64)}' WHERE operation_id='prepared'")
            val before=f.db.state(); val clockCalls=f.clockCalls; f.db.queries.clear()
            failure(f.service().commit(a.id,pending.intent.operationId,pending.intent.requestSha256,f.cancel),FailureCode.CORRUPT_DATA)
            assertEquals(0,f.db.draftBlobs()); assertEquals(clockCalls,f.clockCalls); assertEquals(before,f.db.state())
            f.db.queries.clear(); failure(f.service().load(a.id,f.cancel),FailureCode.CORRUPT_DATA)
            assertEquals(0,f.db.draftBlobs()); assertEquals(clockCalls,f.clockCalls); assertEquals(before,f.db.state())
        }
    }
    @Test fun retainedIntentPolicyRefusalIsCorruptionWhileFreshPackagePreparationIsUnsupported()=runBlocking {
        DraftFixture(HostDeckDraftDatabase()).use { f ->
            val a=f.create(); val p=f.prepare(a,f.write(f.details(a)))
            replaceRevision(f,a.copy(packageInstances=SnapshotList.of(PackageInstance(EntityId("i"),EntityId("p"),EntityId("v"),PackageLinkPolicy.COPY,SnapshotList.empty()))))
            val before=f.db.state(); failure(f.service().load(a.id,f.cancel),FailureCode.CORRUPT_DATA)
            failure(f.service().commit(a.id,p.intent.operationId,p.intent.requestSha256,f.cancel),FailureCode.CORRUPT_DATA); assertEquals(before,f.db.state())
        }
    }
    private fun coherentRaw(f:DraftFixture,body:DeckDraftBody?,kind:String) {
        val generation=f.db.host.scalar("SELECT generation FROM deck_draft_state")
        val raw=body?.let { ok(f.rawCodec.encodeDraft(it)).copyBytes() }
        val fingerprint=if(kind == "WRITE" && raw != null) frame("CLDM") { byte(0); deck(); long(generation-1); bytes(raw) } else "a".repeat(64)
        val rawSql=if(raw == null) "raw_base_revision=NULL,raw_version=NULL,raw_length=NULL,raw_sha256=NULL,raw=NULL" else {
            val hex=raw.joinToString("") { "%02x".format(it.toInt() and 255) }
            "raw_base_revision=${body!!.baseRevision.value},raw_version=1,raw_length=${raw.size},raw_sha256='${sha(raw)}',raw=x'$hex'"
        }
        f.db.sql("UPDATE deck_draft_state SET $rawSql,last_mutation_kind='$kind',last_mutation_sha256='$fingerprint'"); rehashSlot(f)
    }
    private fun q(value:String)="'${value.replace("'","''")}'"
    private fun sha(value:ByteArray)=MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun replaceRevision(f:DraftFixture,snapshot:DeckSnapshot) {
        val bytes=ok(f.codec.encodeSnapshot(snapshot)).copyBytes(); val hex=bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
        f.db.sql("UPDATE deck_revision SET name=${q(snapshot.name)},created_seconds=${snapshot.createdAt.epochSeconds},created_nano=${snapshot.createdAt.nanoAdjustment},modified_seconds=${snapshot.modifiedAt.epochSeconds},modified_nano=${snapshot.modifiedAt.nanoAdjustment},payload_bytes=${bytes.size},payload_sha256='${sha(bytes)}',payload=x'$hex' WHERE revision=${snapshot.revision.value}")
    }
    /** Independent canonical CLDS/CLDI fixture encoder; never invokes production framing. */
    private class Frame(val out:DataOutputStream) {
        fun byte(value:Int)=out.writeByte(value); fun long(value:Long)=out.writeLong(value)
        fun bytes(value:ByteArray) { out.writeInt(value.size); out.write(value) }
        fun text(value:String)=bytes(value.toByteArray(Charsets.UTF_8))
        fun hash(value:String)=out.write(value.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        fun deck() { text("local-decks"); text("deck") }
    }
    private fun frame(domain:String,action:Frame.()->Unit):String { val buffer=ByteArrayOutputStream(); DataOutputStream(buffer).use { it.write(domain.toByteArray()); it.writeShort(1); Frame(it).action() }; return sha(buffer.toByteArray()) }
    private fun fields(f:DraftFixture,table:String,where:String=""):Map<String,Any?> = f.db.host.connection.prepare("SELECT * FROM $table $where").use { row ->
        check(row.step()); (0 until row.getColumnCount()).associate { i -> row.getColumnName(i) to when(row.getColumnType(i)) { 1 -> row.getLong(i); 3 -> row.getText(i); 4 -> row.getBlob(i); else -> null } }
    }
    private fun rehashSlot(f:DraftFixture) {
        val row=fields(f,"deck_draft_state"); val digest=frame("CLDS") {
            deck(); long(row["generation"] as Long)
            val raw=row["raw_base_revision"] as Long?; byte(if(raw == null) 0 else 1)
            if(raw != null) { long(raw); out.writeShort((row["raw_version"] as Long).toInt()); out.writeInt((row["raw_length"] as Long).toInt()); hash(row["raw_sha256"] as String) }
            val latest=row["latest_operation_id"] as String?; byte(if(latest == null) 0 else 1); if(latest != null) text(latest)
            byte(listOf("WRITE","DISCARD_INPUT","DISCARD_PREP","COMMIT").indexOf(row["last_mutation_kind"])); hash(row["last_mutation_sha256"] as String)
        }; f.db.sql("UPDATE deck_draft_state SET state_sha256='$digest'")
    }
    private fun rehashIntent(f:DraftFixture,operation:String) {
        val row=fields(f,"deck_draft_intent","WHERE operation_id=${q(operation)}")
        val digest=frame("CLDI") {
            text(operation); deck(); long(row["source_generation"] as Long); long(row["source_base_revision"] as Long)
            out.writeShort((row["raw_version"] as Long).toInt()); out.writeInt((row["raw_length"] as Long).toInt()); hash(row["raw_sha256"] as String)
            byte(if(row["scope"] == "TEXT_AUTOSAVE") 0 else 1); out.writeShort((row["patch_version"] as Long).toInt()); out.writeInt((row["patch_length"] as Long).toInt()); hash(row["patch_sha256"] as String); out.writeShort(1); hash(row["request_sha256"] as String)
            when(row["status"]) { "PREPARED" -> byte(0); "ABANDONED" -> byte(2); else -> {
                byte(1); long(row["receipt_sequence"] as Long); long(row["result_revision"] as Long)
                byte(listOf("CLEARED","RESIDUAL","PRESERVED_LATER").indexOf(row["effect_kind"])); long(row["effect_generation"] as Long)
                if(row["effect_kind"] != "CLEARED") { long(row["effect_base_revision"] as Long); hash(row["effect_raw_sha256"] as String) }
            } }
        }; f.db.sql("UPDATE deck_draft_intent SET state_sha256='$digest' WHERE operation_id=${q(operation)}")
    }
}
