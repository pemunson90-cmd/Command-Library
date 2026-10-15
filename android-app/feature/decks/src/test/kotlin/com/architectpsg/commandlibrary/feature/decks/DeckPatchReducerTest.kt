package com.architectpsg.commandlibrary.feature.decks

import com.architectpsg.commandlibrary.core.model.*
import org.junit.Assert.*
import org.junit.Test

class DeckPatchReducerTest {
    @Test fun primerAndDescriptionAreIndependentAndOldConstructorsRemainLegacy() {
        val original = deck(entry("a")).copy(description = "Summary")
        assertNull(original.categoryOrder)
        assertSame(original, success(propose(original)))
        val updated = success(propose(original, DeckEdit.Primer("# Primer\nCafé 😀")))
        assertEquals("Summary", updated.description)
        assertEquals("# Primer\nCafé 😀", updated.primer)
        assertEquals("", original.primer)
        assertEquals(Revision(8), updated.revision)
    }

    @Test fun explicitEmptyRegistryDoesNotReintroduceImplicitCategoriesLaterInBatch() {
        val label = EntryLabel(EntityId("x"), "X")
        val original = deck(entry("a").copy(labels = SnapshotList.of(label)))
        val cleared = success(propose(original, DeckEdit.Categories(SnapshotList.empty())))
        assertEquals(SnapshotList.empty<EntryLabel>(), cleared.categoryOrder)
        assertTrue(cleared.entries.single().labels.isEmpty())
        assertEquals(1, cleared.entries.single().quantity)
        assertNull(original.categoryOrder)
        assertInvalid(propose(original, DeckEdit.Categories(SnapshotList.empty()), DeckEdit.Add(entry("b").copy(labels = SnapshotList.of(label)))))
        assertInvalid(propose(cleared, DeckEdit.Add(entry("b").copy(labels = SnapshotList.of(label)))))
        assertEquals(SnapshotList.of(label), original.entries.single().labels)
    }

    @Test fun registryRenameRemovalAndEmptyCategoriesNeverDuplicateOrDeleteCardDemand() {
        val x = EntryLabel(EntityId("x"), "Old")
        val y = EntryLabel(EntityId("y"), "Remove")
        val empty = EntryLabel(EntityId("empty"), "Empty category")
        val first = entry("a").copy(quantity = 4, labels = SnapshotList.of(x, y))
        val second = entry("b").copy(labels = SnapshotList.of(y))
        val original = deck(first, second)
        val renamed = x.copy(name = " Renamed ")
        val result = success(propose(original, DeckEdit.Categories(SnapshotList.of(empty, renamed))))
        assertEquals(SnapshotList.of(empty, renamed), result.categoryOrder)
        assertEquals(SnapshotList.of(renamed), result.entries[0].labels)
        assertTrue(result.entries[1].labels.isEmpty())
        assertEquals(original.entries.map { it.quantity }, result.entries.map { it.quantity })
        assertEquals(original.entries.map { it.identity }, result.entries.map { it.identity })
        val added = entry("c").copy(labels = SnapshotList.of(renamed))
        assertEquals(added, success(propose(original, DeckEdit.Categories(SnapshotList.of(renamed)), DeckEdit.Add(added))).entries.last())
    }

    @Test fun reorderingRequiresAnExactPermutationAndPreservesEntryObjectsAndFields() {
        val a = entry("a"); val b = entry("b"); val c = entry("c")
        val original = deck(a, b, c)
        val reordered = success(propose(original, DeckEdit.ReorderEntries(SnapshotList.of(c.id, a.id, b.id))))
        assertEquals(SnapshotList.of(c, a, b), reordered.entries)
        assertSame(c, reordered.entries[0]); assertSame(a, reordered.entries[1])
        for (order in listOf(SnapshotList.of(a.id, b.id), SnapshotList.of(a.id, a.id, c.id), SnapshotList.of(a.id, b.id, EntityId("foreign")))) {
            assertInvalid(propose(original, DeckEdit.Primer("Changed draft"), DeckEdit.ReorderEntries(order)))
            assertEquals("", original.primer)
        }
    }

