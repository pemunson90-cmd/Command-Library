package com.architectpsg.commandlibrary.core.model

/** Raw recovery persistence is distinct from an acknowledged content save. No partial service binding. */
public interface LocalDeckDrafts {
    public suspend fun load(id: DeckId, cancel: CancelToken): Outcome<DeckDraftState>
    public suspend fun write(id: DeckId, expectedGeneration: Long, body: DeckDraftBody, cancel: CancelToken): Outcome<DeckDraftState>
    public suspend fun prepare(id: DeckId, token: DeckDraftToken, scope: DeckDraftCommitScope,
        patch: DeckPatch, operationId: EntityId, cancel: CancelToken): Outcome<DeckDraftPreparation>
    public suspend fun commit(id: DeckId, operationId: EntityId, preparedRequestSha256: String, cancel: CancelToken): Outcome<DeckDraftCommitAck>
    public suspend fun discard(id: DeckId, expectedGeneration: Long, expectedPendingOperationId: EntityId?,
        mode: DeckDraftDiscardMode, cancel: CancelToken): Outcome<DeckDraftState>
}

/** Generation is a persistent raw-slot counter, never a content revision or a reusable lease. */
public data class DeckDraftToken(public val deckId: DeckId, public val generation: Long,
    public val baseRevision: Revision, public val rawSha256: String)
public data class SavedDeckDraft(public val token: DeckDraftToken, public val body: DeckDraftBody)
public data class DeckDraftState(public val generation: Long, public val draft: SavedDeckDraft?,
    public val latestPreparation: DeckDraftPreparation?)

/** Invalid/incomplete raw text is allowed within recovery limits; structural identifiers remain bounded. */
public sealed interface DeckDraftBody {
    public val baseRevision: Revision
    public data class Details(override val baseRevision: Revision, public val name: String,
        public val description: String, public val primer: String) : DeckDraftBody
    public data class Entry(override val baseRevision: Revision, public val entryId: EntityId,
        public val quantity: String, public val notes: String, public val board: BoardRole,
        public val finish: Knowledge<Finish>, public val labels: SnapshotList<EntryLabel>) : DeckDraftBody
    public data class Add(override val baseRevision: Revision, public val quantity: String,
        public val notes: String, public val board: BoardRole, public val finish: Knowledge<Finish>,
        public val labels: SnapshotList<EntryLabel>, public val selection: DeckDraftSelection?) : DeckDraftBody
    public data class Remove(override val baseRevision: Revision, public val entryId: EntityId) : DeckDraftBody
}

/** Recovery evidence only; a restored identity/generation is not reusable current-search authorization. */
public data class DeckDraftSelection(public val identity: CardIdentity, public val generation: GenerationId?)
public enum class DeckDraftCommitScope { TEXT_AUTOSAVE, EXPLICIT_CONFIRMED }
public enum class DeckDraftDiscardMode { INPUT, PREPARATION_ONLY }

/** Immutable request; ADD's one generated entry identity is already inside patch. */
public data class PreparedDeckDraft(public val operationId: EntityId, public val sourceToken: DeckDraftToken,
    public val scope: DeckDraftCommitScope, public val patch: DeckPatch, public val requestSha256: String)
public sealed interface DeckDraftPreparation {
    public val intent: PreparedDeckDraft
    public data class Pending(override val intent: PreparedDeckDraft) : DeckDraftPreparation
    public data class Committed(override val intent: PreparedDeckDraft, public val acknowledgement: DeckDraftCommitAck) : DeckDraftPreparation
    public data class Abandoned(override val intent: PreparedDeckDraft) : DeckDraftPreparation
}

/** Historical transaction effect, never a claim about today's slot. Tokens carry no residual raw payload. */
public sealed interface DeckDraftSlotEffect {
    public data class Cleared(public val resultGeneration: Long) : DeckDraftSlotEffect
    public data class Residual(public val token: DeckDraftToken) : DeckDraftSlotEffect
    public data class PreservedLater(public val observedToken: DeckDraftToken) : DeckDraftSlotEffect
}
public data class DeckDraftCommitAck(public val operationId: EntityId, public val preparedRequestSha256: String,
    public val snapshot: DeckSnapshot, public val sourceToken: DeckDraftToken, public val slotEffect: DeckDraftSlotEffect)

/** Complete deterministic proposal shared by UI and storage; no clock, IDs, catalog calls or write authority. */
public interface DeckDraftPlanner {
    public fun propose(base: DeckSnapshot, body: DeckDraftBody, scope: DeckDraftCommitScope,
        addedEntryId: EntityId?, cancel: CancelToken): Outcome<DeckDraftPlan>
}
/** Residual base is the projected result revision; commit must rederive and verify against actual result. */
public data class DeckDraftPlan(public val patch: DeckPatch, public val residual: DeckDraftBody?)

/** Lossless recovery encoding, separate from committed CLDK and canonical CLDP validity. */
public interface DeckDraftPayloadCodecPort {
    public fun encodeDraft(value: DeckDraftBody): Outcome<SnapshotBytes>
    public fun decodeDraft(payload: SnapshotBytes): Outcome<DeckDraftBody>
}
public object DeckDraftLimits {
    public const val MAX_ENCODED_BYTES: Int = 8 * 1024 * 1024
    public const val MAX_NAME_CODE_UNITS: Int = 4_096
    public const val MAX_QUANTITY_CODE_UNITS: Int = 4_096
    public const val MAX_TEXT_CODE_UNITS: Int = 1_048_576
    public const val AUTOSAVE_DELAY_MILLIS: Long = 500
    public const val TEXT_REASON: String = "Autosave deck text"
    public const val EXPLICIT_REASON: String = "Edit deck"
}
