package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class PrintingLink(val target: String, val result: PortResult<ResolvedLink>)
internal data class PrintingSelectionState(
    val card: CardRecord? = null,
    val page: Page<PrintingRecord>? = null,
    val selected: PrintingRecord? = null,
    val loading: Boolean = false,
    val problem: Problem? = null,
    val links: List<PrintingLink> = emptyList(),
    val linksLoading: Boolean = false,
    val launchFailed: Boolean = false,
    val ticket: Long = 0,
)

/** Local reader only. Selection lifecycle belongs to the ViewModel, never a width-dependent pane. */
internal class PrintingSelectionController(
    private val scope: CoroutineScope,
    private val port: CatalogPrintingLookup,
    private val dispatcher: CoroutineDispatcher,
    private val resolver: CardLinks? = null,
    private val isCardCurrent: (CardRecord) -> Boolean = { true },
) {
    private val mutable = MutableStateFlow(PrintingSelectionState())
    val state: StateFlow<PrintingSelectionState> = mutable.asStateFlow()
    private var job: Job? = null
    private var ticket = 0L
    private var visible = false
    private var card: CardRecord? = null
    private var generationKnown = false
    private var activeGeneration: GenerationId? = null

    fun selectCard(value: CardRecord?) {
        if (card == value) return
        card = value
        invalidate()
        if (visible && value != null) load(null)
    }

    fun setVisible(value: Boolean) {
        if (visible == value) return
        visible = value
        invalidate()
        if (value && card != null) load(null)
    }

    fun onActiveGeneration(value: GenerationId?) {
        generationKnown = true
        if (activeGeneration == value && card?.provenance?.generation == value) return
        activeGeneration = value
        if (card != null && !currentGeneration()) {
            invalidate()
            mutable.value = PrintingSelectionState(card = card, problem = Problem.STALE_GENERATION, ticket = ticket)
        }
    }

    fun reload() { if (visible && card != null) load(null) }
    fun next() {
        val s = state.value
        if (!s.loading && s.problem == null) s.page?.nextCursor?.let(::load)
    }

    fun selectPrinting(id: CardId) {
        val s = state.value
        if (!visible || s.loading || s.problem != null || !currentGeneration()) return
        val printing = s.page?.items?.singleOrNull { it.id == id } ?: return
        job?.cancel()
        val current = ++ticket
        mutable.value = s.copy(selected = printing, links = emptyList(), linksLoading = resolver != null,
            launchFailed = false, ticket = current)
        val sourceCard = card ?: return
        if (resolver != null) job = scope.launch {
            val links = TARGETS.map { target ->
                PrintingLink(target, resolve(target, sourceCard, printing))
            }
            if (current == ticket) mutable.update { it.copy(links = links, linksLoading = false) }
        }
    }

    /** Re-resolve the displayed target, then fence AGAIN after dispatcher return before browser IO. */
    /** Recheck current selection synchronously before a root-owned collection navigation event. */
    fun withCurrentPrinting(expectedTicket: Long, id: CardId, accept: (PrintingRecord) -> Unit) {
        val s = state.value
        if (!visible || expectedTicket != ticket || !currentGeneration() || s.loading || s.problem != null || s.card != card) return
        val selected = s.selected?.takeIf { it.id == id && s.page?.items?.any { item -> item == it } == true } ?: return
        accept(selected)
    }

    fun openLink(expectedTicket: Long, target: String, open: (ResourceUri) -> Unit) {
        val s = state.value
        val selected = s.selected ?: return
        val sourceCard = s.card ?: return
        val shown = (s.links.singleOrNull { it.target == target }?.result as? PortResult.Ok)?.value ?: return
        if (!launchable(expectedTicket, selected.id)) return
        scope.launch {
            val checked = resolve(target, sourceCard, selected)
            if (!launchable(expectedTicket, selected.id)) return@launch
            if (checked is PortResult.Ok && checked.value == shown) open(checked.value.uri)
            else mutable.update { it.copy(launchFailed = true) }
        }
    }

    fun launchFailed(expectedTicket: Long) {
        if (expectedTicket == ticket) mutable.update { it.copy(launchFailed = true) }
    }

    private fun launchable(expected: Long, selected: CardId): Boolean = visible && expected == ticket &&
        currentGeneration() && !state.value.loading && !state.value.linksLoading &&
        state.value.problem == null && state.value.selected?.id == selected

    private suspend fun resolve(target: String, sourceCard: CardRecord, printing: PrintingRecord): PortResult<ResolvedLink> =
        callPort(dispatcher) {
            val actual = resolver ?: return@callPort Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "No link resolver")
            when (val result = actual.resolve(sourceCard, printing, preferences(target))) {
                is Outcome.Success -> if (result.value.size == 1) Outcome.Success(result.value.single())
                    else Outcome.Failure(DomainFailure(FailureCode.CORRUPT_DATA, "Expected one configured link", SnapshotList.empty()))
                is Outcome.Failure -> result
                is Outcome.Unavailable -> result
                is Outcome.Unknown -> result
            }
        }

    private fun invalidate() {
        job?.cancel(); ++ticket
        mutable.value = PrintingSelectionState(card = card, ticket = ticket)
    }

    private fun currentGeneration(): Boolean = card?.let(isCardCurrent) == true && card?.provenance?.generation != null &&
        (!generationKnown || card?.provenance?.generation == activeGeneration)

    private fun load(cursor: String?) {
        val sourceCard = card ?: return
        invalidate()
        val current = ticket
        if (!currentGeneration()) {
            mutable.update { it.copy(problem = Problem.STALE_GENERATION) }; return
        }
        mutable.update { it.copy(loading = true) }
        job = scope.launch {
            when (val result = callPort(dispatcher) { port.printings(sourceCard.oracleId, PageRequest(PAGE_SIZE, cursor), it) }) {
                is PortResult.Ok -> if (current == ticket) {
                    val page = result.value
                    val valid = page.items.size <= PAGE_SIZE && page.snapshot.generation == sourceCard.provenance.generation &&
                        page.snapshot.source == sourceCard.oracleId.source && page.snapshot.language == sourceCard.language &&
                        page.items.map { it.id }.distinct().size == page.items.size &&
                        (page.nextCursor == null || (page.items.isNotEmpty() && page.nextCursor != cursor)) &&
                        page.items.all { printing ->
                            printing.id.source == page.snapshot.source && printing.provenance.provider == page.snapshot.source &&
                                printing.provenance.generation == page.snapshot.generation &&
                                ((printing.oracleId as? Knowledge.Known)?.value?.let { it == sourceCard.oracleId } != false)
                        }
                    mutable.update { it.copy(page = page.takeIf { valid }, loading = false,
                        problem = if (valid) null else Problem.STALE_GENERATION) }
                }
                is PortResult.Failed -> if (current == ticket) mutable.update { it.copy(loading = false, problem = result.problem) }
            }
        }
    }

    companion object {
        const val PAGE_SIZE = 10
        val TARGETS = listOf("scryfall", "gatherer", "edhrec", "spellbook", "tcgplayer", "cardmarket", "cardkingdom", "cardhoarder")
        private val hosts = mapOf("scryfall" to "scryfall.com", "gatherer" to "gatherer.wizards.com", "edhrec" to "edhrec.com",
            "spellbook" to "commanderspellbook.com", "tcgplayer" to "www.tcgplayer.com", "cardmarket" to "www.cardmarket.com",
            "cardkingdom" to "www.cardkingdom.com", "cardhoarder" to "www.cardhoarder.com")
        private fun preferences(target: String): LinkPreferences {
            val destination = LinkDestination(EntityId(target), target,
                SnapshotList.of(Origin("https", hosts.getValue(target), 443), Origin("http", hosts.getValue(target), 80)), true)
            return LinkPreferences(SnapshotList.of(destination), SnapshotList.empty(), true, false)
        }
    }
}
