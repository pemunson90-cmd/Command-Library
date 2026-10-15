package com.architectpsg.commandlibrary.feature.shell.ui.decks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.DrawProbabilityController
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

@Composable
internal fun DrawProbabilityDialog(controller: DrawProbabilityController) {
    val state by controller.state.collectAsState()
    if (!state.visible) return
    AlertDialog(onDismissRequest = controller::hide,
        title = { Text(stringResource(R.string.draw_probability_title)) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp).testTag("draw-probability-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text(stringResource(R.string.draw_probability_explanation)) }
                item { OutlinedTextField(state.population, controller::population, label = { Text(stringResource(R.string.draw_probability_population)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("draw-probability-population")) }
                item { OutlinedTextField(state.successes, controller::successes, label = { Text(stringResource(R.string.draw_probability_successes)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("draw-probability-successes")) }
                item { OutlinedTextField(state.draws, controller::draws, label = { Text(stringResource(R.string.draw_probability_draws)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("draw-probability-draws")) }
                if (state.invalid) item { Text(stringResource(R.string.draw_probability_invalid), modifier = Modifier.testTag("draw-probability-invalid")) }
                if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.draw_probability_working)) }
                state.result?.let { result ->
                    item { Text(stringResource(R.string.draw_probability_rounded)) }
                    item {
                        val atLeastOne = result.points.filter { it.successesDrawn >= 1 }.fold(BigInteger.ZERO) { sum, point -> sum + point.ways }
                        Text(stringResource(R.string.draw_probability_at_least_one, percent(atLeastOne, result.totalWays)), modifier = Modifier.testTag("draw-probability-at-least-one"))
                    }
                    items(result.points, key = { it.successesDrawn }) { point ->
                        Text(stringResource(R.string.draw_probability_row, point.successesDrawn, percent(point.ways, result.totalWays)), modifier = Modifier.testTag("draw-probability-result-${point.successesDrawn}"))
                    }
                }
            }
        },
        confirmButton = { TextButton(controller::calculate, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("draw-probability-calculate")) { Text(stringResource(R.string.draw_probability_calculate)) } },
        dismissButton = { TextButton(controller::hide, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.draw_probability_close)) } })
}

private fun percent(ways: BigInteger, total: BigInteger): String = BigDecimal(ways).multiply(BigDecimal(100))
    .divide(BigDecimal(total), 6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
