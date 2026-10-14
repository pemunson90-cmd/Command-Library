package com.architectpsg.commandlibrary.data.storage.life.codec

import com.architectpsg.commandlibrary.core.model.*

private val layoutWire = enumeration(GameLayoutMode.TABLETOP, GameLayoutMode.OPERATOR)
internal val presentationWire = wire<GamePresentation>({ v(layoutWire, it.layout); v(nullable(stringWire), it.arrangementId); bool(it.keepAwake) },
    { GamePresentation(v(layoutWire), v(nullable(stringWire)), bool()) })
internal val exitWire = enumeration(SeatExitKind.CONFIRMED_LOSS, SeatExitKind.CONCEDED, SeatExitKind.DISCONNECTED, SeatExitKind.MANUAL)
internal val lossEvidenceWire = wire<LossEvidence>({ v(nullable(entityWire), it.definitionId); v(nullable(entityWire), it.commanderSlotId);
    v(nullable(intWire), it.observedValue); v(nullable(lossPolicyWire), it.policy); v(knowledge(stringWire), it.ruleVersion); string(it.rationale) },
    { LossEvidence(v(nullable(entityWire)), v(nullable(entityWire)), v(nullable(intWire)), v(nullable(lossPolicyWire)), v(knowledge(stringWire)), string()) })
private val participationWire = wire<SeatParticipation>({ value -> when (value) {
    SeatParticipation.Active -> byte(1)
    is SeatParticipation.Eliminated -> { byte(2); v(exitWire, value.kind); string(value.reason); v(nullable(lossEvidenceWire), value.evidence); v(timestampWire, value.at) }
} }, { when (byte()) {
    1 -> SeatParticipation.Active; 2 -> SeatParticipation.Eliminated(v(exitWire), string(), v(nullable(lossEvidenceWire)), v(timestampWire)); else -> errorTag()
} })
private val deckReferenceWire = wire<DeckReference>({ v(deckIdWire, it.id); v(revisionWire, it.revision); string(it.name); bool(it.tombstoned) },
    { DeckReference(v(deckIdWire), v(revisionWire), string(), bool()) })
private val seatWire = wire<Seat>({ v(entityWire, it.id); string(it.playerName); v(nullable(entityWire), it.profileId); v(knowledge(deckReferenceWire), it.deck);
    int(it.order); v(list(commanderWire), it.commanders); v(appearanceWire, it.appearance); v(participationWire, it.participation) },
    { Seat(v(entityWire), string(), v(nullable(entityWire)), v(knowledge(deckReferenceWire)), int(), v(list(commanderWire)), v(appearanceWire), v(participationWire)) })
private val counterWire = wire<CounterState>({ v(entityWire, it.definitionId); v(entityWire, it.seatId); v(nullable(entityWire), it.commanderSlotId); int(it.value); bool(it.hidden) },
    { CounterState(v(entityWire), v(entityWire), v(nullable(entityWire)), int(), bool()) })
private val clockModeWire = enumeration(SeatClockMode.NONE, SeatClockMode.CHESS, SeatClockMode.PER_TURN, SeatClockMode.SHARED)
private val zeroPolicyWire = enumeration(ClockZeroPolicy.ALERT, ClockZeroPolicy.PAUSE, ClockZeroPolicy.ELIMINATE)
private val undoPolicyWire = enumeration(ClockUndoPolicy.RESTORE_TIME, ClockUndoPolicy.KEEP_ELAPSED)
private val seatTimeWire = wire<SeatTime>({ v(entityWire, it.seatId); v(durationWire, it.initial); v(durationWire, it.remaining) },
    { SeatTime(v(entityWire), v(durationWire), v(durationWire)) })
private val anchorWire = wire<ClockAnchor>({ v(timestampWire, it.wallTime); v(durationWire, it.monotonicElapsed); v(entityWire, it.processSessionId) },
    { ClockAnchor(v(timestampWire), v(durationWire), v(entityWire)) })
private val sharedWire = wire<SharedCountdown>({ v(durationWire, it.initial); v(durationWire, it.remaining) }, { SharedCountdown(v(durationWire), v(durationWire)) })
private val clockWire = wire<SeatClockState>({ v(clockModeWire, it.mode); v(list(seatTimeWire), it.seats); v(nullable(entityWire), it.activeSeat);
    v(nullable(anchorWire), it.anchor); bool(it.paused); v(zeroPolicyWire, it.zeroPolicy); v(undoPolicyWire, it.undoPolicy);
    v(list(entityWire), it.firedAlertIds); v(nullable(sharedWire), it.shared) },
    { SeatClockState(v(clockModeWire), v(list(seatTimeWire)), v(nullable(entityWire)), v(nullable(anchorWire)), bool(),
        v(zeroPolicyWire), v(undoPolicyWire), v(list(entityWire)), v(nullable(sharedWire))) })
internal val dayNightWire = enumeration(DayNightState.UNSET, DayNightState.DAY, DayNightState.NIGHT)
private val designationsWire = wire<GameDesignations>({ v(nullable(entityWire), it.monarch); v(nullable(entityWire), it.initiative);
    string(it.initiativeNote); v(dayNightWire, it.dayNight); v(list(entityWire), it.blessedSeats) },
    { GameDesignations(v(nullable(entityWire)), v(nullable(entityWire)), string(), v(dayNightWire), v(list(entityWire))) })
