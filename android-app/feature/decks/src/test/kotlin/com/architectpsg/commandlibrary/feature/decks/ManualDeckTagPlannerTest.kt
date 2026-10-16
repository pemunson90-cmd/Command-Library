package com.architectpsg.commandlibrary.feature.decks

import com.architectpsg.commandlibrary.core.model.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic pure proposals, projected through the real reducer; durable SQL undo is a root gate. */
class ManualDeckTagPlannerTest {
    private val removal = PersonalTag(EntityId("removal"), "Removal", false)
    private val artifact = PersonalTag(EntityId("artifact"), "ArtifactSynergy", false)
    private val commander = PersonalTag(EntityId("commander"), "BetterWithCommander", false)
    private fun pool(vararg tags: PersonalTag) = PersonalTagPool(Revision(1), SnapshotList.of(*tags), UtcTimestamp(20))
    private fun ids(vararg values: String) = SnapshotList.copyOf(values.map(::EntityId))
    private fun apply(deck: DeckSnapshot, result: Outcome<DeckPatch>): DeckSnapshot =
        success(DeckPatchReducer.propose(deck, success(result), deck.revision, UtcTimestamp(30)))
    private fun assign(deck: DeckSnapshot, entries: SnapshotList<EntityId>, tags: SnapshotList<EntityId>) =
        ManualDeckTagPlanner.assign(deck, pool(removal, artifact, commander), entries, tags)

    @Test fun multipleRolesAndBulkConserveAllPhysicalAndManualFieldsInOneRevision() {
        val a = entry("a").copy(quantity = 4, board = BoardRole.COMMANDER,
            roleOverrides = SnapshotList.of(RoleOverride(EntityId("a"), Knowledge.Absent("raw"),
                "removal is a literal role string", false, UtcTimestamp(15))))
        val b = entry("b").copy(quantity = 2)
        val c = entry("c")
        val original = deck(a, b, c).copy(primer = "# Exact primer")
        val before = original.copy()
        val proposal = assign(original, ids("b", "a"), ids("removal", "artifact", "commander"))
        val patch = success(proposal)
        assertEquals(3, patch.edits.size)
        assertTrue(patch.edits[0] is DeckEdit.Categories)
        assertEquals(listOf(a.id, b.id), patch.edits.drop(1).map { (it as DeckEdit.Replace).entry.id })
        val result = apply(original, proposal)
        val labels = SnapshotList.of(EntryLabel(removal.id, removal.name), EntryLabel(artifact.id, artifact.name),
            EntryLabel(commander.id, commander.name))
        assertEquals(original.copy(entries = SnapshotList.of(a.copy(labels = labels), b.copy(labels = labels), c),
            categoryOrder = labels, revision = Revision(8), modifiedAt = UtcTimestamp(30)), result)
        assertEquals(listOf(4, 2, 1), result.entries.map { it.quantity })
        assertEquals(before, original)
        assertSame(c, result.entries[2])
        // One patch advances once, and the untouched original is an exact recovery snapshot.
        assertEquals(Revision(7), original.revision)
    }

    @Test fun poolRenameNeverRewritesLocalNamesOrEmptyDefinitionsAndAppendsInRequestedOrder() {
        val local = EntryLabel(removal.id, "Local removal name")
        val empty = EntryLabel(EntityId("empty"), "Empty but retained")
        val original = deck(entry("a"), entry("b").copy(labels = SnapshotList.of(local)))
            .copy(categoryOrder = SnapshotList.of(empty, local))
        val result = apply(original, assign(original, ids("a"), ids("commander", "removal", "artifact")))
        assertEquals(listOf(empty.id, removal.id, commander.id, artifact.id), result.categoryOrder!!.map { it.id })
        assertEquals(listOf(commander.name, local.name, artifact.name), result.entries[0].labels.map { it.name })
        assertSame(original.entries[1], result.entries[1])
        val retired = pool(removal.copy(retired = true))
        assertInvalid(ManualDeckTagPlanner.assign(original, retired, ids("a"), ids("removal")))
        assertEquals(original, deck(entry("a"), entry("b").copy(labels = SnapshotList.of(local)))
            .copy(categoryOrder = SnapshotList.of(empty, local)))
    }

