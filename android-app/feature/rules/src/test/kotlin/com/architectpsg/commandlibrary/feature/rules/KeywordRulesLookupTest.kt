package com.architectpsg.commandlibrary.feature.rules

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.rules.parser.SyntheticRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration

/** Real import/SQLite/transaction/reader. Invented text, not official keyword/source authority. */
class KeywordRulesLookupTest {
    private val unknown = Knowledge.Unknown("Synthetic source facts", SnapshotList.empty())
    private val token = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private var serial = 0
    private fun reader(db: Database) = LocalRules(db, object : Clock {
        override fun utcNow() = UtcTimestamp(100)
        override fun monotonicNow() = Duration.ZERO
    }, object : IdSource { override fun nextId() = "synthetic-${++serial}" })
    private val unique = SyntheticRules.CORPUS.replace("Synthetic Term\nA second", "Other Term\nA second")
    private suspend fun install(reader: LocalRules, text: String = unique): GenerationId {
        val source = LocalRulesImportSource(ResourceUri("content://synthetic/selected"), "Synthetic rules", ProviderId("synthetic"), LanguageTag.ENGLISH,
            unknown, unknown, unknown, Knowledge.Known(ResourceUri("https://example.invalid/synthetic-rules")), unknown,
            Knowledge.Absent("No expected hash"), Knowledge.Known(text.toByteArray(Charsets.UTF_8).size.toLong()))
        return reader.`import`(source, { SyntheticRules.chunks(text) }, {}, token).requireSuccess().corpus.id
    }
    @Test fun actualExactTitleProducesOnlyAValidatedStoredTargetAndPreservesOriginalKeyword() = runBlocking {
        RulesSqlTestDatabase().use { db ->
            val reader = reader(db); val corpus = install(reader, unique.replace("Synthetic Term", "Café Term"))
            val keyword = "  CAFE\u0301 TERM  "
            val link = reader.lookupKeyword(keyword, null, token).requireSuccess()
            assertEquals(keyword, link.keyword); assertEquals(corpus, link.corpusId)
            val entry = reader.entry((link.target as Knowledge.Known).value, corpus, token).requireSuccess()
            assertEquals(Knowledge.Known("Café Term"), entry.title); assertEquals(RuleEntryKind.GLOSSARY, entry.kind)
            assertEquals(entry.provenance.source, link.evidence.single().source)
            assertEquals(entry.provenance.fetchedAt, link.evidence.single().recordedAt)
            assertTrue(reader.lookupKeyword("Café", null, token).requireSuccess().target is Knowledge.Absent)
        }
    }
    @Test fun normalizedDuplicateTitlesCannotIssueOneCanonicalTarget() = runBlocking {
        RulesSqlTestDatabase().use { db ->
            val reader = reader(db); install(reader, SyntheticRules.CORPUS)
            assertTrue(reader.lookupKeyword("synthetic term", null, token).requireSuccess().target is Knowledge.Unknown)
        }
    }
    @Test fun retiredCorpusRemainsExplicitlyPinnedAndMissingOrAbsentCorpusIsTyped() = runBlocking {
        RulesSqlTestDatabase().use { db ->
            val reader = reader(db)
            assertEquals(UnavailableReason.NOT_INSTALLED, (reader.lookupKeyword("Synthetic Term", null, token) as Outcome.Unavailable).reason)
            val old = install(reader)
            val newer = install(reader, unique.replace("Synthetic Term", "Replacement Term"))
            assertEquals(old, reader.lookupKeyword("Synthetic Term", old, token).requireSuccess().corpusId)
            assertTrue(reader.lookupKeyword("Synthetic Term", old, token).requireSuccess().target is Knowledge.Known)
            assertEquals(newer, reader.lookupKeyword("Replacement Term", null, token).requireSuccess().corpusId)
            assertEquals(FailureCode.STALE_GENERATION, (reader.lookupKeyword("Synthetic Term", GenerationId("missing"), token) as Outcome.Failure).error.code)
        }
    }
    @Test fun nullOrOversizedStoredProjectionsFailClosedBeforeAuthorityOrLargeMaterialization() = runBlocking {
        for (column in listOf("source_payload", "provenance_payload", "upstream_version")) RulesSqlTestDatabase().use { db ->
            val reader = reader(db); install(reader)
            db.exec("UPDATE rules_corpus SET $column=printf('%02100000d',1)")
            db.maxTextBytesRead = 0
            assertEquals(FailureCode.CORRUPT_DATA, (reader.lookupKeyword("Synthetic Term", null, token) as Outcome.Failure).error.code)
            assertTrue(db.maxTextBytesRead < 2 * 1024 * 1024)
        }
        RulesSqlTestDatabase().use { db ->
            val reader = reader(db); install(reader)
            db.exec("UPDATE rule_entry SET title=NULL WHERE kind='GLOSSARY'")
            assertEquals(FailureCode.CORRUPT_DATA, (reader.lookupKeyword("missing", null, token) as Outcome.Failure).error.code)
        }
        RulesSqlTestDatabase().use { db ->
            val reader = reader(db); install(reader)
            db.exec("UPDATE rule_entry SET title=printf('%05000d',1) WHERE kind='GLOSSARY'")
            assertEquals(UnavailableReason.SOURCE_UNAVAILABLE, (reader.lookupKeyword("missing", null, token) as Outcome.Unavailable).reason)
        }
    }
    @Test fun corruptMatchedEntryCannotIssueAStoredIdAndNegativeOrdinalCannotBeSkipped() = runBlocking {
        RulesSqlTestDatabase().use { db ->
            val reader = reader(db); install(reader)
            db.exec("UPDATE rule_entry SET number=printf('%02100000d',1) WHERE kind='GLOSSARY' AND title='Synthetic Term'")
            db.maxTextBytesRead = 0
            assertEquals(FailureCode.CORRUPT_DATA, (reader.lookupKeyword("Synthetic Term", null, token) as Outcome.Failure).error.code)
            assertTrue(db.maxTextBytesRead < 2 * 1024 * 1024)
        }
        RulesSqlTestDatabase().use { db ->
            val reader = reader(db); install(reader)
            db.exec("UPDATE rule_entry SET entry_payload='{}' WHERE kind='GLOSSARY' AND title='Synthetic Term'")
            assertEquals(FailureCode.CORRUPT_DATA, (reader.lookupKeyword("Synthetic Term", null, token) as Outcome.Failure).error.code)
        }
        RulesSqlTestDatabase().use { db ->
            val reader = reader(db); install(reader)
            db.exec("UPDATE rule_entry SET ordinal=-1 WHERE title='Synthetic Term'")
            assertEquals(FailureCode.CORRUPT_DATA, (reader.lookupKeyword("missing", null, token) as Outcome.Failure).error.code)
        }
    }
    @Test fun cancellationAfterMetadataReadRollsBackAndLeavesCorpusUsable() = runBlocking {
        RulesSqlTestDatabase().use { db ->
            val reader = reader(db); val corpus = install(reader)
            var cancelled = false
            val caller = object : CancelToken { override val isCancelled get() = cancelled; override fun throwIfCancelled() { if (cancelled) throw CancellationException("Synthetic caller cancellation") } }
            db.afterQuery = { if (it.statement.startsWith("SELECT ordinal")) cancelled = true }
            try { reader.lookupKeyword("Synthetic Term", null, caller); fail("Expected cancellation") } catch (_: CancellationException) { }
            db.afterQuery = null
            assertEquals(corpus, reader.lookupKeyword("Synthetic Term", null, token).requireSuccess().corpusId)
        }
    }
    @Test fun anActualStoredGlossaryOrdinalZeroIsIncludedWithoutAPageSentinel() = runBlocking {
        RulesSqlTestDatabase().use { db ->
            val reader = reader(db); val corpus = install(reader)
            suspend fun move(old: Long, next: Long) {
                val row = db.query(rulesSql("SELECT entry_payload FROM rule_entry WHERE ordinal=:ordinal", 1, "ordinal" to old), token).requireSuccess().single()
                val payload = RulesCodec.decode(row.text("entry_payload")).apply { addProperty("ordinal", next) }
                val encoded = RulesCodec.encode(payload).replace("'", "''")
                db.exec("UPDATE rule_entry SET ordinal=$next,entry_payload='$encoded' WHERE ordinal=$old")
            }
            val ordinal = db.query(rulesSql("SELECT ordinal FROM rule_entry WHERE title='Synthetic Term'", 1), token).requireSuccess().single().number("ordinal")
            move(0, 10000); move(ordinal, 0)
            val link = reader.lookupKeyword("Synthetic Term", corpus, token).requireSuccess()
            assertEquals(0L, reader.entry((link.target as Knowledge.Known).value, corpus, token).requireSuccess().ordinal)
        }
    }
    @Test fun incompleteMetadataBudgetCannotReturnAnEarlierApparentlyUniqueMatch() = runBlocking {
        RulesSqlTestDatabase().use { db ->
            val reader = reader(db); install(reader)
            db.exec("""WITH RECURSIVE n(i) AS (VALUES(1) UNION ALL SELECT i+1 FROM n WHERE i<2100)
                INSERT INTO rule_entry(corpus_id,provider,local_id,kind,number,title,text,ordinal,entry_payload)
                SELECT e.corpus_id,e.provider,'budget-'||n.i,'GLOSSARY',NULL,printf('%04000d',n.i),'Synthetic oversized index fixture',10000+n.i,e.entry_payload
                FROM rule_entry e CROSS JOIN n WHERE e.title='Synthetic Term'""")
            assertEquals(UnavailableReason.SOURCE_UNAVAILABLE, (reader.lookupKeyword("Synthetic Term", null, token) as Outcome.Unavailable).reason)
        }
    }
}
