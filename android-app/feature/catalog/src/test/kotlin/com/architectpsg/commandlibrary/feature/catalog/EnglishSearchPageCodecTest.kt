package com.architectpsg.commandlibrary.feature.catalog

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test

/** Synthetic source-list protocol fixtures; no source content or completeness claim. */
class EnglishSearchPageCodecTest {
    private val initial = EnglishSearchPageCodec.INITIAL_URI
    private fun envelope(count: Int, total: Long, next: String? = null): JsonObject = JsonObject().apply {
        addProperty("object", "list"); addProperty("total_cards", total); addProperty("has_more", next != null)
        if (next != null) addProperty("next_page", next)
        add("data", JsonArray().apply { repeat(count) { index -> add(JsonObject().apply {
            addProperty("object", "card"); addProperty("lang", "en"); addProperty("id", "synthetic-$index")
        }) } })
    }
    private fun decode(root: JsonObject, uri: String = initial) = EnglishSearchPageCodec.decode(root.toString(), uri, 500_000, 128 * 1024)

    @Test fun validatesWholePageAndFinalCoverageWithoutClaimingSnapshot() {
        val next = initial.replace("page=1", "page=2") + "&format=json"
        val first = decode(envelope(175, 177, next))
        assertEquals(175, first.records.size); assertEquals(177L, first.totalCards); assertEquals(next, first.nextPage)
        val last = decode(envelope(2, 177), next)
        assertEquals(2, last.records.size); assertNull(last.nextPage)
    }
    @Test fun rejectsScopeChangesAndUntrustedContinuationOrigins() {
        listOf(initial.replace("lang%3Aen", "lang%3Afr"), initial.replace("unique=prints", "unique=cards"),
            initial.replace("include_extras=true", "include_extras=false"), initial.replace("https://", "http://"),
            initial.replace("api.scryfall.com", "evil.example"), initial + "&q=lang%3Aen", initial + "&unknown=1",
            initial + "#fragment", initial.replace("page=1", "page=0"), initial.replace("/cards/search", "/cards/named"),
            initial.replace("api.scryfall.com", "user@api.scryfall.com")).forEach { uri ->
            assertThrows(IllegalArgumentException::class.java) { EnglishSearchPageCodec.pageNumber(uri) }
        }
    }
    @Test fun rejectsForeignRecordsWarningsDuplicatesAndIncompletePagination() {
        val foreign = envelope(1, 1).apply { getAsJsonArray("data")[0].asJsonObject.addProperty("lang", "fr") }
        val duplicate = envelope(2, 2).apply { getAsJsonArray("data")[1].asJsonObject.addProperty("id", "synthetic-0") }
        val warned = envelope(1, 1).apply { add("warnings", JsonArray().apply { add("unverified") }) }
        val skipped = envelope(175, 177, initial.replace("page=1", "page=3"))
        listOf(foreign, duplicate, warned, skipped, envelope(1, 2), envelope(174, 177, initial.replace("page=1", "page=2"))).forEach { root ->
            assertThrows(IllegalArgumentException::class.java) { decode(root) }
        }
    }
    @Test fun enforcesRecordAndCountBudgetsAndIntegralCounts() {
        assertThrows(IllegalArgumentException::class.java) { EnglishSearchPageCodec.decode(envelope(1, 1).toString(), initial, 1, 2) }
        assertThrows(IllegalArgumentException::class.java) { EnglishSearchPageCodec.decode(envelope(1, 2).toString(), initial, 1, 128 * 1024) }
        assertThrows(ArithmeticException::class.java) { decode(envelope(1, 1).apply { addProperty("total_cards", 1.5) }) }
    }
}
