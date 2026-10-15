package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.validation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class BoardLimitInput(val minimum: String = "", val maximum: String = "")
internal data class BoardConstraintState(
    val visible: Boolean = false,
    val deck: DeckSnapshot? = null,
    val limits: Map<BoardRole, BoardLimitInput> = BoardRole.entries.associateWith { BoardLimitInput() },
    val busy: Boolean = false,
    val invalid: Boolean = false,
    val result: BoardConstraintAssessment? = null,
)

/** Session-only user rules. Results certify these counts, never official format legality. */
internal class BoardConstraintController(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val validate: (DeckSnapshot, CancelToken) -> Outcome<Unit>,
) {
    private val mutable = MutableStateFlow(BoardConstraintState())
    val state: StateFlow<BoardConstraintState> = mutable.asStateFlow()
    private var owner = 0L
    private var job: Job? = null
    fun open(deck: DeckSnapshot) {
        cancel()
        val previous = mutable.value
        mutable.value = if (previous.deck == deck) previous.copy(visible = true, busy = false)
        else BoardConstraintState(visible = true, deck = deck)
    }
    fun observe(deck: DeckSnapshot?) {
        val previous = mutable.value.deck ?: return
        if (deck != previous) { cancel(); mutable.value = BoardConstraintState() }
    }
    fun hide() { cancel(); mutable.value = mutable.value.copy(visible = false, busy = false) }
    private fun cancel() { ++owner; job?.cancel(); job = null }
    fun edit(board: BoardRole, minimum: Boolean, value: String) {
        val state = mutable.value
        if (!state.visible) return
        cancel()
        if (value.length > 10) { mutable.value = state.copy(busy = false, invalid = true, result = null); return }
        val old = state.limits.getValue(board)
        val input = if (minimum) old.copy(minimum = value) else old.copy(maximum = value)
        mutable.value = state.copy(limits = state.limits + (board to input), busy = false, invalid = false, result = null)
    }
    fun assess() {
        val state = mutable.value
        val deck = state.deck ?: return
        if (!state.visible || state.busy) return
        val constraints = ArrayList<BoardConstraint>()
        for (board in BoardRole.entries) {
            val row = state.limits.getValue(board)
            if (row.minimum.isEmpty() && row.maximum.isEmpty()) continue
            fun count(text: String): Int? = text.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }?.toIntOrNull()
            val low = if (row.minimum.isEmpty()) 0 else count(row.minimum)
            val high = if (row.maximum.isEmpty()) null else count(row.maximum)
            if (low == null || (row.maximum.isNotEmpty() && high == null) || (high != null && high < low)) {
                mutable.value = state.copy(invalid = true, result = null); return
            }
            constraints.add(BoardConstraint(board, low, high, false))
        }
        if (constraints.isEmpty()) { mutable.value = state.copy(invalid = true, result = null); return }
        val ticket = ++owner
        mutable.value = state.copy(busy = true, invalid = false, result = null)
        job = scope.launch {
            try {
                val result = withContext(dispatcher) {
                    val context = currentCoroutineContext()
                    val token = object : CancelToken {
                        override val isCancelled get() = !context.isActive
                        override fun throwIfCancelled() { context.ensureActive() }
                    }
                    when (val checked = validate(deck, token)) {
                        is Outcome.Success -> LocalBoardConstraintChecks.assess(deck, SnapshotList.copyOf(constraints), token)
                        is Outcome.Failure -> checked
                        is Outcome.Unavailable -> checked
                        is Outcome.Unknown -> checked
                    }
                }
                if (owner == ticket) {
                    val value = (result as? Outcome.Success)?.value
                    val valid = value != null && value.deckId == deck.id && value.deckRevision == deck.revision
                    mutable.value = mutable.value.copy(result = value.takeIf { valid }, invalid = !valid)
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (owner == ticket) mutable.value = mutable.value.copy(invalid = true, result = null)
            } finally {
                if (owner == ticket) { mutable.value = mutable.value.copy(busy = false); job = null }
            }
        }
    }
}
