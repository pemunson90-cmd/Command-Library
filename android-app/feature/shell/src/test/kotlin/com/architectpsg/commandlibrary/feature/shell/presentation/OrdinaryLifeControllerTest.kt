@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration

/** Synthetic faithful persisted-port oracles, not real SQLite/engine/app acceptance. */
class OrdinaryLifeControllerTest {
    private val labels = OrdinaryLifeLabels("Life", "Damage", "Casts", "Poison", "Energy", "Experience")
    private class Service : PersistedGameService {
        val games = linkedMapOf<GameId, GameSnapshot>()
        val creations = mutableListOf<GameCreation>()
        val receipts = mutableMapOf<EntityId, Pair<GameCreation, GameId>>()
        val actions = mutableListOf<Pair<GameAction, Revision>>()
        val logs = mutableMapOf<GameId, MutableList<GameEvent>>()
        val undos = mutableMapOf<GameId, MutableList<GameSnapshot>>()
        val streams = mutableMapOf<GameId, MutableSharedFlow<Outcome<GameSnapshot>>>()
        val historyRequests = mutableListOf<PageRequest>()
        val eventRequests = mutableListOf<PageRequest>()
        var reads = 0; var resumes = 0; var undoCalls = 0; var watches = 0
        var createFault: Outcome<GameSnapshot>? = null
        var dispatchFault: Outcome<GameSnapshot>? = null
        var commitBeforeFault = false
        var createGate: CompletableDeferred<Unit>? = null
        var ackGate: CompletableDeferred<Unit>? = null
        var cancelCreate = false
        var readFault: Outcome<GameSnapshot>? = null
        private fun missing(): Outcome.Unavailable = Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Fixture missing")
        private fun stale(): Outcome.Failure = Outcome.Failure(DomainFailure(FailureCode.STALE_REVISION, "Fixture stale", SnapshotList.empty()))
        fun publish(game: GameSnapshot) { games[game.id] = game; stream(game.id).tryEmit(Outcome.Success(game)) }
        fun stream(id: GameId) = streams.getOrPut(id) { MutableSharedFlow(replay = 1, extraBufferCapacity = 20) }
        override suspend fun create(input: GameCreation, cancel: CancelToken): Outcome<GameSnapshot> {
            creations += input; createGate?.await(); cancel.throwIfCancelled()
            receipts[input.operationId]?.let { assertEquals(it.first, input); return games[it.second]?.let { current -> Outcome.Success(current) } ?: missing() }
            if (createFault != null && !commitBeforeFault) return createFault!!
            val id = GameId(ProviderId("fixture"), "game-${games.size + 1}")
            val counters = input.settings.counterDefinitions.flatMap { definition -> input.seats.flatMap { seat ->
                val slots: List<EntityId?> = when (definition.role) {
                    CounterRole.COMMANDER_DAMAGE -> input.seats.filter { it.id != seat.id }.flatMap { it.commanders.map { slot -> slot.id } }
                    CounterRole.COMMANDER_TAX -> seat.commanders.map { it.id }
                    else -> listOf(null)
                }
                slots.map { CounterState(definition.id, seat.id, it, definition.initial, false) }
            } }
            val game = GameSnapshot(id, 1, Revision(0), input.settings, GameStatus.PAUSED, UtcTimestamp(10),
                Knowledge.Absent("Not ended"), input.seats, SnapshotList.copyOf(counters), null, 0, input.clock,
                SnapshotList.empty(), Knowledge.Absent("No event"), Knowledge.Absent("No result"),
                elapsed = ElapsedTimerState(Duration.ZERO, Duration.ZERO, true, null))
            receipts[input.operationId] = input to id; publish(game)
            if (cancelCreate) throw CancellationException("Synthetic unrequested lost creation acknowledgment")
            return createFault ?: Outcome.Success(game)
        }
        override suspend fun read(id: GameId, cancel: CancelToken): Outcome<GameSnapshot> { reads++; return readFault ?: games[id]?.let { Outcome.Success(it) } ?: missing() }
        override suspend fun resume(id: GameId, cancel: CancelToken): Outcome<GameSnapshot> { resumes++; return games[id]?.let { Outcome.Success(it) } ?: missing() }
        override fun observe(id: GameId, cancel: CancelToken): Flow<Outcome<GameSnapshot>> { watches++; return stream(id).onStart { games[id]?.let { stream(id).tryEmit(Outcome.Success(it)) } } }
        override fun watch(id: GameId): Flow<GameSnapshot> = error("Legacy watch is forbidden")
        override suspend fun undo(id: GameId, cancel: CancelToken): Outcome<GameSnapshot> = error("Unconditional undo is forbidden")
        override suspend fun dispatch(id: GameId, action: GameAction, expected: Revision, cancel: CancelToken): Outcome<GameSnapshot> {
            actions += action to expected
            val old = games[id] ?: return missing()
            if (old.revision != expected) return stale()
            if (dispatchFault != null && !commitBeforeFault) return dispatchFault!!
            val revision = old.revision.next()
            val changed = apply(old, action).copy(revision = revision, lastEventId = Knowledge.Known(revision.value))
            undos.getOrPut(id) { mutableListOf() } += old
            logs.getOrPut(id) { mutableListOf() } += GameEvent(revision.value, id,
                (action as? GameAction.Group)?.groupId ?: EntityId("event-${revision.value}"), action, UtcTimestamp(20),
                Knowledge.Absent("Not undo"), revision)
            publish(changed); ackGate?.await(); cancel.throwIfCancelled()
            return dispatchFault ?: Outcome.Success(changed)
        }
        private fun apply(game: GameSnapshot, action: GameAction): GameSnapshot = when (action) {
            is GameAction.AdjustCounter -> {
                val definition = game.settings.counterDefinitions.single { it.id == action.definitionId }
                val old = game.counters.singleOrNull { it.seatId == action.seatId && it.definitionId == action.definitionId && it.commanderSlotId == action.commanderSlotId }
                    ?: CounterState(action.definitionId, action.seatId, action.commanderSlotId, definition.initial, false)
                val value = old.value.toLong() + action.delta
                require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
                require(definition.minimum == null || value >= definition.minimum!!)
                require(definition.maximum == null || value <= definition.maximum!!)
                val next = old.copy(value = value.toInt())
                game.copy(counters = SnapshotList.copyOf(game.counters.filterNot { it.seatId == old.seatId && it.definitionId == old.definitionId && it.commanderSlotId == old.commanderSlotId } + next))
            }
            is GameAction.Group -> {
                require(action.actions.isNotEmpty() && action.actions.none { it is GameAction.Group })
                action.actions.fold(game, ::apply)
            }
            is GameAction.SetAdjustmentStep -> game.copy(settings = game.settings.copy(adjustmentStep = action.step))
            is GameAction.SetCommanderLifeDeduction -> game.copy(settings = game.settings.copy(deductLifeForCommanderDamage = action.enabled))
            is GameAction.SwitchTurn -> game.copy(activeSeatId = action.seatId, turnNumber = game.turnNumber + 1)
            is GameAction.FinishWithOutcomes -> {
                val result = GameResult(EntityId("result"), game.id, SnapshotList.copyOf(action.seatResults.filter { it.result == SeatResultKind.WIN }.map { it.seatId }),
                    UtcTimestamp(20), action.reason, game.revision.next(), false, action.seatResults)
                game.copy(status = GameStatus.COMPLETED, result = Knowledge.Known(result), endedAt = Knowledge.Known(UtcTimestamp(20)))
            }
            is GameAction.Reopen -> game.copy(status = GameStatus.PAUSED,
                result = Knowledge.Known((game.result as Knowledge.Known).value.copy(retracted = true, revision = game.revision.next())), endedAt = Knowledge.Absent("Reopened"))
            else -> error("Unexpected action $action")
        }
        override suspend fun undo(id: GameId, expected: Revision, cancel: CancelToken): Outcome<GameSnapshot> {
            undoCalls++
            val old = games[id] ?: return missing()
            if (old.revision != expected) return stale()
            val restored = undos[id]!!.removeAt(undos[id]!!.lastIndex).copy(revision = old.revision.next())
            publish(restored); ackGate?.await(); cancel.throwIfCancelled(); return Outcome.Success(restored)
        }
        override suspend fun history(filter: GameHistoryFilter, page: PageRequest, cancel: CancelToken): Outcome<GameHistoryPage> {
            historyRequests += page
            val values = games.values.toList().reversed().map { GameHistoryEntry(it.id, it.revision, it.startedAt, it.endedAt, it.seats, it.result) }
            val offset = page.cursor?.toInt() ?: 0
            return Outcome.Success(GameHistoryPage(slicePage(values, offset, page.limit), values.size.toLong()))
        }
        override suspend fun events(id: GameId, page: PageRequest, cancel: CancelToken): Outcome<Page<GameEvent>> {
            eventRequests += page
            return Outcome.Success(slicePage(logs[id].orEmpty(), page.cursor?.toInt() ?: 0, page.limit))
        }
        private fun <T> slicePage(values: List<T>, offset: Int, count: Int) = Page(SnapshotList.copyOf(values.drop(offset).take(count)),
            if (offset + count < values.size) (offset + count).toString() else null, SnapshotReference(ProviderId("fixture"), null, Revision(1), null))
    }
    private inner class Fixture(val scope: TestScope, val service: Service = Service(), idFactory: (() -> String)? = null) {
        var idCount = 0
        val controller = OrdinaryLifeController(scope.backgroundScope, service, StandardTestDispatcher(scope.testScheduler), idFactory ?: { "id-${++this.idCount}" })
        fun setup(count: Int = 2, commanders: Int = 0, life: String = "40") {
            controller.newSetup(); controller.seatCount(controller.state.value.setup, count)
            controller.initialLife(controller.state.value.setup, life)
            repeat(count) { i ->
                controller.playerName(controller.state.value.setup, i, "Player $i")
                controller.commanderCount(controller.state.value.setup, i, commanders)
                repeat(commanders) { n -> controller.commanderLabel(controller.state.value.setup, i, n, "Commander $i/$n") }
            }
        }
        fun create(count: Int = 2, commanders: Int = 0, life: String = "40"): GameSnapshot {
            setup(count, commanders, life); controller.create(controller.state.value.setup, labels); scope.runCurrent()
            return controller.state.value.snapshot!!
        }
        fun frame() = controller.state.value.snapshot!!
        fun life(game: GameSnapshot = frame()) = game.settings.counterDefinitions.single { it.role == CounterRole.LIFE }.id
        fun adjust(delta: Int = -1) { val frame = frame(); controller.action(frame, GameAction.AdjustCounter(frame.seats[0].id, life(frame), null, delta)); scope.runCurrent() }
    }
    @Test fun constructorOpeningAndSetupNeverAutomaticallyCreateOrDispatch() = runTest {
        val f = Fixture(this)
        assertTrue(f.service.creations.isEmpty()); assertTrue(f.service.historyRequests.isEmpty())
        f.controller.open(); runCurrent(); f.setup()
        assertEquals(1, f.service.historyRequests.size); assertTrue(f.service.creations.isEmpty()); assertTrue(f.service.actions.isEmpty())
    }
    @Test fun oneThroughSixSeatsCustomLifeFreshUniqueIdsAndNoVariantOrTimerPrerequisite() = runTest {
        for (count in 1..6) {
            val f = Fixture(this); f.create(count, 2, if (count == 1) "20" else "37")
            val input = f.service.creations.single()
            assertEquals(count, input.seats.size); assertEquals((0 until count).toList(), input.seats.map { it.order })
            val all = listOf(input.operationId) + input.settings.counterDefinitions.map { it.id } + input.seats.map { it.id } + input.seats.flatMap { it.commanders.map { c -> c.id } }
            assertEquals(all.size, all.toSet().size); assertTrue(input.settings.variantDefinitions.isEmpty())
            assertEquals(SeatClockMode.NONE, input.clock.mode); assertFalse(input.settings.elapsedTimers.enabled)
            assertEquals(1, input.settings.counterDefinitions.count { it.role == CounterRole.LIFE })
            assertEquals(input.settings.initialLife, input.settings.counterDefinitions.single { it.role == CounterRole.LIFE }.initial)
            assertEquals(setOf(CounterPreset.POISON, CounterPreset.ENERGY, CounterPreset.EXPERIENCE), input.settings.counterDefinitions.filter { it.role == CounterRole.OTHER }.map { it.preset }.toSet())
            assertTrue(input.seats.all { it.profileId == null && it.deck is Knowledge.Absent && it.commanders.size == 2 })
            assertTrue(input.seats.flatMap { it.commanders }.all { it.identity.oracleId is Knowledge.Absent && it.label.isNotBlank() })
        }
    }
    @Test fun invalidSetupNeverCallsCreateAndPreservesRawInput() = runTest {
        for (life in listOf("", "not a number", "2147483648")) {
            val f = Fixture(this); f.setup(life = life); f.controller.create(f.controller.state.value.setup, labels); runCurrent()
            assertTrue(f.service.creations.isEmpty()); assertEquals(life, f.controller.state.value.setup.initialLife)
            assertEquals(Problem.INVALID_INPUT, f.controller.state.value.problem)
        }
        val f = Fixture(this); f.controller.newSetup(); f.controller.create(f.controller.state.value.setup, labels); runCurrent()
        assertTrue(f.service.creations.isEmpty())
        f.setup(commanders = 1); f.controller.commanderLabel(f.controller.state.value.setup, 0, 0, "")
        f.controller.create(f.controller.state.value.setup, labels); runCurrent(); assertTrue(f.service.creations.isEmpty())
    }
    @Test fun malformedTextDuplicateIdsAndOutOfRangeSeatChangesCannotCreate() = runTest {
        val duplicate = Fixture(this, idFactory = { "same-id" }); duplicate.setup()
        duplicate.controller.create(duplicate.controller.state.value.setup, labels); runCurrent(); assertTrue(duplicate.service.creations.isEmpty())
        val f = Fixture(this); f.setup()
        val before = f.controller.state.value.setup
        f.controller.seatCount(before, 7); f.controller.seatCount(before, 0); assertSame(before, f.controller.state.value.setup)
        f.controller.playerName(before, 0, "bad\uD800")
        f.controller.create(f.controller.state.value.setup, labels); runCurrent(); assertTrue(f.service.creations.isEmpty())
    }
    @Test fun doubleCreateAndStaleSetupCallbackDispatchOnlyOneFrozenCreation() = runTest {
        val f = Fixture(this); f.setup(); val shown = f.controller.state.value.setup
        f.service.createGate = CompletableDeferred()
        f.controller.create(shown, labels); f.controller.create(shown, labels); runCurrent()
        assertEquals(1, f.service.creations.size)
        f.controller.playerName(shown, 0, "Late edit"); assertSame(shown, f.controller.state.value.setup)
        f.service.createGate!!.complete(Unit); runCurrent(); assertNotNull(f.controller.state.value.snapshot)
        f.controller.create(shown, labels); runCurrent(); assertEquals(1, f.service.creations.size)
    }
    @Test fun doubleClickInflightAndStaleOrFutureFramesNeverReplayCommands() = runTest {
        val f = Fixture(this); val frame = f.create()
        val action = GameAction.AdjustCounter(frame.seats[0].id, f.life(frame), null, -1)
        f.service.ackGate = CompletableDeferred()
        f.controller.action(frame, action); f.controller.action(frame, action); runCurrent()
        assertEquals(1, f.service.actions.size)
        f.service.ackGate!!.complete(Unit); runCurrent()
        f.controller.action(frame, action); f.controller.action(f.frame().copy(revision = Revision(99)), action); runCurrent()
        assertEquals(1, f.service.actions.size); assertEquals(Revision(0), f.service.actions.single().second)
    }
    @Test fun damageWithLifeDeductionIsOneFlatGroupAndOneConditionalUndoRestoresBoth() = runTest {
        val f = Fixture(this); f.create(commanders = 2)
        f.controller.action(f.frame(), GameAction.SetCommanderLifeDeduction(true)); runCurrent()
        val before = f.frame(); val damage = before.settings.counterDefinitions.single { it.role == CounterRole.COMMANDER_DAMAGE }.id
        val action = GameAction.AdjustCounter(before.seats[0].id, damage, before.seats[1].commanders[1].id, 5)
        f.controller.action(before, action); runCurrent()
        val sent = f.service.actions.last().first as GameAction.Group
        assertEquals(2, sent.actions.size); assertEquals(action, sent.actions[0])
        assertEquals(GameAction.AdjustCounter(before.seats[0].id, f.life(before), null, -5), sent.actions[1])
        assertTrue(sent.actions.none { it is GameAction.Group })
        assertEquals(35, f.frame().counters.single { it.seatId == before.seats[0].id && it.definitionId == f.life() }.value)
        val changed = f.frame(); f.controller.undo(changed); runCurrent()
        assertEquals(1, f.service.undoCalls); assertEquals(before.counters, f.frame().counters)
    }
    @Test fun damageWithoutDeductionStaysLiteralAndTaxUsesOwnExplicitRole() = runTest {
        val f = Fixture(this); val frame = f.create(commanders = 1)
        val damage = frame.settings.counterDefinitions.single { it.role == CounterRole.COMMANDER_DAMAGE }
        val edit = GameAction.AdjustCounter(frame.seats[0].id, damage.id, frame.seats[1].commanders[0].id, 1)
        f.controller.action(frame, edit); runCurrent(); assertEquals(edit, f.service.actions.single().first)
        assertEquals(40, f.frame().counters.single { it.seatId == frame.seats[0].id && it.definitionId == f.life() }.value)
        val tax = frame.settings.counterDefinitions.single { it.role == CounterRole.COMMANDER_TAX }
        f.controller.action(f.frame(), GameAction.AdjustCounter(frame.seats[0].id, tax.id, frame.seats[0].commanders[0].id, 1)); runCurrent()
        assertEquals(1, f.frame().counters.single { it.seatId == frame.seats[0].id && it.definitionId == tax.id }.value)
    }
    @Test fun unknownActionAckBlocksAllMutationsUntilExplicitReadWithoutReplay() = runTest {
        val f = Fixture(this); f.create()
        f.service.dispatchFault = Outcome.Unknown("Fixture uncertain", SnapshotList.empty()); f.service.commitBeforeFault = true
        f.adjust(); assertTrue(f.controller.state.value.reloadRequired); assertEquals(1, f.service.actions.size)
        f.adjust(); f.controller.undo(f.frame()); runCurrent(); assertEquals(1, f.service.actions.size); assertEquals(0, f.service.undoCalls)
        f.service.dispatchFault = null; f.controller.reload(); runCurrent()
        assertFalse(f.controller.state.value.reloadRequired); assertEquals(1, f.service.reads); assertEquals(1, f.service.actions.size)
        assertEquals(39, f.frame().counters.single { it.seatId == f.frame().seats[0].id && it.definitionId == f.life() }.value)
    }
    @Test fun staleServiceRevisionRequiresReloadAndNeverSilentlyResubmits() = runTest {
        val f = Fixture(this); val shown = f.create()
        f.service.games[shown.id] = shown.copy(revision = Revision(1)) // External writer, notification has not arrived.
        f.adjust(); assertEquals(Problem.STALE_REVISION, f.controller.state.value.problem)
        assertTrue(f.controller.state.value.reloadRequired); assertEquals(1, f.service.actions.size)
        f.controller.reload(); runCurrent(); assertEquals(Revision(1), f.frame().revision)
        assertEquals(1, f.service.actions.size)
    }
    @Test fun creationLostAckAllowsOnlyExactSameFrozenOperationAndInput() = runTest {
        val f = Fixture(this); f.setup(); f.service.createFault = Outcome.Unknown("Fixture lost ack", SnapshotList.empty()); f.service.commitBeforeFault = true
        f.controller.create(f.controller.state.value.setup, labels); runCurrent()
        val frozen = f.controller.state.value.pendingCreation!!
        f.controller.newSetup(); f.controller.playerName(f.controller.state.value.setup, 0, "Changed")
        assertSame(frozen, f.controller.state.value.pendingCreation); assertEquals("Player 0", f.controller.state.value.setup.seats[0].name)
        f.service.createFault = null; f.controller.retryCreation(); runCurrent()
        assertSame(f.service.creations[0], f.service.creations[1]); assertEquals(1, f.service.games.size)
        assertNull(f.controller.state.value.pendingCreation); assertNotNull(f.controller.state.value.snapshot)
    }
    @Test fun exactCreationRetryAcceptsEvolvedCurrentCheckpointAndHistoryDoesNotResolveOperation() = runTest {
        val token = object : CancelToken {
            override val isCancelled = false
            override fun throwIfCancelled() = Unit
        }
        for (completed in listOf(false, true)) {
            val f = Fixture(this); f.setup()
            f.service.createFault = Outcome.Unknown("Fixture lost ack", SnapshotList.empty()); f.service.commitBeforeFault = true
            f.controller.create(f.controller.state.value.setup, labels); runCurrent()
            val frozen = f.controller.state.value.pendingCreation!!
            val initial = f.service.games.values.single()
            f.service.dispatch(initial.id, GameAction.SetAdjustmentStep(5), initial.revision, token)
            if (completed) {
                val current = f.service.games[initial.id]!!
                f.service.dispatch(initial.id, GameAction.FinishWithOutcomes(SnapshotList.copyOf(current.seats.map {
                    SeatResult(it.id, SeatResultKind.DRAW, null)
                }), "Players confirmed a draw"), current.revision, token)
            }
            val evolved = f.service.games[initial.id]!!
            assertTrue(evolved.revision.value > 0); assertEquals(5, evolved.settings.adjustmentStep)
            f.controller.saved(); runCurrent(); f.controller.select(initial.id); runCurrent()
            assertSame(frozen, f.controller.state.value.pendingCreation)
            val calls = f.service.actions.size
            f.controller.action(f.frame(), GameAction.SetAdjustmentStep(1)); runCurrent()
            assertEquals(calls, f.service.actions.size) // History selection is not the operation receipt.
            f.service.createFault = null; f.controller.retryCreation(); runCurrent()
            assertSame(f.service.creations[0], f.service.creations[1])
            assertSame(evolved, f.frame()); assertEquals(evolved.status, f.frame().status)
            assertNull(f.controller.state.value.pendingCreation); assertFalse(f.controller.state.value.reloadRequired)
            assertEquals(1, f.service.games.size); assertEquals(calls, f.service.actions.size)
        }
    }
    @Test fun leakedPortCancellationRemainsUncertainWithoutFreshAutomaticCreate() = runTest {
        val f = Fixture(this); f.setup(); f.service.cancelCreate = true
        f.controller.create(f.controller.state.value.setup, labels); runCurrent()
        assertEquals(Problem.UNEXPECTED, f.controller.state.value.problem)
        assertNotNull(f.controller.state.value.pendingCreation); assertEquals(1, f.service.creations.size)
        assertEquals(1, f.service.games.size); assertFalse(f.controller.state.value.busy)
    }
    @Test fun callerCancellationRetainsFrozenIdentityAndNeverInventsSuccessOrAnotherSend() = runTest {
        val service = Service().also { it.createGate = CompletableDeferred() }
        val owner = SupervisorJob(backgroundScope.coroutineContext[Job])
        val ownedScope = CoroutineScope(backgroundScope.coroutineContext + owner)
        var ids = 0
        val controller = OrdinaryLifeController(ownedScope, service, StandardTestDispatcher(testScheduler), { "cancel-${++ids}" })
        controller.newSetup()
        controller.playerName(controller.state.value.setup, 0, "First")
        controller.playerName(controller.state.value.setup, 1, "Second")
        controller.create(controller.state.value.setup, labels); runCurrent()
        val frozen = controller.state.value.pendingCreation!!
        assertEquals(1, service.creations.size)
        owner.cancel(); runCurrent()
        assertSame(frozen, controller.state.value.pendingCreation)
        assertTrue(controller.state.value.reloadRequired); assertNull(controller.state.value.snapshot)
        assertFalse(controller.state.value.busy); assertTrue(service.games.isEmpty()); assertEquals(1, service.creations.size)
    }
    @Test fun observerNewerRevisionWinsOverOlderCommandAcknowledgment() = runTest {
        val f = Fixture(this); f.create(); f.service.ackGate = CompletableDeferred()
        f.adjust(); val committed = f.service.games.values.single()
        val newer = committed.copy(revision = Revision(2), turnNumber = 7)
        f.service.publish(newer); runCurrent(); assertEquals(Revision(2), f.frame().revision)
        f.service.ackGate!!.complete(Unit); runCurrent()
        assertSame(newer, f.frame()); assertEquals(7L, f.frame().turnNumber); assertFalse(f.controller.state.value.busy)
    }
    @Test fun typedObserverFailureRequiresExplicitReadAndLegacyWatchIsNeverUsed() = runTest {
        val f = Fixture(this); f.create()
        f.service.stream(f.frame().id).emit(Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Fixture ended")); runCurrent()
        assertEquals(Problem.NOT_FOUND, f.controller.state.value.problem); assertTrue(f.controller.state.value.reloadRequired)
        f.controller.reload(); runCurrent(); assertFalse(f.controller.state.value.reloadRequired)
        assertTrue(f.service.watches >= 2)
    }
    @Test fun finishWinnerLossDrawAndReopenAreExplicitAndCompletedTableIsReadOnly() = runTest {
        for (draw in listOf(false, true)) {
            val f = Fixture(this); val frame = f.create(3)
            f.controller.finish(frame, if (draw) emptySet() else setOf(frame.seats[1].id), draw, "Players confirm result"); runCurrent()
            val finished = f.frame(); assertEquals(GameStatus.COMPLETED, finished.status)
            val result = (finished.result as Knowledge.Known).value
            assertEquals(if (draw) 3 else 0, result.seatResults.count { it.result == SeatResultKind.DRAW })
            assertEquals(if (draw) 0 else 1, result.seatResults.count { it.result == SeatResultKind.WIN })
            val calls = f.service.actions.size; f.adjust(); f.controller.undo(f.frame()); runCurrent()
            assertEquals(calls, f.service.actions.size); assertEquals(0, f.service.undoCalls)
            f.controller.action(f.frame(), GameAction.Reopen("Continue playing")); runCurrent()
            assertEquals(GameStatus.PAUSED, f.frame().status); assertTrue((f.frame().result as Knowledge.Known).value.retracted)
        }
    }
    @Test fun savedHistoryAndEventsHaveExplicitPaginationAndClosingNeverDeletesOrMutates() = runTest {
        val f = Fixture(this); val template = f.create()
        repeat(24) { index -> val id = GameId(template.id.source, "other-$index"); f.service.games[id] = template.copy(id = id) }
        f.controller.saved(); runCurrent(); assertEquals(20, f.controller.state.value.history.size)
        assertEquals("20", f.controller.state.value.historyCursor)
        f.controller.moreHistory(); runCurrent(); assertEquals(25, f.controller.state.value.history.size)
        assertNull(f.controller.state.value.historyCursor); assertEquals(listOf(null, "20"), f.service.historyRequests.map { it.cursor })
        f.controller.select(template.id); runCurrent(); assertEquals(1, f.service.resumes)
        repeat(25) { f.adjust() }
        val calls = f.service.actions.size; f.controller.openEvents(f.frame()); runCurrent()
        assertEquals(20, f.controller.state.value.events.size); f.controller.moreEvents(); runCurrent()
        assertEquals(25, f.controller.state.value.events.size); assertEquals(listOf(null, "20"), f.service.eventRequests.map { it.cursor })
        f.controller.saved(); runCurrent(); assertEquals(calls, f.service.actions.size); assertEquals(25, f.service.games.size)
    }
    @Test fun compositionLeaveCancelsObservationAndReentryOnlyReadsWithoutActionReplay() = runTest {
        val f = Fixture(this); val old = f.create()
        f.controller.leave(); runCurrent()
        val newer = old.copy(revision = Revision(1), turnNumber = 2)
        f.service.publish(newer); runCurrent()
        assertSame(old, f.frame())
        f.controller.action(old, GameAction.AdjustCounter(old.seats[0].id, f.life(old), null, -1))
        assertTrue(f.service.actions.isEmpty())
        f.controller.open(); runCurrent()
        assertSame(newer, f.frame()); assertEquals(1, f.service.reads); assertTrue(f.service.actions.isEmpty())
        assertEquals(1, f.service.creations.size)
    }
    @Test fun busyLeaveAndReentryDefersReadAndObservationUntilDispatchOrUndoAcknowledges() = runTest {
        for (undo in listOf(false, true)) {
            val f = Fixture(this); f.create()
            if (undo) f.adjust()
            f.service.ackGate = CompletableDeferred()
            if (undo) f.controller.undo(f.frame()) else {
                val shown = f.frame()
                f.controller.action(shown, GameAction.AdjustCounter(shown.seats[0].id, f.life(shown), null, -1))
            }
            runCurrent(); assertTrue(f.controller.state.value.busy)
            val reads = f.service.reads; val watchers = f.service.watches
            val commands = f.service.actions.size; val undos = f.service.undoCalls
            f.controller.leave(); f.controller.open(); runCurrent()
            assertEquals(reads, f.service.reads); assertEquals(watchers, f.service.watches)
            f.service.ackGate!!.complete(Unit); runCurrent()
            assertFalse(f.controller.state.value.busy); assertEquals(reads + 1, f.service.reads)
            assertTrue(f.service.watches > watchers)
            val current = f.service.games[f.frame().id]!!
            val newer = current.copy(revision = current.revision.next(), turnNumber = 9)
            f.service.publish(newer); runCurrent()
            assertSame(newer, f.frame()); assertFalse(f.controller.state.value.reloadRequired)
            assertEquals(commands, f.service.actions.size); assertEquals(undos, f.service.undoCalls)
            assertEquals(1, f.service.creations.size)
        }
    }
    @Test fun reloadFailureAndContradictoryReadCannotClearMutationFence() = runTest {
        val f = Fixture(this); f.create(); f.service.dispatchFault = Outcome.Unknown("Fixture uncertain", SnapshotList.empty())
        f.adjust(); f.service.readFault = Outcome.Failure(DomainFailure(FailureCode.PERSISTENCE, "Fixture read", SnapshotList.empty()))
        f.controller.reload(); runCurrent(); assertTrue(f.controller.state.value.reloadRequired)
        val frame = f.frame(); f.service.readFault = Outcome.Success(frame.copy(turnNumber = 99))
        f.controller.reload(); runCurrent(); assertTrue(f.controller.state.value.reloadRequired)
        assertEquals(Problem.CORRUPT_DATA, f.controller.state.value.problem)
    }
}
