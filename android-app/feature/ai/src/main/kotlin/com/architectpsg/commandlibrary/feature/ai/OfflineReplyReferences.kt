package com.architectpsg.commandlibrary.feature.ai

import com.architectpsg.commandlibrary.core.model.*
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun offlineCancellation(cancel: CancelToken) {
    currentCoroutineContext().ensureActive()
    cancel.throwIfCancelled()
}

/** One exact explicit-generation query. No preparation, acquisition or current-generation fallback. */
internal class OfflineReplyReferences(private val query: LocalCardQuery) {
    data class Lookup(val status: OfflineReferenceStatus, val cards: SnapshotList<CardRecord>)

    suspend fun lookup(name: String, snapshot: SnapshotReference?, cancel: CancelToken): Lookup {
        offlineCancellation(cancel)
        if (!supported(snapshot)) return Lookup(OfflineReferenceStatus.UNAVAILABLE, SnapshotList.empty())
        val frame = requireNotNull(snapshot)
        val normalizedRequested = try { normalizedName(name, cancel) }
        catch (_: OfflineInputException) { return Lookup(OfflineReferenceStatus.UNAVAILABLE, SnapshotList.empty()) }
        val result = try {
            query.local(QueryAst.Text(SearchTextField.NAME, name, true),
                SearchContext(SnapshotList.of(frame), SnapshotList.of(LanguageTag.ENGLISH), FaceMatchPolicy.CANONICAL), PageRequest(2), cancel)
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { offlineCancellation(cancel); return Lookup(OfflineReferenceStatus.UNAVAILABLE, SnapshotList.empty()) }
        offlineCancellation(cancel)
        if (result !is Outcome.Success) return Lookup(OfflineReferenceStatus.UNAVAILABLE, SnapshotList.empty())
        val page = result.value
        if (page.snapshot != frame || page.items.size > 2) return Lookup(OfflineReferenceStatus.UNAVAILABLE, SnapshotList.empty())
        try {
            for (card in page.items) {
                offlineCancellation(cancel)
                val normalizedOriginal = normalizedName(card.name, cancel)
                OfflinePromptText.utf8Size(card.oracleId.raw, 4096, cancel)
                OfflinePromptText.utf8Size(card.oracleId.source.value, 128, cancel)
                OfflinePromptText.utf8Size(card.provenance.provider.value, 128, cancel)
                card.provenance.generation?.let { OfflinePromptText.utf8Size(it.value, 128, cancel) }
                if (card.oracleId.source != frame.source || card.language != frame.language ||
                    card.provenance.provider != frame.source || card.provenance.generation != frame.generation ||
                    card.revision != frame.revision || normalizedOriginal != normalizedRequested)
                    return Lookup(OfflineReferenceStatus.UNAVAILABLE, SnapshotList.empty())
            }
        } catch (_: OfflineInputException) { return Lookup(OfflineReferenceStatus.UNAVAILABLE, SnapshotList.empty()) }
        return Lookup(when {
            page.nextCursor != null || page.items.size > 1 -> OfflineReferenceStatus.AMBIGUOUS
            page.items.isEmpty() -> OfflineReferenceStatus.NOT_FOUND
            else -> OfflineReferenceStatus.FOUND
        }, page.items)
    }

    /** Same NAME policy as the prepared catalog/compiler; raw values and exact IDs stay untouched. */
    private fun normalizedName(value: String, cancel: CancelToken): String {
        OfflinePromptText.utf8Size(value, 512, cancel)
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim()
        OfflinePromptText.utf8Size(normalized, 4096, cancel)
        return normalized
    }

    fun parse(reply: String, cancel: CancelToken): List<String> {
        OfflinePromptText.utf8Size(reply, 256 * 1024, cancel)
        val names = ArrayList<String>()
        var index = 0
        while (index < reply.length) {
            if (index % 1024 == 0) cancel.throwIfCancelled()
            when {
                reply.startsWith("[[", index) -> {
                    val start = index + 2
                    val end = reply.indexOf("]]", start)
                    if (end < 0 || end - start > 512 || names.size >= 50) throw OfflineInputException()
                    val name = reply.substring(start, end)
                    OfflinePromptText.utf8Size(name, 512, cancel)
                    if (name.isBlank() || '[' in name || ']' in name || '\n' in name || '\r' in name || '\u0000' in name) throw OfflineInputException()
                    names.add(name); index = end + 2
                }
                reply.startsWith("]]", index) -> throw OfflineInputException()
                else -> index++
            }
        }
        return names
    }

    fun angle(reply: String): Knowledge<String> {
        val declarations = reply.lineSequence().filter { it.startsWith("Surprise angle:") }.toList()
        if (declarations.isEmpty()) return Knowledge.Absent("No declared surprise angle")
        val permitted = setOf("hidden synergies", "alternate win conditions", "theme reads", "spicy tech")
        val values = declarations.map { it.removePrefix("Surprise angle: ") }
        return if (values.all { it in permitted } && values.distinct().size == 1) Knowledge.Known(values.first())
        else Knowledge.Unknown("Invalid or conflicting surprise declaration", SnapshotList.empty())
    }

    companion object {
        fun preflight(snapshot: SnapshotReference?, cancel: CancelToken) {
            if (snapshot == null) return
            OfflinePromptText.utf8Size(snapshot.source.value, 128, cancel)
            snapshot.generation?.let { OfflinePromptText.utf8Size(it.value, 128, cancel) }
            snapshot.language?.let { OfflinePromptText.utf8Size(it.value, 32, cancel) }
        }
        fun supported(snapshot: SnapshotReference?): Boolean = snapshot != null && snapshot.source.value == "scryfall" &&
            snapshot.generation != null && snapshot.revision == null && snapshot.language == LanguageTag.ENGLISH
    }
}
