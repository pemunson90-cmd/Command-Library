package com.architectpsg.commandlibrary.feature.shell

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.ui.profiles.ProfileCounterTags
import com.architectpsg.commandlibrary.feature.shell.ui.profiles.ProfileTags
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Compose/controller over synthetic profiles; actual AppGraph Room is a separate probe. */
@RunWith(AndroidJUnit4::class)
class ProfilesCounterUiTest {
    @get:Rule val rule = createComposeRule()
    private val profiles = UiProfilesFixture()
    private val shell = ShellUiHarness(rule, profiles)
    @Before fun hostBudget() = idleBudget(300)
    @After fun close() { shell.close(); idleBudget(60) }
    private fun idleBudget(seconds: Long) = profileHostIdleBudget(seconds)

    private fun click(tag: String) { rule.onNodeWithTag(tag).performScrollTo().performClick() }
    private fun change(tag: String, text: String) { rule.onNodeWithTag(tag).performScrollTo().performTextReplacement(text) }
    private fun managerClick(tag: String) {
        rule.onNodeWithTag(ProfileCounterTags.MANAGER).performScrollToNode(hasTestTag(tag))
        rule.onNodeWithTag(tag).performClick()
    }
    private fun open() { rule.onNodeWithTag("shell-nav-profiles").performClick(); shell.waitForText(shell.string(R.string.profiles_title)) }
    private fun saved() = shell.awaitState {
        val state = shell.controllers.profiles!!.state.value
        state.editor == null && !state.busy && !state.loading
    }
    private fun seed(): PlayerProfileSnapshot {
        profiles.seed("ada", "Ada")
        val old = profiles.rows[EntityId("ada")]!!
        val charge = CounterDefinition(EntityId("charge"), "Charge", null, null, 3, CounterLossPolicy.None, true,
            CounterRole.OTHER, DisplayColor(0xff123456), CounterPreset.CHARGE, CounterResetPolicy.ON_TURN_START)
        val standard = CounterDefinition(EntityId("poison"), "Poison", 0, null, 0, CounterLossPolicy.AtOrAbove(10), false,
            CounterRole.OTHER, null, CounterPreset.POISON)
        val row = old.copy(value = old.value.copy(counterDefinitions = SnapshotList.copyOf(old.value.counterDefinitions + listOf(charge, standard))))
        profiles.rows[row.id] = row
        return row
    }

    @Test fun createsCounterDraftWithExplicitPoliciesAndPersistsOnlyWithWholeProfile() {
        shell.show(); open(); click(ProfileTags.NEW); change(ProfileTags.NAME, "Custom player")
        click(ProfileCounterTags.MANAGE); managerClick(ProfileCounterTags.ADD)
        change(ProfileCounterTags.LABEL, "Custom charge"); change(ProfileCounterTags.INITIAL, "-5")
        change(ProfileCounterTags.MINIMUM, "-10"); change(ProfileCounterTags.MAXIMUM, "10")
        click(ProfileCounterTags.LOSS_ABOVE); change(ProfileCounterTags.THRESHOLD, "99")
        click(ProfileCounterTags.USE_COLOR); change(ProfileCounterTags.COLOR, "#8000FF00")
        click(ProfileCounterTags.RESET_TURN); click(ProfileCounterTags.APPLY)
        shell.waitForText("Custom charge"); assertTrue(profiles.rows.isEmpty())
        managerClick(ProfileCounterTags.BACK); click(ProfileTags.SAVE); saved()
        val counter = profiles.rows.values.single().value.counterDefinitions.single { it.role == CounterRole.OTHER }
        assertEquals(-5, counter.initial); assertEquals(-10, counter.minimum); assertEquals(10, counter.maximum)
        assertEquals(CounterLossPolicy.AtOrAbove(99), counter.lossPolicy); assertEquals(DisplayColor(0x8000ff00), counter.color)
        assertEquals(CounterResetPolicy.ON_TURN_START, counter.resetPolicy); assertTrue(counter.custom)
    }

    @Test fun existingCustomEditKeepsIdPresetAndOtherCountersWhileStandardCountersAreReadOnly() {
        val old = seed(); shell.show(width = 900.dp); open(); shell.waitForText("Ada")
        click("profile-edit-ada"); click(ProfileCounterTags.MANAGE)
        rule.onNodeWithTag(ProfileCounterTags.MANAGER).performScrollToNode(hasText("Poison"))
        rule.onNodeWithTag(ProfileCounterTags.edit("poison")).assertDoesNotExist()
        rule.onNodeWithTag(ProfileCounterTags.delete("poison")).assertDoesNotExist()
        managerClick(ProfileCounterTags.edit("charge")); change(ProfileCounterTags.INITIAL, "7"); click(ProfileCounterTags.APPLY)
        assertEquals(old, profiles.rows[old.id]); managerClick(ProfileCounterTags.BACK); click(ProfileTags.SAVE); saved()
        val updated = profiles.rows[old.id]!!
        assertEquals(Revision(1), updated.revision)
        assertEquals(old.value.counterDefinitions.map { if (it.id == EntityId("charge")) it.copy(initial = 7) else it }, updated.value.counterDefinitions.toList())
    }

