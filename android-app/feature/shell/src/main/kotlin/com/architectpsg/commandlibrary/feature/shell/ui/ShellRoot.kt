package com.architectpsg.commandlibrary.feature.shell.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.architectpsg.commandlibrary.feature.shell.ui.profiles.ProfilesScreen
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.document.DocumentRef
import com.architectpsg.commandlibrary.feature.shell.presentation.ShellControllers
import com.architectpsg.commandlibrary.feature.shell.presentation.activeGeneration

/** Only destinations whose screens work in this version; unbuilt features are absent. */
internal enum class ShellDestination(@param:StringRes val label: Int, @param:DrawableRes val icon: Int, val tag: String) {
    CARDS(R.string.shell_nav_cards, R.drawable.shell_ic_cards, "shell-nav-cards"),
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
        val destinations = ShellDestination.entries.filter {
            when (it) {
                ShellDestination.RULES -> controllers.rules != null
                ShellDestination.PROFILES -> controllers.profiles != null
                else -> true
            }
        }
        val visibleDestination = destination.takeIf { it in destinations } ?: ShellDestination.CARDS
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
                        onNavigate = { destination = it },
                        modifier = contentModifier,
                    )
                }
                if (widthClass == WidthClass.COMPACT) {
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        bottomBar = {
                            NavigationBar(Modifier.testTag(ShellTags.NAVIGATION_BAR)) {
                                destinations.forEach { item ->
                                    NavigationBarItem(
                                        selected = visibleDestination == item,
                                        onClick = { destination = item },
                                        icon = { Icon(painterResource(item.icon), contentDescription = null) },
                                        label = { Text(stringResource(item.label)) },
                                        modifier = Modifier.testTag(item.tag),
                                    )
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
                            destinations.forEach { item ->
                                NavigationRailItem(
                                    selected = visibleDestination == item,
                                    onClick = { destination = item },
                                    icon = { Icon(painterResource(item.icon), contentDescription = null) },
                                    label = { Text(stringResource(item.label)) },
                                    modifier = Modifier.testTag(item.tag),
                                )
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
