@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class ProfileCommanderControllerTest {
    private val catalog = SyntheticCatalog(listOf("Synthetic Commander"))
    private fun original(): PlayerProfileSnapshot {
        val life = CounterDefinition(EntityId("life"), "Life", null, null, 40, CounterLossPolicy.AtOrBelow(0), false, CounterRole.LIFE)
        val identity = syntheticCard("Original", null).profileCommanderIdentity()
        return PlayerProfileSnapshot(EntityId("profile"), Revision(4), PlayerProfileDraft("Ada", 40, SnapshotList.of(life),
            SeatAppearance(DisplayColor(0xffffffff), null, ArtFit.CONTAIN, ArtCrop(0.1, 0.2, 0.8, 0.9), SeatOrientation.INVERTED),
            SnapshotList.of(CommanderSlot(EntityId("old"), identity, "Original")), 3))
    }
    private fun TestScope.controller(repo: DraftProfilesFixture, ids: () -> EntityId = { EntityId(java.util.UUID.randomUUID().toString()) }): ProfilesController {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return ProfilesController(ownerScope(), repo, dispatcher,
            commanderSearch = SearchController(ownerScope(), catalog, dispatcher) {}, commanderIds = ids)
    }
    private fun TestScope.result(c: ProfilesController): CardRecord {
        c.openCommanderPicker(); c.commanderSearch!!.search("Synthetic"); advanceUntilIdle()
        return c.commanderSearch.state.value.results.single()
    }

    @Test fun duplicateOracleAddsSeparateSlotsAndLiteralIdentityOnlyAtWholeSave() = runTest {
        val old = original(); val repo = DraftProfilesFixture(old)
        val card = syntheticCard("Synthetic Commander", catalog.active!!.id).copy(providerIdentities = SnapshotList.of(
            ProviderIdentity(ProviderId("first"), "oracle", "raw-one"), ProviderIdentity(ProviderId("second"), "canonical", "raw-two")))
        catalog.lookupOverride = Outcome.Success(Page(SnapshotList.of(card), null, SnapshotReference(SYNTHETIC_PROVIDER, catalog.active!!.id, null, LanguageTag.ENGLISH)))
        var next = 0; val c = controller(repo) { EntityId("new-${++next}") }; c.edit(old); c.showCommanders()
        c.addCommander(result(c)); c.addCommander(result(c))
        val slots = c.state.value.editor!!.commanders
        assertEquals(listOf("old", "new-1", "new-2"), slots.map { it.id.value })
        for (slot in slots.drop(1)) {
            assertEquals(Knowledge.Known(card.oracleId), slot.identity.oracleId)
            assertTrue(slot.identity.printingId is Knowledge.Absent)
            assertEquals(card.providerIdentities.first(), slot.identity.raw)
            assertEquals(Knowledge.Known(card.name), slot.identity.originalName); assertEquals(card.language, slot.identity.language)
        }
        assertTrue(repo.writes.isEmpty()); c.save(); runCurrent(); assertTrue(repo.writes.isEmpty())
        c.showProfile(); c.save(); advanceUntilIdle()
        assertEquals(old.value.copy(commanders = slots), repo.row.value); assertEquals(listOf(old.revision), repo.expected)
    }

    @Test fun renameKeepsIdentityAndOrderAndConfirmedRemoveAllowsZeroWhileCancelWritesNothing() = runTest {
        val old = original(); val repo = DraftProfilesFixture(old); val c = controller(repo)
        c.edit(old); c.showCommanders(); c.editCommander(EntityId("old")); c.changeCommanderLabel("Partner A"); c.applyCommander()
        assertEquals(old.value.commanders.single().copy(label = "Partner A"), c.state.value.editor!!.commanders.single())
        c.requestCommanderDelete(EntityId("old")); c.dismissCommanderDelete(); assertEquals(1, c.state.value.editor!!.commanders.size)
        c.requestCommanderDelete(EntityId("old")); c.confirmCommanderDelete(); assertTrue(c.state.value.editor!!.commanders.isEmpty())
        assertTrue(repo.writes.isEmpty()); c.closeEditor(); assertEquals(old, repo.row)
        c.edit(old); c.showCommanders(); c.requestCommanderDelete(EntityId("old")); c.confirmCommanderDelete(); c.showProfile(); c.save(); advanceUntilIdle()
        assertEquals(old.value.copy(commanders = SnapshotList.empty()), repo.row.value)
    }

    @Test fun onlyUnchangedLegacyBlankLabelsAreAcceptedAndPerFormCancelPreservesValue() = runTest {
        val base = original(); val old = base.copy(value = base.value.copy(commanders = SnapshotList.of(base.value.commanders.single().copy(label = "  "))))
        val repo = DraftProfilesFixture(old); val c = controller(repo); c.edit(old); c.showCommanders()
        c.editCommander(EntityId("old")); c.applyCommander(); assertNull(c.state.value.editor!!.commanderEditor)
        c.editCommander(EntityId("old")); c.changeCommanderLabel(""); c.applyCommander(); assertTrue(c.state.value.editor!!.commanderEditor!!.invalidLabel)
        c.changeCommanderLabel("New"); c.closeCommanderEditor(); assertEquals(old.value.commanders, c.state.value.editor!!.commanders)
        assertTrue(repo.writes.isEmpty())
    }

    @Test fun forgedLoadingAndStaleResultsCannotBeAddedAndSearchIsResetAcrossEditors() = runTest {
        val old = original(); val repo = DraftProfilesFixture(old); val c = controller(repo); c.edit(old); c.showCommanders()
        val card = result(c); c.addCommander(card.copy(name = "Forged")); assertEquals(old.value.commanders, c.state.value.editor!!.commanders)
        c.commanderSearch!!.onActiveGeneration(GenerationId("new-generation")); c.addCommander(card)
        assertEquals(old.value.commanders, c.state.value.editor!!.commanders)
        c.closeCommanderPicker(); assertTrue(c.commanderSearch.state.value.results.isEmpty()); assertEquals(GenerationId("new-generation"), c.commanderSearch.state.value.activeGeneration)
        c.openCommanderPicker(); catalog.lookupGate = CompletableDeferred(); c.commanderSearch.search("Synthetic"); runCurrent()
        c.addCommander(card); assertEquals(old.value.commanders, c.state.value.editor!!.commanders)
        c.edit(old); catalog.lookupGate!!.complete(Unit); advanceUntilIdle()
        assertFalse(c.state.value.editor!!.commanderManager); assertTrue(c.commanderSearch.state.value.results.isEmpty())
        assertEquals("", c.commanderSearch.queryInput); assertFalse(c.commanderSearch.state.value.searched)
        c.showCommanders(); c.editAppearance(); c.showCounters()
        assertNull(c.state.value.editor!!.appearanceEditor); assertFalse(c.state.value.editor!!.counterManager)
        c.showProfile(); c.editAppearance(); c.showCommanders(); assertFalse(c.state.value.editor!!.commanderManager)
    }

    @Test fun persistenceFailureStaleReloadAndBusyGuardsKeepOriginalAuthority() = runTest {
        val old = original(); val repo = DraftProfilesFixture(old); val c = controller(repo); c.edit(old); c.showCommanders()
        c.editCommander(EntityId("old")); c.changeCommanderLabel("Edited"); c.applyCommander(); val draft = c.state.value.editor!!.commanders
        c.showProfile(); repo.writeStatus = failure(FailureCode.PERSISTENCE); c.save(); advanceUntilIdle()
        assertEquals(draft, c.state.value.editor!!.commanders); assertEquals(old, c.state.value.editor!!.original)
        repo.writeStatus = null; repo.row = old.copy(revision = Revision(9)); c.save(); advanceUntilIdle()
        assertEquals(Problem.STALE_REVISION, c.state.value.problem); assertTrue(repo.expected.all { it == old.revision })
        c.showCommanders(); result(c); c.reloadEditor(); advanceUntilIdle()
        assertEquals(repo.row, c.state.value.editor!!.original); assertEquals(old.value.commanders, c.state.value.editor!!.commanders)
        assertTrue(c.commanderSearch!!.state.value.results.isEmpty()); assertFalse(c.state.value.editor!!.commanderManager)
        repo.gate = CompletableDeferred(); c.save(); runCurrent()
        c.showCommanders(); c.openCommanderPicker(); c.editCommander(EntityId("old")); c.requestCommanderDelete(EntityId("old")); c.confirmCommanderDelete()
        assertFalse(c.state.value.editor!!.commanderManager); assertEquals(old.value.commanders, c.state.value.editor!!.commanders)
        repo.gate!!.complete(Unit); advanceUntilIdle()
    }

    @Test fun duplicateSlotIdCannotReplaceExistingCommanderOrSaveByAccident() = runTest {
        val old = original(); val repo = DraftProfilesFixture(old); val c = controller(repo) { EntityId("old") }
        c.edit(old); c.showCommanders(); c.addCommander(result(c))
        assertEquals(Problem.INTERNAL, c.state.value.problem); assertEquals(old.value.commanders, c.state.value.editor!!.commanders)
        assertTrue(c.state.value.editor!!.commanderPicker); assertTrue(repo.writes.isEmpty())
    }
}
