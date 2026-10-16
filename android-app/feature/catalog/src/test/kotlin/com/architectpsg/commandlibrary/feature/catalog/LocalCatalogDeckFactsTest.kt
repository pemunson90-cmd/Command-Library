package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Synthetic retained source objects imported into actual bundled SQLite; no acquisition or live-source claim. */
class LocalCatalogDeckFactsTest {
    private val source = ProviderId("scryfall")
    private val oracle = OracleId(source, "o")
    private fun snapshot(generation: GenerationId) = SnapshotReference(source, generation, null, LanguageTag.ENGLISH)
    private fun record(id: String = "p", extra: String = "", oracleId: String = "o"): String =
        bulkRecord(id, oracleId).dropLast(1) + extra + "}"
    private fun part(id: String, component: String, extra: String = "") =
        """{"object":"related_card","id":"$id","component":"$component"$extra}"""
    private fun failed(result: Outcome<*>, code: FailureCode = FailureCode.CORRUPT_DATA) {
        assertTrue("Expected typed failure", result is Outcome.Failure)
        assertEquals(code, (result as Outcome.Failure).error.code)
    }
    private fun unavailable(result: Outcome<*>, reason: UnavailableReason) {
        assertTrue("Expected typed unavailability", result is Outcome.Unavailable)
        assertEquals(reason, (result as Outcome.Unavailable).reason)
    }

