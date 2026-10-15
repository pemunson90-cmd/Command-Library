package com.architectpsg.commandlibrary

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.ShellPorts
import com.architectpsg.commandlibrary.feature.shell.R as ShellR
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.annotation.Config
import java.util.UUID

/** Actual saved-deck user-defined board-size checks, actual AppGraph and unchanged Room state. */
@RunWith(AndroidJUnit4::class)
@Config(shadows = [RealHostDirectoryLinux::class, RealHostBlockGuardOs::class])
class BoardConstraintAppIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val cancel = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private lateinit var ports: ShellPorts
    private lateinit var source: DeckSnapshot
    private fun <T> ok(value: Outcome<T>): T = (value as Outcome.Success).value
    private val seed = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val app = ApplicationProvider.getApplicationContext<CommandLibraryApplication>()
            runBlocking {
                ports = ok(app.graph.ports())
                val suffix = UUID.randomUUID().toString()
                source = PlainDeckExportBoundaryFixture.source(DeckId(LocalDeckLibraryLimits.SOURCE, "board-check-$suffix"), "Board checks $suffix", checkNotNull(ports.utcClock).utcNow())
                ok(checkNotNull(ports.deckSnapshotValidation)(source, cancel))
                ok(checkNotNull(ports.localDecks).create(source, EntityId("board-check-seed-$suffix"), cancel))
            }
            base.evaluate()
        }
    } }
    @get:Rule val chain: RuleChain = RuleChain.outerRule(seed).around(compose)
    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun await(check: () -> Boolean) = compose.waitUntil(20_000) { compose.waitForIdle(); check() }
    private fun scroll(list: String, tag: String) {
        await {
            if (!exists(list)) false else try {
                compose.onNodeWithTag(list).performScrollToNode(hasTestTag(tag)); true
            } catch (_: AssertionError) { false }
        }
    }
    private fun edit(tag: String, value: String) {
        scroll("board-checks-list", tag)
        compose.onNodeWithTag(tag).performTextReplacement(value)
    }
    private fun result(tag: String, value: String) {
        scroll("board-checks-list", tag)
        compose.onNodeWithTag(tag).assertTextEquals(value).assertIsDisplayed()
    }
    @Test fun actualSavedDeckUserLimitsCountsRecreationInvalidCorrectionAndNoWrites() {
        val library = checkNotNull(ports.localDecks)
        val before = runBlocking { listOf(ok(library.list(PageRequest(100), cancel)), ok(checkNotNull(ports.localInventory).snapshot(cancel)), ok(ports.settings.read(cancel)), ok(ports.catalogStatus.status(cancel))) }
        await { exists("shell-nav-decks") || exists("shell-nav-more") }
        if (!exists("shell-nav-decks")) compose.onNodeWithTag("shell-nav-more").performClick()
        compose.onNodeWithTag("shell-nav-decks").performClick()
        val row = "deck-open-${source.id.raw}"
        scroll("deck-library-list", row); compose.onNodeWithTag(row).performClick()
        scroll("deck-library-overview", "deck-board-checks")
        compose.onNodeWithTag("deck-board-checks").assertIsEnabled().performClick()
        edit("board-checks-min-MAIN", "5"); edit("board-checks-max-MAIN", "5")
        edit("board-checks-max-SIDEBOARD", "3")
        compose.onNodeWithTag("board-checks-assess").performClick()
        result("board-checks-result-MAIN", compose.activity.getString(ShellR.string.board_checks_pass, "Main deck", "5"))
        result("board-checks-result-SIDEBOARD", compose.activity.getString(ShellR.string.board_checks_fail, "Sideboard", "4"))
        compose.activityRule.scenario.recreate()
        result("board-checks-result-MAIN", compose.activity.getString(ShellR.string.board_checks_pass, "Main deck", "5"))
        edit("board-checks-max-MAIN", "4")
        compose.onNodeWithTag("board-checks-assess").performClick()
        result("board-checks-invalid", compose.activity.getString(ShellR.string.board_checks_invalid))
        compose.onNodeWithTag("board-checks-result-MAIN").assertDoesNotExist()
        edit("board-checks-max-MAIN", "5"); edit("board-checks-max-SIDEBOARD", "4")
        compose.onNodeWithTag("board-checks-assess").performClick()
        result("board-checks-result-SIDEBOARD", compose.activity.getString(ShellR.string.board_checks_pass, "Sideboard", "4"))
        runBlocking {
            assertEquals(source, ok(library.load(source.id, cancel)))
            assertEquals(before, listOf(ok(library.list(PageRequest(100), cancel)), ok(checkNotNull(ports.localInventory).snapshot(cancel)), ok(ports.settings.read(cancel)), ok(ports.catalogStatus.status(cancel))))
        }
    }
}
