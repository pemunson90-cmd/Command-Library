package com.architectpsg.commandlibrary.feature.shell.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.architectpsg.commandlibrary.feature.shell.ui.collection.CollectionScreen
import com.architectpsg.commandlibrary.core.model.DeckId
import com.architectpsg.commandlibrary.feature.shell.ui.decks.DeckVersionsScreen
import com.architectpsg.commandlibrary.feature.shell.ui.decks.DeckLibraryScreen
import com.architectpsg.commandlibrary.feature.shell.ui.profiles.ProfilesScreen
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.document.DocumentRef
import com.architectpsg.commandlibrary.feature.shell.presentation.ShellControllers
import com.architectpsg.commandlibrary.feature.shell.presentation.activeGeneration

/** Only destinations whose screens work in this version; unbuilt features are absent. */
internal enum class ShellDestination(@param:StringRes val label: Int, @param:DrawableRes val icon: Int, val tag: String) {
    CARDS(R.string.shell_nav_cards, R.drawable.shell_ic_cards, "shell-nav-cards"),
    DECKS(R.string.decks_title, R.drawable.shell_ic_decks, "shell-nav-decks"),
    COLLECTION(R.string.collection_title, R.drawable.shell_ic_cards, "shell-nav-collection"),
    CATALOG(R.string.shell_nav_catalog, R.drawable.shell_ic_catalog, "shell-nav-catalog"),
    RULES(R.string.rules_title, R.drawable.shell_ic_catalog, "shell-nav-rules"),
    PROFILES(R.string.profiles_nav, R.drawable.shell_ic_profiles, "shell-nav-profiles"),
    SETTINGS(R.string.shell_nav_settings, R.drawable.shell_ic_settings, "shell-nav-settings"),
}

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
            // Fence any in-flight browser callback in the navigation event, before the next frame.
            controllers.printings?.setVisible(target == ShellDestination.CARDS)
            destination = target
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
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
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
                        onOpenVersions = { id -> controllers.deckVersions?.let { it.openDeck(id); versionsOpen = true } },
                        onCloseVersions = { versionsOpen = false },
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
            if (versionsOpen && versions != null) Box(modifier) { DeckVersionsScreen(versions, widthClass, onCloseVersions) }
            else DeckLibraryScreen(library, widthClass, modifier, controllers.deckEditorSearch,
                onOpenVersions = if (versions != null) onOpenVersions else null)
        }
        ShellDestination.COLLECTION -> controllers.collection?.let {
            Box(modifier) { CollectionScreen(it, widthClass, onBrowseCards = { onNavigate(ShellDestination.CARDS) }) }
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
