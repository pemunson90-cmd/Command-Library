package com.architectpsg.commandlibrary.feature.shell.ui.life

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.ui.*

/** Stateless renderer only. The owner explicitly identifies life; labels/initial values never
 * imply a counter role. No game service or runtime destination is provided by this component.
 */
@Composable
internal fun LifeTable(
    snapshot: GameSnapshot,
    lifeDefinitionId: EntityId,
    onAction: (GameAction) -> Unit,
    onUndo: () -> Unit,
    onOpenLog: () -> Unit,
    modifier: Modifier = Modifier,
    interactionEnabled: Boolean = true,
    adjustmentStep: Int = 1,
    includeAllCommanderSlots: Boolean = false,
    headerContent: @Composable () -> Unit = {},
    logEnabled: Boolean = true,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = WidthClass.of(maxWidth)
        // Large text needs a full-width seat even in a nominally unfolded window.
        val columns = if (maxWidth >= 840.dp && LocalDensity.current.fontScale < 1.5f) 2 else 1
        val seats = snapshot.seats.sortedBy { it.order }
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = width.gutter),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        ) {
            item {
                FeaturePanel {
                    SectionHeading(stringResource(R.string.life_title), style = MaterialTheme.typography.headlineMedium)
                    ActionRow {
                        ReadOnlyBadge(stringResource(when (snapshot.status) {
                            GameStatus.ACTIVE -> R.string.life_status_active
                            GameStatus.PAUSED -> R.string.life_status_paused
                            GameStatus.COMPLETED -> R.string.life_status_completed
                        }), stringResource(R.string.life_turn, snapshot.turnNumber.toString()))
                        OutlinedButton(onClick = onUndo, enabled = interactionEnabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.life_undo)) }
                        OutlinedButton(onClick = onOpenLog, enabled = logEnabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.life_log)) }
                        if (snapshot.clock.mode != SeatClockMode.NONE) {
                            OutlinedButton(onClick = { onAction(GameAction.ClockPause(!snapshot.clock.paused)) }, enabled = interactionEnabled, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(if (snapshot.clock.paused) R.string.life_clock_resume else R.string.life_clock_pause))
                            }
                        }
                    }
                    headerContent()
                }
            }
            if (seats.isEmpty()) item { Text(stringResource(R.string.life_no_seats), style = MaterialTheme.typography.bodyLarge) }
            items(seats.chunked(columns), key = { it.first().id.value }) { group ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    group.forEach { seat ->
                        FeaturePanel(Modifier.weight(1f), accent = snapshot.activeSeatId == seat.id) {
                            SectionHeading(seat.playerName, style = MaterialTheme.typography.titleLarge)
                            if (snapshot.activeSeatId == seat.id) Text(stringResource(R.string.life_active_seat), style = MaterialTheme.typography.bodyMedium)
                            snapshot.clock.seats.firstOrNull { it.seatId == seat.id }?.let {
                                Text(stringResource(R.string.life_clock, it.remaining.toString()), style = MaterialTheme.typography.bodyMedium)
                            }
                            val recorded = snapshot.counters.filter { it.seatId == seat.id }
                            val missing = if (includeAllCommanderSlots) snapshot.settings.counterDefinitions.filter { it.role == CounterRole.COMMANDER_DAMAGE }.flatMap { definition ->
                                val siblings = recorded.filter { it.definitionId == definition.id }
                                val hidden = siblings.isNotEmpty() && siblings.all { it.hidden }
                                seats.flatMap { it.commanders }.filter { slot -> recorded.none { it.definitionId == definition.id && it.commanderSlotId == slot.id } }
                                    .map { CounterState(definition.id, seat.id, it.id, definition.initial, hidden) }
                            } else emptyList()
                            val counters = (recorded + missing).filter { !it.hidden }.sortedBy { if (it.definitionId == lifeDefinitionId && it.commanderSlotId == null) 0 else 1 }
                            counters.forEach { counter ->
                                val definition = snapshot.settings.counterDefinitions.firstOrNull { it.id == counter.definitionId }
                                val sourceSlotId = counter.commanderSlotId
                                val source = snapshot.seats.firstOrNull { owner -> owner.commanders.any { it.id == sourceSlotId } }
                                val commander = source?.commanders?.firstOrNull { it.id == sourceSlotId }
                                val definitionLabel = definition?.label ?: counter.definitionId.value
                                val label = when {
                                    commander != null && source != null -> stringResource(R.string.life_commander_counter, definitionLabel, source.playerName, commander.label, (source.commanders.indexOfFirst { it.id == commander.id } + 1).toString())
                                    sourceSlotId != null -> stringResource(R.string.life_commander_unknown_counter, definitionLabel)
                                    else -> definitionLabel
                                }
                                val minimum = definition?.minimum
                                val maximum = definition?.maximum
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium)
                                    Text(counter.value.toString(), style = MaterialTheme.typography.headlineMedium.copy(fontSize = if (counter.definitionId == lifeDefinitionId) 48.sp else 28.sp, lineHeight = if (counter.definitionId == lifeDefinitionId) 56.sp else 36.sp, fontFeatureSettings = "tnum"))
                                    ActionRow {
                                        val decrease = if (adjustmentStep == 1) stringResource(R.string.life_decrease, label, seat.playerName)
                                            else stringResource(R.string.ordinary_life_decrease_step, label, seat.playerName, adjustmentStep)
                                        val increase = if (adjustmentStep == 1) stringResource(R.string.life_increase, label, seat.playerName)
                                            else stringResource(R.string.ordinary_life_increase_step, label, seat.playerName, adjustmentStep)
                                        OutlinedButton(
                                            onClick = { onAction(GameAction.AdjustCounter(seat.id, counter.definitionId, counter.commanderSlotId, -adjustmentStep)) },
                                            enabled = interactionEnabled && adjustmentStep > 0 && snapshot.status != GameStatus.COMPLETED &&
                                                counter.value.toLong() - adjustmentStep >= (minimum ?: Int.MIN_VALUE).toLong(),
                                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = decrease },
                                        ) { Text(stringResource(R.string.life_minus)) }
                                        Button(
                                            onClick = { onAction(GameAction.AdjustCounter(seat.id, counter.definitionId, counter.commanderSlotId, adjustmentStep)) },
                                            enabled = interactionEnabled && adjustmentStep > 0 && snapshot.status != GameStatus.COMPLETED &&
                                                counter.value.toLong() + adjustmentStep <= (maximum ?: Int.MAX_VALUE).toLong(),
                                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = increase },
                                        ) { Text(stringResource(R.string.life_plus)) }
                                    }
                                }
                            }
                            if (snapshot.activeSeatId != seat.id && snapshot.status != GameStatus.COMPLETED) {
                                OutlinedButton(onClick = { onAction(GameAction.SwitchTurn(seat.id)) }, enabled = interactionEnabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.life_switch, seat.playerName)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
