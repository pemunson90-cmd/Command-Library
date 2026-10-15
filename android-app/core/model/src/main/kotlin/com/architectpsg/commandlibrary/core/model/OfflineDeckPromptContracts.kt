package com.architectpsg.commandlibrary.core.model

/** Retained product modes. This does not alias the unused legacy aggregate LabMode values. */
public enum class OfflineLabMode(public val templateId: EntityId) {
    ROAST(EntityId("offline-lab-roast")),
    CHAT(EntityId("offline-lab-chat")),
    UPGRADE(EntityId("offline-lab-upgrade")),
    SURPRISE(EntityId("offline-lab-surprise"));
    public val shippedVersion: Int get() = 1
}
public enum class RoastIntensity { GENTLE, MEDIUM, SPICY }
public data class OfflinePromptRequest(
    public val deck: DeckSnapshot,
    public val mode: OfflineLabMode,
    public val intensity: RoastIntensity,
    public val question: String,
    public val constraints: String,
    public val includePrivateNotes: Boolean,
    /** Exact optional installed English generation; absence never blocks provider-free export. */
    public val snapshot: SnapshotReference?,
    /** Session override only. null selects the shipped versioned body. */
    public val overrideBody: String?,
)
public data class OfflinePromptResult(
    public val artifact: PromptArtifact,
    public val mode: OfflineLabMode,
    public val template: PromptTemplate,
    public val overrideUsed: Boolean,
    public val snapshot: SnapshotReference?,
    public val oracleContextsIncluded: Int,
    public val oracleContextsOmitted: Int,
)
public enum class OfflineReferenceStatus { FOUND, AMBIGUOUS, NOT_FOUND, UNAVAILABLE }
/** Small identity-only review result; full original cards are loaded only on explicit detail tap. */
public data class OfflineCardReference(
    public val rawName: String,
    public val status: OfflineReferenceStatus,
    /** Originating review frame; card() must reject any different supplied snapshot before I/O. */
    public val snapshot: SnapshotReference?,
    public val candidates: SnapshotList<OracleId>,
    public val canonicalName: Knowledge<String>,
)
public data class OfflineReplyReview(
    public val deckId: DeckId,
    public val deckRevision: Revision,
    public val snapshot: SnapshotReference?,
    public val rawText: String,
    public val references: SnapshotList<OfflineCardReference>,
    public val statedSurpriseAngle: Knowledge<String>,
    /** These are separate missing producers, never a clean legality/affordability/owned verdict. */
    public val formatLegality: LegalityStatus,
    public val colorIdentity: Knowledge<Boolean>,
    public val budget: Knowledge<Boolean>,
    public val ownership: Knowledge<Boolean>,
    public val acquisitionCost: Knowledge<DecimalAmount>,
)
/** Complete provider-free capability; no fake provider/model/runtime methods. No state mutation. */
public interface OfflineDeckPrompts {
    public fun templates(): SnapshotList<PromptTemplate>
    public suspend fun render(request: OfflinePromptRequest, cancel: CancelToken): Outcome<OfflinePromptResult>
    public suspend fun review(deck: DeckSnapshot, snapshot: SnapshotReference?, reply: String, cancel: CancelToken): Outcome<OfflineReplyReview>
    public suspend fun card(reference: OfflineCardReference, snapshot: SnapshotReference, cancel: CancelToken): Outcome<CardRecord>
}