    @Test fun invalidInputRetainsDraftAndColorHasAnExplicitUnsetChoice() {
        val old = seed(); shell.show(); open(); shell.waitForText("Ada")
        click("profile-edit-ada"); click(ProfileCounterTags.MANAGE); managerClick(ProfileCounterTags.edit("charge"))
        change(ProfileCounterTags.INITIAL, "2147483648"); click(ProfileCounterTags.APPLY)
        shell.text(shell.string(R.string.profile_counter_invalid_initial)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(ProfileCounterTags.INITIAL).assertTextContains("2147483648")
        assertEquals(old, profiles.rows[old.id]); change(ProfileCounterTags.INITIAL, "4")
        change(ProfileCounterTags.COLOR, "#bad"); click(ProfileCounterTags.APPLY)
        shell.text(shell.string(R.string.profile_counter_invalid_color)).performScrollTo().assertIsDisplayed()
        click(ProfileCounterTags.USE_COLOR); click(ProfileCounterTags.APPLY)
        managerClick(ProfileCounterTags.BACK); click(ProfileTags.SAVE); saved()
        assertNull(profiles.rows[old.id]!!.value.counterDefinitions[1].color)
    }

    @Test fun counterDeletionRequiresConfirmationAndStillWaitsForWholeProfileSave() {
        val old = seed(); shell.show(); open(); shell.waitForText("Ada")
        click("profile-edit-ada"); click(ProfileCounterTags.MANAGE); managerClick(ProfileCounterTags.delete("charge"))
        rule.onNodeWithTag(ProfileCounterTags.CANCEL_DELETE).performClick()
        assertEquals(old.value.counterDefinitions, shell.controllers.profiles!!.state.value.editor!!.counterDefinitions)
        managerClick(ProfileCounterTags.delete("charge")); rule.onNodeWithTag(ProfileCounterTags.CONFIRM_DELETE).performClick()
        assertEquals(old, profiles.rows[old.id]); managerClick(ProfileCounterTags.BACK); click(ProfileTags.SAVE); saved()
        assertEquals(old.value.counterDefinitions.filter { it.id != EntityId("charge") }, profiles.rows[old.id]!!.value.counterDefinitions.toList())
    }

    @Test fun backFromCounterEditorThenManagerKeepsBasicDraftUntilWholeCancel() {
        val old = seed(); shell.show(); open(); shell.waitForText("Ada")
        click("profile-edit-ada"); change(ProfileTags.NAME, "Unsaved name"); click(ProfileCounterTags.MANAGE)
        managerClick(ProfileCounterTags.edit("charge")); change(ProfileCounterTags.INITIAL, "9")
        fun back() = shell.pressAppBackAfterKeyboardDismissal()
        back(); rule.onNodeWithTag(ProfileCounterTags.MANAGER).assertIsDisplayed()
        assertEquals(old.value.counterDefinitions, shell.controllers.profiles!!.state.value.editor!!.counterDefinitions)
        back(); rule.onNodeWithTag(ProfileTags.NAME).assertTextContains("Unsaved name")
        click(ProfileTags.CANCEL); assertEquals(old, profiles.rows[old.id]); assertNull(shell.controllers.profiles!!.state.value.editor)
    }

    @Test fun narrowLargeFontCounterEditorScrollsAndCapacityIsExplained() {
        val old = seed()
        val more = (1..125).map { CounterDefinition(EntityId("extra-$it"), "Extra $it", null, null, 0, CounterLossPolicy.None, true) }
        profiles.rows[old.id] = old.copy(value = old.value.copy(counterDefinitions = SnapshotList.copyOf(old.value.counterDefinitions + more)))
        shell.show(width = 360.dp, height = 640.dp, fontScale = 2f); open(); shell.waitForText("Ada")
        click("profile-edit-ada"); click(ProfileCounterTags.MANAGE)
        rule.onNodeWithTag(ProfileCounterTags.ADD).performScrollTo().assertIsNotEnabled().assertHeightIsAtLeast(48.dp)
        shell.text(shell.string(R.string.profile_counter_limit, 128)).performScrollTo().assertIsDisplayed()
        managerClick(ProfileCounterTags.edit("charge")); change(ProfileCounterTags.LABEL, "Large font charge")
        rule.onNodeWithTag(ProfileCounterTags.APPLY).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        managerClick(ProfileCounterTags.BACK); click(ProfileTags.SAVE); saved()
        assertEquals("Large font charge", profiles.rows[old.id]!!.value.counterDefinitions[1].label)
    }
}
