package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** Exact optional source fields; no gameplay inference, projection rewrite or OTHER-to-token conversion. */
internal object CatalogDeckFactsFields {
    private val symbols = setOf("W", "U", "B", "R", "G", "C")
    private val components = setOf("token", "combo_piece", "meld_part", "meld_result")

    fun parse(raw: String, cancel: CancelToken): JsonObject {
        cancel.throwIfCancelled()
        textBytes(raw, DeckStatisticsLimits.MAX_RAW_RECORD_BYTES, cancel)
        return ScryfallCodec.parse(raw, 32).also { cancel.throwIfCancelled() }
    }

    /** Preflight precedes the old mapper, which cannot distinguish future components from corruption. */
    fun preflight(value: JsonObject, source: ProviderId, cancel: CancelToken) {
        production(value, cancel)
        parts(value, source, cancel)
    }

    fun production(value: JsonObject, cancel: CancelToken): Knowledge<SnapshotList<String>> {
        cancel.throwIfCancelled()
        val field = value.get("produced_mana")
        if (field == null || field.isJsonNull) return unknown()
        require(field.isJsonArray)
        require(field.asJsonArray.size() <= symbols.size)
        val result = linkedSetOf<String>()
        for (item in field.asJsonArray) {
            cancel.throwIfCancelled()
            val symbol = string(item, cancel)
            require(symbol.isNotBlank())
            if (symbol !in symbols) unsupported()
            require(result.add(symbol))
        }
        return Knowledge.Known(SnapshotList.copyOf(result))
    }

    fun parts(value: JsonObject, source: ProviderId, cancel: CancelToken): Knowledge<SnapshotList<ReportedCardPart>> {
        cancel.throwIfCancelled()
        val field = value.get("all_parts")
        if (field == null || field.isJsonNull) return unknown()
        require(field.isJsonArray && field.asJsonArray.size() <= DeckStatisticsLimits.MAX_RELATED_PARTS)
        val result = linkedMapOf<CardId, ReportedCardPart>()
        for (element in field.asJsonArray) {
            cancel.throwIfCancelled()
            require(element.isJsonObject)
            val part = element.asJsonObject
            require(required(part, "object", cancel) == "related_card")
            val component = required(part, "component", cancel)
            if (component !in components) unsupported()
            val id = required(part, "id", cancel)
            textBytes(id, 128, cancel)
            require('\u0000' !in id)
            val record = ReportedCardPart(CardId(source, id), component,
                optional(part, "name", cancel), optional(part, "type_line", cancel))
            val previous = result.putIfAbsent(record.printingId, record)
            require(previous == null || previous == record)
        }
        return Knowledge.Known(SnapshotList.copyOf(result.values))
    }

    fun required(value: JsonObject, field: String, cancel: CancelToken): String =
        string(requireNotNull(value.get(field)), cancel).also { require(it.isNotBlank()) }

    private fun optional(value: JsonObject, field: String, cancel: CancelToken): Knowledge<String> {
        cancel.throwIfCancelled()
        val item = value.get(field)
        return if (item == null || item.isJsonNull) unknown() else Knowledge.Known(string(item, cancel))
    }
    private fun string(value: JsonElement, cancel: CancelToken): String {
        cancel.throwIfCancelled()
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString.also { textBytes(it, DeckStatisticsLimits.MAX_RAW_RECORD_BYTES, cancel) }
    }
    /** Strict UTF8 sizing before encoding or JSON parsing; bounded scans poll cancellation. */
    fun textBytes(value: String, maximum: Int, cancel: CancelToken): Int {
        require(value.length <= maximum)
        var index = 0
        var bytes = 0
        var nextCheck = 0
        while (index < value.length) {
            if (index >= nextCheck) { cancel.throwIfCancelled(); nextCheck = index + 1024 }
            val c = value[index++]
            bytes += when {
                c.isHighSurrogate() -> { require(index < value.length && value[index].isLowSurrogate()); index++; 4 }
                c.isLowSurrogate() -> error("Invalid retained text")
                c.code <= 0x7f -> 1
                c.code <= 0x7ff -> 2
                else -> 3
            }
            require(bytes <= maximum)
        }
        cancel.throwIfCancelled()
        return bytes
    }
    private fun <T> unknown(): Knowledge<T> = Knowledge.Unknown("Source field not reported", SnapshotList.empty())
    private fun unsupported(): Nothing = throw BulkFailure(Outcome.Unavailable(UnavailableReason.UNSUPPORTED,
        "Reported catalog field is not supported"))
}
