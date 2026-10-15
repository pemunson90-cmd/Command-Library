package com.architectpsg.commandlibrary.feature.ai

import com.architectpsg.commandlibrary.core.model.*
import java.math.BigDecimal

/** Serializes only explicit semantic fields. Unknown diagnostics, locators and profile data stay out. */
internal object OfflineDeckContext {
    data class Enriched(val text: String, val included: Int, val omitted: Int)
    private data class Cached(val id: OracleId?, val text: String?)

    fun mandatory(request: OfflinePromptRequest, cancel: CancelToken): String {
        val deck = request.deck
        if (deck.entries.size > 10000 || deck.catalog.size > 128) throw OfflineInputException()
        val json = OfflinePromptText.Json(OfflinePromptText.DECK_BYTES, cancel)
        with(json) {
            raw("{\"deckId\":{\"source\":"); identity(deck.id.source.value, cancel); raw(",\"raw\":"); identity(deck.id.raw, cancel)
            raw("},\"revision\":${deck.revision.value},\"name\":"); string(deck.name)
            raw(",\"format\":"); knowledge(deck.format) { raw("{\"source\":"); identity(it.source.value, cancel); raw(",\"raw\":"); identity(it.raw, cancel); raw("}") }
            raw(",\"budget\":"); knowledge(deck.budget) { amount(it, cancel) }
            raw(",\"catalog\":[")
            deck.catalog.forEachIndexed { index, reference ->
                cancel.throwIfCancelled(); OfflineReplyReferences.preflight(reference, cancel)
                if (index > 0) raw(","); snapshot(reference)
            }
            raw("],\"entries\":[")
            deck.entries.forEachIndexed { index, entry ->
                cancel.throwIfCancelled()
                if (index > 0) raw(",")
                raw("{\"entryId\":"); identity(entry.id.value, cancel)
                raw(",\"quantity\":${entry.quantity},\"board\":"); string(entry.board.name)
                raw(",\"identity\":{\"oracleId\":"); knowledge(entry.identity.oracleId) { oracleIdentity(it, cancel) }
                raw(",\"printingId\":"); knowledge(entry.identity.printingId) {
                    raw("{\"source\":"); identity(it.source.value, cancel); raw(",\"raw\":"); identity(it.raw, cancel); raw("}")
                }
                raw(",\"raw\":")
                val foreign = entry.identity.raw
                if (foreign == null) raw("null") else {
                    raw("{\"source\":"); identity(foreign.provider.value, cancel); raw(",\"kind\":"); identity(foreign.kind, cancel)
                    raw(",\"raw\":"); identity(foreign.rawId, cancel); raw("}")
                }
                raw(",\"originalName\":"); knowledge(entry.identity.originalName) { string(it) }
                raw(",\"language\":"); identity(entry.identity.language.value, cancel); raw("},\"finish\":"); knowledge(entry.finish) { string(it.name) }
                if (request.includePrivateNotes) { raw(",\"notes\":"); string(entry.notes) }
                raw("}")
            }
            raw("]")
            if (request.includePrivateNotes) { raw(",\"description\":"); string(deck.description); raw(",\"primer\":"); string(deck.primer) }
            raw("}")
        }
        return json.toString()
    }

    suspend fun enrich(request: OfflinePromptRequest, references: OfflineReplyReferences, cancel: CancelToken): Enriched {
        val cache = LinkedHashMap<String, Cached>()
        var cacheBytes = 0
        var totalBytes = 2
        var included = 0
        val optional = StringBuilder("[")
        for (entry in request.deck.entries) {
            offlineCancellation(cancel)
            val expected = (entry.identity.oracleId as? Knowledge.Known)?.value
            val name = (entry.identity.originalName as? Knowledge.Known)?.value
            if (expected == null || name == null || expected.source != request.snapshot?.source || !OfflineReplyReferences.supported(request.snapshot)) continue
            try { OfflinePromptText.utf8Size(name, 512, cancel) } catch (_: OfflineInputException) { continue }
            if (!cache.containsKey(name) && cache.size < 50) {
                val found = references.lookup(name, request.snapshot, cancel)
                val card = found.cards.singleOrNull().takeIf { found.status == OfflineReferenceStatus.FOUND }
                val text = if (card == null) null else try { original(card, cancel) } catch (_: OfflineInputException) { null }
                val size = text?.let { OfflinePromptText.utf8Size(it, OfflinePromptText.CARD_BYTES, cancel) } ?: 0
                val retained = if (size <= OfflinePromptText.OPTIONAL_BYTES - cacheBytes) text else null
                if (retained != null) cacheBytes += size
                cache[name] = Cached(card?.oracleId, retained)
            }
            val value = cache[name] ?: continue
            if (value.id != expected || value.text == null) continue
            val block = try { OfflinePromptText.Json(OfflinePromptText.CARD_BYTES, cancel).apply {
                raw("{\"entryId\":").string(entry.id.value).raw(",\"originalOracle\":").raw(value.text).raw("}")
            }.toString() } catch (_: OfflineInputException) { continue }
            val size = OfflinePromptText.utf8Size(block, OfflinePromptText.CARD_BYTES, cancel) + if (included > 0) 1 else 0
            if (size > OfflinePromptText.OPTIONAL_BYTES - totalBytes) continue
            if (included > 0) optional.append(',')
            optional.append(block); totalBytes += size; included++
        }
        optional.append(']')
        return Enriched(optional.toString(), included, request.deck.entries.size - included)
    }

