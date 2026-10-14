package com.architectpsg.commandlibrary.feature.shell

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.ui.profiles.ProfileAppearanceTags
import com.architectpsg.commandlibrary.feature.shell.ui.profiles.ProfileTags
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileAppearanceUiTest {
    @get:Rule val rule = createComposeRule()
    private val profiles = UiProfilesFixture()
    private val shell = ShellUiHarness(rule, profiles)
    @Before fun hostBudget() = profileHostIdleBudget(300)
    @After fun close() { shell.close(); profileHostIdleBudget(60) }
    private fun click(tag: String) { rule.onNodeWithTag(tag).performScrollTo().performClick() }
    private fun change(tag: String, value: String) { rule.onNodeWithTag(tag).performScrollTo().performTextReplacement(value) }
    private fun open() { rule.onNodeWithTag("shell-nav-profiles").performClick(); shell.waitForText(shell.string(R.string.profiles_title)) }
    private fun saved() = shell.awaitState {
        val state = shell.controllers.profiles!!.state.value
        state.editor == null && !state.busy && !state.loading
    }
    private fun seed(): PlayerProfileSnapshot {
        profiles.seed("ada", "Ada")
        val old = profiles.rows[EntityId("ada")]!!
        val card = CardIdentity(Knowledge.Absent("Synthetic"), Knowledge.Absent("Synthetic"),
            ProviderIdentity(ProviderId("synthetic"), "card", "one"), Knowledge.Known("Commander"), LanguageTag.ENGLISH)
        val appearance = SeatAppearance(DisplayColor(0x01234567), GameArtReference.CachedCard(card, EntityId("cached-art")),
            ArtFit.CONTAIN, ArtCrop(0.1, 0.2, 0.8, 0.9), SeatOrientation.CLOCKWISE)
        val row = old.copy(value = old.value.copy(appearance = appearance, commanders = SnapshotList.of(CommanderSlot(EntityId("partner"), card, "Partner"))))
        profiles.rows[row.id] = row
        return row
    }

    @Test fun createsColorAndOrientationOnlyWhenWholeProfileIsSaved() {
        shell.show(); open(); click(ProfileTags.NEW); change(ProfileTags.NAME, "Color player")
        click(ProfileAppearanceTags.MANAGE); click(ProfileAppearanceTags.USE_COLOR)
        change(ProfileAppearanceTags.COLOR, "#FFFFFFFF"); click(ProfileAppearanceTags.CLOCKWISE)
        rule.onNodeWithTag(ProfileAppearanceTags.CLOCKWISE).assertIsSelected(); click(ProfileAppearanceTags.APPLY)
        assertTrue(profiles.rows.isEmpty()); click(ProfileTags.SAVE); saved()
        val value = profiles.rows.values.single().value.appearance
        assertEquals(4294967295L, value.color!!.argb); assertEquals(SeatOrientation.CLOCKWISE, value.orientation)
    }

    @Test fun unsetAndOrientationEditKeepArtCropFitCommanderAndCounterFields() {
        val old = seed(); shell.show(width = 900.dp); open(); shell.waitForText("Ada")
        click("profile-edit-ada"); click(ProfileAppearanceTags.MANAGE)
        shell.text(shell.string(R.string.profile_appearance_kept)).performScrollTo().assertIsDisplayed()
        for (tag in listOf(ProfileAppearanceTags.UPRIGHT, ProfileAppearanceTags.CLOCKWISE, ProfileAppearanceTags.INVERTED, ProfileAppearanceTags.COUNTERCLOCKWISE)) {
            rule.onNodeWithTag(tag).performScrollTo().assertHasClickAction().assertHeightIsAtLeast(48.dp)
        }
        click(ProfileAppearanceTags.USE_COLOR); click(ProfileAppearanceTags.COUNTERCLOCKWISE); click(ProfileAppearanceTags.APPLY)
        assertEquals(old, profiles.rows[old.id]); click(ProfileTags.SAVE); saved()
        assertEquals(old.value.copy(appearance = old.value.appearance.copy(color = null, orientation = SeatOrientation.COUNTERCLOCKWISE)), profiles.rows[old.id]!!.value)
    }

    @Test fun invalidColorKeepsInputAndPerFormCancelKeepsPriorAppearance() {
        val old = seed(); shell.show(); open(); shell.waitForText("Ada")
        click("profile-edit-ada"); click(ProfileAppearanceTags.MANAGE)
        change(ProfileAppearanceTags.COLOR, "#bad"); click(ProfileAppearanceTags.APPLY)
        shell.text(shell.string(R.string.profile_counter_invalid_color)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(ProfileAppearanceTags.COLOR).assertTextContains("#bad")
        assertEquals(old.value.appearance, shell.controllers.profiles!!.state.value.editor!!.appearance)
        click(ProfileAppearanceTags.CANCEL); assertEquals(old.value.appearance, shell.controllers.profiles!!.state.value.editor!!.appearance)
        assertEquals(old, profiles.rows[old.id])
    }

    @Test fun systemBackReturnsToBasicDraftBeforeWholeCancelDiscardsIt() {
        val old = seed(); shell.show(); open(); shell.waitForText("Ada")
        click("profile-edit-ada"); change(ProfileTags.NAME, "Unsaved name"); click(ProfileAppearanceTags.MANAGE)
        change(ProfileAppearanceTags.COLOR, "#FFFFFFFF")
        Class.forName("androidx.test.espresso.Espresso").getMethod("pressBack").invoke(null); rule.waitForIdle()
        rule.onNodeWithTag(ProfileTags.NAME).assertTextContains("Unsaved name")
        assertEquals(old.value.appearance, shell.controllers.profiles!!.state.value.editor!!.appearance)
        click(ProfileTags.CANCEL); assertNull(shell.controllers.profiles!!.state.value.editor); assertEquals(old, profiles.rows[old.id])
    }

    @Test fun narrowLargeFontEditorKeepsTransparentBlackDistinctFromUnset() {
        val old = seed(); shell.show(width = 360.dp, height = 640.dp, fontScale = 2f); open(); shell.waitForText("Ada")
        click("profile-edit-ada"); click(ProfileAppearanceTags.MANAGE)
        rule.onNodeWithTag(ProfileAppearanceTags.USE_COLOR).performScrollTo().assertIsOn()
        change(ProfileAppearanceTags.COLOR, "#00000000"); click(ProfileAppearanceTags.INVERTED)
        rule.onNodeWithTag(ProfileAppearanceTags.APPLY).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        click(ProfileTags.SAVE); saved()
        assertEquals(DisplayColor(0), profiles.rows[old.id]!!.value.appearance.color)
        assertEquals(SeatOrientation.INVERTED, profiles.rows[old.id]!!.value.appearance.orientation)
    }
}
