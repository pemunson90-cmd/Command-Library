package com.architectpsg.commandlibrary.feature.ai

import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.IOException
import java.io.StringReader
import java.math.BigDecimal
import java.util.concurrent.CancellationException

/** Component-local bounded JSON policy. Diagnostics never include wire or prompt text. */
// Internal collection aborts must pass through transport cancellation handlers unchanged.
internal class AnthropicCodecRejection(val outcome: Outcome<Nothing>) : CancellationException("Anthropic Messages event rejected")

internal fun anthropicReject(message: String, input: Boolean = false): Nothing = throw AnthropicCodecRejection(
    Outcome.Failure(DomainFailure(if (input) FailureCode.INVALID_INPUT else FailureCode.CORRUPT_DATA,
        message, SnapshotList.empty())))

internal fun anthropicRequire(condition: Boolean, message: String, input: Boolean = false) {
    if (!condition) anthropicReject(message, input)
}

internal fun anthropicUnsupported(): Nothing = throw AnthropicCodecRejection(
    Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Unsupported Anthropic Messages capability"))

internal object AnthropicMessagesJson {
    /** Count before UTF-8 allocation; also reject Java strings containing unpaired surrogates. */
    fun textBytes(value: String, max: Int, cancel: CancelToken, input: Boolean = false): Int {
        var count = 0
        var index = 0
        while (index < value.length) {
            if (index and 1023 == 0) cancel.throwIfCancelled()
            val char = value[index++]
            count += when {
                char.isHighSurrogate() -> {
                    anthropicRequire(index < value.length && value[index].isLowSurrogate(), "Invalid Unicode", input)
                    index++
                    4
                }
                char.isLowSurrogate() -> anthropicReject("Invalid Unicode", input)
                char.code < 0x80 -> 1
                char.code < 0x800 -> 2
                else -> 3
            }
            anthropicRequire(count <= max, "Text byte limit exceeded", input)
        }
        return count
    }

    fun id(value: String, cancel: CancelToken, input: Boolean = false) {
        textBytes(value, ProviderTextLimits.MAX_ID_BYTES, cancel, input)
        anthropicRequire(value.isNotBlank() && value.none { it.isISOControl() }, "Invalid identifier", input)
    }

    fun decimal(value: BigDecimal, input: Boolean = false) {
        anthropicRequire(value.precision() <= ProviderTextLimits.MAX_DECIMAL_PRECISION &&
            value.scale() in -ProviderTextLimits.MAX_ABSOLUTE_DECIMAL_SCALE..ProviderTextLimits.MAX_ABSOLUTE_DECIMAL_SCALE,
            "Number limit exceeded", input)
    }

    fun parse(text: String, cancel: CancelToken): JsonObject {
        try {
            JsonReader(StringReader(text)).use { reader ->
                reader.strictness = Strictness.STRICT
                val parsed = read(reader, 0, intArrayOf(ProviderTextLimits.MAX_JSON_NODES), cancel)
                anthropicRequire(reader.peek() == JsonToken.END_DOCUMENT && parsed.isJsonObject, "Invalid JSON event")
                return parsed.asJsonObject
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: IOException) {
            anthropicReject("Malformed JSON event")
        } catch (failure: IllegalArgumentException) {
            anthropicReject("Malformed JSON event")
        } catch (failure: IllegalStateException) {
            anthropicReject("Malformed JSON event")
        }
    }

    private fun read(reader: JsonReader, depth: Int, budget: IntArray, cancel: CancelToken): JsonElement {
        cancel.throwIfCancelled()
        anthropicRequire(depth <= ProviderTextLimits.MAX_JSON_DEPTH && budget[0]-- > 0, "JSON structure limit exceeded")
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                val value = JsonObject()
                reader.beginObject()
                while (reader.hasNext()) {
                    cancel.throwIfCancelled()
                    // Member names consume nodes too, including members whose values are null.
                    anthropicRequire(budget[0]-- > 0, "JSON structure limit exceeded")
                    val key = reader.nextName()
                    textBytes(key, ProviderTextLimits.MAX_EVENT_BYTES, cancel)
                    anthropicRequire(!value.has(key), "Duplicate JSON field")
                    value.add(key, read(reader, depth + 1, budget, cancel))
                }
                reader.endObject()
                value
            }
            JsonToken.BEGIN_ARRAY -> {
                val value = JsonArray()
                reader.beginArray()
                while (reader.hasNext()) value.add(read(reader, depth + 1, budget, cancel))
                reader.endArray()
                value
            }
            JsonToken.STRING -> JsonPrimitive(reader.nextString().also {
                textBytes(it, ProviderTextLimits.MAX_EVENT_BYTES, cancel)
            })
            JsonToken.NUMBER -> {
                val token = reader.nextString()
                anthropicRequire(token.length <= 128, "Number limit exceeded")
                val value = BigDecimal(token)
                decimal(value)
                JsonPrimitive(value)
            }
            JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
            JsonToken.NULL -> { reader.nextNull(); JsonNull.INSTANCE }
            else -> anthropicReject("Malformed JSON event")
        }
    }

    fun string(value: JsonElement): String {
        anthropicRequire(value.isJsonPrimitive && value.asJsonPrimitive.isString, "Expected JSON string")
        return value.asString
    }

    fun nonnegativeLong(value: JsonElement): Long {
        anthropicRequire(value.isJsonPrimitive && value.asJsonPrimitive.isNumber, "Expected JSON integer")
        return try {
            value.asBigDecimal.longValueExact().also { anthropicRequire(it >= 0, "Invalid token count") }
        } catch (failure: ArithmeticException) { anthropicReject("Invalid token count") }
    }

    fun optional(obj: JsonObject, key: String): JsonElement? = obj.get(key)?.takeUnless { it.isJsonNull }
}
