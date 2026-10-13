package com.architectpsg.commandlibrary.feature.settings

import com.architectpsg.commandlibrary.core.model.CachePolicy
import com.architectpsg.commandlibrary.core.model.ConsentRecord
import com.architectpsg.commandlibrary.core.model.ContentHash
import com.architectpsg.commandlibrary.core.model.DecimalAmount
import com.architectpsg.commandlibrary.core.model.DecimalUnit
import com.architectpsg.commandlibrary.core.model.DownloadKind
import com.architectpsg.commandlibrary.core.model.EntityId
import com.architectpsg.commandlibrary.core.model.Evidence
import com.architectpsg.commandlibrary.core.model.Knowledge
import com.architectpsg.commandlibrary.core.model.LanguageTag
import com.architectpsg.commandlibrary.core.model.ProviderId
import com.architectpsg.commandlibrary.core.model.ResourceUri
import com.architectpsg.commandlibrary.core.model.Revision
import com.architectpsg.commandlibrary.core.model.SnapshotList
import com.architectpsg.commandlibrary.core.model.StorageMode
import com.architectpsg.commandlibrary.core.model.ThemePreference
import com.architectpsg.commandlibrary.core.model.UtcTimestamp
import java.nio.ByteBuffer
import java.util.zip.CRC32
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SettingsStoreCodecTest {
    private val fullState = StoredSettings(
        revision = Revision(41),
        updatedAt = UtcTimestamp(1_760_000_000, 999_999_999),
        dataLanguage = LanguageTag("de"),
        theme = ThemePreference.DARK,
        storageMode = StorageMode.LEAN,
        cache = CachePolicy(imageBytes = Long.MAX_VALUE, retainRulings = true, wifiOnlyDownloads = false),
        budget = Knowledge.Unknown(
            "Budget not known",
            SnapshotList.of(
                Evidence("typed by user", ResourceUri("https://example.invalid/e"), UtcTimestamp(5, 6)),
                Evidence("second", null, null),
            ),
        ),
        providerProfileIds = listOf(EntityId("a"), EntityId("b")),
        consents = listOf(
            StoredConsent(
                "11111111-1111-4111-8111-111111111111",
                ConsentRecord(
                    id = EntityId("c1"),
                    intent = intent(
                        id = "i1",
                        kind = DownloadKind.LANGUAGE_PACK,
                        language = LanguageTag("fr"),
                        packId = EntityId("pack"),
                        checksum = ContentHash("sha256", "AbCdEf09"),
                        expectedBytes = Knowledge.Unknown("size not announced", SnapshotList.empty()),
                    ),
                    approvedAt = UtcTimestamp(10, 20),
                    revokedAt = UtcTimestamp(30, 40),
                    explicitUserApproval = true,
                ),
            ),
            StoredConsent(
                "22222222-2222-4222-8222-222222222222",
                ConsentRecord(EntityId("c2"), intent(id = "i2"), UtcTimestamp(1), null, true),
            ),
        ),
    )


    @Test fun schemaOnePreservesEveryLegacyFieldWithoutInferringRemoval() {
        val bytes = SettingsStoreCodec.encode(fullState)
        // Version 1 has identical fields, omitting only each new final Boolean.
        val secondInstallation = fullState.consents[1].installationId.toByteArray()
        val secondStart = (0..bytes.size - secondInstallation.size).first { offset ->
            secondInstallation.indices.all { bytes[offset + it] == secondInstallation[it] }
        } - 4
        val legacy = bytes.filterIndexed { index, _ -> index != secondStart - 1 && index != bytes.size - 5 }.toByteArray()
        ByteBuffer.wrap(legacy, 4, 4).putInt(1)
        assertEquals(fullState, SettingsStoreCodec.decode(reChecksum(legacy)))
        assertTrue(SettingsStoreCodec.decode(legacy).consents.none { it.pendingDataRemoval })
    }

    @Test fun schemaTwoPreservesPendingFlagAndRejectsInvalidBooleanOrUnrevokedRequest() {
        val pending = fullState.copy(consents = listOf(fullState.consents[0].copy(pendingDataRemoval = true)))
        assertEquals(pending, SettingsStoreCodec.decode(SettingsStoreCodec.encode(pending)))
        val bytes = SettingsStoreCodec.encode(pending)
        bytes[bytes.size - 5] = 2
        assertCorrupt(reChecksum(bytes))
        val active = fullState.copy(consents = listOf(fullState.consents[1]))
        val invalid = SettingsStoreCodec.encode(active)
        invalid[invalid.size - 5] = 1
        assertCorrupt(reChecksum(invalid))
        try {
            SettingsStoreCodec.validateCandidate(active.copy(consents = listOf(active.consents.single().copy(pendingDataRemoval = true))))
            fail("expected invalid candidate")
        } catch (expected: InvalidSettingsValueException) { /* write validation is fail closed too */ }
    }

    @Test fun schemaTwoSizeLimitIncludesPendingFlagAndChecksum() {
        val pending = fullState.copy(consents = listOf(fullState.consents[0].copy(pendingDataRemoval = true)),
            budget = Knowledge.Unknown("x", SnapshotList.empty()))
        val overhead = SettingsStoreCodec.encode(pending).size - 1
        val fits = pending.copy(budget = Knowledge.Unknown("x".repeat(SettingsStoreCodec.MAX_STORE_BYTES - overhead), SnapshotList.empty()))
        val bytes = SettingsStoreCodec.encode(fits)
        assertEquals(SettingsStoreCodec.MAX_STORE_BYTES, bytes.size)
        assertEquals(fits, SettingsStoreCodec.decode(bytes))
        try {
            SettingsStoreCodec.validateCandidate(fits.copy(budget = Knowledge.Unknown("x".repeat(SettingsStoreCodec.MAX_STORE_BYTES - overhead + 1), SnapshotList.empty())))
            fail("expected size rejection")
        } catch (expected: InvalidSettingsValueException) { }
    }

    private fun reChecksum(bytes: ByteArray): ByteArray {
        val crc = CRC32().also { it.update(bytes, 0, bytes.size - 4) }.value.toInt()
        ByteBuffer.wrap(bytes, bytes.size - 4, 4).putInt(crc)
        return bytes
    }

    private fun assertCorrupt(bytes: ByteArray) {
        try {
            SettingsStoreCodec.decode(bytes)
        } catch (expected: CorruptSettingsStoreException) {
            return
        }
        fail("expected CorruptSettingsStoreException")
    }

    @Test
    fun theHeaderIsMagicThenSchemaVersionTwo() {
        val bytes = SettingsStoreCodec.encode(StoredSettings.INITIAL)
        assertEquals("CLSS", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals(2, ByteBuffer.wrap(bytes, 4, 4).int)
        assertEquals(2, SettingsStoreCodec.SUPPORTED_VERSION)
    }

    @Test
    fun everyFieldAndVariantRoundTripsExactly() {
        assertEquals(fullState, SettingsStoreCodec.decode(SettingsStoreCodec.encode(fullState)))
        assertEquals(StoredSettings.INITIAL, SettingsStoreCodec.decode(SettingsStoreCodec.encode(StoredSettings.INITIAL)))
    }

    @Test
    fun decimalScaleAndUnitRoundTrip() {
        val amounts = listOf(
            DecimalAmount.exact("100.00", DecimalUnit.Fiat("USD")),
            DecimalAmount.exact("0", DecimalUnit.Tix),
            DecimalAmount.exact("123456789012345678901234567890.123456789", DecimalUnit.Native(ProviderId("p"), "sym")),
        )
        for (amount in amounts) {
            val state = StoredSettings.INITIAL.copy(budget = Knowledge.Known(amount))
            val decoded = SettingsStoreCodec.decode(SettingsStoreCodec.encode(state)).budget as Knowledge.Known
            assertEquals(amount, decoded.value)
            assertEquals(amount.value.scale(), decoded.value.value.scale())
            assertEquals(amount.value.unscaledValue(), decoded.value.value.unscaledValue())
        }
    }

    @Test
    fun encodingIsDeterministic() {
        assertArrayEquals(SettingsStoreCodec.encode(fullState), SettingsStoreCodec.encode(fullState))
    }

    @Test
    fun aNewerVersionIsReportedBeforeTheChecksumIsLookedAt() {
        val bytes = SettingsStoreCodec.encode(fullState)
        ByteBuffer.wrap(bytes, 4, 4).putInt(7)
        try {
            SettingsStoreCodec.decode(bytes)
            fail("expected UnsupportedSettingsStoreVersionException")
        } catch (unsupported: UnsupportedSettingsStoreVersionException) {
            assertEquals(7, unsupported.foundVersion)
            assertEquals(2, unsupported.supportedVersion)
        }
    }

    @Test
    fun damageIsReportedAsCorruptionNeverAsDefaults() {
        val good = SettingsStoreCodec.encode(fullState)
        assertCorrupt(ByteArray(0))
        assertCorrupt(good.copyOf(7))
        assertCorrupt(good.copyOf(11))
        assertCorrupt(good.copyOf(good.size - 1))
        assertCorrupt(good.copyOf().also { it[0] = 'X'.code.toByte() })
        assertCorrupt(good.copyOf().also { it[good.size / 2] = (it[good.size / 2].toInt() xor 0x40).toByte() })
        assertCorrupt(good.copyOf().also { ByteBuffer.wrap(it, 4, 4).putInt(0) })
        assertCorrupt(good.copyOf().also { ByteBuffer.wrap(it, 4, 4).putInt(-3) })
    }

    @Test
    fun aCorrectChecksumOverNonsenseIsStillCorruption() {
        val good = SettingsStoreCodec.encode(StoredSettings.INITIAL)

        // The language string length sits after revision (8 bytes) and updatedAt (12 bytes) in the body.
        val hugeLength = good.copyOf().also { ByteBuffer.wrap(it, 8 + 8 + 12, 4).putInt(Int.MAX_VALUE) }
        assertCorrupt(reChecksum(hugeLength))
        val negativeLength = good.copyOf().also { ByteBuffer.wrap(it, 8 + 8 + 12, 4).putInt(-1) }
        assertCorrupt(reChecksum(negativeLength))

        val unknownTheme = String(good, Charsets.ISO_8859_1).replace("SYSTEM", "SYSTEX").toByteArray(Charsets.ISO_8859_1)
        assertEquals(good.size, unknownTheme.size)
        assertCorrupt(reChecksum(unknownTheme))

        // One extra byte after a complete body, with a matching checksum.
        val trailing = good.copyOf(good.size - 4) + byteArrayOf(0x55) + ByteArray(4)
        assertCorrupt(reChecksum(trailing))
    }

    @Test
    fun anInvalidStoredValueIsCorruptionNotACrash() {
        val good = SettingsStoreCodec.encode(StoredSettings.INITIAL)
        // imageBytes is the first long after the two enum strings; make it negative: CachePolicy rejects it.
        val offset = 8 + 8 + 12 + (4 + 2) + (4 + "SYSTEM".length) + (4 + "FULL".length)
        val negative = good.copyOf().also { ByteBuffer.wrap(it, offset, 8).putLong(-5) }
        assertCorrupt(reChecksum(negative))
        assertTrue(offset < good.size)
    }
}
