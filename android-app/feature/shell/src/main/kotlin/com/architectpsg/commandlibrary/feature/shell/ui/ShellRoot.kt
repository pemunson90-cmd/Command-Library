package com.architectpsg.commandlibrary.feature.shell.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.architectpsg.commandlibrary.feature.shell.ui.collection.CollectionScreen
import com.architectpsg.commandlibrary.feature.shell.ui.collection.DeckMarkOwnedSurface
import com.architectpsg.commandlibrary.core.model.DeckId
import com.architectpsg.commandlibrary.core.model.DeckSnapshot
import com.architectpsg.commandlibrary.feature.shell.presentation.PlainDeckExportPreview
import com.architectpsg.commandlibrary.feature.shell.presentation.PlainDeckExportWritePhase
import com.architectpsg.commandlibrary.feature.shell.presentation.Problem
import com.architectpsg.commandlibrary.feature.shell.ui.decks.PlainDeckExportSurface
import com.architectpsg.commandlibrary.feature.shell.ui.decks.DeckVersionsScreen
import com.architectpsg.commandlibrary.feature.shell.ui.decks.DeckLibraryScreen
import com.architectpsg.commandlibrary.feature.shell.ui.decks.PlainDeckImportSurface
import com.architectpsg.commandlibrary.feature.shell.ui.decks.DraftExitDialog
import com.architectpsg.commandlibrary.feature.shell.ui.decks.DraftRecoveryBanner
import com.architectpsg.commandlibrary.feature.shell.presentation.DeferredDeckAction
import com.architectpsg.commandlibrary.feature.shell.presentation.DraftExitPhase
import com.architectpsg.commandlibrary.feature.shell.ui.profiles.ProfilesScreen
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.ShellDestination
import com.architectpsg.commandlibrary.feature.shell.document.DocumentRef
import com.architectpsg.commandlibrary.feature.shell.presentation.ShellControllers
import com.architectpsg.commandlibrary.feature.shell.presentation.activeGeneration


internal object ShellTags {
    const val NAVIGATION_BAR = "shell-navigation-bar"
    const val NAVIGATION_RAIL = "shell-navigation-rail"
    const val RESULTS_PANE = "shell-results-pane"
    const val DETAIL_PANE = "shell-detail-pane"
    const val IMPORT_PROGRESS = "shell-import-progress"
}

/** Document types offered by the system picker; the importer still validates the content. */
internal val IMPORT_MIME_TYPES: Array<String> = arrayOf("application/json", "text/json", "text/plain", "application/octet-stream")

