package com.architectpsg.commandlibrary.data.storage.decks.codec

import com.architectpsg.commandlibrary.core.model.*
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.Assert.*
import org.junit.Test

class DeckPayloadCodecTest {
    private val codec = DeckPayloadCodec()

    @Test fun independentGoldenDecodesAndEncodesExactly() {
        assertEquals(125, golden().size)
        assertEquals(minimal(), decoded(golden()))
        assertArrayEquals(golden(), encoded(minimal()))
    }

    @Test fun richSnapshotPreservesEveryFieldAndOrderedValue() {
        val evidence = SnapshotList.of(
            Evidence("source Ω", ResourceUri("https://example.invalid/参考"), UtcTimestamp(-9, 123)),
            Evidence("no URI or time", null, null),
        )
        val unknown = Knowledge.Unknown("unresolved 原", evidence)
        val first = DeckEntry(
            EntityId("entry α"),
            CardIdentity(unknown, Knowledge.Known(CardId(ProviderId("foreign"), "raw / 絵")),
                ProviderIdentity(ProviderId("uninstalled"), "foreign kind", "untouched raw"),
                Knowledge.Known("元の名前 🐉"), LanguageTag("ja")),
            Int.MAX_VALUE, BoardRole.COMMANDER, Knowledge.Known(Finish.ETCHED), "notes\nΩ",
            SnapshotList.of(EntryLabel(EntityId("b"), "B"), EntryLabel(EntityId("a"), "A")),
            SnapshotList.of(
                RoleOverride(EntityId("entry α"), Knowledge.Known(OracleId(ProviderId("oracle"), "o")), "role", true, UtcTimestamp(7, 9)),
                RoleOverride(null, unknown, "disabled", false, UtcTimestamp(8)),
            ),
        )
        val second = first.copy(id = EntityId("second"),
            identity = CardIdentity(Knowledge.Known(OracleId(ProviderId("other"), "0")), Knowledge.Absent("no printing"), null, Knowledge.Absent("no name"), LanguageTag("en")),
            quantity = 1, board = BoardRole.SIDEBOARD, finish = unknown,
            labels = SnapshotList.empty(), roleOverrides = SnapshotList.empty())
        val third = second.copy(id = EntityId("third"), identity = second.identity.copy(oracleId = Knowledge.Absent("no oracle"), printingId = unknown, originalName = unknown), finish = Knowledge.Absent("no finish"))
        val value = minimal().copy(
            name = "Deck 🐉", format = Knowledge.Known(FormatId(ProviderId("format source"), "foreign format")),
            description = "description", primer = "# Separate primer\nΩ", folder = unknown,
            tags = SnapshotList.of("z", "a", "z", ""), createdAt = UtcTimestamp(Long.MIN_VALUE, 999_999_999),
            modifiedAt = UtcTimestamp(Long.MAX_VALUE), revision = Revision(Long.MAX_VALUE),
            entries = SnapshotList.of(first, second, third), profileId = EntityId("profile"),
            budget = Knowledge.Known(DecimalAmount.exact("-0.001200", DecimalUnit.Native(ProviderId("money source"), "符号"))),
            catalog = SnapshotList.of(SnapshotReference(ProviderId("catalog2"), GenerationId("pinned"), Revision(0), LanguageTag("ja")), SnapshotReference(ProviderId("catalog1"), null, null, null)),
            packageInstances = SnapshotList.of(
                PackageInstance(EntityId("instance"), EntityId("package"), EntityId("version"), PackageLinkPolicy.LINKED, SnapshotList.of(PackageContribution(EntityId("foreign package entry"), first.id, 4))),
                PackageInstance(EntityId("copy"), EntityId("package2"), EntityId("version2"), PackageLinkPolicy.COPY, SnapshotList.empty()),
            ), categoryOrder = SnapshotList.of(EntryLabel(EntityId("a"), "A"), EntryLabel(EntityId("empty"), "Empty"), EntryLabel(EntityId("b"), "B")),
        )
        assertEquals(value, decoded(encoded(value)))
        assertArrayEquals(encoded(value), encoded(value))
    }

    @Test fun nullAndExplicitEmptyCategoriesRemainDifferent() {
        val legacy = minimal()
        val explicit = legacy.copy(categoryOrder = SnapshotList.empty())
        assertFalse(encoded(legacy).contentEquals(encoded(explicit)))
        assertNull(decoded(encoded(legacy)).categoryOrder)
        assertEquals(SnapshotList.empty<EntryLabel>(), decoded(encoded(explicit)).categoryOrder)
    }

    @Test fun knowledgeVariantsMoneyUnitsAndHugeScaleAreExact() {
        val amounts = listOf(
            DecimalAmount.exact("0.000", DecimalUnit.Fiat("USD")),
            DecimalAmount.exact("1200", DecimalUnit.Tix),
            DecimalAmount(BigDecimal(BigInteger.ONE, Int.MAX_VALUE), DecimalUnit.Native(ProviderId("native"), "N")),
        )
        amounts.forEach { amount ->
            val value = minimal().copy(format = Knowledge.Unknown("format?", SnapshotList.empty()), folder = Knowledge.Known(""), budget = Knowledge.Known(amount))
            assertEquals(value, decoded(encoded(value)))
            assertTrue(encoded(value).size < 512)
        }
        val value = minimal().copy(budget = Knowledge.Unknown("no quote", SnapshotList.of(Evidence("e", null, null))))
        assertEquals(value, decoded(encoded(value)))
    }

    @Test fun allStableEnumNamesRoundTrip() {
        BoardRole.entries.forEach { board -> Finish.entries.forEach { finish ->
            val value = minimal().copy(entries = SnapshotList.of(entry().copy(board = board, finish = Knowledge.Known(finish))))
            assertEquals(value, decoded(encoded(value)))
        } }
    }

    @Test fun structuralCodecDoesNotImposeGraphMutationAuthority() {
        val duplicate = entry()
        val value = minimal().copy(entries = SnapshotList.of(duplicate, duplicate), categoryOrder = SnapshotList.empty())
        assertEquals(value, decoded(encoded(value)))
    }

    private fun encoded(value: DeckSnapshot) = (codec.encodeSnapshot(value) as Outcome.Success).value.copyBytes()
    private fun decoded(bytes: ByteArray) = (codec.decodeSnapshot(SnapshotBytes(bytes)) as Outcome.Success).value
}

internal fun minimal(): DeckSnapshot = DeckSnapshot(
    DeckId(ProviderId("local"), "golden-deck"), "Golden", Knowledge.Absent("none"), "", Knowledge.Absent("none"),
    SnapshotList.empty(), UtcTimestamp(1), UtcTimestamp(1), Revision(0), SnapshotList.empty(), Knowledge.Absent("none"), null,
    SnapshotList.empty(), SnapshotList.empty(),
)
internal fun entry(): DeckEntry = DeckEntry(EntityId("e"), CardIdentity(Knowledge.Absent("none"), Knowledge.Absent("none"), null, Knowledge.Absent("none"), LanguageTag("en")), 1, BoardRole.MAIN, Knowledge.Absent("none"), "", SnapshotList.empty(), SnapshotList.empty())
internal fun golden(): ByteArray = DeckPayloadCodecTest::class.java.getResourceAsStream("/deck-codec/minimal-v1.hex")!!.bufferedReader().use { it.readText().trim() }.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
