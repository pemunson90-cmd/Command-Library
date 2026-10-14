package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Synthetic source evidence exercises persistence, never publisher authenticity. */
class RulingsTrustedInstallationTest {
    @Test fun v3LostActivationAcknowledgementProvesExactEnvelopeWhilePinRemainsHeld() = runBlocking {
        RulingsImportFixture().use { f ->
            val bytes = bulkGzip(rulingTestRaw().toByteArray()); val proof = trustedRulingProof(bytes, "\n".repeat(90_000))
            val artifact = trustedRulingArtifact(proof, bytes)
            val faults = RulingsFaultDatabase(f.db)
            var held = false; var acknowledgements = 0
            faults.afterActivation = { throw kotlinx.coroutines.CancellationException("Synthetic lost activation ACK") }
            faults.beforeAcknowledgement = { assertTrue(held); acknowledgements++ }
            val binding = RulingsVerifiedInputBinding { _, _, consume ->
                held = true
                try { consume(artifact, EntityId("file-consent"), bulkChunks(bytes), proof) } finally { held = false }
            }
            val result = LocalRulingsImport(faults, f.clock, f.ids, binding).`import`(artifact.jobId, {}, BULK_NO_CANCEL).bulkValue()
            assertEquals(1, acknowledgements); assertFalse(held)
            assertEquals(result.generation, LocalCatalogRulings(f.db).status(BULK_NO_CANCEL).bulkValue().active)
        }
    }

    @Test fun completeSourceChainIsStoredOnceAndRemainsReadableWithoutJobsOrReceipts() = runBlocking {
        RulingsImportFixture().use { f ->
            val bytes = bulkGzip(rulingTestRaw().toByteArray())
            val proof = trustedRulingProof(bytes)
            val artifact = trustedRulingArtifact(proof, bytes)
            val binding = RulingsVerifiedInputBinding { _, _, consume -> consume(artifact, EntityId("file-consent"), bulkChunks(bytes), proof) }
            val result = LocalRulingsImport(f.db, f.clock, f.ids, binding).`import`(artifact.jobId, {}, BULK_NO_CANCEL).bulkValue()
            val row = f.db.rows("SELECT payload_schema_version,provenance_payload FROM rulings_generation WHERE status='ACTIVE'").single()
            assertEquals(3L, row.bulkLong("payload_schema_version"))
            val payload = row.bulkText("provenance_payload")
            val decoded = RulingsTrustedInstallationCodec.decode(payload)
            assertEquals(result, decoded.installation.result); assertEquals(proof, decoded.proof)
            val json = JsonParser.parseString(payload).asJsonObject
            assertTrue(json["receipt"].isJsonObject); assertTrue(json["filePlan"].isJsonObject); assertTrue(json["installation"].isJsonObject)
            assertEquals(0L, f.db.count("acquisition_job"))
            assertEquals(result.generation, LocalCatalogRulings(f.db).status(BULK_NO_CANCEL).bulkValue().active)
        }
    }

    @Test fun mismatchedFilePlanReceiptArtifactAndConsentCannotBecomeTrustedInstallation() = runBlocking {
        RulingsImportFixture().use { f ->
            val bytes = bulkGzip(rulingTestRaw().toByteArray()); val proof = trustedRulingProof(bytes)
            val artifact = trustedRulingArtifact(proof, bytes)
            assertThrows(IllegalArgumentException::class.java) { proof.copy(filePlan = proof.filePlan.copy(planHash = ContentHash("SHA-256", "0".repeat(64)))) }
            for(bad in listOf(artifact.copy(bytes = artifact.bytes + 1), artifact.copy(provenance = artifact.provenance.copy(sourceDate = Knowledge.Absent("Forged"))),
                artifact.copy(provenance = artifact.provenance.copy(finalUri = ResourceUri("https://data.scryfall.io/rulings/other.jsonl.gz"))))) {
                val binding = RulingsVerifiedInputBinding { _, _, consume -> consume(bad, EntityId("file-consent"), bulkChunks(bytes), proof) }
                assertTrue(LocalRulingsImport(f.db, f.clock, f.ids, binding).`import`(bad.jobId, {}, BULK_NO_CANCEL) is Outcome.Failure)
                assertEquals(0L, f.db.count("rulings_generation"))
            }
        }
    }

