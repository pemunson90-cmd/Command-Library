@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.failure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Draft-only behavior through the actual controller; SQLite readback is a separate integration gate. */
class ProfilesCounterControllerTest {
    private fun TestScope.controller(repo: DraftProfilesFixture, ids: () -> EntityId = { EntityId("fresh") }) =
        ProfilesController(ownerScope(), repo, StandardTestDispatcher(testScheduler), counterIds = ids)

    private fun original(): PlayerProfileSnapshot {
        val life = CounterDefinition(EntityId("life"), "Life", -100, 100, 20, CounterLossPolicy.AtOrBelow(0), false, CounterRole.LIFE)
        val charge = CounterDefinition(EntityId("charge"), "Charge", null, null, 3, CounterLossPolicy.None, true,
            CounterRole.OTHER, DisplayColor(0xff123456), CounterPreset.CHARGE, CounterResetPolicy.ON_TURN_START)
        val tax = CounterDefinition(EntityId("tax"), "Tax", 0, null, 0, CounterLossPolicy.None, true, CounterRole.COMMANDER_TAX)
        val poison = CounterDefinition(EntityId("poison"), "Poison", 0, null, 0, CounterLossPolicy.AtOrAbove(10), false,
            CounterRole.OTHER, null, CounterPreset.POISON)
        val card = CardIdentity(Knowledge.Absent("Synthetic"), Knowledge.Absent("Synthetic"),
            ProviderIdentity(ProviderId("synthetic"), "card", "one"), Knowledge.Known("Commander"), LanguageTag.ENGLISH)
        return PlayerProfileSnapshot(EntityId("profile"), Revision(4), PlayerProfileDraft("Original", 20,
            SnapshotList.of(life, charge, tax, poison), SeatAppearance(DisplayColor(0xffabcdef), null, orientation = SeatOrientation.INVERTED),
            SnapshotList.of(CommanderSlot(EntityId("partner"), card, "Partner")), 2))
    }

    @Test fun newCounterOnlyChangesDraftUntilWholeProfileSaveAndLifeStaysConsistent() = runTest {
        val repo = DraftProfilesFixture(original()); val c = controller(repo)
        c.create("Life"); c.changeName("New profile"); c.showCounters(); c.addCounter()
        c.changeCounterLabel("  Custom charge  "); c.changeCounterInitial("-5")
        c.changeCounterMinimum("-10"); c.changeCounterMaximum("10")
        c.changeCounterLoss(ProfileCounterLoss.ABOVE); c.changeCounterThreshold("99")
        c.changeCounterUseColor(true); c.changeCounterColor("#8000FF00")
        c.changeCounterReset(CounterResetPolicy.ON_TURN_START); c.applyCounter()
        assertTrue(repo.writes.isEmpty()); assertEquals(0, repo.reads); assertNull(c.state.value.editor!!.counterEditor)
        val added = c.state.value.editor!!.counterDefinitions.last()
        assertEquals(EntityId("fresh"), added.id); assertEquals("  Custom charge  ", added.label)
        assertEquals(-5, added.initial); assertEquals(CounterLossPolicy.AtOrAbove(99), added.lossPolicy)
        assertEquals(DisplayColor(0x8000ff00), added.color); assertTrue(added.custom)
        assertEquals(CounterRole.OTHER, added.role); assertNull(added.preset)
        c.showProfile(); c.changeLife("-20"); c.save(); advanceUntilIdle()
        val value = repo.writes.single()
        assertEquals(-20, value.preferredLife); assertEquals(-20, value.counterDefinitions.single { it.role == CounterRole.LIFE }.initial)
        assertEquals(added, value.counterDefinitions.last()); assertNull(c.state.value.editor)
    }

    @Test fun editingPreservesIdentityPresetOrderAndEveryUntouchedProfileField() = runTest {
        val old = original(); val repo = DraftProfilesFixture(old); val c = controller(repo)
        c.edit(old); c.showCounters(); c.editCounter(EntityId("charge")); c.changeCounterInitial("7"); c.applyCounter()
        val definitions = c.state.value.editor!!.counterDefinitions
        assertEquals(old.value.counterDefinitions.map { it.id }, definitions.map { it.id })
        assertEquals(old.value.counterDefinitions[1].copy(initial = 7), definitions[1])
        assertTrue(repo.writes.isEmpty()); c.showProfile(); c.save(); advanceUntilIdle()
        assertEquals(old.value.copy(counterDefinitions = definitions), repo.writes.single())
        assertEquals(old.revision, repo.expected.single()); assertEquals(old.revision.next(), repo.row.revision)
    }

