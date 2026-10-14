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

/** Entirely synthetic metadata and admission evidence; never an authentic source/TLS assertion. */
class RulingsSourceReceiptTest {
    @get:Rule val folder = TemporaryFolder()
    private val cancel = BULK_NO_CANCEL
    private fun body() = """{"object":"bulk_data","id":"06f54c0b-ab9c-452d-b35a-8297db5eb940","type":"rulings","updated_at":"2026-10-14T09:00:34.909Z","uri":"https://api.scryfall.com/bulk-data/06f54c0b-ab9c-452d-b35a-8297db5eb940","jsonl_download_uri":"https://data.scryfall.io/rulings/synthetic.jsonl.gz","compressed_size":5422271}"""
    private fun receipt(id: String = "receipt", job: String = "metadata-job", raw: String = body()): RulingsSourceReceipt {
        val request = TrustedRulingsProfiles.discovery("Synthetic ruling metadata", true)
        val unhashed = DownloadPlan(EntityId(job), request, ContentHash("SHA-256", "0".repeat(64)), UtcTimestamp(1, 2))
        val plan = unhashed.copy(planHash = receiptHash(AcquisitionPayloadCodec.encodePlan(unhashed).toByteArray()))
        val artifact = VerifiedArtifact(plan.jobId, ArtifactFraming.TEXT, ArtifactCompression.NONE, raw.toByteArray().size.toLong(), receiptHash(raw.toByteArray()),
            false, Knowledge.Unknown("Raw metadata, records not parsed", SnapshotList.empty()), 0,
            AcquisitionProvenance(request.intent.source, request.intent.resource, request.intent.resource, request.sourceVersion, request.sourceDate,
                UtcTimestamp(3, 4), UtcTimestamp(5, 6), Knowledge.Known("synthetic-etag"), Knowledge.Absent("Not supplied"),
                AcquisitionConsistency.FILE_REPRESENTATION, request.languageScope, request.notice))
        return RulingsSourceReceipt(EntityId(id), plan, artifact, EntityId("metadata-consent"), UtcTimestamp(7, 8), raw)
    }
    private fun table(db: BulkSqlTestDatabase) {
        if(db.rows("SELECT name FROM sqlite_master WHERE name='catalog_source_receipt'").isNotEmpty()) return
        var root = File(requireNotNull(System.getProperty("user.dir")))
        val schema: File
        while(true) {
            val candidate = File(root, "android-app/data/storage/schemas/com.architectpsg.commandlibrary.data.storage.schema.StorageSchemaDatabase/8.json")
            if(candidate.isFile) { schema = candidate; break }; root = requireNotNull(root.parentFile)
        }
        val entity = JsonParser.parseString(schema.readText()).asJsonObject.getAsJsonObject("database").getAsJsonArray("entities")
            .single { it.asJsonObject["tableName"].asString == "catalog_source_receipt" }.asJsonObject
        db.exec(entity["createSql"].asString.replace("\${TABLE_NAME}", "catalog_source_receipt"))
        entity.getAsJsonArray("indices").forEach { db.exec(it.asJsonObject["createSql"].asString.replace("\${TABLE_NAME}", "catalog_source_receipt")) }
    }
    private fun corrupt(value: Outcome<*>) { assertTrue(value is Outcome.Failure); assertEquals(FailureCode.CORRUPT_DATA, (value as Outcome.Failure).error.code) }

    @Test fun receiptRoundTripsLiteralBodyPlanArtifactConsentAndPublicationFraction() {
        val original = receipt(raw = body() + "\r\n \t")
        assertEquals(original, RulingsSourceReceiptCodec.decode(RulingsSourceReceiptCodec.encode(original)))
        assertEquals(909_000_000, original.candidate.updatedAt.nanoAdjustment)
        assertEquals(5422271L, original.candidate.compressedBytes)
        assertEquals(Knowledge.Unknown("Upstream source version is not asserted", SnapshotList.empty()), original.metadataArtifact.provenance.sourceVersion)
        assertTrue(original.metadataArtifact.provenance.notice is Knowledge.Unknown)
    }