@Composable
internal fun ShellRoot(controllers: ShellControllers, modifier: Modifier = Modifier) {
    val settingsState by controllers.settings.state.collectAsState()
    ShellTheme(settingsState.theme) {
        var destination by rememberSaveable { mutableStateOf(ShellDestination.CARDS) }
        var pickerUnavailable by rememberSaveable { mutableStateOf(false) }
        // Keep this deck subroute while another destination is shown: the controller retains
        // unsaved names and a frozen pending mutation rather than silently dropping either.
        var versionsOpen by rememberSaveable { mutableStateOf(false) }
        val draft = controllers.deckLibrary?.draftEditor
        val draftState = draft?.state?.collectAsState()?.value
        val destinations = ShellDestination.entries.filter {
            when (it) {
                ShellDestination.DECKS -> controllers.deckLibrary != null
                ShellDestination.COLLECTION -> controllers.collection != null
                ShellDestination.RULES -> controllers.rules != null
                ShellDestination.PROFILES -> controllers.profiles != null
                else -> true
            }
        }
        val visibleDestination = destination.takeIf { it in destinations } ?: ShellDestination.CARDS
        BackHandler(visibleDestination == ShellDestination.DECKS && versionsOpen && controllers.deckVersions != null) {
            if (controllers.deckVersions?.requestClose() == true) versionsOpen = false
        }
        val navigate: (ShellDestination) -> Unit = { target ->
            if (draft != null && draft.state.value.session != null && target != visibleDestination) {
                draft.requestExit(DeferredDeckAction.Navigate(target))
            } else {
                // Fence any in-flight browser callback in the accepted navigation event.
                controllers.printings?.setVisible(target == ShellDestination.CARDS)
                destination = target
            }
        }
        LaunchedEffect(draft, draftState?.exit) {
            val exit = draftState?.exit?.takeIf { it.phase == DraftExitPhase.READY } ?: return@LaunchedEffect
            if (exit.action is DeferredDeckAction.Navigate || exit.action is DeferredDeckAction.OpenVersions) {
                when (val action = draft?.takeReadyExit(exit.ticket)) {
                    is DeferredDeckAction.Navigate -> {
                        controllers.printings?.setVisible(action.destination == ShellDestination.CARDS)
                        destination = action.destination
                    }
                    is DeferredDeckAction.OpenVersions -> controllers.deckVersions?.let {
                        it.openDeck(action.deckId); versionsOpen = true
                    }
                    else -> Unit
                }
            }
        }
        LaunchedEffect(visibleDestination, controllers) {
            controllers.printings?.setVisible(visibleDestination == ShellDestination.CARDS)
        }
        var rulesPickerUnavailable by rememberSaveable { mutableStateOf(false) }
        val rulesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) controllers.rules?.start(DocumentRef(uri.toString()))
        }
        val pickRules = {
            rulesPickerUnavailable = try {
                rulesPicker.launch(arrayOf("text/plain"))
                false
            } catch (_: ActivityNotFoundException) {
                true
            }
        }
        // Registered at the root so a pending pick survives layout changes and activity recreation.
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) controllers.catalogImport.start(DocumentRef(uri.toString()))
        }
        val pickFile = {
            pickerUnavailable = try {
                picker.launch(IMPORT_MIME_TYPES)
                false
            } catch (_: ActivityNotFoundException) {
                true
            }
        }
        var pendingPlainPick by rememberSaveable { mutableStateOf<Long?>(null) }
        var plainPickerUnavailable by rememberSaveable { mutableStateOf(false) }
        val context = LocalContext.current
        fun libraryAllowsPlainPick(): Boolean {
            val library = controllers.deckLibrary?.state?.value ?: return false
            val editor = controllers.deckDraftEditor
            return !library.pending && !library.mutationBusy && library.form == null &&
                editor?.state?.value?.visible != true && editor?.hasMutationBarrier != true
        }
        // One outstanding request avoids aliasing identical OpenDocument intents. Its frame ticket
        // survives activity recreation; a late result never replaces a different form or request.
        val plainPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val ticket = pendingPlainPick
            pendingPlainPick = null
            val importer = controllers.plainDeckImport
            val current = importer?.state?.value
            if (uri != null && ticket != null && importer != null && current != null &&
                current.formTicket == ticket && !current.submitting && !current.uncertain &&
                !current.awaitingFresh && libraryAllowsPlainPick()) {
                val input = controllers.plainDeckInputs.document(DocumentRef(uri.toString()),
                    com.architectpsg.commandlibrary.core.model.EntityId(java.util.UUID.randomUUID().toString()),
                    context.getString(R.string.plain_import_selected_file))
                importer.parse(input)
            }
        }
        val choosePlainFile: () -> Unit = {
            val importer = controllers.plainDeckImport
            val current = importer?.state?.value
            if (importer != null && current != null && pendingPlainPick == null && libraryAllowsPlainPick() &&
                !current.submitting && !current.uncertain && !current.awaitingFresh) {
                pendingPlainPick = current.formTicket
                try {
                    plainPicker.launch(arrayOf("text/plain"))
                    plainPickerUnavailable = false
                } catch (_: ActivityNotFoundException) {
                    pendingPlainPick = null; plainPickerUnavailable = true
                } catch (_: SecurityException) {
                    pendingPlainPick = null; plainPickerUnavailable = true
                }
            }
        }
        val importPlainEntry: (() -> Unit)? = controllers.plainDeckImport?.let { importer -> {
            if (pendingPlainPick == null && libraryAllowsPlainPick()) {
                if (importer.state.value.inputName != null) importer.resume() else choosePlainFile()
            }
        } }
        var pendingPlainExport by rememberSaveable { mutableStateOf<String?>(null) }
        // Consume every saved ticket, including cancellation and revoked hidden frames. The
        // controller alone decides whether the frozen preview still authorizes this result.
        val plainExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            val ticket = pendingPlainExport
            pendingPlainExport = null
            if (ticket != null) controllers.plainDeckExport?.pickerResult(ticket, uri?.let { DocumentRef(it.toString()) })
        }
        val choosePlainExport: (PlainDeckExportPreview) -> Unit = { expected ->
            val exporter = controllers.plainDeckExport
            if (pendingPlainExport == null && exporter != null) {
                val ticket = exporter.requestDestination(expected)
                if (ticket != null) {
                    pendingPlainExport = ticket
                    try {
                        plainExportPicker.launch(context.getString(R.string.plain_export_filename))
                    } catch (_: ActivityNotFoundException) {
                        pendingPlainExport = null
                        exporter.pickerFailed(ticket, Problem.UNSUPPORTED)
                    } catch (_: SecurityException) {
                        pendingPlainExport = null
                        exporter.pickerFailed(ticket, Problem.PERMISSION)
                    }
                }
            }
        }
        val exportPlainEntry: ((DeckSnapshot) -> Unit)? = controllers.plainDeckExport?.let { exporter -> { selected ->
            val library = controllers.deckLibrary?.state?.value
            if (pendingPlainExport == null && libraryAllowsPlainPick() && library != null &&
                !library.loadingDetail && library.selected == selected) {
                val current = exporter.state.value
                if (current.preview != null || current.preparing || current.pendingPicker != null ||
                    current.writePhase != PlainDeckExportWritePhase.IDLE) exporter.resume()
                else exporter.prepare(selected.id, selected.revision)
            }
        } }
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
                controllers.plainDeckImport?.let { PlainDeckImportSurface(it, choosePlainFile, pendingPlainPick != null) }
                controllers.plainDeckExport?.let { PlainDeckExportSurface(it, choosePlainExport, pendingPlainExport != null) }
                controllers.deckMarkOwned?.let { DeckMarkOwnedSurface(it, controllers.inventoryMutationGate.busy) }
                draft?.let { editor -> DraftRecoveryBanner(editor) {
                    val session = editor.state.value.session
                    if (session != null && (!versionsOpen || controllers.deckVersions?.requestClose() == true)) {
                        versionsOpen = false
                        controllers.printings?.setVisible(false)
                        destination = ShellDestination.DECKS
                        editor.open(session.deckId, session.target)
                    }
                } }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val widthClass = WidthClass.of(maxWidth)
                val content: @Composable (Modifier) -> Unit = { contentModifier ->
                    DestinationContent(
                        destination = visibleDestination,
                        controllers = controllers,
                        widthClass = widthClass,
                        pickerUnavailable = pickerUnavailable,
                        rulesPickerUnavailable = rulesPickerUnavailable,
                        onPickRules = pickRules,
                        onPickFile = pickFile,
                        onNavigate = navigate,
                        versionsOpen = versionsOpen,
                        onOpenVersions = { id ->
                            if (draft != null && draft.state.value.session != null) draft.requestExit(DeferredDeckAction.OpenVersions(id))
                            else controllers.deckVersions?.let { it.openDeck(id); versionsOpen = true }
                        },
                        onCloseVersions = { versionsOpen = false },
                        onExportPlain = exportPlainEntry,
                        exportPlainEnabled = pendingPlainExport == null && draftState?.visible != true && draft?.hasMutationBarrier != true,
                        onImportPlain = importPlainEntry,
                        importPlainEnabled = pendingPlainPick == null && draftState?.visible != true && draft?.hasMutationBarrier != true,
                        modifier = contentModifier,
                    )
                }
                if (widthClass == WidthClass.COMPACT) {
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        bottomBar = {
                            val largeText = LocalDensity.current.fontScale >= 1.5f
                            val overflow = controllers.deckLibrary != null && (destinations.size > 5 || (largeText && destinations.size > 3))
                            val primary = if (!overflow) destinations else
                                listOf(ShellDestination.CARDS, ShellDestination.DECKS, ShellDestination.COLLECTION, ShellDestination.RULES, ShellDestination.PROFILES)
                                    .filter { it in destinations }.take(if (largeText) 2 else 4)
                            val secondary = destinations.filter { it !in primary }
                            var menuOpen by remember { mutableStateOf(false) }
                            NavigationBar(Modifier.heightIn(min = if (largeText && overflow) 112.dp else 80.dp).testTag(ShellTags.NAVIGATION_BAR)) {
                                val barScope = this
                                primary.forEach { item ->
                                    NavigationBarItem(selected = visibleDestination == item, onClick = { navigate(item) },
                                        icon = { Icon(painterResource(item.icon), contentDescription = null) },
                                        label = { Text(stringResource(item.label)) }, modifier = Modifier.testTag(item.tag))
                                }
                                if (secondary.isNotEmpty()) Box(Modifier.weight(1f)) {
                                    barScope.NavigationBarItem(selected = visibleDestination in secondary, onClick = { menuOpen = true },
                                        icon = { Icon(painterResource(R.drawable.shell_ic_more), contentDescription = null) },
                                        label = { Text(stringResource(R.string.shell_nav_more)) },
                                        modifier = Modifier.fillMaxWidth().testTag("shell-nav-more"))
                                    DropdownMenu(menuOpen, { menuOpen = false }) {
                                        secondary.forEach { item -> DropdownMenuItem(
                                            text = { Text(stringResource(item.label)) },
                                            leadingIcon = { Icon(painterResource(item.icon), contentDescription = null) },
                                            onClick = { menuOpen = false; navigate(item) },
                                            modifier = Modifier.heightIn(min = 48.dp).testTag(item.tag).semantics { selected = visibleDestination == item },
                                        ) }
                                    }
                                }
                            }
                        },
                    ) { padding ->
                        content(Modifier.padding(padding).consumeWindowInsets(padding))
                    }
                } else {
                    Row(Modifier.fillMaxSize()) {
                        NavigationRail(
                            modifier = Modifier.fillMaxHeight().testTag(ShellTags.NAVIGATION_RAIL),
                            windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
                        ) {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                            destinations.forEach { item ->
                                NavigationRailItem(
                                    selected = visibleDestination == item,
                                    onClick = { navigate(item) },
                                    icon = { Icon(painterResource(item.icon), contentDescription = null) },
                                    label = { Text(stringResource(item.label)) },
                                    modifier = Modifier.testTag(item.tag),
                                )
                            }
                            }
                        }
                        content(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End)),
                        )
                    }
                }
                }
            }
        }
        draft?.let { DraftExitDialog(it) }
        if (plainPickerUnavailable) AlertDialog(onDismissRequest = { plainPickerUnavailable = false },
            text = { Text(stringResource(R.string.plain_import_pick_unavailable)) },
            confirmButton = { TextButton({ plainPickerUnavailable = false }) { Text(stringResource(android.R.string.ok)) } })
    }
}

