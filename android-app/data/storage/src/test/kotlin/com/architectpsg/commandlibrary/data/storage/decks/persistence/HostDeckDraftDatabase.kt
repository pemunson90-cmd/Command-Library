package com.architectpsg.commandlibrary.data.storage.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.decks.codec.*
import com.architectpsg.commandlibrary.feature.decks.LocalDeckDraftPlanner
import com.architectpsg.commandlibrary.feature.decks.persistence.*
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import kotlin.time.Duration

/** Real schema15 SQLite composition; all observations are actual SQL, no substituted row outcomes. */
internal class HostDeckDraftDatabase(file:File=databaseFile(),initialize:Boolean=true):Database,Closeable {
    val database=HostDeckVersionDatabase(file,initialize)
    val host get()=database.host
    val queries get()=database.queries
    var loseAcknowledgement:Boolean get()=database.loseAcknowledgement; set(value) { database.loseAcknowledgement=value }
    override suspend fun <T> transaction(action:suspend(DbTransaction)->T,cancel:CancelToken)=database.transaction(action,cancel)
    override suspend fun query(request:SqlRequest,cancel:CancelToken)=database.query(request,cancel)
    override suspend fun capabilities(cancel:CancelToken)=database.capabilities(cancel)
    fun sql(sql:String)=database.sql(sql)
    fun state()=database.state()
    fun count(table:String)=database.count(table)
    fun draftBlobs()=queries.count { it.statement.startsWith("SELECT raw FROM deck_draft_") || it.statement.startsWith("SELECT patch FROM deck_draft_") }
    fun plans():List<String> = queries.distinctBy { it.statement }.filter {
        it.statement.contains("i.status='PREPARED'") || it.statement.contains("ORDER BY i.source_generation DESC") || it.statement.contains("o.kind=:kind") || it.statement.contains("i.receipt_sequence=:sequence")
    }.map { request -> host.connection.prepare("EXPLAIN QUERY PLAN ${request.statement}").use { statement ->
        com.architectpsg.commandlibrary.data.storage.NamedParameters.bindOrder(request.statement,request.parameters).forEachIndexed { index,value -> when(value) {
            is SqlValue.Integer -> statement.bindLong(index+1,value.value)
            is SqlValue.Text -> statement.bindText(index+1,value.value)
            else -> error("Unexpected plan parameter")
        } }
        request.statement.substringAfter(" FROM ").substringBefore(" WHERE ")+": "+buildList { while(statement.step()) add(statement.getText(3)) }.joinToString("\n")
    } }
    override fun close()=database.close()
    companion object {
        fun databaseFile():File { val folder=File("/tmp/cl-deck-draft-store-01/tmp"); folder.mkdirs(); return File.createTempFile("draft-",".sqlite",folder).also { it.deleteOnExit() } }
    }
}
internal class DraftFixture(val db:HostDeckDraftDatabase):Closeable {
    val codec=DeckPayloadCodec(); val patchCodec=LocalDeckPatchCodec(); val rawCodec=DeckDraftPayloadCodec(); val planner=LocalDeckDraftPlanner()
    val cancel=object:CancelToken { override val isCancelled=false; override fun throwIfCancelled()=Unit }
    var clockCalls=0; var seconds=100L
    val clock=object:Clock { override fun utcNow():UtcTimestamp { clockCalls++; return UtcTimestamp(++seconds,123) }; override fun monotonicNow()=Duration.ZERO }
    val library=SqlLocalDeckLibrary(db,codec,clock,object:IdSource { var index=0; override fun nextId()="copy-${++index}" })
    val edits=SqlLocalDeckEdits(db,codec,patchCodec,clock)
    val versions=SqlLocalDeckVersions(db,codec,clock,object:IdSource { var index=0; override fun nextId()="version-${++index}" })
    fun service(clock:Clock=this.clock):LocalDeckDrafts=SqlLocalDeckDrafts(db,codec,patchCodec,rawCodec,planner,clock)
    fun entry(id:String="entry")=DeckEntry(EntityId(id),CardIdentity(Knowledge.Known(OracleId(ProviderId("foreign"),"oracle")),Knowledge.Unknown("printing",SnapshotList.of(Evidence("proof",null,null))),
        ProviderIdentity(ProviderId("missing"),"kind","raw"),Knowledge.Unknown("name",SnapshotList.empty()),LanguageTag("ja")),2,BoardRole.MAIN,Knowledge.Unknown("finish",SnapshotList.empty()),"notes",SnapshotList.empty(),SnapshotList.empty())
    fun deck()=DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE,"deck"),"Name",Knowledge.Absent("format"),"description",Knowledge.Absent("folder"),SnapshotList.of("a","a"),UtcTimestamp(1),UtcTimestamp(2),Revision(0),SnapshotList.of(entry()),Knowledge.Absent("budget"),EntityId("profile"),SnapshotList.empty(),SnapshotList.empty(),"primer",SnapshotList.empty())
    suspend fun create(value:DeckSnapshot=deck()):DeckSnapshot=ok(library.create(value,EntityId("create"),cancel))
    fun details(base:DeckSnapshot,name:String="Changed")=DeckDraftBody.Details(base.revision,name,base.description,base.primer)
    fun form(base:DeckSnapshot)=base.entries.first().let { DeckDraftBody.Entry(base.revision,it.id,it.quantity.toString(),it.notes,it.board,it.finish,it.labels) }
    suspend fun write(body:DeckDraftBody,expected:Long=0)=ok(service().write(deck().id,expected,body,cancel))
    suspend fun prepare(base:DeckSnapshot,state:DeckDraftState,operation:String="prepared",scope:DeckDraftCommitScope=DeckDraftCommitScope.TEXT_AUTOSAVE,added:EntityId?=null):DeckDraftPreparation {
        val saved=state.draft!!; val plan=ok(planner.propose(base,saved.body,scope,added,cancel))
        return ok(service().prepare(base.id,saved.token,scope,plan.patch,EntityId(operation),cancel))
    }
    suspend fun commit(preparation:DeckDraftPreparation)=ok(service().commit(preparation.intent.sourceToken.deckId,preparation.intent.operationId,preparation.intent.requestSha256,cancel))
    suspend fun rename(base:DeckSnapshot,name:String="Edited",operation:String="edit")=ok(edits.apply(base.id,DeckPatch(SnapshotList.of(DeckEdit.Rename(name)),"Edit"),base.revision,EntityId(operation),cancel))
    override fun close()=db.close()
    companion object {
        fun <T> ok(result:Outcome<T>):T { assertTrue(result.toString(),result is Outcome.Success); return (result as Outcome.Success).value }
        fun failure(result:Outcome<*>,code:FailureCode) { assertTrue(result.toString(),result is Outcome.Failure); assertEquals(code,(result as Outcome.Failure).error.code) }
        fun unavailable(result:Outcome<*>,reason:UnavailableReason) { assertTrue(result.toString(),result is Outcome.Unavailable); assertEquals(reason,(result as Outcome.Unavailable).reason) }
        fun content(expected:DeckSnapshot,actual:DeckSnapshot)=assertEquals(expected.copy(revision=actual.revision,modifiedAt=actual.modifiedAt),actual)
    }
}