    @Test fun fixedDiscoveryAndIssuedFileAreDistinctExactPoliciesWithNoLanguageOrUpstreamHashGuess() {
        val value = receipt(); val profiles = TrustedCatalogProfiles()
        val discovery = value.metadataPlan.request; val file = TrustedRulingsProfiles.file(value.facts(), "Synthetic corpus", false)
        assertTrue(profiles.validate(discovery) is Outcome.Success)
        assertTrue(profiles.validate(file) is Outcome.Failure)
        assertTrue(profiles.validate(TrustedEnglishSearchProfile.request("Synthetic English", false)) is Outcome.Success)
        profiles.publishVerified(value.facts())
        assertTrue(profiles.validate(file) is Outcome.Success)
        assertNotEquals(discovery.intent.resource, file.intent.resource); assertNotEquals(discovery.intent.version, file.intent.version)
        assertNull(file.intent.language); assertNull(file.intent.checksum)
        assertEquals(Knowledge.Known(value.candidate.updatedAt), file.sourceDate)
        assertEquals(ArtifactCompression.GZIP, (file.source as AcquisitionSource.RemoteFile).compression)
        val endpoint = profiles.endpointFor(discovery, discovery.intent.resource)
        assertEquals(10, endpoint.requestsPerSecond); assertTrue(endpoint.allowedRedirectOrigins.isEmpty())
        for(uri in listOf(ResourceUri("https://api.scryfall.com/bulk-data"), ResourceUri("https://data.scryfall.io/rulings/other.jsonl.gz")))
            assertThrows(IllegalArgumentException::class.java) { profiles.endpointFor(discovery, uri) }
        assertThrows(IllegalArgumentException::class.java) { profiles.endpointFor(file, ResourceUri("https://data.scryfall.io/rulings/other.jsonl.gz")) }
        for(bad in listOf(file.copy(sourceDate = Knowledge.Absent("Forged")), file.copy(intent = file.intent.copy(language = LanguageTag.ENGLISH)),
            file.copy(languageScope = AcquisitionLanguageScope.ENGLISH_CARDS), file.copy(intent = file.intent.copy(version = "RULINGS_FILE_v1:unknown"))))
            assertTrue(profiles.validate(bad) is Outcome.Failure)
    }

    @Test fun receiptRejectsSubstitutedPlanArtifactSourceConsentAndBodyBeforeAuthorityExists() {
        val value = receipt()
        for(action in listOf<() -> Unit>(
            { value.copy(metadataBody = body() + " ") }, { value.copy(metadataArtifact = value.metadataArtifact.copy(bytes = 1)) },
            { value.copy(metadataArtifact = value.metadataArtifact.copy(compression = ArtifactCompression.GZIP)) },
            { value.copy(metadataArtifact = value.metadataArtifact.copy(jobId = EntityId("other"))) },
            { value.copy(metadataArtifact = value.metadataArtifact.copy(provenance = value.metadataArtifact.provenance.copy(finalUri = ResourceUri("https://api.scryfall.com/bulk-data")))) },
            { value.copy(metadataPlan = value.metadataPlan.copy(planHash = ContentHash("SHA-256", "0".repeat(64)))) },
            { value.copy(metadataConsentId = EntityId("bad\u0000id")) }, { value.copy(id = EntityId("bad:receipt")) }))
            assertThrows(IllegalArgumentException::class.java) { action() }
        val payload = RulingsSourceReceiptCodec.encode(value)
        for(bad in listOf(payload.replace("\"version\":1", "\"version\":2"), payload.replace("\"issuedNanos\":8", "\"issuedNanos\":4294967296"),
            payload.replace("\"issuedNanos\":8", "\"issuedNanos\":0.5"), payload.replace("\"type\":\"scryfallRulingsReceipt\"", "\"type\":\"private-secret\""))) {
            try { RulingsSourceReceiptCodec.decode(bad); fail() } catch(error: IllegalArgumentException) { assertEquals("Invalid ruling source receipt", error.message); assertNull(error.cause) }
        }
    }

    @Test fun metadataAndDedicatedJsonBoundsHandleExactBytesEscapingAndMalformedUnicode() {
        val prefix = body(); val maximum = RulingsSourceReceiptCodec.MAX_METADATA_BYTES
        val full = receipt(raw = prefix + " ".repeat(maximum - prefix.toByteArray().size))
        assertEquals(maximum.toLong(), full.metadataArtifact.bytes)
        assertEquals(full, RulingsSourceReceiptCodec.decode(RulingsSourceReceiptCodec.encode(full)))
        assertThrows(IllegalArgumentException::class.java) { receipt(raw = full.metadataBody + " ") }
        val json = JsonObject().apply { addProperty("v", "é".repeat(100)) }
        val encoded = BoundedRulingsJson.encode(json, 208)
        assertEquals(208, encoded.toByteArray().size)
        assertThrows(IllegalArgumentException::class.java) { BoundedRulingsJson.encode(json, 207) }
        val escaped = JsonObject().apply { addProperty("v", "\n".repeat(100)) }
        assertEquals(208, BoundedRulingsJson.encode(escaped, 208).toByteArray().size)
        assertThrows(IllegalArgumentException::class.java) { BoundedRulingsJson.bytes("\uD800", 10) }
    }

