package com.architectpsg.commandlibrary.feature.shell

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.architectpsg.commandlibrary.feature.shell.presentation.ShellControllers
import com.architectpsg.commandlibrary.feature.shell.ui.ShellRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** Test-only system picker: records the launch input and answers with [answer]. */
class SyntheticPickerRegistry(private val answer: () -> Uri?) : ActivityResultRegistry() {
    val launchedInputs: MutableList<Any?> = mutableListOf()

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        launchedInputs += input
        val uri = answer()
        dispatchResult(requestCode, if (uri != null) Activity.RESULT_OK else Activity.RESULT_CANCELED, uri?.let { Intent().setData(it) })
    }
}

/**
 * Hosts the real ShellRoot and controllers over synthetic, test-only ports, at a forced width,
 * height and font scale. Controllers run on the main dispatcher like a ViewModel scope.
 */
internal class ShellUiHarness(private val rule: ComposeContentTestRule, private val profiles: com.architectpsg.commandlibrary.core.model.PlayerProfileRepository? = null,
    private val downloads: DownloadsFixture? = null) {
    val catalog = SyntheticCatalog(listOf("Synthetic Existing Card"))
    val settings = SyntheticSettings()
    val documents = MemoryDocuments()
    var pickedUri: String? = PICKED_URI
    val picker = SyntheticPickerRegistry { pickedUri?.let(Uri::parse) }
    private var scope: CoroutineScope? = null
    lateinit var controllers: ShellControllers
        private set

    val context: Context get() = ApplicationProvider.getApplicationContext()

    fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    fun plural(id: Int, count: Int, vararg args: Any): String = context.resources.getQuantityString(id, count, *args)

    fun show(width: Dp = 400.dp, height: Dp = 800.dp, fontScale: Float = 1f) = host(width, height, fontScale) { rule.setContent(it) }

    /** Hosts through [restoration] so a test can emulate saving and restoring UI state, as on a configuration change. */
    fun showWith(restoration: StateRestorationTester, width: Dp = 400.dp, height: Dp = 800.dp) =
        host(width, height, 1f) { restoration.setContent(it) }

    private fun host(width: Dp, height: Dp, fontScale: Float, set: (@Composable () -> Unit) -> Unit) {
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = ownerScope
        // Controllers live outside composition, as they do in the ViewModel.
        controllers = ShellControllers(ownerScope, ShellPorts(catalog, catalog, catalog, downloads?.settings ?: settings,
            playerProfiles = profiles, englishCatalog = downloads?.access), documents, Dispatchers.IO)
        val registryOwner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = picker
        }
        set {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                DeviceConfigurationOverride(
                    DeviceConfigurationOverride.ForcedSize(DpSize(width, height)) then DeviceConfigurationOverride.FontScale(fontScale),
                ) {
                    ShellRoot(controllers)
                }
            }
        }
        rule.waitForIdle()
    }

    fun close() {
        scope?.cancel()
    }

    // Hosted 16 KiB images are materially slower under Compose semantics traversal than the
    // default-page emulator. Keep the assertion unchanged while allowing scheduler variance.
    fun waitForText(text: String, substring: Boolean = false, timeoutMillis: Long = 20_000) {
        rule.waitUntil(timeoutMillis) { rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    }

    /**
     * Waits for controller or port state. Idling first lets main-dispatcher work run under
     * Robolectric's paused looper; on a device it only synchronizes with Compose.
     */
    fun awaitState(timeoutMillis: Long = 10_000, condition: () -> Boolean) {
        rule.waitUntil(timeoutMillis) {
            rule.waitForIdle()
            condition()
        }
    }

    fun waitForTag(tag: String, timeoutMillis: Long = 10_000) {
        rule.waitUntil(timeoutMillis) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    /** App navigation after text entry: a device's first Back may otherwise only dismiss its IME. */
    fun pressAppBackAfterKeyboardDismissal() {
        val espresso = Class.forName("androidx.test.espresso.Espresso")
        espresso.getMethod("closeSoftKeyboard").invoke(null)
        rule.waitForIdle()
        espresso.getMethod("pressBack").invoke(null)
        rule.waitForIdle()
    }

    fun text(text: String, substring: Boolean = false): SemanticsNodeInteraction = rule.onNodeWithText(text, substring = substring)

    fun exists(tag: String): Boolean = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    companion object {
        const val PICKED_URI = "content://synthetic.documents/document/catalog.json"
    }
}

/** Host-only cold SDK initialization budget; never changes instrumentation/device policy. */
internal fun profileHostIdleBudget(seconds: Long) {
    if (android.os.Build.FINGERPRINT.contains("robolectric")) Class.forName("androidx.test.espresso.IdlingPolicies")
        .getMethod("setMasterPolicyTimeout", Long::class.javaPrimitiveType, java.util.concurrent.TimeUnit::class.java)
        .invoke(null, seconds, java.util.concurrent.TimeUnit.SECONDS)
}
