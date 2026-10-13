package com.architectpsg.commandlibrary.feature.catalog

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.JsonParser
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration

/** Executes the importer against the real bundled SQLite engine, foreign keys and FTS5. */
class LocalBulkCatalogTest {
    @Test fun delayedRepeatedFaceMembershipAndBatchCanonicalEquality() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            val raw = listOf(bulkReversible("rev", "o", "o"), bulkRecord("a", "o"), bulkRecord("b", "o", "[\"B\",\"A\"]"))
            val result = db.importer(CatalogBulkImportLimits(maxBatchRecords=1)).`import`(bulkSelected(), bulkChunks(raw.joinToString("\n")), {}, BULK_NO_CANCEL).bulkValue()
            assertEquals(3L,result.installed.counts.printings); assertEquals(1L,result.installed.counts.oracleCards)
            assertEquals(4L,result.printingMembershipRows); assertEquals(0L,result.installed.counts.faces)
            assertEquals("0,1", db.rows("SELECT group_concat(source_slot) AS v FROM printing_oracle_membership WHERE printing_id='rev'").single().bulkText("v"))
            assertEquals(raw[0],db.rows("SELECT metadata_payload FROM printing WHERE printing_id='rev'").single().bulkText("metadata_payload"))
        }
    }
    @Test fun duplicateAndLateCanonicalConflictNeverReplaceActive() = runBlocking {
        for (batchSize in listOf(1,1000)) for (records in listOf(listOf(bulkRecord("b","o"),bulkRecord("b","o")), listOf(bulkRecord("b","o"),bulkRecord("c","o","[\"A\"]")))) {
            BulkSqlTestDatabase().use { db ->
                val installed = db.install(listOf(bulkRecord("old","old-o")))
                val result = db.importer(CatalogBulkImportLimits(maxBatchRecords=batchSize)).`import`(bulkSelected(),bulkChunks(records.joinToString("\n")),{},BULK_NO_CANCEL)
                assertTrue(result is Outcome.Failure); assertEquals(installed.installed.generation.id.value, db.active())
                assertEquals(1L,db.count("catalog_generation"))
            }
        }
    }
    @Test fun invalidRawDecodedAndRelationshipInputsDoNotActivate() = runBlocking {
        for ((source, content) in listOf(
            bulkSelected().copy(expectedBytes=Knowledge.Known(1L)) to bulkRecord("a","o"),
            bulkSelected().copy(expectedChecksum=Knowledge.Known(ContentHash("SHA-256","a".repeat(64)))) to bulkRecord("a","o"),
            bulkSelected() to bulkReversible("a","missing","missing"),
            bulkSelected().copy(framing=ArtifactFraming.JSON_ARRAY) to "[${bulkRecord("a","o")}",
        )) BulkSqlTestDatabase().use { db ->
            val old=db.install(listOf(bulkRecord("old","old-o")))
            assertTrue(db.importer().`import`(source,bulkChunks(content),{},BULK_NO_CANCEL) is Outcome.Failure)
            assertEquals(old.installed.generation.id.value,db.active())
        }
    }
    @Test fun configuredRecordChunkCountAndBatchLimitsAreEnforcedSeparatelyFromTiny() = runBlocking {
        for (limits in listOf(CatalogBulkImportLimits(maxRecordBytes=10),CatalogBulkImportLimits(maxChunkBytes=10),CatalogBulkImportLimits(maxPrintingRecords=1),CatalogBulkImportLimits(maxDecodedBytes=10),CatalogBulkImportLimits(maxRawBytes=10))) BulkSqlTestDatabase().use { db ->
            assertTrue(db.importer(limits).`import`(bulkSelected(),bulkChunks(listOf(bulkRecord("a","o"),bulkRecord("b","o")).joinToString("\n")),{},BULK_NO_CANCEL) is Outcome.Failure)
            assertNull(db.active())
        }
        BulkSqlTestDatabase().use { db ->
            assertTrue(db.importer().`import`(bulkSelected(),bulkChunks("[${bulkRecord("a","o")},${bulkRecord("b","o")}]"),{},BULK_NO_CANCEL) is Outcome.Failure)
            val result=db.importer(CatalogBulkImportLimits(maxOrdinaryBatchBytes=20,maxBatchRecords=1)).`import`(bulkSelected().copy(framing=ArtifactFraming.JSON_ARRAY),bulkChunks("[${bulkRecord("a","o")},${bulkRecord("b","o")}]"),{},BULK_NO_CANCEL).bulkValue()
            assertEquals(2L,result.installed.counts.printings)
        }
        assertEquals(8L*1024*1024,CatalogImportLimits().maxInputBytes)
    }
    @Test fun cancellationRollsBackButLateCommitAndPinReleaseCancellationReportInstalledSuccess() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            val old=db.install(listOf(bulkRecord("old","old-o")))
            try { db.importer().`import`(bulkSelected(),bulkChunks(bulkRecord("a","o")),{ throw CancellationException("stop") },BULK_NO_CANCEL); fail() } catch (_: CancellationException) {}
            assertEquals(old.installed.generation.id.value,db.active())
            val token=BulkMutableCancel()
            db.afterCommit = { if (db.active()!=old.installed.generation.id.value) token.cancelled=true }
            val result=db.importer().`import`(bulkSelected(),bulkChunks(bulkRecord("a","o")),{},token).bulkValue()
            assertEquals(result.installed.generation.id.value,db.active())
        }
        BulkSqlTestDatabase().use { db ->
            val raw=bulkRecord("a","o"); val source=bulkAcquired(raw)
            val adapter=CatalogVerifiedInputBinding { supplied,_,consume -> assertEquals(source,supplied); consume(bulkChunks(raw),EntityId("consent")); throw CancellationException("pin release") }
            val result=db.importer(binding=adapter).`import`(source,flow { error("untrusted flow consumed") },{},BULK_NO_CANCEL).bulkValue()
            assertEquals(result.installed.generation.id.value,db.active()); assertEquals(Knowledge.Known(source.verifiedArtifact),result.acquiredArtifact)
        }
    }
    @Test fun stagedSearchCoverageAndExternalPointerCasAreValidated() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            val old=db.install(listOf(bulkRecord("old","old-o")))
            val result=db.importer().`import`(bulkSelected(),bulkChunks(listOf(bulkRecord("a","o1"),bulkRecord("b","o2")).joinToString("\n")),{
                db.exec("DELETE FROM catalog_name_fts WHERE oracle_id='o2'")
                db.exec("INSERT INTO catalog_name_fts SELECT * FROM catalog_name_fts WHERE oracle_id='o1'")
            },BULK_NO_CANCEL)
            assertEquals(FailureCode.CORRUPT_DATA,(result as Outcome.Failure).error.code)
            assertEquals(old.installed.generation.id.value,db.active())
        }
        BulkSqlTestDatabase().use { db ->
            val first=db.install(listOf(bulkRecord("first","first-o")))
            db.install(listOf(bulkRecord("current","current-o")))
            val result=db.importer().`import`(bulkSelected(),bulkChunks(bulkRecord("a","o")),{
                db.exec("UPDATE catalog_generation SET status='RETIRED' WHERE status='ACTIVE'")
                db.exec("UPDATE catalog_generation SET status='ACTIVE' WHERE generation_id='${first.installed.generation.id.value}'")
                db.exec("UPDATE catalog_active SET generation_id='${first.installed.generation.id.value}'")
            },BULK_NO_CANCEL)
            assertEquals(FailureCode.STALE_GENERATION,(result as Outcome.Failure).error.code)
            assertEquals(first.installed.generation.id.value,db.active())
        }
    }

    @Test fun gzipActivationKeepsRawAndDecodedProofAndRejectsCrcBeforePointerSwitch() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            val bytes=bulkRecord("a","o").toByteArray(); val gzip=bulkGzip(bytes)
            val source=bulkSelected().copy(compression=ArtifactCompression.GZIP,expectedBytes=Knowledge.Known(gzip.size.toLong()))
            val installed=db.importer().`import`(source,bulkChunks(gzip,7),{},BULK_NO_CANCEL).bulkValue()
            assertEquals(gzip.size.toLong(),installed.installed.verification.observedBytes)
            assertEquals(bytes.size.toLong(),installed.decodedBytes); assertNotEquals(installed.installed.verification.computedChecksum,installed.decodedSha256)
            for (bad in listOf(gzip.copyOf(gzip.size-1),gzip.copyOf().apply { this[size-8]=(this[size-8].toInt() xor 1).toByte() })) {
                assertTrue(db.importer().`import`(source,bulkChunks(bad),{},BULK_NO_CANCEL) is Outcome.Failure)
                assertEquals(installed.installed.generation.id.value,db.active())
            }
        }
    }
    @Test fun oversizeSingletonRetainsUnknownFieldsAndDoesNotShareOrdinaryBatch() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            val raw=bulkRecord("large","o").dropLast(1)+",\"unknown_source_field\":\""+"x".repeat(2*1024*1024)+"\"}"
            val events=ArrayList<Long>()
            val result=db.importer().`import`(bulkSelected(),bulkChunks(listOf(bulkRecord("a","o"),raw,bulkRecord("z","o")).joinToString("\n")),{ events.add(it.completedUnits) },BULK_NO_CANCEL).bulkValue()
            assertEquals(listOf(1L,2L,3L),events); assertEquals(3L,result.installed.counts.printings)
            assertEquals(raw,db.rows("SELECT metadata_payload FROM printing WHERE printing_id='large'").single().bulkText("metadata_payload"))
        }
    }

    @Test fun authoritativeArtifactReadFailureRemainsTypedAndSanitized() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            val raw=bulkRecord("a","o"); val source=bulkAcquired(raw)
            val binder=CatalogVerifiedInputBinding { _,_,consume -> consume(flow { throw ArtifactReadException(bulkError(FailureCode.SOURCE_MISMATCH,"private-secret")) },EntityId("consent")) }
            val result=db.importer(binding=binder).`import`(source,bulkChunks(raw),{},BULK_NO_CANCEL) as Outcome.Failure
            assertEquals(FailureCode.SOURCE_MISMATCH,result.error.code); assertFalse(result.error.message.contains("private-secret")); assertNull(db.active())
        }
    }

    @Test fun pointerChangeAndZeroCasRollbackPreserveOriginal() = runBlocking {
        for (zero in listOf(false,true)) BulkSqlTestDatabase().use { db ->
            val old=db.install(listOf(bulkRecord("old","old-o")))
            if (zero) db.zeroPointerWrite=true else db.beforeActivation={ db.exec("UPDATE catalog_generation SET status='FAILED' WHERE status='STAGING'") }
            assertTrue(db.importer().`import`(bulkSelected(),bulkChunks(bulkRecord("a","o")),{},BULK_NO_CANCEL) is Outcome.Failure)
            assertEquals(old.installed.generation.id.value,db.active())
            assertTrue(LocalCatalogIdentities(db).printing(CardId(ProviderId("scryfall"),"old"),BULK_NO_CANCEL) is Outcome.Success)
        }
    }
}

