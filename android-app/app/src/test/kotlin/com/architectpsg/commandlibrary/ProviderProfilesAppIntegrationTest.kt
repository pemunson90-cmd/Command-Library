package com.architectpsg.commandlibrary

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.R as ShellR
import com.architectpsg.commandlibrary.feature.shell.ShellPorts
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Actual Settings/MainActivity/AppGraph/Room journey. Synthetic key text is never submitted. */
@RunWith(AndroidJUnit4::class)
@Config(shadows = [RealHostDirectoryLinux::class, RealHostBlockGuardOs::class])
class ProviderProfilesAppIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val token = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private fun <T> ok(value: Outcome<T>): T {
        assertTrue("Expected successful bounded application operation", value is Outcome.Success)
        return (value as Outcome.Success).value
    }
    private fun text(id: Int) = compose.activity.getString(id)
    private fun button(id: Int) = compose.onNodeWithText(text(id)).performScrollTo()
    private fun field(id: Int) = compose.onNode(hasSetTextAction() and hasText(text(id)))
    private fun edit(id: Int, value: String) { field(id).performScrollTo().performTextReplacement(value); compose.waitForIdle() }
    private fun await(condition: () -> Boolean) { compose.waitUntil(15_000, condition) }
    private fun profiles(ports: ShellPorts) = runBlocking { ok(checkNotNull(ports.providerProfiles).list(PageRequest(100), token)).items }
    private fun editable(id: Int) = field(id).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    @Test fun actualSettingsSaveEditLocalProfileAndClearKeyAfterRevisionBackgroundAndRecreation() {
        val app = ApplicationProvider.getApplicationContext<CommandLibraryApplication>()
        val ports = runBlocking { ok(app.graph.ports()) }
        val repository = checkNotNull(ports.providerProfiles)
        val before = runBlocking { Triple(ok(ports.settings.read(token)), ok(checkNotNull(ports.localInventory).snapshot(token)), ok(ports.catalogStatus.status(token))) }
        fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        await { exists("shell-nav-settings") || exists("shell-nav-more") }
        if (!exists("shell-nav-settings")) compose.onNodeWithTag("shell-nav-more").performClick()
        compose.onNodeWithTag("shell-nav-settings").performClick()
        await { compose.onAllNodesWithText(text(ShellR.string.provider_profiles_manage)).fetchSemanticsNodes().isNotEmpty() }
        button(ShellR.string.provider_profiles_manage).performClick()
        button(ShellR.string.provider_profiles_new).performClick()
        edit(ShellR.string.provider_profiles_name, "Synthetic provider Ω")
        edit(ShellR.string.provider_profiles_base, "https://EXAMPLE.test:443/v1/")
        button(ShellR.string.provider_profiles_local).performClick()
        edit(ShellR.string.provider_profiles_model, "synthetic-manual-model")
        button(ShellR.string.provider_profiles_save).performClick()
        await { profiles(ports).any { it.profile.name == "Synthetic provider Ω" } }
        val created = profiles(ports).single { it.profile.name == "Synthetic provider Ω" }
        assertEquals(Revision(1), created.revision)
        assertEquals(ProviderDialect.LOCAL, created.profile.dialect)
        assertEquals(ResourceUri("https://example.test/v1"), created.profile.baseUri)
        assertNull(created.profile.secretRef)
        assertEquals(Origin("https", "example.test", 443), checkNotNull(created.profile.endpoint).origin)
        await { runBlocking { ok(ports.settings.read(token)).providerProfileIds.contains(created.profile.id) } }
        await { compose.onAllNodesWithText(text(ShellR.string.provider_profiles_saved)).fetchSemanticsNodes().isNotEmpty() }

        // A same-profile save changes the key field's remembered state. ON_STOP must clear THAT state.
        edit(ShellR.string.provider_profiles_api, "synthetic-version-2")
        button(ShellR.string.provider_profiles_save).performClick()
        await { runBlocking { ok(repository.load(created.profile.id, token)).revision == Revision(2) } }
        await { compose.onAllNodesWithText(text(ShellR.string.provider_profiles_saved)).fetchSemanticsNodes().isNotEmpty() }
        edit(ShellR.string.provider_profiles_key, "SYNTHETIC-KEY-NOT-SUBMITTED")
        assertTrue(editable(ShellR.string.provider_profiles_key).isNotEmpty())
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        assertEquals("", editable(ShellR.string.provider_profiles_key))
        edit(ShellR.string.provider_profiles_key, "SYNTHETIC-SECOND-UNSUBMITTED-KEY")
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertEquals("", editable(ShellR.string.provider_profiles_key))
        assertEquals("synthetic-version-2", editable(ShellR.string.provider_profiles_api))
        val stored = runBlocking { ok(repository.load(created.profile.id, token)) }
        assertEquals(Knowledge.Known("synthetic-version-2"), stored.profile.apiVersion)
        assertNull(stored.profile.secretRef)

        button(ShellR.string.provider_profiles_delete).performClick()
        compose.onNodeWithText(text(ShellR.string.provider_profiles_confirm)).performClick()
        await { profiles(ports).none { it.profile.id == created.profile.id } }
        await { runBlocking { !ok(ports.settings.read(token)).providerProfileIds.contains(created.profile.id) } }
        val after = runBlocking { Triple(ok(ports.settings.read(token)), ok(checkNotNull(ports.localInventory).snapshot(token)), ok(ports.catalogStatus.status(token))) }
        assertEquals(before.first.providerProfileIds, after.first.providerProfileIds)
        assertEquals(before.first.theme, after.first.theme)
        assertEquals(before.first.dataLanguage, after.first.dataLanguage)
        assertEquals(before.first.cache, after.first.cache)
        assertEquals(before.first.consents, after.first.consents)
        assertEquals(before.second, after.second)
        assertEquals(before.third, after.third)
    }
}
