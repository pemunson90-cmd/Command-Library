package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalRulingsImportTest {
    @get:Rule val folder = TemporaryFolder()
    private val cancel = BULK_NO_CANCEL
    private fun failure(outcome: Outcome<*>, code: FailureCode? = null) {
        assertTrue("Expected failure, got $outcome", outcome is Outcome.Failure)
        if(code != null) assertEquals(code, (outcome as Outcome.Failure).error.code)
    }

    @Test fun realInstallationRetainsDuplicatesLiteralDatesAndCompleteProofAfterInputDeletionAndReopen() = runBlocking {
        val file = folder.newFile("rulings.db"); val inputFile = folder.newFile("synthetic.jsonl.gz")
        val duplicate = rulingTestRaw(publisher = "scryfall")
        val text = listOf(rulingTestRaw(day = "2026-10-13"), duplicate, duplicate, rulingTestRaw(oracle = RULING_TEST_ORACLE.copy(raw = "00000000-0000-0000-0000-000000000002"))).joinToString("\n")
        val bytes = bulkGzip(text.toByteArray()); inputFile.writeBytes(bytes)
        lateinit var installed: RulingsImportResult
        RulingsImportFixture(file.path).use { fixture ->
            fixture.db.install(listOf(bulkRecord("before", "unrelated")))
            val cardGeneration = fixture.db.active()
            installed = fixture.importer(fixture.artifact(bytes), bulkChunks(inputFile.readBytes(), 7)).`import`(EntityId("job"), {}, cancel).bulkValue()
            assertFalse(fixture.pinHeld); assertEquals(4L, installed.generation.recordCount)
            assertEquals(fixture.artifact(bytes), installed.acquiredArtifact); assertEquals(UtcTimestamp(21, 2), installed.generation.provenance.fetchedAt)
            assertEquals(cardGeneration, fixture.db.active()); assertTrue(inputFile.delete())
            fixture.db.install(listOf(bulkRecord("after", "different")))
            val page = LocalCatalogRulings(fixture.db).rulings(RULING_TEST_ORACLE, PageRequest(100), cancel).bulkValue()
            assertEquals(listOf(2L, 3L, 1L), page.items.map { it.id.ordinal }); assertEquals("  Synthetic\u0000 café 😀 \n", page.items.first().content.comment)
            assertEquals(installed.generation.provenance, page.items.first().provenance)
        }
        RulingsImportFixture(file.path).use { fixture ->
            assertEquals(installed.generation, LocalCatalogRulings(fixture.db).status(cancel).bulkValue().active)
            val stored = fixture.db.rows("SELECT provenance_payload FROM rulings_generation WHERE status='ACTIVE'").single().bulkText("provenance_payload")
            val proof = AcquisitionPayloadCodec.decodeRulingsInstallation(stored)
            assertEquals(installed, proof.result); assertEquals(EntityId("consent"), proof.consentId)
            assertEquals(3, LocalCatalogRulings(fixture.db).rulings(RULING_TEST_ORACLE, PageRequest(100), cancel).bulkValue().items.size)
        }
    }

    @Test fun concatenatedGzipAndCrLfSplitAcrossTinyChunksKeepExactDecodedProof() = runBlocking {
        RulingsImportFixture().use { fixture ->
            val first = rulingTestRaw(); val second = rulingTestRaw(day = "0001-01-01")
            val bytes = bulkGzip((first + "\r\n").toByteArray()) + bulkGzip(second.toByteArray())
            val progress = mutableListOf<Long>()
            val result = fixture.importer(fixture.artifact(bytes), bulkChunks(bytes, 1), policy = RulingsImportPolicy(maxBatchRecords = 1))
                .`import`(EntityId("job"), { progress.add(it.completedUnits); assertNull(fixture.active()) }, cancel).bulkValue()
            assertEquals(listOf(1L, 2L), progress); assertEquals((first + "\r\n" + second).toByteArray().size.toLong(), result.decodedBytes)
            assertEquals(2L, result.generation.recordCount)
        }
    }

    @Test fun malformedGzipFramingUtf8RowsAndExpectedIntegrityNeverReplaceActiveCorpus() = runBlocking {
        RulingsImportFixture().use { fixture ->
            val old = fixture.install(); val valid = bulkGzip(rulingTestRaw().toByteArray())
            val cases = listOf(valid.copyOf(valid.size - 1), valid + byteArrayOf(1), valid.copyOf().also { it[it.size - 8] = (it[it.size - 8].toInt() xor 1).toByte() },
                bulkGzip(byteArrayOf(0xc3.toByte(), 0x28)), bulkGzip(byteArrayOf()), bulkGzip("\n".toByteArray()),
                bulkGzip(rulingTestRaw(publisher = "unknown").toByteArray()), bulkGzip(rulingTestRaw(day = "2026-02-30").toByteArray()),
                bulkGzip((rulingTestRaw() + "\r").toByteArray()))
            for(bytes in cases) {
                failure(fixture.importer(fixture.artifact(bytes), bulkChunks(bytes)).`import`(EntityId("job"), {}, cancel))
                assertEquals(old.generation.id.value, fixture.active())
            }
            for(artifact in listOf(fixture.artifact(valid).copy(bytes = valid.size + 1L), fixture.artifact(valid).copy(sha256 = ContentHash("SHA-256", "0".repeat(64))),
                fixture.artifact(valid).copy(recordCount = Knowledge.Known(2L)))) {
                failure(fixture.importer(artifact, bulkChunks(valid)).`import`(EntityId("job"), {}, cancel))
                assertEquals(old.generation.id.value, fixture.active())
            }
        }
    }

    @Test fun sourceAndResourcePolicyRejectionsDoNotTruncateOrActivate() = runBlocking {
        RulingsImportFixture().use { fixture ->
            val old = fixture.install(); val bytes = bulkGzip((rulingTestRaw() + "\n" + rulingTestRaw()).toByteArray()); val artifact = fixture.artifact(bytes)
            for(policy in listOf(RulingsImportPolicy(maxRawBytes = bytes.size - 1L), RulingsImportPolicy(maxDecodedBytes = 10), RulingsImportPolicy(maxRecords = 1))) {
                failure(fixture.importer(artifact, bulkChunks(bytes), policy = policy).`import`(EntityId("job"), {}, cancel))
                assertEquals(old.generation.id.value, fixture.active())
            }
            for(bad in listOf(artifact.copy(jobId = EntityId("other")), artifact.copy(framing = ArtifactFraming.JSON_ARRAY), artifact.copy(compression = ArtifactCompression.NONE),
                artifact.copy(provenance = artifact.provenance.copy(provider = ProviderId("other"))),
                artifact.copy(provenance = artifact.provenance.copy(languageScope = AcquisitionLanguageScope.ENGLISH_CARDS)),
                artifact.copy(provenance = artifact.provenance.copy(requestedUri = ResourceUri("https://untrusted.invalid/rulings/x.jsonl.gz")))))
                failure(fixture.importer(bad, bulkChunks(bytes)).`import`(EntityId("job"), {}, cancel), FailureCode.INVALID_INPUT)
            assertEquals(old.generation.id.value, fixture.active())
        }
    }

    @Test fun cancelledStagingKeepsPreviousCorpusAndReleasesPinAndOwner() = runBlocking {
        RulingsImportFixture().use { fixture ->
            val old = fixture.install(); val bytes = bulkGzip((rulingTestRaw() + "\n" + rulingTestRaw()).toByteArray()); val token = BulkMutableCancel()
            try {
                fixture.importer(fixture.artifact(bytes), bulkChunks(bytes), policy = RulingsImportPolicy(maxBatchRecords = 1))
                    .`import`(EntityId("job"), { token.cancelled = true }, token)
                fail("Expected cancellation")
            } catch (_: CancellationException) { }
            assertFalse(fixture.pinHeld); assertFalse(CatalogImportOwnership.mutex.isLocked)
            assertEquals(old.generation.id.value, fixture.active()); assertEquals(1L, fixture.db.count("ruling_record"))
            assertTrue(fixture.install().generation.id != old.generation.id)
        }
    }

    @Test fun zeroAffectedStagingRowMetadataOrPointerWritesRollbackAndPreservePriorCorpus() = runBlocking {
        for(prefix in listOf("INSERT INTO rulings_generation", "INSERT INTO ruling_record", "UPDATE rulings_generation SET status='ACTIVE'", "UPDATE rulings_active SET generation_id"))
            RulingsImportFixture().use { fixture ->
                val old = fixture.install(); val bytes = bulkGzip(rulingTestRaw().toByteArray())
                val faults = RulingsFaultDatabase(fixture.db)
                faults.beforeExecute = { request -> if(request.statement.startsWith(prefix)) Outcome.Success(DbWriteResult(0)) else null }
                faults.beforeAcknowledgement = { assertTrue(fixture.pinHeld) }
                failure(fixture.importer(fixture.artifact(bytes), bulkChunks(bytes), faults).`import`(EntityId("job"), {}, cancel))
                assertEquals(old.generation.id.value, fixture.active()); assertEquals(1L, fixture.db.count("ruling_record"))
            }
    }

    @Test fun cancellationAfterDurableCommitReturnsReconciledSuccessWhilePinStillHeld() = runBlocking {
        RulingsImportFixture().use { fixture ->
            fixture.install(); val bytes = bulkGzip(rulingTestRaw(comment = "New corpus").toByteArray())
            val faults = RulingsFaultDatabase(fixture.db); var acknowledgements = 0
            faults.afterActivation = { throw CancellationException("Synthetic lost commit acknowledgement") }
            faults.beforeAcknowledgement = { assertTrue(fixture.pinHeld); acknowledgements++ }
            val result = fixture.importer(fixture.artifact(bytes), bulkChunks(bytes), faults).`import`(EntityId("job"), {}, cancel).bulkValue()
            assertEquals(1, acknowledgements); assertEquals(result.generation.id.value, fixture.active()); assertFalse(fixture.pinHeld)
            assertEquals(0, faults.deletes)
            assertEquals("New corpus", LocalCatalogRulings(fixture.db).rulings(RULING_TEST_ORACLE, PageRequest(10), cancel).bulkValue().items.single().content.comment)
        }
    }

    @Test fun inconclusiveCommitAcknowledgementPreservesPotentiallyActiveDataForFailureAndCancellation() = runBlocking {
        for(cancelled in listOf(false, true)) RulingsImportFixture().use { fixture ->
            fixture.install(); val bytes = bulkGzip(rulingTestRaw(comment = "New corpus").toByteArray()); val faults = RulingsFaultDatabase(fixture.db)
            faults.afterActivation = { if(cancelled) throw CancellationException("Synthetic late cancel") else error("Synthetic lost acknowledgement") }
            faults.acknowledgementFailure = Outcome.Failure(bulkError(FailureCode.PERSISTENCE, "Synthetic acknowledgement unavailable"))
            faults.beforeAcknowledgement = { assertTrue(fixture.pinHeld) }
            try {
                val result = fixture.importer(fixture.artifact(bytes), bulkChunks(bytes), faults).`import`(EntityId("job"), {}, cancel)
                assertFalse(cancelled); assertTrue(result is Outcome.Unknown)
            } catch (_: CancellationException) { assertTrue(cancelled) }
            assertEquals("rulings-2", fixture.active()); assertEquals(2L, fixture.db.count("ruling_record")); assertEquals(0, faults.deletes)
            assertFalse(fixture.pinHeld)
        }
    }

    @Test fun interruptedLargeStagingCleanupIsResumableAndNeverDeletesActiveRows() = runBlocking {
        RulingsImportFixture().use { fixture ->
            val old = fixture.install()
            val text = List(401) { rulingTestRaw() }.joinToString("\n", postfix = "\nprivate-secret")
            val bytes = bulkGzip(text.toByteArray())
            failure(fixture.importer(fixture.artifact(bytes), bulkChunks(bytes), policy = RulingsImportPolicy(maxBatchRecords = 100)).`import`(EntityId("job"), {}, cancel))
            assertEquals(old.generation.id.value, fixture.active())
            val remaining = fixture.db.rows("SELECT count(*) AS n FROM ruling_record WHERE generation_id='rulings-2'").single().bulkLong("n")
            assertTrue(remaining in 1..400)
            var recoveries = 0
            while(!RulingsGenerationCleanup(fixture.db).recover(cancel, maxTransactions = 2).bulkValue()) { assertTrue(++recoveries <= 10) }
            assertEquals(1L, fixture.db.count("ruling_record")); assertEquals(1L, fixture.db.count("rulings_generation"))
            assertTrue(RulingsGenerationCleanup(fixture.db).recover(cancel).bulkValue())
        }
    }

    @Test fun ambiguousInitialInsertAndIdentifierCollisionStayRecoverableWithoutDeletingAnExistingOwner() = runBlocking {
        RulingsImportFixture().use { fixture ->
            val old = fixture.install(); val bytes = bulkGzip(rulingTestRaw().toByteArray()); val faults = RulingsFaultDatabase(fixture.db)
            faults.afterStaging = { throw CancellationException("Synthetic initial insert acknowledgement lost") }
            try { fixture.importer(fixture.artifact(bytes), bulkChunks(bytes), faults).`import`(EntityId("job"), {}, cancel); fail() }
            catch (_: CancellationException) { }
            assertEquals(2L, fixture.db.count("rulings_generation")); assertEquals(old.generation.id.value, fixture.active())
            val existing = fixture.db.rows("SELECT * FROM rulings_generation WHERE generation_id='rulings-2'")
            val collision = object : IdSource { override fun nextId() = "rulings-2" }
            failure(fixture.importer(fixture.artifact(bytes), bulkChunks(bytes), idSource = collision).`import`(EntityId("job"), {}, cancel))
            assertEquals(existing, fixture.db.rows("SELECT * FROM rulings_generation WHERE generation_id='rulings-2'"))
            assertTrue(RulingsGenerationCleanup(fixture.db).recover(cancel, 2).bulkValue())
            assertEquals(1L, fixture.db.count("rulings_generation"))
        }
    }

    @Test fun installedEnvelopeCorruptionAndSqlProjectionDisagreementAreTypedCorruption() = runBlocking {
        for(mutation in listOf("count", "rawBytes", "consent", "version", "private-key")) RulingsImportFixture().use { fixture ->
            val result = fixture.install()
            if(mutation == "count") fixture.db.exec("UPDATE rulings_generation SET record_count=2")
            else {
                val row = fixture.db.rows("SELECT provenance_payload FROM rulings_generation").single().bulkText("provenance_payload")
                val root = JsonParser.parseString(row).asJsonObject; val value = root.getAsJsonObject("value")
                when(mutation) {
                    "rawBytes" -> value.addProperty("rawBytes", result.rawBytes + 1)
                    "consent" -> value.addProperty("consentId", "")
                    "version" -> root.addProperty("version", 2)
                    else -> value.addProperty("private-secret", "ignored?")
                }
                val payload = root.toString().replace("'", "''")
                fixture.db.exec("UPDATE rulings_generation SET provenance_payload='$payload'")
            }
            failure(LocalCatalogRulings(fixture.db).status(cancel), FailureCode.CORRUPT_DATA)
        }
    }

    @Test fun changedActiveGenerationIsNeverOverwrittenByAnOlderImport() = runBlocking {
        RulingsImportFixture().use { fixture ->
            val old = fixture.install(); val bytes = bulkGzip(rulingTestRaw().toByteArray())
            val external = GenerationId("external")
            val metadata = AcquisitionPayloadCodec.encodeStoredProvenance(old.generation.provenance.copy(generation = external)).replace("'", "''")
            var changed = false
            val result = fixture.importer(fixture.artifact(bytes), bulkChunks(bytes)).`import`(EntityId("job"), {
                if(!changed) {
                    changed = true
                    fixture.db.exec("BEGIN IMMEDIATE")
                    fixture.db.exec("UPDATE rulings_generation SET status='RETIRED' WHERE status='ACTIVE'")
                    fixture.db.exec("INSERT INTO rulings_generation VALUES ('external','scryfall','ACTIVE','RULINGS_LANGUAGE_NOT_ASSERTED','$metadata',0,1)")
                    fixture.db.exec("UPDATE rulings_active SET generation_id='external'")
                    fixture.db.exec("COMMIT")
                }
            }, cancel)
            failure(result, FailureCode.STALE_GENERATION)
            assertEquals(external, LocalCatalogRulings(fixture.db).status(cancel).bulkValue().active!!.id)
            assertEquals(1L, fixture.db.count("ruling_record")); assertEquals("external", fixture.active())
        }
    }

    @Test fun missingOrRejectingTrustedBindingCannotReportInstallation() = runBlocking {
        RulingsImportFixture().use { fixture ->
            val old = fixture.install()
            val skipped = RulingsVerifiedInputBinding { _, _, _ -> old }
            failure(LocalRulingsImport(fixture.db, fixture.clock, fixture.ids, skipped).`import`(EntityId("job"), {}, cancel), FailureCode.INVALID_INPUT)
            val rejected = RulingsVerifiedInputBinding { _, _, _ -> throw ArtifactReadException(bulkError(FailureCode.PERMISSION, "private-secret")) }
            val result = LocalRulingsImport(fixture.db, fixture.clock, fixture.ids, rejected).`import`(EntityId("job"), {}, cancel)
            failure(result, FailureCode.PERMISSION); assertFalse((result as Outcome.Failure).error.message.contains("private-secret"))
            assertEquals(old.generation.id.value, fixture.active()); assertEquals(1L, fixture.db.count("rulings_generation"))
        }
    }

    @Test fun acknowledgementTimeoutLeavesInstalledDataAndReleasesThePin() = runBlocking {
        RulingsImportFixture().use { fixture ->
            val bytes = bulkGzip(rulingTestRaw().toByteArray()); val faults = RulingsFaultDatabase(fixture.db)
            faults.afterActivation = { error("Synthetic lost acknowledgement") }
            faults.acknowledgementGate = CompletableDeferred()
            faults.beforeAcknowledgement = { assertTrue(fixture.pinHeld) }
            val result = fixture.importer(fixture.artifact(bytes), bulkChunks(bytes), faults).`import`(EntityId("job"), {}, cancel)
            assertTrue(result is Outcome.Unknown); assertFalse(fixture.pinHeld); assertEquals(0, faults.deletes)
            assertEquals(1L, fixture.db.count("ruling_record")); assertNotNull(LocalCatalogRulings(fixture.db).status(cancel).bulkValue().active)
        }
    }

    @Test fun failedCleanupRemainsRecoverableAndOversizedMetadataIsNeverSilentlyReduced() = runBlocking {
        RulingsImportFixture().use { fixture ->
            val old = fixture.install(); val bytes = bulkGzip((rulingTestRaw() + "\nprivate-secret").toByteArray())
            fixture.db.failCleanup = true
            val result = fixture.importer(fixture.artifact(bytes), bulkChunks(bytes), policy = RulingsImportPolicy(maxBatchRecords = 1))
                .`import`(EntityId("job"), {}, cancel)
            failure(result, FailureCode.INVALID_INPUT)
            assertFalse((result as Outcome.Failure).error.message.contains("private-secret")); assertEquals(old.generation.id.value, fixture.active())
            assertEquals("FAILED", fixture.db.rows("SELECT status FROM rulings_generation WHERE generation_id='rulings-2'").single().bulkText("status"))
            fixture.db.failCleanup = false
            assertTrue(RulingsGenerationCleanup(fixture.db).recover(cancel).bulkValue())
            val valid = bulkGzip(rulingTestRaw().toByteArray()); val original = fixture.artifact(valid)
            val oversized = original.copy(provenance = original.provenance.copy(sourceVersion = Knowledge.Known("é".repeat(70_000))))
            failure(fixture.importer(oversized, bulkChunks(valid)).`import`(EntityId("job"), {}, cancel), FailureCode.INVALID_INPUT)
            assertEquals(1L, fixture.db.count("rulings_generation")); assertEquals(old.generation.id.value, fixture.active())
        }
    }
}
