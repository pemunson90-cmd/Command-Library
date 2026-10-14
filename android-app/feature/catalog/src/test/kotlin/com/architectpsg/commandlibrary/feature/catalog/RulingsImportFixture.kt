package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

internal val RULING_TEST_ORACLE = OracleId(ProviderId("scryfall"), "00000000-0000-0000-0000-000000000001")
internal fun rulingTestRaw(comment: String = "  Synthetic\u0000 café 😀 \n", day: String = "2026-10-14", publisher: String = "wotc", oracle: OracleId = RULING_TEST_ORACLE) = JsonObject().apply {
    addProperty("object", "ruling"); addProperty("oracle_id", oracle.raw); addProperty("source", publisher)
    addProperty("published_at", day); addProperty("comment", comment)
}.toString()

internal fun rulingTestTables(db: BulkSqlTestDatabase) {
    if(db.rows("SELECT name FROM sqlite_master WHERE name='rulings_generation'").isNotEmpty()) return
    var location = File(requireNotNull(System.getProperty("user.dir")))
    val schema: File
    while(true) {
        val candidate = File(location, "android-app/data/storage/schemas/com.architectpsg.commandlibrary.data.storage.schema.StorageSchemaDatabase/7.json")
        if(candidate.isFile) { schema = candidate; break }
        location = requireNotNull(location.parentFile)
    }
    JsonParser.parseString(schema.readText()).asJsonObject.getAsJsonObject("database").getAsJsonArray("entities")
        .map { it.asJsonObject }.filter { it["tableName"].asString in setOf("rulings_generation", "ruling_record", "rulings_active") }.forEach { entity ->
            val name = entity["tableName"].asString
            db.exec(entity["createSql"].asString.replace("\${TABLE_NAME}", name))
            entity.getAsJsonArray("indices")?.forEach { db.exec(it.asJsonObject["createSql"].asString.replace("\${TABLE_NAME}", name)) }
        }
}

/** Synthetic pin and file bytes only, not a production authority or Android locator. */
internal class RulingsImportFixture(path: String = ":memory:") : AutoCloseable {
    val db = BulkSqlTestDatabase(path).also(::rulingTestTables)
    var pinHeld = false
    private var serial = 0
    val clock = object : Clock { override fun utcNow() = UtcTimestamp(123, 7); override fun monotonicNow() = Duration.ZERO }
    val ids = object : IdSource { override fun nextId() = "rulings-${++serial}" }
    fun artifact(bytes: ByteArray) = VerifiedArtifact(EntityId("job"), ArtifactFraming.JSONL, ArtifactCompression.GZIP,
        bytes.size.toLong(), ContentHash("SHA-256", MessageDigest.getInstance("SHA-256").digest(bytes).hex()), false,
        Knowledge.Unknown("No count supplied", SnapshotList.empty()), 0,
        AcquisitionProvenance(ProviderId("scryfall"), ResourceUri("https://data.scryfall.io/rulings/synthetic.jsonl.gz"), ResourceUri("https://data.scryfall.io/rulings/synthetic.jsonl.gz"),
            Knowledge.Unknown("Synthetic version evidence", SnapshotList.of(Evidence("Synthetic", ResourceUri("https://example.invalid/source"), UtcTimestamp(-7, 9)))),
            Knowledge.Known(UtcTimestamp(-10, 999_999_999)), UtcTimestamp(20, 1), UtcTimestamp(21, 2), Knowledge.Known("synthetic-etag"),
            Knowledge.Absent("Not supplied"), AcquisitionConsistency.FILE_REPRESENTATION, AcquisitionLanguageScope.RULINGS_LANGUAGE_NOT_ASSERTED,
            Knowledge.Unknown("License not asserted", SnapshotList.of(Evidence("Synthetic notice evidence", null, null)))))
    fun binding(artifact: VerifiedArtifact, input: Flow<ByteChunk>) = RulingsVerifiedInputBinding { _, _, consume ->
        check(!pinHeld); pinHeld = true
        try { consume(artifact, EntityId("consent"), input) } finally { pinHeld = false }
    }
    fun importer(artifact: VerifiedArtifact, input: Flow<ByteChunk>, database: Database = db, policy: RulingsImportPolicy = RulingsImportPolicy(), idSource: IdSource = ids) =
        LocalRulingsImport(database, clock, idSource, binding(artifact, input), policy)
    suspend fun install(raw: String = rulingTestRaw()): RulingsImportResult {
        val bytes = bulkGzip(raw.toByteArray())
        return importer(artifact(bytes), bulkChunks(bytes)).`import`(EntityId("job"), {}, BULK_NO_CANCEL).bulkValue()
    }
    fun active() = db.rows("SELECT generation_id FROM rulings_active").singleOrNull()?.bulkText("generation_id")
    override fun close() = db.close()
}

/** Fault injection around actual bundled SQLite transactions, never a SQL evaluator. */
internal class RulingsFaultDatabase(private val db: Database) : Database by db {
    var beforeExecute: ((SqlRequest) -> Outcome<DbWriteResult>?)? = null
    var afterActivation: (() -> Unit)? = null
    var afterStaging: (() -> Unit)? = null
    var beforeAcknowledgement: (() -> Unit)? = null
    var acknowledgementFailure: Outcome<SnapshotList<RowData>>? = null
    var acknowledgementGate: CompletableDeferred<Unit>? = null
    var deletes = 0
    override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> {
        if(request.statement.contains("AS active_generation")) {
            beforeAcknowledgement?.invoke()
            acknowledgementGate?.await()
            acknowledgementFailure?.let { return it }
        }
        return db.query(request, cancel)
    }
    override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> {
        var activation = false; var staging = false
        val result = db.transaction({ tx -> action(object : DbTransaction by tx {
            override suspend fun execute(request: SqlRequest, cancel: CancelToken): Outcome<DbWriteResult> {
                if(request.statement.startsWith("UPDATE rulings_generation SET status='ACTIVE'")) activation = true
                if(request.statement.startsWith("INSERT INTO rulings_generation")) staging = true
                if(request.statement.startsWith("DELETE")) deletes++
                return beforeExecute?.invoke(request) ?: tx.execute(request, cancel)
            }
        }) }, cancel)
        if(result is Outcome.Success) {
            if(activation) afterActivation?.also { afterActivation = null }?.invoke()
            if(staging) afterStaging?.also { afterStaging = null }?.invoke()
        }
        return result
    }
}
