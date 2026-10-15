package com.architectpsg.commandlibrary.data.vault

import com.architectpsg.commandlibrary.core.model.EntityId
import com.architectpsg.commandlibrary.core.model.Origin
import com.architectpsg.commandlibrary.core.model.SecretScope
import java.nio.ByteBuffer
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import org.junit.Assert.*
import org.junit.Test

class VaultEnvelopeTest {
    private val scope = SecretScope(EntityId("synthetic-profile"), Origin("https", "Example.test", 443), "provider-key")
    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test fun realJceRoundTripPreservesUtf8AndRandomizesNonce() {
        val ref = VaultEnvelope.ref(scope)
        val key = key()
        val bytes = VaultEnvelope.valueBytes("  synthetic-😀-é\r\n ")
        try {
            val first = VaultEnvelope.encrypt(ref, key, bytes)
            val second = VaultEnvelope.encrypt(ref, key, bytes)
            assertEquals("  synthetic-😀-é\r\n ", VaultEnvelope.decrypt(ref, key, first))
            assertFalse(first.contentEquals(second))
            assertFalse(first.copyOfRange(10, 22).contentEquals(second.copyOfRange(10, 22)))
            assertEquals(12, first[5].toInt())
            assertEquals(bytes.size + 16, ByteBuffer.wrap(first, 6, 4).int)
        } finally { bytes.fill(0) }
    }

    @Test fun authenticatesCiphertextNonceAndExactScopeAad() {
        val key = key()
        val ref = VaultEnvelope.ref(scope)
        val record = VaultEnvelope.encrypt(ref, key, byteArrayOf(42))
        for (index in listOf(10, record.lastIndex)) {
            val changed = record.copyOf().apply { this[index] = (this[index].toInt() xor 1).toByte() }
            assertThrows(AEADBadTagException::class.java) { VaultEnvelope.decrypt(ref, key, changed) }
        }
        val changedRef = VaultEnvelope.ref(scope.copy(purpose = "other"))
        assertThrows(AEADBadTagException::class.java) { VaultEnvelope.decrypt(changedRef, key, record) }
    }

    @Test fun rejectsHeaderVersionNonceLengthAndOversizeBeforeProviderWork() {
        val key = key()
        val ref = VaultEnvelope.ref(scope)
        val record = VaultEnvelope.encrypt(ref, key, byteArrayOf(1))
        val invalid = listOf(
            record.copyOf().apply { this[0] = 0 },
            record.copyOf().apply { this[4] = 2 },
            record.copyOf().apply { this[5] = 11 },
            record.copyOf().apply { ByteBuffer.wrap(this, 6, 4).putInt(Int.MAX_VALUE) },
            record.copyOf(record.size - 1),
            record + byteArrayOf(0),
            ByteArray(VaultEnvelope.MAX_RECORD_BYTES + 1),
        )
        for (bytes in invalid) assertEquals(VaultIssue.CORRUPT,
            assertThrows(VaultFault::class.java) { VaultEnvelope.decrypt(ref, key, bytes) }.issue)
    }

    @Test fun maximumRecordAndAuthenticatedMalformedUtf8AreBounded() {
        val key = key(); val ref = VaultEnvelope.ref(scope)
        val maximum = VaultEnvelope.valueBytes("x".repeat(VaultEnvelope.MAX_VALUE_BYTES))
        try {
            val record = VaultEnvelope.encrypt(ref, key, maximum)
            assertEquals(VaultEnvelope.MAX_RECORD_BYTES, record.size)
            assertEquals("x".repeat(VaultEnvelope.MAX_VALUE_BYTES), VaultEnvelope.decrypt(ref, key, record))
        } finally { maximum.fill(0) }
        val invalidUtf8 = byteArrayOf(0xc3.toByte(), 0x28)
        try {
            val record = VaultEnvelope.encrypt(ref, key, invalidUtf8)
            assertEquals(VaultIssue.CORRUPT,
                assertThrows(VaultFault::class.java) { VaultEnvelope.decrypt(ref, key, record) }.issue)
        } finally { invalidUtf8.fill(0) }
    }

    @Test fun boundsStrictUnicodeWithoutTrimmingOrFolding() {
        for (value in listOf("", "\uD800", "\uDC00", "a".repeat(16385), "é".repeat(8193))) {
            assertEquals(VaultIssue.INVALID, assertThrows(VaultFault::class.java) { VaultEnvelope.valueBytes(value) }.issue)
        }
        assertEquals(16384, VaultEnvelope.valueBytes("é".repeat(8192)).size)
        for (purpose in listOf(" ", "x\u0000y", "\uD800", "é".repeat(513))) {
            assertThrows(VaultFault::class.java) { VaultEnvelope.ref(scope.copy(purpose = purpose)) }
        }
        val admitted = listOf(scope, scope.copy(origin = Origin("https", "example.test", 443)),
            scope.copy(providerProfileId = EntityId("other")), scope.copy(purpose = "other"),
            scope.copy(origin = Origin("http", "Example.test", 443)),
            scope.copy(origin = Origin("https", "Example.test", 444)),
            scope.copy(purpose = "é"), scope.copy(purpose = "e\u0301"))
        assertEquals(admitted.size, admitted.map { VaultEnvelope.ref(it).id }.toSet().size)
        assertEquals(VaultEnvelope.ref(scope), VaultEnvelope.ref(scope.copy()))
    }
}
