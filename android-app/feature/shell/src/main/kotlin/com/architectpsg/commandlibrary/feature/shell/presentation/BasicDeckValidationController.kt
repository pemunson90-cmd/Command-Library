package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class BasicDeckValidationState(
    val deck: DeckSnapshot? = null,
    val snapshot: SnapshotReference? = null,
    val format: BasicDeckFormat? = null,
    val busy: Boolean = false,
    val problem: Problem? = null,
    val result: BasicDeckValidationResult? = null,
)

/** A basic assessment always follows explicit format choice and captured read-only context. */
internal class BasicDeckValidationController(
    private val scope: CoroutineScope,
    private val validation: LocalBasicDeckValidation,
    private val dispatcher: CoroutineDispatcher,
    private val current: (DeckSnapshot, SnapshotReference) -> Boolean,
) {
    private val mutable = MutableStateFlow(BasicDeckValidationState())
    val state: StateFlow<BasicDeckValidationState> = mutable.asStateFlow()
    private var ticket = 0L
    private var job: Job? = null
    fun rendered(frame: BasicDeckValidationState, action: () -> Unit) { if (mutable.value === frame) action() }
    fun open(deck: DeckSnapshot, snapshot: SnapshotReference) {
        if (mutable.value.deck != null || !current(deck, snapshot)) return
        mutable.value = BasicDeckValidationState(deck = deck, snapshot = snapshot)
    }
    fun format(value: BasicDeckFormat) {
        if (mutable.value.deck == null) return
        ++ticket; job?.cancel(); job = null
        mutable.update { it.copy(format = value, result = null, problem = null, busy = false) }
    }
    fun assess() {
        val s = mutable.value
        val deck = s.deck ?: return
        val snapshot = s.snapshot ?: return
        val format = s.format ?: return
        if (s.busy) return
        if (!current(deck, snapshot)) {
            mutable.update { it.copy(problem = Problem.STALE_GENERATION, result = null) }; return
        }
        val request = BasicDeckValidationRequest(deck, snapshot, format)
        val call = ++ticket
        mutable.update { it.copy(busy = true, result = null, problem = null) }
        job = scope.launch {
            val result = callPort(dispatcher) { validation.analyze(request, it) }
            if (call != ticket) return@launch
            if (!current(deck, snapshot)) {
                mutable.update { it.copy(busy = false, result = null, problem = Problem.STALE_GENERATION) }; return@launch
            }
            when (result) {
                is PortResult.Failed -> mutable.update { it.copy(busy = false, result = null, problem = result.problem) }
                is PortResult.Ok -> {
                    val r = result.value
                    if (r.deckId != deck.id || r.deckRevision != deck.revision || r.snapshot != snapshot || r.format != format)
                        mutable.update { it.copy(busy = false, result = null, problem = Problem.CORRUPT_DATA) }
                    else mutable.update { it.copy(busy = false, result = r) }
                }
            }
        }
    }
    fun close() { ++ticket; job?.cancel(); job = null; mutable.value = BasicDeckValidationState() }
}