@Composable
private fun DestinationContent(
    destination: ShellDestination,
    controllers: ShellControllers,
    widthClass: WidthClass,
    pickerUnavailable: Boolean,
    onPickFile: () -> Unit,
    rulesPickerUnavailable: Boolean,
    onPickRules: () -> Unit,
    onNavigate: (ShellDestination) -> Unit,
    versionsOpen: Boolean,
    onOpenVersions: (DeckId) -> Unit,
    onCloseVersions: () -> Unit,
    modifier: Modifier,
    onImportPlain: (() -> Unit)?,
    importPlainEnabled: Boolean,
    onExportPlain: ((DeckSnapshot) -> Unit)?,
    exportPlainEnabled: Boolean,
) {
    val statusState by controllers.status.state.collectAsState()
    when (destination) {
        ShellDestination.CARDS -> CardsScreen(
            controller = controllers.search,
            statusState = statusState,
            widthClass = widthClass,
            onOpenCatalog = { onNavigate(ShellDestination.CATALOG) },
            modifier = modifier,
            rulings = controllers.rulings,
            printings = controllers.printings,
            onAddPrinting = controllers.collection?.let { collection -> { printing ->
                collection.beginAdd(printing); onNavigate(ShellDestination.COLLECTION)
            } },
            onManageRulings = if(controllers.downloads?.rulingsEnabled == true) ({ onNavigate(ShellDestination.CATALOG) }) else null,
            onLookupKeyword = controllers.rules?.let { rules -> { keyword: String ->
                rules.lookupKeyword(keyword); onNavigate(ShellDestination.RULES)
            } },
        )
        ShellDestination.CATALOG -> {
            var downloadsOpen by rememberSaveable { mutableStateOf(false) }
            val importState by controllers.catalogImport.state.collectAsState()
            val downloads = controllers.downloads
            if (downloadsOpen && downloads != null) DownloadsScreen(downloads, widthClass, onBack = { downloadsOpen = false }, modifier = modifier)
            else CatalogScreen(
                statusState = statusState,
                importState = importState,
                limits = controllers.importLimits,
                widthClass = widthClass,
                pickerUnavailable = pickerUnavailable,
                onPickFile = onPickFile,
                onCancelImport = controllers.catalogImport::cancel,
                onDismissImport = controllers.catalogImport::dismiss,
                onRetryStatus = controllers.status::refresh,
                modifier = modifier,
                onOpenDownloads = if (downloads != null) ({ downloadsOpen = true }) else null,
            )
        }
        ShellDestination.RULES -> controllers.rules?.let { rules ->
            RulesScreen(rules, widthClass, rulesPickerUnavailable, onPickRules, modifier)
        }
        ShellDestination.DECKS -> controllers.deckLibrary?.let { library ->
            val versions = controllers.deckVersions
            if (versionsOpen && versions != null) Box(modifier) { DeckVersionsScreen(versions, widthClass, onCloseVersions, controllers.deckDraftEditor) }
            else DeckLibraryScreen(library, widthClass, modifier, controllers.deckEditorSearch,
                onOpenVersions = if (versions != null) onOpenVersions else null,
                onImportText = onImportPlain, importTextEnabled = importPlainEnabled,
                onExportText = onExportPlain, exportTextEnabled = exportPlainEnabled)
        }
        ShellDestination.COLLECTION -> controllers.collection?.let {
            Box(modifier) { CollectionScreen(it, widthClass, onBrowseCards = { onNavigate(ShellDestination.CARDS) },
                onMarkOwned = controllers.deckMarkOwned?.let { markOwned -> { id, policy -> markOwned.open(id, policy) } }) }
        }
        ShellDestination.PROFILES -> controllers.profiles?.let { ProfilesScreen(it, widthClass, modifier) }
        ShellDestination.SETTINGS -> {
            val settingsState by controllers.settings.state.collectAsState()
            SettingsScreen(
                state = settingsState,
                catalogNotice = statusState.status?.activeGeneration()?.provenance?.notice,
                widthClass = widthClass,
                onSelectTheme = controllers.settings::selectTheme,
                onRetry = controllers.settings::load,
                onDismissMessage = controllers.settings::dismissMessage,
                modifier = modifier,
            )
        }
    }
}
