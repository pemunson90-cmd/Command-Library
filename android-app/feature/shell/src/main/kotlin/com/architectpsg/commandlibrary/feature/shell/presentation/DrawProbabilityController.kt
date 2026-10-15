package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.CancelToken
import com.architectpsg.commandlibrary.core.model.Outcome
import com.architectpsg.commandlibrary.feature.decks.DrawProbabilityDistribution
import com.architectpsg.commandlibrary.feature.decks.LocalDrawProbabilities
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class DrawProbabilityState(
    val visible: Boolean = false,
    val population: String = "60",
    val successes: String = "0",
    val draws: String = "7",
    val busy: Boolean = false,
    val invalid: Boolean = false,
    val result: DrawProbabilityDistribution? = null,
)

/** Retained, read-only calculator. Edits cancel and fence old work; nothing changes a deck. */
internal class DrawProbabilityController(private val scope: CoroutineScope, private val dispatcher: CoroutineDispatcher) {
    private val mutable = MutableStateFlow(DrawProbabilityState())
    val state: StateFlow<DrawProbabilityState> = mutable.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    private var opened = false

    fun open(population: Long) {
        if (opened) { mutable.value = mutable.value.copy(visible = true); return }
        opened = true
        mutable.value = DrawProbabilityState(visible = true, population = population.toString(), draws = minOf(population, 7L).coerceAtLeast(0).toString())
    }
    fun hide() {
        ++generation; job?.cancel(); job = null
        mutable.value = mutable.value.copy(visible = false, busy = false)
    }
    fun population(value: String) = edit { it.copy(population = value) }
    fun successes(value: String) = edit { it.copy(successes = value) }
    fun draws(value: String) = edit { it.copy(draws = value) }
    private fun edit(change: (DrawProbabilityState) -> DrawProbabilityState) {
        if (!mutable.value.visible) return
        ++generation; job?.cancel(); job = null
        mutable.value = change(mutable.value).copy(busy = false, invalid = false, result = null)
    }
    fun calculate() {
        val state = mutable.value
        if (!state.visible || state.busy) return
        val n = state.population.toIntOrNull(); val k = state.successes.toIntOrNull(); val d = state.draws.toIntOrNull()
        if (n == null || k == null || d == null) { mutable.value = state.copy(invalid = true, result = null); return }
        val owner = ++generation
        mutable.value = state.copy(busy = true, invalid = false, result = null)
        job = scope.launch {
            try {
                val result = withContext(dispatcher) {
                    val current = currentCoroutineContext()
                    LocalDrawProbabilities.calculate(n, k, d, object : CancelToken {
                        override val isCancelled: Boolean get() = !current.isActive
                        override fun throwIfCancelled() { current.ensureActive() }
                    })
                }
                if (generation == owner) mutable.value = mutable.value.copy(
                    result = (result as? Outcome.Success)?.value, invalid = result !is Outcome.Success)
            } finally {
                if (generation == owner) { mutable.value = mutable.value.copy(busy = false); job = null }
            }
        }
    }
}
