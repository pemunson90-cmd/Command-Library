package com.architectpsg.commandlibrary.data.storage.life.codec

import com.architectpsg.commandlibrary.core.model.*

private val requestWire = wire<RandomRequest>({ value -> when (value) {
    is RandomRequest.Dice -> { byte(1); v(entityWire, value.requestId); long(value.requested); int(value.sides) }
    is RandomRequest.ContinueDice -> { byte(2); v(entityWire, value.requestId); long(value.batchIndex) }
    is RandomRequest.Coin -> { byte(3); v(entityWire, value.requestId) }
    is RandomRequest.SeatPick -> { byte(4); v(entityWire, value.requestId); v(nullable(entityWire), value.asker); bool(value.opponentsOnly) }
    is RandomRequest.SeatOrder -> { byte(5); v(entityWire, value.requestId) }
    is RandomRequest.VariantZoneShuffle -> { byte(6); v(entityWire, value.requestId); v(providerIdentityWire, value.definitionId);
        string(value.actionId); string(value.zoneId); string(value.policyKey); string(value.policyValue) }
} }, { when (byte()) {
    1 -> RandomRequest.Dice(v(entityWire), long(), int()); 2 -> RandomRequest.ContinueDice(v(entityWire), long())
    3 -> RandomRequest.Coin(v(entityWire)); 4 -> RandomRequest.SeatPick(v(entityWire), v(nullable(entityWire)), bool())
    5 -> RandomRequest.SeatOrder(v(entityWire))
    6 -> { require(formatVersion >= 3) { "Variant shuffle request requires format3" }
        RandomRequest.VariantZoneShuffle(v(entityWire), v(providerIdentityWire), string(), string(), string(), string()) }
    else -> errorTag()
} })
private val randomValueWire = wire<RecordedRandomValue>({ value -> when (value) {
    is RecordedRandomValue.Dice -> { byte(1); int(value.sides); v(list(intWire, 10_000), value.faces); long(value.sum);
        long(value.requested); long(value.batchIndex); long(value.remaining) }
    is RecordedRandomValue.Coin -> { byte(2); bool(value.heads) }
    is RecordedRandomValue.SeatPick -> { byte(3); v(nullable(entityWire), value.selected); v(list(entityWire), value.eligible) }
    is RecordedRandomValue.SeatOrder -> { byte(4); v(list(entityWire), value.seatIds) }
    is RecordedRandomValue.VariantZoneShuffle -> { byte(5); v(providerIdentityWire, value.definitionId); string(value.definitionVersion);
        string(value.actionId); string(value.zoneId); string(value.policyKey); string(value.policyValue);
        v(list(providerIdentityWire, RecordedRandomValue.VariantZoneShuffle.MAX_CARDS), value.beforeOrder);
        v(list(providerIdentityWire, RecordedRandomValue.VariantZoneShuffle.MAX_CARDS), value.afterOrder) }
} }, { when (byte()) {
    1 -> RecordedRandomValue.Dice(int(), v(list(intWire, 10_000)), long(), long(), long(), long())
    2 -> RecordedRandomValue.Coin(bool()); 3 -> RecordedRandomValue.SeatPick(v(nullable(entityWire)), v(list(entityWire)))
    4 -> RecordedRandomValue.SeatOrder(v(list(entityWire)))
    5 -> { require(formatVersion >= 3) { "Variant shuffle result requires format3" }
        RecordedRandomValue.VariantZoneShuffle(v(providerIdentityWire), string(), string(), string(), string(), string(),
            v(list(providerIdentityWire, RecordedRandomValue.VariantZoneShuffle.MAX_CARDS)),
            v(list(providerIdentityWire, RecordedRandomValue.VariantZoneShuffle.MAX_CARDS))) }
    else -> errorTag()
} })
private val randomResultWire = wire<RecordedRandomResult>({ v(entityWire, it.requestId); v(timestampWire, it.at); v(randomValueWire, it.value) },
    { RecordedRandomResult(v(entityWire), v(timestampWire), v(randomValueWire)) })

