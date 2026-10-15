package com.architectpsg.commandlibrary.feature.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import java.nio.ByteBuffer
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class DeckLibraryRequestsTest {
    @Test fun independentCreateRequestV1Fingerprint() {
        val bytes = fixture("create")
        assertArrayEquals(byteArrayOf(67, 76, 68, 82, 0, 1, 0), bytes.copyOfRange(0, 7))
        val length = ByteBuffer.wrap(bytes, 7, 4).int
        assertEquals(bytes.size - 11, length)
        assertEquals(hash(bytes), DeckLibraryRequests.create(SnapshotBytes(bytes.copyOfRange(11, bytes.size))))
    }

    @Test fun independentDuplicateRequestV1Fingerprint() {
        val bytes = fixture("duplicate")
        assertEquals(hash(bytes), DeckLibraryRequests.duplicate(DeckId(ProviderId("local-decks"), "golden-deck"), Revision(0), "Copy"))
        assertNotEquals(hash(bytes), DeckLibraryRequests.duplicate(DeckId(ProviderId("foreign"), "golden-deck"), Revision(0), "Copy"))
        assertNotEquals(hash(bytes), DeckLibraryRequests.duplicate(DeckId(ProviderId("local-decks"), "golden-deck"), Revision(1), "Copy"))
    }

    @Test fun createRejectsFutureCodecInsteadOfSilentlyChangingRequestV1() {
        val bytes = fixture("create").drop(11).toByteArray()
        bytes[5] = 2
        assertThrows(IllegalArgumentException::class.java) { DeckLibraryRequests.create(SnapshotBytes(bytes)) }
    }

    @Test fun cursorRoundTripBoundariesAndStrictCanonicalInput() {
        for (id in listOf("a", "界".repeat(341) + "a", "\uD83D\uDC09", "\u0000raw")) {
            val cursor = DeckLibraryRequests.cursor(Long.MAX_VALUE, id)
            assertEquals(DeckLibraryRequests.Cursor(Long.MAX_VALUE, id), DeckLibraryRequests.cursor(cursor))
            assertTrue(cursor.length <= 2048)
            assertThrows(IllegalArgumentException::class.java) { DeckLibraryRequests.cursor(cursor + "=") }
        }
        for (cursor in listOf("", "a", "!", "A".repeat(2049), "AQAAAAAAAAAAAAAAAA")) {
            assertThrows(IllegalArgumentException::class.java) { DeckLibraryRequests.cursor(cursor) }
        }
    }

    @Test fun strictUtf8AndByteBudgetRejectMalformedOrOversizeText() {
        assertEquals(1024, DeckLibraryRequests.utf8("é".repeat(512), 1024).size)
        assertThrows(IllegalArgumentException::class.java) { DeckLibraryRequests.utf8("é".repeat(513), 1024) }
        assertThrows(java.nio.charset.CharacterCodingException::class.java) { DeckLibraryRequests.utf8("\uD800", 1024) }
    }

    private fun fixture(kind: String): ByteArray = javaClass.getResourceAsStream("/deck-library/$kind-request-v1.hex")!!.bufferedReader().use { it.readText().trim() }
        .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
}
