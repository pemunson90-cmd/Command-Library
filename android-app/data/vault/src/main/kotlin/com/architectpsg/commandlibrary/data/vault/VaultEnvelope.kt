package com.architectpsg.commandlibrary.data.vault

import com.architectpsg.commandlibrary.core.model.EntityId
import com.architectpsg.commandlibrary.core.model.SecretRef
import com.architectpsg.commandlibrary.core.model.SecretScope
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Private wire format. All lengths are bounded before allocation or provider invocation. */
internal object VaultEnvelope {
    const val MAX_VALUE_BYTES = 16 * 1024
    const val MAX_RECORD_BYTES = 4 + 1 + 1 + 4 + 12 + MAX_VALUE_BYTES + 16
    private const val MAGIC = 0x434c5654 // CLVT
    private const val VERSION = 1
    private val refPattern = Regex("vault-v1-[0-9a-f]{64}")

    fun scopeBytes(scope: SecretScope): ByteArray {
        val profile = scopeText(scope.providerProfileId.value)
        val host = scopeText(scope.origin.host)
        val purpose = scopeText(scope.purpose)
        if (scope.origin.scheme != "http" && scope.origin.scheme != "https") fault(VaultIssue.INVALID)
        if (scope.origin.port !in 1..65535) fault(VaultIssue.INVALID)
        return frame {
            writeUTF("command-library/vault/scope")
            writeInt(VERSION)
            bytes(profile)
            bytes(scope.origin.scheme.toByteArray(Charsets.US_ASCII))
            bytes(host)
            writeInt(scope.origin.port)
            bytes(purpose)
        }
    }

    fun ref(scope: SecretScope): SecretRef {
        val digest = MessageDigest.getInstance("SHA-256").digest(scopeBytes(scope))
        val hex = digest.joinToString("") { "%02x".format(it.toInt() and 255) }
        return SecretRef(EntityId("vault-v1-$hex"), scope)
    }

    fun checkRef(ref: SecretRef) {
        val expected = this.ref(ref.scope)
        if (!refPattern.matches(ref.id.value) || ref.id != expected.id) fault(VaultIssue.MISMATCH)
    }

    fun alias(ref: SecretRef): String {
        checkRef(ref)
        return "command-library.vault.v1." + ref.id.value.removePrefix("vault-v1-")
    }

    fun valueBytes(value: String): ByteArray = strictBytes(value, MAX_VALUE_BYTES, scope = false)

    fun aad(ref: SecretRef): ByteArray {
        checkRef(ref)
        return frame {
            writeUTF("command-library/vault/aad")
            writeInt(VERSION)
            bytes(ref.id.value.toByteArray(Charsets.US_ASCII))
            bytes(scopeBytes(ref.scope))
        }
    }

    fun encrypt(ref: SecretRef, key: SecretKey, plaintext: ByteArray): ByteArray {
        if (plaintext.isEmpty() || plaintext.size > MAX_VALUE_BYTES) fault(VaultIssue.INVALID)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        // The provider generates the randomized nonce; no caller-chosen IV is ever supplied.
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val nonce = cipher.iv ?: fault(VaultIssue.UNSUPPORTED)
        if (nonce.size != 12) fault(VaultIssue.UNSUPPORTED)
        cipher.updateAAD(aad(ref))
        val ciphertext = cipher.doFinal(plaintext)
        if (ciphertext.size != plaintext.size + 16) fault(VaultIssue.UNSUPPORTED)
        return ByteBuffer.allocate(10 + nonce.size + ciphertext.size)
            .putInt(MAGIC).put(VERSION.toByte()).put(nonce.size.toByte())
            .putInt(ciphertext.size).put(nonce).put(ciphertext).array()
    }

    fun decrypt(ref: SecretRef, key: SecretKey, record: ByteArray): String {
        if (record.size !in 39..MAX_RECORD_BYTES) fault(VaultIssue.CORRUPT)
        val input = ByteBuffer.wrap(record)
        if (input.int != MAGIC || input.get().toInt() != VERSION || input.get().toInt() != 12) {
            fault(VaultIssue.CORRUPT)
        }
        val size = input.int
        if (size !in 17..(MAX_VALUE_BYTES + 16) || input.remaining() != 12 + size) fault(VaultIssue.CORRUPT)
        val nonce = ByteArray(12).also { input.get(it) }
        val ciphertext = ByteArray(size).also { input.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad(ref))
        val plaintext = cipher.doFinal(ciphertext)
        try {
            if (plaintext.isEmpty() || plaintext.size > MAX_VALUE_BYTES) fault(VaultIssue.CORRUPT)
            return try {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(plaintext)).toString()
            } catch (_: CharacterCodingException) {
                fault(VaultIssue.CORRUPT)
            }
        } finally {
            plaintext.fill(0)
        }
    }

    private fun scopeText(value: String): ByteArray = strictBytes(value, 1024, scope = true)

    private fun strictBytes(value: String, limit: Int, scope: Boolean): ByteArray {
        // A UTF-16 code unit needs at least one UTF-8 byte; reject cheaply before encoding.
        if (value.isEmpty() || value.length > limit || (scope && value.isBlank())) fault(VaultIssue.INVALID)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (scope && Character.isISOControl(char)) fault(VaultIssue.INVALID)
            if (Character.isHighSurrogate(char)) {
                if (index + 1 >= value.length || !Character.isLowSurrogate(value[index + 1])) fault(VaultIssue.INVALID)
                index += 2
            } else {
                if (Character.isLowSurrogate(char)) fault(VaultIssue.INVALID)
                index++
            }
        }
        val encoded = value.toByteArray(Charsets.UTF_8)
        if (encoded.size > limit) {
            encoded.fill(0)
            fault(VaultIssue.INVALID)
        }
        return encoded
    }

    private fun frame(action: DataOutputStream.() -> Unit): ByteArray =
        ByteArrayOutputStream().use { output -> DataOutputStream(output).use { it.action() }; output.toByteArray() }

    private fun DataOutputStream.bytes(value: ByteArray) { writeInt(value.size); write(value) }
}
