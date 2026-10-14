package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Real bundled SQLite with actual exported7 ruling tables; fixtures have no source authority. */
class LocalCatalogRulingsTest {
    @get:Rule val folder = TemporaryFolder()
    private val oracle = OracleId(ProviderId("scryfall"), "00000000-0000-0000-0000-000000000001")
    private val other = oracle.copy(raw = "00000000-0000-0000-0000-000000000002")
    private val cancel = BULK_NO_CANCEL
    private fun tables(db: BulkSqlTestDatabase) {
        var location = File(System.getProperty("user.dir"))
        val schema: File
        while(true) {
            val candidate = File(location, "android-app/data/storage/schemas/com.architectpsg.commandlibrary.data.storage.schema.StorageSchemaDatabase/7.json")
            if(candidate.isFile) { schema = candidate; break }
            location = requireNotNull(location.parentFile) { "Actual exported schema7 missing" }
        }
        val entities = JsonParser.parseString(schema.readText()).asJsonObject.getAsJsonObject("database").getAsJsonArray("entities")
        entities.map { it.asJsonObject }.filter { it["tableName"].asString in setOf("rulings_generation", "ruling_record", "rulings_active") }.forEach { e ->
            val name = e["tableName"].asString
            db.exec(e["createSql"].asString.replace("\${TABLE_NAME}", name))
            e.getAsJsonArray("indices")?.forEach { db.exec(it.asJsonObject["createSql"].asString.replace("\${TABLE_NAME}", name)) }
        }
    }
    private fun provenance(id: String = "g") = Provenance(ProviderId("scryfall"), ResourceUri("https://example.invalid/synthetic-rulings"),
        Knowledge.Unknown("Version unsupplied", SnapshotList.of(Evidence("Synthetic evidence", ResourceUri("https://example.invalid/evidence"), UtcTimestamp(-1, 7)))),
        Knowledge.Known(UtcTimestamp(1, 999_999_999)), UtcTimestamp(2, 3), Knowledge.Absent("No effective instant"), GenerationId(id),
        ContentHash("SHA-256", "a".repeat(64)), SourceNotice(ProviderId("wotc"), "Synthetic notice", Knowledge.Unknown("License not asserted", SnapshotList.empty()), "Attribution é", null))
    private fun raw(day: String = "2026-10-14", publisher: String = "wotc", comment: String = "  literal\u0000 café 😀 \n", id: OracleId = oracle) = JsonObject().apply {
        addProperty("object", "ruling"); addProperty("oracle_id", id.raw); addProperty("source", publisher)
        addProperty("published_at", day); addProperty("comment", comment)
    }.toString()
    private suspend fun generation(db: BulkSqlTestDatabase, id: String = "g", records: List<String> = emptyList(), activate: Boolean = true) {
        db.transaction({ tx ->
            tx.bulkWrite("INSERT INTO rulings_generation VALUES (:g,'scryfall',:status,'RULINGS_LANGUAGE_NOT_ASSERTED',:payload,:count,1)", cancel,
                "g" to id, "status" to if(activate) "ACTIVE" else "STAGING", "payload" to AcquisitionPayloadCodec.encodeStoredProvenance(provenance(id)), "count" to records.size)
            records.forEachIndexed { index, record ->
                val decoded = ScryfallRulingRecordCodec.decode(record)
                tx.bulkWrite("INSERT INTO ruling_record VALUES (:g,:ordinal,'scryfall',:oracle,:publisher,:day,:payload)", cancel,
                    "g" to id, "ordinal" to index + 1, "oracle" to decoded.oracleId.raw, "publisher" to decoded.publisher.upstreamValue,
                    "day" to decoded.publishedAt.value, "payload" to record)
            }
            if(activate) {
                tx.bulkWrite("UPDATE rulings_generation SET status='RETIRED' WHERE generation_id<>:g AND status='ACTIVE'", cancel, "g" to id)
                tx.bulkWrite("INSERT OR REPLACE INTO rulings_active VALUES (1,:g)", cancel, "g" to id)
            }
        }, cancel).bulkValue()
    }
    private fun failure(value: Outcome<*>, code: FailureCode) {
        assertTrue("Expected $code, got $value", value is Outcome.Failure)
        assertEquals(code, (value as Outcome.Failure).error.code)
        assertFalse(value.error.message.contains("private-secret"))
    }

