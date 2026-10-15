package com.architectpsg.commandlibrary.data.storage.decks.codec

import com.architectpsg.commandlibrary.core.model.*
import org.junit.Assert.*
import org.junit.Test

class LocalDeckPatchCodecTest {
    private val codec = LocalDeckPatchCodec()
    private fun <T> ok(value: Outcome<T>): T = (value as Outcome.Success).value
    private fun bytes(value: DeckPatch) = ok(codec.encodePatch(value)).copyBytes()
    private fun decoded(value: ByteArray) = ok(codec.decodePatch(SnapshotBytes(value)))
    private fun golden(name: String): ByteArray = requireNotNull(javaClass.getResourceAsStream("/$name.hex"))
        .bufferedReader().use { it.readText().trim() }.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun corrupt(value: ByteArray) {
        assertEquals(FailureCode.CORRUPT_DATA, (codec.decodePatch(SnapshotBytes(value)) as Outcome.Failure).error.code)
    }
    @Test fun independentEmptyAndRenameFixturesMatchInBothDirections() {
        for ((name, patch) in listOf("patch-empty-v1" to DeckPatch(SnapshotList.empty(), ""),
            "patch-rename-v1" to DeckPatch(SnapshotList.of(DeckEdit.Rename("Renamed")), "User rename"))) {
            assertArrayEquals(golden(name), bytes(patch)); assertEquals(patch, decoded(golden(name)))
        }
    }
    @Test fun allPrimitiveVariantsPreserveNestedKnowledgeIdentityMoneyAndOrdering() {
        val unknown = Knowledge.Unknown("unresolved Ω", SnapshotList.of(Evidence("evidence", ResourceUri("https://example.invalid/証"), UtcTimestamp(-9, 7))))
        val labels = SnapshotList.of(EntryLabel(EntityId("empty"), "Empty"), EntryLabel(EntityId("used"), "Used"))
        val entry = DeckEntry(EntityId("e"), CardIdentity(unknown, Knowledge.Known(CardId(ProviderId("foreign"), "raw/絵")),
            ProviderIdentity(ProviderId("uninstalled"), "raw", "untouched"), Knowledge.Known("名前 🐉"), LanguageTag("ja")),
            Int.MAX_VALUE, BoardRole.COMMANDER, unknown, "notes", SnapshotList.of(labels[1]),
            SnapshotList.of(RoleOverride(EntityId("e"), unknown, "role", false, UtcTimestamp(9))))
        val patch = DeckPatch(SnapshotList.of(DeckEdit.Rename("Deck"), DeckEdit.Description("description"), DeckEdit.Primer("# primer"),
            DeckEdit.ReorderEntries(SnapshotList.of(EntityId("z"), EntityId("e"))), DeckEdit.Categories(labels),
            DeckEdit.Format(unknown), DeckEdit.Format(Knowledge.Absent("none")),
            DeckEdit.Format(Knowledge.Known(FormatId(ProviderId("formats"), "foreign"))),
            DeckEdit.Metadata(unknown, SnapshotList.of("b", "a", "b"), Knowledge.Known(DecimalAmount.exact("-0.001200", DecimalUnit.Native(ProviderId("price"), "N"))), EntityId("profile")),
            DeckEdit.Metadata(Knowledge.Absent("none"), SnapshotList.empty(), unknown, null),
            DeckEdit.Add(entry), DeckEdit.Replace(entry.copy(quantity=1, finish=Knowledge.Absent("unconstrained"))), DeckEdit.Remove(entry.id)), "Grouped reason")
        assertEquals(patch, decoded(bytes(patch))); assertArrayEquals(bytes(patch), bytes(patch))
    }
    @Test fun futureVersionsAreUnsupportedButUnknownTagsTruncationAndTrailingAreCorrupt() {
        val golden = golden("patch-rename-v1")
        for (size in 0 until golden.size) corrupt(golden.copyOf(size))
        corrupt(golden + byteArrayOf(0)); corrupt(golden.copyOf().also { it[0] = 0 })
        corrupt(golden.copyOf().also { it[10] = 10 }); corrupt(golden.copyOf().also { it[5] = 0 })
        assertEquals(UnavailableReason.UNSUPPORTED, (codec.decodePatch(SnapshotBytes(golden.copyOf().also { it[5] = 2 })) as Outcome.Unavailable).reason)
        corrupt(golden.copyOf().also { it[6] = -1; it[7] = -1; it[8] = -1; it[9] = -1 })
        corrupt(golden.copyOf().also { it[15] = -1 }) // malformed UTF8 in first name byte
    }
    @Test fun localCapabilityRejectsVersionOperationsAndBoundsWithoutTruncation() {
        assertEquals(UnavailableReason.UNSUPPORTED, (codec.encodePatch(DeckPatch(SnapshotList.of(DeckEdit.RestoreVersion(EntityId("v"))), "")) as Outcome.Unavailable).reason)
        val excessive = DeckPatch(SnapshotList.copyOf(List(LocalDeckEditLimits.MAX_EDITS + 1) { DeckEdit.Rename("a") }), "")
        assertEquals(FailureCode.INVALID_INPUT, (codec.encodePatch(excessive) as Outcome.Failure).error.code)
        for (patch in listOf(DeckPatch(SnapshotList.empty(), "x".repeat(DeckPayloadLimits.MAX_TEXT_BYTES + 1)),
            DeckPatch(SnapshotList.of(DeckEdit.Description("\ud800")), ""),
            DeckPatch(SnapshotList.of(DeckEdit.Categories(SnapshotList.copyOf(List(1025) { EntryLabel(EntityId("c$it"), "C") }))), ""))) {
            assertEquals(FailureCode.INVALID_INPUT, (codec.encodePatch(patch) as Outcome.Failure).error.code)
        }
        val exact = DeckPatch(SnapshotList.copyOf(List(1024) { DeckEdit.Rename("a") }), "")
        assertEquals(exact, decoded(bytes(exact)))
    }
}