    private fun original(card: CardRecord, cancel: CancelToken): String = OfflinePromptText.Json(OfflinePromptText.CARD_BYTES, cancel).apply {
        if (card.faces.size > 32) throw OfflineInputException()
        raw("{\"oracleId\":"); oracleIdentity(card.oracleId, cancel)
        raw(",\"name\":"); string(card.name); raw(",\"language\":"); string(card.language.value)
        raw(",\"source\":"); string(card.provenance.provider.value); raw(",\"generation\":"); string(requireNotNull(card.provenance.generation).value)
        raw(",\"version\":"); knowledge(card.provenance.version) { string(it) }
        raw(",\"originalText\":"); knowledge(card.originalText) { string(it) }
        raw(",\"manaCost\":"); knowledge(card.manaCost) { string(it) }
        raw(",\"manaValue\":"); knowledge(card.manaValue) { decimal(it, cancel) }
        raw(",\"types\":"); knowledge(card.types) { string(it.original) }
        raw(",\"colors\":"); knowledge(card.colors) { colors(it) }
        raw(",\"colorIdentity\":"); knowledge(card.colorIdentity) { colors(it) }
        raw(",\"stats\":"); stats(card.stats)
        raw(",\"keywords\":"); knowledge(card.keywords) {
            if (it.size > 256) throw OfflineInputException()
            raw("["); it.forEachIndexed { index, keyword -> if (index > 0) raw(","); string(keyword) }; raw("]")
        }
        raw(",\"layout\":"); knowledge(card.layout) { string(it) }
        raw(",\"reserved\":"); knowledge(card.reserved) { raw(it.toString()) }
        raw(",\"faces\":[")
        card.faces.forEachIndexed { index, face ->
            cancel.throwIfCancelled()
            if (face.identity.oracleId != card.oracleId) throw OfflineInputException()
            if (index > 0) raw(",")
            raw("{\"index\":${face.identity.index},\"name\":"); string(face.name)
            raw(",\"originalText\":"); knowledge(face.text) { string(it) }
            raw(",\"manaCost\":"); knowledge(face.manaCost) { string(it) }
            raw(",\"types\":"); knowledge(face.types) { string(it.original) }
            raw(",\"stats\":"); stats(face.stats); raw("}")
        }
        raw("]}")
    }.toString()

    private fun OfflinePromptText.Json.identity(value: String, cancel: CancelToken) { OfflinePromptText.utf8Size(value, 4096, cancel); string(value) }
    private fun OfflinePromptText.Json.oracleIdentity(value: OracleId, cancel: CancelToken) {
        raw("{\"source\":"); identity(value.source.value, cancel); raw(",\"raw\":"); identity(value.raw, cancel); raw("}")
    }
    private fun OfflinePromptText.Json.decimal(value: BigDecimal, cancel: CancelToken) {
        cancel.throwIfCancelled()
        if (value.scale() !in -1024..1024 || value.precision() > 1024) throw OfflineInputException()
        string(value.toPlainString())
    }
    private fun OfflinePromptText.Json.amount(value: DecimalAmount, cancel: CancelToken) {
        raw("{\"value\":"); decimal(value.value, cancel); raw(",\"unit\":")
        when (val unit = value.unit) {
            is DecimalUnit.Fiat -> { raw("{\"fiat\":"); string(unit.currencyCode); raw("}") }
            DecimalUnit.Tix -> string("tix")
            is DecimalUnit.Native -> { raw("{\"source\":"); identity(unit.provider.value, cancel); raw(",\"symbol\":"); identity(unit.symbol, cancel); raw("}") }
        }
        raw("}")
    }
    private fun OfflinePromptText.Json.colors(values: SnapshotList<Color>) {
        if (values.size > 5) throw OfflineInputException()
        raw("["); values.forEachIndexed { index, color -> if (index > 0) raw(","); string(color.name) }; raw("]")
    }
    private fun OfflinePromptText.Json.stats(value: CardStats) {
        raw("{\"power\":"); knowledge(value.power) { string(it) }
        raw(",\"toughness\":"); knowledge(value.toughness) { string(it) }
        raw(",\"loyalty\":"); knowledge(value.loyalty) { string(it) }
        raw(",\"defense\":"); knowledge(value.defense) { string(it) }; raw("}")
    }
}
