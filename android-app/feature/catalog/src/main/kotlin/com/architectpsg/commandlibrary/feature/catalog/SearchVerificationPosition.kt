package com.architectpsg.commandlibrary.feature.catalog

/** Independent verification fanout counts, never a replacement for committed source counters. */
internal data class SearchVerificationCounts(
    val cards: Long = 0, val faces: Long = 0, val printings: Long = 0,
    val texts: Long = 0, val legalities: Long = 0,
) {
    init { require(listOf(cards, faces, printings, texts, legalities).all { it >= 0 }) }
    fun encode(): String = listOf(cards, faces, printings, texts, legalities).joinToString(",")
    fun add(other: SearchVerificationCounts): SearchVerificationCounts = SearchVerificationCounts(
        Math.addExact(cards, other.cards), Math.addExact(faces, other.faces), Math.addExact(printings, other.printings),
        Math.addExact(texts, other.texts), Math.addExact(legalities, other.legalities))
}

/** Global rowid watermark may refer to a subsequently deleted unrelated row. NULL is first page.
 * Signed watermarks admit unrelated nonpositive rows without omitting the first page.
 */
internal data class SearchVerificationPosition(val counts: SearchVerificationCounts = SearchVerificationCounts(), val watermark: Long? = null) {
    init { require(watermark != null || counts == SearchVerificationCounts()) }

    companion object {
        fun decode(counts: String, watermark: String?): SearchVerificationPosition {
            require(counts.length in 9..99)
            val parts = counts.split(',')
            require(parts.size == 5)
            val values = parts.map {
                require(it.matches(Regex("0|[1-9][0-9]{0,18}")))
                requireNotNull(it.toLongOrNull())
            }
            val rowid = watermark?.let {
                require(it.length in 1..20 && it.matches(Regex("0|-?[1-9][0-9]{0,18}")))
                requireNotNull(it.toLongOrNull())
            }
            return SearchVerificationPosition(SearchVerificationCounts(values[0], values[1], values[2], values[3], values[4]), rowid)
        }
    }
}
