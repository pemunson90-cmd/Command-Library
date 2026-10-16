package com.architectpsg.commandlibrary.feature.decks

import com.architectpsg.commandlibrary.core.model.*
import java.math.BigDecimal
import java.math.MathContext
import java.util.TreeMap

/** Reduces admitted, source-qualified facts. Service owns IO/selection/snapshot fencing. */
internal object DeckStatisticsReducer {
    fun reduce(request: DeckStatisticsRequest, entries: List<DeckEntry>, facts: List<EntryDeckFacts>, poll: () -> Unit): DeckStatisticsResult {
        statisticsRequire(entries.size == facts.size && entries.size <= DeckStatisticsLimits.MAX_ENTRIES)
        val curve = TreeMap<BigDecimal, Long>() // compareTo makes 1.0 and 1.00 the same bucket.
        val types = sortedMapOf<String, Long>()
        val symbols = sortedMapOf<String, Long>()
        val production = sortedMapOf<String, Long>()
        val tokens = linkedMapOf<Pair<CardId, String>, ReportedCardPart>()
        val labels = linkedMapOf<EntityId, StatisticTagCoverage>()
        val issues = linkedSetOf<String>()
        var copies = 0L
        var unresolved = 0L
        var unknownMv = 0L
        var unknownTypes = 0L
        var unknownCost = 0L
        var unknownProduction = 0L
        var unknownTokens = 0L
        var knownMv = 0L
        var nonlandMv = 0L
        var weightedMv = BigDecimal.ZERO
        var weightedNonland = BigDecimal.ZERO
        for (index in entries.indices) {
            poll()
            val entry = entries[index]
            statisticsRequire(entry.id == facts[index].entryId, message = "Statistics entry attribution mismatch")
            val quantity = entry.quantity.toLong()
            copies = statisticsAdd(copies, quantity)
            for (label in entry.labels) {
                poll()
                val previous = labels[label.id]
                statisticsRequire(previous == null || previous.label == label, message = "Contradictory entry label")
                labels[label.id] = StatisticTagCoverage(label, (previous?.entries ?: 0) + 1,
                    statisticsAdd(previous?.copies ?: 0, quantity))
            }
            val source = facts[index].facts
            if (source == null) {
                unresolved = statisticsAdd(unresolved, quantity)
                unknownMv = statisticsAdd(unknownMv, quantity)
                unknownTypes = statisticsAdd(unknownTypes, quantity)
                unknownCost = statisticsAdd(unknownCost, quantity)
                unknownTokens = statisticsAdd(unknownTokens, quantity)
                issues.add("unresolved")
                continue
            }
            val card = source.card
            val mana = card.manaValue.statisticsKnown()
            val printedTypes = card.types.statisticsKnown()?.types
            if (mana == null) { unknownMv = statisticsAdd(unknownMv, quantity); issues.add("unknown_mv") }
            else {
                curve[mana] = statisticsAdd(curve[mana] ?: 0, quantity)
                val weighted = mana.multiply(BigDecimal.valueOf(quantity))
                weightedMv = weightedMv.add(weighted)
                knownMv = statisticsAdd(knownMv, quantity)
                if (printedTypes != null && "Land" !in printedTypes) {
                    weightedNonland = weightedNonland.add(weighted)
                    nonlandMv = statisticsAdd(nonlandMv, quantity)
                }
            }
            if (printedTypes == null) { unknownTypes = statisticsAdd(unknownTypes, quantity); issues.add("unknown_types") }
            else for (type in printedTypes) { poll(); add(types, type, quantity) }
            val layout = card.layout.statisticsKnown()
            val cost = card.manaCost.statisticsKnown()
            if (layout !in setOf("normal", "saga", "class", "leveler", "prototype", "mutate")) {
                unknownCost = statisticsAdd(unknownCost, quantity)
                issues.add("alternative_cost")
            } else if (cost == null) {
                unknownCost = statisticsAdd(unknownCost, quantity)
                issues.add("unknown_cost")
            } else for (symbol in DeckStatisticsSymbols.read(cost, poll)) { poll(); add(symbols, symbol, quantity) }
            if (printedTypes != null && "Land" in printedTypes) {
                val possible = source.reportedProduction.statisticsKnown()
                if (possible == null) { unknownProduction = statisticsAdd(unknownProduction, quantity); issues.add("unknown_production") }
                else for (symbol in possible) { poll(); add(production, symbol, quantity) }
            }
            val parts = source.reportedParts.statisticsKnown()
            if (parts == null) { unknownTokens = statisticsAdd(unknownTokens, quantity); issues.add("unknown_tokens") }
            else for (part in parts) {
                poll()
                if (part.component != "token") continue
                val key = part.printingId to part.component
                val previous = tokens[key]
                tokens[key] = if (previous == null) part else previous.copy(
                    name = merge(previous.name, part.name), typeLine = merge(previous.typeLine, part.typeLine))
            }
        }
        poll()
        return DeckStatisticsResult(request.deck.id, request.deck.revision, request.snapshot, request.filter,
            SnapshotList.copyOf(entries.map { poll(); it.id }), copies, unresolved,
            SnapshotList.copyOf(curve.map { poll(); StatisticManaBucket(it.key, it.value) }), unknownMv,
            average(weightedMv, knownMv), average(weightedNonland, nonlandMv), counts(types, poll), unknownTypes,
            counts(symbols, poll), unknownCost, counts(production, poll), unknownProduction,
            SnapshotList.copyOf(tokens.values), unknownTokens, SnapshotList.copyOf(labels.values),
            SnapshotList.copyOf(issues.map { issue(it) }))
    }
    private fun add(counts: MutableMap<String, Long>, name: String, quantity: Long) {
        counts[name] = statisticsAdd(counts[name] ?: 0, quantity)
    }
    private fun counts(values: Map<String, Long>, poll: () -> Unit): SnapshotList<StatisticCount> =
        SnapshotList.copyOf(values.map { poll(); StatisticCount(it.key, it.value) })
    private fun average(sum: BigDecimal, copies: Long): StatisticAverage = StatisticAverage(
        if (copies == 0L) Knowledge.Unknown("No known values in the selected denominator", SnapshotList.empty())
        else Knowledge.Known(sum.divide(BigDecimal.valueOf(copies), MathContext.DECIMAL128)), copies)
    private fun merge(a: Knowledge<String>, b: Knowledge<String>): Knowledge<String> {
        val first = a.statisticsKnown()
        val second = b.statisticsKnown()
        statisticsRequire(first == null || second == null || first == second, message = "Contradictory reported token reference")
        return if (first != null) a else if (second != null) b else a
    }
    private fun issue(reason: String): DomainIssue {
        val message = when (reason) {
            "unresolved" -> "Some selected copies have no exact installed Oracle facts; unknown types may hide land sources."
            "unknown_mv" -> "Some source mana values are unknown; averages use only their reported known denominators."
            "unknown_types" -> "Some source card types are unknown; those copies are excluded from the nonland denominator and may hide land sources."
            "alternative_cost" -> "Unknown or alternative-cost layouts do not have one admitted printed cost; face or alternate costs are not summed."
            "unknown_cost" -> "Some main printed mana costs were not reported; unknown costs are not zero symbols."
            "unknown_production" -> "Some known lands lack reported production; reported colors elsewhere are possibilities with unclassified conditions."
            "unknown_tokens" -> "Some related-part facts were not reported; no token creation quantity or direction is inferred."
            else -> statisticsReject(FailureCode.INTERNAL, "Invalid statistics issue reason")
        }
        return DomainIssue("statistics_$reason", IssueSeverity.INFORMATION, message, null, SnapshotList.empty())
    }
}
