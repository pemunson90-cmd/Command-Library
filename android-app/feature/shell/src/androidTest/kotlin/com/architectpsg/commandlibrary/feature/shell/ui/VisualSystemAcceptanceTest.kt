package com.architectpsg.commandlibrary.feature.shell.ui

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
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
import com.architectpsg.commandlibrary.feature.shell.R
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
        val captureFile = File(directory, filename)
        captureFile.outputStream().use {
            assertTrue(rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        println("VISUAL_CAPTURE_PATH=" + captureFile.absolutePath)
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
        rule.onNodeWithText("Synthetic Card 03").assertHasClickAction().assertFullTarget().performClick()
        shell.awaitState { shell.controllers.search.state.value.selected?.name == "Synthetic Card 03" }
        assertEquals(results, shell.controllers.search.state.value.results)
        if (width < 840 || scale >= 1.5f) {
            shell.text(shell.string(R.string.detail_back)).performScrollTo().assertFullTarget().performClick()
            assertNull(shell.controllers.search.state.value.selected)
            shell.text("Synthetic Card 03").assertIsDisplayed()
        } else {
            rule.onNode(hasText("Synthetic Card 03") and hasClickAction()).assertIsSelected()
        }
        assertEquals("Synthetic", shell.controllers.search.queryInput)
    }

    @Test fun detailHasReadableRulesAndNonInteractiveMetadata() {
        val rules = "Synthetic Oracle ability."
        val card = syntheticCard("Synthetic Tidekeeper", GenerationId("synthetic-visual-generation"), rules).copy(stats = CardStats(Knowledge.Known("5"), Knowledge.Known("7"), Knowledge.Known("4"), Knowledge.Known("6")))
        var backed = false
        var importedText = ""
        host {
            importedText = formatTimestamp(card.provenance.fetchedAt)
            CardDetail(card, { backed = true }, WidthClass.of(width.dp))
        }
        rule.onNodeWithText(card.name).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        rule.onNodeWithText(shell.string(R.string.detail_mana_cost)).assert(!hasClickAction())
        rule.onNodeWithText(shell.string(R.string.detail_mana_value)).assert(!hasClickAction())
        screenshot("detail")
        rule.onNodeWithText(rules).performScrollTo().assertIsDisplayed()
        listOf("5/7", "4", "6").forEach { rule.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
        rule.onNodeWithText(shell.string(R.string.detail_language)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(shell.string(R.string.shell_language_english)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(shell.string(R.string.detail_reserved)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(shell.string(R.string.shell_value_no)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(shell.string(R.string.detail_source_title)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(card.provenance.provider.value).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("synthetic-visual-generation").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(shell.string(R.string.catalog_field_imported)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(importedText).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(shell.string(R.string.catalog_field_source_date)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(shell.string(R.string.detail_back)).performScrollTo().assertFullTarget().performClick()
        assertTrue(backed)
    }

    /** Scroll the exact descendant, including within a seat taller than the compact viewport. */
    private fun tableNode(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(matcher)
        return rule.onNode(matcher)
    }

    /** Visible bounds alone can be clipped; compare them to the layout's full touch target. */
    private fun SemanticsNodeInteraction.assertFullTarget(): SemanticsNodeInteraction {
        assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val full = getUnclippedBoundsInRoot()
        val node = fetchSemanticsNode()
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        // ForcedSize can override descendant density; use this target's density for conversion.
        val pixelsPerDp = node.layoutInfo.density.density
        assertTrue("target inside root horizontally: $full / $root", full.left.value * pixelsPerDp >= root.left - 1f && full.right.value * pixelsPerDp <= root.right + 1f)
        assertTrue("target inside root vertically: $full / $root", full.top.value * pixelsPerDp >= root.top - 1f && full.bottom.value * pixelsPerDp <= root.bottom + 1f)
        val visible = node.boundsInRoot
        assertEquals("unclipped target width", full.width.value * pixelsPerDp, visible.width, 1f)
        assertEquals("unclipped target height", full.height.value * pixelsPerDp, visible.height, 1f)
        return this
    }

    @Test fun lifeUsesExplicitRoleAndDispatchesActualCounterIdentity() {
        val game = syntheticGame()
        val displayed = mutableStateOf(game)
        val actions = mutableListOf<GameAction>()
        var undo = 0
        var log = 0
        host { LifeTable(displayed.value, EntityId("synthetic-life"), actions::add, { undo++ }, { log++ }) }
        tableNode(hasText(shell.string(R.string.life_undo))).assertFullTarget().performClick()
        tableNode(hasText(shell.string(R.string.life_log))).assertFullTarget().performClick()
        assertEquals(1, undo)
        assertEquals(1, log)
        val increase = shell.string(R.string.life_increase, "Synthetic vitality", "Synthetic Seat A")
        tableNode(hasContentDescription(increase)).assertFullTarget()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        assertEquals(GameAction.AdjustCounter(EntityId("synthetic-seat-a"), EntityId("synthetic-life"), null, 1), actions.single())
        tableNode(hasText("40")).assertIsDisplayed()
        screenshot("life")

        // Opposing partners have the same display label; their source slot IDs still distinguish them.
        val commanderLabels = listOf(
            "synthetic-b1" to shell.string(R.string.life_commander_counter, "Synthetic damage", "Synthetic Seat B", "Synthetic Partner", "synthetic-b1"),
            "synthetic-b2" to shell.string(R.string.life_commander_counter, "Synthetic damage", "Synthetic Seat B", "Synthetic Partner", "synthetic-b2"),
            "synthetic-a1" to shell.string(R.string.life_commander_counter, "Synthetic damage", "Synthetic Seat A", "Synthetic Own", "synthetic-a1"),
        )
        val descriptions = commanderLabels.map { (_, label) -> shell.string(R.string.life_increase, label, "Synthetic Seat A") }
        assertEquals(3, descriptions.distinct().size)
        commanderLabels.forEach { (slot, label) ->
            tableNode(hasText(label)).assertIsDisplayed()
            tableNode(hasContentDescription(shell.string(R.string.life_increase, label, "Synthetic Seat A"))).assertFullTarget().performClick()
            assertEquals(GameAction.AdjustCounter(EntityId("synthetic-seat-a"), EntityId("synthetic-damage"), EntityId(slot), 1), actions.last())
        }
        // Hidden data exists unchanged in the snapshot, but not in visual or spoken table content.
        rule.onAllNodesWithText("888", useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithText("Synthetic hidden", useUnmergedTree = true).assertCountEquals(0)
        listOf(R.string.life_increase, R.string.life_decrease).forEach { resource ->
            rule.onAllNodesWithContentDescription(shell.string(resource, "Synthetic hidden", "Synthetic Seat A"), useUnmergedTree = true).assertCountEquals(0)
        }
        assertEquals(game.counters, displayed.value.counters)
        assertEquals(888, game.counters.single { it.hidden }.value)

        // At each bound only the direction that remains valid can dispatch.
        val minimum = shell.string(R.string.life_decrease, "Synthetic floor", "Synthetic Seat A")
        val maximum = shell.string(R.string.life_increase, "Synthetic ceiling", "Synthetic Seat A")
        tableNode(hasContentDescription(minimum)).assertFullTarget().assertIsNotEnabled()
        tableNode(hasContentDescription(maximum)).assertFullTarget().assertIsNotEnabled()
        tableNode(hasContentDescription(shell.string(R.string.life_increase, "Synthetic floor", "Synthetic Seat A"))).assertFullTarget().performClick()
        assertEquals(GameAction.AdjustCounter(EntityId("synthetic-seat-a"), EntityId("synthetic-floor"), null, 1), actions.last())
        tableNode(hasContentDescription(shell.string(R.string.life_decrease, "Synthetic ceiling", "Synthetic Seat A"))).assertFullTarget().performClick()
        assertEquals(GameAction.AdjustCounter(EntityId("synthetic-seat-a"), EntityId("synthetic-ceiling"), null, -1), actions.last())

        tableNode(hasText("37")).assertIsDisplayed()
        val switch = shell.string(R.string.life_switch, "Synthetic Seat B")
        tableNode(hasText(switch)).assertFullTarget().performClick()
        assertEquals(GameAction.SwitchTurn(EntityId("synthetic-seat-b")), actions.last())
        val actionCount = actions.size
        rule.runOnIdle { displayed.value = game.copy(status = GameStatus.COMPLETED) }
        tableNode(hasContentDescription(increase)).assertFullTarget().assertIsNotEnabled()
        tableNode(hasContentDescription(shell.string(R.string.life_decrease, "Synthetic vitality", "Synthetic Seat A"))).assertFullTarget().assertIsNotEnabled()
        descriptions.forEach { tableNode(hasContentDescription(it)).assertFullTarget().assertIsNotEnabled() }
        tableNode(hasText("Synthetic Seat B")).assertIsDisplayed()
        rule.onAllNodesWithText(switch).assertCountEquals(0)
        assertEquals(actionCount, actions.size)
        assertEquals(game.counters, displayed.value.counters)
    }

    private fun syntheticGame(): GameSnapshot {
        val life = EntityId("synthetic-life")
        fun identity(name: String): CardIdentity = CardIdentity(
            Knowledge.Known(OracleId(SYNTHETIC_PROVIDER, name)), Knowledge.Absent("synthetic"), null,
            Knowledge.Known(name), LanguageTag.ENGLISH,
        )
        val seats = SnapshotList.of(
            Seat(EntityId("synthetic-seat-a"), "Synthetic Seat A", null, Knowledge.Absent("synthetic"), 0,
                SnapshotList.of(CommanderSlot(EntityId("synthetic-a1"), identity("synthetic-own-commander"), "Synthetic Own"))),
            Seat(EntityId("synthetic-seat-b"), "Synthetic Seat B", null, Knowledge.Absent("synthetic"), 1,
                SnapshotList.of(CommanderSlot(EntityId("synthetic-b1"), identity("synthetic-partner-one"), "Synthetic Partner"), CommanderSlot(EntityId("synthetic-b2"), identity("synthetic-partner-two"), "Synthetic Partner"))),
        )
        val definitions = SnapshotList.of(
            CounterDefinition(life, "Synthetic vitality", null, null, 40, CounterLossPolicy.AtOrBelow(0), false),
            CounterDefinition(EntityId("synthetic-damage"), "Synthetic damage", 0, null, 0, CounterLossPolicy.AtOrAbove(21), false),
            CounterDefinition(EntityId("synthetic-hidden"), "Synthetic hidden", null, null, 0, CounterLossPolicy.None, true),
            CounterDefinition(EntityId("synthetic-floor"), "Synthetic floor", 0, 10, 0, CounterLossPolicy.None, true),
            CounterDefinition(EntityId("synthetic-ceiling"), "Synthetic ceiling", 0, 10, 0, CounterLossPolicy.None, true),
        )
        return GameSnapshot(
            GameId(SYNTHETIC_PROVIDER, "synthetic-visual-game"), 1, Revision(0),
            GameSettings(Knowledge.Absent("synthetic"), 40, definitions, SnapshotList.empty(), SeatClockMode.NONE, 1),
            GameStatus.ACTIVE, SYNTHETIC_TIME, Knowledge.Absent("synthetic"), seats,
            SnapshotList.of(
                CounterState(life, seats[0].id, null, 40, false), CounterState(life, seats[1].id, null, 37, false),
                CounterState(EntityId("synthetic-damage"), seats[0].id, EntityId("synthetic-b1"), 3, false),
                CounterState(EntityId("synthetic-damage"), seats[0].id, EntityId("synthetic-b2"), 4, false),
                CounterState(EntityId("synthetic-damage"), seats[0].id, EntityId("synthetic-a1"), 5, false),
                CounterState(EntityId("synthetic-hidden"), seats[0].id, null, 888, true),
                CounterState(EntityId("synthetic-floor"), seats[0].id, null, 0, false),
                CounterState(EntityId("synthetic-ceiling"), seats[0].id, null, 10, false),
            ),
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
