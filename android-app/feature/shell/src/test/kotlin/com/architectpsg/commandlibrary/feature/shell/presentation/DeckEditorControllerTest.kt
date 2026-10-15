@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.syntheticCard
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic ports validate presentation contracts, not SQL/reducer durability. */
class DeckEditorControllerTest {
    private val entry = DeckEntry(EntityId("original"), CardIdentity(Knowledge.Absent("oracle"), Knowledge.Absent("printing"),
        ProviderIdentity(ProviderId("foreign"), "raw", "untouched"), Knowledge.Known("Foreign name"), LanguageTag("ja")),
        2, BoardRole.SIDEBOARD, Knowledge.Unknown("unresolved", SnapshotList.empty()), "old notes", SnapshotList.empty(), SnapshotList.empty())
    private fun deck(raw: String = "d") = DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE, raw), "Deck", Knowledge.Absent("none"), "description",
        Knowledge.Absent("none"), SnapshotList.empty(), UtcTimestamp(1), UtcTimestamp(1), Revision(0), SnapshotList.of(entry),
        Knowledge.Absent("none"), null, SnapshotList.empty(), SnapshotList.empty(), "primer")
    private fun failure(code: FailureCode) = Outcome.Failure(DomainFailure(code, "Synthetic failure", SnapshotList.empty()))
    private inner class Fixture : LocalDeckLibrary, LocalDeckEdits {
        val rows = linkedMapOf(deck().id to deck())
        val patches = mutableListOf<List<Any>>()
        val undos = mutableListOf<List<Any>>()
        val receipts = mutableMapOf<EntityId, DeckSnapshot>()
        var mutationResult: Outcome<Nothing>? = null
        var gate: CompletableDeferred<Unit>? = null
        var createCalls = 0
        var undoTarget: DeckSnapshot? = null
        var wrongResult = false
        var loadFailure: Outcome<Nothing>? = null
        override suspend fun create(initial: DeckSnapshot, operationId: EntityId, cancel: CancelToken): Outcome<DeckSnapshot> { createCalls++; return Outcome.Success(initial) }
        override suspend fun duplicate(id: DeckId, expected: Revision, name: String, operationId: EntityId, cancel: CancelToken): Outcome<DeckSnapshot> = error("Not part of synthetic editor scenario")
        override suspend fun load(id: DeckId, cancel: CancelToken): Outcome<DeckSnapshot> = loadFailure ?: Outcome.Success(rows.getValue(id))
        override suspend fun list(page: PageRequest, cancel: CancelToken): Outcome<Page<DeckSummary>> = Outcome.Success(Page(SnapshotList.copyOf(rows.values.map {
            DeckSummary(it.id, it.name, it.revision, it.createdAt, it.modifiedAt, it.entries.size, it.entries.sumOf { e -> e.quantity.toLong() })
        }), null, SnapshotReference(LocalDeckLibraryLimits.SOURCE, null, Revision(0), null)))
        override suspend fun apply(id: DeckId, patch: DeckPatch, expected: Revision, operationId: EntityId, cancel: CancelToken): Outcome<DeckSnapshot> {
            patches += listOf(id, patch, expected, operationId)
            if ((mutationResult as? Outcome.Failure)?.error?.code == FailureCode.STALE_REVISION) return mutationResult!!
            val saved = receipts.getOrPut(operationId) {
                var changed = rows.getValue(id)
                for (edit in patch.edits) changed = when (edit) {
                    is DeckEdit.Rename -> changed.copy(name = edit.name)
                    is DeckEdit.Description -> changed.copy(description = edit.text)
                    is DeckEdit.Primer -> changed.copy(primer = edit.markdown)
                    is DeckEdit.Add -> changed.copy(entries = SnapshotList.copyOf(changed.entries + edit.entry))
                    is DeckEdit.Replace -> changed.copy(entries = SnapshotList.copyOf(changed.entries.map { if (it.id == edit.entry.id) edit.entry else it }))
                    is DeckEdit.Remove -> changed.copy(entries = SnapshotList.copyOf(changed.entries.filter { it.id != edit.entryId }))
                    is DeckEdit.ReorderEntries -> changed.copy(entries = SnapshotList.copyOf(edit.entryIds.map { key -> changed.entries.single { it.id == key } }))
                    else -> error("Synthetic test patch outside fixture")
                }
                changed.copy(revision = Revision(expected.value + 1), modifiedAt = UtcTimestamp(7)).also { rows[id] = it }
            }
            gate?.await()
            return mutationResult ?: Outcome.Success(if (wrongResult) saved.copy(id = DeckId(LocalDeckLibraryLimits.SOURCE, "wrong")) else saved)
        }
        override suspend fun undo(id: DeckId, expected: Revision, operationId: EntityId, cancel: CancelToken): Outcome<DeckSnapshot> {
            undos += listOf(id, expected, operationId)
            val saved = receipts.getOrPut(operationId) { (undoTarget ?: deck(id.raw)).copy(revision = Revision(expected.value + 1), modifiedAt = UtcTimestamp(8)).also { rows[id] = it } }
            gate?.await(); return mutationResult ?: Outcome.Success(saved)
        }
    }
    private fun TestScope.controller(f: Fixture, current: (CardRecord) -> Boolean = { true }, onCommitted: (DeckId, Revision) -> Unit = { _, _ -> }): DeckLibraryController {
        var ids = 0
        return DeckLibraryController(ownerScope(), f, StandardTestDispatcher(testScheduler), { "new-${++ids}" }, { UtcTimestamp(7) }, f, current, onCommitted)
    }
    @Test fun detailsAreOneExplicitGroupAndUnsavedInputNeverWrites() = runTest {
        val f=Fixture(); val c=controller(f); c.select(deck().id); advanceUntilIdle(); c.editDetails()
        c.editorChange { it.copy(name="Renamed", description="changed", primer="# revised") }
        assertTrue(f.patches.isEmpty()); assertEquals("Deck", f.rows.getValue(deck().id).name)
        c.confirmEditor(); advanceUntilIdle()
        val patch=f.patches.single()[1] as DeckPatch
        assertEquals(SnapshotList.of(DeckEdit.Rename("Renamed"), DeckEdit.Description("changed"), DeckEdit.Primer("# revised")),patch.edits)
        assertEquals(Revision(0), f.patches.single()[2]); assertEquals(Revision(1), c.state.value.selected!!.revision)
        assertNull(c.state.value.editor); assertFalse(c.state.value.pending)
    }
    @Test fun invalidQuantityAndNamePreserveDraftAndOriginalIdentityWhileValidReplacementRetainsUnknowns() = runTest {
        val f=Fixture(); val c=controller(f); c.select(deck().id); advanceUntilIdle(); c.editEntry(entry.id)
        for (bad in listOf("0","-1","2147483648","1.5","")) {
            c.editorChange { it.copy(quantity=bad) }; c.confirmEditor(); advanceUntilIdle(); assertTrue(c.state.value.invalidEdit); assertTrue(f.patches.isEmpty())
        }
        c.editorChange { it.copy(quantity="2147483647", board=BoardRole.MAIN, notes="new notes") }; c.confirmEditor(); advanceUntilIdle()
        val replacement=((f.patches.single()[1] as DeckPatch).edits.single() as DeckEdit.Replace).entry
        assertEquals(entry.identity,replacement.identity); assertEquals(entry.finish,replacement.finish); assertEquals(entry.id,replacement.id)
        assertEquals(Int.MAX_VALUE,replacement.quantity); assertEquals(BoardRole.MAIN,replacement.board)
        c.editDetails(); c.editorChange { it.copy(name=" ") }; c.confirmEditor(); advanceUntilIdle(); assertTrue(c.state.value.invalidEdit); assertEquals(1,f.patches.size)
    }
    @Test fun lostAcknowledgementRetriesFrozenOperationPatchAndRevisionWithoutOverridingLaterSelection() = runTest {
        val f=Fixture(); f.rows[deck("other").id]=deck("other"); val c=controller(f)
        c.select(deck().id); advanceUntilIdle(); c.editDetails(); c.editorChange { it.copy(name="Saved") }
        f.mutationResult=failure(FailureCode.PERSISTENCE); c.confirmEditor(); advanceUntilIdle(); val original=f.patches.single()
        assertTrue(c.state.value.pending); c.closePendingEditor(); assertNull(c.state.value.editor); assertTrue(c.state.value.pending); c.select(deck("other").id); advanceUntilIdle()
        c.editorChange { it.copy(name="Not sent") }; c.confirmEditor(); assertEquals(1,f.patches.size)
        f.mutationResult=Outcome.Unavailable(UnavailableReason.UNSUPPORTED,"Synthetic later failure"); c.retry(); advanceUntilIdle(); assertTrue(c.state.value.pending)
        f.mutationResult=null; c.retry(); advanceUntilIdle()
        assertEquals(listOf(original,original,original),f.patches); assertEquals(deck("other").id,c.state.value.selectedId)
        assertEquals(Revision(1),f.rows.getValue(deck().id).revision); assertEquals("Saved",f.rows.getValue(deck().id).name)
        assertFalse(c.state.value.pending)
    }
    @Test fun allLibraryAndEditCommandsSerializeAcrossInFlightAttemptAndUndoIsItsOwnFrozenOperation() = runTest {
        val f=Fixture(); val c=controller(f); c.select(deck().id); advanceUntilIdle(); c.editDetails(); f.gate=CompletableDeferred()
        c.confirmEditor(); runCurrent(); c.create(); c.confirm(); c.undo(); c.moveEntry(entry.id,1); c.confirmEditor()
        assertEquals(1,f.patches.size); assertEquals(0,f.createCalls); assertTrue(f.undos.isEmpty()); assertTrue(c.state.value.mutationBusy)
        f.gate!!.complete(Unit); advanceUntilIdle(); f.gate=null; f.mutationResult=failure(FailureCode.PERSISTENCE)
        c.undo(); advanceUntilIdle(); val first=f.undos.single(); assertEquals(Revision(1), first[1])
        f.mutationResult=null; c.retry(); advanceUntilIdle(); assertEquals(listOf(first,first),f.undos)
        assertEquals(Revision(2),c.state.value.selected!!.revision); assertEquals("Deck", c.state.value.selected!!.name)
    }
    @Test fun definitiveStaleEditReloadsHeadWithoutSilentlyRebasingDraftAndCorruptAckStaysPending() = runTest {
        val f=Fixture(); val c=controller(f); c.select(deck().id); advanceUntilIdle(); c.editDetails(); c.editorChange { it.copy(name="Draft") }
        f.rows[deck().id]=deck().copy(revision=Revision(1),name="Other writer"); f.mutationResult=failure(FailureCode.STALE_REVISION)
        c.confirmEditor(); advanceUntilIdle(); assertFalse(c.state.value.pending); assertEquals("Draft",c.state.value.editor!!.name)
        assertEquals("Other writer",c.state.value.selected!!.name); c.confirmEditor(); advanceUntilIdle(); assertEquals(1,f.patches.size)
        c.discardEditor(); assertNull(c.state.value.editor); f.mutationResult=null; f.wrongResult=true
        c.editDetails(); c.confirmEditor(); advanceUntilIdle(); assertTrue(c.state.value.pending); assertEquals(Problem.CORRUPT_DATA,c.state.value.mutationProblem)
    }
    @Test fun actualSelectedCardIsRequiredCurrentAtConfirmationAndAddNeverInventsPrintingOrCoalescesEntries() = runTest {
        val f=Fixture(); var current=false; val c=controller(f, current = { current })
        val card=syntheticCard("Synthetic selected", GenerationId("selected-generation"))
        c.select(deck().id); advanceUntilIdle(); c.addEntry(); c.editorChange { it.copy(card=card,quantity="3") }
        c.confirmEditor(); advanceUntilIdle(); assertTrue(c.state.value.invalidEdit); assertTrue(f.patches.isEmpty())
        current=true; c.confirmEditor(); advanceUntilIdle()
        val added=((f.patches.single()[1] as DeckPatch).edits.single() as DeckEdit.Add).entry
        assertEquals(Knowledge.Known(card.oracleId),added.identity.oracleId); assertEquals(card.language,added.identity.language)
        assertTrue(added.identity.printingId is Knowledge.Absent); assertTrue(added.finish is Knowledge.Absent)
        assertNotEquals(entry.id, added.id); assertEquals(3,added.quantity); assertEquals(2,f.rows.getValue(deck().id).entries.size)
    }
    @Test fun accessibleReorderAndRemovalPreserveIdentityAndRemainUndoGroups() = runTest {
        val f=Fixture(); val second=entry.copy(id=EntityId("second"),quantity=5)
        f.rows[deck().id]=deck().copy(entries=SnapshotList.of(entry,second)); val c=controller(f)
        c.select(deck().id); advanceUntilIdle(); c.moveEntry(second.id,-1); advanceUntilIdle()
        assertEquals(listOf(second.id,entry.id),c.state.value.selected!!.entries.map { it.id })
        assertEquals(7L,c.state.value.selected!!.entries.sumOf { it.quantity.toLong() })
        c.editEntry(entry.id,true); assertEquals(2,f.rows.getValue(deck().id).entries.size)
        c.confirmEditor(); advanceUntilIdle(); assertEquals(listOf(second),c.state.value.selected!!.entries)
        assertEquals(Revision(2),c.state.value.selected!!.revision)
    }
    @Test fun synchronousCatalogStatusFenceRejectsSelectionBeforeSearchCollectorCatchesUp() {
        val first=GenerationId("first"); val second=GenerationId("second")
        val card=syntheticCard("Synthetic current card",first)
        val search=SearchUiState(query="Synthetic",results=listOf(card),generation=first,activeGenerationKnown=true,activeGeneration=first)
        assertTrue(currentDeckCard(card,search,first))
        assertFalse(currentDeckCard(card,search,second)) // collector still says first, current status already second
        assertFalse(currentDeckCard(card,search,null))
        assertFalse(currentDeckCard(card,search.copy(results=emptyList()),first))
    }

    @Test fun acknowledgedRevisionIsForwardedEvenWhenCurrentHeadReloadFails() = runTest {
        val f = Fixture(); val acknowledged = mutableListOf<Pair<DeckId, Revision>>()
        val c = controller(f, onCommitted = { id, revision -> acknowledged += id to revision })
        c.select(deck().id); advanceUntilIdle(); c.editDetails(); c.editorChange { it.copy(name = "Edited") }
        f.gate = CompletableDeferred(); c.confirmEditor(); runCurrent()
        f.loadFailure = failure(FailureCode.TRANSPORT); f.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(listOf(deck().id to Revision(1)), acknowledged)
        assertEquals(Problem.TRANSPORT, c.state.value.detailProblem)
    }

    @Test fun acknowledgedDeckRevisionIsForwardedIndependentlyOfNewerUserSelection() = runTest {
        val f = Fixture(); val other = deck("other"); f.rows[other.id] = other
        val acknowledged = mutableListOf<Pair<DeckId, Revision>>()
        val c = controller(f, onCommitted = { id, revision -> acknowledged += id to revision })
        c.select(deck().id); advanceUntilIdle(); c.editDetails(); c.editorChange { it.copy(name = "Edited") }
        f.gate = CompletableDeferred(); c.confirmEditor(); runCurrent()
        c.select(other.id); advanceUntilIdle(); f.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(other.id, c.state.value.selectedId)
        assertEquals(listOf(deck().id to Revision(1)), acknowledged)
    }

    @Test fun wholeUndoAcceptsPackageGraphsWhilePrimitiveEditsRemainUnavailable() = runTest {
        for (currentHasPackage in listOf(false, true)) {
            val f = Fixture()
            val instance = PackageInstance(EntityId("instance"), EntityId("package"), EntityId("version"), PackageLinkPolicy.LINKED,
                SnapshotList.of(PackageContribution(EntityId("package-entry"), entry.id, 1)))
            val recovered = deck().copy(packageInstances = SnapshotList.of(instance))
            f.undoTarget = recovered
            f.rows[deck().id] = deck().copy(revision = Revision(1),
                packageInstances = if (currentHasPackage) recovered.packageInstances else SnapshotList.empty())
            val acknowledged = mutableListOf<Pair<DeckId, Revision>>()
            val c = controller(f, onCommitted = { id, revision -> acknowledged += id to revision })
            c.select(deck().id); advanceUntilIdle()
            if (currentHasPackage) { c.editDetails(); assertNull(c.state.value.editor); c.addEntry(); assertNull(c.state.value.editor) }
            c.undo(); advanceUntilIdle()
            assertEquals(1, f.undos.size); assertTrue(f.patches.isEmpty())
            assertEquals(recovered.packageInstances, c.state.value.selected!!.packageInstances)
            assertEquals(Revision(2), c.state.value.selected!!.revision)
            assertFalse(c.state.value.pending)
            assertEquals(listOf(deck().id to Revision(2)), acknowledged)
        }
    }

}
