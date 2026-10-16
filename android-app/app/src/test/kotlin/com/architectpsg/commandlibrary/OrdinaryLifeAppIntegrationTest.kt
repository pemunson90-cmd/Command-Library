package com.architectpsg.commandlibrary

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.StorageDatabase
import com.architectpsg.commandlibrary.data.storage.life.codec.LifePayloadCodec
import com.architectpsg.commandlibrary.di.*
import com.architectpsg.commandlibrary.feature.life.persistence.SqlPersistedGameService
import com.architectpsg.commandlibrary.feature.shell.ShellPorts
import com.architectpsg.commandlibrary.feature.shell.R as ShellR
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Actual host UI/AppGraph/Room/engine; the second service epoch is not Android process-death evidence. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], shadows = [RealHostDirectoryLinux::class, RealHostBlockGuardOs::class])
class OrdinaryLifeAppIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val token = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private lateinit var app: CommandLibraryApplication
    private lateinit var ports: ShellPorts
    private lateinit var games: PersistedGameService
    private lateinit var gameId: GameId
    private lateinit var unrelatedDeck: DeckSnapshot
    private fun <T> ok(value: Outcome<T>): T {
        assertTrue("Expected success: $value", value is Outcome.Success)
        return (value as Outcome.Success).value
    }
    @Before fun actualPortsAndUnrelatedPrivateDeck() = runBlocking {
        app = compose.activity.application as CommandLibraryApplication
        ports = ok(app.graph.ports())
        games = checkNotNull(ports.ordinaryGames)
        val source = PlainDeckExportBoundaryFixture.source(DeckId(LocalDeckLibraryLimits.SOURCE, "ordinary-life-conservation"),
            "Unrelated private deck Ω", checkNotNull(ports.utcClock).utcNow())
        unrelatedDeck = ok(checkNotNull(ports.localDecks).create(source, EntityId("ordinary-life-deck-seed"), token))
        assertEquals(0L, ok(games.history(GameHistoryFilter(), PageRequest(20), token)).totalMatches)
    }
    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun await(check: () -> Boolean) = compose.waitUntil(20_000) { compose.waitForIdle(); check() }
    private fun page() = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex) and
        hasAnyAncestor(hasTestTag("ordinary-life-screen")))
    private fun click(matcher: SemanticsMatcher) {
        page().performScrollToNode(matcher)
        await { compose.onAllNodes(matcher and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(matcher).performClick()
    }
    private fun clickTag(tag: String) = click(hasTestTag(tag))
    private fun edit(tag: String, text: String) {
        page().performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performTextReplacement(text)
    }
    private fun header(tag: String) {
        await { compose.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag).performClick()
    }
    private fun navigateLife() {
        await { exists("shell-nav-life") || exists("shell-nav-more") }
        if (!exists("shell-nav-life")) compose.onNodeWithTag("shell-nav-more").performClick()
        compose.onNodeWithTag("shell-nav-life").performClick()
        await { exists("ordinary-life-screen") }
    }
    private fun read(): GameSnapshot = runBlocking { ok(games.read(gameId, token)) }
    private fun events(): List<GameEvent> = runBlocking { ok(games.events(gameId, PageRequest(100), token)).items.toList() }
    private fun next(before: GameSnapshot): GameSnapshot {
        await { read().revision.value == before.revision.value + 1 }
        return read()
    }
    private fun <T> independent(epoch: EntityId = ProcessLifeIdentity.id, block: suspend (PersistedGameService) -> T): T = runBlocking {
        ok(StorageDatabase.open(app, AppGraph.DATABASE_FILE)).use { db ->
            block(SqlPersistedGameService(db, LifePayloadCodec(), RandomUuidIdSource, SystemGameRandom,
                AndroidLifeClock, epoch, ProviderId("local-games")))
        }
    }
    /** No-timer undo/recovery may reanchor; every other snapshot field must remain exact. */
    private fun unchangedExcept(before: GameSnapshot, after: GameSnapshot, counters: List<CounterState> = before.counters,
        settings: GameSettings = before.settings) {
        assertEquals(before.copy(revision = after.revision, lastEventId = after.lastEventId, settings = settings,
            counters = SnapshotList.copyOf(counters), clock = before.clock.copy(anchor = after.clock.anchor),
            elapsed = before.elapsed.copy(anchor = after.elapsed.anchor)), after)
        assertEquals(SeatClockMode.NONE, after.clock.mode)
        assertTrue(after.clock.paused && after.elapsed.paused)
        assertEquals(kotlin.time.Duration.ZERO, after.elapsed.gameElapsed)
        assertEquals(kotlin.time.Duration.ZERO, after.elapsed.turnElapsed)
    }
    private fun changedCounters(before: GameSnapshot, vararg deltas: GameAction.AdjustCounter): List<CounterState> = before.counters.map { state ->
        val delta = deltas.filter { it.seatId == state.seatId && it.definitionId == state.definitionId && it.commanderSlotId == state.commanderSlotId }
            .sumOf { it.delta }
        state.copy(value = state.value + delta)
    }
    private fun description(label: String, player: String, increase: Boolean): String = compose.activity.getString(
        if (increase) ShellR.string.ordinary_life_increase_step else ShellR.string.ordinary_life_decrease_step, label, player, 5)
    private fun counter(label: String, player: String, increase: Boolean) = click(hasContentDescription(description(label, player, increase)))
    private fun baseline(): List<Any?> = runBlocking { listOf(ok(checkNotNull(ports.localDecks).load(unrelatedDeck.id, token)),
        ok(checkNotNull(ports.localInventory).snapshot(token)), ok(ports.settings.read(token)),
        ok(checkNotNull(ports.personalTags).load(token)), ok(checkNotNull(ports.providerProfiles).list(PageRequest(100), token))) }

    @Test fun actualOrdinaryTableGroupsDamageUndoPersistsAcrossRecreationAndEpochThenConfirmsAndReopensDraw() {
        val unrelated = baseline()
        navigateLife()
        await { compose.onAllNodesWithText(compose.activity.getString(ShellR.string.ordinary_life_empty)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0L, runBlocking { ok(games.history(GameHistoryFilter(), PageRequest(20), token)).totalMatches })
        header("ordinary-life-new")
        clickTag("ordinary-life-seat-count-2")
        edit("ordinary-life-initial", "40")
        edit("ordinary-life-player-0", "Alice Ω")
        edit("ordinary-life-player-1", "Bob 日本語")
        clickTag("ordinary-life-commanders-0-1")
        edit("ordinary-life-commander-0-0", "Manual commander A")
        clickTag("ordinary-life-commanders-1-1")
        edit("ordinary-life-commander-1-0", "Manual commander B")
        assertEquals(0L, runBlocking { ok(games.history(GameHistoryFilter(), PageRequest(20), token)).totalMatches })
        clickTag("ordinary-life-create")
        await { runBlocking { ok(games.history(GameHistoryFilter(), PageRequest(20), token)).totalMatches == 1L } }
        gameId = runBlocking { ok(games.history(GameHistoryFilter(), PageRequest(20), token)).games.items.single().gameId }
        val initial = read()
        assertEquals(Revision(0), initial.revision)
        assertEquals(GameStatus.PAUSED, initial.status)
        assertEquals(40, initial.settings.initialLife)
        assertEquals(1, initial.settings.adjustmentStep)
        assertFalse(initial.settings.deductLifeForCommanderDamage)
        assertFalse(initial.settings.elapsedTimers.enabled)
        assertTrue(initial.settings.variantDefinitions.isEmpty() && initial.variants.isEmpty())
        assertEquals(SeatClockMode.NONE, initial.settings.clockMode)
        assertEquals(SeatClockMode.NONE, initial.clock.mode)
        assertTrue(initial.clock.seats.isEmpty() && initial.clock.paused)
        assertNull(initial.clock.anchor)
        assertNull(initial.clock.activeSeat)
        assertEquals(listOf("Alice Ω", "Bob 日本語"), initial.seats.map { it.playerName })
        assertEquals(listOf(0, 1), initial.seats.map { it.order })
        assertEquals(2, initial.seats.map { it.id }.distinct().size)
        assertEquals(listOf("Manual commander A", "Manual commander B"), initial.seats.flatMap { it.commanders }.map { it.label })
        val identities = initial.seats.map { it.id } + initial.seats.flatMap { it.commanders }.map { it.id }
        assertEquals(4, identities.distinct().size)
        initial.seats.forEach { seat ->
            assertNull(seat.profileId); assertTrue(seat.deck is Knowledge.Absent)
            val identity = seat.commanders.single().identity
            assertTrue(identity.oracleId is Knowledge.Absent && identity.printingId is Knowledge.Absent)
        }
        val definitions = initial.settings.counterDefinitions
        assertEquals(6, definitions.size)
        assertEquals(setOf(CounterPreset.POISON, CounterPreset.ENERGY, CounterPreset.EXPERIENCE),
            definitions.filter { it.role == CounterRole.OTHER }.map { it.preset }.toSet())
        val expectedCounters = definitions.flatMap { definition -> initial.seats.flatMap { seat ->
            val slots: List<EntityId?> = when (definition.role) {
                CounterRole.COMMANDER_DAMAGE -> initial.seats.filter { it.id != seat.id }.flatMap { it.commanders.map { c -> c.id } }
                CounterRole.COMMANDER_TAX -> seat.commanders.map { it.id }
                else -> listOf(null)
            }
            slots.map { CounterState(definition.id, seat.id, it, definition.initial, false) }
        } }
        assertEquals(expectedCounters, initial.counters.toList())
        assertEquals(initial, independent { ok(it.read(gameId, token)) })
        assertTrue(events().isEmpty())
        val alice = initial.seats[0]; val bob = initial.seats[1]
        val life = definitions.single { it.role == CounterRole.LIFE }
        val energy = definitions.single { it.preset == CounterPreset.ENERGY }
        val damage = definitions.single { it.role == CounterRole.COMMANDER_DAMAGE }
        clickTag("ordinary-life-step-5")
        val step = next(initial)
        unchangedExcept(initial, step, settings = initial.settings.copy(adjustmentStep = 5))
        counter(life.label, alice.playerName, false)
        val lifeChanged = next(step)
        unchangedExcept(step, lifeChanged, changedCounters(step, GameAction.AdjustCounter(alice.id, life.id, null, -5)))
        counter(energy.label, alice.playerName, true)
        val energyChanged = next(lifeChanged)
        unchangedExcept(lifeChanged, energyChanged, changedCounters(lifeChanged, GameAction.AdjustCounter(alice.id, energy.id, null, 5)))
        clickTag("ordinary-life-deduct")
        val approved = next(energyChanged)
        unchangedExcept(energyChanged, approved, settings = energyChanged.settings.copy(deductLifeForCommanderDamage = true))
        val commander = alice.commanders.single()
        val damageLabel = compose.activity.getString(ShellR.string.life_commander_counter, damage.label, alice.playerName, commander.label, "1")
        counter(damageLabel, bob.playerName, true)
        val damaged = next(approved)
        val damageAction = GameAction.AdjustCounter(bob.id, damage.id, commander.id, 5)
        val lifeAction = GameAction.AdjustCounter(bob.id, life.id, null, -5)
        unchangedExcept(approved, damaged, changedCounters(approved, damageAction, lifeAction))
        val groupEvent = events().single { it.revision == damaged.revision }
        val group = groupEvent.action as GameAction.Group
        assertEquals(listOf(damageAction, lifeAction), group.actions.toList())
        assertTrue(group.actions.none { it is GameAction.Group })
        assertEquals(group.groupId, groupEvent.groupId)
        assertEquals(damaged, independent { ok(it.read(gameId, token)) })
        click(hasText(compose.activity.getString(ShellR.string.life_undo)))
        val undone = next(damaged)
        unchangedExcept(approved, undone)
        val undoEvent = events().single { it.revision == undone.revision }
        assertEquals(Knowledge.Known(groupEvent.id), undoEvent.undoOfEventId)
        click(hasText(compose.activity.getString(ShellR.string.life_log)))
        page().performScrollToNode(hasText(compose.activity.getString(ShellR.string.ordinary_life_event_group)))
        await { compose.onAllNodesWithText(compose.activity.getString(ShellR.string.ordinary_life_event_group)).fetchSemanticsNodes().isNotEmpty() }
        click(hasText(compose.activity.getString(ShellR.string.ordinary_life_table)))
        val eventCount = events().size
        compose.activityRule.scenario.recreate()
        await { exists("ordinary-life-screen") }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        assertEquals(undone, read())
        assertEquals(eventCount, events().size)
        header("ordinary-life-saved") // Stops app observation before deliberate foreign-epoch resume.
        val freshEpoch = EntityId("ordinary-life-host-reopen-epoch")
        assertNotEquals(ProcessLifeIdentity.id, freshEpoch)
        val resumed = independent(freshEpoch) { service ->
            assertEquals(undone, ok(service.read(gameId, token)))
            val saved = ok(service.resume(gameId, token))
            unchangedExcept(undone, saved)
            assertEquals(undone.revision, saved.revision)
            assertEquals(1L, ok(service.history(GameHistoryFilter(), PageRequest(20), token)).totalMatches)
            saved
        }
        clickTag("ordinary-life-open-${gameId.raw}")
        await { exists("ordinary-life-step-5") }
        val reopenedInApp = read()
        unchangedExcept(resumed, reopenedInApp)
        assertEquals(resumed.revision, reopenedInApp.revision)
        assertEquals(eventCount, events().size)
        clickTag("ordinary-life-finish")
        compose.onNodeWithText(compose.activity.getString(ShellR.string.ordinary_life_draw)).performScrollTo().performClick()
        compose.onNodeWithTag("ordinary-life-result-reason").performScrollTo().performTextReplacement("Agreed draw Ω")
        compose.onNodeWithTag("ordinary-life-result-confirm").performClick()
        val finished = next(reopenedInApp)
        assertEquals(GameStatus.COMPLETED, finished.status)
        assertEquals(reopenedInApp.counters, finished.counters)
        assertEquals(reopenedInApp.seats, finished.seats)
        val result = (finished.result as Knowledge.Known).value
        assertEquals("Agreed draw Ω", result.reason)
        assertFalse(result.retracted)
        assertTrue(result.winners.isEmpty())
        assertEquals(initial.seats.map { SeatResult(it.id, SeatResultKind.DRAW, null) }, result.seatResults.toList())
        assertTrue(finished.endedAt is Knowledge.Known)
        assertEquals(reopenedInApp.copy(revision = finished.revision, lastEventId = finished.lastEventId,
            status = GameStatus.COMPLETED, endedAt = finished.endedAt, result = finished.result,
            clock = reopenedInApp.clock.copy(anchor = finished.clock.anchor),
            elapsed = reopenedInApp.elapsed.copy(anchor = finished.elapsed.anchor)), finished)
        assertEquals(finished, independent { ok(it.read(gameId, token)) })
        val disabledLife = hasContentDescription(description(life.label, alice.playerName, false))
        page().performScrollToNode(disabledLife)
        compose.onNode(disabledLife).assertIsNotEnabled()
        clickTag("ordinary-life-reopen")
        compose.onNodeWithTag("ordinary-life-result-reason").performScrollTo().performTextReplacement("Continue this table")
        compose.onNodeWithTag("ordinary-life-result-confirm").performClick()
        val reopened = next(finished)
        assertEquals(GameStatus.PAUSED, reopened.status)
        assertEquals(finished.counters, reopened.counters)
        val retracted = (reopened.result as Knowledge.Known).value
        assertEquals(result.id, retracted.id)
        assertTrue(retracted.retracted)
        assertTrue(reopened.endedAt is Knowledge.Absent)
        assertEquals(finished.copy(revision = reopened.revision, lastEventId = reopened.lastEventId,
            status = GameStatus.PAUSED, endedAt = reopened.endedAt, result = reopened.result,
            clock = finished.clock.copy(anchor = reopened.clock.anchor),
            elapsed = finished.elapsed.copy(anchor = reopened.elapsed.anchor)), reopened)
        page().performScrollToNode(disabledLife)
        compose.onNode(disabledLife).assertIsEnabled()
        assertEquals(reopened, independent { ok(it.read(gameId, token)) })
        assertEquals(1L, runBlocking { ok(games.history(GameHistoryFilter(), PageRequest(20), token)).totalMatches })
        assertEquals(unrelated, baseline())
    }
}