    @Test fun replacementMustUseCurrentRegistryNamesAfterAnEarlierRename() {
        val old = EntryLabel(EntityId("x"), "Old")
        val renamed = old.copy(name = "New")
        val original = deck(entry("a").copy(labels = SnapshotList.of(old)))
            .copy(categoryOrder = SnapshotList.of(old))
        val replacement = original.entries.single().copy(quantity = 3, labels = SnapshotList.of(renamed))
        val accepted = success(propose(original, DeckEdit.Categories(SnapshotList.of(renamed)), DeckEdit.Replace(replacement)))
        assertEquals(replacement, accepted.entries.single())
        assertEquals(SnapshotList.of(renamed), accepted.categoryOrder)
        assertInvalid(propose(original, DeckEdit.Categories(SnapshotList.of(renamed)), DeckEdit.Replace(replacement.copy(labels = SnapshotList.of(old)))))
        assertInvalid(propose(original, DeckEdit.Categories(SnapshotList.of(renamed)), DeckEdit.Replace(replacement.copy(labels = SnapshotList.of(EntryLabel(EntityId("other"), "Other"))))))
        assertEquals(SnapshotList.of(old), original.categoryOrder)
        assertEquals(1, original.entries.single().quantity)
    }

    @Test fun conflictingOrOversizedCategoryDefinitionsRejectWithoutCanonicalizingLegacyInput() {
        val x = EntryLabel(EntityId("x"), "X")
        val a = entry("a").copy(labels = SnapshotList.of(x))
        val b = entry("b").copy(labels = SnapshotList.of(x.copy(name = "Different")))
        assertInvalid(propose(deck(a, b)))
        assertInvalid(propose(deck(a).copy(categoryOrder = SnapshotList.empty())))
        assertInvalid(propose(deck(a), DeckEdit.Categories(SnapshotList.of(x, x))))
        assertInvalid(propose(deck(a), DeckEdit.Categories(SnapshotList.of(x.copy(name = " ")))))
        val tooMany = SnapshotList.copyOf(List(DeckPayloadLimits.MAX_CATEGORIES + 1) { EntryLabel(EntityId("c$it"), "Category $it") })
        assertInvalid(propose(deck(), DeckEdit.Categories(tooMany)))
        assertInvalid(propose(deck(entry("many").copy(labels = tooMany))))
        val legacy = deck(a)
        assertSame(legacy, success(propose(legacy)))
        assertNull(success(propose(legacy, DeckEdit.Add(entry("new").copy(labels = SnapshotList.of(EntryLabel(EntityId("new-label"), "New")))))).categoryOrder)
    }

    @Test fun sequentialEditsPreserveExactFieldsAndAdvanceOnce() {
        val first = entry("a")
        val second = entry("b")
        val original = deck(first, second)
        val replacement = first.copy(quantity = 3, board = BoardRole.COMMANDER)
        val added = entry("c")
        val metadata = DeckEdit.Metadata(Knowledge.Known("  folder  "), SnapshotList.of("tag", "tag"), Knowledge.Absent("no budget"), EntityId("profile"))
        val edits = listOf(DeckEdit.Rename("  New name  "), DeckEdit.Description("# primer\n全文"), DeckEdit.Format(Knowledge.Known(FormatId(ProviderId("foreign"), "custom"))), metadata, DeckEdit.Replace(replacement), DeckEdit.Remove(second.id), DeckEdit.Add(added))
        val result = success(propose(original, *edits.toTypedArray()))
        assertEquals(original.copy(name = "  New name  ", description = "# primer\n全文", format = (edits[2] as DeckEdit.Format).format, folder = metadata.folder, tags = metadata.tags, budget = metadata.budget, profileId = metadata.profileId, entries = SnapshotList.of(replacement, added), revision = Revision(8), modifiedAt = UtcTimestamp(30)), result)
        assertEquals(SnapshotList.of(first, second), original.entries)
        assertSame(first.identity, result.entries[0].identity)
    }

