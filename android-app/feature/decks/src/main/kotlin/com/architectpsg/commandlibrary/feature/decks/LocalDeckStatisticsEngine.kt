package com.architectpsg.commandlibrary.feature.decks

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

/** Captured local English source analysis. Does not acquire data, infer roles, or modify the deck. */
public class LocalDeckStatisticsEngine(
    private val facts: CatalogDeckFactsLookup,
    private val status: CatalogStatus,
) : LocalDeckStatistics {
    override suspend fun analyze(request: DeckStatisticsRequest, cancel: CancelToken): Outcome<DeckStatisticsResult> {
        val context = currentCoroutineContext()
        val poll = { context.ensureActive(); cancel.throwIfCancelled() }
        val guarded = object : CancelToken {
            override val isCancelled: Boolean get() = !context.isActive || cancel.isCancelled
            override fun throwIfCancelled() = poll()
        }
        poll()
        return try {
            statisticsValue(DeckSnapshotValidator.validate(request.deck, guarded))
            poll()
            val entries = select(request, poll)
            checkStatus(statisticsValue(status.status(guarded)), request.snapshot, poll)
            val cache = HashMap<Pair<OracleId, CardId?>, CatalogDeckFacts?>()
            val retained = DeckStatisticsBounds(poll)
            val admitted = ArrayList<EntryDeckFacts>(entries.size)
            for (entry in entries) {
                poll()
                val oracle = entry.identity.oracleId.statisticsKnown()
                val printing = entry.identity.printingId.statisticsKnown()
                val value = if (oracle == null || oracle.source != request.snapshot.source ||
                    printing != null && printing.source != request.snapshot.source) null else {
                    val key = oracle to printing
                    if (!cache.containsKey(key)) {
                        val result = facts.facts(oracle, printing, request.snapshot, guarded)
                        poll()
                        val found = when (result) {
                            is Outcome.Success -> result.value.also {
                                retained.facts(it) // Entire retained record is bounded before any cache or aggregate insertion.
                                validate(it, oracle, printing, request.snapshot, poll)
                            }
                            is Outcome.Unavailable -> if (result.reason in setOf(UnavailableReason.NOT_FOUND,
                                UnavailableReason.NOT_INSTALLED, UnavailableReason.SOURCE_UNAVAILABLE)) null
                                else statisticsValue(result)
                            else -> statisticsValue(result)
                        }
                        cache[key] = found
                    }
                    cache[key]
                }
                admitted.add(EntryDeckFacts(entry.id, value))
            }
            val result = DeckStatisticsReducer.reduce(request, entries, admitted, poll)
            poll()
            checkStatus(statisticsValue(status.status(guarded)), request.snapshot, poll)
            poll()
            Outcome.Success(result)
        } catch (rejection: StatisticsRejection) { rejection.outcome }
    }

    private fun select(request: DeckStatisticsRequest, poll: () -> Unit): List<DeckEntry> {
        val deck = request.deck
        statisticsRequire(deck.entries.size <= DeckStatisticsLimits.MAX_ENTRIES, FailureCode.INVALID_INPUT, "Statistics entry budget exceeded")
        val snapshot = request.snapshot
        statisticsRequire(snapshot.source == ProviderId("scryfall") && snapshot.generation != null && snapshot.revision == null &&
            snapshot.language == LanguageTag.ENGLISH, FailureCode.INVALID_INPUT, "Exact English Scryfall snapshot is required")
        val inputBounds = DeckStatisticsBounds(poll, FailureCode.INVALID_INPUT)
        inputBounds.text(snapshot.source.value, DeckPayloadLimits.MAX_ID_BYTES)
        inputBounds.text(snapshot.generation!!.value, DeckPayloadLimits.MAX_ID_BYTES)
        val filter = request.filter
        statisticsRequire(filter.boards.size <= BoardRole.entries.size && filter.entryIds.let { it == null || it.size <= DeckStatisticsLimits.MAX_ENTRIES } &&
            filter.tagIds.size <= DeckStatisticsLimits.MAX_ENTRIES, FailureCode.INVALID_INPUT, "Statistics filter budget exceeded")
        val selected = filter.entryIds?.let { ids ->
            for (id in ids) { poll(); inputBounds.text(id.value, DeckPayloadLimits.MAX_ID_BYTES) }
            ids.toSet().also { statisticsRequire(it.size == ids.size, FailureCode.INVALID_INPUT, "Duplicate selected entries") }
        }
        for (id in filter.tagIds) { poll(); inputBounds.text(id.value, DeckPayloadLimits.MAX_ID_BYTES) }
        val tags = filter.tagIds.toSet()
        val boards = filter.boards.toSet()
        statisticsRequire(tags.size == filter.tagIds.size && boards.size == filter.boards.size,
            FailureCode.INVALID_INPUT, "Duplicate statistics selection")
        val allEntries = HashSet<EntityId>()
        val allTags = HashSet<EntityId>()
        deck.categoryOrder?.forEach { poll(); allTags.add(it.id) }
        for (entry in deck.entries) {
            poll()
            allEntries.add(entry.id)
            for (label in entry.labels) { poll(); allTags.add(label.id) }
            entry.identity.oracleId.statisticsKnown()?.let { inputBounds.text(it.source.value, DeckPayloadLimits.MAX_ID_BYTES); inputBounds.text(it.raw, DeckPayloadLimits.MAX_ID_BYTES) }
            entry.identity.printingId.statisticsKnown()?.let { inputBounds.text(it.source.value, DeckPayloadLimits.MAX_ID_BYTES); inputBounds.text(it.raw, DeckPayloadLimits.MAX_ID_BYTES) }
        }
        statisticsRequire(selected == null || allEntries.containsAll(selected), FailureCode.INVALID_INPUT, "Unknown selected entry")
        statisticsRequire(allTags.containsAll(tags), FailureCode.INVALID_INPUT, "Unknown selected tag")
        val result = ArrayList<DeckEntry>()
        for (entry in deck.entries) {
            poll()
            if (entry.board !in boards || selected != null && entry.id !in selected) continue
            val entryTags = entry.labels.map { poll(); it.id }.toSet()
            if (tags.isNotEmpty() && if (filter.requireAllTags) !entryTags.containsAll(tags) else tags.none { it in entryTags }) continue
            result.add(entry)
        }
        return result
    }
    private fun checkStatus(value: LocalCatalogStatus, snapshot: SnapshotReference, poll: () -> Unit) {
        poll()
        if (!value.initialized || value.activeGenerations.isEmpty() || LanguageTag.ENGLISH !in value.installedLanguages)
            throw StatisticsRejection(Outcome.Unavailable(UnavailableReason.NOT_INSTALLED, "No installed English catalog"))
        statisticsRequire(value.activeGenerations.size <= DeckStatisticsLimits.MAX_ENTRIES, message = "Invalid catalog status")
        val active = value.activeGenerations.filter { poll(); it.status == GenerationStatus.ACTIVE && LanguageTag.ENGLISH in it.languages }
        statisticsRequire(value.source.statisticsKnown() == snapshot.source && active.size == 1 &&
            active.single().id == snapshot.generation && active.single().provenance.provider == snapshot.source,
            FailureCode.STALE_GENERATION, "Captured catalog generation changed")
        statisticsRequire(active.single().provenance.generation == snapshot.generation, message = "Conflicting catalog generation provenance")
    }
    private fun validate(value: CatalogDeckFacts, oracle: OracleId, printing: CardId?, snapshot: SnapshotReference, poll: () -> Unit) {
        val card = value.card
        statisticsRequire(value.snapshot == snapshot && value.printingId == printing && card.oracleId == oracle &&
            card.language == LanguageTag.ENGLISH && card.provenance.provider == snapshot.source &&
            card.provenance.generation == snapshot.generation && card.revision == null,
            message = "Statistics facts attribution mismatch")
        for (identity in card.providerIdentities) {
            poll()
            if (identity.provider == snapshot.source && identity.kind == "oracle")
                statisticsRequire(identity.rawId == oracle.raw, message = "Statistics Oracle mapping mismatch")
        }
        val indices = HashSet<Int>()
        for (face in card.faces) {
            poll()
            statisticsRequire(face.identity.oracleId == oracle && indices.add(face.identity.index), message = "Statistics face attribution mismatch")
        }
        value.reportedProduction.statisticsKnown()?.let { symbols ->
            statisticsRequire(symbols.size <= 6 && symbols.size == symbols.toSet().size, message = "Invalid reported mana production")
            if (symbols.any { poll(); it !in setOf("W", "U", "B", "R", "G", "C") }) statisticsUnsupported()
        }
        value.reportedParts.statisticsKnown()?.let { parts ->
            for (part in parts) {
                poll()
                statisticsRequire(part.printingId.source == snapshot.source, message = "Reported part source mismatch")
                if (part.component !in setOf("token", "combo_piece", "meld_part", "meld_result")) statisticsUnsupported()
            }
        }
    }
}
