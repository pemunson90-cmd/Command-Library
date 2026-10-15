package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Base64

/** Versioned, canonical local continuation. No source identifiers or sort names are truncated. */
internal data class CatalogQueryCursor(
    val provider: String,
    val generation: String,
    val language: String,
    val projectionVersion: Int,
    val compilerVersion: Int,
    val facePolicy: FaceMatchPolicy,
    val fingerprint: String,
    val name: String,
    val lastProvider: String,
    val oracle: String,
) {
    fun encode(): String {
        validate()
        val fields = listOf("catalog-query-1", provider, generation, language, projectionVersion.toString(),
            compilerVersion.toString(), facePolicy.name, "single-printing-1", fingerprint, name, lastProvider, oracle)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { stream ->
            for (field in fields) {
                require(field.length <= PageRequest.MAX_CURSOR_BYTES)
                val bytes = queryUtf8(field)
                // Reject before allocating an unbounded serialized cursor.
                require(bytes.size <= PageRequest.MAX_CURSOR_BYTES)
                stream.writeInt(bytes.size); stream.write(bytes)
                require(output.size() <= PageRequest.MAX_CURSOR_BYTES)
            }
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray()).also {
            require(it.length <= PageRequest.MAX_CURSOR_BYTES)
        }
    }

    private fun validate() {
        require(provider == "scryfall" && language == "en" && lastProvider == provider)
        require(projectionVersion == 1 && compilerVersion == 1)
        queryIdentity(generation); queryIdentity(lastProvider); queryIdentity(oracle)
        require(fingerprint.length == 64 && fingerprint.matches(Regex("[0-9a-f]{64}")))
        require(name.length <= PageRequest.MAX_CURSOR_BYTES)
        require('\u0000' !in name)
        queryUtf8(name)
    }

    companion object {
        fun decode(encoded: String): CatalogQueryCursor {
            require(encoded.length in 1..PageRequest.MAX_CURSOR_BYTES && encoded.matches(Regex("[A-Za-z0-9_-]+")))
            val bytes = Base64.getUrlDecoder().decode(encoded)
            require(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) == encoded)
            val input = ByteBuffer.wrap(bytes)
            val fields = List(12) {
                require(input.remaining() >= 4)
                val length = input.int
                require(length >= 0 && length <= input.remaining())
                val value = ByteArray(length); input.get(value)
                StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(value)).toString()
            }
            require(!input.hasRemaining() && fields[0] == "catalog-query-1" && fields[7] == "single-printing-1")
            require(fields[4] == "1" && fields[5] == "1")
            return CatalogQueryCursor(fields[1], fields[2], fields[3], 1, 1, FaceMatchPolicy.valueOf(fields[6]),
                fields[8], fields[9], fields[10], fields[11]).also { it.validate(); require(it.encode() == encoded) }
        }
    }
}

internal fun queryUtf8(value: String): ByteArray {
    val buffer = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(value))
    return ByteArray(buffer.remaining()).also { buffer.get(it) }
}

internal fun queryIdentity(value: String) {
    require(value.length in 1..128)
    require(value.isNotBlank() && '\u0000' !in value && queryUtf8(value).size <= 128)
}