    @Test fun invalidCounterInputNeverChangesDefinitionsAndThresholdMayBeOutsideBounds() = runTest {
        val old = original(); val repo = DraftProfilesFixture(old); val c = controller(repo)
        val invalid: List<(ProfilesController) -> Unit> = listOf(
            { it.changeCounterLabel(" ") }, { it.changeCounterInitial("2147483648") },
            { it.changeCounterMinimum("2147483648") }, { it.changeCounterMaximum("-2147483649") },
            { it.changeCounterMinimum("5") }, { it.changeCounterMaximum("2") },
            { it.changeCounterMinimum("5"); it.changeCounterMaximum("2") },
            { it.changeCounterLoss(ProfileCounterLoss.BELOW); it.changeCounterThreshold("2147483648") },
            { it.changeCounterUseColor(true); it.changeCounterColor("#GG000000") },
            { it.changeCounterUseColor(true); it.changeCounterColor("#123456") },
        )
        for (change in invalid) {
            c.edit(old); c.showCounters(); c.editCounter(EntityId("charge")); change(c); c.applyCounter()
            assertNotNull(c.state.value.editor!!.counterEditor!!.validation)
            assertEquals(old.value.counterDefinitions, c.state.value.editor!!.counterDefinitions)
        }
        c.edit(old); c.showCounters(); c.editCounter(EntityId("charge"))
        c.changeCounterMinimum("-10"); c.changeCounterMaximum("10")
        c.changeCounterLoss(ProfileCounterLoss.BELOW); c.changeCounterThreshold("-99")
        c.changeCounterUseColor(false); c.applyCounter()
        assertNull(c.state.value.editor!!.counterEditor)
        val changed = c.state.value.editor!!.counterDefinitions[1]
        assertEquals(CounterLossPolicy.AtOrBelow(-99), changed.lossPolicy); assertNull(changed.color)
        assertTrue(repo.writes.isEmpty())
    }

    @Test fun unchangedLegacyBlankCounterLabelCanKeepItsNameDuringNumericEdit() = runTest {
        val old = original().let { p -> p.copy(value = p.value.copy(counterDefinitions = SnapshotList.copyOf(
            p.value.counterDefinitions.map { if (it.id == EntityId("charge")) it.copy(label = "  ") else it }))) }
        val repo = DraftProfilesFixture(old); val c = controller(repo)
        c.edit(old); c.showCounters(); c.editCounter(EntityId("charge")); c.changeCounterInitial("4"); c.applyCounter()
        assertNull(c.state.value.editor!!.counterEditor)
        assertEquals("  ", c.state.value.editor!!.counterDefinitions[1].label)
        assertEquals(4, c.state.value.editor!!.counterDefinitions[1].initial)
    }

    @Test fun roleAndCustomGuardsProtectReservedAndStandardCountersEvenThroughControllerCalls() = runTest {
        val old = original(); val repo = DraftProfilesFixture(old); val c = controller(repo)
        c.edit(old); c.showCounters()
        for (id in listOf("life", "tax", "poison", "missing")) {
            c.editCounter(EntityId(id)); assertNull(c.state.value.editor!!.counterEditor)
            c.requestCounterDelete(EntityId(id)); assertNull(c.state.value.editor!!.pendingCounterDelete)
            c.confirmCounterDelete(); assertEquals(old.value.counterDefinitions, c.state.value.editor!!.counterDefinitions)
        }
        assertTrue(repo.writes.isEmpty())
    }

    @Test fun capacityIncludesReservedDefinitionsAndNewIdsMustNotCollide() = runTest {
        val base = original()
        val definitions = base.value.counterDefinitions.toList() + (1..124).map {
            CounterDefinition(EntityId("extra-$it"), "Extra $it", null, null, 0, CounterLossPolicy.None, true)
        }
        val full = base.copy(value = base.value.copy(counterDefinitions = SnapshotList.copyOf(definitions)))
        val repo = DraftProfilesFixture(full); var allocated = 0
        val c = controller(repo) { allocated++; EntityId("fresh") }
        c.edit(full); c.showCounters(); c.addCounter()
        assertNull(c.state.value.editor!!.counterEditor); assertEquals(0, allocated)
        c.edit(full.copy(value = full.value.copy(counterDefinitions = SnapshotList.copyOf(definitions.take(127)))))
        c.showCounters(); c.addCounter(); c.changeCounterLabel("Last"); c.applyCounter()
        assertEquals(128, c.state.value.editor!!.counterDefinitions.size); c.addCounter()
        assertNull(c.state.value.editor!!.counterEditor); assertEquals(1, allocated)
        val collision = controller(repo) { EntityId("charge") }
        collision.edit(base); collision.showCounters(); collision.addCounter()
        assertNull(collision.state.value.editor!!.counterEditor)
        assertEquals(Problem.INTERNAL, collision.state.value.problem)
        assertEquals(base.value.counterDefinitions, collision.state.value.editor!!.counterDefinitions)
    }

