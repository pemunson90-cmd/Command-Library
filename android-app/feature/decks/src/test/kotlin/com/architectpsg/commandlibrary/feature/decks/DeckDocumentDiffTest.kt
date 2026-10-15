package com.architectpsg.commandlibrary.feature.decks

import com.architectpsg.commandlibrary.core.model.*
import org.junit.Assert.*
import org.junit.Test

class DeckDocumentDiffTest {
    @Test fun unchangedSnapshotsPreserveOriginalReferences() {
        val before = deck(entry("a"))
        for (after in listOf(before, before.copy())) {
            val result = success(DeckDocumentDiff.compare(before, after))
            assertSame(before, result.before)
            assertSame(after, result.after)
            assertTrue(result.changedFields.isEmpty())
            assertTrue(result.entries.isEmpty())
        }
    }

    @Test fun mapsEveryDocumentFieldIndependentlyAndInEnumOrder() {
        val before = deck(entry("a"), entry("b"))
        val variants = listOf(
            DeckChangedField.NAME to before.copy(name = " Deck "),
            DeckChangedField.FORMAT to before.copy(format = Knowledge.Known(FormatId(ProviderId("custom"), "format"))),
            DeckChangedField.DESCRIPTION to before.copy(description = "description"),
            DeckChangedField.PRIMER to before.copy(primer = "# Primer\n<script>raw</script>"),
            DeckChangedField.FOLDER to before.copy(folder = Knowledge.Absent("none")),
            DeckChangedField.TAGS to before.copy(tags = SnapshotList.of("other", "tag")),
            DeckChangedField.CREATED_AT to before.copy(createdAt = UtcTimestamp(10, 1)),
            DeckChangedField.MODIFIED_AT to before.copy(modifiedAt = UtcTimestamp(20, 1)),
            DeckChangedField.REVISION to before.copy(revision = Revision(8)),
            DeckChangedField.BUDGET to before.copy(budget = Knowledge.Known(DecimalAmount.exact("0.00", DecimalUnit.Tix))),
            DeckChangedField.PROFILE to before.copy(profileId = EntityId("profile")),
            DeckChangedField.CATALOG_REFERENCES to before.copy(catalog = SnapshotList.of(before.catalog.single().copy(generation = GenerationId("next")))),
            DeckChangedField.CATEGORIES to before.copy(categoryOrder = SnapshotList.empty()),
            DeckChangedField.PACKAGES to before.copy(packageInstances = SnapshotList.of(instance())),
            DeckChangedField.ENTRY_ORDER to before.copy(entries = SnapshotList.copyOf(before.entries.reversed())),
        )
        assertEquals(DeckChangedField.entries, variants.map { it.first })
        for ((field, after) in variants) {
            val result = success(DeckDocumentDiff.compare(before, after))
            assertEquals("mapping $field", listOf(field), result.changedFields)
            assertTrue(result.entries.isEmpty())
        }
        val after = variants[0].second.copy(
            format = variants[1].second.format, description = variants[2].second.description,
            primer = variants[3].second.primer, folder = variants[4].second.folder,
            tags = variants[5].second.tags, createdAt = variants[6].second.createdAt,
            modifiedAt = variants[7].second.modifiedAt, revision = variants[8].second.revision,
            budget = variants[9].second.budget, profileId = variants[10].second.profileId,
            catalog = variants[11].second.catalog, categoryOrder = variants[12].second.categoryOrder,
            packageInstances = variants[13].second.packageInstances, entries = variants[14].second.entries,
        )
        repeat(3) { assertEquals(DeckChangedField.entries, success(DeckDocumentDiff.compare(before, after)).changedFields) }
    }

