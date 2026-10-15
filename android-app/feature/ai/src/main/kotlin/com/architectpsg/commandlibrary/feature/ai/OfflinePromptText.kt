package com.architectpsg.commandlibrary.feature.ai

import com.architectpsg.commandlibrary.core.model.*
import java.security.MessageDigest

internal class OfflineInputException : RuntimeException()

/** Bounded strict Unicode and one-pass substitution. Inserted data never becomes template code. */
internal object OfflinePromptText {
    const val USER_BYTES = 32 * 1024
    const val DECK_BYTES = 2 * 1024 * 1024
    const val CARD_BYTES = 128 * 1024
    const val OPTIONAL_BYTES = 512 * 1024
    private val variables = listOf("deck", "constraints", "request")

    fun templates(): SnapshotList<PromptTemplate> = SnapshotList.copyOf(OfflineLabMode.entries.map { mode ->
        val resource = "/prompts/v1/${mode.name.lowercase(java.util.Locale.ROOT)}.txt"
        val body = checkNotNull(OfflinePromptText::class.java.getResourceAsStream(resource)) { "Missing shipped offline template" }
            .use { stream ->
                val buffer = ByteArray(USER_BYTES + 1)
                var count = 0
                while (count < buffer.size) {
                    val read = stream.read(buffer, count, buffer.size - count)
                    if (read < 0) break
                    check(read > 0) { "Unreadable shipped offline template" }
                    count += read
                }
                check(count <= USER_BYTES) { "Oversized shipped offline template" }
                Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(buffer, 0, count)).toString()
            }
        PromptTemplate(mode.templateId, mode.shippedVersion, mode.name, body, Knowledge.Absent("No session override"),
            SnapshotList.copyOf(variables.map { PromptVariable(it, true, it) }))
    })

    fun utf8Size(value: String, maximum: Int, cancel: CancelToken): Int {
        cancel.throwIfCancelled()
        if (value.length > maximum) throw OfflineInputException()
        var bytes = 0
        var i = 0
        var nextCheck = 0
        while (i < value.length) {
            if (i >= nextCheck) { cancel.throwIfCancelled(); nextCheck = i + 1024 }
            val c = value[i++]
            bytes += when {
                c.isHighSurrogate() -> { if (i >= value.length || !value[i].isLowSurrogate()) throw OfflineInputException(); i++; 4 }
                c.isLowSurrogate() -> throw OfflineInputException()
                c.code <= 0x7f -> 1
                c.code <= 0x7ff -> 2
                else -> 3
            }
            if (bytes > maximum) throw OfflineInputException()
        }
        cancel.throwIfCancelled()
        return bytes
    }

    fun bind(body: String, bindings: Map<String, String>, cancel: CancelToken): String {
        utf8Size(body, USER_BYTES, cancel)
        val counts = mutableMapOf<String, Int>()
        val output = StringBuilder()
        var index = 0
        while (index < body.length) {
            cancel.throwIfCancelled()
            if (body.startsWith("{{", index)) {
                val end = body.indexOf("}}", index + 2)
                if (end < 0) throw OfflineInputException()
                val name = body.substring(index + 2, end)
                if (name !in variables || counts.put(name, 1) != null) throw OfflineInputException()
                output.append(bindings.getValue(name))
                index = end + 2
            } else {
                if (body.startsWith("}}", index) || body[index] == '{' || body[index] == '}') throw OfflineInputException()
                output.append(body[index++])
            }
        }
        if (counts.keys != variables.toSet()) throw OfflineInputException()
        return output.toString()
    }

    fun hash(text: String): ContentHash = ContentHash("SHA-256", MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) })

    fun json(text: String, cancel: CancelToken): String = Json(DECK_BYTES, cancel).apply { string(text) }.toString()

    /** Charges escaped UTF8 bytes before appending, so no aggregate grows beyond its budget. */
    class Json(private val maximum: Int, private val cancel: CancelToken) {
        private val out = StringBuilder()
        private var bytes = 0
        fun raw(value: String): Json {
            val size = utf8Size(value, maximum - bytes, cancel)
            bytes += size; out.append(value); return this
        }
        fun string(value: String): Json {
            utf8Size(value, maximum - bytes, cancel)
            raw("\"")
            var i = 0
            var nextCheck = 0
            while (i < value.length) {
                if (i >= nextCheck) { cancel.throwIfCancelled(); nextCheck = i + 1024 }
                val c = value[i++]
                val escaped = when (c) {
                    '"' -> "\\\""; '\\' -> "\\\\"; '\n' -> "\\n"; '\r' -> "\\r"; '\t' -> "\\t"
                    else -> if (c.code < 32) "\\u" + c.code.toString(16).padStart(4, '0')
                        else if (c.isHighSurrogate()) "" + c + value[i++] else c.toString()
                }
                raw(escaped)
            }
            return raw("\"")
        }
        fun <T> knowledge(value: Knowledge<T>, known: Json.(T) -> Unit): Json {
            when (value) {
                is Knowledge.Known -> { raw("{\"state\":\"known\",\"value\":"); known(value.value); raw("}") }
                is Knowledge.Absent -> raw("{\"state\":\"absent\"}")
                is Knowledge.Unknown -> raw("{\"state\":\"unknown\"}")
            }
            return this
        }
        fun snapshot(value: SnapshotReference?): Json {
            if (value == null) return raw("null")
            raw("{\"source\":").string(value.source.value).raw(",\"generation\":")
            value.generation?.let { string(it.value) } ?: raw("null")
            raw(",\"revision\":").raw(value.revision?.value?.toString() ?: "null").raw(",\"language\":")
            value.language?.let { string(it.value) } ?: raw("null")
            return raw("}")
        }
        override fun toString(): String = out.toString()
    }
}
