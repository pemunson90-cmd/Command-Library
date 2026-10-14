package com.architectpsg.commandlibrary.feature.shell

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.presentation.profileCommanderIdentity
import com.architectpsg.commandlibrary.feature.shell.ui.profiles.ProfileCommanderTags as Tags
import com.architectpsg.commandlibrary.feature.shell.ui.profiles.ProfileTags
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileCommanderUiTest {
    @get:Rule val rule = createComposeRule()
    private val profiles = UiProfilesFixture()
    private val shell = ShellUiHarness(rule, profiles)
    @Before fun budget() = profileHostIdleBudget(300)
    @After fun close() { shell.close(); profileHostIdleBudget(60) }
    private fun click(tag: String) { rule.onNodeWithTag(tag).performScrollTo().performClick() }
    private fun change(tag: String, value: String) { rule.onNodeWithTag(tag).performScrollTo().performTextReplacement(value) }
    private fun listClick(list: String, tag: String) {
        rule.onNodeWithTag(list).performScrollToNode(hasTestTag(tag)); rule.onNodeWithTag(tag).performClick()
    }
    private fun open() { rule.onNodeWithTag("shell-nav-profiles").performClick(); shell.waitForText(shell.string(R.string.profiles_title)) }
    private fun saved() = shell.awaitState {
        val state = shell.controllers.profiles!!.state.value
        state.editor == null && !state.busy && !state.loading
    }
    private fun addLocal() {
        listClick(Tags.MANAGER, Tags.ADD); change(Tags.QUERY, "Existing"); listClick(Tags.RESULTS, Tags.SEARCH)
        shell.awaitState { shell.controllers.profiles!!.commanderSearch!!.state.value.let { it.loading == null && it.results.isNotEmpty() } }
        val card = shell.controllers.profiles!!.commanderSearch!!.state.value.results.single()
        val tag = Tags.select(card.oracleId.source.value, card.oracleId.raw)
        rule.onNodeWithTag(Tags.RESULTS).performScrollToNode(hasTestTag(tag))
        rule.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp).performClick()
    }
    private fun seed(): PlayerProfileSnapshot {
        profiles.seed("ada", "Ada"); val old = profiles.rows[EntityId("ada")]!!
        val slot = CommanderSlot(EntityId("partner"), syntheticCard("Stored commander", null).profileCommanderIdentity(), "Partner")
        val updated = old.copy(value = old.value.copy(commanders = SnapshotList.of(slot), appearance = old.value.appearance.copy(color = DisplayColor(0xffffffff))))
        profiles.rows[updated.id] = updated; return updated
    }

    @Test fun twoSelectionsOfOneLocalCardGetDistinctSlotsAndRemainDraftUntilWholeSave() {
        shell.show(width = 900.dp); open(); click(ProfileTags.NEW); change(ProfileTags.NAME, "Partners")
        click(Tags.MANAGE); addLocal(); addLocal()
        val slots = shell.controllers.profiles!!.state.value.editor!!.commanders
        assertEquals(2, slots.size); assertNotEquals(slots[0].id, slots[1].id); assertEquals(slots[0].identity, slots[1].identity)
        assertTrue(profiles.rows.isEmpty()); listClick(Tags.MANAGER, Tags.BACK); click(ProfileTags.SAVE); saved()
        assertEquals(slots, profiles.rows.values.single().value.commanders)
    }

    @Test fun renameRetainsIdentityAndRemoveRequiresConfirmationAndWholeSave() {
        val old = seed(); shell.show(); open(); shell.waitForText("Ada"); click("profile-edit-ada"); click(Tags.MANAGE)
        listClick(Tags.MANAGER, Tags.edit("partner")); change(Tags.LABEL, "Primary commander"); click(Tags.APPLY)
        val expected = old.value.commanders.single().copy(label = "Primary commander")
        assertEquals(expected, shell.controllers.profiles!!.state.value.editor!!.commanders.single()); assertEquals(old, profiles.rows[old.id])
        listClick(Tags.MANAGER, Tags.delete("partner")); rule.onNodeWithTag(Tags.CANCEL_DELETE).performClick()
        assertEquals(1, shell.controllers.profiles!!.state.value.editor!!.commanders.size)
        listClick(Tags.MANAGER, Tags.BACK); click(ProfileTags.SAVE); saved()
        assertEquals(old.value.copy(commanders = SnapshotList.of(expected)), profiles.rows[old.id]!!.value)
        click("profile-edit-ada"); click(Tags.MANAGE); listClick(Tags.MANAGER, Tags.delete("partner"))
        rule.onNodeWithTag(Tags.CONFIRM_DELETE).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, profiles.rows[old.id]!!.value.commanders.size)
        listClick(Tags.MANAGER, Tags.BACK); click(ProfileTags.SAVE); saved()
        assertTrue(profiles.rows[old.id]!!.value.commanders.isEmpty())
    }

    @Test fun pickerSeparatesLiteralIdsContainingColonAndHyphenWithoutDuplicateRows() {
        shell.show(); open(); click(ProfileTags.NEW); change(ProfileTags.NAME, "Identity probe"); click(Tags.MANAGE)
        val generation = shell.catalog.active!!.id
        val card = syntheticCard("Base", generation)
        val cards = listOf("a:b" to "c", "a" to "b:c", "a-b" to "c", "a" to "b-c").mapIndexed { index, (source, raw) ->
            card.copy(oracleId = OracleId(ProviderId(source), raw), name = "Card $index")
        }
        shell.catalog.lookupOverride = Outcome.Success(Page(SnapshotList.copyOf(cards), null,
            SnapshotReference(SYNTHETIC_PROVIDER, generation, null, LanguageTag.ENGLISH)))
        listClick(Tags.MANAGER, Tags.ADD); change(Tags.QUERY, "Card"); listClick(Tags.RESULTS, Tags.SEARCH)
        shell.awaitState { shell.controllers.profiles!!.commanderSearch!!.state.value.results.size == cards.size }
        for (value in cards) {
            val tag = Tags.select(value.oracleId.source.value, value.oracleId.raw)
            rule.onNodeWithTag(Tags.RESULTS).performScrollToNode(hasTestTag(tag)); rule.onNodeWithTag(tag).assertHasClickAction()
        }
        val chosen = cards.last()
        listClick(Tags.RESULTS, Tags.select(chosen.oracleId.source.value, chosen.oracleId.raw))
        assertEquals(Knowledge.Known(chosen.oracleId), shell.controllers.profiles!!.state.value.editor!!.commanders.single().identity.oracleId)
        assertTrue(profiles.rows.isEmpty())
    }

    @Test fun narrowLargeFontValidationAndNestedSystemBackPreserveUnsavedBasicDraft() {
        val old = seed(); shell.show(width = 360.dp, height = 640.dp, fontScale = 2f); open(); shell.waitForText("Ada")
        click("profile-edit-ada"); change(ProfileTags.NAME, "Unsaved"); click(Tags.MANAGE)
        listClick(Tags.MANAGER, Tags.edit("partner")); change(Tags.LABEL, ""); click(Tags.APPLY)
        shell.text(shell.string(R.string.profile_commander_invalid_label)).performScrollTo().assertIsDisplayed()
        click(Tags.CANCEL); listClick(Tags.MANAGER, Tags.ADD); change(Tags.QUERY, "Unsubmitted")
        fun back() { Class.forName("androidx.test.espresso.Espresso").getMethod("pressBack").invoke(null); rule.waitForIdle() }
        back(); rule.onNodeWithTag(Tags.MANAGER).assertExists()
        assertEquals("", shell.controllers.profiles!!.commanderSearch!!.queryInput)
        back(); rule.onNodeWithTag(ProfileTags.NAME).assertTextContains("Unsaved")
        click(ProfileTags.CANCEL); assertEquals(old, profiles.rows[old.id]); assertNull(shell.controllers.profiles!!.state.value.editor)
    }
}