/** A nested group's tag is rejected before reading its child count, bounding recursion to one level. */
private fun actionCodec(allowGroup: Boolean): LifeWire<GameAction> = wire({ value -> when (value) {
    is GameAction.AdjustCounter -> { byte(1); v(entityWire, value.seatId); v(entityWire, value.definitionId); v(nullable(entityWire), value.commanderSlotId); int(value.delta) }
    is GameAction.SetCounterHidden -> { byte(2); v(entityWire, value.seatId); v(entityWire, value.definitionId); bool(value.hidden) }
    is GameAction.RenameSeat -> { byte(3); v(entityWire, value.seatId); string(value.name) }
    is GameAction.ReorderSeats -> { byte(4); v(list(entityWire), value.seatIds) }
    is GameAction.SwitchTurn -> { byte(5); v(entityWire, value.seatId) }
    is GameAction.ClockPause -> { byte(6); bool(value.paused) }
    is GameAction.Variant -> { byte(7); v(providerIdentityWire, value.definitionId); string(value.actionId); v(list(providerIdentityWire), value.selectedCards) }
    is GameAction.Finish -> { byte(8); v(list(entityWire), value.winnerSeatIds); string(value.reason) }
    is GameAction.Reopen -> { byte(9); string(value.reason) }
    is GameAction.Group -> {
        require(allowGroup && value.actions.size in 1..1024) { "Life actions require a nonempty flat group of at most1024" }
        byte(10); v(list(actionCodec(false), 1024), value.actions); v(entityWire, value.groupId)
    }
    is GameAction.RequestRandom -> { byte(11); v(requestWire, value.request) }
    is GameAction.SetCounter -> { byte(12); v(entityWire, value.seatId); v(entityWire, value.definitionId); v(nullable(entityWire), value.commanderSlotId); int(value.value) }
    is GameAction.SetAdjustmentStep -> { byte(13); int(value.step) }
    is GameAction.SetCommanderLifeDeduction -> { byte(14); bool(value.enabled) }
    is GameAction.AddCounterDefinition -> { byte(15); v(definitionWire, value.definition) }
    is GameAction.UpdateCounterDefinition -> { byte(16); v(definitionWire, value.definition) }
    is GameAction.EliminateSeat -> { byte(17); v(entityWire, value.seatId); v(exitWire, value.kind); string(value.reason); v(nullable(lossEvidenceWire), value.evidence) }
    is GameAction.RestoreSeat -> { byte(18); v(entityWire, value.seatId); string(value.reason) }
    is GameAction.DismissLossCandidate -> { byte(19); v(lossEvidenceWire, value.candidate); v(entityWire, value.seatId); string(value.reason) }
    is GameAction.SetMonarch -> { byte(20); v(nullable(entityWire), value.seatId) }
    is GameAction.SetInitiative -> { byte(21); v(nullable(entityWire), value.seatId); string(value.note) }
    is GameAction.SetDayNight -> { byte(22); v(dayNightWire, value.state) }
    is GameAction.SetBlessing -> { byte(23); v(entityWire, value.seatId); bool(value.blessed) }
    is GameAction.SetSeatAppearance -> { byte(24); v(entityWire, value.seatId); v(appearanceWire, value.appearance) }
    is GameAction.SetGamePresentation -> { byte(25); v(presentationWire, value.presentation) }
    is GameAction.FinishWithOutcomes -> { byte(26); v(list(seatResultWire), value.seatResults); string(value.reason) }
    is GameAction.PassClock -> { byte(27); v(entityWire, value.seatId) }
    is GameAction.SetElapsedPaused -> { byte(28); bool(value.paused) }
    is GameAction.AcknowledgeGameAlert -> { byte(29); v(entityWire, value.alertId) }
    is GameAction.SetAlertPreferences -> { byte(30); v(alertPreferencesWire, value.preferences) }
} }, {
    val tag = byte()
    require(formatVersion >= 2 || tag <= 11) { "Action tag is unavailable in format1" }
    when (tag) {
    1 -> GameAction.AdjustCounter(v(entityWire), v(entityWire), v(nullable(entityWire)), int())
    2 -> GameAction.SetCounterHidden(v(entityWire), v(entityWire), bool()); 3 -> GameAction.RenameSeat(v(entityWire), string())
    4 -> GameAction.ReorderSeats(v(list(entityWire))); 5 -> GameAction.SwitchTurn(v(entityWire)); 6 -> GameAction.ClockPause(bool())
    7 -> GameAction.Variant(v(providerIdentityWire), string(), v(list(providerIdentityWire)))
    8 -> GameAction.Finish(v(list(entityWire)), string()); 9 -> GameAction.Reopen(string())
    10 -> {
        require(allowGroup) { "Nested life action group" }
        val actions = v(list(actionCodec(false), 1024)); require(actions.isNotEmpty()) { "Empty life action group" }
        GameAction.Group(actions, v(entityWire))
    }
    11 -> GameAction.RequestRandom(v(requestWire))
    12 -> GameAction.SetCounter(v(entityWire), v(entityWire), v(nullable(entityWire)), int())
    13 -> GameAction.SetAdjustmentStep(int()); 14 -> GameAction.SetCommanderLifeDeduction(bool())
    15 -> GameAction.AddCounterDefinition(v(definitionWire)); 16 -> GameAction.UpdateCounterDefinition(v(definitionWire))
    17 -> GameAction.EliminateSeat(v(entityWire), v(exitWire), string(), v(nullable(lossEvidenceWire)))
    18 -> GameAction.RestoreSeat(v(entityWire), string()); 19 -> GameAction.DismissLossCandidate(v(lossEvidenceWire), v(entityWire), string())
    20 -> GameAction.SetMonarch(v(nullable(entityWire))); 21 -> GameAction.SetInitiative(v(nullable(entityWire)), string())
    22 -> GameAction.SetDayNight(v(dayNightWire)); 23 -> GameAction.SetBlessing(v(entityWire), bool())
    24 -> GameAction.SetSeatAppearance(v(entityWire), v(appearanceWire)); 25 -> GameAction.SetGamePresentation(v(presentationWire))
    26 -> GameAction.FinishWithOutcomes(v(list(seatResultWire)), string()); 27 -> GameAction.PassClock(v(entityWire))
    28 -> GameAction.SetElapsedPaused(bool()); 29 -> GameAction.AcknowledgeGameAlert(v(entityWire))
    30 -> GameAction.SetAlertPreferences(v(alertPreferencesWire)); else -> errorTag()
} })
private val actionWire = actionCodec(true)
internal val eventWire = wire<GameEvent>({ long(it.id); v(gameIdWire, it.gameId); v(entityWire, it.groupId); v(actionWire, it.action);
    v(timestampWire, it.at); v(knowledge(longWire), it.undoOfEventId); v(revisionWire, it.revision); v(list(randomResultWire), it.randomResults) },
    { GameEvent(long(), v(gameIdWire), v(entityWire), v(actionWire), v(timestampWire), v(knowledge(longWire)), v(revisionWire), v(list(randomResultWire))) })
