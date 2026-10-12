package com.architectpsg.commandlibrary.feature.settings

import com.architectpsg.commandlibrary.core.model.FailureCode
import com.architectpsg.commandlibrary.core.model.Knowledge
import com.architectpsg.commandlibrary.core.model.ResourceUri
import com.architectpsg.commandlibrary.core.model.Revision
import com.architectpsg.commandlibrary.core.model.SettingChange
import com.architectpsg.commandlibrary.core.model.SnapshotList
import com.architectpsg.commandlibrary.core.model.ThemePreference
import com.architectpsg.commandlibrary.core.model.UtcTimestamp
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsPersistenceBoundsTest {
    @get:Rule val temporary = TemporaryFolder()
    private val fixtures = mutableListOf<Fixture>()
    private fun fixture() = Fixture(temporary.newFolder()).also { fixtures += it }
    @After fun closeStores() { fixtures.forEach { it.closeAll() } }

    private fun unknown(reason: String) = Knowledge.Unknown(reason, SnapshotList.empty())
    private fun assertInvalid(action: () -> Unit) {
        try { action() } catch (expected: InvalidSettingsValueException) { return }
        fail("expected InvalidSettingsValueException")
    }

    @Test fun exactEncodedLimitIncludesChecksumAndReopens(): Unit = blocking {
        val f = fixture()
        val small = StoredSettings.INITIAL.copy(budget = unknown("x"))
        val overhead = SettingsStoreCodec.encode(small).size - 1
        val fits = "x".repeat(SettingsStoreCodec.MAX_STORE_BYTES - overhead)
        val snapshot = f.repo.update(patch(SettingChange.Budget(unknown(fits))), Revision(0), LiveToken).success()
        assertEquals(SettingsStoreCodec.MAX_STORE_BYTES, f.storeFile.length().toInt())
        val before = f.storeFile.readBytes()
        f.repo.update(patch(SettingChange.Budget(unknown(fits + "x"))), snapshot.revision, LiveToken)
            .failure(FailureCode.INVALID_INPUT)
        assertArrayEquals(before, f.storeFile.readBytes())
        assertEquals(snapshot, f.reopen().read(LiveToken).success())
    }

    @Test fun hugeUnknownReasonRejectsWithoutReplacingPriorSnapshot(): Unit = blocking {
        val f = fixture()
        val snapshot = f.repo.update(patch(SettingChange.Theme(ThemePreference.DARK)), Revision(0), LiveToken).success()
        val before = f.storeFile.readBytes()
        f.repo.update(
            patch(SettingChange.Budget(unknown("x".repeat(SettingsStoreCodec.MAX_STORE_BYTES + 1)))),
            snapshot.revision, LiveToken,
        ).failure(FailureCode.INVALID_INPUT)
        assertArrayEquals(before, f.storeFile.readBytes())
        assertEquals(snapshot, f.repo.read(LiveToken).success())
        assertEquals(snapshot, f.reopen().read(LiveToken).success())
    }

    @Test fun cumulativeApprovalsRejectBeforeReplacingPriorConsents(): Unit = blocking {
        val f = fixture()
        val resource = "https://example.invalid/" + "x".repeat(4 * 1024 * 1024)
        repeat(3) { index ->
            f.repo.approve(intent(id = "large-$index").copy(resource = ResourceUri(resource)), LiveToken).success()
        }
        val snapshot = f.repo.read(LiveToken).success()
        assertEquals(Revision(3), snapshot.revision)
        val before = f.storeFile.readBytes()
        f.repo.approve(intent(id = "large-3").copy(resource = ResourceUri(resource)), LiveToken)
            .failure(FailureCode.INVALID_INPUT)
        assertArrayEquals(before, f.storeFile.readBytes())
        assertEquals(snapshot, f.reopen().read(LiveToken).success())
    }

    @Test fun boundedOutputNeverForwardsOversizeWrites() {
        val destination = ByteArrayOutputStream()
        val bounded = BoundedOutputStream(destination, 4)
        bounded.write(byteArrayOf(1, 2, 3), 0, 3)
        assertInvalid { bounded.write(byteArrayOf(4, 5), 0, 2) }
        assertEquals(3, destination.size())
        bounded.write(4)
        assertInvalid { bounded.write(5) }
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), destination.toByteArray())
    }

    @Test fun isolatedSurrogatesInPatchAndApprovalRejectWithoutChangingBytes(): Unit = blocking {
        val f = fixture()
        val snapshot = f.repo.update(patch(SettingChange.Theme(ThemePreference.DARK)), Revision(0), LiveToken).success()
        val before = f.storeFile.readBytes()
        for (surrogate in listOf("\uD800", "\uDC00")) {
            f.repo.update(patch(SettingChange.Budget(unknown("bad-$surrogate"))), snapshot.revision, LiveToken)
                .failure(FailureCode.INVALID_INPUT)
            f.repo.approve(intent().copy(resource = ResourceUri("https://example.invalid/$surrogate")), LiveToken)
                .failure(FailureCode.INVALID_INPUT)
            assertArrayEquals(before, f.storeFile.readBytes())
            assertEquals(snapshot, f.reopen().read(LiveToken).success())
        }
    }

    @Test fun validChecksumDoesNotPermitMalformedOrTruncatedUtf8(): Unit = blocking {
        for (invalid in listOf(byteArrayOf(0xC3.toByte(), 0x28), byteArrayOf(0x61, 0xE2.toByte()))) {
            val f = fixture()
            val bytes = SettingsStoreCodec.encode(StoredSettings.INITIAL.copy(budget = unknown("xy")))
            val marker = byteArrayOf(0, 0, 0, 2, 'x'.code.toByte(), 'y'.code.toByte())
            val offset = (0..bytes.size - marker.size).first { start ->
                marker.indices.all { bytes[start + it] == marker[it] }
            } + 4
            invalid.copyInto(bytes, offset)
            val checksum = CRC32().also { it.update(bytes, 0, bytes.size - 4) }.value.toInt()
            ByteBuffer.wrap(bytes, bytes.size - 4, 4).putInt(checksum)
            f.storeFile.parentFile!!.mkdirs()
            f.storeFile.writeBytes(bytes)
            repeat(2) {
                f.repo.read(LiveToken).failure(FailureCode.CORRUPT_DATA)
                f.repo.update(patch(SettingChange.Theme(ThemePreference.DARK)), Revision(0), LiveToken)
                    .failure(FailureCode.CORRUPT_DATA)
                f.repo.approve(intent(), LiveToken).failure(FailureCode.CORRUPT_DATA)
                assertArrayEquals(bytes, f.storeFile.readBytes())
                f.reopen()
            }
        }
    }

    @Test fun supplementaryUnicodeAndConsentIntentStayExactAfterReopen(): Unit = blocking {
        val f = fixture()
        val requested = intent(id = "intent-\uD83D\uDE80").copy(resource = ResourceUri("https://example.invalid/\uD83D\uDE80"))
        val record = f.repo.approve(requested, LiveToken).success()
        val snapshot = f.repo.update(patch(SettingChange.Budget(unknown("budget-\uD83D\uDE80"))), Revision(1), LiveToken).success()
        assertEquals(snapshot, f.reopen().read(LiveToken).success())
        assertEquals(requested, f.repo.read(LiveToken).success().consents.single().intent)
        assertEquals(record, f.repo.approve(requested, LiveToken).success())
        assertEquals(snapshot.revision, f.repo.read(LiveToken).success().revision)
    }

    @Test fun epochMillisNormalizesNegativeRemaindersWithoutNewPlatformApis() {
        val cases = listOf(
            0L to UtcTimestamp(0, 0), 999L to UtcTimestamp(0, 999_000_000),
            1000L to UtcTimestamp(1, 0), -1L to UtcTimestamp(-1, 999_000_000),
            -1000L to UtcTimestamp(-1, 0), -1001L to UtcTimestamp(-2, 999_000_000),
            -123456789L to UtcTimestamp(-123457, 211_000_000),
        )
        cases.forEach { (millis, timestamp) -> assertEquals(timestamp, timestampFromEpochMillis(millis)) }
    }
}
