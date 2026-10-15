package com.architectpsg.commandlibrary.feature.shell.ui.decks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.architectpsg.commandlibrary.core.model.BoardRole
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.BoardConstraintController

@Composable
internal fun BoardConstraintDialog(controller: BoardConstraintController) {
    val state by controller.state.collectAsState()
    if (!state.visible) return
    AlertDialog(onDismissRequest = controller::hide,
        title = { Text(stringResource(R.string.board_checks_title)) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp).testTag("board-checks-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text(stringResource(R.string.board_checks_explanation)) }
                state.deck?.let { deck -> item { Text(deck.name) } }
                for (board in BoardRole.entries) {
                    item(key = board.name) {
                        val input = state.limits.getValue(board)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(boardLabel(board)), style = MaterialTheme.typography.titleSmall)
                            OutlinedTextField(input.minimum, { controller.edit(board, true, it) }, singleLine = true,
                                label = { Text(stringResource(R.string.board_checks_minimum)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth().testTag("board-checks-min-${board.name}"))
                            OutlinedTextField(input.maximum, { controller.edit(board, false, it) }, singleLine = true,
                                label = { Text(stringResource(R.string.board_checks_maximum)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth().testTag("board-checks-max-${board.name}"))
                        }
                    }
                }
                if (state.invalid) item { Text(stringResource(R.string.board_checks_invalid), modifier = Modifier.testTag("board-checks-invalid")) }
                if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.result?.let { result ->
                    item { Text(stringResource(R.string.board_checks_results)) }
                    for (finding in result.findings) item(key = "result-${finding.constraint.board.name}") {
                        Text(stringResource(if (finding.satisfied) R.string.board_checks_pass else R.string.board_checks_fail,
                            stringResource(boardLabel(finding.constraint.board)), finding.quantity.toString()),
                            modifier = Modifier.testTag("board-checks-result-${finding.constraint.board.name}"))
                    }
                }
            }
        },
        confirmButton = { TextButton(controller::assess, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("board-checks-assess")) { Text(stringResource(R.string.board_checks_assess)) } },
        dismissButton = { TextButton(controller::hide, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.board_checks_close)) } })
}