    @Test fun descriptionPrimerAndOrderedCategoryRegistryAreIndependent() {
        val a = EntryLabel(EntityId("a"), "Draw")
        val b = EntryLabel(EntityId("b"), "Ramp")
        val legacy = deck()
        val explicit = legacy.copy(categoryOrder = SnapshotList.empty())
        assertEquals(listOf(DeckChangedField.CATEGORIES), success(DeckDocumentDiff.compare(legacy, explicit)).changedFields)
        val before = legacy.copy(categoryOrder = SnapshotList.of(a, b), description = "same", primer = "same")
        for (after in listOf(before.copy(categoryOrder = SnapshotList.of(b, a)), before.copy(categoryOrder = SnapshotList.of(a.copy(name = "draw"), b)))) {
            assertEquals(listOf(DeckChangedField.CATEGORIES), success(DeckDocumentDiff.compare(before, after)).changedFields)
        }
        assertEquals(listOf(DeckChangedField.DESCRIPTION), success(DeckDocumentDiff.compare(before, before.copy(description = "changed"))).changedFields)
        assertEquals(listOf(DeckChangedField.PRIMER), success(DeckDocumentDiff.compare(before, before.copy(primer = "changed"))).changedFields)
    }

    @Test fun entryContentAndReorderPreserveExactEntryDiffValues() {
        val a = entry("a")
        val b = entry("b")
        val variants = listOf(
            a.copy(quantity = 2), a.copy(board = BoardRole.COMMANDER),
            a.copy(identity = a.identity.copy(printingId = Knowledge.Known(CardId(ProviderId("foreign"), "printing")))),
            a.copy(finish = Knowledge.Known(Finish.FOIL)), a.copy(notes = "changed\nnotes"),
            a.copy(labels = SnapshotList.of(EntryLabel(EntityId("label"), "draw"))),
            a.copy(roleOverrides = SnapshotList.of(RoleOverride(a.id, Knowledge.Absent("raw"), "draw", true, UtcTimestamp(40)))),
        )
        val before = deck(a, b)
        for (changed in variants) {
            val after = deck(b, changed)
            val result = success(DeckDocumentDiff.compare(before, after))
            assertEquals(listOf(DeckChangedField.ENTRY_ORDER), result.changedFields)
            assertEquals(success(DeckEntryDiff.compare(before, after)), result.entries)
            assertSame(a, result.entries.single().before)
            assertSame(changed, result.entries.single().after)
        }
        assertTrue(success(DeckDocumentDiff.compare(before, deck(variants[0], b))).changedFields.isEmpty())
    }

    @Test fun additionsAndRemovalsChangeOrderAndFollowExistingEntryDiffOrdering() {
        val a = entry("a")
        val b = entry("b")
        val c = entry("c")
        val d = entry("d")
        val changedB = b.copy(quantity = 3)
        val before = deck(a, b)
        val after = deck(d, changedB, c)
        val result = success(DeckDocumentDiff.compare(before, after))
        assertEquals(listOf(DeckChangedField.ENTRY_ORDER), result.changedFields)
        assertEquals(success(DeckEntryDiff.compare(before, after)), result.entries)
        assertEquals(listOf(a.id, b.id, d.id, c.id), result.entries.map { it.entryId })
        assertNull(result.entries[0].after)
        assertNull(result.entries[2].before)
        assertEquals(listOf(DeckChangedField.ENTRY_ORDER), success(DeckDocumentDiff.compare(deck(a), deck())).changedFields)
        assertEquals(listOf(DeckChangedField.ENTRY_ORDER), success(DeckDocumentDiff.compare(deck(), deck(a))).changedFields)
    }