internal class BulkMutableCancel : CancelToken { var cancelled=false; override val isCancelled get()=cancelled; override fun throwIfCancelled() { if(cancelled) throw CancellationException("cancel") } }
internal fun bulkSelected() = CatalogBulkImportSource(ResourceUri("content://selected/cards"),"Synthetic",ProviderId("scryfall"),LanguageTag.ENGLISH,Knowledge.Unknown("Date not supplied",SnapshotList.empty()),Knowledge.Absent("Not supplied"),Knowledge.Absent("Not supplied"),Knowledge.Absent("Not supplied"),Knowledge.Absent("Not supplied"),ArtifactFraming.JSONL,ArtifactCompression.NONE)
internal fun bulkRecord(id: String, oracle: String, keywords: String="[\"A\",\"B\"]") = """{"object":"card","id":"$id","oracle_id":"$oracle","lang":"en","name":"Synthetic","set":"tst","collector_number":"007a","keywords":$keywords,"layout":"normal"}"""
internal fun bulkReversible(id: String, first: String, second: String) = """{"object":"card","id":"$id","lang":"en","name":"Synthetic reversible","set":"tst","collector_number":"7","layout":"reversible_card","card_faces":[{"object":"card_face","oracle_id":"$first","name":"Side one"},{"object":"card_face","oracle_id":"$second","name":"Side two"}]}"""
internal fun bulkChunks(text: String, chunkSize: Int=64*1024) = bulkChunks(text.toByteArray(Charsets.UTF_8),chunkSize)
internal fun bulkChunks(bytes: ByteArray, chunkSize: Int=64*1024): Flow<ByteChunk> = flow {
    var offset=0; var seq=0L
    while(offset<bytes.size) { val end=minOf(offset+chunkSize,bytes.size); emit(ByteChunk(seq++,offset.toLong(),SnapshotBytes(bytes.copyOfRange(offset,end)),null,end==bytes.size)); offset=end }
    if(bytes.isEmpty()) emit(ByteChunk(0,0,SnapshotBytes(bytes),null,true))
}
internal fun bulkAcquired(raw: String): CatalogBulkImportSource {
    val source=bulkSelected(); val hash=ContentHash("SHA-256",MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).hex())
    val artifact=VerifiedArtifact(EntityId("job"),ArtifactFraming.JSONL,ArtifactCompression.NONE,raw.toByteArray().size.toLong(),hash,false,Knowledge.Known(1L),1,
        AcquisitionProvenance(source.provider,ResourceUri("https://api.scryfall.com/cards/search"),ResourceUri("https://api.scryfall.com/cards/search"),Knowledge.Unknown("Version not supplied",SnapshotList.empty()),source.sourceDate,UtcTimestamp(1),UtcTimestamp(2),Knowledge.Absent("Not supplied"),Knowledge.Absent("Not supplied"),AcquisitionConsistency.NON_ATOMIC_TRAVERSAL,AcquisitionLanguageScope.ENGLISH_CARDS,source.notice))
    return source.copy(inputKind=SourceInputKind.VERIFIED_ARTIFACT,verifiedArtifact=artifact,expectedBytes=Knowledge.Known(artifact.bytes),expectedChecksum=Knowledge.Known(hash),sourceUri=Knowledge.Known(artifact.provenance.requestedUri))
}
internal class BulkSqlTestDatabase(path: String=":memory:") : Database, AutoCloseable {
    private val db: SQLiteConnection=BundledSQLiteDriver().open(path)
    var afterCommit: (() -> Unit)?=null
    var beforeActivation: (() -> Unit)?=null
    var zeroPointerWrite=false
    var failCleanup=false
    private var serial=0
    init {
        exec("PRAGMA foreign_keys=ON")
        if (rows("SELECT name FROM sqlite_master WHERE name='catalog_generation'").isEmpty()) {
        var location=File(System.getProperty("user.dir")); var schema: File?=null
        while(schema==null) { val candidate=File(location,"android-app/data/storage/schemas/com.architectpsg.commandlibrary.data.storage.schema.StorageSchemaDatabase/3.json"); if(candidate.isFile) schema=candidate else location=location.parentFile ?: error("Schema fixture not found") }
        val root=JsonParser.parseString(schema.readText()).asJsonObject.getAsJsonObject("database")
        for(e in root.getAsJsonArray("entities")) { val v=e.asJsonObject; val name=v["tableName"].asString; exec(v["createSql"].asString.replace("\${TABLE_NAME}",name)); for(i in v.getAsJsonArray("indices") ?: emptyList()) exec(i.asJsonObject["createSql"].asString.replace("\${TABLE_NAME}",name)) }
        exec("ALTER TABLE catalog_generation ADD COLUMN input_kind TEXT NOT NULL DEFAULT 'USER_CONTENT_URI'")
        exec("ALTER TABLE catalog_generation ADD COLUMN artifact_job_id TEXT")
        exec("ALTER TABLE catalog_generation ADD COLUMN source_payload TEXT")
        exec("CREATE TABLE printing_oracle_membership(generation_id TEXT NOT NULL,printing_provider TEXT NOT NULL,printing_id TEXT NOT NULL,source_slot INTEGER NOT NULL,source_kind TEXT NOT NULL,oracle_provider TEXT NOT NULL,oracle_id TEXT NOT NULL,oracle_language TEXT NOT NULL,PRIMARY KEY(generation_id,printing_provider,printing_id,source_slot),FOREIGN KEY(generation_id,printing_provider,printing_id) REFERENCES printing(generation_id,provider,printing_id) ON DELETE CASCADE,FOREIGN KEY(generation_id,oracle_provider,oracle_id,oracle_language) REFERENCES oracle_card(generation_id,provider,oracle_id,language) ON DELETE NO ACTION)")
        exec("CREATE INDEX index_printing_oracle_membership_lookup ON printing_oracle_membership(generation_id,oracle_provider,oracle_id,oracle_language,printing_provider,printing_id)")
    }
        }
    fun importer(limits: CatalogBulkImportLimits=CatalogBulkImportLimits(),binding: CatalogVerifiedInputBinding=CatalogVerifiedInputBinding { _,_,_ -> error("Verified input rejected by test fixture") }) = LocalBulkCatalog(this,object : Clock { override fun utcNow()=UtcTimestamp(3); override fun monotonicNow()=Duration.ZERO },object : IdSource { override fun nextId()="synthetic-${++serial}" },binding,limits)
    suspend fun install(records: List<String>)=importer().`import`(bulkSelected(),bulkChunks(records.joinToString("\n")),{},BULK_NO_CANCEL).bulkValue()
    fun exec(sql: String) { db.prepare(sql).use { it.step() } }
    fun active(): String? = rows("SELECT generation_id FROM catalog_active").firstOrNull()?.bulkText("generation_id")
    fun count(table: String)=rows("SELECT count(*) AS n FROM $table").single().bulkLong("n")
    fun rows(sql: String)=queryRaw(bulkSql(sql,101))
    private fun queryRaw(r: SqlRequest): SnapshotList<RowData> = db.prepare(r.statement).use { s ->
        bind(s,r)
        SnapshotList.copyOf(buildList { while(s.step()) { require(size<r.maxRows); add(RowData(SnapshotList.copyOf((0 until s.getColumnCount()).map { i -> RowColumn(s.getColumnName(i),when(s.getColumnType(i)) { 1 -> SqlValue.Integer(s.getLong(i)); 3 -> SqlValue.Text(s.getText(i)); 5 -> SqlValue.Null; else -> error("Unexpected SQLite value") }) }))) } })
    }
    private fun bind(s: androidx.sqlite.SQLiteStatement,r: SqlRequest) {
        val names=Regex(":([A-Za-z][A-Za-z0-9_]*)").findAll(r.statement).map { it.groupValues[1] }.distinct().toList()
        require(names.toSet()==r.parameters.map { it.name }.toSet())
        for((i,name) in names.withIndex()) when(val v=r.parameters.single { it.name==name }.value) { SqlValue.Null -> s.bindNull(i+1); is SqlValue.Text -> s.bindText(i+1,v.value); is SqlValue.Integer -> s.bindLong(i+1,v.value); else -> error("Unused type") }
    }
    override suspend fun query(request: SqlRequest,cancel: CancelToken): Outcome<SnapshotList<RowData>> { cancel.throwIfCancelled(); return try { Outcome.Success(queryRaw(request)) } catch (_: Exception) { Outcome.Failure(bulkError(FailureCode.PERSISTENCE,"SQL query failed")) } }
    override suspend fun <T> transaction(action: suspend (DbTransaction)->T,cancel: CancelToken): Outcome<T> {
        cancel.throwIfCancelled(); exec("BEGIN IMMEDIATE")
        val tx=object : DbTransaction {
            override var state: DbTransactionState=DbTransactionState.Active
            override fun abort(error: DomainFailure): Nothing { state=DbTransactionState.RollbackOnly(Outcome.Failure(error)); throw TransactionAbort(error) }
            override suspend fun query(request: SqlRequest,cancel: CancelToken): Outcome<SnapshotList<RowData>> { val r=this@BulkSqlTestDatabase.query(request,cancel); if(r !is Outcome.Success) state=DbTransactionState.RollbackOnly(when(r) { is Outcome.Failure -> r; is Outcome.Unavailable -> r; is Outcome.Unknown -> r; else -> error("Expected non-success") }); return r }
            override suspend fun execute(request: SqlRequest,cancel: CancelToken): Outcome<DbWriteResult> {
                cancel.throwIfCancelled()
                return try {
                    if(failCleanup && request.statement.startsWith("DELETE")) error("Injected persistence failure")
                    if(request.statement.startsWith("UPDATE catalog_generation SET status='ACTIVE'")) beforeActivation?.invoke()
                    if(zeroPointerWrite && request.statement.startsWith("UPDATE catalog_active")) Outcome.Success(DbWriteResult(0))
                    else { db.prepare(request.statement).use { s -> bind(s,request); s.step() }; Outcome.Success(DbWriteResult(queryRaw(bulkSql("SELECT changes() AS n")).single().bulkLong("n"))) }
                } catch (_: Exception) { val e=Outcome.Failure(bulkError(FailureCode.PERSISTENCE,"SQL write failed")); state=DbTransactionState.RollbackOnly(e); e }
            }
        }
        try { val value=action(tx); cancel.throwIfCancelled(); if(tx.state is DbTransactionState.RollbackOnly) { exec("ROLLBACK"); return (tx.state as DbTransactionState.RollbackOnly).status }; exec("COMMIT"); afterCommit?.invoke(); return Outcome.Success(value) }
        catch(e: CancellationException) { exec("ROLLBACK"); throw e }
        catch(e: TransactionAbort) { exec("ROLLBACK"); return Outcome.Failure(e.failure) }
        catch(e: BulkFailure) { exec("ROLLBACK"); return e.outcome }
        catch(_: Exception) { exec("ROLLBACK"); return Outcome.Failure(bulkError(FailureCode.PERSISTENCE,"SQL transaction failed")) }
    }
    override suspend fun capabilities(cancel: CancelToken)=Outcome.Success(DbCapabilities("bundled SQLite",4,true,true,true,999))
    override fun close() { db.close() }
}
