package com.architectpsg.commandlibrary.feature.ai

import com.architectpsg.commandlibrary.core.model.*
import org.junit.Assert.*
import org.junit.Test

class ProviderProfileCodecTest {
    private val f = ProviderProfileFixtures
    private fun stored(profile: ProviderProfile = f.profile()) = StoredProviderProfile(profile, Revision(7), UtcTimestamp(-10, 3), UtcTimestamp(20, 999_999_999))
    @Test fun completeRoundTripPreservesUnicodeDialectsKnowledgeCapabilitiesAndReference() {
        val p = f.profile(); val e = p.endpoint!!
        val ref = SecretRef(EntityId("synthetic-reference"), SecretScope(p.id, e.origin, "provider-auth-v1"))
        val profiles = ProviderDialect.entries.map { p.copy(dialect = it) } + listOf(
            p.copy(apiVersion = Knowledge.Known("2026-01-01")), p.copy(apiVersion = Knowledge.Absent("Absent")),
            p.copy(secretRef = ref, endpoint = e.copy(secret = ref)))
        profiles.forEach { profile ->
            val expected = stored(profile)
            val bytes = f.ok(ProviderProfileCodec.encode(expected))
            assertEquals(expected, f.ok(ProviderProfileCodec.decode(bytes)))
            assertArrayEquals(bytes.copyBytes(), f.ok(ProviderProfileCodec.encode(f.ok(ProviderProfileCodec.decode(bytes)))).copyBytes())
        }
    }
    @Test fun corruptTruncatedTrailingAndNewerVersionFramesHaveStaticStatuses() {
        val bytes = f.ok(ProviderProfileCodec.encode(stored())).copyBytes()
        val malformed = listOf(byteArrayOf(), bytes.copyOf(7), bytes + byteArrayOf(0), bytes.copyOf().also { it[0] = 0 },
            bytes.copyOf().also { it[8] = 0x7f }, ByteArray(ProviderProfileCodec.MAX_BYTES + 1))
        malformed.forEach {
            val failure = ProviderProfileCodec.decode(SnapshotBytes(it)) as Outcome.Failure
            assertEquals(FailureCode.CORRUPT_DATA, failure.error.code)
            assertEquals("Invalid retained provider payload", failure.error.message)
        }
        val newer = bytes.copyOf().also { it[7] = 2 }
        assertEquals(UnavailableReason.UNSUPPORTED, (ProviderProfileCodec.decode(SnapshotBytes(newer)) as Outcome.Unavailable).reason)
        val old = bytes.copyOf().also { it[7] = 0 }
        assertEquals(FailureCode.CORRUPT_DATA, (ProviderProfileCodec.decode(SnapshotBytes(old)) as Outcome.Failure).error.code)
    }
    @Test fun boundedTextAndBooleanAreCheckedBeforeConstructingDto() {
        val bytes = f.ok(ProviderProfileCodec.encode(stored())).copyBytes()
        // Frame 8 bytes, then length-prefixed ID: invalid UTF8 must not be replacement-decoded.
        val badUtf8 = bytes.copyOf().also { it[12] = 0xc0.toByte() }
        assertTrue(ProviderProfileCodec.decode(SnapshotBytes(badUtf8)) is Outcome.Failure)
        val badLength = bytes.copyOf().also { it[8] = 0x7f }
        assertTrue(ProviderProfileCodec.decode(SnapshotBytes(badLength)) is Outcome.Failure)
        val p = f.profile()
        val firstFlag = 8 + 4 + p.id.value.toByteArray(Charsets.UTF_8).size + 8 + 24 +
            4 + p.name.toByteArray(Charsets.UTF_8).size + 1 + 4 + p.baseUri!!.value.toByteArray(Charsets.UTF_8).size +
            4 + p.selectedModelId.toByteArray(Charsets.UTF_8).size
        val badFlag = bytes.copyOf().also { it[firstFlag] = 2 }
        assertTrue(ProviderProfileCodec.decode(SnapshotBytes(badFlag)) is Outcome.Failure)
        assertTrue(ProviderProfileCodec.encode(stored().copy(revision = Revision(0))) is Outcome.Failure)
        assertTrue(ProviderProfileCodec.encode(stored(f.profile().copy(baseUri = ResourceUri("HTTPS://EXAMPLE.TEST/v1/")))) is Outcome.Failure)
    }
    @Test fun cursorRequiresExactBoundedCanonicalFrame() {
        val cursor = ProviderProfileCursor(Long.MAX_VALUE, "日本-provider")
        assertEquals(cursor, ProviderProfileCursor.decode(cursor.encode()))
        for (bad in listOf("pp1:", "pp2:abc", cursor.encode() + "=", "pp1:" + "x".repeat(600))) {
            assertTrue(profileOutcome { ProviderProfileCursor.decode(bad) } is Outcome.Failure)
        }
        assertNotEquals(ProviderProfileCursor(1, "id").encode(), ProviderProfileCursor(2, "id").encode())
    }
}