    @Test fun legacyUnionUsesFirstAppearanceAndRemoveRetainsLastEmptyDefinition() {
        val a = EntryLabel(removal.id, "Local")
        val b = EntryLabel(artifact.id, artifact.name)
        val original = deck(entry("a").copy(labels = SnapshotList.of(b, a)), entry("b").copy(labels = SnapshotList.of(a)))
        val assigned = apply(original, assign(original, ids("b"), ids("commander")))
        assertEquals(listOf(b.id, a.id, commander.id), assigned.categoryOrder!!.map { it.id })
        val removed = apply(original, ManualDeckTagPlanner.remove(original, ids("a", "b"), ids("removal")))
        assertEquals(SnapshotList.of(b, a), removed.categoryOrder)
        assertEquals(SnapshotList.of(b), removed.entries[0].labels)
        assertTrue(removed.entries[1].labels.isEmpty())
        assertNull(original.categoryOrder)
        assertEquals(SnapshotList.of(b, a), original.entries[0].labels)
    }

    @Test fun removeAffectsOnlySelectedMembershipsAndNeedsNoLivePool() {
        val label = EntryLabel(removal.id, removal.name)
        val original = deck(entry("a").copy(labels = SnapshotList.of(label)), entry("b").copy(labels = SnapshotList.of(label)))
            .copy(categoryOrder = SnapshotList.of(label))
        val proposal = ManualDeckTagPlanner.remove(original, ids("a"), ids("removal"))
        assertEquals(1, success(proposal).edits.size)
        val result = apply(original, proposal)
        assertTrue(result.entries[0].labels.isEmpty())
        assertSame(original.entries[1], result.entries[1])
        assertEquals(original.categoryOrder, result.categoryOrder)
        assertInvalid(ManualDeckTagPlanner.remove(original, ids("a"), ids("unknown")))
    }

    @Test fun explicitCategoryRenameReorderRemovalNeverDeletesEntriesOrChangesPool() {
        val a = EntryLabel(removal.id, removal.name); val b = EntryLabel(artifact.id, artifact.name)
        val empty = EntryLabel(EntityId("empty"), "Empty")
        val original = deck(entry("a").copy(quantity = 4, labels = SnapshotList.of(a, b)))
            .copy(categoryOrder = SnapshotList.of(a, b, empty))
        val renamed = b.copy(name = "My artifact role")
        val proposal = ManualDeckTagPlanner.categories(original, SnapshotList.of(empty, renamed))
        assertEquals(1, success(proposal).edits.size)
        val result = apply(original, proposal)
        assertEquals(original.entries.single().copy(labels = SnapshotList.of(renamed)), result.entries.single())
        assertEquals(SnapshotList.of(empty, renamed), result.categoryOrder)
        assertInvalid(ManualDeckTagPlanner.categories(original, SnapshotList.of(EntryLabel(EntityId("new"), "New"))))
        assertInvalid(ManualDeckTagPlanner.categories(original, SnapshotList.of(a, a)))
    }

    @Test fun noOpsPreserveLegacyNullExplicitEmptyAndNeverInventRevision() {
        val label = EntryLabel(removal.id, removal.name)
        val legacy = deck(entry("a").copy(labels = SnapshotList.of(label)))
        val noOp = success(assign(legacy, ids("a"), ids("removal")))
        assertTrue(noOp.edits.isEmpty())
        assertSame(legacy, success(DeckPatchReducer.propose(legacy, noOp, legacy.revision, legacy.modifiedAt)))
        assertTrue(success(ManualDeckTagPlanner.categories(legacy, SnapshotList.of(label))).edits.isEmpty())
        val empty = deck(entry("a")).copy(categoryOrder = SnapshotList.empty())
        assertTrue(success(ManualDeckTagPlanner.categories(empty, SnapshotList.empty())).edits.isEmpty())
        val assigned = apply(empty, assign(empty, ids("a"), ids("removal")))
        assertEquals(SnapshotList.of(label), assigned.categoryOrder)
    }

