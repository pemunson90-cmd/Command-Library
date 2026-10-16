package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration

/** Actual verified-source installation; synthetic binding does not establish publisher TLS. */
class LocalCatalogSourceVersionTest {
    private val clock = object : Clock {
        override fun utcNow() = UtcTimestamp(3)
        override fun monotonicNow() = Duration.ZERO
    }
    private fun catalog(db: Database) = LocalCatalog(db, clock, object : IdSource { override fun nextId() = "status-read" })
    @Test fun installedKnownAndUnknownSourceVersionsAgreeWithActualIdentityReaders() = runBlocking {
        val versions: List<Knowledge<String>> = listOf(Knowledge.Known("source-v"),
            Knowledge.Unknown("Upstream source version is not asserted", SnapshotList.empty()))
        for (version in versions) BulkSqlTestDatabase().use { db ->
            val raw = bulkRecord("a", "o")
            val original = bulkAcquired(raw)
            val artifact = checkNotNull(original.verifiedArtifact)
            val source = original.copy(verifiedArtifact = artifact.copy(provenance = artifact.provenance.copy(sourceVersion = version)))
            db.importer(binding = CatalogVerifiedInputBinding { _, _, consume -> consume(bulkChunks(raw), EntityId("consent")) })
                .`import`(source, bulkChunks("caller bytes must not be consumed"), {}, BULK_NO_CANCEL).bulkValue()
            val before = db.rows("SELECT * FROM catalog_generation")
            val status = catalog(db).status(BULK_NO_CANCEL).bulkValue()
            val actual = status.generations.single().provenance.version
            val printing = LocalCatalogIdentities(db).printing(CardId(ProviderId("scryfall"), "a"), BULK_NO_CANCEL).bulkValue()
            val oracle = LocalCatalogIdentities(db).oracle(OracleId(ProviderId("scryfall"), "o"), BULK_NO_CANCEL).bulkValue()
            assertEquals(version, actual); assertEquals(printing.provenance.version, actual); assertEquals(oracle.provenance.version, actual)
            assertEquals(before, db.rows("SELECT * FROM catalog_generation"))
        }
    }
    @Test fun selectedLegacySourceKeepsItsExistingUnknownVersion() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            db.install(listOf(bulkRecord("a", "o")))
            val status = catalog(db).status(BULK_NO_CANCEL).bulkValue()
            val printing = LocalCatalogIdentities(db).printing(CardId(ProviderId("scryfall"), "a"), BULK_NO_CANCEL).bulkValue()
            assertEquals(printing.provenance.version, status.generations.single().provenance.version)
        }
    }
    @Test fun changedVersionColumnCannotMasqueradeAsTheRetainedSourceProof() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            val raw = bulkRecord("a", "o"); val source = bulkAcquired(raw)
            db.importer(binding = CatalogVerifiedInputBinding { _, _, consume -> consume(bulkChunks(raw), EntityId("consent")) })
                .`import`(source, bulkChunks("ignored"), {}, BULK_NO_CANCEL).bulkValue()
            db.exec("UPDATE catalog_generation SET source_version='invented'")
            val result = catalog(db).status(BULK_NO_CANCEL)
            assertEquals(FailureCode.CORRUPT_DATA, (result as Outcome.Failure).error.code)
        }
    }
}
