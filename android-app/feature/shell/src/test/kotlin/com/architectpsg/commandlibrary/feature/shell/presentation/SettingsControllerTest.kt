package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.FailureCode
import com.architectpsg.commandlibrary.core.model.Revision
import com.architectpsg.commandlibrary.core.model.SettingChange
import com.architectpsg.commandlibrary.core.model.ThemePreference
import com.architectpsg.commandlibrary.feature.shell.SyntheticSettings
import com.architectpsg.commandlibrary.feature.shell.failure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsControllerTest {
    private val settings = SyntheticSettings()

    private fun TestScope.loaded(): SettingsController {
        val controller = SettingsController(ownerScope(), settings)
        controller.load()
        advanceUntilIdle()
        return controller
    }

    @Test
    fun loadsCommittedSettingsAndDefaultsToSystemTheme() = runTest {
        val controller = SettingsController(ownerScope(), settings)
        assertEquals(ThemePreference.SYSTEM, controller.state.value.theme)
        assertTrue(controller.state.value.loading)
        controller.load()
        advanceUntilIdle()
        assertEquals(Revision(0), controller.state.value.snapshot!!.revision)
        assertEquals(ThemePreference.SYSTEM, controller.state.value.theme)
        assertTrue(!controller.state.value.loading)
    }

    @Test
    fun selectingAThemePersistsItAgainstTheReadRevision() = runTest {
        val controller = loaded()
        settings.updateGate = CompletableDeferred()
        controller.selectTheme(ThemePreference.DARK)
        runCurrent()
        assertEquals(ThemePreference.DARK, controller.state.value.pendingTheme)
        assertEquals("applied theme waits for the commit", ThemePreference.SYSTEM, controller.state.value.theme)

        settings.updateGate!!.complete(Unit)
        advanceUntilIdle()
        val (patch, expected) = settings.updates.single()
        assertEquals(Revision(0), expected)
        assertEquals(listOf(SettingChange.Theme(ThemePreference.DARK)), patch.changes.toList())
        assertEquals(ThemePreference.DARK, controller.state.value.theme)
        assertEquals(Revision(1), controller.state.value.snapshot!!.revision)
        assertNull(controller.state.value.pendingTheme)
        assertEquals(ThemePreference.DARK, settings.stored.theme)
    }

    @Test
    fun staleRevisionReloadsLatestAndDoesNotOverwrite() = runTest {
        val controller = loaded()
        settings.changeElsewhere(ThemePreference.LIGHT)

        controller.selectTheme(ThemePreference.DARK)
        advanceUntilIdle()

        val state = controller.state.value
        assertEquals(SettingsNotice.CHANGED_ELSEWHERE, state.notice)
        assertEquals("latest committed value shown", ThemePreference.LIGHT, state.theme)
        assertEquals(Revision(1), state.snapshot!!.revision)
        assertEquals("other writer's value kept", ThemePreference.LIGHT, settings.stored.theme)
        assertNull(state.pendingTheme)
        assertNull(state.problem)

        controller.selectTheme(ThemePreference.DARK)
        advanceUntilIdle()
        assertEquals(ThemePreference.DARK, controller.state.value.theme)
        assertEquals(Revision(1), settings.updates.last().second)
    }

    @Test
    fun failedSaveKeepsTheCommittedTheme() = runTest {
        val controller = loaded()
        settings.updateOverride = failure(FailureCode.PERSISTENCE)
        controller.selectTheme(ThemePreference.DARK)
        advanceUntilIdle()
        assertEquals(Problem.PERSISTENCE, controller.state.value.problem)
        assertEquals(ThemePreference.SYSTEM, controller.state.value.theme)
        assertNull(controller.state.value.pendingTheme)
        controller.dismissMessage()
        assertNull(controller.state.value.problem)
    }

    @Test
    fun selectingTheCurrentThemeOrBeforeLoadingDoesNothing() = runTest {
        val unloaded = SettingsController(ownerScope(), settings)
        unloaded.selectTheme(ThemePreference.DARK)
        val controller = loaded()
        controller.selectTheme(ThemePreference.SYSTEM)
        advanceUntilIdle()
        assertTrue(settings.updates.isEmpty())
    }

    @Test
    fun readFailureIsReportedAndRetryable() = runTest {
        settings.readOverride = failure(FailureCode.CORRUPT_DATA)
        val controller = loaded()
        assertEquals(Problem.CORRUPT_DATA, controller.state.value.problem)
        assertNull(controller.state.value.snapshot)
        settings.readOverride = null
        controller.load()
        advanceUntilIdle()
        assertNull(controller.state.value.problem)
        assertEquals(Revision(0), controller.state.value.snapshot!!.revision)
    }
}
