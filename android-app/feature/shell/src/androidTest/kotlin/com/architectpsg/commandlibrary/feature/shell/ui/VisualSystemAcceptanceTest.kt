package com.architectpsg.commandlibrary.feature.shell.ui

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.*
import com.architectpsg.commandlibrary.feature.shell.ui.life.LifeTable
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/** Synthetic/non-product fixtures. Actual rendered PNGs, never claims of TalkBack/physical posture.
 * Matrix: 360x800, 900x800, 360x360 dp; light/dark; 100%/200% text.
 */
@RunWith(Parameterized::class)
class VisualSystemAcceptanceTest(private val width: Int, private val height: Int, private val dark: Boolean, private val scale: Float) {
    @get:Rule val rule = createComposeRule()
    private val shell = ShellUiHarness(rule)
    private val theme get() = if (dark) ThemePreference.DARK else ThemePreference.LIGHT
    @After fun close() = shell.close()

    private fun host(content: @Composable () -> Unit) {
        rule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp)) then DeviceConfigurationOverride.FontScale(scale)) {
                ShellTheme(theme) { Surface { content() } }
            }
        }
    }

    private fun screenshot(view: String) {
        rule.waitForIdle()
        val args = InstrumentationRegistry.getArguments()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = args.getString("additionalTestOutputDir")?.let(::File)
            ?: File(context.getExternalFilesDir(null), "visual-acceptance")
        assertTrue(directory.isDirectory || directory.mkdirs())
        val filename = "synthetic-${view}-${width}x${height}-${if (dark) "dark" else "light"}-${scale}.png"
        File(directory, filename).outputStream().use {
            assertTrue(rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun searchSelectionAndBackPreserveResults() {
        shell.settings.changeElsewhere(theme)
        shell.catalog.active = shell.catalog.newGeneration((1..30).map { "Synthetic Card %02d".format(it) }, 0)
        shell.show(width.dp, height.dp, scale)
        rule.runOnIdle { shell.controllers.search.onQueryChange("Synthetic"); shell.controllers.search.submit() }
        shell.awaitState { shell.controllers.search.state.value.results.isNotEmpty() }
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Synthetic Card 03"))
        val results = shell.controllers.search.state.value.results
        screenshot("search")
        rule.onNodeWithText("Synthetic Card 03").assertHasClickAction().performClick()
        shell.awaitState { shell.controllers.search.state.value.selected?.name == "Synthetic Card 03" }
        assertEquals(results, shell.controllers.search.state.value.results)
        if (width < 840 || scale >= 1.5f) {
            shell.text(shell.string(R.string.detail_back)).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
            assertNull(shell.controllers.search.state.value.selected)
            shell.text("Synthetic Card 03").assertIsDisplayed()
        } else {
            rule.onNode(hasText("Synthetic Card 03") and hasClickAction()).assertIsSelected()
        }
        assertEquals("Synthetic", shell.controllers.search.queryInput)
    }

    @Test fun detailHasReadableRulesAndNonInteractiveMetadata() {
        val card = syntheticCard("Synthetic Tidekeeper", null, "Synthetic rules fixture.\nWhenever you draw a card, read this ability.\nA second paragraph remains selectable as normal text.")
        var backed = false
        host { CardDetail(card, { backed = true }, WidthClass.of(width.dp)) }
        rule.onNodeWithText(card.name).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        rule.onNodeWithText(shell.string(R.string.detail_mana_cost)).assert(!hasClickAction())
        rule.onNodeWithText(shell.string(R.string.detail_mana_value)).assert(!hasClickAction())
        screenshot("detail")
        rule.onNodeWithText(shell.string(R.string.detail_language)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(shell.string(R.string.detail_source_title)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(shell.string(R.string.detail_back)).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertTrue(backed)
    }

    @Test fun lifeUsesExplicitRoleAndDispatchesActualCounterIdentity() {
        val game = syntheticGame()
        val actions = mutableListOf<GameAction>()
        var undo = 0
        var log = 0
        host { LifeTable(game, EntityId("synthetic-life"), actions::add, { undo++ }, { log++ }) }
        rule.onNodeWithText(shell.string(R.string.life_undo)).assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithText(shell.string(R.string.life_log)).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, undo)
        assertEquals(1, log)
        val increase = shell.string(R.string.life_increase, "Synthetic vitality", "Synthetic Seat A")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(increase))
        rule.onNodeWithText("40").assertExists()
        rule.onNodeWithContentDescription(increase).assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        assertEquals(GameAction.AdjustCounter(EntityId("synthetic-seat-a"), EntityId("synthetic-life"), null, 1), actions.single())
        // Stateless callback does not pretend to persist or optimistically mutate the snapshot.
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("40"))
        rule.onNodeWithText("40").assertIsDisplayed()
        screenshot("life")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("37"))
        rule.onNodeWithText("37").assertIsDisplayed()
        val switch = shell.string(R.string.life_switch, "Synthetic Seat B")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(switch))
        rule.onNodeWithText(switch).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(GameAction.SwitchTurn(EntityId("synthetic-seat-b")), actions.last())
    }

    private fun syntheticGame(): GameSnapshot {
        val life = EntityId("synthetic-life")
        val seats = SnapshotList.of(
            Seat(EntityId("synthetic-seat-a"), "Synthetic Seat A", null, Knowledge.Absent("synthetic"), 0, SnapshotList.empty()),
            Seat(EntityId("synthetic-seat-b"), "Synthetic Seat B", null, Knowledge.Absent("synthetic"), 1, SnapshotList.empty()),
        )
        return GameSnapshot(
            GameId(SYNTHETIC_PROVIDER, "synthetic-visual-game"), 1, Revision(0),
            GameSettings(Knowledge.Absent("synthetic"), 40, SnapshotList.of(CounterDefinition(life, "Synthetic vitality", null, null, 40, CounterLossPolicy.AtOrBelow(0), false)), SnapshotList.empty(), SeatClockMode.NONE, 1),
            GameStatus.ACTIVE, SYNTHETIC_TIME, Knowledge.Absent("synthetic"), seats,
            SnapshotList.of(CounterState(life, seats[0].id, null, 40, false), CounterState(life, seats[1].id, null, 37, false)),
            seats[0].id, 3,
            SeatClockState(SeatClockMode.NONE, SnapshotList.empty(), null, null, false, ClockZeroPolicy.ALERT, ClockUndoPolicy.KEEP_ELAPSED, SnapshotList.empty()),
            SnapshotList.empty(), Knowledge.Absent("synthetic"), Knowledge.Absent("synthetic"),
        )
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}x{1}-dark={2}-font={3}")
        fun configurations(): List<Array<Any>> = listOf(360 to 800, 900 to 800, 360 to 360).flatMap { (w, h) ->
            listOf(false, true).flatMap { d -> listOf(1f, 2f).map { s -> arrayOf<Any>(w, h, d, s) } }
        }
    }
}