    @Test fun replacementKeepsPositionAndSequentialRemoveThenAddAppends() {
        val a = entry("a")
        val b = entry("b").copy(labels = SnapshotList.of(EntryLabel(EntityId("label"), " 原文 ")), roleOverrides = SnapshotList.of(RoleOverride(EntityId("b"), Knowledge.Absent("raw"), "draw", false, UtcTimestamp(15)), RoleOverride(null, Knowledge.Absent("raw"), "ramp", true, UtcTimestamp(16))))
        val c = entry("c")
        val changedB = b.copy(notes = "edited")
        val replaced = success(propose(deck(a, b, c), DeckEdit.Replace(changedB)))
        assertEquals(SnapshotList.of(a, changedB, c), replaced.entries)
        assertSame(a, replaced.entries[0])
        assertSame(c, replaced.entries[2])
        assertSame(b.labels, replaced.entries[1].labels)
        assertSame(b.roleOverrides, replaced.entries[1].roleOverrides)
        val readded = success(propose(deck(a, b, c), DeckEdit.Remove(b.id), DeckEdit.Add(changedB), DeckEdit.Replace(changedB.copy(quantity = 2))))
        assertEquals(listOf(a.id, c.id, b.id), readded.entries.map { it.id })
        assertEquals(2, readded.entries.last().quantity)
        assertEquals(Revision(8), readded.revision)
    }

    @Test fun lateInvalidEditLeavesOriginalExact() {
        val original = deck(entry("a"))
        val saved = original.copy()
        assertInvalid(propose(original, DeckEdit.Rename("changed"), DeckEdit.Replace(entry("a").copy(quantity = 5)), DeckEdit.Remove(EntityId("missing"))))
        assertEquals(saved, original)
        assertEquals(1, original.entries.single().quantity)
    }

    @Test fun emptyPatchReturnsSameSnapshotAfterChecksEvenAtRevisionLimit() {
        val original = deck(entry("a")).copy(revision = Revision(Long.MAX_VALUE))
        assertSame(original, success(propose(original)))
        assertEquals(FailureCode.STALE_REVISION, failure(DeckPatchReducer.propose(original, patch(), Revision(0), UtcTimestamp(30))).code)
        assertInvalid(DeckPatchReducer.propose(original, patch(), original.revision, UtcTimestamp(19)))
        assertEquals(FailureCode.PERSISTENCE, failure(propose(original, DeckEdit.Description("edit"))).code)
    }

    @Test fun timestampValidationIncludesNanosAndBothExistingTimes() {
        val original = deck().copy(createdAt = UtcTimestamp(25, 2), modifiedAt = UtcTimestamp(20, 5))
        assertInvalid(DeckPatchReducer.propose(original, patch(DeckEdit.Rename("ok")), original.revision, UtcTimestamp(25, 1)))
        val normal = deck().copy(modifiedAt = UtcTimestamp(20, 5))
        assertInvalid(DeckPatchReducer.propose(normal, patch(), normal.revision, UtcTimestamp(20, 4)))
        assertSame(normal, success(DeckPatchReducer.propose(normal, patch(), normal.revision, UtcTimestamp(20, 5))))
    }

    @Test fun rejectsInvalidEntryOwnershipAndDuplicateIds() {
        val a = entry("a")
        val label = EntryLabel(EntityId("label"), "name")
        val duplicateLabels = a.copy(labels = SnapshotList.of(label, label.copy(name = "other")))
        val wrongRole = a.copy(roleOverrides = SnapshotList.of(RoleOverride(EntityId("b"), Knowledge.Absent("unknown"), "draw", true, UtcTimestamp(10))))
        assertInvalid(propose(deck(a, a)))
        assertInvalid(propose(deck(duplicateLabels)))
        assertInvalid(propose(deck(wrongRole)))
        assertInvalid(propose(deck(a), DeckEdit.Add(a)))
        assertInvalid(propose(deck(a), DeckEdit.Replace(entry("missing"))))
        assertInvalid(propose(deck(), DeckEdit.Add(duplicateLabels)))
        assertInvalid(propose(deck(a), DeckEdit.Replace(wrongRole)))
        assertInvalid(propose(deck(a), DeckEdit.Remove(EntityId("missing"))))
        assertInvalid(propose(deck().copy(name = "  ")))
        assertInvalid(propose(deck(), DeckEdit.Rename("\n\t")))
    }

