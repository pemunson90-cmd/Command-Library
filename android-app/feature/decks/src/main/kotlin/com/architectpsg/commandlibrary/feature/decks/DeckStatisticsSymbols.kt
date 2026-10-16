package com.architectpsg.commandlibrary.feature.decks

/** Printed symbols stay alternatives, including braces. No conversion of generic integers,
 * expansion of hybrids, alternate face summing, or color-identity inference. */
internal object DeckStatisticsSymbols {
    private val colors = setOf("W", "U", "B", "R", "G")
    fun read(cost: String, poll: () -> Unit): List<String> {
        val result = ArrayList<String>()
        var index = 0
        while (index < cost.length) {
            poll()
            statisticsRequire(cost[index] == '{', message = "Malformed printed mana cost")
            val start = index++
            val bodyStart = index
            while (index < cost.length && cost[index] != '}') {
                poll()
                statisticsRequire(cost[index] != '{' && !cost[index].isWhitespace(), message = "Malformed printed mana symbol")
                index++
            }
            statisticsRequire(index < cost.length && index > bodyStart, message = "Malformed printed mana symbol")
            val body = cost.substring(bodyStart, index)
            if (!admitted(body)) statisticsUnsupported()
            result.add(cost.substring(start, ++index))
        }
        return result
    }
    private fun admitted(body: String): Boolean {
        if (body in colors || body in setOf("C", "S", "X", "Y", "Z") || body.all { it in '0'..'9' }) return true
        // Empty segments, punctuation and fractional numbers are malformed rather than future symbols.
        val parts = body.split('/')
        statisticsRequire(parts.all { it.isNotEmpty() && it.all { char -> char in 'A'..'Z' || char in '0'..'9' } },
            message = "Malformed printed mana symbol")
        if (parts.size == 2) {
            if (parts[0] in colors && parts[1] in colors) {
                statisticsRequire(parts[0] != parts[1], message = "Malformed hybrid mana symbol")
                return true
            }
            if (parts[0] == "2" && parts[1] in colors || parts[0] in colors && parts[1] == "P") return true
            if (parts.all { part -> part.all { it in '0'..'9' } })
                statisticsReject(com.architectpsg.commandlibrary.core.model.FailureCode.CORRUPT_DATA, "Fractional mana symbol is unsupported grammar")
        }
        if (parts.size == 3 && parts[0] in colors && parts[1] in colors && parts[2] == "P") {
            statisticsRequire(parts[0] != parts[1], message = "Malformed hybrid mana symbol")
            return true
        }
        return false
    }
}
