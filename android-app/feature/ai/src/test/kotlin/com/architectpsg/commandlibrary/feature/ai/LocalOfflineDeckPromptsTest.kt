package com.architectpsg.commandlibrary.feature.ai

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration

class LocalOfflineDeckPromptsTest {
    private val source = ProviderId("scryfall")
    private val snapshot = SnapshotReference(source, GenerationId("g1"), null, LanguageTag.ENGLISH)
    private val unknown = Knowledge.Unknown("private backend diagnostic", SnapshotList.empty())
    private val timestamp = UtcTimestamp(123)
    private val clock = object : Clock { override fun utcNow() = timestamp; override fun monotonicNow() = Duration.ZERO }
    private fun card(name: String = "A-Name // Back", id: String = "oracle") = CardRecord(OracleId(source, id), name,
        Knowledge.Known("Original rules"), unknown, unknown, unknown, unknown, unknown,
        CardStats(unknown, unknown, unknown, unknown), unknown, unknown, unknown, "do not export search index", LanguageTag.ENGLISH,
        SnapshotList.empty(), SnapshotList.empty(), Provenance(source, ResourceUri("private://never-export"), Knowledge.Known("v1"), unknown,
            timestamp, unknown, snapshot.generation, null, null), null)
    private fun deck(count: Int = 1): DeckSnapshot {
        val identity = CardIdentity(Knowledge.Known(OracleId(source, "oracle")), Knowledge.Known(CardId(ProviderId("foreign"), "PRINT")),
            ProviderIdentity(ProviderId("foreign"), "raw-kind", "RAW"), Knowledge.Known("A-Name // Back"), LanguageTag("ja"))
        return DeckSnapshot(DeckId(ProviderId("local"), "deck"), "Deck", unknown, "PRIVATE DESCRIPTION", unknown,
            SnapshotList.empty(), timestamp, timestamp, Revision(3), SnapshotList.copyOf((1..count).map {
                DeckEntry(EntityId("entry$it"), identity, it + 2, BoardRole.entries[it % BoardRole.entries.size], unknown,
                    "PRIVATE NOTE", SnapshotList.empty(), SnapshotList.empty())
            }), unknown, null, SnapshotList.empty(), SnapshotList.empty(), "PRIVATE PRIMER")
    }
    private fun request(deck: DeckSnapshot = deck(), notes: Boolean = false, mode: OfflineLabMode = OfflineLabMode.CHAT) =
        OfflinePromptRequest(deck, mode, RoastIntensity.SPICY, "my {{request}}", "budget constraint", notes, snapshot, null)
    private class Query(var answer: Outcome<Page<CardRecord>>) : LocalCardQuery {
        val calls = mutableListOf<Triple<QueryAst, SearchContext, PageRequest>>()
        override suspend fun local(query: QueryAst, context: SearchContext, page: PageRequest, cancel: CancelToken): Outcome<Page<CardRecord>> {
            cancel.throwIfCancelled(); calls.add(Triple(query, context, page)); return answer
        }
    }
    private fun engine(query: LocalCardQuery) = LocalOfflineDeckPrompts(query, clock) { _, cancel -> cancel.throwIfCancelled(); Outcome.Success(Unit) }
    private fun query(cards: List<CardRecord> = listOf(card()), next: String? = null, frame: SnapshotReference = snapshot) =
        Query(Outcome.Success(Page(SnapshotList.copyOf(cards), next, frame)))

