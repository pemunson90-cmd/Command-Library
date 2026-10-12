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
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.document.DocumentRef
import com.architectpsg.commandlibrary.feature.shell.presentation.ShellControllers
import com.architectpsg.commandlibrary.feature.shell.presentation.activeGeneration

/** Only destinations whose screens work in this version; unbuilt features are absent. */
internal enum class ShellDestination(@param:StringRes val label: Int, @param:DrawableRes val icon: Int, val tag: String) {
    CARDS(R.string.shell_nav_cards, R.drawable.shell_ic_cards, "shell-nav-cards"),
    CATALOG(R.string.shell_nav_catalog, R.drawable.shell_ic_catalog, "shell-nav-catalog"),
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
                        destination = destination,
                        controllers = controllers,
                        widthClass = widthClass,
                        pickerUnavailable = pickerUnavailable,
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
                                ShellDestination.entries.forEach { item ->
                                    NavigationBarItem(
                                        selected = destination == item,
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
                            ShellDestination.entries.forEach { item ->
                                NavigationRailItem(
                                    selected = destination == item,
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
        )
        ShellDestination.CATALOG -> {
            val importState by controllers.catalogImport.state.collectAsState()
            CatalogScreen(
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
            )
        }
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
