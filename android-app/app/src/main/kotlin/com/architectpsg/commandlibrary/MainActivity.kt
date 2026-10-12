package com.architectpsg.commandlibrary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.architectpsg.commandlibrary.core.model.Outcome
import com.architectpsg.commandlibrary.di.AppGraph
import com.architectpsg.commandlibrary.feature.shell.CommandLibraryShell
import com.architectpsg.commandlibrary.feature.shell.ShellPorts

/** Hosts the shell. The activity owns edge-to-edge system bars; the shell owns its own insets and theme. */
public class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as CommandLibraryApplication).graph
        setContent { AppContent(graph) }
    }
}

@Composable
internal fun AppContent(graph: AppGraph) {
    var attempt by rememberSaveable { mutableIntStateOf(0) }
    val ports by produceState<Outcome<ShellPorts>?>(initialValue = null, attempt) {
        value = null
        value = graph.ports()
    }
    when (val current = ports) {
        is Outcome.Success -> CommandLibraryShell(current.value)
        null -> StartupFrame { OpeningCatalog() }
        else -> StartupFrame { CatalogOpenFailed(onRetry = { attempt++ }) }
    }
}

@Composable
private fun StartupFrame(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { content() }
        }
    }
}

@Composable
private fun OpeningCatalog() {
    val description = stringResource(R.string.startup_opening)
    CircularProgressIndicator(Modifier.semantics { contentDescription = description })
    Spacer(Modifier.height(16.dp))
    Text(description, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun CatalogOpenFailed(onRetry: () -> Unit) {
    Text(
        stringResource(R.string.startup_failed_title),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.startup_failed_body), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(24.dp))
    Button(onClick = onRetry) { Text(stringResource(R.string.startup_retry)) }
}
