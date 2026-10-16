package com.architectpsg.commandlibrary.feature.ai

import com.architectpsg.commandlibrary.core.model.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Incremental byte framing. A line is decoded only when complete, so UTF-8 may span chunks. */
internal data class ResponsesSseEvent(val name: String, val data: String)

internal class ResponsesSse(private val cancel: CancelToken) {
    private val line = ByteArrayOutputStream()
    private val data = StringBuilder()
    private var hasData = false
    private var eventName: String? = null
    private var firstLine = true
    private var afterCr = false
    private var eventBytes = 0

    suspend fun accept(bytes: ByteArray, dispatch: suspend (ResponsesSseEvent) -> Unit) {
        for (byte in bytes) {
            cancel.throwIfCancelled()
            val value = byte.toInt() and 255
            // CRLF is a single delimiter, including when split between transport chunks.
            if (afterCr) {
                afterCr = false
                if (value == 10) {
                    if (eventBytes > 0) {
                        responsesRequire(eventBytes < ProviderTextLimits.MAX_EVENT_BYTES, "SSE event byte limit exceeded")
                        eventBytes++
                    }
                    continue
                }
            }
            responsesRequire(eventBytes < ProviderTextLimits.MAX_EVENT_BYTES, "SSE event byte limit exceeded")
            eventBytes++
            if (value == 13 || value == 10) {
                consumeLine(dispatch)
                afterCr = value == 13
            } else {
                responsesRequire(line.size() < ProviderTextLimits.MAX_EVENT_BYTES, "SSE line byte limit exceeded")
                line.write(value)
            }
        }
    }

    fun end() {
        // Validate an unterminated line's UTF-8 as well; EOF never dispatches an incomplete event.
        if (line.size() > 0) decodeLine()
        responsesReject("Responses stream ended before terminal event")
    }

    private fun decodeLine(): String = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(line.toByteArray())).toString()
    } catch (failure: CharacterCodingException) { responsesReject("Malformed UTF-8 stream") }

    private suspend fun consumeLine(dispatch: suspend (ResponsesSseEvent) -> Unit) {
        var value = decodeLine()
        line.reset()
        if (firstLine) {
            firstLine = false
            value = value.removePrefix("\uFEFF")
        }
        if (value.isEmpty()) {
            if (hasData) {
                val event = ResponsesSseEvent(eventName?.takeIf { it.isNotEmpty() } ?: "message", data.toString())
                data.setLength(0)
                hasData = false
                eventName = null
                eventBytes = 0
                dispatch(event)
            } else { eventBytes = 0; eventName = null }
            return
        }
        if (value.startsWith(':')) return
        val colon = value.indexOf(':')
        val field = if (colon < 0) value else value.substring(0, colon)
        if (field != "data" && field != "event") return // id/retry never trigger reconnects.
        var payload = if (colon < 0) "" else value.substring(colon + 1)
        if (payload.startsWith(' ')) payload = payload.substring(1)
        if (field == "event") {
            responsesRequire(eventName == null, "Duplicate SSE event name")
            eventName = payload
            return
        }
        if (hasData) data.append('\n')
        data.append(payload)
        hasData = true
    }
}
