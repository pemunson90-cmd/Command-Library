package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.CatalogImportLimits
import com.architectpsg.commandlibrary.feature.shell.ShellPorts
import com.architectpsg.commandlibrary.feature.shell.document.DocumentSource
import com.architectpsg.commandlibrary.feature.shell.document.PlainDeckInputSource
import com.architectpsg.commandlibrary.feature.shell.document.PlainDeckDocumentWriter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/**
 * All shell presentation state for one owner. Every job runs in [scope], so cancelling the scope
 * cancels a running import and pending reads. A new instance holds no remembered result: it reads
 * committed catalog status and settings from the ports. [ioDispatcher] runs every port call and
 * document read, keeping parsing and storage work off the UI thread.
 */
internal class ShellControllers(
    scope: CoroutineScope,
    ports: ShellPorts,
    documents: DocumentSource,
    ioDispatcher: CoroutineDispatcher,
    documentWriter: PlainDeckDocumentWriter? = null,
) {
    val drawProbability = DrawProbabilityController(scope, kotlinx.coroutines.Dispatchers.Default)
    val importLimits: CatalogImportLimits = ports.importLimits
    val status = CatalogStatusController(scope, ports.catalogStatus, ioDispatcher)
    val search = SearchController(scope, ports.catalogLookup, ioDispatcher, textLookup = ports.catalogTextLookup, enableSuggestions = true, onStaleCursor = status::refresh)
    val advancedQuery = if (ports.localCardQuery != null && ports.catalogSearchPreparation != null && ports.cardQueryParser != null)
        AdvancedQueryController(scope, ports.localCardQuery, ports.catalogSearchPreparation, ports.cardQueryParser, ioDispatcher) else null
    val catalogImport = ImportController(
        scope = scope,
        port = ports.catalogImport,
        documents = documents,
        limits = ports.importLimits,
        ioDispatcher = ioDispatcher,
        onFinished = status::refresh,
    )
    val settings = SettingsController(scope, ports.settings, ioDispatcher)
    val rules: RulesController? = if (ports.rulesImport != null && ports.rulesLookup != null) {
        RulesController(scope, ports.rulesImport, ports.rulesLookup, documents, ioDispatcher, keywordLookup = ports.keywordRulesLookup)
    } else null

    val deckEditorSearch = if (ports.localDecks != null && ports.localDeckEdits != null)
        SearchController(scope, ports.catalogLookup, ioDispatcher, onStaleCursor = status::refresh) else null
    val deckDraftEditor: DeckDraftEditor? = if (ports.localDecks != null && ports.localDeckDrafts != null &&
        ports.deckDraftPlanner != null && ports.deckPatchProjection != null)
        RetainedDeckDraftEditor(scope, ports.localDecks, ports.localDeckDrafts, ports.deckDraftPlanner,
            ports.deckPatchProjection, ioDispatcher,
            { com.architectpsg.commandlibrary.core.model.EntityId(java.util.UUID.randomUUID().toString()) },
            { android.os.SystemClock.elapsedRealtime() },
            cardCurrent = { card -> deckEditorSearch?.state?.value?.let {
                currentDeckCard(card, it, status.state.value.status?.activeGeneration()?.id)
            } == true },
            onCommitted = { id, revision -> deckLibrary?.onDraftCommitted(id, revision) }) else null
    val deckLibrary: DeckLibraryController? = if (ports.localDecks != null && ports.utcClock != null)
        DeckLibraryController(scope, ports.localDecks, ioDispatcher, now = ports.utcClock::utcNow, edits = ports.localDeckEdits,
            draftEditor = deckDraftEditor,
            onCommitted = { id, revision ->
                collection?.onDeckChanged(id, revision)
                deckMarkOwned?.onDeckChanged(id, revision)
                if (deckVersions?.state?.value?.deckId == id) deckVersions.refreshHead()
            },
            cardCurrent = { card -> deckEditorSearch?.state?.value?.let {
                currentDeckCard(card, it, status.state.value.status?.activeGeneration()?.id)
            } == true }) else null
    val inventoryMutationGate = InventoryMutationGate()
    val collection = if (ports.localInventory != null && ports.localDecks != null && ports.ownedQuantity != null &&
        ports.catalogPrintings != null && ports.inventoryProjection != null)
        CollectionController(scope, ports.localInventory, ports.localDecks, ports.ownedQuantity, ports.catalogPrintings,
            ports.inventoryProjection, ioDispatcher, { com.architectpsg.commandlibrary.core.model.EntityId(java.util.UUID.randomUUID().toString()) },
            currentCatalogGeneration = { status.state.value.status?.activeGeneration()?.id },
            inventoryMutationGate = inventoryMutationGate) else null
    val deckMarkOwned: DeckMarkOwnedController? = if (ports.localInventory != null && ports.localDecks != null &&
        ports.ownedQuantity != null && ports.catalogPrintings != null && ports.inventoryProjection != null)
        DeckMarkOwnedController(scope, ports.localInventory, ports.localDecks, ports.ownedQuantity,
            ports.catalogPrintings, ports.inventoryProjection, ioDispatcher,
            { com.architectpsg.commandlibrary.core.model.EntityId(java.util.UUID.randomUUID().toString()) },
            currentCatalogGeneration = { status.state.value.status?.activeGeneration()?.id },
            mutationGate = inventoryMutationGate,
            onCommitted = { fresh -> collection?.onInventoryChanged(fresh) }) else null
    val deckVersions: DeckVersionsController? = if (ports.localDeckVersions != null && ports.localDecks != null)
        DeckVersionsController(scope, ports.localDeckVersions, ports.localDecks, ioDispatcher,
            { com.architectpsg.commandlibrary.core.model.EntityId(java.util.UUID.randomUUID().toString()) },
            mutationAllowed = { deckDraftEditor?.hasMutationBarrier != true },
            onCommitted = { id, revision ->
                deckDraftEditor?.onDeckHeadChanged(id, revision)
                collection?.onDeckChanged(id, revision)
                deckMarkOwned?.onDeckChanged(id, revision)
                deckLibrary?.let { library ->
                    if (library.state.value.selectedId == id) library.select(id)
                    library.refresh()
                }
            }) else null
    val plainDeckInputs = PlainDeckInputSource(documents, ioDispatcher)
    val plainDeckImport: PlainDeckImportController? = if (ports.plainDeckLists != null && ports.localDecks != null &&
        ports.utcClock != null && ports.deckSnapshotValidation != null)
        PlainDeckImportController(scope, ports.plainDeckLists, ports.localDecks, ioDispatcher,
            { com.architectpsg.commandlibrary.core.model.EntityId(java.util.UUID.randomUUID().toString()) },
            ports.utcClock::utcNow, ports.deckSnapshotValidation,
            onCommitted = {
                // Import creates a separate deck: refresh browsers without replacing an editor.
                deckLibrary?.refresh()
                collection?.refreshDecks()
            }) else null
    val plainDeckExport: PlainDeckExportController? = if (documentWriter != null && ports.plainDeckLists != null &&
        ports.localDecks != null && ports.deckSnapshotValidation != null)
        PlainDeckExportController(scope = scope, library = ports.localDecks, codec = ports.plainDeckLists,
            validate = ports.deckSnapshotValidation, write = documentWriter::write, dispatcher = ioDispatcher,
            pickerOwnerNonce = java.util.UUID.randomUUID().toString()) else null
    val profiles = ports.playerProfiles?.let { ProfilesController(scope, it, ioDispatcher,
        commanderSearch = SearchController(scope, ports.catalogLookup, ioDispatcher, onStaleCursor = status::refresh)) }
    val printings = ports.catalogPrintings?.let { PrintingSelectionController(scope, it, ioDispatcher, ports.cardLinks) { card ->
        val advanced = advancedQuery?.state?.value
        (if (advanced?.visible == true) advanced.selected else search.state.value.selected) == card &&
            status.state.value.status?.activeGeneration()?.id == card.provenance.generation
    } }
    val rulings = ports.rulings?.let { RulingsController(scope, it, ioDispatcher) }
    val downloads = ports.englishCatalog?.let { DownloadsController(scope, it, ports.settings, ports.catalogStatus, ioDispatcher,
        { status.refresh(); rulings?.refresh() }, ports.rulingsDownloads, ports.rulings) }

    init {
        if (deckLibrary != null && collection != null) scope.launch {
            deckLibrary.state.mapNotNull { it.selected?.let { deck -> deck.id to deck.revision } }
                .distinctUntilChanged().collect { (id, revision) ->
                    collection.onDeckChanged(id, revision)
                    deckMarkOwned?.onDeckChanged(id, revision)
                }
        }
        if (collection != null && deckMarkOwned != null) scope.launch {
            collection.state.map { maxOf(it.inventory?.revision?.value ?: 0L, it.savedRevision?.value ?: 0L) }
                .distinctUntilChanged().collect { deckMarkOwned.onInventoryChanged(com.architectpsg.commandlibrary.core.model.Revision(it)) }
        }
        scope.launch {
            status.state
                .mapNotNull { it.status }
                .map { it.activeGeneration()?.id }
                .distinctUntilChanged()
                .collect { generation ->
                    search.onActiveGeneration(generation)
                    profiles?.commanderSearch?.onActiveGeneration(generation)
                    deckEditorSearch?.onActiveGeneration(generation)
                    deckDraftEditor?.onActiveGeneration(generation)
                    printings?.onActiveGeneration(generation)
                    collection?.onActiveGeneration(generation)
                    deckMarkOwned?.onActiveGeneration(generation)
                }
        }
        scope.launch {
            val advanced = advancedQuery
            if (advanced == null) search.state.map { it.selected }.distinctUntilChanged().collect { printings?.selectCard(it) }
            else combine(search.state, advanced.state) { literal, typed -> if (typed.visible) typed.selected else literal.selected }
                .distinctUntilChanged().collect { printings?.selectCard(it) }
        }
        status.refresh()
        settings.load()
        rules?.refresh()
        profiles?.refresh()
        deckLibrary?.refresh()
    }
}

/** Current status is read synchronously, independently of the asynchronous search collector. */
internal fun currentDeckCard(card: com.architectpsg.commandlibrary.core.model.CardRecord, search: SearchUiState,
    currentGeneration: com.architectpsg.commandlibrary.core.model.GenerationId?): Boolean =
    !search.stale && search.activeGenerationKnown && card.provenance.generation != null &&
        search.generation == card.provenance.generation && search.activeGeneration == card.provenance.generation &&
        currentGeneration == card.provenance.generation && card in search.results