    @Test fun fullIdentityQuantityPrivacyAndDeduplicatedPinnedEnrichment() = runTest {
        val query = query()
        val result = (engine(query).render(request(deck(7)), noCancel) as Outcome.Success).value
        assertEquals(7, result.oracleContextsIncluded)
        assertEquals(0, result.oracleContextsOmitted)
        assertEquals(1, query.calls.size)
        assertEquals(QueryAst.Text(SearchTextField.NAME, "A-Name // Back", true), query.calls.single().first)
        assertEquals(FaceMatchPolicy.CANONICAL, query.calls.single().second.facePolicy)
        assertEquals(listOf(snapshot), query.calls.single().second.snapshots.toList())
        assertEquals(PageRequest(2), query.calls.single().third)
        val text = result.artifact.rendered
        assertTrue(text.contains("PRINT") && text.contains("RAW") && text.contains("\"quantity\":9"))
        assertTrue(text.contains("my {{request}}") && text.contains("SPICY"))
        assertFalse(text.contains("PRIVATE")); assertFalse(text.contains("private://")); assertFalse(text.contains("private backend"))
        assertEquals(timestamp, result.artifact.createdAt)
        val privateText = (engine(query).render(request(notes = true), noCancel) as Outcome.Success).value.artifact.rendered
        assertTrue(privateText.contains("PRIVATE NOTE") && privateText.contains("PRIVATE DESCRIPTION") && privateText.contains("PRIVATE PRIMER"))
    }
    @Test fun fourModesAndOverrideHashHaveStableIdentity() = runTest {
        val service = engine(query())
        for (mode in OfflineLabMode.entries) {
            val result = (service.render(request(mode = mode), noCancel) as Outcome.Success).value
            assertEquals(mode.templateId, result.artifact.templateId)
            assertTrue(result.artifact.rendered.contains(mode.name))
            val overridden = (service.render(request(mode = mode).copy(overrideBody = "{{request}} {{deck}} {{constraints}}"), noCancel) as Outcome.Success).value
            assertTrue(overridden.overrideUsed)
            assertEquals(service.templates().single { it.id == mode.templateId }, overridden.template)
            assertNotEquals(result.artifact.contextHash, overridden.artifact.contextHash)
            for (intensity in RoastIntensity.entries) {
                val intensified = (service.render(request(mode = mode).copy(intensity = intensity), noCancel) as Outcome.Success).value
                assertTrue(intensified.artifact.rendered.contains("\"intensity\":\"${intensity.name}\""))
            }
        }
    }
    @Test fun missingUnavailableAmbiguousAndMismatchedProvenanceAreDistinct() = runTest {
        val cases = listOf(query(emptyList()) to OfflineReferenceStatus.NOT_FOUND,
            query(listOf(card(), card(id = "other"))) to OfflineReferenceStatus.AMBIGUOUS,
            query(next = "cursor") to OfflineReferenceStatus.AMBIGUOUS,
            Query(Outcome.Unavailable(UnavailableReason.NOT_INSTALLED, "private diagnostic")) to OfflineReferenceStatus.UNAVAILABLE,
            query(frame = snapshot.copy(generation = GenerationId("g2"))) to OfflineReferenceStatus.UNAVAILABLE,
            query(listOf(card().copy(provenance = card().provenance.copy(generation = GenerationId("g2"))))) to OfflineReferenceStatus.UNAVAILABLE)
        for ((query, expected) in cases) {
            val result = (engine(query).review(deck(), snapshot, "Narrative [[A-Name // Back]]", noCancel) as Outcome.Success).value
            assertEquals(expected, result.references.single().status)
            assertEquals(LegalityStatus.UNKNOWN, result.formatLegality)
            assertTrue(result.acquisitionCost is Knowledge.Unknown)
            assertTrue(result.budget is Knowledge.Unknown)
        }
    }
    @Test fun sameGenerationTapAndOracleIdentityAreFencedBeforeIo() = runTest {
        val query = query(); val service = engine(query)
        val review = (service.review(deck(), snapshot, "[[A-Name // Back]] [[A-Name // Back]]", noCancel) as Outcome.Success).value
        assertEquals(1, query.calls.size)
        val ref = review.references.first()
        assertEquals(OfflineReferenceStatus.FOUND, ref.status)
        assertTrue(service.card(ref, snapshot.copy(generation = GenerationId("g2")), noCancel) is Outcome.Failure)
        assertEquals(1, query.calls.size)
        assertEquals(card(), (service.card(ref, snapshot, noCancel) as Outcome.Success).value)
        query.answer = Outcome.Success(Page(SnapshotList.of(card(id = "replacement")), null, snapshot))
        assertTrue(service.card(ref, snapshot, noCancel) is Outcome.Unavailable)
    }
    @Test fun malformedOversizedReplyAndCancellationFailBeforeLookup() = runTest {
        val query = query(); val service = engine(query)
        for (reply in listOf("[[x]", "]]", "[[outer [[inner]]", "[[]]", "[[${"x".repeat(513)}]]", "[[x]]".repeat(51), "x".repeat(262145), "\uD800")) {
            assertTrue(reply.take(50), service.review(deck(), snapshot, reply, noCancel) is Outcome.Failure)
        }
        assertEquals(0, query.calls.size)
        val cancelled = object : CancelToken { override val isCancelled = true; override fun throwIfCancelled(): Unit = throw CancellationException() }
        try { service.render(request(), cancelled); fail() } catch (_: CancellationException) { }
        assertEquals(0, query.calls.size)
    }
    @Test fun unpreparedCatalogOmitsOptionalBlocksWithoutLosingMandatoryDeck() = runTest {
        val query = Query(Outcome.Unavailable(UnavailableReason.NOT_INSTALLED, "backend secret"))
        val result = (engine(query).render(request(deck(55)), noCancel) as Outcome.Success).value
        assertEquals(55, result.oracleContextsOmitted)
        assertEquals(0, result.oracleContextsIncluded)
        assertTrue(result.artifact.rendered.contains("\"entryId\":\"entry55\""))
        assertTrue(result.artifact.rendered.contains("\"oracleContextsOmitted\":55"))
        assertFalse(result.artifact.rendered.contains("backend secret"))
    }
    @Test fun boundedOptionalRulesAreOmittedWholeAndUnknownNamesNeverResolveIdentity() = runTest {
        val query = query(listOf(card().copy(originalText = Knowledge.Known("x".repeat(128 * 1024)))))
        val result = (engine(query).render(request(), noCancel) as Outcome.Success).value
        assertEquals(0, result.oracleContextsIncluded); assertEquals(1, result.oracleContextsOmitted)
        val unresolved = deck().copy(entries = SnapshotList.of(deck().entries.first().let { it.copy(identity = it.identity.copy(oracleId = unknown)) }))
        val another = this@LocalOfflineDeckPromptsTest.query()
        val unresolvedResult = (engine(another).render(request(unresolved), noCancel) as Outcome.Success).value
        assertEquals(0, unresolvedResult.oracleContextsIncluded); assertEquals(0, another.calls.size)
    }
    @Test fun surpriseAngleRequiresOneExactNonconflictingDeclaration() = runTest {
        val service = engine(query())
        val stated = (service.review(deck(), snapshot, "Surprise angle: hidden synergies", noCancel) as Outcome.Success).value
        assertEquals(Knowledge.Known("hidden synergies"), stated.statedSurpriseAngle)
        for (reply in listOf("hidden synergies", "Surprise angle: other", "Surprise angle: hidden synergies\nSurprise angle: spicy tech")) {
            assertFalse((service.review(deck(), snapshot, reply, noCancel) as Outcome.Success).value.statedSurpriseAngle is Knowledge.Known)
        }
    }
    @Test fun oversizedMandatoryContextAndInvalidUserTextFailBeforeLookup() = runTest {
        val query = query(); val service = engine(query)
        val entry = deck().entries.first()
        val longIdentity = entry.identity.copy(originalName = Knowledge.Known("x".repeat(2 * 1024 * 1024)))
        val oversized = deck().copy(entries = SnapshotList.of(entry.copy(identity = longIdentity)))
        assertTrue(service.render(request(oversized), noCancel) is Outcome.Failure)
        for (bad in listOf("x".repeat(32769), "\uD800")) {
            assertTrue(service.render(request().copy(question = bad), noCancel) is Outcome.Failure)
            assertTrue(service.render(request().copy(constraints = bad), noCancel) is Outcome.Failure)
            assertTrue(service.render(request().copy(overrideBody = bad), noCancel) is Outcome.Failure)
        }
        assertEquals(0, query.calls.size)
    }
    @Test fun distinctLookupAndTotalOptionalBudgetsDiscloseWholeOmissions() = runTest {
        var calls = 0
        val query = object : LocalCardQuery {
            override suspend fun local(query: QueryAst, context: SearchContext, page: PageRequest, cancel: CancelToken): Outcome<Page<CardRecord>> {
                calls++
                val name = (query as QueryAst.Text).value
                return Outcome.Success(Page(SnapshotList.of(card(name, name).copy(originalText = Knowledge.Known("x".repeat(100000)))), null, snapshot))
            }
        }
        val base = deck()
        val entries = (1..55).map { index -> base.entries.first().let {
            val name = "Card $index"
            it.copy(id = EntityId("entry$index"), identity = it.identity.copy(originalName = Knowledge.Known(name), oracleId = Knowledge.Known(OracleId(source, name))))
        } }
        val result = (engine(query).render(request(base.copy(entries = SnapshotList.copyOf(entries))), noCancel) as Outcome.Success).value
        assertEquals(50, calls)
        assertEquals(55, result.oracleContextsIncluded + result.oracleContextsOmitted)
        assertTrue(result.oracleContextsIncluded in 1..5)
        assertTrue(result.artifact.rendered.contains("\"entryId\":\"entry55\""))
    }
    @Test fun absentSnapshotCannotProduceFoundAndWrongOracleCannotEnrich() = runTest {
        val query = query(); val service = engine(query)
        val review = (service.review(deck(), null, "[[A-Name // Back]]", noCancel) as Outcome.Success).value
        assertEquals(OfflineReferenceStatus.UNAVAILABLE, review.references.single().status)
        assertEquals(0, query.calls.size)
        val wrong = this@LocalOfflineDeckPromptsTest.query(listOf(card(id = "different")))
        val result = (engine(wrong).render(request(), noCancel) as Outcome.Success).value
        assertEquals(0, result.oracleContextsIncluded); assertEquals(1, result.oracleContextsOmitted)
    }

