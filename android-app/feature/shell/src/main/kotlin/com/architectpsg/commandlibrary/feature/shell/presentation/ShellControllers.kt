package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.CatalogImportLimits
import com.architectpsg.commandlibrary.feature.shell.ShellPorts
import com.architectpsg.commandlibrary.feature.shell.document.DocumentSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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
) {
    val importLimits: CatalogImportLimits = ports.importLimits
    val status = CatalogStatusController(scope, ports.catalogStatus, ioDispatcher)
    val search = SearchController(scope, ports.catalogLookup, ioDispatcher, onStaleCursor = status::refresh)
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
        RulesController(scope, ports.rulesImport, ports.rulesLookup, documents, ioDispatcher)
    } else null

    val profiles = ports.playerProfiles?.let { ProfilesController(scope, it, ioDispatcher,
        commanderSearch = SearchController(scope, ports.catalogLookup, ioDispatcher, onStaleCursor = status::refresh)) }
    val downloads = ports.englishCatalog?.let { DownloadsController(scope, it, ports.settings, ports.catalogStatus, ioDispatcher, status::refresh) }

    init {
        scope.launch {
            status.state
                .mapNotNull { it.status }
                .map { it.activeGeneration()?.id }
                .distinctUntilChanged()
                .collect { generation ->
                    search.onActiveGeneration(generation)
                    profiles?.commanderSearch?.onActiveGeneration(generation)
                }
        }
        status.refresh()
        settings.load()
        rules?.refresh()
        profiles?.refresh()
    }
}
