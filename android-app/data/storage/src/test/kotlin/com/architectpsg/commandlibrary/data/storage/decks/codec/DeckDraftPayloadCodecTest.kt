package com.architectpsg.commandlibrary.data.storage.decks.codec

import com.architectpsg.commandlibrary.core.model.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class DeckDraftPayloadCodecTest {
    private val codec = DeckDraftPayloadCodec()
    private fun details() = DeckDraftBody.Details(Revision(7), "", "x", "Ω\uD800")
    private fun entry() = DeckDraftBody.Entry(Revision(0), EntityId("entry"), "-", "\uDFFF", BoardRole.MAIN, Knowledge.Absent("none"), SnapshotList.empty())
    private fun add() = DeckDraftBody.Add(Revision(1), "1", "", BoardRole.MAYBEBOARD, Knowledge.Unknown("?", SnapshotList.empty()), SnapshotList.empty(), null)
    private fun remove() = DeckDraftBody.Remove(Revision(2), EntityId("entry"))
    private fun <T> ok(result: Outcome<T>): T { assertTrue(result.toString(), result is Outcome.Success); return (result as Outcome.Success).value }
    private fun encoded(body: DeckDraftBody) = ok(codec.encodeDraft(body)).copyBytes()
    private fun decoded(bytes: ByteArray) = ok(codec.decodeDraft(SnapshotBytes(bytes)))
    private fun failCode(result: Outcome<*>, code: FailureCode) { assertTrue(result.toString(), result is Outcome.Failure); assertEquals(code, (result as Outcome.Failure).error.code) }
    private fun corrupt(bytes: ByteArray) = failCode(codec.decodeDraft(SnapshotBytes(bytes)), FailureCode.CORRUPT_DATA)
    private fun invalid(body: DeckDraftBody) = failCode(codec.encodeDraft(body), FailureCode.INVALID_INPUT)
    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun goldens(): List<Pair<String, ByteArray>> {
        val bytes = javaClass.getResourceAsStream("/decks/draft-v1-oracles.json")!!.use { it.readBytes() }
        assertEquals("a893f08249601b09151829b801c0b0e89fc7a9a00fa5c1ac6de854e177a3e580", sha(bytes))
        val fields = Regex("\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\",\\s*\\\"hex\\\"\\s*:\\s*\\\"([0-9a-f]+)\\\",\\s*\\\"sha256\\\"\\s*:\\s*\\\"([0-9a-f]+)\\\"")
        return fields.findAll(bytes.toString(Charsets.UTF_8)).map { match ->
            val payload = hex(match.groupValues[2]); assertEquals(match.groupValues[3], sha(payload)); match.groupValues[1] to payload
        }.toList().also { assertEquals(4, it.size) }
    }
    @Test fun fourIndependentPythonGoldensDecodeAndEncodeExactly() {
        val expected = listOf(details(), entry(), add(), remove())
        goldens().forEachIndexed { index, (name, bytes) ->
            assertEquals(name, expected[index], decoded(bytes)); assertArrayEquals(name, bytes, encoded(expected[index]))
        }
    }
    @Test fun allModesAndStableBoardFinishNamesRoundTrip() {
        for (board in BoardRole.entries) for (finish in Finish.entries) {
            val body = entry().copy(board = board, finish = Knowledge.Known(finish))
            assertEquals(body, decoded(encoded(body)))
            assertEquals(add().copy(board = board, finish = Knowledge.Known(finish)), decoded(encoded(add().copy(board = board, finish = Knowledge.Known(finish)))))
        }
        assertEquals(remove(), decoded(encoded(remove()))); assertEquals(details(), decoded(encoded(details())))
    }
    @Test fun richSelectionGenerationPrintingLabelsAndNestedEvidenceRemainExact() {
        val evidence = SnapshotList.of(Evidence("source Ω", ResourceUri("https://example.invalid/参考"), UtcTimestamp(-9, 123)), Evidence("no location", null, null))
        val identity = CardIdentity(Knowledge.Known(OracleId(ProviderId("unknown-source"), "oracle/界")), Knowledge.Unknown("printing uncertain", evidence),
            ProviderIdentity(ProviderId("uninstalled"), "kind Ω", "raw / same"), Knowledge.Unknown("unknown name", evidence), LanguageTag("ja"))
        val body = add().copy(quantity = "001", notes = "\uD800\u0000\uDFFF", board = BoardRole.COMMANDER, finish = Knowledge.Known(Finish.ETCHED),
            labels = SnapshotList.of(EntryLabel(EntityId("z"), "Z"), EntryLabel(EntityId("a"), "A")), selection = DeckDraftSelection(identity, GenerationId("pinned 界")))
        assertEquals(body, decoded(encoded(body))); assertArrayEquals(encoded(body), encoded(body))
        assertEquals(body.copy(selection = body.selection!!.copy(generation = null)), decoded(encoded(body.copy(selection = body.selection!!.copy(generation = null)))))
    }
    @Test fun unknownAbsentKnownAndNullableSelectionHaveDistinctBytes() {
        val variants = listOf(add().copy(finish = Knowledge.Absent("?")), add().copy(finish = Knowledge.Unknown("?", SnapshotList.empty())), add().copy(finish = Knowledge.Known(Finish.FOIL)))
        variants.forEach { assertEquals(it, decoded(encoded(it))) }; assertEquals(3, variants.map { encoded(it).toList() }.toSet().size)
        val identity = CardIdentity(Knowledge.Absent("oracle"), Knowledge.Absent("printing"), null, Knowledge.Absent("name"), LanguageTag.ENGLISH)
        val selection = add().copy(selection = DeckDraftSelection(identity, null)); assertFalse(encoded(add()).contentEquals(encoded(selection)))
        assertEquals(selection, decoded(encoded(selection)))
    }
    @Test fun rawUnicodeCodeUnitsIncludingLoneSurrogatesNulAndAstralAreLossless() {
        val values = listOf("", " ", "-", "01", "\u0000", "\uD800", "\uDC00", "\uDFFF\uD800", "\uD83D\uDE00", "a\uD800b\uDC00c")
        for (raw in values) {
            val d = details().copy(name = raw, description = raw, primer = raw); assertEquals(d, decoded(encoded(d)))
            val e = entry().copy(quantity = raw, notes = raw); assertEquals(e, decoded(encoded(e)))
            val a = add().copy(quantity = raw, notes = raw); assertEquals(a, decoded(encoded(a)))
        }
    }
    @Test fun generatedArbitraryUtf16AndPartialNumbersRoundTripWithoutNormalization() {
        val random = Random(1510)
        repeat(160) { iteration ->
            val units = CharArray(random.nextInt(0, 300)) { random.nextInt(65536).toChar() }.concatToString()
            val d = details().copy(name = units, description = units.reversed(), primer = units + "\uD800")
            assertEquals(d, decoded(encoded(d)))
            val e = entry().copy(quantity = if (iteration % 2 == 0) "+0001" else "-", notes = units)
            assertEquals(e, decoded(encoded(e))); assertArrayEquals(encoded(e), encoded(decoded(encoded(e))))
        }
    }
    @Test fun exactRawCodeUnitCapsAcceptSurrogatesAndRejectOneAdditionalUnit() {
        val names = "\uD800".repeat(4096); val large = "\uDFFF".repeat(1_048_576)
        val d = details().copy(name = names, description = large, primer = large); assertEquals(d, decoded(encoded(d)))
        val e = entry().copy(quantity = names, notes = large); assertEquals(e, decoded(encoded(e)))
        invalid(d.copy(name = names + "x")); invalid(d.copy(description = large + "x")); invalid(d.copy(primer = large + "x"))
        invalid(e.copy(quantity = names + "x")); invalid(e.copy(notes = large + "x")); invalid(add().copy(quantity = names + "x")); invalid(add().copy(notes = large + "x"))
    }
    @Test fun rawTextDoesNotConsumeTheStructuredCollectionElementBudget() {
        val body = details().copy(description = "x".repeat(100_001), primer = "界".repeat(100_001))
        assertEquals(body, decoded(encoded(body)))
    }
    @Test fun exactEightMiBIndependentPayloadAcceptedAndOneMoreByteRejected() {
        // Independently build ADD wire with eight labels; last ASCII label fills exact byte budget.
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { writer ->
            fun utf8(value: String) { val bytes = value.toByteArray(Charsets.UTF_8); writer.writeInt(bytes.size); writer.write(bytes) }
            writer.write(hex("434c44460001020000000000000001")); writer.writeInt(1); writer.writeChar('1'.code); writer.writeInt(0)
            utf8("MAIN"); writer.writeByte(1); utf8("none"); writer.writeInt(8)
            repeat(7) { utf8("id$it"); utf8("x".repeat(1_048_576)) }
            utf8("id7")
            val finalLength = 8_388_608 - out.size() - 4 - 1 // final string length + absent selection
            assertTrue(finalLength in 1..1_048_576); utf8("x".repeat(finalLength)); writer.writeByte(0)
        }
        val exact = out.toByteArray(); assertEquals(DeckDraftLimits.MAX_ENCODED_BYTES, exact.size)
        val decoded = decoded(exact); assertArrayEquals(exact, encoded(decoded))
        corrupt(exact + 0); invalid((decoded as DeckDraftBody.Add).copy(notes = "x"))
    }
    @Test fun perListBoundsAndAllReachableNestedListsReuseExistingBudget() {
        val label = EntryLabel(EntityId("id"), "name")
        val atCap = add().copy(labels = SnapshotList.copyOf(List(10_000) { label })); assertEquals(atCap, decoded(encoded(atCap)))
        invalid(atCap.copy(labels = SnapshotList.copyOf(List(10_001) { label })))
        val evidence = SnapshotList.copyOf(List(10_000) { Evidence("e", null, null) })
        val identity = CardIdentity(Knowledge.Unknown("o", evidence), Knowledge.Unknown("p", evidence), null, Knowledge.Unknown("n", evidence), LanguageTag.ENGLISH)
        val rich = atCap.copy(selection = DeckDraftSelection(identity, null), finish = Knowledge.Unknown("f", evidence))
        assertEquals(rich, decoded(encoded(rich)))
    }
    @Test fun structuredIdentifiersTextAndListsStayStrictUtf8WhileRawMayBeInvalid() {
        invalid(entry().copy(entryId = EntityId("x".repeat(1025))))
        invalid(entry().copy(labels = SnapshotList.of(EntryLabel(EntityId("x"), "\uD800"))))
        invalid(entry().copy(finish = Knowledge.Unknown("\uDFFF", SnapshotList.empty())))
        val identity = CardIdentity(Knowledge.Known(OracleId(ProviderId("source"), "oracle")), Knowledge.Absent("printing"), null, Knowledge.Known("\uD800"), LanguageTag.ENGLISH)
        invalid(add().copy(selection = DeckDraftSelection(identity, null)))
        val valid = add().copy(selection = DeckDraftSelection(identity.copy(originalName = Knowledge.Known("name")), GenerationId("x".repeat(1024))))
        assertEquals(valid, decoded(encoded(valid))); invalid(valid.copy(selection = valid.selection!!.copy(generation = GenerationId("x".repeat(1025)))))
    }
    @Test fun strictStructuredByteBoundsCountUtf8RatherThanCodeUnits() {
        val label = EntryLabel(EntityId("a".repeat(1024)), "界".repeat(349525))
        val body = entry().copy(labels = SnapshotList.of(label)); assertEquals(body, decoded(encoded(body)))
        invalid(body.copy(labels = SnapshotList.of(label.copy(id = EntityId("界".repeat(342))))))
        invalid(body.copy(labels = SnapshotList.of(label.copy(name = label.name + "界"))))
    }
    @Test fun wrongMagicUnknownModeNonpositiveVersionAndNegativeRevisionAreCorrupt() {
        val bytes = encoded(details())
        corrupt(bytes.copyOf().also { it[0] = 0 }); corrupt(bytes.copyOf().also { it[6] = 4 })
        corrupt(bytes.copyOf().also { it[5] = 0 }); corrupt(bytes.copyOf().also { it[7] = 0x80.toByte() })
    }
    @Test fun everyGoldenTruncationAndAnyTrailingByteAreCorrupt() {
        for ((_, bytes) in goldens()) {
            for (length in 0 until bytes.size) corrupt(bytes.copyOf(length))
            corrupt(bytes + 0); corrupt(bytes + byteArrayOf(0, 1))
        }
    }
    @Test fun completeFutureVersionHeaderIsUnsupportedWithoutParsingItsBody() {
        for (version in listOf(2, 255, 65535)) {
            val header = byteArrayOf(67,76,68,70,(version ushr 8).toByte(),version.toByte())
            for (bytes in listOf(header, header + byteArrayOf(127, -1, 0))) assertEquals(UnavailableReason.UNSUPPORTED, (codec.decodeDraft(SnapshotBytes(bytes)) as Outcome.Unavailable).reason)
            for (length in 0..5) corrupt(header.copyOf(length))
        }
    }
    @Test fun unsignedRawLengthsAreRejectedBeforeNarrowingOrAllocation() {
        val bytes = encoded(details())
        for (count in listOf(4097L, 0x7fffffffL, 0x80000000L, 0xffffffffL)) {
            val malformed = bytes.copyOf(); java.nio.ByteBuffer.wrap(malformed).putInt(15, count.toInt()); corrupt(malformed)
        }
        // Admissible cap but insufficient remaining bytes (including odd-length input) is corrupt.
        val truncated = hex("434c44460001000000000000000000000000020041")
        corrupt(truncated); corrupt(truncated + 0)
    }
    @Test fun malformedStructuredTagsCountsAndUtf8DoNotBecomeRecoveryText() {
        val bytes = encoded(entry())
        // ENTRY's entity field starts at byte15; invalid UTF8 remains a structured-field failure.
        corrupt(bytes.copyOf().also { it[19] = 0x80.toByte() })
        corrupt(bytes.copyOf().also { java.nio.ByteBuffer.wrap(it).putInt(15, -1) })
        val suffix = encoded(add())
        corrupt(suffix.copyOf().also { it[it.lastIndex] = 2 }) // nullable selection boolean
        val enum = encoded(entry()).copyOf(); enum[enum.indexOf('M'.code.toByte())] = 'X'.code.toByte(); corrupt(enum)
    }
    @Test fun codecDoesNotInventMutationAuthorizationOrValidateRawFormSemantics() {
        val label = EntryLabel(EntityId("duplicate"), "")
        val body = add().copy(quantity = "-", notes = "\uDFFF", labels = SnapshotList.of(label, label), selection = null)
        assertEquals(body, decoded(encoded(body)))
        assertEquals(entry().copy(entryId = EntityId("missing entry")), decoded(encoded(entry().copy(entryId = EntityId("missing entry")))))
    }
}
