package com.architectpsg.commandlibrary.feature.decks

import com.architectpsg.commandlibrary.core.model.*
import org.junit.Assert.*
import org.junit.Test

class DeckEntryDiffTest {
    @Test fun reportsEveryEntryFieldUsingExactInputs() {
        val original = entry("a")
        val variants = listOf(
            original.copy(quantity = 2),
            original.copy(board = BoardRole.SIDEBOARD),
            original.copy(finish = Knowledge.Known(Finish.FOIL)),
            original.copy(notes = "other"),
            original.copy(labels = SnapshotList.of(EntryLabel(EntityId("x"), "draw"), EntryLabel(EntityId("y"), "ramp"))),
            original.copy(roleOverrides = SnapshotList.of(RoleOverride(original.id, Knowledge.Absent("raw"), "draw", true, UtcTimestamp(20)))),
            original.copy(identity = original.identity.copy(printingId = Knowledge.Known(CardId(ProviderId("foreign"), "specific")))),
            original.copy(identity = original.identity.copy(oracleId = Knowledge.Known(OracleId(ProviderId("foreign"), "oracle")))),
            original.copy(identity = original.identity.copy(raw = original.identity.raw!!.copy(provider = ProviderId("another")))),
            original.copy(identity = original.identity.copy(originalName = Knowledge.Known("other"))),
            original.copy(identity = original.identity.copy(language = LanguageTag("fr"))),
        )
        for (changed in variants) {
            val changes = success(DeckEntryDiff.compare(deck(original), deck(changed)))
            assertEquals(1, changes.size)
            assertSame(original, changes.single().before)
            assertSame(changed, changes.single().after)
            assertEquals(original.id, changes.single().entryId)
            assertTrue(changes.single().reason.isNotBlank())
        }
        val labelsOnly = variants[4]
        assertEquals(1, labelsOnly.quantity)
        assertEquals(1, success(DeckEntryDiff.compare(deck(original), deck(labelsOnly))).sumOf { it.after!!.quantity })
    }

    @Test fun changesAndRemovalsFollowBeforeOrderThenAdditionsFollowAfterOrder() {
        val a = entry("a")
        val b = entry("b")
        val c = entry("c")
        val d = entry("d")
        val changedB = b.copy(quantity = 4)
        val after = deck(d, changedB, c)
        val changes = success(DeckEntryDiff.compare(deck(a, b), after))
        assertEquals(listOf(a.id, b.id, d.id, c.id), changes.map { it.entryId })
        assertSame(a, changes[0].before)
        assertNull(changes[0].after)
        assertSame(b, changes[1].before)
        assertSame(changedB, changes[1].after)
        assertNull(changes[2].before)
        assertSame(d, changes[2].after)
    }

    @Test fun metadataAndReorderProduceNoEntryChanges() {
        val original = deck(entry("a"), entry("b"))
        val changed = original.copy(name = "renamed", description = "primer edit", revision = Revision(8), modifiedAt = UtcTimestamp(30), entries = SnapshotList.copyOf(original.entries.reversed()))
        assertTrue(success(DeckEntryDiff.compare(original, changed)).isEmpty())
        assertTrue(success(DeckEntryDiff.compare(original, original)).isEmpty())
    }

    @Test fun validatesDeckIdentityAndUniqueEntryIdsInBothInputs() {
        val original = deck(entry("a"))
        assertInvalid(DeckEntryDiff.compare(original, original.copy(id = original.id.copy(source = ProviderId("other")))))
        assertInvalid(DeckEntryDiff.compare(original, original.copy(id = original.id.copy(raw = "other"))))
        val duplicates = deck(entry("a"), entry("a"))
        assertInvalid(DeckEntryDiff.compare(duplicates, original))
        assertInvalid(DeckEntryDiff.compare(original, duplicates))
    }

    @Test fun preservesSeparateForeignEntriesWithSameNamesAndRawIds() {
        val a = entry("a")
        val b = entry("b").copy(identity = a.identity.copy(raw = a.identity.raw!!.copy(provider = ProviderId("other-source"))))
        val result = success(propose(deck(), DeckEdit.Add(a), DeckEdit.Add(b)))
        assertEquals(SnapshotList.of(a, b), result.entries)
        val changes = success(DeckEntryDiff.compare(deck(), result))
        assertEquals(2, changes.size)
        assertEquals(2, changes.sumOf { it.after!!.quantity })
        assertEquals(listOf(a.identity.raw, b.identity.raw), changes.map { it.after!!.identity.raw })
    }
}
