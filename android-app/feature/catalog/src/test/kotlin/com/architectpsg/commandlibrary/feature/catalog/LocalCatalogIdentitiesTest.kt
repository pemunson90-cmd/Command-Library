package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration

class LocalCatalogIdentitiesTest {
    @Test fun selectedTinyImportExposesActualPrintingIdentityAndOraclePage() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val clock = object : Clock { override fun utcNow() = UtcTimestamp(3); override fun monotonicNow() = Duration.ZERO }
            var sequence = 0
            LocalCatalog(db, clock, object : IdSource { override fun nextId() = "selected-${++sequence}" })
                .`import`(bulkSelected().legacySource(), bulkChunks("[${bulkRecord("selected-p", "selected-o")}]") , {}, BULK_NO_CANCEL).bulkValue()
            val lookup = LocalCatalogIdentities(db)
            val printing = lookup.printing(CardId(ProviderId("scryfall"), "selected-p"), BULK_NO_CANCEL).bulkValue()
            assertEquals("selected-p", printing.id.raw)
            assertEquals(listOf("selected-p"), lookup.printings(OracleId(ProviderId("scryfall"), "selected-o"), PageRequest(10), BULK_NO_CANCEL).bulkValue().items.map { it.id.raw })
            assertEquals("WHOLE", db.rows("SELECT source_kind FROM printing_oracle_membership").single().bulkText("source_kind"))
        }
    }

    @Test fun retainedSelectedDirectRelationWorksWithoutRepairButStillRequiresActualOracle() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val clock = object : Clock { override fun utcNow() = UtcTimestamp(3); override fun monotonicNow() = Duration.ZERO }
            var sequence = 0
            LocalCatalog(db, clock, object : IdSource { override fun nextId() = "legacy-${++sequence}" })
                .`import`(bulkSelected().legacySource(), bulkChunks("[${bulkRecord("a", "o")},${bulkRecord("b", "o")}]") , {}, BULK_NO_CANCEL).bulkValue()
            val lookup = LocalCatalogIdentities(db)
            // Before simulating legacy rows, direct+WHOLE membership must deduplicate too.
            assertEquals(listOf("a", "b"), lookup.printings(OracleId(ProviderId("scryfall"), "o"), PageRequest(10), BULK_NO_CANCEL).bulkValue().items.map { it.id.raw })
            db.exec("DELETE FROM printing_oracle_membership")
            val first = lookup.printings(OracleId(ProviderId("scryfall"), "o"), PageRequest(1), BULK_NO_CANCEL).bulkValue()
            assertEquals("a", first.items.single().id.raw)
            assertEquals("b", lookup.printings(OracleId(ProviderId("scryfall"), "o"), PageRequest(1, first.nextCursor), BULK_NO_CANCEL).bulkValue().items.single().id.raw)
            assertEquals("a", lookup.printing(CardId(ProviderId("scryfall"), "a"), BULK_NO_CANCEL).bulkValue().id.raw)
            assertTrue(db.rows("SELECT * FROM printing_oracle_membership").isEmpty())
            db.exec("PRAGMA foreign_keys=OFF"); db.exec("DELETE FROM oracle_card")
            assertEquals(FailureCode.CORRUPT_DATA, (lookup.printing(CardId(ProviderId("scryfall"), "a"), BULK_NO_CANCEL) as Outcome.Failure).error.code)
        }
    }

    @Test fun missingFullProofMembershipCannotHideBehindDirectFallback() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            db.install(listOf(bulkRecord("a", "o")))
            db.exec("DELETE FROM printing_oracle_membership")
            val lookup = LocalCatalogIdentities(db)
            assertEquals(FailureCode.CORRUPT_DATA, (lookup.printing(CardId(ProviderId("scryfall"), "a"), BULK_NO_CANCEL) as Outcome.Failure).error.code)
            assertEquals(FailureCode.CORRUPT_DATA, (lookup.printings(OracleId(ProviderId("scryfall"), "o"), PageRequest(10), BULK_NO_CANCEL) as Outcome.Failure).error.code)
        }
    }

    @Test fun failedTinyMembershipWriteNeverActivatesPartialDataOrDamagesPriorActive() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val active = db.install(listOf(bulkRecord("old-p", "old-o")))
            db.beforeWrite = { if (it.statement.startsWith("INSERT INTO printing_oracle_membership")) error("Injected membership write failure") }
            val clock = object : Clock { override fun utcNow() = UtcTimestamp(3); override fun monotonicNow() = Duration.ZERO }
            var sequence = 0
            val result = LocalCatalog(db, clock, object : IdSource { override fun nextId() = "failed-selected-${++sequence}" })
                .`import`(bulkSelected().legacySource(), bulkChunks("[${bulkRecord("new-p", "new-o")}]") , {}, BULK_NO_CANCEL)
            assertTrue(result is Outcome.Failure)
            db.beforeWrite = null
            assertEquals(active.value, db.rows("SELECT generation_id FROM catalog_active").single().bulkText("generation_id"))
            assertEquals("old-p", LocalCatalogIdentities(db).printing(CardId(ProviderId("scryfall"), "old-p"), BULK_NO_CANCEL).bulkValue().id.raw)
            assertTrue(db.rows("SELECT printing_id FROM printing WHERE printing_id='new-p'").isEmpty())
            assertEquals(1, db.rows("SELECT generation_id FROM catalog_generation").size)
        }
    }
    @Test fun reversibleHasNoWholeOracleAndDistinctPortablePageCursor() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            db.install(listOf(bulkReversible("a","o","o"),bulkRecord("b","o"),bulkRecord("c","o")))
            val lookup=LocalCatalogIdentities(db); val oracle=OracleId(ProviderId("scryfall"),"o")
            val printing=lookup.printing(CardId(ProviderId("scryfall"),"a"),BULK_NO_CANCEL).bulkValue()
            assertTrue(printing.oracleId is Knowledge.Unknown); assertEquals(2,printing.faceAssets.size)
            val first=lookup.printings(oracle,PageRequest(1),BULK_NO_CANCEL).bulkValue(); assertEquals("a",first.items.single().id.raw)
            val second=LocalCatalogIdentities(db).printings(oracle,PageRequest(100,first.nextCursor),BULK_NO_CANCEL).bulkValue()
            assertEquals(listOf("b","c"),second.items.map { it.id.raw }); assertNull(second.nextCursor)
            assertTrue(lookup.oracle(oracle,BULK_NO_CANCEL) is Outcome.Success)
            assertTrue(lookup.oracle(OracleId(ProviderId("other"),"o"),BULK_NO_CANCEL) is Outcome.Unavailable)
            assertTrue(lookup.printings(OracleId(ProviderId("scryfall"),"different"),PageRequest(1,first.nextCursor),BULK_NO_CANCEL) is Outcome.Failure)
            db.install(listOf(bulkRecord("new","o")))
            val stale=lookup.printings(oracle,PageRequest(1,first.nextCursor),BULK_NO_CANCEL) as Outcome.Failure
            assertEquals(FailureCode.STALE_GENERATION,stale.error.code)
        }
    }
    @Test fun reversibleSourceSlotsDoNotInventAdventureCanonicalFaceIndices() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            val adventure=bulkRecord("adventure","ao").replace("\"layout\":\"normal\"", "\"layout\":\"adventure\",\"card_faces\":[{\"object\":\"card_face\",\"name\":\"Creature\"},{\"object\":\"card_face\",\"name\":\"Spell\"}]")
            db.install(listOf(bulkReversible("rev","ao","other"),bulkRecord("ordinary","other"),adventure))
            val printing=LocalCatalogIdentities(db).printing(CardId(ProviderId("scryfall"),"rev"),BULK_NO_CANCEL).bulkValue()
            assertEquals(listOf(0,1),printing.faceAssets.map { it.identity.index })
            assertTrue(printing.faceAssets.all { it.relationships.single().relatedFace is Knowledge.Unknown })
            assertEquals(2,LocalCatalogIdentities(db).oracle(OracleId(ProviderId("scryfall"),"ao"),BULK_NO_CANCEL).bulkValue().faces.size)
            assertEquals(2L,db.count("card_face"))
        }
    }

    @Test fun corruptPayloadProjectionMembershipAndMetadataAreTypedFailures() = runBlocking {
        for(sql in listOf("UPDATE printing SET metadata_payload='private-secret'", "UPDATE printing SET collector_number='bad'", "UPDATE printing_oracle_membership SET source_kind='FACE'", "UPDATE printing_face_asset SET asset_payload='{}'", "UPDATE catalog_generation SET input_kind='VERIFIED_ARTIFACT'", "UPDATE catalog_generation SET content_sha256='abc'")) BulkSqlTestDatabase().use { db ->
            db.install(listOf(bulkRecord("a","o"))); db.exec(sql)
            val r=LocalCatalogIdentities(db).printing(CardId(ProviderId("scryfall"),"a"),BULK_NO_CANCEL) as Outcome.Failure
            assertEquals(FailureCode.CORRUPT_DATA,r.error.code); assertFalse(r.error.message.contains("private-secret"))
        }
        BulkSqlTestDatabase().use { db -> db.install(listOf(bulkRecord("a","o"))); db.exec("UPDATE oracle_card SET canonical_digest='bad'"); assertEquals(FailureCode.CORRUPT_DATA,(LocalCatalogIdentities(db).oracle(OracleId(ProviderId("scryfall"),"o"),BULK_NO_CANCEL) as Outcome.Failure).error.code) }
    }
    @Test fun proofEnvelopeRejectsOriginConsentTypeVersionsAndPrivateKeysWithoutLeak() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            db.install(listOf(bulkRecord("a","o")))
            val payload=db.rows("SELECT source_payload FROM catalog_generation").single().bulkText("source_payload")
            val mutations=listOf<(com.google.gson.JsonObject)->Unit>(
                { it.addProperty("version",2) }, { it.remove("consentId") }, { it.addProperty("consentId","unbound-consent") },
                { it.addProperty("decodedBytes",0) }, { it.addProperty("rawBytes",2147483649L) }, { it.addProperty("memberships",1.5) },
                { it.addProperty("private-secret",true) }, { it.addProperty("rawHash","abc") })
            for(mutate in mutations) {
                val v=com.google.gson.JsonParser.parseString(payload).asJsonObject; mutate(v)
                try { BulkSourceEnvelope.decode(v.toString()); fail("Invalid proof accepted") } catch(e: IllegalArgumentException) { assertEquals("Invalid catalog source proof",e.message); assertNull(e.cause) }
            }
            try { BulkSourceEnvelope.decode("{\"private-secret\":1,\"private-secret\":2}"); fail() } catch(e: IllegalArgumentException) { assertFalse(e.message!!.contains("private-secret")); assertNull(e.cause) }
        }
    }

    @Test fun outOfRangeSqlTimestampCannotWrapIntoValidProvenance() = runBlocking {
        for(field in listOf("observed_at_nano","activated_at_nano","source_date_nano")) for(nano in listOf(-1L,1000000000L,4294967296L,Long.MAX_VALUE)) BulkSqlTestDatabase().use { db ->
            db.install(listOf(bulkRecord("a","o"))); db.exec("UPDATE catalog_generation SET $field=$nano")
            val result=LocalCatalogIdentities(db).printing(CardId(ProviderId("scryfall"),"a"),BULK_NO_CANCEL)
            assertTrue(result is Outcome.Failure); assertEquals(FailureCode.CORRUPT_DATA,(result as Outcome.Failure).error.code)
        }
    }

    @Test fun fullSourceProofAndAcquisitionVersionSurviveWithoutArtifact() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            val raw=bulkRecord("a","o"); val original=bulkAcquired(raw); val artifact=original.verifiedArtifact!!.copy(provenance=original.verifiedArtifact!!.provenance.copy(sourceVersion=Knowledge.Known("source-v")))
            val source=original.copy(verifiedArtifact=artifact)
            db.importer(binding=CatalogVerifiedInputBinding { _,_,consume -> consume(bulkChunks(raw),EntityId("consent")) }).`import`(source,bulkChunks("wrong caller data"),{},BULK_NO_CANCEL).bulkValue()
            val printing=LocalCatalogIdentities(db).printing(CardId(ProviderId("scryfall"),"a"),BULK_NO_CANCEL).bulkValue()
            assertEquals(Knowledge.Known("source-v"),printing.provenance.version)
            val stored=BulkSourceEnvelope.decode(db.rows("SELECT source_payload FROM catalog_generation").single().bulkText("source_payload"))
            assertEquals(source,stored.source); assertEquals(EntityId("consent"),stored.consentId); assertEquals(artifact.bytes,stored.proof.rawBytes); assertEquals(artifact.sha256,stored.proof.rawHash)
        }
    }
}
