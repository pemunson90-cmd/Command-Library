package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*

internal data class DeckVersionsUiState(
    val deckId: DeckId? = null,
    val head: DeckSnapshot? = null,
    val loadingHead: Boolean = false,
    val headProblem: Problem? = null,
    val items: SnapshotList<DeckVersionSummary> = SnapshotList.empty(),
    val cursor: String? = null,
    val epoch: Revision? = null,
    val loadingList: Boolean = false,
    val listProblem: Problem? = null,
    val selectedId: EntityId? = null,
    val selected: DeckVersion? = null,
    val loadingTarget: Boolean = false,
    val targetProblem: Problem? = null,
    val preview: DeckRestorePreview? = null,
    val previewing: Boolean = false,
    val previewProblem: Problem? = null,
    val captureForm: Boolean = false,
    val label: String = "",
    val recoveryLabel: String = "",
    val invalidLabel: Boolean = false,
    val discardPrompt: Boolean = false,
    val mutationBusy: Boolean = false,
    val pending: Boolean = false,
    val mutationProblem: Problem? = null,
    val captured: DeckVersion? = null,
    val restored: DeckRestoreResult? = null,
)
