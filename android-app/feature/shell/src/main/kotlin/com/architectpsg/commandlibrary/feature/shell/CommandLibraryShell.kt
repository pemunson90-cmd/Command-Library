package com.architectpsg.commandlibrary.feature.shell

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.architectpsg.commandlibrary.feature.shell.ui.ShellRoot

/**
 * The Command Library user interface: local catalog status and file import, offline card name
 * search with details, and settings. Layout adapts to the width the composable is given.
 *
 * State lives in a ViewModel scoped to the nearest ViewModelStoreOwner (normally the hosting
 * activity), so it survives configuration changes; clearing that owner cancels a running import.
 * After process recreation the shell reads committed catalog status and settings from [ports]
 * rather than restoring any previous result. [ports] should be process-wide instances; the
 * ViewModel keeps the ones it was created with.
 *
 * File import uses the Storage Access Framework picker, which the host activity must be able to
 * launch (any ComponentActivity can). The shell starts no network transfer of its own.
 */
@Composable
public fun CommandLibraryShell(ports: ShellPorts, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resolver = context.applicationContext.contentResolver
    val viewModel: ShellViewModel = viewModel(factory = ShellViewModel.factory(ports, resolver))
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, viewModel, context) {
        val observer = draftBackgroundObserver(context) {
            viewModel.controllers.deckLibrary?.draftEditor?.onBackground()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    ShellRoot(viewModel.controllers, modifier)
}

/** A configuration stop must not promote text or create another undo group. */
internal fun draftBackgroundObserver(context: Context, onBackground: () -> Unit): LifecycleEventObserver =
    LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP && !context.draftHostChangingConfigurations()) onBackground()
    }

private fun Context.draftHostChangingConfigurations(): Boolean {
    var candidate=this
    while (candidate is ContextWrapper) {
        if (candidate is Activity) return candidate.isChangingConfigurations
        val base=candidate.baseContext
        if (base===candidate) break
        candidate=base
    }
    return (candidate as? Activity)?.isChangingConfigurations == true
}
