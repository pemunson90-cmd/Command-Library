package com.architectpsg.commandlibrary.data.storage.life

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.life.codec.LifePayloadCodec
import com.architectpsg.commandlibrary.feature.life.persistence.SqlPlayerProfileRepository
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Duration

class SqlPlayerProfileRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private class Token: CancelToken {
        var stopped=false
        override val isCancelled get()=stopped
        override fun throwIfCancelled() { if(stopped) throw CancellationException("test cancellation") }
    }
    private val token=Token()
    private var sequence=0
    private var stamp=UtcTimestamp(10,17)
    private val clock=object: Clock {
        override fun utcNow()=stamp
        override fun monotonicNow()=Duration.ZERO
    }
    private val ids=object: IdSource { override fun nextId()="profile-${++sequence}" }
    private fun repo(db: Database, source: IdSource=ids)=SqlPlayerProfileRepository(db,LifePayloadCodec(),clock,source)
    private fun draft(name:String="É Synthetic")=PlayerProfileDraft(name,20,SnapshotList.of(CounterDefinition(
        EntityId("life"),"Life",null,null,20,CounterLossPolicy.None,false,CounterRole.LIFE)),SeatAppearance(null,null),SnapshotList.empty())
    private fun <T> ok(o:Outcome<T>):T=(o as Outcome.Success<T>).value
    private fun fails(o:Outcome<*>,code:FailureCode) {
        assertTrue("Expected Failure($code), received ${o.javaClass.simpleName}",o is Outcome.Failure)
        assertEquals(code,(o as Outcome.Failure).error.code)
    }

    @Test fun createUpdateReopenAndDeletePreserveExactDraftAndRevision()= runBlocking {
        val file=folder.newFile()
        var id=EntityId("unused")
        val initial=draft("E\u0301 原文")
        HostLifeDatabase(file).use { db ->
            val r=repo(db); val created=ok(r.create(initial,token));id=created.id
            assertEquals(Revision(0),created.revision); assertEquals(initial,created.value)
            stamp=UtcTimestamp(55,999_999_999)
            val updated=ok(r.update(id,draft("Changed"),Revision(0),token))
            assertEquals(Revision(1),updated.revision)
            assertEquals(55L,db.scalar("SELECT updated_seconds FROM life_profile"))
            assertEquals(999_999_999L,db.scalar("SELECT updated_nano FROM life_profile"))
        }
        HostLifeDatabase(file,false).use { db ->
            val r=repo(db);assertEquals(draft("Changed"),ok(r.read(id,token)).value)
            ok(r.delete(id,Revision(1),token))
            assertEquals(UnavailableReason.NOT_FOUND,(r.read(id,token) as Outcome.Unavailable).reason)
        }
    }
    @Test fun staleAndMaxRevisionNeverOverwriteExistingPayload()=runBlocking {
        HostLifeDatabase(folder.newFile()).use { db ->
            val r=repo(db);val p=ok(r.create(draft(),token))
            fails(r.update(p.id,draft("Wrong"),Revision(8),token),FailureCode.STALE_REVISION)
            fails(r.delete(p.id,Revision(8),token),FailureCode.STALE_REVISION)
            assertEquals(p,ok(r.read(p.id,token)))
            db.sql("UPDATE life_profile SET revision=9223372036854775807")
            fails(r.update(p.id,draft("Overflow"),Revision(Long.MAX_VALUE),token),FailureCode.INVALID_INPUT)
            assertEquals(p.value,ok(r.read(p.id,token)).value)
            assertEquals(UnavailableReason.NOT_FOUND,(r.delete(EntityId("missing"),Revision(0),token) as Outcome.Unavailable).reason)
        }
    }
    @Test fun keysetPagesHaveNoDuplicatesAndRejectMalformedCursor()=runBlocking {
        HostLifeDatabase(folder.newFile()).use { db ->
            val r=repo(db);repeat(5){ok(r.create(draft("Name$it"),token))}
            val seen=mutableListOf<EntityId>();var cursor:String?=null
            do {val page=ok(r.list(PageRequest(2,cursor),token));seen+=page.items.map{it.id};cursor=page.nextCursor}while(cursor!=null)
            assertEquals(5,seen.size);assertEquals(5,seen.toSet().size)
            for(c in listOf("bad","lp1:***","lp1:wyg","lp1:")) fails(r.list(PageRequest(2,c),token),FailureCode.INVALID_INPUT)
        }
    }
    @Test fun corruptBlobProjectionRevisionAndTimestampAreRejectedBeforeMutation()=runBlocking {
        for(mutation in listOf("payload=X'00'","name='Different'","name_folded='WRONG'","revision=-1","updated_nano=1000000000","payload=zeroblob(4194305)")) {
            HostLifeDatabase(folder.newFile()).use { db ->
                val r=repo(db);val p=ok(r.create(draft(),token));db.sql("UPDATE life_profile SET $mutation")
                fails(r.read(p.id,token),FailureCode.CORRUPT_DATA)
                fails(r.list(PageRequest(10),token),FailureCode.CORRUPT_DATA)
                fails(r.delete(p.id,Revision(0),token),FailureCode.CORRUPT_DATA)
                fails(r.update(p.id,draft("Changed"),Revision(0),token),FailureCode.CORRUPT_DATA)
                assertEquals(1L,db.scalar("SELECT count(*) FROM life_profile"))
            }
        }
    }
    @Test fun generatedIdsAreStrictAndCollisionsDoNotReplace()=runBlocking {
        HostLifeDatabase(folder.newFile()).use { db ->
            for(id in listOf(" ","x".repeat(257),"\ud800")) {
                fails(repo(db,object:IdSource{override fun nextId()=id}).create(draft(),token),FailureCode.INTERNAL)
            }
            val r=repo(db,object:IdSource{override fun nextId()="same"})
            val p=ok(r.create(draft(),token));fails(r.create(draft("Overwrite"),token),FailureCode.PERSISTENCE)
            assertEquals(p,ok(r.read(p.id,token)))
        }
    }
    @Test fun cancellationAfterWriteRollsBackAndEscapes()=runBlocking {
        HostLifeDatabase(folder.newFile()).use { db ->
            val r=repo(db);val p=ok(r.create(draft(),token));db.afterWrite={token.stopped=true}
            try { r.update(p.id,draft("Never committed"),Revision(0),token);fail("cancellation swallowed") }catch(expected:CancellationException){}
            token.stopped=false;db.afterWrite=null;assertEquals(p,ok(r.read(p.id,token)))
        }
    }
    @Test fun byteBudgetReturnsContinuationAtLastReturnedIdWithoutFetchingLookaheadBlobs()=runBlocking {
        HostLifeDatabase(folder.newFile()).use { db ->
            val r=repo(db)
            val base=draft()
            val large=base.copy(counterDefinitions=SnapshotList.copyOf(base.counterDefinitions + (1..60).map {
                CounterDefinition(EntityId("extra$it"),"x".repeat(50_000),null,null,0,CounterLossPolicy.None,true)
            }))
            val expected=(1..3).map{ok(r.create(large,token)).id}
            db.blobQueries=0
            val first=ok(r.list(PageRequest(100),token))
            assertEquals(expected.take(2),first.items.map{it.id});assertNotNull(first.nextCursor)
            assertEquals(2,db.blobQueries)
            val last=ok(r.list(PageRequest(100,first.nextCursor),token))
            assertEquals(expected.drop(2),last.items.map{it.id});assertNull(last.nextCursor)
        }
    }
    @Test fun profileDeleteLeavesFrozenGameAndSearchHistoryUntouched()=runBlocking {
        HostLifeDatabase(folder.newFile()).use { db ->
            val r=repo(db);val p=ok(r.create(draft(),token))
            db.sql("""INSERT INTO life_game VALUES ('synthetic','game',0,'operation',X'01','hash',X'02',0,'PAUSED',10,17,NULL,NULL,10,17)""")
            db.sql("""INSERT INTO life_seat_search VALUES ('synthetic','game','seat','Frozen','frozen',NULL,NULL,NULL,NULL)""")
            ok(r.delete(p.id,p.revision,token))
            assertEquals(1L,db.scalar("SELECT count(*) FROM life_game"))
            assertEquals(1L,db.scalar("SELECT count(*) FROM life_seat_search"))
        }
    }

    @Test fun storedFoldUsesNfcAndRootLocale()=runBlocking {
        val previous=java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"))
            HostLifeDatabase(folder.newFile()).use { db ->
                ok(repo(db).create(draft("I İ E\u0301"),token))
                db.connection.prepare("SELECT name,name_folded FROM life_profile").use {
                    assertTrue(it.step());assertEquals("I İ E\u0301",it.getText(0));assertEquals("i i\u0307 é",it.getText(1))
                }
            }
        }finally{java.util.Locale.setDefault(previous)}
    }
    @Test fun unicodeAndSqlPunctuationIdsUseCanonicalBoundKeysetCursors()=runBlocking {
        HostLifeDatabase(folder.newFile()).use { db ->
            val ids=listOf("profile-2","é'; --","Ω","profile-10").iterator()
            val r=repo(db,object:IdSource{override fun nextId()=ids.next()})
            repeat(4){ok(r.create(draft(),token))}
            val expected=listOf("profile-10","profile-2","é'; --","Ω")
            var cursor:String?=null;val actual=mutableListOf<String>()
            do {
                val page=ok(r.list(PageRequest(1,cursor),token));actual+=page.items.single().id.value
                if(cursor==null) {
                    assertEquals("lp1:cHJvZmlsZS0xMA",page.nextCursor)
                    fails(r.list(PageRequest(1,page.nextCursor+"=="),token),FailureCode.INVALID_INPUT)
                }
                cursor=page.nextCursor
            }while(cursor!=null)
            assertEquals(expected,actual)
            assertEquals("é'; --",ok(r.read(EntityId("é'; --"),token)).id.value)
        }
    }
    @Test fun corruptOversizedMetadataIsRejectedBeforeFetchingBlob()=runBlocking {
        HostLifeDatabase(folder.newFile()).use { db ->
            val r=repo(db);val p=ok(r.create(draft(),token))
            db.connection.prepare("UPDATE life_profile SET name=?,name_folded=?").use {
                it.bindText(1,"x".repeat(100_000));it.bindText(2,"x".repeat(100_000));it.step()
            }
            db.blobQueries=0
            fails(r.read(p.id,token),FailureCode.CORRUPT_DATA)
            assertEquals(0,db.blobQueries)
            db.sql("UPDATE life_profile SET name='Restored',name_folded='restored',profile_id='${"x".repeat(257)}'")
            fails(r.list(PageRequest(1),token),FailureCode.CORRUPT_DATA)
        }
    }
    @Test fun invalidInputAndUnexpectedCasCountRollbackWithoutPartialWrite()=runBlocking {
        HostLifeDatabase(folder.newFile()).use { db ->
            val r=repo(db);fails(r.create(draft("\ud800"),token),FailureCode.INVALID_INPUT)
            assertEquals(0L,db.scalar("SELECT count(*) FROM life_profile"))
            val p=ok(r.create(draft(),token))
            val wrongCount=object:Database by db {
                override suspend fun <T> transaction(action:suspend(DbTransaction)->T,cancel:CancelToken):Outcome<T> = db.transaction({ tx ->
                    action(object:DbTransaction by tx {
                        override suspend fun execute(request:SqlRequest,cancel:CancelToken):Outcome<DbWriteResult> {
                            val result=tx.execute(request,cancel)
                            return if(result is Outcome.Success) Outcome.Success(DbWriteResult(0)) else result
                        }
                    })
                },cancel)
            }
            fails(repo(wrongCount).update(p.id,draft("Rolled back"),p.revision,token),FailureCode.PERSISTENCE)
            assertEquals(p,ok(r.read(p.id,token)))
        }
    }

    @Test fun invalidUtf8StoredProjectionCannotMasqueradeAsReplacementCharacter()=runBlocking {
        HostLifeDatabase(folder.newFile()).use { db ->
            val r=repo(db);val p=ok(r.create(draft("\ufffd"),token))
            db.sql("UPDATE life_profile SET name=CAST(X'FF' AS TEXT),name_folded=CAST(X'FF' AS TEXT)")
            fails(r.read(p.id,token),FailureCode.CORRUPT_DATA)
            fails(r.list(PageRequest(1),token),FailureCode.CORRUPT_DATA)
        }
    }

}
