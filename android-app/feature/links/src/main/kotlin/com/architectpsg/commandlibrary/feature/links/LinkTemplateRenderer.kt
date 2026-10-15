package com.architectpsg.commandlibrary.feature.links

import com.architectpsg.commandlibrary.core.model.*
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** Component substitution only: neither URI decoding nor vendor query-language escaping. */
internal object LinkTemplateRenderer {
    const val MAX_BYTES: Int = 16 * 1024
    val supportedTokens: Set<String> = setOf("name", "oracle_id", "printing_id", "set", "collector_number", "lang")

    fun validate(pattern: String, allowedTokens: List<String>): Outcome<Unit> = checked {
        parse(pattern, allowedTokens)
        Unit
    }

    fun render(pattern: String, values: LinkTokenValues, allowedTokens: List<String> = supportedTokens.toList()): Outcome<ResourceUri> = checked {
        val parts = parse(pattern, allowedTokens)
        val components = mapOf(
            "name" to Knowledge.Known(values.name),
            "oracle_id" to values.oracleId.map { it.raw },
            "printing_id" to values.printingId.map { it.raw },
            "set" to values.setCode,
            "collector_number" to values.collectorNumber,
            "lang" to Knowledge.Known(values.language.value),
        )
        // Validate each supplied component, even when this pattern does not use it.
        components.values.forEach { if (it is Knowledge.Known) boundedText(it.value) }
        val result = StringBuilder()
        for (part in parts) {
            val value = if (part.token) {
                when (val component = components.getValue(part.text)) {
                    is Knowledge.Known -> {
                        if (component.value.isEmpty()) throw MissingToken(part.text)
                        encode(component.value)
                    }
                    is Knowledge.Absent, is Knowledge.Unknown -> throw MissingToken(part.text)
                }
            } else part.text
            if (result.length + value.length > MAX_BYTES) invalid("Expanded URL exceeds limit")
            result.append(value)
        }
        safeUri(result.toString())
        ResourceUri(result.toString())
    }

    fun origin(value: String): Outcome<Origin> = checked { safeUri(value) }

    fun validOrigin(expected: Origin): Boolean {
        if (expected.host != expected.host.lowercase(Locale.ROOT) || expected.host.any { it.code > 127 }) return false
        return when (val parsed = origin("${expected.scheme}://${expected.host}:${expected.port}/")) {
            is Outcome.Success -> parsed.value == expected
            else -> false
        }
    }

    private data class Part(val text: String, val token: Boolean)

    private fun parse(pattern: String, allowedTokens: List<String>): List<Part> {
        boundedText(pattern)
        if (allowedTokens.size > supportedTokens.size || allowedTokens.size != allowedTokens.distinct().size || allowedTokens.any { it !in supportedTokens }) {
            invalid("Invalid template token allowance")
        }
        val authorityStart = pattern.indexOf("://")
        if (authorityStart <= 0) invalid("Template requires an absolute HTTP(S) URL")
        val authorityEnd = (authorityStart + 3 until pattern.length)
            .firstOrNull { pattern[it] in "/?#" } ?: pattern.length
        val parts = mutableListOf<Part>()
        var start = 0
        var cursor = 0
        while (cursor < pattern.length) {
            when (pattern[cursor]) {
                '}' -> invalid("Unmatched template brace")
                '{' -> {
                    if (cursor < authorityEnd) invalid("Template tokens cannot appear in scheme or authority")
                    if (cursor > start) parts.add(Part(pattern.substring(start, cursor), false))
                    val end = pattern.indexOf('}', cursor + 1)
                    if (end < 0) invalid("Unmatched template brace")
                    val token = pattern.substring(cursor + 1, end)
                    if (token !in supportedTokens || token !in allowedTokens) invalid("Unsupported or undeclared template token")
                    parts.add(Part(token, true))
                    cursor = end
                    start = end + 1
                }
            }
            cursor++
        }
        if (start < pattern.length) parts.add(Part(pattern.substring(start), false))
        safeUri(parts.joinToString("") { if (it.token) "x" else it.text })
        return parts
    }

    private fun safeUri(value: String): Origin {
        boundedText(value)
        if (value.any { it == '\\' || it.code <= 32 || it.code in 127..159 }) invalid("Unsafe URL characters")
        val uri = try { URI(value).parseServerAuthority() } catch (_: URISyntaxException) { invalid("Malformed URL") }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme !in listOf("http", "https") || uri.isOpaque || uri.rawUserInfo != null) invalid("Unsafe URL scheme or authority")
        val host = uri.host ?: invalid("URL requires a valid ASCII host")
        if (host.isBlank() || host.any { it.code > 127 }) invalid("URL requires a valid ASCII host")
        val authority = uri.rawAuthority ?: invalid("URL requires an authority")
        // Reject an empty explicit port and any non-host authority syntax. URI alone accepts host:.
        val suffix = authority.removePrefix(host)
        val port = if (suffix.isEmpty()) {
            if (scheme == "https") 443 else 80
        } else {
            if (!suffix.startsWith(":") || suffix.length == 1 || suffix.drop(1).any { it !in '0'..'9' }) invalid("Invalid URL port")
            suffix.drop(1).toIntOrNull()?.takeIf { it in 1..65535 } ?: invalid("Invalid URL port")
        }
        return Origin(scheme!!, host.lowercase(Locale.ROOT), port)
    }

    private fun boundedText(value: String) {
        if (value.length > MAX_BYTES || value.toByteArray(Charsets.UTF_8).size > MAX_BYTES) invalid("Link value exceeds limit")
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (Character.isHighSurrogate(char)) {
                if (index + 1 >= value.length || !Character.isLowSurrogate(value[index + 1])) invalid("Invalid Unicode value")
                index++
            } else if (Character.isLowSurrogate(char)) invalid("Invalid Unicode value")
            index++
        }
    }

    private fun encode(value: String): String = buildString {
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val code = byte.toInt() and 255
            if (code in 65..90 || code in 97..122 || code in 48..57 || code in listOf(45, 46, 95, 126)) {
                append(code.toChar())
            } else {
                append('%')
                append("0123456789ABCDEF"[code ushr 4])
                append("0123456789ABCDEF"[code and 15])
            }
        }
    }

    private fun <T, R> Knowledge<T>.map(transform: (T) -> R): Knowledge<R> = when (this) {
        is Knowledge.Known -> Knowledge.Known(transform(value))
        is Knowledge.Absent -> this
        is Knowledge.Unknown -> this
    }

    private class InvalidInput(message: String) : IllegalArgumentException(message)
    private class MissingToken(val token: String) : IllegalArgumentException()
    private fun invalid(message: String): Nothing = throw InvalidInput(message)
    private fun <T> checked(action: () -> T): Outcome<T> = try {
        Outcome.Success(action())
    } catch (missing: MissingToken) {
        Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Missing template value: ${missing.token}")
    } catch (invalid: InvalidInput) {
        Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT, invalid.message!!, SnapshotList.empty()))
    }
}