    @Test fun absenceEmptyInstalledAndStagingAreDistinctFromCards() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            tables(db); val reader = LocalCatalogRulings(db)
            assertNull(reader.status(cancel).bulkValue().active)
            assertEquals(UnavailableReason.NOT_INSTALLED, (reader.rulings(oracle, PageRequest(20), cancel) as Outcome.Unavailable).reason)
            generation(db, "staging", listOf(raw()), activate = false)
            assertNull(reader.status(cancel).bulkValue().active)
            generation(db)
            val status = reader.status(cancel).bulkValue().active!!
            assertEquals(0L, status.recordCount); assertEquals(provenance(), status.provenance)
            val page = reader.rulings(other, PageRequest(20), cancel).bulkValue()
            assertTrue(page.items.isEmpty()); assertNull(page.nextCursor)
            assertEquals(SnapshotReference(ProviderId("scryfall"), GenerationId("g"), null, null), page.snapshot)
            assertEquals(0L, db.count("catalog_active")); assertEquals(0L, db.count("printing"))
        }
    }

    @Test fun literalDuplicateRecordsKeepLocalIdentityAndDayOrderAcrossReopen() = runBlocking {
        val file = folder.newFile("rulings.db")
        val duplicate = raw("2026-10-14", "scryfall")
        val generationId = "g|é\u0000😀"
        var cursor: String? = null
        BulkSqlTestDatabase(file.path).use { db ->
            tables(db); generation(db, generationId, listOf(raw("2026-10-13"), duplicate, duplicate, raw("2026-10-15"), raw(id = other)))
            val first = LocalCatalogRulings(db).rulings(oracle, PageRequest(2), cancel).bulkValue()
            assertEquals(listOf(4L, 2L), first.items.map { it.id.ordinal }); cursor = first.nextCursor
            assertEquals("  literal\u0000 café 😀 \n", first.items[1].content.comment)
            assertEquals(RulingPublisher.SCRYFALL, first.items[1].content.publisher)
            assertEquals(provenance(generationId), first.items[1].provenance)
        }
        BulkSqlTestDatabase(file.path).use { db ->
            val second = LocalCatalogRulings(db).rulings(oracle, PageRequest(100, cursor), cancel).bulkValue()
            assertEquals(listOf(3L, 1L), second.items.map { it.id.ordinal }); assertNull(second.nextCursor)
            assertEquals(GenerationId(generationId), second.snapshot.generation)
        }
    }

    @Test fun cursorsBindGenerationOracleDayAndPositiveExactOrdinal() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            tables(db); generation(db, records = listOf(raw(), raw()))
            val reader = LocalCatalogRulings(db)
            val cursor = reader.rulings(oracle, PageRequest(1), cancel).bulkValue().nextCursor!!
            failure(reader.rulings(other, PageRequest(1, cursor), cancel), FailureCode.INVALID_INPUT)
            for(bad in listOf("private-secret", cursor.replace("r1|", "r2|"), cursor.replace("|67|", "|ff|"),
                cursor.substringBeforeLast('|') + "|0", cursor.substringBeforeLast('|') + "|+1", cursor.substringBeforeLast('|') + "|9223372036854775808",
                cursor.replace("2026-10-14", "2026-02-30"), cursor.replace("|scryfall|", "|other|")))
                failure(reader.rulings(oracle, PageRequest(1, bad), cancel), FailureCode.INVALID_INPUT)
            failure(reader.rulings(oracle.copy(source = ProviderId("other")), PageRequest(20), cancel), FailureCode.INVALID_INPUT)
            failure(reader.rulings(oracle.copy(raw = "bad"), PageRequest(20), cancel), FailureCode.INVALID_INPUT)
            generation(db, "new", listOf(raw()))
            failure(reader.rulings(oracle, PageRequest(1, cursor), cancel), FailureCode.STALE_GENERATION)
        }
    }

    @Test fun byteBoundReturnsRealContinuationWithoutSkippingAnyRecord() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            tables(db); generation(db, records = List(40) { raw(comment = "é".repeat(30_000)) })
            val reader = LocalCatalogRulings(db)
            val first = reader.rulings(oracle, PageRequest(100), cancel).bulkValue()
            assertTrue(first.items.size in 1..39); assertNotNull(first.nextCursor)
            val second = reader.rulings(oracle, PageRequest(100, first.nextCursor), cancel).bulkValue()
            assertNull(second.nextCursor)
            assertEquals((1L..40L).toList(), (first.items + second.items).map { it.id.ordinal })
        }
    }

    @Test fun persistedGenerationAndProjectionCorruptionNeverBecomesEmptySuccess() = runBlocking {
        val mutations = listOf("UPDATE rulings_generation SET status='STAGING'", "UPDATE rulings_generation SET provider='other'",
            "UPDATE rulings_generation SET language_scope='ENGLISH_CARDS'", "UPDATE rulings_generation SET record_count=-1", "UPDATE rulings_generation SET record_count=0",
            "UPDATE rulings_generation SET payload_schema_version=2", "UPDATE rulings_generation SET provenance_payload='private-secret'",
            "UPDATE rulings_generation SET provenance_payload='${"x".repeat(LocalCatalogRulings.MAX_METADATA_BYTES + 1)}'",
            "UPDATE ruling_record SET ordinal=0", "UPDATE ruling_record SET publisher='scryfall'", "UPDATE ruling_record SET published_day='2026-10-12'", "UPDATE ruling_record SET published_day='zzzzzzzzzz'",
            "UPDATE ruling_record SET record_payload='private-secret'", "UPDATE ruling_record SET record_payload='${"x".repeat(ScryfallRulingRecordCodec.MAX_RECORD_BYTES + 1)}'",
            "UPDATE rulings_active SET singleton_id=2")
        for(mutation in mutations) BulkSqlTestDatabase().use { db ->
            tables(db); generation(db, records = listOf(raw())); db.exec(mutation)
            failure(LocalCatalogRulings(db).rulings(oracle, PageRequest(20), cancel), FailureCode.CORRUPT_DATA)
        }
        BulkSqlTestDatabase().use { db ->
            tables(db); db.exec("PRAGMA foreign_keys=OFF"); db.exec("INSERT INTO rulings_active VALUES (1,'missing')")
            failure(LocalCatalogRulings(db).status(cancel), FailureCode.CORRUPT_DATA)
        }
    }

    @Test fun provenanceMismatchAndOversizedGenerationAreCorrupt() = runBlocking {
        for(id in listOf("other", "x".repeat(513))) BulkSqlTestDatabase().use { db ->
            tables(db); generation(db, records = listOf(raw()))
            if(id == "other") {
                val payload = AcquisitionPayloadCodec.encodeStoredProvenance(provenance(id)).replace("'", "''")
                db.exec("UPDATE rulings_generation SET provenance_payload='$payload'")
            } else {
                db.exec("PRAGMA foreign_keys=OFF"); db.exec("UPDATE rulings_active SET generation_id='$id'")
            }
            failure(LocalCatalogRulings(db).status(cancel), FailureCode.CORRUPT_DATA)
        }
    }

    @Test fun queriesStayInSingleTransactionAndSnapshotSurvivesPointerSwitch() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            tables(db); generation(db, records = listOf(raw(), raw()))
            var transactions = 0
            var pageQuery: SqlRequest? = null
            val guarded = object : Database by db {
                override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> = error("Read outside snapshot")
                override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> {
                    transactions++
                    return db.transaction({ tx -> action(object : DbTransaction by tx {
                        override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> {
                            if(request.statement.contains("FROM ruling_record r")) pageQuery = request
                            return tx.query(request, cancel)
                        }
                    }) }, cancel)
                }
            }
            val reader = LocalCatalogRulings(guarded)
            db.afterCommit = { db.afterCommit = null; db.exec("UPDATE rulings_generation SET status='RETIRED'"); db.exec("DELETE FROM rulings_active") }
            val page = reader.rulings(oracle, PageRequest(20), cancel).bulkValue()
            assertEquals(2, page.items.size); assertEquals(1, transactions)
            assertNull(reader.status(cancel).bulkValue().active); assertEquals(2, transactions)
            val query = requireNotNull(pageQuery)
            val plans = db.query(query.copy(statement = "EXPLAIN QUERY PLAN " + query.statement), cancel).bulkValue().map { it.bulkText("detail") }
            assertTrue(plans.any { it.contains("index_ruling_record_oracle_day_ordinal") })
            assertFalse(plans.any { it.contains("TEMP B-TREE") })
        }
    }

    @Test fun typedDatabaseOutcomesAndCancellationEscapeUnchanged() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            tables(db); generation(db, records = listOf(raw()))
            val outcomes: List<Outcome<Nothing>> = listOf(Outcome.Failure(bulkError(FailureCode.PERSISTENCE, "Synthetic SQL failure")),
                Outcome.Unknown("Synthetic unknown", SnapshotList.empty()), Outcome.Unavailable(UnavailableReason.REVOKED, "Synthetic unavailable"))
            for(expected in outcomes) {
                val failing = object : Database by db {
                    override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> = db.transaction({ tx ->
                        action(object : DbTransaction by tx { override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> = expected })
                    }, cancel)
                }
                assertEquals(expected, LocalCatalogRulings(failing).status(cancel))
            }
            val cancelled = object : CancelToken { override val isCancelled = true; override fun throwIfCancelled(): Unit = throw CancellationException("Synthetic cancellation") }
            try { LocalCatalogRulings(db).rulings(oracle, PageRequest(20), cancelled); fail() } catch (_: CancellationException) { }
            val failing = object : Database by db {
                override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> = db.transaction({ tx ->
                    action(object : DbTransaction by tx { override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> = throw CancellationException("Synthetic query cancellation") })
                }, cancel)
            }
            try { LocalCatalogRulings(failing).status(cancel); fail() } catch (_: CancellationException) { }
            assertEquals(1, LocalCatalogRulings(db).rulings(oracle, PageRequest(20), cancel).bulkValue().items.size)
        }
    }

    @Test fun fullStoredProvenanceRoundTripsKnowledgeEvidenceAndRejectsMalformedNumbers() {
        val original = provenance()
        assertEquals(original, AcquisitionPayloadCodec.decodeStoredProvenance(AcquisitionPayloadCodec.encodeStoredProvenance(original)))
        val absent = original.copy(source = null, checksum = null, notice = null, generation = null, version = Knowledge.Known(" v\u0000é "), sourceDate = Knowledge.Absent("No day"), effectiveAt = Knowledge.Unknown("No instant", SnapshotList.empty()))
        assertEquals(absent, AcquisitionPayloadCodec.decodeStoredProvenance(AcquisitionPayloadCodec.encodeStoredProvenance(absent)))
        val payload = AcquisitionPayloadCodec.encodeStoredProvenance(original)
        for(bad in listOf(payload.replace("\"nanos\":3", "\"nanos\":4294967296"), payload.replace("\"nanos\":3", "\"nanos\":0.5"),
            payload.replace("\"version\":1", "\"version\":2"), payload.replace("\"type\":\"storedProvenance\"", "\"type\":\"artifact\""),
            payload.replace("\"provider\":\"scryfall\"", "\"provider\":\"scryfall\",\"private-secret\":1"))) {
            try { AcquisitionPayloadCodec.decodeStoredProvenance(bad); fail("Accepted malformed metadata") }
            catch (error: IllegalArgumentException) { assertEquals("Invalid acquisition payload", error.message); assertNull(error.cause) }
        }
    }
}