    @Test fun v3GuardDoesNotWidenLegacyMetadataAndRejectsCorruptVersionAndNestedProof() = runBlocking {
        RulingsImportFixture().use { f ->
            val bytes = bulkGzip(rulingTestRaw().toByteArray()); val proof = trustedRulingProof(bytes, "\n".repeat(90_000))
            val artifact = trustedRulingArtifact(proof, bytes)
            val binding = RulingsVerifiedInputBinding { _, _, consume -> consume(artifact, EntityId("file-consent"), bulkChunks(bytes), proof) }
            LocalRulingsImport(f.db, f.clock, f.ids, binding).`import`(artifact.jobId, {}, BULK_NO_CANCEL).bulkValue()
            val payload = f.db.rows("SELECT provenance_payload FROM rulings_generation").single().bulkText("provenance_payload")
            assertTrue(payload.toByteArray().size > LocalCatalogRulings.MAX_METADATA_BYTES)
            assertNotNull(LocalCatalogRulings(f.db).status(BULK_NO_CANCEL).bulkValue().active)
            f.db.exec("UPDATE rulings_generation SET payload_schema_version=2")
            assertTrue(LocalCatalogRulings(f.db).status(BULK_NO_CANCEL) is Outcome.Failure)
            val exact = payload + " ".repeat(RulingsTrustedInstallationCodec.MAX_BYTES - payload.toByteArray().size)
            assertEquals(RulingsTrustedInstallationCodec.MAX_BYTES, exact.toByteArray().size)
            assertEquals(proof, RulingsTrustedInstallationCodec.decode(exact).proof)
            for(bad in listOf(payload.replace("\"version\":3", "\"version\":4"), payload.replace("\"id\":\"receipt\"", "\"id\":\"other\""), exact + " ")) {
                assertThrows(IllegalArgumentException::class.java) { RulingsTrustedInstallationCodec.decode(bad) }
            }
        }
    }
}

internal fun trustedRulingProof(bytes: ByteArray, whitespace: String = ""): RulingsTrustedInputProof {
    val body = """{"object":"bulk_data","id":"06f54c0b-ab9c-452d-b35a-8297db5eb940","type":"rulings","updated_at":"2026-10-14T09:00:34.909Z","uri":"https://api.scryfall.com/bulk-data/06f54c0b-ab9c-452d-b35a-8297db5eb940","jsonl_download_uri":"https://data.scryfall.io/rulings/synthetic.jsonl.gz","compressed_size":${bytes.size}}""" + whitespace
    val metadataPlan = trustedRulingPlan("metadata-job", TrustedRulingsProfiles.discovery("Synthetic discovery", false))
    val r = metadataPlan.request
    val artifact = VerifiedArtifact(metadataPlan.jobId, ArtifactFraming.TEXT, ArtifactCompression.NONE, body.toByteArray().size.toLong(), receiptHash(body.toByteArray()), false,
        Knowledge.Unknown("Not parsed", SnapshotList.empty()), 0, AcquisitionProvenance(r.intent.source, r.intent.resource, r.intent.resource, r.sourceVersion, r.sourceDate,
            UtcTimestamp(2), UtcTimestamp(3), Knowledge.Absent("No etag"), Knowledge.Absent("No last modified"), AcquisitionConsistency.FILE_REPRESENTATION, r.languageScope, r.notice))
    val receipt = RulingsSourceReceipt(EntityId("receipt"), metadataPlan, artifact, EntityId("metadata-consent"), UtcTimestamp(4), body)
    return RulingsTrustedInputProof(receipt, trustedRulingPlan("file-job", TrustedRulingsProfiles.file(receipt.facts(), "Synthetic file", false)))
}
private fun trustedRulingPlan(id: String, request: AcquisitionRequest): DownloadPlan {
    val zero = DownloadPlan(EntityId(id), request, ContentHash("SHA-256", "0".repeat(64)), UtcTimestamp(1))
    return zero.copy(planHash = receiptHash(AcquisitionPayloadCodec.encodePlan(zero).toByteArray()))
}
internal fun trustedRulingArtifact(proof: RulingsTrustedInputProof, bytes: ByteArray): VerifiedArtifact {
    val r = proof.filePlan.request
    return VerifiedArtifact(proof.filePlan.jobId, ArtifactFraming.JSONL, ArtifactCompression.GZIP, bytes.size.toLong(), receiptHash(bytes), false,
        Knowledge.Unknown("Not parsed", SnapshotList.empty()), 0, AcquisitionProvenance(r.intent.source, r.intent.resource, r.intent.resource, r.sourceVersion, r.sourceDate,
            UtcTimestamp(5), UtcTimestamp(6), Knowledge.Absent("No etag"), Knowledge.Absent("No last modified"), AcquisitionConsistency.FILE_REPRESENTATION, r.languageScope, r.notice))
}
