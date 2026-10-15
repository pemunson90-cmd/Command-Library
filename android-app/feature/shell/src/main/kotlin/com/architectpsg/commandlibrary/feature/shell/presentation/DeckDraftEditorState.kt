package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.ShellDestination

internal sealed interface DeckEditorTarget {
    data object Details : DeckEditorTarget
    data class Entry(val id: EntityId) : DeckEditorTarget
    data object Add : DeckEditorTarget
    data class Remove(val id: EntityId) : DeckEditorTarget
}
internal data class DraftSession(val ticket: Long, val deckId: DeckId, val target: DeckEditorTarget)
internal enum class DraftPhase { CLOSED, LOADING, IDLE, SAVING_RAW, PREPARING, COMMITTING, DISCARDING }
internal enum class DraftExitPhase { FLUSHING, NEEDS_CHOICE, READY }
internal enum class DraftExitChoice { STAY, CONTINUE, DISCARD_INPUT }

/** Retained values only: never callbacks captured from a disposed composition. */
internal sealed interface DeferredDeckAction {
    data object CloseEditor : DeferredDeckAction
    data class SelectDeck(val id: DeckId) : DeferredDeckAction
    data object ClearDeck : DeferredDeckAction
    data class OpenEditor(val deckId: DeckId, val target: DeckEditorTarget) : DeferredDeckAction
    data object CreateDeck : DeferredDeckAction
    data class DuplicateDeck(val id: DeckId) : DeferredDeckAction
    data class UndoDeck(val id: DeckId) : DeferredDeckAction
    data class ReorderEntry(val deckId: DeckId, val entryId: EntityId, val direction: Int) : DeferredDeckAction
    data class OpenVersions(val deckId: DeckId) : DeferredDeckAction
    data class Navigate(val destination: ShellDestination) : DeferredDeckAction
}
internal data class PendingDraftExit(val ticket: Long, val sessionTicket: Long,
    val barrierEditSequence: Long, val action: DeferredDeckAction, val phase: DraftExitPhase)
internal data class DraftEditorState(
    val visible: Boolean = false,
    val recovered: Boolean = false,
    val requestedTarget: DeckEditorTarget? = null,
    val session: DraftSession? = null,
    val body: DeckDraftBody? = null,
    val editSequence: Long = 0,
    val durable: DeckDraftState? = null,
    /** Exact matching base only; a current snapshot may never impersonate a historical base. */
    val base: DeckSnapshot? = null,
    val currentHead: DeckSnapshot? = null,
    val phase: DraftPhase = DraftPhase.CLOSED,
    val rawProblem: Problem? = null,
    val contentProblem: Problem? = null,
    val conflict: Boolean = false,
    val uncertain: Boolean = false,
    val hasFrozenCall: Boolean = false,
    val structuralLocked: Boolean = false,
    val rawDirty: Boolean = false,
    val explicitlyInvalid: Boolean = false,
    val exit: PendingDraftExit? = null,
)


/** Complete retained editor lane; its concrete producer owns IO, scheduling and exact recovery. */
internal interface DeckDraftEditor {
    val state: kotlinx.coroutines.flow.StateFlow<DraftEditorState>
    val hasMutationBarrier: Boolean
    val canContinueExit: Boolean
    fun open(id: DeckId, target: DeckEditorTarget)
    fun change(body: DeckDraftBody)
    fun confirmExplicit()
    fun retryExact()
    fun provideCurrentCard(card: CardRecord)
    fun discardInput()
    fun discardPreparation()
    fun requestExit(action: DeferredDeckAction): Long
    fun chooseExit(ticket: Long, choice: DraftExitChoice)
    fun takeReadyExit(ticket: Long): DeferredDeckAction?
    fun onBackground()
    fun onActiveGeneration(generation: GenerationId?)
    fun onDeckHeadChanged(id: DeckId, revision: Revision)
}