private val readingWire = wire<GameTimeObservation>({ v(durationWire, it.monotonic); v(timestampWire, it.wall); v(entityWire, it.processSessionId) },
    { GameTimeObservation(v(durationWire), v(timestampWire), v(entityWire)) })
private val framedSnapshotWire = wire<GameSnapshot>({ blob(encodeLifeFrame(1, SNAPSHOT_LIMIT, snapshotWire, it), SNAPSHOT_LIMIT) },
    { decodeLifeFrame(blob(SNAPSHOT_LIMIT), 1, SNAPSHOT_LIMIT, snapshotWire) })
private val actionProvenanceWire = wire<GameActionProvenance>({ long(it.eventId); v(framedSnapshotWire, it.before);
    v(framedSnapshotWire, it.afterAction); v(readingWire, it.reading) },
    { GameActionProvenance(long(), v(framedSnapshotWire), v(framedSnapshotWire), v(readingWire)) })
internal val archiveWire = wire<GameEventArchive>({ int(it.version); v(eventWire, it.event); v(actionProvenanceWire, it.provenance) },
    { GameEventArchive(int(), v(eventWire), v(actionProvenanceWire)) })
internal val checkpointWire = wire<GameCheckpoint>({ v(framedSnapshotWire, it.snapshot); v(knowledge(longWire), it.lastSettledEventId);
    v(nullable(entityWire), it.recoveredProcessSessionId) },
    { GameCheckpoint(v(framedSnapshotWire), v(knowledge(longWire)), v(nullable(entityWire))) })