    @Test fun selectionPoolCollisionsUnicodeNamesAndBoundsRejectWithoutMutation() {
        val original = deck(entry("a"))
        for ((entries, tags) in listOf(ids() to ids("removal"), ids("a") to ids(), ids("a", "a") to ids("removal"),
            ids("missing") to ids("removal"), ids("a") to ids("removal", "removal"), ids("a") to ids("unknown")))
            assertInvalid(assign(original, entries, tags))
        val invalidPools = listOf(pool(removal, removal), pool(removal, artifact.copy(name = "ＲＥＭＯＶＡＬ")),
            pool(removal).copy(revision = Revision(0)), pool(removal).copy(modifiedAt = null),
            pool(removal.copy(name = "bad\nname")), pool(removal.copy(name = "\uD800")),
            pool(removal.copy(name = "é".repeat(129))), pool(removal.copy(id = EntityId("i".repeat(1025)))),
            pool(removal.copy(id = EntityId("\uD800"))), pool(removal.copy(name = " ")),
            PersonalTagPool(Revision(1), SnapshotList.copyOf(List(4097) { PersonalTag(EntityId("t$it"), "Tag $it", false) }), UtcTimestamp(20)))
        for (pool in invalidPools) assertInvalid(ManualDeckTagPlanner.assign(original, pool, ids("a"), ids("removal")))
        val retiredCollision = pool(removal, artifact.copy(name = removal.name, retired = true))
        assertTrue(ManualDeckTagPlanner.assign(original, retiredCollision, ids("a"), ids("removal")) is Outcome.Success)
        assertEquals(deck(entry("a")), original)
    }

    @Test fun currentGraphConflictsAndExplicitRegistryViolationsReject() {
        val label = EntryLabel(removal.id, removal.name)
        val a = entry("a").copy(labels = SnapshotList.of(label))
        val conflict = deck(a, entry("b").copy(labels = SnapshotList.of(label.copy(name = "Conflict"))))
        assertInvalid(assign(conflict, ids("a"), ids("commander")))
        assertInvalid(assign(deck(a).copy(categoryOrder = SnapshotList.empty()), ids("a"), ids("commander")))
        val full = SnapshotList.copyOf(List(1024) { EntryLabel(EntityId("old$it"), "Old $it") })
        assertInvalid(assign(deck(entry("a")).copy(categoryOrder = full), ids("a"), ids("commander")))
        assertInvalid(ManualDeckTagPlanner.categories(deck(entry("a")), SnapshotList.copyOf(full + EntryLabel(EntityId("extra"), "Extra"))))
        assertInvalid(ManualDeckTagPlanner.categories(deck(a), SnapshotList.of(label.copy(name = "\t"))))
    }

    @Test fun groupLimitCountsCategoryEditAndNeverSilentlySplitsBulk() {
        val original = deck(*Array(1024) { entry("e$it") })
        val all = SnapshotList.copyOf(original.entries.map { it.id })
        assertInvalid(assign(original, all, ids("removal"))) // 1024 replacements + registry.
        val smaller = SnapshotList.copyOf(all.take(1023))
        assertEquals(1024, success(assign(original, smaller, ids("removal"))).edits.size)
        val label = EntryLabel(removal.id, removal.name)
        val explicit = original.copy(categoryOrder = SnapshotList.of(label))
        val proposal = assign(explicit, all, ids("removal"))
        assertEquals(1024, success(proposal).edits.size)
        assertEquals(Revision(8), apply(explicit, proposal).revision)
        assertTrue(original.entries.all { it.labels.isEmpty() })
    }

    @Test fun validPackageLineageIsExplicitlyUnsupportedEvenForNoOp() {
        val original = deck(entry("a")).copy(packageInstances = SnapshotList.of(PackageInstance(EntityId("instance"),
            EntityId("package"), EntityId("version"), PackageLinkPolicy.COPY,
            SnapshotList.of(PackageContribution(EntityId("source-entry"), EntityId("a"), 1)))))
        assertEquals(UnavailableReason.UNSUPPORTED, (assign(original, ids("a"), ids("removal")) as Outcome.Unavailable).reason)
        assertEquals(UnavailableReason.UNSUPPORTED, (ManualDeckTagPlanner.categories(original, SnapshotList.empty()) as Outcome.Unavailable).reason)
    }
}