    @Test fun knowledgeReasonsEvidenceMoneyAndSourceQualifiedIdentitiesStayDistinct() {
        val before = deck(entry("a"))
        val evidence = SnapshotList.of(Evidence("source missing", ResourceUri("urn:source"), UtcTimestamp(1)))
        val moneyVariants: List<Knowledge<DecimalAmount>> = listOf(
            Knowledge.Unknown("unknown", evidence), Knowledge.Absent("other reason"),
            Knowledge.Known(DecimalAmount.exact("0", DecimalUnit.Tix)),
        )
        for (budget in moneyVariants) {
            assertEquals(listOf(DeckChangedField.BUDGET), success(DeckDocumentDiff.compare(before, before.copy(budget = budget))).changedFields)
        }
        val zero = before.copy(budget = Knowledge.Known(DecimalAmount.exact("0", DecimalUnit.Tix)))
        assertEquals(listOf(DeckChangedField.BUDGET), success(DeckDocumentDiff.compare(zero, zero.copy(budget = Knowledge.Known(DecimalAmount.exact("0.00", DecimalUnit.Tix))))).changedFields)
        val format = before.copy(format = Knowledge.Unknown("unknown", SnapshotList.empty()))
        assertEquals(listOf(DeckChangedField.FORMAT), success(DeckDocumentDiff.compare(format, format.copy(format = Knowledge.Unknown("unknown", evidence)))).changedFields)
        val original = before.entries.single()
        val foreign = original.copy(identity = original.identity.copy(raw = original.identity.raw!!.copy(provider = ProviderId("another-source"))))
        val result = success(DeckDocumentDiff.compare(before, deck(foreign)))
        assertEquals(1, result.entries.size)
        assertSame(original, result.entries.single().before)
        assertSame(foreign, result.entries.single().after)
        val distinct = foreign.copy(id = EntityId("b"))
        assertEquals(2, success(DeckDocumentDiff.compare(deck(), deck(original, distinct))).entries.size)
    }

    @Test fun packageLineageAndCatalogProvenanceCompareWithoutMutationAuthority() {
        val packageInstance = instance()
        val before = deck(entry("a")).copy(packageInstances = SnapshotList.of(packageInstance))
        assertTrue(success(DeckDocumentDiff.compare(before, before)).changedFields.isEmpty())
        for (changed in listOf(
            packageInstance.copy(versionId = EntityId("v2")),
            packageInstance.copy(policy = PackageLinkPolicy.LINKED),
            packageInstance.copy(contributions = SnapshotList.of(packageInstance.contributions.single().copy(quantity = 2))),
        )) {
            val after = before.copy(packageInstances = SnapshotList.of(changed))
            val result = success(DeckDocumentDiff.compare(before, after))
            assertEquals(listOf(DeckChangedField.PACKAGES), result.changedFields)
            assertSame(before, result.before)
            assertSame(after, result.after)
            assertSame(packageInstance, result.before.packageInstances.single())
            assertTrue(result.entries.isEmpty())
        }
        val reference = before.catalog.single()
        for (changed in listOf(reference.copy(source = ProviderId("other")), reference.copy(language = LanguageTag("fr")), reference.copy(revision = Revision(4)))) {
            assertEquals(listOf(DeckChangedField.CATALOG_REFERENCES), success(DeckDocumentDiff.compare(before, before.copy(catalog = SnapshotList.of(changed)))).changedFields)
        }
    }

    @Test fun propagatesEntryValidationFailuresInEitherInput() {
        val before = deck(entry("a"))
        val duplicates = deck(entry("a"), entry("a"))
        val overLimit = deck().copy(entries = SnapshotList.copyOf((0..10_000).map { entry("$it") }))
        val invalidPairs = listOf(
            before to before.copy(id = before.id.copy(source = ProviderId("other"))),
            before to before.copy(id = before.id.copy(raw = "other")),
            duplicates to before, before to duplicates,
            overLimit to before, before to overLimit,
        )
        for ((old, new) in invalidPairs) {
            val expected = DeckEntryDiff.compare(old, new)
            assertInvalid(expected)
            assertEquals(expected, DeckDocumentDiff.compare(old, new))
        }
        val atLimit = overLimit.copy(entries = SnapshotList.copyOf(overLimit.entries.take(10_000)))
        assertTrue(success(DeckDocumentDiff.compare(atLimit, atLimit)).entries.isEmpty())
    }

    private fun instance(): PackageInstance = PackageInstance(
        EntityId("instance"), EntityId("package"), EntityId("v1"), PackageLinkPolicy.COPY,
        SnapshotList.of(PackageContribution(EntityId("package-entry"), EntityId("a"), 1)),
    )
}
