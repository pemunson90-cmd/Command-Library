package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*

/** Physical-copy input, independent of deck strategy and ownership estimates. */
internal data class DeckMarkOwnedInput(
    val quantity: String = "",
    val finish: Knowledge<Finish> = Knowledge.Unknown("Physical finish not recorded", SnapshotList.empty()),
    val condition: CardCondition = CardCondition.UNKNOWN,
    val location: String = "",
    val notes: String = "",
)
internal data class DeckMarkOwnedRow(
    val entry: DeckEntry,
    val input: DeckMarkOwnedInput,
    val selected: Boolean = false,
    val estimate: OwnedQuantityDemand? = null,
    val problem: Problem? = null,
    val sourceProblem: Problem? = null,
    val currentEntry: DeckEntry? = entry,
    val currentEstimate: OwnedQuantityDemand? = estimate,
    val supportedFinishes: SnapshotList<Finish> = SnapshotList.empty(),
    val loadingFinishes: Boolean = false,
    val finishProblem: Problem? = null,
    val finishGeneration: GenerationId? = null,
)
internal data class DeckMarkOwnedPreview(
    val deck: DeckSnapshot,
    val inventory: InventorySnapshot,
    val policy: OwnedQuantityPolicy,
    val rows: SnapshotList<DeckMarkOwnedRow>,
    val patch: CollectionPatch,
    val projection: InventoryProjection,
    val generation: GenerationId?,
)
internal data class DeckMarkOwnedState(
    val formTicket: Long = 0,
    val visible: Boolean = false,
    val deckId: DeckId? = null,
    val policy: OwnedQuantityPolicy? = null,
    val deck: DeckSnapshot? = null,
    val inventory: InventorySnapshot? = null,
    val rows: SnapshotList<DeckMarkOwnedRow> = SnapshotList.empty(),
    val allocation: OwnedQuantityPlan? = null,
    val estimateProblem: Problem? = null,
    val loading: Boolean = false,
    val previewing: Boolean = false,
    val submitting: Boolean = false,
    val uncertain: Boolean = false,
    val awaitingFresh: Boolean = false,
    val problem: Problem? = null,
    val issues: SnapshotList<DomainIssue> = SnapshotList.empty(),
    val preview: DeckMarkOwnedPreview? = null,
    val savedRevision: Revision? = null,
)
