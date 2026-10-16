package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.common.CoroutineCancelToken
import com.architectpsg.commandlibrary.core.model.*
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal enum class OrdinaryLifePage { SAVED, SETUP, TABLE, EVENTS }
internal data class OrdinaryLifeSeatSetup(val name: String = "", val commanders: List<String> = emptyList())
internal data class OrdinaryLifeSetup(val seats: List<OrdinaryLifeSeatSetup> = List(2) { OrdinaryLifeSeatSetup() }, val initialLife: String = "40")
internal data class OrdinaryLifeLabels(val life: String, val damage: String, val tax: String, val poison: String, val energy: String, val experience: String)
internal data class OrdinaryLifeState(
    val page: OrdinaryLifePage = OrdinaryLifePage.SAVED, val busy: Boolean = false,
    val setup: OrdinaryLifeSetup = OrdinaryLifeSetup(), val snapshot: GameSnapshot? = null,
    val selectedId: GameId? = null, val reloadRequired: Boolean = false, val problem: Problem? = null,
    val pendingCreation: GameCreation? = null, val history: List<GameHistoryEntry> = emptyList(),
    val historyCursor: String? = null, val historyLoaded: Boolean = false,
    val events: List<GameEvent> = emptyList(), val eventsCursor: String? = null,
)

/**
 * Retained owner on the UI dispatcher; port calls/flow production run on [dispatcher].
 * No IO on construction, automatic creation, action replay, timer or legacy watch.
 */
