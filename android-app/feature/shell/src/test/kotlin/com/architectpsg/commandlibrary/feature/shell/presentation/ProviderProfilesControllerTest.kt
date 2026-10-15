@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class ProviderProfilesControllerTest {
    private fun TestScope.controller(f: ProfileFixture, id: () -> String = { "new-profile" }) =
        ProviderProfilesController(ownerScope(), f.actions(), { Outcome.Success(f.host) }, StandardTestDispatcher(testScheduler), id)
    private fun edit(c: ProviderProfilesController, field: ProviderProfileField, text: String) = c.edit(field, text, c.state.value.formRevision)
    private fun form(c: ProviderProfilesController) {
        c.newProfile(); edit(c, ProviderProfileField.NAME, "New"); edit(c, ProviderProfileField.BASE, "https://example.test/v1"); edit(c, ProviderProfileField.MODEL, "manual")
    }
    @Test fun uncertainCommittedCreateReloadsSameIdWithoutDuplicateCreate() = runTest {
        val f = ProfileFixture(); var ids = 0
        val c = controller(f) { ids++; "stable-id" }
        c.open(); advanceUntilIdle(); form(c)
        f.commitBeforeCreateResult = true; f.createResult = Outcome.Unknown("Fixture uncertain", SnapshotList.empty())
        c.save(c.state.value.formRevision); advanceUntilIdle()
        assertTrue(c.state.value.reloadRequired)
        c.save(c.state.value.formRevision); advanceUntilIdle()
        assertEquals(EntityId("stable-id"), c.state.value.selected!!.profile.id)
        assertEquals(1, ids); assertEquals(1, f.creates); assertFalse(c.state.value.reloadRequired)
    }
    @Test fun uncertainUncommittedCreateRequiresLoadThenExplicitSameIdRetry() = runTest {
        val f = ProfileFixture(); val c = controller(f)
        c.open(); advanceUntilIdle(); form(c)
        f.createResult = Outcome.Unknown("Fixture uncertain", SnapshotList.empty())
        c.save(c.state.value.formRevision); advanceUntilIdle()
        c.newProfile(); assertEquals(EntityId("new-profile"), c.state.value.form!!.id)
        c.refresh(); advanceUntilIdle(); assertEquals(1, f.loads); assertEquals(1, f.creates)
        f.createResult = null
        c.save(c.state.value.formRevision); advanceUntilIdle()
        assertEquals(2, f.creates); assertEquals(EntityId("new-profile"), c.state.value.selected!!.profile.id)
    }
    @Test fun rejectedOverlongFieldCannotFallBackToPreviouslyValidValue() = runTest {
        val f = ProfileFixture(); val c = controller(f)
        c.open(); advanceUntilIdle(); form(c)
        edit(c, ProviderProfileField.NAME, "x".repeat(1025))
        edit(c, ProviderProfileField.MODEL, "corrected-other-field")
        c.save(c.state.value.formRevision); advanceUntilIdle()
        assertEquals(0, f.creates); assertTrue(ProviderProfileField.NAME in c.state.value.form!!.rejected)
        edit(c, ProviderProfileField.NAME, "Corrected")
        c.save(c.state.value.formRevision); advanceUntilIdle(); assertEquals(1, f.creates)
    }
    @Test fun malformedUnicodeAndControlFieldsAreRejectedBeforeDispatch() = runTest {
        val f = ProfileFixture(); val c = controller(f)
        c.open(); advanceUntilIdle(); form(c)
        edit(c, ProviderProfileField.MODEL, "bad\u0000model")
        edit(c, ProviderProfileField.NAME, "\uD800")
        c.save(c.state.value.formRevision); advanceUntilIdle()
        assertEquals(0, f.creates); assertEquals(2, c.state.value.form!!.rejected.size)
    }
    @Test fun staleFormCallbackCannotWriteIntoAnotherModeOrSelection() = runTest {
        val f = ProfileFixture(); f.seed(); val c = controller(f)
        c.open(); advanceUntilIdle(); form(c)
        val old = c.state.value.formRevision
        c.select(f.id); advanceUntilIdle()
        c.edit(ProviderProfileField.NAME, "Obsolete event", old); c.save(old); c.putKey("fixture", old)
        advanceUntilIdle()
        assertEquals("Fixture", c.state.value.form!!.name); assertEquals(0, f.puts); assertEquals(0, f.replaces)
    }
    @Test fun dismissedLoadCompletionDoesNotPublishAndReopenLoadsCurrentSelection() = runTest {
        val f = ProfileFixture(); f.seed(); val c = controller(f)
        c.open(); advanceUntilIdle()
        f.loadGate = CompletableDeferred(); c.select(f.id); runCurrent(); c.dismiss()
        f.loadGate!!.complete(Unit); advanceUntilIdle()
        assertNull(c.state.value.selected); assertFalse(c.state.value.visible)
        c.open(); advanceUntilIdle(); c.select(f.id); advanceUntilIdle()
        assertEquals(f.id, c.state.value.selected!!.profile.id)
    }
    @Test fun paginationExposesAllPagesWithoutSilentTruncation() = runTest {
        val f = ProfileFixture(); repeat(53) { f.seed(key = EntityId("p-%03d".format(it))) }
        val c = controller(f); c.open(); advanceUntilIdle()
        assertEquals(25, c.state.value.items.size); assertNotNull(c.state.value.nextCursor)
        c.more(); advanceUntilIdle(); c.more(); advanceUntilIdle()
        assertEquals(53, c.state.value.items.size); assertNull(c.state.value.nextCursor)
        assertEquals(listOf(null, "25", "50"), f.cursors)
    }
    @Test fun failedKeyPutRequiresReloadAndNeverRepeatsAutomatically() = runTest {
        val f = ProfileFixture(); f.seed(); val c = controller(f)
        c.open(); advanceUntilIdle(); c.select(f.id); advanceUntilIdle()
        f.putResult = profileFailure(FailureCode.PERSISTENCE)
        c.putKey("fixture", c.state.value.formRevision); advanceUntilIdle()
        assertTrue(c.state.value.reloadRequired); assertEquals(1, f.puts)
        c.putKey("fixture", c.state.value.formRevision); advanceUntilIdle(); assertEquals(1, f.puts)
        c.refresh(); advanceUntilIdle()
        assertFalse(c.state.value.reloadRequired); assertNotNull(c.state.value.selected!!.profile.secretRef); assertEquals(1, f.puts)
    }
    @Test fun staleSaveKeepsDraftUntilExplicitReloadAndDoesNotOverwriteLatest() = runTest {
        val f = ProfileFixture(); f.seed(); val c = controller(f)
        c.open(); advanceUntilIdle(); c.select(f.id); advanceUntilIdle()
        edit(c, ProviderProfileField.NAME, "Draft")
        val old = f.rows[f.id.value]!!
        f.rows[f.id.value] = old.copy(revision = Revision(2), profile = old.profile.copy(name = "External"))
        c.save(c.state.value.formRevision); advanceUntilIdle()
        assertEquals(Problem.STALE_REVISION, c.state.value.problem); assertTrue(c.state.value.reloadRequired)
        assertEquals("External", f.rows[f.id.value]!!.profile.name)
        c.refresh(); advanceUntilIdle(); assertEquals("External", c.state.value.form!!.name)
    }
    @Test fun confirmationIsInvalidatedByFieldEditAndOldRevisionCannotDelete() = runTest {
        val f = ProfileFixture(); f.seed(); val c = controller(f)
        c.open(); advanceUntilIdle(); c.select(f.id); advanceUntilIdle()
        val revision = c.state.value.formRevision
        c.requestConfirmation(ProviderProfileConfirmation.DELETE, revision)
        edit(c, ProviderProfileField.NAME, "Changed")
        c.confirm(revision); advanceUntilIdle(); assertEquals(0, f.deletes); assertNull(c.state.value.confirmation)
    }
    @Test fun closeDuringKeyMutationDoesNotCancelAndReopenRequiresAuthoritativeReload() = runTest {
        val f = ProfileFixture(); f.seed(); val c = controller(f)
        c.open(); advanceUntilIdle(); c.select(f.id); advanceUntilIdle()
        f.putGate = CompletableDeferred(); c.putKey("fixture", c.state.value.formRevision); runCurrent()
        c.dismiss(); c.open(); assertTrue(c.state.value.busy)
        f.putGate!!.complete(Unit); advanceUntilIdle()
        assertEquals(1, f.puts); assertFalse(c.state.value.reloadRequired); assertNotNull(c.state.value.selected!!.profile.secretRef)
    }
}
