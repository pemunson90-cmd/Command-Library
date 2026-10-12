package com.architectpsg.commandlibrary.feature.catalog

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** A single bounded producer page; this is not an immutable upstream snapshot. */
internal data class EnglishSearchPage(
    val records: List<String>,
    val totalCards: Long,
    val nextPage: String?,
)

/** Only this exact server-side English printing scope may continue the default acquisition. */
internal object EnglishSearchPageCodec {
    const val INITIAL_URI = "https://api.scryfall.com/cards/search?q=lang%3Aen&unique=prints&include_extras=true&include_variations=true&include_multilingual=false&order=set&dir=asc&page=1"
    private val scope = mapOf(
        "q" to "lang:en", "unique" to "prints", "include_extras" to "true",
        "include_variations" to "true", "include_multilingual" to "false",
        "order" to "set", "dir" to "asc",
    )

    fun pageNumber(value: String): Int {
        val uri = URI(value)
        require(uri.scheme == "https" && uri.host == "api.scryfall.com" && uri.port in listOf(-1, 443)) { "Unexpected search origin" }
        require(uri.rawUserInfo == null && uri.rawFragment == null && uri.rawPath == "/cards/search") { "Invalid search location" }
        val params = linkedMapOf<String, String>()
        val query = requireNotNull(uri.rawQuery) { "Missing search scope" }
        require(query.length <= 2048) { "Search continuation exceeds limit" }
        query.split('&').forEach { pair ->
            val halves = pair.split('=', limit = 2)
            require(halves.size == 2) { "Invalid continuation parameter" }
            val key = URLDecoder.decode(halves[0], StandardCharsets.UTF_8.name())
            val decoded = URLDecoder.decode(halves[1], StandardCharsets.UTF_8.name())
            require(params.put(key, decoded) == null) { "Duplicate continuation parameter" }
        }
        require(params.keys.all { it in scope || it == "page" || it == "format" }) { "Unexpected continuation parameter" }
        require(scope.all { params[it.key] == it.value }) { "Continuation changed English printing scope" }
        require(params["format"] == null || params["format"] == "json") { "Continuation changed representation" }
        val page = requireNotNull(params["page"]) { "Missing page number" }
        require(page.matches(Regex("[1-9][0-9]{0,6}"))) { "Invalid page number" }
        return page.toInt()
    }

    fun decode(raw: String, requestedUri: String, maximumTotal: Long, maximumRecordBytes: Int): EnglishSearchPage {
        require(maximumTotal > 0 && maximumRecordBytes > 0)
        val page = pageNumber(requestedUri)
        val root = ScryfallCodec.parse(raw, 32)
        require(root["object"]?.asString == "list") { "Expected card search list" }
        root["warnings"]?.let { require(it.isJsonArray && it.asJsonArray.isEmpty) { "Search warnings prevent complete coverage" } }
        val totalField = requireNotNull(root["total_cards"]) { "Missing result count" }
        require(totalField.isJsonPrimitive && totalField.asJsonPrimitive.isNumber) { "Invalid result count" }
        val total = totalField.asBigDecimal.longValueExact()
        require(total in 1..maximumTotal) { "Result count exceeds acquisition budget" }
        val data = requireNotNull(root["data"]) { "Missing card records" }
        require(data.isJsonArray && data.asJsonArray.size() in 1..175) { "Invalid page size" }
        val ids = hashSetOf<String>() // One producer page only, never the corpus.
        val records = data.asJsonArray.map { item ->
            require(item.isJsonObject) { "Invalid card record" }
            val card = item.asJsonObject
            require(card["object"]?.asString == "card" && card["lang"]?.asString == "en") { "Non-English or invalid card record" }
            val id = requireNotNull(card["id"]) { "Missing printing identity" }
            require(id.isJsonPrimitive && id.asJsonPrimitive.isString && id.asString.isNotBlank()) { "Invalid printing identity" }
            require(ids.add(id.asString)) { "Repeated printing within page" }
            val record = card.toString()
            require(record.toByteArray(Charsets.UTF_8).size <= maximumRecordBytes) { "Card record exceeds acquisition budget" }
            record
        }
        val more = requireNotNull(root["has_more"]) { "Missing continuation state" }
        require(more.isJsonPrimitive && more.asJsonPrimitive.isBoolean) { "Invalid continuation state" }
        val next = if (more.asBoolean) {
            val value = requireNotNull(root["next_page"]) { "Missing continuation" }
            require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "Invalid continuation" }
            value.asString.also { require(pageNumber(it) == page + 1) { "Search continuation skipped or repeated a page" } }
        } else {
            require(!root.has("next_page") || root["next_page"].isJsonNull) { "Terminal page has continuation" }
            null
        }
        val completedThroughPage = (page - 1L) * 175 + records.size
        require(completedThroughPage <= total) { "Page exceeds stated coverage" }
        if (next == null) require(completedThroughPage == total) { "Terminal page does not establish stated coverage" }
        else require(records.size == 175 && completedThroughPage < total) { "Nonterminal page is incomplete" }
        return EnglishSearchPage(records, total, next)
    }
}