private val elapsedWire = wire<ElapsedTimerState>({ v(durationWire, it.gameElapsed); v(durationWire, it.turnElapsed); bool(it.paused);
    v(nullable(anchorWire), it.anchor); v(knowledge(entityWire), it.turnLineageId) },
    { ElapsedTimerState(v(durationWire), v(durationWire), bool(), v(nullable(anchorWire)), v(knowledge(entityWire))) })
private val targetWire = wire<GameAlertTarget>({ value -> when (value) {
    is GameAlertTarget.SeatClock -> { byte(1); v(entityWire, value.seatId) }
    GameAlertTarget.SharedCountdown -> byte(2); GameAlertTarget.TurnTimer -> byte(3); GameAlertTarget.GameTimer -> byte(4)
} }, { when (byte()) {
    1 -> GameAlertTarget.SeatClock(v(entityWire)); 2 -> GameAlertTarget.SharedCountdown; 3 -> GameAlertTarget.TurnTimer
    4 -> GameAlertTarget.GameTimer; else -> errorTag()
} })
private val alertWire = wire<GameAlert>({ v(entityWire, it.id); v(gameIdWire, it.gameId); v(targetWire, it.target); v(timestampWire, it.firedAt);
    v(nullable(timestampWire), it.acknowledgedAt) },
    { GameAlert(v(entityWire), v(gameIdWire), v(targetWire), v(timestampWire), v(nullable(timestampWire))) })
internal val alertPreferencesWire = wire<GameAlertPreferences>({ bool(it.visual); bool(it.haptic); bool(it.audio) }, { GameAlertPreferences(bool(), bool(), bool()) })
private val timerSettingsWire = wire<ElapsedTimerSettings>({ bool(it.enabled); v(nullable(durationWire), it.turnWarning); v(nullable(durationWire), it.gameWarning) },
    { ElapsedTimerSettings(bool(), v(nullable(durationWire)), v(nullable(durationWire))) })
private val settingsWire = wire<GameSettings>({ v(knowledge(formatIdWire), it.format); int(it.initialLife); v(list(definitionWire, 128), it.counterDefinitions);
    v(list(variantDefinitionWire, 128), it.variantDefinitions); v(clockModeWire, it.clockMode); long(it.randomSeed); v(presentationWire, it.presentation);
    int(it.adjustmentStep); bool(it.deductLifeForCommanderDamage); v(timerSettingsWire, it.elapsedTimers); v(alertPreferencesWire, it.alertPreferences) },
    { GameSettings(v(knowledge(formatIdWire)), int(), v(list(definitionWire, 128)), v(list(variantDefinitionWire, 128)), v(clockModeWire), long(),
        v(presentationWire), int(), bool(), v(timerSettingsWire), v(alertPreferencesWire)) })
private val seatResultKindWire = enumeration(SeatResultKind.WIN, SeatResultKind.LOSS, SeatResultKind.DRAW)
internal val seatResultWire = wire<SeatResult>({ v(entityWire, it.seatId); v(seatResultKindWire, it.result); v(nullable(exitWire), it.exit) },
    { SeatResult(v(entityWire), v(seatResultKindWire), v(nullable(exitWire))) })
private val resultWire = wire<GameResult>({ v(entityWire, it.id); v(gameIdWire, it.gameId); v(list(entityWire), it.winners); v(timestampWire, it.finishedAt);
    string(it.reason); v(revisionWire, it.revision); bool(it.retracted); v(list(seatResultWire), it.seatResults) },
    { GameResult(v(entityWire), v(gameIdWire), v(list(entityWire)), v(timestampWire), string(), v(revisionWire), bool(),
        if (formatVersion >= 2) v(list(seatResultWire)) else SnapshotList.empty()) })
private val statusWire = enumeration(GameStatus.ACTIVE, GameStatus.PAUSED, GameStatus.COMPLETED)
internal val snapshotWire = wire<GameSnapshot>({ v(gameIdWire, it.id); int(it.version); v(revisionWire, it.revision); v(settingsWire, it.settings);
    v(statusWire, it.status); v(timestampWire, it.startedAt); v(knowledge(timestampWire), it.endedAt); v(list(seatWire), it.seats);
    v(list(counterWire), it.counters); v(nullable(entityWire), it.activeSeatId); long(it.turnNumber); v(clockWire, it.clock);
    v(list(variantStateWire), it.variants); v(knowledge(longWire), it.lastEventId); v(knowledge(resultWire), it.result);
    v(designationsWire, it.designations); v(elapsedWire, it.elapsed); v(list(alertWire), it.alerts) },
    { GameSnapshot(v(gameIdWire), int(), v(revisionWire), v(settingsWire), v(statusWire), v(timestampWire), v(knowledge(timestampWire)),
        v(list(seatWire)), v(list(counterWire)), v(nullable(entityWire)), long(), v(clockWire), v(list(variantStateWire)),
        v(knowledge(longWire)), v(knowledge(resultWire)), v(designationsWire), v(elapsedWire), v(list(alertWire))) })

internal val creationWire = wire<GameCreation>({ v(entityWire, it.operationId); v(settingsWire, it.settings); v(list(seatWire), it.seats); v(clockWire, it.clock) },
    { GameCreation(v(entityWire), v(settingsWire), v(list(seatWire)), v(clockWire)) })
