package com.architectpsg.commandlibrary.feature.decks

import com.architectpsg.commandlibrary.core.model.*
import java.util.concurrent.CancellationException
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class LocalDeckDraftPlannerTest {
    private val planner = LocalDeckDraftPlanner()
    private val cancel = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private val text = DeckDraftCommitScope.TEXT_AUTOSAVE
    private val explicit = DeckDraftCommitScope.EXPLICIT_CONFIRMED
    private val label = EntryLabel(EntityId("category"), "Category")
    private val identity = CardIdentity(Knowledge.Known(OracleId(ProviderId("foreign"), "oracle")), Knowledge.Unknown("printing unknown", SnapshotList.of(Evidence("unavailable", null, null))),
        ProviderIdentity(ProviderId("uninstalled"), "kind", "raw"), Knowledge.Unknown("name unknown", SnapshotList.empty()), LanguageTag("ja"))
    private fun entry(id: String = "entry") = DeckEntry(EntityId(id), identity, 1, BoardRole.MAIN, Knowledge.Unknown("finish unknown", SnapshotList.empty()), "old notes", SnapshotList.of(label),
        SnapshotList.of(RoleOverride(EntityId(id), Knowledge.Absent("oracle"), "custom role", true, UtcTimestamp(1))))
    private fun deck() = DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE, "deck"), "Name", Knowledge.Absent("format"), "description", Knowledge.Absent("folder"), SnapshotList.of("tag", "tag"),
        UtcTimestamp(1), UtcTimestamp(2), Revision(7), SnapshotList.of(entry(), entry("other")), Knowledge.Absent("budget"), EntityId("profile"),
        SnapshotList.of(SnapshotReference(ProviderId("foreign"), GenerationId("pinned"), Revision(3), LanguageTag("ja"))), SnapshotList.empty(), "primer", SnapshotList.of(label))
    private fun details(base: DeckSnapshot = deck()) = DeckDraftBody.Details(base.revision, base.name, base.description, base.primer)
    private fun form(base: DeckSnapshot = deck()) = base.entries.first().let { DeckDraftBody.Entry(base.revision, it.id, it.quantity.toString(), it.notes, it.board, it.finish, it.labels) }
    private fun add(base: DeckSnapshot = deck()) = DeckDraftBody.Add(base.revision, "1", "notes", BoardRole.SIDEBOARD, Knowledge.Known(Finish.ETCHED), SnapshotList.of(label), DeckDraftSelection(identity, GenerationId("generation")))
    private fun <T> ok(result: Outcome<T>): T { assertTrue(result.toString(), result is Outcome.Success); return (result as Outcome.Success).value }
    private fun failCode(result: Outcome<*>, code: FailureCode = FailureCode.INVALID_INPUT) { assertTrue(result.toString(), result is Outcome.Failure); assertEquals(code, (result as Outcome.Failure).error.code) }
    private fun plan(body: DeckDraftBody, scope: DeckDraftCommitScope = text, base: DeckSnapshot = deck(), id: EntityId? = null) = ok(planner.propose(base, body, scope, id, cancel))
    private fun projected(base: DeckSnapshot, plan: DeckDraftPlan) = ok(DeckPatchReducer.propose(base, plan.patch, base.revision, base.modifiedAt))

    @Test fun detailsCanonicalOrderAndReasonsContainOnlyActualDifferences() {
        val base = deck(); val body = details().copy(name = "New", description = "desc", primer = "new primer")
        for (scope in listOf(text, explicit)) {
            val p = plan(body, scope); assertEquals(listOf(DeckEdit.Rename("New"), DeckEdit.Description("desc"), DeckEdit.Primer("new primer")), p.patch.edits)
            assertEquals(if (scope == text) DeckDraftLimits.TEXT_REASON else DeckDraftLimits.EXPLICIT_REASON, p.patch.reason); assertNull(p.residual)
            assertEquals(Revision(8), projected(base, p).revision)
        }
    }
    @Test fun blankNameDoesNotBlockPrimerAndExactInvalidNameRemainsRecoverable() {
        val raw = " \t"; val body = details().copy(name = raw, primer = "changed")
        val p = plan(body); assertEquals(listOf(DeckEdit.Primer("changed")), p.patch.edits)
        assertEquals(body.copy(baseRevision = Revision(8)), p.residual)
        failCode(planner.propose(projected(deck(), p), p.residual!!, text, null, cancel))
    }
    @Test fun invalidUnicodeFieldsPromoteIndependentlyAndRemainExact() {
        val body = details().copy(name = "\uD800", description = "new", primer = "\uDC00")
        val p = plan(body); assertEquals(listOf(DeckEdit.Description("new")), p.patch.edits)
        assertEquals(body.copy(baseRevision = Revision(8)), p.residual)
        failCode(planner.propose(deck(), body, explicit, null, cancel))
    }
    @Test fun explicitDetailsRequiresEveryFieldValidEvenWhenOtherFieldsChanged() {
        for (body in listOf(details().copy(name = "", primer = "changed"), details().copy(description = "\uDFFF", name = "Changed"), details().copy(primer = "\uD800", description = "changed")))
            failCode(planner.propose(deck(), body, explicit, null, cancel))
    }
    @Test fun unchangedAndInvalidOnlyOrStagedOnlyAutosaveHaveNoProgress() {
        for (body in listOf(details(), details().copy(name = ""), form(), form().copy(quantity = "-"), form().copy(board = BoardRole.COMMANDER)))
            failCode(planner.propose(deck(), body, text, null, cancel))
        assertTrue(plan(details(), explicit).patch.edits.isEmpty()); assertNull(plan(form(), explicit).residual)
    }
    @Test fun partialQuantityAndNewNotesPreserveQuantityAndStagedChoices() {
        val body = form().copy(quantity = "-", notes = "new notes", board = BoardRole.COMMANDER, finish = Knowledge.Known(Finish.FOIL), labels = SnapshotList.empty())
        val p = plan(body); val replacement = (p.patch.edits.single() as DeckEdit.Replace).entry
        assertEquals(deck().entries.first().copy(notes = "new notes"), replacement)
        assertEquals(body.copy(baseRevision = Revision(8)), p.residual)
    }
    @Test fun quantityAliasesAreConsumedWithoutAContentRevisionOrGroup() {
        for (alias in listOf("01", "+1", "0000000001")) {
            val p = plan(form().copy(quantity = alias)); assertTrue(p.patch.edits.isEmpty()); assertNull(p.residual)
            assertEquals(Revision(7), projected(deck(), p).revision)
        }
        val staged = form().copy(quantity = "01", board = BoardRole.SIDEBOARD)
        val p = plan(staged); assertTrue(p.patch.edits.isEmpty()); assertEquals(staged.copy(quantity = "1"), p.residual)
        failCode(planner.propose(deck(), p.residual!!, text, null, cancel))
    }
    @Test fun onlyValidQuantityMayChangeWhileInvalidNotesAndStagesAreRetained() {
        val body = form().copy(quantity = "002", notes = "\uD800", board = BoardRole.MAYBEBOARD)
        val p = plan(body); assertEquals(deck().entries.first().copy(quantity = 2), (p.patch.edits.single() as DeckEdit.Replace).entry)
        assertEquals(body.copy(baseRevision = Revision(8), quantity = "2"), p.residual)
    }
    @Test fun positiveIntGrammarRejectsPartialWhitespaceUnicodeDigitsZeroNegativeAndOverflow() {
        for (raw in listOf("", "-", "+", "0", "-1", " 1", "1 ", "1.0", "１", "١", "2147483648", "++1", "1+")) {
            val body = form().copy(quantity = raw, notes = "changed")
            val p = plan(body); assertEquals(1, (p.patch.edits.single() as DeckEdit.Replace).entry.quantity)
            assertEquals(raw, (p.residual as DeckDraftBody.Entry).quantity)
            failCode(planner.propose(deck(), body, explicit, null, cancel))
        }
        assertEquals(Int.MAX_VALUE, (plan(form().copy(quantity = Int.MAX_VALUE.toString())).patch.edits.single() as DeckEdit.Replace).entry.quantity)
    }
    @Test fun explicitEntryCommitsAllConfirmedChoicesInOneBaseDerivedReplacement() {
        val body = form().copy(quantity = "3", notes = "changed", board = BoardRole.COMMANDER, finish = Knowledge.Known(Finish.ETCHED), labels = SnapshotList.empty())
        val p = plan(body, explicit); assertEquals(listOf(DeckEdit.Replace(deck().entries.first().copy(quantity = 3, notes = "changed", board = body.board, finish = body.finish, labels = body.labels))), p.patch.edits)
        assertNull(p.residual); assertEquals(deck().entries[1], projected(deck(), p).entries[1])
        assertEquals(identity, projected(deck(), p).entries.first().identity); assertEquals(deck().entries.first().roleOverrides, projected(deck(), p).entries.first().roleOverrides)
    }
    @Test fun structurallyBoundedInvalidStagedLabelsStayRawUntilExplicitValidation() {
        val body = form().copy(notes = "changed", labels = SnapshotList.of(label, label))
        val p = plan(body); assertEquals(body.copy(baseRevision = Revision(8)), p.residual)
        failCode(planner.propose(deck(), body, explicit, null, cancel))
        val mismatch = body.copy(labels = SnapshotList.of(label.copy(name = "mismatch")))
        assertNotNull(plan(mismatch).residual); failCode(planner.propose(deck(), mismatch, explicit, null, cancel))
    }
    @Test fun addAndRemoveNeverAutosaveAndNoDefaultQuantityProvidesAuthority() {
        failCode(planner.propose(deck(), add(), text, EntityId("new"), cancel))
        failCode(planner.propose(deck(), DeckDraftBody.Remove(Revision(7), EntityId("entry")), text, null, cancel))
        failCode(planner.propose(deck(), add().copy(selection = null), explicit, EntityId("new"), cancel))
    }
    @Test fun explicitAddPreservesExactIdentitySelectionAndFrozenCallerId() {
        val body = add(); val p = plan(body, explicit, id = EntityId("new")); val entry = (p.patch.edits.single() as DeckEdit.Add).entry
        assertEquals(EntityId("new"), entry.id); assertEquals(identity, entry.identity); assertEquals(body.finish, entry.finish); assertEquals(body.labels, entry.labels)
        assertTrue(entry.roleOverrides.isEmpty()); assertNull(p.residual); assertEquals(3, projected(deck(), p).entries.size)
        assertEquals(p, plan(body, explicit, id = EntityId("new")))
    }
    @Test fun addRequiresKnownQualifiedOracleGenerationFreshIdAndValidFields() {
        val body = add()
        for (bad in listOf(body.copy(selection = null), body.copy(selection = body.selection!!.copy(generation = null)), body.copy(selection = DeckDraftSelection(identity.copy(oracleId = Knowledge.Absent("unknown")), GenerationId("g"))), body.copy(quantity = "-"), body.copy(notes = "\uD800")))
            failCode(planner.propose(deck(), bad, explicit, EntityId("new"), cancel))
        failCode(planner.propose(deck(), body, explicit, null, cancel)); failCode(planner.propose(deck(), body, explicit, EntityId("entry"), cancel))
        failCode(planner.propose(deck(), body, explicit, EntityId("界".repeat(342)), cancel))
    }
    @Test fun removeIsOneExplicitTargetEditAndUnrelatedIdsAreRejected() {
        val body = DeckDraftBody.Remove(Revision(7), EntityId("entry")); val p = plan(body, explicit)
        assertEquals(listOf(DeckEdit.Remove(EntityId("entry"))), p.patch.edits); assertNull(p.residual)
        assertEquals(listOf(deck().entries[1]), projected(deck(), p).entries)
        for (draft in listOf(details(), form(), body)) failCode(planner.propose(deck(), draft, explicit, EntityId("irrelevant"), cancel))
    }
    @Test fun missingEntriesStaleBaseAndWrongSourceAreTypedFailures() {
        failCode(planner.propose(deck(), form().copy(entryId = EntityId("missing")), text, null, cancel))
        failCode(planner.propose(deck(), DeckDraftBody.Remove(Revision(7), EntityId("missing")), explicit, null, cancel))
        failCode(planner.propose(deck(), details().copy(baseRevision = Revision(6)), text, null, cancel), FailureCode.STALE_REVISION)
        failCode(planner.propose(deck().copy(id = DeckId(ProviderId("foreign"), "deck")), details().copy(primer = "new"), text, null, cancel))
    }
    @Test fun malformedBaseGraphAndInvalidBaseTextAreRejected() {
        for (base in listOf(deck().copy(entries = SnapshotList.of(entry(), entry())), deck().copy(name = ""), deck().copy(name = "x".repeat(1025)), deck().copy(primer = "\uD800"), deck().copy(entries = SnapshotList.of(entry().copy(notes = "\uDFFF")))))
            failCode(planner.propose(base, details(base).copy(description = "new"), text, null, cancel))
    }
    @Test fun packageBearingDraftsRemainUnsupportedAndInputsUntouched() {
        val base = deck().copy(packageInstances = SnapshotList.of(PackageInstance(EntityId("i"), EntityId("p"), EntityId("v"), PackageLinkPolicy.LINKED, SnapshotList.empty())))
        val body = details(base).copy(primer = "new")
        for (scope in listOf(text, explicit)) assertEquals(UnavailableReason.UNSUPPORTED, (planner.propose(base, body, scope, null, cancel) as Outcome.Unavailable).reason)
        assertEquals("new", body.primer); assertEquals("primer", base.primer); assertEquals(1, base.packageInstances.size)
    }
    @Test fun revisionExhaustionOnlyBlocksActualContentDelta() {
        val base = deck().copy(revision = Revision(Long.MAX_VALUE))
        failCode(planner.propose(base, details(base).copy(primer = "new"), text, null, cancel), FailureCode.PERSISTENCE)
        assertTrue(plan(details(base), explicit, base).patch.edits.isEmpty())
        val p = plan(form(base).copy(quantity = "01"), text, base); assertTrue(p.patch.edits.isEmpty()); assertNull(p.residual)
    }
    @Test fun rawAndCommittedBoundsHaveDistinctExactUtf16AndUtf8Semantics() {
        assertEquals(listOf(DeckEdit.Rename("a".repeat(1024))), plan(details().copy(name = "a".repeat(1024))).patch.edits)
        val raw = "界".repeat(342); val p = plan(details().copy(name = raw, primer = "new")); assertEquals(raw, (p.residual as DeckDraftBody.Details).name)
        val exact = details().copy(name = "x".repeat(4096), description = "x".repeat(1_048_576), primer = "new")
        assertEquals(2, plan(exact).patch.edits.size); assertNotNull(plan(exact).residual)
        failCode(planner.propose(deck(), exact.copy(name = "x".repeat(4097)), text, null, cancel))
        failCode(planner.propose(deck(), details().copy(description = "x".repeat(1_048_577)), text, null, cancel))
        failCode(planner.propose(deck(), form().copy(quantity = "0".repeat(4097)), text, null, cancel))
    }
    @Test fun rawStagedStructuredFieldsStillUseStrictBoundedEncoding() {
        failCode(planner.propose(deck(), form().copy(notes = "new", labels = SnapshotList.of(label.copy(name = "\uD800"))), text, null, cancel))
        failCode(planner.propose(deck(), form().copy(notes = "new", labels = SnapshotList.copyOf(List(10001) { label })), text, null, cancel))
        failCode(planner.propose(deck(), add().copy(selection = DeckDraftSelection(identity, GenerationId("x".repeat(1025)))), explicit, EntityId("new"), cancel))
    }
    @Test fun generatedValidTextPlansAreDeterministicConserveOtherEntriesAndReachFixedPoint() {
        val random = Random(21015)
        repeat(150) {
            val base = deck(); val body = form().copy(quantity = random.nextInt(1, 10000).toString(), notes = "notes-${random.nextInt()}")
            val p = plan(body); assertEquals(p, plan(body)); val result = projected(base, p)
            assertEquals(body.quantity.toInt(), result.entries.first().quantity); assertEquals(body.notes, result.entries.first().notes)
            assertEquals(base.entries[1], result.entries[1]); assertEquals(base.copy(entries = result.entries, revision = result.revision), result)
            assertEquals(Revision(7), base.revision); assertEquals("old notes", base.entries.first().notes); assertNull(p.residual)
            failCode(planner.propose(result, form(result), text, null, cancel))
        }
    }
    @Test fun arbitraryInvalidUtf16TextIsRetainedWithoutReplacementWhenAnotherFieldProgresses() {
        val random = Random(991)
        repeat(100) {
            val invalid = "\uD800x" + CharArray(50) { random.nextInt(65536).toChar() }.concatToString() + "\uDFFF"
            val body = details().copy(name = invalid, primer = "new")
            val p = plan(body); assertEquals(invalid, (p.residual as DeckDraftBody.Details).name); assertEquals(listOf(DeckEdit.Primer("new")), p.patch.edits)
        }
    }
    @Test fun aggregatePreflightRejectsRepeatedHugeStagedAndBaseStringsBeforeScanning() {
        val large = "x".repeat(1_048_576)
        val staged = form().copy(notes = "changed", labels = SnapshotList.copyOf(List(10_000) { label.copy(name = large) }))
        val enormousBase = deck().copy(entries = SnapshotList.copyOf(List(9) { entry("e$it").copy(notes = large) }))
        for ((base, body) in listOf(deck() to staged, enormousBase to details(enormousBase).copy(primer = "changed"))) {
            val token = object : CancelToken {
                var checks = 0
                override val isCancelled get() = checks >= 500
                override fun throwIfCancelled() { if (++checks >= 500) throw CancellationException("unexpected content scan") }
            }
            failCode(planner.propose(base, body, text, null, token))
            assertTrue("length preflight must reject before long content scans", token.checks < 500)
        }
    }
    @Test fun independentlyValidLargeBaseAndDraftHaveSeparateTraversalBudgets() {
        val large = "x".repeat(1_048_576)
        val base = deck().copy(entries = SnapshotList.copyOf(List(7) { entry("e$it").copy(notes = large) }))
        val body = details(base).copy(description = large, primer = large)
        val p = plan(body, text, base); assertEquals(2, p.patch.edits.size); assertNull(p.residual)
        assertEquals(base.entries, projected(base, p).entries)
    }
    @Test fun cancellationEscapesBeforePlanningAndWithinLongUnicodeScans() {
        val exception = CancellationException("exact cancellation")
        for (limit in listOf(1, 100)) {
            val token = object : CancelToken { var checks = 0; override val isCancelled get() = checks >= limit; override fun throwIfCancelled() { if (++checks >= limit) throw exception } }
            val body = details().copy(description = "x" + "\uD83D\uDE00".repeat(200000))
            try { planner.propose(deck(), body, text, null, token); fail("expected cancellation") } catch (actual: CancellationException) { assertSame(exception, actual) }
        }
    }
}
