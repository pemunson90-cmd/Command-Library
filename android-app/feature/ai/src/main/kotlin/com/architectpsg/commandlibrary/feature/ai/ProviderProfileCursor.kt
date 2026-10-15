package com.architectpsg.commandlibrary.feature.ai

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.io.encoding.Base64

/** Exact mutation epoch plus BINARY last ID; never an offset or a credential-bearing frame. */
internal data class ProviderProfileCursor(val epoch: Long, val lastId: String) {
    fun encode(): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            val id = profileText(lastId, 256)
            out.writeByte(1); out.writeLong(epoch); out.writeInt(id.size); out.write(id)
        }
        return "pp1:" + CURSOR_BASE64.encode(bytes.toByteArray())
    }
    companion object {
        private val CURSOR_BASE64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        fun decode(value: String): ProviderProfileCursor {
            profileRequire(value.length <= 512 && value.startsWith("pp1:"), "Invalid provider cursor")
            return try {
                val bytes = CURSOR_BASE64.decode(value.substring(4))
                val input = DataInputStream(ByteArrayInputStream(bytes))
                profileRequire(input.readUnsignedByte() == 1, "Invalid provider cursor version")
                val epoch = input.readLong(); val size = input.readInt()
                profileRequire(epoch >= 0 && size in 1..256 && input.available() == size, "Invalid provider cursor frame")
                val id = ByteArray(size); input.readFully(id)
                ProviderProfileCursor(epoch, profileDecodeText(id)).also {
                    profileText(it.lastId, 256)
                    profileRequire(it.encode() == value, "Noncanonical provider cursor")
                }
            } catch (_: java.io.IOException) { profileReject("Invalid provider cursor") }
            catch (_: IllegalArgumentException) { profileReject("Invalid provider cursor") }
        }
    }
}