    @Test fun dialogCancelDeleteConfirmationAndWholeCancelNeverWriteTheRepository() = runTest {
        val old = original(); val repo = DraftProfilesFixture(old); val c = controller(repo)
        c.edit(old); c.showCounters(); c.editCounter(EntityId("charge")); c.changeCounterInitial("9"); c.closeCounterEditor()
        assertEquals(old.value.counterDefinitions, c.state.value.editor!!.counterDefinitions)
        c.requestCounterDelete(EntityId("charge")); c.dismissCounterDelete()
        assertEquals(old.value.counterDefinitions, c.state.value.editor!!.counterDefinitions)
        c.requestCounterDelete(EntityId("charge")); c.confirmCounterDelete()
        assertEquals(old.value.counterDefinitions.filter { it.id != EntityId("charge") }, c.state.value.editor!!.counterDefinitions.toList())
        c.showProfile(); assertNotNull(c.state.value.editor); assertFalse(c.state.value.editor!!.counterManager)
        c.closeEditor(); assertNull(c.state.value.editor)
        assertEquals(old, repo.row); assertTrue(repo.writes.isEmpty()); assertEquals(0, repo.reads)
    }

    @Test fun failureAndStaleRevisionRetainCounterDraftAndOnlyReloadReplacesIt() = runTest {
        val old = original(); val repo = DraftProfilesFixture(old); val c = controller(repo)
        c.edit(old); c.showCounters(); c.editCounter(EntityId("charge")); c.changeCounterInitial("9"); c.applyCounter(); c.showProfile()
        val edited = c.state.value.editor!!.counterDefinitions
        for (status in listOf(failure(FailureCode.PERSISTENCE), Outcome.Unknown("Synthetic", SnapshotList.empty()),
            Outcome.Unavailable(UnavailableReason.OFFLINE, "Synthetic"))) {
            repo.writeStatus = status; c.save(); advanceUntilIdle()
            assertEquals(edited, c.state.value.editor!!.counterDefinitions); assertEquals(old, c.state.value.editor!!.original)
        }
        repo.writeStatus = null; repo.row = old.copy(revision = Revision(9), value = old.value.copy(name = "Other writer"))
        c.save(); advanceUntilIdle(); assertEquals(Problem.STALE_REVISION, c.state.value.problem)
        assertEquals(edited, c.state.value.editor!!.counterDefinitions); assertTrue(repo.expected.all { it == old.revision })
        c.showCounters(); c.addCounter(); c.reloadEditor(); advanceUntilIdle()
        assertEquals(repo.row, c.state.value.editor!!.original)
        assertEquals(repo.row.value.counterDefinitions, c.state.value.editor!!.counterDefinitions)
        assertFalse(c.state.value.editor!!.counterManager); assertNull(c.state.value.editor!!.counterEditor)
        assertNull(c.state.value.editor!!.pendingCounterDelete)
    }

    @Test fun busyWholeProfileSaveBlocksEveryConcurrentCounterMutation() = runTest {
        val old = original(); val repo = DraftProfilesFixture(old); val c = controller(repo)
        c.edit(old); repo.gate = CompletableDeferred(); c.save(); runCurrent()
        c.showCounters(); c.addCounter(); c.editCounter(EntityId("charge")); c.requestCounterDelete(EntityId("charge"))
        c.changeCounterLabel("Racing"); c.applyCounter(); c.confirmCounterDelete()
        assertFalse(c.state.value.editor!!.counterManager); assertNull(c.state.value.editor!!.counterEditor)
        assertNull(c.state.value.editor!!.pendingCounterDelete)
        assertEquals(old.value.counterDefinitions, c.state.value.editor!!.counterDefinitions)
        repo.gate!!.complete(Unit); advanceUntilIdle(); assertEquals(listOf(old.value), repo.writes)
    }
}

internal class DraftProfilesFixture(var row: PlayerProfileSnapshot): PlayerProfileRepository {
    val writes = mutableListOf<PlayerProfileDraft>()
    val expected = mutableListOf<Revision>()
    var writeStatus: Outcome<Nothing>? = null
    var gate: CompletableDeferred<Unit>? = null
    var reads = 0
    override suspend fun read(id: EntityId, cancel: CancelToken): Outcome<PlayerProfileSnapshot> { reads++; return Outcome.Success(row) }
    override suspend fun list(page: PageRequest, cancel: CancelToken): Outcome<Page<PlayerProfileSnapshot>> =
        Outcome.Success(Page(SnapshotList.of(row), null, SnapshotReference(ProviderId("synthetic-counter-profiles"), null, null, null)))
    override suspend fun create(value: PlayerProfileDraft, cancel: CancelToken): Outcome<PlayerProfileSnapshot> {
        writes += value; gate?.await(); cancel.throwIfCancelled(); writeStatus?.let { return it }
        row = PlayerProfileSnapshot(EntityId("created"), Revision(0), value); return Outcome.Success(row)
    }
    override suspend fun update(id: EntityId, value: PlayerProfileDraft, expected: Revision, cancel: CancelToken): Outcome<PlayerProfileSnapshot> {
        writes += value; this.expected += expected; gate?.await(); cancel.throwIfCancelled(); writeStatus?.let { return it }
        if (row.revision != expected) return failure(FailureCode.STALE_REVISION)
        row = PlayerProfileSnapshot(id, expected.next(), value); return Outcome.Success(row)
    }
    override suspend fun delete(id: EntityId, expected: Revision, cancel: CancelToken): Outcome<Unit> {
        writes += row.value
        return Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Whole-profile deletion is outside this fixture scenario")
    }
}