internal class OrdinaryLifeController(
    private val scope: CoroutineScope,
    private val service: PersistedGameService,
    private val dispatcher: CoroutineDispatcher,
    private val nextId: () -> String = { UUID.randomUUID().toString() },
) {
    private val mutable = MutableStateFlow(OrdinaryLifeState())
    val state: StateFlow<OrdinaryLifeState> = mutable.asStateFlow()
    private var observer: Job? = null
    private var selection = 0L
    private var attached = true
    private var reopenAfterCall = false
    private val historyCursors = mutableSetOf<String>()
    private val eventCursors = mutableSetOf<String>()

    /** Compose callbacks capture a real immutable frame, rather than delegating to the latest frame. */
    fun rendered(frame: OrdinaryLifeState, action: () -> Unit) { if (attached && frame === state.value) action() }
    fun open() {
        attached = true
        if (state.value.busy) { reopenAfterCall = true; return }
        if (state.value.snapshot != null && state.value.page in setOf(OrdinaryLifePage.TABLE, OrdinaryLifePage.EVENTS) && observer?.isActive != true) reload()
        else if (!state.value.historyLoaded) refreshHistory()
    }
    /** Composition exit never cancels or replays a possibly committed command. */
    fun leave() { attached = false; reopenAfterCall = false; stopObservation() }
    fun saved() {
        if (!attached || state.value.busy) return
        stopObservation()
        mutable.value = state.value.copy(page = OrdinaryLifePage.SAVED)
        refreshHistory()
    }
    fun newSetup() {
        if (!attached || state.value.busy || state.value.pendingCreation != null) return
        stopObservation()
        mutable.value = state.value.copy(page = OrdinaryLifePage.SETUP, setup = OrdinaryLifeSetup(),
            snapshot = null, selectedId = null, reloadRequired = false, problem = null)
    }
    private fun edit(shown: OrdinaryLifeSetup, change: (OrdinaryLifeSetup) -> OrdinaryLifeSetup) {
        if (!attached || state.value.busy || state.value.page != OrdinaryLifePage.SETUP || state.value.pendingCreation != null || state.value.setup !== shown) return
        mutable.value = state.value.copy(setup = change(shown), problem = null)
    }
    fun seatCount(shown: OrdinaryLifeSetup, count: Int) {
        if (count in 1..6) edit(shown) { it.copy(seats = List(count) { i -> it.seats.getOrNull(i) ?: OrdinaryLifeSeatSetup() }) }
    }
    fun playerName(shown: OrdinaryLifeSetup, index: Int, text: String) {
        if (index !in shown.seats.indices || text.length > 512) return
        edit(shown) { it.copy(seats = it.seats.mapIndexed { i, seat -> if (i == index) seat.copy(name = text) else seat }) }
    }
    fun initialLife(shown: OrdinaryLifeSetup, text: String) { if (text.length <= 32) edit(shown) { it.copy(initialLife = text) } }
    fun commanderCount(shown: OrdinaryLifeSetup, index: Int, count: Int) {
        if (index !in shown.seats.indices || count !in 0..2) return
        edit(shown) { it.copy(seats = it.seats.mapIndexed { i, seat ->
            if (i == index) seat.copy(commanders = List(count) { n -> seat.commanders.getOrNull(n).orEmpty() }) else seat
        }) }
    }
    fun commanderLabel(shown: OrdinaryLifeSetup, index: Int, slot: Int, text: String) {
        if (index !in shown.seats.indices || slot !in shown.seats[index].commanders.indices || text.length > 512) return
        edit(shown) { it.copy(seats = it.seats.mapIndexed { i, seat ->
            if (i == index) seat.copy(commanders = seat.commanders.mapIndexed { n, label -> if (n == slot) text else label }) else seat
        }) }
    }
    fun create(shown: OrdinaryLifeSetup, labels: OrdinaryLifeLabels) {
        if (!attached || state.value.busy || state.value.page != OrdinaryLifePage.SETUP || state.value.setup !== shown || state.value.pendingCreation != null) return
        val creation = try { creation(shown, labels) }
        catch (stopped: CancellationException) { throw stopped }
        catch (_: Exception) { mutable.value = state.value.copy(problem = Problem.INVALID_INPUT); return }
        mutable.value = state.value.copy(pendingCreation = creation)
        createFrozen(creation)
    }
    fun retryCreation() {
        if (!attached || state.value.busy) return
        state.value.pendingCreation?.let(::createFrozen)
    }
    private fun createFrozen(creation: GameCreation) {
        stopObservation()
        launch(mutation = true) {
            when (val result = callPort(dispatcher) { service.create(creation, it) }) {
                is PortResult.Ok -> {
                    val game = result.value
                    // The service authenticates this exact creation operation, then returns its CURRENT
                    // checkpoint. A retry may legitimately include later settings, seats or a result.
                    if (game.version != 1) refuse(Problem.CORRUPT_DATA)
                    else {
                        mutable.value = state.value.copy(page = OrdinaryLifePage.TABLE,
                            snapshot = state.value.snapshot?.takeIf { it.id == game.id }, selectedId = game.id,
                            events = emptyList(), eventsCursor = null)
                        if (adopt(game)) {
                            mutable.value = state.value.copy(pendingCreation = null, problem = null, reloadRequired = false)
                            observe(game.id)
                        }
                    }
                }
                is PortResult.Failed -> refuse(result.problem)
            }
        }
    }
    fun refreshHistory() { history(null) }
    fun moreHistory() { state.value.historyCursor?.let(::history) }
    private fun history(cursor: String?) {
        if (!attached || state.value.busy) return
        if (cursor == null) historyCursors.clear()
        else if (!historyCursors.add(cursor)) { mutable.value = state.value.copy(problem = Problem.CORRUPT_DATA, historyCursor = null); return }
        launch {
            when (val result = callPort(dispatcher) { service.history(GameHistoryFilter(), PageRequest(20, cursor), it) }) {
                is PortResult.Ok -> {
                    val rows = (if (cursor == null) emptyList() else state.value.history) + result.value.games.items
                    if (rows.map { it.gameId }.distinct().size != rows.size || result.value.games.nextCursor == cursor && cursor != null)
                        mutable.value = state.value.copy(problem = Problem.CORRUPT_DATA, historyCursor = null)
                    else mutable.value = state.value.copy(history = rows, historyCursor = result.value.games.nextCursor, historyLoaded = true, problem = null)
                }
                is PortResult.Failed -> mutable.value = state.value.copy(problem = result.problem, historyCursor = null)
            }
        }
    }
    fun select(id: GameId) {
        if (!attached || state.value.busy) return
        stopObservation()
        // A history selection is not acknowledgment of pendingCreation; keep that exact operation
        // frozen and table mutations fenced until its explicit retry is acknowledged.
        mutable.value = state.value.copy(page = OrdinaryLifePage.TABLE, selectedId = id, snapshot = null, events = emptyList(), eventsCursor = null)
        launch(mutation = true) {
            when (val result = callPort(dispatcher) { service.resume(id, it) }) {
                is PortResult.Ok -> if (result.value.id != id) refuse(Problem.CORRUPT_DATA) else {
                    if (adopt(result.value)) { mutable.value = state.value.copy(reloadRequired = false, problem = null); observe(id) }
                }
                is PortResult.Failed -> refuse(result.problem)
            }
        }
    }
    fun reload() {
        if (!attached || state.value.busy) return
        val id = state.value.selectedId ?: return
        stopObservation()
        launch {
            when (val result = callPort(dispatcher) { service.read(id, it) }) {
                is PortResult.Ok -> {
                    val old = state.value.snapshot
                    if (result.value.id != id || old != null && result.value.revision.value < old.revision.value) refuse(Problem.CORRUPT_DATA)
                    else if (adopt(result.value)) { mutable.value = state.value.copy(reloadRequired = false, problem = null); observe(id) }
                }
                is PortResult.Failed -> refuse(result.problem)
            }
        }
    }
    private fun ready(frame: GameSnapshot, completed: Boolean = false): Boolean = attached && !state.value.busy && !state.value.reloadRequired && state.value.pendingCreation == null &&
        state.value.snapshot === frame && state.value.selectedId == frame.id &&
        state.value.page == OrdinaryLifePage.TABLE && (completed || frame.status != GameStatus.COMPLETED)
    fun action(frame: GameSnapshot, action: GameAction) {
        if (!ready(frame, action is GameAction.Reopen)) return
        val request = when (action) {
            is GameAction.AdjustCounter -> {
                val definition = frame.settings.counterDefinitions.singleOrNull { it.id == action.definitionId } ?: return
                if (action.delta == 0 || action.delta == Int.MIN_VALUE) return
                if (definition.role == CounterRole.COMMANDER_DAMAGE && frame.settings.deductLifeForCommanderDamage) {
                    val life = frame.settings.counterDefinitions.singleOrNull { it.role == CounterRole.LIFE } ?: return
                    val group = freshIdentity() ?: return
                    GameAction.Group(SnapshotList.of(action, GameAction.AdjustCounter(action.seatId, life.id, null, -action.delta)), group)
                } else action
            }
            is GameAction.SetAdjustmentStep -> if (action.step == 1 || action.step == 5) action else return
            is GameAction.Reopen -> if (text(action.reason)) action else return
            is GameAction.SetCommanderLifeDeduction, is GameAction.SwitchTurn -> action
            else -> return
        }
        dispatch(frame, request)
    }
    fun finish(frame: GameSnapshot, winnerIds: Set<EntityId>, draw: Boolean, reason: String) {
        if (!ready(frame) || !text(reason) || winnerIds.any { id -> frame.seats.none { it.id == id } } ||
            draw && winnerIds.isNotEmpty() || !draw && winnerIds.isEmpty()) return
        val outcomes = frame.seats.sortedBy { it.order }.map {
            SeatResult(it.id, if (draw) SeatResultKind.DRAW else if (it.id in winnerIds) SeatResultKind.WIN else SeatResultKind.LOSS, null)
        }
        dispatch(frame, GameAction.FinishWithOutcomes(SnapshotList.copyOf(outcomes), reason))
    }
    private fun dispatch(frame: GameSnapshot, action: GameAction) {
        launch(mutation = true) {
            when (val result = callPort(dispatcher) { service.dispatch(frame.id, action, frame.revision, it) }) {
                is PortResult.Ok -> if (result.value.id != frame.id || frame.revision.value == Long.MAX_VALUE || result.value.revision.value != frame.revision.value + 1)
                    refuse(Problem.CORRUPT_DATA) else adopt(result.value)
                is PortResult.Failed -> refuse(result.problem)
            }
        }
    }
    fun undo(frame: GameSnapshot) {
        if (!ready(frame)) return
        launch(mutation = true) {
            when (val result = callPort(dispatcher) { service.undo(frame.id, frame.revision, it) }) {
                is PortResult.Ok -> if (result.value.id != frame.id || frame.revision.value == Long.MAX_VALUE || result.value.revision.value != frame.revision.value + 1)
                    refuse(Problem.CORRUPT_DATA) else adopt(result.value)
                is PortResult.Failed -> refuse(result.problem)
            }
        }
    }
    fun openEvents(frame: GameSnapshot) {
        if (!attached || state.value.busy || state.value.page != OrdinaryLifePage.TABLE || state.value.snapshot !== frame) return
        mutable.value = state.value.copy(page = OrdinaryLifePage.EVENTS, events = emptyList(), eventsCursor = null)
        eventCursors.clear(); events(null)
    }
    fun table() { if (attached && !state.value.busy && state.value.snapshot != null) mutable.value = state.value.copy(page = OrdinaryLifePage.TABLE) }
    fun moreEvents() { state.value.eventsCursor?.let(::events) }
    private fun events(cursor: String?) {
        if (!attached || state.value.busy) return
        val id = state.value.selectedId ?: return
        if (cursor != null && !eventCursors.add(cursor)) { mutable.value = state.value.copy(problem = Problem.CORRUPT_DATA, eventsCursor = null); return }
        launch {
            when (val result = callPort(dispatcher) { service.events(id, PageRequest(20, cursor), it) }) {
                is PortResult.Ok -> {
                    val rows = (if (cursor == null) emptyList() else state.value.events) + result.value.items
                    if (rows.any { it.gameId != id } || rows.map { it.id }.distinct().size != rows.size ||
                        result.value.nextCursor == cursor && cursor != null)
                        mutable.value = state.value.copy(problem = Problem.CORRUPT_DATA, eventsCursor = null)
                    else mutable.value = state.value.copy(events = rows, eventsCursor = result.value.nextCursor, problem = null)
                }
                is PortResult.Failed -> mutable.value = state.value.copy(problem = result.problem, eventsCursor = null)
            }
        }
    }
    private fun adopt(game: GameSnapshot): Boolean {
        val old = state.value.snapshot
        if (old == null || old.id != game.id || game.revision.value > old.revision.value) mutable.value = state.value.copy(snapshot = game)
        else if (old.revision == game.revision && old != game) { refuse(Problem.CORRUPT_DATA); return false }
        return true
    }
    private fun refuse(problem: Problem) { mutable.value = state.value.copy(problem = problem, reloadRequired = true) }
    private fun stopObservation() { ++selection; observer?.cancel(); observer = null }
    private fun observe(id: GameId) {
        if (!attached) return
        val ticket = selection
        observer?.cancel()
        observer = scope.launch {
            try {
                flow { emitAll(service.observe(id, CoroutineCancelToken(currentCoroutineContext()))) }.flowOn(dispatcher).collect { result ->
                    if (ticket != selection || state.value.selectedId != id) return@collect
                    when (result) {
                        is Outcome.Success -> if (result.value.id != id) refuse(Problem.CORRUPT_DATA) else adopt(result.value)
                        is Outcome.Failure -> refuse(result.error.code.toProblem())
                        is Outcome.Unavailable -> refuse(result.reason.toProblem())
                        is Outcome.Unknown -> refuse(Problem.UNKNOWN_RESULT)
                    }
                }
            } catch (stopped: CancellationException) { throw stopped }
            catch (_: Exception) { if (ticket == selection) refuse(Problem.UNEXPECTED) }
        }
    }
    private fun launch(mutation: Boolean = false, call: suspend () -> Unit) {
        if (!attached || state.value.busy) return
        mutable.value = state.value.copy(busy = true, problem = null)
        scope.launch {
            try { call() }
            catch (stopped: CancellationException) { if (mutation) refuse(Problem.UNKNOWN_RESULT); throw stopped }
            finally {
                mutable.value = state.value.copy(busy = false)
                if (reopenAfterCall) {
                    reopenAfterCall = false
                    // A busy composition reentry requested attachment. Once the call settles,
                    // only the normal read/typed-observe opening path runs, never a command.
                    if (attached && currentCoroutineContext().isActive) open()
                }
            }
        }
    }
    private fun freshIdentity(): EntityId? = try { EntityId(nextId()).also { require(text(it.value)) } }
    catch (stopped: CancellationException) { throw stopped }
    catch (_: Exception) { mutable.value = state.value.copy(problem = Problem.INVALID_INPUT); null }
    private fun creation(setup: OrdinaryLifeSetup, labels: OrdinaryLifeLabels): GameCreation {
        require(setup.seats.size in 1..6)
        val life = requireNotNull(setup.initialLife.toIntOrNull())
        require(setup.seats.all { text(it.name) && it.commanders.size <= 2 && it.commanders.all(::text) })
        require(listOf(labels.life, labels.damage, labels.tax, labels.poison, labels.energy, labels.experience).all(::text))
        val used = HashSet<EntityId>()
        fun id(): EntityId = requireNotNull(freshIdentity()).also { require(used.add(it)) }
        val operation = id()
        val definitions = listOf(
            CounterDefinition(id(), labels.life, null, null, life, CounterLossPolicy.AtOrBelow(0), false, CounterRole.LIFE),
            CounterDefinition(id(), labels.damage, 0, null, 0, CounterLossPolicy.AtOrAbove(21), false, CounterRole.COMMANDER_DAMAGE),
            CounterDefinition(id(), labels.tax, 0, null, 0, CounterLossPolicy.None, false, CounterRole.COMMANDER_TAX),
            CounterDefinition(id(), labels.poison, 0, null, 0, CounterLossPolicy.AtOrAbove(10), false, CounterRole.OTHER, preset = CounterPreset.POISON),
            CounterDefinition(id(), labels.energy, 0, null, 0, CounterLossPolicy.None, false, CounterRole.OTHER, preset = CounterPreset.ENERGY),
            CounterDefinition(id(), labels.experience, 0, null, 0, CounterLossPolicy.None, false, CounterRole.OTHER, preset = CounterPreset.EXPERIENCE))
        val seats = setup.seats.mapIndexed { index, seat ->
            Seat(id(), seat.name, null, Knowledge.Absent("Not supplied"), index, SnapshotList.copyOf(seat.commanders.map { label ->
                CommanderSlot(id(), CardIdentity(Knowledge.Absent("Manual label"), Knowledge.Absent("Manual label"), null,
                    Knowledge.Known(label), LanguageTag("und")), label)
            }))
        }
        val settings = GameSettings(Knowledge.Absent("Not supplied"), life, SnapshotList.copyOf(definitions), SnapshotList.empty(),
            SeatClockMode.NONE, 0, adjustmentStep = 1, elapsedTimers = ElapsedTimerSettings(enabled = false))
        return GameCreation(operation, settings, SnapshotList.copyOf(seats), SeatClockState(SeatClockMode.NONE, SnapshotList.empty(),
            null, null, true, ClockZeroPolicy.ALERT, ClockUndoPolicy.KEEP_ELAPSED, SnapshotList.empty()))
    }
    private fun text(value: String): Boolean = try {
        value.isNotBlank() && value.length <= 256 && value.none { Character.isISOControl(it) } &&
            Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(value)).remaining() <= 256
    } catch (_: Exception) { false }
}