    @Test fun actualSqliteIdempotentEvidenceSurvivesJobAbsenceReopenAndRehydratesCanonicalFacts() = runBlocking {
        val file = folder.newFile("receipt.db"); val original = receipt()
        BulkSqlTestDatabase(file.path).use { db ->
            table(db); val store = RulingsReceiptStore(db)
            assertEquals(original, store.save(original, cancel).bulkValue())
            assertEquals(original, store.save(original.copy(id = EntityId("unused-new-id"), issuedAt = UtcTimestamp(99)), cancel).bulkValue())
            assertEquals(1L, db.count("catalog_source_receipt")); assertEquals(0L, db.count("acquisition_job"))
            assertEquals(original, store.byMetadataJob(original.metadataPlan.jobId, cancel).bulkValue())
        }
        BulkSqlTestDatabase(file.path).use { db ->
            val store = RulingsReceiptStore(db)
            assertEquals(original, store.byId(original.id, cancel).bulkValue())
            val profiles = TrustedCatalogProfiles(store.loadFacts(cancel).bulkValue())
            assertTrue(profiles.validate(TrustedRulingsProfiles.file(original.facts(), "Synthetic corpus", true)) is Outcome.Success)
        }
    }

    @Test fun receiptCapacityRejectsOnlyNewEvidenceWhileExistingProofRemainsAvailable() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            table(db); val first = receipt(); val second = receipt("second", "second-job")
            val store = RulingsReceiptStore(db, maxReceipts = 1)
            assertEquals(first, store.save(first, cancel).bulkValue())
            assertTrue(store.save(second, cancel) is Outcome.Unavailable)
            assertEquals(first, store.save(first.copy(id = EntityId("retry")), cancel).bulkValue())
            assertEquals(1, store.loadFacts(cancel).bulkValue().size)
        }
        BulkSqlTestDatabase().use { db ->
            table(db); val store = RulingsReceiptStore(db, maxTotalBytes = 1)
            assertTrue(store.save(receipt(), cancel) is Outcome.Unavailable)
            assertEquals(0L, db.count("catalog_source_receipt"))
        }
    }

    @Test fun corruptReceiptOrProjectionCannotRehydrateWireAuthority() = runBlocking {
        for(sql in listOf("UPDATE catalog_source_receipt SET payload_schema_version=2", "UPDATE catalog_source_receipt SET metadata_job_id='other'",
            "UPDATE catalog_source_receipt SET receipt_payload='private-secret'", "UPDATE catalog_source_receipt SET receipt_id='bad:receipt'"))
            BulkSqlTestDatabase().use { db ->
                table(db); val store = RulingsReceiptStore(db); store.save(receipt(), cancel).bulkValue(); db.exec(sql)
                corrupt(store.loadFacts(cancel))
            }
    }

    @Test fun cancellationAfterReceiptCommitCanReconcileWithoutSpeculativeCachePublication() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            table(db); val value = receipt(); val faults = object : Database by db {
                var thrown = false
                override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> {
                    val result = db.transaction(action, cancel)
                    if(!thrown && result is Outcome.Success) { thrown = true; throw CancellationException("Synthetic late cancellation") }
                    return result
                }
            }
            assertEquals(value, RulingsReceiptStore(faults).save(value, cancel).bulkValue())
            assertEquals(1L, db.count("catalog_source_receipt"))
        }
    }

    @Test fun inconclusiveReceiptAcknowledgementGrantsNoCacheAuthorityAndKeepsDurableEvidence() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            table(db); val value = receipt(); var afterCommit = false
            val faults = object : Database by db {
                override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> {
                    val result = db.transaction(action, cancel); afterCommit = true
                    return if(result is Outcome.Success) Outcome.Failure(bulkError(FailureCode.PERSISTENCE, "Synthetic lost acknowledgement")) else result
                }
                override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> =
                    if(afterCommit) Outcome.Failure(bulkError(FailureCode.PERSISTENCE, "Synthetic read unavailable")) else db.query(request, cancel)
            }
            assertTrue(RulingsReceiptStore(faults).save(value, cancel) is Outcome.Unknown)
            assertEquals(1L, db.count("catalog_source_receipt"))
            val profiles = TrustedCatalogProfiles()
            assertTrue(profiles.validate(TrustedRulingsProfiles.file(value.facts(), "Unissued", true)) is Outcome.Failure)
            assertEquals(value, RulingsReceiptStore(db).byMetadataJob(value.metadataPlan.jobId, cancel).bulkValue())
        }
    }
}
