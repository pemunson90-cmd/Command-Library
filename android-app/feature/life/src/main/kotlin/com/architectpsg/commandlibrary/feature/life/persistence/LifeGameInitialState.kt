package com.architectpsg.commandlibrary.feature.life.persistence

import com.architectpsg.commandlibrary.core.model.*
import kotlin.time.Duration
import com.architectpsg.commandlibrary.feature.life.domain.SnapshotGraphValidator

/** Structural new-game checks. Authentic variant/source semantics belong to the engine initializer. */
internal fun validateInitialGame(input: GameCreation, checkpoint: GameCheckpoint) {
    val code = FailureCode.INVALID_INPUT
    fun need(valid: Boolean, message: String) = requireGame(valid, code, message)
    val game = checkpoint.snapshot
    gameIdentity(game.id.source.value, code); gameIdentity(game.id.raw, code)
    need(game.version == 1 && game.revision.value == 0L, "Invalid initial game version/revision")
    need(game.settings == input.settings && game.seats == input.seats && game.clock == input.clock, "Initialized game differs from creation input")
    need(game.status != GameStatus.COMPLETED && game.turnNumber == 0L && game.activeSeatId == null, "Invalid initial game activity")
    need(game.endedAt is Knowledge.Absent && game.result is Knowledge.Absent && game.lastEventId is Knowledge.Absent &&
        checkpoint.lastSettledEventId is Knowledge.Absent && checkpoint.recoveredProcessSessionId == null, "Initial game contains incomplete/prior lineage")
    need(game.alerts.isEmpty() && game.designations == GameDesignations(), "Initial game contains prior alerts/designations")
    need(game.elapsed.gameElapsed == Duration.ZERO && game.elapsed.turnElapsed == Duration.ZERO && game.elapsed.paused &&
        game.elapsed.anchor == null && (game.elapsed.turnLineageId is Knowledge.Absent || game.elapsed.turnLineageId is Knowledge.Known), "Invalid initial elapsed timer")
    when (val checked = SnapshotGraphValidator.validate(game)) {
        is Outcome.Success -> Unit
        is Outcome.Failure -> rejectGame(code, checked.error.message)
        else -> rejectGame(code, "Unsupported/incomplete initial snapshot graph")
    }
    val definitions = input.settings.counterDefinitions.associateBy { it.id }
    for (state in game.counters) {
        val definition = definitions.getValue(state.definitionId) // graph guard proved each reference
        need(state.value == definition.initial && !state.hidden, "Invalid initial counter value/visibility")
    }
}
