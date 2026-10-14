@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.failure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class ProfileAppearanceControllerTest {
    private fun TestScope.controller(repo: DraftProfilesFixture) =
        ProfilesController(ownerScope(), repo, StandardTestDispatcher(testScheduler))
    private fun original(): PlayerProfileSnapshot {
        val card = CardIdentity(Knowledge.Absent("Synthetic"), Knowledge.Absent("Synthetic"),
            ProviderIdentity(ProviderId("synthetic"), "card", "one"), Knowledge.Known("Commander"), LanguageTag.ENGLISH)
        val life = CounterDefinition(EntityId("life"), "Life", null, null, 20, CounterLossPolicy.AtOrBelow(0), false, CounterRole.LIFE)
        val charge = CounterDefinition(EntityId("charge"), "Charge", -10, 10, 3, CounterLossPolicy.None, true,
            CounterRole.OTHER, DisplayColor(0xff112233), CounterPreset.CHARGE, CounterResetPolicy.ON_TURN_START)
        val appearance = SeatAppearance(DisplayColor(0x01234567), GameArtReference.CachedCard(card, EntityId("cached-art")),
            ArtFit.CONTAIN, ArtCrop(0.1, 0.2, 0.8, 0.9), SeatOrientation.INVERTED)
        return PlayerProfileSnapshot(EntityId("profile"), Revision(4), PlayerProfileDraft("  ", 20,
            SnapshotList.of(life, charge), appearance, SnapshotList.of(CommanderSlot(EntityId("partner"), card, "Partner")), 3))
    }

    @Test fun everyOrientationPreservesExactColorArtFitCropAndAllOtherFieldsAtOriginalRevision() = runTest {
        for (orientation in SeatOrientation.entries) {
            val old = original(); val repo = DraftProfilesFixture(old); val c = controller(repo)
            c.edit(old); c.editAppearance(); c.changeAppearanceOrientation(orientation); c.applyAppearance()
            val expected = old.value.appearance.copy(orientation = orientation)
            assertEquals(expected, c.state.value.editor!!.appearance); assertNull(c.state.value.editor!!.appearanceEditor)
            assertTrue(repo.writes.isEmpty()); assertEquals(0, repo.reads)
            c.save(); advanceUntilIdle()
            assertEquals(old.value.copy(appearance = expected), repo.writes.single())
            assertEquals(listOf(old.revision), repo.expected); assertEquals(old.revision.next(), repo.row.revision)
        }
    }

    @Test fun transparentBlackUnsignedWhiteAndExplicitUnsetStayDistinctForCreateAndUpdate() = runTest {
        val repo = DraftProfilesFixture(original()); val c = controller(repo)
        c.create("Life"); c.changeName("New"); c.editAppearance()
        c.changeAppearanceUseColor(true); c.changeAppearanceColor("#00000000"); c.applyAppearance()
        assertEquals(DisplayColor(0), c.state.value.editor!!.appearance.color)
        assertTrue(repo.writes.isEmpty()); c.save(); advanceUntilIdle()
        assertEquals(DisplayColor(0), repo.row.value.appearance.color)
        c.edit(repo.row); c.editAppearance(); c.changeAppearanceColor("#FFFFFFFF"); c.applyAppearance(); c.save(); advanceUntilIdle()
        assertEquals(4294967295L, repo.row.value.appearance.color!!.argb)
        c.edit(repo.row); c.editAppearance(); c.changeAppearanceUseColor(false); c.applyAppearance(); c.save(); advanceUntilIdle()
        assertNull(repo.row.value.appearance.color)
    }

    @Test fun invalidColorRetainsEnteredTextAndCannotMutateDraftUntilExplicitUnset() = runTest {
        val old = original(); val repo = DraftProfilesFixture(old); val c = controller(repo)
        for (color in listOf("", "#123456", "#FFFFFFFFF", " #FF112233", "#GG112233", "FF112233")) {
            c.edit(old); c.editAppearance(); c.changeAppearanceColor(color); c.applyAppearance()
            assertTrue(c.state.value.editor!!.appearanceEditor!!.invalidColor)
            assertEquals(color, c.state.value.editor!!.appearanceEditor!!.color)
            assertEquals(old.value.appearance, c.state.value.editor!!.appearance)
        }
        assertTrue(repo.writes.isEmpty()); assertEquals(0, repo.reads)
        c.changeAppearanceUseColor(false); c.applyAppearance()
        assertNull(c.state.value.editor!!.appearanceEditor)
        assertEquals(old.value.appearance.copy(color = null), c.state.value.editor!!.appearance)
    }

    @Test fun unappliedFormsBlockSaveAndOtherFormsWhileCancelKeepsAppliedDraft() = runTest {
        val old = original(); val repo = DraftProfilesFixture(old); val c = controller(repo)
        c.edit(old); c.changeName("Draft"); c.editAppearance(); c.changeAppearanceOrientation(SeatOrientation.CLOCKWISE)
        c.showCounters(); assertFalse(c.state.value.editor!!.counterManager)
        c.save(); runCurrent(); assertTrue(repo.writes.isEmpty())
        c.closeAppearanceEditor(); assertEquals(old.value.appearance, c.state.value.editor!!.appearance)
        assertEquals("Draft", c.state.value.editor!!.name)
        c.showCounters(); c.editAppearance(); assertNull(c.state.value.editor!!.appearanceEditor)
        c.showProfile(); c.editAppearance(); c.changeAppearanceOrientation(SeatOrientation.CLOCKWISE); c.applyAppearance()
        val applied = c.state.value.editor!!.appearance
        c.editAppearance(); c.changeAppearanceOrientation(SeatOrientation.UPRIGHT); c.closeAppearanceEditor()
        assertEquals(applied, c.state.value.editor!!.appearance)
        c.closeEditor(); assertNull(c.state.value.editor); assertEquals(old, repo.row); assertTrue(repo.writes.isEmpty())
    }

    @Test fun failuresAndStaleRevisionKeepAppearanceAndOnlyExplicitReloadReplacesNestedDrafts() = runTest {
        val old = original(); val repo = DraftProfilesFixture(old); val c = controller(repo)
        c.edit(old); c.editAppearance(); c.changeAppearanceUseColor(false)
        c.changeAppearanceOrientation(SeatOrientation.COUNTERCLOCKWISE); c.applyAppearance()
        val proposed = c.state.value.editor!!.appearance
        for (status in listOf(failure(FailureCode.PERSISTENCE), Outcome.Unknown("Synthetic", SnapshotList.empty()),
            Outcome.Unavailable(UnavailableReason.OFFLINE, "Synthetic"))) {
            repo.writeStatus = status; c.save(); advanceUntilIdle()
            assertEquals(proposed, c.state.value.editor!!.appearance); assertEquals(old, c.state.value.editor!!.original)
        }
        repo.writeStatus = null
        repo.row = old.copy(revision = Revision(9), value = old.value.copy(appearance = old.value.appearance.copy(color = DisplayColor(0))))
        c.save(); advanceUntilIdle(); assertEquals(Problem.STALE_REVISION, c.state.value.problem)
        assertEquals(proposed, c.state.value.editor!!.appearance); assertTrue(repo.expected.all { it == old.revision })
        c.editAppearance(); c.changeAppearanceColor("invalid draft"); c.reloadEditor(); advanceUntilIdle()
        assertEquals(repo.row, c.state.value.editor!!.original); assertEquals(repo.row.value.appearance, c.state.value.editor!!.appearance)
        assertNull(c.state.value.editor!!.appearanceEditor); assertFalse(c.state.value.editor!!.counterManager)
    }

    @Test fun busyPersistenceBlocksAppearanceChangesWithoutDroppingThePendingWrite() = runTest {
        val old = original(); val repo = DraftProfilesFixture(old); val c = controller(repo)
        c.edit(old); repo.gate = CompletableDeferred(); c.save(); runCurrent()
        c.editAppearance(); c.changeAppearanceUseColor(false); c.changeAppearanceColor("#FFFFFFFF")
        c.changeAppearanceOrientation(SeatOrientation.CLOCKWISE); c.applyAppearance(); c.showCounters()
        assertNull(c.state.value.editor!!.appearanceEditor); assertFalse(c.state.value.editor!!.counterManager)
        assertEquals(old.value.appearance, c.state.value.editor!!.appearance)
        repo.gate!!.complete(Unit); advanceUntilIdle(); assertEquals(listOf(old.value), repo.writes)
    }
}
