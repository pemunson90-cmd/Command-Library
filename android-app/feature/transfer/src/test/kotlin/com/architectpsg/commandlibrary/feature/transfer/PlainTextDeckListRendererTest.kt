package com.architectpsg.commandlibrary.feature.transfer

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PlainTextDeckListRendererTest {
    private val codec = PlainTextDeckListCodec()
    private val absent = Knowledge.Absent("Not supplied")
    private val unknown = Knowledge.Unknown("Unresolved", SnapshotList.of(Evidence("Identity evidence", null, null)))
    private val token = object : CancelToken {
        override val isCancelled = false
        override fun throwIfCancelled() = Unit
    }
    private fun entry(id: String, name: Knowledge<String> = Knowledge.Known("Name"), board: BoardRole = BoardRole.MAIN, quantity: Int = 1): DeckEntry = DeckEntry(
        EntityId(id), CardIdentity(unknown, absent, null, name, LanguageTag("fr")), quantity, board,
        unknown, "", SnapshotList.empty(), SnapshotList.empty(),
    )
    private fun deck(vararg entries: DeckEntry): DeckSnapshot = DeckSnapshot(
        DeckId(ProviderId("local"), "d"), "Deck name", absent, "", unknown, SnapshotList.empty(),
        UtcTimestamp(1), UtcTimestamp(2), Revision(3), SnapshotList.copyOf(entries.asList()), absent, null,
        SnapshotList.empty(), SnapshotList.empty(),
    )
    private fun success(deck: DeckSnapshot): PlainDeckListExport {
        val result = codec.render(deck, token)
        assertTrue("Expected success, got $result", result is Outcome.Success)
        return (result as Outcome.Success).value
    }
    private fun failure(deck: DeckSnapshot) {
        val result = codec.render(deck, token)
        assertTrue("Expected failure, got $result", result is Outcome.Failure)
        assertEquals(FailureCode.INVALID_INPUT, (result as Outcome.Failure).error.code)
        assertTrue(result.error.evidence.isEmpty())
    }
    private val baseLosses = listOf(
        PlainDeckListLossField.DECK_ID, PlainDeckListLossField.DECK_NAME, PlainDeckListLossField.FORMAT,
        PlainDeckListLossField.FOLDER, PlainDeckListLossField.CREATED_AT, PlainDeckListLossField.MODIFIED_AT,
        PlainDeckListLossField.REVISION, PlainDeckListLossField.BUDGET,
    )
    private val entryLosses = listOf(
        PlainDeckListLossField.ENTRY_ID, PlainDeckListLossField.ORACLE_ID, PlainDeckListLossField.PRINTING_ID,
        PlainDeckListLossField.LANGUAGE, PlainDeckListLossField.FINISH,
    )

    @Test fun emptyDeckGoldenAndKnowledgeLossesAreExact() {
        val exported = success(deck())
        assertArrayEquals("Command Library Deck List 1\n".toByteArray(), exported.bytes.copyBytes())
        assertEquals(baseLosses.map { PlainDeckListLoss(null, it) }, exported.losses)
    }

    @Test fun allBoardsHaveFixedGoldenWithWithinBoardOrderAndDuplicateNames() {
        val original = deck(
            entry("m1", Knowledge.Known("Éclair // (X)  "), quantity = 2),
            entry("s", Knowledge.Known("Sideboard"), BoardRole.SIDEBOARD),
            entry("c", Knowledge.Known("Commander"), BoardRole.COMMANDER),
            entry("p", Knowledge.Known("Partner"), BoardRole.PARTNER),
            entry("b", Knowledge.Known("Background"), BoardRole.BACKGROUND),
            entry("co", Knowledge.Known("Companion"), BoardRole.COMPANION),
            entry("mb", Knowledge.Known("Maybeboard"), BoardRole.MAYBEBOARD),
            entry("m2", Knowledge.Known("Éclair // (X)  "), quantity = Int.MAX_VALUE),
        )
        val result = success(original)
        assertArrayEquals(("Command Library Deck List 1\nCommander\n1 Commander\nPartner\n1 Partner\nBackground\n1 Background\nCompanion\n1 Companion\nDeck\n2 Éclair // (X)  \n2147483647 Éclair // (X)  \nSideboard\n1 Sideboard\nMaybeboard\n1 Maybeboard\n").toByteArray(), result.bytes.copyBytes())
        assertEquals(baseLosses.map { PlainDeckListLoss(null, it) } + PlainDeckListLoss(null, PlainDeckListLossField.ENTRY_ORDER) + original.entries.flatMap { e -> entryLosses.map { PlainDeckListLoss(e.id, it) } }, result.losses)
    }

    @Test fun richSnapshotDisclosesAll25FieldsInExactOriginalEntryOrder() {
        fun rich(e: DeckEntry) = e.copy(
            identity = e.identity.copy(raw = ProviderIdentity(ProviderId("foreign"), "printing", "opaque")),
            notes = "Notes", labels = SnapshotList.of(EntryLabel(EntityId("label"), "Label")),
            roleOverrides = SnapshotList.of(RoleOverride(null, unknown, "role", true, UtcTimestamp(1))),
        )
        val first = rich(entry("first", board = BoardRole.MAIN))
        val second = rich(entry("second", board = BoardRole.COMMANDER))
        val source = deck(first, second).copy(
            description = "Description", tags = SnapshotList.of("tag"), profileId = EntityId("profile"),
            catalog = SnapshotList.of(SnapshotReference(ProviderId("catalog"), null, null, null)),
            packageInstances = SnapshotList.of(PackageInstance(EntityId("instance"), EntityId("package"), EntityId("version"), PackageLinkPolicy.LINKED, SnapshotList.empty())),
            primer = "Primer", categoryOrder = SnapshotList.empty(),
        )
        val expectedDeck = listOf(
            PlainDeckListLossField.DECK_ID, PlainDeckListLossField.DECK_NAME, PlainDeckListLossField.FORMAT,
            PlainDeckListLossField.DESCRIPTION, PlainDeckListLossField.FOLDER, PlainDeckListLossField.TAGS,
            PlainDeckListLossField.CREATED_AT, PlainDeckListLossField.MODIFIED_AT, PlainDeckListLossField.REVISION,
            PlainDeckListLossField.BUDGET, PlainDeckListLossField.PROFILE_ID, PlainDeckListLossField.CATALOG_REFERENCES,
            PlainDeckListLossField.PACKAGE_INSTANCES, PlainDeckListLossField.PRIMER, PlainDeckListLossField.CATEGORY_ORDER,
            PlainDeckListLossField.ENTRY_ORDER,
        )
        val expectedEntry = listOf(
            PlainDeckListLossField.ENTRY_ID, PlainDeckListLossField.ORACLE_ID, PlainDeckListLossField.PRINTING_ID,
            PlainDeckListLossField.RAW_IDENTITY, PlainDeckListLossField.LANGUAGE, PlainDeckListLossField.FINISH,
            PlainDeckListLossField.NOTES, PlainDeckListLossField.LABELS, PlainDeckListLossField.ROLE_OVERRIDES,
        )
        assertEquals(expectedDeck.map { PlainDeckListLoss(null, it) } + listOf(first, second).flatMap { e -> expectedEntry.map { PlainDeckListLoss(e.id, it) } }, success(source).losses)
        assertEquals(34, success(source).losses.size)
    }

    @Test fun explicitEmptyCategoryRegistryIsLostAndNullIsNot() {
        assertFalse(success(deck()).losses.any { it.field == PlainDeckListLossField.CATEGORY_ORDER })
        assertTrue(success(deck().copy(categoryOrder = SnapshotList.empty())).losses.contains(PlainDeckListLoss(null, PlainDeckListLossField.CATEGORY_ORDER)))
    }

    @Test fun orderLossOnlyWhenStableBoardGroupingChangesActualSequence() {
        val ordered = deck(entry("a", board = BoardRole.COMMANDER), entry("b"), entry("c"), entry("d", board = BoardRole.MAYBEBOARD))
        assertFalse(success(ordered).losses.any { it.field == PlainDeckListLossField.ENTRY_ORDER })
        val swapped = ordered.copy(entries = SnapshotList.of(ordered.entries[1], ordered.entries[0], ordered.entries[2], ordered.entries[3]))
        assertTrue(success(swapped).losses.any { it.field == PlainDeckListLossField.ENTRY_ORDER })
    }

    @Test fun unrepresentableNamesAlwaysRefusedWithoutFallback() {
        for (name in listOf<Knowledge<String>>(absent, unknown, Knowledge.Known(""), Knowledge.Known(" \t"), Knowledge.Known(" Name"), Knowledge.Known("\tName"), Knowledge.Known("A\nB"), Knowledge.Known("A\rB"), Knowledge.Known("A\uD800"), Knowledge.Known("\uDC00"), Knowledge.Known("é".repeat(8193)))) {
            failure(deck(entry("e", name)))
        }
        assertArrayEquals("Command Library Deck List 1\nDeck\n1 \u2003Name\t  \n".toByteArray(), success(deck(entry("e", Knowledge.Known("\u2003Name\t  ")))).bytes.copyBytes())
        failure(deck(entry("e", Knowledge.Known("\u2003"))))
    }

    @Test fun duplicateEntryIdsRefusedButNamesAndBoundedHashCollisionsPreserved() {
        failure(deck(entry("same"), entry("same", board = BoardRole.SIDEBOARD)))
        assertEquals(2, success(deck(entry("Aa"), entry("BB"))).bytes.copyBytes().toString(Charsets.UTF_8).lineSequence().count { it == "1 Name" })
        val id = "é".repeat(512)
        assertNotNull(success(deck(entry(id))))
        failure(deck(entry(id + "x")))
        failure(deck(entry("bad\uD800")))
    }

    @Test fun omittedGraphsAndStringsAreNotRecursivelyValidated() {
        val oversizedMalformed = "\uD800".repeat(100000)
        val identity = entry("e").identity.copy(
            oracleId = Knowledge.Unknown(oversizedMalformed, SnapshotList.of(Evidence(oversizedMalformed, null, null))),
            raw = ProviderIdentity(ProviderId(oversizedMalformed), oversizedMalformed, oversizedMalformed),
            language = LanguageTag(oversizedMalformed),
        )
        val source = deck(entry("e").copy(identity = identity, notes = oversizedMalformed)).copy(
            id = DeckId(ProviderId(oversizedMalformed), oversizedMalformed), name = oversizedMalformed,
            description = oversizedMalformed, folder = Knowledge.Known(oversizedMalformed),
            format = Knowledge.Unknown(oversizedMalformed, SnapshotList.empty()), tags = SnapshotList.of(oversizedMalformed),
            primer = oversizedMalformed,
        )
        val result = success(source)
        assertArrayEquals("Command Library Deck List 1\nDeck\n1 Name\n".toByteArray(), result.bytes.copyBytes())
        assertTrue(result.losses.contains(PlainDeckListLoss(EntityId("e"), PlainDeckListLossField.RAW_IDENTITY)))
    }

    @Test fun exactNameEntryAndLossCeilings() {
        assertNotNull(success(deck(entry("e", Knowledge.Known("é".repeat(8192))))))
        val rich = entry("e").copy(
            identity = entry("e").identity.copy(raw = ProviderIdentity(ProviderId("p"), "k", "r")),
            notes = "n", labels = SnapshotList.of(EntryLabel(EntityId("l"), "l")),
            roleOverrides = SnapshotList.of(RoleOverride(null, absent, "r", true, UtcTimestamp(0))),
        )
        val entries = List(10000) { index -> rich.copy(id = EntityId("id$index"), board = if (index % 2 == 0) BoardRole.MAIN else BoardRole.COMMANDER) }
        val source = deck().copy(entries = SnapshotList.copyOf(entries), description = "d", tags = SnapshotList.of("t"), profileId = EntityId("p"),
            catalog = SnapshotList.of(SnapshotReference(ProviderId("p"), null, null, null)),
            packageInstances = SnapshotList.of(PackageInstance(EntityId("i"), EntityId("p"), EntityId("v"), PackageLinkPolicy.COPY, SnapshotList.empty())),
            primer = "p", categoryOrder = SnapshotList.empty())
        assertEquals(90016, success(source).losses.size)
        failure(source.copy(entries = SnapshotList.copyOf(entries + rich.copy(id = EntityId("last")))))
    }

    @Test fun outputByteCeilingCheckedIncludingMarkerHeadingSpacesAndFinalLf() {
        val prefix = "Command Library Deck List 1\nDeck\n".toByteArray().size
        val fullRows = 511
        val remainingName = 8 * 1024 * 1024 - prefix - fullRows * (16384 + 3) - 3
        assertTrue(remainingName in 1..16383)
        val entries = List(fullRows) { entry("id$it", Knowledge.Known("a".repeat(16384))) } + entry("last", Knowledge.Known("b".repeat(remainingName)))
        val source = deck().copy(entries = SnapshotList.copyOf(entries))
        val result = success(source)
        assertEquals(8 * 1024 * 1024, result.bytes.size)
        val tooLarge = entries.dropLast(1) + entry("last", Knowledge.Known("b".repeat(remainingName + 1)))
        failure(source.copy(entries = SnapshotList.copyOf(tooLarge)))
    }

    @Test fun cancellationBeforeDuringAndAfterImmutableConstructionPropagates() {
        val source = deck(entry("e", Knowledge.Known("a".repeat(16384))))
        for (stop in listOf(1, 2, 8, 20)) {
            var checks = 0
            val cancelling = object : CancelToken {
                override val isCancelled = false
                override fun throwIfCancelled() { if (++checks == stop) throw CancellationException("cancel") }
            }
            try { codec.render(source, cancelling); fail("Expected cancellation at $stop") } catch (_: CancellationException) { }
            assertEquals(stop, checks)
        }
        var checks = 0
        val observer = object : CancelToken {
            override val isCancelled = false
            override fun throwIfCancelled() { checks++ }
        }
        assertTrue(codec.render(deck(), observer) is Outcome.Success)
        val stopAtLast = checks
        var polls = 0
        val final = object : CancelToken {
            override val isCancelled = false
            override fun throwIfCancelled() { if (++polls == stopAtLast) throw CancellationException("final") }
        }
        try { codec.render(deck(), final); fail() } catch (_: CancellationException) { }
    }

    @Test fun generatedProjectionSupplementConservesEveryQuantityNameAndBoard() = runTest {
        val order = listOf(BoardRole.COMMANDER, BoardRole.PARTNER, BoardRole.BACKGROUND, BoardRole.COMPANION, BoardRole.MAIN, BoardRole.SIDEBOARD, BoardRole.MAYBEBOARD)
        repeat(35) { seed ->
            val entries = List(seed) { index -> entry("$seed-$index", Knowledge.Known("Né ${index % 4} // ($index)  "), BoardRole.entries[(seed * 3 + index * 5) % 7], 1 + index * 17) }
            val source = deck().copy(entries = SnapshotList.copyOf(entries))
            val exported = success(source)
            val input = TransferInput(EntityId("in"), "generated", absent, Knowledge.Known(exported.bytes.size.toLong()), flowOf(ByteChunk(0, 0, exported.bytes, null, true)))
            val parsed = codec.parse(input, token) as Outcome.Success
            assertTrue(parsed.value.complete)
            val expected = order.flatMap { board -> entries.filter { it.board == board }.map { PlainDeckListEntry(it.quantity, (it.identity.originalName as Knowledge.Known).value, board) } }
            assertEquals(expected, parsed.value.rows.mapNotNull { it.entry })
            assertEquals(entries.sumOf { it.quantity }, parsed.value.rows.sumOf { it.entry?.quantity ?: 0 })
        }
    }
}