    @Test fun selectedPrintingExtrasDifferFromFirstOracleWithoutCrossPrintingMergeOrWrites() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val first = record("first", """, "produced_mana":["C"],"all_parts":[]""")
            val second = record("second", """, "produced_mana":["W","U"],"all_parts":[${part("t", "token", ",\"name\":\"Synthetic token\",\"type_line\":\"Token Creature\"")}]""")
            val generation = db.install(listOf(first, second))
            var writes = 0
            db.beforeWrite = { writes++ }
            val lookup = LocalCatalogDeckFacts(db)
            val whole = lookup.facts(oracle, null, snapshot(generation), BULK_NO_CANCEL).bulkValue()
            val selected = lookup.facts(oracle, CardId(source, "second"), snapshot(generation), BULK_NO_CANCEL).bulkValue()
            assertNull(whole.printingId)
            assertEquals(Knowledge.Known(SnapshotList.of("C")), whole.reportedProduction)
            assertEquals(Knowledge.Known(SnapshotList.empty<ReportedCardPart>()), whole.reportedParts)
            assertEquals(Knowledge.Known(SnapshotList.of("W", "U")), selected.reportedProduction)
            assertEquals(CardId(source, "second"), selected.printingId)
            assertEquals(snapshot(generation), selected.snapshot)
            assertEquals(whole.card, selected.card)
            val token = (selected.reportedParts as Knowledge.Known).value.single()
            assertEquals("token", token.component)
            assertEquals(Knowledge.Known("Synthetic token"), token.name)
            assertEquals(Knowledge.Known("Token Creature"), token.typeLine)
            assertEquals(0, writes)
            assertEquals(first, db.rows("SELECT record_payload FROM oracle_card").single().bulkText("record_payload"))
        }
    }

    @Test fun exactComponentsAndUnknownOptionalMetadataNeverFlattenComboPiecesIntoTokens() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val parts = listOf(part("a", "token"), part("b", "combo_piece", ",\"name\":null,\"type_line\":null"),
                part("c", "meld_part"), part("d", "meld_result"))
            val g = db.install(listOf(record(extra = ",\"produced_mana\":[\"C\"],\"all_parts\":[${parts.joinToString(",")}]")))
            val facts = LocalCatalogDeckFacts(db).facts(oracle, CardId(source, "p"), snapshot(g), BULK_NO_CANCEL).bulkValue()
            val reported = (facts.reportedParts as Knowledge.Known).value
            assertEquals(listOf("token", "combo_piece", "meld_part", "meld_result"), reported.map { it.component })
            assertEquals(listOf("a", "b", "c", "d"), reported.map { it.printingId.raw })
            assertTrue(reported.all { it.name is Knowledge.Unknown && it.typeLine is Knowledge.Unknown })
            assertEquals(Knowledge.Known(SnapshotList.of("C")), facts.reportedProduction)
        }
    }

    @Test fun absentNullAndKnownEmptyAreDistinctWithoutConvenientEmptyFallback() = runBlocking {
        for (extra in listOf("", ",\"produced_mana\":null,\"all_parts\":null", ",\"produced_mana\":[],\"all_parts\":[]")) {
            SearchProjectionSqlDatabase().use { db ->
                val g = db.install(listOf(record(extra = extra)))
                val facts = LocalCatalogDeckFacts(db).facts(oracle, null, snapshot(g), BULK_NO_CANCEL).bulkValue()
                if (extra.endsWith("[]")) {
                    assertEquals(Knowledge.Known(SnapshotList.empty<String>()), facts.reportedProduction)
                    assertEquals(Knowledge.Known(SnapshotList.empty<ReportedCardPart>()), facts.reportedParts)
                } else {
                    assertTrue(facts.reportedProduction is Knowledge.Unknown)
                    assertTrue(facts.reportedParts is Knowledge.Unknown)
                }
            }
        }
    }

    @Test fun selectedPrintingMustHaveMatchingUniqueWholeOracleAndSource() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val g = db.install(listOf(record(), record("other", oracleId = "other-o"), bulkReversible("rev", "o", "other-o")))
            val lookup = LocalCatalogDeckFacts(db)
            failed(lookup.facts(oracle, CardId(source, "other"), snapshot(g), BULK_NO_CANCEL))
            unavailable(lookup.facts(oracle, CardId(source, "rev"), snapshot(g), BULK_NO_CANCEL), UnavailableReason.UNSUPPORTED)
            unavailable(lookup.facts(oracle, CardId(ProviderId("other"), "p"), snapshot(g), BULK_NO_CANCEL), UnavailableReason.UNSUPPORTED)
        }
    }

    @Test fun malformedAndFutureFieldsUseDifferentTypedOutcomesAndNoPayloadInErrors() = runBlocking {
        val corrupt = listOf(
            ",\"produced_mana\":12", ",\"produced_mana\":[1]", ",\"produced_mana\":[\"W\",\"W\"]",
            ",\"produced_mana\":[\"\"]", ",\"all_parts\":false", ",\"all_parts\":[null]",
            ",\"all_parts\":[${part("a", "token", ",\"name\":5")}]",
            ",\"all_parts\":[${part("a", "token", ",\"type_line\":false")}]",
            ",\"all_parts\":[{\"object\":\"related_card\",\"id\":5,\"component\":\"token\"}]",
            ",\"all_parts\":[${part("a", "token")},${part("a", "combo_piece")}]",
            ",\"all_parts\":[${part("a", "token", ",\"name\":\"one\"")},${part("a", "token", ",\"name\":\"two\"")}]",
            ",\"all_parts\":[${part("a".repeat(129), "token")}]",
            ",\"all_parts\":[${List(129) { part("part$it", "token") }.joinToString(",")}]")
        val future = listOf(",\"produced_mana\":[\"P\"]", ",\"all_parts\":[${part("a", "future_component")}]")
        for ((extra, reason) in corrupt.map { it to false } + future.map { it to true }) {
            SearchProjectionSqlDatabase().use { db ->
                val g = db.install(listOf(record()))
                // The old mapper intentionally cannot admit future component kinds; mutate retained raw only.
                replacePrintingRaw(db, record(extra = extra))
                val result = LocalCatalogDeckFacts(db).facts(oracle, CardId(source, "p"), snapshot(g), BULK_NO_CANCEL)
                if (reason) unavailable(result, UnavailableReason.UNSUPPORTED) else failed(result)
                if (result is Outcome.Failure) assertFalse(result.error.message.contains("future_component"))
            }
        }
    }

    @Test fun exactDuplicatePartsDeduplicateButRetainSuppliedEmptyStrings() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val part = part("a", "token", ",\"name\":\"\",\"type_line\":\"\"")
            val g = db.install(listOf(record(extra = ",\"all_parts\":[$part,$part]")))
            val facts = LocalCatalogDeckFacts(db).facts(oracle, null, snapshot(g), BULK_NO_CANCEL).bulkValue()
            val parts = (facts.reportedParts as Knowledge.Known).value
            assertEquals(1, parts.size)
            assertEquals(Knowledge.Known(""), parts.single().name)
            assertEquals(Knowledge.Known(""), parts.single().typeLine)
        }
    }

    @Test fun actualValidatedReadersRejectProjectionAndRawTampering() = runBlocking {
        for (sql in listOf("UPDATE oracle_card SET canonical_digest='bad'", "UPDATE oracle_card SET name='private-marker'",
            "UPDATE printing SET collector_number='bad'", "UPDATE printing_oracle_membership SET source_kind='FACE'")) {
            SearchProjectionSqlDatabase().use { db ->
                val g = db.install(listOf(record()))
                db.exec(sql)
                val result = LocalCatalogDeckFacts(db).facts(oracle, CardId(source, "p"), snapshot(g), BULK_NO_CANCEL)
                failed(result)
                assertFalse((result as Outcome.Failure).error.message.contains("private-marker"))
            }
        }
    }

    @Test fun importedOverBudgetOracleOrSelectedPrintingFailsBeforeLegacyPerFaceValidation() = runBlocking {
        val faces = List(DeckStatisticsLimits.MAX_FACES + 1) {
            """{"object":"card_face","name":"Synthetic face $it"}"""
        }.joinToString(",")
        for (selected in listOf(false, true)) SearchProjectionSqlDatabase().use { db ->
            val oversized = record("many-faces", ",\"card_faces\":[$faces]", if (selected) "other-o" else "o")
            val generation = db.install(if (selected) listOf(record(), oversized) else listOf(oversized))
            // This is a genuine importer-retained 17-face object, not invented projection rows.
            assertEquals(17L, db.rows("SELECT count(*) AS n FROM card_face").single().bulkLong("n"))
            var legacyReads = 0
            var perFaceReads = 0
            db.beforeQuery = { request ->
                if (request.statement.startsWith("SELECT * FROM oracle_card") ||
                    request.statement.startsWith("SELECT * FROM printing WHERE")) legacyReads++
                if (request.statement.contains("FROM card_face") ||
                    request.statement.contains("FROM printing_face_asset")) perFaceReads++
            }
            failed(LocalCatalogDeckFacts(db).facts(oracle, if (selected) CardId(source, "many-faces") else null,
                snapshot(generation), BULK_NO_CANCEL))
            assertEquals(0, legacyReads)
            assertEquals(0, perFaceReads)
        }
    }

    @Test fun strictParserAndRawByteLimitApplyBeforeExistingMapperAndPreserveCancellation() = runBlocking {
        val malformed = listOf(record().dropLast(1) + ",\"produced_mana\":[],\"produced_mana\":[]}",
            record().dropLast(1) + ",\"extra\":\"\\uD800\"}", record().dropLast(1) + ",\"extra\":" + "[".repeat(34) + "0" + "]".repeat(34) + "}")
        for (raw in malformed) SearchProjectionSqlDatabase().use { db ->
            val g = db.install(listOf(record()))
            replacePrintingRaw(db, raw)
            failed(LocalCatalogDeckFacts(db).facts(oracle, CardId(source, "p"), snapshot(g), BULK_NO_CANCEL))
        }
        SearchProjectionSqlDatabase().use { db ->
            val g = db.install(listOf(record()))
            db.exec("UPDATE printing SET metadata_payload=printf('%.*c',${DeckStatisticsLimits.MAX_RAW_RECORD_BYTES + 1},'x')")
            var fetched = false
            db.beforeQuery = { if (it.statement.startsWith("SELECT metadata_payload AS raw")) fetched = true }
            failed(LocalCatalogDeckFacts(db).facts(oracle, CardId(source, "p"), snapshot(g), BULK_NO_CANCEL))
            assertFalse(fetched) // SQL byte-size guard precedes fetching the oversized text.
        }
    }

    @Test fun snapshotAdmissionMissingIdentityAndGenerationRacesNeverReturnMixedFacts() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val lookup = LocalCatalogDeckFacts(db)
            unavailable(lookup.facts(oracle, null, snapshot(GenerationId("absent")), BULK_NO_CANCEL), UnavailableReason.NOT_INSTALLED)
            val g = db.install(listOf(record()))
            unavailable(lookup.facts(OracleId(source, "missing"), null, snapshot(g), BULK_NO_CANCEL), UnavailableReason.NOT_FOUND)
            unavailable(lookup.facts(oracle, CardId(source, "missing"), snapshot(g), BULK_NO_CANCEL), UnavailableReason.NOT_FOUND)
            failed(lookup.facts(oracle, null, snapshot(g).copy(generation = null), BULK_NO_CANCEL), FailureCode.INVALID_INPUT)
            failed(lookup.facts(oracle, null, snapshot(g).copy(revision = Revision(1)), BULK_NO_CANCEL), FailureCode.INVALID_INPUT)
            failed(lookup.facts(oracle, null, snapshot(g).copy(language = LanguageTag("fr")), BULK_NO_CANCEL), FailureCode.INVALID_INPUT)
            failed(lookup.facts(OracleId(source, "o".repeat(129)), null, snapshot(g), BULK_NO_CANCEL), FailureCode.INVALID_INPUT)
            failed(lookup.facts(oracle, null, snapshot(GenerationId("stale")), BULK_NO_CANCEL), FailureCode.STALE_GENERATION)
        }
        for (changeAt in listOf(2, 3)) SearchProjectionSqlDatabase().use { db ->
            val g = db.install(listOf(record()))
            var activeReads = 0
            db.beforeQuery = { request ->
                if (request.statement.startsWith("SELECT g.* FROM catalog_active") && ++activeReads == changeAt)
                    db.exec("DELETE FROM catalog_active")
            }
            failed(LocalCatalogDeckFacts(db).facts(oracle, null, snapshot(g), BULK_NO_CANCEL), FailureCode.STALE_GENERATION)
        }
    }

    @Test fun cancellationBeforeSqlAndBetweenFieldsPropagatesInsteadOfBecomingUnknown() = runBlocking {
        SearchProjectionSqlDatabase().use { db ->
            val g = db.install(listOf(record()))
            val marker = CancellationException("Synthetic cancellation")
            var sql = 0
            db.beforeQuery = { sql++ }
            val cancelled = object : CancelToken {
                override val isCancelled = true
                override fun throwIfCancelled(): Unit = throw marker
            }
            try { LocalCatalogDeckFacts(db).facts(oracle, null, snapshot(g), cancelled); fail("Cancellation swallowed") }
            catch (actual: CancellationException) { assertSame(marker, actual) }
            assertEquals(0, sql)
            var checks = 0
            val fields = object : CancelToken {
                override val isCancelled get() = checks >= 4
                override fun throwIfCancelled() { if (++checks >= 4) throw marker }
            }
            val objectValue = CatalogDeckFactsFields.parse(record(extra = ",\"produced_mana\":[\"W\",\"U\",\"C\"]"), BULK_NO_CANCEL)
            try { CatalogDeckFactsFields.production(objectValue, fields); fail("Cancellation swallowed") }
            catch (actual: CancellationException) { assertSame(marker, actual) }
        }
    }

    private fun replacePrintingRaw(db: SearchProjectionSqlDatabase, raw: String) {
        val quoted = raw.replace("'", "''")
        db.exec("UPDATE printing SET metadata_payload='$quoted'")
        db.exec("UPDATE printing_face_asset SET asset_payload='$quoted'")
    }
}
