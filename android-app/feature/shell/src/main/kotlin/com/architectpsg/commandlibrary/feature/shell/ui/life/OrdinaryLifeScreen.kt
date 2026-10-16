package com.architectpsg.commandlibrary.feature.shell.ui.life

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.*
import com.architectpsg.commandlibrary.feature.shell.ui.*

/** Actual ordinary game consumer; opening only reads saved history, never creates or dispatches. */
@Composable
internal fun OrdinaryLifeScreen(controller: OrdinaryLifeController, widthClass: WidthClass, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsState()
    val rendered = state
    val ui: (() -> Unit) -> Unit = { controller.rendered(rendered, it) }
    LaunchedEffect(controller) { controller.open() }
    DisposableEffect(controller) { onDispose { controller.leave() } }
    Column(modifier.fillMaxSize().testTag("ordinary-life-screen")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = widthClass.gutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeading(stringResource(R.string.ordinary_life_title))
            ActionRow {
                OutlinedButton({ ui(controller::saved) }, enabled = !state.busy, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("ordinary-life-saved")) {
                    Text(stringResource(R.string.ordinary_life_saved))
                }
                OutlinedButton({ ui(controller::newSetup) }, enabled = !state.busy && state.pendingCreation == null,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("ordinary-life-new")) { Text(stringResource(R.string.ordinary_life_new)) }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.problem?.let { Text(stringResource(problemText(it)), color = MaterialTheme.colorScheme.error) }
            if (state.pendingCreation != null) {
                Text(stringResource(R.string.ordinary_life_create_uncertain))
                OutlinedButton({ ui(controller::retryCreation) }, enabled = !state.busy,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("ordinary-life-retry-create")) { Text(stringResource(R.string.ordinary_life_retry_create)) }
            }
            if (state.reloadRequired && state.selectedId != null) {
                Text(stringResource(R.string.ordinary_life_uncertain))
                OutlinedButton({ ui(controller::reload) }, enabled = !state.busy,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("ordinary-life-reload")) { Text(stringResource(R.string.ordinary_life_reload)) }
            }
        }
        when (state.page) {
            OrdinaryLifePage.SAVED -> SavedGames(controller, rendered, widthClass, Modifier.weight(1f))
            OrdinaryLifePage.SETUP -> Setup(controller, rendered, widthClass, Modifier.weight(1f))
            OrdinaryLifePage.TABLE -> Table(controller, rendered, Modifier.weight(1f))
            OrdinaryLifePage.EVENTS -> Events(controller, rendered, widthClass, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SavedGames(controller: OrdinaryLifeController, state: OrdinaryLifeState, width: WidthClass, modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(width.gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionHeading(stringResource(R.string.ordinary_life_saved)) }
        item { OutlinedButton({ controller.rendered(state, controller::refreshHistory) }, enabled = !state.busy,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("ordinary-life-refresh")) { Text(stringResource(R.string.ordinary_life_refresh)) } }
        if (state.historyLoaded && state.history.isEmpty()) item { Text(stringResource(R.string.ordinary_life_empty)) }
        items(state.history, key = { "${it.gameId.source.value}:${it.gameId.raw}" }) { entry ->
            FeaturePanel {
                Text(entry.seats.sortedBy { it.order }.joinToString(" · ") { it.playerName })
                Text(stringResource(R.string.ordinary_life_history_row, timeText(entry.startedAt), entry.revision.value.toString()))
                (entry.result as? Knowledge.Known)?.value?.takeUnless { it.retracted }?.let { result ->
                    result.seatResults.forEach { row -> ResultRow(entry.seats, row) }
                }
                OutlinedButton({ controller.rendered(state) { controller.select(entry.gameId) } }, enabled = !state.busy,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("ordinary-life-open-${entry.gameId.raw}")) { Text(stringResource(R.string.ordinary_life_resume)) }
            }
        }
        if (state.historyCursor != null) item { OutlinedButton({ controller.rendered(state, controller::moreHistory) }, enabled = !state.busy,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text(stringResource(R.string.ordinary_life_more)) } }
    }
}

@Composable
private fun Setup(controller: OrdinaryLifeController, state: OrdinaryLifeState, width: WidthClass, modifier: Modifier) {
    val form = state.setup
    val enabled = !state.busy && state.pendingCreation == null
    val labels = OrdinaryLifeLabels(stringResource(R.string.ordinary_life_life), stringResource(R.string.ordinary_life_damage),
        stringResource(R.string.ordinary_life_tax), stringResource(R.string.ordinary_life_poison), stringResource(R.string.ordinary_life_energy),
        stringResource(R.string.ordinary_life_experience))
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(width.gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionHeading(stringResource(R.string.ordinary_life_setup))
            Text(stringResource(R.string.ordinary_life_setup_help))
            Text(stringResource(R.string.ordinary_life_seats, form.seats.size))
            ActionRow { (1..6).forEach { count ->
                FilterChip(selected = form.seats.size == count, onClick = { controller.seatCount(form, count) }, enabled = enabled,
                    label = { Text(count.toString()) }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("ordinary-life-seat-count-$count"))
            } }
            OutlinedTextField(form.initialLife, { controller.initialLife(form, it) }, enabled = enabled,
                label = { Text(stringResource(R.string.ordinary_life_initial)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("ordinary-life-initial"))
            Text(stringResource(R.string.ordinary_life_initial_help))
            ActionRow { listOf(20, 40).forEach { life ->
                OutlinedButton({ controller.initialLife(form, life.toString()) }, enabled = enabled,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text(life.toString()) }
            } }
        }
        itemsIndexed(form.seats) { index, seat ->
            FeaturePanel {
                OutlinedTextField(seat.name, { controller.playerName(form, index, it) }, enabled = enabled,
                    label = { Text(stringResource(R.string.ordinary_life_player, index + 1)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("ordinary-life-player-$index"))
                Text(stringResource(R.string.ordinary_life_commanders, seat.name, seat.commanders.size))
                ActionRow { (0..2).forEach { count ->
                    FilterChip(selected = seat.commanders.size == count, onClick = { controller.commanderCount(form, index, count) }, enabled = enabled,
                        label = { Text(count.toString()) }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("ordinary-life-commanders-$index-$count"))
                } }
                seat.commanders.forEachIndexed { slot, label ->
                    OutlinedTextField(label, { controller.commanderLabel(form, index, slot, it) }, enabled = enabled,
                        label = { Text(stringResource(R.string.ordinary_life_commander_label, slot + 1)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("ordinary-life-commander-$index-$slot"))
                }
            }
        }
        item {
            Text(stringResource(R.string.ordinary_life_manual_help))
            Button({ controller.create(form, labels) }, enabled = enabled,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("ordinary-life-create")) { Text(stringResource(R.string.ordinary_life_create)) }
        }
    }
}

@Composable
private fun Table(controller: OrdinaryLifeController, state: OrdinaryLifeState, modifier: Modifier) {
    val game = state.snapshot ?: return
    val life = game.settings.counterDefinitions.singleOrNull { it.role == CounterRole.LIFE } ?: return
    val enabled = !state.busy && !state.reloadRequired && state.pendingCreation == null
    var confirming by remember(game.id, game.revision) { mutableStateOf(false) }
    var reopening by remember(game.id, game.revision) { mutableStateOf(false) }
    LifeTable(game, life.id, { controller.action(game, it) }, { controller.undo(game) }, { controller.openEvents(game) },
        modifier, interactionEnabled = enabled && game.status != GameStatus.COMPLETED,
        adjustmentStep = game.settings.adjustmentStep, includeAllCommanderSlots = true, logEnabled = !state.busy, headerContent = {
            Text(stringResource(R.string.ordinary_life_rules_help))
            if (game.status == GameStatus.COMPLETED) {
                Text(stringResource(R.string.ordinary_life_completed))
                (game.result as? Knowledge.Known)?.value?.takeUnless { it.retracted }?.seatResults?.forEach { ResultRow(game.seats, it) }
                OutlinedButton({ reopening = true }, enabled = enabled,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("ordinary-life-reopen")) { Text(stringResource(R.string.ordinary_life_reopen)) }
            } else {
                Text(stringResource(R.string.ordinary_life_step))
                ActionRow { listOf(1, 5).forEach { step ->
                    FilterChip(selected = game.settings.adjustmentStep == step,
                        onClick = { controller.action(game, GameAction.SetAdjustmentStep(step)) }, enabled = enabled,
                        label = { Text(step.toString()) }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("ordinary-life-step-$step"))
                } }
                Row(Modifier.fillMaxWidth()) {
                    Checkbox(game.settings.deductLifeForCommanderDamage, { controller.action(game, GameAction.SetCommanderLifeDeduction(it)) },
                        enabled = enabled, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("ordinary-life-deduct"))
                    Text(stringResource(R.string.ordinary_life_deduct), Modifier.weight(1f))
                }
                Button({ confirming = true }, enabled = enabled,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("ordinary-life-finish")) { Text(stringResource(R.string.ordinary_life_finish)) }
            }
        })
    if (confirming || reopening) ResultDialog(game, reopening, enabled, { confirming = false; reopening = false }) { winners, draw, reason ->
        if (reopening) controller.action(game, GameAction.Reopen(reason)) else controller.finish(game, winners, draw, reason)
        confirming = false; reopening = false
    }
}

@Composable
private fun ResultDialog(game: GameSnapshot, reopen: Boolean, enabled: Boolean, dismiss: () -> Unit,
    confirm: (Set<EntityId>, Boolean, String) -> Unit) {
    var winners by remember(game.id, game.revision) { mutableStateOf(emptySet<EntityId>()) }
    var draw by remember(game.id, game.revision) { mutableStateOf(false) }
    var reason by remember(game.id, game.revision) { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss,
        title = { Text(stringResource(if (reopen) R.string.ordinary_life_reopen else R.string.ordinary_life_finish)) },
        text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!reopen) {
                Text(stringResource(R.string.ordinary_life_finish_help))
                FilterChip(selected = draw, onClick = { draw = !draw; winners = emptySet() }, enabled = enabled,
                    label = { Text(stringResource(R.string.ordinary_life_draw)) }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp))
                game.seats.sortedBy { it.order }.forEach { seat ->
                    FilterChip(selected = seat.id in winners, onClick = { draw = false; winners = if (seat.id in winners) winners - seat.id else winners + seat.id },
                        enabled = enabled, label = { Text(stringResource(R.string.ordinary_life_winner, seat.playerName)) }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp))
                }
            }
            OutlinedTextField(reason, { if (it.length <= 256) reason = it }, label = { Text(stringResource(R.string.ordinary_life_reason)) },
                enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("ordinary-life-result-reason"))
        } },
        confirmButton = { TextButton({ confirm(winners, draw, reason) }, enabled = enabled && reason.isNotBlank() && (reopen || draw || winners.isNotEmpty()),
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("ordinary-life-result-confirm")) { Text(stringResource(R.string.ordinary_life_confirm)) } },
        dismissButton = { TextButton(dismiss, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text(stringResource(R.string.ordinary_life_cancel)) } })
}

@Composable
private fun ResultRow(seats: List<Seat>, row: SeatResult) {
    val name = seats.firstOrNull { it.id == row.seatId }?.playerName ?: stringResource(R.string.ordinary_life_unknown_player)
    Text(stringResource(R.string.ordinary_life_result, name, stringResource(when (row.result) {
        SeatResultKind.WIN -> R.string.ordinary_life_win
        SeatResultKind.LOSS -> R.string.ordinary_life_loss
        SeatResultKind.DRAW -> R.string.ordinary_life_draw_result
    })))
}

@Composable
private fun Events(controller: OrdinaryLifeController, state: OrdinaryLifeState, width: WidthClass, modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(width.gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionHeading(stringResource(R.string.ordinary_life_events))
            OutlinedButton({ controller.rendered(state, controller::table) }, enabled = !state.busy,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text(stringResource(R.string.ordinary_life_table)) }
        }
        if (state.events.isEmpty() && !state.busy) item { Text(stringResource(R.string.ordinary_life_no_events)) }
        items(state.events, key = { it.id }) { event ->
            FeaturePanel {
                Text(stringResource(R.string.ordinary_life_event_row, event.id.toString(), event.revision.value.toString()))
                EventAction(event.action, state.snapshot)
                (event.undoOfEventId as? Knowledge.Known)?.value?.let { Text(stringResource(R.string.ordinary_life_event_undo, it.toString())) }
            }
        }
        if (state.eventsCursor != null) item { OutlinedButton({ controller.rendered(state, controller::moreEvents) }, enabled = !state.busy,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text(stringResource(R.string.ordinary_life_more)) } }
    }
}

@Composable
private fun EventAction(action: GameAction, game: GameSnapshot?) {
    when (action) {
        is GameAction.AdjustCounter -> Text(stringResource(R.string.ordinary_life_event_adjust,
            game?.seats?.firstOrNull { it.id == action.seatId }?.playerName ?: stringResource(R.string.ordinary_life_unknown_player),
            game?.settings?.counterDefinitions?.firstOrNull { it.id == action.definitionId }?.label ?: stringResource(R.string.ordinary_life_unknown_counter), action.delta.toString()))
        is GameAction.Group -> {
            Text(stringResource(R.string.ordinary_life_event_group))
            action.actions.filterIsInstance<GameAction.AdjustCounter>().forEach { EventAction(it, game) }
        }
        else -> Text(stringResource(when (action) {
            is GameAction.Finish, is GameAction.FinishWithOutcomes -> R.string.ordinary_life_event_finish
            is GameAction.Reopen -> R.string.ordinary_life_event_reopen
            is GameAction.SetAdjustmentStep, is GameAction.SetCommanderLifeDeduction -> R.string.ordinary_life_event_preference
            is GameAction.SwitchTurn -> R.string.ordinary_life_event_turn
            else -> R.string.ordinary_life_event_other
        }))
    }
}

@Composable
private fun timeText(value: UtcTimestamp): String {
    return if (value.epochSeconds in (Long.MIN_VALUE / 1000)..(Long.MAX_VALUE / 1000)) formatTimestamp(value)
    else stringResource(R.string.ordinary_life_unknown_time)
}
