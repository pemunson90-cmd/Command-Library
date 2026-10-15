package com.architectpsg.commandlibrary.data.storage.decks.codec

import com.architectpsg.commandlibrary.core.model.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class DeckPayloadBoundsTest {
    private val codec = DeckPayloadCodec()
    @Test fun everyTruncationAndTrailingByteIsCorrupt() {
        val bytes = golden()
        for (size in 0 until bytes.size) corrupt(bytes.copyOf(size))
        corrupt(bytes + 0)
    }
    @Test fun magicVersionAndUnsignedVersionAreCheckedBeforeBody() {
        corrupt(golden().also { it[0] = 0 })
        corrupt(golden().also { it[5] = 0 })
        listOf(2, 32768, 65535).forEach { version ->
            val bytes = golden().copyOf(6).also { it[4] = (version ushr 8).toByte(); it[5] = version.toByte() }
            assertEquals(UnavailableReason.UNSUPPORTED, (codec.decodeSnapshot(SnapshotBytes(bytes)) as Outcome.Unavailable).reason)
        }
    }
    @Test fun strictUnicodeOnEncodeAndDecode() {
        listOf("\ud800", "\udc00", "x\ud800x").forEach { invalid(minimal().copy(name = it)) }
        corrupt(golden().also { it[10] = 0xc0.toByte() })
        corrupt(golden().also { it[10] = 0xed.toByte(); it[11] = 0xa0.toByte(); it[12] = 0x80.toByte() })
    }
    @Test fun idAndTextByteLimitsAreExact() {
        roundTrip(minimal().copy(id = DeckId(ProviderId("é".repeat(512)), "a".repeat(1024)), name = "é".repeat(524288)))
        invalid(minimal().copy(id = DeckId(ProviderId("é".repeat(513)), "x")))
        invalid(minimal().copy(id = DeckId(ProviderId("local"), "x".repeat(1025))))
        invalid(minimal().copy(name = "é".repeat(524289)))
    }
    @Test fun encodedByteBudgetIsEnforcedOnBothDirections() {
        val tags = List(7) { "x".repeat(1024 * 1024) } + "x".repeat(1024 * 1024 - 157)
        val atLimit = minimal().copy(tags = SnapshotList.copyOf(tags))
        assertEquals(DeckPayloadLimits.MAX_ENCODED_BYTES, encoded(atLimit).size)
        roundTrip(atLimit)
        invalid(atLimit.copy(tags = SnapshotList.copyOf(tags.dropLast(1) + (tags.last() + "x"))))
        corrupt(ByteArray(DeckPayloadLimits.MAX_ENCODED_BYTES + 1))
    }
    @Test fun collectionCapsAndAggregateCapApplyOnEncode() {
        roundTrip(minimal().copy(entries = SnapshotList.copyOf(List(10000) { entry() }), categoryOrder = SnapshotList.copyOf(List(1024) { EntryLabel(EntityId("c"), "C") })))
        invalid(minimal().copy(entries = SnapshotList.copyOf(List(10001) { entry() })))
        invalid(minimal().copy(categoryOrder = SnapshotList.copyOf(List(1025) { EntryLabel(EntityId("c"), "C") })))
        invalid(minimal().copy(tags = SnapshotList.copyOf(List(10001) { "" })))
        val labels = SnapshotList.copyOf(List(10000) { EntryLabel(EntityId("x"), "X") })
        invalid(minimal().copy(entries = SnapshotList.copyOf(List(10) { entry().copy(labels = labels) })))
        val exactLabels = SnapshotList.copyOf(labels.dropLast(1))
        roundTrip(minimal().copy(entries = SnapshotList.copyOf(List(10) { entry().copy(labels = exactLabels) })))
    }
    @Test fun uint32LengthsAndCountsNeverNarrowOrAllocateFirst() {
        val bytes = golden()
        listOf(1025L, 0x80000000L, 0xffffffffL).forEach { corrupt(replaceInt(bytes, 6, it)) }
        // tags count follows the two absent reasons and empty description.
        listOf(10001L, 0x80000000L, 0xffffffffL, 10000L).forEach { corrupt(replaceInt(bytes, 62, it)) }
        corrupt(replaceInt(bytes, 30, 1024 * 1024L + 1))
        corrupt(replaceInt(bytes, 30, 1024 * 1024L)) // valid cap, fewer remaining bytes
        listOf(10001L, 0x80000000L, 0xffffffffL).forEach { corrupt(replaceInt(bytes, 98, it)) }
        // Explicit category registry with an over-cap count, enough bytes for the minimum-byte check.
        val categories = bytes.copyOf(bytes.size + 1029).also { it[124] = 1 }
        corrupt(replaceInt(categories, 125, 1025))
    }
    @Test fun cumulativeDeclaredDecodeCountsAreChargedBeforeNestedAllocation() {
        val labels = SnapshotList.copyOf(List(10000) { EntryLabel(EntityId("x"), "X") })
        val one = encoded(minimal().copy(entries = SnapshotList.of(entry().copy(labels = labels))))
        // CLDK1 minimal prefix ends at entry count 98; the unchanged minimal suffix is 23 bytes.
        val entryBody = one.copyOfRange(102, one.size - 23)
        val prefix = replaceInt(golden().copyOf(102), 98, 10)
        val suffix = golden().copyOfRange(102, 125)
        val combined = prefix + ByteArray(entryBody.size * 10).also { target ->
            repeat(10) { entryBody.copyInto(target, it * entryBody.size) }
        } + suffix
        // 10 entries plus 10 x 10,000 labels = 100,010 declared elements.
        corrupt(combined)
    }
    @Test fun tagsBooleansEnumsAndConstructorsRejectInvalidFrames() {
        corrupt(golden().also { it[40] = 3 }) // Knowledge format tag
        corrupt(golden().also { it[it.lastIndex] = 2 }) // category presence
        val bytes = encoded(minimal().copy(entries = SnapshotList.of(entry().copy(roleOverrides = SnapshotList.of(RoleOverride(null, Knowledge.Absent("none"), "role", true, UtcTimestamp(1)))))))
        val board = find(bytes, "MAIN".toByteArray())
        corrupt(bytes.copyOf().also { it[board] = 'X'.code.toByte() })
        val enabled = find(bytes, "role".toByteArray()) + 4
        corrupt(bytes.copyOf().also { it[enabled] = 2 })
        corrupt(replaceInt(bytes, board - 8, 0)) // quantity before framed enum
        corrupt(replaceInt(golden(), 74, 1_000_000_000)) // createdAt nanos
    }
    @Test fun decimalPrecisionAndCanonicalIntegerAreChecked() {
        roundTrip(minimal().copy(budget = Knowledge.Known(DecimalAmount.exact("9".repeat(4096), DecimalUnit.Tix))))
        invalid(minimal().copy(budget = Knowledge.Known(DecimalAmount.exact("9".repeat(4097), DecimalUnit.Tix))))
        val bytes = encoded(minimal().copy(budget = Knowledge.Known(DecimalAmount.exact("12", DecimalUnit.Tix))))
        val offset = find(bytes, "12".toByteArray())
        listOf("00", "-0", "+1", "01", "1x").forEach { text -> corrupt(bytes.copyOf().also { text.toByteArray().copyInto(it, offset) }) }
        corrupt(replaceInt(bytes, offset + 2, 0xffffffffL)) // negative scale
        corrupt(bytes.copyOf().also { it[offset + 6] = 3 }) // unknown DecimalUnit tag
        corrupt(replaceInt(bytes, offset - 4, 4098)) // integer frame must be bounded before reading
        val oversizedInteger = "9".repeat(4097).toByteArray()
        val declared = replaceInt(bytes.copyOfRange(0, offset), offset - 4, oversizedInteger.size.toLong())
        corrupt(declared + oversizedInteger + bytes.copyOfRange(offset + 2, bytes.size)) // in-frame 4097 digits
        roundTrip(minimal().copy(budget = Knowledge.Known(DecimalAmount.exact("-" + "9".repeat(4096), DecimalUnit.Tix))))
    }
    @Test fun constructorFailuresAndNestedPresenceTagsAreCorrupt() {
        corrupt(golden().also { it[10] = ' '.code.toByte(); it[11] = ' '.code.toByte(); it[12] = ' '.code.toByte(); it[13] = ' '.code.toByte(); it[14] = ' '.code.toByte() })
        corrupt(golden().also { it[111] = 2 }) // profile presence
        corrupt(golden().also { for (n in 90..97) it[n] = 0xff.toByte() }) // negative revision
        val fiat = encoded(minimal().copy(budget = Knowledge.Known(DecimalAmount.exact("0", DecimalUnit.Fiat("USD")))))
        corrupt(fiat.copyOf().also { it[find(it, "USD".toByteArray())] = 'u'.code.toByte() })
        val reference = encoded(minimal().copy(catalog = SnapshotList.of(SnapshotReference(ProviderId("catalog sentinel"), null, null, null))))
        val presence = find(reference, "catalog sentinel".toByteArray()) + "catalog sentinel".length
        for (n in 0..2) corrupt(reference.copyOf().also { it[presence + n] = 2 })
        val rawIdentity = entry().identity.copy(raw = ProviderIdentity(ProviderId("raw source"), "kind", "raw sentinel"))
        val raw = encoded(minimal().copy(entries = SnapshotList.of(entry().copy(identity = rawIdentity))))
        val source = find(raw, "raw source".toByteArray())
        corrupt(raw.copyOf().also { it[source - 5] = 2 })
        val destination = "contribution-destination"
        val packaged = encoded(minimal().copy(packageInstances = SnapshotList.of(PackageInstance(EntityId("i"), EntityId("p"), EntityId("v"), PackageLinkPolicy.COPY, SnapshotList.of(PackageContribution(EntityId("pe"), EntityId(destination), 1))))))
        corrupt(packaged.copyOf().also { it[find(it, "COPY".toByteArray())] = 'X'.code.toByte() })
        val contribution = find(packaged, destination.toByteArray()) + destination.length
        corrupt(replaceInt(packaged, contribution, 0))
    }
    @Test fun malformedFramesNeverEchoSerializedUserText() {
        val marker = "user-secret-example"
        val bytes = encoded(minimal().copy(entries = SnapshotList.of(entry().copy(board = BoardRole.COMMANDER))))
        val board = find(bytes, "COMMANDER".toByteArray())
        val malformed = bytes.copyOfRange(0, board - 4) + byteArrayOf(0, 0, 0, marker.length.toByte()) + marker.toByteArray() + bytes.copyOfRange(board + 9, bytes.size)
        val error = (codec.decodeSnapshot(SnapshotBytes(malformed)) as Outcome.Failure).error
        assertEquals(FailureCode.CORRUPT_DATA, error.code)
        assertFalse(error.message.contains(marker))
        assertTrue(error.evidence.isEmpty())
    }
    private fun invalid(value: DeckSnapshot) = assertEquals(FailureCode.INVALID_INPUT, (codec.encodeSnapshot(value) as Outcome.Failure).error.code)
    private fun corrupt(bytes: ByteArray) = assertEquals(FailureCode.CORRUPT_DATA, (codec.decodeSnapshot(SnapshotBytes(bytes)) as Outcome.Failure).error.code)
    private fun encoded(value: DeckSnapshot) = (codec.encodeSnapshot(value) as Outcome.Success).value.copyBytes()
    private fun roundTrip(value: DeckSnapshot) = assertEquals(value, (codec.decodeSnapshot(SnapshotBytes(encoded(value))) as Outcome.Success).value)
    private fun replaceInt(bytes: ByteArray, offset: Int, value: Long) = bytes.copyOf().also { for (n in 0..3) it[offset + n] = (value ushr (24 - n * 8)).toByte() }
    private fun find(bytes: ByteArray, pattern: ByteArray): Int = (0..bytes.size - pattern.size).first { offset -> pattern.indices.all { bytes[offset + it] == pattern[it] } }
}