    @Test fun enforcesBoundsWithoutTruncation() {
        val full = deck(*Array(10_000) { entry("$it") })
        assertSame(full, success(propose(full)))
        assertInvalid(propose(full, DeckEdit.Add(entry("extra"))))
        assertInvalid(propose(full.copy(entries = SnapshotList.copyOf(full.entries + entry("extra")))))
        assertInvalid(propose(deck(), *Array(1_025) { DeckEdit.Description("$it") }))
        assertEquals("1023", success(propose(deck(), *Array(1_024) { DeckEdit.Description("$it") })).description)
    }

    @Test fun unsupportedOperationsAndPackageLineageRejectEntireBatch() {
        val original = deck(entry("a"))
        val version = PackageVersion(EntityId("version"), EntityId("package"), 1, UtcTimestamp(10), SnapshotList.of(entry("a")), "notes")
        val preview = PackagePreview(EntityId("preview"), original.id, original.revision, PackageAction.Add(version, PackageLinkPolicy.LINKED), SnapshotList.empty(), SnapshotList.empty(), SnapshotList.empty())
        for (unsupported in listOf(DeckEdit.RestoreVersion(EntityId("version")), DeckEdit.ApplyPackage(preview))) {
            val result = propose(original, DeckEdit.Rename(""), unsupported)
            assertEquals(UnavailableReason.UNSUPPORTED, (result as Outcome.Unavailable).reason)
        }
        val instance = PackageInstance(EntityId("instance"), version.packageId, version.id, PackageLinkPolicy.COPY, SnapshotList.of(PackageContribution(EntityId("package-entry"), EntityId("a"), 1)))
        val linked = original.copy(packageInstances = SnapshotList.of(instance))
        assertEquals(UnavailableReason.UNSUPPORTED, (propose(linked) as Outcome.Unavailable).reason)
        assertEquals(UnavailableReason.UNSUPPORTED, (propose(linked, DeckEdit.Remove(EntityId("a"))) as Outcome.Unavailable).reason)
        assertEquals(SnapshotList.of(instance), linked.packageInstances)
        assertEquals("Deck", original.name)
    }
}

internal fun entry(id: String): DeckEntry = DeckEntry(EntityId(id), CardIdentity(Knowledge.Unknown("unmapped", SnapshotList.empty()), Knowledge.Unknown("not installed", SnapshotList.empty()), ProviderIdentity(ProviderId("foreign"), "printing", "生/Raw:$id"), Knowledge.Known(" É原文 "), LanguageTag("ja")), 1, BoardRole.MAIN, Knowledge.Unknown("unresolved", SnapshotList.empty()), "note\n原文", SnapshotList.empty(), SnapshotList.empty())
internal fun deck(vararg entries: DeckEntry): DeckSnapshot = DeckSnapshot(DeckId(ProviderId("local"), "deck"), "Deck", Knowledge.Unknown("custom", SnapshotList.empty()), "primer", Knowledge.Known("folder"), SnapshotList.of("tag"), UtcTimestamp(10), UtcTimestamp(20), Revision(7), SnapshotList.of(*entries), Knowledge.Absent("none"), null, SnapshotList.of(SnapshotReference(ProviderId("foreign"), GenerationId("gen"), Revision(3), LanguageTag("ja"))), SnapshotList.empty())
internal fun patch(vararg edits: DeckEdit): DeckPatch = DeckPatch(SnapshotList.of(*edits), "edit")
internal fun propose(original: DeckSnapshot, vararg edits: DeckEdit): Outcome<DeckSnapshot> = DeckPatchReducer.propose(original, patch(*edits), original.revision, UtcTimestamp(30))
internal fun <T> success(outcome: Outcome<T>): T = (outcome as Outcome.Success).value
internal fun failure(outcome: Outcome<*>): DomainFailure = (outcome as Outcome.Failure).error
internal fun assertInvalid(outcome: Outcome<*>) { assertEquals(FailureCode.INVALID_INPUT, failure(outcome).code) }
