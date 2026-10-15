package com.architectpsg.commandlibrary.core.model

public enum class HistoryTurnSource { OFFLINE_CAPTURE, PROVIDER_GENERATION }
public data class HistoryPromptProvenance(
    public val effectiveTemplateHash: ContentHash,
    public val overrideUsed: Boolean,
    public val snapshot: SnapshotReference?,
)
public data class NewConversation(public val id: EntityId, public val deckId: DeckId,
    public val mode: OfflineLabMode, public val title: String)
/** Initial provider attempts are PENDING/empty; offline captures are COMPLETE with unknown usage. */
public data class NewHistoryTurn(
    public val id: EntityId,
    public val source: HistoryTurnSource,
    public val prompt: PromptArtifact,
    public val provenance: HistoryPromptProvenance,
    public val rawContent: String,
    public val providerProfileId: EntityId? = null,
    public val modelId: String? = null,
    public val retryOf: EntityId? = null,
    public val ownerEpoch: EntityId? = null,
)
/** A cumulative durable checkpoint, not permission to dispatch/replay a charged generation. */
public data class HistoryProgress(public val status: TurnStatus, public val rawContent: String,
    public val usage: Knowledge<Usage>)
/** List rows deliberately exclude prompt/reply BLOBs. Load exact content only on selection. */
public data class HistoryTurnSummary(public val id: EntityId, public val conversationId: EntityId,
    public val revision: Revision, public val ordinal: Long, public val source: HistoryTurnSource,
    public val status: TurnStatus, public val startedAt: UtcTimestamp, public val finishedAt: Knowledge<UtcTimestamp>)

/**
 * Complete local history capability. No transport, credentials, generation, or current-deck writes.
 * All IDs caller supplied, permanently reserved after deletion. Conversation deck/mode and each
 * turn's exact artifact/provenance/source/provider/model/retry identity are immutable. Input mode
 * is the conversation's actual four-mode value, never an alias of legacy aggregate LabMode.
 * Conversation revision advances once per transaction changing its children; turn revisions fence
 * checkpoints. CAS precedes no-op checks. Overflow fails before clock/write. Pages are stable
 * BINARY conversation-ID / ascending turn-ordinal keysets, bounded by PageRequest; any relevant
 * mutation invalidates their cursor. Missing/deleted is NOT_FOUND; duplicate/stale is STALE_REVISION.
 * Append starts revision1/next ordinal. OFFLINE_CAPTURE requires absent provider/model/retry/epoch,
 * records COMPLETE and unknown usage. PROVIDER_GENERATION requires explicit model/profile/epoch,
 * starts PENDING with empty reply, and does not send anything. retryOf must reference an earlier
 * terminal provider attempt in this same conversation; it never mutates/replays that attempt.
 * Checkpoints require exact epoch/revision and a nonterminal provider attempt. PENDING can become
 * STREAMING or terminal; STREAMING cannot return to PENDING. Raw content must retain its committed
 * prefix. COMPLETE/INTERRUPTED/FAILED are terminal and immutable, including usage. Unknown usage
 * and unknown billing stay distinct from known zero; cached-token counts remain separate.
 * Root supplies one process UUID and calls recover(epoch) before exposing this capability.
 * Recovery atomically interrupts only unfinished foreign-epoch attempts, preserving every payload
 * byte and updating header/turn revisions. Same-epoch reopen does not interrupt live work.
 * Delete CAS-clears title and all prompt/reply payloads atomically; tombstones retain identity so
 * late callbacks cannot recreate history. Cancellation propagates and never proves rollback after
 * a possible commit. Reload original IDs after uncertainty; no automatic write/generation replay.
 * Versioned strict UTF8 payloads are length/type/hash preflighted before BLOB reads. Malformed is
 * CORRUPT_DATA, future format UNSUPPORTED, invalid input INVALID_INPUT; diagnostics exclude content.
 */
public interface LocalAiHistory {
    public suspend fun pageConversations(deckId: DeckId, page: PageRequest, cancel: CancelToken): Outcome<Page<Conversation>>
    public suspend fun getConversation(id: EntityId, cancel: CancelToken): Outcome<Conversation>
    public suspend fun createConversation(input: NewConversation, cancel: CancelToken): Outcome<Conversation>
    public suspend fun pageTurns(conversationId: EntityId, page: PageRequest, cancel: CancelToken): Outcome<Page<HistoryTurnSummary>>
    public suspend fun getTurn(id: EntityId, cancel: CancelToken): Outcome<Turn>
    public suspend fun appendTurn(conversationId: EntityId, expectedRevision: Revision, input: NewHistoryTurn, cancel: CancelToken): Outcome<Turn>
    public suspend fun checkpoint(turnId: EntityId, expectedRevision: Revision, ownerEpoch: EntityId, progress: HistoryProgress, cancel: CancelToken): Outcome<Turn>
    public suspend fun deleteConversation(id: EntityId, expectedRevision: Revision, cancel: CancelToken): Outcome<Unit>
    public suspend fun recover(ownerEpoch: EntityId, cancel: CancelToken): Outcome<Long>
}
public object AiHistoryLimits {
    public const val UNFINISHED_REASON: String = "Response unfinished"
    public const val MAX_PAYLOAD_BYTES: Int = 8 * 1024 * 1024
    public const val MAX_PROMPT_BYTES: Int = 4 * 1024 * 1024
    public const val MAX_REPLY_BYTES: Int = 256 * 1024
    public const val MAX_TITLE_BYTES: Int = 4096
    public const val MAX_BINDINGS: Int = 32
    public const val MAX_METADATA_BYTES: Int = 4096
}

/** SQL-controlled fields stay outside the content BLOB so recovery preserves content bytes exactly. */
public data class HistoryTurnMetadata(public val id: EntityId, public val conversationId: EntityId,
    public val revision: Revision, public val ordinal: Long, public val source: HistoryTurnSource,
    public val ownerEpoch: EntityId?, public val status: TurnStatus, public val startedAt: UtcTimestamp,
    public val finishedAt: Knowledge<UtcTimestamp>)
/** Format1 encodes immutable identity/provenance plus reply/usage, not SQL-controlled metadata. */
public interface AiHistoryPayloadCodecPort {
    public fun encodeTurn(value: Turn): Outcome<SnapshotBytes>
    public fun decodeTurn(payload: SnapshotBytes, metadata: HistoryTurnMetadata): Outcome<Turn>
}