    @Test fun structuralValidationFailureAndCancellationAfterQueryCannotEscapeAsSuccess() = runTest {
        val query = query()
        val failing = LocalOfflineDeckPrompts(query, clock) { _, _ -> Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT, "private failure", SnapshotList.empty())) }
        val failure = failing.render(request(), noCancel) as Outcome.Failure
        assertEquals(FailureCode.INVALID_INPUT, failure.error.code)
        assertFalse(failure.error.message.contains("private")); assertEquals(0, query.calls.size)
        var cancelled = false
        val token = object : CancelToken {
            override val isCancelled get() = cancelled
            override fun throwIfCancelled() { if (cancelled) throw CancellationException() }
        }
        val cancellingQuery = object : LocalCardQuery {
            override suspend fun local(query: QueryAst, context: SearchContext, page: PageRequest, cancel: CancelToken): Outcome<Page<CardRecord>> {
                cancelled = true
                return Outcome.Success(Page(SnapshotList.of(card()), null, snapshot))
            }
        }
        try { engine(cancellingQuery).review(deck(), snapshot, "[[A-Name // Back]]", token); fail() } catch (_: CancellationException) { }
    }

    @Test fun exactNamePolicyAcceptsNfkcCompositionAndTrimWithoutChangingOriginals() = runTest {
        val equivalentNames = listOf(
            "Ａ－Ｎａｍｅ ／／ Ｂａｃｋ" to "A-Name // Back",
            "Cafe\u0301" to "Café",
            "  A-Name // Back  " to "A-Name // Back",
            "A-Name // Back" to "Ａ－Ｎａｍｅ ／／ Ｂａｃｋ",
            "Café" to "Cafe\u0301",
        )
        for ((requested, canonical) in equivalentNames) {
            val query = query(listOf(card(canonical)))
            val service = engine(query)
            val review = (service.review(deck(), snapshot, "[[$requested]]", noCancel) as Outcome.Success).value
            val reference = review.references.single()
            assertEquals(OfflineReferenceStatus.FOUND, reference.status)
            assertEquals(requested, reference.rawName)
            assertEquals(Knowledge.Known(canonical), reference.canonicalName)
            assertEquals(listOf(OracleId(source, "oracle")), reference.candidates.toList())
            assertEquals(QueryAst.Text(SearchTextField.NAME, requested, true), query.calls.single().first)
            assertEquals(canonical, (service.card(reference, snapshot, noCancel) as Outcome.Success).value.name)
            val original = deck().entries.first()
            val identity = original.identity.copy(originalName = Knowledge.Known(requested))
            val exportDeck = deck().copy(entries = SnapshotList.of(original.copy(identity = identity)))
            val rendered = (service.render(request(exportDeck), noCancel) as Outcome.Success).value
            assertEquals(1, rendered.oracleContextsIncluded)
            assertTrue(rendered.artifact.rendered.contains(requested))
            assertEquals(identity, exportDeck.entries.single().identity)
        }
    }
    @Test fun nonEquivalentReturnedNamesCannotDropArenaPrefixOrMultifaceName() = runTest {
        for (returned in listOf("Name // Back", "A-Name", "A-Other // Back")) {
            val query = query(listOf(card(returned)))
            val service = engine(query)
            val review = (service.review(deck(), snapshot, "[[A-Name // Back]]", noCancel) as Outcome.Success).value
            assertEquals(OfflineReferenceStatus.UNAVAILABLE, review.references.single().status)
            assertTrue(review.references.single().candidates.isEmpty())
            val rendered = (service.render(request(), noCancel) as Outcome.Success).value
            assertEquals(0, rendered.oracleContextsIncluded)
            assertEquals(1, rendered.oracleContextsOmitted)
        }
    }

}
